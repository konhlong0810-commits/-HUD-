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

    // UI
    private SpeedGaugeView speedGauge;
    private TextView tvCoord, tvGpsAcc, tvAlt, tvAccXYZ, tvGyroXYZ, tvPressure;
    private TextView tvHeadingBig, tvDist, tvDur, tvTrailCount, tvUser, gpsBadge;
    private TextView navHud, navHistory, navRank;

    // Services
    private LocationService locService;
    private ApiClient api;
    private SessionManager sessionMgr;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean bound = false;

    // State
    private String username, displayName, sessionId;
    private double speed, lat, lng, altitude, accuracy = 99, heading;
    private float accX, accY, accZ, gyroX, gyroY, gyroZ, hPa;
    private long sessionStart;
    private long lastUpdateMs;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        username = getIntent().getStringExtra("username");
        displayName = getIntent().getStringExtra("display_name");
        sessionId = getIntent().getStringExtra("session_id");

        api = new ApiClient();
        api.setSessionId(sessionId);
        sessionMgr = new SessionManager(this);
        sessionStart = System.currentTimeMillis();

        // Bind UI
        speedGauge = findViewById(R.id.speedGauge);
        tvCoord = findViewById(R.id.tvCoord);
        tvGpsAcc = findViewById(R.id.tvGpsAcc);
        tvAlt = findViewById(R.id.tvAlt);
        tvAccXYZ = findViewById(R.id.tvAccXYZ);
        tvGyroXYZ = findViewById(R.id.tvGyroXYZ);
        tvPressure = findViewById(R.id.tvPressure);
        tvHeadingBig = findViewById(R.id.tvHeadingBig);
        tvDist = findViewById(R.id.tvDist);
        tvDur = findViewById(R.id.tvDur);
        tvTrailCount = findViewById(R.id.tvTrailCount);
        tvUser = findViewById(R.id.tvUser);
        gpsBadge = findViewById(R.id.gpsBadge);
        navHud = findViewById(R.id.navHud);
        navHistory = findViewById(R.id.navHistory);
        navRank = findViewById(R.id.navRank);

        tvUser.setText(displayName != null ? displayName : username);

        navHistory.setOnClickListener(v -> showHistory());
        navRank.setOnClickListener(v -> showLeaderboard());

        // Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION}, PERM_REQ);
        } else {
            initService();
        }

        // Periodic UI update (1Hz) + session save (10s)
        handler.postDelayed(new Runnable() {
            int tick = 0;
            @Override public void run() {
                tick++;
                updateSensorPanel();
                if (tick % 10 == 0) saveSessionIfNeeded();
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == PERM_REQ) initService();
    }

    private void initService() {
        Intent si = new Intent(this, LocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(si);
        else startService(si);
        bindService(si, svcConn, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection svcConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder b) {
            locService = ((LocationService.LocalBinder) b).getService();
            bound = true;
            locService.registerCallback(locationCallback);
            gpsBadge.setText("定位中");
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { bound = false; locService = null; }
    };

    private final LocationService.Callback locationCallback = new LocationService.Callback() {
        @Override
        public void onLocation(double la, double ln, double spd, double alt, double acc, double hdg) {
            lat = la; lng = ln; speed = spd; altitude = alt; accuracy = acc; heading = hdg;
            lastUpdateMs = System.currentTimeMillis();
            runOnUiThread(() -> {
                speedGauge.setSpeed(spd);
                gpsBadge.setText("定位中");
            });
        }
        @Override
        public void onAccel(float x, float y, float z) {
            accX = x; accY = y; accZ = z;
        }
        @Override
        public void onGyro(float x, float y, float z) {
            gyroX = x; gyroY = y; gyroZ = z;
        }
        @Override
        public void onPressure(float p) {
            hPa = p;
        }
    };

    private void updateSensorPanel() {
        tvCoord.setText(String.format(Locale.US, "%.6f, %.6f", lat, lng));
        tvGpsAcc.setText(String.format(Locale.US, "%.1f m", accuracy));
        tvAlt.setText(String.format(Locale.US, "%.1f m", altitude));
        tvAccXYZ.setText(String.format(Locale.US, "X:%.2f Y:%.2f Z:%.2f g", accX, accY, accZ));
        tvGyroXYZ.setText(String.format(Locale.US, "X:%.3f Y:%.3f Z:%.3f rad/s", gyroX, gyroY, gyroZ));
        tvPressure.setText(String.format(Locale.US, "%.1f hPa", hPa));
        tvHeadingBig.setText(String.format(Locale.US, "%.0f°", heading));

        // Mileage
        if (bound && locService != null) {
            double distM = locService.getSessionDist();
            if (distM < 1000) tvDist.setText(String.format(Locale.US, "%.0f m", distM));
            else tvDist.setText(String.format(Locale.US, "%.1f km", distM / 1000));
            long durSec = (System.currentTimeMillis() - sessionStart) / 1000;
            if (durSec < 60) tvDur.setText(durSec + "秒");
            else if (durSec < 3600) tvDur.setText((durSec / 60) + "分");
            else tvDur.setText(String.format(Locale.US, "%.1f时", durSec / 3600.0));
            tvTrailCount.setText(locService.getPositions().size() + " 点");
        }

        // GPS stale check
        long since = System.currentTimeMillis() - lastUpdateMs;
        if (since > 10000) gpsBadge.setText("无信号");
    }

    // ==================== History ====================
    private void showHistory() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("历史记录");
        new Thread(() -> {
            try {
                List<TrailSession> sessions = api.getSessions();
                runOnUiThread(() -> {
                    if (sessions.isEmpty()) {
                        b.setMessage("暂无记录").setPositiveButton("关闭", null).show();
                        return;
                    }
                    String[] items = new String[sessions.size()];
                    for (int i = 0; i < sessions.size(); i++) {
                        TrailSession ts = sessions.get(i);
                        Date st = new Date(ts.startTime);
                        double km = ts.distance / 1000;
                        long dur = (ts.endTime - ts.startTime) / 1000;
                        String durStr = dur < 60 ? dur + "秒" : dur < 3600 ?
                            (dur / 60) + "分" : String.format(Locale.US, "%.1f时", dur / 3600.0);
                        items[i] = String.format(Locale.US,
                            "%tm/%td %tH:%tM  %.1fkm  %s", st, st, st, st, km, durStr);
                    }
                    b.setItems(items, (d, i) -> {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("操作").setMessage("删除这条记录？")
                            .setPositiveButton("删除", (dd, ii) -> deleteSession(sessions.get(i)))
                            .setNegativeButton("取消", null).show();
                    });
                    b.setNegativeButton("关闭", null).show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void deleteSession(TrailSession ts) {
        new Thread(() -> { try { api.deleteSession(ts.id); } catch (IOException e) {} }).start();
    }

    // ==================== Leaderboard ====================
    private void showLeaderboard() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("排行榜");
        new Thread(() -> {
            try {
                List<ApiClient.RankEntry> list = api.getLeaderboard();
                runOnUiThread(() -> {
                    if (list.isEmpty()) {
                        b.setMessage("暂无数据").setPositiveButton("关闭", null).show();
                        return;
                    }
                    list.sort((a, b2) -> Double.compare(b2.total, a.total));
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < list.size() && i < 20; i++) {
                        var e = list.get(i);
                        String medal = i == 0 ? " " : i == 1 ? " " : i == 2 ? " " : (i + 1) + ".";
                        double km = e.total / 1000;
                        sb.append(String.format(Locale.US, "%s %s  %.1fkm\n",
                            medal, e.display_name != null ? e.display_name : e.username, km));
                    }
                    b.setMessage(sb.toString()).setPositiveButton("关闭", null).show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ==================== Session Save ====================
    private void saveSessionIfNeeded() {
        if (!bound || locService == null) return;
        List<TrailPoint> pts = locService.getPositions();
        if (pts.size() < 2) return;
        double dist = locService.getSessionDist();
        long endTime = System.currentTimeMillis();
        long dur = (endTime - sessionStart) / 1000;
        new Thread(() -> {
            try {
                api.saveSession(sessionStart, endTime, dist, pts);
                api.saveTrail(pts, dist, dur);
            } catch (IOException ignored) {}
        }).start();
    }

    // ==================== Lifecycle ====================
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound && locService != null) {
            locService.unregisterCallback(locationCallback);
            unbindService(svcConn);
        }
        saveSessionIfNeeded();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setMessage("退出恐龙HUD？")
            .setPositiveButton("退出", (d, i) -> {
                saveSessionIfNeeded();
                sessionMgr.logout();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            })
            .setNegativeButton("取消", null).show();
    }
}
