package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Phase 20A depth-aware rain renderer.
 *
 * The pass keeps current-condition rain truth from GlSceneSnapshot, but renders
 * precipitation as separate far/mid/near depth bands instead of one flat sheet.
 * Wet-lens detail is deliberately gated to sustained medium/heavy rain so light
 * rain and drizzle remain visually clean.
 */
public final class HeroGlDepthRainRenderer {

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
            "#ifdef GL_FRAGMENT_PRECISION_HIGH",
            "precision highp float;",
            "#else",
            "precision mediump float;",
            "#endif",
            "varying vec2 vUv;",
            "uniform sampler2D uNoise;",
            "uniform vec2 uResolution;",
            "uniform float uTime;",
            "uniform float uRain;",
            "uniform float uDrizzle;",
            "uniform float uStorm;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uVisibility;",
            "uniform float uSceneLight;",
            "",
            "float rnd(vec2 id,float seed){",
            "  vec2 uv=fract((id+vec2(seed,seed*1.731)+0.5)/64.0);",
            "  return texture2D(uNoise,uv).r;",
            "}",
            "",
            "float rainBand(vec2 p,vec2 grid,float speed,float width,float length,float lean,float seed,float density){",
            "  vec2 q=p;",
            "  q.x+=q.y*lean;",
            "  q*=grid;",
            "  vec2 id=floor(q);",
            "  float a=rnd(id,seed);",
            "  float b=rnd(id,seed+9.37);",
            "  float c=rnd(id,seed+21.11);",
            "  float x=fract(q.x)-0.5+(a-0.5)*0.58;",
            "  float y=fract(q.y+uTime*speed*(0.72+b*0.58)+c*6.0);",
            "  float line=1.0-smoothstep(width,width*2.45,abs(x));",
            "  float head=smoothstep(0.015,0.095,y);",
            "  float tail=1.0-smoothstep(length,min(0.995,length+0.20),y);",
            "  float gate=step(1.0-density,b);",
            "  return line*head*tail*gate;",
            "}",
            "",
            "float wetDrop(vec2 p,float seed,float density){",
            "  vec2 g=p*vec2(6.0,9.0);",
            "  vec2 id=floor(g);",
            "  float a=rnd(id,seed);",
            "  float b=rnd(id,seed+13.4);",
            "  vec2 f=fract(g)-0.5+vec2((a-0.5)*0.42,(b-0.5)*0.34);",
            "  f.y*=0.80;",
            "  float d=length(f);",
            "  float outer=1.0-smoothstep(0.19,0.28,d);",
            "  float inner=1.0-smoothstep(0.115,0.18,d);",
            "  float rim=max(0.0,outer-inner);",
            "  float highlight=(1.0-smoothstep(0.025,0.085,length(f-vec2(-0.055,0.060))))*0.65;",
            "  return (rim+highlight*outer)*step(1.0-density,a);",
            "}",
            "",
            "void main(){",
            "  float rain=clamp(uRain,0.0,1.0);",
            "  float drizzle=clamp(uDrizzle,0.0,1.0);",
            "  float effective=max(rain,drizzle*0.58);",
            "  if(effective<0.004){gl_FragColor=vec4(0.0);return;}",
            "",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  vec2 sceneP=vec2((p.x-0.5)*aspect+0.5,p.y);",
            "",
            "  float side=sin(uWindDir);",
            "  float forward=cos(uWindDir);",
            "  float lean=side*(0.035+uWind*0.34)+forward*0.018*side;",
            "  float windSpeed=0.78+uWind*0.82;",
            "",
            "  float drizzleGate=drizzle*(1.0-smoothstep(0.22,0.58,rain));",
            "  float farGate=clamp(0.18+rain*0.58+drizzleGate*0.35,0.0,0.78);",
            "  float midGate=clamp(0.12+rain*0.66+drizzleGate*0.20,0.0,0.82);",
            "  float nearGate=clamp(rain*0.70-0.04,0.0,0.72);",
            "",
            "  float drizzleFine=rainBand(sceneP+vec2(0.13,0.04),vec2(106.0,58.0),0.52*windSpeed,0.0065,0.35,lean*0.48,2.3,0.25+drizzleGate*0.34);",
            "  float farRain=rainBand(sceneP+vec2(0.31,0.09),vec2(82.0,45.0),0.70*windSpeed,0.0085,0.43,lean*0.62,5.9,farGate);",
            "  float midRain=rainBand(sceneP+vec2(0.47,0.16),vec2(52.0,31.0),0.96*windSpeed,0.0135,0.56,lean*0.84,11.7,midGate);",
            "  float nearRain=rainBand(sceneP+vec2(0.67,0.25),vec2(29.0,19.0),1.28*windSpeed,0.0220,0.69,lean*1.08,18.1,nearGate);",
            "",
            "  float lineAlpha=drizzleFine*drizzleGate*0.26",
            "      +farRain*rain*(0.18+rain*0.12)",
            "      +midRain*rain*(0.34+rain*0.18)",
            "      +nearRain*rain*(0.54+rain*0.26);",
            "  lineAlpha=clamp(lineAlpha,0.0,0.82);",
            "",
            "  float heavy=smoothstep(0.52,0.92,rain);",
            "  vec2 mistUv=vec2(sceneP.x*0.46+uTime*side*0.006,sceneP.y*0.38-uTime*(0.010+rain*0.014));",
            "  float mistNoise=texture2D(uNoise,mistUv).r*0.64",
            "      +texture2D(uNoise,mistUv*1.91+vec2(0.17,0.23)).r*0.36;",
            "  float lowVisibility=1.0-clamp(uVisibility,0.0,1.0);",
            "  float rainVeil=(0.020+mistNoise*0.070)*heavy*(0.82+lowVisibility*0.34);",
            "",
            "  float wetGate=smoothstep(0.42,0.86,effective);",
            "  float wet=wetDrop(p,4.7,0.055+wetGate*0.14)",
            "      +wetDrop(p+vec2(0.16,0.08),12.6,0.035+wetGate*0.10);",
            "  wet*=wetGate;",
            "  float lowerFilm=smoothstep(0.80,1.0,p.y)*(0.010+heavy*0.045);",
            "",
            "  float stormLift=clamp(uStorm,0.0,1.0)*0.14;",
            "  vec3 rainColor=mix(vec3(0.58,0.70,0.80),vec3(0.84,0.92,0.98),0.38+uSceneLight*0.30+stormLift);",
            "  vec3 color=rainColor;",
            "  float alpha=lineAlpha;",
            "",
            "  float veil=clamp(rainVeil,0.0,0.12);",
            "  color=mix(color,vec3(0.35,0.43,0.50),veil*2.4);",
            "  alpha=1.0-(1.0-alpha)*(1.0-veil);",
            "",
            "  float wetAlpha=clamp(wet*0.20,0.0,0.18);",
            "  color=mix(color,vec3(0.86,0.94,1.0),wetAlpha*2.5);",
            "  alpha=1.0-(1.0-alpha)*(1.0-wetAlpha);",
            "",
            "  float film=clamp(lowerFilm,0.0,0.07);",
            "  color=mix(color,vec3(0.19,0.27,0.33),film*1.8);",
            "  alpha=1.0-(1.0-alpha)*(1.0-film);",
            "",
            "  alpha*=0.70+0.30*(0.34+uSceneLight*0.66);",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.86));",
            "}"
    );

    private final FloatBuffer quadBuffer;

    private int program;
    private int noiseTexture;
    private int aPosition;
    private int uNoise;
    private int uResolution;
    private int uTime;
    private int uRain;
    private int uDrizzle;
    private int uStorm;
    private int uWind;
    private int uWindDir;
    private int uVisibility;
    private int uSceneLight;
    private int width = 1;
    private int height = 1;
    private long startNanos;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlDepthRainRenderer() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(QUAD.length * 4)
                .order(ByteOrder.nativeOrder());
        quadBuffer = bytes.asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot value) {
        snapshot = value;
    }

    public void onSurfaceCreated() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        noiseTexture = GlDeterministicTextureFactory.createCloudNoiseTexture();
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uNoise = uniform("uNoise");
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

    public void onSurfaceChanged(int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        GLES20.glViewport(0, 0, width, height);
    }

    public void drawFrame() {
        GlSceneSnapshot state = snapshot;
        if (program == 0 || noiseTexture == 0 || state == null
                || (state.rainIntensity <= 0.003f && state.drizzleIntensity <= 0.003f)) {
            return;
        }

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTexture);

        GLES20.glUniform1i(uNoise, 0);
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
        GLES20.glVertexAttribPointer(
                aPosition,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                quadBuffer
        );
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPosition);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
        if (noiseTexture != 0) {
            int[] ids = {noiseTexture};
            GLES20.glDeleteTextures(1, ids, 0);
            noiseTexture = 0;
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }

    private int uniform(@NonNull String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    private static int createProgram(String vertexShader, String fragmentShader) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);
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
            throw new IllegalStateException("Depth rain program link failed: " + log);
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
            throw new IllegalStateException("Depth rain shader compile failed: " + log);
        }
        return shader;
    }
}
