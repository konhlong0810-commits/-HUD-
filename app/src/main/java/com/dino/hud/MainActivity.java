package com.dino.hud;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dino.hud.api.ApiClient;
import com.dino.hud.models.TrailPoint;
import com.dino.hud.models.TrailSession;
import com.dino.hud.services.LocationService;
import com.dino.hud.utils.*;
import com.dino.hud.views.SpeedGaugeView;

import java.io.IOException;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final int PERM_REQ = 200;

    private SpeedGaugeView speedGauge;
    private TextView tvUser, gpsBadge, tvCoord, tvGpsInfo, tvAccel, tvGyro, tvPressure, tvHeading, tvDist, tvDur;

    private LocationService locService;
    private ApiClient api;
    private SessionManager sm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { @Override public void run() {
        if (isFinishing() || isDestroyed()) return;
        updateUI();
        h.postDelayed(this, 1000);
    }};
    private boolean bound;
    private long saveTick;

    private double lat, lng, speedKmh, alt, acc, heading;
    private float ax, ay, az, gx, gy, gz, hPa;
    private long sessionStart, lastGpsMs;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        speedGauge = findViewById(R.id.speedGauge);
        tvUser = findViewById(R.id.tvUser);
        gpsBadge = findViewById(R.id.gpsBadge);
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

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            sm.logout();
            startActivity(new Intent(MainActivity.this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        findViewById(R.id.btnHistory).setOnClickListener(v -> showHistory());
        findViewById(R.id.btnRank).setOnClickListener(v -> showLeaderboard());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            bindLocService();
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERM_REQ);
        }

        h.postDelayed(tick, 1000);
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == PERM_REQ && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            bindLocService();
        }
    }

    // ==================== Service ====================

    private void bindLocService() {
        try {
            Intent si = new Intent(this, LocationService.class);
            startService(si);
            bindService(si, sc, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            gpsBadge.setText("GPS启动失败");
        }
    }

    private final ServiceConnection sc = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            locService = ((LocationService.LocalBinder) b).getService();
            bound = true;
            locService.registerCallback(cb);
            gpsBadge.setText("搜索中");
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            bound = false; locService = null;
            gpsBadge.setText("离线");
        }
    };

    private final LocationService.Callback cb = new LocationService.Callback() {
        @Override public void onLocation(double la, double ln, double spd, double al, double ac, double hdg) {
            lat = la; lng = ln; speedKmh = spd; alt = al; acc = ac; heading = hdg;
            lastGpsMs = System.currentTimeMillis();
        }
        @Override public void onAccel(float x, float y, float z) { ax = x; ay = y; az = z; }
        @Override public void onGyro(float x, float y, float z) { gx = x; gy = y; gz = z; }
        @Override public void onPressure(float p) { hPa = p; }
    };

    // ==================== UI Update ====================

    private void updateUI() {
        if (lastGpsMs > 0) {
            speedGauge.setSpeed(speedKmh);
            tvCoord.setText(String.format(Locale.US, "%.6f,%.6f", lat, lng));
            tvGpsInfo.setText(String.format(Locale.US, " %.1fm  %.1fm", acc, alt));
            tvHeading.setText(String.format(Locale.US, "%.0f ", heading));
            long since = System.currentTimeMillis() - lastGpsMs;
            gpsBadge.setText(since < 5000 ? " 定位中" : " 弱信号");
        }
        tvAccel.setText(String.format(Locale.US, "X:%.2f Y:%.2f Z:%.2f", ax, ay, az));
        tvGyro.setText(String.format(Locale.US, "X:%.3f Y:%.3f Z:%.3f", gx, gy, gz));
        tvPressure.setText(String.format(Locale.US, "%.1f hPa", hPa));

        if (bound && locService != null) {
            double dist = locService.getSessionDist();
            tvDist.setText(dist < 1000 ? String.format(Locale.US, "%.0f m", dist) : String.format(Locale.US, "%.1f km", dist/1000));
            long dur = (System.currentTimeMillis() - sessionStart) / 1000;
            tvDur.setText(dur < 60 ? dur+"秒" : dur < 3600 ? (dur/60)+"分" : String.format(Locale.US, "%.1f时", dur/3600.0));
            if (dur - saveTick >= 10) { saveTick = dur; saveSession(); }
        }
    }

    private void saveSession() {
        if (locService == null) return;
        final List<TrailPoint> pts = locService.getPositions();
        if (pts.size() < 2) return;
        final double dist = locService.getSessionDist();
        final long start = sessionStart;
        final long end = System.currentTimeMillis();
        new Thread(() -> {
            try {
                api.saveSession(start, end, dist, pts);
                api.saveTrail(pts, dist, (end-start)/1000);
            } catch (Exception ignored) {}
        }).start();
    }

    // ==================== History ====================

    private void showHistory() {
        final AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
        b.setTitle(" 历史记录");
        new Thread(() -> {
            try {
                final List<TrailSession> list = api.getSessions();
                runOnUiThread(() -> {
                    if (list.isEmpty()) { b.setMessage("暂无记录").setPositiveButton("关闭",null).show(); return; }
                    String[] items = new String[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        TrailSession ts = list.get(i);
                        Date st = new Date(ts.startTime);
                        double km = ts.distance/1000;
                        long dur = (ts.endTime-ts.startTime)/1000;
                        String ds = dur<60?dur+"秒":dur<3600?(dur/60)+"分":String.format(Locale.US,"%.1f时",dur/3600.0);
                        items[i] = String.format(Locale.US,"%tm/%td %tH:%tM %.1fkm %s",st,st,st,st,km,ds);
                    }
                    b.setItems(items, (d,i) -> {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("操作").setMessage("删除这条记录？")
                            .setPositiveButton("删除", (dd,ii) -> new Thread(()->{
                                try{api.deleteSession(list.get(i).id);}catch(Exception ignored){}
                            }).start())
                            .setNegativeButton("取消",null).show();
                    }).setNegativeButton("关闭",null).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ==================== Leaderboard ====================

    private void showLeaderboard() {
        final AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
        b.setTitle(" 排行榜");
        new Thread(() -> {
            try {
                final List<ApiClient.RankEntry> list = api.getLeaderboard();
                runOnUiThread(() -> {
                    if (list.isEmpty()) { b.setMessage("暂无数据").setPositiveButton("关闭",null).show(); return; }
                    list.sort((a,b2)->Double.compare(b2.total,a.total));
                    StringBuilder sb = new StringBuilder();
                    for (int i=0;i<list.size()&&i<20;i++) {
                        var e = list.get(i);
                        String medal = i==0?" ":i==1?" ":i==2?" ":(i+1)+".";
                        sb.append(String.format(Locale.US,"%s %s %.1fkm\n",medal,
                            e.display_name!=null?e.display_name:e.username,e.total/1000));
                    }
                    b.setMessage(sb.toString()).setPositiveButton("关闭",null).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ==================== Lifecycle ====================

    @Override protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacks(tick);
        saveSession();
        if (bound && locService != null) {
            locService.unregisterCallback(cb);
            unbindService(sc);
        }
    }

    @Override public void onBackPressed() {
        new AlertDialog.Builder(MainActivity.this).setMessage("退出恐龙HUD？")
            .setPositiveButton("退出", (d,i)->{ saveSession(); sm.logout();
                startActivity(new Intent(MainActivity.this,LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                finish(); })
            .setNegativeButton("取消",null).show();
    }
}
