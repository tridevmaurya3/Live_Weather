package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Physical-device / emulator smoke test for the shared App Hero + Live Wallpaper GL pipeline.
 *
 * This test creates a real EGL ES 2.0 context, then asks every active renderer in HeroGlPipeline
 * to create/compile/link its shaders. It is intentionally tiny (64x64 pbuffer) and does not need
 * network, location, permissions or a weather response.
 */
@RunWith(AndroidJUnit4.class)
public final class HeroGlPipelineDeviceSmokeTest {

    @Test
    public void sharedPipelineCompilesAllRenderersOnRealGlesContext() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        assertTrue(display != EGL14.EGL_NO_DISPLAY);

        int[] version = new int[2];
        assertTrue(EGL14.eglInitialize(display, version, 0, version, 1));

        int[] configAttributes = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] configCount = new int[1];
        assertTrue(EGL14.eglChooseConfig(
                display,
                configAttributes,
                0,
                configs,
                0,
                configs.length,
                configCount,
                0
        ));
        assertTrue(configCount[0] > 0);
        assertNotNull(configs[0]);

        int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        EGLContext context = EGL14.eglCreateContext(
                display,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                contextAttributes,
                0
        );
        assertTrue(context != EGL14.EGL_NO_CONTEXT);

        int[] surfaceAttributes = {
                EGL14.EGL_WIDTH, 64,
                EGL14.EGL_HEIGHT, 64,
                EGL14.EGL_NONE
        };
        EGLSurface surface = EGL14.eglCreatePbufferSurface(
                display,
                configs[0],
                surfaceAttributes,
                0
        );
        assertTrue(surface != EGL14.EGL_NO_SURFACE);
        assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context));

        HeroGlPipeline pipeline = new HeroGlPipeline();
        try {
            pipeline.onSurfaceCreated();
            pipeline.onSurfaceChanged(64, 64);
            pipeline.setPerformanceDetailScale(0.58f);
            pipeline.drawFrame();
            GLES20.glFinish();

            assertEquals("none", pipeline.getRendererFaultSummary());
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError());
            String report = pipeline.buildDiagnosticsReport();
            assertTrue(report.contains("gpu="));
            assertTrue(report.contains("gl="));
        } finally {
            pipeline.release();
            EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
            );
            EGL14.eglDestroySurface(display, surface);
            EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
        }
    }
}
