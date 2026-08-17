package com.tridev.liveweather.ui.gl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.LiveWeatherApplication;
import com.tridev.liveweather.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Photoreal cloud-atlas renderer shared by the app Hero and Live Wallpaper.
 *
 * Video-driven cloud cluster realism:
 * - portrait-scale widths are reduced so one cloud cannot read as a screen-wide loaf,
 * - cloud coverage is rebuilt from several smaller vertically fuller puff clusters,
 * - irregular lobes create cauliflower-like crowns and uneven soft bases,
 * - procedural volume keeps interiors continuous while the atlas supplies texture/detail,
 * - subtle contour evolution preserves formation/dissolve without pulsing,
 * - weather truth remains authoritative and clear conditions never invent cloud cover.
 */
public final class HeroGlTextureCloudRenderer {

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
            "uniform sampler2D uAtlas;",
            "uniform vec2 uResolution;",
            "uniform float uTime;",
            "uniform float uCloud;",
            "uniform float uDensity;",
            "uniform float uFarLayer;",
            "uniform float uMidLayer;",
            "uniform float uNearLayer;",
            "uniform float uStormCeiling;",
            "uniform float uRain;",
            "uniform float uStorm;",
            "uniform float uBrightness;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uParallax;",
            "uniform float uDetail;",
            "uniform float uSceneLight;",
            "uniform vec2 uSunPos;",
            "uniform float uSunVis;",
            "uniform float uSunAltitude;",
            "uniform vec2 uMoonPos;",
            "uniform float uMoonVis;",

            "vec4 atlasSample(vec2 uv,float index){",
            "  float col=mod(index,4.0);float row=floor(index/4.0);",
            "  vec2 atlasUv=(vec2(col,1.0-row)+vec2(uv.x,1.0-uv.y))/vec2(4.0,2.0);",
            "  vec4 tex=texture2D(uAtlas,atlasUv);vec3 rgb=tex.rgb;",
            "  float hi=max(rgb.r,max(rgb.g,rgb.b));",
            "  float luma=dot(rgb,vec3(0.2126,0.7152,0.0722));",
            "  float body=smoothstep(0.018,0.255,max(hi,luma));",
            "  float alpha=tex.a*body;",
            "  rgb=max(vec3(0.0),(rgb-vec3(0.012))/0.988);",
            "  rgb=mix(vec3(dot(rgb,vec3(0.25,0.62,0.13))),rgb,0.76);",
            "  return vec4(rgb,alpha);",
            "}",

            "float ellipseMask(vec2 q,vec2 center,vec2 radius){",
            "  float d=length((q-center)/max(radius,vec2(0.001)));",
            "  return 1.0-smoothstep(0.76,1.10,d);",
            "}",

            "float roundedCloudMask(vec2 q,float seed,float evolve){",
            "  float lift=(evolve-0.5)*0.022;",
            "  float wobble=0.018*sin(q.y*12.0+seed*1.9)+0.010*sin(q.y*25.0-seed*0.7);",
            "  vec2 wq=vec2(q.x+wobble,q.y);",
            "  float lowL=ellipseMask(wq,vec2(0.31,0.710+lift*0.10),vec2(0.175,0.180));",
            "  float lowM=ellipseMask(wq,vec2(0.50,0.735-lift*0.10),vec2(0.205,0.190));",
            "  float lowR=ellipseMask(wq,vec2(0.69,0.695+lift*0.06),vec2(0.170,0.175));",
            "  float midL=ellipseMask(wq,vec2(0.285,0.545+lift),vec2(0.155,0.225));",
            "  float midM=ellipseMask(wq,vec2(0.500,0.505-lift*0.25),vec2(0.190,0.255));",
            "  float midR=ellipseMask(wq,vec2(0.690,0.540+lift*0.20),vec2(0.155,0.220));",
            "  float crownL=ellipseMask(wq,vec2(0.395,0.385-lift*0.45),vec2(0.145,0.205));",
            "  float crownM=ellipseMask(wq,vec2(0.535+0.018*sin(seed),0.315-lift*0.50),vec2(0.155,0.215));",
            "  float crownR=ellipseMask(wq,vec2(0.645,0.405+lift*0.25),vec2(0.125,0.180));",
            "  float cap=ellipseMask(wq,vec2(0.505,0.225-lift*0.30),vec2(0.105,0.135));",
            "  float lobes=max(max(lowL,max(lowM,lowR)),max(max(midL,max(midM,midR)),max(max(crownL,max(crownM,crownR)),cap)));",
            "  float notchA=ellipseMask(wq,vec2(0.195,0.400),vec2(0.080,0.125));",
            "  float notchB=ellipseMask(wq,vec2(0.805,0.455),vec2(0.072,0.118));",
            "  float baseRipple=0.020*sin(q.x*17.0+seed*2.1)+0.011*sin(q.x*31.0-seed);",
            "  float lowerFade=1.0-smoothstep(0.845+baseRipple,0.985+baseRipple,q.y);",
            "  float feather=smoothstep(0.0,0.070,q.x)*smoothstep(0.0,0.070,1.0-q.x)*smoothstep(0.0,0.050,q.y)*lowerFade;",
            "  float sideBreak=1.0-0.15*max(notchA,notchB);",
            "  return clamp(lobes*feather*sideBreak,0.0,1.0);",
            "}",

