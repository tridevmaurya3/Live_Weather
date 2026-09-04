package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Atlas-free cloud-volume foundation for the rebuilt cloud system.
 *
 * Each visible body is assembled from asymmetric soft lobes and multi-scale
 * density breakup. No rectangular sprite, stretched texture or reversing
 * displacement is used. The renderer stays disconnected from the production
 * pipeline until the weather-family, lighting and performance stages pass.
 */
public final class HeroGlVolumetricCloudRenderer {

    private static final float[] QUAD = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};

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
            "uniform vec2 uResolution;",
            "uniform float uTime;",
            "uniform float uCloud;",
            "uniform float uDensity;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uSceneLight;",
            "uniform float uBrightness;",
            "float hash21(vec2 p){",
            "  p=fract(p*vec2(123.34,456.21));p+=dot(p,p+45.32);return fract(p.x*p.y);",
            "}",
            "float noise2(vec2 p){",
            "  vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);",
            "  return mix(mix(hash21(i),hash21(i+vec2(1.0,0.0)),f.x),",
            "             mix(hash21(i+vec2(0.0,1.0)),hash21(i+vec2(1.0)),f.x),f.y);",
            "}",
            "float fbm(vec2 p){",
            "  float v=0.0,a=0.52;",
            "  for(int i=0;i<4;i++){v+=a*noise2(p);p=mat2(1.62,1.18,-1.18,1.62)*p+2.17;a*=0.48;}",
            "  return v;",
            "}",
            "float ellipse(vec2 p,vec2 center,vec2 radius){",
            "  vec2 q=(p-center)/radius;return 1.0-dot(q,q);",
            "}",
            "float compactMass(vec2 p,vec2 center,float scale,float seed){",
            "  vec2 q=(p-center)/scale;",
            "  float body=ellipse(q,vec2(0.00,-0.05),vec2(0.82,0.36));",
            "  body=max(body,ellipse(q,vec2(-0.48,0.04),vec2(0.46,0.40)));",
            "  body=max(body,ellipse(q,vec2(-0.10,0.22),vec2(0.52,0.55)));",
            "  body=max(body,ellipse(q,vec2(0.34,0.17),vec2(0.45,0.48)));",
            "  body=max(body,ellipse(q,vec2(0.60,0.00),vec2(0.33,0.31)));",
            "  float detail=fbm(q*3.2+vec2(seed,seed*1.71));",
            "  float micro=fbm(q*7.1+vec2(seed*2.3,-seed));",
            "  return body+(detail-0.50)*0.34+(micro-0.50)*0.09;",
            "}",
            "float wrappedMass(vec2 p,vec2 center,float scale,float seed){",
            "  float dx=p.x-center.x;dx-=floor(dx+0.5);",
            "  return compactMass(vec2(center.x+dx,p.y),center,scale,seed);",
            "}",
            "void composite(inout vec3 rgb,inout float alpha,float field,vec2 p,vec2 center,float opacity){",
            "  float soft=0.085;float a=smoothstep(-soft,soft,field)*opacity;",
            "  float crown=smoothstep(-0.28,0.52,(p.y-center.y));",
            "  float rim=smoothstep(-0.02,0.15,field)-smoothstep(0.15,0.38,field);",
            "  float light=clamp(uSceneLight,0.08,1.0);",
            "  vec3 shadow=vec3(0.34,0.39,0.47)*mix(0.55,1.0,light);",
            "  vec3 white=vec3(0.91,0.94,0.98)*mix(0.62,1.08,light)*uBrightness;",
            "  vec3 cloud=mix(shadow,white,0.30+0.58*crown)+rim*0.08*light;",
            "  rgb=mix(rgb,cloud,a);alpha=1.0-(1.0-alpha)*(1.0-a);",
            "}",
            "void main(){",
            "  if(uCloud<0.015){gl_FragColor=vec4(0.0);return;}",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);p.x=(p.x-0.5)*aspect+0.5;",
            "  float projected=sin(uWindDir)+cos(uWindDir)*0.38;",
            "  float direction=projected<0.0?-1.0:1.0;",
            "  float travel=direction*uTime*(0.006+uWind*0.018);",
            "  float cover=clamp(uCloud,0.0,1.0),density=clamp(uDensity,0.0,1.0);",
            "  vec3 color=vec3(0.0);float alpha=0.0;",
            "  vec2 c0=vec2(fract(0.18+travel*0.44),0.24);",
            "  vec2 c1=vec2(fract(0.64+travel*0.78),0.38);",
            "  vec2 c2=vec2(fract(0.34+travel*1.18),0.54);",
            "  float f0=wrappedMass(p,c0,0.22,1.7);",
            "  float f1=wrappedMass(p,c1,0.29,4.1);",
            "  float f2=wrappedMass(p,c2,0.34,7.3);",
            "  composite(color,alpha,f0,p,c0,(0.12+cover*0.20)*smoothstep(0.04,0.28,cover));",
            "  composite(color,alpha,f1,p,c1,(0.16+cover*0.31)*smoothstep(0.16,0.55,cover));",
            "  composite(color,alpha,f2,p,c2,(0.12+cover*0.36)*smoothstep(0.38,0.82,cover)*mix(0.72,1.0,density));",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.88));",
            "}");

    private final FloatBuffer quad;
    private int program;
    private int aPosition;
    private int uResolution;
    private int uTime;
    private int uCloud;
    private int uDensity;
    private int uWind;
    private int uWindDir;
    private int uSceneLight;
    private int uBrightness;
    private int width = 1;
    private int height = 1;
    private long startNanos;
    @Nullable private volatile GlSceneSnapshot snapshot;

    public HeroGlVolumetricCloudRenderer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder());
        quad = buffer.asFloatBuffer();
        quad.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot value) {
        snapshot = value;
    }

    public void onSurfaceCreated() {
        program = createProgram(VS, FS);
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uResolution = uniform("uResolution");
        uTime = uniform("uTime");
        uCloud = uniform("uCloud");
        uDensity = uniform("uDensity");
        uWind = uniform("uWind");
        uWindDir = uniform("uWindDir");
        uSceneLight = uniform("uSceneLight");
        uBrightness = uniform("uBrightness");
        startNanos = System.nanoTime();
    }

    public void onSurfaceChanged(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public void drawFrame() {
        GlSceneSnapshot scene = snapshot;
        if (program == 0 || scene == null || scene.cloudCover < 0.015f) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, width, height);
        GLES20.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f);
        GLES20.glUniform1f(uCloud, scene.cloudCover);
        GLES20.glUniform1f(uDensity, scene.cloudDensity);
        GLES20.glUniform1f(uWind, scene.windStrength);
        GLES20.glUniform1f(uWindDir, scene.windDirectionRadians);
        GLES20.glUniform1f(uSceneLight, scene.sceneLight);
        GLES20.glUniform1f(uBrightness, scene.cloudBrightness);
        quad.position(0);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quad);
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
        int vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
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
            throw new IllegalStateException("Volumetric cloud program link failed: " + log);
        }
        return result;
    }

    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Volumetric cloud shader compile failed: " + log);
        }
        return shader;
    }
}
