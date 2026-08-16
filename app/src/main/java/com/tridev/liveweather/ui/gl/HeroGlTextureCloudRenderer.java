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
 * Photoreal cloud sprite renderer shared by app Hero and Live Wallpaper.
 *
 * Weather data always controls cloud truth. Performance detail only changes the
 * number of secondary sprite samples; cloud type, cover, darkness and motion are
 * never replaced by a cheaper fake weather state.
 */
public final class HeroGlTextureCloudRenderer {

    private static final float[] QUAD = {-1f,-1f, 1f,-1f, -1f,1f, 1f,1f};

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
            "uniform float uRain;",
            "uniform float uStorm;",
            "uniform float uBrightness;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uParallax;",
            "uniform float uDetail;",
            "",
            "vec4 atlasSample(vec2 uv,float index){",
            "  float col=mod(index,4.0);",
            "  float row=floor(index/4.0);",
            "  vec2 atlasUv=(vec2(col,1.0-row)+vec2(uv.x,1.0-uv.y))/vec2(4.0,2.0);",
            "  vec4 tex=texture2D(uAtlas,atlasUv);",
            "  vec3 rgb=tex.rgb;",
            "  float hi=max(rgb.r,max(rgb.g,rgb.b));",
            "  float alpha=tex.a*smoothstep(0.035,0.18,hi);",
            "  rgb=max(vec3(0.0),(rgb-vec3(0.025))/0.975);",
            "  return vec4(rgb,alpha);",
            "}",
            "vec4 sprite(vec2 p,vec2 center,vec2 size,float cell,float opacity){",
            "  vec2 q=(p-center)/size+0.5;",
            "  float inside=step(0.0,q.x)*step(q.x,1.0)*step(0.0,q.y)*step(q.y,1.0);",
            "  vec4 s=atlasSample(clamp(q,0.0,1.0),cell);",
            "  s.a*=inside*opacity;",
            "  return s;",
            "}",
            "void over(inout vec3 color,inout float alpha,vec4 s,vec3 tint){",
            "  float a=clamp(s.a,0.0,0.94);",
            "  color=mix(color,s.rgb*tint,a);",
            "  alpha=1.0-(1.0-alpha)*(1.0-a);",
            "}",
            "void main(){",
            "  if(uCloud<0.015){gl_FragColor=vec4(0.0);return;}",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  p.x=(p.x-0.5)*aspect+0.5;",
            "  float detail=clamp(uDetail,0.5,1.0);",
            "  float gust=smoothstep(0.56,0.94,uWind);",
            "  float gustPulse=0.5+0.5*sin(uTime*(0.72+uWind*0.82)+uWindDir*1.7);",
            "  float gustMod=1.0+gust*(0.10+0.12*gustPulse);",
            "  float speed=0.010*(0.50+uWind*1.65)*gustMod;",
            "  float projectedWind=sin(uWindDir)+cos(uWindDir)*0.38;",
            "  float direction=projectedWind<0.0?-1.0:1.0;",
            "  float cross=sin(uTime*(0.43+uWind*0.31)+uWindDir*2.1)*0.012*gust;",
            "  float lift=cos(uTime*(0.37+uWind*0.26)+uWindDir)*0.008*gust;",
            "  float drift=direction*uTime*speed*(0.72+0.28*abs(projectedWind))+(uParallax-0.5)*0.055+cross;",
            "  float cell=uStorm>0.08?2.0:(uRain>0.06?1.0:(uCloud>0.78?0.0:(uCloud>0.52?7.0:(uCloud>0.25?6.0:5.0))));",
            "  float farCell=uCloud>0.68?0.0:(uCloud>0.32?7.0:4.0);",
            "  float shade=mix(1.0,0.48,clamp(uStorm*0.82+uRain*0.25+(1.0-uBrightness)*0.20,0.0,1.0));",
            "  vec3 tint=vec3(shade*0.96,shade*0.99,shade*1.03);",
            "  vec3 color=vec3(0.0);float alpha=0.0;",
            "  float cover=clamp(uCloud,0.0,1.0);",
            "  over(color,alpha,sprite(p,vec2(fract(0.18+drift*0.46),0.22+lift*0.22),vec2(0.72,0.25),farCell,(0.18+cover*0.26)*smoothstep(0.05,0.30,cover)),tint*1.08);",
            "  if(detail>0.72){",
            "    over(color,alpha,sprite(p,vec2(fract(0.72+drift*0.52),0.29-lift*0.18),vec2(0.67,0.24),farCell,(0.16+cover*0.24)*smoothstep(0.12,0.36,cover)),tint*1.04);",
            "  }",
            "  over(color,alpha,sprite(p,vec2(fract(0.33+drift*0.82),0.37+lift*0.44),vec2(0.94,0.40),cell,(0.30+cover*0.48)*smoothstep(0.18,0.52,cover)),tint);",
            "  if(detail>0.64){",
            "    over(color,alpha,sprite(p,vec2(fract(0.86+drift*0.88),0.43-lift*0.38),vec2(0.88,0.38),cell,(0.28+cover*0.46)*smoothstep(0.30,0.62,cover)),tint*0.94);",
            "  }",
            "  over(color,alpha,sprite(p,vec2(fract(0.55+drift*1.18),0.51+lift*0.64),vec2(1.02,0.46),cell,(0.26+cover*0.54)*smoothstep(0.45,0.76,cover)),tint*0.88);",
            "  float overcast=smoothstep(0.76,0.94,cover);",
            "  float sheet=(0.22+0.12*sin(p.x*10.0+uTime*0.026*(1.0+gust*0.34))+0.06*sin(p.x*23.0-uTime*0.017*(1.0+gust*0.46)))*overcast;",
            "  color=mix(color,vec3(0.43,0.48,0.54)*shade,clamp(sheet,0.0,0.34));",
            "  alpha=1.0-(1.0-alpha)*(1.0-clamp(sheet,0.0,0.34));",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.94));",
            "}");

    private final FloatBuffer quad;
    private int program,texture,aPosition,uAtlas,uResolution,uTime,uCloud,uDensity,uRain,uStorm,uBrightness,uWind,uWindDir,uParallax,uDetail;
    private int width=1,height=1;
    private long startNanos;
    private volatile float detailScale=1f;
    @Nullable private volatile GlSceneSnapshot snapshot;

    public HeroGlTextureCloudRenderer(){
        ByteBuffer b=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.nativeOrder());
        quad=b.asFloatBuffer();quad.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot value){snapshot=value;}
    public void setDetailScale(float value){detailScale=clamp(value,0.5f,1f);}

    public void onSurfaceCreated(){
        program=createProgram(VS,FS);
        texture=loadTexture();
        aPosition=GLES20.glGetAttribLocation(program,"aPosition");
        uAtlas=u("uAtlas");uResolution=u("uResolution");uTime=u("uTime");uCloud=u("uCloud");uDensity=u("uDensity");
        uRain=u("uRain");uStorm=u("uStorm");uBrightness=u("uBrightness");uWind=u("uWind");uWindDir=u("uWindDir");
        uParallax=u("uParallax");uDetail=u("uDetail");
        startNanos=System.nanoTime();
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int w,int h){width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,width,height);}

    public void drawFrame(){
        GlSceneSnapshot s=snapshot;
        if(program==0||texture==0||s==null||s.cloudCover<0.015f)return;
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glUniform1i(uAtlas,0);GLES20.glUniform2f(uResolution,width,height);
        GLES20.glUniform1f(uTime,(System.nanoTime()-startNanos)/1_000_000_000f);GLES20.glUniform1f(uCloud,s.cloudCover);
        GLES20.glUniform1f(uDensity,s.cloudDensity);GLES20.glUniform1f(uRain,s.rainIntensity);GLES20.glUniform1f(uStorm,s.stormIntensity);
        GLES20.glUniform1f(uBrightness,s.cloudBrightness);GLES20.glUniform1f(uWind,s.windStrength);GLES20.glUniform1f(uWindDir,s.windDirectionRadians);
        GLES20.glUniform1f(uParallax,s.parallax);GLES20.glUniform1f(uDetail,detailScale);
        quad.position(0);GLES20.glEnableVertexAttribArray(aPosition);GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,quad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release(){
        if(texture!=0){int[] ids={texture};GLES20.glDeleteTextures(1,ids,0);texture=0;}
        if(program!=0){GLES20.glDeleteProgram(program);program=0;}
    }

    private int loadTexture(){
        Bitmap bitmap=BitmapFactory.decodeResource(LiveWeatherApplication.appContext().getResources(),R.drawable.cloud_texture_atlas);
        if(bitmap==null)throw new IllegalStateException("Unable to decode cloud texture atlas");
        int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);return ids[0];
    }

    private int u(@NonNull String n){return GLES20.glGetUniformLocation(program,n);}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}

    private static int createProgram(String vs,String fs){
        int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();
        GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] st=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,st,0);
        GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(st[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("Texture cloud program link failed: "+log);}return p;
    }

    private static int compile(int type,String src){
        int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] st=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,st,0);
        if(st[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("Texture cloud shader compile failed: "+log);}return s;
    }
}