            "vec4 spriteWrapped(vec2 p,vec2 center,vec2 size,float cell,float opacity,float mirrorX,float formSeed){",
            "  float dx=p.x-center.x;dx-=floor(dx+0.5);",
            "  vec2 q=vec2(dx/size.x+0.5,(p.y-center.y)/size.y+0.5);",
            "  float inside=step(0.0,q.x)*step(q.x,1.0)*step(0.0,q.y)*step(q.y,1.0);",
            "  if(mirrorX>0.5){q.x=1.0-q.x;}",
            "  float evolve=0.5+0.5*sin(uTime*(0.028+0.004*mod(formSeed,3.0))+formSeed*1.731+cell*0.83);",
            "  vec2 q0=clamp(q,0.0,1.0);",
            "  vec2 q1=clamp(q+vec2(0.027,-0.021),0.0,1.0);",
            "  vec2 q2=clamp(q+vec2(-0.024,0.026),0.0,1.0);",
            "  vec4 s0=atlasSample(q0,cell);",
            "  vec4 s1=atlasSample(q1,cell);",
            "  vec4 s2=atlasSample(q2,cell);",
            "  float shape=roundedCloudMask(q,formSeed,evolve);",
            "  float formation=0.94+0.06*evolve;",
            "  float texDetail=clamp((s0.a+s1.a+s2.a)/2.10,0.0,1.0);",
            "  float volume=shape*(0.76+0.24*texDetail);",
            "  vec3 atlasRgb=(s0.rgb+s1.rgb+s2.rgb)/3.0;",
            "  float atlasLum=dot(atlasRgb,vec3(0.2126,0.7152,0.0722));",
            "  float microA=sin(q.x*17.0+q.y*13.0+formSeed);",
            "  float microB=sin(q.x*9.0-q.y*21.0+cell*0.7);",
            "  float micro=0.5+0.5*microA*microB;",
            "  float topLight=1.16-0.30*smoothstep(0.22,0.94,q.y);",
            "  float sideLight=0.97+0.055*(1.0-q.x);",
            "  float textureTone=mix(0.94,1.07,clamp(atlasLum*0.65+micro*0.35,0.0,1.0));",
            "  vec3 neutral=vec3(0.78,0.81,0.85)*topLight*sideLight*textureTone;",
            "  vec3 detailRgb=mix(neutral,atlasRgb,0.18+0.16*texDetail);",
            "  return vec4(detailRgb,inside*opacity*volume*formation);",
            "}",

            "void over(inout vec3 color,inout float alpha,vec4 s,vec3 tint){",
            "  float a=clamp(s.a,0.0,0.64);",
            "  color=mix(color,s.rgb*tint,a);",
            "  alpha=1.0-(1.0-alpha)*(1.0-a);",
            "}",

            "void main(){",
            "  if(uCloud<0.015){gl_FragColor=vec4(0.0);return;}",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  p.x=(p.x-0.5)*aspect+0.5;",

            "  float detail=clamp(uDetail,0.5,1.0);",
            "  float cover=clamp(uCloud,0.0,1.0);",
            "  float density=clamp(uDensity,0.0,1.0);",
            "  float farLayer=clamp(uFarLayer,0.0,1.0);",
            "  float midLayer=clamp(uMidLayer,0.0,1.0);",
            "  float nearLayer=clamp(uNearLayer,0.0,1.0);",
            "  float ceilingTruth=clamp(uStormCeiling,0.0,1.0);",
            "  float mass=clamp(cover*0.67+density*0.43,0.0,1.0);",

