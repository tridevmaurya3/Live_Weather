package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.domain.scene.SceneryMode;
import com.tridev.liveweather.domain.scene.SceneryRuntimeState;
import com.tridev.liveweather.domain.scene.SceneryVariantRuntimeState;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Deterministic atmospheric world shared by app Hero and Live Wallpaper.
 *
 * Scenery S4 adds four stable visual composition variants per user-facing scene.
 * Variant 1 preserves the S3 baseline; variants 2-4 alter only presentation geometry,
 * spacing and material rhythm. Weather truth still comes exclusively from GlSceneSnapshot.
 */
public final class HeroGlAnalyticWorldRenderer {

    private static final long SCENERY_TRANSITION_NANOS = 1_800_000_000L;
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
            "uniform float uWind;",
            "uniform float uTime;",
            "uniform float uSceneryFrom;",
            "uniform float uSceneryTo;",
            "uniform float uSceneryMix;",
            "uniform float uVariantFrom;",
            "uniform float uVariantTo;",
            "const float TAU=6.28318530718;",
            "float modeAt(float scene,float id){return 1.0-step(0.45,abs(scene-id));}",
            "float mode(float id){return mix(modeAt(uSceneryFrom,id),modeAt(uSceneryTo,id),uSceneryMix);}",
            "void main(){",
            " vec2 p=vec2(vUv.x,1.0-vUv.y);",
            " float aspect=uResolution.x/max(1.0,uResolution.y);",
            " float baseX=(p.x-0.5)*(aspect/0.45)+0.5;",
            " float par=uParallax-0.5;",
            " float variant=mix(uVariantFrom,uVariantTo,uSceneryMix);",
            " float vNorm=clamp(variant/3.0,0.0,1.0);",
            " float vPhase=variant*1.6180339;",
            " float vAlt=sin(variant*2.17+0.4);",
            " float xFar=baseX+par*(0.018+vNorm*0.003);",
            " float xMid=baseX+par*(0.046+vNorm*0.005);",
            " float xNear=baseX+par*(0.082+vNorm*0.008);",
            " float mAuto=mode(0.0);float mOpen=mode(1.0);float mNature=max(mode(2.0),mAuto);",
            " float mVillage=mode(3.0);float mFarm=mode(4.0);float mRiver=mode(5.0);",
            " float mFlowers=mode(6.0);float mUrban=mode(7.0);",
            " float terrainW=clamp(mNature+mVillage*0.72+mFarm*0.30+mRiver*0.46+mFlowers*0.40+mUrban*0.18,0.0,1.0);",
            " float vegetationW=clamp(mNature+mVillage*0.72+mFarm*0.58+mRiver*0.72+mFlowers+mUrban*0.12,0.0,1.0);",
            " float openW=mOpen;",
            " float farLine=0.700+vNorm*0.005*vAlt+0.024*sin(TAU*(xFar*(0.76+vNorm*0.05))+0.3+vPhase*0.31)+0.015*sin(TAU*(xFar*(1.58+vNorm*0.11))+1.1-vPhase*0.17)+0.008*sin(TAU*(xFar*3.00)+0.4+vPhase*0.23)+0.004*sin(TAU*(xFar*5.7)+2.0);",
            " float midLine=0.773-vNorm*0.004*vAlt+0.027*sin(TAU*(xMid*(0.94+vNorm*0.06))+1.8-vPhase*0.21)+0.014*sin(TAU*(xMid*(2.08+vNorm*0.10))+0.5+vPhase*0.19)+0.007*sin(TAU*(xMid*4.05)+2.0+vPhase*0.12)+0.003*sin(TAU*(xMid*7.2)+0.8);",
            " float nearLine=0.842+vNorm*0.003*sin(vPhase)+0.020*sin(TAU*(xNear*(1.20+vNorm*0.08))+0.7+vPhase*0.16)+0.011*sin(TAU*(xNear*(2.80+vNorm*0.16))+2.2-vPhase*0.13)+0.005*sin(TAU*(xNear*5.35)+1.2+vPhase*0.09);",
            " float canopyLine=0.873+0.009*sin(TAU*(xNear*(4.7+vNorm*0.32))+0.4+vPhase*0.11)+0.006*sin(TAU*(xNear*8.9)+1.7-vPhase*0.09)+0.0035*sin(TAU*(xNear*15.4)+2.6);",
            " float forestLine=0.892+0.006*sin(TAU*(xMid*(5.4+vNorm*0.24))+0.8-vPhase*0.08)+0.004*sin(TAU*(xMid*10.1)+1.9+vPhase*0.07);",
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
            " farC=mix(farC,vec3(0.21,0.30,0.28),vNorm*0.035*mNature);",
            " vec3 forestC=mix(vec3(0.010,0.024,0.030),vec3(0.043,0.082,0.074),light);",
            " vec3 color=vec3(0.0);float alpha=0.0;",
            " float a=farM*(0.42-haze*0.22);color=mix(color,farC,a);alpha=max(alpha,a);",
            " a=midM*(0.58-haze*0.18);color=mix(color,midC,a);alpha=max(alpha,a);",
            " a=nearM*(0.76-haze*0.08);color=mix(color,nearC,a);alpha=max(alpha,a);",
            " a=max(forest*0.66,canopy*0.52);color=mix(color,forestC,a);alpha=max(alpha,a);",
            " float farEdge=(1.0-smoothstep(farLine-0.007,farLine+0.011,p.y))*smoothstep(farLine-0.020,farLine-0.004,p.y)*terrainW;",
            " float midEdge=(1.0-smoothstep(midLine-0.006,midLine+0.010,p.y))*smoothstep(midLine-0.017,midLine-0.003,p.y)*terrainW;",
            " float rimLight=(0.012+light*0.028)*(1.0-haze*0.72)*(1.0-uStorm*0.45);",
            " color+=vec3(0.34,0.39,0.40)*farEdge*rimLight+vec3(0.20,0.27,0.28)*midEdge*rimLight*0.72;",
            " float groundStart=mix(0.925-vNorm*0.004*sin(vPhase),0.975-vNorm*0.005,mOpen);",
            " float ground=smoothstep(groundStart,min(0.998,groundStart+0.033),p.y);",
            " vec3 groundC=mix(vec3(0.008,0.018,0.024),vec3(0.025,0.046,0.052),light);",
            " groundC=mix(groundC,vec3(0.032,0.052,0.060),cold*0.045);",
            " color=mix(color,groundC,ground*(1.0-openW*0.72));alpha=max(alpha,ground*(1.0-openW*0.72));",
            " float contactShade=smoothstep(0.885,0.998,p.y)*(1.0-openW*0.72)*(0.04+0.05*(1.0-light));",
            " color=mix(color,vec3(0.006,0.012,0.016),contactShade);",
            " float cityScale=15.0+variant*1.35;",
            " float cityCell=floor(fract(xNear)*cityScale);float cityLocal=fract(xNear*cityScale);",
            " float legacyTop=0.775+0.035*(0.5+0.5*sin(cityCell*2.07+0.9));",
            " float urbanHeight=0.028+(0.040+vNorm*0.010)*(0.5+0.5*sin(cityCell*(1.43+vNorm*0.12)+vPhase*0.20));",
            " float villageHeight=0.012+(0.020+vNorm*0.006)*(0.5+0.5*sin(cityCell*(2.07+vNorm*0.08)+0.9+vPhase*0.16));",
            " float designedScene=clamp(mUrban+mVillage,0.0,1.0);",
            " float designedTop=0.807-mUrban*urbanHeight-mVillage*villageHeight;",
            " float cityTop=mix(legacyTop,designedTop,designedScene);",
            " float footprint=0.54+0.16*mUrban+vNorm*0.035*sin(cityCell*0.71+vPhase);",
            " float buildingShape=step(cityTop,p.y)*step(cityLocal,footprint)*(1.0-smoothstep(0.912,0.941,p.y));",
            " float legacySettlement=clamp((0.018+night*0.22+precip*0.055+uStorm*0.045)*(1.0-uFog*0.70)*mNature,0.0,0.40);",
            " float sceneStructure=designedScene*(0.46+light*0.24)*(1.0-uFog*0.68);",
            " float building=buildingShape*max(legacySettlement,sceneStructure);",
            " vec3 cityC=mix(vec3(0.026,0.039,0.052),vec3(0.045,0.055,0.064),light);",
            " vec3 villageC=mix(vec3(0.055,0.038,0.027),vec3(0.18,0.14,0.105),light);",
            " cityC=mix(cityC,vec3(0.055,0.067,0.073),vNorm*0.18);",
            " villageC=mix(villageC,vec3(0.20,0.16,0.10),vNorm*0.12);",
            " vec3 structureC=mix(cityC,villageC,mVillage);",
            " color=mix(color,structureC,building*mix(0.52,0.82,designedScene));alpha=max(alpha,building*mix(0.52,0.82,designedScene));",
            " float facadeSplit=0.40+vNorm*0.13;",
            " float facadeShade=building*step(facadeSplit,cityLocal)*(0.06+0.08*(1.0-light));",
            " color=mix(color,vec3(0.018,0.026,0.032),facadeShade);",
            " float roofCenter=0.27+vNorm*0.055*sin(cityCell+vPhase);",
            " float roofLocal=abs(cityLocal-roofCenter);",
            " float roofLine=cityTop-(0.025+vNorm*0.004)+roofLocal*(0.090+vNorm*0.018);",
            " float roof=mVillage*step(roofLine,p.y)*(1.0-step(cityTop+0.004,p.y))*step(cityLocal,0.54+vNorm*0.04)*sceneStructure;",
            " color=mix(color,mix(vec3(0.075,0.036,0.020),vec3(0.28,0.14,0.075),light),roof*0.88);alpha=max(alpha,roof*0.82);",
            " float road=mVillage*smoothstep(0.912,0.936,p.y)*(1.0-smoothstep(0.970,0.993,p.y));",
            " float roadCurve=0.5+0.5*sin(xNear*(4.0+variant*0.34)+0.8+vPhase*0.31);",
            " road*=smoothstep(0.16,0.48,roadCurve)*(1.0-uFog*0.45);",
            " color=mix(color,mix(vec3(0.030,0.030,0.026),vec3(0.115,0.100,0.075),light),road*0.20);alpha=max(alpha,road*0.10);",
            " float windowEnd=0.41+0.14*mUrban+vNorm*0.035;",
            " float windowShape=step(0.80-vNorm*0.04,fract(cityCell*0.618+vPhase*0.07))*step(0.22,cityLocal)*step(cityLocal,windowEnd);",
            " float windowBand=windowShape*buildingShape*night*(1.0-uFog*0.70)*(0.20*mUrban+0.13*mVillage+0.10*mNature);",
            " color+=vec3(0.78,0.58,0.30)*windowBand*0.55;alpha=max(alpha,windowBand*0.14);",
            " float sway=sin(uTime*(0.42+uWind*0.72)+xNear*(19.0+variant*1.6)+vPhase)*uWind;",
            " float farmGround=mFarm*smoothstep(0.855-vNorm*0.004,0.950,p.y);",
            " float farmPerspective=max(0.0,p.y-(0.850-vNorm*0.003));",
            " vec3 fieldBase=mix(vec3(0.022,0.040,0.020),vec3(0.095,0.135,0.050),light);",
            " fieldBase=mix(fieldBase,vec3(0.12,0.12,0.045),vNorm*0.10);",
            " color=mix(color,fieldBase,farmGround*(0.22+light*0.12)*(1.0-uFog*0.52));alpha=max(alpha,farmGround*0.28);",
            " float farmFreq=8.0+variant*1.25;",
            " float farmRows=0.5+0.5*sin((xNear/(0.16+farmPerspective*1.9))*farmFreq+farmPerspective*(42.0+variant*4.0)+sway*0.10+vPhase);",
            " float rowMask=farmGround*smoothstep(0.48,0.74,farmRows)*(1.0-uFog*0.55);",
            " float fineRows=0.5+0.5*sin((xNear/(0.11+farmPerspective*2.5))*(13.0+variant*1.7)+farmPerspective*(61.0+variant*3.2)-sway*0.07-vPhase*0.5);",
            " vec3 farmC=mix(vec3(0.035,0.060,0.030),vec3(0.18,0.25,0.085),light);",
            " color=mix(color,farmC,rowMask*(0.34+vNorm*0.04));alpha=max(alpha,rowMask*0.25);",
            " color+=vec3(0.10,0.12,0.045)*fineRows*farmGround*(0.026+vNorm*0.006)*(1.0-uFog*0.60);",
            " float riverShift=vNorm*0.006*sin(vPhase*1.3);",
            " float bank=mRiver*smoothstep(0.862+riverShift,0.878+riverShift,p.y)*(1.0-smoothstep(0.892+riverShift,0.906+riverShift,p.y));",
            " vec3 bankC=mix(vec3(0.018,0.038,0.026),vec3(0.080,0.115,0.062),light);",
            " bankC=mix(bankC,vec3(0.105,0.095,0.060),vNorm*0.10);",
            " color=mix(color,bankC,bank*(0.45-haze*0.16));alpha=max(alpha,bank*0.40);",
            " float water=mRiver*smoothstep(0.885+riverShift,0.945,p.y);",
            " float wave=0.5+0.5*sin(xMid*(26.0+variant*2.8)+uTime*(0.18+precip*0.70)+vPhase);",
            " float wave2=0.5+0.5*sin(xNear*(51.0+variant*4.0)-uTime*(0.12+precip*0.36)+1.7-vPhase*0.6);",
            " float microWave=0.5+0.5*sin(xNear*(91.0+variant*7.0)+uTime*(0.16+uWind*0.32)+vPhase*0.4);",
            " vec3 waterC=mix(vec3(0.020,0.044,0.060),vec3(0.095,0.18,0.22),light);",
            " waterC=mix(waterC,vec3(0.070,0.155,0.145),vNorm*0.10);",
            " waterC+=vec3(0.025,0.038,0.050)*(wave*0.55+wave2*0.32+microWave*0.13);",
            " color=mix(color,waterC,water*(0.64-haze*0.18));alpha=max(alpha,water*0.55);",
            " float waterGlint=water*(1.0-uFog*0.72)*(0.5+0.5*microWave)*(0.008+uSunVis*0.020+uMoonVis*night*0.008);",
            " color+=mix(vec3(0.17,0.24,0.30),vec3(0.52,0.60,0.63),light)*waterGlint;",
            " float meadow=mFlowers*smoothstep(0.850-vNorm*0.005,0.935,p.y);",
            " float leafTexture=0.5+0.5*sin(xNear*(67.0+variant*5.0)+sin(xNear*(19.0+variant*1.2)+sway*0.12+vPhase)*2.2);",
            " vec3 meadowC=mix(vec3(0.018,0.048,0.034),vec3(0.080,0.18,0.090),light);",
            " meadowC=mix(meadowC,vec3(0.065,0.17,0.11),vNorm*0.12);",
            " color=mix(color,meadowC,meadow*(0.42+leafTexture*0.16)*(1.0-uFog*0.42));alpha=max(alpha,meadow*0.46);",
            " float flowerA=step(0.955-vNorm*0.018,0.5+0.5*sin(xNear*(113.0+variant*4.0)+floor(p.y*112.0)*2.31+sway*0.16+vPhase));",
            " float flowerB=step(0.965-vNorm*0.016,0.5+0.5*sin(xNear*(79.0+variant*3.0)-floor(p.y*96.0)*1.73+1.4-sway*0.11-vPhase*0.7));",
            " float flowerDots=meadow*max(flowerA,flowerB)*(1.0-uFog*0.72)*(0.34+light*0.44);",
            " vec3 flowerPaletteA=mix(vec3(0.92,0.50,0.64),vec3(0.70,0.58,0.95),vNorm);",
            " vec3 flowerPaletteB=mix(vec3(0.96,0.78,0.34),vec3(0.96,0.66,0.40),vNorm);",
            " vec3 flowerC=mix(flowerPaletteA,flowerPaletteB,flowerB);",
            " color=mix(color,flowerC,flowerDots*0.42);alpha=max(alpha,flowerDots*0.18);",
            " float wet=smoothstep(0.16,0.74,precip)*ground*(1.0-mRiver);",
            " float reflectionBand=smoothstep(0.930,0.998,p.y);",
            " float ripple=0.5+0.5*sin(xNear*(31.0+variant*1.8)+uTime*(0.68+uRain*1.20)+vPhase*0.2);",
            " float ripple2=0.5+0.5*sin(xNear*(57.0+variant*2.6)-uTime*0.44+1.4-vPhase*0.2);",
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
    private int uWind;
    private int uTime;
    private int uSceneryFrom;
    private int uSceneryTo;
    private int uSceneryMix;
    private int uVariantFrom;
    private int uVariantTo;

