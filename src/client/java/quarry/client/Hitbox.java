package quarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Контуры хитбоксов и «таргет ESP». Линии рисуются с проверкой глубины, за стенами их не видно. */
public class Hitbox {
    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(Hitbox::render);
    }

    private static int colorOf(int choice) {
        switch (choice) {
            case 1:
                return 0xFFFFFFFF;
            case 2:
                return 0xFFFF4B4B;
            case 3:
                return 0xFF4BE07C;
            default:
                return Config.accent();
        }
    }

    private static void render(WorldRenderContext ctx) {
        boolean boxes = Config.get("hitbox");
        boolean esp = Config.get("target_esp");
        if (!boxes && !esp) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        MultiBufferSource consumers = ctx.consumers();
        PoseStack ps = ctx.matrixStack();
        if (consumers == null || ps == null) return;

        Vec3 cam = ctx.camera().getPosition();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        VertexConsumer vc = consumers.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        if (boxes) {
            int target = Config.setting("hitbox", "target");
            double range = Config.setting("hitbox", "range");
            float alpha = Config.setting("hitbox", "alpha") / 100f;
            int col = colorOf(Config.setting("hitbox", "color"));
            float r = ((col >> 16) & 255) / 255f, g = ((col >> 8) & 255) / 255f, b = (col & 255) / 255f;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
                if (e.distanceToSqr(mc.player) > range * range) continue;
                boolean isPlayer = e instanceof Player;
                if (target == 0 && !isPlayer) continue;
                if (target == 1 && !(e instanceof LivingEntity && !isPlayer)) continue;
                ShapeRenderer.renderLineBox(ps, vc, movedBox(e, pt), r, g, b, alpha);
            }
        }

        // таргет ESP: только существо под прицелом (то есть то, что и так видно и можно ударить)
        Entity t = mc.crosshairPickEntity;
        if (esp && t instanceof LivingEntity && t != mc.player) {
            int col = colorOf(Config.setting("target_esp", "color"));
            float r = ((col >> 16) & 255) / 255f, g = ((col >> 8) & 255) / 255f, b = (col & 255) / 255f;
            AABB box = movedBox(t, pt).inflate(0.05);
            boolean dashed = Config.setting("target_esp", "style") == 0;
            double dash = Config.setting("target_esp", "dash") / 100.0;
            double phase = (System.currentTimeMillis() % 100000L) / 1000.0 * Config.setting("target_esp", "speed") * 0.05;
            if (dashed) {
                dashedBox(ps, vc, box.inflate(0.06), r, g, b, 0.30f, dash * 1.6, dash * 0.8, phase);
                dashedBox(ps, vc, box, r, g, b, 1.0f, dash, dash * 0.7, phase);
                dashedBox(ps, vc, box.inflate(0.012), r, g, b, 1.0f, dash, dash * 0.7, phase);
            } else {
                ShapeRenderer.renderLineBox(ps, vc, box.inflate(0.06), r, g, b, 0.30f);
                ShapeRenderer.renderLineBox(ps, vc, box, r, g, b, 1.0f);
                ShapeRenderer.renderLineBox(ps, vc, box.inflate(0.012), r, g, b, 1.0f);
            }
        }
        ps.popPose();
    }

    private static AABB movedBox(Entity e, float pt) {
        double dx = Mth.lerp(pt, e.xo, e.getX()) - e.getX();
        double dy = Mth.lerp(pt, e.yo, e.getY()) - e.getY();
        double dz = Mth.lerp(pt, e.zo, e.getZ()) - e.getZ();
        return e.getBoundingBox().move(dx, dy, dz);
    }

    private static void dashedBox(PoseStack ps, VertexConsumer vc, AABB b, float r, float g, float bl, float a,
                                  double dash, double gap, double phase) {
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (double y : ys) for (double z : zs) dashLine(ps, vc, b.minX, y, z, b.maxX, y, z, r, g, bl, a, dash, gap, phase);
        for (double x : xs) for (double z : zs) dashLine(ps, vc, x, b.minY, z, x, b.maxY, z, r, g, bl, a, dash, gap, phase);
        for (double x : xs) for (double y : ys) dashLine(ps, vc, x, y, b.minZ, x, y, b.maxZ, r, g, bl, a, dash, gap, phase);
    }

    /** Один ребро рамки пунктиром: короткие отрезки (вырожденные коробки) с зазорами. */
    private static void dashLine(PoseStack ps, VertexConsumer vc, double x1, double y1, double z1,
                                 double x2, double y2, double z2, float r, float g, float b, float a,
                                 double dash, double gap, double phase) {
        double len = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        if (len <= 0) return;
        double period = Math.max(0.02, dash + gap);
        for (double s = -period + (phase % period); s < len; s += period) {
            double a0 = Math.max(0, s), a1 = Math.min(len, s + dash);
            if (a1 <= a0) continue;
            double f0 = a0 / len, f1 = a1 / len;
            AABB seg = new AABB(x1 + (x2 - x1) * f0, y1 + (y2 - y1) * f0, z1 + (z2 - z1) * f0,
                    x1 + (x2 - x1) * f1, y1 + (y2 - y1) * f1, z1 + (z2 - z1) * f1);
            ShapeRenderer.renderLineBox(ps, vc, seg, r, g, b, a);
        }
    }
}
