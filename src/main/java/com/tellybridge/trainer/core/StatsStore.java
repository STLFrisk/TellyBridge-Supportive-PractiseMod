package com.tellybridge.trainer.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tellybridge.trainer.TellyBridgeTrainer;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * 历史记录的读写。Minecraft 自带 Gson，直接用它写 config/tellybridge/stats.json。
 */
public class StatsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final File file;
    private Stats stats = new Stats();

    public StatsStore(File file) {
        this.file = file;
        load();
    }

    public Stats get() {
        return stats;
    }

    public void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), UTF8);
            Stats loaded = GSON.fromJson(reader, Stats.class);
            if (loaded != null) {
                stats = loaded;
                stats.normalize();
            }
        } catch (Exception e) {
            TellyBridgeTrainer.logger().warn("[TellyBridge] 读取统计失败（将使用空记录）: " + e);
        } finally {
            close(reader);
        }
    }

    public void save() {
        if (file == null) {
            return;
        }
        Writer writer = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                TellyBridgeTrainer.logger().warn("[TellyBridge] 无法创建目录: " + parent.getAbsolutePath());
            }
            writer = new OutputStreamWriter(new FileOutputStream(file), UTF8);
            GSON.toJson(stats, writer);
        } catch (Exception e) {
            TellyBridgeTrainer.logger().warn("[TellyBridge] 保存统计失败: " + e);
        } finally {
            close(writer);
        }
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
