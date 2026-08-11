package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Perceptual night-star pass.
 *
 * The pass never invents daylight stars: a zero starVisibility snapshot yields
 * a fully transparent frame. Non-zero astronomy visibility is displayed with
 * sparse larger points that survive launcher dimming and screenshot scaling.
 * A procedural local cloud gate plus fog/rain/storm attenuation prevents the
 * overlay from behaving like decorative stars over bad weather.
 */
public final class HeroGlNightStarOverlayRenderer {

    private static final float[] QUAD = {
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
    };

    private static final String VERTEX_SHADER = String.join("\n",
            "attribute vec2 aPosition;",
            "varying vec2 vUv;",
            "void main(){",
            "  vUv=aPosition*0.5+0.5;",
            "  gl_Position=vec4(aPosition,0.0,1.0);",
            "}"
    );

    private static final String FRAGMENT_SHADER = String.join("\n",
            "precision mediump float;",
            "varying vec2 vUv;",
            "uniform vec2 uResolution;",
            "uniform float uTime;",
            "uniform float uStarVis;",
            "uniform vec2 uMoonPos;",
            "uniform float uMoonVis;",
            "uniform float uMoonIllum;",
            "uniform float uCloud;",
            "uniform float uFog;",
            "uniform float uHaze;",
            "uniform float uRain;",
            "uniform float uDrizzle;",
            "uniform float uStorm;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "",
            "float hash21(vec2 p){",
            "  p=fract(p*vec2(123.34,456.21));",
            "  p+=dot(p,p+45.32);",
            "  return fract(p.x*p.y);",
            "}",
            "float noise(vec2 p){",
            "  vec2 i=floor(p);",
            "  vec2 f=fract(p);",
            "  f=f*f*(3.0-2.0*f);",
            "  float a=hash21(i);",
            "  float b=hash21(i+vec2(1.0,0.0));",
            "  float c=hash21(i+vec2(0.0,1.0));",
            "  float d=hash21(i+vec2(1.0,1.0));",
            "  return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);",
            "}",
            "float starPoint(vec2 p,vec2 grid,float seed,float threshold,float radius){",
            "  vec2 q=p*grid;",
            "  vec2 id=floor(q);",
            "  vec2 f=fract(q)-0.5;",
            "  float rnd=hash21(id+vec2(seed,seed*1.73));",
            "  vec2 jitter=vec2(hash21(id+seed*2.11),hash21(id+seed*3.47))-0.5;",
            "  f-=jitter*0.50;",
            "  float d=length(f);",
            "  float core=1.0-smoothstep(radius,radius*2.15,d);",
            "  return core*step(threshold,rnd);",
            "}",
            "float brightSpark(vec2 p,vec2 grid,float seed,float threshold){",
            "  vec2 q=p*grid;",
            "  vec2 id=floor(q);",
            "  vec2 f=fract(q)-0.5;",
            "  float rnd=hash21(id+vec2(seed,seed*1.41));",
            "  vec2 jitter=vec2(hash21(id+seed*2.31),hash21(id+seed*3.13))-0.5;",
            "  f-=jitter*0.42;",
            "  float core=1.0-smoothstep(0.045,0.105,length(f));",
            "  float rayX=(1.0-smoothstep(0.018,0.055,abs(f.x)))*(1.0-smoothstep(0.10,0.27,abs(f.y)));",
            "  float rayY=(1.0-smoothstep(0.018,0.055,abs(f.y)))*(1.0-smoothstep(0.10,0.27,abs(f.x)));",
            "  return max(core,max(rayX,rayY)*0.38)*step(threshold,rnd);",
            "}",
            "",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  float horizonFade=1.0-smoothstep(0.68,0.96,p.y);",
            "  float weatherGate=(1.0-uFog*0.82)*(1.0-uHaze*0.58)",
            "      *(1.0-uRain*0.78)*(1.0-uDrizzle*0.52)*(1.0-uStorm*0.97);",
            "  float cloudGlobal=1.0-smoothstep(0.38,0.82,uCloud);",
            "",
            "  vec2 windVec=vec2(sin(uWindDir),-cos(uWindDir));",
            "  vec2 cq=p*vec2(2.35,2.85)+windVec*uTime*(0.0035+uWind*0.0045);",
            "  float cloudField=noise(cq)*0.64+noise(cq*2.13+vec2(7.4,3.1))*0.36;",
            "  float localCloud=smoothstep(0.50,0.77,cloudField)*uCloud;",
            "  float localClear=1.0-localCloud*0.92;",
            "",
            "  float moonDistance=length((p-uMoonPos)*vec2(aspect,1.0));",
            "  float moonGlare=exp(-moonDistance*6.8)*uMoonVis*(0.18+uMoonIllum*0.62);",
            "  float visibility=clamp(uStarVis*horizonFade*weatherGate*cloudGlobal*localClear*(1.0-moonGlare*0.74),0.0,1.0);",
            "  if(visibility<=0.002){",
            "    gl_FragColor=vec4(0.0);",
            "    return;",
            "  }",
            "",
            "  float tw1=0.91+0.09*sin(uTime*0.62+13.2);",
            "  float tw2=0.90+0.10*sin(uTime*0.49+27.5);",
            "  float tw3=0.92+0.08*sin(uTime*0.73+41.3);",
            "",
            "  float faint=starPoint(p,vec2(92.0,158.0),3.7,0.982-visibility*0.020,0.050)*tw1;",
            "  float mediumStar=starPoint(p+vec2(0.0018,0.0021),vec2(66.0,112.0),11.3,0.988-visibility*0.014,0.064)*tw2;",
            "  float bright=brightSpark(p+vec2(0.0031,0.0014),vec2(43.0,74.0),19.6,0.993-visibility*0.008)*tw3;",
            "",
            "  vec2 colorId=floor((p+vec2(0.0031,0.0014))*vec2(43.0,74.0));",
            "  float temp=hash21(colorId+vec2(28.3,7.1));",
            "  vec3 brightColor=temp<0.24?vec3(0.72,0.84,1.0):(temp>0.83?vec3(1.0,0.86,0.69):vec3(0.94,0.97,1.0));",
            "",
            "  vec3 color=vec3(0.76,0.85,1.0)*faint*0.46",
            "      +vec3(0.88,0.93,1.0)*mediumStar*0.72",
            "      +brightColor*bright*1.08;",
            "  float alpha=clamp((faint*0.48+mediumStar*0.72+bright)*visibility,0.0,0.96);",
            "  gl_FragColor=vec4(color*visibility,alpha);",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program;
    private int width = 1;
    private int height = 1;
    private int aPosition;
    private int uResolution;
    private int uTime;
    private int uStarVis;
    private int uMoonPos;
    private int uMoonVis;
    private int uMoonIllum;
    private int uCloud;
    private int uFog;
    private int uHaze;
    private int uRain;
    private int uDrizzle;
    private int uStorm;
    private int uWind;
    private int uWindDir;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlNightStarOverlayRenderer() {
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
        uStarVis = uniform("uStarVis");
        uMoonPos = uniform("uMoonPos");
        uMoonVis = uniform("uMoonVis");
        uMoonIllum = uniform("uMoonIllum");
        uCloud = uniform("uCloud");
        uFog = uniform("uFog");
        uHaze = uniform("uHaze");
        uRain = uniform("uRain");
        uDrizzle = uniform("uDrizzle");
        uStorm = uniform("uStorm");
        uWind = uniform("uWind");
        uWindDir = uniform("uWindDir");
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
        if (program == 0 || state == null || state.starVisibility <= 0.001f) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, width, height);
        GLES20.glUniform1f(uTime, (System.nanoTime() / 1_000_000_000f) % 4096f);
        GLES20.glUniform1f(uStarVis, state.starVisibility);
        GLES20.glUniform2f(uMoonPos, state.moonX, state.moonY);
        GLES20.glUniform1f(uMoonVis, state.moonVisibility);
        GLES20.glUniform1f(uMoonIllum, state.moonIllumination);
        GLES20.glUniform1f(uCloud, state.cloudCover);
        GLES20.glUniform1f(uFog, state.fogIntensity);
        GLES20.glUniform1f(uHaze, state.airHazeIntensity);
        GLES20.glUniform1f(uRain, state.rainIntensity);
        GLES20.glUniform1f(uDrizzle, state.drizzleIntensity);
        GLES20.glUniform1f(uStorm, state.stormIntensity);
        GLES20.glUniform1f(uWind, state.windStrength);
        GLES20.glUniform1f(uWindDir, state.windDirectionRadians);

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
            throw new IllegalStateException("OpenGL night-star program link failed: " + log);
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
            throw new IllegalStateException("OpenGL night-star shader compile failed: " + log);
        }
        return shader;
    }
}
