package com.tridev.liveweather.ui.scene;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.annotation.NonNull;

/**
 * Procedural branched lightning generator.
 *
 * The bolt is deterministic for a given event seed, so the same strike does not
 * jitter randomly between adjacent animation frames. Glow and core are rendered
 * in separate passes for a more photographic electric appearance.
 */
public final class LightningBoltGenerator {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LightningBoltGenerator() {
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);

        corePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStrokeCap(Paint.Cap.ROUND);
        corePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void draw(
            @NonNull Canvas canvas,
            int width,
            int height,
            float anchorXFraction,
            int seed,
            float strength
    ) {
        float energy = clamp01(strength);
        if (energy < 0.04f || width <= 0 || height <= 0) return;

        float startX = width * clamp(anchorXFraction, 0.08f, 0.92f);
        float startY = height * (0.035f + hash01(seed * 17 + 3) * 0.08f);
        float targetY = height * (0.63f + hash01(seed * 23 + 11) * 0.25f);

        drawBranch(canvas, width, height, startX, startY, targetY, seed, 0, energy);
    }

    private void drawBranch(
            Canvas canvas,
            int width,
            int height,
            float startX,
            float startY,
            float targetY,
            int seed,
            int depth,
            float energy
    ) {
        if (energy < 0.07f || depth > 2 || startY >= height) return;

        int segments = depth == 0 ? 13 : depth == 1 ? 7 : 4;
        float totalHeight = Math.max(height * 0.10f, targetY - startY);
        float stepY = totalHeight / segments;
        float lateralScale = width * (depth == 0 ? 0.075f : depth == 1 ? 0.052f : 0.035f);

        Path path = new Path();
        path.moveTo(startX, startY);

        float x = startX;
        float y = startY;
        for (int i = 0; i < segments; i++) {
            float forkBias = hash01(seed + i * 97 + depth * 313) - 0.5f;
            float zig = forkBias * lateralScale * (0.58f + hash01(seed + i * 61) * 0.82f);
            x += zig;
            y += stepY * (0.78f + hash01(seed + i * 43 + 5) * 0.48f);
            path.lineTo(x, y);

            boolean canFork = depth < 2 && i >= 2 && i <= segments - 3;
            float forkChance = depth == 0 ? 0.60f : 0.34f;
            if (canFork && hash01(seed + i * 149 + 17) > forkChance) {
                float branchTarget = y + height * (depth == 0 ? 0.16f : 0.10f)
                        * (0.70f + hash01(seed + i * 181) * 0.85f);
                drawBranch(
                        canvas,
                        width,
                        height,
                        x,
                        y,
                        Math.min(height * 0.94f, branchTarget),
                        seed + i * 1009 + depth * 7919,
                        depth + 1,
                        energy * (depth == 0 ? 0.58f : 0.44f)
                );
            }
        }

        float baseCore = Math.max(1.15f, width / 430f);
        float depthFactor = depth == 0 ? 1f : depth == 1 ? 0.72f : 0.52f;

        glowPaint.setStrokeWidth(baseCore * (7.2f - depth * 1.35f));
        glowPaint.setColor(Color.argb(
                clampInt(Math.round(78f * energy * depthFactor), 0, 102),
                132,
                174,
                255
        ));
        canvas.drawPath(path, glowPaint);

        glowPaint.setStrokeWidth(baseCore * (3.2f - depth * 0.55f));
        glowPaint.setColor(Color.argb(
                clampInt(Math.round(148f * energy * depthFactor), 0, 185),
                188,
                218,
                255
        ));
        canvas.drawPath(path, glowPaint);

        corePaint.setStrokeWidth(Math.max(0.75f, baseCore * (1.05f - depth * 0.16f)));
        corePaint.setColor(Color.argb(
                clampInt(Math.round(255f * energy * depthFactor), 0, 255),
                248,
                252,
                255
        ));
        canvas.drawPath(path, corePaint);
    }

    private static float hash01(int seed) {
        int n = seed;
        n = (n << 13) ^ n;
        int nn = n * (n * n * 15731 + 789221) + 1376312589;
        return (nn & 0x7fffffff) / 2147483647f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
