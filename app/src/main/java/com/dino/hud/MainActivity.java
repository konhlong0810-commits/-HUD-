package com.dino.hud;

import android.Manifest;
import android.content.*;
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
    private ApiClient api; private SessionManager sm;
    private LocationManager lm;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        try {
            setContentView(R.layout.activity_main);
            tvUser = findViewById(R.id.tvUser);
            tvSpeed = findViewById(R.id.tvSpeed);
            tvGps = findViewById(R.id.tvGps);

            String un = getIntent().getStringExtra("username");
            String dn = getIntent().getStringExtra("display_name");
            tvUser.setText("用户: " + (dn != null ? dn : un));

            api = new ApiClient();
            api.setSessionId(getIntent().getStringExtra("session_id"));
            sm = new SessionManager(this);
            lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            findViewById(R.id.btnLogout).setOnClickListener(v -> {
                sm.logout();
                startActivity(new Intent(MainActivity.this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); });

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) startGps();
            else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERM_REQ);
        } catch (Throwable e) {
            Toast.makeText(this, "启动失败: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            sm.logout();
            startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        }
    }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        super.onRequestPermissionsResult(c, p, r);
        if (c == PERM_REQ && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) startGps();
    }

    @SuppressWarnings("MissingPermission")
    private void startGps() {
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.5f, gpsL, Looper.getMainLooper()); }
        catch (Exception e) { tvGps.setText("GPS失败: "+e.getMessage()); }
    }

    private final LocationListener gpsL = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            double spd = l.hasSpeed() ? l.getSpeed() * 3.6 : 0;
            String info = String.format("速度: %.0f km/h  精度: %.0fm", spd, l.hasAccuracy()?l.getAccuracy():99);
            tvSpeed.setText(String.valueOf(Math.round(spd)));
            tvGps.setText(String.format("%.5f,%.5f\n%s", l.getLatitude(), l.getLongitude(), info));
        }
    };

    @Override protected void onDestroy() {
        super.onDestroy();
        try { if (lm != null) lm.removeUpdates(gpsL); } catch (Exception ignored) {}
    }

    @Override public void onBackPressed() {
        new AlertDialog.Builder(MainActivity.this).setMessage("退出恐龙HUD？")
            .setPositiveButton("退出", (d,i)->{ sm.logout();
                startActivity(new Intent(MainActivity.this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); })
            .setNegativeButton("取消", null).show();
    }
}
