package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Cross-device storm/lightning pass using deterministic texture samples. */
public final class HeroGlPortableStormRenderer {

    private static final float[] QUAD = {-1f,-1f, 1f,-1f, -1f,1f, 1f,1f};

    private static final String VERTEX_SHADER = String.join("\n",
            "attribute vec2 aPosition;",
            "varying vec2 vUv;",
            "void main(){ vUv=aPosition*0.5+0.5; gl_Position=vec4(aPosition,0.0,1.0); }"
    );

    private static final String FRAGMENT_SHADER = String.join("\n",
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
            "",
            "float texRnd(float a,float b){ return texture2D(uNoise,vec2(fract(a),fract(b))).r; }",
            "float boltLine(vec2 p,float anchor,float seed,float y0,float y1,float width,float drift){",
            "  float inside=step(y0,p.y)*step(p.y,y1);",
            "  float t=clamp((p.y-y0)/max(0.001,y1-y0),0.0,1.0);",
            "  float n=texRnd(t*0.47+seed*0.071,t*0.19+seed*0.137)-0.5;",
            "  float micro=sin(t*46.0+seed*5.7)*0.008+sin(t*91.0+seed*2.3)*0.004;",
            "  float x=anchor+n*0.115+micro+drift*t;",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  float d=abs((p.x-x)*aspect);",
            "  float core=exp(-d/max(0.00055,width));",
            "  float glow=exp(-d/max(0.0025,width*5.6))*0.30;",
            "  return (core+glow)*inside;",
            "}",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float storm=clamp(uStorm,0.0,1.0);",
            "  if(storm<0.02){ gl_FragColor=vec4(0.0); return; }",
            "  vec2 wind=vec2(sin(uWindDir),-cos(uWindDir));",
            "  vec2 cuv=p*vec2(0.72,0.92)+wind*uTime*(0.003+uWind*0.006);",
            "  float cloudN=texture2D(uNoise,cuv).r*0.70+texture2D(uNoise,cuv*1.9+vec2(0.17,0.31)).r*0.30;",
            "  float upper=1.0-smoothstep(0.56,0.88,p.y);",
            "  float cloudMass=clamp(uCloudCeiling*0.76+uCloudNear*0.16+uCloudDensity*0.16,0.0,1.0);",
            "  float darkMask=smoothstep(0.43,0.68,cloudN)*upper*cloudMass;",
            "  float darkAlpha=(0.020+storm*0.070)*darkMask;",
            "",
            "  float window=max(4.6,7.8-storm*2.5);",
            "  float cycle=floor(uTime/window); float phase=mod(uTime,window);",
            "  float seed=texRnd(cycle*0.037+0.13,0.31);",
            "  float start=0.48+seed*1.42; float local=phase-start;",
            "  float p1=0.0; if(local>=0.0&&local<0.070) p1=1.0-local/0.070;",
            "  float p2=0.0; if(local>=0.115&&local<0.190) p2=(1.0-(local-0.115)/0.075)*0.56;",
            "  float p3=0.0; if(local>=0.245&&local<0.315) p3=(1.0-(local-0.245)/0.070)*0.22;",
            "  float pulse=(p1+p2+p3)*storm*uElectricalEnabled;",
            "  float anchor=0.20+texRnd(cycle*0.041+0.37,0.63)*0.60;",
            "  float y0=0.10+texRnd(cycle*0.029+0.19,0.77)*0.14;",
            "  float y1=0.52+texRnd(cycle*0.053+0.51,0.21)*0.19;",
            "  float mainBolt=boltLine(p,anchor,seed*7.0+1.3,y0,y1,0.0011+storm*0.0008,(texRnd(seed,0.44)-0.5)*0.10);",
            "  float branchGate=step(0.38,texRnd(cycle*0.067+0.11,0.52));",
            "  float branch=boltLine(p,anchor+0.015,seed*11.0+4.7,y0+0.16,y1-0.02,0.0008,0.12)*branchGate;",
            "  float bolt=(mainBolt+branch*0.62)*pulse;",
            "",
            "  float localGlow=exp(-abs(p.x-anchor)*5.8)*upper*pulse*(0.13+cloudMass*0.20);",
            "  float exposure=pulse*(0.035+storm*0.045);",
            "  vec3 color=vec3(0.0); float alpha=0.0;",
            "  color=mix(color,vec3(0.018,0.026,0.040),darkAlpha*5.0); alpha=1.0-(1.0-alpha)*(1.0-darkAlpha);",
            "  color+=vec3(0.58,0.68,0.88)*localGlow; alpha=1.0-(1.0-alpha)*(1.0-localGlow*0.48);",
            "  color+=vec3(0.90,0.94,1.0)*bolt; alpha=1.0-(1.0-alpha)*(1.0-clamp(bolt*0.88,0.0,0.92));",
            "  color+=vec3(0.72,0.80,0.96)*exposure; alpha=1.0-(1.0-alpha)*(1.0-exposure);",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.92));",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program;
    private int noiseTexture;
    private int width=1, height=1;
    private int aPosition,uNoise,uResolution,uTime,uStorm,uCloudDensity,uCloudCeiling,uCloudNear,uRain,uWind,uWindDir,uElectricalEnabled;
    private long startNanos;
    private boolean electricalEnabled=true;
    @Nullable private volatile GlSceneSnapshot snapshot;

    public HeroGlPortableStormRenderer(){ByteBuffer b=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.nativeOrder());quadBuffer=b.asFloatBuffer();quadBuffer.put(QUAD).position(0);}
    public void setSnapshot(@Nullable GlSceneSnapshot snapshot){this.snapshot=snapshot;}
    public void setElectricalEnabled(boolean enabled){electricalEnabled=enabled;}
    public void onSurfaceCreated(){
        program=createProgram(VERTEX_SHADER,FRAGMENT_SHADER); noiseTexture=GlDeterministicTextureFactory.createCloudNoiseTexture();
        aPosition=GLES20.glGetAttribLocation(program,"aPosition"); uNoise=uniform("uNoise"); uResolution=uniform("uResolution"); uTime=uniform("uTime");
        uStorm=uniform("uStorm");uCloudDensity=uniform("uCloudDensity");uCloudCeiling=uniform("uCloudCeiling");uCloudNear=uniform("uCloudNear");uRain=uniform("uRain");uWind=uniform("uWind");uWindDir=uniform("uWindDir");uElectricalEnabled=uniform("uElectricalEnabled");
        startNanos=System.nanoTime(); GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDisable(GLES20.GL_CULL_FACE);
    }
    public void onSurfaceChanged(int width,int height){this.width=Math.max(1,width);this.height=Math.max(1,height);GLES20.glViewport(0,0,this.width,this.height);}
    public void drawFrame(){
        GlSceneSnapshot s=snapshot; if(program==0||noiseTexture==0||s==null||s.stormIntensity<0.02f)return;
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,noiseTexture);GLES20.glUniform1i(uNoise,0);
        GLES20.glUniform2f(uResolution,width,height);GLES20.glUniform1f(uTime,(System.nanoTime()-startNanos)/1_000_000_000f);GLES20.glUniform1f(uStorm,s.stormIntensity);
        GLES20.glUniform1f(uCloudDensity,s.cloudDensity);GLES20.glUniform1f(uCloudCeiling,s.cloudStormCeiling);GLES20.glUniform1f(uCloudNear,s.cloudNearLayer);GLES20.glUniform1f(uRain,s.rainIntensity);
        GLES20.glUniform1f(uWind,s.windStrength);GLES20.glUniform1f(uWindDir,s.windDirectionRadians);GLES20.glUniform1f(uElectricalEnabled,electricalEnabled?1f:0f);
        quadBuffer.position(0);GLES20.glEnableVertexAttribArray(aPosition);GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,quadBuffer);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);GLES20.glDisable(GLES20.GL_BLEND);
    }
    public void release(){if(noiseTexture!=0){int[] ids={noiseTexture};GLES20.glDeleteTextures(1,ids,0);noiseTexture=0;}if(program!=0){GLES20.glDeleteProgram(program);program=0;}}
    private int uniform(@NonNull String n){return GLES20.glGetUniformLocation(program,n);}
    private static int createProgram(String vs,String fs){int v=compileShader(GLES20.GL_VERTEX_SHADER,vs),f=compileShader(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] st=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,st,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(st[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("OpenGL portable storm program link failed: "+log);}return p;}
    private static int compileShader(int t,String src){int s=GLES20.glCreateShader(t);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] st=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,st,0);if(st[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("OpenGL portable storm shader compile failed: "+log);}return s;}
}
