package com.tridev.liveweather.ui.scene;

import androidx.annotation.NonNull;

/**
 * Deterministic storm-event scheduler used by the Hero storm renderer.
 *
 * It intentionally produces irregular lightning windows and multi-pulse flashes
 * without timers, allocations or network access. Because the result is derived
 * from the current clock + storm intensity, app preview and WallpaperService
 * stay visually consistent and animation never "runs out" after a few seconds.
 */
public final class StormFlashController {

    @NonNull
    public FlashFrame frame(long nowMillis, float stormIntensity) {
        float intensity = clamp01(stormIntensity);
        if (intensity < 0.10f) {
            return FlashFrame.none();
        }

        // Strong storms attempt an event more frequently. A fixed window per
        // intensity band keeps the schedule stable between adjacent frames.
        long window = 7_400L - Math.round(intensity * 2_700L);
        window = Math.max(4_300L, window);

        long cycle = nowMillis / window;
        long phase = nowMillis % window;
        int seed = foldSeed(cycle);

        float chance = hash01(seed * 31 + 17);
        float threshold = 0.24f + intensity * 0.64f;
        if (chance > threshold) {
            return FlashFrame.none();
        }

        // Each event begins at a different point inside the cycle, preventing a
        // metronome-like strike cadence.
        long eventStart = 420L + Math.round(hash01(seed * 43 + 9) * 1_700L);
        long local = phase - eventStart;
        if (local < 0L || local > 620L) {
            return FlashFrame.none();
        }

        float flash = 0f;
        float bolt = 0f;

        if (local <= 72L) {
            float t = local / 72f;
            flash = 0.72f + (1f - t) * 0.28f;
            bolt = 1f - t * 0.20f;
        } else if (local <= 145L) {
            float t = (local - 72L) / 73f;
            flash = lerp(0.72f, 0.12f, t);
            bolt = lerp(0.80f, 0.30f, t);
        } else if (local >= 205L && local <= 302L) {
            float t = (local - 205L) / 97f;
            float secondPulse = 1f - t;
            flash = 0.52f * secondPulse;
            bolt = hash01(seed * 59 + 21) > 0.42f ? 0.48f * secondPulse : 0f;
        } else if (local > 302L) {
            float t = clamp01((local - 302L) / 318f);
            flash = 0.13f * (1f - t);
        }

        flash *= 0.62f + intensity * 0.58f;
        bolt *= 0.72f + intensity * 0.42f;

        boolean distant = hash01(seed * 71 + 7) < (0.24f + (1f - intensity) * 0.20f);
        if (distant) {
            bolt *= 0.12f;
            flash *= 0.78f;
        }

        float anchor = 0.16f + hash01(seed * 83 + 29) * 0.68f;
        return new FlashFrame(
                clamp01(flash),
                clamp01(bolt),
                seed,
                anchor,
                distant
        );
    }

    private static int foldSeed(long cycle) {
        long mixed = cycle ^ (cycle >>> 32);
        return (int) mixed * 1103515245 + 12345;
    }

    private static float hash01(int seed) {
        int n = seed;
        n = (n << 13) ^ n;
        int nn = n * (n * n * 15731 + 789221) + 1376312589;
        return (nn & 0x7fffffff) / 2147483647f;
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static final class FlashFrame {
        private static final FlashFrame NONE = new FlashFrame(0f, 0f, 0, 0.5f, false);

        private final float flashStrength;
        private final float boltStrength;
        private final int seed;
        private final float anchorXFraction;
        private final boolean distant;

        FlashFrame(
                float flashStrength,
                float boltStrength,
                int seed,
                float anchorXFraction,
                boolean distant
        ) {
            this.flashStrength = flashStrength;
            this.boltStrength = boltStrength;
            this.seed = seed;
            this.anchorXFraction = anchorXFraction;
            this.distant = distant;
        }

        @NonNull
        static FlashFrame none() {
            return NONE;
        }

        public float getFlashStrength() {
            return flashStrength;
        }

        public float getBoltStrength() {
            return boltStrength;
        }

        public int getSeed() {
            return seed;
        }

        public float getAnchorXFraction() {
            return anchorXFraction;
        }

        public boolean isDistant() {
            return distant;
        }
    }
}
