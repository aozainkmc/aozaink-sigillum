package com.aozainkmc.sigillum.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

public final class SigillumCriterionTrigger extends SimpleCriterionTrigger<SigillumCriterionTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, Event event) {
        trigger(player, instance -> instance.matches(event));
    }

    public record Event(
        String type,
        String grade,
        String skill,
        String modifier,
        String special,
        boolean tail,
        boolean linked,
        boolean shared,
        boolean escaped,
        boolean hitPlayer,
        boolean killedEntity,
        int count
    ) {
        public static Event empty() {
            return new Event("", "", "", "", "", false, false, false, false, false, false, 0);
        }

        public Event withType(String value) {
            return new Event(value, grade, skill, modifier, special, tail, linked, shared, escaped, hitPlayer, killedEntity, count);
        }

        public Event withGrade(String value) {
            return new Event(type, value, skill, modifier, special, tail, linked, shared, escaped, hitPlayer, killedEntity, count);
        }

        public Event withSkill(String value) {
            return new Event(type, grade, value, modifier, special, tail, linked, shared, escaped, hitPlayer, killedEntity, count);
        }

        public Event withModifier(String value) {
            return new Event(type, grade, skill, value, special, tail, linked, shared, escaped, hitPlayer, killedEntity, count);
        }

        public Event withSpecial(String value) {
            return new Event(type, grade, skill, modifier, value, tail, linked, shared, escaped, hitPlayer, killedEntity, count);
        }

        public Event withTail(boolean value) {
            return new Event(type, grade, skill, modifier, special, value, linked, shared, escaped, hitPlayer, killedEntity, count);
        }

        public Event withLinked(boolean value) {
            return new Event(type, grade, skill, modifier, special, tail, value, shared, escaped, hitPlayer, killedEntity, count);
        }

        public Event withShared(boolean value) {
            return new Event(type, grade, skill, modifier, special, tail, linked, value, escaped, hitPlayer, killedEntity, count);
        }

        public Event withEscaped(boolean value) {
            return new Event(type, grade, skill, modifier, special, tail, linked, shared, value, hitPlayer, killedEntity, count);
        }

        public Event withHitPlayer(boolean value) {
            return new Event(type, grade, skill, modifier, special, tail, linked, shared, escaped, value, killedEntity, count);
        }

        public Event withKilledEntity(boolean value) {
            return new Event(type, grade, skill, modifier, special, tail, linked, shared, escaped, hitPlayer, value, count);
        }

        public Event withCount(int value) {
            return new Event(type, grade, skill, modifier, special, tail, linked, shared, escaped, hitPlayer, killedEntity, value);
        }
    }

    public record Instance(
        Optional<ContextAwarePredicate> player,
        Optional<String> type,
        Optional<String> grade,
        Optional<String> skill,
        Optional<String> modifier,
        Optional<String> special,
        Optional<Boolean> tail,
        Optional<Boolean> linked,
        Optional<Boolean> shared,
        Optional<Boolean> escaped,
        Optional<Boolean> hitPlayer,
        Optional<Boolean> killedEntity,
        Optional<Integer> minCount
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.optionalFieldOf("type").forGetter(Instance::type),
                Codec.STRING.optionalFieldOf("grade").forGetter(Instance::grade),
                Codec.STRING.optionalFieldOf("skill").forGetter(Instance::skill),
                Codec.STRING.optionalFieldOf("modifier").forGetter(Instance::modifier),
                Codec.STRING.optionalFieldOf("special").forGetter(Instance::special),
                Codec.BOOL.optionalFieldOf("tail").forGetter(Instance::tail),
                Codec.BOOL.optionalFieldOf("linked").forGetter(Instance::linked),
                Codec.BOOL.optionalFieldOf("shared").forGetter(Instance::shared),
                Codec.BOOL.optionalFieldOf("escaped").forGetter(Instance::escaped),
                Codec.BOOL.optionalFieldOf("hit_player").forGetter(Instance::hitPlayer),
                Codec.BOOL.optionalFieldOf("killed_entity").forGetter(Instance::killedEntity),
                Codec.INT.optionalFieldOf("min_count").forGetter(Instance::minCount)
            ).apply(instance, Instance::new));

        private boolean matches(Event event) {
            return matches(type, event.type())
                && matches(grade, event.grade())
                && matches(skill, event.skill())
                && matches(modifier, event.modifier())
                && matches(special, event.special())
                && matches(tail, event.tail())
                && matches(linked, event.linked())
                && matches(shared, event.shared())
                && matches(escaped, event.escaped())
                && matches(hitPlayer, event.hitPlayer())
                && matches(killedEntity, event.killedEntity())
                && minCount.map(min -> event.count() >= min).orElse(true);
        }

        private static boolean matches(Optional<String> expected, String actual) {
            return expected.isEmpty() || expected.get().equals(actual);
        }

        private static boolean matches(Optional<Boolean> expected, boolean actual) {
            return expected.isEmpty() || expected.get() == actual;
        }
    }
}
