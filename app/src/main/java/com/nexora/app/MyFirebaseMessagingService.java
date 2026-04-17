package com.nexora.app;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * NEXORA — FCM Service
 * ─────────────────────────────────────────────────────────────
 * অ্যাপ বন্ধ / background এ থাকলেও FCM data message পেলে
 * এই service জেগে ওঠে এবং incoming call notification দেখায়।
 * ঠিক Messenger / WhatsApp এর মতো।
 * ─────────────────────────────────────────────────────────────
 * android/app/src/main/java/com/nexora/app/ এ রাখুন
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "NexoraFCM";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        if (data == null || data.isEmpty()) return;

        String type = data.get("type");
        Log.d(TAG, "FCM received, type: " + type);

        if ("incoming_call".equals(type)) {
            // ── Incoming Call — Messenger স্টাইলে রিং বাজাও ──────────────
            handleIncomingCall(data);

        } else if ("call_cancelled".equals(type)) {
            // ── কল বাতিল হয়ে গেছে — notification সরাও ──────────────────
            CallNotificationManager manager = new CallNotificationManager(this);
            manager.dismissCallNotification();

        } else if ("call_accepted".equals(type)) {
            // ── অন্য device এ accept হয়েছে — notification সরাও ─────────
            CallNotificationManager manager = new CallNotificationManager(this);
            manager.dismissCallNotification();

        } else {
            // ── Regular notification (message, friend request, etc.) ───────
            handleRegularNotification(data);
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Token update হলে app এর JS side handle করবে
        Log.d(TAG, "FCM token refreshed");
    }

    // ── Incoming Call Handler ─────────────────────────────────────────────────
    private void handleIncomingCall(Map<String, String> data) {
        String callId     = data.getOrDefault("callId",     "");
        String callerUid  = data.getOrDefault("callerUid",  "");
        String callerName = data.getOrDefault("callerName", "কেউ");
        String callerPhoto= data.getOrDefault("callerPhoto","");
        String callType   = data.getOrDefault("type",       "audio"); // audio/video
        String emoji      = data.getOrDefault("callerEmoji","😊");

        // callType field এ "incoming_call" আসে, আলাদাভাবে mediaType নিই
        String mediaType  = data.getOrDefault("mediaType",  "audio");

        CallNotificationManager manager = new CallNotificationManager(this);
        manager.showIncomingCallNotification(
            callId, callerUid, callerName, callerPhoto, mediaType, emoji
        );
    }

    // ── Regular Notification Handler ─────────────────────────────────────────
    private void handleRegularNotification(Map<String, String> data) {
        String title = data.getOrDefault("title", "NEXORA");
        String body  = data.getOrDefault("body",  "নতুন বার্তা");
        String type  = data.getOrDefault("type",  "message");

        CallNotificationManager manager = new CallNotificationManager(this);
        manager.showRegularNotification(title, body, type);
    }
}
