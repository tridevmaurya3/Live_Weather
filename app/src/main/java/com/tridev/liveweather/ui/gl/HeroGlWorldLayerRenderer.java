package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Procedural world/horizon pass for the live weather scene.
 *
 * This layer does not pretend to be the user's real physical surroundings.
 * It is an original artistic environment that gives the weather engine a
 * visible world to illuminate: calm/clear scenes use distant hills/forest,
 * while rain/storm scenes blend toward a restrained urban horizon and wet
 * reflective lower surface.
 *
 * Sun/Moon/weather remain authoritative inputs from GlSceneSnapshot.
 */
public final class HeroGlWorldLayerRenderer {

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
            "",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float night=1.0-smoothstep(-7.0,1.5,uSunAltitude);",
            "  float precip=max(uRain,uDrizzle*0.66);",
            "  float urbanMix=clamp(precip*1.25+uStorm*0.82,0.0,1.0);",
            "  float distanceFade=clamp((1.0-uFog*0.66-uHaze*0.25),0.24,1.0);",
            "",
            "  float ridgeFar=0.705+(noise1(p.x*4.2+2.0)-0.5)*0.105",
            "                 +sin(p.x*17.0+1.2)*0.010;",
            "  float ridgeNear=0.770+(noise1(p.x*6.8+8.0)-0.5)*0.090",
            "                  +sin(p.x*25.0+4.7)*0.008;",
            "  float farHill=smoothstep(ridgeFar-0.006,ridgeFar+0.006,p.y);",
            "  float nearHill=smoothstep(ridgeNear-0.006,ridgeNear+0.006,p.y);",
            "",
            "  float treeCell=p.x*54.0;",
            "  float treeId=floor(treeCell);",
            "  float treeLocal=abs(fract(treeCell)-0.5);",
            "  float treeTop=0.735+hash11(treeId+19.0)*0.070;",
            "  float treeShape=step(treeTop+treeLocal*0.18,p.y);",
            "  treeShape*=1.0-smoothstep(0.865,0.900,p.y);",
            "",
            "  float buildingCell=p.x*24.0;",
            "  float buildingId=floor(buildingCell);",
            "  float buildingTop=0.650+hash11(buildingId+71.0)*0.155;",
            "  float building=step(buildingTop,p.y);",
            "  building*=1.0-smoothstep(0.895,0.915,p.y);",
            "",
            "  vec2 windowGrid=vec2(p.x*94.0,p.y*84.0);",
            "  vec2 windowId=floor(windowGrid);",
            "  vec2 wf=fract(windowGrid);",
            "  float windowRect=step(0.23,wf.x)*step(wf.x,0.66)*step(0.24,wf.y)*step(wf.y,0.62);",
            "  float windowRnd=hash21(windowId+vec2(17.0,5.0));",
            "  float windowGate=step(0.78,windowRnd);",
            "  float windowMask=windowRect*windowGate*building*night*urbanMix;",
            "  windowMask*=distanceFade*(1.0-uStorm*0.36);",
            "",
            "  float ground=smoothstep(0.865,0.895,p.y);",
            "  float wet=clamp(precip*0.92+uStorm*0.35,0.0,1.0);",
            "  float reflectionX=abs(fract(p.x*47.0)-0.5);",
            "  float reflectionLine=1.0-smoothstep(0.04,0.17,reflectionX);",
            "  float reflectionBreak=0.55+0.45*noise1(p.y*96.0-uTime*(0.45+uWind*0.55));",
            "  float reflection=reflectionLine*reflectionBreak*ground*wet*night;",
            "",
            "  float lunarLift=night*uMoonVis*uMoonIllum*(1.0-uCloud*0.55)*(1.0-uFog*0.65);",
            "  float light=clamp(0.18+uSceneLight*0.72+lunarLift*0.18,0.16,0.92);",
            "",
            "  vec3 farColor=mix(vec3(0.055,0.085,0.120),vec3(0.150,0.205,0.245),light);",
            "  vec3 nearColor=mix(vec3(0.025,0.043,0.065),vec3(0.090,0.135,0.170),light);",
            "  vec3 cityColor=mix(vec3(0.025,0.036,0.052),vec3(0.075,0.100,0.125),light);",
            "",
            "  vec3 color=vec3(0.0);",
            "  float alpha=0.0;",
            "",
            "  float calmFar=farHill*(1.0-urbanMix*0.68);",
            "  float calmNear=max(nearHill,treeShape*0.82)*(1.0-urbanMix*0.58);",
            "  color=mix(color,farColor,calmFar*0.72*distanceFade);",
            "  alpha=max(alpha,calmFar*0.72*distanceFade);",
            "  color=mix(color,nearColor,calmNear*0.92);",
            "  alpha=max(alpha,calmNear*0.92);",
            "",
            "  float cityMask=building*urbanMix;",
            "  color=mix(color,cityColor,cityMask*0.94);",
            "  alpha=max(alpha,cityMask*0.94);",
            "",
            "  vec3 windowColor=mix(vec3(0.90,0.64,0.35),vec3(0.66,0.82,1.0),hash21(windowId+vec2(3.1,9.2))*0.42);",
            "  color+=windowColor*windowMask*0.82;",
            "  alpha=max(alpha,windowMask*0.84);",
            "",
            "  vec3 groundColor=mix(vec3(0.018,0.028,0.041),vec3(0.048,0.067,0.084),light*0.55);",
            "  groundColor=mix(groundColor,vec3(0.030,0.045,0.060),wet*0.55);",
            "  color=mix(color,groundColor,ground*0.96);",
            "  alpha=max(alpha,ground*0.96);",
            "",
            "  color+=mix(vec3(0.21,0.36,0.56),vec3(0.84,0.55,0.28),hash11(floor(p.x*47.0)+23.0))*reflection*0.16;",
            "",
            "  float horizonMist=smoothstep(0.64,0.80,p.y)*(1.0-smoothstep(0.82,0.92,p.y));",
            "  horizonMist*=clamp(uFog*0.28+uHaze*0.10+precip*0.08,0.0,0.24);",
            "  color=mix(color,vec3(0.29,0.36,0.42),horizonMist);",
            "  alpha=max(alpha,horizonMist*0.75);",
            "",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.98));",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program;
    private int width = 1;
    private int height = 1;

    private int aPosition;
    private int uResolution;
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

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlWorldLayerRenderer() {
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
            throw new IllegalStateException("OpenGL world program link failed: " + log);
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
            throw new IllegalStateException("OpenGL world shader compile failed: " + log);
        }
        return shader;
    }
}
