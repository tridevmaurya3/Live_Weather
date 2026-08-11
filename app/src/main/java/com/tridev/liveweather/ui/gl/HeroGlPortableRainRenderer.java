package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Cross-device rain/wet-screen pass using deterministic texture samples. */
public final class HeroGlPortableRainRenderer {

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
            "uniform float uTime;",
            "uniform float uRain;",
            "uniform float uDrizzle;",
            "uniform float uStorm;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uVisibility;",
            "uniform float uSceneLight;",
            "",
            "float cellRnd(vec2 id,float seed){",
            "  vec2 uv=fract((id+vec2(seed,seed*1.73)+0.5)/64.0);",
            "  return texture2D(uNoise,uv).r;",
            "}",
            "float streak(vec2 p,float xc,float yc,float speed,float seed,float width,float len,float slope,float density){",
            "  vec2 q=p; q.x+=q.y*slope; q*=vec2(xc,yc);",
            "  vec2 id=floor(q);",
            "  float r1=cellRnd(id,seed);",
            "  float r2=cellRnd(id,seed*2.17+3.1);",
            "  float lx=fract(q.x)-0.5+(r1-0.5)*0.52;",
            "  float ly=fract(q.y+uTime*speed*(0.74+r1*0.50)+r2*7.0);",
            "  float core=1.0-smoothstep(width,width*2.70,abs(lx));",
            "  float body=smoothstep(0.02,0.12,ly)*(1.0-smoothstep(len,min(0.99,len+0.18),ly));",
            "  return core*body*step(1.0-density,r2);",
            "}",
            "float droplet(vec2 p,float seed,float density){",
            "  vec2 g=p*vec2(7.0,10.0); vec2 id=floor(g);",
            "  float r1=cellRnd(id,seed); float r2=cellRnd(id,seed+9.7);",
            "  vec2 f=fract(g)-0.5+vec2((r1-0.5)*0.40,(r2-0.5)*0.30);",
            "  f.y*=0.82; float d=length(f);",
            "  float outer=1.0-smoothstep(0.22,0.30,d);",
            "  float inner=1.0-smoothstep(0.14,0.21,d);",
            "  return (max(0.0,outer-inner)*0.90+inner*0.08)*step(1.0-density,r1);",
            "}",
            "vec2 slider(vec2 p,float seed,float density){",
            "  vec2 g=p*vec2(5.5,7.0); vec2 id=floor(g);",
            "  float r1=cellRnd(id,seed); float r2=cellRnd(id,seed+13.2);",
            "  float travel=fract(uTime*(0.018+0.052*r2)+r1*6.0);",
            "  vec2 f=fract(g)-0.5; f.x+=(r1-0.5)*0.38;",
            "  float dy=f.y-(0.43-travel*1.14);",
            "  float d=length(vec2(f.x,dy*0.76));",
            "  float outer=1.0-smoothstep(0.16,0.23,d);",
            "  float inner=1.0-smoothstep(0.10,0.15,d);",
            "  float rim=max(0.0,outer-inner);",
            "  float trail=(1.0-smoothstep(0.028,0.070,abs(f.x)))*smoothstep(0.0,0.08,dy)*(1.0-smoothstep(0.08,0.54,dy));",
            "  float gate=step(1.0-density,r2);",
            "  return vec2((rim*0.94+inner*0.07)*gate,trail*gate);",
            "}",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float rain=clamp(uRain,0.0,1.0); float drizzle=clamp(uDrizzle,0.0,1.0);",
            "  float effective=max(rain,drizzle*0.62);",
            "  if(effective<0.004){ gl_FragColor=vec4(0.0); return; }",
            "  float slope=sin(uWindDir)*(0.045+uWind*0.36);",
            "  float heavy=smoothstep(0.46,0.94,rain);",
            "  float medium=smoothstep(0.16,0.66,rain);",
            "  float fine=streak(p,78.0,38.0,0.58,2.7,0.010,0.46,slope*0.60,0.34+rain*0.30);",
            "  float far=streak(p+vec2(0.19,0.06),55.0,27.0,0.78,6.1,0.014,0.55,slope*0.76,0.30+rain*0.42);",
            "  float mid=streak(p+vec2(0.37,0.13),38.0,21.0,1.02,11.3,0.021,0.66,slope*0.92,0.27+rain*0.50);",
            "  float near=streak(p+vec2(0.53,0.23),23.0,14.0,1.34,17.9,0.032,0.76,slope*1.10,0.20+rain*0.56);",
            "  float d1=streak(p+vec2(0.07,0.03),43.0,24.0,0.40,4.2,0.016,0.48,slope*0.55,0.42);",
            "  float d2=streak(p+vec2(0.31,0.17),57.0,31.0,0.51,9.8,0.012,0.40,slope*0.66,0.31);",
            "  float lineAlpha=clamp((d1*0.62+d2*0.44)*drizzle*(1.0-medium*0.55)*0.34",
            "      +(fine*0.20+far*0.34+mid*0.58+near*0.92)*rain*0.64,0.0,0.82);",
            "  vec2 curtainUv=vec2(p.x*0.55+uTime*sin(uWindDir)*0.010,p.y*0.45-uTime*0.025);",
            "  float curtainN=texture2D(uNoise,curtainUv).r*0.68+texture2D(uNoise,curtainUv*2.0+vec2(0.21,0.17)).r*0.32;",
            "  float curtain=(0.035+curtainN*0.095)*heavy*(0.72+0.28*(1.0-p.y))*mix(1.0,0.72,uVisibility);",
            "  float wetGate=smoothstep(0.18,0.78,effective);",
            "  float fixedWet=(droplet(p,3.4,0.12+wetGate*0.24)+droplet(p+vec2(0.11,0.07),9.6,0.08+wetGate*0.18))*wetGate;",
            "  vec2 s1=slider(p,6.7,0.08+wetGate*0.26); vec2 s2=slider(p+vec2(0.17,0.05),13.1,0.05+wetGate*0.18);",
            "  float wetRim=fixedWet+(s1.x+s2.x)*wetGate;",
            "  float wetTrail=(s1.y+s2.y)*wetGate;",
            "  float lowerFilm=smoothstep(0.76,1.0,p.y)*(0.020+0.085*heavy)*(0.70+texture2D(uNoise,vec2(p.x*0.7,uTime*0.012)).r*0.30);",
            "  float flashWindow=max(4.6,7.8-uStorm*2.5);",
            "  float cycle=floor(uTime/flashWindow); float phase=mod(uTime,flashWindow);",
            "  float eventRnd=texture2D(uNoise,vec2(fract(cycle*0.037+0.13),0.31)).r;",
            "  float local=phase-(0.48+eventRnd*1.42);",
            "  float pulse=0.0;",
            "  if(local>=0.0 && local<0.070) pulse=1.0-local/0.070;",
            "  if(local>=0.115 && local<0.190) pulse=max(pulse,(1.0-(local-0.115)/0.075)*0.56);",
            "  if(local>=0.245 && local<0.315) pulse=max(pulse,(1.0-(local-0.245)/0.070)*0.22);",
            "  float stormPulse=pulse*uStorm;",
            "  vec3 rainColor=mix(vec3(0.68,0.78,0.86),vec3(0.90,0.95,1.0),0.38+stormPulse*0.44);",
            "  float alpha=lineAlpha; vec3 color=rainColor;",
            "  float veil=clamp(curtain,0.0,0.17); alpha=1.0-(1.0-alpha)*(1.0-veil);",
            "  color=mix(color,vec3(0.40,0.49,0.56),clamp(veil*2.5,0.0,0.42));",
            "  float rimA=clamp(wetRim*0.30,0.0,0.26); alpha=1.0-(1.0-alpha)*(1.0-rimA);",
            "  color=mix(color,vec3(0.82,0.91,0.98),clamp(rimA*2.8,0.0,0.52));",
            "  float darkWet=clamp(wetTrail*0.020+lowerFilm,0.0,0.14);",
            "  color=mix(color,vec3(0.17,0.25,0.31),darkWet*1.6); alpha=1.0-(1.0-alpha)*(1.0-darkWet);",
            "  alpha*=0.72+0.28*(0.35+uSceneLight*0.65);",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.88));",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program;
    private int noiseTexture;
    private int aPosition;
    private int uNoise;
    private int uTime;
    private int uRain;
    private int uDrizzle;
    private int uStorm;
    private int uWind;
    private int uWindDir;
    private int uVisibility;
    private int uSceneLight;
    private long startNanos;

    @Nullable private volatile GlSceneSnapshot snapshot;

    public HeroGlPortableRainRenderer() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder());
        quadBuffer = bytes.asFloatBuffer(); quadBuffer.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) { this.snapshot = snapshot; }

    public void onSurfaceCreated() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        noiseTexture = GlDeterministicTextureFactory.createCloudNoiseTexture();
        aPosition = GLES20.glGetAttribLocation(program,"aPosition");
        uNoise=uniform("uNoise"); uTime=uniform("uTime"); uRain=uniform("uRain");
        uDrizzle=uniform("uDrizzle"); uStorm=uniform("uStorm"); uWind=uniform("uWind");
        uWindDir=uniform("uWindDir"); uVisibility=uniform("uVisibility"); uSceneLight=uniform("uSceneLight");
        startNanos=System.nanoTime();
        GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int width,int height){ GLES20.glViewport(0,0,Math.max(1,width),Math.max(1,height)); }

    public void drawFrame(){
        GlSceneSnapshot s=snapshot;
        if(program==0||noiseTexture==0||s==null||(s.rainIntensity<=0.003f&&s.drizzleIntensity<=0.003f)) return;
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,noiseTexture);
        GLES20.glUniform1i(uNoise,0); GLES20.glUniform1f(uTime,(System.nanoTime()-startNanos)/1_000_000_000f);
        GLES20.glUniform1f(uRain,s.rainIntensity); GLES20.glUniform1f(uDrizzle,s.drizzleIntensity); GLES20.glUniform1f(uStorm,s.stormIntensity);
        GLES20.glUniform1f(uWind,s.windStrength); GLES20.glUniform1f(uWindDir,s.windDirectionRadians);
        GLES20.glUniform1f(uVisibility,s.visibilityFactor); GLES20.glUniform1f(uSceneLight,s.sceneLight);
        quadBuffer.position(0); GLES20.glEnableVertexAttribArray(aPosition); GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0); GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release(){
        if(noiseTexture!=0){int[] ids={noiseTexture}; GLES20.glDeleteTextures(1,ids,0); noiseTexture=0;}
        if(program!=0){GLES20.glDeleteProgram(program); program=0;}
    }

    private int uniform(@NonNull String name){ return GLES20.glGetUniformLocation(program,name); }
    private static int createProgram(String vs,String fs){int v=compileShader(GLES20.GL_VERTEX_SHADER,vs);int f=compileShader(GLES20.GL_FRAGMENT_SHADER,fs);int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] st=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,st,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(st[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("OpenGL portable rain program link failed: "+log);}return p;}
    private static int compileShader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] st=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,st,0);if(st[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("OpenGL portable rain shader compile failed: "+log);}return s;}
}
