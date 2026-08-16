package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Storm/lightning pass with governor-controlled secondary fork detail. */
public final class HeroGlPortableStormRenderer {

    private static final float[] QUAD={-1f,-1f,1f,-1f,-1f,1f,1f,1f};

    private static final String VS=String.join("\n",
            "attribute vec2 aPosition;",
            "varying vec2 vUv;",
            "void main(){vUv=aPosition*0.5+0.5;gl_Position=vec4(aPosition,0.0,1.0);}");

    private static final String FS=String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH",
            "precision highp float;",
            "#else",
            "precision mediump float;",
            "#endif",
            "varying vec2 vUv;",
            "uniform sampler2D uNoise;",
            "uniform vec2 uResolution;",
            "uniform float uTime;",
            "uniform float uStorm;",
            "uniform float uCloudDensity;",
            "uniform float uCloudCeiling;",
            "uniform float uCloudNear;",
            "uniform float uRain;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uElectricalEnabled;",
            "uniform float uDetail;",
            "float texRnd(float a,float b){return texture2D(uNoise,vec2(fract(a),fract(b))).r;}",
            "float boltLine(vec2 p,float anchor,float seed,float y0,float y1,float width,float drift){",
            "  float inside=step(y0,p.y)*step(p.y,y1);float t=clamp((p.y-y0)/max(0.001,y1-y0),0.0,1.0);",
            "  float n=texRnd(t*0.47+seed*0.071,t*0.19+seed*0.137)-0.5;float micro=sin(t*46.0+seed*5.7)*0.008+sin(t*91.0+seed*2.3)*0.004;",
            "  float bend=sin(t*8.0+seed*1.9)*0.010*t;float x=anchor+n*0.115+micro+bend+drift*t;float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  float d=abs((p.x-x)*aspect);float core=exp(-d/max(0.00055,width));float glow=exp(-d/max(0.0028,width*5.8))*0.28;return (core+glow)*inside;",
            "}",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);float storm=clamp(uStorm,0.0,1.0);if(storm<0.02){gl_FragColor=vec4(0.0);return;}",
            "  float detail=clamp(uDetail,0.5,1.0);vec2 wind=vec2(sin(uWindDir),-cos(uWindDir));vec2 cuv=p*vec2(0.72,0.92)+wind*uTime*(0.003+uWind*0.006);",
            "  float cloudN=texture2D(uNoise,cuv).r;if(detail>0.66){cloudN=cloudN*0.67+texture2D(uNoise,cuv*1.9+vec2(0.17,0.31)).r*0.33;}",
            "  float upper=1.0-smoothstep(0.58,0.91,p.y);float cloudMass=clamp(uCloudCeiling*0.72+uCloudNear*0.18+uCloudDensity*0.20,0.0,1.0);",
            "  float rainLoad=clamp(uRain,0.0,1.0);float darkMask=smoothstep(0.39,0.67,cloudN)*upper*cloudMass;float baseVeil=upper*cloudMass*storm*(0.010+rainLoad*0.018);",
            "  float darkAlpha=(0.026+storm*0.096+rainLoad*0.028)*darkMask+baseVeil;",
            "  float window=max(5.2,9.0-storm*3.2);float cycle=floor(uTime/window);float phase=mod(uTime,window);float seed=texRnd(cycle*0.037+0.13,0.31);",
            "  float strikeGate=step(0.30-storm*0.18,seed);float start=0.55+texRnd(cycle*0.047+0.23,0.73)*1.55;float local=phase-start;",
            "  float p1=0.0;if(local>=0.0&&local<0.075)p1=1.0-local/0.075;float p2=0.0;if(local>=0.120&&local<0.205)p2=(1.0-(local-0.120)/0.085)*0.50;",
            "  float p3=0.0;if(local>=0.265&&local<0.340)p3=(1.0-(local-0.265)/0.075)*0.18;float pulse=(p1+p2+p3)*storm*uElectricalEnabled*strikeGate;",
            "  float anchor=0.18+texRnd(cycle*0.041+0.37,0.63)*0.64;float y0=0.07+texRnd(cycle*0.029+0.19,0.77)*0.13;float y1=0.63+texRnd(cycle*0.053+0.51,0.21)*0.18;",
            "  float drift=(texRnd(seed,0.44)-0.5)*(0.10+uWind*0.07);float mainBolt=boltLine(p,anchor,seed*7.0+1.3,y0,y1,0.0010+storm*0.0010,drift);",
            "  float fork1=0.0;float fork2=0.0;if(detail>0.72){",
            "    float fork1Gate=step(0.36,texRnd(cycle*0.067+0.11,0.52));fork1=boltLine(p,anchor+0.012,seed*11.0+4.7,y0+0.18,y1-0.03,0.00072,0.12+drift*0.42)*fork1Gate;",
            "    if(detail>0.90){float fork2Gate=step(0.68,texRnd(cycle*0.083+0.61,0.26));fork2=boltLine(p,anchor-0.010,seed*13.0+2.1,y0+0.28,y1-0.10,0.00064,-0.10+drift*0.35)*fork2Gate;}",
            "  }",
            "  float bolt=(mainBolt+fork1*0.56+fork2*0.40)*pulse;float horizontalGlow=exp(-abs(p.x-anchor)*(4.6+storm*1.4));",
            "  float verticalGlow=1.0-smoothstep(0.50,0.94,p.y);float cloudGlowMask=mix(0.35,0.88,smoothstep(0.30,0.75,cloudN));",
            "  float localGlow=horizontalGlow*verticalGlow*cloudGlowMask*pulse*(0.12+cloudMass*0.24);float broadFlash=upper*cloudGlowMask*pulse*(0.018+storm*0.052)*(0.48+cloudMass*0.52);",
            "  vec3 color=vec3(0.0);float alpha=0.0;float darkness=clamp(darkAlpha,0.0,0.18);color=mix(color,vec3(0.014,0.021,0.034),clamp(darkness*5.4,0.0,0.76));",
            "  alpha=1.0-(1.0-alpha)*(1.0-darkness);color+=vec3(0.52,0.64,0.88)*localGlow;alpha=1.0-(1.0-alpha)*(1.0-clamp(localGlow*0.50,0.0,0.32));",
            "  color+=vec3(0.69,0.79,0.98)*broadFlash;alpha=1.0-(1.0-alpha)*(1.0-clamp(broadFlash,0.0,0.12));color+=vec3(0.92,0.96,1.0)*bolt;",
            "  alpha=1.0-(1.0-alpha)*(1.0-clamp(bolt*0.90,0.0,0.94));gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.92));",
            "}");

    private final FloatBuffer quad;
    private int program,noiseTexture,width=1,height=1,aPosition,uNoise,uResolution,uTime,uStorm,uCloudDensity,uCloudCeiling,uCloudNear,uRain,uWind,uWindDir,uElectricalEnabled,uDetail;
    private long startNanos;
    private boolean electricalEnabled=true;
    private volatile float detailScale=1f;
    @Nullable private volatile GlSceneSnapshot snapshot;

    public HeroGlPortableStormRenderer(){ByteBuffer b=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.nativeOrder());quad=b.asFloatBuffer();quad.put(QUAD).position(0);}
    public void setSnapshot(@Nullable GlSceneSnapshot value){snapshot=value;}
    public void setElectricalEnabled(boolean enabled){electricalEnabled=enabled;}
    public void setDetailScale(float value){detailScale=clamp(value,0.5f,1f);}

    public void onSurfaceCreated(){
        program=createProgram(VS,FS);noiseTexture=GlDeterministicTextureFactory.createCloudNoiseTexture();aPosition=GLES20.glGetAttribLocation(program,"aPosition");
        uNoise=u("uNoise");uResolution=u("uResolution");uTime=u("uTime");uStorm=u("uStorm");uCloudDensity=u("uCloudDensity");uCloudCeiling=u("uCloudCeiling");
        uCloudNear=u("uCloudNear");uRain=u("uRain");uWind=u("uWind");uWindDir=u("uWindDir");uElectricalEnabled=u("uElectricalEnabled");uDetail=u("uDetail");
        startNanos=System.nanoTime();GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int w,int h){width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,width,height);}

    public void drawFrame(){
        GlSceneSnapshot s=snapshot;if(program==0||noiseTexture==0||s==null||s.stormIntensity<0.02f)return;
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,noiseTexture);GLES20.glUniform1i(uNoise,0);GLES20.glUniform2f(uResolution,width,height);
        GLES20.glUniform1f(uTime,(System.nanoTime()-startNanos)/1_000_000_000f);GLES20.glUniform1f(uStorm,s.stormIntensity);GLES20.glUniform1f(uCloudDensity,s.cloudDensity);
        GLES20.glUniform1f(uCloudCeiling,s.cloudStormCeiling);GLES20.glUniform1f(uCloudNear,s.cloudNearLayer);GLES20.glUniform1f(uRain,s.rainIntensity);GLES20.glUniform1f(uWind,s.windStrength);
        GLES20.glUniform1f(uWindDir,s.windDirectionRadians);GLES20.glUniform1f(uElectricalEnabled,electricalEnabled?1f:0f);GLES20.glUniform1f(uDetail,detailScale);
        quad.position(0);GLES20.glEnableVertexAttribArray(aPosition);GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(aPosition);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release(){if(noiseTexture!=0){int[] ids={noiseTexture};GLES20.glDeleteTextures(1,ids,0);noiseTexture=0;}if(program!=0){GLES20.glDeleteProgram(program);program=0;}}
    private int u(@NonNull String name){return GLES20.glGetUniformLocation(program,name);}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private static int createProgram(String vs,String fs){int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] st=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,st,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(st[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("OpenGL portable storm program link failed: "+log);}return p;}
    private static int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] st=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,st,0);if(st[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("OpenGL portable storm shader compile failed: "+log);}return s;}
}
