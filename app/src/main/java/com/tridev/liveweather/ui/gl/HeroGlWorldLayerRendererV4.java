package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Device-consistent world pass.
 *
 * Mountain/forest profiles come from a deterministic Java-generated texture,
 * not fragment sin/hash randomness. This keeps the same silhouettes across
 * emulator, Adreno and Mali while retaining parallax and weather lighting.
 */
public final class HeroGlWorldLayerRendererV4 {

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
            "uniform sampler2D uProfile;",
            "uniform float uTime;",
            "uniform float uSunAltitude;",
            "uniform float uMoonVis;",
            "uniform float uMoonIllum;",
            "uniform float uCloud;",
            "uniform float uRain;",
            "uniform float uDrizzle;",
            "uniform float uStorm;",
            "uniform float uFog;",
            "uniform float uHaze;",
            "uniform float uSceneLight;",
            "uniform float uWind;",
            "uniform float uParallax;",
            "",
            "void main(){",
            "  vec2 p=vec2(vUv.x,1.0-vUv.y);",
            "  float night=1.0-smoothstep(-7.0,1.5,uSunAltitude);",
            "  float precip=max(uRain,uDrizzle*0.66);",
            "  float urbanMix=clamp(precip*1.20+uStorm*0.86,0.0,1.0);",
            "  float calmMix=1.0-urbanMix;",
            "  float distanceFade=clamp(1.0-uFog*0.66-uHaze*0.25,0.22,1.0);",
            "  float centered=uParallax-0.5;",
            "",
            "  float farX=fract(p.x+centered*0.014);",
            "  float midX=fract(p.x+0.071+centered*0.026);",
            "  float nearX=fract(p.x+0.143+centered*0.042);",
            "  float forestX=fract(p.x*1.32+0.217+centered*0.055);",
            "",
            "  float farLine=texture2D(uProfile,vec2(farX,0.5)).r;",
            "  float midLine=texture2D(uProfile,vec2(midX,0.5)).g;",
            "  float nearLine=texture2D(uProfile,vec2(nearX,0.5)).b;",
            "  float forestLine=texture2D(uProfile,vec2(forestX,0.5)).a;",
            "",
            "  float farMountain=smoothstep(farLine-0.006,farLine+0.006,p.y);",
            "  float midMountain=smoothstep(midLine-0.006,midLine+0.006,p.y);",
            "  float nearMountain=smoothstep(nearLine-0.005,nearLine+0.005,p.y);",
            "  float forest=smoothstep(forestLine-0.004,forestLine+0.004,p.y);",
            "  forest*=1.0-smoothstep(0.905,0.935,p.y);",
            "",
            "  float cityX=fract(p.x+centered*0.032);",
            "  float cityCell=floor(cityX*26.0);",
            "  float cityLocal=fract(cityX*26.0);",
            "  float sampleX=(cityCell+0.5)/26.0;",
            "  float cityRnd=texture2D(uProfile,vec2(fract(sampleX+0.37),0.5)).r;",
            "  float buildingTop=0.615+cityRnd*0.255;",
            "  float building=step(buildingTop,p.y)*step(cityLocal,0.72);",
            "  building*=1.0-smoothstep(0.900,0.920,p.y);",
            "",
            "  float windowColumn=step(0.20,cityLocal)*step(cityLocal,0.58);",
            "  float windowRows=step(0.20,fract(p.y*74.0))*step(fract(p.y*74.0),0.55);",
            "  float cellPattern=step(1.0,mod(cityCell+floor(p.y*74.0),3.0));",
            "  float windows=windowColumn*windowRows*cellPattern*building*night*urbanMix;",
            "",
            "  float foreground=smoothstep(0.880,0.918,p.y);",
            "  float wet=clamp(precip*0.95+uStorm*0.34,0.0,1.0);",
            "  float water=foreground*calmMix;",
            "  float road=foreground*urbanMix;",
            "  float lunarLift=night*uMoonVis*uMoonIllum*(1.0-uCloud*0.55)*(1.0-uFog*0.65);",
            "  float light=clamp(0.20+uSceneLight*0.78+lunarLift*0.22,0.18,0.96);",
            "",
            "  vec3 farColor=mix(vec3(0.040,0.070,0.108),vec3(0.185,0.245,0.300),light);",
            "  vec3 midColor=mix(vec3(0.025,0.050,0.078),vec3(0.125,0.180,0.225),light);",
            "  vec3 nearColor=mix(vec3(0.014,0.032,0.050),vec3(0.075,0.120,0.155),light);",
            "  vec3 forestColor=mix(vec3(0.008,0.022,0.030),vec3(0.040,0.082,0.078),light);",
            "  vec3 cityColor=mix(vec3(0.020,0.030,0.044),vec3(0.070,0.095,0.120),light);",
            "",
            "  vec3 color=vec3(0.0);",
            "  float alpha=0.0;",
            "  float farMask=farMountain*calmMix*0.64*distanceFade;",
            "  color=mix(color,farColor,farMask); alpha=max(alpha,farMask);",
            "  float midMask=midMountain*calmMix*0.80*distanceFade;",
            "  color=mix(color,midColor,midMask); alpha=max(alpha,midMask);",
            "  float nearMask=nearMountain*calmMix*0.93;",
            "  color=mix(color,nearColor,nearMask); alpha=max(alpha,nearMask);",
            "  float forestMask=forest*calmMix*0.88;",
            "  color=mix(color,forestColor,forestMask); alpha=max(alpha,forestMask);",
            "",
            "  float cityMask=building*urbanMix*0.95;",
            "  color=mix(color,cityColor,cityMask); alpha=max(alpha,cityMask);",
            "  color+=vec3(0.92,0.67,0.36)*windows*0.56;",
            "  alpha=max(alpha,windows*0.62);",
            "",
            "  vec3 waterColor=mix(vec3(0.009,0.020,0.032),vec3(0.034,0.068,0.096),light*0.76);",
            "  color=mix(color,waterColor,water*0.97); alpha=max(alpha,water*0.97);",
            "  float ripple=0.5+0.5*sin(p.y*220.0+uTime*0.11);",
            "  float rippleBand=smoothstep(0.89,0.93,p.y)*ripple*calmMix*lunarLift;",
            "  color+=vec3(0.20,0.31,0.46)*rippleBand*0.060;",
            "",
            "  vec3 roadColor=mix(vec3(0.012,0.020,0.030),vec3(0.035,0.050,0.066),light*0.50);",
            "  color=mix(color,roadColor,road*0.98); alpha=max(alpha,road*0.98);",
            "  float reflBands=0.5+0.5*sin(p.x*155.0+p.y*57.0-uTime*(0.18+uWind*0.12));",
            "  color+=mix(vec3(0.18,0.33,0.52),vec3(0.76,0.50,0.28),step(0.5,fract(p.x*9.0)))",
            "      *reflBands*road*wet*night*0.055;",
            "",
            "  float horizonMist=smoothstep(0.56,0.73,p.y)*(1.0-smoothstep(0.82,0.91,p.y));",
            "  horizonMist*=clamp(uFog*0.34+uHaze*0.12+precip*0.08,0.0,0.27);",
            "  color=mix(color,vec3(0.26,0.34,0.41),horizonMist);",
            "  alpha=max(alpha,horizonMist*0.76);",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.99));",
            "}"
    );

    private final FloatBuffer quadBuffer;
    private int program;
    private int profileTexture;
    private int aPosition;
    private int uProfile;
    private int uTime;
    private int uSunAltitude;
    private int uMoonVis;
    private int uMoonIllum;
    private int uCloud;
    private int uRain;
    private int uDrizzle;
    private int uStorm;
    private int uFog;
    private int uHaze;
    private int uSceneLight;
    private int uWind;
    private int uParallax;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlWorldLayerRendererV4() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder());
        quadBuffer = bytes.asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void onSurfaceCreated() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        profileTexture = GlDeterministicTextureFactory.createWorldProfileTexture();
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uProfile = uniform("uProfile");
        uTime = uniform("uTime");
        uSunAltitude = uniform("uSunAltitude");
        uMoonVis = uniform("uMoonVis");
        uMoonIllum = uniform("uMoonIllum");
        uCloud = uniform("uCloud");
        uRain = uniform("uRain");
        uDrizzle = uniform("uDrizzle");
        uStorm = uniform("uStorm");
        uFog = uniform("uFog");
        uHaze = uniform("uHaze");
        uSceneLight = uniform("uSceneLight");
        uWind = uniform("uWind");
        uParallax = uniform("uParallax");
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
    }

    public void drawFrame() {
        GlSceneSnapshot state = snapshot;
        if (program == 0 || profileTexture == 0 || state == null) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, profileTexture);
        GLES20.glUniform1i(uProfile, 0);
        GLES20.glUniform1f(uTime, (System.nanoTime() / 1_000_000_000f) % 4096f);
        GLES20.glUniform1f(uSunAltitude, state.sunAltitude);
        GLES20.glUniform1f(uMoonVis, state.moonVisibility);
        GLES20.glUniform1f(uMoonIllum, state.moonIllumination);
        GLES20.glUniform1f(uCloud, state.cloudCover);
        GLES20.glUniform1f(uRain, state.rainIntensity);
        GLES20.glUniform1f(uDrizzle, state.drizzleIntensity);
        GLES20.glUniform1f(uStorm, state.stormIntensity);
        GLES20.glUniform1f(uFog, state.fogIntensity);
        GLES20.glUniform1f(uHaze, state.airHazeIntensity);
        GLES20.glUniform1f(uSceneLight, state.sceneLight);
        GLES20.glUniform1f(uWind, state.windStrength);
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
        if (profileTexture != 0) {
            int[] ids = {profileTexture};
            GLES20.glDeleteTextures(1, ids, 0);
            profileTexture = 0;
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
            throw new IllegalStateException("OpenGL portable world program link failed: " + log);
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
            throw new IllegalStateException("OpenGL portable world shader compile failed: " + log);
        }
        return shader;
    }
}
