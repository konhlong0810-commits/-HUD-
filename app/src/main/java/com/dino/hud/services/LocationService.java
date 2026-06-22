package com.dino.hud.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.*;
import android.os.*;
import android.util.Log;

import com.dino.hud.models.TrailPoint;
import com.dino.hud.utils.CoordTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** GPS + 传感器采集服务 */
public class LocationService extends Service implements SensorEventListener {
    private static final String TAG = "LocSvc";

    private LocationManager lm;
    private SensorManager sm;
    private Sensor accel, gyro, pressure;
    private final LocalBinder binder = new LocalBinder();
    private final List<Callback> callbacks = new CopyOnWriteArrayList<>();

    private double lat, lng, speed, altitude = 0, accuracy = 99, heading;
    private float accX, accY, accZ, gyroX, gyroY, gyroZ, hPa;
    private final List<TrailPoint> positions = new ArrayList<>();
    private double sessionDist;
    private TrailPoint lastPoint;

    public interface Callback {
        void onLocation(double lat, double lng, double speed, double alt, double acc, double heading);
        void onAccel(float x, float y, float z);
        void onGyro(float x, float y, float z);
        void onPressure(float hPa);
    }

    public class LocalBinder extends Binder {
        public LocationService getService() { return LocationService.this; }
    }

    @Override public IBinder onBind(Intent i) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            pressure = sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
        }
        startTracking();
    }

    @Override
    public void onDestroy() {
        stopTracking();
        super.onDestroy();
    }

    @SuppressWarnings("MissingPermission")
    private void startTracking() {
        try {
            if (lm != null) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.5f, gpsListener, Looper.getMainLooper());
            }
        } catch (Exception e) { Log.w(TAG, "GPS err: " + e.getMessage()); }
        try { if (lm != null) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 1f, gpsListener, Looper.getMainLooper()); } catch (Exception ignored) {}
        try {
            if (sm != null) {
                if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
                if (gyro != null) sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME);
                if (pressure != null) sm.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } catch (Exception e) { Log.w(TAG, "Sensor err: " + e.getMessage()); }
    }

    private void stopTracking() {
        try { if (lm != null) lm.removeUpdates(gpsListener); } catch (Exception ignored) {}
        try { if (sm != null) sm.unregisterListener(this); } catch (Exception ignored) {}
    }

    // ---- Getters ----
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public double getSpeed() { return speed; }
    public double getAltitude() { return altitude; }
    public double getAccuracy() { return accuracy; }
    public double getHeading() { return heading; }
    public float getAccX() { return accX; } public float getAccY() { return accY; } public float getAccZ() { return accZ; }
    public float getGyroX() { return gyroX; } public float getGyroY() { return gyroY; } public float getGyroZ() { return gyroZ; }
    public float getPressure() { return hPa; }
    public List<TrailPoint> getPositions() { return new ArrayList<>(positions); }
    public double getSessionDist() { return sessionDist; }

    // ---- Callbacks ----
    public void registerCallback(Callback cb) { if (!callbacks.contains(cb)) callbacks.add(cb); }
    public void unregisterCallback(Callback cb) { callbacks.remove(cb); }

    // ---- GPS Listener ----
    private final LocationListener gpsListener = new LocationListener() {
        @Override public void onLocationChanged(Location loc) {
            lat = loc.getLatitude(); lng = loc.getLongitude();
            speed = loc.hasSpeed() ? loc.getSpeed() * 3.6 : 0;
            altitude = loc.hasAltitude() ? loc.getAltitude() : 0;
            accuracy = loc.hasAccuracy() ? loc.getAccuracy() : 99;
            if (loc.hasBearing() && loc.getBearing() > 0) heading = loc.getBearing();

            TrailPoint pt = new TrailPoint(); pt.lat = lat; pt.lng = lng;
            if (lastPoint != null && positions.size() > 0) {
                double d = CoordTransform.haversineM(lastPoint.lat, lastPoint.lng, lat, lng);
                if (d > 3) { positions.add(pt); sessionDist += d; lastPoint = pt; }
            } else { positions.add(pt); lastPoint = pt; }
            if (positions.size() > 20000) positions.remove(0);

            for (Callback cb : callbacks)
                cb.onLocation(lat, lng, speed, altitude, accuracy, heading);
        }
    };

    // ---- Sensor Listener ----
    @Override public void onSensorChanged(SensorEvent e) {
        switch (e.sensor.getType()) {
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
}
