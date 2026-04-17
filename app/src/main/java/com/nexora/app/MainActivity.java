package com.nexora.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "NexoraMain";
    private static final int PERM_REQ = 101;
    private static final int FILE_REQ = 100;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest r) { r.grant(r.getResources()); }
            @Override public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try { startActivityForResult(p.createIntent(), FILE_REQ); } catch (Exception e) { filePathCallback = null; return false; }
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient());
        requestAppPerms();
        webView.loadUrl("file:///android_asset/public/index.html");
        handleCallIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handleCallIntent(i); }

    private void handleCallIntent(Intent intent) {
        if (intent == null) return;
        if (CallNotificationManager.ACTION_ACCEPT.equals(intent.getAction())) {
            String cId = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID);
            String cUid = intent.getStringExtra(CallNotificationManager.EXTRA_CALLER_UID);
            String mType = intent.getStringExtra(CallNotificationManager.EXTRA_MEDIA_TYPE);
            Log.d(TAG, "Accept intent callId=" + cId);
            CallNotificationManager.stopRingtone();
            CallNotificationManager.stopVibration();
            new CallNotificationManager(this).dismissCallNotification();
            if (cId != null && !cId.isEmpty()) {
                final String fId=cId, fUid=cUid!=null?cUid:"", fType=mType!=null?mType:"audio";
                webView.postDelayed(() -> {
                    webView.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('nexora:accept_call',{detail:{callId:'"+fId+"',callerUid:'"+fUid+"',mediaType:'"+fType+"'}}))",
                        null);
                }, 1500);
            }
        }
    }

    private void requestAppPerms() {
        String[] p = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.POST_NOTIFICATIONS};
        boolean need = false;
        for (String x : p) if (ContextCompat.checkSelfPermission(this,x)!=PackageManager.PERMISSION_GRANTED){need=true;break;}
        if (need) ActivityCompat.requestPermissions(this, p, PERM_REQ);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req==FILE_REQ && filePathCallback!=null) {
            Uri[] r = (res==Activity.RESULT_OK&&data!=null) ? WebChromeClient.FileChooserParams.parseResult(res,data) : null;
            filePathCallback.onReceiveValue(r); filePathCallback=null;
        }
    }
    @Override public void onBackPressed() { if(webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
    @Override protected void onPause() { super.onPause(); webView.onPause(); }
    @Override protected void onResume() { super.onResume(); webView.onResume(); }
}
