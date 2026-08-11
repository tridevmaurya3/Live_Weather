package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * ODM-4 final sky + cloud + celestial OpenGL ES 2.0 base renderer.
 *
 * Reality ownership remains outside the shader:
 * - Sun/Moon altitude + azimuth come from the astronomy engine.
 * - Moon phase angle/illumination come from the astronomy engine.
 * - Moon visibility comes from DynamicRealityComposer's atmosphere/daylight rules.
 * - Star visibility comes from astronomy darkness + weather/AQI transparency.
 *
 * This renderer only turns those resolved values into a high-fidelity scene.
 * Rain/wet-screen and storm/lightning are dedicated later passes, so their
 * legacy fragment work is deliberately absent here.
 */
public final class HeroGlCloudSceneRenderer {

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
            "uniform float uStarVis;",
            "uniform float uCloud;",
            "uniform float uCloudDensity;",
            "uniform float uCloudFar;",
            "uniform float uCloudMid;",
            "uniform float uCloudNear;",
            "uniform float uCloudCeiling;",
            "uniform float uCloudBrightness;",
            "uniform float uFog;",
            "uniform float uHaze;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uSceneLight;",
            "uniform float uVisibility;",
            "uniform float uParallax;",
            "",
            "float hash21(vec2 p){",
            "  p=fract(p*vec2(123.34,456.21));",
            "  p+=dot(p,p+45.32);",
            "  return fract(p.x*p.y);",
            "}",
            "float noise(vec2 p){",
            "  vec2 i=floor(p);",
            "  vec2 f=fract(p);",
            "  f=f*f*(3.0-2.0*f);",
            "  float a=hash21(i);",
            "  float b=hash21(i+vec2(1.0,0.0));",
            "  float c=hash21(i+vec2(0.0,1.0));",
            "  float d=hash21(i+vec2(1.0,1.0));",
            "  return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);",
            "}",
            "float fbm(vec2 p){",
            "  float v=0.0;",
            "  float a=0.52;",
            "  for(int i=0;i<4;i++){",
            "    v+=a*noise(p);",
            "    p=p*2.02+vec2(13.1,7.7);",
            "    a*=0.5;",
            "  }",
            "  return v;",
            "}",
            "float cloudNoise(vec2 p,float seed){",
            "  float warp=noise(p*0.47+vec2(seed*0.13,seed*0.29))-0.5;",
            "  vec2 wp=p+vec2(warp,-warp)*0.22;",
            "  float body=fbm(wp+vec2(seed*0.31,seed*0.17));",
            "  float breakup=fbm(wp*1.91+vec2(seed*1.73,seed*0.83));",
            "  return body*0.80+breakup*0.20;",
            "}",
            "float verticalBand(float y,float top,float bottom,float feather){",
            "  return smoothstep(top-feather,top+feather,y)*(1.0-smoothstep(bottom-feather,bottom+feather,y));",
            "}",
            "float cloudMask(float n,float threshold,float softness){",
            "  return smoothstep(threshold,threshold+softness,n);",
            "}",
            "float cloudEdge(float n,float threshold,float softness){",
            "  float outer=cloudMask(n,threshold,softness);",
            "  float inner=cloudMask(n,threshold+softness*0.48,softness*0.72);",
            "  return max(0.0,outer-inner);",
            "}",
            "",
            "float starPoint(vec2 p,vec2 grid,float seed,float threshold,float size,float twinkleSpeed){",
            "  vec2 q=p*grid;",
            "  vec2 id=floor(q);",
            "  vec2 f=fract(q)-0.5;",
            "  float rnd=hash21(id+vec2(seed,seed*1.71));",
            "  vec2 jitter=vec2(hash21(id+seed*2.13),hash21(id+seed*3.37))-0.5;",
            "  f-=jitter*0.54;",
            "  float d=length(f);",
            "  float point=1.0-smoothstep(size,size*2.25,d);",
            "  float gate=step(threshold,rnd);",
            "  float tw=0.90+0.10*sin(uTime*(twinkleSpeed+rnd*0.55)+rnd*38.0);",
            "  return point*gate*tw;",
            "}",
            "",
            "float mariaPattern(vec2 ml){",
            "  float m=0.0;",
            "  vec2 q1=(ml-vec2(-0.20,-0.10))*vec2(1.45,1.85);",
            "  vec2 q2=(ml-vec2(0.18,0.08))*vec2(1.85,1.45);",
            "  vec2 q3=(ml-vec2(0.05,-0.30))*vec2(2.35,1.75);",
            "  vec2 q4=(ml-vec2(-0.34,0.26))*vec2(2.55,2.05);",
            "  m+=exp(-dot(q1,q1)*7.2)*0.20;",
            "  m+=exp(-dot(q2,q2)*8.0)*0.15;",
            "  m+=exp(-dot(q3,q3)*8.8)*0.12;",
            "  m+=exp(-dot(q4,q4)*10.0)*0.09;",
            "  return m;",
            "}",
            "",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "",
            "  vec3 sky=mix(uHorizon,uMid,smoothstep(0.20,0.67,1.0-p.y));",
            "  sky=mix(sky,uTop,smoothstep(0.43,1.0,1.0-p.y));",
            "",
            "  float horizonMask=smoothstep(0.48,0.96,p.y);",
            "  float horizonHaze=(uFog*0.48+uHaze*0.30)*horizonMask;",
            "  sky=mix(sky,vec3(0.60,0.67,0.71),clamp(horizonHaze,0.0,0.48));",
            "",
            "  float astronomicalDark=1.0-smoothstep(-12.0,-3.0,uSunAltitude);",
            "  float deepNight=1.0-smoothstep(-18.0,-8.0,uSunAltitude);",
            "  float airglow=horizonMask*deepNight*(0.018+uVisibility*0.018)*(1.0-uHaze*0.55);",
            "  sky+=vec3(0.045,0.085,0.125)*airglow;",
            "  vec3 color=sky;",
            "",
            "  float moonDistance=length((p-uMoonPos)*vec2(aspect,1.0));",
            "  float moonGlare=exp(-moonDistance*8.0)*uMoonVis*(0.12+uMoonIllum*0.62)*astronomicalDark;",
            "  float starHorizon=1.0-smoothstep(0.70,0.97,p.y);",
            "  float starAtmosphere=starHorizon*(1.0-uHaze*0.52)*(1.0-uFog*0.70);",
            "  float starBase=clamp(uStarVis*starAtmosphere*(1.0-moonGlare*0.72),0.0,1.0);",
            "",
            "  float faintStars=starPoint(p,vec2(118.0,176.0),3.7,0.988-starBase*0.036,0.024,0.45);",
            "  float midStars=starPoint(p+vec2(0.0017,0.0023),vec2(84.0,132.0),11.2,0.991-starBase*0.025,0.032,0.62);",
            "  float brightStars=starPoint(p+vec2(0.0031,0.0011),vec2(61.0,98.0),19.4,0.995-starBase*0.015,0.044,0.78);",
            "",
            "  vec2 brightId=floor((p+vec2(0.0031,0.0011))*vec2(61.0,98.0));",
            "  float temp=hash21(brightId+vec2(28.3,7.1));",
            "  vec3 brightColor=temp<0.25?vec3(0.74,0.84,1.0):(temp>0.82?vec3(1.0,0.86,0.70):vec3(0.92,0.95,1.0));",
            "  color+=vec3(0.72,0.82,0.96)*faintStars*starBase*0.34;",
            "  color+=vec3(0.86,0.91,0.99)*midStars*starBase*0.58;",
            "  color+=brightColor*brightStars*starBase*0.92;",
            "",
            "  vec2 windVec=vec2(sin(uWindDir),-cos(uWindDir));",
            "  vec2 crossWind=vec2(-windVec.y,windVec.x);",
            "  float driftBase=0.48+uWind*1.32;",
            "  float centeredParallax=uParallax-0.5;",
            "  float presenceGate=smoothstep(0.025,0.11,uCloud);",
            "",
            "  vec2 qFar=p*vec2(1.08,1.64)+windVec*uTime*(0.0030*driftBase)+crossWind*sin(uTime*0.031+1.4)*0.008+vec2(centeredParallax*0.014,0.0);",
            "  float nFar=cloudNoise(qFar,2.7);",
            "  float farThreshold=0.73-uCloudDensity*0.15;",
            "  float farCloud=cloudMask(nFar,farThreshold,0.17)*verticalBand(p.y,0.025,0.58,0.080)*uCloudFar*presenceGate;",
            "  float farEdge=cloudEdge(nFar,farThreshold,0.17)*farCloud;",
            "",
            "  vec2 qMid=p*vec2(1.58,2.18)+windVec*uTime*(0.0061*driftBase)+crossWind*sin(uTime*0.041+3.2)*0.012+vec2(4.7,1.6)+vec2(centeredParallax*0.028,0.0);",
            "  float nMid=cloudNoise(qMid,7.1);",
            "  float midThreshold=0.69-uCloudDensity*0.18;",
            "  float midCloud=cloudMask(nMid,midThreshold,0.145)*verticalBand(p.y,0.045,0.68,0.082)*uCloudMid*presenceGate;",
            "  float midEdge=cloudEdge(nMid,midThreshold,0.145)*midCloud;",
            "",
            "  vec2 qNear=p*vec2(2.08,2.76)+windVec*uTime*(0.0092*driftBase)+crossWind*sin(uTime*0.052+5.6)*0.016+vec2(9.4,5.3)+vec2(centeredParallax*0.044,0.0);",
            "  float nNear=cloudNoise(qNear,12.9);",
            "  float nearThreshold=0.66-uCloudDensity*0.19;",
            "  float nearCloud=cloudMask(nNear,nearThreshold,0.13)*verticalBand(p.y,0.075,0.77,0.088)*uCloudNear*presenceGate;",
            "  float nearEdge=cloudEdge(nNear,nearThreshold,0.13)*nearCloud;",
            "",
            "  vec2 qCeil=p*vec2(1.30,1.78)+windVec*uTime*(0.0070*driftBase)+crossWind*sin(uTime*0.036+8.1)*0.010+vec2(15.2,3.7)+vec2(centeredParallax*0.022,0.0);",
            "  float nCeil=cloudNoise(qCeil,18.6);",
            "  float ceilingShape=cloudMask(nCeil,0.54-uCloudCeiling*0.13,0.145);",
            "  float ceiling=ceilingShape*verticalBand(p.y,0.0,0.60,0.085)*uCloudCeiling*presenceGate;",
            "",
            "  float farShade=clamp((nFar-farThreshold)*2.25+0.43,0.0,1.0);",
            "  float midShade=clamp((nMid-midThreshold)*2.55+0.36,0.0,1.0);",
            "  float nearShade=clamp((nNear-nearThreshold)*2.85+0.30,0.0,1.0);",
            "  float stormShade=clamp(uCloudCeiling*0.74+(1.0-uCloudBrightness)*0.34,0.0,1.0);",
            "  float bright=clamp(uCloudBrightness,0.18,1.0);",
            "  vec2 sunDelta=(uSunPos-p)*vec2(aspect,1.0);",
            "  float sunEdge=exp(-length(sunDelta)*1.55)*uSunVis*(1.0-stormShade*0.72);",
            "  float silver=(farEdge*0.22+midEdge*0.48+nearEdge*0.72)*sunEdge;",
            "",
            "  vec3 farLow=mix(vec3(0.58,0.64,0.70),vec3(0.19,0.23,0.29),stormShade);",
            "  vec3 farHigh=mix(vec3(0.92,0.94,0.95),vec3(0.40,0.44,0.50),stormShade);",
            "  vec3 farColor=mix(farLow,farHigh,farShade)*mix(0.78,1.0,bright);",
            "  float farAlpha=clamp(farCloud*(0.17+uCloudDensity*0.15),0.0,0.34);",
            "  color=mix(color,farColor,farAlpha);",
            "",
            "  vec3 midLow=mix(vec3(0.49,0.55,0.61),vec3(0.11,0.14,0.19),stormShade);",
            "  vec3 midHigh=mix(vec3(0.91,0.93,0.94),vec3(0.32,0.36,0.42),stormShade);",
            "  vec3 midColor=mix(midLow,midHigh,midShade)*mix(0.71,1.0,bright);",
            "  float midAlpha=clamp(midCloud*(0.33+uCloudDensity*0.27),0.0,0.64);",
            "  color=mix(color,midColor,midAlpha);",
            "",
            "  vec3 nearLow=mix(vec3(0.42,0.48,0.55),vec3(0.055,0.075,0.105),stormShade);",
            "  vec3 nearHigh=mix(vec3(0.89,0.91,0.92),vec3(0.25,0.29,0.35),stormShade);",
            "  vec3 nearColor=mix(nearLow,nearHigh,nearShade)*mix(0.66,1.0,bright);",
            "  float nearAlpha=clamp(nearCloud*(0.45+uCloudDensity*0.35),0.0,0.83);",
            "  color=mix(color,nearColor,nearAlpha);",
            "",
            "  float ceilShade=clamp(nCeil*1.72-0.36,0.0,1.0);",
            "  vec3 ceilLow=vec3(0.032,0.046,0.070);",
            "  vec3 ceilHigh=mix(vec3(0.18,0.22,0.27),vec3(0.29,0.33,0.39),bright*0.43);",
            "  vec3 ceilColor=mix(ceilLow,ceilHigh,ceilShade);",
            "  float ceilingAlpha=clamp(ceiling*(0.56+uCloudCeiling*0.24),0.0,0.90);",
            "  color=mix(color,ceilColor,ceilingAlpha);",
            "",
            "  color+=vec3(1.0,0.94,0.78)*silver*0.20;",
            "  float internalShadow=clamp((nearCloud*nearAlpha+midCloud*midAlpha)*stormShade*0.07,0.0,0.065);",
            "  color*=1.0-internalShadow;",
            "",
            "  float cloudTotal=1.0-(1.0-farCloud*0.28)*(1.0-midCloud*0.60)*(1.0-nearCloud*0.79)*(1.0-ceiling*0.88);",
            "  cloudTotal=clamp(cloudTotal,0.0,1.0);",
            "",
            "  vec2 sp=(p-uSunPos)*vec2(aspect,1.0);",
            "  float sd=length(sp);",
            "  float sunObscure=clamp(1.0-cloudTotal*0.90,0.03,1.0);",
            "  float sunGlow=exp(-sd*22.0)*uSunVis*sunObscure;",
            "  float sunDisc=1.0-smoothstep(0.028,0.033,sd);",
            "  color+=vec3(1.0,0.72,0.25)*sunGlow*0.82;",
            "  color=mix(color,vec3(1.0,0.91,0.48),sunDisc*uSunVis*sunObscure);",
            "",
            "  vec2 mp=(p-uMoonPos)*vec2(aspect,1.0);",
            "  float moonDistanceLocal=length(mp);",
            "  float moonRadius=0.031;",
            "  vec2 ml=mp/moonRadius;",
            "  float m2=dot(ml,ml);",
            "  float localMoonTrans=clamp(1.0-cloudTotal*0.82,0.025,1.0);",
            "  float moonAltitudeWarm=1.0-smoothstep(5.0,24.0,uMoonAltitude);",
            "  vec3 moonBase=mix(vec3(0.88,0.91,0.96),vec3(0.96,0.84,0.67),moonAltitudeWarm*0.34);",
            "",
            "  if(m2<1.0 && uMoonVis>0.001){",
            "    float mz=sqrt(max(0.0,1.0-m2));",
            "    float incident=ml.x*sin(uMoonPhase)+mz*(-cos(uMoonPhase));",
            "    float lit=smoothstep(-0.030,0.050,incident);",
            "    float darkness=astronomicalDark;",
            "    float earthshine=(0.004+0.022*darkness)*(1.0-uMoonIllum*0.58);",
            "    float phaseLight=earthshine+lit*(0.96-earthshine)*(0.62+0.38*max(0.0,incident));",
            "    float limb=1.0-smoothstep(0.90,1.0,sqrt(m2));",
            "    float limbShade=0.74+0.26*mz;",
            "    float fineNoise=fbm(ml*5.6+vec2(4.2,1.7));",
            "    float maria=mariaPattern(ml);",
            "    float surface=clamp(0.84+fineNoise*0.15-maria,0.60,1.02);",
            "    float craterSmall=noise(ml*17.0+vec2(8.1,2.4));",
            "    surface*=0.94+craterSmall*0.08;",
            "    float discAlpha=uMoonVis*localMoonTrans*limb;",
            "    vec3 lunarColor=moonBase*phaseLight*limbShade*surface;",
            "    color=mix(color,lunarColor,clamp(discAlpha,0.0,1.0));",
            "  }",
            "",
            "  float haloStrength=uMoonVis*localMoonTrans*(0.035+uMoonIllum*0.20)*astronomicalDark;",
            "  float moonHalo=exp(-moonDistanceLocal*17.0)*haloStrength;",
            "  float moonAura=exp(-moonDistanceLocal*6.5)*haloStrength*0.22;",
            "  color+=mix(vec3(0.30,0.42,0.60),vec3(0.40,0.48,0.58),moonAltitudeWarm*0.30)*(moonHalo+moonAura);",
            "",
            "  float lowMist=(uFog*0.30+uHaze*0.08)*smoothstep(0.62,0.99,p.y);",
            "  color=mix(color,vec3(0.42,0.49,0.54),clamp(lowMist,0.0,0.30));",
            "",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),1.0);",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program;
    private int width = 1;
    private int height = 1;
    private long startNanos;

    private int aPosition;
    private int uResolution;
    private int uTime;
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
    private int uStarVis;
    private int uCloud;
    private int uCloudDensity;
    private int uCloudFar;
    private int uCloudMid;
    private int uCloudNear;
    private int uCloudCeiling;
    private int uCloudBrightness;
    private int uFog;
    private int uHaze;
    private int uWind;
    private int uWindDir;
    private int uSceneLight;
    private int uVisibility;
    private int uParallax;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlCloudSceneRenderer() {
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
        uStarVis = uniform("uStarVis");
        uCloud = uniform("uCloud");
        uCloudDensity = uniform("uCloudDensity");
        uCloudFar = uniform("uCloudFar");
        uCloudMid = uniform("uCloudMid");
        uCloudNear = uniform("uCloudNear");
        uCloudCeiling = uniform("uCloudCeiling");
        uCloudBrightness = uniform("uCloudBrightness");
        uFog = uniform("uFog");
        uHaze = uniform("uHaze");
        uWind = uniform("uWind");
        uWindDir = uniform("uWindDir");
        uSceneLight = uniform("uSceneLight");
        uVisibility = uniform("uVisibility");
        uParallax = uniform("uParallax");
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
        GLES20.glClearColor(0.02f, 0.04f, 0.08f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GlSceneSnapshot state = snapshot;
        if (program == 0 || state == null) return;

        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, width, height);
        GLES20.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f);
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
        GLES20.glUniform1f(uStarVis, state.starVisibility);
        GLES20.glUniform1f(uCloud, state.cloudCover);
        GLES20.glUniform1f(uCloudDensity, state.cloudDensity);
        GLES20.glUniform1f(uCloudFar, state.cloudFarLayer);
        GLES20.glUniform1f(uCloudMid, state.cloudMidLayer);
        GLES20.glUniform1f(uCloudNear, state.cloudNearLayer);
        GLES20.glUniform1f(uCloudCeiling, state.cloudStormCeiling);
        GLES20.glUniform1f(uCloudBrightness, state.cloudBrightness);
        GLES20.glUniform1f(uFog, state.fogIntensity);
        GLES20.glUniform1f(uHaze, state.airHazeIntensity);
        GLES20.glUniform1f(uWind, state.windStrength);
        GLES20.glUniform1f(uWindDir, state.windDirectionRadians);
        GLES20.glUniform1f(uSceneLight, state.sceneLight);
        GLES20.glUniform1f(uVisibility, state.visibilityFactor);
        GLES20.glUniform1f(uParallax, state.parallax);

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
            throw new IllegalStateException("OpenGL sky/cloud/celestial program link failed: " + log);
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
            throw new IllegalStateException("OpenGL sky/cloud/celestial shader compile failed: " + log);
        }
        return shader;
    }
}
