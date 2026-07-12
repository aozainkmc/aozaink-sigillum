package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.network.InscriptionRevealPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class SigillumInscriptionCamera {
    private static final long RETURN_NANOS = 340_000_000L;
    private static Shot active;

    private SigillumInscriptionCamera() {}

    public static void begin(InscriptionRevealPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.getUUID().equals(payload.ownerId())) return;
        Vec3 center = Vec3.atCenterOf(payload.pos()).add(0.0D, 0.15D, 0.0D);
        Vec3 start = minecraft.player.getEyePosition(1.0F);
        Vec3 radial = start.subtract(center).multiply(1.0D, 0.0D, 1.0D);
        if (radial.lengthSqr() < 0.01D) {
            float yaw = minecraft.player.getYRot() * Mth.DEG_TO_RAD;
            radial = new Vec3(Math.sin(yaw), 0.0D, -Math.cos(yaw));
        }
        radial = radial.normalize();
        double radius = Math.max(2.5D, payload.radius());
        Vec3 end = center.add(radial.scale(radius)).add(0.0D, Math.max(3.2D, radius * 0.82D), 0.0D);
        float[] endRotation = lookAt(end, center);
        long duration = InscriptionRevealPayload.durationTicks(payload.radius() - payload.startRadius()) * 50_000_000L;
        active = new Shot(start, end, minecraft.player.getYRot(), minecraft.player.getXRot(),
            endRotation[0], endRotation[1], System.nanoTime(), duration);
    }

    public static void tick(Minecraft minecraft) {
        Shot shot = active;
        if (shot == null) return;
        if (minecraft.player == null || minecraft.level == null
                || System.nanoTime() - shot.startNanos >= shot.outwardNanos + RETURN_NANOS) {
            active = null;
        }
    }

    public static CameraPose cameraPose(long nowNanos) {
        Shot shot = active;
        Minecraft minecraft = Minecraft.getInstance();
        if (shot == null || minecraft.player == null) return null;
        long elapsed = nowNanos - shot.startNanos;
        if (elapsed <= shot.outwardNanos) {
            float progress = smooth(Mth.clamp(elapsed / (float) shot.outwardNanos, 0.0F, 1.0F));
            return new CameraPose(
                shot.startPosition.lerp(shot.endPosition, progress),
                Mth.rotLerp(progress, shot.startYaw, shot.endYaw),
                Mth.lerp(progress, shot.startPitch, shot.endPitch)
            );
        }

        float progress = smooth(Mth.clamp((elapsed - shot.outwardNanos) / (float) RETURN_NANOS, 0.0F, 1.0F));
        Vec3 playerEye = minecraft.player.getEyePosition(1.0F);
        return new CameraPose(
            shot.endPosition.lerp(playerEye, progress),
            Mth.rotLerp(progress, shot.endYaw, minecraft.player.getYRot()),
            Mth.lerp(progress, shot.endPitch, minecraft.player.getXRot())
        );
    }

    public static void reset() {
        active = null;
    }

    public static boolean isActive() {
        return active != null;
    }

    private static float[] lookAt(Vec3 position, Vec3 target) {
        Vec3 delta = target.subtract(position);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        return new float[] {
            (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F,
            (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG)
        };
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) {}

    private record Shot(Vec3 startPosition, Vec3 endPosition, float startYaw, float startPitch,
                        float endYaw, float endPitch, long startNanos, long outwardNanos) {}
}
