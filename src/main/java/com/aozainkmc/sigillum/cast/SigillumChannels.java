package com.aozainkmc.sigillum.cast;

import com.aozainkmc.core.api.InkChannel;
import com.aozainkmc.sigillum.SigillumMod;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Core channels Sigillum asks; see the channel index in aozaink-core/README.md. */
public final class SigillumChannels {
    /**
     * {@code aozaink_sigillum:owner}. Question: a creature. Answer: the UUID of the player it
     * belongs to (another module's summons, such as bean soldiers), or null when it belongs to
     * nobody. The first answer is used. 刻护 shields a creature owned by the ward's owner or a
     * teammate and treats one owned by anyone else as an intruder.
     */
    public static final InkChannel<LivingEntity, UUID> OWNER = InkChannel.of(
        ResourceLocation.fromNamespaceAndPath(SigillumMod.MOD_ID, "owner"), LivingEntity.class, UUID.class);

    private SigillumChannels() {}
}
