package com.tellybridge.trainer.core;

import com.tellybridge.trainer.config.TBTConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * telly 的落点提示。
 *
 * 动作结构：向前疾跑 → 起跳 → 空中回头看 → 把方块放到脚下那一格。
 * 于是这里算两件事：
 *
 *   1) 目标格 targetCell —— 脚下面朝下的那一格。只有它还是空气（说明你正悬在缺口上方）
 *      才有意义；站在桥上时脚下是实心，直接不提示。
 *   2) 瞄点 aimPoint —— 想填这一格，必须点击「相邻支撑块朝向它的那个面」。
 *      取离眼睛最近的那个可点击面，面中心就是准星应该落到的地方。
 *
 * 然后由 requiredYaw/requiredPitch 换算出「还要转多少度」，deltaYaw>0 表示要往右转，
 * deltaPitch>0 表示要往下压（MC 的 pitch 正值朝下）。
 *
 * 坐标换算：世界坐标 → 相机坐标 → 裁剪空间 → 屏幕 GUI 坐标，
 * 这样屏幕上能直接画一个「幽灵准星」在该瞄的位置，而不是只能看数字。
 *
 * 纯提示：只读自己的位置/准星和世界方块，不做任何瞄准或放置。
 */
public final class AimHint {

    public static final AimHint INSTANCE = new AimHint();

