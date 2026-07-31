package com.aozainkmc.sigillum.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SoulWardHandlerTest {

    @Test
    void inferiorGradeGetsLowestTier() {
        assertEquals(0, SoulWardHandler.tierOf(0.55f));
        assertEquals(40, SoulWardHandler.windowTicksOf(0));
    }

    @Test
    void fineGradeGetsMiddleTier() {
        assertEquals(1, SoulWardHandler.tierOf(0.8f));
        assertEquals(600, SoulWardHandler.windowTicksOf(1));
    }

    @Test
    void exquisiteGradeGetsTopTier() {
        assertEquals(2, SoulWardHandler.tierOf(1.0f));
        assertEquals(2400, SoulWardHandler.windowTicksOf(2));
    }

    @Test
    void strongModifierCanPromoteTier() {
        assertEquals(2, SoulWardHandler.tierOf(0.8f * 2.0f));
        assertEquals(2, SoulWardHandler.tierOf(0.55f * 2.0f));
    }

    @Test
    void onlyTopTierLocksAfterTrigger() {
        assertEquals(0, SoulWardHandler.lockTicksOf(0));
        assertEquals(0, SoulWardHandler.lockTicksOf(1));
        assertEquals(100, SoulWardHandler.lockTicksOf(2));
    }
}
