package com.aozainkmc.sigillum.client;

import com.aozainkmc.sigillum.network.InscriptionStatusPayload;
import com.aozainkmc.sigillum.network.InscriptionRevealPayload;
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
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class SigillumInscriptionOverlay {
    private static final int TTL_TICKS = 30;
    private static final int RENDER_DISTANCE_BUFFER_BLOCKS = 16;
    private static final float WIDTH = 1.0f;
    private static final float HEIGHT = 0.12f;
    private static final int BARRIER_SEGMENTS = 16;
    private static final int BARRIER_RINGS = 7;
    private static final int SIGIL_SEGMENTS = 48;
    private static final Vec3 UNIT_TOP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 UNIT_BOTTOM = new Vec3(0.0, -1.0, 0.0);
    private static final Vec3[][] UNIT_RINGS = createUnitRings();
    private static final Map<Long, Entry> ENTRIES = new HashMap<>();
    private static final Map<Long, Reveal> REVEALS = new HashMap<>();

    private SigillumInscriptionOverlay() {}

    public static void update(List<InscriptionStatusPayload.Entry> entries) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        long expiresAt = minecraft.level.getGameTime() + TTL_TICKS;
        for (InscriptionStatusPayload.Entry entry : entries) {
            ENTRIES.put(entry.posLong(), new Entry(entry.progress(), entry.radius(), entry.style(), expiresAt));
        }
    }

    public static void beginReveal(InscriptionRevealPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        REVEALS.put(payload.pos().asLong(), new Reveal(
            payload.startRadius(), payload.radius(), payload.ward(), minecraft.level.getGameTime(),
            InscriptionRevealPayload.durationTicks(payload.radius() - payload.startRadius())
        ));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || (ENTRIES.isEmpty() && REVEALS.isEmpty())) return;

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
        Iterator<Map.Entry<Long, Reveal>> revealIterator = REVEALS.entrySet().iterator();
        while (revealIterator.hasNext()) {
            Map.Entry<Long, Reveal> mapEntry = revealIterator.next();
            if (now - mapEntry.getValue().startTick >= mapEntry.getValue().durationTicks) revealIterator.remove();
        }
        if (ENTRIES.isEmpty() && REVEALS.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = new Tesselator(4096).begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        BufferBuilder veilBuilder = new Tesselator(4096).begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        BufferBuilder structureBuilder = new Tesselator(4096).begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Camera camera = event.getCamera();
        Frustum frustum = event.getFrustum();
        Vector3f leftVector = camera.getLeftVector();
        Vector3f upVector = camera.getUpVector();
        Vec3 right = new Vec3(-leftVector.x(), -leftVector.y(), -leftVector.z());
        Vec3 up = new Vec3(upVector.x(), upVector.y(), upVector.z());
        boolean anyCameraInsideWard = false;

        for (Map.Entry<Long, Reveal> mapEntry : REVEALS.entrySet()) {
            BlockPos pos = BlockPos.of(mapEntry.getKey());
            Reveal reveal = mapEntry.getValue();
            if (reveal.ward) {
                if (renderWardReveal(veilBuilder, structureBuilder, matrix, Vec3.atCenterOf(pos), cameraPos,
                        right, up, reveal, now, event.getPartialTick().getGameTimeDeltaPartialTick(false), frustum)) {
                    anyCameraInsideWard = true;
                }
            } else {
                renderReveal(builder, matrix, minecraft.level, Vec3.atCenterOf(pos), cameraPos,
                    reveal, now, event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        }

        for (Map.Entry<Long, Entry> mapEntry : ENTRIES.entrySet()) {
            BlockPos pos = BlockPos.of(mapEntry.getKey());
            Entry entry = mapEntry.getValue();
            if (REVEALS.containsKey(mapEntry.getKey())) continue;
            Vec3 anchor = Vec3.atCenterOf(pos);
            if (entry.ward()) {
                double radius = Math.max(0.18, entry.radius);
                if (isInFrustum(anchor, radius, frustum)
                        && renderWardBarrier(veilBuilder, structureBuilder, matrix, anchor, cameraPos,
                                right, up, entry)) {
                    anyCameraInsideWard = true;
                }
            } else {
                renderGroundSigil(builder, matrix, minecraft.level, anchor, cameraPos, entry);
                Vec3 lockTarget = lockTarget(minecraft, anchor, entry);
                if (lockTarget != null) {
                    renderLockRay(builder, matrix, anchor.add(0.0, 0.62, 0.0).subtract(cameraPos),
                        lockTarget.subtract(cameraPos), right, up, entry.progress);
                }
            }
            Vec3 display = displayPosition(minecraft.level, pos);
            Vec3 relative = display.subtract(cameraPos);
            renderBar(builder, matrix, relative, right, up, entry.progress);
        }

        var mainMesh = builder.build();
        if (mainMesh != null) {
            BufferUploader.drawWithShader(mainMesh);
        }

        var structureMesh = structureBuilder.build();
        if (structureMesh != null) {
            BufferUploader.drawWithShader(structureMesh);
        }

        if (anyCameraInsideWard) {
            RenderSystem.disableCull();
        } else {
            RenderSystem.enableCull();
        }
        var veilMesh = veilBuilder.build();
        if (veilMesh != null) {
            BufferUploader.drawWithShader(veilMesh);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static double maxRenderDistanceSqr(Minecraft minecraft) {
        int chunks = Math.max(2, minecraft.options.renderDistance().get());
        double blocks = chunks * 16.0 + RENDER_DISTANCE_BUFFER_BLOCKS;
        return blocks * blocks;
    }

    private static void renderGroundSigil(BufferBuilder builder, Matrix4f matrix, Level level,
            Vec3 anchor, Vec3 cameraPos, Entry entry) {
        double radius = Math.max(2.5, entry.radius);
        double edgeWidth = 0.25;
        double innerRadius = Math.max(0.75, radius - edgeWidth);
        int edgeColor = alphaColor(0x5A360806, entry.progress);

        for (int i = 0; i < SIGIL_SEGMENTS; i++) {
            double a0 = Math.PI * 2.0 * i / SIGIL_SEGMENTS;
            double a1 = Math.PI * 2.0 * (i + 1) / SIGIL_SEGMENTS;
            groundQuad(builder, matrix, level, anchor, cameraPos,
                polar(anchor, innerRadius, a0), polar(anchor, radius, a0), polar(anchor, radius, a1), polar(anchor, innerRadius, a1), edgeColor);
        }
    }

    private static void renderReveal(BufferBuilder builder, Matrix4f matrix, Level level, Vec3 anchor,
            Vec3 cameraPos, Reveal reveal, long now, float partialTick) {
        float progress = Math.max(0.0F, Math.min(1.0F,
            (now - reveal.startTick + partialTick) / reveal.durationTicks));
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        double front = Math.max(0.08D, reveal.startRadius + (reveal.radius - reveal.startRadius) * eased);
        double trail = Math.max(0.8D, (reveal.radius - reveal.startRadius) * 0.32D);
        int bands = 7;
        for (int band = 0; band < bands; band++) {
            double outer = Math.max(0.05D, front - trail * band / bands);
            double inner = Math.max(0.01D, front - trail * (band + 1) / bands);
            float strength = 1.0F - band / (float) bands;
            int alpha = Math.round(150.0F * strength * (0.55F + 0.45F * (1.0F - progress)));
            int color = (alpha << 24) | 0x002A0504;
            for (int i = 0; i < SIGIL_SEGMENTS; i++) {
                double a0 = Math.PI * 2.0D * i / SIGIL_SEGMENTS;
                double a1 = Math.PI * 2.0D * (i + 1) / SIGIL_SEGMENTS;
                groundQuad(builder, matrix, level, anchor, cameraPos,
                    polar(anchor, inner, a0), polar(anchor, outer, a0),
                    polar(anchor, outer, a1), polar(anchor, inner, a1), color);
            }
        }
    }

    private static boolean renderWardReveal(BufferBuilder veilBuilder, BufferBuilder structureBuilder,
            Matrix4f matrix, Vec3 anchor, Vec3 cameraPos, Vec3 cameraRight, Vec3 cameraUp,
            Reveal reveal, long now, float partialTick, Frustum frustum) {
        float progress = Math.max(0.0F, Math.min(1.0F,
            (now - reveal.startTick + partialTick) / reveal.durationTicks));
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        float radius = Math.max(0.18F, reveal.startRadius + (reveal.radius - reveal.startRadius) * eased);
        if (!isInFrustum(anchor, radius, frustum)) return false;
        return renderWardBarrier(veilBuilder, structureBuilder, matrix, anchor, cameraPos, cameraRight, cameraUp,
            new Entry(1.0F, radius, InscriptionStatusPayload.Entry.STYLE_WARD, Long.MAX_VALUE));
    }

    private static boolean isInFrustum(Vec3 anchor, double radius, Frustum frustum) {
        return frustum.isVisible(new AABB(
            anchor.x - radius, anchor.y - radius, anchor.z - radius,
            anchor.x + radius, anchor.y + radius, anchor.z + radius));
    }

    private static Vec3 lockTarget(Minecraft minecraft, Vec3 anchor, Entry entry) {
        double radiusSqr = entry.radius * entry.radius;
        if (entry.style == InscriptionStatusPayload.Entry.STYLE_SELF) {
            return null;
        }
        if (entry.style != InscriptionStatusPayload.Entry.STYLE_HOSTILE || minecraft.level == null) {
            return null;
        }
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !(living instanceof Enemy) || !living.isAlive()) continue;
            double distance = living.getBoundingBox().getCenter().distanceToSqr(anchor);
            if (distance > radiusSqr || distance >= bestDistance) continue;
            best = living;
            bestDistance = distance;
        }
        return best == null ? null : best.getBoundingBox().getCenter();
    }

    private static void renderLockRay(BufferBuilder builder, Matrix4f matrix, Vec3 start, Vec3 end,
            Vec3 cameraRight, Vec3 cameraUp, float progress) {
        lineQuad(builder, matrix, start, end, cameraRight, cameraUp, 0.11, alphaColor(0x462A0504, progress));
        lineQuad(builder, matrix, start, end, cameraRight, cameraUp, 0.055, alphaColor(0x8A4A0B07, progress));
    }

    private static void groundQuad(BufferBuilder builder, Matrix4f matrix, Level level, Vec3 anchor,
            Vec3 cameraPos, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        quad(builder, matrix,
            groundPoint(level, anchor, cameraPos, a),
            groundPoint(level, anchor, cameraPos, b),
            groundPoint(level, anchor, cameraPos, c),
            groundPoint(level, anchor, cameraPos, d),
            argb);
    }

    private static Vec3 groundPoint(Level level, Vec3 anchor, Vec3 cameraPos, Vec3 point) {
        double y = surfaceY(level, anchor, point.x, point.z);
        return new Vec3(point.x - cameraPos.x, y - cameraPos.y, point.z - cameraPos.z);
    }

    private static double surfaceY(Level level, Vec3 anchor, double x, double z) {
        int blockX = (int)Math.floor(x);
        int blockZ = (int)Math.floor(z);
        int centerY = (int)Math.floor(anchor.y);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (int y = centerY + 8; y >= centerY - 8; y--) {
            cursor.set(blockX, y, blockZ);
            above.set(blockX, y + 1, blockZ);
            if (!level.getBlockState(cursor).isAir() && level.getBlockState(above).isAir()) {
                return y + 1.018;
            }
        }
        return anchor.y + 0.018;
    }

    private static Vec3 polar(Vec3 center, double radius, double angle) {
        return center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
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
            fillCenter.add(fr).add(h.scale(0.65)), fillCenter.subtract(fr).add(h.scale(0.65)), 0xEECF9A28);

        Vec3 shineCenter = fillCenter.add(up.scale(HEIGHT * 0.23));
        Vec3 sh = up.scale(HEIGHT * 0.12);
        quad(builder, matrix, shineCenter.subtract(fr), shineCenter.add(fr),
            shineCenter.add(fr).add(sh), shineCenter.subtract(fr).add(sh), 0xCCEDE1A8);
    }

    private static boolean renderWardBarrier(BufferBuilder veilBuilder, BufferBuilder structureBuilder,
            Matrix4f matrix, Vec3 anchor, Vec3 cameraPos, Vec3 cameraRight, Vec3 cameraUp, Entry entry) {
        double radius = Math.max(0.18, entry.radius);
        Vec3 center = anchor.subtract(cameraPos);
        Vec3 top = center.add(0.0, radius, 0.0);
        Vec3 bottom = center.add(0.0, -radius, 0.0);
        Vec3[][] rings = barrierRings(center, radius);
        boolean cameraInside = anchor.distanceToSqr(cameraPos) < radius * radius;

        int veil = alphaColor(0x24F3C86A, entry.progress);
        int topRing = rings.length - 1;
        for (int i = 0; i < BARRIER_SEGMENTS; i++) {
            int next = (i + 1) % BARRIER_SEGMENTS;
            triangle(veilBuilder, matrix, top, rings[topRing][next], rings[topRing][i], veil);
            triangle(veilBuilder, matrix, bottom, rings[0][i], rings[0][next], veil);
        }
        for (int ring = 0; ring < rings.length - 1; ring++) {
            for (int i = 0; i < BARRIER_SEGMENTS; i++) {
                int next = (i + 1) % BARRIER_SEGMENTS;
                triangle(veilBuilder, matrix, rings[ring][i], rings[ring + 1][i], rings[ring + 1][next], veil);
                triangle(veilBuilder, matrix, rings[ring][i], rings[ring + 1][next], rings[ring][next], veil);
            }
        }

        for (Vec3[] ring : rings) {
            for (int i = 0; i < BARRIER_SEGMENTS; i++) {
                int next = (i + 1) % BARRIER_SEGMENTS;
                renderGoldLine(structureBuilder, matrix, ring[i], ring[next], cameraRight, cameraUp, 0.026, entry.progress);
            }
        }
        for (int i = 0; i < BARRIER_SEGMENTS; i++) {
            renderGoldLine(structureBuilder, matrix, bottom, rings[0][i], cameraRight, cameraUp, 0.022, entry.progress);
            renderGoldLine(structureBuilder, matrix, rings[topRing][i], top, cameraRight, cameraUp, 0.022, entry.progress);
            for (int ring = 0; ring < rings.length - 1; ring++) {
                int next = (i + 1) % BARRIER_SEGMENTS;
                renderGoldLine(structureBuilder, matrix, rings[ring][i], rings[ring + 1][i], cameraRight, cameraUp, 0.022, entry.progress);
                renderGoldLine(structureBuilder, matrix, rings[ring][i], rings[ring + 1][next], cameraRight, cameraUp, 0.018, entry.progress);
            }
        }

        renderNode(structureBuilder, matrix, top, cameraRight, cameraUp, 0.14, entry.progress);
        renderNode(structureBuilder, matrix, bottom, cameraRight, cameraUp, 0.14, entry.progress);
        for (int i = 0; i < BARRIER_SEGMENTS; i++) {
            for (Vec3[] ring : rings) {
                renderNode(structureBuilder, matrix, ring[i], cameraRight, cameraUp, 0.11, entry.progress);
            }
        }

        return cameraInside;
    }

    private static Vec3[][] barrierRings(Vec3 center, double radius) {
        Vec3[][] rings = new Vec3[BARRIER_RINGS][BARRIER_SEGMENTS];
        for (int ring = 0; ring < BARRIER_RINGS; ring++) {
            for (int i = 0; i < BARRIER_SEGMENTS; i++) {
                Vec3 unit = UNIT_RINGS[ring][i];
                rings[ring][i] = center.add(unit.x * radius, unit.y * radius, unit.z * radius);
            }
        }
        return rings;
    }

    private static Vec3[][] createUnitRings() {
        Vec3[][] rings = new Vec3[BARRIER_RINGS][BARRIER_SEGMENTS];
        double step = Math.PI / (BARRIER_RINGS + 1);
        for (int ring = 0; ring < BARRIER_RINGS; ring++) {
            double latitude = -Math.PI * 0.5 + step * (ring + 1);
            double ringRadius = Math.cos(latitude);
            double y = Math.sin(latitude);
            double offset = ring % 2 == 0 ? 0.0 : Math.PI / BARRIER_SEGMENTS;
            for (int i = 0; i < BARRIER_SEGMENTS; i++) {
                double angle = offset + Math.PI * 2.0 * i / BARRIER_SEGMENTS;
                rings[ring][i] = new Vec3(Math.cos(angle) * ringRadius, y, Math.sin(angle) * ringRadius);
            }
        }
        return rings;
    }

    public static void renderGoldLine(BufferBuilder builder, Matrix4f matrix, Vec3 a, Vec3 b,
            Vec3 cameraRight, Vec3 cameraUp, double width, float progress) {
        lineQuad(builder, matrix, a, b, cameraRight, cameraUp, width * 2.2, alphaColor(0x36E8B448, progress));
        lineQuad(builder, matrix, a, b, cameraRight, cameraUp, width, alphaColor(0xB8FFE8A6, progress));
        lineQuad(builder, matrix, a, b, cameraRight, cameraUp, width * 0.35, alphaColor(0xA0B84A1B, progress));
    }

    public static void renderNode(BufferBuilder builder, Matrix4f matrix, Vec3 center,
            Vec3 cameraRight, Vec3 cameraUp, double size, float progress) {
        billboard(builder, matrix, center, cameraRight, cameraUp, size * 1.8, alphaColor(0x34F4C75E, progress));
        billboard(builder, matrix, center, cameraRight, cameraUp, size, alphaColor(0xE8FFF2BD, progress));
        billboard(builder, matrix, center, cameraRight, cameraUp, size * 0.42, alphaColor(0xE0B9371E, progress));
    }

    public static void lineQuad(BufferBuilder builder, Matrix4f matrix, Vec3 a, Vec3 b,
            Vec3 cameraRight, Vec3 cameraUp, double width, int argb) {
        Vec3 line = b.subtract(a);
        double x = line.dot(cameraRight);
        double y = line.dot(cameraUp);
        Vec3 normal = cameraRight.scale(-y).add(cameraUp.scale(x));
        if (normal.lengthSqr() < 0.0001) {
            normal = cameraUp;
        } else {
            normal = normal.normalize();
        }
        Vec3 half = normal.scale(width * 0.5);
        quad(builder, matrix, a.subtract(half), b.subtract(half), b.add(half), a.add(half), argb);
    }

    public static void billboard(BufferBuilder builder, Matrix4f matrix, Vec3 center,
            Vec3 right, Vec3 up, double size, int argb) {
        Vec3 r = right.scale(size * 0.5);
        Vec3 u = up.scale(size * 0.5);
        quad(builder, matrix, center.subtract(r).subtract(u), center.add(r).subtract(u),
            center.add(r).add(u), center.subtract(r).add(u), argb);
    }

    private static void triangle(BufferBuilder builder, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, int argb) {
        quad(builder, matrix, a, b, c, c, argb);
    }

    public static int alphaColor(int argb, float progress) {
        float life = Math.max(0.35f, Math.min(1.0f, progress));
        int alpha = Math.round(((argb >>> 24) & 0xFF) * life);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    public static void quad(BufferBuilder builder, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
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

    private record Entry(float progress, float radius, int style, long expiresAt) {
        private boolean ward() {
            return style == InscriptionStatusPayload.Entry.STYLE_WARD;
        }
    }

    private record Reveal(float startRadius, float radius, boolean ward, long startTick, int durationTicks) {}
}
