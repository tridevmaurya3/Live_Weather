package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * ODM-1D final Sky + Cloud Foundation renderer.
 *
 * Cloud presence comes from CloudPresenceResolver. This renderer turns that
 * presence into independent far, mid, near and storm-ceiling fields with
 * centered parallax, different wind speeds, subtle cross-wind turbulence,
 * feathered coverage, sun-facing edge light and darker internal cloud mass.
 *
 * The main acceptance contract is deliberately strict: no decorative clouds in
 * genuinely clear conditions, no full-screen cloud tint outside a cloud mask,
 * no rectangular texture bounds and no single scrolling grey sheet.
 */
public final class HeroGlCloudSceneRenderer {

    private static final float[] QUAD = {
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
    };

    private static final String VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "varying vec2 vUv;\n" +
            "void main(){\n" +
            "  vUv=aPosition*0.5+0.5;\n" +
            "  gl_Position=vec4(aPosition,0.0,1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vUv;\n" +
            "uniform vec2 uResolution;\n" +
            "uniform float uTime;\n" +
            "uniform vec3 uTop;\n" +
            "uniform vec3 uMid;\n" +
            "uniform vec3 uHorizon;\n" +
            "uniform vec2 uSunPos;\n" +
            "uniform float uSunVis;\n" +
            "uniform vec2 uMoonPos;\n" +
            "uniform float uMoonVis;\n" +
            "uniform float uMoonIllum;\n" +
            "uniform float uMoonPhase;\n" +
            "uniform float uStarVis;\n" +
            "uniform float uCloud;\n" +
            "uniform float uCloudDensity;\n" +
            "uniform float uCloudFar;\n" +
            "uniform float uCloudMid;\n" +
            "uniform float uCloudNear;\n" +
            "uniform float uCloudCeiling;\n" +
            "uniform float uCloudBrightness;\n" +
            "uniform float uRain;\n" +
            "uniform float uDrizzle;\n" +
            "uniform float uFog;\n" +
            "uniform float uStorm;\n" +
            "uniform float uHaze;\n" +
            "uniform float uWind;\n" +
            "uniform float uWindDir;\n" +
            "uniform float uSceneLight;\n" +
            "uniform float uVisibility;\n" +
            "uniform float uParallax;\n" +

            "float hash21(vec2 p){\n" +
            "  p=fract(p*vec2(123.34,456.21));\n" +
            "  p+=dot(p,p+45.32);\n" +
            "  return fract(p.x*p.y);\n" +
            "}\n" +
            "float hash11(float p){ return fract(sin(p*127.1)*43758.5453); }\n" +
            "float noise(vec2 p){\n" +
            "  vec2 i=floor(p); vec2 f=fract(p);\n" +
            "  f=f*f*(3.0-2.0*f);\n" +
            "  float a=hash21(i);\n" +
            "  float b=hash21(i+vec2(1.0,0.0));\n" +
            "  float c=hash21(i+vec2(0.0,1.0));\n" +
            "  float d=hash21(i+vec2(1.0,1.0));\n" +
            "  return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);\n" +
            "}\n" +
            "float fbm(vec2 p){\n" +
            "  float v=0.0; float a=0.52;\n" +
            "  for(int i=0;i<4;i++){\n" +
            "    v+=a*noise(p);\n" +
            "    p=p*2.02+vec2(13.1,7.7);\n" +
            "    a*=0.5;\n" +
            "  }\n" +
            "  return v;\n" +
            "}\n" +
            "float cloudNoise(vec2 p,float seed){\n" +
            "  float warp=noise(p*0.47+vec2(seed*0.13,seed*0.29))-0.5;\n" +
            "  vec2 wp=p+vec2(warp,-warp)*0.22;\n" +
            "  float body=fbm(wp+vec2(seed*0.31,seed*0.17));\n" +
            "  float breakup=fbm(wp*1.91+vec2(seed*1.73,seed*0.83));\n" +
            "  return body*0.80+breakup*0.20;\n" +
            "}\n" +
            "float verticalBand(float y,float top,float bottom,float feather){\n" +
            "  return smoothstep(top-feather,top+feather,y)*(1.0-smoothstep(bottom-feather,bottom+feather,y));\n" +
            "}\n" +
            "float cloudMask(float n,float threshold,float softness){\n" +
            "  return smoothstep(threshold,threshold+softness,n);\n" +
            "}\n" +
            "float cloudEdge(float n,float threshold,float softness){\n" +
            "  float outer=cloudMask(n,threshold,softness);\n" +
            "  float inner=cloudMask(n,threshold+softness*0.48,softness*0.72);\n" +
            "  return max(0.0,outer-inner);\n" +
            "}\n" +

