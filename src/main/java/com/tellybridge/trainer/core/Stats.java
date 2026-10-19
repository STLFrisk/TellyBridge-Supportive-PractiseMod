package com.tellybridge.trainer.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史最佳记录与幽灵路线。字段名即 JSON 键名，改动会重置旧存档，注意保持稳定。
 */
public class Stats {

    /** 最佳放置速率（方块/秒） */
    public double bestRate = 0.0D;
    /** 单次练习最长方块数 */
    public int longestRun = 0;
    /** 最佳节奏一致性（0~100） */
    public double bestConsistency = 0.0D;
    /** 最佳每跳方块数 */
    public double bestBlocksPerJump = 0.0D;
    public int totalRuns = 0;
    public int totalBlocks = 0;

    /** 幽灵路线：每个元素是 [x, y, z]（玩家脚部位置） */
    public List<double[]> ghostPath = new ArrayList<double[]>();
    /** 幽灵路线中记录到的方块位置：[x, y, z] */
    public List<double[]> ghostBlocks = new ArrayList<double[]>();

    /** Gson 反序列化后可能为 null，统一补齐 */
    public void normalize() {
        if (ghostPath == null) {
            ghostPath = new ArrayList<double[]>();
        }
        if (ghostBlocks == null) {
            ghostBlocks = new ArrayList<double[]>();
        }
    }

    public void reset() {
        bestRate = 0.0D;
        longestRun = 0;
        bestConsistency = 0.0D;
        bestBlocksPerJump = 0.0D;
        totalRuns = 0;
        totalBlocks = 0;
        ghostPath.clear();
        ghostBlocks.clear();
    }
}