            "  float gust=smoothstep(0.54,0.94,uWind);",
            "  float gustPulse=0.5+0.5*sin(uTime*(0.64+uWind*0.74)+uWindDir*1.7);",
            "  float gustMod=1.0+gust*(0.06+0.095*gustPulse);",
            "  float speed=0.0128*(0.58+uWind*1.55)*gustMod;",
            "  float projectedWind=sin(uWindDir)+cos(uWindDir)*0.38;",
            "  float direction=projectedWind<0.0?-1.0:1.0;",
            "  float cross=sin(uTime*(0.31+uWind*0.26)+uWindDir*2.1)*0.008*gust;",
            "  float lift=cos(uTime*(0.26+uWind*0.20)+uWindDir)*0.0060*gust;",
            "  float breatheA=sin(uTime*0.062+0.7)*0.0055;",
            "  float breatheB=sin(uTime*0.043+2.2)*0.0045;",
            "  float drift=direction*uTime*speed*(0.74+0.26*abs(projectedWind))+(uParallax-0.5)*0.055+cross;",

            "  float cell=uStorm>0.08?2.0:(uRain>0.06?1.0:(cover>0.78?0.0:(cover>0.52?7.0:(cover>0.25?6.0:5.0))));",
            "  float farCell=cover>0.68?0.0:(cover>0.32?7.0:4.0);",
            "  float altCell=mod(cell+3.0,8.0);",
            "  float farAlt=mod(farCell+5.0,8.0);",

            "  float weatherShade=clamp(uStorm*0.72+uRain*0.17+(1.0-uBrightness)*0.16+density*0.045,0.0,1.0);",
            "  float shade=mix(1.0,0.70,weatherShade);",
            "  vec3 tint=vec3(shade*0.98,shade*1.00,shade*1.025);",

            "  float twilight=clamp(1.0-abs(uSunAltitude)/16.0,0.0,1.0)*uSunVis;",
            "  vec3 warmTint=vec3(1.08,0.96,0.83);",
            "  vec3 moonTint=vec3(0.84,0.91,1.08);",
            "  tint*=mix(vec3(1.0),warmTint,twilight*0.14*(1.0-weatherShade));",
            "  tint*=mix(vec3(1.0),moonTint,uMoonVis*(1.0-uSceneLight)*0.07);",

            "  float farTruth=smoothstep(0.015,0.62,farLayer);",
            "  float midTruth=smoothstep(0.03,0.72,midLayer);",
            "  float nearTruth=smoothstep(0.05,0.78,nearLayer);",
            "  float farOpacity=(0.070+mass*0.205)*smoothstep(0.035,0.25,cover)*(0.16+0.84*farTruth);",
            "  float midOpacity=(0.115+mass*0.320)*smoothstep(0.12,0.48,cover)*(0.12+0.88*midTruth);",
            "  float nearOpacity=(0.105+mass*0.350)*smoothstep(0.28,0.72,cover)*(0.08+0.92*nearTruth);",
            "  float farDrift=drift*0.44;",
            "  float midDrift=drift*0.80;",
            "  float nearDrift=drift*1.15;",
            "  float formA=0.78+0.22*(0.5+0.5*sin(uTime*0.036+0.9));",
            "  float formB=0.78+0.22*(0.5+0.5*sin(uTime*0.031+2.4));",
            "  float formC=0.78+0.22*(0.5+0.5*sin(uTime*0.040+4.1));",

