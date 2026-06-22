package com.dino.hud;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.*;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dino.hud.api.ApiClient;
import com.dino.hud.models.TrailPoint;
import com.dino.hud.models.TrailSession;
import com.dino.hud.utils.*;

import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final int PERM_REQ = 200;

    private TextView tvUser, gpsBadge, tvSpeed, tvCoord, tvGpsInfo, tvAccel, tvGyro, tvPressure, tvHeading, tvDist, tvDur;

    private ApiClient api;
    private SessionManager sm;
    private final Handler h = new Handler(Looper.getMainLooper());

    // GPS
    private LocationManager lm;
    private double lat, lng, speedKmh, alt = 0, accuracy = 99, heading;
    private long lastGpsMs;

    // Sensors
    private SensorManager srm;
    private float ax, ay, az, gx, gy, gz, hPa;
    private final float[] accCache = new float[3], gyrCache = new float[3];
    private float pressCache;

    // Session
    private final List<TrailPoint> trail = new ArrayList<>();
    private double dist;
    private TrailPoint lastPt;
    private long sessionStart, saveTick;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        tvUser = findViewById(R.id.tvUser);
        gpsBadge = findViewById(R.id.gpsBadge);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvCoord = findViewById(R.id.tvCoord);
        tvGpsInfo = findViewById(R.id.tvGpsInfo);
        tvAccel = findViewById(R.id.tvAccel);
        tvGyro = findViewById(R.id.tvGyro);
        tvPressure = findViewById(R.id.tvPressure);
        tvHeading = findViewById(R.id.tvHeading);
        tvDist = findViewById(R.id.tvDist);
        tvDur = findViewById(R.id.tvDur);

        String un = getIntent().getStringExtra("username");
        String dn = getIntent().getStringExtra("display_name");
        String sid = getIntent().getStringExtra("session_id");
        tvUser.setText(dn != null ? dn : un);

        api = new ApiClient();
        api.setSessionId(sid);
        sm = new SessionManager(this);
        sessionStart = System.currentTimeMillis();

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        srm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            sm.logout();
            startActivity(new Intent(MainActivity.this, LoginActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        findViewById(R.id.btnHistory).setOnClickListener(v -> showHistory());
        findViewById(R.id.btnRank).setOnClickListener(v -> showLeaderboard());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startGps();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERM_REQ);
        }
        startSensors();

        h.postDelayed(tick, 1000);
    }

    private final Runnable tick = new Runnable() { @Override public void run() {
        if (isFinishing() || isDestroyed()) return;
        updateUI();
        h.postDelayed(this, 1000);
    }};

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == PERM_REQ && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) startGps();
    }

    @SuppressWarnings("MissingPermission")
    private void startGps() {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.5f, gpsListener, Looper.getMainLooper());
            gpsBadge.setText("搜索中");
        } catch (Exception e) { gpsBadge.setText("GPS失败"); }
    }

    private void startSensors() {
        try {
            if (srm != null) {
                Sensor a = srm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                Sensor g = srm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                Sensor p = srm.getDefaultSensor(Sensor.TYPE_PRESSURE);
                if (a != null) srm.registerListener(sensorListener, a, SensorManager.SENSOR_DELAY_GAME);
                if (g != null) srm.registerListener(sensorListener, g, SensorManager.SENSOR_DELAY_GAME);
                if (p != null) srm.registerListener(sensorListener, p, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } catch (Exception ignored) {}
    }

    private final LocationListener gpsListener = new LocationListener() {
        @Override public void onLocationChanged(Location loc) {
            lat = loc.getLatitude(); lng = loc.getLongitude();
            speedKmh = loc.hasSpeed() ? loc.getSpeed() * 3.6 : 0;
            alt = loc.hasAltitude() ? loc.getAltitude() : 0;
            accuracy = loc.hasAccuracy() ? loc.getAccuracy() : 99;
            if (loc.hasBearing() && loc.getBearing() > 0) heading = loc.getBearing();
            lastGpsMs = System.currentTimeMillis();

            TrailPoint pt = new TrailPoint(); pt.lat = lat; pt.lng = lng;
            if (lastPt != null) {
                double d = CoordTransform.haversineM(lastPt.lat, lastPt.lng, lat, lng);
                if (d > 3) { trail.add(pt); dist += d; lastPt = pt; }
            } else { trail.add(pt); lastPt = pt; }
        }
    };

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            switch (e.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER: accCache[0]=e.values[0]; accCache[1]=e.values[1]; accCache[2]=e.values[2]; break;
                case Sensor.TYPE_GYROSCOPE: gyrCache[0]=e.values[0]; gyrCache[1]=e.values[1]; gyrCache[2]=e.values[2]; break;
                case Sensor.TYPE_PRESSURE: pressCache=e.values[0]; break;
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void updateUI() {
        ax = accCache[0]; ay = accCache[1]; az = accCache[2];
        gx = gyrCache[0]; gy = gyrCache[1]; gz = gyrCache[2];
        hPa = pressCache;

        if (lastGpsMs > 0) {
            tvSpeed.setText(String.valueOf(Math.round(speedKmh)));
            tvCoord.setText(String.format(Locale.US, "%.6f,%.6f", lat, lng));
            tvGpsInfo.setText(String.format(Locale.US, " %.0fm  %.0fm", accuracy, alt));
            tvHeading.setText(String.format(Locale.US, "%.0f ", heading));
            long since = System.currentTimeMillis() - lastGpsMs;
            gpsBadge.setText(since < 5000 ? " 定位中" : " 弱信号");
        }
        tvAccel.setText(String.format(Locale.US, "X:%.2f Y:%.2f Z:%.2f", ax, ay, az));
        tvGyro.setText(String.format(Locale.US, "X:%.3f Y:%.3f Z:%.3f", gx, gy, gz));
        tvPressure.setText(String.format(Locale.US, "%.1f hPa", hPa));

        tvDist.setText(dist < 1000 ? String.format(Locale.US, "%.0f m", dist) : String.format(Locale.US, "%.1f km", dist/1000));
        long dur = (System.currentTimeMillis() - sessionStart) / 1000;
        tvDur.setText(dur < 60 ? dur+"秒" : dur < 3600 ? (dur/60)+"分" : String.format(Locale.US, "%.1f时", dur/3600.0));
        if (dur - saveTick >= 10) { saveTick = dur; saveSession(); }
    }

    private void saveSession() {
        if (trail.size() < 2) return;
        final List<TrailPoint> pts = new ArrayList<>(trail);
        final double d = dist;
        final long start = sessionStart;
        final long end = System.currentTimeMillis();
        new Thread(() -> {
            try { api.saveSession(start, end, d, pts); api.saveTrail(pts, d, (end-start)/1000); }
            catch (Exception ignored) {}
        }).start();
    }

    private void showHistory() {
        AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
        b.setTitle(" 历史记录");
        new Thread(() -> {
            try {
                List<TrailSession> list = api.getSessions();
                runOnUiThread(() -> {
                    if (list.isEmpty()) { b.setMessage("暂无记录").setPositiveButton("关闭",null).show(); return; }
                    String[] items = new String[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        TrailSession ts = list.get(i);
                        Date st = new Date(ts.startTime);
                        double km = ts.distance/1000;
                        long dms = (ts.endTime-ts.startTime)/1000;
                        String ds = dms<60?dms+"秒":dms<3600?(dms/60)+"分":String.format(Locale.US,"%.1f时",dms/3600.0);
                        items[i] = String.format(Locale.US,"%tm/%td %tH:%tM %.1fkm %s",st,st,st,st,km,ds);
                    }
                    b.setItems(items, (d,i) -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("操作").setMessage("删除这条记录？")
                        .setPositiveButton("删除", (dd,ii) -> new Thread(()->{
                            try{api.deleteSession(list.get(i).id);}catch(Exception ignored){}
                        }).start()).setNegativeButton("取消",null).show()
                    ).setNegativeButton("关闭",null).show();
                });
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "加载失败", Toast.LENGTH_SHORT).show()); }
        }).start();
    }

    private void showLeaderboard() {
        AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
        b.setTitle(" 排行榜");
        new Thread(() -> {
            try {
                List<ApiClient.RankEntry> list = api.getLeaderboard();
                runOnUiThread(() -> {
                    if (list.isEmpty()) { b.setMessage("暂无数据").setPositiveButton("关闭",null).show(); return; }
                    list.sort((a,b2)->Double.compare(b2.total,a.total));
                    StringBuilder sb = new StringBuilder();
                    for (int i=0;i<list.size()&&i<20;i++) {
                        var e = list.get(i);
                        sb.append(String.format(Locale.US,"%s %s %.1fkm\n",
                            i==0?" ":i==1?" ":i==2?" ":(i+1)+".",
                            e.display_name!=null?e.display_name:e.username, e.total/1000));
                    }
                    b.setMessage(sb.toString()).setPositiveButton("关闭",null).show();
                });
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "加载失败", Toast.LENGTH_SHORT).show()); }
        }).start();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacks(tick);
        try { if (lm != null) lm.removeUpdates(gpsListener); } catch (Exception ignored) {}
        try { if (srm != null) srm.unregisterListener(sensorListener); } catch (Exception ignored) {}
        saveSession();
    }

    @Override public void onBackPressed() {
        new AlertDialog.Builder(MainActivity.this).setMessage("退出恐龙HUD？")
            .setPositiveButton("退出", (d,i)->{ saveSession(); sm.logout();
                startActivity(new Intent(MainActivity.this,LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                finish(); })
            .setNegativeButton("取消",null).show();
    }
}
