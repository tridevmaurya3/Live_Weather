package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Shared storm/lightning pass with truth-gated electrical activity and cinematic exposure.
 * Fog/low visibility diffuse the flash through the cloud volume while clear air preserves
 * a sharper bolt. Electrical display options never alter the underlying storm truth.
 */
public final class HeroGlPortableStormRenderer {

    private static final float[] QUAD={-1f,-1f,1f,-1f,-1f,1f,1f,1f};

    private static final String VS=String.join("\n","attribute vec2 aPosition;","varying vec2 vUv;","void main(){vUv=aPosition*0.5+0.5;gl_Position=vec4(aPosition,0.0,1.0);}");

    private static final String FS=String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH","precision highp float;","#else","precision mediump float;","#endif",
            "varying vec2 vUv;uniform sampler2D uNoise;uniform vec2 uResolution;uniform float uTime;uniform float uStorm;uniform float uCloudDensity;uniform float uCloudCeiling;uniform float uCloudNear;uniform float uRain;uniform float uFog;uniform float uVisibility;uniform float uWind;uniform float uWindDir;uniform float uSceneLight;uniform float uElectricalEnabled;uniform float uDetail;",
            "float texRnd(float a,float b){return texture2D(uNoise,vec2(fract(a),fract(b))).r;}",
            "float boltLine(vec2 p,float anchor,float seed,float y0,float y1,float width,float drift){float inside=step(y0,p.y)*step(p.y,y1);float t=clamp((p.y-y0)/max(0.001,y1-y0),0.0,1.0);float n=texRnd(t*0.47+seed*0.071,t*0.19+seed*0.137)-0.5;float micro=sin(t*43.0+seed*5.7)*0.0075+sin(t*89.0+seed*2.3)*0.0042+sin(t*151.0+seed)*0.0020;float bend=sin(t*7.2+seed*1.9)*0.011*t;float x=anchor+n*0.112+micro+bend+drift*t;float aspect=uResolution.x/max(1.0,uResolution.y);float d=abs((p.x-x)*aspect);float core=exp(-d/max(0.00055,width));float glow=exp(-d/max(0.0030,width*5.9))*0.27;return (core+glow)*inside;}",
            "void main(){",
            " vec2 p=vec2(vUv.x,1.0-vUv.y);float storm=clamp(uStorm,0.0,1.0);if(storm<0.02){gl_FragColor=vec4(0.0);return;}float detail=clamp(uDetail,0.5,1.0);",
            " vec2 wind=vec2(sin(uWindDir),-cos(uWindDir));vec2 cuv=p*vec2(0.72,0.92)+wind*uTime*(0.003+uWind*0.006);float cloudN=texture2D(uNoise,cuv).r;if(detail>0.66){cloudN=cloudN*0.67+texture2D(uNoise,cuv*1.9+vec2(0.17,0.31)).r*0.33;}",
            " float upper=1.0-smoothstep(0.58,0.91,p.y);float cloudMass=clamp(uCloudCeiling*0.72+uCloudNear*0.18+uCloudDensity*0.20,0.0,1.0);float rainLoad=clamp(uRain,0.0,1.0);float diffusion=clamp(uFog*0.62+(1.0-uVisibility)*0.44+rainLoad*0.14,0.0,1.0);float darkMask=smoothstep(0.37,0.68,cloudN)*upper*cloudMass;float baseVeil=upper*cloudMass*storm*(0.010+rainLoad*0.018);float darkAlpha=(0.026+storm*0.098+rainLoad*0.030)*darkMask+baseVeil;",
            " float cycle=mix(11.2,6.4,smoothstep(0.18,0.95,storm));float macro=floor(uTime/cycle);float phase=mod(uTime,cycle);float seed=texRnd(macro*0.037+0.13,0.31);float strikeGate=step(0.46-storm*0.28,seed);float start=0.52+texRnd(macro*0.047+0.23,0.73)*max(0.8,cycle-1.45);float local=phase-start;",
            " float p1=0.0;if(local>=0.0&&local<0.070)p1=1.0-local/0.070;float p2=0.0;if(local>=0.115&&local<0.205)p2=(1.0-(local-0.115)/0.090)*0.48;float p3=0.0;if(local>=0.255&&local<0.335)p3=(1.0-(local-0.255)/0.080)*0.16;float pulse=(p1+p2+p3)*storm*uElectricalEnabled*strikeGate;",
            " float anchor=0.17+texRnd(macro*0.041+0.37,0.63)*0.66;float y0=0.07+texRnd(macro*0.029+0.19,0.77)*0.14;float y1=0.63+texRnd(macro*0.053+0.51,0.21)*0.18;float drift=(texRnd(seed,0.44)-0.5)*(0.10+uWind*0.07);float mainBolt=boltLine(p,anchor,seed*7.0+1.3,y0,y1,0.0010+storm*0.0010,drift);",
            " float fork1=0.0;float fork2=0.0;float companion=0.0;if(detail>0.72){float fork1Gate=step(0.36,texRnd(macro*0.067+0.11,0.52));fork1=boltLine(p,anchor+0.012,seed*11.0+4.7,y0+0.18,y1-0.03,0.00072,0.12+drift*0.42)*fork1Gate;if(detail>0.90){float fork2Gate=step(0.68,texRnd(macro*0.083+0.61,0.26));fork2=boltLine(p,anchor-0.010,seed*13.0+2.1,y0+0.28,y1-0.10,0.00064,-0.10+drift*0.35)*fork2Gate;}float companionGate=step(0.80,texRnd(macro*0.101+0.44,0.18))*smoothstep(0.58,0.94,storm);companion=boltLine(p,anchor+(texRnd(seed,0.91)-0.5)*0.28,seed*17.0+3.0,y0+0.05,y1-0.12,0.00070,-drift*0.45)*companionGate;}",
            " float exposure=1.0+(1.0-uSceneLight)*0.24;float bolt=(mainBolt+fork1*0.56+fork2*0.40+companion*0.46)*pulse*exposure*(1.0-diffusion*0.28);float horizontalGlow=exp(-abs(p.x-anchor)*(4.5+storm*1.5));float verticalGlow=1.0-smoothstep(0.50,0.94,p.y);float cloudGlowMask=mix(0.30,0.92,smoothstep(0.28,0.76,cloudN));float chargeCore=exp(-abs(p.x-anchor)*(9.0+storm*4.0))*upper;",
            " float localGlow=horizontalGlow*verticalGlow*cloudGlowMask*pulse*(0.12+cloudMass*0.25)*exposure;float cloudCharge=chargeCore*cloudGlowMask*pulse*(0.11+cloudMass*0.20)*exposure;float broadFlash=upper*cloudGlowMask*pulse*(0.017+storm*0.052)*(0.48+cloudMass*0.52)*exposure*(1.0+diffusion*0.36);float diffuseGlow=exp(-abs(p.x-anchor)*(2.2+storm))*upper*pulse*diffusion*(0.018+cloudMass*0.050)*exposure;float horizonFlash=smoothstep(0.62,0.94,p.y)*pulse*(0.010+rainLoad*0.020)*horizontalGlow*exposure;",
            " vec3 color=vec3(0.0);float alpha=0.0;float darkness=clamp(darkAlpha,0.0,0.18);color=mix(color,vec3(0.014,0.021,0.034),clamp(darkness*5.4,0.0,0.76));alpha=1.0-(1.0-alpha)*(1.0-darkness);color+=vec3(0.50,0.63,0.89)*localGlow;alpha=1.0-(1.0-alpha)*(1.0-clamp(localGlow*0.50,0.0,0.32));color+=vec3(0.62,0.73,0.98)*cloudCharge;alpha=1.0-(1.0-alpha)*(1.0-clamp(cloudCharge*0.42,0.0,0.22));color+=vec3(0.65,0.75,0.96)*(broadFlash+diffuseGlow)+vec3(0.43,0.54,0.76)*horizonFlash;alpha=1.0-(1.0-alpha)*(1.0-clamp(broadFlash+diffuseGlow+horizonFlash,0.0,0.16));color+=vec3(0.92,0.96,1.0)*bolt;alpha=1.0-(1.0-alpha)*(1.0-clamp(bolt*0.90,0.0,0.94));",
            " gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.92));}");

    private final FloatBuffer quad;
    private int program,noiseTexture,width=1,height=1,aPosition,uNoise,uResolution,uTime,uStorm,uCloudDensity,uCloudCeiling,uCloudNear,uRain,uFog,uVisibility,uWind,uWindDir,uSceneLight,uElectricalEnabled,uDetail;
    private long startNanos;private boolean electricalEnabled=true;private volatile float detailScale=1f;@Nullable private volatile GlSceneSnapshot snapshot;
    public HeroGlPortableStormRenderer(){ByteBuffer b=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.nativeOrder());quad=b.asFloatBuffer();quad.put(QUAD).position(0);}public void setSnapshot(@Nullable GlSceneSnapshot value){snapshot=value;}public void setElectricalEnabled(boolean enabled){electricalEnabled=enabled;}public void setDetailScale(float value){detailScale=clamp(value,0.5f,1f);}
    public void onSurfaceCreated(){program=createProgram(VS,FS);noiseTexture=GlDeterministicTextureFactory.createCloudNoiseTexture();aPosition=GLES20.glGetAttribLocation(program,"aPosition");uNoise=u("uNoise");uResolution=u("uResolution");uTime=u("uTime");uStorm=u("uStorm");uCloudDensity=u("uCloudDensity");uCloudCeiling=u("uCloudCeiling");uCloudNear=u("uCloudNear");uRain=u("uRain");uFog=u("uFog");uVisibility=u("uVisibility");uWind=u("uWind");uWindDir=u("uWindDir");uSceneLight=u("uSceneLight");uElectricalEnabled=u("uElectricalEnabled");uDetail=u("uDetail");startNanos=System.nanoTime();GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);}
    public void onSurfaceChanged(int w,int h){width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,width,height);}
    public void drawFrame(){GlSceneSnapshot s=snapshot;if(program==0||noiseTexture==0||s==null||s.stormIntensity<0.02f)return;GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,noiseTexture);GLES20.glUniform1i(uNoise,0);GLES20.glUniform2f(uResolution,width,height);GLES20.glUniform1f(uTime,(System.nanoTime()-startNanos)/1_000_000_000f);GLES20.glUniform1f(uStorm,s.stormIntensity);GLES20.glUniform1f(uCloudDensity,s.cloudDensity);GLES20.glUniform1f(uCloudCeiling,s.cloudStormCeiling);GLES20.glUniform1f(uCloudNear,s.cloudNearLayer);GLES20.glUniform1f(uRain,s.rainIntensity);GLES20.glUniform1f(uFog,s.fogIntensity);GLES20.glUniform1f(uVisibility,s.visibilityFactor);GLES20.glUniform1f(uWind,s.windStrength);GLES20.glUniform1f(uWindDir,s.windDirectionRadians);GLES20.glUniform1f(uSceneLight,s.sceneLight);GLES20.glUniform1f(uElectricalEnabled,electricalEnabled?1f:0f);GLES20.glUniform1f(uDetail,detailScale);quad.position(0);GLES20.glEnableVertexAttribArray(aPosition);GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(aPosition);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);GLES20.glDisable(GLES20.GL_BLEND);}
    public void release(){if(noiseTexture!=0){int[] ids={noiseTexture};GLES20.glDeleteTextures(1,ids,0);noiseTexture=0;}if(program!=0){GLES20.glDeleteProgram(program);program=0;}}
    private int u(@NonNull String name){return GLES20.glGetUniformLocation(program,name);}private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private static int createProgram(String vs,String fs){int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] st=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,st,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(st[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("OpenGL portable storm program link failed: "+log);}return p;}
    private static int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] st=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,st,0);if(st[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("OpenGL portable storm shader compile failed: "+log);}return s;}
}
