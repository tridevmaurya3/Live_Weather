package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VegetationMaterialPolicyTest {

    @Test
    public void humidDewCloseAirResolvesMuchMoisterThanHotDryAir() {
        float dry = VegetationMaterialPolicy.resolveAtmosphericMoisture(34d, 25d, 10d);
        float humid = VegetationMaterialPolicy.resolveAtmosphericMoisture(30d, 85d, 27d);

        assertTrue(dry < 0.30f);
        assertTrue(humid > 0.75f);
        assertTrue(humid > dry + 0.45f);
    }

    @Test
    public void missingAirObservationsStayNeutralInsteadOfPretendingDryness() {
        assertEquals(
                0.50f,
                VegetationMaterialPolicy.resolveAtmosphericMoisture(null, null, null),
                0.0001f
        );
    }

    @Test
    public void heatAndLowMoistureCreateDryStress() {
        VegetationMaterialPolicy.Sample sample = new VegetationMaterialPolicy.Sample();
        VegetationMaterialPolicy.resolveInto(
                sample,
                0.18f, 0f, 0f,
                0f, 0f, 0f,
                0.90f
        );

        assertTrue(sample.dryStress > 0.85f);
        assertTrue(sample.vitality < 0.45f);
    }

    @Test
    public void retainedWetSoilPreventsFalseHeatDryness() {
        VegetationMaterialPolicy.Sample sample = new VegetationMaterialPolicy.Sample();
        VegetationMaterialPolicy.resolveInto(
                sample,
                0.40f, 0.80f, 0.75f,
                0f, 0f, 0f,
                0.90f
        );

        assertEquals(0f, sample.dryStress, 0.01f);
        assertTrue(sample.vitality > 0.85f);
    }

    @Test
    public void currentRainCanRestoreMoistureWithoutInventingStoredHistory() {
        VegetationMaterialPolicy.Sample sample = new VegetationMaterialPolicy.Sample();
        VegetationMaterialPolicy.resolveInto(
                sample,
                0.35f, 0.05f, 0.05f,
                0.70f, 0f, 0f,
                0.45f
        );

        assertTrue(sample.effectiveMoisture > 0.75f);
        assertEquals(0f, sample.dryStress, 0.01f);
        assertTrue(sample.vitality > 0.90f);
    }

    @Test
    public void genuineColdCreatesColdStressButNotDryStress() {
        VegetationMaterialPolicy.Sample sample = new VegetationMaterialPolicy.Sample();
        VegetationMaterialPolicy.resolveInto(
                sample,
                0.70f, 0.50f, 0.50f,
                0f, 0f, 0f,
                -0.90f
        );

        assertTrue(sample.coldStress > 0.85f);
        assertEquals(0f, sample.dryStress, 0.0001f);
    }

    @Test
    public void everyMaterialSignalRemainsBounded() {
        VegetationMaterialPolicy.Sample sample = new VegetationMaterialPolicy.Sample();
        VegetationMaterialPolicy.resolveInto(
                sample,
                4f, -4f, 3f,
                8f, -2f, 5f,
                -9f
        );

        assertTrue(sample.effectiveMoisture >= 0f && sample.effectiveMoisture <= 1f);
        assertTrue(sample.vitality >= 0f && sample.vitality <= 1f);
        assertTrue(sample.dryStress >= 0f && sample.dryStress <= 1f);
        assertTrue(sample.coldStress >= 0f && sample.coldStress <= 1f);
    }
}
