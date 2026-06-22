package com.dino.hud;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private SensorManager sensorManager;
    private Sensor accelSensor, gyroSensor, pressureSensor;
    private LocationManager locationManager;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setUserAgentString(ws.getUserAgentString() + " DinoHUD/0.0.1");

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new SensorBridge(), "AndroidBridge");
        webView.loadUrl("https://konglong-milk.top:4002/HUDAPK");

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        }
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        requestPermissions();
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else { startSensors(); }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        if (rc == 1) startSensors();
    }

    @SuppressLint("MissingPermission")
    private void startSensors() {
        if (sensorManager != null) {
            sensorManager.registerListener(sensorListener, accelSensor, SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(sensorListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
            if (pressureSensor != null)
                sensorManager.registerListener(sensorListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (locationManager != null) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        1000L, 0f, locationListener, Looper.getMainLooper());
            } catch (Exception ignored) {}
        }
    }

    private void sendToJS(String json) {
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(
                    "if(typeof onAndroidData==='function')onAndroidData(" + json + ")", null));
        }
    }

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            int type = e.sensor.getType();
            if (type == Sensor.TYPE_ACCELEROMETER && e.values.length >= 3) {
                sendToJS(String.format(Locale.US, "{\"t\":\"accel\",\"x\":%.3f,\"y\":%.3f,\"z\":%.3f}", e.values[0], e.values[1], e.values[2]));
            } else if (type == Sensor.TYPE_GYROSCOPE && e.values.length >= 3) {
                sendToJS(String.format(Locale.US, "{\"t\":\"gyro\",\"x\":%.4f,\"y\":%.4f,\"z\":%.4f}", e.values[0], e.values[1], e.values[2]));
            } else if (type == Sensor.TYPE_PRESSURE && e.values.length >= 1) {
                sendToJS(String.format(Locale.US, "{\"t\":\"pressure\",\"hPa\":%.2f}", e.values[0]));
            }
        }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location loc) {
            sendToJS(String.format(Locale.US, "{\"t\":\"gps\",\"lat\":%.8f,\"lng\":%.8f,\"alt\":%.1f,\"acc\":%.1f,\"spd\":%.2f}",
                    loc.getLatitude(), loc.getLongitude(),
                    loc.hasAltitude() ? loc.getAltitude() : 0,
                    loc.hasAccuracy() ? loc.getAccuracy() : 99,
                    loc.hasSpeed() ? loc.getSpeed() : 0));
        }
        @Override public void onProviderDisabled(String p) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onStatusChanged(String p, int s, Bundle b) {}
    };

    public class SensorBridge {
        @JavascriptInterface
        public String getPlatform() { return "android"; }
        @JavascriptInterface
        public void ready() { startSensors(); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(sensorListener);
        if (locationManager != null) locationManager.removeUpdates(locationListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startSensors();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
