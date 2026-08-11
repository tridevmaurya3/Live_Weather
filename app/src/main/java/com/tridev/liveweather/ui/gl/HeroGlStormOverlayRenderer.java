package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * ODM-3 dedicated OpenGL ES 2.0 storm + lightning pass.
 *
 * This renderer is intentionally separate from the sky/cloud base and rain pass.
 * It owns electrical timing, short branched bolts, localized cloud illumination
 * and restrained exposure flashes. The base renderer keeps storm sky/cloud
 * structure but its legacy lightning is disabled by the wallpaper pipeline.
 *
 * No weather/network decisions happen here. Storm intensity comes from the
 * shared confidence-aware SceneState / GlSceneSnapshot.
 */
public final class HeroGlStormOverlayRenderer {

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
            "uniform float uStorm;\n" +
            "uniform float uCloudDensity;\n" +
            "uniform float uCloudCeiling;\n" +
            "uniform float uCloudNear;\n" +
            "uniform float uRain;\n" +
            "uniform float uWind;\n" +
            "uniform float uWindDir;\n" +
            "uniform float uElectricalEnabled;\n" +

            "float hash21(vec2 p){\n" +
            "  p=fract(p*vec2(123.34,456.21));\n" +
            "  p+=dot(p,p+45.32);\n" +
            "  return fract(p.x*p.y);\n" +
            "}\n" +
            "float hash11(float p){ return fract(sin(p*127.1)*43758.5453); }\n" +
            "float noise(vec2 p){\n" +
            "  vec2 i=floor(p); vec2 f=fract(p);\n" +
            "  f=f*f*(3.0-2.0*f);\n" +
            "  float a=hash21(i);\n" +
            "  float b=hash21(i+vec2(1.0,0.0));\n" +
            "  float c=hash21(i+vec2(0.0,1.0));\n" +
            "  float d=hash21(i+vec2(1.0,1.0));\n" +
            "  return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);\n" +
            "}\n" +
            "float fbm3(vec2 p){\n" +
            "  float v=0.0; float a=0.55;\n" +
            "  for(int i=0;i<3;i++){\n" +
            "    v+=a*noise(p);\n" +
            "    p=p*2.03+vec2(9.1,4.7);\n" +
            "    a*=0.48;\n" +
            "  }\n" +
            "  return v;\n" +
            "}\n" +

            "float boltCenter(float y,float anchor,float seed,float y0,float y1,float drift){\n" +
            "  float t=clamp((y-y0)/max(0.001,y1-y0),0.0,1.0);\n" +
            "  float seg=t*11.0;\n" +
            "  float idx=floor(seg);\n" +
            "  float f=fract(seg);\n" +
            "  f=f*f*(3.0-2.0*f);\n" +
            "  float a=(hash11(seed+idx*1.91)-0.5)*0.105;\n" +
            "  float b=(hash11(seed+(idx+1.0)*1.91)-0.5)*0.105;\n" +
            "  float micro=(noise(vec2(t*29.0,seed*0.23))-0.5)*0.022;\n" +
            "  return anchor+mix(a,b,f)+micro+drift*t;\n" +
            "}\n" +
            "float boltLine(vec2 p,float anchor,float seed,float y0,float y1,float width,float drift){\n" +
            "  float inside=step(y0,p.y)*step(p.y,y1);\n" +
            "  float x=boltCenter(p.y,anchor,seed,y0,y1,drift);\n" +
            "  float aspect=uResolution.x/max(1.0,uResolution.y);\n" +
            "  float d=abs((p.x-x)*aspect);\n" +
            "  float core=exp(-d/max(0.00055,width));\n" +
            "  float glow=exp(-d/max(0.0024,width*5.4))*0.32;\n" +
            "  return (core+glow)*inside;\n" +
            "}\n" +

