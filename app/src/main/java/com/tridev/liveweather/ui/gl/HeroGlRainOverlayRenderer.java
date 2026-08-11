package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * ODM-2 dedicated OpenGL ES 2.0 rain + wet-screen pass.
 *
 * This renderer is intentionally separate from the sky/cloud pass. It draws a
 * transparent overlay after the atmospheric scene, so rain depth, wet glass and
 * heavy-rain veils can evolve without destabilising clouds, Moon or stars.
 *
 * The pass is confidence-driven: it receives only the rain/drizzle intensities
 * already resolved by DynamicRealityComposer. It performs no network or weather
 * decisions inside the frame loop.
 */
public final class HeroGlRainOverlayRenderer {

    private static final float[] QUAD = {
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
    };

    private static final String VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "varying vec2 vUv;\n" +
            "void main(){\n" +
            "  vUv=aPosition*0.5+0.5;\n" +
            "  gl_Position=vec4(aPosition,0.0,1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vUv;\n" +
            "uniform vec2 uResolution;\n" +
            "uniform float uTime;\n" +
            "uniform float uRain;\n" +
            "uniform float uDrizzle;\n" +
            "uniform float uStorm;\n" +
            "uniform float uWind;\n" +
            "uniform float uWindDir;\n" +
            "uniform float uVisibility;\n" +
            "uniform float uSceneLight;\n" +

            "float hash21(vec2 p){\n" +
            "  p=fract(p*vec2(123.34,456.21));\n" +
            "  p+=dot(p,p+45.32);\n" +
            "  return fract(p.x*p.y);\n" +
            "}\n" +
            "float hash11(float p){ return fract(sin(p*127.1)*43758.5453); }\n" +
            "float noise(vec2 p){\n" +
            "  vec2 i=floor(p);\n" +
            "  vec2 f=fract(p);\n" +
            "  f=f*f*(3.0-2.0*f);\n" +
            "  float a=hash21(i);\n" +
            "  float b=hash21(i+vec2(1.0,0.0));\n" +
            "  float c=hash21(i+vec2(0.0,1.0));\n" +
            "  float d=hash21(i+vec2(1.0,1.0));\n" +
            "  return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);\n" +
            "}\n" +

            "float streakLayer(vec2 p,float xCells,float yCells,float speed,float seed,float width,float length,float slope,float density){\n" +
            "  vec2 q=p;\n" +
            "  q.x+=q.y*slope;\n" +
            "  q*=vec2(xCells,yCells);\n" +
            "  vec2 id=floor(q);\n" +
            "  float rnd=hash21(id+vec2(seed,seed*1.73));\n" +
            "  float rnd2=hash21(id+vec2(seed*2.19,seed*0.61));\n" +
            "  float localX=fract(q.x)-0.5+(rnd-0.5)*0.54;\n" +
            "  float localY=fract(q.y+uTime*speed*(0.74+rnd*0.52)+rnd2*9.0);\n" +
            "  float core=1.0-smoothstep(width,width*2.75,abs(localX));\n" +
            "  float head=smoothstep(0.02,0.13,localY);\n" +
            "  float tail=1.0-smoothstep(length,min(0.99,length+0.20),localY);\n" +
            "  float gate=step(1.0-density,rnd2);\n" +
            "  float breakup=0.72+0.28*hash21(id+floor(uTime*0.16)+seed*4.0);\n" +
            "  return core*head*tail*gate*breakup;\n" +
            "}\n" +

            "float drizzleLayer(vec2 p,float slope){\n" +
            "  float a=streakLayer(p+vec2(0.07,0.03),43.0,24.0,0.40,4.2,0.016,0.48,slope*0.55,0.42);\n" +
            "  float b=streakLayer(p+vec2(0.31,0.17),57.0,31.0,0.51,9.8,0.012,0.40,slope*0.66,0.31);\n" +
            "  return a*0.62+b*0.44;\n" +
            "}\n" +

            "float fixedDroplet(vec2 p,float seed,float density){\n" +
            "  vec2 grid=vec2(7.0,10.0);\n" +
            "  vec2 g=p*grid;\n" +
            "  vec2 id=floor(g);\n" +
            "  float rnd=hash21(id+seed);\n" +
            "  float rnd2=hash21(id+seed*2.71);\n" +
            "  vec2 f=fract(g)-0.5;\n" +
            "  f.x+=(rnd-0.5)*0.42;\n" +
            "  f.y+=(rnd2-0.5)*0.32;\n" +
            "  f.y*=0.82;\n" +
            "  float d=length(f);\n" +
            "  float outer=1.0-smoothstep(0.245,0.315,d);\n" +
            "  float inner=1.0-smoothstep(0.155,0.225,d);\n" +
            "  float rim=max(0.0,outer-inner);\n" +
            "  return (rim*0.92+inner*0.09)*step(1.0-density,rnd);\n" +
            "}\n" +

