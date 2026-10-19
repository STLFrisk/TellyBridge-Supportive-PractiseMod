package com.tellybridge.trainer.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 方块放置检测。
 *
 * 关键点：不能简单地"右键一次就算一块"，因为在创造模式 / 服务器上：
 *   * 右键可能被服务端拒绝（保护区、没有支撑面、距离过远）；
 *   * 右键也可能根本没放下方块（点到箱子、告示牌等交互方块）。
 * 所以这里的做法是：右键时记下"理论落点"，随后几个 tick 里观察世界，
 * 只有该位置真的出现了方块才计入一次成功放置。这样统计到的方块数
 * 就是服务端认可的真实进度，和自己数方块一致。
 *
 * 数据来源有两条，互为兜底：
 *   1) PlayerInteractEvent（右键方块时触发，最准）
 *   2) 按住右键时用准星目标作为候选（防止某些情况下事件没有走到）
 * 两条来源共用同一份 pending 列表，并用 counted 去重，不会重复计数。
 */
public class PlacementTracker {

    /** 候选落点的有效期（tick）。给高延迟服务器留足确认时间。 */
    private static final int PLACE_TIMEOUT_TICKS = 60;
    /** 同一坐标的去重窗口（tick） */
    private static final int DUPLICATE_WINDOW_TICKS = 40;

    private final PracticeSession session;
    private final List<Pending> pending = new ArrayList<Pending>();
    private final Map<Long, Integer> counted = new HashMap<Long, Integer>();
    private int ticks;

    public PlacementTracker(PracticeSession session) {
        this.session = session;
    }

    private static final class Pending {
        final BlockPos pos;
        final int expireTick;

        Pending(BlockPos pos, int expireTick) {
            this.pos = pos;
            this.expireTick = expireTick;
        }
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.pos == null || event.face == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        if (player == null || event.entityPlayer != player) {
            return;
        }
        if (player.worldObj == null || !player.worldObj.isRemote) {
            return;
        }
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            return;
        }
        BlockPos target = event.pos.offset(event.face);
        if (!player.worldObj.isAirBlock(target)) {
            return;
        }
        offer(target);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        if (player == null || mc.theWorld == null) {
            pending.clear();
            counted.clear();
            return;
        }

        // 兜底来源：按住右键时把准星落点作为候选
        if (mc.gameSettings != null && mc.gameSettings.keyBindUseItem.isKeyDown()) {
            ItemStack held = player.getHeldItem();
            if (held != null && held.getItem() instanceof ItemBlock) {
                MovingObjectPosition mop = mc.objectMouseOver;
                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    BlockPos target = mop.getBlockPos().offset(mop.sideHit);
                    if (mc.theWorld.isAirBlock(target)) {
                        offer(target);
                    }
                }
            }
        }

        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (ticks > p.expireTick) {
                it.remove();
                continue;
            }
            if (!mc.theWorld.isAirBlock(p.pos)) {
                it.remove();
                long key = p.pos.toLong();
                Integer last = counted.get(Long.valueOf(key));
                if (last != null && ticks - last.intValue() < DUPLICATE_WINDOW_TICKS) {
                    continue;
                }
                counted.put(Long.valueOf(key), Integer.valueOf(ticks));
                session.onBlockPlaced(p.pos);
            }
        }

        if ((ticks & 63) == 0) {
            Iterator<Map.Entry<Long, Integer>> e = counted.entrySet().iterator();
            while (e.hasNext()) {
                if (ticks - e.next().getValue().intValue() > 200) {
                    e.remove();
                }
            }
        }
    }

    private void offer(BlockPos pos) {
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).pos.equals(pos)) {
                return;
            }
        }
        pending.add(new Pending(pos, ticks + PLACE_TIMEOUT_TICKS));
    }

    /** 断线 / 换世界时清空中间状态 */
    public void clear() {
        pending.clear();
        counted.clear();
    }
}