    private int width = 1;
    private int height = 1;
    private long startNanos;
    private long sceneryTransitionStartedNanos;

    @NonNull private SceneryMode sceneryFrom = SceneryMode.NATURAL_HILLS;
    @NonNull private SceneryMode sceneryTo = SceneryMode.NATURAL_HILLS;
    private int variantFrom;
    private int variantTo;
    private float sceneryMix = 1f;

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
        uWind = u("uWind");
        uTime = u("uTime");
        uSceneryFrom = u("uSceneryFrom");
        uSceneryTo = u("uSceneryTo");
        uSceneryMix = u("uSceneryMix");
        uVariantFrom = u("uVariantFrom");
        uVariantTo = u("uVariantTo");
        startNanos = System.nanoTime();
        sceneryFrom = SceneryRuntimeState.get();
        sceneryTo = sceneryFrom;
        variantFrom = SceneryVariantRuntimeState.get();
        variantTo = variantFrom;
        sceneryMix = 1f;
        sceneryTransitionStartedNanos = startNanos;
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

        long nowNanos = System.nanoTime();
        updateSceneryTransition(nowNanos);

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
        GLES20.glUniform1f(uWind, scene.windStrength);
        GLES20.glUniform1f(uTime, (nowNanos - startNanos) / 1_000_000_000f);
        GLES20.glUniform1f(uSceneryFrom, sceneryFrom.getShaderId());
        GLES20.glUniform1f(uSceneryTo, sceneryTo.getShaderId());
        GLES20.glUniform1f(uSceneryMix, sceneryMix);
        GLES20.glUniform1f(uVariantFrom, variantFrom);
        GLES20.glUniform1f(uVariantTo, variantTo);

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

    private void updateSceneryTransition(long nowNanos) {
        SceneryMode requestedScene = SceneryRuntimeState.get();
        int requestedVariant = SceneryVariantRuntimeState.get();
        if (requestedScene != sceneryTo || requestedVariant != variantTo) {
            if (sceneryMix >= 0.5f) {
                sceneryFrom = sceneryTo;
                variantFrom = variantTo;
            }
            sceneryTo = requestedScene;
            variantTo = requestedVariant;
            sceneryMix = 0f;
            sceneryTransitionStartedNanos = nowNanos;
        }

        if (sceneryFrom == sceneryTo && variantFrom == variantTo) {
            sceneryMix = 1f;
            return;
        }

        float linear = clamp(
                (nowNanos - sceneryTransitionStartedNanos) / (float) SCENERY_TRANSITION_NANOS,
                0f,
                1f
        );
        sceneryMix = linear * linear * (3f - 2f * linear);
        if (linear >= 1f) {
            sceneryFrom = sceneryTo;
            variantFrom = variantTo;
            sceneryMix = 1f;
        }
    }

    private int u(@NonNull String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
