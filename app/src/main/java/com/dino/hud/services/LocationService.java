package com.dino.hud.services;

import android.Manifest;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.*;
import android.os.*;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.dino.hud.models.TrailPoint;
import com.dino.hud.utils.CoordTransform;

import java.util.ArrayList;
import java.util.List;

/**
 * GPS + 传感器采集服务（前台服务，保活）
 */
public class LocationService extends Service implements SensorEventListener {
    private static final String TAG = "LocationService";
    private static final int NOTIFY_ID = 1001;
    private static final String CHANNEL_ID = "dino_hud";

    private LocationManager lm;
    private SensorManager sm;
    private Sensor accel, gyro, pressure;
    private final Binder binder = new LocalBinder();
    private final List<Callback> callbacks = new ArrayList<>();

    // 当前状态
    private double lat, lng, speed, altitude, accuracy = 99, heading;
    private float accX, accY, accZ, gyroX, gyroY, gyroZ;
    private float hPa;
    private long lastGpsTime;
    private final List<TrailPoint> positions = new ArrayList<>();
    private double sessionDist;

    public interface Callback {
        void onLocation(double lat, double lng, double speed, double alt, double acc, double heading);
        void onAccel(float x, float y, float z);
        void onGyro(float x, float y, float z);
        void onPressure(float hPa);
    }

    public class LocalBinder extends Binder {
        public LocationService getService() { return LocationService.this; }
    }

    @Override
    public IBinder onBind(Intent i) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFY_ID, buildNoti("正在初始化…"));

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            pressure = sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
        }
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        startTracking();
        return START_STICKY;
    }

    @SuppressWarnings("MissingPermission")
    private void startTracking() {
        // GPS
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                1000L, 0.5f, gpsListener, Looper.getMainLooper());
        } catch (Exception e) { Log.w(TAG, "GPS not available: " + e.getMessage()); }
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                2000L, 1f, gpsListener, Looper.getMainLooper());
        } catch (Exception ignored) {}

        // Sensors
        if (sm != null) {
            if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
            if (gyro != null) sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME);
            if (pressure != null) sm.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL);
        }
        updateNoti("GPS 搜索中…");
    }

    public void registerCallback(Callback cb) { if (!callbacks.contains(cb)) callbacks.add(cb); }
    public void unregisterCallback(Callback cb) { callbacks.remove(cb); }

    // Getters
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public double getSpeed() { return speed; }
    public double getAltitude() { return altitude; }
    public double getAccuracy() { return accuracy; }
    public double getHeading() { return heading; }
    public float getAccX() { return accX; }
    public float getAccY() { return accY; }
    public float getAccZ() { return accZ; }
    public float getGyroX() { return gyroX; }
    public float getGyroY() { return gyroY; }
    public float getGyroZ() { return gyroZ; }
    public float getPressure() { return hPa; }
    public List<TrailPoint> getPositions() { return new ArrayList<>(positions); }
    public double getSessionDist() { return sessionDist; }

    public void rotateScreen(boolean on) { /* no-op for now */ }

    // ---- GPS Listener ----
    private final LocationListener gpsListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location loc) {
            lat = loc.getLatitude();
            lng = loc.getLongitude();
            speed = (loc.hasSpeed() ? loc.getSpeed() : 0) * 3.6; // km/h
            altitude = loc.hasAltitude() ? loc.getAltitude() : 0;
            accuracy = loc.hasAccuracy() ? loc.getAccuracy() : 99;
            if (loc.hasBearing() && loc.getBearing() > 0) heading = loc.getBearing();
            lastGpsTime = System.currentTimeMillis();

            // 累积轨迹
            if (positions.isEmpty()) {
                positions.add(point(lat, lng));
            } else {
                TrailPoint last = positions.get(positions.size() - 1);
                double d = CoordTransform.haversineM(last.lat, last.lng, lat, lng);
                if (d > 3) {
                    positions.add(point(lat, lng));
                    if (positions.size() > 20000) positions.remove(0);
                    sessionDist += d;
                }
            }

            updateNoti(String.format("速度 %.0f km/h GPS精度 %.0fm", speed, accuracy));

            for (Callback cb : callbacks)
                cb.onLocation(lat, lng, speed, altitude, accuracy, heading);
        }
    };

    // ---- Sensor Listener ----
    @Override
    public void onSensorChanged(SensorEvent e) {
        int t = e.sensor.getType();
        switch (t) {
            case Sensor.TYPE_ACCELEROMETER:
                accX = e.values[0]; accY = e.values[1]; accZ = e.values[2];
                for (Callback cb : callbacks) cb.onAccel(accX, accY, accZ);
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyroX = e.values[0]; gyroY = e.values[1]; gyroZ = e.values[2];
                for (Callback cb : callbacks) cb.onGyro(gyroX, gyroY, gyroZ);
                break;
            case Sensor.TYPE_PRESSURE:
                hPa = e.values[0];
                for (Callback cb : callbacks) cb.onPressure(hPa);
                break;
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    @Override
    public void onDestroy() {
        if (lm != null) lm.removeUpdates(gpsListener);
        if (sm != null) sm.unregisterListener(this);
        stopForeground(true);
        super.onDestroy();
    }

    // ---- Helpers ----
    private TrailPoint point(double la, double ln) {
        TrailPoint p = new TrailPoint();
        p.lat = la; p.lng = ln; return p;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                "恐龙HUD", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("GPS 与传感器数据采集");
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
        }
    }

    private Notification buildNoti(String text) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        Intent i = launch != null ? launch : new Intent();
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("恐龙HUD 运行中")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pi)
            .build();
    }

    private void updateNoti(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFY_ID, buildNoti(text));
    }
}
