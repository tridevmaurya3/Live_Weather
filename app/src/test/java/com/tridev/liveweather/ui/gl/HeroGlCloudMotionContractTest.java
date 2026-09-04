package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

/** Release-lock contracts for the atlas-free volumetric cloud rebuild. */
public final class HeroGlCloudMotionContractTest {

    @Test
    public void sharedPipelineUsesOnlyTheNewVolumetricRenderer() throws Exception {
        Field field = HeroGlPipeline.class.getDeclaredField("cloudRenderer");
        assertEquals(HeroGlVolumetricCloudRenderer.class, field.getType());
    }

    @Test
    public void cloudBodiesAreProceduralCompactMassesWithoutAtlasSampling() throws Exception {
        String source = fragmentShader();
        assertTrue(source.contains("float compactMass("));
        assertTrue(source.contains("float ellipse("));
        assertTrue(source.contains("float fbm("));
        assertTrue(count(source, "body=max(body,ellipse") >= 4);
        assertFalse(source.contains("sampler2D"));
        assertFalse(source.contains("texture2D"));
        assertFalse(source.contains("atlasSample"));
        assertFalse(source.contains("spriteWrapped"));
    }

    @Test
    public void weatherTruthSelectsAllRequiredCloudFamilies() throws Exception {
        String source = fragmentShader();
        assertTrue(source.contains("float fair="));
        assertTrue(source.contains("float scattered="));
        assertTrue(source.contains("float broken="));
        assertTrue(source.contains("float overcast="));
        assertTrue(source.contains("float rainFamily="));
        assertTrue(source.contains("float stormFamily="));
        assertTrue(source.contains("uFarLayer"));
        assertTrue(source.contains("uMidLayer"));
        assertTrue(source.contains("uNearLayer"));
    }

    @Test
    public void centersUseOrderedOneWayWindTravelWithoutPendulumMotion() throws Exception {
        String source = fragmentShader();
        assertTrue(source.contains("float travel=direction*uTime"));
        assertTrue(source.contains("float farTravel=travel*0.34"));
        assertTrue(source.contains("float midTravel=travel*0.72"));
        assertTrue(source.contains("float nearTravel=travel*1.18"));
        assertFalse(source.contains("float cross="));
        assertFalse(source.contains("float lift="));
        assertFalse(source.contains("breatheA"));
        assertFalse(source.contains("breatheB"));
        assertFalse(source.contains("vec2 c0=vec2(sin"));
        assertFalse(source.contains("vec2 c1=vec2(sin"));
        assertFalse(source.contains("vec2 c2=vec2(sin"));
    }

    @Test
    public void evolutionChangesInternalDensityInsteadOfCloudCenterDirection() throws Exception {
        String source = fragmentShader();
        assertTrue(source.contains("float evolution=uTime*"));
        assertTrue(source.contains("float morphA=sin(evolution"));
        assertTrue(source.contains("vec2 evolveShift="));
        assertTrue(source.contains("+evolveShift"));
    }

    @Test
    public void lightingRespondsToSunMoonTwilightAndSevereWeather() throws Exception {
        String source = fragmentShader();
        assertTrue(source.contains("uSunPos"));
        assertTrue(source.contains("uSunAltitude"));
        assertTrue(source.contains("uMoonPos"));
        assertTrue(source.contains("float directional="));
        assertTrue(source.contains("float twilight="));
        assertTrue(source.contains("float underside="));
        assertTrue(source.contains("float weather=clamp(uRain"));
    }

    @Test
    public void adaptiveDetailPreservesPrimaryMassesAndDropsOnlySecondaryMass() throws Exception {
        String source = fragmentShader();
        assertTrue(source.contains("if(uDetail>0.66){f3="));
        assertTrue(source.contains("float f0=wrappedMass"));
        assertTrue(source.contains("float f1=wrappedMass"));
        assertTrue(source.contains("float f2=wrappedMass"));
    }

    @Test
    public void cloudShaderRemainsStructurallyBalanced() throws Exception {
        String source = fragmentShader();
        assertTrue(source.contains("void main()"));
        assertTrue(source.contains("gl_FragColor"));
        assertEquals(count(source, '{'), count(source, '}'));
    }

    private static String fragmentShader() throws Exception {
        Field field = HeroGlVolumetricCloudRenderer.class.getDeclaredField("FS");
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
        int offset = 0;
        while ((offset = value.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
    }
}
