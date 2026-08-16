package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Deterministic atmospheric world pass shared by app Hero and Live Wallpaper.
 * Phase 24 adds richer terrain depth, natural foreground breakup and rain-driven
 * wet-ground reflections without inventing any location-specific landmark.
 */
public final class HeroGlAnalyticWorldRenderer {
    private static final float[] QUAD={-1f,-1f,1f,-1f,-1f,1f,1f,1f};
    private static final String VS=String.join("\n",
            "attribute vec2 aPosition;",
            "varying vec2 vUv;",
            "void main(){vUv=aPosition*0.5+0.5;gl_Position=vec4(aPosition,0.0,1.0);}");
    private static final String FS=String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH","precision highp float;","#else","precision mediump float;","#endif",
            "varying vec2 vUv;",
            "uniform vec2 uResolution;",
            "uniform float uSunAltitude;",
            "uniform float uMoonVis;",
            "uniform float uMoonIllum;",
            "uniform float uCloud;",
            "uniform float uRain;",
            "uniform float uDrizzle;",
            "uniform float uStorm;",
            "uniform float uFog;",
            "uniform float uHaze;",
            "uniform float uSceneLight;",
            "uniform float uParallax;",
            "uniform float uTime;",
            "const float TAU=6.28318530718;",
            "void main(){",
            " vec2 p=vec2(vUv.x,1.0-vUv.y);float aspect=uResolution.x/max(1.0,uResolution.y);",
            " float x=(p.x-0.5)*(aspect/0.45)+0.5+(uParallax-0.5)*0.055;",
            " float farLine=0.700+0.024*sin(TAU*(x*0.76)+0.3)+0.015*sin(TAU*(x*1.58)+1.1)+0.008*sin(TAU*(x*3.00)+0.4)+0.004*sin(TAU*(x*5.7)+2.0);",
            " float midLine=0.773+0.029*sin(TAU*(x*0.94)+1.8)+0.016*sin(TAU*(x*2.08)+0.5)+0.008*sin(TAU*(x*4.05)+2.0)+0.004*sin(TAU*(x*7.2)+0.8);",
            " float nearLine=0.842+0.023*sin(TAU*(x*1.20)+0.7)+0.014*sin(TAU*(x*2.80)+2.2)+0.006*sin(TAU*(x*5.35)+1.2);",
            " float treeCell=floor(x*28.0);float treeLocal=fract(x*28.0);float treeRnd=0.5+0.5*sin(treeCell*7.13+1.7);",
            " float treeTop=0.842+0.030*treeRnd;float treeHalf=0.105+0.045*treeRnd;float treeShape=1.0-smoothstep(0.070,treeHalf,abs(treeLocal-0.5));",
            " float treeMask=step(treeTop-(treeShape*0.050),p.y)*(1.0-smoothstep(0.920,0.945,p.y));",
            " float forestLine=0.878+0.009*sin(TAU*(x*6.1)+0.4)+0.006*sin(TAU*(x*10.8)+1.7);",
            " float farM=smoothstep(farLine-0.010,farLine+0.012,p.y);float midM=smoothstep(midLine-0.009,midLine+0.011,p.y);",
            " float nearM=smoothstep(nearLine-0.008,nearLine+0.010,p.y);float forest=smoothstep(forestLine-0.006,forestLine+0.008,p.y);",
            " float precip=max(uRain,uDrizzle*0.65);float night=1.0-smoothstep(-7.0,1.5,uSunAltitude);",
            " float lunar=night*uMoonVis*uMoonIllum*(1.0-uCloud*0.5);float light=clamp(0.18+uSceneLight*0.74+lunar*0.18,0.16,0.95);float haze=max(uFog*0.55,uHaze*0.25);",
            " vec3 farC=mix(vec3(0.055,0.085,0.120),vec3(0.25,0.32,0.36),light);vec3 midC=mix(vec3(0.032,0.058,0.086),vec3(0.15,0.22,0.25),light);",
            " vec3 nearC=mix(vec3(0.018,0.038,0.058),vec3(0.085,0.14,0.17),light);vec3 forestC=mix(vec3(0.010,0.024,0.030),vec3(0.043,0.082,0.074),light);",
            " vec3 color=vec3(0.0);float alpha=0.0;float a=farM*(0.46-haze*0.18);color=mix(color,farC,a);alpha=max(alpha,a);",
            " a=midM*(0.65-haze*0.16);color=mix(color,midC,a);alpha=max(alpha,a);a=nearM*0.83;color=mix(color,nearC,a);alpha=max(alpha,a);",
            " a=max(forest*0.86,treeMask*0.82);color=mix(color,forestC,a);alpha=max(alpha,a);",
            " float ground=smoothstep(0.918,0.953,p.y);vec3 groundC=mix(vec3(0.008,0.018,0.024),vec3(0.025,0.046,0.052),light);color=mix(color,groundC,ground);alpha=max(alpha,ground);",
            " float settlement=clamp(0.12+night*0.30+precip*0.22+uStorm*0.20,0.0,0.58);float cityCell=floor(fract(x)*18.0);float cityLocal=fract(x*18.0);",
            " float cityTop=0.744+0.055*(0.5+0.5*sin(cityCell*2.07+0.9));float building=step(cityTop,p.y)*step(cityLocal,0.66)*(1.0-smoothstep(0.908,0.938,p.y))*settlement;",
            " vec3 cityC=mix(vec3(0.026,0.039,0.052),vec3(0.045,0.055,0.064),light);color=mix(color,cityC,building*0.72);alpha=max(alpha,building*0.72);",
            " float windowBand=step(0.80,fract(cityCell*0.618))*step(0.20,cityLocal)*step(cityLocal,0.48)*building*night*(1.0-uFog*0.65);",
            " color+=vec3(0.78,0.58,0.30)*windowBand*0.18;alpha=max(alpha,windowBand*0.12);",
            " float wet=smoothstep(0.18,0.76,precip)*ground;float reflectionBand=smoothstep(0.925,0.995,p.y);",
            " float ripple=0.5+0.5*sin(x*34.0+uTime*(0.75+uRain*1.35));float ripple2=0.5+0.5*sin(x*61.0-uTime*0.48+1.4);",
            " float wetSheen=wet*reflectionBand*(0.020+0.018*ripple+0.010*ripple2);",
            " vec3 reflected=mix(vec3(0.16,0.23,0.30),vec3(0.31,0.38,0.43),uSceneLight)*(0.65+uStorm*0.20);",
            " color=mix(color,reflected,clamp(wetSheen*3.4,0.0,0.18));alpha=max(alpha,ground);",
            " float lightReflection=windowBand*wet*reflectionBand*(0.025+0.035*ripple);color+=vec3(0.82,0.56,0.27)*lightReflection;",
            " gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.96));}");
    private final FloatBuffer quad;
    private int program,aPos,uRes,uSun,uMoonVis,uMoonIll,uCloud,uRain,uDrizzle,uStorm,uFog,uHaze,uLight,uParallax,uTime;
    private int width=1,height=1;private long startNanos;@Nullable private volatile GlSceneSnapshot snapshot;
    public HeroGlAnalyticWorldRenderer(){ByteBuffer b=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.nativeOrder());quad=b.asFloatBuffer();quad.put(QUAD).position(0);}public void setSnapshot(@Nullable GlSceneSnapshot s){snapshot=s;}
    public void onSurfaceCreated(){program=createProgram(VS,FS);aPos=GLES20.glGetAttribLocation(program,"aPosition");uRes=u("uResolution");uSun=u("uSunAltitude");uMoonVis=u("uMoonVis");uMoonIll=u("uMoonIllum");uCloud=u("uCloud");uRain=u("uRain");uDrizzle=u("uDrizzle");uStorm=u("uStorm");uFog=u("uFog");uHaze=u("uHaze");uLight=u("uSceneLight");uParallax=u("uParallax");uTime=u("uTime");startNanos=System.nanoTime();GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);}
    public void onSurfaceChanged(int w,int h){width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,width,height);}
    public void drawFrame(){GlSceneSnapshot s=snapshot;if(program==0||s==null)return;GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glUseProgram(program);GLES20.glUniform2f(uRes,width,height);GLES20.glUniform1f(uSun,s.sunAltitude);GLES20.glUniform1f(uMoonVis,s.moonVisibility);GLES20.glUniform1f(uMoonIll,s.moonIllumination);GLES20.glUniform1f(uCloud,s.cloudCover);GLES20.glUniform1f(uRain,s.rainIntensity);GLES20.glUniform1f(uDrizzle,s.drizzleIntensity);GLES20.glUniform1f(uStorm,s.stormIntensity);GLES20.glUniform1f(uFog,s.fogIntensity);GLES20.glUniform1f(uHaze,s.airHazeIntensity);GLES20.glUniform1f(uLight,s.sceneLight);GLES20.glUniform1f(uParallax,s.parallax);GLES20.glUniform1f(uTime,(System.nanoTime()-startNanos)/1_000_000_000f);quad.position(0);GLES20.glEnableVertexAttribArray(aPos);GLES20.glVertexAttribPointer(aPos,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(aPos);GLES20.glDisable(GLES20.GL_BLEND);}
    public void release(){if(program!=0){GLES20.glDeleteProgram(program);program=0;}}
    private int u(@NonNull String n){return GLES20.glGetUniformLocation(program,n);}
    private static int createProgram(String vs,String fs){int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] st=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,st,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(st[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("OpenGL analytic world program link failed: "+log);}return p;}
    private static int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] st=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,st,0);if(st[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("OpenGL analytic world shader compile failed: "+log);}return s;}
}
