package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Depth-aware snowfall with wind turbulence, thermal tone and governor-controlled detail. */
public final class HeroGlSnowRenderer {

    private static final float[] QUAD={-1f,-1f,1f,-1f,-1f,1f,1f,1f};
    private static final String VS=String.join("\n","attribute vec2 aPosition;","varying vec2 vUv;","void main(){vUv=aPosition*0.5+0.5;gl_Position=vec4(aPosition,0.0,1.0);}");
    private static final String FS=String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH","precision highp float;","#else","precision mediump float;","#endif",
            "varying vec2 vUv;uniform sampler2D uNoise;uniform float uTime;uniform float uSnow;uniform float uWind;uniform float uWindDir;uniform float uSceneLight;uniform float uVisibility;uniform float uThermal;uniform float uDetail;",
            "float cellRnd(vec2 id,float seed){vec2 uv=fract((id+vec2(seed,seed*1.73)+0.5)/64.0);return texture2D(uNoise,uv).r;}",
            "float flake(vec2 p,float cols,float rows,float speed,float seed,float radius,float density,float drift,float wobble,float gust){vec2 q=p*vec2(cols,rows);q.x-=uTime*drift;q.y-=uTime*speed;vec2 id=floor(q);float r1=cellRnd(id,seed);float r2=cellRnd(id,seed+7.3);vec2 f=fract(q)-0.5;f.x+=(r1-0.5)*0.54;f.y+=(r2-0.5)*0.24;f.x+=sin((f.y+r1)*6.28318+uTime*(0.45+r2*0.55))*wobble;f.x+=sin(uTime*(0.72+r1*0.45)+r2*9.0)*gust;float d=length(f);float soft=1.0-smoothstep(radius,radius*1.85,d);return soft*step(1.0-density,r2);}",
            "void main(){vec2 p=vec2(vUv.x,1.0-vUv.y);float snow=clamp(uSnow,0.0,1.0);if(snow<0.004){gl_FragColor=vec4(0.0);return;}float detail=clamp(uDetail,0.5,1.0);float side=sin(uWindDir);float drift=side*(0.015+uWind*0.085);float gust=(0.002+uWind*0.010)*(0.55+0.45*sin(uTime*0.41+uWindDir));",
            " float far=flake(p+vec2(0.11,0.03),31.0,22.0,0.055+snow*0.025,2.9,0.055,0.24+snow*0.24,drift*0.52,0.035,gust*0.45);float mid=flake(p+vec2(0.29,0.13),21.0,16.0,0.083+snow*0.038,8.7,0.075,0.20+snow*0.34,drift*0.78,0.050,gust*0.72);float near=0.0;if(detail>0.62){near=flake(p+vec2(0.47,0.21),13.0,10.0,0.118+snow*0.052,15.4,0.105,0.15+snow*0.42,drift*1.10,0.070,gust);}",
            " float flakeAlpha=clamp(far*0.24+mid*0.48+near*0.76,0.0,0.88)*snow;float lowVis=1.0-uVisibility;float depthMist=smoothstep(0.56,1.0,p.y)*smoothstep(0.34,0.92,snow)*(0.012+snow*0.046)*(0.58+0.42*lowVis);float cold=max(0.0,-uThermal);vec3 flakeColor=mix(vec3(0.78,0.86,0.92),vec3(0.97,0.99,1.0),0.42+uSceneLight*0.42);flakeColor=mix(flakeColor,vec3(0.88,0.95,1.0),cold*0.16);vec3 color=flakeColor*flakeAlpha+vec3(0.70,0.79,0.86)*depthMist;float alpha=1.0-(1.0-flakeAlpha)*(1.0-depthMist);gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.90));}");

    private final FloatBuffer quad;
    private int program,noiseTexture,aPosition,uNoise,uTime,uSnow,uWind,uWindDir,uSceneLight,uVisibility,uThermal,uDetail;private long startNanos;private volatile float detailScale=1f;@Nullable private volatile GlSceneSnapshot snapshot;
    public HeroGlSnowRenderer(){ByteBuffer bytes=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.nativeOrder());quad=bytes.asFloatBuffer();quad.put(QUAD).position(0);}public void setSnapshot(@Nullable GlSceneSnapshot value){snapshot=value;}public void setDetailScale(float value){detailScale=clamp(value,0.5f,1f);}
    public void onSurfaceCreated(){program=createProgram(VS,FS);noiseTexture=GlDeterministicTextureFactory.createCloudNoiseTexture();aPosition=GLES20.glGetAttribLocation(program,"aPosition");uNoise=u("uNoise");uTime=u("uTime");uSnow=u("uSnow");uWind=u("uWind");uWindDir=u("uWindDir");uSceneLight=u("uSceneLight");uVisibility=u("uVisibility");uThermal=u("uThermal");uDetail=u("uDetail");startNanos=System.nanoTime();GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);}
    public void onSurfaceChanged(int width,int height){GLES20.glViewport(0,0,Math.max(1,width),Math.max(1,height));}
    public void drawFrame(){GlSceneSnapshot s=snapshot;if(program==0||noiseTexture==0||s==null||s.snowIntensity<=0.003f)return;GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,noiseTexture);GLES20.glUniform1i(uNoise,0);GLES20.glUniform1f(uTime,(System.nanoTime()-startNanos)/1_000_000_000f);GLES20.glUniform1f(uSnow,s.snowIntensity);GLES20.glUniform1f(uWind,s.windStrength);GLES20.glUniform1f(uWindDir,s.windDirectionRadians);GLES20.glUniform1f(uSceneLight,s.sceneLight);GLES20.glUniform1f(uVisibility,s.visibilityFactor);GLES20.glUniform1f(uThermal,s.thermalBias);GLES20.glUniform1f(uDetail,detailScale);quad.position(0);GLES20.glEnableVertexAttribArray(aPosition);GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(aPosition);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);GLES20.glDisable(GLES20.GL_BLEND);}
    public void release(){if(noiseTexture!=0){int[] ids={noiseTexture};GLES20.glDeleteTextures(1,ids,0);noiseTexture=0;}if(program!=0){GLES20.glDeleteProgram(program);program=0;}}
    private int u(@NonNull String name){return GLES20.glGetUniformLocation(program,name);}private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private static int createProgram(String vs,String fs){int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] st=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,st,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(st[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("OpenGL snow program link failed: "+log);}return p;}
    private static int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] st=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,st,0);if(st[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("OpenGL snow shader compile failed: "+log);}return s;}
}
