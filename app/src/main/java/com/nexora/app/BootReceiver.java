package com.nexora.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * NEXORA — Boot Receiver
 * ─────────────────────────────────────────────────────────────
 * Phone restart হলে FCM connection আবার establish হয়।
 * Facebook / Messenger এর মতো phone on হওয়ার পরেই
 * push notification receive করার জন্য।
 * ─────────────────────────────────────────────────────────────
 * android/app/src/main/java/com/nexora/app/ এ রাখুন
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "NexoraBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Log.d(TAG, "Boot/update received, FCM will reconnect automatically");
            // FCM SDK নিজেই reconnect করে — এখানে extra কিছু করতে হবে না
            // শুধু notification channels তৈরি নিশ্চিত করো
            new CallNotificationManager(context);
        }
    }
}
