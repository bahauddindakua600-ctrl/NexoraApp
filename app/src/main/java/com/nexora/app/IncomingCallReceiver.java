package com.nexora.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * NEXORA — Incoming Call Receiver
 * ─────────────────────────────────────────────────────────────
 * Notification এর Accept / Decline বোতামে tap করলে
 * এই Receiver কাজ করে।
 *
 * Accept → MainActivity খোলে এবং call join করে
 * Decline → Ringtone বন্ধ করে, notification সরায়,
 *           Firebase এ cancelled লেখে (JS side handle করে)
 * ─────────────────────────────────────────────────────────────
 * android/app/src/main/java/com/nexora/app/ এ রাখুন
 */
public class IncomingCallReceiver extends BroadcastReceiver {

    private static final String TAG = "NexoraCallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action    = intent.getAction();
        String callId    = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID);
        String callerUid = intent.getStringExtra(CallNotificationManager.EXTRA_CALLER_UID);
        String mediaType = intent.getStringExtra(CallNotificationManager.EXTRA_MEDIA_TYPE);

        Log.d(TAG, "Action received: " + action + ", callId: " + callId);

        if (CallNotificationManager.ACTION_ACCEPT.equals(action)) {
            handleAccept(context, callId, callerUid, mediaType);

        } else if (CallNotificationManager.ACTION_DECLINE.equals(action)) {
            handleDecline(context, callId, callerUid);
        }
    }

    // ── Accept: অ্যাপ খুলে call join করো ────────────────────────────────────
    private void handleAccept(Context context, String callId, String callerUid, String mediaType) {
        // Ringtone ও Vibration বন্ধ করো
        CallNotificationManager.stopRingtone();
        CallNotificationManager.stopVibration();

        // Notification সরাও
        CallNotificationManager manager = new CallNotificationManager(context);
        manager.dismissCallNotification();

        // MainActivity খুলে call data পাঠাও
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setAction(CallNotificationManager.ACTION_ACCEPT);
        launchIntent.putExtra(CallNotificationManager.EXTRA_CALL_ID,    callId);
        launchIntent.putExtra(CallNotificationManager.EXTRA_CALLER_UID, callerUid);
        launchIntent.putExtra(CallNotificationManager.EXTRA_MEDIA_TYPE,
            mediaType != null ? mediaType : "audio");
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_CLEAR_TOP |
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        context.startActivity(launchIntent);

        Log.d(TAG, "Call accepted, opening app");
    }

    // ── Decline: Ringtone বন্ধ করো, Firebase এ cancelled লেখো ──────────────
    private void handleDecline(Context context, String callId, String callerUid) {
        // Ringtone ও Vibration বন্ধ করো
        CallNotificationManager.stopRingtone();
        CallNotificationManager.stopVibration();

        // Notification সরাও
        CallNotificationManager manager = new CallNotificationManager(context);
        manager.dismissCallNotification();

        // Firebase এ call cancelled লেখো — background thread এ
        // (JS side Firebase listener এটা দেখে caller কে জানাবে)
        if (callId != null && !callId.isEmpty()) {
            new Thread(() -> {
                try {
                    // Firebase REST API ব্যবহার করো (SDK ছাড়া)
                    String dbUrl = "https://bahauddin-23ee3-default-rtdb.firebaseio.com";
                    String path  = "/calls/" + callId + "/status.json";
                    String body  = "\"declined\"";

                    java.net.URL url = new java.net.URL(dbUrl + path);
                    java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PUT");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.getOutputStream().write(body.getBytes("UTF-8"));
                    conn.getResponseCode(); // trigger request
                    conn.disconnect();
                    Log.d(TAG, "Call declined, Firebase updated");
                } catch (Exception e) {
                    Log.e(TAG, "Decline Firebase error: " + e.getMessage());
                }
            }).start();
        }
    }
}
