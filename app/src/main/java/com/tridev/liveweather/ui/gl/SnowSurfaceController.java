package com.tridev.liveweather.ui.gl;

import androidx.annotation.VisibleForTesting;

/**
 * Stage 12 retained surface-snow model shared by App Hero and Live Wallpaper.
 *
 * Falling snow remains authoritative in GlSceneSnapshot.snowIntensity. This controller owns
 * only surface presentation memory: observed ground snow can remain visible after snowfall
 * stops, fresh snowfall can build between provider refreshes, and genuinely warm surfaces
 * melt the retained pack gradually. It never creates snowfall, frost, ice or a weather code.
 *
 * Production instances share one process state and a monotonic advance gate so simultaneous
 * app/wallpaper render loops do not make accumulation or melt run twice as fast.
 */
public final class SnowSurfaceController {

    private static final Object SHARED_LOCK = new Object();
    private static final State SHARED_STATE = new State();
    private static long sharedLastAdvanceNanos;

    private final boolean shared;
    private final State localState;

    public SnowSurfaceController() {
        shared = true;
        localState = null;
    }

    @VisibleForTesting
    static SnowSurfaceController isolatedForTest() {
        return new SnowSurfaceController(false);
    }

    private SnowSurfaceController(boolean shared) {
        this.shared = shared;
        this.localState = shared ? null : new State();
    }

    public void advance(
            float observedSnowDepthMeters,
            float fallingSnowIntensity,
            float surfaceTemperatureC,
            float rainIntensity,
            float drizzleIntensity,
            float sceneLight,
            float deltaSeconds
    ) {
        if (shared) {
            long now = System.nanoTime();
            synchronized (SHARED_LOCK) {
                float elapsed;
                if (sharedLastAdvanceNanos == 0L) {
                    elapsed = clamp(deltaSeconds, 0f, 0.25f);
                } else {
                    elapsed = clamp((now - sharedLastAdvanceNanos) / 1_000_000_000f, 0f, 0.25f);
                }
                if (elapsed < 0.006f && sharedLastAdvanceNanos != 0L) {
                    return;
                }
                sharedLastAdvanceNanos = now;
                advanceState(
                        SHARED_STATE,
                        observedSnowDepthMeters,
                        fallingSnowIntensity,
                        surfaceTemperatureC,
                        rainIntensity,
                        drizzleIntensity,
                        sceneLight,
                        elapsed
                );
            }
            return;
        }

        advanceState(
                localState,
                observedSnowDepthMeters,
                fallingSnowIntensity,
                surfaceTemperatureC,
                rainIntensity,
                drizzleIntensity,
                sceneLight,
                clamp(deltaSeconds, 0f, 2f)
        );
    }

    public float getCoverage() {
        return read(Field.COVERAGE);
    }

    public float getPack() {
        return read(Field.PACK);
    }

    public float getFreshness() {
        return read(Field.FRESHNESS);
    }

    /** Normalized visual melt-water production; it is not atmospheric rain truth. */
    public float getMeltWaterIntensity() {
        return read(Field.MELT_WATER);
    }

    @VisibleForTesting
    void seedForTest(float pack, float coverage, float freshness) {
        if (shared) return;
        localState.pack = clamp01(pack);
        localState.coverage = clamp01(coverage);
        localState.freshness = clamp01(freshness);
        localState.meltWater = 0f;
        localState.initialized = true;
        localState.hasObservedDepth = false;
    }

    private float read(Field field) {
        if (!shared) return field.read(localState);
        synchronized (SHARED_LOCK) {
            return field.read(SHARED_STATE);
        }
    }