            "  vec3 color=vec3(0.0);",
            "  float alpha=0.0;",

            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.08+farDrift),0.185+lift*0.14+breatheB),vec2(0.18,0.205),farCell,farOpacity*0.84,0.0,1.1),tint*1.08);",
            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.36+farDrift*1.02),0.245-lift*0.10-breatheA),vec2(0.17,0.195),farAlt,farOpacity*0.64*formA,1.0,2.7),tint*1.055);",
            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.64+farDrift*1.06),0.210+breatheA*0.30),vec2(0.16,0.185),farCell,farOpacity*0.52*formB,0.0,4.2),tint*1.04);",
            "  if(detail>0.72){over(color,alpha,spriteWrapped(p,vec2(fract(0.88+farDrift*0.96),0.280-breatheB*0.25),vec2(0.15,0.175),farAlt,farOpacity*0.42*formC,1.0,5.1),tint*1.03);}",

            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.16+midDrift),0.355+lift*0.28+breatheA),vec2(0.21,0.285),cell,midOpacity*0.78,0.0,5.6),tint);",
            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.46+midDrift*1.03),0.405-lift*0.20-breatheB),vec2(0.20,0.270),altCell,midOpacity*0.59*formB,1.0,7.3),tint*0.99);",
            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.75+midDrift*0.97),0.340+breatheB*0.38),vec2(0.19,0.250),cell,midOpacity*0.46*formC,0.0,8.8),tint*1.01);",
            "  if(detail>0.72){over(color,alpha,spriteWrapped(p,vec2(fract(0.94+midDrift*1.08),0.430-breatheA*0.25),vec2(0.17,0.230),altCell,midOpacity*0.36*formA,1.0,9.4),tint*0.98);}",

            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.25+nearDrift),0.515+lift*0.40+breatheB),vec2(0.23,0.340),cell,nearOpacity*0.72,1.0,10.4),tint*0.94);",
            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.57+nearDrift*1.02),0.465-lift*0.16+breatheA),vec2(0.22,0.315),altCell,nearOpacity*0.54*formA,0.0,12.1),tint*0.96);",
            "  if(detail>0.78&&nearTruth>0.30){over(color,alpha,spriteWrapped(p,vec2(fract(0.84+nearDrift*0.94),0.555+breatheB*0.35),vec2(0.20,0.285),cell,nearOpacity*0.39*formC,1.0,13.9),tint*0.95);}",

            "  if(detail>0.88&&cover<0.46&&farTruth>0.12){",
            "    float wispOpacity=(0.035+0.070*farTruth)*(1.0-smoothstep(0.34,0.52,cover));",
            "    over(color,alpha,spriteWrapped(p,vec2(fract(0.41+farDrift*1.26),0.310+breatheA*0.45),vec2(0.14,0.135),4.0,wispOpacity*formB,1.0,15.2),vec3(1.06)*tint);",
            "  }",

            "  float overcast=smoothstep(0.69,0.93,mass);",
            "  float ceilingStrength=max(overcast,ceilingTruth*0.94);",
            "  float xWave=0.5+0.5*sin(p.x*7.2+uTime*0.023*(1.0+gust*0.30));",
            "  float xWave2=0.5+0.5*sin(p.x*15.6-uTime*0.016*(1.0+gust*0.42)+1.8);",
            "  float ceilingTop=0.47-ceilingTruth*0.050;",
            "  float ceilingBottom=0.84-ceilingTruth*0.070;",
            "  float ceiling=1.0-smoothstep(ceilingTop,ceilingBottom,p.y);",
            "  float sheet=(0.090+0.050*xWave+0.030*xWave2)*ceilingStrength*ceiling;",
            "  vec3 sheetTint=mix(vec3(0.58,0.62,0.66),vec3(0.40,0.44,0.50),clamp(uStorm*0.75+uRain*0.15+ceilingTruth*0.16,0.0,1.0))*shade;",
            "  color=mix(color,sheetTint,clamp(sheet,0.0,0.21));",
            "  alpha=1.0-(1.0-alpha)*(1.0-clamp(sheet,0.0,0.21));",
            "  float undersideBand=smoothstep(0.40,0.65,p.y)*(1.0-smoothstep(0.77,0.95,p.y));",
            "  float underside=undersideBand*ceilingStrength*(0.010+weatherShade*0.026+ceilingTruth*0.018);",
            "  color=mix(color,vec3(0.24,0.28,0.34),underside);",
            "  alpha=1.0-(1.0-alpha)*(1.0-underside*0.40);",

            "  vec2 sunPos=uSunPos;",
            "  sunPos.x=(sunPos.x-0.5)*aspect+0.5;",
            "  vec2 moonPos=uMoonPos;",
            "  moonPos.x=(moonPos.x-0.5)*aspect+0.5;",
            "  float sunHalo=(1.0-smoothstep(0.10,0.58,distance(p,sunPos)))*uSunVis*(1.0-weatherShade);",
            "  float moonHalo=(1.0-smoothstep(0.08,0.42,distance(p,moonPos)))*uMoonVis*(1.0-uSceneLight);",
            "  float edgeLight=clamp(alpha*(sunHalo*0.09+moonHalo*0.045),0.0,0.09);",
            "  color+=warmTint*sunHalo*edgeLight+moonTint*moonHalo*edgeLight;",

            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.84));",
            "}"
    );

    private final FloatBuffer quad;
    private int program;
    private int texture;
    private int aPosition;
    private int uAtlas;
    private int uResolution;
    private int uTime;
    private int uCloud;
    private int uDensity;
    private int uFarLayer;
    private int uMidLayer;
    private int uNearLayer;
    private int uStormCeiling;
    private int uRain;
    private int uStorm;
    private int uBrightness;
    private int uWind;
    private int uWindDir;
    private int uParallax;
    private int uDetail;
    private int uSceneLight;
    private int uSunPos;
    private int uSunVis;
    private int uSunAltitude;
    private int uMoonPos;
    private int uMoonVis;

    private int width = 1;
    private int height = 1;
    private long startNanos;
    private volatile float detailScale = 1f;
    @Nullable private volatile GlSceneSnapshot snapshot;

    public HeroGlTextureCloudRenderer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder());
        quad = buffer.asFloatBuffer();
        quad.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot value) {
        snapshot = value;
    }

    public void setDetailScale(float value) {
        detailScale = clamp(value, 0.5f, 1f);
    }

    public void onSurfaceCreated() {
        program = createProgram(VS, FS);
        texture = loadTexture();
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uAtlas = u("uAtlas");
        uResolution = u("uResolution");
        uTime = u("uTime");
        uCloud = u("uCloud");
        uDensity = u("uDensity");
        uFarLayer = u("uFarLayer");
        uMidLayer = u("uMidLayer");
        uNearLayer = u("uNearLayer");
        uStormCeiling = u("uStormCeiling");
        uRain = u("uRain");
        uStorm = u("uStorm");
        uBrightness = u("uBrightness");
        uWind = u("uWind");
        uWindDir = u("uWindDir");
        uParallax = u("uParallax");
        uDetail = u("uDetail");
        uSceneLight = u("uSceneLight");
        uSunPos = u("uSunPos");
        uSunVis = u("uSunVis");
        uSunAltitude = u("uSunAltitude");
        uMoonPos = u("uMoonPos");
        uMoonVis = u("uMoonVis");
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
        if (program == 0 || texture == 0 || scene == null || scene.cloudCover < 0.015f) {
            return;
        }

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);

        GLES20.glUniform1i(uAtlas, 0);
        GLES20.glUniform2f(uResolution, width, height);
        GLES20.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f);
        GLES20.glUniform1f(uCloud, scene.cloudCover);
        GLES20.glUniform1f(uDensity, scene.cloudDensity);
        GLES20.glUniform1f(uFarLayer, scene.cloudFarLayer);
        GLES20.glUniform1f(uMidLayer, scene.cloudMidLayer);
        GLES20.glUniform1f(uNearLayer, scene.cloudNearLayer);
        GLES20.glUniform1f(uStormCeiling, scene.cloudStormCeiling);
        GLES20.glUniform1f(uRain, scene.rainIntensity);
        GLES20.glUniform1f(uStorm, scene.stormIntensity);
        GLES20.glUniform1f(uBrightness, scene.cloudBrightness);
        GLES20.glUniform1f(uWind, scene.windStrength);
        GLES20.glUniform1f(uWindDir, scene.windDirectionRadians);
        GLES20.glUniform1f(uParallax, scene.parallax);
        GLES20.glUniform1f(uDetail, detailScale);
        GLES20.glUniform1f(uSceneLight, scene.sceneLight);
        GLES20.glUniform2f(uSunPos, scene.sunX, scene.sunY);
        GLES20.glUniform1f(uSunVis, scene.sunVisibility);
        GLES20.glUniform1f(uSunAltitude, scene.sunAltitude);
        GLES20.glUniform2f(uMoonPos, scene.moonX, scene.moonY);
        GLES20.glUniform1f(uMoonVis, scene.moonVisibility);

        quad.position(0);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
        if (texture != 0) {
            int[] ids = {texture};
            GLES20.glDeleteTextures(1, ids, 0);
            texture = 0;
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }

    private int loadTexture() {
        Bitmap bitmap = BitmapFactory.decodeResource(
                LiveWeatherApplication.appContext().getResources(),
                R.drawable.cloud_texture_atlas
        );
        if (bitmap == null) {
            throw new IllegalStateException("Unable to decode cloud texture atlas");
        }

        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return ids[0];
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
            throw new IllegalStateException("Texture cloud program link failed: " + log);
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
            throw new IllegalStateException("Texture cloud shader compile failed: " + log);
        }
        return shader;
    }
}
