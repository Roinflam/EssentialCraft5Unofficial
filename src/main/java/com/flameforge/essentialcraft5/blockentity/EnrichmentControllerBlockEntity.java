package com.flameforge.essentialcraft5.blockentity;

import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.enrichment.CuboidShellScanner;
import com.flameforge.essentialcraft5.enrichment.EnrichmentControllers;
import com.flameforge.essentialcraft5.enrichment.EnrichmentPart;
import com.flameforge.essentialcraft5.enrichment.EnrichmentStabilizer;
import com.flameforge.essentialcraft5.enrichment.EnrichmentStorage;
import com.flameforge.essentialcraft5.enrichment.EnrichmentTrigger;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.MRUResistances;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECShade;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.structure.StructureResult;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * MRUCU 富集仓控制器：整座仓的 MRU 都存在这里。对应原作 TileMRUCUECController。
 * <p>
 * 外壳是空心长方体（{@link CuboidShellScanner}），由 {@code essentialcraft5:mrucuec_casing} 标签里的方块组成，控制器本身是外壳的一格。
 * 容量 = 基础 60000 + 外壳上每个存储方块 100000（原作 MaxMRU / MRUPerStorage，服务端配置）。
 * 外面的机器不能直接绑定控制器（原作也不行），MRU 从接收器进、从输出器出，状态检测器看数值，红石控制器出信号。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>扫描有上下限（每边 3~32 格，配置）、只读已加载的区块；区块没加载完判不了时（UNKNOWN）什么都不改，沿用上次确定的结果。
 *       原作是无界循环、沿途加载区块，而且结构检查失败（1.20 里包括区块暂时没加载）时上限立刻回到 6 万，下次加 MRU 就把存量截断。</li>
 *   <li>上限变小时（拆了存储方块、结构不完整）存量不截断，超出部分只出不进（{@link EnrichmentStorage}）。</li>
 *   <li>结构不完整时部件全部失效（接收器不拉、输出器取不出、红石控制器不出信号），仓里的 MRU 原样留着，修好外壳就回来。
 *       原作部件一直引用着控制器，外壳拆了、控制器拆了都照样能进出。</li>
 *   <li>一座仓只能有一个控制器（原作两个控制器各算一份满容量）；一个部件只属于一座仓（共用墙上的存储方块不重复计算）。</li>
 *   <li>节流：放下 / 拆掉外壳方块时标脏，最快 20 tick 重扫一次；平时每 200 tick 复查一次（原作间隔）；右键立即查（同一座仓 10 tick 内只查一次）。</li>
 *   <li>不再每 30 tick 把整个 NBT 发给 16 格内所有人：控制器没有界面，状态检测器的界面走菜单数据。</li>
 *   <li>红石控制器不再要求仓里有 MRU 聚集体（D10）。</li>
 * </ul>
 *
 * <h2>稳定器（第三批）</h2>
 * 外壳上有稳定器（方块类实现 {@link EnrichmentStabilizer}，ECPentacle 的 ec_balancer）且成形时，每 {@link #STABILIZE_INTERVAL} tick
 * 给整个外壳范围（含外壳本身，同原作 getMRUCU 的 AABB(lowerCoord, upperCoord)）里的 MRU 聚集体续 {@link #STABILIZE_TICKS} tick 稳定期
 * （{@link MRUPresenceEntity#stabilize}：期间不给玩家加矩阵损伤）。原作只稳定查到的第一个聚集体，这里仓里的全部都稳定。
 * 稳定器个数随外壳扫描更新（放下 / 拆掉外壳方块会标脏，最快 20 tick 重扫）；外壳不完整时不稳定。
 *
 * <h2>聚集体坍缩（第十批，研究手册第十六章）</h2>
 * 每秒一次，接在稳定那次实体查询后面（{@link #collapseSecond}，不另外扫描、不另查实体）。下面几条都满足时进度 +1（这个世界正有海纳尔天灾时 × {@code event_multiplier}）：
 * 维度在 {@code dimensions} 里（默认只有海纳尔）；外壳里 {@link #COLLAPSE_CASING}（虚空石、虚空玻璃、恶魔护板）至少 {@code void_casing} 块（外壳扫描时逐格数）；
 * 外壳上有生效的稳定器；仓里有一团存满 {@code presence_mru} 的聚集体；仓里存着至少 {@code mru} MRU，平衡值 ≤ {@code frozen_max}（冰冻 → 虚空核心）
 * 或 ≥ {@code chaos_min}（混沌 → MRU 反应核）。攒满 {@code seconds} 就坍缩：那团聚集体消失、仓里扣 {@code mru}、产物作为物品出现在仓内正中
 * （永不消失：仓是封死的，人不在旁边时不能 5 分钟后就没了），16 格内的玩家打勾。<b>世界里一格方块都不动。</b>
 * <p>
 * 永不消失的物品实体原版不合并（{@code ItemEntity.isMergable} 排除 age = -32768），所以产物不另生成：同一座仓、同一种产物加进仓里已有的那一堆，
 * 满一组（64）就停在满格（{@link CollapseState#OUTPUT_FULL}，不扣 MRU、不动聚集体），拿走以后下一秒接着坍缩。平时 30 分钟一个、天灾中 10 分钟一个，
 * 没人来收时实体数也不会往上涨。产物先放、放成了才扣：要新生成的那个被别的模组或混合端插件拦下时（{@link CollapseState#BLOCKED}），
 * MRU、聚集体、进度都不动，下一秒再试。
 * <p>
 * 第十三批 13d 加了魔力潮汐（batch13.md §6.2、§0.4 第 18 条）：<b>在海纳尔里</b>每秒的进度 × (1 + 0.5T)（T = 潮位，新月 ×1、满月 ×1.5），
 * 和天灾的 {@code event_multiplier} 取大、不叠乘（{@link #collapseRate}）；小数部分攒着（{@link #collapseCarry}，不存盘，读档最多少算不到一秒）。
 * 潮汐关掉（{@code general.hoanna_tide.enabled = false}）时不吃潮汐、按 ×1 走（{@link #collapseRateAt}），不按恒定潮位 0.5 算成 ×1.25：
 * 关掉潮汐的意思是不放大也不缩小。别的维度（{@code dimensions} 里加了主世界之类）不吃潮汐：潮汐是海纳尔的机制。凝结矩阵不吃潮汐。
 * <p>
 * 条件中途不满足时进度停住、不清零；认准的那团聚集体没了才清零。外壳跨的区块里有实体还没读完的（区块刚加载、控制器所在区块正好在
 * 能 tick 的边上，远端区块不加载实体），这一秒不判、不走（{@link CollapseState#LOADING}），免得把查不到当成没了。进度、认准的聚集体存盘。
 *
 * <h2>凝结矩阵（第十三批 13a，研究手册第十二章，batch13.md §3.5）</h2>
 * 富集仓的第二个分支，平衡值在中间（纯净）时走。和坍缩共用每秒那一次实体查询（{@link #chamberSecond}），各有各的进度、认准的聚集体和状态，
 * 互不推动：默认平衡值区间 [0.8, 1.2] 和坍缩的两头（≤ 0.5、≥ 1.5）不重叠，海纳尔里同一座仓按平衡值走其中一支。下面几条都满足时进度 +1
 * （所在世界正有海纳尔天灾时 × {@code condense.event_multiplier}，默认 1：不吃天灾）：
 * 维度在 {@code condense.dimensions} 里（空 = 所有维度）；外壳里 {@link ECTags#CONDENSE_CASING}（魔力护板、淡青护板）至少 {@code casing} 块；
 * 外壳上有生效的稳定器；仓里有一团存量 ≥ {@code presence_mru} 的聚集体；仓里存着至少 {@code mru} MRU；平衡值在 [{@code balance_min}, {@code balance_max}]。
 * 攒满 {@code seconds} 就凝结：那团聚集体消失、扣 {@code mru}、仓内正中多一个 {@code condensed_matrix}。产物的规矩同坍缩（永不消失、
 * 加进同种那一堆、满一组停在 {@link CondenseState#OUTPUT_FULL}、新生成的被拦下时停在 {@link CondenseState#BLOCKED} 什么都不扣），
 * 世界里一格方块都不动、不发方块事件。
 *
 * <h2>暗影核心（第十三批 13e，研究手册第二十二章，batch13.md §3.5）</h2>
 * 坍缩的第三分支，和虚空核心 / 反应核共用坍缩那一套进度、认准的聚集体和状态（{@link CollapseState#SHADE}），条件换成：
 * 在海纳尔、外壳成形时，控制器所在格或它周围 8 个方向（正方向 {@code site_radius} 格、斜方向同样远）有一点的群系在
 * {@link #SHADE_CORE_SITES}（默认 = 审判庭地点，暗影腐化之地）里——群系只在外壳扫描时采这 9 点（{@link #shadeSiteAt}，不加载区块），结果缓存到下一次扫描；
 * 外壳里关着的影晶物品合计 ≥ {@code umbral}（4）——每秒查一次外壳范围里的物品实体（只在海纳尔里成形的仓查），在暗影地点时把它们设成永不消失
 * （仓是封死的，30 分钟里普通掉落物早就没了）。这两条都满足（{@link ShadeHint#ACTIVE}）时<b>这座仓只走暗影分支</b>：不再按平衡值凝虚空核心 / 反应核，
 * 凝结矩阵也让位（{@link CondenseState#SHADE}）；外壳里虚空级方块 ≥ {@code shade.casing}、稳定器、存满 {@code collapse.presence_mru} 的聚集体、
 * 仓里 ≥ {@code shade.mru}（10 万）MRU，平衡值不限，攒满 {@code shade.seconds}（1 800）秒。每秒的进度照 {@link #collapseRate}：
 * 在海纳尔吃潮汐 ×(1 + 0.5T)，<b>黑暗</b>天灾中和 {@code collapse.event_multiplier}（3）取大（别的天灾不加快）。
 * 影晶不够、地点不对时照旧按平衡值走前两支。坍缩完：仓内正中多一个暗影核心（同种堆叠、满一组停、新生成的被拦下时什么都不扣），
 * 扣 MRU、那团聚集体消失、吃掉 {@code umbral} 个影晶——都只碰仓里的物品实体和仓自己的存量，世界里一格方块都不动。
 * 分支切换一般不清进度（同冰冻 / 混沌之间换平衡值）：进度是同一团聚集体在同一座仓里攒的。只有一种例外：虚空核心 / 反应核那两支
 * 在黑暗以外的天灾里按 ×3 攒过进度（{@link #collapseBoosted}），换到暗影分支时从头算——不然先拿 3 个影晶在日阵里攒、再补第 4 个，
 * 就绕开了「暗影核心只有黑暗天灾 ×3」。反过来从暗影换到前两支不用清：暗影那支的速度不会比它们快。
 */
public class EnrichmentControllerBlockEntity extends BlockEntity {

    /** 原作 tileentities.enrichmentchamber，另加外壳尺寸与复查间隔（D10） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruPerStorage;
        public static ForgeConfigSpec.IntValue minSize;
        public static ForgeConfigSpec.IntValue maxSize;
        public static ForgeConfigSpec.IntValue recheckInterval;
        public static ForgeConfigSpec.BooleanValue collapseEnabled;
        public static ForgeConfigSpec.IntValue collapseMRU;
        public static ForgeConfigSpec.IntValue collapseSeconds;
        public static ForgeConfigSpec.IntValue collapseEventMultiplier;
        public static ForgeConfigSpec.IntValue collapseVoidCasing;
        public static ForgeConfigSpec.ConfigValue<List<? extends String>> collapseDimensions;
        public static ForgeConfigSpec.IntValue collapsePresenceMRU;
        public static ForgeConfigSpec.DoubleValue collapseFrozenMax;
        public static ForgeConfigSpec.DoubleValue collapseChaosMin;
        @Nullable
        private static CollapseValues collapseTestOverride;
        public static ForgeConfigSpec.BooleanValue condenseEnabled;
        public static ForgeConfigSpec.IntValue condenseMRU;
        public static ForgeConfigSpec.IntValue condenseSeconds;
        public static ForgeConfigSpec.IntValue condenseCasing;
        public static ForgeConfigSpec.IntValue condensePresenceMRU;
        public static ForgeConfigSpec.DoubleValue condenseBalanceMin;
        public static ForgeConfigSpec.DoubleValue condenseBalanceMax;
        public static ForgeConfigSpec.IntValue condenseEventMultiplier;
        public static ForgeConfigSpec.ConfigValue<List<? extends String>> condenseDimensions;
        @Nullable
        private static CondenseValues condenseTestOverride;
        public static ForgeConfigSpec.BooleanValue shadeEnabled;
        public static ForgeConfigSpec.IntValue shadeMRU;
        public static ForgeConfigSpec.IntValue shadeSeconds;
        public static ForgeConfigSpec.IntValue shadeUmbral;
        public static ForgeConfigSpec.IntValue shadeSiteRadius;
        public static ForgeConfigSpec.IntValue shadeCasing;
        @Nullable
        private static ShadeValues shadeTestOverride;
        @Nullable
        private static Boolean shadeSiteTestOverride;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("enrichment_chamber");
            maxMRU = builder.comment("Base capacity of an Enrichment Chamber controller.", "富集仓控制器的基础容量")
                    .defineInRange("max_mru", 60000, 1, Integer.MAX_VALUE);
            mruPerStorage = builder.comment("Capacity added by each Holding Block in the shell.", "外壳上每个存储方块增加的容量")
                    .defineInRange("mru_per_storage", 100000, 0, Integer.MAX_VALUE);
            minSize = builder.comment("Minimum shell size along each axis (blocks).", "外壳每边最少几格")
                    .defineInRange("min_size", 3, 3, 32);
            maxSize = builder.comment("Maximum shell size along each axis (blocks). Larger shells cost more to scan.",
                            "外壳每边最多几格（越大扫描越贵）")
                    .defineInRange("max_size", 32, 3, 32);
            recheckInterval = builder.comment("Ticks between routine shell re-checks (placing or breaking casing blocks triggers an earlier check).",
                            "平时多少 tick 复查一次外壳（放下、拆掉外壳方块会提前查）")
                    .defineInRange("recheck_interval", 200, 20, 72000);
            CollapseValues d = COLLAPSE_DEFAULTS;
            builder.comment("Collapse: in Hoanna, a full MRUCU shut inside a formed chamber slowly condenses into a Void Core (frozen chamber) or an MRU Reactor (chaotic chamber).",
                            "聚集体坍缩：海纳尔里成形的富集仓关着一团存满的聚集体，慢慢凝成虚空核心（仓是冰冻的）或 MRU 反应核（仓是混沌的）")
                    .push("collapse");
            collapseEnabled = builder.comment("Enable collapse.", "开启坍缩").define("enabled", d.enabled());
            collapseMRU = builder.comment("MRU taken from the chamber by each collapse; the chamber must store at least this much (100,000 needs a Holding Block in the shell).",
                            "每次坍缩从仓里扣多少 MRU；仓里至少要存着这么多（存 10 万要外壳上有一个存储方块）")
                    .defineInRange("mru", d.mru(), 1, Integer.MAX_VALUE);
            collapseSeconds = builder.comment("Progress needed, in seconds: +1 per second while every condition holds (× event_multiplier during a Hoanna event).",
                            "要攒多少进度（秒）：条件都满足时每秒 +1（海纳尔天灾期间 × event_multiplier）")
                    .defineInRange("seconds", d.seconds(), 1, 1_000_000);
            collapseEventMultiplier = builder.comment("Progress per second while the chamber's world has a Hoanna event going on.",
                            "所在世界正有海纳尔天灾时，每秒的进度")
                    .defineInRange("event_multiplier", d.eventMultiplier(), 1, 100);
            collapseVoidCasing = builder.comment("Shell blocks from #essentialcraft5:collapse_casing (Void Stone, Void Glass, Demonic Plating) the shell must contain.",
                            "外壳里至少要有几块 #essentialcraft5:collapse_casing（虚空石、虚空玻璃、恶魔护板）")
                    .defineInRange("void_casing", d.voidCasing(), 0, 10_000);
            collapseDimensions = builder.comment("Dimensions where collapse works.", "能坍缩的维度")
                    .defineListAllowEmpty("dimensions", d.dimensions(), o -> o instanceof String str && ResourceLocation.tryParse(str) != null);
            collapsePresenceMRU = builder.comment("MRU the MRUCU inside must hold (20,000 is a full one).", "仓里的聚集体要存着多少 MRU（20000 是存满）")
                    .defineInRange("presence_mru", d.presenceMru(), 1, MRUPresenceEntity.MAX_MRU);
            collapseFrozenMax = builder.comment("A chamber balance at or below this condenses a Void Core.", "仓的平衡值不高于它时凝出虚空核心")
                    .defineInRange("frozen_max", d.frozenMax(), MRUBalance.MIN, MRUBalance.MAX);
            collapseChaosMin = builder.comment("A chamber balance at or above this condenses an MRU Reactor.", "仓的平衡值不低于它时凝出 MRU 反应核")
                    .defineInRange("chaos_min", d.chaosMin(), MRUBalance.MIN, MRUBalance.MAX);
            builder.pop();
            CondenseValues c = CONDENSE_DEFAULTS;
            builder.comment("Condensing: a formed chamber at a pure balance slowly turns the MRUCU shut inside into a Condensed Matrix.",
                            "凝结矩阵：成形的富集仓平衡值在中间（纯净）时，把关在里面的聚集体慢慢凝成一个凝结矩阵")
                    .push("condense");
            // 凝结矩阵没有别的来源，矩阵阶四阶起的祭品都要它（batch13.md §3.5）：关掉时要连带关矩阵阶或放宽门，注释里写明
            condenseEnabled = builder.comment("Enable condensing. Condensed Matrices come only from here, and matrix ranks 4 and up take them as offerings. "
                                    + "If you turn this off, also do one of these: turn off general.matrix_rank.enabled; set every gate under general.matrix_rank.gates "
                                    + "to 3 or lower (ranks then stop at 3, but the gates no longer block anything); or change the offerings in matrix_ranks with a data pack. "
                                    + "Otherwise players stop at rank 3 and can never enter Hoanna or summon the Hologram.",
                            "开启凝结。凝结矩阵只能从这里来，矩阵四阶起的祭品都要它。关掉时请再做一样：关掉 general.matrix_rank.enabled；"
                                    + "或者把 general.matrix_rank.gates 下的几道门都设到 3 或更低（阶升到三阶为止，但门不再挡后面的内容）；"
                                    + "或者用数据包改 matrix_ranks 里的祭品。不然玩家会停在三阶，进不了海纳尔、也召唤不了全息投影")
                    .define("enabled", c.enabled());
            condenseMRU = builder.comment("MRU taken from the chamber by each Condensed Matrix; the chamber must store at least this much.",
                            "每凝出一个从仓里扣多少 MRU；仓里至少要存着这么多")
                    .defineInRange("mru", c.mru(), 1, Integer.MAX_VALUE);
            condenseSeconds = builder.comment("Progress needed, in seconds: +1 per second while every condition holds (× event_multiplier during a Hoanna event).",
                            "要攒多少进度（秒）：条件都满足时每秒 +1（海纳尔天灾期间 × event_multiplier）")
                    .defineInRange("seconds", c.seconds(), 1, 1_000_000);
            condenseCasing = builder.comment("Shell blocks from #essentialcraft5:condense_casing (Magic Plating, Pale Plating) the shell must contain.",
                            "外壳里至少要有几块 #essentialcraft5:condense_casing（魔力护板、淡青护板）")
                    .defineInRange("casing", c.casing(), 0, 10_000);
            condensePresenceMRU = builder.comment("MRU the MRUCU inside must hold.", "仓里的聚集体至少要存着多少 MRU")
                    .defineInRange("presence_mru", c.presenceMru(), 1, MRUPresenceEntity.MAX_MRU);
            condenseBalanceMin = builder.comment("Lowest chamber balance that condenses.", "能凝结的平衡值下限")
                    .defineInRange("balance_min", c.balanceMin(), MRUBalance.MIN, MRUBalance.MAX);
            condenseBalanceMax = builder.comment("Highest chamber balance that condenses.", "能凝结的平衡值上限")
                    .defineInRange("balance_max", c.balanceMax(), MRUBalance.MIN, MRUBalance.MAX);
            condenseEventMultiplier = builder.comment("Progress per second while the chamber's world has a Hoanna event going on (1 = events don't speed it up).",
                            "所在世界正有海纳尔天灾时，每秒的进度（1 = 天灾不加快）")
                    .defineInRange("event_multiplier", c.eventMultiplier(), 1, 100);
            condenseDimensions = builder.comment("Dimensions where condensing works; empty = every dimension.", "能凝结的维度；空 = 所有维度")
                    .defineListAllowEmpty("dimensions", c.dimensions(), o -> o instanceof String str && ResourceLocation.tryParse(str) != null);
            builder.pop();
            ShadeValues h = SHADE_DEFAULTS;
            builder.comment("Shade Core: in Hoanna, a chamber built in or near a shade site (#essentialcraft5:shade_core_sites) with Umbral Crystals "
                                    + "shut inside collapses its MRUCU into a Shade Core instead of a Void Core or an MRU Reactor.",
                            "暗影核心：海纳尔里建在暗影地点（#essentialcraft5:shade_core_sites）或它附近、外壳里关着影晶的富集仓，"
                                    + "坍缩出的是暗影核心，不再是虚空核心或 MRU 反应核")
                    .push("shade");
            // 暗影核心没有别的来源：暗渎水晶柱、天灾信标、矩阵八 / 九阶祭品都要它（batch13.md §3.5）。关掉时主线停在第二十二章，注释里写明怎么接上
            shadeEnabled = builder.comment("Enable Shade Cores. They come only from here; the SHADE Corrupted Crystal Pillar, the Calamity Beacon and matrix ranks 8 and 9 "
                                    + "all need them. If you turn this off, also do one of these: turn off general.hoanna.portal_enabled as well "
                                    + "(chapters 22 and 23 are hidden with it); add another Shade Core recipe with a data pack; "
                                    + "or change those recipes and the offerings in matrix_ranks with a data pack; or accept that the main line stops at chapter 22 "
                                    + "(no tier 4 Soul Pentacle, no rank 8, no Heart of Calamity).",
                            "开启暗影核心。暗影核心只能从这里来，暗渎水晶柱、天灾信标、矩阵八 / 九阶的祭品都要它。关掉时请再做一样：一起关掉 general.hoanna.portal_enabled"
                                    + "（第二十二、二十三章跟着藏起来）；用数据包给暗影核心加别的配方；"
                                    + "或者用数据包改掉那几条配方和 matrix_ranks 里的祭品；或者接受主线停在第二十二章（没有 4 档五芒星、八阶、天灾之心）")
                    .define("enabled", h.enabled());
            shadeMRU = builder.comment("MRU taken from the chamber by each Shade Core; the chamber must store at least this much.",
                            "每凝出一个暗影核心从仓里扣多少 MRU；仓里至少要存着这么多")
                    .defineInRange("mru", h.mru(), 1, Integer.MAX_VALUE);
            shadeSeconds = builder.comment("Progress needed, in seconds (sped up by the tide, and by the Darkness event like collapse.event_multiplier).",
                            "要攒多少进度（秒；大潮按潮汐加快，黑暗天灾中按 collapse.event_multiplier 加快，两者取大）")
                    .defineInRange("seconds", h.seconds(), 1, 1_000_000);
            shadeUmbral = builder.comment("Umbral Crystals (dropped items) that must lie inside the shell; eaten when the Shade Core forms.",
                            "外壳里要关着几个影晶（掉在地上的物品）；凝出暗影核心时吃掉")
                    .defineInRange("umbral", h.umbral(), 1, 1024);
            shadeSiteRadius = builder.comment("The chamber counts as a shade site if the controller, or a point this many blocks away in any of the 8 directions, "
                                    + "is in #essentialcraft5:shade_core_sites (biomes are sampled when the shell is scanned).",
                            "控制器所在格、或它往 8 个方向各这么远的一点在 #essentialcraft5:shade_core_sites 里，就算暗影地点（外壳扫描时采群系）")
                    .defineInRange("site_radius", h.siteRadius(), 0, 256);
            shadeCasing = builder.comment("Shell blocks from #essentialcraft5:collapse_casing (Void Stone, Void Glass, Demonic Plating) the shell must contain.",
                            "外壳里至少要有几块 #essentialcraft5:collapse_casing（虚空石、虚空玻璃、恶魔护板）")
                    .defineInRange("casing", h.casing(), 0, 10_000);
            builder.pop();
            builder.pop();
        }

        /** 暗影核心的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static ShadeValues shade() {
            ShadeValues override = shadeTestOverride;
            if (override != null) {
                return override;
            }
            if (shadeEnabled == null) {
                return SHADE_DEFAULTS;
            }
            return new ShadeValues(ECConfig.get(shadeEnabled), ECConfig.get(shadeMRU), ECConfig.get(shadeSeconds), ECConfig.get(shadeUmbral),
                    ECConfig.get(shadeSiteRadius), ECConfig.get(shadeCasing));
        }

        /** GameTest 用：暗影核心整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setShadeTestOverride(@Nullable ShadeValues values) {
            shadeTestOverride = values;
        }

        /** GameTest 用：把每座仓都当成 / 不当成暗影地点（超平坦测试世界里没有暗影腐化之地），null = 照群系采样 */
        public static void setShadeSiteTestOverride(@Nullable Boolean site) {
            shadeSiteTestOverride = site;
        }

        /** 凝结的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static CondenseValues condense() {
            CondenseValues override = condenseTestOverride;
            if (override != null) {
                return override;
            }
            if (condenseEnabled == null) {
                return CONDENSE_DEFAULTS;
            }
            return new CondenseValues(ECConfig.get(condenseEnabled), ECConfig.get(condenseMRU), ECConfig.get(condenseSeconds),
                    ECConfig.get(condenseCasing), ECConfig.get(condensePresenceMRU), ECConfig.get(condenseBalanceMin),
                    ECConfig.get(condenseBalanceMax), ECConfig.get(condenseEventMultiplier), stringList(condenseDimensions));
        }

        /** GameTest 用：凝结整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setCondenseTestOverride(@Nullable CondenseValues values) {
            condenseTestOverride = values;
        }

        /** 坍缩的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static CollapseValues collapse() {
            CollapseValues override = collapseTestOverride;
            if (override != null) {
                return override;
            }
            if (collapseEnabled == null) {
                return COLLAPSE_DEFAULTS;
            }
            return new CollapseValues(ECConfig.get(collapseEnabled), ECConfig.get(collapseMRU), ECConfig.get(collapseSeconds),
                    ECConfig.get(collapseEventMultiplier), ECConfig.get(collapseVoidCasing), stringList(collapseDimensions),
                    ECConfig.get(collapsePresenceMRU), ECConfig.get(collapseFrozenMax), ECConfig.get(collapseChaosMin));
        }

        /** 维度名单（ECConfig.get 没有列表的重载：没加载时用默认值，同 MRUResistances） */
        private static List<String> stringList(ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
            boolean loaded = ECConfig.spec() != null && ECConfig.spec().isLoaded();
            List<String> out = new ArrayList<>();
            for (String entry : loaded ? value.get() : value.getDefault()) {
                out.add(entry);
            }
            return List.copyOf(out);
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setCollapseTestOverride(@Nullable CollapseValues values) {
            collapseTestOverride = values;
        }
    }

    /** {@code machines.enrichment_chamber.collapse} 的一套数值（seconds 是要攒的进度，每秒 +1） */
    public record CollapseValues(boolean enabled, int mru, int seconds, int eventMultiplier, int voidCasing, List<String> dimensions,
                                 int presenceMru, double frozenMax, double chaosMin) {
    }

    public static final CollapseValues COLLAPSE_DEFAULTS = new CollapseValues(true, 100_000, 1_800, 3, 8,
            List.of(ECHoanna.ID.toString()), MRUPresenceEntity.MAX_MRU, 0.5D, 1.5D);

    /**
     * {@code machines.enrichment_chamber.condense} 的一套数值（seconds 是要攒的进度，每秒 +1；dimensions 空 = 所有维度）
     */
    public record CondenseValues(boolean enabled, int mru, int seconds, int casing, int presenceMru, double balanceMin, double balanceMax,
                                 int eventMultiplier, List<String> dimensions) {
    }

    /** batch13.md §3.5：5 万 MRU、900 秒、护板 8 块、聚集体 1.5 万、平衡值 [0.8, 1.2]、不吃天灾、所有维度 */
    public static final CondenseValues CONDENSE_DEFAULTS = new CondenseValues(true, 50_000, 900, 8, 15_000, 0.8D, 1.2D, 1, List.of());

    /** {@code machines.enrichment_chamber.shade} 的一套数值（seconds 是要攒的进度；umbral 是外壳里要有、坍缩时吃掉的影晶数） */
    public record ShadeValues(boolean enabled, int mru, int seconds, int umbral, int siteRadius, int casing) {
    }

    /** batch13.md §3.5：10 万 MRU、1 800 秒、影晶 4 个、地点半径 32、虚空级外壳 8 块 */
    public static final ShadeValues SHADE_DEFAULTS = new ShadeValues(true, 100_000, 1_800, 4, 32, 8);

    /** 暗影核心的地点（13e）：默认 = {@code #essentialcraft5:inquisitorium_sites}（暗影腐化之地），数据包可改 */
    public static final TagKey<Biome> SHADE_CORE_SITES = TagKey.create(Registries.BIOME, EssentialCraft5.id("shade_core_sites"));

    /**
     * 这座仓离暗影核心差什么（每秒更新一次，随状态检测器的界面数据同步；序号只往末尾加）：
     * {@link #NONE} 不相干（暗影核心关着、不在海纳尔、没成形，或者不在暗影地点、外壳里也没有影晶）；{@link #NOT_SITE} 外壳里放了影晶、可这里不是暗影地点；
     * {@link #UMBRAL} 是暗影地点、影晶还不够；{@link #ACTIVE} 地点和影晶都够了，这座仓走暗影分支。
     */
    public enum ShadeHint {
        NONE, NOT_SITE, UMBRAL, ACTIVE;

        private static final ShadeHint[] VALUES = values();

        public static ShadeHint byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
        }
    }

    /** 平衡值区间两头的容差：平衡值是 float（0.8F 比 0.8 大一点点、1.2F 也比 1.2 大），按显示的两位小数算在区间里 */
    private static final double BALANCE_EPS = 1.0E-4D;

    /** 坍缩时外壳里要数的「虚空级」方块：虚空石、虚空玻璃、恶魔护板（tools/tags/ecenrichment.json，三种本来就在外壳标签里） */
    public static final TagKey<Block> COLLAPSE_CASING = ECTags.modBlock("collapse_casing");
    /** 研究手册第十六章「聚集体坍缩」一步，只由代码发（进度文件被数据包删掉时什么都不做） */
    public static final ResourceLocation GUIDE_COLLAPSE = EssentialCraft5.id("guide/hoanna/collapse");
    public static final String GUIDE_CRITERION = "awarded_by_code";

    /**
     * 这一秒坍缩卡在哪（按检查顺序取第一个不满足的），{@link #FROZEN} / {@link #CHAOS} 是正在坍缩。
     * 序号随状态检测器的界面数据同步，只在本模组内部用；新状态只往末尾加，前面的序号不动。
     */
    public enum CollapseState {
        DISABLED, NOT_FORMED, WRONG_DIMENSION, CASING, NO_STABILIZER, NO_PRESENCE, PRESENCE_LOW, LOW_MRU, BALANCE, FROZEN, CHAOS,
        /** 进度攒满了，可仓里同种产物那一堆已经满一组：停在满格，拿走以后下一秒坍缩 */
        OUTPUT_FULL,
        /** 外壳跨的区块里有实体还没读完：不判聚集体在不在，这一秒不走（排在 NO_STABILIZER 之后判） */
        LOADING,
        /** 进度攒满了，新生成的产物却被拦下（加入世界的事件被取消，或刚加入就被移除）：什么都不扣，停在满格，下一秒再试 */
        BLOCKED,
        /** 正在凝暗影核心（13e，第三分支：暗影地点 + 外壳里够数的影晶） */
        SHADE;

        private static final CollapseState[] VALUES = values();

        public boolean working() {
            return this == FROZEN || this == CHAOS || this == SHADE;
        }

        public static CollapseState byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : DISABLED;
        }

        String key() {
            return "message.essentialcraft5.ec_controller.collapse." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 这一秒凝结卡在哪（按检查顺序取第一个不满足的），{@link #CONDENSING} 是正在凝结。
     * 序号随状态检测器的界面数据同步，只在本模组内部用；新状态只往末尾加，前面的序号不动。
     */
    public enum CondenseState {
        DISABLED, NOT_FORMED, WRONG_DIMENSION, CASING, NO_STABILIZER, LOADING, NO_PRESENCE, PRESENCE_LOW, LOW_MRU, BALANCE, CONDENSING,
        /** 进度攒满了，可仓里的凝结矩阵那一堆已经满一组：停在满格，拿走以后下一秒凝结 */
        OUTPUT_FULL,
        /** 进度攒满了，新生成的凝结矩阵却被拦下（同 {@link CollapseState#BLOCKED}）：什么都不扣，停在满格，下一秒再试 */
        BLOCKED,
        /** 这座仓在凝暗影核心（13e）：凝结让位，进度停住不清零 */
        SHADE;

        private static final CondenseState[] VALUES = values();

        public boolean working() {
            return this == CONDENSING;
        }

        public static CondenseState byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : DISABLED;
        }

        String key() {
            return "message.essentialcraft5.ec_controller.condense." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** 标脏后两次扫描至少隔多少 tick */
    public static final int DIRTY_INTERVAL = 20;
    /** 右键控制器时两次扫描至少隔多少 tick（防止连点反复扫几千格） */
    public static final int USE_INTERVAL = 10;
    /** 仓刚成形时，多远以内的玩家算「建成了」（进度） */
    public static final double TRIGGER_RADIUS = 16D;
    /** 有稳定器时每隔多少 tick 给仓里的聚集体续一次稳定期 */
    public static final int STABILIZE_INTERVAL = 20;
    /** 每次续多少 tick（比间隔长一倍：少一次也不会断，拆掉稳定器最多 40 tick 后失效） */
    public static final int STABILIZE_TICKS = 40;

    private static final String TAG_STORAGE = "mru_storage";
    private static final String TAG_STRUCTURE = "structure";
    private static final String TAG_BOX = "box";
    private static final String TAG_CHAMBERS = "chambers";
    private static final String TAG_RESISTANCE = "resistance";
    private static final String TAG_PARTS = "parts";
    private static final String TAG_STABILIZERS = "stabilizers";
    private static final String TAG_VOID_CASING = "void_casing";
    private static final String TAG_COLLAPSE = "collapse";
    private static final String TAG_COLLAPSE_TARGET = "collapse_target";
    private static final String TAG_COLLAPSE_BOOSTED = "collapse_boosted";
    private static final String TAG_CONDENSE_CASING = "condense_casing";
    private static final String TAG_CONDENSE = "condense";
    private static final String TAG_CONDENSE_TARGET = "condense_target";

    /** GameTest 用：强制「外壳跨的区块实体都读完了没有」的结论，null = 照实查。同一段同步代码里设、测、置回 */
    @Nullable
    private static Boolean shellEntitiesLoadedTestOverride;

    private final EnrichmentStorage storage = new EnrichmentStorage(this::capacity).onChanged(this::markSaved);

    private StructureResult status = StructureResult.UNKNOWN;
    private StructureResult lastKnown = StructureResult.UNKNOWN;
    private boolean dirty = true;
    private long scannedAt = Long.MIN_VALUE;
    @Nullable
    private BoundingBox box;
    private int chambers;
    /** 外壳上起作用的稳定器个数（没成形时为 0） */
    private int stabilizers;
    private int stabilizeTimer;
    /** 外壳里 {@link #COLLAPSE_CASING} 的块数（没成形时为 0） */
    private int voidCasing;
    /** 坍缩进度（秒，存盘） */
    private int collapseProgress;
    /** 潮汐倍率攒下的小数部分（0~1，不存盘） */
    private float collapseCarry;
    /** 正在坍缩的那团聚集体（存盘）；它没了进度清零 */
    @Nullable
    private UUID collapseTarget;
    /** 进度里有没有在黑暗以外的天灾中按倍数攒的（虚空核心 / 反应核那两支；存盘）：有的话换到暗影分支时从头算 */
    private boolean collapseBoosted;
    /** 上一秒的坍缩状态、那团聚集体的存量、有没有天灾（给状态检测器看，不存盘） */
    private CollapseState collapseState = CollapseState.NOT_FORMED;
    private int collapsePresenceMRU;
    private boolean collapseEvent;
    /** 外壳里 {@link ECTags#CONDENSE_CASING} 的块数（没成形时为 0） */
    private int condenseCasing;
    /** 凝结进度（秒，存盘），和坍缩的进度分开记 */
    private int condenseProgress;
    /** 正在凝结的那团聚集体（存盘）；它没了进度清零 */
    @Nullable
    private UUID condenseTarget;
    /** 上一秒的凝结状态、那团聚集体的存量、有没有天灾（给状态检测器看，不存盘） */
    private CondenseState condenseState = CondenseState.NOT_FORMED;
    private int condensePresenceMRU;
    private boolean condenseEvent;
    /** 服务端：还要把「在坍缩」算多少次每秒检查（防止忽停忽走时不停发包）；客户端不用 */
    /** 上一次外壳扫描时采的群系：这座仓在不在暗影地点（不存盘，读档后第一次扫描补上） */
    private boolean shadeSite;
    /** 这一秒的暗影分支提示、外壳里的影晶数和那几个物品实体（每秒 {@link #updateShade} 更新；不存盘） */
    private ShadeHint shadeHint = ShadeHint.NONE;
    private int umbralCount;
    private List<ItemEntity> umbralItems = List.of();
    private int ambientGrace;
    /** 服务端：上次告诉客户端的「在坍缩」；客户端：收到的值 */
    private boolean ambientOn;
    /** 客户端：声音的播放进度 */
    @Nullable
    private MachineAmbience.State ambienceState;
    private float resistance;
    /** 外壳上认领的部件（成形时有效） */
    private final Set<BlockPos> parts = new HashSet<>();
    @Nullable
    private CuboidShellScanner.Failure failure;
    @Nullable
    private BlockPos failurePos;
    private boolean registered;

    public EnrichmentControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ECEnrichment.EC_CONTROLLER_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------ 访问

    public EnrichmentStorage storage() {
        return storage;
    }

    /** 上次<b>确定</b>的结果是成形（区块暂时没加载不改变它） */
    public boolean isFormed() {
        return lastKnown == StructureResult.MATCH;
    }

    /** 最近一次扫描的结果（可能是 UNKNOWN） */
    public StructureResult status() {
        return status;
    }

    public boolean hasPart(BlockPos pos) {
        return parts.contains(pos);
    }

    public Set<BlockPos> parts() {
        return Collections.unmodifiableSet(parts);
    }

    /** 外壳上的存储方块数（没成形时为 0） */
    public int chambers() {
        return chambers;
    }

    /** 外壳上起作用的稳定器个数（没成形时为 0） */
    public int stabilizers() {
        return stabilizers;
    }

    /** 外壳里虚空石、虚空玻璃、恶魔护板的块数（没成形时为 0） */
    public int voidCasing() {
        return voidCasing;
    }

    /** 坍缩进度（秒） */
    public int collapseProgress() {
        return collapseProgress;
    }

    /** 正在坍缩的那团聚集体的 UUID；还没认准时为 null */
    @Nullable
    public UUID collapseTarget() {
        return collapseTarget;
    }

    /** 上一秒的坍缩状态 */
    public CollapseState collapseState() {
        return collapseState;
    }

    /** 上一秒认准的（或存量最多的）那团聚集体的存量；没查到为 0 */
    public int collapsePresenceMRU() {
        return collapsePresenceMRU;
    }

    /** 上一秒是不是在天灾里（进度按倍数走） */
    public boolean collapseEvent() {
        return collapseEvent;
    }

    /** 外壳里魔力护板、淡青护板的块数（没成形时为 0） */
    /** 这一秒离暗影核心差什么 */
    public ShadeHint shadeHint() {
        return shadeHint;
    }

    /** 这一秒外壳里的影晶数（只在海纳尔里成形的仓数） */
    public int umbralCount() {
        return umbralCount;
    }

    /** 上一次外壳扫描时这座仓算不算暗影地点（测试覆盖值优先） */
    public boolean shadeSite() {
        Boolean override = Config.shadeSiteTestOverride;
        return override != null ? override : shadeSite;
    }

    /** 坍缩这一支现在要攒多少秒：走暗影分支时是 {@code shade.seconds}，否则 {@code collapse.seconds}（状态检测器的进度条用） */
    public int collapseSecondsNeeded() {
        return shadeHint == ShadeHint.ACTIVE ? Config.shade().seconds() : Config.collapse().seconds();
    }

    public int condenseCasing() {
        return condenseCasing;
    }

    /** 凝结进度（秒） */
    public int condenseProgress() {
        return condenseProgress;
    }

    /** 正在凝结的那团聚集体的 UUID；还没认准时为 null */
    @Nullable
    public UUID condenseTarget() {
        return condenseTarget;
    }

    /** 上一秒的凝结状态 */
    public CondenseState condenseState() {
        return condenseState;
    }

    /** 上一秒凝结认准的（或存量最多的）那团聚集体的存量；没查到为 0 */
    public int condensePresenceMRU() {
        return condensePresenceMRU;
    }

    /** 上一秒凝结是不是在天灾里（只在 event_multiplier 不是 1 时有区别） */
    public boolean condenseEvent() {
        return condenseEvent;
    }

    /** 外壳各方块 MRU 抗性之和（原作 resistance，状态检测器显示为 MROV） */
    public float resistance() {
        return resistance;
    }

    /** 成形时的外壳（含外壳本身） */
    @Nullable
    public BoundingBox box() {
        return box;
    }

    @Nullable
    public CuboidShellScanner.Failure failure() {
        return failure;
    }

    @Nullable
    public BlockPos failurePos() {
        return failurePos;
    }

    /** 当前上限：没成形时是基础容量；用 long 算再夹到 int 上限（存储方块多、配置调大时不溢出） */
    public int capacity() {
        int base = ECConfig.get(Config.maxMRU);
        if (!isFormed()) {
            return base;
        }
        long total = (long) base + (long) ECConfig.get(Config.mruPerStorage) * chambers;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static int minSize() {
        return ECConfig.get(Config.minSize);
    }

    public static int maxSize() {
        return Math.max(minSize(), ECConfig.get(Config.maxSize));
    }

    // ------------------------------------------------------------------ tick 与扫描

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnrichmentControllerBlockEntity be) {
        if (!be.registered) {
            be.registered = true;
            EnrichmentControllers.add(be);
        }
        if (++be.stabilizeTimer >= STABILIZE_INTERVAL) {
            be.stabilizeTimer = 0;
            be.chamberSecond(level.players());
            be.syncAmbient(level, pos, state);
        }
        long now = level.getGameTime();
        if (be.scannedAt != Long.MIN_VALUE) {
            long elapsed = now - be.scannedAt;
            int interval = be.dirty ? DIRTY_INTERVAL : ECConfig.get(Config.recheckInterval);
            if (elapsed >= 0 && elapsed < interval) {
                return;
            }
        }
        be.scan(now);
    }

    /**
     * 给外壳范围里的聚集体续 {@link #STABILIZE_TICKS} tick 稳定期，返回稳定了几个。不看有没有稳定器（tick 里判断过了；GameTest 直接调）。
     * 没成形（没有外壳范围）时什么都不做。
     */
    public int stabilizeContents() {
        return stabilizeAll().size();
    }

    private List<MRUPresenceEntity> stabilizeAll() {
        List<MRUPresenceEntity> presences = presencesInside();
        for (MRUPresenceEntity presence : presences) {
            presence.stabilize(STABILIZE_TICKS);
        }
        return presences;
    }

    /** 外壳范围里活着的聚集体（没成形时为空） */
    private List<MRUPresenceEntity> presencesInside() {
        BoundingBox b = box;
        if (level == null || level.isClientSide || b == null) {
            return List.of();
        }
        return level.getEntitiesOfClass(MRUPresenceEntity.class, AABB.of(b), Entity::isAlive);
    }

    // ------------------------------------------------------------------ 聚集体坍缩

    /**
     * 每秒一次（tick 里每 {@link #STABILIZE_INTERVAL} 刻调；GameTest 直接调）：有稳定器且成形时先给仓里的聚集体续稳定期（这就是坍缩用的那次实体查询），
     * 再推一秒坍缩（规则见类注释）。candidates 是坍缩完成时可能打勾的玩家：正常是 {@code level.players()}，GameTest 造的真玩家不在那张表里、由测试传。
     *
     * @return 这一秒的坍缩状态
     */
    public CollapseState collapseSecond(List<? extends Player> candidates) {
        if (level == null || level.isClientSide) {
            return collapseState;
        }
        List<MRUPresenceEntity> presences = stabilizers > 0 && isFormed() ? stabilizeAll() : null;
        updateShade(Config.shade());
        return collapseStep(presences, shellEntitiesLoaded(), candidates);
    }

    /**
     * tick 里每秒一次：先给仓里的聚集体续稳定期（唯一一次实体查询），再推一秒坍缩、一秒凝结。坍缩在前：这一秒坍缩吃掉的那团聚集体，
     * 凝结那边按「没了」处理（列表里的实体已经不是活的）。GameTest 测两支一起走时直接调。
     */
    public void chamberSecond(List<? extends Player> candidates) {
        if (level == null || level.isClientSide) {
            return;
        }
        List<MRUPresenceEntity> presences = stabilizers > 0 && isFormed() ? stabilizeAll() : null;
        boolean loaded = shellEntitiesLoaded();
        updateShade(Config.shade());
        collapseStep(presences, loaded, candidates);
        condenseStep(presences, loaded, candidates);
    }

    /**
     * 每秒一次：数外壳里的影晶、定这一秒的 {@link ShadeHint}（规则见类注释「暗影核心」）。只在海纳尔里、成形、暗影核心开着时查物品实体；
     * 在暗影地点时把数到的影晶设成永不消失（玩家丢进来的普通掉落物 5 分钟就没了，坍缩要 30 分钟）。
     */
    private void updateShade(ShadeValues shade) {
        umbralItems = List.of();
        umbralCount = 0;
        shadeHint = ShadeHint.NONE;
        BoundingBox b = box;
        if (!shade.enabled() || level == null || level.isClientSide || b == null || !isFormed() || !ECHoanna.isHoanna(level)) {
            return;
        }
        boolean site = shadeSite();
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, AABB.of(b),
                e -> e.isAlive() && e.getItem().is(ECHoannaMaterials.UMBRAL_CRYSTAL.get()));
        long count = 0L;
        for (ItemEntity item : items) {
            count += item.getItem().getCount();
            if (site && item.getAge() >= 0) {
                item.setUnlimitedLifetime();
            }
        }
        umbralItems = items;
        umbralCount = (int) Math.min(Integer.MAX_VALUE, count);
        if (site) {
            shadeHint = umbralCount >= shade.umbral() ? ShadeHint.ACTIVE : ShadeHint.UMBRAL;
        } else if (umbralCount > 0) {
            shadeHint = ShadeHint.NOT_SITE;
        }
    }

    /**
     * 暗影地点的判法（纯函数，GameTest 测）：center 那一格，或者往东西南北各 radius 格、往四个斜方向各 radius 格远（每轴 ⌊radius ÷ √2⌉）的
     * 一点满足 isSite。radius ≤ 0 时只看 center。最多 9 次判定。
     */
    public static boolean shadeSiteAt(Predicate<BlockPos> isSite, BlockPos center, int radius) {
        for (BlockPos at : shadeSamplePoints(center, radius)) {
            if (isSite.test(at)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #shadeSiteAt} 采的那几点：先中心，再四个正方向、四个斜方向 */
    public static List<BlockPos> shadeSamplePoints(BlockPos center, int radius) {
        if (radius <= 0) {
            return List.of(center.immutable());
        }
        int d = (int) Math.round(radius / Math.sqrt(2D));
        return List.of(center.immutable(), center.offset(radius, 0, 0), center.offset(-radius, 0, 0), center.offset(0, 0, radius),
                center.offset(0, 0, -radius), center.offset(d, 0, d), center.offset(d, 0, -d), center.offset(-d, 0, d), center.offset(-d, 0, -d));
    }

    /** 外壳扫描时调：采 9 点群系定这座仓算不算暗影地点（只在海纳尔、暗影核心开着时采；{@code getBiome} 对没加载的区块按群系源算，不加载区块） */
    private boolean computeShadeSite() {
        ShadeValues shade = Config.shade();
        if (!shade.enabled() || !(level instanceof ServerLevel serverLevel) || !ECHoanna.isHoanna(serverLevel)) {
            return false;
        }
        return shadeSiteAt(pos -> serverLevel.getBiome(pos).is(SHADE_CORE_SITES), worldPosition, shade.siteRadius());
    }

    /** 推一秒坍缩（presences 为 null = 这一秒没查实体：没成形或没有稳定器） */
    private CollapseState collapseStep(@Nullable List<MRUPresenceEntity> presences, boolean entitiesLoaded, List<? extends Player> candidates) {
        CollapseValues values = Config.collapse();
        ShadeValues shade = Config.shade();
        boolean shadeActive = shadeHint == ShadeHint.ACTIVE;
        CollapseCheck check = checkCollapse(values, shade, shadeActive, presences, entitiesLoaded);
        if (check.targetLost()) {
            // 认准的那团聚集体没了：从头算
            resetCollapse();
        }
        collapseState = check.state();
        collapsePresenceMRU = check.target() == null ? 0 : check.target().getMRU();
        collapseEvent = false;
        if (!check.state().working()) {
            return collapseState;
        }
        MRUPresenceEntity target = check.target();
        if (collapseTarget == null) {
            collapseTarget = target.getUUID();
        }
        collapseEvent = collapseEventNow(shadeActive);
        if (shadeActive && collapseBoosted) {
            // 前两支在别的天灾里 ×3 攒过：暗影核心不认那份倍数，从头算（认准的聚集体不变）
            collapseProgress = 0;
            collapseCarry = 0F;
            collapseBoosted = false;
            markSaved();
        } else if (!shadeActive && collapseEvent && !collapseBoosted && !collapseEventNow(true)) {
            collapseBoosted = true;
            markSaved();
        }
        float rate = collapseRateAt(level, collapseEvent, values.eventMultiplier());
        float carried = collapseCarry + rate;
        int step = (int) carried;
        collapseCarry = carried - step;
        int seconds = shadeActive ? shade.seconds() : values.seconds();
        if ((long) collapseProgress + step < seconds) {
            collapseProgress += step;
            markSaved();
            return collapseState;
        }
        // 这一秒攒满：先看仓里同种产物那一堆满没满（只在进度攒满时查物品实体，平时不多查）
        BoundingBox b = box;
        ItemEntity pile = b == null ? null : findProduct(b, productFor(collapseState));
        if (collapseProgress != seconds) {
            collapseProgress = seconds;
            markSaved();
        }
        if (pile != null && isFullPile(pile)) {
            // 满一组了：停在满格，不扣 MRU、不动聚集体；拿走以后下一秒坍缩
            collapseState = CollapseState.OUTPUT_FULL;
            return collapseState;
        }
        finishCollapse(target, collapseState, shadeActive ? shade.mru() : values.mru(), shade, candidates, pile);
        return collapseState;
    }

    /** 这一秒按不按天灾倍数算：虚空核心 / 反应核是任何海纳尔天灾；暗影核心只认黑暗（batch13.md §3.5「黑暗天灾时 ×3」） */
    private boolean collapseEventNow(boolean shadeActive) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        HoannaEvent event = HoannaEvents.current(serverLevel);
        return shadeActive ? event == HoannaEvent.DARKNESS : !event.isNone();
    }

    /** 一次检查的结果：卡在哪、那团聚集体（认准的，或者存量最多的）、认准的那团是不是没了 */
    private record CollapseCheck(CollapseState state, @Nullable MRUPresenceEntity target, boolean targetLost) {
    }

    /**
     * 按顺序查坍缩的条件，不改任何东西。presences 是外壳里的聚集体；为 null 表示这一秒没查实体（没成形、没有稳定器），
     * 这时不知道聚集体在不在，也就不判「没了」。entitiesLoaded 为 false（外壳跨的区块里有实体还没读完）时同样不判「没了」，
     * 状态在稳定器那一项之后取 {@link CollapseState#LOADING}。
     */
    private CollapseCheck checkCollapse(CollapseValues values, ShadeValues shade, boolean shadeActive, @Nullable List<MRUPresenceEntity> presences,
                                        boolean entitiesLoaded) {
        MRUPresenceEntity target = null;
        boolean lost = false;
        if (presences != null && entitiesLoaded) {
            if (collapseTarget != null) {
                for (MRUPresenceEntity presence : presences) {
                    if (presence.getUUID().equals(collapseTarget)) {
                        target = presence;
                        break;
                    }
                }
                lost = target == null;
            }
            if (target == null) {
                for (MRUPresenceEntity presence : presences) {
                    if (target == null || presence.getMRU() > target.getMRU()) {
                        target = presence;
                    }
                }
            }
        }
        CollapseState state;
        if (shadeActive) {
            // 暗影分支（地点、影晶已经由 updateShade 判过）：不看 collapse.enabled / dimensions，外壳块数和 MRU 照 shade 的，平衡值不限
            if (!isFormed()) {
                state = CollapseState.NOT_FORMED;
            } else if (voidCasing < shade.casing()) {
                state = CollapseState.CASING;
            } else if (stabilizers < 1 || presences == null) {
                state = CollapseState.NO_STABILIZER;
            } else if (!entitiesLoaded) {
                state = CollapseState.LOADING;
            } else if (target == null) {
                state = CollapseState.NO_PRESENCE;
            } else if (target.getMRU() < values.presenceMru()) {
                state = CollapseState.PRESENCE_LOW;
            } else if (storage.getMRU() < shade.mru()) {
                state = CollapseState.LOW_MRU;
            } else {
                state = CollapseState.SHADE;
            }
        } else if (!values.enabled()) {
            state = CollapseState.DISABLED;
        } else if (!isFormed()) {
            state = CollapseState.NOT_FORMED;
        } else if (!inCollapseDimension(values)) {
            state = CollapseState.WRONG_DIMENSION;
        } else if (voidCasing < values.voidCasing()) {
            state = CollapseState.CASING;
        } else if (stabilizers < 1 || presences == null) {
            state = CollapseState.NO_STABILIZER;
        } else if (!entitiesLoaded) {
            state = CollapseState.LOADING;
        } else if (target == null) {
            state = CollapseState.NO_PRESENCE;
        } else if (target.getMRU() < values.presenceMru()) {
            state = CollapseState.PRESENCE_LOW;
        } else if (storage.getMRU() < values.mru()) {
            state = CollapseState.LOW_MRU;
        } else {
            float balance = storage.getBalance();
            state = balance <= values.frozenMax() ? CollapseState.FROZEN
                    : balance >= values.chaosMin() ? CollapseState.CHAOS : CollapseState.BALANCE;
        }
        return new CollapseCheck(state, target, lost);
    }

    /** 这个世界能不能坍缩：维度 id 在名单里；名单里有海纳尔时，GameTest 当成海纳尔的世界（{@link ECHoanna#isHoanna(Level)}）也算 */
    private boolean inCollapseDimension(CollapseValues values) {
        if (level == null) {
            return false;
        }
        ResourceLocation here = level.dimension().location();
        for (String entry : values.dimensions()) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id != null && (id.equals(here) || (id.equals(ECHoanna.ID) && ECHoanna.isHoanna(level)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 外壳跨的每个区块（每边最多 32 格，最多 3 × 3 个区块）的实体是不是都读进来了。1.20.1 的实体在区块加载以后异步读入，
     * 而且只有 FULL 及以上的区块才加载实体：控制器所在区块能 tick 时，隔两个区块的邻居可能还没有实体，这时查不到聚集体不等于它没了。
     * 没成形、不在服务端时返回 true（不需要判）。
     */
    private boolean shellEntitiesLoaded() {
        Boolean override = shellEntitiesLoadedTestOverride;
        if (override != null) {
            return override;
        }
        BoundingBox b = box;
        if (b == null || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
            for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
                if (!serverLevel.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** GameTest 用：强制外壳区块的实体「读完了 / 没读完」，null = 照实查。同一段同步代码里设、测、置回 */
    public static void setShellEntitiesLoadedTestOverride(@Nullable Boolean loaded) {
        shellEntitiesLoadedTestOverride = loaded;
    }

    /** 这种坍缩出什么：冰冻 → 虚空核心，混沌 → MRU 反应核，暗影 → 暗影核心 */
    private static ItemStack productFor(CollapseState kind) {
        if (kind == CollapseState.SHADE) {
            return new ItemStack(ECShade.SHADE_CORE.get());
        }
        return new ItemStack(kind == CollapseState.FROZEN ? ECVoid.VOID_CORE.get() : ECVoid.VOID_MRU_REACTOR.get());
    }

    /**
     * 外壳范围里以前坍缩出来的那一堆同种产物：活着、永不消失（年龄为负）、物品和标签都相同；有没满一组的先给没满的，
     * 都满了给满的那堆（调用方据此停住），一堆都没有返回 null。只认永不消失的，免得加进会消失的普通掉落物里。
     */
    @Nullable
    private ItemEntity findProduct(BoundingBox b, ItemStack product) {
        if (level == null) {
            return null;
        }
        ItemEntity full = null;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, AABB.of(b),
                e -> e.isAlive() && e.getAge() < 0 && ItemStack.isSameItemSameTags(e.getItem(), product))) {
            if (!isFullPile(item)) {
                return item;
            }
            if (full == null) {
                full = item;
            }
        }
        return full;
    }

    private static boolean isFullPile(ItemEntity item) {
        ItemStack stack = item.getItem();
        return stack.getCount() >= stack.getMaxStackSize();
    }

    /**
     * 坍缩：产物出现在仓内正中，然后扣 MRU、那团聚集体消失，16 格内的玩家打勾并收到一句提示。只生成物品实体，不动方块。
     * pile 是仓里没满一组的同种产物（{@link #findProduct}），有就加进那一堆，没有才新生成一个。
     * 先放产物、放成了才扣：新生成的那个被拦下时（{@link #spawnProduct} 返回 false）MRU、聚集体、进度都不动，状态记 {@link CollapseState#BLOCKED}。
     * 暗影核心另外吃掉外壳里 {@code shade.umbral} 个影晶（这一秒 {@link #updateShade} 数到的那几个物品实体），不给第十六章的勾。
     */
    private void finishCollapse(MRUPresenceEntity target, CollapseState kind, int mru, ShadeValues shade, List<? extends Player> candidates,
                                @Nullable ItemEntity pile) {
        BoundingBox b = box;
        if (level == null || b == null || storage.extractMRU(mru, false) < mru) {
            return;
        }
        boolean shadeCore = kind == CollapseState.SHADE;
        if (shadeCore && umbralCount < shade.umbral()) {
            return;
        }
        ItemStack product = productFor(kind);
        double x = (b.minX() + b.maxX() + 1) / 2D;
        double y = (b.minY() + b.maxY() + 1) / 2D;
        double z = (b.minZ() + b.maxZ() + 1) / 2D;
        if (pile != null && pile.isAlive() && !isFullPile(pile)) {
            // 加进已有的那一堆（不发事件，拦不住）：换一份新的堆叠（原地改数量不会同步给客户端，看到的还是旧数）
            ItemStack had = pile.getItem();
            pile.setItem(had.copyWithCount(had.getCount() + 1));
        } else if (!spawnProduct(product, x, y, z)) {
            collapseState = CollapseState.BLOCKED;
            return;
        }
        storage.extractMRU(mru, true);
        target.discard();
        if (shadeCore) {
            eatUmbral(shade.umbral());
        }
        resetCollapse();
        level.playSound(null, x, y, z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1F, shadeCore ? 0.5F : 0.6F);
        double cx = worldPosition.getX() + 0.5D;
        double cy = worldPosition.getY() + 0.5D;
        double cz = worldPosition.getZ() + 0.5D;
        for (Player player : candidates) {
            if (player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer) && player.isAlive()
                    && player.level() == level && player.distanceToSqr(cx, cy, cz) <= TRIGGER_RADIUS * TRIGGER_RADIUS) {
                if (!shadeCore) {
                    PlayerPentacleBlockEntity.award(serverPlayer, GUIDE_COLLAPSE, GUIDE_CRITERION);
                }
                serverPlayer.displayClientMessage(Component.translatable("message.essentialcraft5.ec_controller.collapse.done",
                        product.getHoverName()), false);
            }
        }
        collapseState = kind;
    }

    /** 吃掉外壳里 count 个影晶（这一秒数到的那几个物品实体，按查到的顺序；换一份新的堆叠，客户端才看得到数量变化） */
    private void eatUmbral(int count) {
        int left = count;
        for (ItemEntity item : umbralItems) {
            if (left <= 0) {
                break;
            }
            if (!item.isAlive()) {
                continue;
            }
            ItemStack had = item.getItem();
            int take = Math.min(left, had.getCount());
            left -= take;
            if (take >= had.getCount()) {
                item.discard();
            } else {
                item.setItem(had.copyWithCount(had.getCount() - take));
            }
        }
        umbralCount = Math.max(0, umbralCount - (count - left));
    }

    /**
     * 在仓内正中新生成一个永不消失的产物（坍缩、凝结共用）。仓是封死的：主人不在旁边时也不能 5 分钟后就消失；
     * 永不消失的物品原版不合并，以后的产物由调用方加进这一堆，满一组就停。
     * <p>
     * 别的模组取消 {@code EntityJoinLevelEvent}、混合端插件取消 ItemSpawnEvent 时 {@code addFreshEntity} 返回 false；
     * 有的在加入事件里直接移除而不取消，返回值仍是 true。两种都返回 false，调用方据此什么都不扣。
     */
    private boolean spawnProduct(ItemStack product, double x, double y, double z) {
        if (level == null) {
            return false;
        }
        ItemEntity item = new ItemEntity(level, x, y, z, product.copy(), 0D, 0D, 0D);
        item.setUnlimitedLifetime();
        if (level.addFreshEntity(item) && !item.isRemoved()) {
            return true;
        }
        // 加入事件里就被移除的仍挂在实体分区里（分区的回调在事件之后才登记），再移除一次才真正摘掉；被取消的没进分区，这一下什么都不做
        item.discard();
        return false;
    }

    /**
     * 这个世界此刻每秒的坍缩进度：在海纳尔、潮汐开着（或 GameTest 固定了潮位）时按潮位走 {@link #collapseRate}，
     * 潮汐关掉时和别的维度一样 ×1（再和天灾倍数取大）。
     */
    public static float collapseRateAt(Level level, boolean event, int eventMultiplier) {
        boolean tide = ECHoanna.isHoanna(level) && HoannaTide.active();
        return collapseRate(tide, tide ? HoannaTide.value(level) : HoannaTide.DISABLED_VALUE, event, eventMultiplier);
    }

    /**
     * 坍缩每秒的进度倍率（纯函数，GameTest 测）：海纳尔里潮汐 1 + 0.5T（T 夹在 0~1），天灾中再和 {@code event_multiplier} 取大（不叠乘）；
     * 不在海纳尔时只有天灾那一项（天灾只在海纳尔发生，所以实际是 1）。
     */
    public static float collapseRate(boolean inHoanna, float tide, boolean event, int eventMultiplier) {
        float tideRate = inHoanna ? 1F + 0.5F * Math.max(0F, Math.min(1F, tide)) : 1F;
        return event ? Math.max(eventMultiplier, tideRate) : tideRate;
    }

    private void resetCollapse() {
        collapseCarry = 0F;
        if (collapseProgress != 0 || collapseTarget != null || collapseBoosted) {
            collapseProgress = 0;
            collapseTarget = null;
            collapseBoosted = false;
            markSaved();
        }
    }

    /**
     * 坍缩的说明行：进度（天灾中带倍数），再一行卡在哪 / 在凝什么。服务端右键控制器、客户端状态检测器的悬停提示共用；
     * 门槛按 values 写（客户端读到的是同步过来的服务端配置）。
     */
    public static List<Component> collapseLines(CollapseState state, int progress, int seconds, boolean event, int voidCasing,
                                                int presenceMRU, int storedMRU, float balance, CollapseValues values) {
        return collapseLines(state, progress, seconds, event, voidCasing, presenceMRU, storedMRU, balance, values, ShadeHint.NONE, 0, SHADE_DEFAULTS);
    }

    /**
     * 同上，带暗影分支（13e）：走暗影分支（{@link ShadeHint#ACTIVE}）时外壳块数、MRU 门槛按 shade 写；
     * 地点不对、影晶不够时在最后多一行说暗影核心差什么。
     */
    public static List<Component> collapseLines(CollapseState state, int progress, int seconds, boolean event, int voidCasing,
                                                int presenceMRU, int storedMRU, float balance, CollapseValues values,
                                                ShadeHint hint, int umbral, ShadeValues shade) {
        boolean shadeActive = hint == ShadeHint.ACTIVE;
        List<Component> lines = new ArrayList<>();
        lines.add(event && state.working()
                ? Component.translatable("message.essentialcraft5.ec_controller.collapse.progress_event", progress, seconds, values.eventMultiplier())
                : Component.translatable("message.essentialcraft5.ec_controller.collapse.progress", progress, seconds));
        Component reason = switch (state) {
            case DISABLED, NOT_FORMED, WRONG_DIMENSION, NO_STABILIZER, NO_PRESENCE, FROZEN, CHAOS, OUTPUT_FULL, LOADING, BLOCKED, SHADE ->
                    Component.translatable(state.key());
            case CASING -> Component.translatable(state.key(), voidCasing, shadeActive ? shade.casing() : values.voidCasing());
            case PRESENCE_LOW -> Component.translatable(state.key(), presenceMRU, values.presenceMru());
            case LOW_MRU -> Component.translatable(state.key(), storedMRU, shadeActive ? shade.mru() : values.mru());
            case BALANCE -> Component.translatable(state.key(), MRUBalance.format(balance),
                    MRUBalance.format((float) values.frozenMax()), MRUBalance.format((float) values.chaosMin()));
        };
        lines.add(reason);
        switch (hint) {
            case NOT_SITE -> lines.add(Component.translatable("message.essentialcraft5.ec_controller.shade.not_site", shade.siteRadius()));
            case UMBRAL -> lines.add(Component.translatable("message.essentialcraft5.ec_controller.shade.umbral", umbral, shade.umbral()));
            default -> {
            }
        }
        return lines;
    }

    /** 右键控制器时要不要写坍缩那几行：开着、在能坍缩的维度里，或者已经攒了进度；暗影分支有话要说时也写 */
    private boolean showsCollapse(CollapseValues values) {
        return values.enabled() && (inCollapseDimension(values) || collapseProgress > 0) || shadeHint != ShadeHint.NONE;
    }

    // ------------------------------------------------------------------ 凝结矩阵

    /**
     * GameTest 用：只推一秒凝结（先续稳定期、查一次实体，不推坍缩）。candidates 同 {@link #collapseSecond}：凝完时给 16 格内的玩家发一句提示。
     *
     * @return 这一秒的凝结状态
     */
    public CondenseState condenseSecond(List<? extends Player> candidates) {
        if (level == null || level.isClientSide) {
            return condenseState;
        }
        List<MRUPresenceEntity> presences = stabilizers > 0 && isFormed() ? stabilizeAll() : null;
        return condenseStep(presences, shellEntitiesLoaded(), candidates);
    }

    private CondenseState condenseStep(@Nullable List<MRUPresenceEntity> presences, boolean entitiesLoaded, List<? extends Player> candidates) {
        CondenseValues values = Config.condense();
        CondenseCheck check = checkCondense(values, presences, entitiesLoaded);
        if (check.targetLost()) {
            resetCondense();
        }
        condenseState = yieldToShade(check.state());
        condensePresenceMRU = check.target() == null ? 0 : check.target().getMRU();
        condenseEvent = false;
        // 判让位之后的状态：暗影分支生效时 SHADE 不算在凝，进度停住不清零，聚集体留给暗影核心
        if (!condenseState.working()) {
            return condenseState;
        }
        MRUPresenceEntity target = check.target();
        if (condenseTarget == null) {
            condenseTarget = target.getUUID();
        }
        condenseEvent = level instanceof ServerLevel serverLevel && !HoannaEvents.current(serverLevel).isNone();
        int step = condenseEvent ? values.eventMultiplier() : 1;
        if ((long) condenseProgress + step < values.seconds()) {
            condenseProgress += step;
            markSaved();
            return condenseState;
        }
        BoundingBox b = box;
        ItemEntity pile = b == null ? null : findProduct(b, condensedMatrix());
        if (condenseProgress != values.seconds()) {
            condenseProgress = values.seconds();
            markSaved();
        }
        if (pile != null && isFullPile(pile)) {
            condenseState = CondenseState.OUTPUT_FULL;
            return condenseState;
        }
        finishCondense(target, values, candidates, pile);
        return condenseState;
    }

    /** 一次凝结检查的结果：卡在哪、那团聚集体（认准的，或者存量最多的）、认准的那团是不是没了 */
    private record CondenseCheck(CondenseState state, @Nullable MRUPresenceEntity target, boolean targetLost) {
    }

    /**
     * 按顺序查凝结的条件，不改任何东西。presences 的含义同 {@link #checkCollapse}；列表里已经不是活的（这一秒刚被坍缩吃掉的）不算。
     */
    private CondenseCheck checkCondense(CondenseValues values, @Nullable List<MRUPresenceEntity> presences, boolean entitiesLoaded) {
        MRUPresenceEntity target = null;
        boolean lost = false;
        if (presences != null && entitiesLoaded) {
            if (condenseTarget != null) {
                for (MRUPresenceEntity presence : presences) {
                    if (presence.isAlive() && presence.getUUID().equals(condenseTarget)) {
                        target = presence;
                        break;
                    }
                }
                lost = target == null;
            }
            if (target == null) {
                for (MRUPresenceEntity presence : presences) {
                    if (presence.isAlive() && (target == null || presence.getMRU() > target.getMRU())) {
                        target = presence;
                    }
                }
            }
        }
        CondenseState state;
        if (!values.enabled()) {
            state = CondenseState.DISABLED;
        } else if (!isFormed()) {
            state = CondenseState.NOT_FORMED;
        } else if (!inCondenseDimension(values)) {
            state = CondenseState.WRONG_DIMENSION;
        } else if (condenseCasing < values.casing()) {
            state = CondenseState.CASING;
        } else if (stabilizers < 1 || presences == null) {
            state = CondenseState.NO_STABILIZER;
        } else if (!entitiesLoaded) {
            state = CondenseState.LOADING;
        } else if (target == null) {
            state = CondenseState.NO_PRESENCE;
        } else if (target.getMRU() < values.presenceMru()) {
            state = CondenseState.PRESENCE_LOW;
        } else if (storage.getMRU() < values.mru()) {
            state = CondenseState.LOW_MRU;
        } else if (!inCondenseBalance(storage.getBalance(), values)) {
            state = CondenseState.BALANCE;
        } else {
            state = CondenseState.CONDENSING;
        }
        return new CondenseCheck(state, target, lost);
    }

    /** 这座仓走暗影分支时凝结让位（关着、没成形、维度不对的照实写，其余一律写「让位」）；进度停住不清零 */
    private CondenseState yieldToShade(CondenseState state) {
        if (shadeHint != ShadeHint.ACTIVE) {
            return state;
        }
        return switch (state) {
            case DISABLED, NOT_FORMED, WRONG_DIMENSION -> state;
            default -> CondenseState.SHADE;
        };
    }

    /** 平衡值在凝结区间里（两头含，按两位小数的容差） */
    public static boolean inCondenseBalance(float balance, CondenseValues values) {
        return balance >= values.balanceMin() - BALANCE_EPS && balance <= values.balanceMax() + BALANCE_EPS;
    }

    /** 这个世界能不能凝结：名单空 = 都能；否则同坍缩的维度判法（名单里有海纳尔时，GameTest 当成海纳尔的世界也算） */
    private boolean inCondenseDimension(CondenseValues values) {
        if (level == null) {
            return false;
        }
        if (values.dimensions().isEmpty()) {
            return true;
        }
        ResourceLocation here = level.dimension().location();
        for (String entry : values.dimensions()) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id != null && (id.equals(here) || (id.equals(ECHoanna.ID) && ECHoanna.isHoanna(level)))) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack condensedMatrix() {
        return new ItemStack(ECEnrichment.CONDENSED_MATRIX.get());
    }

    /**
     * 凝结：仓内正中多一个凝结矩阵，然后扣 MRU、那团聚集体消失，16 格内的玩家收到一句提示。只生成物品实体，不动方块。
     * 同坍缩，先放产物、放成了才扣：新生成的被拦下时什么都不动，状态记 {@link CondenseState#BLOCKED}。
     */
    private void finishCondense(MRUPresenceEntity target, CondenseValues values, List<? extends Player> candidates, @Nullable ItemEntity pile) {
        BoundingBox b = box;
        if (level == null || b == null || storage.extractMRU(values.mru(), false) < values.mru()) {
            return;
        }
        ItemStack product = condensedMatrix();
        double x = (b.minX() + b.maxX() + 1) / 2D;
        double y = (b.minY() + b.maxY() + 1) / 2D;
        double z = (b.minZ() + b.maxZ() + 1) / 2D;
        if (pile != null && pile.isAlive() && !isFullPile(pile)) {
            ItemStack had = pile.getItem();
            pile.setItem(had.copyWithCount(had.getCount() + 1));
        } else if (!spawnProduct(product, x, y, z)) {
            condenseState = CondenseState.BLOCKED;
            return;
        }
        storage.extractMRU(values.mru(), true);
        target.discard();
        resetCondense();
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1F, 0.8F);
        double cx = worldPosition.getX() + 0.5D;
        double cy = worldPosition.getY() + 0.5D;
        double cz = worldPosition.getZ() + 0.5D;
        for (Player player : candidates) {
            if (player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer) && player.isAlive()
                    && player.level() == level && player.distanceToSqr(cx, cy, cz) <= TRIGGER_RADIUS * TRIGGER_RADIUS) {
                serverPlayer.displayClientMessage(Component.translatable("message.essentialcraft5.ec_controller.condense.done",
                        product.getHoverName()), false);
            }
        }
        condenseState = CondenseState.CONDENSING;
    }

    private void resetCondense() {
        if (condenseProgress != 0 || condenseTarget != null) {
            condenseProgress = 0;
            condenseTarget = null;
            markSaved();
        }
    }

    /**
     * 凝结的说明行：进度（天灾中且倍数不是 1 时带倍数），再一行卡在哪 / 正在凝结。服务端右键控制器、客户端状态检测器的悬停提示共用；
     * 门槛按 values 写（客户端读到的是同步过来的服务端配置）。
     */
    public static List<Component> condenseLines(CondenseState state, int progress, int seconds, boolean event, int casing,
                                                int presenceMRU, int storedMRU, float balance, CondenseValues values) {
        List<Component> lines = new ArrayList<>();
        lines.add(event && state.working() && values.eventMultiplier() != 1
                ? Component.translatable("message.essentialcraft5.ec_controller.condense.progress_event", progress, seconds, values.eventMultiplier())
                : Component.translatable("message.essentialcraft5.ec_controller.condense.progress", progress, seconds));
        Component reason = switch (state) {
            case DISABLED, NOT_FORMED, WRONG_DIMENSION, NO_STABILIZER, LOADING, NO_PRESENCE, CONDENSING, OUTPUT_FULL, BLOCKED, SHADE ->
                    Component.translatable(state.key());
            case CASING -> Component.translatable(state.key(), casing, values.casing());
            case PRESENCE_LOW -> Component.translatable(state.key(), presenceMRU, values.presenceMru());
            case LOW_MRU -> Component.translatable(state.key(), storedMRU, values.mru());
            case BALANCE -> Component.translatable(state.key(), MRUBalance.format(balance),
                    MRUBalance.format((float) values.balanceMin()), MRUBalance.format((float) values.balanceMax()));
        };
        lines.add(reason);
        return lines;
    }

    /** 右键控制器时要不要写凝结那几行：开着、在能凝结的维度里，或者已经攒了进度 */
    private boolean showsCondense(CondenseValues values) {
        return values.enabled() && (inCondenseDimension(values) || condenseProgress > 0);
    }

    /** 放下 / 拆掉外壳方块时调用：过了最小间隔就重扫 */
    public void markDirty() {
        dirty = true;
    }

    /** pos 处的方块变化会不会影响这座仓（成形时看外壳外扩 1 格，没成形时看周围 maxSize 格） */
    public boolean watches(BlockPos pos) {
        BoundingBox b = box;
        if (b != null) {
            return pos.getX() >= b.minX() - 1 && pos.getX() <= b.maxX() + 1
                    && pos.getY() >= b.minY() - 1 && pos.getY() <= b.maxY() + 1
                    && pos.getZ() >= b.minZ() - 1 && pos.getZ() <= b.maxZ() + 1;
        }
        int r = maxSize();
        return Math.abs(pos.getX() - worldPosition.getX()) <= r
                && Math.abs(pos.getY() - worldPosition.getY()) <= r
                && Math.abs(pos.getZ() - worldPosition.getZ()) <= r;
    }

    /** 立即扫描，不节流（GameTest、内部用） */
    public StructureResult checkNow() {
        return level == null || level.isClientSide ? status : scan(level.getGameTime());
    }

    /** 玩家右键：立即扫描，但同一座仓 {@link #USE_INTERVAL} tick 内只扫一次（标脏时不限） */
    public StructureResult checkForPlayer() {
        if (level == null || level.isClientSide) {
            return status;
        }
        long now = level.getGameTime();
        if (!dirty && scannedAt != Long.MIN_VALUE && now - scannedAt >= 0 && now - scannedAt < USE_INTERVAL) {
            return status;
        }
        return scan(now);
    }

    private StructureResult scan(long now) {
        scannedAt = now;
        if (level == null) {
            return status;
        }
        ShellVisitor visitor = new ShellVisitor();
        CuboidShellScanner.Result result = CuboidShellScanner.scan(level, worldPosition,
                state -> state.is(ECEnrichment.CASING), minSize(), maxSize(), visitor);
        status = result.status();
        if (status == StructureResult.UNKNOWN) {
            // 区块没加载完：什么都不改，保持标脏，过了最小间隔再试
            dirty = true;
            return status;
        }
        dirty = false;
        boolean wasFormed = isFormed();
        int oldChambers = chambers;
        int oldStabilizers = stabilizers;
        int oldVoidCasing = voidCasing;
        int oldCondenseCasing = condenseCasing;
        StructureResult oldKnown = lastKnown;
        BoundingBox oldBox = box;
        float oldResistance = resistance;
        Set<BlockPos> oldParts = Set.copyOf(parts);
        lastKnown = status;
        if (status == StructureResult.MATCH) {
            failure = null;
            failurePos = null;
            box = result.box();
            chambers = visitor.chambers;
            stabilizers = visitor.stabilizers;
            voidCasing = visitor.voidCasing;
            condenseCasing = visitor.condenseCasing;
            resistance = visitor.resistance;
            relink(visitor.found);
            shadeSite = computeShadeSite();
        } else {
            failure = result.failure();
            failurePos = result.failurePos();
            box = null;
            chambers = 0;
            stabilizers = 0;
            voidCasing = 0;
            condenseCasing = 0;
            resistance = 0F;
            relink(Map.of());
            shadeSite = false;
        }
        // 平时每 200 tick 的复查结果多半不变，不变就不标记存盘
        if (lastKnown != oldKnown || chambers != oldChambers || stabilizers != oldStabilizers || voidCasing != oldVoidCasing
                || condenseCasing != oldCondenseCasing
                || resistance != oldResistance
                || !Objects.equals(box, oldBox) || !parts.equals(oldParts)) {
            markSaved();
        }
        if (isFormed() && (!wasFormed || chambers > oldChambers)) {
            triggerNearby();
        }
        return status;
    }

    /** 认领这次扫到的部件，放掉不再属于这座仓的 */
    private void relink(Map<BlockPos, EnrichmentPart> found) {
        for (BlockPos old : parts) {
            if (!found.containsKey(old)) {
                unlinkPart(old);
            }
        }
        parts.clear();
        parts.addAll(found.keySet());
        for (EnrichmentPart part : found.values()) {
            part.enrichmentLink().bind(worldPosition);
        }
    }

    private void unlinkPart(BlockPos pos) {
        if (level != null && ECLevels.loadedBlockEntity(level, pos) instanceof EnrichmentPart part) {
            part.enrichmentLink().unbind(worldPosition);
        }
    }

    /** 部件被拆掉（部件方块的 onRemove 调）：立即去掉它、存储方块立即扣容量，再标脏等重扫 */
    public void onPartRemoved(BlockPos pos, boolean holdingChamber) {
        if (parts.remove(pos)) {
            if (holdingChamber && chambers > 0) {
                chambers--;
            }
            markSaved();
        }
        markDirty();
    }

    /** 控制器被拆掉（方块的 onRemove 调）：解开所有部件。仓里的 MRU 随控制器消失（同原作） */
    public void onBroken() {
        for (BlockPos pos : new ArrayList<>(parts)) {
            unlinkPart(pos);
        }
        parts.clear();
        chambers = 0;
        stabilizers = 0;
        voidCasing = 0;
        condenseCasing = 0;
        box = null;
        lastKnown = StructureResult.MISMATCH;
    }

    private void triggerNearby() {
        if (level instanceof ServerLevel serverLevel) {
            double cx = worldPosition.getX() + 0.5D;
            double cy = worldPosition.getY() + 0.5D;
            double cz = worldPosition.getZ() + 0.5D;
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(cx, cy, cz) <= TRIGGER_RADIUS * TRIGGER_RADIUS) {
                    EnrichmentTrigger.INSTANCE.trigger(player, chambers);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 给右键的玩家看的结果

    /** 右键控制器时发给玩家的几行（第一行是结果，可能再跟一行补充） */
    public List<Component> describe(StructureResult result) {
        List<Component> lines = new ArrayList<>();
        if (result == StructureResult.UNKNOWN) {
            lines.add(Component.translatable("message.essentialcraft5.ec_controller.unknown"));
            lines.add(isFormed()
                    ? Component.translatable("message.essentialcraft5.ec_controller.unknown.formed")
                    : Component.translatable("message.essentialcraft5.ec_controller.unknown.not_formed"));
            return lines;
        }
        if (result == StructureResult.MATCH && box != null) {
            lines.add(Component.translatable("message.essentialcraft5.ec_controller.formed",
                    box.getXSpan(), box.getYSpan(), box.getZSpan(), chambers, storage.getMRU(), storage.getMaxMRU()));
            if (storage.isOverCapacity()) {
                lines.add(Component.translatable("message.essentialcraft5.ec_controller.over_capacity"));
            }
            CollapseValues values = Config.collapse();
            CondenseValues condense = Config.condense();
            boolean collapseShown = showsCollapse(values);
            boolean condenseShown = showsCondense(condense);
            // 右键时当场查一次（同一座仓 USE_INTERVAL 内只扫一次外壳；实体查询只在外壳范围里），坍缩、凝结两边共用这一份
            List<MRUPresenceEntity> presences = (collapseShown || condenseShown) && stabilizers > 0 ? presencesInside() : null;
            boolean entitiesLoaded = shellEntitiesLoaded();
            if (collapseShown) {
                ShadeValues shade = Config.shade();
                boolean shadeActive = shadeHint == ShadeHint.ACTIVE;
                int seconds = shadeActive ? shade.seconds() : values.seconds();
                CollapseCheck check = checkCollapse(values, shade, shadeActive, presences, entitiesLoaded);
                MRUPresenceEntity target = check.target();
                CollapseState state = check.state();
                if (state.working() && !check.targetLost() && collapseProgress >= seconds) {
                    // 停在满格的仓：那一堆产物满一组了、或者上一秒产物被拦下了，就照实写出来
                    ItemEntity pile = findProduct(box, productFor(state));
                    if (pile != null && isFullPile(pile)) {
                        state = CollapseState.OUTPUT_FULL;
                    } else if (collapseState == CollapseState.BLOCKED) {
                        state = CollapseState.BLOCKED;
                    }
                }
                lines.addAll(collapseLines(state, check.targetLost() ? 0 : collapseProgress, seconds, collapseEventNow(shadeActive), voidCasing,
                        target == null ? 0 : target.getMRU(), storage.getMRU(), storage.getBalance(), values, shadeHint, umbralCount, shade));
            }
            if (condenseShown) {
                CondenseCheck check = checkCondense(condense, presences, entitiesLoaded);
                MRUPresenceEntity target = check.target();
                CondenseState state = yieldToShade(check.state());
                if (state.working() && !check.targetLost() && condenseProgress >= condense.seconds()) {
                    ItemEntity pile = findProduct(box, condensedMatrix());
                    if (pile != null && isFullPile(pile)) {
                        state = CondenseState.OUTPUT_FULL;
                    } else if (condenseState == CondenseState.BLOCKED) {
                        state = CondenseState.BLOCKED;
                    }
                }
                boolean event = level instanceof ServerLevel serverLevel && !HoannaEvents.current(serverLevel).isNone();
                lines.addAll(condenseLines(state, check.targetLost() ? 0 : condenseProgress, condense.seconds(), event, condenseCasing,
                        target == null ? 0 : target.getMRU(), storage.getMRU(), storage.getBalance(), condense));
            }
            return lines;
        }
        CuboidShellScanner.Failure f = failure == null ? CuboidShellScanner.Failure.NO_SHELL : failure;
        BlockPos at = failurePos;
        switch (f) {
            case SIZE -> lines.add(Component.translatable("message.essentialcraft5.ec_controller.size", minSize(), maxSize()));
            case GAP -> lines.add(at == null
                    ? Component.translatable("message.essentialcraft5.ec_controller.no_shell")
                    : Component.translatable("message.essentialcraft5.ec_controller.gap", at.getX(), at.getY(), at.getZ()));
            case REJECTED -> lines.add(at == null
                    ? Component.translatable("message.essentialcraft5.ec_controller.no_shell")
                    : Component.translatable("message.essentialcraft5.ec_controller.extra_controller", at.getX(), at.getY(), at.getZ()));
            case NO_SHELL -> lines.add(Component.translatable("message.essentialcraft5.ec_controller.no_shell"));
        }
        if (storage.getMRU() > 0) {
            lines.add(Component.translatable("message.essentialcraft5.ec_controller.kept", storage.getMRU()));
        }
        if (collapseProgress > 0 && Config.collapse().enabled()) {
            lines.add(Component.translatable("message.essentialcraft5.ec_controller.collapse.kept", collapseProgress, Config.collapse().seconds()));
        }
        if (condenseProgress > 0 && Config.condense().enabled()) {
            lines.add(Component.translatable("message.essentialcraft5.ec_controller.condense.kept", condenseProgress, Config.condense().seconds()));
        }
        return lines;
    }

    // ------------------------------------------------------------------ 生命周期、存盘

    private void markSaved() {
        // 存量常在别的方块实体的 tick 里变（接收器、从输出器拉的机器），所在区块可能不 tick：立即标记存盘
        if (level != null && !level.isClientSide) {
            level.blockEntityChanged(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (registered) {
            registered = false;
            EnrichmentControllers.remove(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_STORAGE, storage.serializeNBT());
        if (lastKnown != StructureResult.UNKNOWN) {
            tag.putByte(TAG_STRUCTURE, (byte) lastKnown.ordinal());
        }
        if (box != null) {
            tag.putIntArray(TAG_BOX, new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        }
        tag.putInt(TAG_CHAMBERS, chambers);
        if (stabilizers > 0) {
            tag.putInt(TAG_STABILIZERS, stabilizers);
        }
        if (voidCasing > 0) {
            tag.putInt(TAG_VOID_CASING, voidCasing);
        }
        if (collapseProgress > 0) {
            tag.putInt(TAG_COLLAPSE, collapseProgress);
        }
        if (collapseTarget != null) {
            tag.putUUID(TAG_COLLAPSE_TARGET, collapseTarget);
        }
        if (collapseBoosted) {
            tag.putBoolean(TAG_COLLAPSE_BOOSTED, true);
        }
        if (condenseCasing > 0) {
            tag.putInt(TAG_CONDENSE_CASING, condenseCasing);
        }
        if (condenseProgress > 0) {
            tag.putInt(TAG_CONDENSE, condenseProgress);
        }
        if (condenseTarget != null) {
            tag.putUUID(TAG_CONDENSE_TARGET, condenseTarget);
        }
        tag.putFloat(TAG_RESISTANCE, resistance);
        long[] packed = new long[parts.size()];
        int i = 0;
        for (BlockPos pos : parts) {
            packed[i++] = pos.asLong();
        }
        tag.putLongArray(TAG_PARTS, packed);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        storage.deserializeNBT(tag.getCompound(TAG_STORAGE));
        lastKnown = StructureResult.UNKNOWN;
        if (tag.contains(TAG_STRUCTURE, Tag.TAG_BYTE)) {
            int ordinal = tag.getByte(TAG_STRUCTURE);
            StructureResult[] values = StructureResult.values();
            if (ordinal >= 0 && ordinal < values.length) {
                lastKnown = values[ordinal];
            }
        }
        box = null;
        int[] b = tag.getIntArray(TAG_BOX);
        if (b.length == 6 && b[0] <= b[3] && b[1] <= b[4] && b[2] <= b[5]) {
            box = new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
        }
        chambers = Math.max(0, tag.getInt(TAG_CHAMBERS));
        // 第二批的存档没有这个键：0，读档后第一次重扫补上
        stabilizers = Math.max(0, tag.getInt(TAG_STABILIZERS));
        // 第十批以前的存档没有这几个键：0 / 没有，读档后第一次重扫补上块数
        voidCasing = Math.max(0, tag.getInt(TAG_VOID_CASING));
        collapseProgress = Math.max(0, tag.getInt(TAG_COLLAPSE));
        collapseTarget = tag.hasUUID(TAG_COLLAPSE_TARGET) ? tag.getUUID(TAG_COLLAPSE_TARGET) : null;
        // 13e 以前的存档没有这个键：false（攒进度时还不记这一项，按没在别的天灾里攒过算）
        collapseBoosted = tag.getBoolean(TAG_COLLAPSE_BOOSTED);
        // 第十三批以前的存档没有这几个键：0 / 没有，护板块数读档后第一次重扫补上
        condenseCasing = Math.max(0, tag.getInt(TAG_CONDENSE_CASING));
        condenseProgress = Math.max(0, tag.getInt(TAG_CONDENSE));
        condenseTarget = tag.hasUUID(TAG_CONDENSE_TARGET) ? tag.getUUID(TAG_CONDENSE_TARGET) : null;
        float r = tag.getFloat(TAG_RESISTANCE);
        resistance = Float.isFinite(r) ? r : 0F;
        parts.clear();
        for (long packed : tag.getLongArray(TAG_PARTS)) {
            parts.add(BlockPos.of(packed));
        }
        // 读档后下一次 tick 立即重扫；邻区块还没加载（UNKNOWN）就沿用存档里的结果
        status = StructureResult.UNKNOWN;
        dirty = true;
        scannedAt = Long.MIN_VALUE;
    }

    /** 扫描时统计外壳上的部件（只有 MATCH 那一轮的统计会被采用） */
    private final class ShellVisitor implements CuboidShellScanner.Visitor {
        int chambers;
        int stabilizers;
        int voidCasing;
        int condenseCasing;
        float resistance;
        final Map<BlockPos, EnrichmentPart> found = new LinkedHashMap<>();

        @Override
        public void reset() {
            chambers = 0;
            stabilizers = 0;
            voidCasing = 0;
            condenseCasing = 0;
            resistance = 0F;
            found.clear();
        }

        @Override
        public boolean accept(BlockPos pos, BlockState state) {
            resistance += MRUResistances.get(state);
            if (state.is(COLLAPSE_CASING)) {
                voidCasing++;
            }
            if (state.is(ECTags.CONDENSE_CASING)) {
                condenseCasing++;
            }
            if (state.getBlock() instanceof EnrichmentStabilizer stabilizer && stabilizer.stabilizes(state)) {
                stabilizers++;
            }
            if (state.is(ECEnrichment.EC_CONTROLLER.get())) {
                // 一座仓只能有一个控制器
                return pos.equals(worldPosition);
            }
            if (state.hasBlockEntity() && level != null
                    && ECLevels.loadedBlockEntity(level, pos) instanceof EnrichmentPart part
                    && !part.enrichmentLink().ownedByOther(worldPosition)) {
                found.put(pos.immutable(), part);
                if (part.countsAsHoldingChamber()) {
                    chambers++;
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ 声音和粒子（客户端）

    /** 「在坍缩」的 NBT 键（只进更新包） */
    private static final String AMBIENT_KEY = "ambient";

    /** 每秒一次（坍缩、凝结检查之后）：在坍缩或凝结就让客户端放反应堆的声音和粒子；停了要隔两次检查才告诉客户端 */
    private void syncAmbient(Level level, BlockPos pos, BlockState state) {
        ambientGrace = collapseState.working() || condenseState.working() ? 2 : Math.max(0, ambientGrace - 1);
        boolean on = ambientGrace > 0;
        if (on != ambientOn) {
            ambientOn = on;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** 服务端告诉客户端的「在坍缩」（客户端是收到的值；GameTest 用） */
    public boolean isAmbientOn() {
        return ambientOn;
    }

    /** 客户端每刻调（方块类的 getTicker 挂上） */
    public static void clientTick(Level level, BlockPos pos, BlockState state, EnrichmentControllerBlockEntity be) {
        if (be.ambientOn) {
            if (be.ambienceState == null) {
                be.ambienceState = new MachineAmbience.State();
            }
            MachineAmbiences.ENRICHMENT.tick(level, pos, be.ambienceState);
        }
    }

    /** 更新包只带「在不在坍缩」这一位，其余状态客户端用不着 */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (ambientOn) {
            tag.putBoolean(AMBIENT_KEY, true);
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        ambientOn = tag.getBoolean(AMBIENT_KEY);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
