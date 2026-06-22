package com.dino.hud;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.dino.hud.api.ApiClient;
import com.dino.hud.utils.SessionManager;

import java.io.IOException;

public class LoginActivity extends AppCompatActivity {
    private EditText etUser, etPass, etPass2;
    private TextView tvError, tabLogin, tabRegister;
    private Button btnSubmit;
    private boolean isLogin = true;
    private ApiClient api;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        try {
            setContentView(R.layout.activity_login);

            etUser = findViewById(R.id.etUsername);
            etPass = findViewById(R.id.etPassword);
            etPass2 = findViewById(R.id.etPassword2);
            tvError = findViewById(R.id.tvError);
            tabLogin = findViewById(R.id.tabLogin);
            tabRegister = findViewById(R.id.tabRegister);
            btnSubmit = findViewById(R.id.btnSubmit);

            Log.d("Login", "UI loaded OK");
        } catch (Exception e) {
            Log.e("Login", "UI crash: " + Log.getStackTraceString(e));
            Toast.makeText(this, "UI错误: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        try {
            api = new ApiClient();
            Log.d("Login", "ApiClient OK");
        } catch (Exception e) {
            Log.e("Login", "ApiClient crash: " + Log.getStackTraceString(e));
            Toast.makeText(this, "网络初始化错误", Toast.LENGTH_LONG).show();
            api = new ApiClient(); // 重试（fallback）
        }

        // 如果已有 session，直接进主页
        try {
            SessionManager sm = new SessionManager(this);
            if (sm.isLoggedIn()) {
                String username = sm.getUsername();
                api.setSessionId(sm.getSessionId());
                new Thread(() -> {
                    try {
                        var user = api.getMe();
                        if (user != null) {
                            runOnUiThread(() -> gotoMain(user.username, user.display_name));
                            return;
                        }
                    } catch (Exception e) {
                        sm.logout();
                    }
                }).start();
            }
        } catch (Exception e) {
            Log.e("Login", "Session check crash: " + Log.getStackTraceString(e));
        }

        tabLogin.setOnClickListener(v -> switchTab(true));
        tabRegister.setOnClickListener(v -> switchTab(false));
        btnSubmit.setOnClickListener(v -> doAuth());
    }

    private void switchTab(boolean login) {
        isLogin = login;
        tabLogin.setTextColor(login ? 0xFF2563EB : 0xFF94A3B8);
        tabLogin.setBackgroundColor(login ? 0xFFFFFFFF : 0x00000000);
        tabRegister.setTextColor(login ? 0xFF94A3B8 : 0xFF2563EB);
        tabRegister.setBackgroundColor(login ? 0x00000000 : 0xFFFFFFFF);
        btnSubmit.setText(login ? "登 录" : "注 册");
        etPass2.setVisibility(login ? View.GONE : View.VISIBLE);
        tvError.setText("");
    }

    private void doAuth() {
        try {
            String u = etUser.getText().toString().trim();
            String p = etPass.getText().toString();
            String p2 = etPass2.getText().toString();

            if (TextUtils.isEmpty(u) || TextUtils.isEmpty(p)) {
                tvError.setText("请填写用户名和密码"); return;
            }
            if (!isLogin && !p.equals(p2)) {
                tvError.setText("两次密码不一致"); return;
            }
            btnSubmit.setEnabled(false);
            btnSubmit.setText("请等待…");
            tvError.setText("");

            new Thread(() -> {
                try {
                    ApiClient.AuthResult r = isLogin
                        ? api.login(u, p) : api.register(u, p);
                    runOnUiThread(() -> {
                        if (r.ok && r.sessionId != null) {
                            api.setSessionId(r.sessionId);
                            new SessionManager(LoginActivity.this).saveSession(r.sessionId,
                                r.user != null ? r.user.username : u,
                                r.user != null ? r.user.display_name : u);
                            gotoMain(r.user != null ? r.user.username : u,
                                     r.user != null ? r.user.display_name : u);
                        } else {
                            btnSubmit.setEnabled(true);
                            btnSubmit.setText(isLogin ? "登 录" : "注 册");
                            tvError.setText(r.msg != null ? r.msg : "操作失败");
                        }
                    });
                } catch (IOException e) {
                    runOnUiThread(() -> {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText(isLogin ? "登 录" : "注 册");
                        tvError.setText("网络错误: " + e.getMessage());
                    });
                }
            }).start();
        } catch (Exception e) {
            Log.e("Login", "Auth crash: " + Log.getStackTraceString(e));
            Toast.makeText(this, "错误: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void gotoMain(String username, String displayName) {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("username", username);
            i.putExtra("display_name", displayName);
            i.putExtra("session_id", api.getSessionId());
            startActivity(i);
            finish();
        } catch (Exception e) {
            Log.e("Login", "MainActivity missing: " + Log.getStackTraceString(e));
            Toast.makeText(this, "MainActivity 缺失", Toast.LENGTH_LONG).show();
        }
    }
}
