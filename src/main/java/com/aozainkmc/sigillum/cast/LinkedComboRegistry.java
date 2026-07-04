package com.aozainkmc.sigillum.cast;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class LinkedComboRegistry {

    @FunctionalInterface
    public interface ComboExecutor {
        void execute(ServerPlayer player, LivingEntity target, SkillCast.CastEnv env);
    }

    @FunctionalInterface
    public interface ComboDescriber {
        String describe(boolean detailed);
    }

    public record ComboKey(String first, String second) {
        public static ComboKey of(String a, String b) {
            if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.equals(b)) {
                throw new IllegalArgumentException("Invalid combo key: " + a + ", " + b);
            }
            int cmp = SKILL_RANK.compare(a, b);
            return cmp <= 0 ? new ComboKey(a, b) : new ComboKey(b, a);
        }

        private static final Comparator<String> SKILL_RANK = Comparator.comparing(s -> {
            return switch (s) {
                case "镇" -> 0;
                case "封" -> 1;
                case "退" -> 2;
                case "引" -> 3;
                case "火" -> 4;
                case "雷" -> 5;
                case "护" -> 6;
                case "净" -> 7;
                case "斩" -> 8;
                case "明" -> 9;
                case "吸" -> 10;
                case "魄" -> 11;
                default -> 100;
            };
        });
    }

    public record ComboSpec(
        ComboKey key,
        SkillCast.LinkedComboKind kind,
        boolean needsTarget,
        String label,
        ComboExecutor executor,
        ComboDescriber describer
    ) {}

    private static final Map<ComboKey, ComboSpec> COMBOS = new HashMap<>();

    private LinkedComboRegistry() {}

    static {
        registerDrainCombos();
        registerSoulCombos();
        registerSuppressCombos();
        registerRemainingCombos();
    }

    private static void registerDrainCombos() {
        register("吸", "镇", SkillCast.LinkedComboKind.DRAIN, true, "吸联动",
            (p, t, env) -> SkillCast.applyDrainCombo(p, t, "镇", env),
            detailed -> detailed ? "吸联动 · 镇压期间每秒吸血" : "镇压吸血");

        register("吸", "封", SkillCast.LinkedComboKind.DRAIN, true, "吸联动",
            (p, t, env) -> SkillCast.applyDrainCombo(p, t, "封", env),
            detailed -> detailed ? "吸联动 · 封印期间每秒吸血" : "封印吸血");

        register("吸", "退", SkillCast.LinkedComboKind.DRAIN, true, "吸联动",
            (p, t, env) -> SkillCast.applyDrainCombo(p, t, "退", env),
            detailed -> detailed ? "吸联动 · 击退后按位移距离回血" : "击退回血");

        register("吸", "引", SkillCast.LinkedComboKind.DRAIN, true, "吸联动",
            (p, t, env) -> SkillCast.applyDrainCombo(p, t, "引", env),
            detailed -> detailed ? "吸联动 · 牵引后按位移距离吸血" : "牵引吸血");

        register("吸", "火", SkillCast.LinkedComboKind.DRAIN, true, "吸联动",
            (p, t, env) -> SkillCast.applyDrainCombo(p, t, "火", env),
            detailed -> detailed ? "吸联动 · 火焰每跳造成的实际燃烧伤害等量回复施法者" : "燃烧伤害转回血");

        register("吸", "雷", SkillCast.LinkedComboKind.DRAIN, true, "吸联动",
            (p, t, env) -> SkillCast.applyDrainCombo(p, t, "雷", env),
            detailed -> detailed ? "吸联动 · 雷击伤害转回血" : "雷击吸血");

        register("吸", "护", SkillCast.LinkedComboKind.DRAIN, true, "吸联动",
            (p, t, env) -> SkillCast.applyDrainCombo(p, t, "护", env),
            detailed -> detailed ? "吸联动 · 获得护盾并持续吸血转溢出护盾" : "吸血转盾");

        register("吸", "净", SkillCast.LinkedComboKind.DRAIN, true, "吸联动",
            (p, t, env) -> SkillCast.applyDrainCombo(p, t, "净", env),
            detailed -> detailed ? "吸联动 · 净化自身并吸血（亡灵额外圣化伤害）" : "净化吸血");

        register("吸", "斩", SkillCast.LinkedComboKind.DRAIN, true, "吸联动",
            (p, t, env) -> SkillCast.applyDrainCombo(p, t, "斩", env),
            detailed -> detailed ? "吸联动 · 斩杀回血或按阈值吸血" : "斩杀吸血");

        register("吸", "明", SkillCast.LinkedComboKind.DRAIN, true, "吸联动",
            (p, t, env) -> SkillCast.applyDrainCombo(p, t, "明", env),
            detailed -> detailed ? "吸联动 · 照妖标记并吸血主目标+范围低吸" : "照妖吸血");

        register("吸", "魄", SkillCast.LinkedComboKind.DRAIN, true, "吸联动",
            (p, t, env) -> SkillCast.applyDrainCombo(p, t, "魄", env),
            detailed -> detailed ? "吸联动 · 吸血主目标并召回掉落" : "吸血召回");
    }

    private static void registerSoulCombos() {
        register("魄", "退", SkillCast.LinkedComboKind.SOUL, false, "魄联动",
            (p, t, env) -> SkillCast.applySoulCombo(p, t, "退", env),
            detailed -> detailed ? "魄联动 · 召回成功时从施法者与死亡点各触发一次无伤害驱退脉冲" : "召回成功触发双端驱退");

        register("魄", "引", SkillCast.LinkedComboKind.SOUL, false, "魄联动",
            (p, t, env) -> SkillCast.applySoulCombo(p, t, "引", env),
            detailed -> detailed ? "魄联动 · 把死亡点周围可召回掉落直接传到玩家脚底" : "掉落牵到脚边");

        register("魄", "护", SkillCast.LinkedComboKind.SOUL, false, "魄联动",
            (p, t, env) -> SkillCast.applySoulCombo(p, t, "护", env),
            detailed -> detailed ? "魄联动 · 获得护盾并同时执行魄的掉落召回" : "护盾并召回掉落");

        register("魄", "净", SkillCast.LinkedComboKind.SOUL, false, "魄联动",
            (p, t, env) -> SkillCast.applySoulCombo(p, t, "净", env),
            detailed -> detailed ? "魄联动 · 净化自身并同时执行魄的掉落召回" : "净化并召回掉落");

        register("魄", "明", SkillCast.LinkedComboKind.SOUL, false, "魄联动",
            (p, t, env) -> SkillCast.applySoulCombo(p, t, "明", env),
            detailed -> detailed ? "魄联动 · 照妖标记并同时执行魄的掉落召回" : "照妖并召回掉落");

        for (String other : new String[]{"镇", "封", "火", "雷", "斩"}) {
            String otherBrief = SkillCast.briefLabel(other);
            register("魄", other, SkillCast.LinkedComboKind.SOUL, true, "魄联动",
                (p, t, env) -> SkillCast.applySoulCombo(p, t, other, env),
                detailed -> detailed
                    ? "魄联动 · 主目标获得「" + other + "」效果，同时执行魄的掉落召回"
                    : otherBrief + "并召回掉落");
        }
    }

    private static void registerSuppressCombos() {
        register("镇", "封", SkillCast.LinkedComboKind.SUPPRESS_SEAL, true, "镇联动",
            SkillCast::applySuppressSeal,
            detailed -> detailed ? "镇联动 · 稳定镇封控制（减速/弱化/挖掘减速）" : "镇封强控");

        register("镇", "退", SkillCast.LinkedComboKind.SUPPRESS_REPEL, true, "镇联动",
            SkillCast::applySuppressRepel,
            detailed -> detailed ? "镇联动 · 击退后落点追加短镇压" : "击退后镇压");

        register("镇", "引", SkillCast.LinkedComboKind.SUPPRESS_LURE, true, "镇联动",
            SkillCast::applySuppressLure,
            detailed -> detailed ? "镇联动 · 牵引到安全距离后镇压" : "牵引后镇压");

        register("镇", "火", SkillCast.LinkedComboKind.SUPPRESS_FIRE, true, "镇联动",
            SkillCast::applySuppressFire,
            detailed -> detailed ? "镇联动 · 镇压窗口内持续燃烧，DoT 至少覆盖镇压时长" : "镇压期持续燃烧");

        register("镇", "雷", SkillCast.LinkedComboKind.SUPPRESS_THUNDER, true, "镇联动",
            SkillCast::applySuppressThunder,
            detailed -> detailed ? "镇联动 · 雷击锚定主目标并低倍率链跳附近敌人" : "雷击锚定链跳");

        register("镇", "护", SkillCast.LinkedComboKind.SUPPRESS_SHIELD, true, "镇联动",
            SkillCast::applySuppressShield,
            detailed -> detailed ? "镇联动 · 镇压期间目标移动距离转为施法者持久护盾" : "移动转持久盾");

        register("镇", "净", SkillCast.LinkedComboKind.SUPPRESS_PURIFY, true, "镇联动",
            SkillCast::applySuppressPurify,
            detailed -> detailed ? "镇联动 · 镇压期间抵消新增负面，追加圣化并延长镇压" : "净镇反负面");

        register("镇", "斩", SkillCast.LinkedComboKind.SUPPRESS_SLASH, true, "镇联动",
            SkillCast::applySuppressSlash,
            detailed -> detailed ? "镇联动 · 镇压期间目标每移动 1 格重检斩杀" : "移动触发斩杀");

        register("镇", "明", SkillCast.LinkedComboKind.SUPPRESS_LIGHT, true, "镇联动",
            SkillCast::applySuppressLight,
            detailed -> detailed ? "镇联动 · 主目标死亡后转移到明标记目标并刷新镇压" : "死亡转移镇压");
    }

    private static void registerRemainingCombos() {
        registerPair("封", "退", SkillCast.LinkedComboKind.SEAL_PAIR, "封联动",
            "封联动 · 目标被击退并上抛，落地后施加封禁", "击飞落地后封禁");
        registerPair("封", "引", SkillCast.LinkedComboKind.SEAL_PAIR, "封联动",
            "封联动 · 拉到玩家前方安全距离后封禁", "牵引后封禁");
        registerPair("封", "火", SkillCast.LinkedComboKind.DIRECT_PAIR, "直接组合",
            "直接组合 · 主目标获得封效果和火 DoT，不做额外联动", "封禁并燃烧");
        registerPair("封", "雷", SkillCast.LinkedComboKind.SEAL_PAIR, "封联动",
            "封联动 · 封禁期间每 2 秒落雷一次，并刷新短封禁", "封禁期循环雷击");
        registerPair("封", "护", SkillCast.LinkedComboKind.SEAL_PAIR, "封联动",
            "封联动 · 获得护盾；主目标被封期间试图伤害玩家会被阻止并转为额外盾", "封伤转额外盾");
        registerPair("封", "净", SkillCast.LinkedComboKind.SEAL_PAIR, "封联动",
            "封联动 · 净化自身；亡灵先吃圣化伤害，再封禁并移除再生", "净化圣化后封禁");
        registerPair("封", "斩", SkillCast.LinkedComboKind.SEAL_PAIR, "封联动",
            "封联动 · 先封禁，再用提高但封顶的斩杀阈值判定", "封禁提高斩杀线");
        registerPair("封", "明", SkillCast.LinkedComboKind.DIRECT_PAIR, "直接组合",
            "直接组合 · 主目标封禁；明按单字标记范围敌人", "封禁并照妖");

        registerPair("退", "引", SkillCast.LinkedComboKind.REPEL_PAIR, "退联动",
            "退联动 · 主目标击退后在停止位置聚怪，并造成 20 点撕裂伤", "击退落点聚怪");
        registerPair("退", "火", SkillCast.LinkedComboKind.REPEL_PAIR, "退联动",
            "退联动 · 击退并点燃，落点小范围火花点燃附近敌人", "击退落点火花");
        registerPair("退", "雷", SkillCast.LinkedComboKind.REPEL_PAIR, "退联动",
            "退联动 · 雷击点产生冲击波，雷伤打主目标并击退近处敌人", "雷击冲击波");
        registerPair("退", "护", SkillCast.LinkedComboKind.REPEL_PAIR, "退联动",
            "退联动 · 获得动能盾；盾吸收伤害会击退攻击者，近身碰撞也会推开敌人", "动能反推护盾");
        registerPair("退", "净", SkillCast.LinkedComboKind.REPEL_PAIR, "退联动",
            "退联动 · 净化自身；亡灵先圣化再被更强击退", "净化圣化击退");
        registerPair("退", "斩", SkillCast.LinkedComboKind.REPEL_PAIR, "退联动",
            "退联动 · 先斩杀，失败则击退并在短时间内持续重检斩杀线", "追斩击退");
        registerPair("退", "明", SkillCast.LinkedComboKind.REPEL_PAIR, "退联动",
            "退联动 · 击退后在落点范围标记敌人发光", "落点照妖");

        registerPair("引", "火", SkillCast.LinkedComboKind.LURE_PAIR, "引联动",
            "引联动 · 牵引并点燃，牵引路径敌人吃低倍率火 DoT", "燃烧牵引线");
        registerPair("引", "雷", SkillCast.LinkedComboKind.LURE_PAIR, "引联动",
            "引联动 · 牵引路径敌人各吃一次低倍率雷击，主目标终点再雷击", "牵引路径落雷");
        registerPair("引", "护", SkillCast.LinkedComboKind.LURE_PAIR, "引联动",
            "引联动 · 先获得护盾，再把主目标拉到护盾边界外", "护盾边界牵引");
        registerPair("引", "净", SkillCast.LinkedComboKind.LURE_PAIR, "引联动",
            "引联动 · 净化自身；亡灵被拉近后在终点触发圣化小爆发", "牵引圣化爆发");
        registerPair("引", "斩", SkillCast.LinkedComboKind.LURE_PAIR, "引联动",
            "引联动 · 拉入近身后延迟执行斩杀判定", "近身牵引斩");
        registerPair("引", "明", SkillCast.LinkedComboKind.LURE_PAIR, "引联动",
            "引联动 · 明标记范围敌人，成功牵引后延长主目标发光窗口", "牵引延长照妖");

        registerPair("火", "雷", SkillCast.LinkedComboKind.FIRE_PAIR, "火联动",
            "火联动 · 雷击引爆燃烧预算形成小范围爆发，并保留短燃烧", "火雷引爆");
        registerPair("火", "护", SkillCast.LinkedComboKind.FIRE_PAIR, "火联动",
            "火联动 · 获得护盾；火 DoT 的实际伤害转为额外火盾", "燃烧伤害转盾");
        registerPair("火", "净", SkillCast.LinkedComboKind.FIRE_PAIR, "火联动",
            "火联动 · 净焰 DoT；亡灵用圣化伤害并移除再生", "净焰持续伤害");
        registerPair("火", "斩", SkillCast.LinkedComboKind.FIRE_PAIR, "火联动",
            "火联动 · 每次火 DoT 实际伤害后立即重检斩杀", "燃烧触发斩杀");
        registerPair("火", "明", SkillCast.LinkedComboKind.FIRE_PAIR, "火联动",
            "火联动 · 主目标燃烧时，跳伤会短暂照亮附近敌人", "火炬标记");

        registerPair("雷", "护", SkillCast.LinkedComboKind.THUNDER_PAIR, "雷联动",
            "雷联动 · 雷击主目标，获得基础护盾和雷击实际伤害 50% 的额外盾", "雷击转额外盾");
        registerPair("雷", "净", SkillCast.LinkedComboKind.THUNDER_PAIR, "雷联动",
            "雷联动 · 净化自身；亡灵额外吃圣化雷击并移除一个增益", "净雷破增益");
        registerPair("雷", "斩", SkillCast.LinkedComboKind.THUNDER_PAIR, "雷联动",
            "雷联动 · 雷击实际伤害后立即执行斩杀判定", "雷后斩杀");
        registerPair("雷", "明", SkillCast.LinkedComboKind.THUNDER_PAIR, "雷联动",
            "雷联动 · 雷击产生大范围闪照，暴露附近敌人", "闪照标记");

        registerPair("护", "净", SkillCast.LinkedComboKind.SHIELD_PAIR, "护联动",
            "护联动 · 获得护盾并净化；护盾期间新增负面会被抵消并扣盾", "净护抵消负面");
        registerPair("护", "斩", SkillCast.LinkedComboKind.SHIELD_PAIR, "护联动",
            "护联动 · 斩杀成功时把目标剩余生命转成护盾，否则获得基础护盾", "处决转护盾");
        registerPair("护", "明", SkillCast.LinkedComboKind.SHIELD_PAIR, "护联动",
            "护联动 · 明护期间保持夜视，攻击护盾者会被发光标记", "明护反标记");

        registerPair("净", "斩", SkillCast.LinkedComboKind.PURIFY_PAIR, "净联动",
            "净联动 · 净化自身；亡灵先圣化并移除增益，再斩杀判定", "净化圣化斩");
        registerPair("净", "明", SkillCast.LinkedComboKind.PURIFY_PAIR, "净联动",
            "净联动 · 净化自身，只标记亡灵、隐身或带负面状态的敌人", "净明筛选标记");
        registerPair("斩", "明", SkillCast.LinkedComboKind.SLASH_PAIR, "斩联动",
            "斩联动 · 标记范围敌人，从主目标开始寻找首个低于斩杀线的目标处决", "标记择一处决");
    }

    private static void registerPair(String a, String b, SkillCast.LinkedComboKind kind, String label,
                                     String detailedText, String briefText) {
        register(a, b, kind, pairNeedsTarget(a, b), label,
            (p, t, env) -> SkillCast.applyPairCombo(p, t, a, b, env),
            detailed -> detailed ? detailedText : briefText);
    }

    private static boolean pairNeedsTarget(String a, String b) {
        return SkillCast.requiresTarget(a) || SkillCast.requiresTarget(b);
    }

    private static void register(String a, String b, SkillCast.LinkedComboKind kind, boolean needsTarget, String label,
                                  ComboExecutor executor, ComboDescriber describer) {
        ComboKey key = ComboKey.of(a, b);
        if (COMBOS.containsKey(key)) {
            throw new IllegalStateException("Duplicate combo registration: " + key);
        }
        COMBOS.put(key, new ComboSpec(key, kind, needsTarget, label, executor, describer));
    }

    public static ComboSpec lookup(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.equals(b)) {
            return null;
        }
        try {
            return COMBOS.get(ComboKey.of(a, b));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Set<ComboKey> allKeys() {
        return Collections.unmodifiableSet(COMBOS.keySet());
    }
}
