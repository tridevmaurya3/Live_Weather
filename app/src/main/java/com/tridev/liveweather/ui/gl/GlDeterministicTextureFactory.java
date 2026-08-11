package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Creates deterministic, context-local GPU textures from Java-side data.
 *
 * Procedural fragment hashes based on large sin()/fract() constants can diverge
 * between desktop emulator GPUs and real mobile mediump hardware. These fields
 * are generated once with deterministic integer/float math on the CPU and then
 * sampled by simple shaders, so emulator/Adreno/Mali receive the same bytes.
 */
public final class GlDeterministicTextureFactory {

    public static final int CLOUD_SIZE = 256;
    public static final int STAR_SIZE = 512;
    public static final int PROFILE_WIDTH = 512;

    private GlDeterministicTextureFactory() {
    }

    public static int createCloudNoiseTexture() {
        final int size = CLOUD_SIZE;
        ByteBuffer pixels = ByteBuffer.allocateDirect(size * size * 4)
                .order(ByteOrder.nativeOrder());

        // Three seamless value-noise octaves. O(width*height), unlike the old
        // repeated large-kernel blur, so multiple in-app GL surfaces can create
        // their own context texture without a CPU spike.
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float broad = periodicValueNoise(x, y, size, 8, 0x4C495645);
                float medium = periodicValueNoise(x, y, size, 16, 0x13572468);
                float detail = periodicValueNoise(x, y, size, 32, 0x6A09E667);
                float value = clamp01(broad * 0.56f + medium * 0.30f + detail * 0.14f);
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

    private static float periodicValueNoise(
            int x,
            int y,
            int size,
            int cells,
            int seed
    ) {
        float gx = x / (float) size * cells;
        float gy = y / (float) size * cells;
        int x0 = (int) Math.floor(gx);
        int y0 = (int) Math.floor(gy);
        int x1 = (x0 + 1) % cells;
        int y1 = (y0 + 1) % cells;
        x0 = Math.floorMod(x0, cells);
        y0 = Math.floorMod(y0, cells);

        float fx = gx - (float) Math.floor(gx);
        float fy = gy - (float) Math.floor(gy);
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);

        float a = unitHash(x0, y0, seed);
        float b = unitHash(x1, y0, seed);
        float c = unitHash(x0, y1, seed);
        float d = unitHash(x1, y1, seed);
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fy);
    }

    private static float unitHash(int x, int y, int seed) {
        int h = mix32(seed ^ (x * 0x1F123BB5) ^ (y * 0x5F356495));
        return ((h >>> 8) & 0x00FFFFFF) / 16777215f;
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
