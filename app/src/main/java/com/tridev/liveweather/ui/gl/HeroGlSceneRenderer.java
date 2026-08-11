package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * OpenGL ES 2.0 full-screen Hero weather renderer.
 *
 * Stage-1 migration intentionally uses original procedural shader content rather
 * than copying assets from another application. The shader owns sky scattering,
 * irregular volumetric cloud fields, continuous rain/wet-glass, storm flashes,
 * stars and a physically-shaped lunar phase mask.
 */
public final class HeroGlSceneRenderer {

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
            "  vUv = aPosition * 0.5 + 0.5;\n" +
            "  gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vUv;\n" +
            "uniform vec2 uResolution;\n" +
            "uniform float uTime;\n" +
            "uniform vec3 uTop;\n" +
            "uniform vec3 uMid;\n" +
            "uniform vec3 uHorizon;\n" +
            "uniform vec2 uSunPos;\n" +
            "uniform float uSunVis;\n" +
            "uniform vec2 uMoonPos;\n" +
            "uniform float uMoonVis;\n" +
            "uniform float uMoonIllum;\n" +
            "uniform float uMoonPhase;\n" +
            "uniform float uStarVis;\n" +
            "uniform float uCloud;\n" +
            "uniform float uRain;\n" +
            "uniform float uDrizzle;\n" +
            "uniform float uFog;\n" +
            "uniform float uStorm;\n" +
            "uniform float uHaze;\n" +
            "uniform float uWind;\n" +
            "uniform float uWindDir;\n" +
            "uniform float uSceneLight;\n" +
            "uniform float uVisibility;\n" +
            "uniform float uParallax;\n" +
            "float hash21(vec2 p){ p=fract(p*vec2(123.34,456.21)); p+=dot(p,p+45.32); return fract(p.x*p.y); }\n" +
            "float hash11(float p){ return fract(sin(p*127.1)*43758.5453); }\n" +
            "float noise(vec2 p){\n" +
            "  vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);\n" +
            "  float a=hash21(i), b=hash21(i+vec2(1.0,0.0)), c=hash21(i+vec2(0.0,1.0)), d=hash21(i+vec2(1.0,1.0));\n" +
            "  return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);\n" +
            "}\n" +
            "float fbm(vec2 p){ float v=0.0; float a=0.5; for(int i=0;i<5;i++){v+=a*noise(p);p=p*2.03+vec2(13.1,7.7);a*=0.5;} return v; }\n" +
            "float circle(vec2 p, vec2 c, float r, float softness){ return 1.0-smoothstep(r-softness,r+softness,length(p-c)); }\n" +
            "float rainLayer(vec2 p,float scale,float speed,float seed,float slope){\n" +
            "  vec2 q=p; q.x += q.y*slope; q*=vec2(34.0,18.0)*scale;\n" +
            "  vec2 id=floor(q); float rnd=hash21(id+seed);\n" +
            "  float x=fract(q.x)-0.5+(rnd-0.5)*0.45;\n" +
            "  float y=fract(q.y+uTime*speed*(0.82+rnd*0.42)+rnd*7.0);\n" +
            "  float core=1.0-smoothstep(0.018,0.065,abs(x));\n" +
            "  float tail=smoothstep(0.02,0.34,y)*(1.0-smoothstep(0.58,0.96,y));\n" +
            "  return core*tail*step(0.30,rnd);\n" +
            "}\n" +
            "float wetDrop(vec2 p,float seed){\n" +
            "  vec2 grid=vec2(8.0,13.0); vec2 g=p*grid; vec2 id=floor(g); float rnd=hash21(id+seed);\n" +
            "  vec2 f=fract(g)-0.5; float slide=fract(uTime*(0.018+0.045*rnd)+rnd*8.0); f.y += slide-0.5;\n" +
            "  f.x += (rnd-0.5)*0.38; f.y*=0.72; float d=length(f);\n" +
            "  float rim=smoothstep(0.31,0.22,d)-smoothstep(0.21,0.14,d);\n" +
            "  float body=(1.0-smoothstep(0.26,0.30,d))*0.13; return (rim*0.75+body)*step(0.64,rnd);\n" +
            "}\n" +
            "void main(){\n" +
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);\n" +
            "  float aspect=uResolution.x/max(1.0,uResolution.y);\n" +
            "  float skyT=clamp(p.y,0.0,1.0);\n" +
            "  vec3 sky=mix(uHorizon,uMid,smoothstep(0.22,0.66,1.0-skyT));\n" +
            "  sky=mix(sky,uTop,smoothstep(0.42,1.0,1.0-skyT));\n" +
            "  float horizonHaze=(uFog*0.52+uHaze*0.34)*(1.0-smoothstep(0.48,0.90,p.y));\n" +
            "  sky=mix(sky,vec3(0.61,0.68,0.72),clamp(horizonHaze,0.0,0.58));\n" +
            "  vec3 color=sky;\n" +
            "  vec2 starGrid=p*vec2(92.0,148.0); vec2 sid=floor(starGrid); vec2 sf=fract(starGrid)-0.5;\n" +
            "  float sr=hash21(sid+vec2(31.7,9.2)); float tw=0.72+0.28*sin(uTime*(0.8+sr*2.1)+sr*40.0);\n" +
            "  float star=(1.0-smoothstep(0.015,0.055,length(sf)))*step(0.985-uStarVis*0.045,sr)*uStarVis*tw;\n" +
            "  color += vec3(0.82,0.90,1.0)*star;\n" +
            "  vec2 windVec=vec2(sin(uWindDir),-cos(uWindDir));\n" +
            "  vec2 cp=p*vec2(2.2,3.5)+windVec*uTime*(0.012+uWind*0.038);\n" +
            "  float c1=fbm(cp); float c2=fbm(cp*1.73+vec2(4.1,9.7)); float density=c1*0.72+c2*0.28;\n" +
            "  float cloudThreshold=0.74-uCloud*0.34; float cloud=smoothstep(cloudThreshold,cloudThreshold+0.18,density);\n" +
            "  float verticalMask=smoothstep(0.015,0.10,p.y)*(1.0-smoothstep(0.72,0.96,p.y)); cloud*=verticalMask*uCloud;\n" +
            "  float stormShade=clamp(uStorm*0.82+uRain*0.22,0.0,1.0);\n" +
            "  vec3 cloudLight=mix(vec3(0.84,0.87,0.90),vec3(0.16,0.20,0.27),stormShade);\n" +
            "  vec3 cloudDark=mix(vec3(0.54,0.59,0.65),vec3(0.055,0.075,0.11),stormShade);\n" +
            "  vec3 cloudColor=mix(cloudDark,cloudLight,clamp(density*1.25-0.22,0.0,1.0));\n" +
            "  color=mix(color,cloudColor,clamp(cloud*0.88,0.0,0.94));\n" +
            "  float sunAspect=aspect; vec2 sp=(p-uSunPos)*vec2(sunAspect,1.0); float sd=length(sp);\n" +
            "  float sunGlow=exp(-sd*22.0)*uSunVis*(1.0-cloud*0.72); float sunDisc=1.0-smoothstep(0.028,0.033,sd);\n" +
            "  color+=vec3(1.0,0.72,0.25)*sunGlow*0.82; color=mix(color,vec3(1.0,0.91,0.48),sunDisc*uSunVis*(1.0-cloud*0.82));\n" +
            "  vec2 mp=(p-uMoonPos)*vec2(aspect,1.0); float mr=0.031; vec2 ml=mp/mr; float m2=dot(ml,ml);\n" +
            "  if(m2<1.0 && uMoonVis>0.001){\n" +
            "    float mz=sqrt(max(0.0,1.0-m2)); float inc=ml.x*sin(uMoonPhase)+mz*(-cos(uMoonPhase));\n" +
            "    float lit=smoothstep(-0.035,0.055,inc); float earth=0.018+0.035*(1.0-uSceneLight);\n" +
            "    float lunar=earth+lit*(0.98-earth)*(0.58+0.42*max(0.0,inc));\n" +
            "    float limb=smoothstep(1.0,0.88,sqrt(m2)); float crater=0.88+0.12*noise(ml*5.8+vec2(3.7,1.9));\n" +
            "    float ma=uMoonVis*(1.0-cloud*0.78)*limb; vec3 moonCol=vec3(0.86,0.89,0.94)*lunar*crater; color=mix(color,moonCol,ma);\n" +
            "  }\n" +
            "  float moonGlow=exp(-length(mp)*16.0)*uMoonVis*(0.15+uMoonIllum*0.42)*(1.0-cloud*0.72); color+=vec3(0.32,0.42,0.58)*moonGlow;\n" +
            "  float effectiveRain=max(uRain,uDrizzle*0.62); float slope=sin(uWindDir)*(0.10+uWind*0.46);\n" +
            "  float r1=rainLayer(p,0.72,0.48,3.1,slope); float r2=rainLayer(p+vec2(0.13,0.07),1.05,0.78,8.4,slope); float r3=rainLayer(p+vec2(0.27,0.19),1.48,1.18,14.7,slope);\n" +
            "  float rain=(r1*0.30+r2*0.52+r3*0.82)*effectiveRain; color=mix(color,vec3(0.76,0.87,0.95),clamp(rain,0.0,0.88));\n" +
            "  float wet=(wetDrop(p,2.4)+wetDrop(p+vec2(0.07,0.11),9.7))*smoothstep(0.16,0.82,effectiveRain); color+=vec3(0.28,0.42,0.54)*wet;\n" +
            "  float window=max(4.2,7.2-uStorm*2.6); float cycle=floor(uTime/window); float phase=mod(uTime,window); float chance=hash11(cycle+3.7);\n" +
            "  float eventStart=0.45+hash11(cycle+11.9)*1.65; float local=phase-eventStart; float active=step(chance,0.20+uStorm*0.64);\n" +
            "  float pulse=0.0; if(local>=0.0 && local<0.11) pulse=1.0-local/0.11; else if(local>=0.18 && local<0.30) pulse=(1.0-(local-0.18)/0.12)*0.58;\n" +
            "  pulse*=active*uStorm; float anchor=0.18+hash11(cycle+21.3)*0.64; float jag=(noise(vec2(p.y*23.0,cycle*0.37))-0.5)*0.11;\n" +
            "  float boltX=anchor+jag+(p.y-0.18)*0.055; float bolt=exp(-abs(p.x-boltX)*520.0)*step(0.05,p.y)*step(p.y,0.80)*step(0.0,local)*step(local,0.22)*active*uStorm;\n" +
            "  float branch1=exp(-abs(p.x-(boltX+0.095*(p.y-0.34)))*430.0)*step(0.28,p.y)*step(p.y,0.56)*bolt;\n" +
            "  float branch2=exp(-abs(p.x-(boltX-0.12*(p.y-0.52)))*390.0)*step(0.46,p.y)*step(p.y,0.70)*bolt;\n" +
            "  float electrical=clamp(bolt+branch1+branch2,0.0,1.0); color+=vec3(0.82,0.91,1.0)*electrical*1.7;\n" +
            "  color=mix(color,vec3(0.89,0.94,1.0),clamp(pulse*0.74,0.0,0.86));\n" +
            "  float groundMist=(uFog*0.42+effectiveRain*0.15)*(1.0-smoothstep(0.64,1.0,p.y)); color=mix(color,vec3(0.48,0.54,0.57),clamp(groundMist,0.0,0.44));\n" +
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),1.0);\n" +
            "}\n";

    private final FloatBuffer quadBuffer;
    private int program;
    private int width = 1;
    private int height = 1;
    private long startNanos;

    private int aPosition;
    private int uResolution;
    private int uTime;
    private int uTop;
    private int uMid;
    private int uHorizon;
    private int uSunPos;
    private int uSunVis;
    private int uMoonPos;
    private int uMoonVis;
    private int uMoonIllum;
    private int uMoonPhase;
    private int uStarVis;
    private int uCloud;
    private int uRain;
    private int uDrizzle;
    private int uFog;
    private int uStorm;
    private int uHaze;
    private int uWind;
    private int uWindDir;
    private int uSceneLight;
    private int uVisibility;
    private int uParallax;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlSceneRenderer() {
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
        uTop = uniform("uTop");
        uMid = uniform("uMid");
        uHorizon = uniform("uHorizon");
        uSunPos = uniform("uSunPos");
        uSunVis = uniform("uSunVis");
        uMoonPos = uniform("uMoonPos");
        uMoonVis = uniform("uMoonVis");
        uMoonIllum = uniform("uMoonIllum");
        uMoonPhase = uniform("uMoonPhase");
        uStarVis = uniform("uStarVis");
        uCloud = uniform("uCloud");
        uRain = uniform("uRain");
        uDrizzle = uniform("uDrizzle");
        uFog = uniform("uFog");
        uStorm = uniform("uStorm");
        uHaze = uniform("uHaze");
        uWind = uniform("uWind");
        uWindDir = uniform("uWindDir");
        uSceneLight = uniform("uSceneLight");
        uVisibility = uniform("uVisibility");
        uParallax = uniform("uParallax");
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
        GLES20.glClearColor(0.02f, 0.04f, 0.08f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GlSceneSnapshot state = snapshot;
        if (program == 0 || state == null) return;

        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, width, height);
        GLES20.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f);
        GLES20.glUniform3f(uTop, state.topR, state.topG, state.topB);
        GLES20.glUniform3f(uMid, state.midR, state.midG, state.midB);
        GLES20.glUniform3f(uHorizon, state.horizonR, state.horizonG, state.horizonB);
        GLES20.glUniform2f(uSunPos, state.sunX, state.sunY);
        GLES20.glUniform1f(uSunVis, state.sunVisibility);
        GLES20.glUniform2f(uMoonPos, state.moonX, state.moonY);
        GLES20.glUniform1f(uMoonVis, state.moonVisibility);
        GLES20.glUniform1f(uMoonIllum, state.moonIllumination);
        GLES20.glUniform1f(uMoonPhase, state.moonPhaseAngleRadians);
        GLES20.glUniform1f(uStarVis, state.starVisibility);
        GLES20.glUniform1f(uCloud, state.cloudCover);
        GLES20.glUniform1f(uRain, state.rainIntensity);
        GLES20.glUniform1f(uDrizzle, state.drizzleIntensity);
        GLES20.glUniform1f(uFog, state.fogIntensity);
        GLES20.glUniform1f(uStorm, state.stormIntensity);
        GLES20.glUniform1f(uHaze, state.airHazeIntensity);
        GLES20.glUniform1f(uWind, state.windStrength);
        GLES20.glUniform1f(uWindDir, state.windDirectionRadians);
        GLES20.glUniform1f(uSceneLight, state.sceneLight);
        GLES20.glUniform1f(uVisibility, state.visibilityFactor);
        GLES20.glUniform1f(uParallax, state.parallax);

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPosition);
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
            throw new IllegalStateException("OpenGL program link failed: " + log);
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
            throw new IllegalStateException("OpenGL shader compile failed: " + log);
        }
        return shader;
    }
}
