package quarry.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Hud {
    private static final int BG = Ui.HUD_BG;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static void render(GuiGraphics ctx, DeltaTracker tick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen instanceof MenuScreen) return;
        Font tr = mc.font;
        int acc = Config.accent();
        int accDim = (acc & 0x00FFFFFF);
        int sw = ctx.guiWidth();
        int sh = ctx.guiHeight();

        if (Config.get("edge_glow")) {
            int al = Math.min(255, Config.setting("edge_glow", "power") * 2) << 24;
            ctx.fillGradient(0, 0, sw, 30, accDim | al, accDim);
            ctx.fillGradient(0, sh - 30, sw, sh, accDim, accDim | al);
        }
        if (Config.get("lowhp") && mc.player.getHealth() <= 6f) {
            int pulse = 40 + (int) (30 * Math.sin(System.currentTimeMillis() / 220.0));
            ctx.fillGradient(0, 0, sw, 50, (pulse << 24) | 0xFF2020, 0x00FF2020);
            ctx.fillGradient(0, sh - 50, sw, sh, 0x00FF2020, (pulse << 24) | 0xFF2020);
        }
        if (Config.get("ring")) {
            int rad = Config.setting("ring", "radius");
            for (int a = 0; a < 360; a += 5) {
                double r = Math.toRadians(a);
                int px = sw / 2 + (int) Math.round(Math.cos(r) * rad);
                int py = sh / 2 + (int) Math.round(Math.sin(r) * rad);
                ctx.fill(px, py, px + 1, py + 1, acc);
            }
        }

        if (Config.get("hud_fps")) {
            String s = "Quarry  " + mc.getFps() + " FPS";
            int w = Ui.w(tr, s) + 18;
            Ui.rrect(ctx, sw / 2 - w / 2, 4, w, 17, 8, BG);
            Ui.draw(ctx, tr, "Quarry", sw / 2 - w / 2 + 9, 9, acc, false);
            Ui.draw(ctx, tr, mc.getFps() + " FPS", sw / 2 - w / 2 + 9 + Ui.w(tr, "Quarry  "), 9, 0xFFFFFFFF, false);
        }

        // слева сверху
        int y = 6;
        if (Config.get("hud_coords")) {
            y = pill(ctx, tr, "XYZ", (int) Math.floor(mc.player.getX()) + "  " + (int) Math.floor(mc.player.getY())
                    + "  " + (int) Math.floor(mc.player.getZ()) + "  " + mc.player.getDirection().getName(), 6, y, acc, false, sw);
        }
        if (Config.get("hud_speed")) {
            Vec3 v = mc.player.getDeltaMovement();
            double sp = Math.sqrt(v.x * v.x + v.z * v.z) * 20;
            y = pill(ctx, tr, "Speed", Config.setting("hud_speed", "unit") == 1 ? String.format("%.0f km/h", sp * 3.6) : String.format("%.1f b/s", sp), 6, y, acc, false, sw);
        }
        if (Config.get("hud_ping")) {
            String ping = "—";
            ClientPacketListener conn = mc.getConnection();
            if (conn != null) {
                PlayerInfo info = conn.getPlayerInfo(mc.player.getUUID());
                if (info != null) ping = info.getLatency() + " ms";
            }
            y = pill(ctx, tr, "Ping", ping, 6, y, acc, false, sw);
        }
        if (Config.get("hud_clock")) {
            y = pill(ctx, tr, "Time", LocalTime.now().format(DateTimeFormatter.ofPattern(Config.setting("hud_clock", "fmt") == 1 ? "hh:mm a" : "HH:mm")), 6, y, acc, false, sw);
        }
        if (Config.get("hud_world") && mc.level != null) {
            long dt = mc.level.getDayTime() % 24000L;
            y = pill(ctx, tr, "World", String.format("%02d:%02d", (dt / 1000 + 6) % 24, (dt % 1000) * 60 / 1000), 6, y, acc, false, sw);
        }
        if (Config.get("hud_memory")) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
            y = pill(ctx, tr, "RAM", used + " / " + (rt.maxMemory() >> 20) + " MB", 6, y, acc, false, sw);
        }

        // справа сверху
        int ry = 6;
        if (Config.get("hud_cps")) {
            ry = pill(ctx, tr, "CPS", String.valueOf(QuarryClient.cps()), 0, ry, acc, true, sw);
        }
        if (Config.get("hud_combo")) {
            ry = pill(ctx, tr, "Combo", "x" + Combat.combo, 0, ry, acc, true, sw);
        }
        if (Config.get("hud_reach")) {
            ry = pill(ctx, tr, "Reach", Combat.reach > 0 ? String.format("%.2f", Combat.reach) : "—", 0, ry, acc, true, sw);
        }
        if (Config.get("hud_cooldowns")) {
            Item[] its = {Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.CHORUS_FRUIT, Items.SHIELD, Items.WIND_CHARGE};
            String[] nms = {"Pearls", "Apple", "G-Apple", "Chorus", "Shield", "Wind"};
            for (int i = 0; i < its.length; i++) {
                float cd = mc.player.getCooldowns().getCooldownPercent(new ItemStack(its[i]), 0f);
                if (cd > 0f) ry = pill(ctx, tr, nms[i], Math.round(cd * 100) + "%", 0, ry, acc, true, sw);
            }
        }
        if (Config.get("hud_items")) {
            Inventory inv = mc.player.getInventory();
            int apples = inv.countItem(Items.GOLDEN_APPLE) + inv.countItem(Items.ENCHANTED_GOLDEN_APPLE);
            int[] counts = {inv.countItem(Items.TOTEM_OF_UNDYING), apples, inv.countItem(Items.ENDER_PEARL), inv.countItem(Items.ARROW)};
            String[] names = {"Totems", "Apples", "Pearls", "Arrows"};
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] > 0) ry = pill(ctx, tr, names[i], "x" + counts[i], 0, ry, acc, true, sw);
            }
        }

        // цель под прицелом
        if (Config.get("hud_target") && Combat.target != null && Combat.target.isAlive()) {
            LivingEntity t = Combat.target;
            String name = t.getName().getString();
            double dist = Math.sqrt(mc.player.distanceToSqr(t));
            float hp = t.getHealth(), max = Math.max(1f, t.getMaxHealth());
            String hs = String.format("%.1f / %.0f", hp, max);
            String ds = String.format("%.1f m", dist);
            int w = Math.max(130, Math.max(Ui.w(tr, name), Ui.w(tr, hs)) + Ui.w(tr, ds) + 34);
            int tx = sw / 2 + 28, ty = sh / 2 + 20;
            Ui.rrect(ctx, tx, ty, w, 38, 10, BG);
            Ui.rrect(ctx, tx + 6, ty + 8, 2, 8, 1, acc);
            Ui.draw(ctx, tr, name, tx + 13, ty + 8, 0xFFFFFFFF, false);
            Ui.draw(ctx, tr, ds, tx + w - 10 - Ui.w(tr, ds), ty + 8, acc, false);
            Ui.rrect(ctx, tx + 10, ty + 22, w - 20, 8, 4, 0xFF262B3A);
            float frac = Math.min(1f, hp / max);
            int hc = frac > 0.5f ? 0xFF7CE08A : frac > 0.25f ? 0xFFFFC857 : 0xFFFF6B6B;
            Ui.rrect(ctx, tx + 10, ty + 22, Math.max(4, Math.round((w - 20) * frac)), 8, 4, hc);
            Ui.draw(ctx, tr, hs, tx + 13, ty + 40, 0xFF9AA8B8, false);
        }

        // эффекты слева по центру
        if (Config.get("hud_effects")) {
            int ey = sh / 2 - 20;
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                if (Config.get("fullbright") && e.getEffect().equals(MobEffects.NIGHT_VISION)) continue;
                int secs = e.getDuration() / 20;
                String time = e.isInfiniteDuration() ? "∞" : String.format("%d:%02d", secs / 60, secs % 60);
                String name = e.getEffect().value().getDisplayName().getString() + " " + (e.getAmplifier() + 1);
                ey = pill(ctx, tr, name, time, 6, ey, acc, false, sw);
            }
        }

        // броня справа снизу
        int minPct = 100;
        int ay = sh - 112;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = mc.player.getItemBySlot(slot);
            if (st.isEmpty() || !st.isDamageableItem()) continue;
            int pct = (st.getMaxDamage() - st.getDamageValue()) * 100 / Math.max(1, st.getMaxDamage());
            minPct = Math.min(minPct, pct);
            if (!Config.get("hud_armor")) continue;
            Ui.rrect(ctx, sw - 72, ay - 2, 66, 20, 8, BG);
            ctx.renderItem(st, sw - 69, ay);
            int col = pct > 50 ? 0xFF7CE08A : pct > 20 ? 0xFFFFC857 : 0xFFFF6B6B;
            Ui.draw(ctx, tr, pct + "%", sw - 47, ay + 4, col, false);
            ay += 22;
        }
        if (Config.get("hud_warn") && minPct < 10) {
            String s = "Armor is breaking!";
            int w = Ui.w(tr, s) + 18;
            Ui.rrect(ctx, sw / 2 - w / 2, 26, w, 17, 8, 0xD0501418);
            Ui.draw(ctx, tr, s, sw / 2 - w / 2 + 9, 31, 0xFFFF8A8A, false);
        }

        if (Config.get("hud_keys")) {
            int bx = 8, by = sh - 70;
            key(ctx, tr, "W", bx + 24, by, mc.options.keyUp.isDown(), acc);
            key(ctx, tr, "A", bx, by + 24, mc.options.keyLeft.isDown(), acc);
            key(ctx, tr, "S", bx + 24, by + 24, mc.options.keyDown.isDown(), acc);
            key(ctx, tr, "D", bx + 48, by + 24, mc.options.keyRight.isDown(), acc);
        }
    }

    private static int pill(GuiGraphics ctx, Font tr, String label, String value, int x, int y, int acc, boolean right, int sw) {
        int w = Ui.w(tr, label) + (value.isEmpty() ? 0 : Ui.w(tr, value) + 7) + 20;
        int px = right ? sw - w - 6 : x;
        Ui.rrect(ctx, px, y, w, 17, 8, BG);
        Ui.rrect(ctx, px + 5, y + 5, 2, 7, 1, acc);
        Ui.draw(ctx, tr, label, px + 11, y + 5, acc, false);
        if (!value.isEmpty()) Ui.draw(ctx, tr, value, px + 11 + Ui.w(tr, label) + 7, y + 5, 0xFFFFFFFF, false);
        return y + 20;
    }

    private static void key(GuiGraphics ctx, Font tr, String s, int x, int y, boolean down, int acc) {
        Ui.rrect(ctx, x, y, 22, 22, 7, down ? acc : BG);
        Ui.draw(ctx, tr, s, x + 11 - Ui.w(tr, s) / 2, y + 7, down ? 0xFF04232B : 0xFFFFFFFF, false);
    }
}
