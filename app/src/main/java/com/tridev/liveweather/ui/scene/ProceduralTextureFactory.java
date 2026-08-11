package com.tridev.liveweather.ui.scene;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds reusable procedural textures for the cinematic weather renderer.
 * Expensive pixel synthesis happens only when a texture is first requested;
 * animation frames only scale/tint cached bitmaps.
 */
public final class ProceduralTextureFactory {

    public enum CloudKind {
        CIRRUS,
        CUMULUS,
        STRATUS,
        STORM
    }

    private static final int CLOUD_WIDTH = 256;
    private static final int CLOUD_HEIGHT = 128;
    private static final int FOG_WIDTH = 320;
    private static final int FOG_HEIGHT = 96;
    private static final int MOON_SIZE = 192;

    private final Map<String, Bitmap> cloudCache = new HashMap<>();
    private final Map<Integer, Bitmap> fogCache = new HashMap<>();

    private Bitmap moonAlbedo;
    private Bitmap moonPhase;
    private int moonPhaseBucket = Integer.MIN_VALUE;

    @NonNull
    public Bitmap cloud(@NonNull CloudKind kind, int variant) {
        int safeVariant = Math.floorMod(variant, 8);
        String key = kind.name() + ':' + safeVariant;
        Bitmap cached = cloudCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        Bitmap created = buildCloud(kind, safeVariant);
        cloudCache.put(key, created);
        return created;
    }

    @NonNull
    public Bitmap fog(int variant) {
        int safeVariant = Math.floorMod(variant, 5);
        Bitmap cached = fogCache.get(safeVariant);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        Bitmap created = buildFog(safeVariant);
        fogCache.put(safeVariant, created);
        return created;
    }

    /**
     * Creates a phase-correct illuminated Moon disc. Astronomy Engine's phase
     * convention is used by the caller: 0=new, 90=first quarter, 180=full,
     * 270=third quarter.
     */
    @NonNull
    public Bitmap moonPhase(double phaseAngleDegrees) {
        int bucket = (int) Math.round(normalizeDegrees(phaseAngleDegrees) * 2.0d);
        if (moonPhase != null && !moonPhase.isRecycled() && bucket == moonPhaseBucket) {
            return moonPhase;
        }
        if (moonAlbedo == null || moonAlbedo.isRecycled()) {
            moonAlbedo = buildMoonAlbedo();
        }
        moonPhase = buildMoonPhase(moonAlbedo, bucket / 2.0d);
        moonPhaseBucket = bucket;
        return moonPhase;
    }

