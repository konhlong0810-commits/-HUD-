package com.dino.hud;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.dino.hud.api.ApiClient;
import com.dino.hud.utils.SessionManager;

public class MainActivity extends AppCompatActivity {
    private ApiClient api;
    private SessionManager sm;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        Button btnTest = findViewById(R.id.btnTest);
        Button btnLogout = findViewById(R.id.btnLogout);

        String un = getIntent().getStringExtra("username");
        String dn = getIntent().getStringExtra("display_name");
        String sid = getIntent().getStringExtra("session_id");

        api = new ApiClient();
        api.setSessionId(sid);
        sm = new SessionManager(this);

        tvStatus.setText("用户: " + (dn != null ? dn : un) + "\nSession: " + (sid != null ? "OK" : "无"));

        btnTest.setOnClickListener(v -> {
            tvStatus.setText("按钮正常！\n点击时间: " + System.currentTimeMillis() % 100000);
        });

        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setMessage("确定退出？")
                .setPositiveButton("退出", (d, i) -> {
                    sm.logout();
                    startActivity(new Intent(this, LoginActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    finish();
                })
                .setNegativeButton("取消", null).show();
        });
    }
}
