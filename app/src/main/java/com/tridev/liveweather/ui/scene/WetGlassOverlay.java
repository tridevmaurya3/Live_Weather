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
 * volume. Every drop is deterministic and recycled forever while rain remains
 * active, so wetness never expires after a short animation.
 */
public final class WetGlassOverlay {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint streamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path trailPath = new Path();
    private final Path streamPath = new Path();

    private long animationOriginMillis;

    public WetGlassOverlay() {
        fillPaint.setStyle(Paint.Style.FILL);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeCap(Paint.Cap.ROUND);
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        streamPaint.setStyle(Paint.Style.STROKE);
        streamPaint.setStrokeCap(Paint.Cap.ROUND);
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

        if (amount > 0.44f) {
            drawGlassStreams(canvas, width, height, amount, stormFlash, seconds);
        }

        int count = 12 + Math.round(amount * 30f);
        for (int i = 0; i < count; i++) {
            int seed = 311 + i * 977;
            float sizeFactor = hash01(seed * 13 + 5);
            float radius = 3.8f + sizeFactor * (7.0f + amount * 12.0f);
            float stretch = 1.08f + hash01(seed * 19 + 11) * 0.68f + amount * 0.44f;

            float xBase = hash01(seed * 29 + 17) * width;
            float drift = (hash01(seed * 37 + 23) - 0.5f) * radius * 1.35f;
            float x = xBase + drift;

            // Some beads creep slowly, others release and slide faster.
            float release = hash01(seed * 101 + 37);
            float speed = (2.4f + hash01(seed * 43 + 7) * 22f)
                    * (0.26f + amount * 0.98f)
                    * (release > 0.68f ? 1.65f : 0.62f);
            float cycle = height + radius * 26f;
            float yBase = hash01(seed * 53 + 31) * cycle;
            float y = positiveMod(yBase + seconds * speed, cycle) - radius * 11f;

            float pulse = 0.94f + 0.06f * (float) Math.sin(seconds * 0.46f + seed * 0.017f);
            float rx = radius * pulse;
            float ry = radius * stretch * pulse;
            oval.set(x - rx, y - ry, x + rx, y + ry);

            // Refraction body: transparent centre with subtle cool tint.
            int bodyAlpha = clampInt(Math.round(12f + amount * 32f + stormFlash * 22f), 9, 64);
            fillPaint.setColor(Color.argb(bodyAlpha, 204, 232, 247));
            canvas.drawOval(oval, fillPaint);

            // Lower dark edge provides refraction depth.
            rimPaint.setStrokeWidth(Math.max(0.8f, radius * 0.115f));
            rimPaint.setColor(Color.argb(
                    clampInt(Math.round(22f + amount * 40f), 16, 76),
                    46,
                    70,
                    88
            ));
            canvas.drawArc(oval, 5f, 170f, false, rimPaint);

            // Upper specular rim catches sky and lightning.
            rimPaint.setStrokeWidth(Math.max(0.7f, radius * 0.105f));
            rimPaint.setColor(Color.argb(
                    clampInt(Math.round(58f + amount * 78f + stormFlash * 108f), 44, 225),
                    246,
                    253,
                    255
            ));
            canvas.drawArc(oval, 185f, 130f, false, rimPaint);

            fillPaint.setColor(Color.argb(
                    clampInt(Math.round(58f + amount * 78f + stormFlash * 86f), 40, 212),
                    253,
                    255,
                    255
            ));
            canvas.drawCircle(
                    x - rx * 0.34f,
                    y - ry * 0.34f,
                    Math.max(0.9f, radius * 0.17f),
                    fillPaint
            );

            if (amount > 0.22f && radius > 7.4f && hash01(seed * 67 + 41) > 0.30f) {
                float trailLength = radius * (3.0f + amount * 7.0f);
                float sway = (hash01(seed * 71 + 19) - 0.5f) * radius * 0.95f;
                trailPath.reset();
                trailPath.moveTo(x, y - ry * 0.82f);
                trailPath.cubicTo(
                        x + sway * 0.22f,
                        y - ry - trailLength * 0.25f,
                        x - sway * 0.18f,
                        y - ry - trailLength * 0.68f,
                        x + sway,
                        y - ry - trailLength
                );
                trailPaint.setStrokeWidth(Math.max(0.8f, radius * 0.11f));
                trailPaint.setColor(Color.argb(
                        clampInt(Math.round(18f + amount * 40f + stormFlash * 20f), 13, 72),
                        201,
                        227,
                        242
                ));
                canvas.drawPath(trailPath, trailPaint);
            }
        }
    }

    /**
     * Heavy rain creates a few slow water channels on the virtual glass.
     * These are long translucent curved strokes, not screen-wide rectangles.
     */
    private void drawGlassStreams(
            Canvas canvas,
            int width,
            int height,
            float amount,
            float stormFlash,
            float seconds
    ) {
        float heavy = clamp01((amount - 0.44f) / 0.56f);
        int streams = 3 + Math.round(heavy * 5f);

        for (int i = 0; i < streams; i++) {
            int seed = 7001 + i * 1237;
            float xBase = hash01(seed * 17 + 5) * width;
            float cycle = height * 1.25f;
            float speed = 8f + hash01(seed * 29 + 11) * 18f + heavy * 16f;
            float headY = positiveMod(
                    hash01(seed * 37 + 7) * cycle + seconds * speed,
                    cycle
            ) - height * 0.12f;
            float length = height * (0.10f + hash01(seed * 43 + 19) * 0.16f + heavy * 0.08f);
            float sway = (hash01(seed * 53 + 31) - 0.5f) * width * 0.045f;

            streamPath.reset();
            streamPath.moveTo(xBase, headY);
            streamPath.cubicTo(
                    xBase + sway * 0.25f,
                    headY - length * 0.30f,
                    xBase - sway * 0.15f,
                    headY - length * 0.72f,
                    xBase + sway,
                    headY - length
            );

            streamPaint.setStrokeWidth(2.0f + heavy * 2.2f);
            streamPaint.setColor(Color.argb(
                    clampInt(Math.round(10f + heavy * 24f + stormFlash * 18f), 8, 48),
                    196,
                    221,
                    236
            ));
            canvas.drawPath(streamPath, streamPaint);

            streamPaint.setStrokeWidth(0.7f + heavy * 0.8f);
            streamPaint.setColor(Color.argb(
                    clampInt(Math.round(24f + heavy * 42f + stormFlash * 62f), 18, 106),
                    244,
                    252,
                    255
            ));
            canvas.drawPath(streamPath, streamPaint);
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