            "float rainLayer(vec2 p,float scale,float speed,float seed,float slope){\n" +
            "  vec2 q=p; q.x+=q.y*slope; q*=vec2(31.0,16.0)*scale;\n" +
            "  vec2 id=floor(q); float rnd=hash21(id+seed);\n" +
            "  float x=fract(q.x)-0.5+(rnd-0.5)*0.42;\n" +
            "  float y=fract(q.y+uTime*speed*(0.80+rnd*0.46)+rnd*7.0);\n" +
            "  float core=1.0-smoothstep(0.020,0.070,abs(x));\n" +
            "  float tail=smoothstep(0.01,0.24,y)*(1.0-smoothstep(0.66,0.98,y));\n" +
            "  return core*tail*step(0.28,rnd);\n" +
            "}\n" +
            "float wetDrop(vec2 p,float seed){\n" +
            "  vec2 grid=vec2(7.0,11.0); vec2 g=p*grid; vec2 id=floor(g);\n" +
            "  float rnd=hash21(id+seed); vec2 f=fract(g)-0.5;\n" +
            "  float slide=fract(uTime*(0.014+0.040*rnd)+rnd*8.0);\n" +
            "  f.y+=slide-0.5; f.x+=(rnd-0.5)*0.34; f.y*=0.70;\n" +
            "  float d=length(f);\n" +
            "  float rim=smoothstep(0.33,0.235,d)-smoothstep(0.215,0.145,d);\n" +
            "  float body=(1.0-smoothstep(0.27,0.31,d))*0.12;\n" +
            "  return (rim*0.78+body)*step(0.63,rnd);\n" +
            "}\n" +

            "float boltCenter(float y,float anchor,float seed,float y0,float y1,float drift){\n" +
            "  float t=clamp((y-y0)/max(0.001,y1-y0),0.0,1.0);\n" +
            "  float seg=t*12.0; float idx=floor(seg); float f=fract(seg);\n" +
            "  f=f*f*(3.0-2.0*f);\n" +
            "  float a=(hash11(seed+idx*1.73)-0.5)*0.115;\n" +
            "  float b=(hash11(seed+(idx+1.0)*1.73)-0.5)*0.115;\n" +
            "  float micro=(noise(vec2(t*31.0,seed*0.19))-0.5)*0.025;\n" +
            "  return anchor+mix(a,b,f)+micro+drift*t;\n" +
            "}\n" +
            "float boltLine(vec2 p,float anchor,float seed,float y0,float y1,float width,float drift){\n" +
            "  float inside=step(y0,p.y)*step(p.y,y1);\n" +
            "  float x=boltCenter(p.y,anchor,seed,y0,y1,drift);\n" +
            "  float aspect=uResolution.x/max(1.0,uResolution.y);\n" +
            "  float d=abs((p.x-x)*aspect);\n" +
            "  float core=exp(-d/max(0.0006,width));\n" +
            "  float glow=exp(-d/max(0.0022,width*5.0))*0.34;\n" +
            "  return (core+glow)*inside;\n" +
            "}\n" +

