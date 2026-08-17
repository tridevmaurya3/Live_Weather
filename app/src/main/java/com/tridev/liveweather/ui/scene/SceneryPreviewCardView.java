package com.tridev.liveweather.ui.scene;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.domain.scene.SceneryMode;

/**
 * Lightweight procedural thumbnail used by the Wallpaper scenery library.
 *
 * This is intentionally a UI-only representation of scenery identity. It does not render
 * or infer live weather, astronomy, precipitation, cloud state or forecast probability.
 * No bitmap, texture, network request or animation loop is used here.
 */
public final class SceneryPreviewCardView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final RectF previewRect = new RectF();
    private final RectF tempRect = new RectF();
    private final Path path = new Path();

    @NonNull private SceneryMode mode = SceneryMode.NATURAL_HILLS;
    @NonNull private SceneryMode autoResolvedMode = SceneryMode.NATURAL_HILLS;
    @NonNull private String label = "Natural Hills";
    private int variant;

    public SceneryPreviewCardView(@NonNull Context context) {
        this(context, null);
    }

    public SceneryPreviewCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SceneryPreviewCardView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        setClickable(true);
        setFocusable(true);
        setMinimumWidth(dp(132));
        setMinimumHeight(dp(148));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setScene(@NonNull SceneryMode value, @NonNull String displayLabel) {
        mode = value;
        label = displayLabel;
        invalidate();
    }

    public void setVariant(int value) {
        int bounded = Math.max(0, Math.min(3, value));
        if (variant == bounded) return;
        variant = bounded;
        invalidate();
    }

    public void setAutoResolvedMode(@NonNull SceneryMode value) {
        SceneryMode concrete = value == SceneryMode.AUTO ? SceneryMode.NATURAL_HILLS : value;
        if (autoResolvedMode == concrete) return;
        autoResolvedMode = concrete;
        invalidate();
    }

    @Override
    public void setSelected(boolean selected) {
        boolean changed = selected != isSelected();
        super.setSelected(selected);
        if (changed) invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = dp(136);
        int desiredHeight = dp(152);
        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 1f || height <= 1f) return;

        float outerInset = dpF(1f);
        cardRect.set(outerInset, outerInset, width - outerInset, height - outerInset);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(Color.rgb(14, 32, 50));
        canvas.drawRoundRect(cardRect, dpF(16f), dpF(16f), paint);

        strokePaint.setShader(null);
        strokePaint.setStrokeWidth(dpF(isSelected() ? 2f : 1f));
        strokePaint.setColor(isSelected() ? Color.rgb(124, 232, 225) : Color.argb(92, 220, 235, 255));
        canvas.drawRoundRect(cardRect, dpF(16f), dpF(16f), strokePaint);

        float previewLeft = dpF(7f);
        float previewTop = dpF(7f);
        float previewRight = width - dpF(7f);
        float previewBottom = height - dpF(43f);
        previewRect.set(previewLeft, previewTop, previewRight, previewBottom);

        int save = canvas.save();
        canvas.clipRoundRect(previewRect, dpF(12f), dpF(12f));
        drawPreview(canvas, mode == SceneryMode.AUTO ? autoResolvedMode : mode);
        canvas.restoreToCount(save);

        drawFooter(canvas, width, height);
        if (mode == SceneryMode.AUTO) drawAutoBadge(canvas);
    }

    private void drawPreview(@NonNull Canvas canvas, @NonNull SceneryMode renderMode) {
        float l = previewRect.left;
        float t = previewRect.top;
        float r = previewRect.right;
        float b = previewRect.bottom;
        float w = previewRect.width();
        float h = previewRect.height();
        float phase = variant * 0.17f;

        int skyTop = Color.rgb(54 + variant * 4, 103 + variant * 3, 151 + variant * 5);
        int skyBottom = Color.rgb(154 + variant * 3, 193 + variant * 2, 217 + variant * 2);
        paint.setShader(new LinearGradient(l, t, l, b, skyTop, skyBottom, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(previewRect, paint);
        paint.setShader(null);

        float sunX = l + w * (0.72f - phase * 0.35f);
        float sunY = t + h * (0.22f + phase * 0.10f);
        paint.setColor(Color.argb(220, 255, 215, 156));
        canvas.drawCircle(sunX, sunY, dpF(7.5f), paint);

        switch (renderMode) {
            case OPEN_SKY:
                drawOpenSky(canvas, phase);
                break;
            case VILLAGE:
                drawHills(canvas, phase, false);
                drawVillage(canvas, phase);
                break;
            case FARM_CROPS:
                drawFarm(canvas, phase);
                break;
            case RIVER_LAKE:
                drawHills(canvas, phase, true);
                drawRiver(canvas, phase);
                break;
            case FLOWERS_GREENERY:
                drawFlowers(canvas, phase);
                break;
            case URBAN_BUILDINGS:
                drawUrban(canvas, phase);
                break;
            case NATURAL_HILLS:
            default:
                drawHills(canvas, phase, false);
                break;
        }
    }

    private void drawOpenSky(@NonNull Canvas canvas, float phase) {
        float groundY = previewRect.bottom - dpF(13f + phase * 5f);
        paint.setColor(Color.rgb(57, 93, 78));
        canvas.drawRect(previewRect.left, groundY, previewRect.right, previewRect.bottom, paint);

        strokePaint.setStrokeWidth(dpF(1.2f));
        strokePaint.setColor(Color.argb(105, 242, 249, 255));
        float y = previewRect.top + previewRect.height() * (0.47f + phase * 0.05f);
        path.reset();
        path.moveTo(previewRect.left + dpF(10f), y);
        path.cubicTo(
                previewRect.left + previewRect.width() * 0.33f, y - dpF(4f),
                previewRect.left + previewRect.width() * 0.67f, y + dpF(3f),
                previewRect.right - dpF(10f), y - dpF(1f)
        );
        canvas.drawPath(path, strokePaint);
    }

    private void drawHills(@NonNull Canvas canvas, float phase, boolean lowerForWater) {
        float h = previewRect.height();
        float base = previewRect.bottom - (lowerForWater ? h * 0.32f : h * 0.11f);

        path.reset();
        path.moveTo(previewRect.left, base);
        path.cubicTo(
                previewRect.left + previewRect.width() * 0.18f,
                base - h * (0.28f + phase * 0.05f),
                previewRect.left + previewRect.width() * 0.37f,
                base - h * (0.18f - phase * 0.03f),
                previewRect.left + previewRect.width() * 0.52f,
                base - h * 0.25f
        );
        path.cubicTo(
                previewRect.left + previewRect.width() * 0.68f,
                base - h * (0.34f - phase * 0.05f),
                previewRect.left + previewRect.width() * 0.82f,
                base - h * 0.15f,
                previewRect.right,
                base - h * (0.22f + phase * 0.04f)
        );
        path.lineTo(previewRect.right, previewRect.bottom);
        path.lineTo(previewRect.left, previewRect.bottom);
        path.close();
        paint.setColor(Color.rgb(78, 114, 102));
        canvas.drawPath(path, paint);

        path.reset();
        float nearBase = previewRect.bottom;
        path.moveTo(previewRect.left, nearBase - h * 0.18f);
        path.cubicTo(
                previewRect.left + previewRect.width() * 0.30f,
                nearBase - h * (0.30f - phase * 0.04f),
                previewRect.left + previewRect.width() * 0.62f,
                nearBase - h * (0.22f + phase * 0.04f),
                previewRect.right,
                nearBase - h * 0.27f
        );
        path.lineTo(previewRect.right, nearBase);
        path.lineTo(previewRect.left, nearBase);
        path.close();
        paint.setColor(Color.rgb(52, 86, 72));
        canvas.drawPath(path, paint);
    }

    private void drawVillage(@NonNull Canvas canvas, float phase) {
        float ground = previewRect.bottom - dpF(13f);
        drawHouse(canvas, previewRect.left + previewRect.width() * (0.19f + phase * 0.10f), ground, 0.84f);
        drawHouse(canvas, previewRect.left + previewRect.width() * (0.58f - phase * 0.08f), ground + dpF(1f), 1.00f);

        strokePaint.setStrokeWidth(dpF(2f));
        strokePaint.setColor(Color.argb(155, 213, 193, 145));
        path.reset();
        path.moveTo(previewRect.left + previewRect.width() * 0.45f, previewRect.bottom);
        path.cubicTo(
                previewRect.left + previewRect.width() * 0.48f,
                previewRect.bottom - dpF(13f),
                previewRect.left + previewRect.width() * 0.55f,
                previewRect.bottom - dpF(22f),
                previewRect.left + previewRect.width() * 0.58f,
                previewRect.bottom - dpF(30f)
        );
        canvas.drawPath(path, strokePaint);
    }

    private void drawHouse(@NonNull Canvas canvas, float x, float ground, float scale) {
        float bodyW = dpF(22f) * scale;
        float bodyH = dpF(16f) * scale;
        tempRect.set(x, ground - bodyH, x + bodyW, ground);
        paint.setColor(Color.rgb(190, 158, 119));
        canvas.drawRoundRect(tempRect, dpF(2f), dpF(2f), paint);

        path.reset();
        path.moveTo(x - dpF(2f), ground - bodyH);
        path.lineTo(x + bodyW * 0.5f, ground - bodyH - dpF(9f) * scale);
        path.lineTo(x + bodyW + dpF(2f), ground - bodyH);
        path.close();
        paint.setColor(Color.rgb(119, 77, 62));
        canvas.drawPath(path, paint);

        paint.setColor(Color.rgb(70, 88, 84));
        tempRect.set(x + bodyW * 0.42f, ground - bodyH * 0.54f, x + bodyW * 0.62f, ground);
        canvas.drawRect(tempRect, paint);
    }

    private void drawFarm(@NonNull Canvas canvas, float phase) {
        float horizon = previewRect.top + previewRect.height() * (0.48f - phase * 0.04f);
        paint.setColor(Color.rgb(85, 123, 73));
        canvas.drawRect(previewRect.left, horizon, previewRect.right, previewRect.bottom, paint);

        float vanishingX = previewRect.left + previewRect.width() * (0.50f + phase * 0.15f);
        strokePaint.setStrokeWidth(dpF(1.7f));
        for (int i = -4; i <= 4; i++) {
            float bottomX = previewRect.left + previewRect.width() * (0.5f + i * 0.13f);
            strokePaint.setColor((i & 1) == 0 ? Color.rgb(207, 185, 105) : Color.rgb(126, 153, 76));
            canvas.drawLine(vanishingX, horizon, bottomX, previewRect.bottom, strokePaint);
        }

        paint.setColor(Color.rgb(225, 202, 111));
        int heads = 5 + variant;
        for (int i = 0; i < heads; i++) {
            float x = previewRect.left + previewRect.width() * (0.10f + i * (0.80f / Math.max(1, heads - 1)));
            float y = previewRect.bottom - dpF(11f + (i % 2) * 5f);
            canvas.drawCircle(x, y, dpF(1.8f), paint);
        }
    }

    private void drawRiver(@NonNull Canvas canvas, float phase) {
        float waterTop = previewRect.top + previewRect.height() * (0.58f + phase * 0.03f);
        paint.setShader(new LinearGradient(
                previewRect.left,
                waterTop,
                previewRect.left,
                previewRect.bottom,
                Color.rgb(76, 133, 155),
                Color.rgb(39, 92, 120),
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(previewRect.left, waterTop, previewRect.right, previewRect.bottom, paint);
        paint.setShader(null);

        strokePaint.setStrokeWidth(dpF(1f));
        strokePaint.setColor(Color.argb(145, 199, 231, 239));
        for (int line = 0; line < 4; line++) {
            float y = waterTop + dpF(8f + line * 7f);
            path.reset();
            path.moveTo(previewRect.left + dpF(7f), y);
            path.cubicTo(
                    previewRect.left + previewRect.width() * 0.30f, y - dpF(2f + variant * 0.4f),
                    previewRect.left + previewRect.width() * 0.68f, y + dpF(2f),
                    previewRect.right - dpF(7f), y - dpF(1f)
            );
            canvas.drawPath(path, strokePaint);
        }

        paint.setColor(Color.rgb(62, 91, 64));
        path.reset();
        path.moveTo(previewRect.left, waterTop + dpF(2f));
        path.lineTo(previewRect.left + previewRect.width() * (0.27f + phase * 0.10f), waterTop - dpF(6f));
        path.lineTo(previewRect.left + previewRect.width() * 0.36f, waterTop + dpF(4f));
        path.lineTo(previewRect.left, waterTop + dpF(10f));
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawFlowers(@NonNull Canvas canvas, float phase) {
        float ground = previewRect.top + previewRect.height() * (0.54f - phase * 0.04f);
        paint.setColor(Color.rgb(57, 111, 68));
        canvas.drawRect(previewRect.left, ground, previewRect.right, previewRect.bottom, paint);

        strokePaint.setStrokeWidth(dpF(1.1f));
        strokePaint.setColor(Color.rgb(102, 158, 88));
        int count = 7 + variant;
        for (int i = 0; i < count; i++) {
            float x = previewRect.left + previewRect.width() * (0.08f + (i * 0.84f / Math.max(1, count - 1)));
            float stemTop = previewRect.bottom - dpF(10f + ((i + variant) % 3) * 7f);
            canvas.drawLine(x, previewRect.bottom, x + ((i & 1) == 0 ? dpF(1f) : -dpF(1f)), stemTop, strokePaint);
            paint.setColor((i % 3) == 0
                    ? Color.rgb(255, 211, 160)
                    : (i % 3) == 1 ? Color.rgb(233, 172, 204) : Color.rgb(214, 225, 139));
            canvas.drawCircle(x, stemTop, dpF(2.3f), paint);
        }
    }

    private void drawUrban(@NonNull Canvas canvas, float phase) {
        float ground = previewRect.bottom - dpF(9f);
        paint.setColor(Color.rgb(50, 64, 75));
        canvas.drawRect(previewRect.left, ground, previewRect.right, previewRect.bottom, paint);

        int buildings = 5 + (variant % 2);
        float usable = previewRect.width() - dpF(16f);
        float gap = dpF(3f);
        float buildingW = (usable - gap * (buildings - 1)) / buildings;
        for (int i = 0; i < buildings; i++) {
            float x = previewRect.left + dpF(8f) + i * (buildingW + gap);
            float bh = dpF(27f + ((i + variant) % 3) * 10f + phase * 8f);
            int shade = 65 + (i % 3) * 10;
            paint.setColor(Color.rgb(shade, shade + 8, shade + 15));
            tempRect.set(x, ground - bh, x + buildingW, ground);
            canvas.drawRect(tempRect, paint);

            paint.setColor(Color.argb(170, 255, 219, 151));
            float windowY = ground - bh + dpF(6f);
            while (windowY < ground - dpF(5f)) {
                canvas.drawRect(
                        x + dpF(3f),
                        windowY,
                        Math.min(x + buildingW - dpF(3f), x + dpF(6f)),
                        windowY + dpF(2f),
                        paint
                );
                windowY += dpF(7f);
            }
        }
    }

    private void drawFooter(@NonNull Canvas canvas, float width, float height) {
        float left = dpF(10f);
        float textY = height - dpF(20f);
        float maxLabelWidth = width - dpF(52f);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(null);
        paint.setColor(Color.rgb(246, 250, 255));
        paint.setTextSize(sp(12f));
        if (paint.measureText(label) > maxLabelWidth) {
            paint.setTextSize(sp(10.5f));
        }
        canvas.drawText(label, left, textY, paint);

        String variantText = "V" + (variant + 1);
        paint.setTextSize(sp(10.5f));
        float badgeW = Math.max(dpF(26f), paint.measureText(variantText) + dpF(10f));
        tempRect.set(width - badgeW - dpF(8f), height - dpF(33f), width - dpF(8f), height - dpF(10f));
        paint.setColor(isSelected() ? Color.argb(55, 124, 232, 225) : Color.argb(48, 184, 199, 214));
        canvas.drawRoundRect(tempRect, dpF(8f), dpF(8f), paint);
        paint.setColor(isSelected() ? Color.rgb(124, 232, 225) : Color.rgb(184, 199, 214));
        float tx = tempRect.centerX() - paint.measureText(variantText) * 0.5f;
        float ty = tempRect.centerY() - (paint.ascent() + paint.descent()) * 0.5f;
        canvas.drawText(variantText, tx, ty, paint);
    }

    private void drawAutoBadge(@NonNull Canvas canvas) {
        String text = "AUTO";
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(sp(8.5f));
        float paddingX = dpF(5f);
        float badgeW = paint.measureText(text) + paddingX * 2f;
        tempRect.set(
                previewRect.left + dpF(6f),
                previewRect.top + dpF(6f),
                previewRect.left + dpF(6f) + badgeW,
                previewRect.top + dpF(24f)
        );
        paint.setColor(Color.argb(190, 5, 18, 31));
        canvas.drawRoundRect(tempRect, dpF(7f), dpF(7f), paint);
        paint.setColor(Color.rgb(124, 232, 225));
        float tx = tempRect.centerX() - paint.measureText(text) * 0.5f;
        float ty = tempRect.centerY() - (paint.ascent() + paint.descent()) * 0.5f;
        canvas.drawText(text, tx, ty, paint);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dpF(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
