package com.tellybridge.trainer.command;

import com.tellybridge.trainer.ClientProxy;
import com.tellybridge.trainer.config.TBTConfig;
import com.tellybridge.trainer.core.PracticeSession;
import com.tellybridge.trainer.core.Stats;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 客户端命令 /telly（别名 /tbt）。不经过服务端，所以任何服务器都能用。
 */
public class CommandTelly extends CommandBase {

    @Override
    public String getCommandName() {
        return "telly";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("tbt");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/telly <hud|pos|scale|start|stop|reset|ghost|hint|stats|pitch|target|save|help>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        PracticeSession session = ClientProxy.session;
        if (session == null) {
            msg(sender, "\u00A7c模组还没有初始化完成");
            return;
        }
        if (args.length == 0) {
            help(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("hud")) {
            if (args.length >= 2) {
                String v = args[1].toLowerCase(Locale.ROOT);
                if (v.equals("on")) {
                    TBTConfig.hudEnabled = true;
                } else if (v.equals("off")) {
                    TBTConfig.hudEnabled = false;
                } else {
                    TBTConfig.hudEnabled = !TBTConfig.hudEnabled;
                }
            } else {
                TBTConfig.hudEnabled = !TBTConfig.hudEnabled;
            }
            TBTConfig.save();
            msg(sender, "HUD: " + (TBTConfig.hudEnabled ? "\u00A7a开" : "\u00A7c关"));
            return;
        }

        if (sub.equals("pos")) {
            if (args.length < 3) {
                msg(sender, "\u00A7e用法: /telly pos <x> <y>");
                return;
            }
            Integer x = parseIntOrNull(args[1]);
            Integer y = parseIntOrNull(args[2]);
            if (x == null || y == null) {
                msg(sender, "\u00A7c坐标必须是整数");
                return;
            }
            TBTConfig.hudX = x.intValue();
            TBTConfig.hudY = y.intValue();
            TBTConfig.save();
            msg(sender, "HUD 位置: " + TBTConfig.hudX + ", " + TBTConfig.hudY);
            return;
        }

        if (sub.equals("scale")) {
            if (args.length < 2) {
                msg(sender, "\u00A7e当前缩放: " + TBTConfig.hudScalePercent + "%（用法 /telly scale 50~300）");
                return;
            }
            Integer v = parseIntOrNull(args[1]);
            if (v == null) {
                msg(sender, "\u00A7c缩放必须是整数百分比");
                return;
            }
            int p = v.intValue();
            if (p < 50) {
                p = 50;
            } else if (p > 300) {
                p = 300;
            }
            TBTConfig.hudScalePercent = p;
            TBTConfig.save();
            msg(sender, "HUD 缩放: " + p + "%");
            return;
        }

        if (sub.equals("start")) {
            session.start();
            msg(sender, "\u00A7a开始记录（也可以直接放方块自动开始）");
            return;
        }

        if (sub.equals("stop")) {
            if (!session.isRunning()) {
                msg(sender, "\u00A77当前没有在记录");
                return;
            }
            session.stop();
            msg(sender, String.format(Locale.ROOT,
                    "本次: %d 块 / %.2fs / %.2f 块每秒 / 稳定性 %.0f%%",
                    session.getBlocks(), session.getElapsedSeconds(),
                    session.getRate(), session.getConsistency()));
            return;
        }

        if (sub.equals("reset")) {
            session.reset();
            msg(sender, "本次练习已重置（历史纪录保留）");
            return;
        }

        if (sub.equals("ghost")) {
            if (args.length < 2) {
                TBTConfig.showGhost = !TBTConfig.showGhost;
                TBTConfig.save();
                msg(sender, "幽灵路线: " + (TBTConfig.showGhost ? "\u00A7a开" : "\u00A7c关"));
                return;
            }
            String v = args[1].toLowerCase(Locale.ROOT);
            if (v.equals("save")) {
                session.saveGhost();
                if (ClientProxy.stats != null) {
                    ClientProxy.stats.save();
                }
                msg(sender, "\u00A7a已保存当前路线为幽灵");
            } else if (v.equals("clear")) {
                if (ClientProxy.stats != null) {
                    ClientProxy.stats.get().ghostPath.clear();
                    ClientProxy.stats.get().ghostBlocks.clear();
                    ClientProxy.stats.save();
                }
                msg(sender, "已清空幽灵路线");
            } else if (v.equals("on")) {
                TBTConfig.showGhost = true;
                TBTConfig.save();
                msg(sender, "幽灵路线: \u00A7a开");
            } else if (v.equals("off")) {
                TBTConfig.showGhost = false;
                TBTConfig.save();
                msg(sender, "幽灵路线: \u00A7c关");
            } else if (v.equals("xray")) {
                TBTConfig.ghostXray = !TBTConfig.ghostXray;
                TBTConfig.save();
                msg(sender, "幽灵穿透显示: " + (TBTConfig.ghostXray ? "\u00A7a开" : "\u00A7c关"));
            } else {
                msg(sender, "\u00A7e用法: /telly ghost [save|clear|on|off|xray]");
            }
            return;
        }

        if (sub.equals("hint")) {
            if (args.length >= 2) {
                String v = args[1].toLowerCase(Locale.ROOT);
                if (v.equals("on")) {
                    TBTConfig.hintEnabled = true;
                } else if (v.equals("off")) {
                    TBTConfig.hintEnabled = false;
                } else if (v.equals("ghost")) {
                    TBTConfig.hintGhostCrosshair = !TBTConfig.hintGhostCrosshair;
                } else if (v.equals("line")) {
                    TBTConfig.hintGuideLine = !TBTConfig.hintGuideLine;
                } else if (v.equals("fill")) {
                    TBTConfig.hintFill = !TBTConfig.hintFill;
                } else if (v.equals("air")) {
                    TBTConfig.hintAirOnly = !TBTConfig.hintAirOnly;
                } else {
                    TBTConfig.hintEnabled = !TBTConfig.hintEnabled;
                }
            } else {
                TBTConfig.hintEnabled = !TBTConfig.hintEnabled;
            }
            TBTConfig.save();
            msg(sender, "落点提示: " + (TBTConfig.hintEnabled ? "\u00A7a开" : "\u00A7c关")
                    + "\u00A77（幽灵准星 " + (TBTConfig.hintGhostCrosshair ? "开" : "关")
                    + " / 方块填充 " + (TBTConfig.hintFill ? "开" : "关")
                    + " / 仅空中 " + (TBTConfig.hintAirOnly ? "开" : "关")
                    + " / 引导线 " + (TBTConfig.hintGuideLine ? "开" : "关") + "）");
            return;
        }

        if (sub.equals("stats")) {
            if (ClientProxy.stats == null) {
                msg(sender, "\u00A7c统计数据还没加载");
                return;
            }
            Stats st = ClientProxy.stats.get();
            if (args.length >= 2 && args[1].toLowerCase(Locale.ROOT).equals("reset")) {
                st.reset();
                ClientProxy.stats.save();
                msg(sender, "历史纪录已清空");
                return;
            }
            msg(sender, String.format(Locale.ROOT,
                    "\u00A77最佳速率 \u00A7f%.2f 块/秒  \u00A77最长 \u00A7f%d 块", st.bestRate, st.longestRun));
            msg(sender, String.format(Locale.ROOT,
                    "\u00A77最佳稳定性 \u00A7f%.0f%%  \u00A77最佳每跳 \u00A7f%.2f 块",
                    st.bestConsistency, st.bestBlocksPerJump));
            msg(sender, String.format(Locale.ROOT,
                    "\u00A77累计 \u00A7f%d 次练习 / %d 块", st.totalRuns, st.totalBlocks));
            return;
        }

        if (sub.equals("pitch")) {
            if (args.length < 3) {
                msg(sender, "\u00A7e当前靶带: " + TBTConfig.pitchMin + " ~ " + TBTConfig.pitchMax
                        + "（用法 /telly pitch <min> <max>）");
                return;
            }
            Integer min = parseIntOrNull(args[1]);
            Integer max = parseIntOrNull(args[2]);
            if (min == null || max == null) {
                msg(sender, "\u00A7c角度必须是整数");
                return;
            }
            int a = Math.max(-90, Math.min(90, min.intValue()));
            int b = Math.max(-90, Math.min(90, max.intValue()));
            if (a > b) {
                int t = a;
                a = b;
                b = t;
            }
            TBTConfig.pitchMin = a;
            TBTConfig.pitchMax = b;
            TBTConfig.save();
            msg(sender, "俯仰靶带: " + a + " ~ " + b);
            return;
        }

        if (sub.equals("target")) {
            if (args.length < 2) {
                msg(sender, "\u00A7e当前目标: " + TBTConfig.targetBlocks + " 块（0 = 不显示进度条）");
                return;
            }
            Integer v = parseIntOrNull(args[1]);
            if (v == null) {
                msg(sender, "\u00A7c目标必须是整数");
                return;
            }
            TBTConfig.targetBlocks = Math.max(0, Math.min(999, v.intValue()));
            TBTConfig.save();
            msg(sender, "目标: " + TBTConfig.targetBlocks + " 块");
            return;
        }

        if (sub.equals("save")) {
            TBTConfig.save();
            if (ClientProxy.stats != null) {
                ClientProxy.stats.save();
            }
            msg(sender, "\u00A7a配置与历史纪录已写入 config/tellybridge/");
            return;
        }

        help(sender);
    }

    private static void help(ICommandSender sender) {
        msg(sender, "\u00A77/telly hud [on|off] \u00A78— 开关面板");
        msg(sender, "\u00A77/telly pos <x> <y> \u00A78— 面板位置");
        msg(sender, "\u00A77/telly scale <50~300> \u00A78— 面板缩放");
        msg(sender, "\u00A77/telly start | stop | reset \u00A78— 练习记录");
        msg(sender, "\u00A77/telly ghost [save|clear|on|off|xray] \u00A78— 幽灵路线");
        msg(sender, "\u00A77/telly hint [on|off|ghost|fill|air|line] \u00A78— 落点提示");
        msg(sender, "\u00A77/telly pitch <min> <max> \u00A78— 俯仰靶带");
        msg(sender, "\u00A77/telly target <n> \u00A78— 目标方块数");
        msg(sender, "\u00A77/telly stats [reset] \u00A78— 历史纪录");
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void msg(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText("\u00A7b[Telly]\u00A7r " + text));
    }
}
