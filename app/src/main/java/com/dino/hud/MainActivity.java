package com.dino.hud;

import android.Manifest;
import android.content.*;
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
import com.dino.hud.utils.SessionManager;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int PERM_REQ = 200;

    private TextView tvUser, tvSpeed, tvGps, tvAccel, tvGyro, tvPressure;
    private ApiClient api; private SessionManager sm;
    private LocationManager lm; private SensorManager srm;

    // Sensor data (updated from sensor thread, read on UI thread)
    private volatile float ax, ay, az, gx, gy, gz, hPa;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        try {
            setContentView(R.layout.activity_main);
            tvUser = findViewById(R.id.tvUser); tvSpeed = findViewById(R.id.tvSpeed);
            tvGps = findViewById(R.id.tvGps); tvAccel = findViewById(R.id.tvAccel);
            tvGyro = findViewById(R.id.tvGyro); tvPressure = findViewById(R.id.tvPressure);

            String un = getIntent().getStringExtra("username");
            String dn = getIntent().getStringExtra("display_name");
            tvUser.setText("用户: " + (dn != null ? dn : un));

            api = new ApiClient();
            api.setSessionId(getIntent().getStringExtra("session_id"));
            sm = new SessionManager(this);
            lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            srm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

            findViewById(R.id.btnLogout).setOnClickListener(v -> {
                sm.logout();
                startActivity(new Intent(MainActivity.this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); });

            // GPS
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) startGps();
            else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERM_REQ);

            // Sensors
            startSensors();

            // 1Hz sensor display update
            h.postDelayed(tick, 1000);
        } catch (Throwable e) {
            Toast.makeText(this, "启动失败: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            if (sm != null) sm.logout();
            startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        }
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { @Override public void run() {
        if (isFinishing() || isDestroyed()) return;
        tvAccel.setText(String.format(Locale.US, "加速度: X=%.2f Y=%.2f Z=%.2f", ax, ay, az));
        tvGyro.setText(String.format(Locale.US, "陀螺仪: X=%.3f Y=%.3f Z=%.3f", gx, gy, gz));
        tvPressure.setText(String.format(Locale.US, "气压: %.1f hPa", hPa));
        h.postDelayed(this, 1000);
    }};

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

    @Override protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacks(tick);
        try { if (lm != null) lm.removeUpdates(gpsL); } catch (Exception ignored) {}
        try { if (srm != null) srm.unregisterListener(sensL); } catch (Exception ignored) {}
    }

    @Override public void onBackPressed() {
        new AlertDialog.Builder(MainActivity.this).setMessage("退出恐龙HUD？")
            .setPositiveButton("退出", (d,i)->{ sm.logout();
                startActivity(new Intent(MainActivity.this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); })
            .setNegativeButton("取消", null).show();
    }
}
