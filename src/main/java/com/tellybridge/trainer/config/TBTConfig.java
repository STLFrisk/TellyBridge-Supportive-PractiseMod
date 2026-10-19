package com.tellybridge.trainer.config;

import com.tellybridge.trainer.TellyBridgeTrainer;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * 极简配置：直接用 java.util.Properties，避免不同 Forge 版本 Configuration API 的差异。
 * 文件位置：config/tellybridge/config.cfg，也可以用 /telly 命令在线修改。
 */
public final class TBTConfig {

    // ---- 显示 ----
    public static boolean hudEnabled = true;
    public static int hudX = 4;
    public static int hudY = 4;
    /** HUD 缩放，百分比（50 ~ 300） */
    public static int hudScalePercent = 100;
    public static boolean showCadenceBar = true;
    public static boolean showKeys = true;
    public static boolean showPitch = true;

    // ---- 世界内参考 ----
    public static boolean showTargetBox = true;
    public static boolean showPlacedBlocks = true;
    public static boolean showGhost = true;
    /** true = 幽灵路线穿透方块显示 */
    public static boolean ghostXray = false;

    // ---- 落点提示（telly 转头 → 放块）----
    /** 总开关：目标格 + 瞄点 + 转角提示 */
    public static boolean hintEnabled = true;
    /** 屏幕上在瞄点位置画一个"幽灵准星" */
    public static boolean hintGhostCrosshair = true;
    /** 目标格画成半透明绿色幽灵方块（关掉则只留线框） */
    public static boolean hintFill = true;
    /** 只在空中提示（站在方块上不提示） */
    public static boolean hintAirOnly = true;
    /** 从眼睛到瞄点画一条引导线 */
    public static boolean hintGuideLine = false;

    // ---- 练习 ----
    public static boolean autoStart = true;
    /** 一条练习的目标方块数，0 = 不显示进度条 */
    public static int targetBlocks = 30;
    /** 目标俯仰角区间（度），用来做 HUD 上的靶带提示 */
    public static int pitchMin = 45;
    public static int pitchMax = 75;
    /** 超过这么久没有放方块就自动结束本次练习 */
    public static int runTimeoutMs = 4000;
    public static boolean saveStats = true;

    private static final Properties PROPS = new Properties();
    private static File file;

    private TBTConfig() {
    }

    public static void load(File f) {
        file = f;
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            TellyBridgeTrainer.logger().warn("[TellyBridge] 无法创建配置目录");
        }
        if (f.isFile()) {
            InputStream in = null;
            try {
                in = new FileInputStream(f);
                PROPS.load(in);
            } catch (Exception e) {
                TellyBridgeTrainer.logger().warn("[TellyBridge] 读取配置失败: " + e);
            } finally {
                close(in);
            }
        }
        pull();
    }

    /** 把 Properties 里的值读进静态字段 */
    private static void pull() {
        hudEnabled = getBool("display.hud", hudEnabled);
        hudX = getInt("display.x", hudX);
        hudY = getInt("display.y", hudY);
        hudScalePercent = clamp(getInt("display.scalePercent", hudScalePercent), 50, 300);
        showCadenceBar = getBool("display.cadenceBar", showCadenceBar);
        showKeys = getBool("display.keys", showKeys);
        showPitch = getBool("display.pitch", showPitch);

        showTargetBox = getBool("world.targetBox", showTargetBox);
        showPlacedBlocks = getBool("world.placedBlocks", showPlacedBlocks);
        showGhost = getBool("world.ghost", showGhost);
        ghostXray = getBool("world.ghostXray", ghostXray);

        hintEnabled = getBool("hint.enabled", hintEnabled);
        hintGhostCrosshair = getBool("hint.ghostCrosshair", hintGhostCrosshair);
        hintFill = getBool("hint.fill", hintFill);
        hintAirOnly = getBool("hint.airOnly", hintAirOnly);
        hintGuideLine = getBool("hint.guideLine", hintGuideLine);

        autoStart = getBool("practice.autoStart", autoStart);
        targetBlocks = clamp(getInt("practice.targetBlocks", targetBlocks), 0, 999);
        pitchMin = clamp(getInt("practice.pitchMin", pitchMin), -90, 90);
        pitchMax = clamp(getInt("practice.pitchMax", pitchMax), -90, 90);
        runTimeoutMs = clamp(getInt("practice.runTimeoutMs", runTimeoutMs), 500, 60000);
        saveStats = getBool("practice.saveStats", saveStats);
    }

    /** 把静态字段写回 Properties 并落盘 */
    public static void save() {
        PROPS.setProperty("display.hud", Boolean.toString(hudEnabled));
        PROPS.setProperty("display.x", Integer.toString(hudX));
        PROPS.setProperty("display.y", Integer.toString(hudY));
        PROPS.setProperty("display.scalePercent", Integer.toString(hudScalePercent));
        PROPS.setProperty("display.cadenceBar", Boolean.toString(showCadenceBar));
        PROPS.setProperty("display.keys", Boolean.toString(showKeys));
        PROPS.setProperty("display.pitch", Boolean.toString(showPitch));

        PROPS.setProperty("world.targetBox", Boolean.toString(showTargetBox));
        PROPS.setProperty("world.placedBlocks", Boolean.toString(showPlacedBlocks));
        PROPS.setProperty("world.ghost", Boolean.toString(showGhost));
        PROPS.setProperty("world.ghostXray", Boolean.toString(ghostXray));

        PROPS.setProperty("hint.enabled", Boolean.toString(hintEnabled));
        PROPS.setProperty("hint.ghostCrosshair", Boolean.toString(hintGhostCrosshair));
        PROPS.setProperty("hint.fill", Boolean.toString(hintFill));
        PROPS.setProperty("hint.airOnly", Boolean.toString(hintAirOnly));
        PROPS.setProperty("hint.guideLine", Boolean.toString(hintGuideLine));

        PROPS.setProperty("practice.autoStart", Boolean.toString(autoStart));
        PROPS.setProperty("practice.targetBlocks", Integer.toString(targetBlocks));
        PROPS.setProperty("practice.pitchMin", Integer.toString(pitchMin));
        PROPS.setProperty("practice.pitchMax", Integer.toString(pitchMax));
        PROPS.setProperty("practice.runTimeoutMs", Integer.toString(runTimeoutMs));
        PROPS.setProperty("practice.saveStats", Boolean.toString(saveStats));

        if (file == null) {
            return;
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            PROPS.store(out, "TellyBridge Trainer config");
        } catch (Exception e) {
            TellyBridgeTrainer.logger().warn("[TellyBridge] 保存配置失败: " + e);
        } finally {
            close(out);
        }
    }

    public static float hudScale() {
        return hudScalePercent / 100.0F;
    }

    private static boolean getBool(String key, boolean def) {
        String v = PROPS.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static int getInt(String key, int def) {
        String v = PROPS.getProperty(key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }
}
