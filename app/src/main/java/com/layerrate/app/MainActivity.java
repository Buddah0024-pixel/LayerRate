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
  private ValueCallback<Uri[]> photoCallback;
  private Uri cameraPhotoUri;
  private static final int PHOTO_REQUEST = 4242;
  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
    super.onActivityResult(requestCode,resultCode,data);
    if(requestCode != PHOTO_REQUEST || photoCallback == null) return;
    Uri[] results = null;
    if(resultCode == RESULT_OK) {
      if(data != null && data.getData() != null) results = new Uri[]{data.getData()};
      else if(cameraPhotoUri != null) results = new Uri[]{cameraPhotoUri};
    }
    photoCallback.onReceiveValue(results);
    photoCallback = null;
    cameraPhotoUri = null;
  }


  String pendingUrl;
  boolean updateAvailable = false;
  boolean initialResume = true;
  void showUpdateStatus(boolean available) {
    updateAvailable = available;
    if (web != null) {
      String label = available ? "Update Available" : "Check Update";
      String js = "(function(){var b=document.getElementById('check-update-button');if(!b)return;b.textContent='" + label + "';b.style.borderColor='" + (available ? "#16a34a" : "") + "';b.style.color='" + (available ? "#16a34a" : "") + "';})()";
      web.evaluateJavascript(js, null);
    }
  }
  @Override protected void onResume() {
    super.onResume();
    if (initialResume) { initialResume = false; return; }
    checkUpdates(false);
  }
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
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        if(photoCallback != null) photoCallback.onReceiveValue(null);
        photoCallback = callback;
        cameraPhotoUri = null;
        Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
        gallery.addCategory(Intent.CATEGORY_OPENABLE);
        gallery.setType("image/*");
        Intent chooser = Intent.createChooser(gallery,"Choose print photo");
        try {
          android.content.ContentValues values = new android.content.ContentValues();
          values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"LayerRate_"+System.currentTimeMillis()+".jpg");
          values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
          cameraPhotoUri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
          if(cameraPhotoUri != null) {
            Intent camera = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,cameraPhotoUri);
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,new Intent[]{camera});
          }
        } catch(Exception ignored) { cameraPhotoUri = null; }
        try { startActivityForResult(chooser,PHOTO_REQUEST); return true; }
        catch(Exception e) { photoCallback.onReceiveValue(null); photoCallback=null; return false; }
      }
    });
    web.setWebViewClient(new WebViewClient() {
      @Override public void onPageFinished(WebView view, String url) {
        showUpdateStatus(updateAvailable);
      }
    });
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

          showUpdateStatus(version > BuildConfig.VERSION_CODE);
          if(version>BuildConfig.VERSION_CODE && manual) {
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
