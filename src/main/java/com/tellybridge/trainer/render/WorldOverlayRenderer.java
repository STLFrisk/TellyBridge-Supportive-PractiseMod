package com.tellybridge.trainer.render;

import com.tellybridge.trainer.ClientProxy;
import com.tellybridge.trainer.config.TBTConfig;
import com.tellybridge.trainer.core.AimHint;
import com.tellybridge.trainer.core.PracticeSession;
import com.tellybridge.trainer.core.Stats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 世界内叠加层：
 *   1) 绿色参考框：你当前准星将要把方块放到的那一格（纯本地 rayTrace，不含任何辅助瞄准）
 *   2) 蓝色线框：本次练习已经放下的方块，用来回看自己的线路是否直
 *   3) 橙色线框 + 折线：上一次破纪录的幽灵路线
 */
public class WorldOverlayRenderer {

    private static final int MAX_PLACED_DRAWN = 64;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        PracticeSession session = ClientProxy.session;
        if (session == null) {
            return;
        }

        float pt = event.partialTicks;
        EntityPlayerSP player = mc.thePlayer;
        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * pt;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * pt;
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * pt;

        boolean textureWasOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean depthWasOn = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendWasOn = GL11.glIsEnabled(GL11.GL_BLEND);
        MovingObjectPosition mop = null;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0F);

        if (TBTConfig.showTargetBox || TBTConfig.hintEnabled) {
            Entity view = mc.getRenderViewEntity();
            if (view != null) {
                mop = view.rayTrace(5.0D, pt);
            }
        }
        if (TBTConfig.hintEnabled) {
            AimHint.INSTANCE.update(mc, mop, pt);
        } else {
            AimHint.INSTANCE.clear();
        }

        if (TBTConfig.showTargetBox) {
            renderTargetBox(mc, mop, camX, camY, camZ);
        }
        if (TBTConfig.hintEnabled) {
            renderAimHint(mc, camX, camY, camZ);
        }
        if (TBTConfig.showPlacedBlocks) {
            renderPlacedBlocks(session, camX, camY, camZ);
        }
        if (TBTConfig.showGhost) {
            if (TBTConfig.ghostXray) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            renderGhost(camX, camY, camZ);
            if (TBTConfig.ghostXray && depthWasOn) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
        }

        GL11.glLineWidth(1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();

        // 精确还原，保证 GlStateManager 里的状态缓存与实际 GL 状态一致
        if (!blendWasOn) {
            GL11.glDisable(GL11.GL_BLEND);
        }
        if (textureWasOn) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
        if (depthWasOn) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
    }

    // ------------------------------------------------------------------ 各部分

    private void renderTargetBox(Minecraft mc, MovingObjectPosition mop, double camX, double camY, double camZ) {
        EntityPlayerSP player = mc.thePlayer;
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            return;
        }
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        BlockPos target = mop.getBlockPos().offset(mop.sideHit);
        if (target == null || !mc.theWorld.isAirBlock(target)) {
            return;
        }
        // 准星自己的落点框：对上了就和绿色幽灵方块重合，不再重复画；没对上用蓝色提示"会放歪"
        AimHint hint = AimHint.INSTANCE;
        if (hint.valid) {
            if (hint.aligned) {
                return;
            }
            GL11.glColor4f(0.40F, 0.72F, 1.0F, 0.80F);
        } else {
            GL11.glColor4f(0.35F, 1.0F, 0.55F, 0.95F);
        }
        box(target.getX() - camX, target.getY() - camY, target.getZ() - camZ, 1.0D);
    }

    /**
     * 落点提示：目标格画成半透明绿色实体方块（幽灵方块），瞄点画成白色小十字。
     * 对上了填充更实一点，相当于"这一块就是它了"。
     */
    private void renderAimHint(Minecraft mc, double camX, double camY, double camZ) {
        AimHint hint = AimHint.INSTANCE;
        if (!hint.valid || hint.targetCell == null) {
            return;
        }
        double bx = hint.targetCell.getX() - camX;
        double by = hint.targetCell.getY() - camY;
        double bz = hint.targetCell.getZ() - camZ;

        boolean cullWasOn = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        if (TBTConfig.hintFill) {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);   // 只上色不写深度，避免把自己的描边遮掉
            fillCube(bx, by, bz, 0.30F, 1.0F, 0.42F, hint.aligned ? 0.34F : 0.22F);
            GL11.glDepthMask(true);
            if (cullWasOn) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            }
        }

        GL11.glColor4f(0.35F, 1.0F, 0.45F, 0.95F);
        GL11.glLineWidth(2.5F);
        box(bx, by, bz, 1.0D);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.85F);
        GL11.glLineWidth(1.5F);
        cross3D(hint.aimX - camX, hint.aimY - camY, hint.aimZ - camZ, 0.10D);

        if (TBTConfig.hintGuideLine) {
            EntityPlayerSP p = mc.thePlayer;
            double ex = p.posX - camX;
            double ey = p.posY + (double) p.getEyeHeight() - camY;
            double ez = p.posZ - camZ;
            GL11.glColor4f(0.35F, 1.0F, 0.45F, 0.35F);
            GL11.glLineWidth(1.0F);
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer wr = tessellator.getWorldRenderer();
            wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
            wr.pos(ex, ey, ez).endVertex();
            wr.pos(hint.aimX - camX, hint.aimY - camY, hint.aimZ - camZ).endVertex();
            tessellator.draw();
        }
        GL11.glLineWidth(2.0F);
    }

    /** 半透明实心立方体：六面各一个 quad */
    private void fillCube(double x, double y, double z, float r, float g, float b, float a) {
        double x1 = x + 1.0D;
        double y1 = y + 1.0D;
        double z1 = z + 1.0D;
        GL11.glColor4f(r, g, b, a);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        // 底面
        wr.pos(x, y, z).endVertex();
        wr.pos(x1, y, z).endVertex();
        wr.pos(x1, y, z1).endVertex();
        wr.pos(x, y, z1).endVertex();
        // 顶面
        wr.pos(x, y1, z1).endVertex();
        wr.pos(x1, y1, z1).endVertex();
        wr.pos(x1, y1, z).endVertex();
        wr.pos(x, y1, z).endVertex();
        // -Z
        wr.pos(x, y, z).endVertex();
        wr.pos(x, y1, z).endVertex();
        wr.pos(x1, y1, z).endVertex();
        wr.pos(x1, y, z).endVertex();
        // +Z
        wr.pos(x, y, z1).endVertex();
        wr.pos(x1, y, z1).endVertex();
        wr.pos(x1, y1, z1).endVertex();
        wr.pos(x, y1, z1).endVertex();
        // -X
        wr.pos(x, y, z).endVertex();
        wr.pos(x, y, z1).endVertex();
        wr.pos(x, y1, z1).endVertex();
        wr.pos(x, y1, z).endVertex();
        // +X
        wr.pos(x1, y, z).endVertex();
        wr.pos(x1, y1, z).endVertex();
        wr.pos(x1, y1, z1).endVertex();
        wr.pos(x1, y, z1).endVertex();
        tessellator.draw();
    }

    /** 一个 3D 小十字：告诉玩家"准星应该落在这个点上" */
    private void cross3D(double x, double y, double z, double size) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        wr.pos(x - size, y, z).endVertex();
        wr.pos(x + size, y, z).endVertex();
        wr.pos(x, y - size, z).endVertex();
        wr.pos(x, y + size, z).endVertex();
        wr.pos(x, y, z - size).endVertex();
        wr.pos(x, y, z + size).endVertex();
        tessellator.draw();
    }

    private void renderPlacedBlocks(PracticeSession session, double camX, double camY, double camZ) {
        List<BlockPos> placed = session.getPlaced();
        if (placed.isEmpty()) {
            return;
        }
        int from = Math.max(0, placed.size() - MAX_PLACED_DRAWN);
        GL11.glColor4f(0.35F, 0.72F, 1.0F, 0.55F);
        for (int i = from; i < placed.size(); i++) {
            BlockPos p = placed.get(i);
            box(p.getX() - camX, p.getY() - camY, p.getZ() - camZ, 1.0D);
        }
    }

    private void renderGhost(double camX, double camY, double camZ) {
        if (ClientProxy.stats == null) {
            return;
        }
        Stats stats = ClientProxy.stats.get();
        List<double[]> path = stats.ghostPath;
        if (path != null && path.size() >= 2) {
            GL11.glColor4f(1.0F, 0.62F, 0.20F, 0.85F);
            GL11.glLineWidth(2.5F);
            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer wr = tessellator.getWorldRenderer();
            wr.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
            for (int i = 0; i < path.size(); i++) {
                double[] p = path.get(i);
                if (p == null || p.length < 3) {
                    continue;
                }
                wr.pos(p[0] - camX, p[1] - camY + 0.08D, p[2] - camZ).endVertex();
            }
            tessellator.draw();
            GL11.glLineWidth(2.0F);
        }

        List<double[]> blocks = stats.ghostBlocks;
        if (blocks != null && !blocks.isEmpty()) {
            GL11.glColor4f(1.0F, 0.62F, 0.20F, 0.45F);
            for (int i = 0; i < blocks.size(); i++) {
                double[] b = blocks.get(i);
                if (b == null || b.length < 3) {
                    continue;
                }
                box(b[0] - camX, b[1] - camY, b[2] - camZ, 1.0D);
            }
        }
    }

    // ------------------------------------------------------------------ 基础线框

    /** 以相对相机的坐标画一个 size 边长的立方体线框 */
    private void box(double x, double y, double z, double size) {
        double x1 = x + size;
        double y1 = y + size;
        double z1 = z + size;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);

        // 底面
        v(wr, x, y, z, x1, y, z);
        v(wr, x1, y, z, x1, y, z1);
        v(wr, x1, y, z1, x, y, z1);
        v(wr, x, y, z1, x, y, z);
        // 顶面
        v(wr, x, y1, z, x1, y1, z);
        v(wr, x1, y1, z, x1, y1, z1);
        v(wr, x1, y1, z1, x, y1, z1);
        v(wr, x, y1, z1, x, y1, z);
        // 四条竖边
        v(wr, x, y, z, x, y1, z);
        v(wr, x1, y, z, x1, y1, z);
        v(wr, x1, y, z1, x1, y1, z1);
        v(wr, x, y, z1, x, y1, z1);

        tessellator.draw();
    }

    private void v(WorldRenderer wr, double x1, double y1, double z1, double x2, double y2, double z2) {
        wr.pos(x1, y1, z1).endVertex();
        wr.pos(x2, y2, z2).endVertex();
    }
}
