package com.tellybridge.trainer;

import com.tellybridge.trainer.command.CommandTelly;
import com.tellybridge.trainer.config.TBTConfig;
import com.tellybridge.trainer.core.KeyHandler;
import com.tellybridge.trainer.core.PlacementTracker;
import com.tellybridge.trainer.core.PracticeSession;
import com.tellybridge.trainer.core.StatsStore;
import com.tellybridge.trainer.hud.HudRenderer;
import com.tellybridge.trainer.render.WorldOverlayRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;

/**
 * 客户端入口：注册按键、事件监听器与客户端命令。
 */
public class ClientProxy extends CommonProxy {

    public static final KeyBinding KEY_HUD =
            new KeyBinding("key.telly.hud", Keyboard.KEY_H, "key.categories.tellybridge");
    public static final KeyBinding KEY_GHOST =
            new KeyBinding("key.telly.ghost", Keyboard.KEY_G, "key.categories.tellybridge");
    public static final KeyBinding KEY_RESET =
            new KeyBinding("key.telly.reset", Keyboard.KEY_R, "key.categories.tellybridge");
    public static final KeyBinding KEY_SAVE =
            new KeyBinding("key.telly.save", Keyboard.KEY_J, "key.categories.tellybridge");

    /** 当前练习会话（统计数据都在里面） */
    public static PracticeSession session;
    /** 历史最佳记录 + 幽灵路线 */
    public static StatsStore stats;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        File dir = new File(event.getModConfigurationDirectory(), "tellybridge");
        if (!dir.exists() && !dir.mkdirs()) {
            TellyBridgeTrainer.logger().warn("[TellyBridge] 无法创建配置目录: " + dir.getAbsolutePath());
        }
        TBTConfig.load(new File(dir, "config.cfg"));
        stats = new StatsStore(new File(dir, "stats.json"));
        session = new PracticeSession(stats);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ClientRegistry.registerKeyBinding(KEY_HUD);
        ClientRegistry.registerKeyBinding(KEY_GHOST);
        ClientRegistry.registerKeyBinding(KEY_RESET);
        ClientRegistry.registerKeyBinding(KEY_SAVE);

        MinecraftForge.EVENT_BUS.register(session);              // 每 tick 采集跳跃/轨迹
        MinecraftForge.EVENT_BUS.register(new PlacementTracker(session));
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
        MinecraftForge.EVENT_BUS.register(new HudRenderer());     // 2D 面板
        MinecraftForge.EVENT_BUS.register(new WorldOverlayRenderer()); // 3D 参考框 / 幽灵

        ClientCommandHandler.instance.registerCommand(new CommandTelly());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        TBTConfig.save();
        if (stats != null) {
            stats.save();
        }
    }
}