            "void main(){\n" +
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);\n" +
            "  float storm=clamp(uStorm,0.0,1.0);\n" +
            "  if(storm<0.02){\n" +
            "    gl_FragColor=vec4(0.0);\n" +
            "    return;\n" +
            "  }\n" +

            "  vec2 windVec=vec2(sin(uWindDir),-cos(uWindDir));\n" +
            "  float cloudMass=clamp(uCloudCeiling*0.74+uCloudNear*0.18+uCloudDensity*0.18,0.0,1.0);\n" +
            "  vec2 cloudP=p*vec2(2.0,2.7)+windVec*uTime*(0.005+uWind*0.010);\n" +
            "  float cloudTexture=fbm3(cloudP+vec2(8.2,3.7));\n" +
            "  float upperBand=1.0-smoothstep(0.54,0.86,p.y);\n" +
            "  float darkTexture=smoothstep(0.34,0.78,cloudTexture)*upperBand*cloudMass;\n" +
            "  float baseDark=(0.025+storm*0.075)*darkTexture;\n" +

            "  float flashWindow=max(4.6,7.8-storm*2.5);\n" +
            "  float cycle=floor(uTime/flashWindow);\n" +
            "  float phase=mod(uTime,flashWindow);\n" +
            "  float eventStart=0.48+hash11(cycle+11.9)*1.42;\n" +
            "  float local=phase-eventStart;\n" +

            "  float pulse1=0.0;\n" +
            "  if(local>=0.0 && local<0.070) pulse1=1.0-local/0.070;\n" +
            "  float pulse2=0.0;\n" +
            "  if(local>=0.115 && local<0.190) pulse2=(1.0-(local-0.115)/0.075)*0.56;\n" +
            "  float pulse3=0.0;\n" +
            "  if(local>=0.245 && local<0.315) pulse3=(1.0-(local-0.245)/0.070)*0.22;\n" +
            "  float pulse=(pulse1+pulse2+pulse3)*storm*uElectricalEnabled;\n" +

            "  float anchor=0.14+hash11(cycle+21.3)*0.72;\n" +
            "  float y0=0.060+hash11(cycle+30.4)*0.095;\n" +
            "  float y1=0.43+hash11(cycle+41.8)*0.21;\n" +
            "  float strikeChance=hash11(cycle+3.7);\n" +
            "  float showStrike=step(strikeChance,0.28+storm*0.52)*uElectricalEnabled;\n" +
            "  float strikeWindow=step(0.0,local)*step(local,0.155)*showStrike*storm;\n" +
            "  float drift=(hash11(cycle+52.1)-0.5)*0.075;\n" +

            "  float mainBolt=boltLine(p,anchor,cycle+6.3,y0,y1,0.00125,drift)*strikeWindow;\n" +
            "  float b1Start=mix(y0,y1,0.34);\n" +
            "  float b1Anchor=boltCenter(b1Start,anchor,cycle+6.3,y0,y1,drift);\n" +
            "  float b1Dir=mix(-0.16,0.16,step(0.5,hash11(cycle+73.0)));\n" +
            "  float branch1=boltLine(p,b1Anchor,cycle+71.4,b1Start,min(y1,b1Start+0.17),0.00092,b1Dir)*strikeWindow*0.70;\n" +
            "  float b2Start=mix(y0,y1,0.58);\n" +
            "  float b2Anchor=boltCenter(b2Start,anchor,cycle+6.3,y0,y1,drift);\n" +
            "  float b2Dir=mix(-0.13,0.13,step(0.5,hash11(cycle+96.0)));\n" +
            "  float branch2=boltLine(p,b2Anchor,cycle+93.6,b2Start,min(y1,b2Start+0.13),0.00078,b2Dir)*strikeWindow*0.52;\n" +
            "  float electrical=clamp(mainBolt+branch1+branch2,0.0,1.18);\n" +

            "  float dx=abs(p.x-anchor);\n" +
            "  float localized=exp(-dx*4.6)*(1.0-smoothstep(0.58,0.86,p.y));\n" +
            "  float cloudLightMask=smoothstep(0.30,0.78,cloudTexture)*upperBand;\n" +
            "  float cloudFlash=pulse*localized*cloudLightMask*(0.55+cloudMass*0.45);\n" +
            "  float sheetFlash=pulse*(0.055+storm*0.050)*(0.72+0.28*(1.0-p.y));\n" +
            "  float rainGlow=pulse*uRain*(0.025+0.030*(1.0-p.y));\n" +

            "  vec3 color=vec3(0.020,0.028,0.042)*baseDark;\n" +
            "  float alpha=baseDark;\n" +

            "  vec3 cloudGlowColor=vec3(0.63,0.72,0.88);\n" +
            "  color+=cloudGlowColor*cloudFlash*0.58;\n" +
            "  alpha+=cloudFlash*0.42;\n" +

            "  color+=vec3(0.78,0.86,0.98)*sheetFlash;\n" +
            "  alpha+=sheetFlash*0.52;\n" +

            "  color+=vec3(0.70,0.82,0.94)*rainGlow;\n" +
            "  alpha+=rainGlow*0.40;\n" +

            "  color+=vec3(0.92,0.96,1.0)*electrical*1.55;\n" +
            "  alpha+=clamp(electrical*0.92,0.0,0.96);\n" +

            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.96));\n" +
            "}\n";

    private final FloatBuffer quadBuffer;
    private int program;
    private int width = 1;
    private int height = 1;
    private long startNanos;

    private int aPosition;
    private int uResolution;
    private int uTime;
    private int uStorm;
    private int uCloudDensity;
    private int uCloudCeiling;
    private int uCloudNear;
    private int uRain;
    private int uWind;
    private int uWindDir;
    private int uElectricalEnabled;

    private volatile boolean electricalEnabled = true;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlStormOverlayRenderer() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder());
        quadBuffer = bytes.asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void setElectricalEnabled(boolean enabled) {
        electricalEnabled = enabled;
    }

    public void onSurfaceCreated() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uResolution = uniform("uResolution");
        uTime = uniform("uTime");
        uStorm = uniform("uStorm");
        uCloudDensity = uniform("uCloudDensity");
        uCloudCeiling = uniform("uCloudCeiling");
        uCloudNear = uniform("uCloudNear");
        uRain = uniform("uRain");
        uWind = uniform("uWind");
        uWindDir = uniform("uWindDir");
        uElectricalEnabled = uniform("uElectricalEnabled");
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
        if (program == 0 || state == null || state.stormIntensity < 0.02f) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, width, height);
        GLES20.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f);
        GLES20.glUniform1f(uStorm, state.stormIntensity);
        GLES20.glUniform1f(uCloudDensity, state.cloudDensity);
        GLES20.glUniform1f(uCloudCeiling, state.cloudStormCeiling);
        GLES20.glUniform1f(uCloudNear, state.cloudNearLayer);
        GLES20.glUniform1f(uRain, state.rainIntensity);
        GLES20.glUniform1f(uWind, state.windStrength);
        GLES20.glUniform1f(uWindDir, state.windDirectionRadians);
        GLES20.glUniform1f(uElectricalEnabled, electricalEnabled ? 1f : 0f);

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
            throw new IllegalStateException("OpenGL storm program link failed: " + log);
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
            throw new IllegalStateException("OpenGL storm shader compile failed: " + log);
        }
        return shader;
    }
}
