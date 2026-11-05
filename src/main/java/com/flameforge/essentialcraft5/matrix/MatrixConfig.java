package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.network.S2CMatrixCodexPacket;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 服务端配置 {@code general.matrix_rank}（第十三批 13a，batch13.md §3.1.5）：矩阵阶的总开关、三道门、耐受、容量、首杀、图谱上限、稳固。
 * 子小节 {@code trial}（试炼）在 {@link MatrixTrialConfig}，{@code siphon} / {@code resonance}（虹吸、相性与矩阵特性）在
 * {@link MatrixResonanceConfig}，各管各的文件；都由 {@link MatrixSystems#init()} 登记一次。
 * <p>
 * 每阶的图谱条件、祭品、能量、回响数值在数据里（{@code data/essentialcraft5/matrix_ranks/rank_<n>.json}），不进配置。
 * 读值一律走下面的方法（配置没加载时返回默认值）。
 */
public final class MatrixConfig {

    public static ForgeConfigSpec.BooleanValue enabled;
    public static ForgeConfigSpec.IntValue gateHoannaPortal;
    public static ForgeConfigSpec.IntValue gateHolopad;
    public static ForgeConfigSpec.IntValue gateCalamityBeacon;
    public static ForgeConfigSpec.BooleanValue gateBlockAllEntries;
    public static ForgeConfigSpec.DoubleValue tolerancePerRank;
    public static ForgeConfigSpec.IntValue poolPerRank;
    public static ForgeConfigSpec.IntValue poolAbsorbPerSecond;
    public static ForgeConfigSpec.IntValue poolAbsorbReserve;
    public static ForgeConfigSpec.DoubleValue firstKillUbmruMultiplier;
    public static ForgeConfigSpec.IntValue codexMaxEntries;
    public static ForgeConfigSpec.DoubleValue stabilityMatrixDamageMultiplier;

    private MatrixConfig() {
    }

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Matrix ranks: nine ranks broken through at the Matrix Crucible.", "矩阵阶：在矩阵淬炼台突破的九阶").push("matrix_rank");
        enabled = builder.comment("Matrix ranks on. When off, every gate is open, the crucible can be placed but no trial starts, attunement stages ignore the rank requirement, and ranks already reached keep their rewards. The Matrix Codex keeps recording kills (without the first-kill UBMRU bonus).",
                        "矩阵阶总开关。关掉时所有门都开、淬炼台能放但开始不了试炼、同调升阶不看矩阵阶，已经升上去的阶照给回报；矩阵图谱照记，只是不给首杀 UBMRU")
                .define("enabled", true);

        // 门的上限照阶数据里的祭品算（check_progression 的「海纳尔之门要 6 阶」情形会报断链）：
        // 六阶要海纳尔里的怪和东西、七阶要全息投影掉的数学核心，所以前两道门分别最高 5、6；八、九阶的数据要等后续内容
        builder.comment("Rank needed to pass each gate (0 = no gate). Gates only stop going in, never coming out. "
                        + "A gate set higher than any rank players can reach never opens; ranks 8 and 9 come with later content.",
                "过每道门要几阶（0 = 不设门）。门只挡进去，不挡出来。填得比玩家能升到的最高阶还高，这道门就永远打不开；八、九阶要等后续内容").push("gates");
        gateHoannaPortal = builder.comment("Rank needed to enter Hoanna from anywhere else (Hoanna portal, Magical Teleporter, Homeward Compass; other mods and commands too while block_all_entries is on). "
                                + "Rank 6 needs kills and items found only in Hoanna, so above 5 nobody can get in.",
                        "从别处进海纳尔要几阶（海纳尔之门、魔力传送器、归途罗盘；block_all_entries 开着时别的模组、指令也算）。六阶要在海纳尔里杀怪、要海纳尔里才有的东西，填 6 以上就谁都进不去")
                .defineInRange("hoanna_portal", 5, 0, 9);
        gateHolopad = builder.comment("Rank needed to summon the Hologram at a Holopad. Rank 7 offerings need a Math Core, which only the Hologram drops, "
                                + "so above 6 nobody can summon it.",
                        "在全息投影台召唤全息投影要几阶。七阶的祭品要数学核心，只有全息投影掉，填 7 以上就谁都召唤不了")
                .defineInRange("holopad", 6, 0, 9);
        gateCalamityBeacon = builder.comment("Rank needed to summon at a Calamity Beacon.", "在天灾信标召唤要几阶")
                .defineInRange("calamity_beacon", 8, 0, 9);
        gateBlockAllEntries = builder.comment("Also stop players below the Hoanna portal rank from entering Hoanna through other mods and commands (/tp, waystones and the like; cancels EntityTravelToDimensionEvent). Creative and spectator players and leaving Hoanna are never stopped. An operator's /tp of a survival player into Hoanna is stopped too, so raise their rank first or turn this off.",
                        "低于海纳尔之门阶的玩家，经别的模组、指令进海纳尔（/tp、传送石碑之类，取消 EntityTravelToDimensionEvent）也一并挡住。创造、旁观和出海纳尔永远不挡；"
                                + "管理员用 /tp 送生存玩家进海纳尔同样会被挡，要先给他升阶或关掉这一项")
                .define("block_all_entries", true);
        builder.pop();

        tolerancePerRank = builder.comment("Tolerance per rank: matrix damage from the sources added with matrix ranks (trial success, siphon, new monsters) is divided by 1 + this x rank. Older sources are not affected.",
                        "每阶的耐受：矩阵阶带来的新损伤来源（试炼成功、虹吸、新怪物）记的矩阵损伤 ÷ (1 + 此值 × 阶)；以前就有的来源不受影响")
                .defineInRange("tolerance_per_rank", 0.1D, 0D, 10D);
        poolPerRank = builder.comment("Matrix reservoir size per rank from rank 2 (size = this x (rank - 1)).",
                        "矩阵容量每阶多少 MRU（二阶起，容量 = 此值 × (阶 − 1)）")
                .defineInRange("pool_per_rank", 5000, 0, 10_000_000);
        poolAbsorbPerSecond = builder.comment("Most MRU moved into the reservoir per second (UBMRU converted at the Matrix Converter's 10 : 1).",
                        "每秒最多往容量里换入多少 MRU（UBMRU 按矩阵换能器的 10 比 1 换）")
                .defineInRange("pool_absorb_per_second", 10, 0, 100_000);
        poolAbsorbReserve = builder.comment("UBMRU always kept back when filling the reservoir; only UBMRU above this is converted (x5 while wearing the theta belt).",
                        "往容量里换入时留着不动的 UBMRU，只换超过这条线的部分（戴着 θ 腰带时 ×5）")
                .defineInRange("pool_absorb_reserve", 20_000, 0, 100_000_000);
        firstKillUbmruMultiplier = builder.comment("Extra UBMRU on the kill that adds a new kind to the Matrix Codex, as a multiple of that kill's normal UBMRU (1 = doubled; 0 = no bonus).",
                        "给矩阵图谱添新种类的那一下多给的 UBMRU，按这一下本来给的倍数算（1 = 翻倍；0 = 不多给）")
                .defineInRange("first_kill_ubmru_multiplier", 1D, 0D, 10D);
        codexMaxEntries = builder.comment("Most kinds one player's Matrix Codex keeps.", "每人的矩阵图谱最多记多少种")
                .defineInRange("codex_max_entries", 1024, 1, S2CMatrixCodexPacket.MAX_ENTRIES);

        builder.comment("Matrix Fortitude (rank 7).", "矩阵稳固（七阶）").push("stability");
        stabilityMatrixDamageMultiplier = builder.comment("Multiplier on matrix damage from the sources added with matrix ranks, on top of tolerance. Older sources are not affected.",
                        "矩阵阶带来的新损伤来源再乘的倍数（和耐受连乘）；以前就有的来源不受影响")
                .defineInRange("matrix_damage_multiplier", 0.8D, 0D, 1D);
        builder.pop();

        MatrixTrialConfig.define(builder);
        MatrixResonanceConfig.define(builder);
        builder.pop();
    }

    public static boolean enabled() {
        return ECConfig.get(enabled);
    }

    public static int gateHoannaPortal() {
        return ECConfig.get(gateHoannaPortal);
    }

    public static int gateHolopad() {
        return ECConfig.get(gateHolopad);
    }

    public static int gateCalamityBeacon() {
        return ECConfig.get(gateCalamityBeacon);
    }

    public static boolean gateBlockAllEntries() {
        return ECConfig.get(gateBlockAllEntries);
    }

    public static double tolerancePerRank() {
        return ECConfig.get(tolerancePerRank);
    }

    public static int poolPerRank() {
        return ECConfig.get(poolPerRank);
    }

    public static int poolAbsorbPerSecond() {
        return ECConfig.get(poolAbsorbPerSecond);
    }

    public static int poolAbsorbReserve() {
        return ECConfig.get(poolAbsorbReserve);
    }

    public static double firstKillUbmruMultiplier() {
        return ECConfig.get(firstKillUbmruMultiplier);
    }

    public static int codexMaxEntries() {
        return ECConfig.get(codexMaxEntries);
    }

    public static double stabilityMatrixDamageMultiplier() {
        return ECConfig.get(stabilityMatrixDamageMultiplier);
    }
}
