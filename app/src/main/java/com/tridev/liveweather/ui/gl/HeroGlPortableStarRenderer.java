package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Cross-device star renderer backed by a deterministic Java-generated texture.
 * Zero astronomical visibility still renders nothing. Real clouds are drawn
 * after this pass, so they naturally cover stars without GPU hash randomness.
 */
public final class HeroGlPortableStarRenderer {

    private static final float[] QUAD = {
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
    };

    private static final String VERTEX_SHADER = String.join("\n",
            "attribute vec2 aPosition;",
            "varying vec2 vUv;",
            "void main(){",
            "  vUv=aPosition*0.5+0.5;",
            "  gl_Position=vec4(aPosition,0.0,1.0);",
            "}"
    );

    private static final String FRAGMENT_SHADER = String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH",
            "precision highp float;",
            "#else",
            "precision mediump float;",
            "#endif",
            "varying vec2 vUv;",
            "uniform sampler2D uStars;",
            "uniform vec2 uResolution;",
            "uniform float uTime;",
            "uniform float uStarVis;",
            "uniform vec2 uMoonPos;",
            "uniform float uMoonVis;",
            "uniform float uMoonIllum;",
            "uniform float uCloud;",
            "uniform float uFog;",
            "uniform float uHaze;",
            "uniform float uRain;",
            "uniform float uDrizzle;",
            "uniform float uStorm;",
            "uniform float uParallax;",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  float horizonFade=1.0-smoothstep(0.70,0.96,p.y);",
            "  float weatherGate=(1.0-uFog*0.86)*(1.0-uHaze*0.62)",
            "      *(1.0-uRain*0.82)*(1.0-uDrizzle*0.58)*(1.0-uStorm*0.98);",
            "  float cloudGlobal=1.0-smoothstep(0.78,1.0,uCloud)*0.72;",
            "  vec2 mp=(p-uMoonPos)*vec2(aspect,1.0);",
            "  float moonGlare=exp(-length(mp)*6.5)*uMoonVis*(0.18+uMoonIllum*0.64);",
            "  float visibility=clamp(uStarVis*horizonFade*weatherGate*cloudGlobal*(1.0-moonGlare*0.76),0.0,1.0);",
            "  if(visibility<=0.001){ gl_FragColor=vec4(0.0); return; }",
            "  vec2 uv=vec2(clamp(vUv.x+(uParallax-0.5)*0.006,0.0,1.0),vUv.y);",
            "  vec4 star=texture2D(uStars,uv);",
            "  float twinkle=0.92+0.08*sin(uTime*0.55+vUv.x*9.0+vUv.y*13.0);",
            "  float alpha=star.a*visibility*twinkle;",
            "  vec3 color=star.rgb*visibility*(1.10+0.34*star.a)*twinkle;",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.98));",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program;
    private int starTexture;
    private int width = 1;
    private int height = 1;
    private int aPosition;
    private int uStars;
    private int uResolution;
    private int uTime;
    private int uStarVis;
    private int uMoonPos;
    private int uMoonVis;
    private int uMoonIllum;
    private int uCloud;
    private int uFog;
    private int uHaze;
    private int uRain;
    private int uDrizzle;
    private int uStorm;
    private int uParallax;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlPortableStarRenderer() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder());
        quadBuffer = bytes.asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void onSurfaceCreated() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        starTexture = GlDeterministicTextureFactory.createStarFieldTexture();
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uStars = uniform("uStars");
        uResolution = uniform("uResolution");
        uTime = uniform("uTime");
        uStarVis = uniform("uStarVis");
        uMoonPos = uniform("uMoonPos");
        uMoonVis = uniform("uMoonVis");
        uMoonIllum = uniform("uMoonIllum");
        uCloud = uniform("uCloud");
        uFog = uniform("uFog");
        uHaze = uniform("uHaze");
        uRain = uniform("uRain");
        uDrizzle = uniform("uDrizzle");
        uStorm = uniform("uStorm");
        uParallax = uniform("uParallax");
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        GLES20.glViewport(0, 0, this.width, this.height);
    }

    public void drawFrame() {
        GlSceneSnapshot state = snapshot;
        if (program == 0 || starTexture == 0 || state == null || state.starVisibility <= 0.001f) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, starTexture);
        GLES20.glUniform1i(uStars, 0);
        GLES20.glUniform2f(uResolution, width, height);
        GLES20.glUniform1f(uTime, (System.nanoTime() / 1_000_000_000f) % 4096f);
        GLES20.glUniform1f(uStarVis, state.starVisibility);
        GLES20.glUniform2f(uMoonPos, state.moonX, state.moonY);
        GLES20.glUniform1f(uMoonVis, state.moonVisibility);
        GLES20.glUniform1f(uMoonIllum, state.moonIllumination);
        GLES20.glUniform1f(uCloud, state.cloudCover);
        GLES20.glUniform1f(uFog, state.fogIntensity);
        GLES20.glUniform1f(uHaze, state.airHazeIntensity);
        GLES20.glUniform1f(uRain, state.rainIntensity);
        GLES20.glUniform1f(uDrizzle, state.drizzleIntensity);
        GLES20.glUniform1f(uStorm, state.stormIntensity);
        GLES20.glUniform1f(uParallax, state.parallax);

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
        if (starTexture != 0) {
            int[] ids = {starTexture};
            GLES20.glDeleteTextures(1, ids, 0);
            starTexture = 0;
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }

    private int uniform(@NonNull String name) {
        return GLES20.glGetUniformLocation(program, name);
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
            throw new IllegalStateException("OpenGL portable star program link failed: " + log);
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
            throw new IllegalStateException("OpenGL portable star shader compile failed: " + log);
        }
        return shader;
    }
}
