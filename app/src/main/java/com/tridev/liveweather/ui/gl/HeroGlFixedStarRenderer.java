package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Real-time celestial star field shared by the app Hero and Live Wallpaper.
 *
 * Reality R2-R7 keeps the restrained atmospheric scintillation introduced by the
 * previous polish, but moves the field off fixed screen coordinates. A compact
 * bright-star catalogue plus deterministic faint celestial background is stored
 * as right ascension / declination and projected from observer latitude + local
 * sidereal time on the GPU. Resolved star visibility remains the single authority
 * for whether stars are visible; clouds drawn afterwards provide local occlusion.
 */
public final class HeroGlFixedStarRenderer {

    private static final int BACKGROUND_COUNT = 112;
    private static final int FLOATS_PER_STAR = 5; // RA, Dec, brightness, size, warm/cool mix

    private static final float[] BRIGHT_CATALOG = {
            // RA hours, Dec degrees, brightness, size, color mix (0 cool .. 1 warm)
            6.7525f, -16.7161f, 1.00f, 3.10f, 0.34f, // Sirius
            6.3992f, -52.6957f, 0.92f, 2.92f, 0.66f, // Canopus
            14.2610f, 19.1824f, 0.90f, 2.86f, 0.92f, // Arcturus
            18.6156f, 38.7837f, 0.89f, 2.84f, 0.10f, // Vega
            5.2782f, 45.9980f, 0.86f, 2.76f, 0.70f, // Capella
            5.2423f, -8.2016f, 0.84f, 2.72f, 0.06f, // Rigel
            7.6550f, 5.2250f, 0.80f, 2.62f, 0.48f, // Procyon
            1.6286f, -57.2368f, 0.79f, 2.58f, 0.10f, // Achernar
            5.9195f, 7.4071f, 0.78f, 2.56f, 0.98f, // Betelgeuse
            14.0637f, -60.3730f, 0.76f, 2.50f, 0.12f, // Hadar
            19.8464f, 8.8683f, 0.75f, 2.48f, 0.28f, // Altair
            12.4433f, -63.0991f, 0.74f, 2.46f, 0.10f, // Acrux
            4.5987f, 16.5093f, 0.73f, 2.44f, 0.96f, // Aldebaran
            13.4199f, -11.1613f, 0.72f, 2.42f, 0.05f, // Spica
            16.4901f, -26.4320f, 0.71f, 2.40f, 1.00f, // Antares
            7.7553f, 28.0262f, 0.69f, 2.36f, 0.76f, // Pollux
            22.9608f, -29.6222f, 0.68f, 2.34f, 0.22f, // Fomalhaut
            20.6905f, 45.2803f, 0.67f, 2.32f, 0.12f, // Deneb
            10.1395f, 11.9672f, 0.66f, 2.30f, 0.18f  // Regulus
    };

    private static final int CATALOG_COUNT = BRIGHT_CATALOG.length / FLOATS_PER_STAR;
    private static final int STAR_COUNT = BACKGROUND_COUNT + CATALOG_COUNT;

