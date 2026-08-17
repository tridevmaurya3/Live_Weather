package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Allocation-free temporal smoothing between resolved live-weather snapshots.
 *
 * Weather truth remains the target snapshot immediately. This controller owns
 * only the visual presentation state used by the GL renderers. The same mutable
 * snapshot is reused for the lifetime of the surface; no snapshot is allocated
 * from the frame loop.
 *
 * Reality R2-R7 uses exponential, frame-rate-independent easing. A short frame
 * clamp prevents a single janky/resume frame from visually teleporting clouds,
 * precipitation, celestial positions or atmosphere toward a new target.
 *
 * R9 centralizes the visual wind sample here so every renderer view receives the
 * same coherent gust strength and heading on the same process-wide monotonic clock.
 * Base weather wind remains separately smoothed and authoritative; gusts affect only
 * presentation. This also keeps Hero and Live Wallpaper phase-aligned without any
 * preferences, network access or allocations in the GL frame loop.
 */
final class GlSceneTransitionController {

    private static final float MAX_FRAME_SECONDS = 0.08f;

    private final UnifiedWindController windController = new UnifiedWindController();

    @Nullable
    private GlSceneSnapshot target;

    @Nullable
    private GlSceneSnapshot current;

    private long lastFrameNanos;
    private boolean transitioning;
    private boolean windInitialized;
    private float smoothedWindStrength;
    private float smoothedWindDirectionRadians;

    public void setTarget(@Nullable GlSceneSnapshot next) {
        target = next;
        if (next == null) {
            current = null;
            transitioning = false;
            lastFrameNanos = 0L;
            windInitialized = false;
            smoothedWindStrength = 0f;
            smoothedWindDirectionRadians = 0f;
            return;
        }

        if (current == null) {
            current = GlSceneSnapshot.reusableCopyOf(next);
            transitioning = false;
            smoothedWindStrength = next.windStrength;
            smoothedWindDirectionRadians = next.windDirectionRadians;
            windInitialized = true;
            applyUnifiedWind(current);
        } else {
            if (!windInitialized) {
                smoothedWindStrength = next.windStrength;
                smoothedWindDirectionRadians = next.windDirectionRadians;
                windInitialized = true;
            }
            transitioning = true;
        }
        lastFrameNanos = System.nanoTime();
    }

    @Nullable
    public GlSceneSnapshot current() {
        return current;
    }

