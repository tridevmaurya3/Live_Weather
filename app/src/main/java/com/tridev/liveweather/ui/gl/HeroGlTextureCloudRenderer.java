package com.tridev.liveweather.ui.gl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.LiveWeatherApplication;
import com.tridev.liveweather.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Photoreal cloud-atlas renderer shared by the app Hero and Live Wallpaper.
 *
 * Reality polish R1 keeps weather truth authoritative while making the cloud field
 * feel less like repeated sprites and more like one continuous atmospheric volume.
 * The renderer now consumes the resolved far/mid/near/storm-ceiling layers directly,
 * adds bounded wind shear, very slow vertical breathing, atlas variation, overcast
 * continuity and restrained Sun/Moon response. Performance detail still controls
 * only secondary samples; weather type, coverage, density and darkness never change.
 */
public final class HeroGlTextureCloudRenderer {

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
            "uniform sampler2D uAtlas;",
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
            "uniform float uBrightness;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uParallax;",
            "uniform float uDetail;",
            "uniform float uSceneLight;",
            "uniform vec2 uSunPos;",
            "uniform float uSunVis;",
            "uniform float uSunAltitude;",
            "uniform vec2 uMoonPos;",
            "uniform float uMoonVis;",
            "vec4 atlasSample(vec2 uv,float index){",
            "  float col=mod(index,4.0);float row=floor(index/4.0);",
            "  vec2 atlasUv=(vec2(col,1.0-row)+vec2(uv.x,1.0-uv.y))/vec2(4.0,2.0);",
            "  vec4 tex=texture2D(uAtlas,atlasUv);vec3 rgb=tex.rgb;float hi=max(rgb.r,max(rgb.g,rgb.b));",
            "  float alpha=tex.a*smoothstep(0.026,0.18,hi);rgb=max(vec3(0.0),(rgb-vec3(0.018))/0.982);",
            "  return vec4(rgb,alpha);",
            "}",
            "vec4 spriteWrapped(vec2 p,vec2 center,vec2 size,float cell,float opacity,float mirrorX){",
            "  float dx=p.x-center.x;dx-=floor(dx+0.5);",
            "  vec2 q=vec2(dx/size.x+0.5,(p.y-center.y)/size.y+0.5);",
            "  float inside=step(0.0,q.x)*step(q.x,1.0)*step(0.0,q.y)*step(q.y,1.0);",
            "  if(mirrorX>0.5){q.x=1.0-q.x;}",
            "  vec4 s=atlasSample(clamp(q,0.0,1.0),cell);",
            "  float edgeX=smoothstep(0.0,0.082,q.x)*smoothstep(0.0,0.082,1.0-q.x);",
            "  float edgeY=smoothstep(0.0,0.050,q.y)*smoothstep(0.0,0.050,1.0-q.y);",
            "  s.a*=inside*opacity*edgeX*edgeY;return s;",
            "}",
            "void over(inout vec3 color,inout float alpha,vec4 s,vec3 tint){",
            "  float a=clamp(s.a,0.0,0.94);color=mix(color,s.rgb*tint,a);alpha=1.0-(1.0-alpha)*(1.0-a);",
            "}",
            "void main(){",
            "  if(uCloud<0.015){gl_FragColor=vec4(0.0);return;}",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);float aspect=uResolution.x/max(1.0,uResolution.y);p.x=(p.x-0.5)*aspect+0.5;",
            "  float detail=clamp(uDetail,0.5,1.0);float cover=clamp(uCloud,0.0,1.0);float density=clamp(uDensity,0.0,1.0);",
            "  float farLayer=clamp(uFarLayer,0.0,1.0);float midLayer=clamp(uMidLayer,0.0,1.0);float nearLayer=clamp(uNearLayer,0.0,1.0);",
            "  float ceilingTruth=clamp(uStormCeiling,0.0,1.0);float mass=clamp(cover*0.67+density*0.43,0.0,1.0);",
            "  float gust=smoothstep(0.56,0.94,uWind);float gustPulse=0.5+0.5*sin(uTime*(0.72+uWind*0.82)+uWindDir*1.7);",
            "  float gustMod=1.0+gust*(0.07+0.105*gustPulse);float speed=0.0092*(0.50+uWind*1.62)*gustMod;",
            "  float projectedWind=sin(uWindDir)+cos(uWindDir)*0.38;float direction=projectedWind<0.0?-1.0:1.0;",
            "  float cross=sin(uTime*(0.40+uWind*0.30)+uWindDir*2.1)*0.009*gust;",
            "  float lift=cos(uTime*(0.34+uWind*0.24)+uWindDir)*0.0065*gust;",
            "  float breatheA=sin(uTime*0.070+0.7)*0.006;float breatheB=sin(uTime*0.047+2.2)*0.005;",
            "  float drift=direction*uTime*speed*(0.72+0.28*abs(projectedWind))+(uParallax-0.5)*0.055+cross;",
            "  float cell=uStorm>0.08?2.0:(uRain>0.06?1.0:(cover>0.78?0.0:(cover>0.52?7.0:(cover>0.25?6.0:5.0))));",
            "  float farCell=cover>0.68?0.0:(cover>0.32?7.0:4.0);float altCell=mod(cell+3.0,8.0);float farAlt=mod(farCell+5.0,8.0);",
            "  float weatherShade=clamp(uStorm*0.84+uRain*0.25+(1.0-uBrightness)*0.22+density*0.08,0.0,1.0);",
            "  float shade=mix(1.0,0.46,weatherShade);vec3 tint=vec3(shade*0.96,shade*0.99,shade*1.035);",
            "  float twilight=clamp(1.0-abs(uSunAltitude)/16.0,0.0,1.0)*uSunVis;",
            "  vec3 warmTint=vec3(1.08,0.96,0.83);vec3 moonTint=vec3(0.84,0.91,1.08);",
            "  tint*=mix(vec3(1.0),warmTint,twilight*0.16*(1.0-weatherShade));",
            "  tint*=mix(vec3(1.0),moonTint,uMoonVis*(1.0-uSceneLight)*0.09);",
            "  float farTruth=smoothstep(0.015,0.62,farLayer);float midTruth=smoothstep(0.03,0.72,midLayer);float nearTruth=smoothstep(0.05,0.78,nearLayer);",
            "  float farOpacity=(0.12+mass*0.31)*smoothstep(0.035,0.25,cover)*(0.16+0.84*farTruth);",
            "  float midOpacity=(0.20+mass*0.53)*smoothstep(0.12,0.48,cover)*(0.12+0.88*midTruth);",
            "  float nearOpacity=(0.18+mass*0.60)*smoothstep(0.28,0.72,cover)*(0.08+0.92*nearTruth);",
            "  float farDrift=drift*0.43;float midDrift=drift*0.79;float nearDrift=drift*1.13;",
            "  vec3 color=vec3(0.0);float alpha=0.0;",
            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.18+farDrift),0.205+lift*0.17+breatheB),vec2(0.76,0.245),farCell,farOpacity,0.0),tint*1.10);",
            "  if(detail>0.66){over(color,alpha,spriteWrapped(p,vec2(fract(0.71+farDrift*1.08),0.278-lift*0.15-breatheA),vec2(0.68,0.225),farAlt,farOpacity*0.78,1.0),tint*1.055);}",
            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.31+midDrift),0.360+lift*0.38+breatheA),vec2(0.96,0.385),cell,midOpacity,0.0),tint);",
            "  if(detail>0.60){over(color,alpha,spriteWrapped(p,vec2(fract(0.84+midDrift*1.07),0.423-lift*0.31-breatheB),vec2(0.86,0.355),altCell,midOpacity*0.80,1.0),tint*0.96);}",
            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.54+nearDrift),0.500+lift*0.56+breatheB),vec2(1.04,0.455),cell,nearOpacity,1.0),tint*0.89);",
            "  if(detail>0.82&&nearTruth>0.34){over(color,alpha,spriteWrapped(p,vec2(fract(0.035+nearDrift*0.94),0.452-lift*0.25+breatheA),vec2(0.79,0.325),altCell,nearOpacity*0.43,0.0),tint*0.92);}",
            "  if(detail>0.88&&cover<0.46&&farTruth>0.12){",
            "    float wispOpacity=(0.055+0.105*farTruth)*(1.0-smoothstep(0.34,0.52,cover));",
            "    over(color,alpha,spriteWrapped(p,vec2(fract(0.46+farDrift*1.26),0.315+breatheA*0.55),vec2(0.56,0.145),4.0,wispOpacity,1.0),vec3(1.08)*tint);",
            "  }",
            "  float overcast=smoothstep(0.69,0.93,mass);float ceilingStrength=max(overcast,ceilingTruth*0.94);",
            "  float xWave=0.5+0.5*sin(p.x*7.7+uTime*0.019*(1.0+gust*0.30));",
            "  float xWave2=0.5+0.5*sin(p.x*16.8-uTime*0.013*(1.0+gust*0.42)+1.8);",
            "  float ceilingTop=0.48-ceilingTruth*0.055;float ceilingBottom=0.83-ceilingTruth*0.075;",
            "  float ceiling=1.0-smoothstep(ceilingTop,ceilingBottom,p.y);",
            "  float sheet=(0.14+0.080*xWave+0.048*xWave2)*ceilingStrength*ceiling;",
            "  vec3 sheetTint=mix(vec3(0.53,0.57,0.62),vec3(0.32,0.37,0.44),clamp(uStorm*0.80+uRain*0.20+ceilingTruth*0.18,0.0,1.0))*shade;",
            "  color=mix(color,sheetTint,clamp(sheet,0.0,0.32));alpha=1.0-(1.0-alpha)*(1.0-clamp(sheet,0.0,0.32));",
            "  float undersideBand=smoothstep(0.39,0.63,p.y)*(1.0-smoothstep(0.76,0.94,p.y));",
            "  float underside=undersideBand*ceilingStrength*(0.018+weatherShade*0.050+ceilingTruth*0.032);",
            "  color=mix(color,vec3(0.18,0.22,0.285),underside);alpha=1.0-(1.0-alpha)*(1.0-underside*0.56);",
            "  vec2 sunPos=uSunPos;sunPos.x=(sunPos.x-0.5)*aspect+0.5;vec2 moonPos=uMoonPos;moonPos.x=(moonPos.x-0.5)*aspect+0.5;",
            "  float sunHalo=(1.0-smoothstep(0.10,0.58,distance(p,sunPos)))*uSunVis*(1.0-weatherShade);",
            "  float moonHalo=(1.0-smoothstep(0.08,0.42,distance(p,moonPos)))*uMoonVis*(1.0-uSceneLight);",
            "  float edgeLight=clamp(alpha*(sunHalo*0.11+moonHalo*0.055),0.0,0.12);",
            "  color+=warmTint*sunHalo*edgeLight+moonTint*moonHalo*edgeLight;",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.94));",
            "}");

    private final FloatBuffer quad;
    private int program;
    private int texture;
    private int aPosition;
    private int uAtlas;
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
    private int uBrightness;
    private int uWind;
    private int uWindDir;
    private int uParallax;
    private int uDetail;
    private int uSceneLight;
    private int uSunPos;
    private int uSunVis;
    private int uSunAltitude;
    private int uMoonPos;
    private int uMoonVis;

    private int width = 1;
    private int height = 1;
    private long startNanos;
    private volatile float detailScale = 1f;
    @Nullable private volatile GlSceneSnapshot snapshot;

    public HeroGlTextureCloudRenderer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder());
        quad = buffer.asFloatBuffer();
        quad.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot value) {
        snapshot = value;
    }

    public void setDetailScale(float value) {
        detailScale = clamp(value, 0.5f, 1f);
    }

    public void onSurfaceCreated() {
        program = createProgram(VS, FS);
        texture = loadTexture();
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uAtlas = u("uAtlas");
        uResolution = u("uResolution");
        uTime = u("uTime");
        uCloud = u("uCloud");
        uDensity = u("uDensity");
        uFarLayer = u("uFarLayer");
        uMidLayer = u("uMidLayer");
        uNearLayer = u("uNearLayer");
        uStormCeiling = u("uStormCeiling");
        uRain = u("uRain");
        uStorm = u("uStorm");
        uBrightness = u("uBrightness");
        uWind = u("uWind");
        uWindDir = u("uWindDir");
        uParallax = u("uParallax");
        uDetail = u("uDetail");
        uSceneLight = u("uSceneLight");
        uSunPos = u("uSunPos");
        uSunVis = u("uSunVis");
        uSunAltitude = u("uSunAltitude");
        uMoonPos = u("uMoonPos");
        uMoonVis = u("uMoonVis");
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
        GlSceneSnapshot scene = snapshot;
        if (program == 0 || texture == 0 || scene == null || scene.cloudCover < 0.015f) {
            return;
        }

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);

        GLES20.glUniform1i(uAtlas, 0);
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
        GLES20.glUniform1f(uBrightness, scene.cloudBrightness);
        GLES20.glUniform1f(uWind, scene.windStrength);
        GLES20.glUniform1f(uWindDir, scene.windDirectionRadians);
        GLES20.glUniform1f(uParallax, scene.parallax);
        GLES20.glUniform1f(uDetail, detailScale);
        GLES20.glUniform1f(uSceneLight, scene.sceneLight);
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
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
        if (texture != 0) {
            int[] ids = {texture};
            GLES20.glDeleteTextures(1, ids, 0);
            texture = 0;
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }

    private int loadTexture() {
        Bitmap bitmap = BitmapFactory.decodeResource(
                LiveWeatherApplication.appContext().getResources(),
                R.drawable.cloud_texture_atlas
        );
        if (bitmap == null) {
            throw new IllegalStateException("Unable to decode cloud texture atlas");
        }

        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return ids[0];
    }

    private int u(@NonNull String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
            throw new IllegalStateException("Texture cloud program link failed: " + log);
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
            throw new IllegalStateException("Texture cloud shader compile failed: " + log);
        }
        return shader;
    }
}