            "void main(){\n" +
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);\n" +
            "  float aspect=uResolution.x/max(1.0,uResolution.y);\n" +

            "  vec3 sky=mix(uHorizon,uMid,smoothstep(0.20,0.67,1.0-p.y));\n" +
            "  sky=mix(sky,uTop,smoothstep(0.43,1.0,1.0-p.y));\n" +
            "  float horizonHaze=(uFog*0.47+uHaze*0.29)*(1.0-smoothstep(0.48,0.92,p.y));\n" +
            "  sky=mix(sky,vec3(0.60,0.67,0.71),clamp(horizonHaze,0.0,0.48));\n" +
            "  vec3 color=sky;\n" +

            "  vec2 starGrid=p*vec2(92.0,148.0);\n" +
            "  vec2 sid=floor(starGrid); vec2 sf=fract(starGrid)-0.5;\n" +
            "  float sr=hash21(sid+vec2(31.7,9.2));\n" +
            "  float tw=0.74+0.26*sin(uTime*(0.8+sr*2.1)+sr*40.0);\n" +
            "  float star=(1.0-smoothstep(0.015,0.055,length(sf)))*step(0.985-uStarVis*0.045,sr)*uStarVis*tw;\n" +
            "  color+=vec3(0.82,0.90,1.0)*star;\n" +

            "  vec2 windVec=vec2(sin(uWindDir),-cos(uWindDir));\n" +
            "  vec2 crossWind=vec2(-windVec.y,windVec.x);\n" +
            "  float driftBase=0.48+uWind*1.32;\n" +
            "  float centeredParallax=uParallax-0.5;\n" +
            "  float presenceGate=smoothstep(0.025,0.11,uCloud);\n" +

            "  vec2 qFar=p*vec2(1.08,1.64)+windVec*uTime*(0.0030*driftBase)+crossWind*sin(uTime*0.031+1.4)*0.008+vec2(centeredParallax*0.014,0.0);\n" +
            "  float nFar=cloudNoise(qFar,2.7);\n" +
            "  float farThreshold=0.73-uCloudDensity*0.15;\n" +
            "  float farCloud=cloudMask(nFar,farThreshold,0.17)*verticalBand(p.y,0.025,0.58,0.080)*uCloudFar*presenceGate;\n" +
            "  float farEdge=cloudEdge(nFar,farThreshold,0.17)*farCloud;\n" +

            "  vec2 qMid=p*vec2(1.58,2.18)+windVec*uTime*(0.0061*driftBase)+crossWind*sin(uTime*0.041+3.2)*0.012+vec2(4.7,1.6)+vec2(centeredParallax*0.028,0.0);\n" +
            "  float nMid=cloudNoise(qMid,7.1);\n" +
            "  float midThreshold=0.69-uCloudDensity*0.18;\n" +
            "  float midCloud=cloudMask(nMid,midThreshold,0.145)*verticalBand(p.y,0.045,0.68,0.082)*uCloudMid*presenceGate;\n" +
            "  float midEdge=cloudEdge(nMid,midThreshold,0.145)*midCloud;\n" +

            "  vec2 qNear=p*vec2(2.08,2.76)+windVec*uTime*(0.0092*driftBase)+crossWind*sin(uTime*0.052+5.6)*0.016+vec2(9.4,5.3)+vec2(centeredParallax*0.044,0.0);\n" +
            "  float nNear=cloudNoise(qNear,12.9);\n" +
            "  float nearThreshold=0.66-uCloudDensity*0.19;\n" +
            "  float nearCloud=cloudMask(nNear,nearThreshold,0.13)*verticalBand(p.y,0.075,0.77,0.088)*uCloudNear*presenceGate;\n" +
            "  float nearEdge=cloudEdge(nNear,nearThreshold,0.13)*nearCloud;\n" +

            "  vec2 qCeil=p*vec2(1.30,1.78)+windVec*uTime*(0.0070*driftBase)+crossWind*sin(uTime*0.036+8.1)*0.010+vec2(15.2,3.7)+vec2(centeredParallax*0.022,0.0);\n" +
            "  float nCeil=cloudNoise(qCeil,18.6);\n" +
            "  float ceilingShape=cloudMask(nCeil,0.54-uCloudCeiling*0.13,0.145);\n" +
            "  float ceiling=ceilingShape*verticalBand(p.y,0.0,0.60,0.085)*uCloudCeiling*presenceGate;\n" +

            "  float farShade=clamp((nFar-farThreshold)*2.25+0.43,0.0,1.0);\n" +
            "  float midShade=clamp((nMid-midThreshold)*2.55+0.36,0.0,1.0);\n" +
            "  float nearShade=clamp((nNear-nearThreshold)*2.85+0.30,0.0,1.0);\n" +
            "  float stormShade=clamp(uStorm*0.90+uRain*0.16+uCloudCeiling*0.28,0.0,1.0);\n" +
            "  float bright=clamp(uCloudBrightness,0.18,1.0);\n" +
            "  vec2 sunDelta=(uSunPos-p)*vec2(aspect,1.0);\n" +
            "  float sunEdge=exp(-length(sunDelta)*1.55)*uSunVis*(1.0-stormShade*0.72);\n" +
            "  float silver=(farEdge*0.22+midEdge*0.48+nearEdge*0.72)*sunEdge;\n" +

            "  vec3 farLow=mix(vec3(0.58,0.64,0.70),vec3(0.19,0.23,0.29),stormShade);\n" +
            "  vec3 farHigh=mix(vec3(0.92,0.94,0.95),vec3(0.40,0.44,0.50),stormShade);\n" +
            "  vec3 farColor=mix(farLow,farHigh,farShade)*mix(0.78,1.0,bright);\n" +
            "  float farAlpha=clamp(farCloud*(0.17+uCloudDensity*0.15),0.0,0.34);\n" +
            "  color=mix(color,farColor,farAlpha);\n" +

            "  vec3 midLow=mix(vec3(0.49,0.55,0.61),vec3(0.11,0.14,0.19),stormShade);\n" +
            "  vec3 midHigh=mix(vec3(0.91,0.93,0.94),vec3(0.32,0.36,0.42),stormShade);\n" +
            "  vec3 midColor=mix(midLow,midHigh,midShade)*mix(0.71,1.0,bright);\n" +
            "  float midAlpha=clamp(midCloud*(0.33+uCloudDensity*0.27),0.0,0.64);\n" +
            "  color=mix(color,midColor,midAlpha);\n" +

            "  vec3 nearLow=mix(vec3(0.42,0.48,0.55),vec3(0.055,0.075,0.105),stormShade);\n" +
            "  vec3 nearHigh=mix(vec3(0.89,0.91,0.92),vec3(0.25,0.29,0.35),stormShade);\n" +
            "  vec3 nearColor=mix(nearLow,nearHigh,nearShade)*mix(0.66,1.0,bright);\n" +
            "  float nearAlpha=clamp(nearCloud*(0.45+uCloudDensity*0.35),0.0,0.83);\n" +
            "  color=mix(color,nearColor,nearAlpha);\n" +

            "  float ceilShade=clamp(nCeil*1.72-0.36,0.0,1.0);\n" +
            "  vec3 ceilLow=vec3(0.032,0.046,0.070);\n" +
            "  vec3 ceilHigh=mix(vec3(0.18,0.22,0.27),vec3(0.29,0.33,0.39),bright*0.43);\n" +
            "  vec3 ceilColor=mix(ceilLow,ceilHigh,ceilShade);\n" +
            "  float ceilingAlpha=clamp(ceiling*(0.56+uStorm*0.28),0.0,0.90);\n" +
            "  color=mix(color,ceilColor,ceilingAlpha);\n" +

            "  color+=vec3(1.0,0.94,0.78)*silver*0.20;\n" +
            "  float internalShadow=clamp((nearCloud*nearAlpha+midCloud*midAlpha)*stormShade*0.07,0.0,0.065);\n" +
            "  color*=1.0-internalShadow;\n" +

            "  float cloudTotal=1.0-(1.0-farCloud*0.28)*(1.0-midCloud*0.60)*(1.0-nearCloud*0.79)*(1.0-ceiling*0.88);\n" +
            "  cloudTotal=clamp(cloudTotal,0.0,1.0);\n" +

            "  vec2 sp=(p-uSunPos)*vec2(aspect,1.0); float sd=length(sp);\n" +
            "  float sunObscure=clamp(1.0-cloudTotal*0.90,0.03,1.0);\n" +
            "  float sunGlow=exp(-sd*22.0)*uSunVis*sunObscure;\n" +
            "  float sunDisc=1.0-smoothstep(0.028,0.033,sd);\n" +
            "  color+=vec3(1.0,0.72,0.25)*sunGlow*0.82;\n" +
            "  color=mix(color,vec3(1.0,0.91,0.48),sunDisc*uSunVis*sunObscure);\n" +

            "  vec2 mp=(p-uMoonPos)*vec2(aspect,1.0); float mr=0.031; vec2 ml=mp/mr; float m2=dot(ml,ml);\n" +
            "  if(m2<1.0 && uMoonVis>0.001){\n" +
            "    float mz=sqrt(max(0.0,1.0-m2));\n" +
            "    float incident=ml.x*sin(uMoonPhase)+mz*(-cos(uMoonPhase));\n" +
            "    float lit=smoothstep(-0.035,0.055,incident);\n" +
            "    float earth=0.016+0.030*(1.0-uSceneLight);\n" +
            "    float lunar=earth+lit*(0.98-earth)*(0.58+0.42*max(0.0,incident));\n" +
            "    float limb=1.0-smoothstep(0.88,1.0,sqrt(m2));\n" +
            "    float crater=0.88+0.12*noise(ml*5.8+vec2(3.7,1.9));\n" +
            "    float ma=uMoonVis*(1.0-cloudTotal*0.86)*limb;\n" +
            "    vec3 moonCol=vec3(0.86,0.89,0.94)*lunar*crater;\n" +
            "    color=mix(color,moonCol,ma);\n" +
            "  }\n" +
            "  float moonGlow=exp(-length(mp)*16.0)*uMoonVis*(0.14+uMoonIllum*0.40)*(1.0-cloudTotal*0.82);\n" +
            "  color+=vec3(0.32,0.42,0.58)*moonGlow;\n" +

            "  float effectiveRain=max(uRain,uDrizzle*0.62);\n" +
            "  float slope=sin(uWindDir)*(0.08+uWind*0.42);\n" +
            "  float rFar=rainLayer(p,1.52,0.62,3.1,slope);\n" +
            "  float rMid=rainLayer(p+vec2(0.13,0.07),1.02,0.90,8.4,slope);\n" +
            "  float rNear=rainLayer(p+vec2(0.27,0.19),0.52,1.42,14.7,slope);\n" +
            "  float rain=(rFar*0.32+rMid*0.56+rNear*0.94)*effectiveRain;\n" +
            "  color=mix(color,vec3(0.77,0.87,0.95),clamp(rain,0.0,0.90));\n" +
            "  float rainVeil=smoothstep(0.38,0.95,effectiveRain)*(0.025+noise(p*vec2(8.0,19.0)+uTime*vec2(0.0,2.3))*0.040);\n" +
            "  color=mix(color,vec3(0.47,0.56,0.63),rainVeil);\n" +
            "  float wet=(wetDrop(p,2.4)+wetDrop(p+vec2(0.07,0.11),9.7))*smoothstep(0.15,0.82,effectiveRain);\n" +
            "  color+=vec3(0.28,0.42,0.54)*wet;\n" +

            "  float flashWindow=max(4.3,7.4-uStorm*2.7);\n" +
            "  float cycle=floor(uTime/flashWindow); float phase=mod(uTime,flashWindow);\n" +
            "  float chance=hash11(cycle+3.7); float eventStart=0.42+hash11(cycle+11.9)*1.55;\n" +
            "  float local=phase-eventStart; float active=step(chance,0.22+uStorm*0.62);\n" +
            "  float pulse=0.0;\n" +
            "  if(local>=0.0 && local<0.085) pulse=1.0-local/0.085;\n" +
            "  else if(local>=0.16 && local<0.255) pulse=(1.0-(local-0.16)/0.095)*0.54;\n" +
            "  pulse*=active*uStorm;\n" +
            "  float anchor=0.16+hash11(cycle+21.3)*0.68;\n" +
            "  float y0=0.055+hash11(cycle+30.4)*0.090;\n" +
            "  float y1=0.48+hash11(cycle+41.8)*0.22;\n" +
            "  float visibleBolt=step(0.0,local)*step(local,0.19)*active*uStorm;\n" +
            "  float drift=(hash11(cycle+52.1)-0.5)*0.08;\n" +
            "  float mainBolt=boltLine(p,anchor,cycle+6.3,y0,y1,0.0014,drift)*visibleBolt;\n" +
            "  float b1Start=mix(y0,y1,0.34);\n" +
            "  float b1Anchor=boltCenter(b1Start,anchor,cycle+6.3,y0,y1,drift);\n" +
            "  float branchDrift1=mix(-0.15,0.15,step(0.5,hash11(cycle+73.0)));\n" +
            "  float branch1=boltLine(p,b1Anchor,cycle+71.4,b1Start,min(y1,b1Start+0.18),0.0011,branchDrift1)*visibleBolt*0.78;\n" +
            "  float b2Start=mix(y0,y1,0.58);\n" +
            "  float b2Anchor=boltCenter(b2Start,anchor,cycle+6.3,y0,y1,drift);\n" +
            "  float branchDrift2=mix(-0.12,0.12,step(0.5,hash11(cycle+96.0)));\n" +
            "  float branch2=boltLine(p,b2Anchor,cycle+93.6,b2Start,min(y1,b2Start+0.14),0.0009,branchDrift2)*visibleBolt*0.60;\n" +
            "  float electrical=clamp(mainBolt+branch1+branch2,0.0,1.25);\n" +
            "  float cloudFlash=pulse*(0.28+0.72*exp(-abs(p.x-anchor)*3.1))*(1.0-smoothstep(0.62,0.88,p.y));\n" +
            "  color=mix(color,vec3(0.73,0.82,0.96),clamp(cloudFlash*cloudTotal*0.86,0.0,0.76));\n" +
            "  color+=vec3(0.86,0.93,1.0)*electrical*1.45;\n" +
            "  float wholeFlash=clamp(pulse*(0.52+uStorm*0.20),0.0,0.78);\n" +
            "  color=mix(color,vec3(0.90,0.95,1.0),wholeFlash);\n" +

            "  float groundMist=(uFog*0.40+effectiveRain*0.14)*(1.0-smoothstep(0.64,1.0,p.y));\n" +
            "  color=mix(color,vec3(0.47,0.53,0.57),clamp(groundMist,0.0,0.40));\n" +
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),1.0);\n" +
            "}\n";

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
    private int uMoonPos;
    private int uMoonVis;
    private int uMoonIllum;
    private int uMoonPhase;
    private int uStarVis;
    private int uCloud;
    private int uCloudDensity;
    private int uCloudFar;
    private int uCloudMid;
    private int uCloudNear;
    private int uCloudCeiling;
    private int uCloudBrightness;
    private int uRain;
    private int uDrizzle;
    private int uFog;
    private int uStorm;
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
        uMoonPos = uniform("uMoonPos");
        uMoonVis = uniform("uMoonVis");
        uMoonIllum = uniform("uMoonIllum");
        uMoonPhase = uniform("uMoonPhase");
        uStarVis = uniform("uStarVis");
        uCloud = uniform("uCloud");
        uCloudDensity = uniform("uCloudDensity");
        uCloudFar = uniform("uCloudFar");
        uCloudMid = uniform("uCloudMid");
        uCloudNear = uniform("uCloudNear");
        uCloudCeiling = uniform("uCloudCeiling");
        uCloudBrightness = uniform("uCloudBrightness");
        uRain = uniform("uRain");
        uDrizzle = uniform("uDrizzle");
        uFog = uniform("uFog");
        uStorm = uniform("uStorm");
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
        GLES20.glUniform2f(uMoonPos, state.moonX, state.moonY);
        GLES20.glUniform1f(uMoonVis, state.moonVisibility);
        GLES20.glUniform1f(uMoonIllum, state.moonIllumination);
        GLES20.glUniform1f(uMoonPhase, state.moonPhaseAngleRadians);
        GLES20.glUniform1f(uStarVis, state.starVisibility);
        GLES20.glUniform1f(uCloud, state.cloudCover);
        GLES20.glUniform1f(uCloudDensity, state.cloudDensity);
        GLES20.glUniform1f(uCloudFar, state.cloudFarLayer);
        GLES20.glUniform1f(uCloudMid, state.cloudMidLayer);
        GLES20.glUniform1f(uCloudNear, state.cloudNearLayer);
        GLES20.glUniform1f(uCloudCeiling, state.cloudStormCeiling);
        GLES20.glUniform1f(uCloudBrightness, state.cloudBrightness);
        GLES20.glUniform1f(uRain, state.rainIntensity);
        GLES20.glUniform1f(uDrizzle, state.drizzleIntensity);
        GLES20.glUniform1f(uFog, state.fogIntensity);
        GLES20.glUniform1f(uStorm, state.stormIntensity);
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
            throw new IllegalStateException("OpenGL program link failed: " + log);
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
            throw new IllegalStateException("OpenGL shader compile failed: " + log);
        }
        return shader;
    }
}
