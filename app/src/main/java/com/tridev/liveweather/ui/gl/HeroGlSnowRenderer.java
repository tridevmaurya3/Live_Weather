package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Phase 20A depth-aware snow pass shared by the app Hero scene and Live Wallpaper.
 *
 * Snow truth comes only from GlSceneSnapshot. Three flake layers use different
 * scale, fall speed and wind response so snowfall has depth instead of looking
 * like a flat sheet. The deterministic noise texture keeps the layout stable
 * across emulator, Adreno and Mali devices.
 */
public final class HeroGlSnowRenderer {

    private static final float[] QUAD = {-1f,-1f, 1f,-1f, -1f,1f, 1f,1f};

    private static final String VERTEX_SHADER = String.join("\n",
            "attribute vec2 aPosition;",
            "varying vec2 vUv;",
            "void main(){vUv=aPosition*0.5+0.5;gl_Position=vec4(aPosition,0.0,1.0);}");

    private static final String FRAGMENT_SHADER = String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH",
            "precision highp float;",
            "#else",
            "precision mediump float;",
            "#endif",
            "varying vec2 vUv;",
            "uniform sampler2D uNoise;",
            "uniform float uTime;",
            "uniform float uSnow;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uSceneLight;",
            "uniform float uVisibility;",
            "",
            "float cellRnd(vec2 id,float seed){",
            "  vec2 uv=fract((id+vec2(seed,seed*1.73)+0.5)/64.0);",
            "  return texture2D(uNoise,uv).r;",
            "}",
            "float flake(vec2 p,float cols,float rows,float speed,float seed,float radius,float density,float drift,float wobble){",
            "  vec2 q=p*vec2(cols,rows);",
            "  q.x-=uTime*drift;",
            "  q.y-=uTime*speed;",
            "  vec2 id=floor(q);",
            "  float r1=cellRnd(id,seed);",
            "  float r2=cellRnd(id,seed+7.3);",
            "  vec2 f=fract(q)-0.5;",
            "  f.x+=(r1-0.5)*0.54;",
            "  f.y+=(r2-0.5)*0.24;",
            "  f.x+=sin((f.y+r1)*6.28318+uTime*(0.45+r2*0.55))*wobble;",
            "  float d=length(f);",
            "  float soft=1.0-smoothstep(radius,radius*1.85,d);",
            "  return soft*step(1.0-density,r2);",
            "}",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float snow=clamp(uSnow,0.0,1.0);",
            "  if(snow<0.004){gl_FragColor=vec4(0.0);return;}",
            "  float side=sin(uWindDir);",
            "  float drift=side*(0.015+uWind*0.085);",
            "  float far=flake(p+vec2(0.11,0.03),31.0,22.0,0.055+snow*0.025,2.9,0.055,0.24+snow*0.24,drift*0.52,0.035);",
            "  float mid=flake(p+vec2(0.29,0.13),21.0,16.0,0.083+snow*0.038,8.7,0.075,0.20+snow*0.34,drift*0.78,0.050);",
            "  float near=flake(p+vec2(0.47,0.21),13.0,10.0,0.118+snow*0.052,15.4,0.105,0.15+snow*0.42,drift*1.10,0.070);",
            "  float flakeAlpha=clamp(far*0.24+mid*0.48+near*0.76,0.0,0.88)*snow;",
            "  float depthMist=smoothstep(0.58,1.0,p.y)*smoothstep(0.38,0.92,snow)*(0.012+snow*0.045)*(0.60+0.40*(1.0-uVisibility));",
            "  vec3 flakeColor=mix(vec3(0.78,0.86,0.92),vec3(0.96,0.98,1.0),0.42+uSceneLight*0.42);",
            "  vec3 color=flakeColor*flakeAlpha;",
            "  float alpha=flakeAlpha;",
            "  color+=vec3(0.70,0.78,0.84)*depthMist;",
            "  alpha=1.0-(1.0-alpha)*(1.0-depthMist);",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.90));",
            "}");

    private final FloatBuffer quad;
    private int program;
    private int noiseTexture;
    private int aPosition;
    private int uNoise;
    private int uTime;
    private int uSnow;
    private int uWind;
    private int uWindDir;
    private int uSceneLight;
    private int uVisibility;
    private long startNanos;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlSnowRenderer() {
        ByteBuffer bytes=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.nativeOrder());
        quad=bytes.asFloatBuffer();
        quad.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        this.snapshot=snapshot;
    }

    public void onSurfaceCreated() {
        program=createProgram(VERTEX_SHADER,FRAGMENT_SHADER);
        noiseTexture=GlDeterministicTextureFactory.createCloudNoiseTexture();
        aPosition=GLES20.glGetAttribLocation(program,"aPosition");
        uNoise=uniform("uNoise");
        uTime=uniform("uTime");
        uSnow=uniform("uSnow");
        uWind=uniform("uWind");
        uWindDir=uniform("uWindDir");
        uSceneLight=uniform("uSceneLight");
        uVisibility=uniform("uVisibility");
        startNanos=System.nanoTime();
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int width,int height) {
        GLES20.glViewport(0,0,Math.max(1,width),Math.max(1,height));
    }

    public void drawFrame() {
        GlSceneSnapshot state=snapshot;
        if(program==0||noiseTexture==0||state==null||state.snowIntensity<=0.003f)return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,noiseTexture);
        GLES20.glUniform1i(uNoise,0);
        GLES20.glUniform1f(uTime,(System.nanoTime()-startNanos)/1_000_000_000f);
        GLES20.glUniform1f(uSnow,state.snowIntensity);
        GLES20.glUniform1f(uWind,state.windStrength);
        GLES20.glUniform1f(uWindDir,state.windDirectionRadians);
        GLES20.glUniform1f(uSceneLight,state.sceneLight);
        GLES20.glUniform1f(uVisibility,state.visibilityFactor);

        quad.position(0);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,quad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
        if(noiseTexture!=0){int[] ids={noiseTexture};GLES20.glDeleteTextures(1,ids,0);noiseTexture=0;}
        if(program!=0){GLES20.glDeleteProgram(program);program=0;}
    }

    private int uniform(@NonNull String name) {
        return GLES20.glGetUniformLocation(program,name);
    }

    private static int createProgram(String vertexSource,String fragmentSource) {
        int vertex=compileShader(GLES20.GL_VERTEX_SHADER,vertexSource);
        int fragment=compileShader(GLES20.GL_FRAGMENT_SHADER,fragmentSource);
        int result=GLES20.glCreateProgram();
        GLES20.glAttachShader(result,vertex);
        GLES20.glAttachShader(result,fragment);
        GLES20.glLinkProgram(result);
        int[] status=new int[1];
        GLES20.glGetProgramiv(result,GLES20.GL_LINK_STATUS,status,0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if(status[0]==0){String log=GLES20.glGetProgramInfoLog(result);GLES20.glDeleteProgram(result);throw new IllegalStateException("OpenGL snow program link failed: "+log);}
        return result;
    }

    private static int compileShader(int type,String source) {
        int shader=GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader,source);
        GLES20.glCompileShader(shader);
        int[] status=new int[1];
        GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0);
        if(status[0]==0){String log=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException("OpenGL snow shader compile failed: "+log);}
        return shader;
    }
}
