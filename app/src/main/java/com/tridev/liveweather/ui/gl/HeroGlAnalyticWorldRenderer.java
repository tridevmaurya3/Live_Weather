package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.domain.scene.SceneryRuntimeState;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Deterministic atmospheric world shared by app Hero and Live Wallpaper.
 *
 * Scenery Step S1 adds a stable multi-scene foundation without changing weather truth.
 * The selected scenery controls only generic world silhouettes/materials. Rain, fog,
 * storm, Sun/Moon light and wetness continue to come from the shared current-weather
 * snapshot. Natural Hills remains the compatibility default.
 */
public final class HeroGlAnalyticWorldRenderer {

    private static final float[] QUAD = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};

    private static final String VS = String.join("\n",
            "attribute vec2 aPosition;",
            "varying vec2 vUv;",
            "void main(){vUv=aPosition*0.5+0.5;gl_Position=vec4(aPosition,0.0,1.0);}");

    private static final String FS = String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH",
            "precision highp float;",
            "#else",
            "precision mediump float;",
            "#endif",
            "varying vec2 vUv;",
            "uniform vec2 uResolution;",
            "uniform float uSunAltitude;",
            "uniform vec2 uSunPos;",
            "uniform float uSunVis;",
            "uniform vec2 uMoonPos;",
            "uniform float uMoonVis;",
            "uniform float uMoonIllum;",
            "uniform float uCloud;",
            "uniform float uRain;",
            "uniform float uDrizzle;",
            "uniform float uStorm;",
            "uniform float uFog;",
            "uniform float uHaze;",
            "uniform float uSceneLight;",
            "uniform float uThermal;",
            "uniform float uParallax;",
            "uniform float uTime;",
            "uniform float uScenery;",
            "const float TAU=6.28318530718;",
            "float mode(float id){return 1.0-step(0.45,abs(uScenery-id));}",
            "void main(){",
            " vec2 p=vec2(vUv.x,1.0-vUv.y);",
            " float aspect=uResolution.x/max(1.0,uResolution.y);",
            " float x=(p.x-0.5)*(aspect/0.45)+0.5+(uParallax-0.5)*0.055;",
            " float mAuto=mode(0.0);float mOpen=mode(1.0);float mNature=max(mode(2.0),mAuto);",
            " float mVillage=mode(3.0);float mFarm=mode(4.0);float mRiver=mode(5.0);",
            " float mFlowers=mode(6.0);float mUrban=mode(7.0);",
            " float terrainW=clamp(mNature+mVillage*0.72+mFarm*0.30+mRiver*0.46+mFlowers*0.40+mUrban*0.18,0.0,1.0);",
            " float vegetationW=clamp(mNature*0.88+mVillage*0.72+mFarm*0.58+mRiver*0.72+mFlowers+mUrban*0.12,0.0,1.0);",
            " float settlementW=clamp(mNature*0.10+mVillage*0.78+mUrban,0.0,1.0);",
            " float openW=mOpen;",
            " float farLine=0.700+0.024*sin(TAU*(x*0.76)+0.3)+0.015*sin(TAU*(x*1.58)+1.1)+0.008*sin(TAU*(x*3.00)+0.4)+0.004*sin(TAU*(x*5.7)+2.0);",
            " float midLine=0.773+0.027*sin(TAU*(x*0.94)+1.8)+0.014*sin(TAU*(x*2.08)+0.5)+0.007*sin(TAU*(x*4.05)+2.0)+0.003*sin(TAU*(x*7.2)+0.8);",
            " float nearLine=0.842+0.020*sin(TAU*(x*1.20)+0.7)+0.011*sin(TAU*(x*2.80)+2.2)+0.005*sin(TAU*(x*5.35)+1.2);",
            " float canopyLine=0.873+0.009*sin(TAU*(x*4.7)+0.4)+0.006*sin(TAU*(x*8.9)+1.7)+0.0035*sin(TAU*(x*15.4)+2.6);",
            " float forestLine=0.892+0.006*sin(TAU*(x*5.4)+0.8)+0.004*sin(TAU*(x*10.1)+1.9);",
            " float farM=smoothstep(farLine-0.012,farLine+0.014,p.y)*terrainW;",
            " float midM=smoothstep(midLine-0.011,midLine+0.013,p.y)*terrainW;",
            " float nearM=smoothstep(nearLine-0.010,nearLine+0.012,p.y)*terrainW;",
            " float canopy=smoothstep(canopyLine-0.008,canopyLine+0.010,p.y)*(1.0-smoothstep(0.928,0.954,p.y))*vegetationW;",
            " float forest=smoothstep(forestLine-0.007,forestLine+0.009,p.y)*vegetationW;",
            " float precip=max(uRain,uDrizzle*0.65);",
            " float night=1.0-smoothstep(-7.0,1.5,uSunAltitude);",
            " float lunar=night*uMoonVis*uMoonIllum*(1.0-uCloud*0.5);",
            " float light=clamp(0.18+uSceneLight*0.74+lunar*0.18,0.16,0.95);",
            " float rainAir=precip*(0.08+uStorm*0.08);",
            " float haze=max(max(uFog*0.55,uHaze*0.25),rainAir);",
            " float warm=max(0.0,uThermal);float cold=max(0.0,-uThermal);",
            " vec3 farC=mix(vec3(0.055,0.085,0.120),vec3(0.25,0.32,0.36),light);",
            " vec3 midC=mix(vec3(0.032,0.058,0.086),vec3(0.15,0.22,0.25),light);",
            " vec3 nearC=mix(vec3(0.018,0.038,0.058),vec3(0.085,0.14,0.17),light);",
            " farC=mix(farC,vec3(0.31,0.28,0.23),warm*0.045);",
            " midC=mix(midC,vec3(0.18,0.22,0.28),cold*0.040);",
            " vec3 forestC=mix(vec3(0.010,0.024,0.030),vec3(0.043,0.082,0.074),light);",
            " vec3 color=vec3(0.0);float alpha=0.0;",
            " float a=farM*(0.42-haze*0.22);color=mix(color,farC,a);alpha=max(alpha,a);",
            " a=midM*(0.58-haze*0.18);color=mix(color,midC,a);alpha=max(alpha,a);",
            " a=nearM*(0.76-haze*0.08);color=mix(color,nearC,a);alpha=max(alpha,a);",
            " a=max(forest*0.66,canopy*0.52);color=mix(color,forestC,a);alpha=max(alpha,a);",
            " float groundStart=mOpen>0.5?0.975:0.925;",
            " float ground=smoothstep(groundStart,min(0.998,groundStart+0.033),p.y);",
            " vec3 groundC=mix(vec3(0.008,0.018,0.024),vec3(0.025,0.046,0.052),light);",
            " groundC=mix(groundC,vec3(0.032,0.052,0.060),cold*0.045);",
            " color=mix(color,groundC,ground*(1.0-openW*0.72));alpha=max(alpha,ground*(1.0-openW*0.72));",
            " float settlement=clamp((0.018+night*0.22+precip*0.055+uStorm*0.045)*(1.0-uFog*0.70)*settlementW,0.0,0.40);",
            " float cityCell=floor(fract(x)*15.0);float cityLocal=fract(x*15.0);",
            " float urbanHeight=mUrban*(0.028+0.040*(0.5+0.5*sin(cityCell*1.43)));",
            " float villageHeight=mVillage*(0.012+0.020*(0.5+0.5*sin(cityCell*2.07+0.9)));",
            " float cityTop=0.807-urbanHeight-villageHeight;",
            " float footprint=mUrban>0.5?0.70:0.54;",
            " float building=step(cityTop,p.y)*step(cityLocal,footprint)*(1.0-smoothstep(0.912,0.941,p.y))*settlement;",
            " vec3 cityC=mix(vec3(0.026,0.039,0.052),vec3(0.045,0.055,0.064),light);",
            " color=mix(color,cityC,building*0.64);alpha=max(alpha,building*0.64);",
            " float windowBand=step(0.84,fract(cityCell*0.618))*step(0.24,cityLocal)*step(cityLocal,0.42)*building*night*(1.0-uFog*0.70);",
            " color+=vec3(0.78,0.58,0.30)*windowBand*0.15;alpha=max(alpha,windowBand*0.10);",
            " float farmGround=mFarm*smoothstep(0.865,0.955,p.y);",
            " float farmPerspective=max(0.0,p.y-0.855);",
            " float farmRows=0.5+0.5*sin((x/(0.16+farmPerspective*1.9))*8.0+farmPerspective*42.0);",
            " float rowMask=farmGround*smoothstep(0.48,0.74,farmRows)*(1.0-uFog*0.55);",
            " vec3 farmC=mix(vec3(0.035,0.060,0.030),vec3(0.18,0.25,0.085),light);",
            " color=mix(color,farmC,rowMask*0.24);alpha=max(alpha,rowMask*0.20);",
            " float water=mRiver*smoothstep(0.885,0.945,p.y);",
            " float wave=0.5+0.5*sin(x*26.0+uTime*(0.18+precip*0.70));",
            " float wave2=0.5+0.5*sin(x*51.0-uTime*(0.12+precip*0.36)+1.7);",
            " vec3 waterC=mix(vec3(0.020,0.044,0.060),vec3(0.095,0.18,0.22),light);",
            " waterC+=vec3(0.025,0.038,0.050)*(wave*0.6+wave2*0.4);",
            " color=mix(color,waterC,water*(0.56-haze*0.16));alpha=max(alpha,water*0.48);",
            " float meadow=mFlowers*smoothstep(0.860,0.940,p.y);",
            " float leafTexture=0.5+0.5*sin(x*67.0+sin(x*19.0)*2.2);",
            " vec3 meadowC=mix(vec3(0.018,0.048,0.034),vec3(0.080,0.18,0.090),light);",
            " color=mix(color,meadowC,meadow*(0.34+leafTexture*0.12)*(1.0-uFog*0.42));alpha=max(alpha,meadow*0.38);",
            " float wet=smoothstep(0.16,0.74,precip)*ground*(1.0-mRiver);",
            " float reflectionBand=smoothstep(0.930,0.998,p.y);",
            " float ripple=0.5+0.5*sin(x*31.0+uTime*(0.68+uRain*1.20));",
            " float ripple2=0.5+0.5*sin(x*57.0-uTime*0.44+1.4);",
            " float wetSheen=wet*reflectionBand*(0.016+0.014*ripple+0.008*ripple2);",
            " vec3 reflected=mix(vec3(0.16,0.23,0.30),vec3(0.31,0.38,0.43),uSceneLight)*(0.62+uStorm*0.18);",
            " color=mix(color,reflected,clamp(wetSheen*3.0,0.0,0.15));",
            " float sunLow=smoothstep(0.60,0.94,uSunPos.y)*uSunVis*(1.0-night);",
            " float sunColumn=exp(-abs(p.x-uSunPos.x)*10.0)*reflectionBand*max(wet,water)*sunLow*(0.009+0.022*ripple);",
            " float moonColumn=exp(-abs(p.x-uMoonPos.x)*13.0)*reflectionBand*max(wet,water)*night*uMoonVis*uMoonIllum*(0.006+0.013*ripple2);",
            " color+=vec3(0.94,0.55,0.24)*sunColumn+vec3(0.42,0.58,0.78)*moonColumn;",
            " float lightReflection=windowBand*wet*reflectionBand*(0.020+0.028*ripple);",
            " color+=vec3(0.82,0.56,0.27)*lightReflection;",
            " gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.94));",
            "}");

    private final FloatBuffer quad;

    private int program;
    private int aPos;
    private int uRes;
    private int uSun;
    private int uSunPos;
    private int uSunVis;
    private int uMoonPos;
    private int uMoonVis;
    private int uMoonIll;
    private int uCloud;
    private int uRain;
    private int uDrizzle;
    private int uStorm;
    private int uFog;
    private int uHaze;
    private int uLight;
    private int uThermal;
    private int uParallax;
    private int uTime;
    private int uScenery;

    private int width = 1;
    private int height = 1;
    private long startNanos;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlAnalyticWorldRenderer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder());
        quad = buffer.asFloatBuffer();
        quad.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void onSurfaceCreated() {
        program = createProgram(VS, FS);
        aPos = GLES20.glGetAttribLocation(program, "aPosition");
        uRes = u("uResolution");
        uSun = u("uSunAltitude");
        uSunPos = u("uSunPos");
        uSunVis = u("uSunVis");
        uMoonPos = u("uMoonPos");
        uMoonVis = u("uMoonVis");
        uMoonIll = u("uMoonIllum");
        uCloud = u("uCloud");
        uRain = u("uRain");
        uDrizzle = u("uDrizzle");
        uStorm = u("uStorm");
        uFog = u("uFog");
        uHaze = u("uHaze");
        uLight = u("uSceneLight");
        uThermal = u("uThermal");
        uParallax = u("uParallax");
        uTime = u("uTime");
        uScenery = u("uScenery");
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
        GlSceneSnapshot scene = snapshot;
        if (program == 0 || scene == null) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uRes, width, height);
        GLES20.glUniform1f(uSun, scene.sunAltitude);
        GLES20.glUniform2f(uSunPos, scene.sunX, scene.sunY);
        GLES20.glUniform1f(uSunVis, scene.sunVisibility);
        GLES20.glUniform2f(uMoonPos, scene.moonX, scene.moonY);
        GLES20.glUniform1f(uMoonVis, scene.moonVisibility);
        GLES20.glUniform1f(uMoonIll, scene.moonIllumination);
        GLES20.glUniform1f(uCloud, scene.cloudCover);
        GLES20.glUniform1f(uRain, scene.rainIntensity);
        GLES20.glUniform1f(uDrizzle, scene.drizzleIntensity);
        GLES20.glUniform1f(uStorm, scene.stormIntensity);
        GLES20.glUniform1f(uFog, scene.fogIntensity);
        GLES20.glUniform1f(uHaze, scene.airHazeIntensity);
        GLES20.glUniform1f(uLight, scene.sceneLight);
        GLES20.glUniform1f(uThermal, scene.thermalBias);
        GLES20.glUniform1f(uParallax, scene.parallax);
        GLES20.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f);
        GLES20.glUniform1f(uScenery, SceneryRuntimeState.get().getShaderId());

        quad.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }

    private int u(@NonNull String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
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
            throw new IllegalStateException("OpenGL analytic world program link failed: " + log);
        }
        return result;
    }

    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("OpenGL analytic world shader compile failed: " + log);
        }
        return shader;
    }
}
