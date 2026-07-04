package com.aozainkmc.sigillum.glyph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GlyphEffectDescriberTest {
    private final GlyphEffectDescriber describer = new GlyphEffectDescriber(() -> true);

    @Test
    void rejectsWideAndPierceTogether() {
        List<String> lines = describer.describe(List.of("火", "广", "穿"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("废符"));
        assertTrue(lines.get(0).contains("广/穿不能同时出现"));
    }

    @Test
    void shieldDescriptionDoesNotPromiseExpiry() {
        List<String> lines = describer.describe(List.of("护"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("只受击消耗"));
    }

    @Test
    void lightCanUseWideModifier() {
        List<String> lines = describer.describe(List.of("明", "广"));

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("明"));
        assertTrue(lines.get(1).contains("修饰"));
    }

    @Test
    void lightCanUsePierceModifier() {
        List<String> lines = describer.describe(List.of("明", "穿"));

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("明"));
        assertTrue(lines.get(1).contains("修饰"));
    }

    @Test
    void selfOnlyShieldStillRejectsWideModifier() {
        List<String> lines = describer.describe(List.of("护", "广"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("废符"));
        assertTrue(lines.get(0).contains("自施技能不支持广/穿"));
    }

    @Test
    void twoSkillSupportCanUseWideModifier() {
        List<String> lines = describer.describe(List.of("护", "净", "广"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("护"));
        assertTrue(lines.get(0).contains("净"));
        assertTrue(lines.get(0).contains("广域"));
        assertTrue(lines.get(0).contains("溢出"));
    }

    @Test
    void soulSupportCanUseWideModifier() {
        List<String> lines = describer.describe(List.of("净", "魄", "广"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("净"));
        assertTrue(lines.get(0).contains("魄"));
        assertTrue(lines.get(0).contains("溢出"));
    }

    @Test
    void rejectsInscriptionWithPiercingModifier() {
        List<String> lines = describer.describe(List.of("刻", "广", "穿"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("废符"));
    }

    @Test
    void describesInscriptionWithTwoValidModifiers() {
        List<String> lines = describer.describe(List.of("刻", "强", "续"));

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("刻印操作"));
        assertTrue(lines.get(0).contains("加强"));
        assertTrue(lines.get(0).contains("续时"));
        assertTrue(lines.get(1).contains("已有刻印"));
    }

    @Test
    void describesInscriptionExtensionOperation() {
        List<String> lines = describer.describe(List.of("刻", "续"));

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("刻印操作"));
        assertTrue(lines.get(0).contains("续时"));
        assertTrue(lines.get(1).contains("已有刻印"));
    }

    @Test
    void rejectsPiercingInscription() {
        List<String> lines = describer.describe(List.of("刻", "火", "穿"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("废符"));
        assertTrue(lines.get(0).contains("不支持穿"));
    }

    @Test
    void describesDrainFireAsLinkedCombo() {
        List<String> lines = describer.describe(List.of("火", "吸"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("吸联动"));
        assertTrue(lines.get(0).contains("实际燃烧伤害"));
    }

    @Test
    void modifiedDrainComboRemainsGenericThreeGlyphCombo() {
        List<String> lines = describer.describe(List.of("火", "吸", "强"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("吸联动"));
        assertTrue(lines.get(0).contains("强化"));
    }

    @Test
    void describesSoulRepelAsLinkedCombo() {
        List<String> lines = describer.describe(List.of("退", "魄"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("魄联动"));
        assertTrue(lines.get(0).contains("驱退脉冲"));
    }

    @Test
    void describesSoulLureAsLinkedCombo() {
        List<String> lines = describer.describe(List.of("引", "魄"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("魄联动"));
        assertTrue(lines.get(0).contains("脚底"));
    }

    @Test
    void rejectsInscriptionWithSkillInTailSlot() {
        List<String> lines = describer.describe(List.of("火", "刻", "雷"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("废符"));
        assertTrue(lines.get(0).contains("尾修槽"));
    }

    @Test
    void acceptsInscriptionWithMarkInSecondSlotAndTailModifier() {
        List<String> lines = describer.describe(List.of("火", "刻", "广"));

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("刻印符"));
        assertTrue(lines.get(0).contains("火+广"));
    }

    @Test
    void describesSuppressSealAsLinkedCombo() {
        List<String> lines = describer.describe(List.of("镇", "封"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("镇联动"));
        assertTrue(lines.get(0).contains("镇封"));
    }

    @Test
    void describesSuppressFireAsLinkedCombo() {
        List<String> lines = describer.describe(List.of("火", "镇"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("镇联动"));
        assertTrue(lines.get(0).contains("持续燃烧"));
    }

    @Test
    void describesSuppressShieldAsLinkedCombo() {
        List<String> lines = describer.describe(List.of("护", "镇"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("镇联动"));
        assertTrue(lines.get(0).contains("持久护盾"));
    }

    @Test
    void describesSuppressSlashAsLinkedCombo() {
        List<String> lines = describer.describe(List.of("镇", "斩"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("镇联动"));
        assertTrue(lines.get(0).contains("斩杀"));
    }

    @Test
    void describesSuppressLightAsLinkedCombo() {
        List<String> lines = describer.describe(List.of("明", "镇"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("镇联动"));
        assertTrue(lines.get(0).contains("转移"));
    }

    @Test
    void modifiedSuppressComboRemainsGenericThreeGlyphCombo() {
        List<String> lines = describer.describe(List.of("镇", "火", "强"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("镇联动"));
        assertTrue(lines.get(0).contains("强化"));
    }

    @Test
    void linkedComboCanUseWideModifier() {
        List<String> lines = describer.describe(List.of("镇", "火", "广"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("镇联动"));
        assertTrue(lines.get(0).contains("广域联动"));
    }

    @Test
    void supportedLinkedComboCanUsePierceModifier() {
        List<String> lines = describer.describe(List.of("镇", "火", "穿"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("镇联动"));
        assertTrue(lines.get(0).contains("穿透"));
    }

    @Test
    void unsupportedLinkedComboRejectsPierceModifier() {
        List<String> lines = describer.describe(List.of("护", "净", "穿"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("废符"));
        assertTrue(lines.get(0).contains("不支持"));
    }

    @Test
    void drainSuppressUsesExactDrainSpec() {
        List<String> lines = describer.describe(List.of("吸", "镇"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("吸联动"));
        assertTrue(lines.get(0).contains("镇压"));
    }

    @Test
    void drainSoulUsesExactDrainSpec() {
        List<String> lines = describer.describe(List.of("吸", "魄"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("吸联动"));
        assertTrue(lines.get(0).contains("召回"));
    }

    @Test
    void soulComboRetainsGenericLookForSuppressPair() {
        List<String> lines = describer.describe(List.of("镇", "魄"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("魄联动"));
        assertTrue(lines.get(0).contains("镇"));
    }

    @Test
    void describesRemainingPairAsLinkedCombo() {
        List<String> lines = describer.describe(List.of("火", "雷"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("火联动"));
        assertTrue(lines.get(0).contains("引爆"));
    }

    @Test
    void describesSelfOnlyRemainingPairWithoutTargetPromise() {
        List<String> lines = describer.describe(List.of("护", "净"));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("护联动"));
        assertTrue(lines.get(0).contains("负面"));
        assertTrue(lines.get(0).contains("抵消"));
    }
}
