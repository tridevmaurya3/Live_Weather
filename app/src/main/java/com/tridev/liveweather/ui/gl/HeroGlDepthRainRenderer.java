package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Shared depth-aware rain, wet-glass and lower-world impact pass for Hero and Wallpaper. */
public final class HeroGlDepthRainRenderer {

    private static final float[] QUAD={-1f,-1f,1f,-1f,-1f,1f,1f,1f};
    private static final String VS=String.join("\n","attribute vec2 aPosition;","varying vec2 vUv;","void main(){vUv=aPosition*0.5+0.5;gl_Position=vec4(aPosition,0.0,1.0);}");
    private static final String FS=String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH","precision highp float;","#else","precision mediump float;","#endif",
            "varying vec2 vUv;uniform sampler2D uNoise;uniform vec2 uResolution;uniform float uTime;uniform float uRain;uniform float uDrizzle;uniform float uStorm;uniform float uWind;uniform float uWindDir;uniform float uVisibility;uniform float uSceneLight;uniform float uDetail;",
            "float rnd(vec2 id,float seed){vec2 uv=fract((id+vec2(seed,seed*1.731)+0.5)/64.0);return texture2D(uNoise,uv).r;}",
            "float rainBand(vec2 p,vec2 grid,float speed,float width,float length,float lean,float seed,float density,float depth){",
            " vec2 q=p;float broadSway=sin(uTime*(0.62+depth*0.29)+p.y*3.6+seed)*0.0055*uWind;q.x+=q.y*lean+broadSway;q*=grid;vec2 id=floor(q);",
            " float a=rnd(id,seed);float b=rnd(id,seed+9.37);float c=rnd(id,seed+21.11);float d=rnd(id,seed+31.47);",
            " float jitter=0.70+b*0.74;float y=fract(q.y+uTime*speed*jitter+c*6.0);",
            " float localSkew=(c-0.5)*(0.055+0.105*depth)+(d-0.5)*0.025*uWind;",
            " float x=fract(q.x)-0.5+(a-0.5)*(0.62-0.14*depth)+(y-0.5)*localSkew;",
            " float w=width*(0.64+0.74*a);float len=length*(0.56+0.78*c);",
            " float line=1.0-smoothstep(w,w*2.45,abs(x));float head=smoothstep(0.010,0.075,y);float tail=1.0-smoothstep(len,min(0.995,len+0.22),y);",
            " float breakup=0.68+0.32*smoothstep(0.18,0.95,d);return line*head*tail*breakup*step(1.0-density,b);",
            "}",
            "float glassDrop(vec2 p,float seed,float density,float speed){vec2 g=p*vec2(6.2,8.7);vec2 id=floor(g);float a=rnd(id,seed);float b=rnd(id,seed+13.4);float c=rnd(id,seed+27.2);float fall=fract(uTime*speed*(0.34+0.70*b)+c*5.0);vec2 f=fract(g)-0.5+vec2((a-0.5)*0.38,fall-0.50);f.y*=0.82;float d=length(f);float outer=1.0-smoothstep(0.18,0.275,d);float inner=1.0-smoothstep(0.105,0.175,d);float rim=max(0.0,outer-inner);float highlight=(1.0-smoothstep(0.020,0.080,length(f-vec2(-0.052,0.060))))*0.72*outer;float trailX=1.0-smoothstep(0.025,0.070,abs(f.x));float trailY=smoothstep(0.02,0.20,f.y)*(1.0-smoothstep(0.20,0.48,f.y));float trail=trailX*trailY*(0.20+0.38*b);return (rim+highlight+trail)*step(1.0-density,a);}",
            "float groundSplash(vec2 p,float seed,float density){float ground=smoothstep(0.900,0.985,p.y);vec2 q=vec2(p.x*39.0,(p.y-0.89)*24.0);vec2 id=floor(q);float a=rnd(id,seed);float b=rnd(id,seed+8.4);float t=fract(uTime*(1.55+uRain*1.65)+b*7.0);vec2 f=fract(q)-vec2(0.5,0.12+t*0.50);float arc=1.0-smoothstep(0.050,0.115,length(vec2(f.x,f.y*0.72)));float life=(1.0-smoothstep(0.10,0.90,t))*smoothstep(0.0,0.16,t);return arc*life*ground*step(1.0-density,a);}",
            "void main(){float rain=clamp(uRain,0.0,1.0);float drizzle=clamp(uDrizzle,0.0,1.0);float detail=clamp(uDetail,0.5,1.0);float effective=max(rain,drizzle*0.58);if(effective<0.004){gl_FragColor=vec4(0.0);return;}vec2 p=vec2(vUv.x,1.0-vUv.y);float aspect=uResolution.x/max(1.0,uResolution.y);vec2 sceneP=vec2((p.x-0.5)*aspect+0.5,p.y);",
            " float side=sin(uWindDir);float forward=cos(uWindDir);float gust=0.90+0.10*sin(uTime*(0.48+uWind*0.44)+uWindDir*1.7);float microGust=0.96+0.04*sin(uTime*(1.17+uWind*0.63)+1.9);float lean=side*(0.030+uWind*0.31)+forward*0.016*side;float windSpeed=(0.80+uWind*0.82)*gust*microGust;",
            " float drizzleGate=drizzle*(1.0-smoothstep(0.22,0.58,rain));float farGate=clamp(0.14+rain*0.48+drizzleGate*0.30,0.0,0.70);float midGate=clamp(0.10+rain*0.54+drizzleGate*0.17,0.0,0.72);float nearGate=clamp(rain*0.54-0.035,0.0,0.58);",
            " float drizzleFine=rainBand(sceneP+vec2(0.13,0.04),vec2(96.0,56.0),0.54*windSpeed,0.0060,0.28,lean*0.42,2.3,0.20+drizzleGate*0.29,0.16);",
            " float farRain=rainBand(sceneP+vec2(0.31,0.09),vec2(70.0,42.0),0.72*windSpeed,0.0080,0.36,lean*0.58,5.9,farGate,0.32);",
            " float midRain=rainBand(sceneP+vec2(0.47,0.16),vec2(42.0,27.0),1.00*windSpeed,0.0126,0.49,lean*0.82,11.7,midGate,0.61);",
            " float nearRain=0.0;if(detail>0.62){nearRain=rainBand(sceneP+vec2(0.67,0.25),vec2(22.0,14.0),1.34*windSpeed,0.0198,0.62,lean*1.08,18.1,nearGate,0.94);}",
            " float crossSpray=0.0;if(detail>0.80&&uWind>0.45&&rain>0.38){crossSpray=rainBand(sceneP+vec2(0.08,0.33),vec2(56.0,33.0),0.88*windSpeed,0.0072,0.31,lean*1.42+side*0.025,27.4,0.20+rain*0.22,0.48);}",
            " float lineAlpha=drizzleFine*drizzleGate*0.21+farRain*rain*(0.15+rain*0.10)+midRain*rain*(0.29+rain*0.15)+nearRain*rain*(0.46+rain*0.21)+crossSpray*rain*0.12;",
            " float perspective=mix(0.76,1.08,smoothstep(0.20,0.96,p.y));lineAlpha=clamp(lineAlpha*perspective,0.0,0.68);",
            " float heavy=smoothstep(0.50,0.90,rain);vec2 mistUv=vec2(sceneP.x*0.45+uTime*side*0.006,sceneP.y*0.38-uTime*(0.010+rain*0.014));float mistNoise=texture2D(uNoise,mistUv).r;if(detail>0.70){mistNoise=mistNoise*0.64+texture2D(uNoise,mistUv*1.91+vec2(0.17,0.23)).r*0.36;}float lowVisibility=1.0-clamp(uVisibility,0.0,1.0);float rainVeil=(0.016+mistNoise*0.061)*heavy*(0.82+lowVisibility*0.35);",
            " float wetGate=smoothstep(0.38,0.84,effective);float wet=0.0;if(detail>0.56){wet=glassDrop(p,4.7,0.050+wetGate*0.13,0.16+rain*0.18);}if(detail>0.82){wet+=glassDrop(p+vec2(0.16,0.08),12.6,0.032+wetGate*0.095,0.12+rain*0.16);}wet*=wetGate;float lowerFilm=smoothstep(0.79,1.0,p.y)*(0.008+heavy*0.042);float filmRipple=(0.5+0.5*sin(p.x*34.0+uTime*(1.1+rain*1.8)))*(0.003+heavy*0.010)*smoothstep(0.86,1.0,p.y);",
            " float splash=0.0;if(detail>0.68&&rain>0.48){splash=groundSplash(p,23.7,0.20+rain*0.34)*smoothstep(0.48,0.88,rain);}float stormLift=clamp(uStorm,0.0,1.0)*0.10;vec3 rainColor=mix(vec3(0.52,0.63,0.73),vec3(0.79,0.88,0.96),0.32+uSceneLight*0.28+stormLift);",
            " vec3 color=rainColor;float alpha=lineAlpha;float veil=clamp(rainVeil,0.0,0.105);color=mix(color,vec3(0.34,0.42,0.50),veil*2.2);alpha=1.0-(1.0-alpha)*(1.0-veil);",
            " float wetAlpha=clamp(wet*0.18,0.0,0.16);color=mix(color,vec3(0.87,0.95,1.0),wetAlpha*2.35);alpha=1.0-(1.0-alpha)*(1.0-wetAlpha);",
            " float film=clamp(lowerFilm+filmRipple,0.0,0.068);color=mix(color,vec3(0.18,0.27,0.34),film*1.70);alpha=1.0-(1.0-alpha)*(1.0-film);",
            " color+=vec3(0.69,0.80,0.88)*splash*0.26;alpha=1.0-(1.0-alpha)*(1.0-clamp(splash*0.13,0.0,0.10));alpha*=0.68+0.32*(0.34+uSceneLight*0.66);gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.80));}");

    private final FloatBuffer quad;private int program,noiseTexture,aPosition,uNoise,uResolution,uTime,uRain,uDrizzle,uStorm,uWind,uWindDir,uVisibility,uSceneLight,uDetail;private int width=1,height=1;private long startNanos;private volatile float detailScale=1f;@Nullable private volatile GlSceneSnapshot snapshot;
    public HeroGlDepthRainRenderer(){ByteBuffer b=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.nativeOrder());quad=b.asFloatBuffer();quad.put(QUAD).position(0);}public void setSnapshot(@Nullable GlSceneSnapshot value){snapshot=value;}public void setDetailScale(float value){detailScale=clamp(value,0.5f,1f);}
    public void onSurfaceCreated(){program=createProgram(VS,FS);noiseTexture=GlDeterministicTextureFactory.createCloudNoiseTexture();aPosition=GLES20.glGetAttribLocation(program,"aPosition");uNoise=u("uNoise");uResolution=u("uResolution");uTime=u("uTime");uRain=u("uRain");uDrizzle=u("uDrizzle");uStorm=u("uStorm");uWind=u("uWind");uWindDir=u("uWindDir");uVisibility=u("uVisibility");uSceneLight=u("uSceneLight");uDetail=u("uDetail");startNanos=System.nanoTime();GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);}
    public void onSurfaceChanged(int w,int h){width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,width,height);}
    public void drawFrame(){GlSceneSnapshot s=snapshot;if(program==0||noiseTexture==0||s==null||(s.rainIntensity<=0.003f&&s.drizzleIntensity<=0.003f))return;GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,noiseTexture);GLES20.glUniform1i(uNoise,0);GLES20.glUniform2f(uResolution,width,height);GLES20.glUniform1f(uTime,(System.nanoTime()-startNanos)/1_000_000_000f);GLES20.glUniform1f(uRain,s.rainIntensity);GLES20.glUniform1f(uDrizzle,s.drizzleIntensity);GLES20.glUniform1f(uStorm,s.stormIntensity);GLES20.glUniform1f(uWind,s.windStrength);GLES20.glUniform1f(uWindDir,s.windDirectionRadians);GLES20.glUniform1f(uVisibility,s.visibilityFactor);GLES20.glUniform1f(uSceneLight,s.sceneLight);GLES20.glUniform1f(uDetail,detailScale);quad.position(0);GLES20.glEnableVertexAttribArray(aPosition);GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(aPosition);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);GLES20.glDisable(GLES20.GL_BLEND);}
    public void release(){if(noiseTexture!=0){int[] ids={noiseTexture};GLES20.glDeleteTextures(1,ids,0);noiseTexture=0;}if(program!=0){GLES20.glDeleteProgram(program);program=0;}}
    private int u(@NonNull String name){return GLES20.glGetUniformLocation(program,name);}private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private static int createProgram(String vs,String fs){int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] st=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,st,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(st[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("Depth rain program link failed: "+log);}return p;}
    private static int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] st=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,st,0);if(st[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("Depth rain shader compile failed: "+log);}return s;}
}
