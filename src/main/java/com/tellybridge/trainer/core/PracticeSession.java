package com.tellybridge.trainer.core;

import com.tellybridge.trainer.config.TBTConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次 telly 练习的采集与统计。
 *
 * 统计口径（HUD 上都会显示）：
 *   Blocks    本次练习成功放下的方块数（由 PlacementTracker 实测世界变化确认）
 *   Time      从第一个方块到最近一个方块的时间
 *   Rate      Blocks / Time，方块每秒，衡量推进速度
 *   Cadence   相邻两次放置的时间间隔均值（毫秒），也就是你的"手速节奏"
 *   Jitter    间隔的标准差（毫秒），越小说明节奏越稳
 *   J>P       起跳 → 空中第一块 的延迟，telly 的核心手感指标
 *   B/Jump    平均每跳放出多少方块
 *   Miss      起跳后落地却一块都没放出来的次数（空跳）
 */
public class PracticeSession {

    private static final int MAX_INTERVALS = 32;
    private static final int MAX_TRAIL = 2400;
    private static final int MAX_PLACED_KEPT = 512;
    /** Rate 统计要求的最少方块数，避免 1~2 块刷出假纪录 */
    private static final int MIN_BLOCKS_FOR_BEST = 8;

    private final StatsStore store;

    private boolean running;
    private long startMs;
    private long lastPlaceMs;
    private long lastJumpMs;
    private int blocks;
    private int jumps;
    private int misses;

    private boolean placedThisAir;
    private boolean wasOnGround = true;
    private double lastGap;
    private long lastJumpToPlaceMs = -1L;
    private double jumpToPlaceEma = -1.0D;

    /**
     * 一次跳跃的"滞空窗口"。
     * telly 的核心约束是：必须在落回起跳高度之前，把方块一路连到自己脚下。
     * 所以这里用上一次真实滞空时长当基准，实时倒计时剩余时间。
     */
    private long jumpStartMs;
    private long lastAirMs = 1100L;
    /** 本次跳跃已经放了几块 */
    private int blocksThisJump;
    /** 上一次跳跃一共放了几块（落地后显示用） */
    private int lastJumpBlocks;
    /** 水平速度（格/秒，EMA 平滑），用来检查回头放块时有没有掉速 */
    private double speed;
    private double lastPosX;
    private double lastPosZ;
    private long lastTickMs;

    private final List<Long> intervals = new ArrayList<Long>();
    private final List<BlockPos> placed = new ArrayList<BlockPos>();
    private final List<double[]> trail = new ArrayList<double[]>();
    private long lastGhostSaveMs;

    public PracticeSession(StatsStore store) {
        this.store = store;
    }

    // ------------------------------------------------------------------ 生命周期

    public void start() {
        running = true;
        startMs = System.currentTimeMillis();
        lastPlaceMs = 0L;
        lastJumpMs = 0L;
        blocks = 0;
        jumps = 0;
        misses = 0;
        placedThisAir = false;
        lastGap = 0.0D;
        lastJumpToPlaceMs = -1L;
        jumpToPlaceEma = -1.0D;
        intervals.clear();
        placed.clear();
        trail.clear();
    }

