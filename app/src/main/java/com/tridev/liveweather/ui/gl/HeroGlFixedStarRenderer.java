package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Cross-device stable star field with restrained atmospheric scintillation.
 * Star positions remain deterministic and current-weather visibility stays the
 * single authority. Twinkle is deliberately subtle: stars never blink in/out.
 */
public final class HeroGlFixedStarRenderer {

    private static final int STAR_COUNT = 104;
    private static final int FLOATS_PER_STAR = 4;

    private static final String VERTEX_SHADER = String.join("\n",
            "attribute vec4 aStar;",
            "uniform float uStarVis;",
            "uniform float uPixelScale;",
            "uniform float uTime;",
            "varying float vAlpha;",
            "varying float vTone;",
            "void main(){",
            "  vec2 clip=vec2(aStar.x*2.0-1.0,1.0-aStar.y*2.0);",
            "  gl_Position=vec4(clip,0.0,1.0);",
            "  float phase=aStar.x*37.7+aStar.y*71.3+aStar.z*13.1;",
            "  float scint=0.94+0.06*sin(uTime*(0.75+aStar.z*0.85)+phase);",
            "  gl_PointSize=clamp(aStar.w*uPixelScale*(0.96+0.04*scint),1.15,4.2);",
            "  vAlpha=aStar.z*uStarVis*scint;",
            "  vTone=0.5+0.5*sin(phase*1.73);",
            "}"
    );

    private static final String FRAGMENT_SHADER = String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH",
            "precision highp float;",
            "#else",
            "precision mediump float;",
            "#endif",
            "varying float vAlpha;",
            "varying float vTone;",
            "void main(){",
            "  vec2 q=gl_PointCoord-0.5;",
            "  float d=length(q);",
            "  float core=1.0-smoothstep(0.16,0.49,d);",
            "  float halo=1.0-smoothstep(0.04,0.50,d);",
            "  float alpha=clamp((core*0.80+halo*0.20)*vAlpha,0.0,0.88);",
            "  vec3 cool=vec3(0.78,0.87,1.0);",
            "  vec3 warm=vec3(1.0,0.93,0.78);",
            "  vec3 color=mix(cool,warm,vTone*0.30);",
            "  color=mix(color,vec3(1.0),clamp(vAlpha*0.62,0.0,0.70));",
            "  gl_FragColor=vec4(color,alpha);",
            "}"
    );

    private final FloatBuffer starBuffer;
    private int program;
    private int aStar;
    private int uStarVis;
    private int uPixelScale;
    private int uTime;
    private float pixelScale = 1f;
    private long startNanos;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlFixedStarRenderer() {
        float[] stars = new float[STAR_COUNT * FLOATS_PER_STAR];
        int seed = 0x4C495645;
        for (int i = 0; i < STAR_COUNT; i++) {
            seed = next(seed);
            float x = ((seed >>> 8) & 0x00FFFFFF) / 16777215f;
            seed = next(seed);
            float y = 0.035f + (((seed >>> 8) & 0x00FFFFFF) / 16777215f) * 0.64f;
            seed = next(seed);
            float b = 0.38f + (((seed >>> 8) & 0x00FFFFFF) / 16777215f) * 0.62f;
            seed = next(seed);
            float size = 1.35f + (((seed >>> 8) & 0x00FFFFFF) / 16777215f) * 1.78f;

            int base = i * FLOATS_PER_STAR;
            stars[base] = x;
            stars[base + 1] = y;
            stars[base + 2] = b;
            stars[base + 3] = size;
        }

        ByteBuffer bytes = ByteBuffer.allocateDirect(stars.length * 4).order(ByteOrder.nativeOrder());
        starBuffer = bytes.asFloatBuffer();
        starBuffer.put(stars).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void onSurfaceCreated() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        aStar = GLES20.glGetAttribLocation(program, "aStar");
        uStarVis = GLES20.glGetUniformLocation(program, "uStarVis");
        uPixelScale = GLES20.glGetUniformLocation(program, "uPixelScale");
        uTime = GLES20.glGetUniformLocation(program, "uTime");
        startNanos = System.nanoTime();
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
        pixelScale = Math.max(0.78f, Math.min(1.65f, Math.max(1, height) / 800f));
    }

    public void drawFrame() {
        GlSceneSnapshot state = snapshot;
        if (program == 0 || state == null || state.starVisibility <= 0.001f) return;

        float resolved = Math.max(0f, Math.min(1f, state.starVisibility));
        float visibility = resolved <= 0f
                ? 0f
                : Math.max(0f, Math.min(1f, (float) Math.pow(resolved, 0.92d)));
        if (visibility <= 0.001f) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform1f(uStarVis, visibility);
        GLES20.glUniform1f(uPixelScale, pixelScale);
        GLES20.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f);

        starBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aStar);
        GLES20.glVertexAttribPointer(
                aStar,
                FLOATS_PER_STAR,
                GLES20.GL_FLOAT,
                false,
                FLOATS_PER_STAR * 4,
                starBuffer
        );
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, STAR_COUNT);
        GLES20.glDisableVertexAttribArray(aStar);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }

    private static int next(int value) {
        return value * 1664525 + 1013904223;
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int result = GLES20.glCreateProgram();
        GLES20.glAttachShader(result, vertex);
        GLES20.glAttachShader(result, fragment);
        GLES20.glLinkProgram(result);
        int[] status = new int[1];
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(result);
            GLES20.glDeleteProgram(result);
            throw new IllegalStateException("OpenGL fixed star program link failed: " + log);
        }
        return result;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("OpenGL fixed star shader compile failed: " + log);
        }
        return shader;
    }
}
