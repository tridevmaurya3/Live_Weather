package com.tridev.liveweather.ui.forecast;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.tridev.liveweather.R;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight native interactive forecast chart.
 * Supports temperature line and precipitation-probability bar modes.
 */
public final class ForecastChartView extends View {

    public enum Mode {
        TEMPERATURE,
        PRECIPITATION
    }

    public interface OnPointSelectedListener {
        void onPointSelected(int index);
    }

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<String> labels = new ArrayList<>();
    private final List<Double> values = new ArrayList<>();
    private Mode mode = Mode.TEMPERATURE;
    private int selectedIndex = -1;
    private String emptyMessage = "Waiting for forecast data";
    private OnPointSelectedListener onPointSelectedListener;

    public ForecastChartView(Context context) {
        super(context);
        init();
    }

    public ForecastChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ForecastChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;

        gridPaint.setColor(ContextCompat.getColor(getContext(), R.color.weather_divider));
        gridPaint.setStrokeWidth(Math.max(1f, density));

        linePaint.setColor(ContextCompat.getColor(getContext(), R.color.weather_sky_blue));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeWidth(2.5f * density);

        pointPaint.setColor(ContextCompat.getColor(getContext(), R.color.weather_aqua));
        pointPaint.setStyle(Paint.Style.FILL);