    private static final EnumFacing[] FACES = EnumFacing.values();
    /** 沿桥轴最多往外找几格 */
    private static final int SCAN_RANGE = 4;
    /** 超过这个距离放不到 */
    private static final double MAX_REACH = 5.0D;
    private static final FloatBuffer MAT_MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer MAT_PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);

    /** 桥面所在的方块层（起跳后沿用起跳时的值） */
    private int bridgeLevel = Integer.MIN_VALUE;

    /** 本帧是否算出了有效的提示 */
    public boolean valid;
    /** 应该填的那一格（脚下） */
    public BlockPos targetCell;
    /** 需要点击的支撑方块 */
    public BlockPos supportBlock;
    /** 支撑方块朝向目标格的那一面 */
    public EnumFacing supportFace;
    /** 瞄点（支撑块该面上的中心，世界坐标） */
    public double aimX;
    public double aimY;
    public double aimZ;
    /** 准星应该指向的 yaw / pitch */
    public float requiredYaw;
    public float requiredPitch;
    /** 与当前视角的差值（度） */
    public float deltaYaw;
    public float deltaPitch;
    /** 当前准星是否已经会放到目标格 */
    public boolean aligned;
    /** 瞄点是否投影在屏幕内 */
    public boolean onScreen;
    public float screenX;
    public float screenY;

    private AimHint() {
    }

    public void clear() {
        valid = false;
        onScreen = false;
        targetCell = null;
        supportBlock = null;
        supportFace = null;
        aligned = false;
    }

    /**
     * @param mop 本帧的准星命中结果（由渲染层用 partialTicks 重新 rayTrace 得到）
     */
    public void update(Minecraft mc, MovingObjectPosition mop, float partialTicks) {
        clear();
        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        World world = mc == null ? null : mc.theWorld;
        if (player == null || world == null) {
            return;
        }

        // 桥面所在的那一层：站着时按脚下算，跳起来之后沿用起跳时的层
        int feetLevel = (int) Math.floor(player.posY) - 1;
        if (player.onGround) {
            bridgeLevel = feetLevel;
            if (TBTConfig.hintAirOnly) {
                // 站在方块上（桥面/地面）没有要补的缺口，不提示
                return;
            }
        } else if (bridgeLevel == Integer.MIN_VALUE) {
            bridgeLevel = feetLevel;
        }
        int level = bridgeLevel;

        // 水平轴：优先用实际移动方向；站着不动时用朝向
        double ax = player.posX - player.lastTickPosX;
        double az = player.posZ - player.lastTickPosZ;
        double len = Math.sqrt(ax * ax + az * az);
        if (len < 0.02D) {
            double yawRad = Math.toRadians(player.rotationYaw);
            ax = -Math.sin(yawRad);
            az = Math.cos(yawRad);
        } else {
            ax /= len;
            az /= len;
        }

        double eyeX = player.posX;
        double eyeY = player.posY + (double) player.getEyeHeight();
        double eyeZ = player.posZ;

        // 从正脚下（t=0）开始，沿轴两侧由近到远扫，找第一个"能放"的空格。
        // 桥体本身是实心会被跳过；越过缺口、紧贴桥末端的那一格才贴得上方块，
        // 所以这样找到的正是"该补的下一块"，离人 0~4 格都可以。
        for (int t = 0; t <= SCAN_RANGE && !valid; t++) {
            int signStart = (t == 0) ? 1 : -1;
            for (int sign = signStart; sign <= 1 && !valid; sign += 2) {
                BlockPos cell = new BlockPos(
                        (int) Math.floor(player.posX + sign * ax * t),
                        level,
                        (int) Math.floor(player.posZ + sign * az * t));
                if (!world.isAirBlock(cell)) {
                    continue;
                }
                if (!pickFace(world, cell, eyeX, eyeY, eyeZ)) {
                    continue;   // 四周没有可贴的面，放了也贴不上
                }
                double dx = aimX - eyeX;
                double dy = aimY - eyeY;
                double dz = aimZ - eyeZ;
                if (dx * dx + dy * dy + dz * dz > MAX_REACH * MAX_REACH) {
                    continue;   // 够不着
                }
                valid = true;
                targetCell = cell;
            }
        }
        if (!valid) {
            return;
        }

        double dx = aimX - eyeX;
        double dy = aimY - eyeY;
        double dz = aimZ - eyeZ;
        requiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        requiredPitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        deltaYaw = wrapDegrees(requiredYaw - player.rotationYaw);
        deltaPitch = requiredPitch - player.rotationPitch;

        aligned = mop != null
                && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && targetCell.equals(mop.getBlockPos().offset(mop.sideHit));

        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) partialTicks;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) partialTicks;
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) partialTicks;
        project(mc, camX, camY, camZ);
    }

    /**
     * 给目标格挑一个"离眼睛最近的可点击面"，写进 supportBlock / supportFace / aimX/Y/Z。
     * 四个方向都没有可贴的面时返回 false（这一格放不了）。
     */
    private boolean pickFace(World world, BlockPos target, double eyeX, double eyeY, double eyeZ) {
        double bestDist = Double.MAX_VALUE;
        BlockPos bestNeighbor = null;
        EnumFacing bestFace = null;
        double bestX = 0.0D;
        double bestY = 0.0D;
        double bestZ = 0.0D;

        for (int i = 0; i < FACES.length; i++) {
            EnumFacing face = FACES[i];
            BlockPos neighbor = target.offset(face);
            if (!world.isSideSolid(neighbor, face.getOpposite(), false)) {
                continue;
            }
            // 邻居朝向目标格那一面的中心 = 邻居中心 - 0.5 * 方向
            double cx = neighbor.getX() + 0.5D - 0.5D * face.getFrontOffsetX();
            double cy = neighbor.getY() + 0.5D - 0.5D * face.getFrontOffsetY();
            double cz = neighbor.getZ() + 0.5D - 0.5D * face.getFrontOffsetZ();
            double ddx = cx - eyeX;
            double ddy = cy - eyeY;
            double ddz = cz - eyeZ;
            double dist = ddx * ddx + ddy * ddy + ddz * ddz;
            if (dist < bestDist) {
                bestDist = dist;
                bestNeighbor = neighbor;
                bestFace = face;
                bestX = cx;
                bestY = cy;
                bestZ = cz;
            }
        }
        if (bestNeighbor == null) {
            return false;
        }
        supportBlock = bestNeighbor;
        supportFace = bestFace;
        aimX = bestX;
        aimY = bestY;
        aimZ = bestZ;
        return true;
    }

    /** 世界坐标 → 屏幕 GUI 坐标，供 HUD 画幽灵准星 */
    private void project(Minecraft mc, double camX, double camY, double camZ) {
        MAT_MODELVIEW.clear();
        MAT_PROJECTION.clear();
        VIEWPORT.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MAT_MODELVIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, MAT_PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);

        float x = (float) (aimX - camX);
        float y = (float) (aimY - camY);
        float z = (float) (aimZ - camZ);

        // 物体空间 → 相机空间
        float ex = MAT_MODELVIEW.get(0) * x + MAT_MODELVIEW.get(4) * y + MAT_MODELVIEW.get(8) * z + MAT_MODELVIEW.get(12);
        float ey = MAT_MODELVIEW.get(1) * x + MAT_MODELVIEW.get(5) * y + MAT_MODELVIEW.get(9) * z + MAT_MODELVIEW.get(13);
        float ez = MAT_MODELVIEW.get(2) * x + MAT_MODELVIEW.get(6) * y + MAT_MODELVIEW.get(10) * z + MAT_MODELVIEW.get(14);
        float ew = MAT_MODELVIEW.get(3) * x + MAT_MODELVIEW.get(7) * y + MAT_MODELVIEW.get(11) * z + MAT_MODELVIEW.get(15);

        // 相机空间 → 裁剪空间
        float cx = MAT_PROJECTION.get(0) * ex + MAT_PROJECTION.get(4) * ey + MAT_PROJECTION.get(8) * ez + MAT_PROJECTION.get(12) * ew;
        float cy = MAT_PROJECTION.get(1) * ex + MAT_PROJECTION.get(5) * ey + MAT_PROJECTION.get(9) * ez + MAT_PROJECTION.get(13) * ew;
        float cz = MAT_PROJECTION.get(2) * ex + MAT_PROJECTION.get(6) * ey + MAT_PROJECTION.get(10) * ez + MAT_PROJECTION.get(14) * ew;
        float cw = MAT_PROJECTION.get(3) * ex + MAT_PROJECTION.get(7) * ey + MAT_PROJECTION.get(11) * ez + MAT_PROJECTION.get(15) * ew;

        if (cw <= 0.0001F) {
            return; // 在相机背后
        }
        float ndcX = cx / cw;
        float ndcY = cy / cw;
        float ndcZ = cz / cw;
        if (ndcZ < -1.0F || ndcZ > 1.0F) {
            return;
        }

        int vpW = VIEWPORT.get(2);
        int vpH = VIEWPORT.get(3);
        if (vpW <= 0 || vpH <= 0) {
            return;
        }
        float winX = (ndcX * 0.5F + 0.5F) * vpW;
        float winY = (ndcY * 0.5F + 0.5F) * vpH; // 原点在左下

        ScaledResolution sr = new ScaledResolution(mc);
        float scaledWidth = sr.getScaledWidth();
        float scaledHeight = sr.getScaledHeight();
        screenX = winX / vpW * scaledWidth;
        screenY = (1.0F - winY / vpH) * scaledHeight;
        onScreen = screenX >= -8.0F && screenX <= scaledWidth + 8.0F
                && screenY >= -8.0F && screenY <= scaledHeight + 8.0F;
    }

    private static float wrapDegrees(float angle) {
        float a = angle % 360.0F;
        if (a >= 180.0F) {
            a -= 360.0F;
        }
        if (a < -180.0F) {
            a += 360.0F;
        }
        return a;
    }
}
