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
 * Cloud-reality repair Stages 1-3 keep provider-resolved far/mid/near cloud truth authoritative,
 * rebuild the atlas presentation as compact atmospheric masses, use one-way wind advection rather
 * than pendulum movement, and preserve real depth through layer-specific speed/parallax. Slow
 * internal UV evolution adds life without reversing the cloud center motion.
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
            "  vec4 tex=texture2D(uAtlas,atlasUv);vec3 rgb=tex.rgb;",
            "  float hi=max(rgb.r,max(rgb.g,rgb.b));float luma=dot(rgb,vec3(0.2126,0.7152,0.0722));",
            "  float body=smoothstep(0.020,0.245,max(hi,luma));",
            "  float alpha=tex.a*body;",
            "  rgb=max(vec3(0.0),(rgb-vec3(0.014))/0.986);",
            "  rgb=mix(vec3(dot(rgb,vec3(0.25,0.62,0.13))),rgb,0.76);",
            "  return vec4(rgb,alpha);",
            "}",
            "vec4 spriteWrapped(vec2 p,vec2 center,vec2 size,float cell,float opacity,float mirrorX){",
            "  float dx=p.x-center.x;dx-=floor(dx+0.5);",
            "  vec2 q=vec2(dx/size.x+0.5,(p.y-center.y)/size.y+0.5);",
            "  float inside=step(0.0,q.x)*step(q.x,1.0)*step(0.0,q.y)*step(q.y,1.0);",
            "  if(mirrorX>0.5){q.x=1.0-q.x;}",
            "  float evolution=uTime*(0.020+uWind*0.014)+cell*0.73;",
            "  vec2 warp=vec2(sin(q.y*6.2+evolution),cos(q.x*5.4-evolution*0.83))*0.0065*(0.55+0.45*uWind);",
            "  q+=warp;",
            "  vec4 s=atlasSample(clamp(q,0.0,1.0),cell);",
            "  float edgeX=smoothstep(0.0,0.145,q.x)*smoothstep(0.0,0.145,1.0-q.x);",
            "  float edgeY=smoothstep(0.0,0.120,q.y)*smoothstep(0.0,0.120,1.0-q.y);",
            "  float crown=0.93+0.07*sin((q.x*5.7+q.y*3.1+cell)*3.14159);",
            "  float breakup=0.91+0.09*sin(q.x*10.6+sin(q.y*7.3+cell*0.83)*1.35+cell);",
            "  s.a*=inside*opacity*edgeX*edgeY*crown*breakup;return s;",
            "}",
            "void over(inout vec3 color,inout float alpha,vec4 s,vec3 tint){",
            "  float a=clamp(s.a,0.0,0.80);",
            "  color=mix(color,s.rgb*tint,a);",
            "  alpha=1.0-(1.0-alpha)*(1.0-a);",
            "}",
            "void main(){",
            "  if(uCloud<0.015){gl_FragColor=vec4(0.0);return;}",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);float aspect=uResolution.x/max(1.0,uResolution.y);p.x=(p.x-0.5)*aspect+0.5;",
            "  float detail=clamp(uDetail,0.5,1.0);float cover=clamp(uCloud,0.0,1.0);float density=clamp(uDensity,0.0,1.0);",
            "  float farLayer=clamp(uFarLayer,0.0,1.0);float midLayer=clamp(uMidLayer,0.0,1.0);float nearLayer=clamp(uNearLayer,0.0,1.0);",
            "  float ceilingTruth=clamp(uStormCeiling,0.0,1.0);float mass=clamp(cover*0.67+density*0.43,0.0,1.0);",
            "  float gust=smoothstep(0.54,0.94,uWind);float gustPulse=0.5+0.5*sin(uTime*(0.64+uWind*0.74)+uWindDir*1.7);",
            "  float gustMod=1.0+gust*(0.05+0.070*gustPulse);float speed=0.0128*(0.58+uWind*1.55)*gustMod;",
            "  float projectedWind=sin(uWindDir)+cos(uWindDir)*0.38;float direction=projectedWind<0.0?-1.0:1.0;",
            "  float advection=direction*uTime*speed*(0.74+0.26*abs(projectedWind));",
            "  float parallaxShift=uParallax-0.5;",
            "  float cell=uStorm>0.08?2.0:(uRain>0.06?1.0:(cover>0.78?0.0:(cover>0.52?7.0:(cover>0.25?6.0:5.0))));",
            "  float farCell=cover>0.68?0.0:(cover>0.32?7.0:4.0);float altCell=mod(cell+3.0,8.0);float farAlt=mod(farCell+5.0,8.0);",
            "  float weatherShade=clamp(uStorm*0.72+uRain*0.17+(1.0-uBrightness)*0.18+density*0.055,0.0,1.0);",
            "  float shade=mix(1.0,0.60,weatherShade);vec3 tint=vec3(shade*0.97,shade*1.00,shade*1.035);",
            "  float twilight=clamp(1.0-abs(uSunAltitude)/16.0,0.0,1.0)*uSunVis;",
            "  vec3 warmTint=vec3(1.08,0.96,0.83);vec3 moonTint=vec3(0.84,0.91,1.08);",
            "  tint*=mix(vec3(1.0),warmTint,twilight*0.15*(1.0-weatherShade));",
            "  tint*=mix(vec3(1.0),moonTint,uMoonVis*(1.0-uSceneLight)*0.08);",
            "  float farTruth=smoothstep(0.015,0.62,farLayer);float midTruth=smoothstep(0.03,0.72,midLayer);float nearTruth=smoothstep(0.05,0.78,nearLayer);",
            "  float farOpacity=(0.088+mass*0.235)*smoothstep(0.035,0.25,cover)*(0.16+0.84*farTruth);",
            "  float midOpacity=(0.142+mass*0.382)*smoothstep(0.12,0.48,cover)*(0.12+0.88*midTruth);",
            "  float nearOpacity=(0.128+mass*0.425)*smoothstep(0.28,0.72,cover)*(0.08+0.92*nearTruth);",
            "  float farDrift=advection*0.32+parallaxShift*0.015;",
            "  float midDrift=advection*0.72+parallaxShift*0.036;",
            "  float nearDrift=advection*1.18+parallaxShift*0.072;",
            "  vec3 color=vec3(0.0);float alpha=0.0;",
            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.16+farDrift),0.205),vec2(1.56,0.645),farCell,farOpacity,0.0),tint*1.11);",
            "  if(detail>0.66){over(color,alpha,spriteWrapped(p,vec2(fract(0.66+farDrift*1.05),0.275),vec2(1.41,0.600),farAlt,farOpacity*0.76,1.0),tint*1.07);}",
            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.25+midDrift),0.360),vec2(1.74,0.915),cell,midOpacity,0.0),tint);",
            "  if(detail>0.60){over(color,alpha,spriteWrapped(p,vec2(fract(0.78+midDrift*1.04),0.438),vec2(1.59,0.825),altCell,midOpacity*0.76,1.0),tint*0.97);}",
            "  over(color,alpha,spriteWrapped(p,vec2(fract(0.48+nearDrift),0.520),vec2(1.80,1.050),cell,nearOpacity,1.0),tint*0.90);",
            "  if(detail>0.82&&nearTruth>0.34){over(color,alpha,spriteWrapped(p,vec2(fract(0.01+nearDrift*0.96),0.458),vec2(1.47,0.780),altCell,nearOpacity*0.40,0.0),tint*0.94);}",
            "  if(detail>0.88&&cover<0.46&&farTruth>0.12){",
            "    float wispOpacity=(0.045+0.090*farTruth)*(1.0-smoothstep(0.34,0.52,cover));",
            "    over(color,alpha,spriteWrapped(p,vec2(fract(0.43+farDrift*1.22),0.315),vec2(1.14,0.375),4.0,wispOpacity,1.0),vec3(1.08)*tint);",
            "  }",
            "  float overcast=smoothstep(0.69,0.93,mass);float ceilingStrength=max(overcast,ceilingTruth*0.94);",
            "  float sheetFlow=direction*uTime*(0.004+uWind*0.006);",
            "  float xWave=0.5+0.5*sin((p.x-sheetFlow)*7.2+uWindDir*0.35);",
            "  float xWave2=0.5+0.5*sin((p.x-sheetFlow*0.73)*15.6+1.8+uWindDir*0.21);",
            "  float ceilingTop=0.47-ceilingTruth*0.050;float ceilingBottom=0.84-ceilingTruth*0.070;",
            "  float ceiling=1.0-smoothstep(ceilingTop,ceilingBottom,p.y);",
            "  float sheet=(0.105+0.058*xWave+0.036*xWave2)*ceilingStrength*ceiling;",
            "  vec3 sheetTint=mix(vec3(0.56,0.60,0.64),vec3(0.38,0.42,0.48),clamp(uStorm*0.75+uRain*0.15+ceilingTruth*0.16,0.0,1.0))*shade;",
            "  color=mix(color,sheetTint,clamp(sheet,0.0,0.25));alpha=1.0-(1.0-alpha)*(1.0-clamp(sheet,0.0,0.25));",
            "  float undersideBand=smoothstep(0.40,0.65,p.y)*(1.0-smoothstep(0.77,0.95,p.y));",
            "  float underside=undersideBand*ceilingStrength*(0.012+weatherShade*0.031+ceilingTruth*0.020);",
            "  color=mix(color,vec3(0.22,0.26,0.32),underside);alpha=1.0-(1.0-alpha)*(1.0-underside*0.45);",
            "  vec2 sunPos=uSunPos;sunPos.x=(sunPos.x-0.5)*aspect+0.5;vec2 moonPos=uMoonPos;moonPos.x=(moonPos.x-0.5)*aspect+0.5;",
            "  float sunHalo=(1.0-smoothstep(0.10,0.58,distance(p,sunPos)))*uSunVis*(1.0-weatherShade);",
            "  float moonHalo=(1.0-smoothstep(0.08,0.42,distance(p,moonPos)))*uMoonVis*(1.0-uSceneLight);",
            "  float edgeLight=clamp(alpha*(sunHalo*0.10+moonHalo*0.050),0.0,0.10);",
            "  color+=warmTint*sunHalo*edgeLight+moonTint*moonHalo*edgeLight;",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.88));",
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