            "vec2 slidingDroplet(vec2 p,float seed,float density){\n" +
            "  vec2 grid=vec2(5.5,7.0);\n" +
            "  vec2 g=p*grid;\n" +
            "  vec2 id=floor(g);\n" +
            "  float rnd=hash21(id+seed);\n" +
            "  float rnd2=hash21(id+seed*3.17);\n" +
            "  float rate=0.018+0.055*rnd2;\n" +
            "  float travel=fract(uTime*rate+rnd*8.0);\n" +
            "  vec2 f=fract(g)-0.5;\n" +
            "  f.x+=(rnd-0.5)*0.40;\n" +
            "  float headY=0.43-travel*1.16;\n" +
            "  float dy=f.y-headY;\n" +
            "  float headD=length(vec2(f.x,dy*0.76));\n" +
            "  float headOuter=1.0-smoothstep(0.17,0.235,headD);\n" +
            "  float headInner=1.0-smoothstep(0.105,0.155,headD);\n" +
            "  float rim=max(0.0,headOuter-headInner);\n" +
            "  float behind=smoothstep(0.00,0.08,dy)*(1.0-smoothstep(0.08,0.56,dy));\n" +
            "  float trail=(1.0-smoothstep(0.028,0.072,abs(f.x)))*behind;\n" +
            "  float gate=step(1.0-density,rnd2);\n" +
            "  return vec2((rim*0.95+headInner*0.08)*gate,trail*gate);\n" +
            "}\n" +

            "float splashField(vec2 p,float intensity){\n" +
            "  if(p.y<0.80) return 0.0;\n" +
            "  vec2 q=vec2(p.x*24.0,(p.y-0.80)*28.0);\n" +
            "  vec2 id=floor(q);\n" +
            "  vec2 f=fract(q)-0.5;\n" +
            "  float rnd=hash21(id+floor(uTime*7.0));\n" +
            "  float d=length(vec2(f.x,f.y*2.2));\n" +
            "  float ring=(1.0-smoothstep(0.20,0.30,d))*smoothstep(0.10,0.19,d);\n" +
            "  return ring*step(0.82-intensity*0.18,rnd);\n" +
            "}\n" +