    private static final String VERTEX_SHADER = String.join("\n",
            "attribute vec4 aStar;",
            "attribute float aColorMix;",
            "uniform float uStarVis;",
            "uniform float uPixelScale;",
            "uniform float uLatitude;",
            "uniform float uSidereal;",
            "uniform float uParallax;",
            "uniform float uTime;",
            "varying float vAlpha;",
            "varying float vColorMix;",
            "varying float vBrightness;",
            "const float TAU=6.28318530718;",
            "void main(){",
            "  float ra=aStar.x;float dec=aStar.y;float h=uSidereal-ra;",
            "  float sinLat=sin(uLatitude);float cosLat=cos(uLatitude);",
            "  float sinDec=sin(dec);float cosDec=cos(dec);",
            "  float sinAlt=clamp(sinDec*sinLat+cosDec*cosLat*cos(h),-1.0,1.0);",
            "  float alt=asin(sinAlt);float cosAlt=max(0.001,cos(alt));",
            "  float sinAz=-cosDec*sin(h)/cosAlt;",
            "  float cosAz=(sinDec-sinAlt*sinLat)/max(0.001,cosAlt*cosLat);",
            "  float az=atan(sinAz,cosAz);if(az<0.0){az+=TAU;}",
            "  float x=fract(az/TAU+(uParallax-0.5)*0.026);",
            "  float altDeg=alt*57.2957795131;float mapped=clamp(altDeg,-7.0,90.0);",
            "  float y=0.86-((mapped+7.0)/97.0)*0.77;",
            "  gl_Position=vec4(x*2.0-1.0,1.0-y*2.0,0.0,1.0);",
            "  float horizonFade=smoothstep(-0.025,0.070,alt);",
            "  float phase=ra*7.31+dec*13.17+aStar.z*5.7;",
            "  float nearHorizon=1.0-smoothstep(0.10,0.72,max(0.0,sinAlt));",
            "  float twinkleAmp=(0.012+nearHorizon*0.050)*(1.0-aStar.z*0.32);",
            "  float scint=1.0+sin(uTime*(0.55+fract(abs(phase))*0.52)+phase)*twinkleAmp;",
            "  vAlpha=clamp(aStar.z*uStarVis*horizonFade*scint,0.0,1.0);",
            "  gl_PointSize=clamp(aStar.w*uPixelScale*2.0*(0.96+0.04*scint),2.0,9.6);",
            "  vColorMix=clamp(aColorMix,0.0,1.0);vBrightness=aStar.z;",
            "}"
    );

