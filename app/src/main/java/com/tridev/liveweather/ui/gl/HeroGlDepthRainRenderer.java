package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Shared depth-aware rain, wet-glass and lower-world impact pass for Hero and Wallpaper.
 *
 * Device-video correction: top-origin scene coordinates mean increasing Y is downward.
 * Rain phase therefore advances with -time so the visible droplets travel with gravity.
 * Streaks are intentionally short, translucent and depth-weighted rather than bright
 * white lines laid over the scene. R9 consumes the centralized coherent visual wind
 * sample shared by snowfall, atmosphere, scenery, storms and clouds.
 */
public final class HeroGlDepthRainRenderer {

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
            "uniform sampler2D uNoise;",
            "uniform vec2 uResolution;",
            "uniform float uTime;",
            "uniform float uRain;",
            "uniform float uDrizzle;",
            "uniform float uStorm;",
            "uniform float uWind;",
            "uniform float uWindDir;",
            "uniform float uTurbulence;",
            "uniform float uFallScale;",
            "uniform float uLeanScale;",
            "uniform float uVisibility;",
            "uniform float uSceneLight;",
            "uniform float uDetail;",
            "float rnd(vec2 id,float seed){",
            "  vec2 uv=fract((id+vec2(seed,seed*1.731)+0.5)/64.0);",
            "  return texture2D(uNoise,uv).r;",
            "}",
            "float rainBand(vec2 p,vec2 grid,float speed,float width,float length,float lean,float seed,float density,float depth){",
            "  vec2 q=p;",
            "  float broadSway=sin(uTime*(0.48+depth*0.24)+p.y*3.1+seed)*0.0042*uWind*(0.72+uTurbulence*0.38);",
            "  q.x+=q.y*lean+broadSway;",
            "  q*=grid;",
            "  vec2 id=floor(q);",
            "  float a=rnd(id,seed);",
            "  float b=rnd(id,seed+9.37);",
            "  float c=rnd(id,seed+21.11);",
            "  float d=rnd(id,seed+31.47);",
            "  float jitter=0.72+b*0.62;",
            "  // p.y is top-origin and grows toward the ground. Subtracting time makes",
            "  // the procedural feature move toward larger p.y: physically downward.",
            "  float y=fract(q.y-uTime*speed*jitter+c*6.0);",
            "  float localSkew=(c-0.5)*(0.030+0.070*depth)+(d-0.5)*0.018*uWind;",
            "  float x=fract(q.x)-0.5+(a-0.5)*(0.65-0.15*depth)+(y-0.5)*localSkew;",
            "  float w=width*(0.58+0.68*a);",
            "  float len=length*(0.50+0.70*c);",
            "  float core=1.0-smoothstep(w,w*2.8,abs(x));",
            "  float head=smoothstep(0.018,0.090,y);",
            "  float tail=1.0-smoothstep(len,min(0.985,len+0.20),y);",
            "  float taper=smoothstep(0.0,0.13,y)*(0.74+0.26*d);",
            "  return core*head*tail*taper*step(1.0-density,b);",
            "}",
            "float glassDrop(vec2 p,float seed,float density,float speed){",
            "  vec2 g=p*vec2(6.2,8.7);",
            "  vec2 id=floor(g);",
            "  float a=rnd(id,seed);",
            "  float b=rnd(id,seed+13.4);",
            "  float c=rnd(id,seed+27.2);",
            "  float fall=fract(-uTime*speed*(0.34+0.70*b)+c*5.0);",
            "  vec2 f=fract(g)-0.5+vec2((a-0.5)*0.38,fall-0.50);",
            "  f.y*=0.82;",
            "  float d=length(f);",
            "  float outer=1.0-smoothstep(0.18,0.275,d);",
            "  float inner=1.0-smoothstep(0.105,0.175,d);",
            "  float rim=max(0.0,outer-inner);",
            "  float highlight=(1.0-smoothstep(0.020,0.080,length(f-vec2(-0.052,0.060))))*0.55*outer;",
            "  float trailX=1.0-smoothstep(0.025,0.070,abs(f.x));",
            "  float trailY=smoothstep(0.02,0.20,f.y)*(1.0-smoothstep(0.20,0.48,f.y));",
            "  float trail=trailX*trailY*(0.15+0.28*b);",
            "  return (rim+highlight+trail)*step(1.0-density,a);",
            "}",
            "float groundSplash(vec2 p,float seed,float density){",
            "  float ground=smoothstep(0.900,0.985,p.y);",
            "  vec2 q=vec2(p.x*39.0,(p.y-0.89)*24.0);",
            "  vec2 id=floor(q);",
            "  float a=rnd(id,seed);",
            "  float b=rnd(id,seed+8.4);",
            "  float t=fract(uTime*(1.45+uRain*1.50)+b*7.0);",
            "  vec2 f=fract(q)-vec2(0.5,0.12+t*0.50);",
            "  float arc=1.0-smoothstep(0.050,0.115,length(vec2(f.x,f.y*0.72)));",
            "  float life=(1.0-smoothstep(0.10,0.90,t))*smoothstep(0.0,0.16,t);",
            "  return arc*life*ground*step(1.0-density,a);",
            "}",
            "void main(){",
            "  float rain=clamp(uRain,0.0,1.0);",
            "  float drizzle=clamp(uDrizzle,0.0,1.0);",
            "  float detail=clamp(uDetail,0.5,1.0);",
            "  float effective=max(rain,drizzle*0.58);",
            "  if(effective<0.004){gl_FragColor=vec4(0.0);return;}",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float aspect=uResolution.x/max(1.0,uResolution.y);",
            "  vec2 sceneP=vec2((p.x-0.5)*aspect+0.5,p.y);",
            "  float side=sin(uWindDir);",
            "  float forward=cos(uWindDir);",
            "  // uWind already contains the shared coherent gust sample from the scene controller.",
            "  // Wind changes horizontal lean only; gravity remains downward.",
            "  float lean=side*uLeanScale+forward*0.010*side;",
            "  float windSpeed=uFallScale;",
            "  float drizzleGate=drizzle*(1.0-smoothstep(0.22,0.58,rain));",
            "  float farGate=clamp(0.10+rain*0.38+drizzleGate*0.24,0.0,0.58);",
            "  float midGate=clamp(0.075+rain*0.42+drizzleGate*0.14,0.0,0.60);",
            "  float nearGate=clamp(rain*0.40-0.025,0.0,0.46);",
            "  float drizzleFine=rainBand(sceneP+vec2(0.13,0.04),vec2(104.0,62.0),0.46*windSpeed,0.0047,0.16,lean*0.38,2.3,0.17+drizzleGate*0.24,0.14);",
            "  float farRain=rainBand(sceneP+vec2(0.31,0.09),vec2(76.0,48.0),0.66*windSpeed,0.0060,0.21,lean*0.54,5.9,farGate,0.30);",
            "  float midRain=rainBand(sceneP+vec2(0.47,0.16),vec2(48.0,31.0),0.92*windSpeed,0.0084,0.29,lean*0.75,11.7,midGate,0.60);",
            "  float nearRain=0.0;",
            "  if(detail>0.62){",
            "    nearRain=rainBand(sceneP+vec2(0.67,0.25),vec2(27.0,18.0),1.18*windSpeed,0.0120,0.38,lean*0.96,18.1,nearGate,0.92);",
            "  }",
            "  float crossSpray=0.0;",
            "  if(detail>0.84&&uWind>0.58&&rain>0.50){",
            "    crossSpray=rainBand(sceneP+vec2(0.08,0.33),vec2(62.0,38.0),0.80*windSpeed,0.0052,0.20,lean*1.18+side*(0.014+uTurbulence*0.010),27.4,0.16+rain*0.16,0.46);",
            "  }",
            "  float lineAlpha=drizzleFine*drizzleGate*0.12",
            "      +farRain*rain*(0.075+rain*0.045)",
            "      +midRain*rain*(0.14+rain*0.075)",
            "      +nearRain*rain*(0.22+rain*0.11)",
            "      +crossSpray*rain*0.055;",
            "  float perspective=mix(0.68,1.02,smoothstep(0.20,0.96,p.y));",
            "  lineAlpha=clamp(lineAlpha*perspective,0.0,0.44);",
            "  float heavy=smoothstep(0.54,0.92,rain);",
            "  vec2 mistUv=vec2(sceneP.x*0.45+uTime*side*(0.0015+uWind*0.0055),sceneP.y*0.38-uTime*(0.008+rain*0.011));",
            "  float mistNoise=texture2D(uNoise,mistUv).r;",
            "  if(detail>0.70){",
            "    mistNoise=mistNoise*0.68+texture2D(uNoise,mistUv*1.91+vec2(0.17,0.23)).r*0.32;",
            "  }",
            "  float lowVisibility=1.0-clamp(uVisibility,0.0,1.0);",
            "  float rainVeil=(0.010+mistNoise*0.042)*heavy*(0.82+lowVisibility*0.32);",
            "  float wetGate=smoothstep(0.42,0.86,effective);",
            "  float wet=0.0;",
            "  if(detail>0.56){wet=glassDrop(p,4.7,0.050+wetGate*0.13,0.15+rain*0.16);}",
            "  if(detail>0.84){wet+=glassDrop(p+vec2(0.16,0.08),12.6,0.032+wetGate*0.090,0.11+rain*0.14);}",
            "  wet*=wetGate;",
            "  float lowerFilm=smoothstep(0.80,1.0,p.y)*(0.006+heavy*0.030);",
            "  float filmRipple=(0.5+0.5*sin(p.x*34.0+uTime*(1.0+rain*1.5)))*(0.002+heavy*0.007)*smoothstep(0.87,1.0,p.y);",
            "  float splash=0.0;",
            "  if(detail>0.72&&rain>0.56){",
            "    splash=groundSplash(p,23.7,0.18+rain*0.30)*smoothstep(0.56,0.90,rain);",
            "  }",
            "  // Rain is mostly transmitted scene light, not white paint.",
            "  float stormTone=clamp(uStorm,0.0,1.0);",
            "  vec3 rainColor=mix(vec3(0.34,0.42,0.50),vec3(0.56,0.64,0.71),0.28+uSceneLight*0.22);",
            "  rainColor=mix(rainColor,vec3(0.37,0.44,0.52),stormTone*0.25);",
            "  vec3 color=rainColor;",
            "  float alpha=lineAlpha;",
            "  float veil=clamp(rainVeil,0.0,0.070);",
            "  color=mix(color,vec3(0.30,0.36,0.43),veil*1.8);",
            "  alpha=1.0-(1.0-alpha)*(1.0-veil);",
            "  float wetAlpha=clamp(wet*0.105,0.0,0.090);",
            "  color=mix(color,vec3(0.64,0.72,0.78),wetAlpha*1.7);",
            "  alpha=1.0-(1.0-alpha)*(1.0-wetAlpha);",
            "  float film=clamp(lowerFilm+filmRipple,0.0,0.048);",
            "  color=mix(color,vec3(0.17,0.23,0.29),film*1.45);",
            "  alpha=1.0-(1.0-alpha)*(1.0-film);",
            "  color+=vec3(0.54,0.62,0.68)*splash*0.18;",
            "  alpha=1.0-(1.0-alpha)*(1.0-clamp(splash*0.085,0.0,0.070));",
            "  alpha*=0.66+0.24*uSceneLight;",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.50));",
            "}");

    private final FloatBuffer quad;
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
    private int uTurbulence;
    private int uFallScale;
    private int uLeanScale;
    private int uVisibility;
    private int uSceneLight;
    private int uDetail;
    private int width = 1;
    private int height = 1;
    private volatile float detailScale = 1f;
    @Nullable private volatile GlSceneSnapshot snapshot;

    public HeroGlDepthRainRenderer() {
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
        noiseTexture = GlDeterministicTextureFactory.createCloudNoiseTexture();
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uNoise = u("uNoise");
        uResolution = u("uResolution");
        uTime = u("uTime");
        uRain = u("uRain");
        uDrizzle = u("uDrizzle");
        uStorm = u("uStorm");
        uWind = u("uWind");
        uWindDir = u("uWindDir");
        uTurbulence = u("uTurbulence");
        uFallScale = u("uFallScale");
        uLeanScale = u("uLeanScale");
        uVisibility = u("uVisibility");
        uSceneLight = u("uSceneLight");
        uDetail = u("uDetail");
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
        if (program == 0 || noiseTexture == 0 || state == null
                || (state.rainIntensity <= 0.003f && state.drizzleIntensity <= 0.003f)) {
            return;
        }

        float renderSeconds = UnifiedWindController.sharedMonotonicSeconds();
        float turbulence = PrecipitationDynamicsPolicy.turbulence(
                state.windStrength, state.stormIntensity);
        float fallScale = PrecipitationDynamicsPolicy.fallSpeedScale(
                state.rainIntensity, state.drizzleIntensity, state.windStrength);
        float leanScale = PrecipitationDynamicsPolicy.leanScale(
                state.windStrength, state.stormIntensity);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTexture);
        GLES20.glUniform1i(uNoise, 0);
        GLES20.glUniform2f(uResolution, width, height);
        GLES20.glUniform1f(uTime, renderSeconds);
        GLES20.glUniform1f(uRain, state.rainIntensity);
        GLES20.glUniform1f(uDrizzle, state.drizzleIntensity);
        GLES20.glUniform1f(uStorm, state.stormIntensity);
        GLES20.glUniform1f(uWind, state.windStrength);
        GLES20.glUniform1f(uWindDir, state.windDirectionRadians);
        GLES20.glUniform1f(uTurbulence, turbulence);
        GLES20.glUniform1f(uFallScale, fallScale);
        GLES20.glUniform1f(uLeanScale, leanScale);
        GLES20.glUniform1f(uVisibility, state.visibilityFactor);
        GLES20.glUniform1f(uSceneLight, state.sceneLight);
        GLES20.glUniform1f(uDetail, detailScale);

        quad.position(0);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, quad);
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
            throw new IllegalStateException("Depth rain program link failed: " + log);
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
            throw new IllegalStateException("Depth rain shader compile failed: " + log);
        }
        return shader;
    }
}