    public boolean advance() {
        GlSceneSnapshot from = current;
        GlSceneSnapshot to = target;
        if (from == null || to == null) return false;

        long now = System.nanoTime();
        if (lastFrameNanos <= 0L) {
            lastFrameNanos = now;
            applyUnifiedWind(from);
            return true;
        }

        float dt = Math.min(
                MAX_FRAME_SECONDS,
                Math.max(0f, (now - lastFrameNanos) / 1_000_000_000f)
        );
        lastFrameNanos = now;

        if (transitioning && dt > 0f) {
            from.topR = approach(from.topR, to.topR, dt, 3.2f, 3.2f);
            from.topG = approach(from.topG, to.topG, dt, 3.2f, 3.2f);
            from.topB = approach(from.topB, to.topB, dt, 3.2f, 3.2f);
            from.midR = approach(from.midR, to.midR, dt, 3.0f, 3.0f);
            from.midG = approach(from.midG, to.midG, dt, 3.0f, 3.0f);
            from.midB = approach(from.midB, to.midB, dt, 3.0f, 3.0f);
            from.horizonR = approach(from.horizonR, to.horizonR, dt, 2.8f, 2.8f);
            from.horizonG = approach(from.horizonG, to.horizonG, dt, 2.8f, 2.8f);
            from.horizonB = approach(from.horizonB, to.horizonB, dt, 2.8f, 2.8f);

            from.sunX = approachUnitCycle(from.sunX, to.sunX, dt, 1.4f);
            from.sunY = approach(from.sunY, to.sunY, dt, 1.4f, 1.4f);
            from.sunVisibility = approach(from.sunVisibility, to.sunVisibility, dt, 1.8f, 2.4f);
            from.sunAltitude = approach(from.sunAltitude, to.sunAltitude, dt, 1.5f, 1.5f);

            from.moonX = approachUnitCycle(from.moonX, to.moonX, dt, 1.4f);
            from.moonY = approach(from.moonY, to.moonY, dt, 1.4f, 1.4f);
            from.moonVisibility = approach(from.moonVisibility, to.moonVisibility, dt, 1.9f, 2.5f);
            from.moonIllumination = approach(from.moonIllumination, to.moonIllumination, dt, 4.5f, 4.5f);
            from.moonPhaseAngleRadians = approachAngle(
                    from.moonPhaseAngleRadians,
                    to.moonPhaseAngleRadians,
                    dt,
                    4.5f
            );
            from.moonAltitude = approach(from.moonAltitude, to.moonAltitude, dt, 1.5f, 1.5f);
            from.starVisibility = approach(from.starVisibility, to.starVisibility, dt, 2.2f, 2.0f);
            from.observerLatitudeRadians = approach(
                    from.observerLatitudeRadians,
                    to.observerLatitudeRadians,
                    dt,
                    2.6f,
                    2.6f
            );
            from.localSiderealRadians = approachAngle(
                    from.localSiderealRadians,
                    to.localSiderealRadians,
                    dt,
                    1.6f
            );

            from.cloudCover = approach(from.cloudCover, to.cloudCover, dt, 3.4f, 4.4f);
            from.cloudDensity = approach(from.cloudDensity, to.cloudDensity, dt, 3.2f, 4.0f);
            from.cloudFarLayer = approach(from.cloudFarLayer, to.cloudFarLayer, dt, 3.8f, 4.6f);
            from.cloudMidLayer = approach(from.cloudMidLayer, to.cloudMidLayer, dt, 3.2f, 4.2f);
            from.cloudNearLayer = approach(from.cloudNearLayer, to.cloudNearLayer, dt, 2.8f, 3.8f);
            from.cloudStormCeiling = approach(from.cloudStormCeiling, to.cloudStormCeiling, dt, 1.2f, 3.0f);
            from.cloudBrightness = approach(from.cloudBrightness, to.cloudBrightness, dt, 2.6f, 2.8f);

            from.rainIntensity = approach(from.rainIntensity, to.rainIntensity, dt, 0.75f, 2.1f);
            from.drizzleIntensity = approach(from.drizzleIntensity, to.drizzleIntensity, dt, 0.95f, 1.8f);
            from.snowIntensity = approach(from.snowIntensity, to.snowIntensity, dt, 1.0f, 2.4f);
            from.fogIntensity = approach(from.fogIntensity, to.fogIntensity, dt, 2.0f, 3.8f);
            from.stormIntensity = approach(from.stormIntensity, to.stormIntensity, dt, 0.65f, 2.5f);
            from.airHazeIntensity = approach(from.airHazeIntensity, to.airHazeIntensity, dt, 3.0f, 4.8f);

            smoothedWindStrength = approach(
                    smoothedWindStrength,
                    to.windStrength,
                    dt,
                    1.25f,
                    2.2f
            );
            smoothedWindDirectionRadians = approachAngle(
                    smoothedWindDirectionRadians,
                    to.windDirectionRadians,
                    dt,
                    1.8f
            );
            from.sceneLight = approach(from.sceneLight, to.sceneLight, dt, 2.2f, 2.6f);
            from.thermalBias = approach(from.thermalBias, to.thermalBias, dt, 4.8f, 4.8f);
            from.visibilityFactor = approach(from.visibilityFactor, to.visibilityFactor, dt, 2.0f, 3.2f);
            from.parallax = approach(from.parallax, to.parallax, dt, 0.10f, 0.10f);

            if (isVisuallySettled(from, to)
                    && near(smoothedWindStrength, to.windStrength, 0.002f)
                    && Math.abs(wrapRadians(
                    smoothedWindDirectionRadians - to.windDirectionRadians
            )) < 0.008f) {
                from.copyFrom(to);
                smoothedWindStrength = to.windStrength;
                smoothedWindDirectionRadians = to.windDirectionRadians;
                transitioning = false;
            }
        }

        applyUnifiedWind(from);
        return true;
    }

    private void applyUnifiedWind(@NonNull GlSceneSnapshot state) {
        if (!windInitialized) {
            smoothedWindStrength = state.windStrength;
            smoothedWindDirectionRadians = state.windDirectionRadians;
            windInitialized = true;
        }
        windController.sample(
                smoothedWindStrength,
                smoothedWindDirectionRadians,
                state.stormIntensity,
                UnifiedWindController.sharedMonotonicSeconds()
        );
        state.windStrength = windController.getEffectiveStrength();
        state.windDirectionRadians = windController.getDirectionRadians();
    }

