package com.layerrate.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.net.*;
import java.io.*;

public class MainActivity extends Activity {
  WebView web;

  String pendingUrl;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);if (Build.VERSION.SDK_INT >= 35) {
    getWindow().setStatusBarContrastEnforced(false);
    getWindow().setNavigationBarContrastEnforced(false);
}

getWindow().getDecorView().setOnApplyWindowInsetsListener((view, insets) -> {
    android.graphics.Insets bars = insets.getInsets(
        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()
    );
    view.setPadding(0, bars.top, 0, bars.bottom);
    return insets;
});
    web = new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.setWebViewClient(new WebViewClient());
    web.addJavascriptInterface(new Object() { @JavascriptInterface public void checkForUpdates() { runOnUiThread(() -> checkUpdates(true)); } }, "LayerRateAndroid");
    web.loadUrl("file:///android_asset/index.html");
    setContentView(web);
    checkUpdates(false);
  }
  void checkUpdates(boolean manual) {
    if(BuildConfig.UPDATE_MANIFEST_URL.contains("REPLACE-WITH-YOUR-HOST")) {
      if(manual) new AlertDialog.Builder(this).setMessage("Update server not configured yet.").setPositiveButton("OK",null).show();
      return;
    }

    new Thread(() -> {
      try {
        URL url = new URL(BuildConfig.UPDATE_MANIFEST_URL + (BuildConfig.UPDATE_MANIFEST_URL.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis());
        if(!url.getProtocol().equals("https")) throw new Exception("HTTPS required");
        HttpURLConnection c = (HttpURLConnection)url.openConnection();
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
        c.setRequestProperty("Pragma", "no-cache");
        c.setConnectTimeout(10000); c.setReadTimeout(10000);
        if(c.getResponseCode()!=200) throw new Exception("Server error "+c.getResponseCode());
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=c.getInputStream()) {
          byte[] buf=new byte[4096]; int n;
          while((n=in.read(buf))!=-1) { out.write(buf,0,n); if(out.size()>65536) throw new Exception("Manifest too large"); }
        }
        JSONObject data=new JSONObject(out.toString("UTF-8"));
        int version=data.getInt("versionCode");
        String apk=data.getString("apkUrl");
        String notes=data.optString("notes","Bug fixes and improvements");
        if(!apk.startsWith("https://")) throw new Exception("APK must use HTTPS");
        runOnUiThread(() -> {

          if(version>BuildConfig.VERSION_CODE) {
            new AlertDialog.Builder(this).setTitle("LayerRate update available")
              .setMessage("Version "+data.optString("versionName","new")+"\n\n"+notes)
              .setNegativeButton("Later",null)
              .setPositiveButton("Download", (d,w)->download(apk)).show();
          } else if(manual) Toast.makeText(this,"LayerRate is up to date",Toast.LENGTH_LONG).show();
        });
      } catch(Exception e) {
        runOnUiThread(() -> {

          if(manual) new AlertDialog.Builder(this).setMessage("Unable to check for updates: "+e.getMessage()).setPositiveButton("OK",null).show();
        });
      }
    }).start();
  }
  void download(String apk) {
    try {
      DownloadManager.Request req=new DownloadManager.Request(Uri.parse(apk));
      req.setTitle("LayerRate update");
      req.setDescription("Tap download notification to install after download completes");
      req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"LayerRate-update.apk");
      ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
      new AlertDialog.Builder(this).setMessage("Downloading the update. When finished, tap the download notification and approve the Android installation.").setPositiveButton("OK",null).show();
    } catch(Exception e) { Toast.makeText(this,"Download failed: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
  }
  @Override public void onBackPressed() {
    if(web.canGoBack()) web.goBack(); else super.onBackPressed();
  }
}
