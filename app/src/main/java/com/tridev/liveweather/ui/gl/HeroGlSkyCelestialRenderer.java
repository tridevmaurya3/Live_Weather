package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Hash-free base renderer for gradient sky, Sun and Moon.
 *
 * Astronomy/visibility remain supplied by GlSceneSnapshot. Moon phase geometry
 * is analytic; small maria patches use fixed gaussian shapes rather than GPU
 * random hashes, keeping the same result across emulator/Adreno/Mali.
 */
public final class HeroGlSkyCelestialRenderer {

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
            "uniform vec3 uTop;",
            "uniform vec3 uMid;",
            "uniform vec3 uHorizon;",
            "uniform vec2 uSunPos;",
            "uniform float uSunVis;",
            "uniform float uSunAltitude;",
            "uniform vec2 uMoonPos;",
            "uniform float uMoonVis;",
            "uniform float uMoonIllum;",
            "uniform float uMoonPhase;",
            "uniform float uMoonAltitude;",
            "uniform float uFog;",
            "uniform float uHaze;",
            "uniform float uVisibility;",
            "",
            "float maria(vec2 q){",
            "  float m=0.0;",
            "  vec2 a=(q-vec2(-0.22,-0.10))*vec2(1.45,1.85);",
            "  vec2 b=(q-vec2(0.18,0.08))*vec2(1.85,1.45);",
            "  vec2 c=(q-vec2(0.05,-0.31))*vec2(2.35,1.75);",
            "  vec2 d=(q-vec2(-0.34,0.26))*vec2(2.55,2.05);",
            "  m+=exp(-dot(a,a)*7.2)*0.18;",
            "  m+=exp(-dot(b,b)*8.0)*0.14;",
            "  m+=exp(-dot(c,c)*8.8)*0.11;",
            "  m+=exp(-dot(d,d)*10.0)*0.08;",
            "  return m;",
            "}",
            "",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  vec3 sky=mix(uHorizon,uMid,smoothstep(0.20,0.67,1.0-p.y));",
            "  sky=mix(sky,uTop,smoothstep(0.43,1.0,1.0-p.y));",
            "  float horizon=smoothstep(0.50,0.97,p.y);",
            "  float haze=(uFog*0.42+uHaze*0.26+(1.0-uVisibility)*0.08)*horizon;",
            "  sky=mix(sky,vec3(0.54,0.60,0.65),clamp(haze,0.0,0.42));",
            "  float deepNight=1.0-smoothstep(-18.0,-8.0,uSunAltitude);",
            "  sky+=vec3(0.026,0.052,0.083)*horizon*deepNight*(1.0-uHaze*0.55)*0.34;",
            "  vec3 color=sky;",
            "",
            "  vec2 sp=(p-uSunPos)*vec2(aspect,1.0);",
            "  float sd=length(sp);",
            "  float sunGlow=exp(-sd*20.0)*uSunVis;",
            "  float sunDisc=1.0-smoothstep(0.027,0.033,sd);",
            "  color+=vec3(1.0,0.69,0.24)*sunGlow*0.76;",
            "  color=mix(color,vec3(1.0,0.91,0.50),sunDisc*uSunVis);",
            "",
            "  vec2 mp=(p-uMoonPos)*vec2(aspect,1.0);",
            "  float md=length(mp);",
            "  float moonRadius=0.031;",
            "  vec2 q=mp/moonRadius;",
            "  float q2=dot(q,q);",
            "  float astronomicalDark=1.0-smoothstep(-12.0,-3.0,uSunAltitude);",
            "  float warm=1.0-smoothstep(5.0,24.0,uMoonAltitude);",
            "  vec3 moonBase=mix(vec3(0.88,0.92,0.98),vec3(0.97,0.84,0.68),warm*0.34);",
            "  if(q2<1.0 && uMoonVis>0.001){",
            "    float z=sqrt(max(0.0,1.0-q2));",
            "    float incident=q.x*sin(uMoonPhase)+z*(-cos(uMoonPhase));",
            "    float lit=smoothstep(-0.030,0.050,incident);",
            "    float earthshine=(0.004+0.020*astronomicalDark)*(1.0-uMoonIllum*0.58);",
            "    float phaseLight=earthshine+lit*(0.96-earthshine)*(0.62+0.38*max(0.0,incident));",
            "    float limb=1.0-smoothstep(0.90,1.0,sqrt(q2));",
            "    float limbShade=0.75+0.25*z;",
            "    float surface=clamp(0.96-maria(q),0.68,1.0);",
            "    vec3 moonColor=moonBase*phaseLight*limbShade*surface;",
            "    color=mix(color,moonColor,clamp(uMoonVis*limb,0.0,1.0));",
            "  }",
            "  float haloStrength=uMoonVis*(0.030+uMoonIllum*0.18)*astronomicalDark;",
            "  float halo=exp(-md*16.0)*haloStrength+exp(-md*6.4)*haloStrength*0.20;",
            "  color+=vec3(0.30,0.40,0.57)*halo;",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),1.0);",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program;
    private int width = 1;
    private int height = 1;
    private int aPosition;
    private int uResolution;
    private int uTop;
    private int uMid;
    private int uHorizon;
    private int uSunPos;
    private int uSunVis;
    private int uSunAltitude;
    private int uMoonPos;
    private int uMoonVis;
    private int uMoonIllum;
    private int uMoonPhase;
    private int uMoonAltitude;
    private int uFog;
    private int uHaze;
    private int uVisibility;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlSkyCelestialRenderer() {
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
        uTop = uniform("uTop");
        uMid = uniform("uMid");
        uHorizon = uniform("uHorizon");
        uSunPos = uniform("uSunPos");
        uSunVis = uniform("uSunVis");
        uSunAltitude = uniform("uSunAltitude");
        uMoonPos = uniform("uMoonPos");
        uMoonVis = uniform("uMoonVis");
        uMoonIllum = uniform("uMoonIllum");
        uMoonPhase = uniform("uMoonPhase");
        uMoonAltitude = uniform("uMoonAltitude");
        uFog = uniform("uFog");
        uHaze = uniform("uHaze");
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
        GLES20.glClearColor(0.02f, 0.04f, 0.08f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GlSceneSnapshot state = snapshot;
        if (program == 0 || state == null) return;

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, width, height);
        GLES20.glUniform3f(uTop, state.topR, state.topG, state.topB);
        GLES20.glUniform3f(uMid, state.midR, state.midG, state.midB);
        GLES20.glUniform3f(uHorizon, state.horizonR, state.horizonG, state.horizonB);
        GLES20.glUniform2f(uSunPos, state.sunX, state.sunY);
        GLES20.glUniform1f(uSunVis, state.sunVisibility);
        GLES20.glUniform1f(uSunAltitude, state.sunAltitude);
        GLES20.glUniform2f(uMoonPos, state.moonX, state.moonY);
        GLES20.glUniform1f(uMoonVis, state.moonVisibility);
        GLES20.glUniform1f(uMoonIllum, state.moonIllumination);
        GLES20.glUniform1f(uMoonPhase, state.moonPhaseAngleRadians);
        GLES20.glUniform1f(uMoonAltitude, state.moonAltitude);
        GLES20.glUniform1f(uFog, state.fogIntensity);
        GLES20.glUniform1f(uHaze, state.airHazeIntensity);
        GLES20.glUniform1f(uVisibility, state.visibilityFactor);

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPosition);
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
            throw new IllegalStateException("OpenGL sky/celestial program link failed: " + log);
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
            throw new IllegalStateException("OpenGL sky/celestial shader compile failed: " + log);
        }
        return shader;
    }
}
