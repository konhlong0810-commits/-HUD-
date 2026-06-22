package com.dino.hud;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.*;
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
    private ApiClient api; private SessionManager sm;
    private LocationManager lm; private SensorManager srm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private long sessionStart, saveTick;

    // GPS
    private double lat, lng, kmh, alt, acc, heading; private long lastGpsMs;
    // Sensor cache (thread-safe via main-thread-only read)
    private float ax, ay, az, gx, gy, gz, hPa;
    // Trail
    private final List<TrailPoint> trail = new ArrayList<>();
    private TrailPoint lastPt; private double dist;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        tvUser = findViewById(R.id.tvUser); gpsBadge = findViewById(R.id.gpsBadge);
        tvSpeed = findViewById(R.id.tvSpeed); tvCoord = findViewById(R.id.tvCoord);
        tvGpsInfo = findViewById(R.id.tvGpsInfo); tvAccel = findViewById(R.id.tvAccel);
        tvGyro = findViewById(R.id.tvGyro); tvPressure = findViewById(R.id.tvPressure);
        tvHeading = findViewById(R.id.tvHeading); tvDist = findViewById(R.id.tvDist);
        tvDur = findViewById(R.id.tvDur);

        String un = getIntent().getStringExtra("username");
        String dn = getIntent().getStringExtra("display_name");
        tvUser.setText(dn != null ? dn : un);

        api = new ApiClient();
        api.setSessionId(getIntent().getStringExtra("session_id"));
        sm = new SessionManager(this);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        srm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sessionStart = System.currentTimeMillis();

        findViewById(R.id.btnLogout).setOnClickListener(v -> { sm.logout();
            startActivity(new Intent(MainActivity.this, LoginActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); });
        findViewById(R.id.btnHistory).setOnClickListener(v -> showHistory());
        findViewById(R.id.btnRank).setOnClickListener(v -> showLeaderboard());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) startGps();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERM_REQ);
        startSensors();
        h.postDelayed(tick, 1000);
    }

    private final Runnable tick = new Runnable() { @Override public void run() {
        if (isFinishing() || isDestroyed()) return;
        updateUI();
        h.postDelayed(this, 1000);
    }};

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        super.onRequestPermissionsResult(c, p, r);
        if (c == PERM_REQ && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) startGps();
    }

    // ==================== GPS ====================

    @SuppressWarnings("MissingPermission")
    private void startGps() {
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.5f, gpsL, Looper.getMainLooper()); }
        catch (Exception e) { gpsBadge.setText("GPS失败"); }
    }

    private final LocationListener gpsL = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            lat = l.getLatitude(); lng = l.getLongitude();
            kmh = l.hasSpeed() ? l.getSpeed() * 3.6 : 0;
            alt = l.hasAltitude() ? l.getAltitude() : 0;
            acc = l.hasAccuracy() ? l.getAccuracy() : 99;
            if (l.hasBearing() && l.getBearing() > 0) heading = l.getBearing();
            lastGpsMs = System.currentTimeMillis();
            TrailPoint pt = new TrailPoint(); pt.lat = lat; pt.lng = lng;
            if (lastPt != null) { double d = CoordTransform.haversineM(lastPt.lat, lastPt.lng, lat, lng);
                if (d > 3) { trail.add(pt); dist += d; lastPt = pt; } }
            else { trail.add(pt); lastPt = pt; }
        }
    };

    // ==================== Sensors ====================

    private void startSensors() {
        if (srm == null) return;
        try {
            Sensor a = srm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor g = srm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            Sensor p = srm.getDefaultSensor(Sensor.TYPE_PRESSURE);
            if (a != null) srm.registerListener(sensL, a, SensorManager.SENSOR_DELAY_GAME);
            if (g != null) srm.registerListener(sensL, g, SensorManager.SENSOR_DELAY_GAME);
            if (p != null) srm.registerListener(sensL, p, SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception ignored) {}
    }

    private final SensorEventListener sensL = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            switch (e.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER: ax=e.values[0]; ay=e.values[1]; az=e.values[2]; break;
                case Sensor.TYPE_GYROSCOPE:     gx=e.values[0]; gy=e.values[1]; gz=e.values[2]; break;
                case Sensor.TYPE_PRESSURE:      hPa=e.values[0]; break;
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int a) {}
    };

    // ==================== UI ====================

    private void updateUI() {
        tvSpeed.setText(String.valueOf(Math.round(kmh)));
        if (lastGpsMs > 0) {
            tvCoord.setText(String.format(Locale.US, "坐标: %.6f, %.6f", lat, lng));
            tvGpsInfo.setText(String.format(Locale.US, "精度:%.0fm  海拔:%.0fm  航向:%.0f°", acc, alt, heading));
            gpsBadge.setText(System.currentTimeMillis() - lastGpsMs < 5000 ? " 定位中" : " 弱信号");
        }
        tvAccel.setText(String.format(Locale.US, "加速度: X=%.2f Y=%.2f Z=%.2f", ax, ay, az));
        tvGyro.setText(String.format(Locale.US, "陀螺仪: X=%.3f Y=%.3f Z=%.3f", gx, gy, gz));
        tvPressure.setText(String.format(Locale.US, "气压: %.1f hPa", hPa));
        tvHeading.setText(String.format(Locale.US, "航向: %.0f°", heading));

        long dur = (System.currentTimeMillis() - sessionStart) / 1000;
        tvDist.setText(String.format(Locale.US, "里程: %s", dist < 1000 ? String.format("%.0f m", dist) : String.format("%.1f km", dist/1000)));
        tvDur.setText(String.format(Locale.US, "时长: %s", dur < 60 ? dur+"秒" : dur < 3600 ? (dur/60)+"分" : String.format("%.1f时", dur/3600.0)));
        if (dur - saveTick >= 10) { saveTick = dur; saveSession(); }
    }

    private void saveSession() {
        if (trail.size() < 2) return;
        final List<TrailPoint> pts = new ArrayList<>(trail);
        final double d = dist;
        final long st = sessionStart, et = System.currentTimeMillis();
        new Thread(() -> { try { api.saveSession(st, et, d, pts); api.saveTrail(pts, d, (et-st)/1000); } catch (Exception ignored) {} }).start();
    }

    // ==================== Dialogs ====================

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
                        TrailSession ts = list.get(i); Date st = new Date(ts.startTime);
                        items[i] = String.format(Locale.US, "%tm/%td %tH:%tM  %.1fkm  %ds",
                            st,st,st,st, ts.distance/1000, (ts.endTime-ts.startTime)/1000);
                    }
                    b.setItems(items, (d,i) -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("删除?").setMessage(items[i])
                        .setPositiveButton("删除", (dd,ii)->new Thread(()->{
                            try{api.deleteSession(list.get(i).id);}catch(Exception ignored){} }).start())
                        .setNegativeButton("取消",null).show()
                    ).setNegativeButton("关闭",null).show();
                });
            } catch (Exception e) { runOnUiThread(()->Toast.makeText(MainActivity.this,"加载失败",Toast.LENGTH_SHORT).show()); }
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
                        var e = list.get(i); String m = i==0?" ":i==1?" ":i==2?" ":(i+1)+".";
                        sb.append(String.format(Locale.US,"%s %s  %.1fkm\n",m,
                            e.display_name!=null?e.display_name:e.username, e.total/1000));
                    }
                    b.setMessage(sb.toString()).setPositiveButton("关闭",null).show();
                });
            } catch (Exception e) { runOnUiThread(()->Toast.makeText(MainActivity.this,"加载失败",Toast.LENGTH_SHORT).show()); }
        }).start();
    }

    // ==================== Lifecycle ====================

    @Override protected void onDestroy() {
        super.onDestroy(); h.removeCallbacks(tick);
        try { lm.removeUpdates(gpsL); } catch (Exception ignored) {}
        try { srm.unregisterListener(sensL); } catch (Exception ignored) {}
        saveSession();
    }

    @Override public void onBackPressed() {
        new AlertDialog.Builder(MainActivity.this).setMessage("退出恐龙HUD？")
            .setPositiveButton("退出", (d,i)->{ saveSession(); sm.logout();
                startActivity(new Intent(MainActivity.this,LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); })
            .setNegativeButton("取消",null).show();
    }
}