    @NonNull
    private Bitmap buildCloud(CloudKind kind, int variant) {
        int[] pixels = new int[CLOUD_WIDTH * CLOUD_HEIGHT];
        Lobe[] lobes = lobes(kind, variant);
        double threshold;
        double feather;
        double verticalBias;
        double maximumAlpha;

        switch (kind) {
            case CIRRUS:
                threshold = 0.36d;
                feather = 0.26d;
                verticalBias = 0.25d;
                maximumAlpha = 0.72d;
                break;
            case STRATUS:
                threshold = 0.34d;
                feather = 0.22d;
                verticalBias = 0.48d;
                maximumAlpha = 0.90d;
                break;
            case STORM:
                threshold = 0.30d;
                feather = 0.20d;
                verticalBias = 0.62d;
                maximumAlpha = 0.98d;
                break;
            case CUMULUS:
            default:
                threshold = 0.33d;
                feather = 0.22d;
                verticalBias = 0.52d;
                maximumAlpha = 0.94d;
                break;
        }

        for (int py = 0; py < CLOUD_HEIGHT; py++) {
            double y = py / (double) (CLOUD_HEIGHT - 1);
            double ny = y * 2.0d - 1.0d;
            for (int px = 0; px < CLOUD_WIDTH; px++) {
                double x = px / (double) (CLOUD_WIDTH - 1);
                double nx = x * 2.0d - 1.0d;

                double body = 0.0d;
                for (Lobe lobe : lobes) {
                    double dx = (nx - lobe.x) / lobe.rx;
                    double dy = (ny - lobe.y) / lobe.ry;
                    double gaussian = Math.exp(-(dx * dx + dy * dy) * lobe.softness);
                    body = Math.max(body, gaussian * lobe.weight);
                }

                double broadNoise = fbm(nx * 2.2d + variant * 3.7d,
                        ny * 2.6d - variant * 1.9d, 4);
                double detailNoise = fbm(nx * 6.4d - variant * 2.1d,
                        ny * 7.1d + variant * 1.3d, 3);
                double edgeNoise = (broadNoise - 0.5d) * 0.34d
                        + (detailNoise - 0.5d) * 0.16d;

                double density;
                if (kind == CloudKind.CIRRUS) {
                    double wave = 0.5d + 0.5d * Math.sin(nx * 7.0d + broadNoise * 4.2d);
                    density = body * (0.68d + wave * 0.20d) + edgeNoise;
                } else {
                    density = body + edgeNoise;
                }

                double edgeFade = smoothstep(0.02d, 0.12d, x)
                        * (1.0d - smoothstep(0.88d, 0.98d, x))
                        * smoothstep(0.02d, 0.16d, y)
                        * (1.0d - smoothstep(0.90d, 0.99d, y));
                double alpha = smoothstep(threshold - feather, threshold + feather, density)
                        * edgeFade * maximumAlpha;

                if (alpha <= 0.002d) {
                    pixels[py * CLOUD_WIDTH + px] = Color.TRANSPARENT;
                    continue;
                }

                double topLight = 1.0d - y;
                double bottomShadow = Math.pow(y, 1.35d) * verticalBias;
                double texture = (broadNoise - 0.5d) * 0.14d + (detailNoise - 0.5d) * 0.07d;
                int luminance = clampInt((int) Math.round(
                        205.0d + topLight * 35.0d - bottomShadow * 74.0d + texture * 255.0d
                ), 92, 246);
                int a = clampInt((int) Math.round(alpha * 255.0d), 0, 255);
                pixels[py * CLOUD_WIDTH + px] = Color.argb(a, luminance, luminance, luminance);
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(CLOUD_WIDTH, CLOUD_HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, CLOUD_WIDTH, 0, 0, CLOUD_WIDTH, CLOUD_HEIGHT);
        return bitmap;
    }

    @NonNull
    private Bitmap buildFog(int variant) {
        int[] pixels = new int[FOG_WIDTH * FOG_HEIGHT];
        for (int py = 0; py < FOG_HEIGHT; py++) {
            double y = py / (double) (FOG_HEIGHT - 1);
            double vertical = Math.sin(Math.PI * clamp(y, 0.0d, 1.0d));
            for (int px = 0; px < FOG_WIDTH; px++) {
                double x = px / (double) (FOG_WIDTH - 1);
                double broad = fbm(x * 3.6d + variant * 7.1d, y * 2.7d, 4);
                double detail = fbm(x * 10.0d - variant * 2.4d, y * 5.0d, 2);
                double density = vertical
                        * clamp(0.45d + broad * 0.55d + (detail - 0.5d) * 0.12d, 0.0d, 1.0d);
                int alpha = clampInt((int) Math.round(density * 178.0d), 0, 178);
                int tone = clampInt((int) Math.round(220.0d + broad * 22.0d), 205, 242);
                pixels[py * FOG_WIDTH + px] = Color.argb(alpha, tone, tone + 2, tone + 4);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(FOG_WIDTH, FOG_HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, FOG_WIDTH, 0, 0, FOG_WIDTH, FOG_HEIGHT);
        return bitmap;
    }

    @NonNull
    private Bitmap buildMoonAlbedo() {
        int[] pixels = new int[MOON_SIZE * MOON_SIZE];
        Crater[] craters = new Crater[]{
                new Crater(-0.30d, -0.25d, 0.18d, 0.18d),
                new Crater(0.33d, -0.12d, 0.11d, 0.16d),
                new Crater(0.16d, 0.28d, 0.15d, 0.14d),
                new Crater(-0.42d, 0.20d, 0.09d, 0.12d),
                new Crater(0.48d, 0.36d, 0.08d, 0.10d),
                new Crater(-0.05d, -0.48d, 0.07d, 0.10d)
        };

        for (int py = 0; py < MOON_SIZE; py++) {
            double y = (py + 0.5d) / MOON_SIZE * 2.0d - 1.0d;
            for (int px = 0; px < MOON_SIZE; px++) {
                double x = (px + 0.5d) / MOON_SIZE * 2.0d - 1.0d;
                double r2 = x * x + y * y;
                if (r2 > 1.0d) {
                    pixels[py * MOON_SIZE + px] = Color.TRANSPARENT;
                    continue;
                }

                double n = fbm(x * 4.8d + 1.7d, y * 4.8d - 2.3d, 4);
                double albedo = 0.78d + (n - 0.5d) * 0.20d;
                for (Crater crater : craters) {
                    double dx = x - crater.x;
                    double dy = y - crater.y;
                    double distance = Math.sqrt(dx * dx + dy * dy) / crater.radius;
                    if (distance < 1.0d) {
                        double depression = (1.0d - distance) * crater.depth;
                        double rim = Math.exp(-Math.pow((distance - 0.82d) / 0.11d, 2.0d)) * 0.10d;
                        albedo -= depression;
                        albedo += rim;
                    }
                }
                double limb = Math.pow(Math.max(0.0d, 1.0d - r2), 0.12d);
                int tone = clampInt((int) Math.round((0.50d + albedo * 0.50d) * limb * 245.0d), 70, 238);
                pixels[py * MOON_SIZE + px] = Color.argb(255, tone, tone, clampInt(tone + 7, 0, 255));
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(MOON_SIZE, MOON_SIZE, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, MOON_SIZE, 0, 0, MOON_SIZE, MOON_SIZE);
        return bitmap;
    }

    @NonNull
    private Bitmap buildMoonPhase(@NonNull Bitmap albedo, double phaseAngleDegrees) {
        int[] source = new int[MOON_SIZE * MOON_SIZE];
        int[] target = new int[source.length];
        albedo.getPixels(source, 0, MOON_SIZE, 0, 0, MOON_SIZE, MOON_SIZE);

        double angle = Math.toRadians(normalizeDegrees(phaseAngleDegrees));
        double lightX = Math.sin(angle);
        double lightZ = -Math.cos(angle);

        for (int py = 0; py < MOON_SIZE; py++) {
            double y = (py + 0.5d) / MOON_SIZE * 2.0d - 1.0d;
            for (int px = 0; px < MOON_SIZE; px++) {
                int index = py * MOON_SIZE + px;
                int base = source[index];
                if (Color.alpha(base) == 0) {
                    target[index] = Color.TRANSPARENT;
                    continue;
                }
                double x = (px + 0.5d) / MOON_SIZE * 2.0d - 1.0d;
                double z2 = 1.0d - x * x - y * y;
                if (z2 <= 0.0d) {
                    target[index] = Color.TRANSPARENT;
                    continue;
                }
                double z = Math.sqrt(z2);
                double incidence = x * lightX + z * lightZ;
                double lit = smoothstep(-0.035d, 0.055d, incidence);
                double earthshine = 0.028d;
                double brightness = earthshine + lit * (0.97d - earthshine)
                        * (0.48d + 0.52d * Math.max(0.0d, incidence));
                int r = clampInt((int) Math.round(Color.red(base) * brightness), 3, 255);
                int g = clampInt((int) Math.round(Color.green(base) * brightness), 4, 255);
                int b = clampInt((int) Math.round(Color.blue(base) * brightness), 7, 255);
                target[index] = Color.argb(255, r, g, b);
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(MOON_SIZE, MOON_SIZE, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(target, 0, MOON_SIZE, 0, 0, MOON_SIZE, MOON_SIZE);
        return bitmap;
    }

    private Lobe[] lobes(CloudKind kind, int variant) {
        int count;
        double verticalCenter;
        double verticalSpread;
        double rxMin;
        double rxRange;
        double ryMin;
        double ryRange;

        switch (kind) {
            case CIRRUS:
                count = 7;
                verticalCenter = -0.02d;
                verticalSpread = 0.26d;
                rxMin = 0.36d;
                rxRange = 0.34d;
                ryMin = 0.08d;
                ryRange = 0.09d;
                break;
            case STRATUS:
                count = 9;
                verticalCenter = 0.10d;
                verticalSpread = 0.25d;
                rxMin = 0.28d;
                rxRange = 0.30d;
                ryMin = 0.18d;
                ryRange = 0.17d;
                break;
            case STORM:
                count = 11;
                verticalCenter = 0.02d;
                verticalSpread = 0.39d;
                rxMin = 0.24d;
                rxRange = 0.28d;
                ryMin = 0.22d;
                ryRange = 0.30d;
                break;
            case CUMULUS:
            default:
                count = 10;
                verticalCenter = 0.08d;
                verticalSpread = 0.42d;
                rxMin = 0.20d;
                rxRange = 0.28d;
                ryMin = 0.20d;
                ryRange = 0.28d;
                break;
        }

        Lobe[] result = new Lobe[count];
        for (int i = 0; i < count; i++) {
            double t = count <= 1 ? 0.5d : i / (double) (count - 1);
            double x = -0.75d + t * 1.50d + (hash(variant * 101 + i * 31) - 0.5d) * 0.22d;
            double arch = 1.0d - Math.pow((t - 0.5d) * 2.0d, 2.0d);
            double y = verticalCenter - arch * (kind == CloudKind.CUMULUS ? 0.30d : kind == CloudKind.STORM ? 0.22d : 0.08d)
                    + (hash(variant * 211 + i * 47) - 0.5d) * verticalSpread;
            double rx = rxMin + hash(variant * 307 + i * 61) * rxRange;
            double ry = ryMin + hash(variant * 401 + i * 73) * ryRange;
            double weight = 0.78d + hash(variant * 503 + i * 83) * 0.28d;
            double softness = 1.45d + hash(variant * 607 + i * 97) * 1.25d;
            result[i] = new Lobe(x, y, rx, ry, weight, softness);
        }
        return result;
    }

    private static double fbm(double x, double y, int octaves) {
        double value = 0.0d;
        double amplitude = 0.5d;
        double frequency = 1.0d;
        double total = 0.0d;
        for (int i = 0; i < octaves; i++) {
            value += valueNoise(x * frequency, y * frequency) * amplitude;
            total += amplitude;
            frequency *= 2.03d;
            amplitude *= 0.50d;
        }
        return total <= 0.0d ? 0.5d : value / total;
    }

    private static double valueNoise(double x, double y) {
        int x0 = fastFloor(x);
        int y0 = fastFloor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        double tx = smoothFraction(x - x0);
        double ty = smoothFraction(y - y0);
        double a = hash2(x0, y0);
        double b = hash2(x1, y0);
        double c = hash2(x0, y1);
        double d = hash2(x1, y1);
        double top = lerp(a, b, tx);
        double bottom = lerp(c, d, tx);
        return lerp(top, bottom, ty);
    }

    private static double hash2(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0x7fffffff) / 2147483647.0d;
    }

    private static double hash(int seed) {
        int n = seed * 747796405 + 2891336453L > Integer.MAX_VALUE
                ? (int) (seed * 747796405L + 2891336453L)
                : seed * 747796405 + (int) 2891336453L;
        n = (n ^ (n >>> 16)) * 2246822519L > Integer.MAX_VALUE
                ? (int) ((n ^ (n >>> 16)) * 2246822519L)
                : (n ^ (n >>> 16)) * (int) 2246822519L;
        n ^= n >>> 13;
        return (n & 0x7fffffff) / 2147483647.0d;
    }

    private static int fastFloor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double smoothFraction(double value) {
        return value * value * (3.0d - 2.0d * value);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0d : 1.0d;
        }
        double t = clamp((value - edge0) / (edge1 - edge0), 0.0d, 1.0d);
        return t * t * (3.0d - 2.0d * t);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static double normalizeDegrees(double degrees) {
        double value = degrees % 360.0d;
        return value < 0.0d ? value + 360.0d : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Lobe {
        final double x;
        final double y;
        final double rx;
        final double ry;
        final double weight;
        final double softness;

        Lobe(double x, double y, double rx, double ry, double weight, double softness) {
            this.x = x;
            this.y = y;
            this.rx = rx;
            this.ry = ry;
            this.weight = weight;
            this.softness = softness;
        }
    }

    private static final class Crater {
        final double x;
        final double y;
        final double radius;
        final double depth;

        Crater(double x, double y, double radius, double depth) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.depth = depth;
        }
    }
}
