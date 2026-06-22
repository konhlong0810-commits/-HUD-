package com.dino.hud;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dino.hud.api.ApiClient;
import com.dino.hud.utils.SessionManager;

public class MainActivity extends AppCompatActivity {
    private static final int PERM_REQ = 200;
    private TextView tvUser, tvSpeed, tvGps;
    private LocationManager lm;
    private ApiClient api;
    private SessionManager sm;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        tvUser = findViewById(R.id.tvUser);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvGps = findViewById(R.id.tvGps);

        String un = getIntent().getStringExtra("username");
        String dn = getIntent().getStringExtra("display_name");
        String sid = getIntent().getStringExtra("session_id");
        tvUser.setText("用户: " + (dn != null ? dn : un));

        api = new ApiClient();
        api.setSessionId(sid);
        sm = new SessionManager(this);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        findViewById(R.id.btnTest).setOnClickListener(v ->
            tvGps.setText("按钮正常! " + System.currentTimeMillis() % 100000));

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            sm.logout();
            startActivity(new Intent(MainActivity.this, LoginActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startGps();
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERM_REQ);
        }
    }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        super.onRequestPermissionsResult(c, p, r);
        if (c == PERM_REQ && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) startGps();
    }

    @SuppressWarnings("MissingPermission")
    private void startGps() {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.5f, gpsListener, Looper.getMainLooper());
            tvGps.setText("GPS 搜索中...");
        } catch (Exception e) { tvGps.setText("GPS 启动失败: " + e.getMessage()); }
    }

    private final LocationListener gpsListener = new LocationListener() {
        @Override public void onLocationChanged(Location loc) {
            double spd = loc.hasSpeed() ? loc.getSpeed() * 3.6 : 0;
            String coord = String.format("%.5f,%.5f", loc.getLatitude(), loc.getLongitude());
            String info = String.format("速度: %.0f km/h  精度: %.0fm  海拔: %.0fm",
                spd, loc.hasAccuracy() ? loc.getAccuracy() : 99,
                loc.hasAltitude() ? loc.getAltitude() : 0);
            tvSpeed.setText(String.valueOf(Math.round(spd)));
            tvGps.setText(coord + "\n" + info);
        }
    };

    @Override protected void onDestroy() {
        super.onDestroy();
        try { if (lm != null) lm.removeUpdates(gpsListener); } catch (Exception ignored) {}
    }

    @Override public void onBackPressed() {
        new AlertDialog.Builder(MainActivity.this)
            .setMessage("退出恐龙HUD？")
            .setPositiveButton("退出", (d,i) -> {
                sm.logout();
                startActivity(new Intent(MainActivity.this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                finish();
            })
            .setNegativeButton("取消", null).show();
    }
}
