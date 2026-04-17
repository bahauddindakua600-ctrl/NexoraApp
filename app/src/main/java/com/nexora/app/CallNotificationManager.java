package com.nexora.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * NEXORA — Call Notification Manager
 * ─────────────────────────────────────────────────────────────
 * Messenger-স্টাইলে incoming call notification দেখায়:
 * • Full-screen notification (lock screen এও দেখা যাবে)
 * • CallStyle UI (Android 12+)
 * • Ringtone বাজায়
 * • Vibration
 * • Accept / Decline বোতাম
 * ─────────────────────────────────────────────────────────────
 * android/app/src/main/java/com/nexora/app/ এ রাখুন
 */
public class CallNotificationManager {

    private static final String TAG = "NexoraCallNotif";

    // Notification channels
    public static final String CHANNEL_CALLS    = "nexora_calls";
    public static final String CHANNEL_MESSAGES = "nexora_messages";
    public static final String CHANNEL_DEFAULT  = "nexora_default";

    // Notification IDs
    public static final int NOTIF_ID_CALL    = 1001;
    public static final int NOTIF_ID_MESSAGE = 1002;

    // Intent actions
    public static final String ACTION_ACCEPT  = "com.nexora.app.ACCEPT_CALL";
    public static final String ACTION_DECLINE = "com.nexora.app.DECLINE_CALL";

    // Extra keys
    public static final String EXTRA_CALL_ID    = "callId";
    public static final String EXTRA_CALLER_UID = "callerUid";
    public static final String EXTRA_MEDIA_TYPE = "mediaType";

    private final Context context;
    private static MediaPlayer ringtonePlayer;
    private static Vibrator vibrator;

    public CallNotificationManager(Context context) {
        this.context = context;
        createNotificationChannels();
    }

    // ── Notification Channels তৈরি ────────────────────────────────────────────
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = context.getSystemService(NotificationManager.class);

        // ── Call Channel — সর্বোচ্চ priority, ringtone বাজবে ──────────────
        Uri callRingtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        AudioAttributes audioAttr = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build();

