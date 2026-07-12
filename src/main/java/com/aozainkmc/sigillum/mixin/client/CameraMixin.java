package com.aozainkmc.sigillum.mixin.client;

import com.aozainkmc.sigillum.client.SigillumInscriptionCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void aozainkSigillum$applyInscriptionCamera(BlockGetter level, Entity entity,
            boolean detached, boolean mirror, float partialTick, CallbackInfo callback) {
        SigillumInscriptionCamera.CameraPose pose = SigillumInscriptionCamera.cameraPose(System.nanoTime());
        if (pose == null) return;
        this.detached = true;
        setPosition(pose.position());
        setRotation(pose.yaw(), pose.pitch());
    }
}
