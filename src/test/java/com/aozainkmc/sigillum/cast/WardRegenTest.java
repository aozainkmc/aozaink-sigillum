package com.aozainkmc.sigillum.cast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WardRegenTest {

    @Test
    void emptyShieldFillsInTenRefreshes() {
        float shield = 0.0f;
        int refreshes = 0;
        while (shield < 30.0f && refreshes < 100) {
            shield += SigillumShieldManager.regenStep(shield, 30.0f);
            refreshes++;
        }
        assertEquals(10, refreshes);
        assertEquals(30.0f, shield, 1.0e-4f);
    }

    @Test
    void neverOvershootsTheWardShield() {
        assertEquals(1.0f, SigillumShieldManager.regenStep(29.0f, 30.0f), 1.0e-6f);
    }

    @Test
    void fullOrLargerShieldGetsNothing() {
        assertEquals(0.0f, SigillumShieldManager.regenStep(30.0f, 30.0f));
        assertEquals(0.0f, SigillumShieldManager.regenStep(50.0f, 30.0f));
    }

    @Test
    void damagePausesRegenForFiveSeconds() {
        assertTrue(SigillumShieldManager.regenPaused(1000L, 1000L));
        assertTrue(SigillumShieldManager.regenPaused(1000L, 1099L));
        assertFalse(SigillumShieldManager.regenPaused(1000L, 1100L));
        assertFalse(SigillumShieldManager.regenPaused(null, 1000L));
    }
}
