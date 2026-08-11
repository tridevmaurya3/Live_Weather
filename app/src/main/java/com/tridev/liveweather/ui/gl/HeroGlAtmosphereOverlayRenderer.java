package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * ODM-5 cinematic atmosphere pass.
 *
 * This is deliberately restrained. It does not invent scenery or weather. It
 * uses the already-resolved scene state to add distance haze, low-horizon
 * scattering, rain/storm atmosphere, lunar fill and a subtle optical vignette.
 * The pass is transparent and contains no bitmap/texture bounds.
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
            "precision mediump float;",
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
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  float horizon=smoothstep(0.48,0.98,p.y);",
            "  float lower=smoothstep(0.64,1.0,p.y);",
            "  float fogVeil=uFog*(0.10+0.34*horizon);",
            "  float hazeVeil=uHaze*(0.025+0.12*horizon);",
            "  float rainVeil=max(uRain,uDrizzle*0.55)*(0.018+0.060*horizon);",
            "  float distanceLoss=(1.0-uVisibility)*(0.020+0.14*horizon);",
            "  float atmospheric=clamp(fogVeil+hazeVeil+rainVeil+distanceLoss,0.0,0.42);",
            "",
            "  float sunLow=smoothstep(0.40,0.83,uSunPos.y)*uSunVis;",
            "  vec2 sp=(p-uSunPos)*vec2(aspect,1.0);",
            "  float sunScatter=exp(-length(sp)*2.1)*sunLow*horizon*(1.0-uStorm*0.72);",
            "  float moonNight=(1.0-uSceneLight)*uMoonVis*uMoonIllum;",
            "  vec2 mp=(p-uMoonPos)*vec2(aspect,1.0);",
            "  float moonScatter=exp(-length(mp)*2.4)*moonNight*(0.020+0.050*horizon);",
            "",
            "  vec3 coolVeil=mix(vec3(0.38,0.47,0.55),vec3(0.22,0.29,0.37),uStorm);",
            "  vec3 warmVeil=vec3(0.78,0.52,0.31);",
            "  vec3 moonVeil=vec3(0.28,0.37,0.52);",
            "  vec3 color=coolVeil*atmospheric;",
            "  float alpha=atmospheric;",
            "  color+=warmVeil*sunScatter*0.14;",
            "  alpha+=sunScatter*0.08;",
            "  color+=moonVeil*moonScatter;",
            "  alpha+=moonScatter*0.60;",
            "",
            "  float stormFloor=uStorm*lower*(0.015+uCloud*0.020);",
            "  color+=vec3(0.035,0.050,0.072)*stormFloor;",
            "  alpha+=stormFloor;",
            "",
            "  vec2 centered=(p-0.5)*vec2(1.0,0.78);",
            "  float vignette=smoothstep(0.42,0.69,length(centered));",
            "  vignette*=0.012+uStorm*0.026+max(uRain,uDrizzle*0.5)*0.010;",
            "  color=mix(color,vec3(0.008,0.012,0.020),clamp(vignette*7.0,0.0,0.28));",
            "  alpha=1.0-(1.0-alpha)*(1.0-vignette);",
            "",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.46));",
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
