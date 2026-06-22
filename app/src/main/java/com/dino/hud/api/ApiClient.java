package com.dino.hud.api;

import com.dino.hud.models.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.socket.client.IO;
import io.socket.client.Socket;
import okhttp3.*;
import okhttp3.MediaType;

/**
 * HTTP 客户端，封装所有服务端 API 调用
 */
public class ApiClient {
    private static final String BASE = "https://konglong-milk.top:4002";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    private final OkHttpClient http;
    private String sessionId;
    private Socket socket;

    public ApiClient() {
        http = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .hostnameVerifier((h, s) -> true)       // 自签/Let's Encrypt 兼容
            .build();
    }

    public void setSessionId(String id) { this.sessionId = id; }
    public String getSessionId() { return sessionId; }

    // ==================== 认证 ====================

    public static class AuthResult {
        public boolean ok;
        public String msg;
        public String sessionId;
        public User user;
        public boolean alreadyLoggedIn;
        public String ip;
        public String loginTime;
    }

    public AuthResult login(String username, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        return parseAuth(post("/api/login", body));
    }

    public AuthResult register(String username, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        return parseAuth(post("/api/register", body));
    }

    private AuthResult parseAuth(String json) {
        AuthResult r = new AuthResult();
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        r.ok = getBool(o, "ok");
        r.msg = getStr(o, "msg");
        r.sessionId = getStr(o, "sessionId");
        r.alreadyLoggedIn = getBool(o, "alreadyLoggedIn");
        r.ip = getStr(o, "ip");
        r.loginTime = getStr(o, "loginTime");
        if (o.has("user") && !o.get("user").isJsonNull()) {
            r.user = GSON.fromJson(o.get("user"), User.class);
        }
        return r;
    }

    public User getMe() throws IOException {
        String json = get("/api/me");
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (getBool(o, "ok") && o.has("user")) {
            return GSON.fromJson(o.get("user"), User.class);
        }
        return null;
    }

    public void logout() throws IOException {
        post("/api/logout", new JsonObject());
    }

    // ==================== Sessions ====================

    public List<TrailSession> getSessions() throws IOException {
        String json = get("/api/sessions/my");
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (getBool(o, "ok") && o.has("sessions")) {
            Type listType = new TypeToken<List<TrailSession>>(){}.getType();
            return GSON.fromJson(o.get("sessions"), listType);
        }
        return new ArrayList<>();
    }

    public boolean saveSession(long startTime, long endTime, double distance,
                                List<TrailPoint> points) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("startTime", String.valueOf(startTime));
        body.addProperty("endTime", String.valueOf(endTime));
        body.addProperty("distance", distance);
        body.add("points", GSON.toJsonTree(points));
        return getBool(JsonParser.parseString(post("/api/sessions/save", body))
                .getAsJsonObject(), "ok");
    }

    public boolean deleteSession(long id) throws IOException {
        String json = httpDelete("/api/sessions/" + id);
        return getBool(JsonParser.parseString(json).getAsJsonObject(), "ok");
    }

    // ==================== Trails ====================

    public boolean saveTrail(List<TrailPoint> points, double distance, long duration) throws IOException {
        JsonObject body = new JsonObject();
        body.add("points", GSON.toJsonTree(points));
        body.addProperty("distance", distance);
        body.addProperty("duration", duration);
        return getBool(JsonParser.parseString(post("/api/trails/save", body))
                .getAsJsonObject(), "ok");
    }

    // ==================== Leaderboard ====================

    public static class RankEntry {
        public String username;
        public String display_name;
        public double total;
        public double today;
    }

    public List<RankEntry> getLeaderboard() throws IOException {
        String json = get("/api/leaderboard");
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (getBool(o, "ok") && o.has("list")) {
            Type listType = new TypeToken<List<RankEntry>>(){}.getType();
            return GSON.fromJson(o.get("list"), listType);
        }
        return new ArrayList<>();
    }

    // ==================== Socket.IO ====================

    public Socket connectSocket() {
        if (socket != null && socket.connected()) return socket;
        try {
            IO.Options opts = new IO.Options();
            opts.forceNew = true;
            opts.reconnection = true;
            opts.query = "sessionId=" + (sessionId != null ? sessionId : "");
            // SSL
            OkHttpClient ok = new OkHttpClient.Builder()
                .hostnameVerifier((h, s) -> true).build();
            opts.webSocketFactory = ok;
            opts.callFactory = ok;
            socket = IO.socket(BASE, opts);
            socket.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return socket;
    }

    public void disconnectSocket() {
        if (socket != null) { socket.disconnect(); socket.off(); socket = null; }
    }

    // ==================== HTTP helpers ====================

    private String get(String path) throws IOException {
        Request.Builder b = new Request.Builder().url(BASE + path).get();
        addSession(b);
        try (Response r = http.newCall(b.build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            return r.body() != null ? r.body().string() : "{}";
        }
    }

    private String post(String path, JsonObject body) throws IOException {
        RequestBody rb = RequestBody.create(body.toString(), JSON);
        Request.Builder b = new Request.Builder().url(BASE + path).post(rb);
        addSession(b);
        try (Response r = http.newCall(b.build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            return r.body() != null ? r.body().string() : "{}";
        }
    }

    private String httpDelete(String path) throws IOException {
        Request.Builder b = new Request.Builder().url(BASE + path).delete();
        addSession(b);
        try (Response r = http.newCall(b.build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            return r.body() != null ? r.body().string() : "{}";
        }
    }

    private void addSession(Request.Builder b) {
        if (sessionId != null) b.header("x-session-id", sessionId);
    }

    // ==================== JSON util ====================

    private static boolean getBool(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
    }

    private static String getStr(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
