package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Layered atmospheric pass for fog, haze, precipitation veil and real thermal ambience.
 *
 * Reality R2-R7 replaces visibly periodic fog bands with deterministic multi-scale
 * noise that drifts with the resolved wind. Weather truth still controls intensity;
 * the noise only changes local density. Secondary atmosphere sampling is governed by
 * the same performance detail tier used by Hero and Live Wallpaper.
 */
public final class HeroGlAtmosphereOverlayRenderer {

    private static final float[] QUAD = {-1f,-1f, 1f,-1f, -1f,1f, 1f,1f};

    private static final String VERTEX_SHADER = String.join("\n",
            "attribute vec2 aPosition;",
            "varying vec2 vUv;",
            "void main(){vUv=aPosition*0.5+0.5;gl_Position=vec4(aPosition,0.0,1.0);}");

    private static final String FRAGMENT_SHADER = String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH","precision highp float;","#else","precision mediump float;","#endif",
            "varying vec2 vUv;",
            "uniform sampler2D uNoise;",
            "uniform vec2 uResolution;uniform vec2 uSunPos;uniform float uSunVis;uniform vec2 uMoonPos;uniform float uMoonVis;uniform float uMoonIllum;",
            "uniform float uCloud;uniform float uRain;uniform float uDrizzle;uniform float uFog;uniform float uStorm;uniform float uHaze;uniform float uSceneLight;uniform float uVisibility;",
            "uniform float uTime;uniform float uWind;uniform float uWindDir;uniform float uThermal;uniform float uDetail;",
            "float sampleNoise(vec2 uv){return texture2D(uNoise,fract(uv)).r;}",
            "void main(){",
            " vec2 p=vec2(vUv.x,1.0-vUv.y);float aspect=uResolution.x/max(1.0,uResolution.y);",
            " float detail=clamp(uDetail,0.5,1.0);float horizon=smoothstep(0.40,0.98,p.y);float lower=smoothstep(0.56,1.0,p.y);",
            " vec2 wind=vec2(sin(uWindDir),-cos(uWindDir));",
            " vec2 uv1=vec2(p.x*0.68,p.y*0.54)+wind*uTime*(0.0025+uWind*0.0060);",
            " float n1=sampleNoise(uv1);float rolling=n1;",
            " if(detail>0.64){vec2 uv2=vec2(p.x*1.31,p.y*1.08)-wind*uTime*(0.0016+uWind*0.0040)+vec2(0.17,0.31);rolling=n1*0.66+sampleNoise(uv2)*0.34;}",
            " if(detail>0.88){vec2 uv3=vec2(p.x*2.42,p.y*1.82)+wind*uTime*(0.0011+uWind*0.0028)+vec2(0.43,0.11);rolling=rolling*0.82+sampleNoise(uv3)*0.18;}",
            " rolling=smoothstep(0.18,0.86,rolling);",
            " float fogBase=uFog*(0.058+0.238*horizon);",
            " float fogBands=uFog*lower*(0.014+0.092*rolling)*(0.78+0.22*(1.0-uVisibility));",
            " float fogVeil=fogBase+fogBands;",
            " float hazeVeil=uHaze*(0.016+0.118*horizon)*(0.90+0.10*uSceneLight);",
            " float precip=max(uRain,uDrizzle*0.55);float rainVeil=precip*(0.010+0.042*horizon);",
            " float distanceLoss=(1.0-uVisibility)*(0.014+0.126*horizon);",
            " float atmospheric=clamp(fogVeil+hazeVeil+rainVeil+distanceLoss,0.0,0.46);",
            " float aerosol=clamp(uFog*0.60+uHaze*0.42+(1.0-uVisibility)*0.34+precip*0.10,0.0,1.0);",
            " float sunLow=smoothstep(0.36,0.86,uSunPos.y)*uSunVis;vec2 sp=(p-uSunPos)*vec2(aspect,1.0);",
            " float sunScatter=exp(-length(sp)*(1.65+0.55*(1.0-aerosol)))*sunLow*horizon*(1.0-uStorm*0.72)*(0.045+aerosol*0.13);",
            " float moonNight=(1.0-uSceneLight)*uMoonVis*uMoonIllum;vec2 mp=(p-uMoonPos)*vec2(aspect,1.0);",
            " float moonScatter=exp(-length(mp)*2.15)*moonNight*(0.012+0.055*horizon)*(0.42+uFog*0.58);",
            " float warm=max(0.0,uThermal);float cold=max(0.0,-uThermal);",
            " float heatField=sampleNoise(vec2(p.x*1.9+uTime*0.018,p.y*2.7-uTime*0.010));",
            " float heatRipple=(0.45+0.55*heatField)*warm*lower*(1.0-uFog)*(1.0-precip)*(0.60+0.40*uSceneLight);",
            " vec3 fogColor=mix(vec3(0.50,0.56,0.60),vec3(0.37,0.43,0.49),1.0-uSceneLight);",
            " vec3 hazeColor=mix(vec3(0.47,0.50,0.51),vec3(0.61,0.53,0.43),uSceneLight*0.42);",
            " hazeColor=mix(hazeColor,vec3(0.68,0.53,0.36),warm*0.10);fogColor=mix(fogColor,vec3(0.58,0.66,0.73),cold*0.08);",
            " vec3 rainColor=mix(vec3(0.38,0.47,0.55),vec3(0.22,0.29,0.37),uStorm);",
            " float fogPart=clamp(fogVeil,0.0,0.36);float hazePart=clamp(hazeVeil+distanceLoss,0.0,0.23);float rainPart=clamp(rainVeil,0.0,0.14);",
            " vec3 color=fogColor*fogPart+hazeColor*hazePart+rainColor*rainPart;float alpha=atmospheric;",
            " color+=vec3(0.82,0.54,0.31)*sunScatter;alpha+=sunScatter*0.42;",
            " color+=vec3(0.29,0.38,0.54)*moonScatter;alpha+=moonScatter*0.52;",
            " float thermalVeil=heatRipple*0.007+cold*horizon*(1.0-uFog)*0.005;",
            " color+=vec3(0.64,0.46,0.28)*heatRipple*0.010+vec3(0.25,0.39,0.55)*cold*horizon*0.005;alpha+=thermalVeil;",
            " float stormFloor=uStorm*lower*(0.011+uCloud*0.018);color+=vec3(0.035,0.050,0.072)*stormFloor;alpha+=stormFloor;",
            " vec2 centered=(p-0.5)*vec2(1.0,0.78);float vignette=smoothstep(0.44,0.70,length(centered))*(0.007+uStorm*0.021+precip*0.007);",
            " color=mix(color,vec3(0.008,0.012,0.020),clamp(vignette*6.6,0.0,0.22));alpha=1.0-(1.0-alpha)*(1.0-vignette);",
            " gl_FragColor=vec4(clamp(color,0.0,1.0),clamp(alpha,0.0,0.48));}");

    private final FloatBuffer quadBuffer;
    private int program;
    private int noiseTexture;
    private int width=1;
    private int height=1;
    private int aPosition;
    private int uNoise;
    private int uResolution;
    private int uSunPos;
    private int uSunVis;
    private int uMoonPos;
    private int uMoonVis;
    private int uMoonIllum;
    private int uCloud;
    private int uRain;
    private int uDrizzle;
    private int uFog;
    private int uStorm;
    private int uHaze;
    private int uSceneLight;
    private int uVisibility;
    private int uTime;
    private int uWind;
    private int uWindDir;
    private int uThermal;
    private int uDetail;
    private long startNanos;
    private volatile float detailScale=1f;
    @Nullable private volatile GlSceneSnapshot snapshot;

    public HeroGlAtmosphereOverlayRenderer(){
        ByteBuffer bytes=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.nativeOrder());
        quadBuffer=bytes.asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot){this.snapshot=snapshot;}
    public void setDetailScale(float value){detailScale=Math.max(0.5f,Math.min(1f,value));}

    public void onSurfaceCreated(){
        program=createProgram(VERTEX_SHADER,FRAGMENT_SHADER);
        noiseTexture=GlDeterministicTextureFactory.createCloudNoiseTexture();
        aPosition=GLES20.glGetAttribLocation(program,"aPosition");
        uNoise=uniform("uNoise");
        uResolution=uniform("uResolution");uSunPos=uniform("uSunPos");uSunVis=uniform("uSunVis");uMoonPos=uniform("uMoonPos");uMoonVis=uniform("uMoonVis");uMoonIllum=uniform("uMoonIllum");
        uCloud=uniform("uCloud");uRain=uniform("uRain");uDrizzle=uniform("uDrizzle");uFog=uniform("uFog");uStorm=uniform("uStorm");uHaze=uniform("uHaze");uSceneLight=uniform("uSceneLight");uVisibility=uniform("uVisibility");
        uTime=uniform("uTime");uWind=uniform("uWind");uWindDir=uniform("uWindDir");uThermal=uniform("uThermal");uDetail=uniform("uDetail");
        startNanos=System.nanoTime();GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void onSurfaceChanged(int width,int height){
        this.width=Math.max(1,width);this.height=Math.max(1,height);GLES20.glViewport(0,0,this.width,this.height);
    }

    public void drawFrame(){
        GlSceneSnapshot s=snapshot;
        if(program==0||noiseTexture==0||s==null)return;
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,noiseTexture);GLES20.glUniform1i(uNoise,0);
        GLES20.glUniform2f(uResolution,width,height);GLES20.glUniform2f(uSunPos,s.sunX,s.sunY);GLES20.glUniform1f(uSunVis,s.sunVisibility);GLES20.glUniform2f(uMoonPos,s.moonX,s.moonY);GLES20.glUniform1f(uMoonVis,s.moonVisibility);GLES20.glUniform1f(uMoonIllum,s.moonIllumination);
        GLES20.glUniform1f(uCloud,s.cloudCover);GLES20.glUniform1f(uRain,s.rainIntensity);GLES20.glUniform1f(uDrizzle,s.drizzleIntensity);GLES20.glUniform1f(uFog,s.fogIntensity);GLES20.glUniform1f(uStorm,s.stormIntensity);GLES20.glUniform1f(uHaze,s.airHazeIntensity);GLES20.glUniform1f(uSceneLight,s.sceneLight);GLES20.glUniform1f(uVisibility,s.visibilityFactor);
        GLES20.glUniform1f(uTime,(System.nanoTime()-startNanos)/1_000_000_000f);GLES20.glUniform1f(uWind,s.windStrength);GLES20.glUniform1f(uWindDir,s.windDirectionRadians);GLES20.glUniform1f(uThermal,s.thermalBias);GLES20.glUniform1f(uDetail,detailScale);
        quadBuffer.position(0);GLES20.glEnableVertexAttribArray(aPosition);GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,quadBuffer);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release(){
        if(noiseTexture!=0){int[] ids={noiseTexture};GLES20.glDeleteTextures(1,ids,0);noiseTexture=0;}
        if(program!=0){GLES20.glDeleteProgram(program);program=0;}
    }

    private int uniform(@NonNull String name){return GLES20.glGetUniformLocation(program,name);}
    private static int createProgram(String vs,String fs){int v=compileShader(GLES20.GL_VERTEX_SHADER,vs),f=compileShader(GLES20.GL_FRAGMENT_SHADER,fs),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] status=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,status,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(status[0]==0){String log=GLES20.glGetProgramInfoLog(p);GLES20.glDeleteProgram(p);throw new IllegalStateException("OpenGL atmosphere program link failed: "+log);}return p;}
    private static int compileShader(int type,String source){int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);int[] status=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0);if(status[0]==0){String log=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException("OpenGL atmosphere shader compile failed: "+log);}return shader;}
}