        NotificationChannel callChannel = new NotificationChannel(
            CHANNEL_CALLS,
            "ইনকামিং কল",
            NotificationManager.IMPORTANCE_HIGH
        );
        callChannel.setDescription("NEXORA থেকে আসা কল notification");
        callChannel.setSound(callRingtone, audioAttr);
        callChannel.enableVibration(true);
        callChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000, 500, 1000});
        callChannel.setShowBadge(true);
        callChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(callChannel);

        // ── Message Channel ────────────────────────────────────────────────
        NotificationChannel msgChannel = new NotificationChannel(
            CHANNEL_MESSAGES,
            "বার্তা",
            NotificationManager.IMPORTANCE_DEFAULT
        );
        msgChannel.setDescription("NEXORA বার্তা notification");
        nm.createNotificationChannel(msgChannel);

        // ── Default Channel ────────────────────────────────────────────────
        NotificationChannel defChannel = new NotificationChannel(
            CHANNEL_DEFAULT,
            "সাধারণ",
            NotificationManager.IMPORTANCE_DEFAULT
        );
        nm.createNotificationChannel(defChannel);
    }

    // ══ MAIN: Incoming Call Notification (Messenger-স্টাইল) ══════════════════
    public void showIncomingCallNotification(
        String callId,
        String callerUid,
        String callerName,
        String callerPhoto,
        String mediaType,
        String emoji
    ) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        // ── Accept Intent ─────────────────────────────────────────────────
        Intent acceptIntent = new Intent(context, IncomingCallReceiver.class);
        acceptIntent.setAction(ACTION_ACCEPT);
        acceptIntent.putExtra(EXTRA_CALL_ID,    callId);
        acceptIntent.putExtra(EXTRA_CALLER_UID, callerUid);
        acceptIntent.putExtra(EXTRA_MEDIA_TYPE, mediaType);
        PendingIntent acceptPI = PendingIntent.getBroadcast(
            context, 0, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // ── Decline Intent ────────────────────────────────────────────────
        Intent declineIntent = new Intent(context, IncomingCallReceiver.class);
        declineIntent.setAction(ACTION_DECLINE);
        declineIntent.putExtra(EXTRA_CALL_ID,    callId);
        declineIntent.putExtra(EXTRA_CALLER_UID, callerUid);
        PendingIntent declinePI = PendingIntent.getBroadcast(
            context, 1, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // ── Full-screen Intent (lock screen এও দেখাবে) ───────────────────
        Intent fullScreenIntent = new Intent(context, MainActivity.class);
        fullScreenIntent.setAction(ACTION_ACCEPT);
        fullScreenIntent.putExtra(EXTRA_CALL_ID,    callId);
        fullScreenIntent.putExtra(EXTRA_CALLER_UID, callerUid);
        fullScreenIntent.putExtra(EXTRA_MEDIA_TYPE, mediaType);
        fullScreenIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_CLEAR_TOP |
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        PendingIntent fullScreenPI = PendingIntent.getActivity(
            context, 2, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // ── Caller Photo load করো (background thread) ─────────────────────
        Bitmap callerBitmap = loadBitmapFromUrl(callerPhoto);
        if (callerBitmap == null) {
            // Photo নেই — emoji থেকে placeholder তৈরি করো
            callerBitmap = createEmojiPlaceholder(emoji);
        } else {
            callerBitmap = getCircularBitmap(callerBitmap);
        }

        // ── Notification Build করো ────────────────────────────────────────
        boolean isVideo = "video".equalsIgnoreCase(mediaType);
        String callTypeText = isVideo ? "ভিডিও কল আসছে" : "ভয়েস কল আসছে";

        NotificationCompat.Builder builder;

        // Android 12+ এ CallStyle ব্যবহার করো (Messenger-এর মতো দেখতে)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Person caller = new Person.Builder()
                .setName(callerName)
                .setIcon(callerBitmap != null
                    ? IconCompat.createWithBitmap(callerBitmap)
                    : IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setImportant(true)
                .build();

            builder = new NotificationCompat.Builder(context, CHANNEL_CALLS)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(
                    caller, declinePI, acceptPI
                ))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setFullScreenIntent(fullScreenPI, true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(45000); // ৪৫ সেকেন্ড পর auto dismiss

        } else {
            // Android 11 এবং পুরনো — classic heads-up notification
            builder = new NotificationCompat.Builder(context, CHANNEL_CALLS)
                .setContentTitle(callerName)
                .setContentText(callTypeText)
                .setSubText("NEXORA")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(callerBitmap)
                .setFullScreenIntent(fullScreenPI, true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(45000)
                .addAction(new NotificationCompat.Action.Builder(
                    R.mipmap.ic_launcher,
                    "❌  কাটুন",
                    declinePI
                ).build())
                .addAction(new NotificationCompat.Action.Builder(
                    R.mipmap.ic_launcher,
                    isVideo ? "📹  ভিডিও" : "📞  ধরুন",
                    acceptPI
                ).build());
        }

        // ── Notification দেখাও ────────────────────────────────────────────
        try {
            nm.notify(NOTIF_ID_CALL, builder.build());
            Log.d(TAG, "Call notification shown for: " + callerName);
        } catch (Exception e) {
            Log.e(TAG, "Notification error: " + e.getMessage());
        }

        // ── Ringtone বাজাও ────────────────────────────────────────────────
        startRingtone();

        // ── Vibration ─────────────────────────────────────────────────────
        startVibration();
    }

    // ── Call Notification সরাও ────────────────────────────────────────────────
    public void dismissCallNotification() {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF_ID_CALL);
        stopRingtone();
        stopVibration();
    }

    // ── Regular Notification ─────────────────────────────────────────────────
    public void showRegularNotification(String title, String body, String type) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("notifType", type);
        PendingIntent pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true);

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID_MESSAGE, builder.build());
    }

    // ── Ringtone Start ────────────────────────────────────────────────────────
    private void startRingtone() {
        try {
            stopRingtone(); // আগের টা বন্ধ করো
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtonePlayer = new MediaPlayer();
            ringtonePlayer.setDataSource(context, ringtoneUri);
            ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
            ringtonePlayer.setLooping(true); // কল না কাটা পর্যন্ত বাজতে থাকবে
            ringtonePlayer.prepare();
            ringtonePlayer.start();
            Log.d(TAG, "Ringtone started");
        } catch (Exception e) {
            Log.e(TAG, "Ringtone error: " + e.getMessage());
        }
    }

    // ── Ringtone Stop ─────────────────────────────────────────────────────────
    public static void stopRingtone() {
        try {
            if (ringtonePlayer != null) {
                if (ringtonePlayer.isPlaying()) ringtonePlayer.stop();
                ringtonePlayer.release();
                ringtonePlayer = null;
            }
        } catch (Exception e) {
            Log.e("NexoraCallNotif", "Stop ringtone error: " + e.getMessage());
        }
    }

    // ── Vibration Start ───────────────────────────────────────────────────────
    private void startVibration() {
        try {
            long[] pattern = {0, 800, 400, 800, 400};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vibrator = vm.getDefaultVibrator();
                }
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Vibration error: " + e.getMessage());
        }
    }

    // ── Vibration Stop ────────────────────────────────────────────────────────
    public static void stopVibration() {
        try {
            if (vibrator != null) {
                vibrator.cancel();
                vibrator = null;
            }
        } catch (Exception ignore) {}
    }

    // ── URL থেকে Bitmap লোড ───────────────────────────────────────────────────
    private Bitmap loadBitmapFromUrl(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoInput(true);
            conn.connect();
            InputStream input = conn.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Circular Bitmap (গোল ছবি) ────────────────────────────────────────────
    private Bitmap getCircularBitmap(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        Rect rect = new Rect(0, 0, size, size);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawOval(new RectF(rect), paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return output;
    }

    // ── Emoji Placeholder (ছবি না থাকলে) ────────────────────────────────────
    private Bitmap createEmojiPlaceholder(String emoji) {
        int size = 200;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.parseColor("#1a1a2e"));
        Paint textPaint = new Paint();
        textPaint.setTextSize(80);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        canvas.drawText(emoji != null ? emoji : "👤", size / 2f, size / 2f + 30, textPaint);
        return bitmap;
    }
}