    private static float approach(float from, float to, float dt, float upTau, float downTau) {
        float tau = Math.max(0.001f, to >= from ? upTau : downTau);
        float alpha = 1f - (float) Math.exp(-dt / tau);
        return from + (to - from) * alpha;
    }

    private static float approachUnitCycle(float from, float to, float dt, float tau) {
        float delta = to - from;
        if (delta > 0.5f) delta -= 1f;
        if (delta < -0.5f) delta += 1f;
        float alpha = 1f - (float) Math.exp(-dt / Math.max(0.001f, tau));
        float value = from + delta * alpha;
        value %= 1f;
        return value < 0f ? value + 1f : value;
    }

    private static float approachAngle(float from, float to, float dt, float tau) {
        float delta = wrapRadians(to - from);
        float alpha = 1f - (float) Math.exp(-dt / Math.max(0.001f, tau));
        return wrapPositiveRadians(from + delta * alpha);
    }

    private static boolean isVisuallySettled(
            @NonNull GlSceneSnapshot a,
            @NonNull GlSceneSnapshot b
    ) {
        return near(a.topR, b.topR, 0.002f)
                && near(a.topG, b.topG, 0.002f)
                && near(a.topB, b.topB, 0.002f)
                && near(a.midR, b.midR, 0.002f)
                && near(a.midG, b.midG, 0.002f)
                && near(a.midB, b.midB, 0.002f)
                && near(a.horizonR, b.horizonR, 0.002f)
                && near(a.horizonG, b.horizonG, 0.002f)
                && near(a.horizonB, b.horizonB, 0.002f)
                && unitCycleDistance(a.sunX, b.sunX) < 0.002f
                && near(a.sunY, b.sunY, 0.002f)
                && near(a.sunVisibility, b.sunVisibility, 0.002f)
                && near(a.sunAltitude, b.sunAltitude, 0.05f)
                && unitCycleDistance(a.moonX, b.moonX) < 0.002f
                && near(a.moonY, b.moonY, 0.002f)
                && near(a.moonVisibility, b.moonVisibility, 0.002f)
                && near(a.moonIllumination, b.moonIllumination, 0.002f)
                && Math.abs(wrapRadians(a.moonPhaseAngleRadians - b.moonPhaseAngleRadians)) < 0.008f
                && near(a.moonAltitude, b.moonAltitude, 0.05f)
                && near(a.starVisibility, b.starVisibility, 0.002f)
                && near(a.observerLatitudeRadians, b.observerLatitudeRadians, 0.002f)
                && Math.abs(wrapRadians(a.localSiderealRadians - b.localSiderealRadians)) < 0.008f
                && near(a.cloudCover, b.cloudCover, 0.002f)
                && near(a.cloudDensity, b.cloudDensity, 0.002f)
                && near(a.cloudFarLayer, b.cloudFarLayer, 0.002f)
                && near(a.cloudMidLayer, b.cloudMidLayer, 0.002f)
                && near(a.cloudNearLayer, b.cloudNearLayer, 0.002f)
                && near(a.cloudStormCeiling, b.cloudStormCeiling, 0.002f)
                && near(a.cloudBrightness, b.cloudBrightness, 0.002f)
                && near(a.rainIntensity, b.rainIntensity, 0.002f)
                && near(a.drizzleIntensity, b.drizzleIntensity, 0.002f)
                && near(a.snowIntensity, b.snowIntensity, 0.002f)
                && near(a.fogIntensity, b.fogIntensity, 0.002f)
                && near(a.stormIntensity, b.stormIntensity, 0.002f)
                && near(a.airHazeIntensity, b.airHazeIntensity, 0.002f)
                && near(a.sceneLight, b.sceneLight, 0.002f)
                && near(a.thermalBias, b.thermalBias, 0.002f)
                && near(a.visibilityFactor, b.visibilityFactor, 0.002f)
                && near(a.parallax, b.parallax, 0.001f);
    }

    private static boolean near(float a, float b, float epsilon) {
        return Math.abs(a - b) < epsilon;
    }

    private static float unitCycleDistance(float a, float b) {
        float delta = Math.abs(a - b);
        return Math.min(delta, 1f - delta);
    }

    private static float wrapRadians(float value) {
        float twoPi = (float) (Math.PI * 2.0);
        value %= twoPi;
        if (value > Math.PI) value -= twoPi;
        if (value < -Math.PI) value += twoPi;
        return value;
    }

    private static float wrapPositiveRadians(float value) {
        float twoPi = (float) (Math.PI * 2.0);
        value %= twoPi;
        return value < 0f ? value + twoPi : value;
    }
}
