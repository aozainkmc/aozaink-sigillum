package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.network.InscriptionStatusPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class SigillumInscriptionOverlay {
    private static final int TTL_TICKS = 30;
    private static final int RENDER_DISTANCE_BUFFER_BLOCKS = 16;
    private static final float WIDTH = 1.0f;
    private static final float HEIGHT = 0.12f;
    private static final Map<Long, Entry> ENTRIES = new HashMap<>();

    private SigillumInscriptionOverlay() {}

    public static void update(List<InscriptionStatusPayload.Entry> entries) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        long expiresAt = minecraft.level.getGameTime() + TTL_TICKS;
        for (InscriptionStatusPayload.Entry entry : entries) {
            ENTRIES.put(entry.posLong(), new Entry(entry.progress(), expiresAt));
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || ENTRIES.isEmpty()) return;

        long now = minecraft.level.getGameTime();
        Vec3 cameraPos = event.getCamera().getPosition();
        double maxDistanceSqr = maxRenderDistanceSqr(minecraft);
        Iterator<Map.Entry<Long, Entry>> iterator = ENTRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Entry> mapEntry = iterator.next();
            if (mapEntry.getValue().expiresAt <= now
                    || Vec3.atCenterOf(BlockPos.of(mapEntry.getKey())).distanceToSqr(cameraPos) > maxDistanceSqr) {
                iterator.remove();
            }
        }
        if (ENTRIES.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Camera camera = event.getCamera();
        Vector3f leftVector = camera.getLeftVector();
        Vector3f upVector = camera.getUpVector();
        Vec3 right = new Vec3(-leftVector.x(), -leftVector.y(), -leftVector.z());
        Vec3 up = new Vec3(upVector.x(), upVector.y(), upVector.z());

        for (Map.Entry<Long, Entry> mapEntry : ENTRIES.entrySet()) {
            BlockPos pos = BlockPos.of(mapEntry.getKey());
            Vec3 display = displayPosition(minecraft.level, pos);
            Vec3 relative = display.subtract(cameraPos);
            renderBar(builder, matrix, relative, right, up, mapEntry.getValue().progress);
        }

        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static double maxRenderDistanceSqr(Minecraft minecraft) {
        int chunks = Math.max(2, minecraft.options.renderDistance().get());
        double blocks = chunks * 16.0 + RENDER_DISTANCE_BUFFER_BLOCKS;
        return blocks * blocks;
    }

    private static void renderBar(BufferBuilder builder, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up, float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        Vec3 r = right.scale(WIDTH * 0.5);
        Vec3 h = up.scale(HEIGHT * 0.5);
        quad(builder, matrix, center.subtract(r).subtract(h), center.add(r).subtract(h),
            center.add(r).add(h), center.subtract(r).add(h), 0xAA101722);

        double fillWidth = WIDTH * clamped;
        Vec3 fillCenter = center.subtract(right.scale((WIDTH - fillWidth) * 0.5));
        Vec3 fr = right.scale(fillWidth * 0.5);
        quad(builder, matrix, fillCenter.subtract(fr).subtract(h.scale(0.65)), fillCenter.add(fr).subtract(h.scale(0.65)),
            fillCenter.add(fr).add(h.scale(0.65)), fillCenter.subtract(fr).add(h.scale(0.65)), 0xEE2EA8FF);

        Vec3 shineCenter = fillCenter.add(up.scale(HEIGHT * 0.23));
        Vec3 sh = up.scale(HEIGHT * 0.12);
        quad(builder, matrix, shineCenter.subtract(fr), shineCenter.add(fr),
            shineCenter.add(fr).add(sh), shineCenter.subtract(fr).add(sh), 0xCCBDEBFF);
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        builder.addVertex(matrix, (float)a.x, (float)a.y, (float)a.z).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, (float)b.x, (float)b.y, (float)b.z).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, (float)c.x, (float)c.y, (float)c.z).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, (float)d.x, (float)d.y, (float)d.z).setColor(red, green, blue, alpha);
    }

    private static Vec3 displayPosition(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        if (level.getBlockState(above).isAir()) {
            return Vec3.atCenterOf(above).add(0.0, 0.35, 0.0);
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (level.getBlockState(side).isAir()) {
                return Vec3.atCenterOf(side).add(0.0, 0.55, 0.0);
            }
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = 1; dy >= -1; dy--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos candidate = pos.offset(dx, dy, dz);
                    if (!level.getBlockState(candidate).isAir()) continue;
                    double dist = candidate.distSqr(pos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = candidate.immutable();
                    }
                }
            }
        }
        return best == null ? Vec3.atCenterOf(pos).add(0.0, 1.35, 0.0) : Vec3.atCenterOf(best).add(0.0, 0.35, 0.0);
    }

    private record Entry(float progress, long expiresAt) {}
}
