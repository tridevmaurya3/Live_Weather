package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Creates deterministic, context-local GPU textures from Java-side data.
 *
 * Why this exists:
 * procedural fragment hashes based on large sin()/fract() constants can diverge
 * noticeably between desktop emulator GPUs and real mobile mediump hardware.
 * These textures are generated once with deterministic integer math on the CPU
 * and then sampled by simple shaders, so emulator/Adreno/Mali receive the same
 * source field.
 */
public final class GlDeterministicTextureFactory {

    public static final int CLOUD_SIZE = 256;
    public static final int STAR_SIZE = 512;
    public static final int PROFILE_WIDTH = 512;

    private GlDeterministicTextureFactory() {
    }

    public static int createCloudNoiseTexture() {
        final int size = CLOUD_SIZE;
        float[] field = new float[size * size];
        int seed = 0x4C495645;
        for (int i = 0; i < field.length; i++) {
            seed = next(seed);
            field[i] = ((seed >>> 8) & 0x00FFFFFF) / 16777215f;
        }

        float[] temp = new float[field.length];
        // Wrapped blur creates a seamless texture and broad cloud masses.
        for (int pass = 0; pass < 6; pass++) {
            int radius = pass < 2 ? 8 : (pass < 4 ? 4 : 2);
            boxBlurWrapped(field, temp, size, radius);
            float[] swap = field;
            field = temp;
            temp = swap;
        }

        ByteBuffer pixels = ByteBuffer.allocateDirect(size * size * 4)
                .order(ByteOrder.nativeOrder());
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                float broad = field[index];
                // Add a small deterministic high-frequency component without
                // depending on GPU hash precision.
                int h = mix32(x * 73856093 ^ y * 19349663 ^ 0x6A09E667);
                float detail = ((h >>> 8) & 0x00FFFFFF) / 16777215f;
                float value = clamp01(broad * 0.88f + detail * 0.12f);
                int channel = Math.round(value * 255f);
                pixels.put((byte) channel);
                pixels.put((byte) channel);
                pixels.put((byte) channel);
                pixels.put((byte) 255);
            }
        }
        pixels.position(0);
        return uploadRgbaTexture(pixels, size, size, true);
    }

    public static int createStarFieldTexture() {
        final int size = STAR_SIZE;
        byte[] rgba = new byte[size * size * 4];
        int seed = 0x53544152;

        // Thousands of tiny deterministic candidates, most intentionally dim.
        for (int i = 0; i < 1050; i++) {
            seed = next(seed);
            int x = Math.floorMod(seed, size);
            seed = next(seed);
            int y = Math.floorMod(seed, size);
            seed = next(seed);
            int brightness = 105 + Math.floorMod(seed >>> 3, 151);
            seed = next(seed);
            int type = Math.floorMod(seed, 17);

            putStarPixel(rgba, size, x, y, brightness, type);
            if (type == 0 || type == 1) {
                putStarPixel(rgba, size, x - 1, y, brightness / 3, type);
                putStarPixel(rgba, size, x + 1, y, brightness / 3, type);
                putStarPixel(rgba, size, x, y - 1, brightness / 3, type);
                putStarPixel(rgba, size, x, y + 1, brightness / 3, type);
            }
        }

        ByteBuffer pixels = ByteBuffer.allocateDirect(rgba.length)
                .order(ByteOrder.nativeOrder());
        pixels.put(rgba).position(0);
        return uploadRgbaTexture(pixels, size, size, false);
    }

    public static int createWorldProfileTexture() {
        final int width = PROFILE_WIDTH;
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * 4)
                .order(ByteOrder.nativeOrder());

        float[] base = makePeriodicNoise(width, 0x13579BDF);
        float[] mid = makePeriodicNoise(width, 0x2468ACE1);
        float[] near = makePeriodicNoise(width, 0x10293847);
        float[] forest = makePeriodicNoise(width, 0x55667711);

        for (int x = 0; x < width; x++) {
            float farH = clamp01(0.55f + (base[x] - 0.5f) * 0.22f);
            float midH = clamp01(0.66f + (mid[x] - 0.5f) * 0.18f);
            float nearH = clamp01(0.75f + (near[x] - 0.5f) * 0.12f);
            float forestH = clamp01(0.79f + (forest[x] - 0.5f) * 0.09f);
            pixels.put((byte) Math.round(farH * 255f));
            pixels.put((byte) Math.round(midH * 255f));
            pixels.put((byte) Math.round(nearH * 255f));
            pixels.put((byte) Math.round(forestH * 255f));
        }
        pixels.position(0);
        return uploadRgbaTexture(pixels, width, 1, true);
    }

    private static float[] makePeriodicNoise(int width, int seed) {
        final int controlCount = 32;
        float[] controls = new float[controlCount];
        int value = seed;
        for (int i = 0; i < controlCount; i++) {
            value = next(value);
            controls[i] = ((value >>> 8) & 0x00FFFFFF) / 16777215f;
        }

        float[] result = new float[width];
        for (int x = 0; x < width; x++) {
            float u = x / (float) width * controlCount;
            int i0 = (int) Math.floor(u) % controlCount;
            int i1 = (i0 + 1) % controlCount;
            float f = u - (float) Math.floor(u);
            f = f * f * (3f - 2f * f);
            float primary = lerp(controls[i0], controls[i1], f);

            float u2 = x / (float) width * (controlCount / 2f);
            int j0 = (int) Math.floor(u2) % controlCount;
            int j1 = (j0 + 1) % controlCount;
            float f2 = u2 - (float) Math.floor(u2);
            f2 = f2 * f2 * (3f - 2f * f2);
            float secondary = lerp(controls[j0], controls[j1], f2);
            result[x] = clamp01(primary * 0.68f + secondary * 0.32f);
        }
        return result;
    }

    private static void boxBlurWrapped(float[] source, float[] target, int size, int radius) {
        int diameter = radius * 2 + 1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float sum = 0f;
                for (int oy = -radius; oy <= radius; oy++) {
                    int sy = Math.floorMod(y + oy, size);
                    for (int ox = -radius; ox <= radius; ox++) {
                        int sx = Math.floorMod(x + ox, size);
                        sum += source[sy * size + sx];
                    }
                }
                target[y * size + x] = sum / (diameter * diameter);
            }
        }
    }

    private static void putStarPixel(byte[] rgba, int size, int x, int y, int brightness, int type) {
        if (x < 0 || y < 0 || x >= size || y >= size) return;
        int index = (y * size + x) * 4;
        int b = Math.max(0, Math.min(255, brightness));
        int r = b;
        int g = b;
        int blue = b;
        if (type % 11 == 0) {
            r = Math.min(255, b + 18);
            g = Math.min(255, b + 8);
            blue = Math.max(0, b - 12);
        } else if (type % 7 == 0) {
            r = Math.max(0, b - 18);
            g = Math.min(255, b + 2);
            blue = Math.min(255, b + 22);
        }
        if ((rgba[index + 3] & 0xFF) > b) return;
        rgba[index] = (byte) r;
        rgba[index + 1] = (byte) g;
        rgba[index + 2] = (byte) blue;
        rgba[index + 3] = (byte) b;
    }

    private static int uploadRgbaTexture(
            @NonNull ByteBuffer pixels,
            int width,
            int height,
            boolean repeat
    ) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int texture = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                repeat ? GLES20.GL_REPEAT : GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                repeat ? GLES20.GL_REPEAT : GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                pixels
        );
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return texture;
    }

    private static int next(int value) {
        return value * 1664525 + 1013904223;
    }

    private static int mix32(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
