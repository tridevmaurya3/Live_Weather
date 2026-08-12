package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Stable analytic cloud renderer.
 * Uses only bounded ellipse fields and small-angle motion; no random/hash texture.
 */
public final class HeroGlAnalyticCloudRenderer {

    private static final float REFERENCE_ASPECT = 0.45f;
    private static final float[] QUAD = {-1f,-1f, 1f,-1f, -1f,1f, 1f,1f};

    private static final String VERTEX_SHADER = String.join("\n",
            "attribute vec2 aPosition;",
            "varying vec2 vUv;",
            "void main(){vUv=aPosition*0.5+0.5;gl_Position=vec4(aPosition,0.0,1.0);}" );

    private static final String FRAGMENT_SHADER = String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH",
            "precision highp float;",
            "#else",
            "precision mediump float;",
            "#endif",
            "varying vec2 vUv;",
            "uniform vec2 uResolution;",
            "uniform float uTime;",
            "uniform float uCloud;",
            "uniform float uDensity;",
            "uniform float uFar;",
            "uniform float uMid;",
            "uniform float uNear;",
            "uniform float uCeiling;",
            "uniform float uBrightness;",
            "uniform float uStorm;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uParallax;",
            "",
            "float wrapDx(float x,float cx){float d=abs(x-cx);return min(d,1.0-d);}",
            "float ellipseField(vec2 p,vec2 c,vec2 r){",
            "  float dx=wrapDx(p.x,c.x)/max(0.001,r.x);",
            "  float dy=(p.y-c.y)/max(0.001,r.y);",
            "  return exp(-(dx*dx+dy*dy)*2.25);",
            "}",
            "float bank(vec2 p,float x,float y,float sx,float sy){",
            "  float v=0.0;",
            "  v+=ellipseField(p,vec2(x,y),vec2(sx,sy))*0.78;",
            "  v+=ellipseField(p,vec2(fract(x-0.14),y+0.018),vec2(sx*0.64,sy*0.86))*0.48;",
            "  v+=ellipseField(p,vec2(fract(x+0.15),y+0.012),vec2(sx*0.70,sy*0.82))*0.52;",
            "  v+=ellipseField(p,vec2(fract(x+0.02),y+0.050),vec2(sx*1.18,sy*0.58))*0.34;",
            "  return v;",
            "}",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  if(uCloud<=0.004){gl_FragColor=vec4(0.0);return;}",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  p.x=(p.x-0.5)*(aspect/0.45)+0.5;",
            "  vec2 wind=vec2(sin(uWindDir),-cos(uWindDir));",
            "  float speed=0.0040*(0.45+uWind*1.20);",
            "  float par=(uParallax-0.5)*0.035;",
            "  float drift=wind.x*uTime*speed+par;",
            "  float sway=sin(uTime*0.045+uWindDir)*0.010;",
            "",
            "  float farShape=bank(p,fract(0.12+drift*0.45),0.17+sway,0.30,0.090)",
            "      +bank(p,fract(0.67+drift*0.38),0.27-sway*0.5,0.27,0.085);",
            "  float midShape=bank(p,fract(0.28+drift*0.78),0.34+sway*0.7,0.25,0.115)",
            "      +bank(p,fract(0.82+drift*0.72),0.42-sway*0.4,0.23,0.105);",
            "  float nearShape=bank(p,fract(0.52+drift*1.05),0.49+sway,0.22,0.135);",
            "",
            "  float farM=smoothstep(0.26,0.82,farShape)*uFar;",
            "  float midM=smoothstep(0.24,0.86,midShape)*uMid;",
            "  float nearM=smoothstep(0.22,0.88,nearShape)*uNear;",
            "  float overcast=smoothstep(0.68,0.94,uCloud)*smoothstep(0.05,0.22,p.y)*(1.0-smoothstep(0.58,0.76,p.y));",
            "  float ceiling=overcast*uCeiling;",
            "",
            "  float stormShade=clamp(uStorm*0.76+uCeiling*0.56+(1.0-uBrightness)*0.30,0.0,1.0);",
            "  vec3 farColor=mix(vec3(0.78,0.83,0.87),vec3(0.18,0.22,0.28),stormShade);",
            "  vec3 midColor=mix(vec3(0.73,0.79,0.83),vec3(0.11,0.15,0.20),stormShade);",
            "  vec3 nearColor=mix(vec3(0.67,0.73,0.78),vec3(0.07,0.10,0.14),stormShade);",
            "  vec3 ceilingColor=mix(vec3(0.42,0.47,0.52),vec3(0.035,0.050,0.074),clamp(uStorm+uCeiling,0.0,1.0));",
            "",
            "  float d=clamp(uDensity,0.0,1.0);",
            "  float farA=clamp(farM*(0.10+d*0.14),0.0,0.25);",
            "  float midA=clamp(midM*(0.18+d*0.24),0.0,0.46);",
            "  float nearA=clamp(nearM*(0.25+d*0.30),0.0,0.60);",
            "  float ceilA=clamp(ceiling*(0.22+uCeiling*0.32),0.0,0.58);",
            "  vec3 color=vec3(0.0);float alpha=0.0;",
            "  color=mix(color,farColor,farA);alpha=1.0-(1.0-alpha)*(1.0-farA);",
            "  color=mix(color,midColor,midA);alpha=1.0-(1.0-alpha)*(1.0-midA);",
            "  color=mix(color,nearColor,nearA);alpha=1.0-(1.0-alpha)*(1.0-nearA);",
            "  color=mix(color,ceilingColor,ceilA);alpha=1.0-(1.0-alpha)*(1.0-ceilA);",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha*uCloud,0.0,0.78));",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program,aPosition,uResolution,uTime,uCloud,uDensity,uFar,uMid,uNear,uCeiling,uBrightness,uStorm,uWind,uWindDir,uParallax;
    private int width=1,height=1;
    @Nullable private volatile GlSceneSnapshot snapshot;

    public HeroGlAnalyticCloudRenderer(){
        ByteBuffer b=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.nativeOrder());
        quadBuffer=b.asFloatBuffer();quadBuffer.put(QUAD).position(0);
    }
    public void setSnapshot(@Nullable GlSceneSnapshot s){snapshot=s;}
    public void onSurfaceCreated(){
        program=createProgram(VERTEX_SHADER,FRAGMENT_SHADER);
        aPosition=GLES20.glGetAttribLocation(program,"aPosition");
        uResolution=u("uResolution");uTime=u("uTime");uCloud=u("uCloud");uDensity=u("uDensity");
        uFar=u("uFar");uMid=u("uMid");uNear=u("uNear");uCeiling=u("uCeiling");uBrightness=u("uBrightness");
        uStorm=u("uStorm");uWind=u("uWind");uWindDir=u("uWindDir");uParallax=u("uParallax");
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);
    }
    public void onSurfaceChanged(int w,int h){width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,width,height);}
    public void drawFrame(){
        GlSceneSnapshot s=snapshot;if(program==0||s==null||s.cloudCover<=0.004f)return;
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);GLES20.glUniform2f(uResolution,width,height);GLES20.glUniform1f(uTime,(System.nanoTime()/1_000_000_000f)%4096f);
        GLES20.glUniform1f(uCloud,s.cloudCover);GLES20.glUniform1f(uDensity,s.cloudDensity);GLES20.glUniform1f(uFar,s.cloudFarLayer);
        GLES20.glUniform1f(uMid,s.cloudMidLayer);GLES20.glUniform1f(uNear,s.cloudNearLayer);GLES20.glUniform1f(uCeiling,s.cloudStormCeiling);
        GLES20.glUniform1f(uBrightness,s.cloudBrightness);GLES20.glUniform1f(uStorm,s.stormIntensity);GLES20.glUniform1f(uWind,s.windStrength);
        GLES20.glUniform1f(uWindDir,s.windDirectionRadians);GLES20.glUniform1f(uParallax,s.parallax);
        quadBuffer.position(0);GLES20.glEnableVertexAttribArray(aPosition);GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(aPosition);GLES20.glDisable(GLES20.GL_BLEND);
    }
    public void release(){if(program!=0){GLES20.glDeleteProgram(program);program=0;}}
    private int u(@NonNull String n){return GLES20.glGetUniformLocation(program,n);}
    private static int createProgram(String vs,String fs){int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] st=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,st,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(st[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("OpenGL analytic cloud program link failed: "+log);}return p;}
    private static int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] st=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,st,0);if(st[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("OpenGL analytic cloud shader compile failed: "+log);}return s;}
}
