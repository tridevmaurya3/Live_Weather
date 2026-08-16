package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Phase 20A cinematic atmosphere pass.
 *
 * Fog is treated as low, layered moisture while haze is a broader horizon veil.
 * Both remain driven only by the resolved scene state. Subtle wind-driven band
 * movement prevents fog from looking like a static grey overlay.
 */
public final class HeroGlAtmosphereOverlayRenderer {

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
            "uniform vec2 uResolution;",
            "uniform vec2 uSunPos;",
            "uniform float uSunVis;",
            "uniform vec2 uMoonPos;",
            "uniform float uMoonVis;",
            "uniform float uMoonIllum;",
            "uniform float uCloud;",
            "uniform float uRain;",
            "uniform float uDrizzle;",
            "uniform float uFog;",
            "uniform float uStorm;",
            "uniform float uHaze;",
            "uniform float uSceneLight;",
            "uniform float uVisibility;",
            "uniform float uTime;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  float horizon=smoothstep(0.44,0.98,p.y);",
            "  float lower=smoothstep(0.60,1.0,p.y);",
            "  float side=sin(uWindDir);",
            "  float drift=uTime*side*(0.006+uWind*0.015);",
            "  float band1=0.5+0.5*sin((p.x+drift)*8.0+p.y*4.2);",
            "  float band2=0.5+0.5*sin((p.x-drift*0.62)*13.0-p.y*6.4+1.7);",
            "  float rolling=clamp(band1*0.62+band2*0.38,0.0,1.0);",
            "",
            "  float fogBase=uFog*(0.075+0.29*horizon);",
            "  float fogBands=uFog*lower*(0.020+0.075*rolling);",
            "  float fogVeil=fogBase+fogBands;",
            "  float hazeVeil=uHaze*(0.020+0.125*horizon)*(0.90+0.10*uSceneLight);",
            "  float precip=max(uRain,uDrizzle*0.55);",
            "  float rainVeil=precip*(0.014+0.052*horizon);",
            "  float distanceLoss=(1.0-uVisibility)*(0.018+0.135*horizon);",
            "  float atmospheric=clamp(fogVeil+hazeVeil+rainVeil+distanceLoss,0.0,0.48);",
            "",
            "  float sunLow=smoothstep(0.40,0.83,uSunPos.y)*uSunVis;",
            "  vec2 sp=(p-uSunPos)*vec2(aspect,1.0);",
            "  float sunScatter=exp(-length(sp)*2.15)*sunLow*horizon*(1.0-uStorm*0.72);",
            "  float moonNight=(1.0-uSceneLight)*uMoonVis*uMoonIllum;",
            "  vec2 mp=(p-uMoonPos)*vec2(aspect,1.0);",
            "  float moonScatter=exp(-length(mp)*2.45)*moonNight*(0.018+0.048*horizon);",
            "",
            "  vec3 fogColor=mix(vec3(0.50,0.56,0.60),vec3(0.38,0.44,0.49),1.0-uSceneLight);",
            "  vec3 hazeColor=mix(vec3(0.48,0.50,0.50),vec3(0.61,0.54,0.44),uSceneLight*0.42);",
            "  vec3 rainColor=mix(vec3(0.38,0.47,0.55),vec3(0.22,0.29,0.37),uStorm);",
            "  float fogPart=clamp(fogVeil,0.0,0.38);",
            "  float hazePart=clamp(hazeVeil+distanceLoss,0.0,0.24);",
            "  float rainPart=clamp(rainVeil,0.0,0.16);",
            "  vec3 color=fogColor*fogPart+hazeColor*hazePart+rainColor*rainPart;",
            "  float alpha=atmospheric;",
            "  color+=vec3(0.78,0.52,0.31)*sunScatter*0.13;",
            "  alpha+=sunScatter*0.07;",
            "  color+=vec3(0.28,0.37,0.52)*moonScatter;",
            "  alpha+=moonScatter*0.55;",
            "",
            "  float stormFloor=uStorm*lower*(0.013+uCloud*0.020);",
            "  color+=vec3(0.035,0.050,0.072)*stormFloor;",
            "  alpha+=stormFloor;",
            "",
            "  vec2 centered=(p-0.5)*vec2(1.0,0.78);",
            "  float vignette=smoothstep(0.42,0.69,length(centered));",
            "  vignette*=0.010+uStorm*0.024+precip*0.009;",
            "  color=mix(color,vec3(0.008,0.012,0.020),clamp(vignette*7.0,0.0,0.26));",
            "  alpha=1.0-(1.0-alpha)*(1.0-vignette);",
            "",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.50));",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program;
    private int width = 1;
    private int height = 1;

    private int aPosition;
    private int uResolution;
    private int uSunPos;
    private int uSunVis;
    private int uMoonPos;
    private int uMoonVis;
    private int uMoonIllum;
    private int uCloud;
    private int uRain;
    private int uDrizzle;
    private int uFog;
    private int uStorm;
    private int uHaze;
    private int uSceneLight;
    private int uVisibility;
    private int uTime;
    private int uWind;
    private int uWindDir;
    private long startNanos;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlAtmosphereOverlayRenderer() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder());
        quadBuffer = bytes.asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void onSurfaceCreated() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uResolution = uniform("uResolution");
        uSunPos = uniform("uSunPos");
        uSunVis = uniform("uSunVis");
        uMoonPos = uniform("uMoonPos");
        uMoonVis = uniform("uMoonVis");
        uMoonIllum = uniform("uMoonIllum");
        uCloud = uniform("uCloud");
        uRain = uniform("uRain");
        uDrizzle = uniform("uDrizzle");
        uFog = uniform("uFog");
        uStorm = uniform("uStorm");
        uHaze = uniform("uHaze");
        uSceneLight = uniform("uSceneLight");
        uVisibility = uniform("uVisibility");
        uTime = uniform("uTime");
        uWind = uniform("uWind");
        uWindDir = uniform("uWindDir");
        startNanos = System.nanoTime();
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
        if (program == 0 || state == null) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, width, height);
        GLES20.glUniform2f(uSunPos, state.sunX, state.sunY);
        GLES20.glUniform1f(uSunVis, state.sunVisibility);
        GLES20.glUniform2f(uMoonPos, state.moonX, state.moonY);
        GLES20.glUniform1f(uMoonVis, state.moonVisibility);
        GLES20.glUniform1f(uMoonIllum, state.moonIllumination);
        GLES20.glUniform1f(uCloud, state.cloudCover);
        GLES20.glUniform1f(uRain, state.rainIntensity);
        GLES20.glUniform1f(uDrizzle, state.drizzleIntensity);
        GLES20.glUniform1f(uFog, state.fogIntensity);
        GLES20.glUniform1f(uStorm, state.stormIntensity);
        GLES20.glUniform1f(uHaze, state.airHazeIntensity);
        GLES20.glUniform1f(uSceneLight, state.sceneLight);
        GLES20.glUniform1f(uVisibility, state.visibilityFactor);
        GLES20.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f);
        GLES20.glUniform1f(uWind, state.windStrength);
        GLES20.glUniform1f(uWindDir, state.windDirectionRadians);

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
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
            throw new IllegalStateException("OpenGL atmosphere program link failed: " + log);
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
            throw new IllegalStateException("OpenGL atmosphere shader compile failed: " + log);
        }
        return shader;
    }
}
