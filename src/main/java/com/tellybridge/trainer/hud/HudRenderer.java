package com.tellybridge.trainer.hud;

import com.tellybridge.trainer.ClientProxy;
import com.tellybridge.trainer.config.TBTConfig;
import com.tellybridge.trainer.core.AimHint;
import com.tellybridge.trainer.core.PracticeSession;
import com.tellybridge.trainer.core.Stats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Locale;

/**
 * HUD 面板。用 RenderGameOverlayEvent 的 TEXT 阶段绘制（此时仍是 2D 正交投影，
 * 且已经过聊天栏渲染，不会被原版元素覆盖）。
 */
public class HudRenderer {

    private static final int PANEL_WIDTH = 192;
    private static final int LINE_HEIGHT = 10;
    private static final int PAD = 4;
    private static final int GAP = 3;

    private static final int C_TITLE = 0xFF6FD3FF;
    private static final int C_LABEL = 0xFF9E9E9E;
    private static final int C_VALUE = 0xFFFFFFFF;
    private static final int C_GOOD = 0xFF55FF55;
    private static final int C_WARN = 0xFFFFAA00;
    private static final int C_BAD = 0xFFFF5555;
    private static final int C_BG = 0x90000000;
    private static final int C_BORDER = 0xFF2E6FA8;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        // 1.8.9 的 GuiIngameForge#renderGameOverlay 在最后无条件 post(ALL)，
        // 此时 2D 正交投影仍然有效，且是整帧最后一个绘制点，不会被聊天栏盖住。
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.gameSettings == null || mc.gameSettings.hideGUI) {
            return;
        }
        // 打开背包/菜单时让位，但聊天栏不算：打字查命令时不该看不到面板
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) {
            return;
        }
        PracticeSession session = ClientProxy.session;
        if (session == null) {
            return;
        }

        EntityPlayerSP player = mc.thePlayer;
        FontRenderer fr = mc.fontRendererObj;
        int scaledWidth = event.resolution.getScaledWidth();
        int scaledHeight = event.resolution.getScaledHeight();

        // 面板可以单独关掉，落点提示仍然保留
        if (!TBTConfig.hudEnabled) {
            if (TBTConfig.hintEnabled) {
                drawAimOverlay(fr, scaledWidth, scaledHeight);
            }
            return;
        }

        float scale = TBTConfig.hudScale();
        if (scale <= 0.1F) {
            scale = 1.0F;
        }

        int height = panelHeight();

        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0F);

        int x = Math.round(TBTConfig.hudX / scale);
        int y = Math.round(TBTConfig.hudY / scale);
        int innerLeft = x + PAD + 2;
        int innerRight = x + PANEL_WIDTH - PAD - 2;
        int innerWidth = innerRight - innerLeft;

        drawPanel(x, y, height);

        int cy = y + PAD;

        // ---- 标题行 ----
        fr.drawStringWithShadow("TellyBridge Trainer", innerLeft, cy, C_TITLE);
        String state = session.isRunning() ? "\u00A7aRUNNING" : "\u00A78idle";
        fr.drawStringWithShadow(state, innerRight - fr.getStringWidth(state), cy, C_VALUE);
        cy += LINE_HEIGHT + 1;

        // ---- 进度：方块 / 时间 / 速率 ----
        int cx = innerLeft;
        cx = drawPair(fr, cx, cy, "Blocks", Integer.toString(session.getBlocks()), C_VALUE);
        cx = drawPair(fr, cx + GAP, cy, "Time", fmt("%.2fs", session.getElapsedSeconds()), C_VALUE);
        drawPair(fr, cx + GAP, cy, "Rate", fmt("%.2f/s", session.getRate()), C_VALUE);
        cy += LINE_HEIGHT;

        // ---- 跳跃相关 ----
        cx = innerLeft;
        cx = drawPair(fr, cx, cy, "Jumps", Integer.toString(session.getJumps()), C_VALUE);
        cx = drawPair(fr, cx + GAP, cy, "B/Jump", fmt("%.2f", session.getBlocksPerJump()), C_VALUE);
        drawPair(fr, cx + GAP, cy, "Miss", Integer.toString(session.getMisses()),
                session.getMisses() > 0 ? C_WARN : C_VALUE);
        cy += LINE_HEIGHT;

        // ---- 滞空窗口 / 本次跳跃方块数 / 水平速度 ----
        long airLeft = session.getAirRemainingMs();
        cx = innerLeft;
        cx = drawPair(fr, cx, cy, "Air", airLeft > 0L ? fmt("%.2fs", airLeft / 1000.0D) : "--",
                airLeft <= 0L ? C_VALUE : (airLeft < 300L ? C_BAD : (airLeft < 600L ? C_WARN : C_GOOD)));
        cx = drawPair(fr, cx + GAP, cy, "Blk", Integer.toString(session.getJumpBlocks()), C_VALUE);
        double spd = session.getSpeed();
        drawPair(fr, cx + GAP, cy, "Spd", fmt("%.1f", spd),
                spd >= 4.8D ? C_GOOD : (spd >= 3.8D ? C_WARN : C_BAD));
        cy += LINE_HEIGHT;

        // ---- 节奏条 ----
        if (TBTConfig.showCadenceBar) {
            drawCadenceBars(session, innerLeft, cy + 2, innerWidth, 12);
            cy += 16;
        }

        // ---- 节奏数值 ----
        double mean = session.getMeanIntervalMs();
        double jitter = session.getJitterMs();
        boolean hasCadence = mean > 0.0D;
        cx = innerLeft;
        cx = drawPair(fr, cx, cy, "Cadence", hasCadence ? fmt("%.0fms", mean) : "--", C_VALUE);
        cx = drawPair(fr, cx + GAP, cy, "Jitter", hasCadence ? fmt("%.0fms", jitter) : "--",
                hasCadence ? jitterColor(mean, jitter) : C_VALUE);
        double jp = session.getJumpToPlaceEma();
        drawPair(fr, cx + GAP, cy, "J>P", jp >= 0.0D ? fmt("%.0fms", jp) : "--",
                jp < 0.0D ? C_VALUE : (jp <= 400.0D ? C_GOOD : C_WARN));
        cy += LINE_HEIGHT;

        // ---- 俯仰角 ----
        if (TBTConfig.showPitch) {
            float pitch = player.rotationPitch;
            boolean inBand = pitch >= TBTConfig.pitchMin && pitch <= TBTConfig.pitchMax;
            String band = fmt("%d~%d", TBTConfig.pitchMin, TBTConfig.pitchMax);
            cx = drawPair(fr, innerLeft, cy, "Pitch", fmt("%.1f\u00B0", pitch), inBand ? C_GOOD : C_BAD);
            fr.drawStringWithShadow("[" + band + "]", cx + GAP, cy, C_LABEL);
            cy += LINE_HEIGHT;
            drawPitchGauge(innerLeft, cy, innerWidth, pitch);
            cy += 7;
        }

        // ---- 落点提示：还差多少度 ----
        if (TBTConfig.hintEnabled) {
            AimHint hint = AimHint.INSTANCE;
            String aimText;
            int aimColor;
            if (!hint.valid) {
                aimText = "--";
                aimColor = C_VALUE;
            } else if (hint.aligned) {
                aimText = "OK";
                aimColor = C_GOOD;
            } else {
                boolean yawOk = Math.abs(hint.deltaYaw) < 6.0F;
                boolean pitchOk = Math.abs(hint.deltaPitch) < 6.0F;
                aimText = dirText(hint.deltaYaw, "L", "R") + " " + dirText(hint.deltaPitch, "U", "D");
                aimColor = (yawOk && pitchOk) ? C_WARN : C_VALUE;
            }
            drawPair(fr, innerLeft, cy, "Aim", aimText, aimColor);
            cy += LINE_HEIGHT;
        }

        // ---- 按键状态 ----
        if (TBTConfig.showKeys) {
            int kx = innerLeft;
            kx = drawKey(fr, kx, cy, 12, "W", mc.gameSettings.keyBindForward.isKeyDown());
            kx = drawKey(fr, kx, cy, 12, "A", mc.gameSettings.keyBindLeft.isKeyDown());
            kx = drawKey(fr, kx, cy, 12, "S", mc.gameSettings.keyBindBack.isKeyDown());
            kx = drawKey(fr, kx, cy, 12, "D", mc.gameSettings.keyBindRight.isKeyDown());
            kx = drawKey(fr, kx, cy, 26, "RMB", mc.gameSettings.keyBindUseItem.isKeyDown());
            kx = drawKey(fr, kx, cy, 26, "JUMP", mc.gameSettings.keyBindJump.isKeyDown());
            drawKey(fr, kx, cy, 26, "SPR", player.isSprinting());

            String airText = player.onGround ? "GND" : "AIR";
            fr.drawStringWithShadow(airText, innerRight - fr.getStringWidth(airText), cy + 2,
                    player.onGround ? C_LABEL : C_GOOD);
            cy += 14;
        }

        // ---- 目标进度条 ----
        if (TBTConfig.targetBlocks > 0) {
            int done = Math.min(session.getBlocks(), TBTConfig.targetBlocks);
            int filled = (int) Math.round(innerWidth * (done / (double) TBTConfig.targetBlocks));
            Gui.drawRect(innerLeft, cy, innerLeft + innerWidth, cy + 4, 0x40000000);
            if (filled > 0) {
                Gui.drawRect(innerLeft, cy, innerLeft + filled, cy + 4,
                        done >= TBTConfig.targetBlocks ? C_GOOD : 0xFF3FA9F5);
            }
            cy += 9;
        }

        // ---- 历史最佳 ----
        Stats stats = ClientProxy.stats == null ? null : ClientProxy.stats.get();
        String best = stats == null
                ? ""
                : fmt("Best %.2f/s  %d blocks  %.0f%%", stats.bestRate, stats.longestRun, stats.bestConsistency);
        fr.drawStringWithShadow(best, innerLeft, cy, 0xFFC8C8C8);

        GL11.glPopMatrix();

        // 准星附近的提示不跟随面板缩放，必须和投影坐标一致
        if (TBTConfig.hintEnabled) {
            drawAimOverlay(fr, scaledWidth, scaledHeight);
        }
    }

    /**
     * 准星旁的落点提示。
     *
     *   瞄点已经在视野里  → 在它该出现的位置画一个"幽灵准星"，再用折线从真准星引过去
     *   瞄点在身后（telly 转头时大多数情况）→ 用左右/上下三角指示该往哪边转
     *   两种情况都在准星下方给出还需转动的度数，对上目标格时显示 OK
     */
    private void drawAimOverlay(FontRenderer fr, int scaledWidth, int scaledHeight) {
        AimHint hint = AimHint.INSTANCE;
        if (!hint.valid) {
            return;
        }
        int cx = scaledWidth / 2;
        int cy = scaledHeight / 2;
        int color = hint.aligned ? 0xFF55FF55 : 0xFFFFD24A;

        if (hint.onScreen) {
            int tx = Math.round(hint.screenX);
            int ty = Math.round(hint.screenY);
            if (TBTConfig.hintGhostCrosshair) {
                drawGhostCrosshair(tx, ty, color);
            }
            drawSteppedGuide(cx, cy, tx, ty, 0x50FFFFFF);
        } else {
            // 瞄点在背后：用方向三角提示转头
            if (Math.abs(hint.deltaYaw) >= 0.5F) {
                drawTurnTriangle(cx + (hint.deltaYaw > 0.0F ? 30 : -30), cy, hint.deltaYaw > 0.0F, color);
            }
            if (Math.abs(hint.deltaPitch) >= 0.5F) {
                drawTiltTriangle(cx, cy + (hint.deltaPitch > 0.0F ? 18 : -18), hint.deltaPitch > 0.0F, color);
            }
        }

        String text = hint.aligned
                ? "OK"
                : dirText(hint.deltaYaw, "L", "R") + " " + dirText(hint.deltaPitch, "U", "D");
        fr.drawStringWithShadow(text, cx - fr.getStringWidth(text) / 2, cy + 34, color);
    }

    /** 目标位置的"幽灵准星"：10x10 空心框 + 中心点 */
    private void drawGhostCrosshair(int x, int y, int color) {
        Gui.drawRect(x - 6, y - 6, x + 7, y - 5, color);
        Gui.drawRect(x - 6, y + 6, x + 7, y + 7, color);
        Gui.drawRect(x - 6, y - 5, x - 5, y + 6, color);
        Gui.drawRect(x + 6, y - 5, x + 7, y + 6, color);
        Gui.drawRect(x - 1, y - 1, x + 1, y + 1, color);
    }

    /** 从真准星到幽灵准星的 L 形引导（纯矩形，避免动 GL 状态） */
    private void drawSteppedGuide(int cx, int cy, int tx, int ty, int color) {
        int left = Math.min(cx, tx);
        int right = Math.max(cx, tx);
        int top = Math.min(cy, ty);
        int bottom = Math.max(cy, ty);
        Gui.drawRect(left, cy, right, cy + 1, color);
        Gui.drawRect(tx, top, tx + 1, bottom, color);
    }

    /** 左右三角：提示往左/往右带鼠标 */
    private void drawTurnTriangle(int x, int y, boolean right, int color) {
        for (int i = 0; i < 4; i++) {
            int half = 4 - i;
            int px = right ? x + i : x - i - 1;
            Gui.drawRect(px, y - half, px + 1, y + half, color);
        }
        int shaftX = right ? x - 6 : x - 4;
        Gui.drawRect(shaftX, y, shaftX + 6, y + 1, color);
    }

    /** 上下三角：提示往下压 / 往上抬 */
    private void drawTiltTriangle(int x, int y, boolean down, int color) {
        for (int i = 0; i < 4; i++) {
            int half = 4 - i;
            int py = down ? y + i : y - i - 1;
            Gui.drawRect(x - half, py, x + half, py + 1, color);
        }
        int shaftY = down ? y - 6 : y - 4;
        Gui.drawRect(x, shaftY, x + 1, shaftY + 6, color);
    }

    /** 把角度差写成 "L148" / "R23" / "0" */
    private static String dirText(float delta, String negative, String positive) {
        if (Math.abs(delta) < 0.5F) {
            return "0";
        }
        return (delta > 0.0F ? positive : negative) + Math.round(Math.abs(delta));
    }

    // ------------------------------------------------------------------ 绘制工具

    private int panelHeight() {
        int h = PAD;
        h += LINE_HEIGHT + 1;         // 标题
        h += LINE_HEIGHT;             // 方块/时间/速率
        h += LINE_HEIGHT;             // 跳跃
        h += LINE_HEIGHT;             // 滞空/本次方块/速度
        if (TBTConfig.showCadenceBar) {
            h += 16;
        }
        h += LINE_HEIGHT;             // 节奏数值
        if (TBTConfig.showPitch) {
            h += LINE_HEIGHT + 7;
        }
        if (TBTConfig.hintEnabled) {
            h += LINE_HEIGHT;         // 落点提示
        }
        if (TBTConfig.showKeys) {
            h += 14;
        }
        if (TBTConfig.targetBlocks > 0) {
            h += 9;
        }
        h += LINE_HEIGHT;             // 最佳记录
        h += PAD - 2;
        return h;
    }

    private void drawPanel(int x, int y, int height) {
        Gui.drawRect(x, y, x + PANEL_WIDTH, y + height, C_BG);
        Gui.drawRect(x, y, x + PANEL_WIDTH, y + 1, C_BORDER);
        Gui.drawRect(x, y + height - 1, x + PANEL_WIDTH, y + height, C_BORDER);
        Gui.drawRect(x, y, x + 1, y + height, C_BORDER);
        Gui.drawRect(x + PANEL_WIDTH - 1, y, x + PANEL_WIDTH, y + height, C_BORDER);
    }

    /** 画一组 "标签 值"，返回下一个可用的 x */
    private int drawPair(FontRenderer fr, int x, int y, String label, String value, int valueColor) {
        fr.drawStringWithShadow(label, x, y, C_LABEL);
        int vx = x + fr.getStringWidth(label) + 3;
        fr.drawStringWithShadow(value, vx, y, valueColor);
        return vx + fr.getStringWidth(value) + 6;
    }

    /** 画一个按键方框，返回下一个方块的 x */
    private int drawKey(FontRenderer fr, int x, int y, int w, String label, boolean active) {
        Gui.drawRect(x, y, x + w, y + 12, active ? 0xCC1E5B33 : 0x66000000);
        Gui.drawRect(x, y, x + w, y + 1, active ? 0xFF55FF55 : 0x553A6EA5);
        fr.drawStringWithShadow(label, x + (w - fr.getStringWidth(label)) / 2, y + 2,
                active ? 0xFFFFFFFF : 0xFF9E9E9E);
        return x + w + 1;
    }

    private void drawCadenceBars(PracticeSession session, int x, int baseY, int width, int height) {
        List<Long> intervals = session.getIntervals();
        int slots = Math.max(1, width / 5);
        int start = Math.max(0, intervals.size() - slots);
        int idx = 0;
        for (int i = start; i < intervals.size(); i++) {
            long v = intervals.get(i).longValue();
            float t = (v - 140.0F) / (560.0F - 140.0F);
            if (t < 0.0F) {
                t = 0.0F;
            } else if (t > 1.0F) {
                t = 1.0F;
            }
            int barHeight = 2 + Math.round((height - 2) * (1.0F - t));
            int color = lerpColor(0xFF55FF55, 0xFFFF4433, t);
            int bx = x + idx * 5;
            Gui.drawRect(bx, baseY + height - barHeight, bx + 4, baseY + height, color);
            idx++;
        }
        for (; idx < slots; idx++) {
            int bx = x + idx * 5;
            Gui.drawRect(bx, baseY + height - 2, bx + 4, baseY + height, 0x30FFFFFF);
        }
    }

    private void drawPitchGauge(int x, int y, int width, float pitch) {
        Gui.drawRect(x, y, x + width, y + 5, 0x40000000);
        int bandStart = x + (int) (width * clamp01(TBTConfig.pitchMin / 90.0F));
        int bandEnd = x + (int) (width * clamp01(TBTConfig.pitchMax / 90.0F));
        Gui.drawRect(bandStart, y, bandEnd, y + 5, 0x5533CC66);
        int marker = x + (int) (width * clamp01(pitch / 90.0F));
        Gui.drawRect(marker - 1, y - 1, marker + 1, y + 6, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ 小工具

    private static int jitterColor(double mean, double jitter) {
        double ratio = mean <= 0.0D ? 0.0D : jitter / mean;
        if (ratio <= 0.15D) {
            return C_GOOD;
        }
        if (ratio <= 0.35D) {
            return C_WARN;
        }
        return C_BAD;
    }

    private static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >>> 24) & 0xFF;
        int r1 = (c1 >>> 16) & 0xFF;
        int g1 = (c1 >>> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF;
        int r2 = (c2 >>> 16) & 0xFF;
        int g2 = (c2 >>> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp01(float v) {
        if (v < 0.0F) {
            return 0.0F;
        }
        return v > 1.0F ? 1.0F : v;
    }

    private static String fmt(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}