            "void main(){\n" +
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);\n" +
            "  float aspect=uResolution.x/max(1.0,uResolution.y);\n" +
            "  float rain=clamp(uRain,0.0,1.0);\n" +
            "  float drizzle=clamp(uDrizzle,0.0,1.0);\n" +
            "  float effective=max(rain,drizzle*0.62);\n" +
            "  if(effective<0.004){\n" +
            "    gl_FragColor=vec4(0.0);\n" +
            "    return;\n" +
            "  }\n" +

            "  float windSide=sin(uWindDir);\n" +
            "  float slope=windSide*(0.045+uWind*0.36);\n" +
            "  float heavy=smoothstep(0.46,0.94,rain);\n" +
            "  float medium=smoothstep(0.16,0.66,rain);\n" +
            "  float flashWindow=max(4.3,7.4-uStorm*2.7);\n" +
            "  float cycle=floor(uTime/flashWindow);\n" +
            "  float phase=mod(uTime,flashWindow);\n" +
            "  float eventStart=0.42+hash11(cycle+11.9)*1.55;\n" +
            "  float local=phase-eventStart;\n" +
            "  float stormPulse=0.0;\n" +
            "  if(local>=0.0 && local<0.085) stormPulse=1.0-local/0.085;\n" +
            "  else if(local>=0.16 && local<0.255) stormPulse=(1.0-(local-0.16)/0.095)*0.54;\n" +
            "  stormPulse*=uStorm;\n" +

            "  float fine=streakLayer(p,78.0,38.0,0.58,2.7,0.010,0.46,slope*0.60,0.34+rain*0.30);\n" +
            "  float far=streakLayer(p+vec2(0.19,0.06),55.0,27.0,0.78,6.1,0.014,0.55,slope*0.76,0.30+rain*0.42);\n" +
            "  float mid=streakLayer(p+vec2(0.37,0.13),38.0,21.0,1.02,11.3,0.021,0.66,slope*0.92,0.27+rain*0.50);\n" +
            "  float near=streakLayer(p+vec2(0.53,0.23),23.0,14.0,1.34,17.9,0.032,0.76,slope*1.10,0.20+rain*0.56);\n" +
            "  float drizzleLines=drizzleLayer(p,slope)*drizzle*(1.0-medium*0.55);\n" +
            "  float rainLines=(fine*0.20+far*0.34+mid*0.58+near*0.92)*rain;\n" +
            "  float lineAlpha=clamp(drizzleLines*0.34+rainLines*0.64,0.0,0.82);\n" +
            "  vec3 rainColor=mix(vec3(0.68,0.78,0.86),vec3(0.87,0.93,0.98),0.40+stormPulse*0.40);\n" +

            "  float curtainNoise=noise(vec2(p.x*7.0+uTime*windSide*0.10,p.y*2.2-uTime*0.48));\n" +
            "  float curtainBands=noise(vec2(p.x*17.0+uTime*windSide*0.17,p.y*6.0-uTime*0.86));\n" +
            "  float curtain=(0.045+curtainNoise*0.070+curtainBands*0.035)*heavy;\n" +
            "  curtain*=0.72+0.28*(1.0-p.y);\n" +
            "  curtain*=mix(1.0,0.72,uVisibility);\n" +

            "  float wetGate=smoothstep(0.18,0.78,effective);\n" +
            "  float fixedWet=fixedDroplet(p,3.4,0.12+wetGate*0.24);\n" +
            "  float fixedWet2=fixedDroplet(p+vec2(0.11,0.07),9.6,0.08+wetGate*0.18);\n" +
            "  vec2 slider1=slidingDroplet(p,6.7,0.08+wetGate*0.26);\n" +
            "  vec2 slider2=slidingDroplet(p+vec2(0.17,0.05),13.1,0.05+wetGate*0.18);\n" +
            "  float wetRim=(fixedWet+fixedWet2+slider1.x+slider2.x)*wetGate;\n" +
            "  float wetTrail=(slider1.y+slider2.y)*wetGate;\n" +
            "  float wetBody=clamp((fixedWet+fixedWet2)*0.16+(slider1.x+slider2.x)*0.10+wetTrail*0.05,0.0,0.18);\n" +

            "  float splash=splashField(p,rain)*heavy;\n" +
            "  float lowerFilm=smoothstep(0.76,1.0,p.y)*(0.025+0.090*heavy)*(0.65+0.35*noise(vec2(p.x*9.0,uTime*0.12)));\n" +

            "  vec3 overlayColor=rainColor;\n" +
            "  float alpha=lineAlpha;\n" +
            "  vec3 veilColor=vec3(0.40,0.49,0.56);\n" +
            "  float veilAlpha=clamp(curtain,0.0,0.17);\n" +
            "  overlayColor=mix(overlayColor,veilColor,clamp(veilAlpha*2.5,0.0,0.42));\n" +
            "  alpha=1.0-(1.0-alpha)*(1.0-veilAlpha);\n" +

            "  float rimAlpha=clamp(wetRim*0.30,0.0,0.26);\n" +
            "  alpha=1.0-(1.0-alpha)*(1.0-rimAlpha);\n" +
            "  overlayColor=mix(overlayColor,vec3(0.82,0.91,0.98),clamp(rimAlpha*2.8,0.0,0.52));\n" +

            "  float darkWet=clamp(wetBody+wetTrail*0.018+lowerFilm,0.0,0.14);\n" +
            "  overlayColor=mix(overlayColor,vec3(0.17,0.25,0.31),darkWet*1.6);\n" +
            "  alpha=1.0-(1.0-alpha)*(1.0-darkWet);\n" +

            "  float splashAlpha=clamp(splash*0.30,0.0,0.22);\n" +
            "  alpha=1.0-(1.0-alpha)*(1.0-splashAlpha);\n" +
            "  overlayColor=mix(overlayColor,vec3(0.74,0.86,0.94),splashAlpha);\n" +

            "  alpha*=0.72+0.28*(0.35+uSceneLight*0.65);\n" +
            "  gl_FragColor=vec4(clamp(overlayColor,0.0,1.0),clamp(alpha,0.0,0.88));\n" +
            "}\n";

    private final FloatBuffer quadBuffer;
    private int program;
    private int width = 1;
    private int height = 1;
    private long startNanos;

    private int aPosition;
    private int uResolution;
    private int uTime;
    private int uRain;
    private int uDrizzle;
    private int uStorm;
    private int uWind;
    private int uWindDir;
    private int uVisibility;
    private int uSceneLight;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlRainOverlayRenderer() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder());
        quadBuffer = bytes.asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void onSurfaceCreated() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uResolution = uniform("uResolution");
        uTime = uniform("uTime");
        uRain = uniform("uRain");
        uDrizzle = uniform("uDrizzle");
        uStorm = uniform("uStorm");
        uWind = uniform("uWind");
        uWindDir = uniform("uWindDir");
        uVisibility = uniform("uVisibility");
        uSceneLight = uniform("uSceneLight");
        startNanos = System.nanoTime();
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        GLES20.glViewport(0, 0, this.width, this.height);
    }

    public void drawFrame() {
        GlSceneSnapshot state = snapshot;
        if (program == 0 || state == null) return;
        if (state.rainIntensity <= 0.003f && state.drizzleIntensity <= 0.003f) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, width, height);
        GLES20.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f);
        GLES20.glUniform1f(uRain, state.rainIntensity);
        GLES20.glUniform1f(uDrizzle, state.drizzleIntensity);
        GLES20.glUniform1f(uStorm, state.stormIntensity);
        GLES20.glUniform1f(uWind, state.windStrength);
        GLES20.glUniform1f(uWindDir, state.windDirectionRadians);
        GLES20.glUniform1f(uVisibility, state.visibilityFactor);
        GLES20.glUniform1f(uSceneLight, state.sceneLight);

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }

    private int uniform(@NonNull String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int result = GLES20.glCreateProgram();
        GLES20.glAttachShader(result, vertex);
        GLES20.glAttachShader(result, fragment);
        GLES20.glLinkProgram(result);

        int[] status = new int[1];
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(result);
            GLES20.glDeleteProgram(result);
            throw new IllegalStateException("OpenGL rain program link failed: " + log);
        }
        return result;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("OpenGL rain shader compile failed: " + log);
        }
        return shader;
    }
}
