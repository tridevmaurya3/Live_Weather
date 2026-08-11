package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Visual-correction V3 world pass.
 *
 * Removes the repeated thin pine/picket artifact from V2. Calm scenes use
 * broad multi-depth mountain silhouettes plus clustered forest crowns and dark
 * water foreground. Rain/storm scenes still blend toward an urban horizon and
 * wet reflective ground. This is artistic scenery only; weather/Sun/Moon are
 * supplied by the shared reality snapshot.
 */
public final class HeroGlWorldLayerRendererV3 {

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
            "uniform float uTime;",
            "uniform float uSunAltitude;",
            "uniform float uMoonVis;",
            "uniform float uMoonIllum;",
            "uniform float uCloud;",
            "uniform float uRain;",
            "uniform float uDrizzle;",
            "uniform float uStorm;",
            "uniform float uFog;",
            "uniform float uHaze;",
            "uniform float uSceneLight;",
            "uniform float uWind;",
            "uniform float uParallax;",
            "",
            "float hash11(float p){",
            "  return fract(sin(p*127.1+311.7)*43758.5453123);",
            "}",
            "float hash21(vec2 p){",
            "  p=fract(p*vec2(123.34,345.45));",
            "  p+=dot(p,p+34.345);",
            "  return fract(p.x*p.y);",
            "}",
            "float noise1(float x){",
            "  float i=floor(x);",
            "  float f=fract(x);",
            "  f=f*f*(3.0-2.0*f);",
            "  return mix(hash11(i),hash11(i+1.0),f);",
            "}",
            "float fbm1(float x){",
            "  float v=0.0;",
            "  float a=0.56;",
            "  v+=a*noise1(x); x=x*2.07+13.7; a*=0.50;",
            "  v+=a*noise1(x); x=x*2.11+7.3;  a*=0.50;",
            "  v+=a*noise1(x); x=x*2.03+19.1; a*=0.50;",
            "  v+=a*noise1(x);",
            "  return v;",
            "}",
            "float ridge1(float x){",
            "  float n=fbm1(x);",
            "  return 1.0-abs(n*1.85-0.92);",
            "}",
            "",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float night=1.0-smoothstep(-7.0,1.5,uSunAltitude);",
            "  float precip=max(uRain,uDrizzle*0.66);",
            "  float urbanMix=clamp(precip*1.22+uStorm*0.86,0.0,1.0);",
            "  float calmMix=1.0-urbanMix;",
            "  float distanceFade=clamp(1.0-uFog*0.66-uHaze*0.25,0.22,1.0);",
            "  float px=p.x+(uParallax-0.5)*0.035;",
            "",
            "  float farLine=0.575",
            "      +(fbm1(px*2.10+2.2)-0.48)*0.145",
            "      +(ridge1(px*1.16+7.0)-0.48)*0.080;",
            "  float midLine=0.685",
            "      +(fbm1(px*3.45+21.0)-0.49)*0.112",
            "      +(ridge1(px*1.92+4.5)-0.50)*0.060;",
            "  float nearLine=0.765",
            "      +(fbm1(px*5.15+37.0)-0.50)*0.070",
            "      +(noise1(px*13.0+5.0)-0.50)*0.020;",
            "",
            "  float farMountain=smoothstep(farLine-0.005,farLine+0.005,p.y);",
            "  float midMountain=smoothstep(midLine-0.005,midLine+0.005,p.y);",
            "  float nearMountain=smoothstep(nearLine-0.005,nearLine+0.005,p.y);",
            "",
            "  float forestBase=0.795",
            "      +(fbm1(px*8.5+73.0)-0.50)*0.040",
            "      +(ridge1(px*11.0+22.0)-0.50)*0.025;",
            "  float forestCanopy=smoothstep(forestBase-0.004,forestBase+0.004,p.y);",
            "  forestCanopy*=1.0-smoothstep(0.902,0.925,p.y);",
            "",
            "  float crownCell=px*25.0;",
            "  float crownId=floor(crownCell);",
            "  float crownLocal=abs(fract(crownCell)-0.5);",
            "  float crownTop=0.745+hash11(crownId+29.0)*0.060;",
            "  float crownSlope=0.17+hash11(crownId+61.0)*0.10;",
            "  float crownLine=crownTop+crownLocal*crownSlope;",
            "  float crown=step(crownLine,p.y);",
            "  crown*=1.0-smoothstep(0.865,0.905,p.y);",
            "  crown*=step(0.18,hash11(crownId+91.0));",
            "  float forest=max(forestCanopy,crown*0.86);",
            "",
            "  float buildingCell=px*27.0;",
            "  float buildingId=floor(buildingCell);",
            "  float buildingLocal=fract(buildingCell);",
            "  float buildingTop=0.635+hash11(buildingId+71.0)*0.185;",
            "  float buildingWidth=0.64+hash11(buildingId+11.0)*0.27;",
            "  float building=step(buildingTop,p.y)*step(buildingLocal,buildingWidth);",
            "  building*=1.0-smoothstep(0.900,0.918,p.y);",
            "",
            "  vec2 windowGrid=vec2(px*108.0,p.y*92.0);",
            "  vec2 windowId=floor(windowGrid);",
            "  vec2 wf=fract(windowGrid);",
            "  float windowRect=step(0.22,wf.x)*step(wf.x,0.62)*step(0.22,wf.y)*step(wf.y,0.58);",
            "  float windowGate=step(0.82,hash21(windowId+vec2(17.0,5.0)));",
            "  float windowMask=windowRect*windowGate*building*night*urbanMix;",
            "  windowMask*=distanceFade*(1.0-uStorm*0.38);",
            "",
            "  float foreground=smoothstep(0.875,0.915,p.y);",
            "  float wet=clamp(precip*0.95+uStorm*0.34,0.0,1.0);",
            "  float water=foreground*calmMix;",
            "  float road=foreground*urbanMix;",
            "",
            "  float lunarLift=night*uMoonVis*uMoonIllum*(1.0-uCloud*0.55)*(1.0-uFog*0.65);",
            "  float light=clamp(0.20+uSceneLight*0.78+lunarLift*0.22,0.18,0.96);",
            "",
            "  vec3 farColor=mix(vec3(0.040,0.070,0.108),vec3(0.185,0.245,0.300),light);",
            "  vec3 midColor=mix(vec3(0.025,0.050,0.078),vec3(0.125,0.180,0.225),light);",
            "  vec3 nearColor=mix(vec3(0.014,0.032,0.050),vec3(0.075,0.120,0.155),light);",
            "  vec3 forestColor=mix(vec3(0.008,0.022,0.030),vec3(0.040,0.082,0.078),light);",
            "  vec3 cityColor=mix(vec3(0.020,0.030,0.044),vec3(0.070,0.095,0.120),light);",
            "",
            "  vec3 color=vec3(0.0);",
            "  float alpha=0.0;",
            "",
            "  float farMask=farMountain*calmMix*0.66*distanceFade;",
            "  color=mix(color,farColor,farMask);",
            "  alpha=max(alpha,farMask);",
            "",
            "  float midMask=midMountain*calmMix*0.82*distanceFade;",
            "  color=mix(color,midColor,midMask);",
            "  alpha=max(alpha,midMask);",
            "",
            "  float nearMask=nearMountain*calmMix*0.94;",
            "  color=mix(color,nearColor,nearMask);",
            "  alpha=max(alpha,nearMask);",
            "",
            "  float forestMask=forest*calmMix*0.91;",
            "  color=mix(color,forestColor,forestMask);",
            "  alpha=max(alpha,forestMask);",
            "",
            "  float cityMask=building*urbanMix*0.95;",
            "  color=mix(color,cityColor,cityMask);",
            "  alpha=max(alpha,cityMask);",
            "",
            "  vec3 windowColor=mix(vec3(0.95,0.66,0.34),vec3(0.67,0.82,1.0),hash21(windowId+vec2(3.1,9.2))*0.38);",
            "  color+=windowColor*windowMask*0.88;",
            "  alpha=max(alpha,windowMask*0.88);",
            "",
            "  vec3 waterColor=mix(vec3(0.009,0.020,0.032),vec3(0.034,0.068,0.096),light*0.76);",
            "  color=mix(color,waterColor,water*0.97);",
            "  alpha=max(alpha,water*0.97);",
            "  float ripple=(0.5+0.5*sin(p.y*430.0+noise1(px*16.0)*6.0+uTime*0.14));",
            "  float rippleBand=smoothstep(0.88,0.91,p.y)*ripple*calmMix*lunarLift;",
            "  color+=vec3(0.20,0.31,0.46)*rippleBand*0.065;",
            "",
            "  vec3 roadColor=mix(vec3(0.012,0.020,0.030),vec3(0.035,0.050,0.066),light*0.50);",
            "  color=mix(color,roadColor,road*0.98);",
            "  alpha=max(alpha,road*0.98);",
            "  float reflX=abs(fract(px*51.0)-0.5);",
            "  float reflLine=1.0-smoothstep(0.035,0.15,reflX);",
            "  float reflBreak=0.46+0.54*noise1(p.y*104.0-uTime*(0.42+uWind*0.52));",
            "  float reflection=reflLine*reflBreak*road*wet*night;",
            "  vec3 reflColor=mix(vec3(0.20,0.36,0.58),vec3(0.86,0.56,0.28),hash11(floor(px*51.0)+23.0));",
            "  color+=reflColor*reflection*0.18;",
            "",
            "  float horizonMist=smoothstep(0.56,0.73,p.y)*(1.0-smoothstep(0.82,0.91,p.y));",
            "  horizonMist*=clamp(uFog*0.34+uHaze*0.12+precip*0.08,0.0,0.27);",
            "  color=mix(color,vec3(0.26,0.34,0.41),horizonMist);",
            "  alpha=max(alpha,horizonMist*0.76);",
            "",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.99));",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program;
    private int aPosition;
    private int uTime;
    private int uSunAltitude;
    private int uMoonVis;
    private int uMoonIllum;
    private int uCloud;
    private int uRain;
    private int uDrizzle;
    private int uStorm;
    private int uFog;
    private int uHaze;
    private int uSceneLight;
    private int uWind;
    private int uParallax;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlWorldLayerRendererV3() {
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
        uTime = uniform("uTime");
        uSunAltitude = uniform("uSunAltitude");
        uMoonVis = uniform("uMoonVis");
        uMoonIllum = uniform("uMoonIllum");
        uCloud = uniform("uCloud");
        uRain = uniform("uRain");
        uDrizzle = uniform("uDrizzle");
        uStorm = uniform("uStorm");
        uFog = uniform("uFog");
        uHaze = uniform("uHaze");
        uSceneLight = uniform("uSceneLight");
        uWind = uniform("uWind");
        uParallax = uniform("uParallax");
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
    }

    public void drawFrame() {
        GlSceneSnapshot state = snapshot;
        if (program == 0 || state == null) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform1f(uTime, (System.nanoTime() / 1_000_000_000f) % 4096f);
        GLES20.glUniform1f(uSunAltitude, state.sunAltitude);
        GLES20.glUniform1f(uMoonVis, state.moonVisibility);
        GLES20.glUniform1f(uMoonIllum, state.moonIllumination);
        GLES20.glUniform1f(uCloud, state.cloudCover);
        GLES20.glUniform1f(uRain, state.rainIntensity);
        GLES20.glUniform1f(uDrizzle, state.drizzleIntensity);
        GLES20.glUniform1f(uStorm, state.stormIntensity);
        GLES20.glUniform1f(uFog, state.fogIntensity);
        GLES20.glUniform1f(uHaze, state.airHazeIntensity);
        GLES20.glUniform1f(uSceneLight, state.sceneLight);
        GLES20.glUniform1f(uWind, state.windStrength);
        GLES20.glUniform1f(uParallax, state.parallax);

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
            throw new IllegalStateException("OpenGL world V3 program link failed: " + log);
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
            throw new IllegalStateException("OpenGL world V3 shader compile failed: " + log);
        }
        return shader;
    }
}
