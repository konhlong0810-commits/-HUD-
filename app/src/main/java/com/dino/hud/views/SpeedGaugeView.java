package com.dino.hud.views;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/**
 * 自定义速度仪表盘 — 环形进度 + 数字速度
 */
public class SpeedGaugeView extends View {
    private double speedKmh = 0;
    private final Paint ringBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textSpeed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textUnit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    public SpeedGaugeView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        ringBg.setStyle(Paint.Style.STROKE);
        ringBg.setStrokeWidth(8f);
        ringBg.setColor(0xFFE2E8F0);
        ringBg.setStrokeCap(Paint.Cap.ROUND);

        ringFill.setStyle(Paint.Style.STROKE);
        ringFill.setStrokeWidth(8f);
        ringFill.setStrokeCap(Paint.Cap.ROUND);

        textSpeed.setColor(0xFF1E293B);
        textSpeed.setTextAlign(Paint.Align.CENTER);
        textSpeed.setLetterSpacing(-0.02f);

        textUnit.setColor(0xFF64748B);
        textUnit.setTextAlign(Paint.Align.CENTER);
    }

    public void setSpeed(double kmh) {
        this.speedKmh = kmh;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h * 0.55f;
        float radius = Math.min(w, h) * 0.38f;
        float pad = 12f;
        oval.set(cx - radius + pad, cy - radius + pad, cx + radius - pad, cy + radius - pad);

        // 背景环
        c.drawArc(oval, 135f, 270f, false, ringBg);

        // 进度环（0-180 km/h）
        float pct = (float) Math.min(1, speedKmh / 180.0);
        // 渐变：蓝 → 深蓝
        int color;
        if (pct < 0.5f) color = 0xFF3B82F6;
        else if (pct < 0.75f) color = 0xFF2563EB;
        else color = 0xFF1D4ED8;
        ringFill.setColor(color);
        ringFill.setShader(new SweepGradient(cx, cy,
            new int[]{0x3B82F6, 0x2563EB, 0x1D4ED8},
            new float[]{0f, 0.5f, 1f}));
        c.drawArc(oval, 135f, 270f * pct, false, ringFill);
        ringFill.setShader(null);

        // 速度数字
        textSpeed.setTextSize(radius * 0.85f);
        c.drawText(String.valueOf(Math.round(speedKmh)), cx, cy + textSpeed.getTextSize() * 0.1f, textSpeed);

        // 单位
        textUnit.setTextSize(radius * 0.18f);
        c.drawText("公里 / 时", cx, cy + radius * 0.55f, textUnit);

        // 刻度标签
        Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setColor(0xFF94A3B8);
        tickPaint.setTextSize(radius * 0.12f);
        tickPaint.setTextAlign(Paint.Align.CENTER);
        c.drawText("0", cx - radius * 0.85f, cy + radius * 0.25f, tickPaint);
        c.drawText("90", cx, cy - radius + pad - 6, tickPaint);
        c.drawText("180", cx + radius * 0.85f, cy + radius * 0.25f, tickPaint);
    }
}
