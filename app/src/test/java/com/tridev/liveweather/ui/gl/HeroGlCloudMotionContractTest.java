package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

/** Regression contract for the post-roadmap cloud-shape and motion repair. */
public final class HeroGlCloudMotionContractTest {

    @Test
    public void cloudCentersUseContinuousWindAdvectionWithoutPendulumOffsets() throws Exception {
        String source = fragmentShader();

        assertTrue(source.contains("float advection=direction*uTime*speed"));
        assertTrue(source.contains("float farDrift=advection*0.32"));
        assertTrue(source.contains("float midDrift=advection*0.72"));
        assertTrue(source.contains("float nearDrift=advection*1.18"));

        assertFalse(source.contains("float cross="));
        assertFalse(source.contains("float lift="));
        assertFalse(source.contains("breatheA"));
        assertFalse(source.contains("breatheB"));
    }

    @Test
    public void cloudLifeComesFromInternalEvolutionNotCenterReversal() throws Exception {
        String source = fragmentShader();

        assertTrue(source.contains("float evolution=uTime*(0.020+uWind*0.014)+cell*0.73"));
        assertTrue(source.contains("vec2 warp=vec2(sin(q.y*6.2+evolution)"));
        assertTrue(source.contains("float sheetFlow=direction*uTime*(0.004+uWind*0.006)"));
    }

    @Test
    public void repairedCloudMassesStayCompactInsteadOfOldWideStrips() throws Exception {
        String source = fragmentShader();

        assertTrue(source.contains("vec2(1.56,0.645)"));
        assertTrue(source.contains("vec2(1.74,0.915)"));
        assertTrue(source.contains("vec2(1.80,1.050)"));
        assertTrue(source.contains("float breakup=0.91+0.09*sin"));

        assertFalse(source.contains("vec2(0.82,0.315)"));
        assertFalse(source.contains("vec2(0.88,0.365)"));
    }

    @Test
    public void cloudShaderRemainsStructurallyBalanced() throws Exception {
        String source = fragmentShader();
        assertTrue(source.contains("void main()"));
        assertTrue(source.contains("gl_FragColor"));
        assertEquals(count(source, '{'), count(source, '}'));
    }

    private static String fragmentShader() throws Exception {
        Field field = HeroGlTextureCloudRenderer.class.getDeclaredField("FS");
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static int count(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) count++;
        }
        return count;
    }
}
