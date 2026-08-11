package com.tridev.liveweather.ui.scene;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.ui.air.AirQualityIntelligence;

/**
 * Lightweight post-processing layer that visually integrates AQI haze without
 * duplicating or destabilising the procedural nature renderer.
 */
public final class AirHazeOverlayRenderer {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private AirQualityResponse airQuality;

    public void setAirQuality(@Nullable AirQualityResponse airQuality) {
        this.airQuality = airQuality;
    }

    public void draw(Canvas canvas, int width, int height) {
        if (airQuality == null || width <= 0 || height <= 0) {
            return;
        }
        double haze = AirQualityIntelligence.hazeIntensity(airQuality);
        if (haze < 0.03d) {
            return;
        }

        int topAlpha = (int) Math.round(18d + haze * 38d);
        int horizonAlpha = (int) Math.round(35d + haze * 85d);
        int top = Color.argb(clamp(topAlpha), 176, 183, 184);
        int middle = Color.argb(clamp((topAlpha + horizonAlpha) / 2), 194, 185, 163);
        int horizon = Color.argb(clamp(horizonAlpha), 205, 191, 160);

        paint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                height,
                new int[]{top, middle, horizon},
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(180, value));
    }
}