    private static final String FRAGMENT_SHADER = String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH",
            "precision highp float;",
            "#else",
            "precision mediump float;",
            "#endif",
            "varying float vAlpha;",
            "varying float vColorMix;",
            "varying float vBrightness;",
            "void main(){",
            "  vec2 q=gl_PointCoord-0.5;float d=length(q);",
            "  float core=1.0-smoothstep(0.10,0.34,d);",
            "  float halo=1.0-smoothstep(0.18,0.50,d);",
            "  float crossX=(1.0-smoothstep(0.012,0.16,abs(q.x)))*(1.0-smoothstep(0.15,0.48,abs(q.y)));",
            "  float crossY=(1.0-smoothstep(0.012,0.16,abs(q.y)))*(1.0-smoothstep(0.15,0.48,abs(q.x)));",
            "  float sparkle=(crossX+crossY)*0.5*vBrightness;",
            "  float alpha=clamp((core*0.74+halo*0.21+sparkle*0.05)*vAlpha,0.0,0.92);",
            "  vec3 cool=vec3(0.70,0.82,1.0);vec3 warm=vec3(1.0,0.79,0.60);",
            "  vec3 color=mix(cool,warm,vColorMix);color=mix(color,vec3(1.0),0.38+vBrightness*0.24);",
            "  gl_FragColor=vec4(clamp(color,0.0,1.0),alpha);",
            "}"
    );

    private final FloatBuffer starBuffer;
    private int program;
    private int aStar;
    private int aColorMix;
    private int uStarVis;
    private int uPixelScale;
    private int uLatitude;
    private int uSidereal;
    private int uParallax;
    private int uTime;
    private float pixelScale = 1f;
    private long startNanos;

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    public HeroGlFixedStarRenderer() {
        float[] stars = new float[STAR_COUNT * FLOATS_PER_STAR];
        int cursor = 0;

        int seed = 0x4C495645;
        for (int i = 0; i < BACKGROUND_COUNT; i++) {
            seed = next(seed);
            float uRa = ((seed >>> 8) & 0x00FFFFFF) / 16777215f;
            seed = next(seed);
            float uDec = ((seed >>> 8) & 0x00FFFFFF) / 16777215f;
            seed = next(seed);
            float uBrightness = ((seed >>> 8) & 0x00FFFFFF) / 16777215f;
            seed = next(seed);
            float uSize = ((seed >>> 8) & 0x00FFFFFF) / 16777215f;
            seed = next(seed);
            float uColor = ((seed >>> 8) & 0x00FFFFFF) / 16777215f;

            float ra = uRa * (float) (Math.PI * 2d);
            float sinDec = uDec * 2f - 1f;
            float dec = (float) Math.asin(Math.max(-1f, Math.min(1f, sinDec)));
            float brightness = 0.20f + uBrightness * 0.40f;
            float size = 1.10f + uSize * 0.82f;
            float colorMix = 0.18f + uColor * 0.64f;

            stars[cursor++] = ra;
            stars[cursor++] = dec;
            stars[cursor++] = brightness;
            stars[cursor++] = size;
            stars[cursor++] = colorMix;
        }

        for (int i = 0; i < BRIGHT_CATALOG.length; i += FLOATS_PER_STAR) {
            float raHours = BRIGHT_CATALOG[i];
            float decDegrees = BRIGHT_CATALOG[i + 1];
            stars[cursor++] = (float) Math.toRadians(raHours * 15f);
            stars[cursor++] = (float) Math.toRadians(decDegrees);
            stars[cursor++] = BRIGHT_CATALOG[i + 2];
            stars[cursor++] = BRIGHT_CATALOG[i + 3];
            stars[cursor++] = BRIGHT_CATALOG[i + 4];
        }

        ByteBuffer bytes = ByteBuffer.allocateDirect(stars.length * 4).order(ByteOrder.nativeOrder());
        starBuffer = bytes.asFloatBuffer();
        starBuffer.put(stars).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void onSurfaceCreated() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        aStar = GLES20.glGetAttribLocation(program, "aStar");
        aColorMix = GLES20.glGetAttribLocation(program, "aColorMix");
        uStarVis = GLES20.glGetUniformLocation(program, "uStarVis");
        uPixelScale = GLES20.glGetUniformLocation(program, "uPixelScale");
        uLatitude = GLES20.glGetUniformLocation(program, "uLatitude");
        uSidereal = GLES20.glGetUniformLocation(program, "uSidereal");
        uParallax = GLES20.glGetUniformLocation(program, "uParallax");
        uTime = GLES20.glGetUniformLocation(program, "uTime");
        startNanos = System.nanoTime();
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
        pixelScale = Math.max(0.78f, Math.min(1.65f, Math.max(1, height) / 800f));
    }

    public void drawFrame() {
        GlSceneSnapshot state = snapshot;
        if (program == 0 || state == null || state.starVisibility <= 0.001f) return;

        float resolved = Math.max(0f, Math.min(1f, state.starVisibility));
        float visibility = resolved <= 0f
                ? 0f
                : Math.max(0f, Math.min(1f, (float) Math.pow(resolved, 0.92d)));
        if (visibility <= 0.001f) return;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform1f(uStarVis, visibility);
        GLES20.glUniform1f(uPixelScale, pixelScale);
        GLES20.glUniform1f(uLatitude, state.observerLatitudeRadians);
        GLES20.glUniform1f(uSidereal, state.localSiderealRadians);
        GLES20.glUniform1f(uParallax, state.parallax);
        GLES20.glUniform1f(uTime, (System.nanoTime() - startNanos) / 1_000_000_000f);

        int stride = FLOATS_PER_STAR * 4;
        starBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aStar);
        GLES20.glVertexAttribPointer(aStar, 4, GLES20.GL_FLOAT, false, stride, starBuffer);

        starBuffer.position(4);
        GLES20.glEnableVertexAttribArray(aColorMix);
        GLES20.glVertexAttribPointer(aColorMix, 1, GLES20.GL_FLOAT, false, stride, starBuffer);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, STAR_COUNT);
        GLES20.glDisableVertexAttribArray(aStar);
        GLES20.glDisableVertexAttribArray(aColorMix);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }

    private static int next(int value) {
        return value * 1664525 + 1013904223;
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
            throw new IllegalStateException("OpenGL sidereal star program link failed: " + log);
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
            throw new IllegalStateException("OpenGL sidereal star shader compile failed: " + log);
        }
        return shader;
    }
}
