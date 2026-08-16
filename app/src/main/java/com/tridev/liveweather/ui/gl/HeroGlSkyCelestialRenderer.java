package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Cinematic-but-natural sky, Sun and Moon pass shared by app Hero and Live Wallpaper.
 * Astronomy stays authoritative; this shader only improves atmospheric scattering,
 * twilight continuity, disc softness, lunar surface response and real thermal ambience.
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
            "uniform float uThermal;",
            "",
            "float maria(vec2 q){",
            "  float m=0.0;",
            "  vec2 a=(q-vec2(-0.22,-0.10))*vec2(1.45,1.85);",
            "  vec2 b=(q-vec2(0.18,0.08))*vec2(1.85,1.45);",
            "  vec2 c=(q-vec2(0.05,-0.31))*vec2(2.35,1.75);",
            "  vec2 d=(q-vec2(-0.34,0.26))*vec2(2.55,2.05);",
            "  vec2 e=(q-vec2(0.31,-0.22))*vec2(3.10,2.70);",
            "  m+=exp(-dot(a,a)*7.2)*0.18;",
            "  m+=exp(-dot(b,b)*8.0)*0.14;",
            "  m+=exp(-dot(c,c)*8.8)*0.11;",
            "  m+=exp(-dot(d,d)*10.0)*0.08;",
            "  m+=exp(-dot(e,e)*11.2)*0.055;",
            "  return m;",
            "}",
            "",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  float vertical=1.0-p.y;",
            "  vec3 sky=mix(uHorizon,uMid,smoothstep(0.18,0.66,vertical));",
            "  sky=mix(sky,uTop,smoothstep(0.42,1.0,vertical));",
            "",
            "  float horizon=smoothstep(0.47,0.98,p.y);",
            "  float deepHorizon=smoothstep(0.67,1.0,p.y);",
            "  float haze=(uFog*0.40+uHaze*0.25+(1.0-uVisibility)*0.085)*horizon;",
            "  vec3 neutralHaze=mix(vec3(0.52,0.59,0.65),vec3(0.46,0.53,0.59),1.0-uVisibility);",
            "  sky=mix(sky,neutralHaze,clamp(haze,0.0,0.43));",
            "",
            "  float warmAir=max(0.0,uThermal);",
            "  float coldAir=max(0.0,-uThermal);",
            "  sky=mix(sky,vec3(0.76,0.64,0.50),deepHorizon*warmAir*0.030*(1.0-uFog));",
            "  sky=mix(sky,vec3(0.50,0.63,0.77),horizon*coldAir*0.022*(1.0-uHaze));",
            "",
            "  float astronomicalNight=1.0-smoothstep(-18.0,-8.0,uSunAltitude);",
            "  float civilTwilight=1.0-smoothstep(-5.5,4.5,uSunAltitude);",
            "  float twilightBand=(1.0-smoothstep(0.42,0.93,abs(p.y-0.76)))*civilTwilight*(1.0-astronomicalNight*0.72);",
            "  sky+=vec3(0.022,0.044,0.074)*horizon*astronomicalNight*(1.0-uHaze*0.55)*0.34;",
            "  sky+=vec3(0.20,0.082,0.025)*twilightBand*0.045*uSunVis;",
            "  vec3 color=sky;",
            "",
            "  float sunVis=clamp(uSunVis,0.0,1.0);",
            "  vec2 sp=(p-uSunPos)*vec2(aspect,1.0);",
            "  float sd=length(sp);",
            "  float lowSun=1.0-smoothstep(2.0,19.0,uSunAltitude);",
            "  float nearHorizon=1.0-smoothstep(-1.5,8.0,uSunAltitude);",
            "  vec3 sunDiscColor=mix(vec3(1.0,0.94,0.67),vec3(1.0,0.56,0.20),lowSun*0.86);",
            "  vec3 sunGlowColor=mix(vec3(1.0,0.72,0.30),vec3(1.0,0.40,0.12),lowSun*0.78);",
            "  float extinction=1.0-clamp(uFog*0.32+uHaze*0.24+(1.0-uVisibility)*0.16,0.0,0.58);",
            "  float sunGlow=exp(-sd*18.0)*sunVis*extinction;",
            "  float sunWideGlow=exp(-sd*6.8)*sunVis*(0.035+nearHorizon*0.095)*extinction;",
            "  float aureole=exp(-sd*3.0)*sunVis*nearHorizon*deepHorizon*0.030*extinction;",
            "  float sunDisc=1.0-smoothstep(0.026,0.034,sd);",
            "  float sunLimb=1.0-smoothstep(0.020,0.031,sd);",
            "  color+=sunGlowColor*sunGlow*0.73;",
            "  color+=sunGlowColor*sunWideGlow;",
            "  color+=vec3(1.0,0.42,0.12)*aureole;",
            "  color=mix(color,sunDiscColor*(0.94+0.06*sunLimb),sunDisc*sunVis*extinction);",
            "",
            "  float moonVis=clamp(uMoonVis,0.0,1.0);",
            "  vec2 mp=(p-uMoonPos)*vec2(aspect,1.0);",
            "  float md=length(mp);",
            "  float moonRadius=0.031;",
            "  vec2 q=mp/moonRadius;",
            "  float q2=dot(q,q);",
            "  float twilightDark=1.0-smoothstep(-4.0,3.0,uSunAltitude);",
            "  float warmMoon=1.0-smoothstep(5.0,24.0,uMoonAltitude);",
            "  vec3 moonBase=mix(vec3(0.89,0.93,0.99),vec3(0.97,0.83,0.66),warmMoon*0.34);",
            "  if(q2<1.0 && moonVis>0.0001){",
            "    float z=sqrt(max(0.0,1.0-q2));",
            "    float incident=q.x*sin(uMoonPhase)+z*(-cos(uMoonPhase));",
            "    float lit=smoothstep(-0.030,0.050,incident);",
            "    float earthshine=(0.004+0.022*astronomicalNight)*(1.0-uMoonIllum*0.58);",
            "    float phaseLight=earthshine+lit*(0.96-earthshine)*(0.61+0.39*max(0.0,incident));",
            "    float limb=1.0-smoothstep(0.90,1.0,sqrt(q2));",
            "    float limbShade=0.73+0.27*z;",
            "    float surface=clamp(0.965-maria(q),0.67,1.0);",
            "    float craterRim=exp(-abs(length(q-vec2(0.24,0.22))-0.13)*32.0)*0.035;",
            "    vec3 moonColor=moonBase*phaseLight*limbShade*clamp(surface+craterRim,0.0,1.0);",
            "    color=mix(color,moonColor,clamp(moonVis*limb,0.0,1.0));",
            "  }",
            "  float haloStrength=moonVis*(0.024+uMoonIllum*0.16)*mix(0.26,1.0,twilightDark)*(1.0-uFog*0.38);",
            "  float halo=exp(-md*15.0)*haloStrength+exp(-md*6.0)*haloStrength*0.19;",
            "  color+=vec3(0.30,0.40,0.58)*halo;",
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
    private int uThermal;

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
        uThermal = uniform("uThermal");
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
        GLES20.glUniform1f(uThermal, state.thermalBias);

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