    /** 结束当前练习并结算纪录；未在练习中调用则无事发生 */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        settle();
    }

    /** 直接清空，不结算（R 键 / /telly reset） */
    public void reset() {
        running = false;
        startMs = 0L;
        lastPlaceMs = 0L;
        lastJumpMs = 0L;
        blocks = 0;
        jumps = 0;
        misses = 0;
        placedThisAir = false;
        lastGap = 0.0D;
        lastJumpToPlaceMs = -1L;
        jumpToPlaceEma = -1.0D;
        jumpStartMs = 0L;
        blocksThisJump = 0;
        lastJumpBlocks = 0;
        speed = 0.0D;
        intervals.clear();
        placed.clear();
        trail.clear();
    }

    private void settle() {
        if (store == null) {
            return;
        }
        Stats s = store.get();
        s.totalRuns++;
        s.totalBlocks += blocks;

        boolean newBest = false;
        if (blocks >= MIN_BLOCKS_FOR_BEST) {
            double rate = getRate();
            double consistency = getConsistency();
            double perJump = getBlocksPerJump();
            if (rate > s.bestRate) {
                s.bestRate = rate;
                newBest = true;
            }
            if (blocks > s.longestRun) {
                s.longestRun = blocks;
            }
            if (intervals.size() >= 8 && consistency > s.bestConsistency) {
                s.bestConsistency = consistency;
            }
            if (perJump > s.bestBlocksPerJump) {
                s.bestBlocksPerJump = perJump;
            }
        }
        if (newBest) {
            saveGhost();
        }
        if (TBTConfig.saveStats) {
            store.save();
        }
    }

    /** 把当前练习的轨迹与方块存成幽灵路线（J 键 / 破纪录时自动调用） */
    public void saveGhost() {
        if (store == null) {
            return;
        }
        Stats s = store.get();
        s.ghostPath.clear();
        s.ghostBlocks.clear();
        if (trail.isEmpty()) {
            // 手动保存且还没有轨迹时，至少把当前位置记下来
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP p = mc == null ? null : mc.thePlayer;
            if (p != null) {
                s.ghostPath.add(new double[]{p.posX, p.posY, p.posZ});
            }
        } else {
            for (int i = 0; i < trail.size(); i++) {
                double[] pt = trail.get(i);
                s.ghostPath.add(new double[]{pt[0], pt[1], pt[2]});
            }
        }
        for (int i = 0; i < placed.size(); i++) {
            BlockPos pos = placed.get(i);
            s.ghostBlocks.add(new double[]{pos.getX(), pos.getY(), pos.getZ()});
        }
        lastGhostSaveMs = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ 采样

    /** 由 PlacementTracker 在确认"世界里的方块真的出现了"之后回调 */
    public void onBlockPlaced(BlockPos pos) {
        long now = System.currentTimeMillis();

        if (!running) {
            if (!TBTConfig.autoStart) {
                return;
            }
            start();
            startMs = now; // 自动开始：从第一块算起
        }

        if (lastPlaceMs > 0L) {
            long dt = now - lastPlaceMs;
            if (dt > 0L && dt < 5000L) {
                intervals.add(Long.valueOf(dt));
                while (intervals.size() > MAX_INTERVALS) {
                    intervals.remove(0);
                }
            }
        }

        if (!placed.isEmpty()) {
            BlockPos prev = placed.get(placed.size() - 1);
            double dx = pos.getX() - prev.getX();
            double dz = pos.getZ() - prev.getZ();
            lastGap = Math.sqrt(dx * dx + dz * dz);
        }

        blocks++;
        placed.add(pos);
        while (placed.size() > MAX_PLACED_KEPT) {
            placed.remove(0);
        }
        lastPlaceMs = now;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        if (player != null && !player.onGround) {
            blocksThisJump++;
            if (!placedThisAir) {
                placedThisAir = true;
                if (lastJumpMs > 0L) {
                    long d = now - lastJumpMs;
                    if (d >= 0L && d < 2000L) {
                        lastJumpToPlaceMs = d;
                        jumpToPlaceEma = jumpToPlaceEma < 0.0D ? (double) d : jumpToPlaceEma * 0.8D + (double) d * 0.2D;
                    }
                }
            }
        }
    }

    /** 每 tick：记录轨迹、统计起跳/落地、超时自动结束 */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();

        // 水平速度（掉速是回头放块时最常见的毛病）
        double mdx = player.posX - lastPosX;
        double mdz = player.posZ - lastPosZ;
        double dt = (now - lastTickMs) / 1000.0D;
        if (dt > 0.001D && dt < 0.5D) {
            double v = Math.sqrt(mdx * mdx + mdz * mdz) / dt;
            if (v >= 0.0D && v < 40.0D) {
                speed = speed * 0.8D + v * 0.2D;
            }
        }
        lastPosX = player.posX;
        lastPosZ = player.posZ;
        lastTickMs = now;

        boolean onGround = player.onGround;

        if (wasOnGround && !onGround) {
            lastJumpMs = now;
            jumpStartMs = now;
            blocksThisJump = 0;
            placedThisAir = false;
            if (running) {
                jumps++;
            }
        } else if (!wasOnGround && onGround) {
            // 用真实滞空时长更新基准，落地后把这一跳的方块数留作显示
            long air = now - jumpStartMs;
            if (jumpStartMs > 0L && air > 150L && air < 3000L) {
                lastAirMs = air;
            }
            lastJumpBlocks = blocksThisJump;
            if (running && !placedThisAir && blocks > 0) {
                misses++;
            }
        }
        wasOnGround = onGround;

        if (running) {
            if (trail.size() < MAX_TRAIL) {
                trail.add(new double[]{player.posX, player.posY, player.posZ});
            }
            if (lastPlaceMs > 0L && now - lastPlaceMs > TBTConfig.runTimeoutMs) {
                stop();
            }
        }
    }

    // ------------------------------------------------------------------ 读数

    public boolean isRunning() {
        return running;
    }

    public int getBlocks() {
        return blocks;
    }

    public int getJumps() {
        return jumps;
    }

    public int getMisses() {
        return misses;
    }

    public double getElapsedSeconds() {
        if (startMs <= 0L || lastPlaceMs <= 0L) {
            return 0.0D;
        }
        long d = lastPlaceMs - startMs;
        return d <= 0L ? 0.0D : d / 1000.0D;
    }

    public double getRate() {
        double t = getElapsedSeconds();
        if (blocks < 2 || t <= 0.05D) {
            return 0.0D;
        }
        return blocks / t;
    }

    public double getMeanIntervalMs() {
        if (intervals.isEmpty()) {
            return 0.0D;
        }
        long sum = 0L;
        for (int i = 0; i < intervals.size(); i++) {
            sum += intervals.get(i).longValue();
        }
        return (double) sum / intervals.size();
    }

    public double getJitterMs() {
        double mean = getMeanIntervalMs();
        if (mean <= 0.0D || intervals.size() < 2) {
            return 0.0D;
        }
        double acc = 0.0D;
        for (int i = 0; i < intervals.size(); i++) {
            double d = intervals.get(i).longValue() - mean;
            acc += d * d;
        }
        return Math.sqrt(acc / intervals.size());
    }

    /** 0~100，100 表示每次间隔几乎一样 */
    public double getConsistency() {
        double mean = getMeanIntervalMs();
        if (mean <= 0.0D || intervals.size() < 2) {
            return 0.0D;
        }
        double cv = getJitterMs() / mean;
        double v = 1.0D - cv;
        if (v < 0.0D) {
            v = 0.0D;
        }
        return v * 100.0D;
    }

    public double getBlocksPerJump() {
        if (jumps <= 0) {
            return 0.0D;
        }
        return (double) blocks / jumps;
    }

    public double getLastGap() {
        return lastGap;
    }

    /** 起跳→第一块的平滑延迟；负值表示还没有数据 */
    public double getJumpToPlaceEma() {
        return jumpToPlaceEma;
    }

    public long getLastJumpToPlaceMs() {
        return lastJumpToPlaceMs;
    }

    public List<Long> getIntervals() {
        return intervals;
    }

    public List<BlockPos> getPlaced() {
        return placed;
    }

    public List<double[]> getTrail() {
        return trail;
    }

    public long getLastGhostSaveMs() {
        return lastGhostSaveMs;
    }

    /** 本次跳跃还剩多少毫秒落地（用上一次真实滞空时长做基准）；落地时返回 0 */
    public long getAirRemainingMs() {
        if (wasOnGround || jumpStartMs <= 0L) {
            return 0L;
        }
        long left = lastAirMs - (System.currentTimeMillis() - jumpStartMs);
        return left < 0L ? 0L : left;
    }

    /** 本次跳跃已放几块；落地后显示上一跳的数量 */
    public int getJumpBlocks() {
        return wasOnGround ? lastJumpBlocks : blocksThisJump;
    }

    /** 水平速度（格/秒，已平滑） */
    public double getSpeed() {
        return speed;
    }

    /** 上一次跳跃的完整滞空时长（毫秒），可作为窗口基准 */
    public long getLastAirMs() {
        return lastAirMs;
    }
}
