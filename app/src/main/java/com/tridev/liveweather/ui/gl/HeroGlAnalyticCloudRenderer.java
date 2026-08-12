package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Cross-device organic cloud renderer shared by the app and Live Wallpaper.
 *
 * Phase 20A keeps the renderer texture-free and deterministic, but replaces the
 * old repeating ellipse banks with layered, broken-up cloud masses. Every
 * layer has a different scale, drift and vertical depth; edge light and base
 * shade make clouds read as atmosphere rather than flat white shapes.
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
            "float puff(vec2 p,vec2 c,vec2 r){",
            "  float dx=wrapDx(p.x,c.x)/max(0.001,r.x);",
            "  float dy=(p.y-c.y)/max(0.001,r.y);",
            "  float d=dx*dx+dy*dy;",
            "  return exp(-d*2.45);",
            "}",
            "float cloudMass(vec2 p,float x,float y,float sx,float sy,float seed){",
            "  float v=0.0;",
            "  v+=puff(p,vec2(x,y),vec2(sx,sy))*0.70;",
            "  v+=puff(p,vec2(fract(x-sx*0.62),y+sy*0.05),vec2(sx*0.62,sy*0.82))*0.50;",
            "  v+=puff(p,vec2(fract(x+sx*0.67),y+sy*0.02),vec2(sx*0.68,sy*0.78))*0.48;",
            "  v+=puff(p,vec2(fract(x-sx*0.24),y-sy*0.48),vec2(sx*0.50,sy*0.72))*0.36;",
            "  v+=puff(p,vec2(fract(x+sx*0.28),y-sy*0.55),vec2(sx*0.46,sy*0.68))*0.34;",
            "  float breakup=0.92+0.08*sin((p.x*17.0+p.y*11.0)+seed)",
            "      +0.05*sin((p.x*31.0-p.y*19.0)+seed*1.73);",
            "  return v*breakup;",
            "}",
            "float layerField(vec2 p,float drift,float y,float sx,float sy,float seed){",
            "  float a=cloudMass(p,fract(0.11+drift),y,sx,sy,seed);",
            "  float b=cloudMass(p,fract(0.48+drift*0.91),y+sy*0.34,sx*0.78,sy*0.84,seed+2.3);",
            "  float c=cloudMass(p,fract(0.81+drift*1.07),y-sy*0.18,sx*0.66,sy*0.72,seed+5.1);",
            "  return max(a,max(b,c*0.88));",
            "}",
            "float softMask(float field,float density,float layerBias){",
            "  float threshold=mix(0.78,0.43,clamp(density+layerBias,0.0,1.0));",
            "  return smoothstep(threshold,threshold+0.34,field);",
            "}",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  if(uCloud<=0.004){gl_FragColor=vec4(0.0);return;}",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  p.x=(p.x-0.5)*(aspect/" + REFERENCE_ASPECT + ")+0.5;",
            "  vec2 wind=vec2(sin(uWindDir),-cos(uWindDir));",
            "  float speed=0.0028*(0.48+uWind*1.75);",
            "  float par=(uParallax-0.5)*0.040;",
            "  float crossSway=sin(uTime*0.031+uWindDir)*0.007*(0.35+uWind);",
            "  float farDrift=wind.x*uTime*speed*0.50+par*0.35;",
            "  float midDrift=wind.x*uTime*speed*0.82+par*0.68;",
            "  float nearDrift=wind.x*uTime*speed*1.16+par;",
            "",
            "  float farField=layerField(p,farDrift,0.20+crossSway,0.25,0.075,1.7);",
            "  float midField=layerField(p,midDrift,0.35-crossSway*0.7,0.22,0.105,4.9);",
            "  float nearField=layerField(p,nearDrift,0.50+crossSway,0.19,0.130,8.2);",
            "  float farM=softMask(farField,uDensity,0.00)*uFar;",
            "  float midM=softMask(midField,uDensity,0.06)*uMid;",
            "  float nearM=softMask(nearField,uDensity,0.11)*uNear;",
            "",
            "  float ceilingNoise=0.90+0.06*sin(p.x*13.0+uTime*speed*9.0)",
            "      +0.04*sin(p.x*27.0-uTime*speed*5.0);",
            "  float ceilingBand=smoothstep(0.02,0.20,p.y)*(1.0-smoothstep(0.56,0.78,p.y));",
            "  float ceiling=ceilingBand*ceilingNoise*uCeiling*smoothstep(0.60,0.94,uCloud);",
            "",
            "  float stormShade=clamp(uStorm*0.78+uCeiling*0.54+(1.0-uBrightness)*0.34,0.0,1.0);",
            "  float daylight=clamp(uBrightness,0.18,1.0);",
            "  vec3 farColor=mix(vec3(0.82,0.86,0.89),vec3(0.20,0.24,0.30),stormShade)*mix(0.80,1.0,daylight);",
            "  vec3 midColor=mix(vec3(0.77,0.82,0.86),vec3(0.12,0.16,0.21),stormShade)*mix(0.75,1.0,daylight);",
            "  vec3 nearColor=mix(vec3(0.70,0.76,0.81),vec3(0.065,0.09,0.13),stormShade)*mix(0.70,1.0,daylight);",
            "  vec3 ceilingColor=mix(vec3(0.43,0.48,0.53),vec3(0.025,0.040,0.065),clamp(uStorm+uCeiling,0.0,1.0));",
            "",
            "  float farEdge=max(0.0,softMask(farField,uDensity-0.05,0.0)-farM)*uFar;",
            "  float midBase=smoothstep(0.32,0.90,midField)*midM*smoothstep(0.25,0.72,p.y);",
            "  float nearBase=smoothstep(0.30,0.88,nearField)*nearM*smoothstep(0.35,0.82,p.y);",
            "  farColor+=vec3(0.08,0.09,0.10)*farEdge*(1.0-stormShade);",
            "  midColor=mix(midColor,midColor*0.66,midBase*0.42);",
            "  nearColor=mix(nearColor,nearColor*0.58,nearBase*0.50);",
            "",
            "  float d=clamp(uDensity,0.0,1.0);",
            "  float farA=clamp(farM*(0.16+d*0.18),0.0,0.38);",
            "  float midA=clamp(midM*(0.30+d*0.30),0.0,0.68);",
            "  float nearA=clamp(nearM*(0.40+d*0.38),0.0,0.84);",
            "  float ceilA=clamp(ceiling*(0.40+uCeiling*0.42),0.0,0.88);",
            "  vec3 color=vec3(0.0);float alpha=0.0;",
            "  color=mix(color,farColor,farA);alpha=1.0-(1.0-alpha)*(1.0-farA);",
            "  color=mix(color,midColor,midA);alpha=1.0-(1.0-alpha)*(1.0-midA);",
            "  color=mix(color,nearColor,nearA);alpha=1.0-(1.0-alpha)*(1.0-nearA);",
            "  color=mix(color,ceilingColor,ceilA);alpha=1.0-(1.0-alpha)*(1.0-ceilA);",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha*uCloud,0.0,0.94));",
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
