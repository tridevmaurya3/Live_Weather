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
 */
final class GlSceneTransitionController {

    private static final float MAX_FRAME_SECONDS = 0.12f;

    @Nullable
    private GlSceneSnapshot target;

    @Nullable
    private GlSceneSnapshot current;

    private long lastFrameNanos;
    private boolean transitioning;

    public void setTarget(@Nullable GlSceneSnapshot next) {
        target = next;
        if (next == null) {
            current = null;
            transitioning = false;
            lastFrameNanos = 0L;
            return;
        }

        if (current == null) {
            current = GlSceneSnapshot.reusableCopyOf(next);
            transitioning = false;
        } else {
            transitioning = true;
        }
        lastFrameNanos = System.nanoTime();
    }

    @Nullable
    public GlSceneSnapshot current() {
        return current;
    }

    /**
     * Advances the reusable display state. Returns true only when renderer-facing
     * values changed and therefore need copying into the renderer-specific views.
     */
    public boolean advance() {
        GlSceneSnapshot from = current;
        GlSceneSnapshot to = target;
        if (!transitioning || from == null || to == null) return false;

        long now = System.nanoTime();
        if (lastFrameNanos <= 0L) {
            lastFrameNanos = now;
            return false;
        }

        float dt = Math.min(
                MAX_FRAME_SECONDS,
                Math.max(0f, (now - lastFrameNanos) / 1_000_000_000f)
        );
        lastFrameNanos = now;
        if (dt <= 0f) return false;

        // Sky / ambient light: slow enough to feel atmospheric, not like a page refresh.
        from.topR = approach(from.topR, to.topR, dt, 3.2f, 3.2f);
        from.topG = approach(from.topG, to.topG, dt, 3.2f, 3.2f);
        from.topB = approach(from.topB, to.topB, dt, 3.2f, 3.2f);
        from.midR = approach(from.midR, to.midR, dt, 3.0f, 3.0f);
        from.midG = approach(from.midG, to.midG, dt, 3.0f, 3.0f);
        from.midB = approach(from.midB, to.midB, dt, 3.0f, 3.0f);
        from.horizonR = approach(from.horizonR, to.horizonR, dt, 2.8f, 2.8f);
        from.horizonG = approach(from.horizonG, to.horizonG, dt, 2.8f, 2.8f);
        from.horizonB = approach(from.horizonB, to.horizonB, dt, 2.8f, 2.8f);

        // Celestial positions use wrap-safe interpolation across azimuth 0/360.
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

        // Clouds build and clear gradually like a real sky mass rather than switching sprites.
        from.cloudCover = approach(from.cloudCover, to.cloudCover, dt, 3.4f, 4.4f);
        from.cloudDensity = approach(from.cloudDensity, to.cloudDensity, dt, 3.2f, 4.0f);
        from.cloudFarLayer = approach(from.cloudFarLayer, to.cloudFarLayer, dt, 3.8f, 4.6f);
        from.cloudMidLayer = approach(from.cloudMidLayer, to.cloudMidLayer, dt, 3.2f, 4.2f);
        from.cloudNearLayer = approach(from.cloudNearLayer, to.cloudNearLayer, dt, 2.8f, 3.8f);
        from.cloudStormCeiling = approach(from.cloudStormCeiling, to.cloudStormCeiling, dt, 1.2f, 3.0f);
        from.cloudBrightness = approach(from.cloudBrightness, to.cloudBrightness, dt, 2.6f, 2.8f);

        // Confirmed precipitation appears promptly, then decays more softly after it stops.
        from.rainIntensity = approach(from.rainIntensity, to.rainIntensity, dt, 0.75f, 2.1f);
        from.drizzleIntensity = approach(from.drizzleIntensity, to.drizzleIntensity, dt, 0.95f, 1.8f);
        from.snowIntensity = approach(from.snowIntensity, to.snowIntensity, dt, 1.0f, 2.4f);
        from.fogIntensity = approach(from.fogIntensity, to.fogIntensity, dt, 2.0f, 3.8f);
        from.stormIntensity = approach(from.stormIntensity, to.stormIntensity, dt, 0.65f, 2.5f);
        from.airHazeIntensity = approach(from.airHazeIntensity, to.airHazeIntensity, dt, 3.0f, 4.8f);

        from.windStrength = approach(from.windStrength, to.windStrength, dt, 1.25f, 2.2f);
        from.windDirectionRadians = approachAngle(
                from.windDirectionRadians,
                to.windDirectionRadians,
                dt,
                1.8f
        );
        from.sceneLight = approach(from.sceneLight, to.sceneLight, dt, 2.2f, 2.6f);
        from.visibilityFactor = approach(from.visibilityFactor, to.visibilityFactor, dt, 2.0f, 3.2f);

        // Launcher parallax must stay responsive; this is deliberately much faster.
        from.parallax = approach(from.parallax, to.parallax, dt, 0.10f, 0.10f);

        if (isVisuallySettled(from, to)) {
            from.copyFrom(to);
            transitioning = false;
        }
        return true;
    }

    private static float approach(float from, float to, float dt, float upTau, float downTau) {
        float tau = to >= from ? upTau : downTau;
        float alpha = dt / Math.max(0.001f, tau + dt);
        return from + (to - from) * alpha;
    }

    private static float approachUnitCycle(float from, float to, float dt, float tau) {
        float delta = to - from;
        if (delta > 0.5f) delta -= 1f;
        if (delta < -0.5f) delta += 1f;
        float alpha = dt / Math.max(0.001f, tau + dt);
        float value = from + delta * alpha;
        value %= 1f;
        return value < 0f ? value + 1f : value;
    }

    private static float approachAngle(float from, float to, float dt, float tau) {
        float delta = wrapRadians(to - from);
        float alpha = dt / Math.max(0.001f, tau + dt);
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
                && near(a.windStrength, b.windStrength, 0.002f)
                && Math.abs(wrapRadians(a.windDirectionRadians - b.windDirectionRadians)) < 0.008f
                && near(a.sceneLight, b.sceneLight, 0.002f)
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