        barPaint.setColor(ContextCompat.getColor(getContext(), R.color.weather_rain_blue));
        barPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.weather_text_tertiary));
        textPaint.setTextSize(10f * scaledDensity);
        textPaint.setTextAlign(Paint.Align.CENTER);

        selectionPaint.setColor(ContextCompat.getColor(getContext(), R.color.weather_aqua));
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(Math.max(1.5f, 1.4f * density));

        setMinimumHeight(Math.round(180f * density));
        setClickable(true);
        setFocusable(true);
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.TEMPERATURE : mode;
        invalidate();
    }

    public void setData(@Nullable List<String> newLabels, @Nullable List<Double> newValues) {
        labels.clear();
        values.clear();
        if (newLabels != null) labels.addAll(newLabels);
        if (newValues != null) values.addAll(newValues);
        if (selectedIndex >= Math.min(labels.size(), values.size())) selectedIndex = -1;
        invalidate();
    }

    public void setSelectedIndex(int index) {
        int count = Math.min(labels.size(), values.size());
        selectedIndex = index >= 0 && index < count ? index : -1;
        invalidate();
    }

    public void setEmptyMessage(@Nullable String message) {
        emptyMessage = message == null || message.trim().isEmpty()
                ? "Waiting for forecast data"
                : message;
        invalidate();
    }

    public void setOnPointSelectedListener(@Nullable OnPointSelectedListener listener) {
        onPointSelectedListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int count = Math.min(labels.size(), values.size());
            if (count > 0) {
                ChartBounds bounds = chartBounds();
                float x = Math.max(bounds.left, Math.min(bounds.right, event.getX()));
                float fraction = (x - bounds.left) / Math.max(1f, bounds.width());
                int index = Math.round(fraction * Math.max(0, count - 1));
                setSelectedIndex(index);
                if (onPointSelectedListener != null) onPointSelectedListener.onPointSelected(index);
                performClick();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = Math.min(labels.size(), values.size());
        if (count < 2) {
            drawEmpty(canvas);
            return;
        }
        if (mode == Mode.PRECIPITATION) drawPrecipitation(canvas, count);
        else drawTemperature(canvas, count);
    }

    private void drawTemperature(Canvas canvas, int count) {
        List<Double> finiteValues = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Double value = displayTemperature(values.get(i));
            if (value != null && Double.isFinite(value)) finiteValues.add(value);
        }
        if (finiteValues.size() < 2) {
            drawEmpty(canvas);
            return;
        }

        double min = Collections.min(finiteValues);
        double max = Collections.max(finiteValues);
        if (Math.abs(max - min) < 0.5) {
            max += 1.0;
            min -= 1.0;
        }
        double padding = Math.max(1.0, (max - min) * 0.15);
        min -= padding;
        max += padding;

        ChartBounds bounds = chartBounds();
        drawGrid(canvas, bounds);

        Path path = new Path();
        boolean started = false;
        for (int i = 0; i < count; i++) {
            Double value = displayTemperature(values.get(i));
            if (value == null || !Double.isFinite(value)) continue;
            float x = pointX(bounds, count, i);
            float y = mapY(value, min, max, bounds);
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
            canvas.drawCircle(x, y, dp(i == selectedIndex ? 4.2f : 2.4f), pointPaint);
            if (i == selectedIndex) drawSelection(canvas, bounds, x, y, value, false);
        }
        canvas.drawPath(path, linePaint);

        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(String.format(Locale.getDefault(), "%.0f°", max), bounds.left, bounds.top + sp(9), textPaint);
        canvas.drawText(String.format(Locale.getDefault(), "%.0f°", min), bounds.left, bounds.bottom, textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        drawXAxisLabels(canvas, bounds, count);
    }

    @Nullable
    private Double displayTemperature(@Nullable Double celsius) {
        return WeatherFormatter.temperatureValue(celsius);
    }

    private void drawPrecipitation(Canvas canvas, int count) {
        ChartBounds bounds = chartBounds();
        drawGrid(canvas, bounds);
        float slot = bounds.width() / count;
        float barWidth = Math.max(dp(3), slot * 0.58f);

        for (int i = 0; i < count; i++) {
            Double raw = values.get(i);
            double value = raw == null || !Double.isFinite(raw)
                    ? 0.0
                    : Math.max(0.0, Math.min(100.0, raw));
            float xCenter = bounds.left + slot * i + slot / 2f;
            float y = mapY(value, 0.0, 100.0, bounds);
            canvas.drawRoundRect(
                    xCenter - barWidth / 2f,
                    y,
                    xCenter + barWidth / 2f,
                    bounds.bottom,
                    dp(3),
                    dp(3),
                    barPaint
            );
            if (i == selectedIndex) drawSelection(canvas, bounds, xCenter, y, value, true);
        }

        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("100%", bounds.left, bounds.top + sp(9), textPaint);
        canvas.drawText("0%", bounds.left, bounds.bottom, textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        drawXAxisLabels(canvas, bounds, count);
    }

    private void drawSelection(
            Canvas canvas,
            ChartBounds bounds,
            float x,
            float y,
            double value,
            boolean percent
    ) {
        canvas.drawLine(x, bounds.top, x, bounds.bottom, selectionPaint);
        String valueText = percent
                ? String.format(Locale.getDefault(), "%.0f%%", value)
                : String.format(Locale.getDefault(), "%.0f°", value);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(valueText, x, Math.max(bounds.top + sp(11), y - dp(8)), textPaint);
    }

    private void drawGrid(Canvas canvas, ChartBounds bounds) {
        for (int i = 0; i <= 3; i++) {
            float y = bounds.top + bounds.height() * i / 3f;
            canvas.drawLine(bounds.left, y, bounds.right, y, gridPaint);
        }
    }

    private void drawXAxisLabels(Canvas canvas, ChartBounds bounds, int count) {
        int availableLabels = Math.max(3, getWidth() / Math.max(dp(64), 1));
        int step = Math.max(1, (int) Math.ceil(count / (double) availableLabels));
        for (int i = 0; i < count; i += step) {
            canvas.drawText(shortLabel(labels.get(i)), pointX(bounds, count, i), getHeight() - dp(7), textPaint);
        }
        if ((count - 1) % step != 0) {
            canvas.drawText(shortLabel(labels.get(count - 1)), bounds.right, getHeight() - dp(7), textPaint);
        }
    }

    private void drawEmpty(Canvas canvas) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(emptyMessage, getWidth() / 2f, getHeight() / 2f, textPaint);
    }

    private String shortLabel(String label) {
        if (label == null || label.trim().isEmpty()) return "—";
        return label.replace(" ", "");
    }

    private float pointX(ChartBounds bounds, int count, int index) {
        return bounds.left + (bounds.width() * index / (float) Math.max(1, count - 1));
    }

    private float mapY(double value, double min, double max, ChartBounds bounds) {
        double fraction = (value - min) / Math.max(0.0001, max - min);
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        return (float) (bounds.bottom - fraction * bounds.height());
    }

    private ChartBounds chartBounds() {
        return new ChartBounds(getWidth(), getHeight(), dp(26), dp(18), dp(22), dp(30));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static final class ChartBounds {
        final float left;
        final float top;
        final float right;
        final float bottom;

        ChartBounds(int width, int height, float left, float top, float rightPadding, float bottomPadding) {
            this.left = left;
            this.top = top;
            this.right = Math.max(left + 1f, width - rightPadding);
            this.bottom = Math.max(top + 1f, height - bottomPadding);
        }

        float width() { return right - left; }
        float height() { return bottom - top; }
    }
}
