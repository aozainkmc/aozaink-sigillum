package com.aozainkmc.sigillum.cast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkillCastLinkedComboTest {

    @Test
    void drainSuppressHasExactSpec() {
        SkillCast.LinkedComboSpec spec = SkillCast.linkedComboSpec("镇", "吸");
        assertNotNull(spec);
        assertEquals(SkillCast.LinkedComboKind.DRAIN, spec.kind());
        assertTrue(spec.needsTarget());
    }

    @Test
    void drainSoulHasExactSpec() {
        SkillCast.LinkedComboSpec spec = SkillCast.linkedComboSpec("魄", "吸");
        assertNotNull(spec);
        assertEquals(SkillCast.LinkedComboKind.DRAIN, spec.kind());
        assertTrue(spec.needsTarget());
    }

    @Test
    void soulRepelComboNeedsNoTarget() {
        SkillCast.LinkedComboSpec spec = SkillCast.linkedComboSpec("退", "魄");
        assertNotNull(spec);
        assertEquals(SkillCast.LinkedComboKind.SOUL, spec.kind());
        assertFalse(spec.needsTarget());
    }

    @Test
    void soulLureComboNeedsNoTarget() {
        SkillCast.LinkedComboSpec spec = SkillCast.linkedComboSpec("引", "魄");
        assertNotNull(spec);
        assertEquals(SkillCast.LinkedComboKind.SOUL, spec.kind());
        assertFalse(spec.needsTarget());
    }

    @Test
    void soulSuppressPairNeedsTarget() {
        SkillCast.LinkedComboSpec spec = SkillCast.linkedComboSpec("镇", "魄");
        assertNotNull(spec);
        assertEquals(SkillCast.LinkedComboKind.SOUL, spec.kind());
        assertTrue(spec.needsTarget());
    }

    @Test
    void suppressSealComboNeedsTarget() {
        SkillCast.LinkedComboSpec spec = SkillCast.linkedComboSpec("镇", "封");
        assertNotNull(spec);
        assertEquals(SkillCast.LinkedComboKind.SUPPRESS_SEAL, spec.kind());
        assertTrue(spec.needsTarget());
    }

    @Test
    void suppressFireComboNeedsTarget() {
        SkillCast.LinkedComboSpec spec = SkillCast.linkedComboSpec("火", "镇");
        assertNotNull(spec);
        assertEquals(SkillCast.LinkedComboKind.SUPPRESS_FIRE, spec.kind());
        assertTrue(spec.needsTarget());
    }

    @Test
    void suppressLightComboNeedsTarget() {
        SkillCast.LinkedComboSpec spec = SkillCast.linkedComboSpec("明", "镇");
        assertNotNull(spec);
        assertEquals(SkillCast.LinkedComboKind.SUPPRESS_LIGHT, spec.kind());
        assertTrue(spec.needsTarget());
    }

    @Test
    void identicalGlyphsAreNotLinkedCombos() {
        assertNull(SkillCast.linkedComboSpec("镇", "镇"));
    }

    @Test
    void modifiersAreNotLinkedCombos() {
        assertNull(SkillCast.linkedComboSpec("镇", "强"));
        assertNull(SkillCast.linkedComboSpec("吸", "广"));
    }

    @Test
    void fireThunderHasExactSpec() {
        SkillCast.LinkedComboSpec spec = SkillCast.linkedComboSpec("火", "雷");
        assertNotNull(spec);
        assertEquals(SkillCast.LinkedComboKind.FIRE_PAIR, spec.kind());
        assertTrue(spec.needsTarget());
    }

    @Test
    void selfOnlyPairsNeedNoTarget() {
        assertFalse(SkillCast.linkedComboSpec("护", "净").needsTarget());
        assertFalse(SkillCast.linkedComboSpec("护", "明").needsTarget());
        assertFalse(SkillCast.linkedComboSpec("净", "明").needsTarget());
    }

    @Test
    void lightHasSelfEffectAndOptionalTargetEffect() {
        assertTrue(SkillCast.hasSelfEffect("明"));
        assertTrue(SkillCast.hasTargetEffect("明"));
        assertEquals(SkillCast.TargetRequirement.OPTIONAL, SkillCast.targetRequirement("明"));
        assertFalse(SkillCast.requiresTarget("明"));
    }

    @Test
    void targetOnlySkillsStillRequireTargets() {
        assertFalse(SkillCast.hasSelfEffect("火"));
        assertTrue(SkillCast.hasTargetEffect("火"));
        assertEquals(SkillCast.TargetRequirement.REQUIRED, SkillCast.targetRequirement("火"));
        assertTrue(SkillCast.requiresTarget("火"));
    }

    @Test
    void targetPathCombosCanUsePiercing() {
        assertTrue(SkillCast.supportsPiercingCombo(SkillCast.linkedComboSpec("镇", "火")));
        assertTrue(SkillCast.supportsPiercingCombo(SkillCast.linkedComboSpec("封", "雷")));
        assertTrue(SkillCast.supportsPiercingCombo(SkillCast.linkedComboSpec("斩", "吸")));
    }

    @Test
    void supportSoulAndScanCombosRejectPiercing() {
        assertFalse(SkillCast.supportsPiercingCombo(SkillCast.linkedComboSpec("护", "净")));
        assertFalse(SkillCast.supportsPiercingCombo(SkillCast.linkedComboSpec("镇", "魄")));
        assertFalse(SkillCast.supportsPiercingCombo(SkillCast.linkedComboSpec("斩", "明")));
    }

    @Test
    void everyTwoSkillPairHasSpec() {
        String[] skills = {"火", "雷", "护", "净", "镇", "封", "退", "引", "斩", "明", "吸", "魄"};
        int count = 0;
        for (int i = 0; i < skills.length; i++) {
            for (int j = i + 1; j < skills.length; j++) {
                assertNotNull(SkillCast.linkedComboSpec(skills[i], skills[j]), skills[i] + "+" + skills[j]);
                count++;
            }
        }
        assertEquals(66, count);
        assertEquals(66, LinkedComboRegistry.allKeys().size());
    }
}
