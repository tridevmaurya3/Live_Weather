package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

public final class HeroGlWorldShaderContractTest {

    @Test
    public void fragmentShaderKeepsStage15VisibilityUniformContract() throws Exception {
        String source = readPrivateStaticString("FS");

        assertUniformUsed(source, "uFarTransmission");
        assertUniformUsed(source, "uMidTransmission");
        assertUniformUsed(source, "uNearTransmission");
        assertUniformUsed(source, "uMicroVisibility");

        HeroGlAnalyticWorldRenderer.class.getDeclaredField("uFarTransmission");
        HeroGlAnalyticWorldRenderer.class.getDeclaredField("uMidTransmission");
        HeroGlAnalyticWorldRenderer.class.getDeclaredField("uNearTransmission");
        HeroGlAnalyticWorldRenderer.class.getDeclaredField("uMicroVisibility");
    }

    @Test
    public void shaderSourcesRemainStructurallyBalanced() throws Exception {
        String vertex = readPrivateStaticString("VS");
        String fragment = readPrivateStaticString("FS");

        assertTrue(vertex.contains("void main()"));
        assertTrue(fragment.contains("void main()"));
        assertTrue(fragment.contains("gl_FragColor"));
        assertEquals(count(vertex, '{'), count(vertex, '}'));
        assertEquals(count(fragment, '{'), count(fragment, '}'));
    }

    @Test
    public void lowVisibilityCannotRemoveForegroundTransmissionFloor() throws Exception {
        String fragment = readPrivateStaticString("FS");

        assertTrue(fragment.contains("clamp(uFarTransmission,0.08,1.0)"));
        assertTrue(fragment.contains("clamp(uMidTransmission,0.25,1.0)"));
        assertTrue(fragment.contains("clamp(uNearTransmission,0.62,1.0)"));
        assertTrue(fragment.contains("clamp(uMicroVisibility,0.34,1.0)"));
    }

    private static void assertUniformUsed(String source, String uniform) {
        assertTrue(source.contains("uniform float " + uniform + ";"));
        assertTrue(count(source, uniform) >= 2);
    }

    private static String readPrivateStaticString(String name) throws Exception {
        Field field = HeroGlAnalyticWorldRenderer.class.getDeclaredField(name);
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

    private static int count(String value, String target) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }
}
