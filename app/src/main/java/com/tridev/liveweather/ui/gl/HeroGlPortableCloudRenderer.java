package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Cross-device cloud pass backed by deterministic CPU-generated noise.
 *
 * No fragment hash is used for cloud structure. The same texture bytes are
 * uploaded on emulator and real devices, then only simple UV drift/parallax is
 * applied in the shader.
 */
public final class HeroGlPortableCloudRenderer {

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
            "uniform sampler2D uCloudTex;",
            "uniform float uTime;",
            "uniform float uCloud;",
            "uniform float uDensity;",
            "uniform float uFar;",
            "uniform float uMid;",
            "uniform float uNear;",
            "uniform float uCeiling;",
            "uniform float uBrightness;",
            "uniform float uStorm;",
            "uniform float uFog;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uParallax;",
            "",
            "float band(float y,float top,float bottom,float feather){",
            "  return smoothstep(top-feather,top+feather,y)*(1.0-smoothstep(bottom-feather,bottom+feather,y));",
            "}",
            "float sampleField(vec2 uv,float scale,vec2 offset){",
            "  float a=texture2D(uCloudTex,uv*scale+offset).r;",
            "  float b=texture2D(uCloudTex,uv*(scale*0.53)+offset*0.37+vec2(0.31,0.17)).r;",
            "  return a*0.76+b*0.24;",
            "}",
            "float maskField(float n,float density,float bias){",
            "  float low=0.505-bias-density*0.085;",
            "  float high=0.555-bias-density*0.035;",
            "  return smoothstep(low,high,n);",
            "}",
            "",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  if(uCloud<=0.005){ gl_FragColor=vec4(0.0); return; }",
            "  vec2 wind=vec2(sin(uWindDir),-cos(uWindDir));",
            "  float speed=0.45+uWind*1.35;",
            "  float par=uParallax-0.5;",
            "",
            "  vec2 farOff=wind*uTime*(0.0022*speed)+vec2(par*0.012,0.0);",
            "  vec2 midOff=wind*uTime*(0.0046*speed)+vec2(0.19,0.07)+vec2(par*0.024,0.0);",
            "  vec2 nearOff=wind*uTime*(0.0078*speed)+vec2(0.43,0.28)+vec2(par*0.040,0.0);",
            "  vec2 ceilOff=wind*uTime*(0.0054*speed)+vec2(0.67,0.13)+vec2(par*0.018,0.0);",
            "",
            "  float farN=sampleField(p,0.82,farOff);",
            "  float midN=sampleField(p,1.36,midOff);",
            "  float nearN=sampleField(p,1.92,nearOff);",
            "  float ceilN=sampleField(p,1.06,ceilOff);",
            "",
            "  float farM=maskField(farN,uDensity,0.000)*band(p.y,0.03,0.57,0.09)*uFar;",
            "  float midM=maskField(midN,uDensity,0.012)*band(p.y,0.04,0.68,0.09)*uMid;",
            "  float nearM=maskField(nearN,uDensity,0.022)*band(p.y,0.06,0.78,0.09)*uNear;",
            "  float ceilM=maskField(ceilN,max(uDensity,uCeiling),0.035)*band(p.y,0.00,0.61,0.10)*uCeiling;",
            "",
            "  float stormShade=clamp(uStorm*0.72+uCeiling*0.55+(1.0-uBrightness)*0.25,0.0,1.0);",
            "  float bright=clamp(uBrightness,0.18,1.0);",
            "  vec3 farColor=mix(vec3(0.73,0.79,0.84),vec3(0.19,0.23,0.29),stormShade)*mix(0.72,1.0,bright);",
            "  vec3 midColor=mix(vec3(0.70,0.76,0.80),vec3(0.12,0.16,0.21),stormShade)*mix(0.68,1.0,bright);",
            "  vec3 nearColor=mix(vec3(0.65,0.71,0.76),vec3(0.07,0.10,0.15),stormShade)*mix(0.64,1.0,bright);",
            "  vec3 ceilColor=mix(vec3(0.30,0.34,0.39),vec3(0.035,0.050,0.075),clamp(uStorm+uCeiling,0.0,1.0));",
            "",
            "  float farA=clamp(farM*(0.16+uDensity*0.16),0.0,0.34);",
            "  float midA=clamp(midM*(0.30+uDensity*0.28),0.0,0.62);",
            "  float nearA=clamp(nearM*(0.42+uDensity*0.36),0.0,0.82);",
            "  float ceilA=clamp(ceilM*(0.50+uCeiling*0.32),0.0,0.90);",
            "",
            "  vec3 color=vec3(0.0);",
            "  float alpha=0.0;",
            "  color=mix(color,farColor,farA); alpha=1.0-(1.0-alpha)*(1.0-farA);",
            "  color=mix(color,midColor,midA); alpha=1.0-(1.0-alpha)*(1.0-midA);",
            "  color=mix(color,nearColor,nearA); alpha=1.0-(1.0-alpha)*(1.0-nearA);",
            "  color=mix(color,ceilColor,ceilA); alpha=1.0-(1.0-alpha)*(1.0-ceilA);",
            "  float lowerFog=uFog*smoothstep(0.58,0.92,p.y)*0.12;",
            "  color=mix(color,vec3(0.47,0.52,0.56),lowerFog);",
            "  alpha=1.0-(1.0-alpha)*(1.0-lowerFog);",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha*uCloud,0.0,0.96));",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program;
    private int cloudTexture;
    private int aPosition;
    private int uCloudTex;
    private int uTime;
    private int uCloud;
    private int uDensity;
    private int uFar;
    private int uMid;
    private int uNear;
    private int uCeiling;
    private int uBrightness;
    private int uStorm;
    private int uFog;
    private int uWind;
    private int uWindDir;
    private int uParallax;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlPortableCloudRenderer() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder());
        quadBuffer = bytes.asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void onSurfaceCreated() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        cloudTexture = GlDeterministicTextureFactory.createCloudNoiseTexture();
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uCloudTex = uniform("uCloudTex");
        uTime = uniform("uTime");
        uCloud = uniform("uCloud");
        uDensity = uniform("uDensity");
        uFar = uniform("uFar");
        uMid = uniform("uMid");
        uNear = uniform("uNear");
        uCeiling = uniform("uCeiling");
        uBrightness = uniform("uBrightness");
        uStorm = uniform("uStorm");
        uFog = uniform("uFog");
        uWind = uniform("uWind");
        uWindDir = uniform("uWindDir");
        uParallax = uniform("uParallax");
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
    }

    public void drawFrame() {
        GlSceneSnapshot state = snapshot;
        if (program == 0 || cloudTexture == 0 || state == null || state.cloudCover <= 0.005f) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cloudTexture);
        GLES20.glUniform1i(uCloudTex, 0);
        GLES20.glUniform1f(uTime, (System.nanoTime() / 1_000_000_000f) % 4096f);
        GLES20.glUniform1f(uCloud, state.cloudCover);
        GLES20.glUniform1f(uDensity, state.cloudDensity);
        GLES20.glUniform1f(uFar, state.cloudFarLayer);
        GLES20.glUniform1f(uMid, state.cloudMidLayer);
        GLES20.glUniform1f(uNear, state.cloudNearLayer);
        GLES20.glUniform1f(uCeiling, state.cloudStormCeiling);
        GLES20.glUniform1f(uBrightness, state.cloudBrightness);
        GLES20.glUniform1f(uStorm, state.stormIntensity);
        GLES20.glUniform1f(uFog, state.fogIntensity);
        GLES20.glUniform1f(uWind, state.windStrength);
        GLES20.glUniform1f(uWindDir, state.windDirectionRadians);
        GLES20.glUniform1f(uParallax, state.parallax);

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quadBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
        if (cloudTexture != 0) {
            int[] ids = {cloudTexture};
            GLES20.glDeleteTextures(1, ids, 0);
            cloudTexture = 0;
        }
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
            throw new IllegalStateException("OpenGL portable cloud program link failed: " + log);
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
            throw new IllegalStateException("OpenGL portable cloud shader compile failed: " + log);
        }
        return shader;
    }
}
