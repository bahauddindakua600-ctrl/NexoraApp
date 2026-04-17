/**
 * NEXORA — Netlify Function: send-push
 * ─────────────────────────────────────────────────────────────────────────────
 * Messenger-স্টাইলে FCM push পাঠায়।
 *
 * Call notification:
 *   • শুধু DATA message পাঠায় (notification field নেই)
 *   • Android FCM SDK app বন্ধ থাকলেও MyFirebaseMessagingService জাগায়
 *   • Service নিজেই CallStyle / heads-up notification দেখায়
 *   • Ringtone + Vibration চালু করে
 *
 * Regular notification (message, friend request):
 *   • notification + data উভয় পাঠায়
 * ─────────────────────────────────────────────────────────────────────────────
 */

const admin = require("firebase-admin");

if (!admin.apps.length) {
  try {
    const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
    if (!raw) throw new Error("FIREBASE_SERVICE_ACCOUNT not set");
    const serviceAccount = JSON.parse(raw);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      databaseURL: process.env.FIREBASE_DATABASE_URL,
    });
  } catch(e) {
    console.error("Firebase init error:", e.message);
  }
}

const db        = admin.apps.length ? admin.database() : null;
const messaging = admin.apps.length ? admin.messaging() : null;

exports.handler = async (event) => {
  const headers = {
    "Access-Control-Allow-Origin":  "*",
    "Access-Control-Allow-Headers": "Content-Type",
    "Content-Type": "application/json",
  };

  if (event.httpMethod === "OPTIONS") {
    return { statusCode: 200, headers, body: "" };
  }

  if (event.httpMethod !== "POST") {
    return { statusCode: 405, headers, body: JSON.stringify({ error: "Method not allowed" }) };
  }

  if (!db || !messaging) {
    return { statusCode: 500, headers, body: JSON.stringify({ error: "Firebase not initialized" }) };
  }

  try {
    const body = JSON.parse(event.body || "{}");
    const { uid, callData, notifData } = body;

    if (!uid) {
      return { statusCode: 400, headers, body: JSON.stringify({ error: "Missing uid" }) };
    }

    // FCM token নিয়ে আসো
    const tokenSnap = await db.ref(`/users/${uid}/fcmToken`).get();
    const token = tokenSnap.val();

    if (!token) {
      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({ success: false, reason: "no_token" }),
      };
    }

    let message;

    // ══ CALL NOTIFICATION ══════════════════════════════════════════════════════
    if (callData) {
      const mediaType = callData.mediaType || callData.type || "audio";
      const isVideo   = mediaType === "video";

      // ─── Pure DATA message — MyFirebaseMessagingService handle করবে ────────
      // ⚠️ notification field নেই — তাহলে FCM নিজে notification দেখাবে না।
      //    আমাদের native service CallStyle notification দেখাবে।
      message = {
        token,
        // Android: HIGH priority — app বন্ধ থাকলেও wake করবে
        android: {
          priority: "high",
          ttl:      "30s",
          // notification field ইচ্ছাকৃতভাবে নেই!
          data: {
            type:        "incoming_call",
            callId:      String(callData.callId      || ""),
            callerUid:   String(callData.callerUid   || ""),
            callerName:  String(callData.callerName  || ""),
            callerEmoji: String(callData.callerEmoji || "😊"),
            callerPhoto: String(callData.callerPhoto || ""),
            mediaType:   String(mediaType),
          },
        },
        // Web/PWA এর জন্য webpush (browser notification)
        webpush: {
          headers: { Urgency: "high", TTL: "30" },
          fcmOptions: { link: "/" },
          data: {
            type:        "incoming_call",
            callId:      String(callData.callId      || ""),
            callerUid:   String(callData.callerUid   || ""),
            callerName:  String(callData.callerName  || ""),
            callerEmoji: String(callData.callerEmoji || "😊"),
            callerPhoto: String(callData.callerPhoto || ""),
            mediaType:   String(mediaType),
          },
        },
        // data field (legacy / fallback)
        data: {
          type:        "incoming_call",
          callId:      String(callData.callId      || ""),
          callerUid:   String(callData.callerUid   || ""),
          callerName:  String(callData.callerName  || ""),
          callerEmoji: String(callData.callerEmoji || "😊"),
          callerPhoto: String(callData.callerPhoto || ""),
          mediaType:   String(mediaType),
        },
      };

    // ══ CALL CANCELLED ════════════════════════════════════════════════════════
    } else if (body.cancelCall) {
      message = {
        token,
        android: { priority: "high", ttl: "10s" },
        data: {
          type:   "call_cancelled",
          callId: String(body.cancelCall.callId || ""),
        },
      };

    // ══ REGULAR NOTIFICATION (message, friend request, etc.) ═════════════════
    } else if (notifData) {
      const title = notifData.title || "NEXORA";
      const body2 = notifData.body  || "নতুন বার্তা";
      const nType = notifData.type  || "message";

      message = {
        token,
        android: {
          priority: "high",
          notification: {
            title,
            body: body2,
            icon: "ic_launcher",
            color: "#00d4ff",
            sound: "default",
            channel_id: "nexora_messages",
            click_action: "FLUTTER_NOTIFICATION_CLICK",
          },
        },
        webpush: {
          headers: { Urgency: "high" },
          notification: { title, body: body2, icon: "/icon-192.png" },
          fcmOptions: { link: "/" },
        },
        data: { type: nType, title, body: body2 },
      };

    } else {
      return {
        statusCode: 400,
        headers,
        body: JSON.stringify({ error: "Missing callData or notifData" }),
      };
    }

    await messaging.send(message);
    return { statusCode: 200, headers, body: JSON.stringify({ success: true }) };

  } catch (err) {
    console.error("send-push error:", err.message);
    return { statusCode: 500, headers, body: JSON.stringify({ error: err.message }) };
  }
};
