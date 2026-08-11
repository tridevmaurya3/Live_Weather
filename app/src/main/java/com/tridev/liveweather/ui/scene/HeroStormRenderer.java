package com.tridev.liveweather.ui.scene;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;

/**
 * Hero thunderstorm renderer.
 *
 * Responsibilities:
 * - deep storm darkening that never exposes rectangular texture bounds;
 * - irregular whole-screen lightning flashes;
 * - radial cloud illumination around the strike;
 * - deterministic branched lightning with a short electrical afterglow.
 */
public final class HeroStormRenderer {

    private static final long STATE_REFRESH_MILLIS = 4_000L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final StormFlashController flashController = new StormFlashController();
    private final LightningBoltGenerator boltGenerator = new LightningBoltGenerator();

    private WeatherResponse weather;
    private boolean enabled = true;
    private long lastStateRefresh;
    private float stormIntensity;
    private float rainIntensity;
    private float cloudCover;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setWeatherData(@Nullable WeatherResponse weather) {
        this.weather = weather;
        lastStateRefresh = 0L;
    }

    public void clearWeatherData() {
        weather = null;
        lastStateRefresh = 0L;
        stormIntensity = 0f;
        rainIntensity = 0f;
        cloudCover = 0f;
    }

    /**
     * Draw behind the foreground rain. This makes the entire cloud/sky volume
     * react to lightning rather than rendering a bright line on an unchanged sky.
     */
    public void drawAtmosphere(
            @NonNull Canvas canvas,
            int width,
            int height,
            long nowMillis
    ) {
        if (!enabled || weather == null || width <= 0 || height <= 0) return;
        refreshState(nowMillis);

        float storm = clamp01(stormIntensity);
        if (storm < 0.08f) return;

        StormFlashController.FlashFrame frame = flashController.frame(nowMillis, storm);
        float flash = frame.getFlashStrength();

        // Persistent storm ceiling / pressure-darkening. It is a full-screen
        // gradient, never a moving rectangle or sprite.
        int topAlpha = clampInt(Math.round(35f + storm * 68f), 28, 110);
        int lowerAlpha = clampInt(Math.round(24f + storm * 52f + rainIntensity * 24f), 18, 96);
        paint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                height,
                new int[]{
                        Color.argb(topAlpha, 4, 12, 26),
                        Color.argb(lowerAlpha, 15, 25, 37),
                        Color.argb(Math.max(10, lowerAlpha / 2), 31, 42, 48)
                },
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);

        if (flash <= 0.001f) return;

        float strikeX = width * frame.getAnchorXFraction();
        float glowY = height * (0.25f + cloudCover * 0.12f);
        float radius = Math.max(width, height) * (0.45f + flash * 0.22f);

        paint.setShader(new RadialGradient(
                strikeX,
                glowY,
                radius,
                new int[]{
                        Color.argb(clampInt(Math.round(210f * flash), 0, 230), 231, 241, 255),
                        Color.argb(clampInt(Math.round(118f * flash), 0, 155), 170, 197, 238),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.34f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);

        // Whole-scene electrical flash. Strong strikes can momentarily wash out
        // the screen, matching the way real lightning illuminates rain and cloud.
        int flashAlpha = clampInt(Math.round((92f + storm * 110f) * flash), 0, 224);
        paint.setColor(Color.argb(flashAlpha, 218, 232, 255));
        canvas.drawRect(0f, 0f, width, height, paint);
    }

    /** Draw the visible bolt above rain/wet-glass layers. */
    public void drawForeground(
            @NonNull Canvas canvas,
            int width,
            int height,
            long nowMillis
    ) {
        if (!enabled || weather == null || width <= 0 || height <= 0) return;
        refreshState(nowMillis);

        float storm = clamp01(stormIntensity);
        if (storm < 0.08f) return;

        StormFlashController.FlashFrame frame = flashController.frame(nowMillis, storm);
        float bolt = frame.getBoltStrength();
        if (bolt > 0.035f && !frame.isDistant()) {
            boltGenerator.draw(
                    canvas,
                    width,
                    height,
                    frame.getAnchorXFraction(),
                    frame.getSeed(),
                    bolt
            );
        }

        // A very short high-energy front-glass pulse makes the strike feel like
        // emitted light rather than a white graphic pasted onto the wallpaper.
        if (frame.getFlashStrength() > 0.72f) {
            int alpha = clampInt(Math.round(48f * frame.getFlashStrength()), 0, 58);
            paint.setShader(null);
            paint.setColor(Color.argb(alpha, 245, 250, 255));
            canvas.drawRect(0f, 0f, width, height, paint);
        }
    }

    public float flashStrength(long nowMillis) {
        if (!enabled || weather == null) return 0f;
        refreshState(nowMillis);
        return flashController.frame(nowMillis, stormIntensity).getFlashStrength();
    }

    private void refreshState(long nowMillis) {
        if (nowMillis - lastStateRefresh < STATE_REFRESH_MILLIS) return;
        lastStateRefresh = nowMillis;

        LiveConditionResolver.ResolvedCondition resolved = LiveConditionResolver.resolve(weather);
        WeatherResponse.CurrentWeather current = weather.getCurrent();

        int code = resolved.getWeatherCode() == null ? 0 : resolved.getWeatherCode();
        double precipitation = Math.max(0d, resolved.getPrecipitationSignalMm());
        double rain = value(current == null ? null : current.getRain());
        double showers = value(current == null ? null : current.getShowers());
        double gusts = value(current == null ? null : current.getWindGusts10m());
        double clouds = value(current == null ? null : current.getCloudCover());

        rainIntensity = (float) clamp(
                precipitation * 0.58d + rain * 0.42d + showers * 0.52d,
                0d,
                1d
        );
        cloudCover = (float) clamp(clouds / 100d, 0d, 1d);

        // Lightning is only enabled by thunderstorm WMO codes. Strong rain/gusts
        // can deepen the storm visual but never fabricate electrical activity.
        if (code >= 95) {
            stormIntensity = (float) clamp(
                    0.62d
                            + rainIntensity * 0.24d
                            + Math.min(1d, gusts / 90d) * 0.16d,
                    0.62d,
                    1d
            );
        } else {
            stormIntensity = 0f;
        }
    }

    private static double value(Double value) {
        return value == null ? 0d : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
