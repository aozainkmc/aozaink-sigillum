package com.aozainkmc.sigillum.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class SigillumBindingOverlay {
    private static final int DURATION_TICKS = 40;
    private static final int RING_SEGMENTS = 48;
    private static final int SPIRAL_SEGMENTS = 36;
    private static final double RING_RADIUS = 1.4;

    private static final List<Ritual> RITUALS = new ArrayList<>();

    private SigillumBindingOverlay() {}

    public static void add(Vec3 anchor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RITUALS.add(new Ritual(anchor, mc.level.getGameTime()));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || RITUALS.isEmpty()) return;

        long now = mc.level.getGameTime();
        Iterator<Ritual> iterator = RITUALS.iterator();
        while (iterator.hasNext()) {
            Ritual ritual = iterator.next();
            if (now - ritual.startTick >= DURATION_TICKS) {
                iterator.remove();
            }
        }
        if (RITUALS.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
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
        Vec3 cameraPos = camera.getPosition();

        for (Ritual ritual : RITUALS) {
            float progress = Math.min(1.0f, (now - ritual.startTick) / (float) DURATION_TICKS);
            float life = 1.0f - progress;
            Vec3 anchor = ritual.anchor.subtract(cameraPos);
            renderRing(builder, matrix, anchor, right, up, progress, life);
            renderSpiral(builder, matrix, anchor, right, up, progress, life);
            renderTopSeal(builder, matrix, anchor, right, up, progress, life);
        }

        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderRing(BufferBuilder builder, Matrix4f matrix, Vec3 anchor,
            Vec3 cameraRight, Vec3 cameraUp, float progress, float life) {
        double radius = RING_RADIUS * (0.35 + easeOut(progress) * 0.65);
        double innerRadius = radius * 0.92;
        float alpha = Math.min(1.0f, life * 1.35f);
        Vec3 prevOuter = null;
        Vec3 prevInner = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / RING_SEGMENTS;
            Vec3 outer = anchor.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            Vec3 inner = anchor.add(Math.cos(angle) * innerRadius, 0.0, Math.sin(angle) * innerRadius);
            if (prevOuter != null) {
                SigillumInscriptionOverlay.quad(builder, matrix, prevInner, prevOuter, outer, inner,
                    SigillumInscriptionOverlay.alphaColor(0x70E8B448, alpha));
            }
            prevOuter = outer;
            prevInner = inner;
        }

        for (int i = 0; i < RING_SEGMENTS; i += 4) {
            double angle = Math.PI * 2.0 * i / RING_SEGMENTS;
            double x = anchor.x + Math.cos(angle) * radius;
            double z = anchor.z + Math.sin(angle) * radius;
            Vec3 p = new Vec3(x, anchor.y, z);
            SigillumInscriptionOverlay.renderNode(builder, matrix, p, cameraRight, cameraUp, 0.055, alpha);
        }
    }

    private static void renderSpiral(BufferBuilder builder, Matrix4f matrix, Vec3 anchor,
            Vec3 cameraRight, Vec3 cameraUp, float progress, float life) {
        Vec3 prev = null;
        double phase = easeOut(progress) * Math.PI * 1.35;
        float alpha = Math.min(1.0f, life * 1.45f);
        for (int i = 0; i <= SPIRAL_SEGMENTS; i++) {
            float p = i / (float) SPIRAL_SEGMENTS;
            double angle = p * 2.5 * Math.PI + phase;
            double radius = RING_RADIUS * (0.85 - progress * 0.25) * (1.0 - p * 0.72);
            double y = p * (1.75 + progress * 0.55);
            Vec3 point = anchor.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            if (prev != null) {
                SigillumInscriptionOverlay.renderGoldLine(builder, matrix, prev, point,
                    cameraRight, cameraUp, 0.018, alpha);
            }
            if (i % 6 == 0) {
                SigillumInscriptionOverlay.renderNode(builder, matrix, point,
                    cameraRight, cameraUp, 0.055 + 0.025 * life, alpha);
            }
            prev = point;
        }
    }

    private static void renderTopSeal(BufferBuilder builder, Matrix4f matrix, Vec3 anchor,
            Vec3 cameraRight, Vec3 cameraUp, float progress, float life) {
        Vec3 top = anchor.add(0.0, 1.9 + easeOut(progress) * 0.35, 0.0);
        float alpha = Math.min(1.0f, life * 1.25f);
        SigillumInscriptionOverlay.renderNode(builder, matrix, top, cameraRight, cameraUp, 0.13 + 0.04 * life, alpha);
        int cinnabar = SigillumInscriptionOverlay.alphaColor(0x70B43C2B, alpha);
        SigillumInscriptionOverlay.billboard(builder, matrix, top, cameraRight, cameraUp, 0.20 + 0.10 * life, cinnabar);
    }

    private static float easeOut(float progress) {
        float t = Math.max(0.0f, Math.min(1.0f, progress));
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private record Ritual(Vec3 anchor, long startTick) {}
}
