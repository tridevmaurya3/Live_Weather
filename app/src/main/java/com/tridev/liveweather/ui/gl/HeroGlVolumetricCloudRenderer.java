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
            "uniform float uFarLayer;",
            "uniform float uMidLayer;",
            "uniform float uNearLayer;",
            "uniform float uStormCeiling;",
            "uniform float uRain;",
            "uniform float uStorm;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uParallax;",
            "uniform float uSceneLight;",
            "uniform float uBrightness;",
            "uniform vec2 uSunPos;",
            "uniform float uSunVis;",
            "uniform float uSunAltitude;",
            "uniform vec2 uMoonPos;",
            "uniform float uMoonVis;",
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
            "float compactMass(vec2 p,vec2 center,float scale,float seed,float tower,float evolution){",
            "  vec2 q=(p-center)/scale;",
            "  float morphA=sin(evolution+seed*1.37);float morphB=cos(evolution*0.83+seed*0.91);",
            "  float body=ellipse(q,vec2(0.00,-0.05),vec2(0.82+morphA*0.025,0.36+morphB*0.018));",
            "  body=max(body,ellipse(q,vec2(-0.48+morphB*0.018,0.04),vec2(0.46,0.40+morphA*0.020)));",
            "  body=max(body,ellipse(q,vec2(-0.10,0.22+morphA*0.018),vec2(0.52+morphB*0.018,0.55)));",
            "  body=max(body,ellipse(q,vec2(0.34+morphA*0.015,0.17),vec2(0.45,0.48+morphB*0.018)));",
            "  body=max(body,ellipse(q,vec2(0.60,0.00+morphB*0.014),vec2(0.33+morphA*0.016,0.31)));",
            "  body=max(body,mix(-2.0,ellipse(q,vec2(-0.04,0.62),vec2(0.43,0.50)),tower));",
            "  body=max(body,mix(-2.0,ellipse(q,vec2(0.16,1.00),vec2(0.38,0.48)),tower));",
            "  vec2 evolveShift=vec2(evolution*0.13,-evolution*0.071);",
            "  float detail=fbm(q*3.2+vec2(seed,seed*1.71)+evolveShift);",
            "  float micro=fbm(q*7.1+vec2(seed*2.3,-seed)+evolveShift*1.7);",
            "  return body+(detail-0.50)*0.34+(micro-0.50)*0.09;",
            "}",
            "float wrappedMass(vec2 p,vec2 center,float scale,float seed,float tower,float evolution){",
            "  float dx=p.x-center.x;dx-=floor(dx+0.5);",
            "  return compactMass(vec2(center.x+dx,p.y),center,scale,seed,tower,evolution);",
            "}",
            "void composite(inout vec3 rgb,inout float alpha,float field,vec2 p,vec2 center,float opacity){",
            "  float soft=0.085;float a=smoothstep(-soft,soft,field)*opacity;",
            "  float crown=smoothstep(-0.28,0.52,(p.y-center.y));",
            "  float rim=smoothstep(-0.02,0.15,field)-smoothstep(0.15,0.38,field);",
            "  float light=clamp(uSceneLight,0.08,1.0);",
            "  float weather=clamp(uRain*0.40+uStorm*0.78+uStormCeiling*0.18,0.0,1.0);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  vec2 sunPos=uSunPos;sunPos.x=(sunPos.x-0.5)*aspect+0.5;",
            "  vec2 moonPos=uMoonPos;moonPos.x=(moonPos.x-0.5)*aspect+0.5;",
            "  float sunSide=sign(sunPos.x-center.x+0.0001);",
            "  float sideLight=clamp(0.50+(p.x-center.x)*sunSide*2.1,0.0,1.0);",
            "  float directional=clamp(crown*0.62+sideLight*0.38,0.0,1.0);",
            "  float twilight=clamp(1.0-abs(uSunAltitude)/15.0,0.0,1.0)*uSunVis;",
            "  float underside=(1.0-crown)*(0.16+weather*0.52);",
            "  vec3 shadow=mix(vec3(0.38,0.43,0.50),vec3(0.20,0.24,0.31),weather)*mix(0.52,1.0,light);",
            "  vec3 white=vec3(0.91,0.94,0.98)*mix(0.62,1.08,light)*uBrightness;",
            "  vec3 cloud=mix(shadow,white,0.22+directional*0.70-underside*0.22);",
            "  vec3 warm=vec3(1.12,0.78,0.48);vec3 moon=vec3(0.68,0.79,1.05);",
            "  cloud=mix(cloud,cloud*warm,twilight*directional*(1.0-weather)*0.28);",
            "  cloud=mix(cloud,cloud*moon,uMoonVis*(1.0-light)*directional*0.18);",
            "  float sunNear=1.0-smoothstep(0.12,0.58,distance(p,sunPos));",
            "  float moonNear=1.0-smoothstep(0.10,0.46,distance(p,moonPos));",
            "  cloud+=rim*(warm*sunNear*uSunVis*0.16+moon*moonNear*uMoonVis*(1.0-light)*0.08);",
            "  rgb=mix(rgb,cloud,a);alpha=1.0-(1.0-alpha)*(1.0-a);",
            "}",
            "void main(){",
            "  if(uCloud<0.015){gl_FragColor=vec4(0.0);return;}",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);p.x=(p.x-0.5)*aspect+0.5;",
            "  float projected=sin(uWindDir)+cos(uWindDir)*0.38;",
            "  float direction=projected<0.0?-1.0:1.0;",
            "  float travel=direction*uTime*(0.006+uWind*0.018);",
            "  float evolution=uTime*(0.0032+uWind*0.0018);",
            "  float parallaxShift=uParallax-0.5;",
            "  float cover=clamp(uCloud,0.0,1.0),density=clamp(uDensity,0.0,1.0);",
            "  float rain=clamp(uRain,0.0,1.0),storm=clamp(uStorm,0.0,1.0);",
            "  float fair=1.0-smoothstep(0.20,0.42,cover);",
            "  float scattered=smoothstep(0.12,0.34,cover)*(1.0-smoothstep(0.42,0.62,cover));",
            "  float broken=smoothstep(0.38,0.62,cover)*(1.0-smoothstep(0.68,0.84,cover));",
            "  float overcast=smoothstep(0.68,0.91,cover)*(1.0-max(rain,storm));",
            "  float rainFamily=smoothstep(0.05,0.45,rain)*(1.0-smoothstep(0.30,0.70,storm));",
            "  float stormFamily=smoothstep(0.08,0.62,storm);",
            "  float tower=stormFamily*(0.62+0.38*uStormCeiling);",
            "  float farTruth=smoothstep(0.02,0.62,uFarLayer);",
            "  float midTruth=smoothstep(0.03,0.72,uMidLayer);",
            "  float nearTruth=smoothstep(0.05,0.78,uNearLayer);",
            "  vec3 color=vec3(0.0);float alpha=0.0;",
            "  float farTravel=travel*0.34+parallaxShift*0.014;",
            "  float midTravel=travel*0.72+parallaxShift*0.036;",
            "  float nearTravel=travel*1.18+parallaxShift*0.072;",
            "  vec2 c0=vec2(fract(0.18+farTravel),mix(0.20,0.27,broken));",
            "  vec2 c1=vec2(fract(0.64+midTravel),mix(0.34,0.41,rainFamily));",
            "  vec2 c2=vec2(fract(0.34+nearTravel),mix(0.50,0.43,stormFamily));",
            "  vec2 c3=vec2(fract(0.84+midTravel*0.93),0.30);",
            "  float farScale=mix(0.14,0.25,scattered+broken*0.72);",
            "  float midScale=mix(0.22,0.38,broken+overcast*0.80+rainFamily*0.72);",
            "  float nearScale=mix(0.26,0.43,overcast+rainFamily*0.82+stormFamily);",
            "  float f0=wrappedMass(p,c0,farScale,1.7,0.0,evolution*0.62);",
            "  float f1=wrappedMass(p,c1,midScale,4.1,tower*0.16,evolution*0.88);",
            "  float f2=wrappedMass(p,c2,nearScale,7.3,tower,evolution*1.13);",
            "  float f3=wrappedMass(p,c3,mix(0.18,0.34,overcast+rainFamily),10.9,tower*0.08,evolution*0.79);",
            "  float fairOpacity=(0.16+0.13*fair)*(0.30+0.70*farTruth);",
            "  float midOpacity=(0.18+cover*0.30)*(0.22+0.78*midTruth);",
            "  float nearOpacity=(0.15+cover*0.38)*(0.18+0.82*nearTruth);",
            "  composite(color,alpha,f0,p,c0,fairOpacity*smoothstep(0.025,0.24,cover));",
            "  composite(color,alpha,f1,p,c1,midOpacity*smoothstep(0.13,0.52,cover));",
            "  composite(color,alpha,f2,p,c2,nearOpacity*smoothstep(0.36,0.78,cover)*mix(0.72,1.0,density));",
            "  composite(color,alpha,f3,p,c3,(0.12+0.32*broken+0.38*overcast+0.42*rainFamily)*(0.25+0.75*midTruth));",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.88));",
            "}");

    private final FloatBuffer quad;
    private int program;
    private int aPosition;
    private int uResolution;
    private int uTime;
    private int uCloud;
    private int uDensity;
    private int uFarLayer;
    private int uMidLayer;
    private int uNearLayer;
    private int uStormCeiling;
    private int uRain;
    private int uStorm;
    private int uWind;
    private int uWindDir;
    private int uParallax;
    private int uSceneLight;
    private int uBrightness;
    private int uSunPos;
    private int uSunVis;
    private int uSunAltitude;
    private int uMoonPos;
    private int uMoonVis;
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
        uFarLayer = uniform("uFarLayer");
        uMidLayer = uniform("uMidLayer");
        uNearLayer = uniform("uNearLayer");
        uStormCeiling = uniform("uStormCeiling");
        uRain = uniform("uRain");
        uStorm = uniform("uStorm");
        uWind = uniform("uWind");
        uWindDir = uniform("uWindDir");
        uParallax = uniform("uParallax");
        uSceneLight = uniform("uSceneLight");
        uBrightness = uniform("uBrightness");
        uSunPos = uniform("uSunPos");
        uSunVis = uniform("uSunVis");
        uSunAltitude = uniform("uSunAltitude");
        uMoonPos = uniform("uMoonPos");
        uMoonVis = uniform("uMoonVis");
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
        GLES20.glUniform1f(uFarLayer, scene.cloudFarLayer);
        GLES20.glUniform1f(uMidLayer, scene.cloudMidLayer);
        GLES20.glUniform1f(uNearLayer, scene.cloudNearLayer);
        GLES20.glUniform1f(uStormCeiling, scene.cloudStormCeiling);
        GLES20.glUniform1f(uRain, scene.rainIntensity);
        GLES20.glUniform1f(uStorm, scene.stormIntensity);
        GLES20.glUniform1f(uWind, scene.windStrength);
        GLES20.glUniform1f(uWindDir, scene.windDirectionRadians);
        GLES20.glUniform1f(uParallax, scene.parallax);
        GLES20.glUniform1f(uSceneLight, scene.sceneLight);
        GLES20.glUniform1f(uBrightness, scene.cloudBrightness);
        GLES20.glUniform2f(uSunPos, scene.sunX, scene.sunY);
        GLES20.glUniform1f(uSunVis, scene.sunVisibility);
        GLES20.glUniform1f(uSunAltitude, scene.sunAltitude);
        GLES20.glUniform2f(uMoonPos, scene.moonX, scene.moonY);
        GLES20.glUniform1f(uMoonVis, scene.moonVisibility);
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
