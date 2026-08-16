package com.tridev.liveweather.core.performance;

/**
 * Allocation-free frame-cost guard for the app Hero and Live Wallpaper.
 *
 * This class never changes weather truth or frame cadence. It observes only the
 * steady GL draw + buffer-swap cost and gently trims secondary renderer detail
 * after sustained pressure. Detail is restored more slowly after the renderer
 * remains comfortably inside budget, preventing quality oscillation.
 */
public final class AdaptiveFrameTimeGuard {

    private static final int WINDOW_SAMPLES = 12;
    private static final int PRESSURE_WINDOWS_TO_STEP_DOWN = 2;
    private static final int STABLE_WINDOWS_TO_STEP_UP = 4;
    private static final float MIN_DETAIL_SCALE = 0.50f;
    private static final float MIN_MULTIPLIER = 0.72f;
    private static final float STEP_DOWN = 0.08f;
    private static final float STEP_UP = 0.04f;

    private long baseFrameIntervalMillis = 33L;
    private float baseDetailScale = 0.82f;
    private float adaptiveMultiplier = 1f;
    private float effectiveDetailScale = 0.82f;

    private float ewmaCostMillis;
    private int sampleCount;
    private int pressureWindows;
    private int stableWindows;
    private boolean hasSample;

    /**
     * Updates the user/power-policy baseline. Repeated calls with the same
     * profile are intentionally no-ops so the adaptive history is preserved.
     */
    public float setBaseProfile(long frameIntervalMillis, float detailScale) {
        long boundedFrame = Math.max(16L, frameIntervalMillis);
        float boundedDetail = clamp(detailScale, MIN_DETAIL_SCALE, 1f);
        boolean changed = boundedFrame != baseFrameIntervalMillis
                || Math.abs(boundedDetail - baseDetailScale) > 0.001f;
        if (!changed) return effectiveDetailScale;

        baseFrameIntervalMillis = boundedFrame;
        baseDetailScale = boundedDetail;
        adaptiveMultiplier = 1f;
        effectiveDetailScale = baseDetailScale;
        resetSamples();
        return effectiveDetailScale;
    }

    /**
     * Records one successful GL draw + eglSwapBuffers cost.
     *
     * @return a new detail scale only when adaptation actually changed it;
     *         otherwise Float.NaN so callers can avoid redundant uniform writes.
     */
    public float observeFrameNanos(long elapsedNanos) {
        if (elapsedNanos <= 0L) return Float.NaN;

        float elapsedMillis = elapsedNanos / 1_000_000f;
        float maxUsefulSample = Math.max(50f, baseFrameIntervalMillis * 3.0f);
        elapsedMillis = Math.min(elapsedMillis, maxUsefulSample);

        if (!hasSample) {
            ewmaCostMillis = elapsedMillis;
            hasSample = true;
        } else {
            // Low-cost smoothing: enough memory to ignore one-off driver spikes.
            ewmaCostMillis = ewmaCostMillis * 0.88f + elapsedMillis * 0.12f;
        }

        sampleCount++;
        if (sampleCount < WINDOW_SAMPLES) return Float.NaN;
        sampleCount = 0;

        // Leave headroom for handler scheduling and occasional lightweight state work.
        float pressureBudget = Math.max(8f, baseFrameIntervalMillis * 0.82f);
        float pressureRatio = ewmaCostMillis / pressureBudget;

        if (pressureRatio >= 0.94f) {
            pressureWindows++;
            stableWindows = 0;
            if (pressureWindows >= PRESSURE_WINDOWS_TO_STEP_DOWN) {
                pressureWindows = 0;
                float minMultiplierForBase = Math.max(
                        MIN_MULTIPLIER,
                        MIN_DETAIL_SCALE / Math.max(MIN_DETAIL_SCALE, baseDetailScale)
                );
                float next = Math.max(minMultiplierForBase, adaptiveMultiplier - STEP_DOWN);
                return applyMultiplierIfChanged(next);
            }
            return Float.NaN;
        }

        if (pressureRatio <= 0.62f) {
            stableWindows++;
            pressureWindows = 0;
            if (stableWindows >= STABLE_WINDOWS_TO_STEP_UP) {
                stableWindows = 0;
                float next = Math.min(1f, adaptiveMultiplier + STEP_UP);
                return applyMultiplierIfChanged(next);
            }
            return Float.NaN;
        }

        // Neutral zone prevents rapid quality toggling around a threshold.
        pressureWindows = Math.max(0, pressureWindows - 1);
        stableWindows = Math.max(0, stableWindows - 1);
        return Float.NaN;
    }

    public float getEffectiveDetailScale() {
        return effectiveDetailScale;
    }

    private float applyMultiplierIfChanged(float nextMultiplier) {
        nextMultiplier = clamp(nextMultiplier, MIN_MULTIPLIER, 1f);
        float nextDetail = clamp(
                baseDetailScale * nextMultiplier,
                MIN_DETAIL_SCALE,
                baseDetailScale
        );
        if (Math.abs(nextDetail - effectiveDetailScale) < 0.015f) {
            adaptiveMultiplier = nextMultiplier;
            return Float.NaN;
        }

        adaptiveMultiplier = nextMultiplier;
        effectiveDetailScale = nextDetail;
        return effectiveDetailScale;
    }

    private void resetSamples() {
        ewmaCostMillis = 0f;
        sampleCount = 0;
        pressureWindows = 0;
        stableWindows = 0;
        hasSample = false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
