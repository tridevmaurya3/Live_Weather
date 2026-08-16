package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.local.WallpaperPreferences;

/** Shared GPU composition pipeline for the app Hero and Android Live Wallpaper. */
public final class HeroGlPipeline {

    private final HeroGlSkyCelestialRenderer sceneRenderer=new HeroGlSkyCelestialRenderer();
    private final HeroGlFixedStarRenderer starRenderer=new HeroGlFixedStarRenderer();
    private final HeroGlTextureCloudRenderer cloudRenderer=new HeroGlTextureCloudRenderer();
    private final HeroGlAnalyticWorldRenderer worldRenderer=new HeroGlAnalyticWorldRenderer();
    private final HeroGlAtmosphereOverlayRenderer atmosphereRenderer=new HeroGlAtmosphereOverlayRenderer();
    private final HeroGlPortableStormRenderer stormRenderer=new HeroGlPortableStormRenderer();
    private final HeroGlDepthRainRenderer rainRenderer=new HeroGlDepthRainRenderer();
    private final HeroGlSnowRenderer snowRenderer=new HeroGlSnowRenderer();
    private final HeroGlDiagnostics diagnostics=new HeroGlDiagnostics();

    @Nullable private GlSceneSnapshot fullSnapshot;
    private volatile float performanceDetailScale=1f;

    @NonNull
    private WallpaperPreferences.Options options=new WallpaperPreferences.Options(true,true,true,true,true,true,true);

    public void onSurfaceCreated(){
        sceneRenderer.onSurfaceCreated();starRenderer.onSurfaceCreated();cloudRenderer.onSurfaceCreated();worldRenderer.onSurfaceCreated();
        atmosphereRenderer.onSurfaceCreated();stormRenderer.onSurfaceCreated();rainRenderer.onSurfaceCreated();snowRenderer.onSurfaceCreated();diagnostics.onSurfaceCreated();
        applyPerformanceDetail();applySnapshot();
    }

    public void onSurfaceChanged(int width,int height){
        sceneRenderer.onSurfaceChanged(width,height);starRenderer.onSurfaceChanged(width,height);cloudRenderer.onSurfaceChanged(width,height);worldRenderer.onSurfaceChanged(width,height);
        atmosphereRenderer.onSurfaceChanged(width,height);stormRenderer.onSurfaceChanged(width,height);rainRenderer.onSurfaceChanged(width,height);snowRenderer.onSurfaceChanged(width,height);diagnostics.onSurfaceChanged(width,height);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot){fullSnapshot=snapshot;diagnostics.setSnapshot(snapshot);applySnapshot();}

    public void setOptions(@NonNull WallpaperPreferences.Options options){this.options=options;diagnostics.setOptions(options);applySnapshot();}

    /**
     * Changes only secondary visual sampling cost. Weather state/intensity is untouched.
     */
    public void setPerformanceDetailScale(float detailScale){
        performanceDetailScale=Math.max(0.5f,Math.min(1f,detailScale));
        applyPerformanceDetail();
    }

    @NonNull public HeroGlDiagnostics.Snapshot captureDiagnostics(){return diagnostics.capture();}
    @NonNull public String buildDiagnosticsReport(){return diagnostics.buildReport();}

    public void drawFrame(){
        sceneRenderer.drawFrame();starRenderer.drawFrame();cloudRenderer.drawFrame();worldRenderer.drawFrame();atmosphereRenderer.drawFrame();stormRenderer.drawFrame();rainRenderer.drawFrame();snowRenderer.drawFrame();
    }

    public void release(){
        sceneRenderer.release();starRenderer.release();cloudRenderer.release();worldRenderer.release();atmosphereRenderer.release();stormRenderer.release();rainRenderer.release();snowRenderer.release();
    }

    private void applyPerformanceDetail(){
        float d=performanceDetailScale;
        cloudRenderer.setDetailScale(d);stormRenderer.setDetailScale(d);rainRenderer.setDetailScale(d);snowRenderer.setDetailScale(d);
    }

    private void applySnapshot(){
        GlSceneSnapshot state=fullSnapshot;
        if(state==null){
            sceneRenderer.setSnapshot(null);starRenderer.setSnapshot(null);cloudRenderer.setSnapshot(null);worldRenderer.setSnapshot(null);atmosphereRenderer.setSnapshot(null);
            stormRenderer.setSnapshot(null);rainRenderer.setSnapshot(null);snowRenderer.setSnapshot(null);return;
        }

        GlSceneSnapshot sceneSnapshot=state.withVisualOptions(false,false,true,options.isSnow(),options.isFog(),false);
        GlSceneSnapshot starSnapshot=state.withVisualOptions(options.isClouds(),true,true,true,options.isFog(),options.isStars());
        GlSceneSnapshot cloudSnapshot=state.withVisualOptions(options.isClouds(),false,true,options.isSnow(),options.isFog(),false);
        GlSceneSnapshot worldSnapshot=state.withVisualOptions(options.isClouds(),options.isRain(),true,options.isSnow(),options.isFog(),options.isStars());
        GlSceneSnapshot atmosphereSnapshot=state.withVisualOptions(options.isClouds(),true,true,options.isSnow(),options.isFog(),options.isStars());
        GlSceneSnapshot stormSnapshot=state.withVisualOptions(options.isClouds(),false,true,options.isSnow(),options.isFog(),options.isStars());
        GlSceneSnapshot rainSnapshot=state.withVisualOptions(true,options.isRain(),options.isLightning(),true,true,true);
        GlSceneSnapshot snowSnapshot=state.withVisualOptions(true,false,true,options.isSnow(),true,true);

        sceneRenderer.setSnapshot(sceneSnapshot);starRenderer.setSnapshot(starSnapshot);cloudRenderer.setSnapshot(cloudSnapshot);worldRenderer.setSnapshot(worldSnapshot);
        atmosphereRenderer.setSnapshot(atmosphereSnapshot);stormRenderer.setSnapshot(stormSnapshot);stormRenderer.setElectricalEnabled(options.isLightning());
        rainRenderer.setSnapshot(rainSnapshot);snowRenderer.setSnapshot(snowSnapshot);
    }
}
