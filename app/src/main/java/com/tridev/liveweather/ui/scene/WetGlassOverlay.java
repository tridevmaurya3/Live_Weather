package com.tridev.liveweather.ui.scene;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.NonNull;

/**
 * Foreground water layer for the Hero rain pipeline.
 *
 * Drops live on a virtual phone-glass plane rather than in the distant rain
 * volume. Every drop uses a deterministic recycle cycle, so the effect stays
 * alive for as long as rain remains active without allocating objects per frame.
 */
public final class WetGlassOverlay {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path trailPath = new Path();

    private long animationOriginMillis;

    public WetGlassOverlay() {
        fillPaint.setStyle(Paint.Style.FILL);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeCap(Paint.Cap.ROUND);
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void draw(
            @NonNull Canvas canvas,
            int width,
            int height,
            float wetness,
            float stormFlash,
            long nowMillis
    ) {
        if (width <= 0 || height <= 0) return;
        float amount = clamp01(wetness);
        if (amount < 0.035f) return;

        if (animationOriginMillis == 0L) animationOriginMillis = nowMillis;
        float seconds = Math.max(0L, nowMillis - animationOriginMillis) / 1000f;
        int count = 10 + Math.round(amount * 24f);

        for (int i = 0; i < count; i++) {
            int seed = 311 + i * 977;
            float sizeFactor = hash01(seed * 13 + 5);
            float radius = 3.4f + sizeFactor * (5.8f + amount * 10.0f);
            float stretch = 1.05f + hash01(seed * 19 + 11) * 0.60f + amount * 0.38f;

            float xBase = hash01(seed * 29 + 17) * width;
            float drift = (hash01(seed * 37 + 23) - 0.5f) * radius * 1.25f;
            float x = xBase + drift;

            float speed = (3.5f + hash01(seed * 43 + 7) * 20f)
                    * (0.30f + amount * 0.92f);
            float cycle = height + radius * 24f;
            float yBase = hash01(seed * 53 + 31) * cycle;
            float y = positiveMod(yBase + seconds * speed, cycle) - radius * 10f;

            float pulse = 0.92f + 0.08f * (float) Math.sin(seconds * 0.55f + seed * 0.017f);
            float rx = radius * pulse;
            float ry = radius * stretch * pulse;
            oval.set(x - rx, y - ry, x + rx, y + ry);

            int bodyAlpha = clampInt(Math.round(10f + amount * 25f + stormFlash * 18f), 8, 52);
            fillPaint.setColor(Color.argb(bodyAlpha, 205, 231, 245));
            canvas.drawOval(oval, fillPaint);

            rimPaint.setStrokeWidth(Math.max(0.75f, radius * 0.11f));
            rimPaint.setColor(Color.argb(
                    clampInt(Math.round(20f + amount * 34f), 14, 62),
                    54,
                    80,
                    99
            ));
            canvas.drawArc(oval, 8f, 164f, false, rimPaint);

            rimPaint.setStrokeWidth(Math.max(0.65f, radius * 0.10f));
            rimPaint.setColor(Color.argb(
                    clampInt(Math.round(52f + amount * 68f + stormFlash * 95f), 40, 210),
                    244,
                    252,
                    255
            ));
            canvas.drawArc(oval, 188f, 126f, false, rimPaint);

            fillPaint.setColor(Color.argb(
                    clampInt(Math.round(52f + amount * 70f + stormFlash * 72f), 38, 196),
                    252,
                    255,
                    255
            ));
            canvas.drawCircle(
                    x - rx * 0.34f,
                    y - ry * 0.34f,
                    Math.max(0.9f, radius * 0.16f),
                    fillPaint
            );

            if (amount > 0.28f && radius > 7.2f && hash01(seed * 67 + 41) > 0.38f) {
                float trailLength = radius * (2.8f + amount * 5.8f);
                float sway = (hash01(seed * 71 + 19) - 0.5f) * radius * 0.85f;
                trailPath.reset();
                trailPath.moveTo(x, y - ry * 0.85f);
                trailPath.cubicTo(
                        x + sway * 0.25f,
                        y - ry - trailLength * 0.28f,
                        x - sway * 0.20f,
                        y - ry - trailLength * 0.70f,
                        x + sway,
                        y - ry - trailLength
                );
                trailPaint.setStrokeWidth(Math.max(0.7f, radius * 0.10f));
                trailPaint.setColor(Color.argb(
                        clampInt(Math.round(15f + amount * 32f), 12, 54),
                        202,
                        226,
                        240
                ));
                canvas.drawPath(trailPath, trailPaint);
            }
        }
    }

    private static float hash01(int seed) {
        int n = seed;
        n = (n << 13) ^ n;
        int nn = n * (n * n * 15731 + 789221) + 1376312589;
        return (nn & 0x7fffffff) / 2147483647f;
    }

    private static float positiveMod(float value, float modulo) {
        if (modulo <= 0f) return 0f;
        float result = value % modulo;
        return result < 0f ? result + modulo : result;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
