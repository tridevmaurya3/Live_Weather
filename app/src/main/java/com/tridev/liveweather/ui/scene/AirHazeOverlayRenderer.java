package com.tridev.liveweather.ui.scene;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.domain.AirQualityReality;

/**
 * Lightweight post-processing layer that integrates AQI haze without flattening
 * the entire sky. Real atmospheric haze is strongest toward the lower atmosphere
 * and horizon, while the zenith generally retains more sky colour/contrast.
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
        double haze = AirQualityReality.hazeIntensity(airQuality);
        if (haze < 0.03d) {
            return;
        }

        int zenithAlpha = clamp((int) Math.round(2d + haze * 9d));
        int middleAlpha = clamp((int) Math.round(8d + haze * 24d));
        int horizonAlpha = clamp((int) Math.round(22d + haze * 66d));
        int groundAlpha = clamp((int) Math.round(16d + haze * 48d));

        int zenith = Color.argb(zenithAlpha, 181, 190, 194);
        int middle = Color.argb(middleAlpha, 193, 190, 177);
        int horizon = Color.argb(horizonAlpha, 213, 199, 169);
        int ground = Color.argb(groundAlpha, 196, 193, 180);

        paint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                height,
                new int[]{zenith, middle, horizon, ground},
                new float[]{0f, 0.48f, 0.82f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(145, value));
    }
}
