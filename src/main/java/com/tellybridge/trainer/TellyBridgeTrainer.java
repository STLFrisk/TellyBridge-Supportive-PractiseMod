package com.tellybridge.trainer;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * TellyBridge Trainer —— Minecraft 1.8.9 Forge 客户端的 telly bridge 练习辅助模组。
 *
 * 设计原则（重要）：
 *   * 纯客户端。只读取本地玩家的输入、准星与世界方块状态，用来绘制反馈面板。
 *   * 不发送任何额外的动作数据包，不做自动放置 / 自动瞄准 / 自动转向 / 修改触及距离。
 *   * 所有数字都来自你自己的操作，相当于一个"节奏与轨迹的仪表盘"。
 */
@Mod(modid = TellyBridgeTrainer.MODID,
        name = TellyBridgeTrainer.NAME,
        version = TellyBridgeTrainer.VERSION,
        acceptedMinecraftVersions = "[1.8.9]")
public class TellyBridgeTrainer {

    public static final String MODID = "tellybridge";
    public static final String NAME = "TellyBridge Trainer";
    public static final String VERSION = "1.0.0";

    private static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static TellyBridgeTrainer instance;

    @SidedProxy(clientSide = "com.tellybridge.trainer.ClientProxy",
            serverSide = "com.tellybridge.trainer.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    public static Logger logger() {
        return LOGGER;
    }
}
