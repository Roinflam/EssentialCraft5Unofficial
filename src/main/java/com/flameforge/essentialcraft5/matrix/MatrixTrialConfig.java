package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 服务端配置 {@code general.matrix_rank.trial}（第十三批 13a，batch13.md §3.1.2、§3.1.5）：淬炼台试炼的回响、离开距离、失败冷却、和平引导。
 * 由 {@link MatrixConfig#define} 在 {@code matrix_rank} 小节里调用；每阶的回响数量、生命、伤害、时限在阶数据里，不在这里。
 */
public final class MatrixTrialConfig {

    public static ForgeConfigSpec.BooleanValue mobsEnabled;
    public static ForgeConfigSpec.IntValue spawnRadiusMin;
    public static ForgeConfigSpec.IntValue spawnRadiusMax;
    public static ForgeConfigSpec.IntValue leaveRadius;
    public static ForgeConfigSpec.IntValue failCooldownSeconds;
    public static ForgeConfigSpec.IntValue maxEchoesTotal;
    public static ForgeConfigSpec.IntValue peacefulChannelSeconds;
    public static ForgeConfigSpec.IntValue successMatrixDamagePerRank;

    private MatrixTrialConfig() {
    }

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Trials at the Matrix Crucible.", "矩阵淬炼台的试炼").push("trial");
        mobsEnabled = builder.comment("Trials send out Matrix Echoes to fight. When off, every trial is the peaceful channel instead (stand still near the crucible).",
                        "试炼放出矩阵回响来打。关掉时所有试炼都改成和平难度那种引导（站在台子旁边不动）")
                .define("mobs_enabled", true);
        spawnRadiusMin = builder.comment("Nearest distance (blocks) from the crucible where an Echo appears.", "回响离淬炼台最近几格出现")
                .defineInRange("spawn_radius_min", 4, 1, 16);
        spawnRadiusMax = builder.comment("Farthest distance (blocks) from the crucible where an Echo appears. An Echo that strays more than leave_radius + 8,",
                        "or spawn_radius_max + 4 if that is larger, from the crucible is pulled back and replaced.",
                        "回响离淬炼台最远几格出现。回响离台子超过 leave_radius + 8 格（或 spawn_radius_max + 4，取大的）就收回、另补一只")
                .defineInRange("spawn_radius_max", 8, 1, 16);
        leaveRadius = builder.comment("Moving farther than this (blocks) from the crucible fails the trial. Values below 5 count as 5: a trial starts",
                        "within 3.5 blocks, so anything smaller would fail it at once.",
                        "试炼中离淬炼台超过几格就算失败。小于 5 按 5 算：开始时要站在 3.5 格内，再小就一开始就算走远了")
                .defineInRange("leave_radius", 12, 2, 64);
        failCooldownSeconds = builder.comment("Seconds before a player can start another trial after failing one.", "试炼失败以后多少秒才能再开")
                .defineInRange("fail_cooldown_seconds", 180, 0, 86_400);
        maxEchoesTotal = builder.comment("Most Matrix Echoes alive on the whole server at once; a trial that would go past it waits to send more.",
                        "全服同时最多有几只矩阵回响；超过时试炼等着、先不放新的")
                .defineInRange("max_echoes_total", 64, 1, 1024);
        peacefulChannelSeconds = builder.comment("Base seconds of the peaceful channel (plus 10 per rank).", "和平引导的基础秒数（另加每阶 10 秒）")
                .defineInRange("peaceful_channel_seconds", 30, 0, 3600);
        successMatrixDamagePerRank = builder.comment("Matrix damage added on a successful trial, times the new rank (goes through tolerance and the damage cap).",
                        "试炼成功时加的矩阵损伤，乘以新阶（照样吃耐受和损伤上限）")
                .defineInRange("success_matrix_damage_per_rank", 1000, 0, 100_000);
        builder.pop();
    }

    public static boolean mobsEnabled() {
        return ECConfig.get(mobsEnabled);
    }

    /** 最近出现距离（填反时取两者中小的） */
    public static int spawnRadiusMin() {
        return Math.min(ECConfig.get(spawnRadiusMin), ECConfig.get(spawnRadiusMax));
    }

    /** 最远出现距离（填反时取两者中大的） */
    public static int spawnRadiusMax() {
        return Math.max(ECConfig.get(spawnRadiusMin), ECConfig.get(spawnRadiusMax));
    }

    /** 配置里填的值；试炼实际用的（至少 5 格）见 {@code MatrixTrials.leaveRadius()} */
    public static int leaveRadius() {
        return ECConfig.get(leaveRadius);
    }

    public static int failCooldownSeconds() {
        return ECConfig.get(failCooldownSeconds);
    }

    public static int maxEchoesTotal() {
        return ECConfig.get(maxEchoesTotal);
    }

    public static int peacefulChannelSeconds() {
        return ECConfig.get(peacefulChannelSeconds);
    }

    public static int successMatrixDamagePerRank() {
        return ECConfig.get(successMatrixDamagePerRank);
    }
}
