package com.tellybridge.trainer.core;

import com.tellybridge.trainer.ClientProxy;
import com.tellybridge.trainer.config.TBTConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 热键处理。采用"按下沿"检测（tick 轮询 + 状态记忆），
 * 这样按住不放只触发一次，也不会在打开 GUI 时误触。
 */
public class KeyHandler {

    private boolean hudDown;
    private boolean ghostDown;
    private boolean resetDown;
    private boolean saveDown;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        boolean blocked = mc.thePlayer == null || mc.currentScreen != null;

        boolean hudNow = !blocked && ClientProxy.KEY_HUD.isKeyDown();
        if (hudNow && !hudDown) {
            TBTConfig.hudEnabled = !TBTConfig.hudEnabled;
            TBTConfig.save();
        }
        hudDown = hudNow;

        boolean ghostNow = !blocked && ClientProxy.KEY_GHOST.isKeyDown();
        if (ghostNow && !ghostDown) {
            TBTConfig.showGhost = !TBTConfig.showGhost;
            TBTConfig.save();
            chat("幽灵路线: " + (TBTConfig.showGhost ? "\u00A7a开" : "\u00A7c关"));
        }
        ghostDown = ghostNow;

        boolean resetNow = !blocked && ClientProxy.KEY_RESET.isKeyDown();
        if (resetNow && !resetDown) {
            if (ClientProxy.session != null) {
                ClientProxy.session.reset();
            }
            chat("本次练习已重置");
        }
        resetDown = resetNow;

        boolean saveNow = !blocked && ClientProxy.KEY_SAVE.isKeyDown();
        if (saveNow && !saveDown) {
            if (ClientProxy.session != null) {
                ClientProxy.session.saveGhost();
            }
            if (ClientProxy.stats != null) {
                ClientProxy.stats.save();
            }
            chat("已保存幽灵路线与历史记录");
        }
        saveDown = saveNow;
    }

    private static void chat(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("\u00A7b[Telly]\u00A7r " + text));
        }
    }
}
