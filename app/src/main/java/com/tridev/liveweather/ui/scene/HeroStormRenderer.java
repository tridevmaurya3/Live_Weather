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
 * HRS-2 responsibilities:
 * - persistent storm darkening without rectangular artifacts;
 * - irregular multi-pulse whole-screen electrical flashes;
 * - broad cloud-ceiling illumination around each strike;
 * - deterministic branched lightning + short afterglow;
 * - distant lightning may illuminate clouds without showing a dominant bolt.
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
     * Draw behind foreground rain. The flash lights the atmosphere/cloud volume
     * first so the strike feels emitted rather than pasted onto the wallpaper.
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

        // Deep pressure-darkened storm ceiling.
        int topAlpha = clampInt(Math.round(46f + storm * 82f), 36, 134);
        int midAlpha = clampInt(Math.round(34f + storm * 68f + rainIntensity * 18f), 28, 116);
        int lowerAlpha = clampInt(Math.round(24f + storm * 48f + rainIntensity * 28f), 18, 104);
        paint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                height,
                new int[]{
                        Color.argb(topAlpha, 3, 10, 23),
                        Color.argb(midAlpha, 11, 21, 35),
                        Color.argb(lowerAlpha, 30, 42, 50)
                },
                new float[]{0f, 0.56f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);

        if (flash <= 0.001f) return;

        float strikeX = width * frame.getAnchorXFraction();
        float cloudY = height * (0.16f + cloudCover * 0.10f);
        float localRadius = Math.max(width, height) * (0.40f + flash * 0.26f);

        // Local cloud illumination around the electrical channel.
        paint.setShader(new RadialGradient(
                strikeX,
                cloudY,
                localRadius,
                new int[]{
                        Color.argb(clampInt(Math.round(238f * flash), 0, 248), 238, 246, 255),
                        Color.argb(clampInt(Math.round(158f * flash), 0, 184), 184, 207, 244),
                        Color.argb(clampInt(Math.round(52f * flash), 0, 72), 105, 135, 190),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.22f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height * 0.72f, paint);
        paint.setShader(null);

        // Wide cloud-ceiling flash: even distant lightning should make the whole
        // storm bank breathe with light for a fraction of a second.
        int ceilingAlpha = clampInt(Math.round((62f + storm * 88f) * flash), 0, 182);
        paint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                height * 0.58f,
                new int[]{
                        Color.argb(ceilingAlpha, 224, 237, 255),
                        Color.argb(ceilingAlpha / 2, 158, 188, 235),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.50f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height * 0.62f, paint);
        paint.setShader(null);

        // Whole-screen electrical response. Close strikes can briefly wash the
        // screen nearly white, as requested for the Hero storm effect.
        int flashAlpha = clampInt(Math.round((118f + storm * 124f) * flash), 0, 242);
        paint.setColor(Color.argb(flashAlpha, 220, 235, 255));
        canvas.drawRect(0f, 0f, width, height, paint);

        if (flash > 0.76f) {
            int whitePulse = clampInt(Math.round((flash - 0.76f) / 0.24f * 96f), 0, 104);
            paint.setColor(Color.argb(whitePulse, 250, 253, 255));
            canvas.drawRect(0f, 0f, width, height, paint);
        }
    }

    /** Draw visible lightning branches above rain/wet-glass layers. */
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

        // Short lens/front-glass electrical pulse.
        float flash = frame.getFlashStrength();
        if (flash > 0.64f) {
            int alpha = clampInt(Math.round(68f * flash), 0, 78);
            paint.setShader(null);
            paint.setColor(Color.argb(alpha, 247, 251, 255));
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

        // Electrical activity is never fabricated from rain alone. It is enabled
        // only for thunderstorm WMO codes; rain/gusts merely tune intensity.
        if (code >= 95) {
            stormIntensity = (float) clamp(
                    0.66d
                            + rainIntensity * 0.22d
                            + Math.min(1d, gusts / 90d) * 0.16d,
                    0.66d,
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
