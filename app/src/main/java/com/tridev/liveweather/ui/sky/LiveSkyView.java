package com.tridev.liveweather.ui.sky;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;
import com.tridev.liveweather.domain.SkyRealityEngine;
import com.tridev.liveweather.domain.SkyRealityState;

import java.util.Locale;

/**
 * Lightweight live celestial/weather preview.
 *
 * Weather is supplied by the shared cached weather pipeline. Sun/Moon position
 * is recalculated from the device clock every 30 seconds while this View is
 * attached, so celestial motion does not trigger weather network requests.
 */
public final class LiveSkyView extends View {

    private static final long TICK_MILLIS = 30_000L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WeatherResponse weather;
    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private SkyRealityState lastState;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (isAttachedToWindow()) {
                invalidate();
                handler.postDelayed(this, TICK_MILLIS);
            }
        }
    };

    public LiveSkyView(Context context) {
        super(context);
        init();
    }

    public LiveSkyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiveSkyView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dp(12));
    }

    public void setWeatherData(@Nullable WeatherResponse weather, double latitude, double longitude) {
        this.weather = weather;
        this.latitude = latitude;
        this.longitude = longitude;
        invalidate();
    }

    public void clearWeatherData() {
        weather = null;
        latitude = Double.NaN;
        longitude = Double.NaN;
        lastState = null;
        invalidate();
    }

    @Nullable
    public SkyRealityState getLastState() {
        return lastState;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (weather == null || Double.isNaN(latitude) || Double.isNaN(longitude)) {
            drawWaiting(canvas);
            return;
        }

        try {
            lastState = SkyRealityEngine.calculate(
                    weather,
                    latitude,
                    longitude,
                    System.currentTimeMillis()
            );
            drawSky(canvas, lastState);
        } catch (RuntimeException exception) {
            drawWaiting(canvas);
        }
    }

    private void drawWaiting(Canvas canvas) {
        canvas.drawColor(Color.rgb(15, 27, 50));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.argb(210, 235, 245, 255));
        textPaint.setTextSize(dp(14));
        canvas.drawText("Live sky waiting for location + weather", getWidth() / 2f, getHeight() / 2f, textPaint);
    }

    private void drawSky(Canvas canvas, SkyRealityState state) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        LiveConditionResolver.ResolvedCondition condition = LiveConditionResolver.resolve(weather);
        int[] colors = backgroundColors(state.getSkyStage(), condition.getWeatherCode());
        paint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                height,
                colors[0],
                colors[1],
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);

        drawStars(canvas, state);
        drawSun(canvas, state);
        drawMoon(canvas, state);
        drawClouds(canvas, condition);
        drawPrecipitation(canvas, condition);
        drawHorizon(canvas, state);
        drawStatus(canvas, state, condition);
    }

    private void drawStars(Canvas canvas, SkyRealityState state) {
        int visibility = state.getStarVisibilityPercent();
        if (visibility <= 2) {
            return;
        }

        int alpha = Math.min(235, 35 + visibility * 2);
        paint.setColor(Color.argb(alpha, 245, 248, 255));
        int count = Math.max(8, (int) Math.round(72d * visibility / 100d));
        float usableHeight = getHeight() * 0.72f;

        for (int i = 0; i < count; i++) {
            float x = (((i * 73) % 997) / 997f) * getWidth();
            float y = 10f + ((((i * 137) + 41) % 659) / 659f) * usableHeight;
            float radius = dp(i % 9 == 0 ? 1.35f : 0.75f);
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private void drawSun(Canvas canvas, SkyRealityState state) {
        if (state.getSunAltitude() < -5d) {
            return;
        }
        float x = celestialX(state.getSunAzimuth());
        float y = celestialY(state.getSunAltitude());
        float radius = dp(18);

        paint.setColor(Color.argb(48, 255, 214, 92));
        canvas.drawCircle(x, y, radius * 2.0f, paint);
        paint.setColor(Color.argb(235, 255, 221, 103));
        canvas.drawCircle(x, y, radius, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(10));
        textPaint.setColor(Color.argb(220, 255, 248, 220));
        canvas.drawText("SUN", x, y + radius + dp(16), textPaint);
    }

    private void drawMoon(Canvas canvas, SkyRealityState state) {
        if (state.getMoonAltitude() < -5d) {
            return;
        }
        float x = celestialX(state.getMoonAzimuth());
        float y = celestialY(state.getMoonAltitude());
        float radius = dp(15);

        paint.setColor(Color.argb(230, 228, 235, 247));
        canvas.drawCircle(x, y, radius, paint);

        double illumination = Math.max(0d, Math.min(100d, state.getMoonIlluminationPercent())) / 100d;
        String phase = state.getMoonPhaseName();
        boolean waxing = phase.startsWith("Waxing") || phase.contains("First Quarter");
        float shadowWidth = (float) ((1d - illumination) * radius * 1.8d);
        if (shadowWidth > 0.5f) {
            paint.setColor(Color.argb(215, 19, 29, 49));
            RectF shadow = new RectF(
                    waxing ? x - radius : x - radius + shadowWidth,
                    y - radius,
                    waxing ? x + radius - shadowWidth : x + radius,
                    y + radius
            );
            canvas.drawOval(shadow, paint);
        }

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(10));
        textPaint.setColor(Color.argb(220, 240, 244, 255));
        canvas.drawText("MOON", x, y + radius + dp(16), textPaint);
    }

    private void drawClouds(Canvas canvas, LiveConditionResolver.ResolvedCondition condition) {
        WeatherResponse.CurrentWeather current = weather == null ? null : weather.getCurrent();
        double cloudCover = current == null || current.getCloudCover() == null
                ? 0d
                : Math.max(0d, Math.min(100d, current.getCloudCover()));
        int cloudCount = cloudCover < 15d ? 0 : cloudCover < 45d ? 2 : cloudCover < 75d ? 4 : 6;
        if (cloudCount == 0) {
            return;
        }

        int code = condition.getWeatherCode() == null ? 0 : condition.getWeatherCode();
        int alpha = code >= 51 ? 185 : 125;
        paint.setColor(Color.argb(alpha, 105, 123, 148));
        for (int i = 0; i < cloudCount; i++) {
            float cx = getWidth() * (0.08f + ((i * 0.19f) % 0.88f));
            float cy = getHeight() * (0.18f + (i % 3) * 0.13f);
            float w = dp(70 + (i % 2) * 22);
            float h = dp(24 + (i % 3) * 5);
            canvas.drawOval(new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), paint);
            canvas.drawCircle(cx - w * 0.20f, cy - h * 0.18f, h * 0.62f, paint);
            canvas.drawCircle(cx + w * 0.08f, cy - h * 0.30f, h * 0.75f, paint);
        }
    }

    private void drawPrecipitation(Canvas canvas, LiveConditionResolver.ResolvedCondition condition) {
        Integer code = condition.getWeatherCode();
        if (code == null) {
            return;
        }
        boolean rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
        boolean snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
        if (!rain && !snow) {
            return;
        }

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(rain ? 150 : 200, 205, 226, 248));
        for (int i = 0; i < 36; i++) {
            float x = (((i * 47) % 491) / 491f) * getWidth();
            float y = getHeight() * (0.28f + ((((i * 83) + 17) % 277) / 277f) * 0.58f);
            if (snow) {
                canvas.drawCircle(x, y, dp(1.4f), paint);
            } else {
                canvas.drawLine(x, y, x - dp(4), y + dp(12), paint);
            }
        }
    }

    private void drawHorizon(Canvas canvas, SkyRealityState state) {
        float horizon = getHeight() * 0.82f;
        int light = state.getAmbientLightPercent();
        paint.setColor(Color.argb(110, 10 + light / 5, 27 + light / 4, 38 + light / 3));
        canvas.drawRect(0f, horizon, getWidth(), getHeight(), paint);
    }

    private void drawStatus(
            Canvas canvas,
            SkyRealityState state,
            LiveConditionResolver.ResolvedCondition condition
    ) {
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(dp(11));
        textPaint.setColor(Color.argb(220, 239, 246, 255));
        String line = String.format(
                Locale.getDefault(),
                "%s · %s · Stars %d%% · Light %d%%",
                state.getSkyStage(),
                condition.getLabel(),
                state.getStarVisibilityPercent(),
                state.getAmbientLightPercent()
        );
        canvas.drawText(line, dp(12), getHeight() - dp(14), textPaint);
    }

    private int[] backgroundColors(String stage, @Nullable Integer weatherCode) {
        int top;
        int bottom;
        if (stage.contains("Daylight")) {
            top = Color.rgb(51, 122, 186);
            bottom = Color.rgb(121, 181, 220);
        } else if (stage.contains("Golden")) {
            top = Color.rgb(61, 86, 145);
            bottom = Color.rgb(203, 129, 84);
        } else if (stage.contains("Civil")) {
            top = Color.rgb(42, 57, 112);
            bottom = Color.rgb(111, 82, 137);
        } else if (stage.contains("Nautical")) {
            top = Color.rgb(23, 39, 83);
            bottom = Color.rgb(55, 61, 105);
        } else {
            top = Color.rgb(8, 18, 43);
            bottom = Color.rgb(19, 31, 61);
        }

        if (weatherCode != null && ((weatherCode >= 51 && weatherCode <= 67)
                || (weatherCode >= 80 && weatherCode <= 82)
                || weatherCode >= 95)) {
            top = darken(top, 0.55f);
            bottom = darken(bottom, 0.65f);
        }
        return new int[]{top, bottom};
    }

    private int darken(int color, float factor) {
        return Color.rgb(
                Math.round(Color.red(color) * factor),
                Math.round(Color.green(color) * factor),
                Math.round(Color.blue(color) * factor)
        );
    }

    private float celestialX(double azimuth) {
        double normalized = ((azimuth % 360d) + 360d) % 360d;
        return (float) (normalized / 360d * getWidth());
    }

    private float celestialY(double altitude) {
        float horizon = getHeight() * 0.82f;
        double normalized = Math.max(-6d, Math.min(90d, altitude));
        return (float) (horizon - ((normalized + 6d) / 96d) * getHeight() * 0.70f);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
