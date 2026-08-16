package com.tridev.liveweather.ui.sky;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.core.performance.CinematicPerformanceGovernor;
import com.tridev.liveweather.data.local.PerformancePreferences;
import com.tridev.liveweather.data.local.WallpaperPreferences;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.SkyRealityEngine;
import com.tridev.liveweather.domain.SkyRealityState;
import com.tridev.liveweather.ui.gl.GlRealityAdapter;
import com.tridev.liveweather.ui.gl.GlSceneSnapshot;
import com.tridev.liveweather.ui.gl.HeroGlPipeline;

/**
 * In-app live weather surface backed by the same OpenGL pipeline as the system
 * Live Wallpaper. Hidden views draw zero frames. The cinematic governor changes
 * only frame pacing and secondary shader detail; resolved weather truth is never
 * reduced for performance.
 */
public final class LiveSkyView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private static final long REALITY_REFRESH_MILLIS=30_000L;
    private static final long PERFORMANCE_REFRESH_MILLIS=15_000L;

    private static final Object SHARED_LOCK=new Object();
    @Nullable private static WeatherResponse sharedWeather;
    @Nullable private static AirQualityResponse sharedAirQuality;
    @Nullable private static WallpaperPreferences.Options sharedOptions;
    private static double sharedLatitude=Double.NaN;
    private static double sharedLongitude=Double.NaN;
    private static long sharedVersion=1L;

    private final Context appContext;
    private final TextureView textureView;
    private final HeroGlPipeline pipeline=new HeroGlPipeline();
    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final PerformancePreferences performancePreferences;

    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext=EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface=EGL14.EGL_NO_SURFACE;
    private EGLConfig eglConfig;

    private int surfaceWidth=1;
    private int surfaceHeight=1;
    private boolean attached;
    private boolean visible;
    private volatile boolean released;
    private boolean pipelineCreated;
    private long lastRealityRefresh;
    private long lastPerformanceRefresh;
    private long frameIntervalMillis=33L;
    private long seenSharedVersion=-1L;

    @Nullable private volatile SkyRealityState lastSkyState;

    private final Runnable renderRunnable=new Runnable(){
        @Override public void run(){
            if(released||!visible||eglSurface==EGL14.EGL_NO_SURFACE)return;
            try{
                refreshPerformanceIfNeeded();
                refreshRealityIfNeeded();
                pipeline.drawFrame();
                if(!EGL14.eglSwapBuffers(display,eglSurface)){
                    detachEglSurface();
                    return;
                }
            }catch(RuntimeException ignored){
                detachEglSurface();
                return;
            }
            if(!released&&visible&&eglSurface!=EGL14.EGL_NO_SURFACE){
                renderHandler.postDelayed(this,frameIntervalMillis);
            }
        }
    };

    public LiveSkyView(Context context){this(context,null);}
    public LiveSkyView(Context context,@Nullable AttributeSet attrs){this(context,attrs,0);}

    public LiveSkyView(Context context,@Nullable AttributeSet attrs,int defStyleAttr){
        super(context,attrs,defStyleAttr);
        appContext=context.getApplicationContext();
        performancePreferences=new PerformancePreferences(appContext);

        synchronized(SHARED_LOCK){
            if(sharedOptions==null)sharedOptions=new WallpaperPreferences(context).load();
        }

        WallpaperPreferences.Options initialOptions;
        synchronized(SHARED_LOCK){initialOptions=sharedOptions;}
        CinematicPerformanceGovernor.Profile initialProfile=CinematicPerformanceGovernor.resolve(
                appContext,
                performancePreferences.loadMode(),
                initialOptions==null||initialOptions.isBatteryAdaptive(),
                CinematicPerformanceGovernor.Surface.APP_HERO
        );
        frameIntervalMillis=initialProfile.frameIntervalMillis;
        pipeline.setPerformanceDetailScale(initialProfile.detailScale);

        setClipToOutline(true);
        setClipChildren(true);

        textureView=new TextureView(context);
        textureView.setOpaque(true);
        textureView.setSurfaceTextureListener(this);
        textureView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(textureView,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));

        renderThread=new HandlerThread("LiveWeather-AppGL",Process.THREAD_PRIORITY_DISPLAY);
        renderThread.start();
        renderHandler=new Handler(renderThread.getLooper());
    }

    public void setWeatherData(@Nullable WeatherResponse weather,double latitude,double longitude){
        synchronized(SHARED_LOCK){
            sharedWeather=weather;
            sharedLatitude=weather==null?Double.NaN:latitude;
            sharedLongitude=weather==null?Double.NaN:longitude;
            sharedVersion++;
        }
        requestRealityRefresh();
    }

    public void setAirQualityData(@Nullable AirQualityResponse airQuality){
        synchronized(SHARED_LOCK){sharedAirQuality=airQuality;sharedVersion++;}
        requestRealityRefresh();
    }

    public void clearWeatherData(){
        synchronized(SHARED_LOCK){sharedWeather=null;sharedLatitude=Double.NaN;sharedLongitude=Double.NaN;sharedVersion++;}
        requestRealityRefresh();
    }

    public void clearAirQualityData(){
        synchronized(SHARED_LOCK){sharedAirQuality=null;sharedVersion++;}
        requestRealityRefresh();
    }

    public void setRenderOptions(@NonNull WallpaperPreferences.Options options){
        synchronized(SHARED_LOCK){sharedOptions=options;sharedVersion++;}
        requestRealityRefresh();
    }

    @Nullable public SkyRealityState getLastState(){return lastSkyState;}

    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;updateVisibilityAndLoop();}

    @Override protected void onDetachedFromWindow(){
        attached=false;visible=false;renderHandler.removeCallbacks(renderRunnable);releaseRendererThread();super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);updateVisibilityAndLoop();}

    @Override protected void onVisibilityChanged(@NonNull View changedView,int visibility){super.onVisibilityChanged(changedView,visibility);updateVisibilityAndLoop();}

    @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface,int width,int height){
        if(released)return;
        surfaceWidth=Math.max(1,width);surfaceHeight=Math.max(1,height);
        renderHandler.post(()->{
            if(released)return;
            createEglSurface(surface,surfaceWidth,surfaceHeight);
            seenSharedVersion=-1L;lastPerformanceRefresh=0L;restartLoopOnRenderThread();
        });
    }

    @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface,int width,int height){
        surfaceWidth=Math.max(1,width);surfaceHeight=Math.max(1,height);
        renderHandler.post(()->{
            if(released||eglSurface==EGL14.EGL_NO_SURFACE)return;
            if(EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext))pipeline.onSurfaceChanged(surfaceWidth,surfaceHeight);
        });
    }

    @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface){
        if(!released){
            renderHandler.post(()->{renderHandler.removeCallbacks(renderRunnable);detachEglSurface();});
        }
        return true;
    }

    @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface){
        // Frames are produced by the dedicated EGL thread.
    }

    private void updateVisibilityAndLoop(){
        if(released)return;
        visible=attached&&getWindowVisibility()==VISIBLE&&getVisibility()==VISIBLE&&isShown();
        renderHandler.post(this::restartLoopOnRenderThread);
    }

    private void requestRealityRefresh(){
        if(released)return;
        renderHandler.post(()->{
            seenSharedVersion=-1L;lastRealityRefresh=0L;lastPerformanceRefresh=0L;restartLoopOnRenderThread();
        });
    }

    private void restartLoopOnRenderThread(){
        renderHandler.removeCallbacks(renderRunnable);
        if(!released&&visible&&eglSurface!=EGL14.EGL_NO_SURFACE)renderHandler.post(renderRunnable);
    }

    private void initializeEgl(){
        if(released||display!=EGL14.EGL_NO_DISPLAY)return;
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(display==EGL14.EGL_NO_DISPLAY)throw new IllegalStateException("Unable to obtain app EGL display");
        int[] versions=new int[2];
        if(!EGL14.eglInitialize(display,versions,0,versions,1))throw new IllegalStateException("Unable to initialize app EGL");

        int[] configAttributes={
                EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                EGL14.EGL_DEPTH_SIZE,0,EGL14.EGL_STENCIL_SIZE,0,EGL14.EGL_NONE
        };
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        if(!EGL14.eglChooseConfig(display,configAttributes,0,configs,0,1,count,0)||count[0]<=0){
            throw new IllegalStateException("No compatible app OpenGL ES 2 EGL config");
        }
        eglConfig=configs[0];
        int[] contextAttributes={EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE};
        eglContext=EGL14.eglCreateContext(display,eglConfig,EGL14.EGL_NO_CONTEXT,contextAttributes,0);
        if(eglContext==null||eglContext==EGL14.EGL_NO_CONTEXT)throw new IllegalStateException("Unable to create app OpenGL ES 2 context");
    }

    private void createEglSurface(@NonNull SurfaceTexture surfaceTexture,int width,int height){
        if(released)return;
        if(display==EGL14.EGL_NO_DISPLAY||eglContext==EGL14.EGL_NO_CONTEXT||eglConfig==null)initializeEgl();
        detachEglSurface();
        int[] surfaceAttributes={EGL14.EGL_NONE};
        eglSurface=EGL14.eglCreateWindowSurface(display,eglConfig,surfaceTexture,surfaceAttributes,0);
        if(eglSurface==null||eglSurface==EGL14.EGL_NO_SURFACE){eglSurface=EGL14.EGL_NO_SURFACE;return;}
        if(!EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext)){detachEglSurface();return;}
        if(!pipelineCreated){pipeline.onSurfaceCreated();pipelineCreated=true;}
        pipeline.onSurfaceChanged(width,height);seenSharedVersion=-1L;lastRealityRefresh=0L;lastPerformanceRefresh=0L;
    }

    private void detachEglSurface(){
        if(display==EGL14.EGL_NO_DISPLAY)return;
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        if(eglSurface!=null&&eglSurface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,eglSurface);
        eglSurface=EGL14.EGL_NO_SURFACE;
    }

    private void refreshPerformanceIfNeeded(){
        long now=System.currentTimeMillis();
        if(lastPerformanceRefresh>0L&&now-lastPerformanceRefresh<PERFORMANCE_REFRESH_MILLIS)return;
        lastPerformanceRefresh=now;

        WallpaperPreferences.Options options;
        synchronized(SHARED_LOCK){options=sharedOptions;}
        CinematicPerformanceGovernor.Profile profile=CinematicPerformanceGovernor.resolve(
                appContext,
                performancePreferences.loadMode(),
                options==null||options.isBatteryAdaptive(),
                CinematicPerformanceGovernor.Surface.APP_HERO
        );
        frameIntervalMillis=profile.frameIntervalMillis;
        pipeline.setPerformanceDetailScale(profile.detailScale);
    }

    private void refreshRealityIfNeeded(){
        long now=System.currentTimeMillis();
        WeatherResponse weather;AirQualityResponse airQuality;WallpaperPreferences.Options options;double latitude;double longitude;long version;
        synchronized(SHARED_LOCK){
            weather=sharedWeather;airQuality=sharedAirQuality;options=sharedOptions;latitude=sharedLatitude;longitude=sharedLongitude;version=sharedVersion;
        }
        if(version==seenSharedVersion&&lastRealityRefresh>0L&&now-lastRealityRefresh<REALITY_REFRESH_MILLIS)return;
        seenSharedVersion=version;lastRealityRefresh=now;
        if(options!=null)pipeline.setOptions(options);
        if(weather==null||Double.isNaN(latitude)||Double.isNaN(longitude)){
            pipeline.setSnapshot(null);lastSkyState=null;return;
        }
        GlSceneSnapshot snapshot=GlRealityAdapter.compose(weather,airQuality,latitude,longitude,now,0.5f);
        pipeline.setSnapshot(snapshot);
        lastSkyState=SkyRealityEngine.calculate(weather,latitude,longitude,now);
    }

    private void releaseRendererThread(){
        if(released)return;
        released=true;
        renderHandler.post(()->{
            renderHandler.removeCallbacksAndMessages(null);
            if(display!=EGL14.EGL_NO_DISPLAY&&eglSurface!=EGL14.EGL_NO_SURFACE&&eglContext!=EGL14.EGL_NO_CONTEXT){
                EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext);
            }
            if(pipelineCreated){pipeline.release();pipelineCreated=false;}
            detachEglSurface();
            if(display!=EGL14.EGL_NO_DISPLAY&&eglContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,eglContext);
            if(display!=EGL14.EGL_NO_DISPLAY)EGL14.eglTerminate(display);
            eglContext=EGL14.EGL_NO_CONTEXT;display=EGL14.EGL_NO_DISPLAY;renderThread.quitSafely();
        });
    }
}
