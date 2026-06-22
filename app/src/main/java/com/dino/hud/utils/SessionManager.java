package com.dino.hud.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 本地 session token 持久化
 */
public class SessionManager {
    private static final String PREFS = "dino_prefs";
    private static final String KEY_SESSION = "session_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DISPLAY = "display_name";
    private final SharedPreferences sp;

    public SessionManager(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveSession(String sessionId, String username, String displayName) {
        sp.edit().putString(KEY_SESSION, sessionId)
          .putString(KEY_USERNAME, username)
          .putString(KEY_DISPLAY, displayName)
          .apply();
    }

    public String getSessionId() { return sp.getString(KEY_SESSION, null); }
    public String getUsername() { return sp.getString(KEY_USERNAME, null); }
    public String getDisplayName() { return sp.getString(KEY_DISPLAY, null); }
    public boolean isLoggedIn() { return getSessionId() != null; }

    public void logout() {
        sp.edit().remove(KEY_SESSION).remove(KEY_USERNAME).remove(KEY_DISPLAY).apply();
    }
}