    private static void advanceState(
            State state,
            float observedSnowDepthMeters,
            float fallingSnowIntensity,
            float surfaceTemperatureC,
            float rainIntensity,
            float drizzleIntensity,
            float sceneLight,
            float dt
    ) {
        if (dt <= 0f) return;

        float snow = clamp01(fallingSnowIntensity);
        float rain = clamp01(Math.max(rainIntensity, drizzleIntensity * 0.55f));
        float light = clamp01(sceneLight);
        float surfaceC = isFinite(surfaceTemperatureC) ? surfaceTemperatureC : 0f;
        boolean hasObservedDepth = isFinite(observedSnowDepthMeters)
                && observedSnowDepthMeters >= 0f;
        float observedDepth = hasObservedDepth ? Math.max(0f, observedSnowDepthMeters) : 0f;
        float observedCoverage = hasObservedDepth
                ? 1f - (float) Math.exp(-observedDepth / 0.025f)
                : 0f;
        float observedPack = hasObservedDepth ? clamp01(observedDepth / 0.15f) : 0f;

        boolean observationChanged = hasObservedDepth && (
                !state.hasObservedDepth
                        || Math.abs(observedDepth - state.lastObservedDepthMeters) > 0.0005f
        );

        // On startup or a materially new provider depth, authoritative ground snow is an anchor.
        if ((!state.initialized && hasObservedDepth) || observationChanged) {
            state.pack = Math.max(observedPack, snow * 0.08f);
            state.coverage = Math.max(observedCoverage, smoothstep(0.02f, 0.42f, snow));
            if (!state.initialized) {
                state.freshness = snow > 0.02f ? 0.82f : (observedDepth > 0f ? 0.42f : 0f);
            }
        }
        state.initialized = true;
        if (hasObservedDepth) {
            state.hasObservedDepth = true;
            state.lastObservedDepthMeters = observedDepth;
        }

        // Snow can build visually between provider refreshes only when the surface can retain it.
        float coldRetention = 1f - smoothstep(0.4f, 2.2f, surfaceC);
        if (snow > 0.003f && coldRetention > 0f) {
            float addition = snow * coldRetention * dt * 0.000085f;
            state.pack = clamp01(state.pack + addition);
            float coverTarget = Math.max(state.coverage, smoothstep(0.015f, 0.48f, snow));
            state.coverage = approach(state.coverage, coverTarget, dt, 12f);
            state.freshness = approach(state.freshness, 1f, dt, 75f);
        } else {
            // Fresh whiteness ages slowly; retained mass does not vanish just because flakes stop.
            state.freshness = clamp01(state.freshness - dt / 21_600f);
        }

        // Positive surface temperature is required for active thaw. Warm rain accelerates it.
        float warmth = smoothstep(0.3f, 5.5f, surfaceC);
        float sunAssist = warmth * light * 0.35f;
        float rainAssist = warmth * rain * 0.65f;
        float beforeMelt = state.pack;
        if (state.pack > 0f && warmth > 0f) {
            float meltRate = (0.000012f + warmth * 0.000050f)
                    * (1f + sunAssist + rainAssist);
            state.pack = clamp01(state.pack - meltRate * dt);
        }

        float packLoss = Math.max(0f, beforeMelt - state.pack);
        float meltSignal = dt > 0f ? clamp01((packLoss / dt) * 720f) : 0f;
        state.meltWater = approach(
                state.meltWater,
                meltSignal,
                dt,
                meltSignal > state.meltWater ? 2.5f : 18f
        );

        // Coverage follows retained mass with a slower patchy retreat than pack depth.
        float massCoverage = state.pack <= 0f ? 0f : clamp01((float) Math.sqrt(state.pack));
        float retreatTau = warmth > 0.15f ? 38f : 90f;
        state.coverage = approach(
                state.coverage,
                Math.max(massCoverage, snow * 0.10f),
                dt,
                retreatTau
        );

        if (state.pack < 0.00015f && state.coverage < 0.001f) {
            state.pack = 0f;
            state.coverage = 0f;
            if (snow <= 0.003f) state.freshness = 0f;
        }

        state.pack = clamp01(state.pack);
        state.coverage = clamp01(state.coverage);
        state.freshness = clamp01(state.freshness);
        state.meltWater = clamp01(state.meltWater);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float approach(float from, float to, float dt, float tauSeconds) {
        float alpha = 1f - (float) Math.exp(-dt / Math.max(0.001f, tauSeconds));
        return from + (to - from) * alpha;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / Math.max(0.0001f, edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Field {
        COVERAGE { @Override float read(State state) { return state.coverage; } },
        PACK { @Override float read(State state) { return state.pack; } },
        FRESHNESS { @Override float read(State state) { return state.freshness; } },
        MELT_WATER { @Override float read(State state) { return state.meltWater; } };

        abstract float read(State state);
    }

    private static final class State {
        float pack;
        float coverage;
        float freshness;
        float meltWater;
        boolean initialized;
        boolean hasObservedDepth;
        float lastObservedDepthMeters;
    }
}
