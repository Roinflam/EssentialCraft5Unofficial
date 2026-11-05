package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CollapseState;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CollapseValues;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CondenseState;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CondenseValues;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.ShadeHint;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.ShadeValues;
import com.flameforge.essentialcraft5.blockentity.EnrichmentStateCheckerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.corruption.CorruptionGate;
import com.flameforge.essentialcraft5.demon.DemonStanding;
import com.flameforge.essentialcraft5.demon.DemonTrades;
import com.flameforge.essentialcraft5.demon.DemonWant;
import com.flameforge.essentialcraft5.entity.DemonEntity;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeContracts;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.item.MatrixProjectionItem;
import com.flameforge.essentialcraft5.item.creative.CreativeOnly;
import com.flameforge.essentialcraft5.matrix.MatrixConfig;
import com.flameforge.essentialcraft5.matrix.MatrixPool;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.matrix.MatrixRankData;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import com.flameforge.essentialcraft5.matrix.MatrixTrialChecks;
import com.flameforge.essentialcraft5.menu.DemonTradeMenu;
import com.flameforge.essentialcraft5.menu.EnrichmentStateCheckerMenu;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.registry.ECCrystalPillars;
import com.flameforge.essentialcraft5.registry.ECDemon;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECMatrix;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECShade;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructureResult;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 第十三批 13e「暗影线」的 GameTest（batch13.md §2.2 第二十二章、§3.1.3 八 / 九阶、§3.4 挚契、§3.5 暗影核心、§7.5 末行）：
 * <ul>
 *   <li>4 档五芒星：24 根柱、58 格，四根暗渎柱在 3 档外框外一格的斜角、不和前四档重叠；成形 / 少一根暗渎柱、柱下没垫虚空石、柱位放恶魔柱都退回 3 档；
 *       有效 ESPE 每点 ×2（同 3 档，满柱 172 800）；档位存盘读档不丢；打开界面给手册 4 档打勾。</li>
 *   <li>治疗能力同 3 档：{@code cureTier} 只算到 3 档，{@code NEVER} 写死 4；柱子容量配小、只有 4 档付得起的效果仍是 NEVER。</li>
 *   <li>矩阵之影的 ESPE：4 档预扣从暗渎柱抽、退还能退进暗渎柱位（四档以前的表只有 20 个位置）。</li>
 *   <li>八阶站点要 4 档（3 档报档位不够）；八 / 九阶数据逐项对 §3.1.3；暗影相性七阶不生效、八阶 II、九阶 III；容量 3.5 万 / 4 万；阶名到九阶。</li>
 *   <li>暗影投影用掉：变暗影属性、发第二十二章 attune，八阶以前多一句提示。</li>
 *   <li>挚契：70 以下不能立誓；够了扣 16 个影晶（分几格也凑得齐）给 1 个恶魔之誓、恶魔离开、不加声望；别人（创造模式）立不了；影晶不够不扣。</li>
 *   <li>暗影核心：暗影地点 + 外壳里 ≥ 4 个影晶时平衡值不限地走暗影分支（冰冻仓也不出虚空核心）、凝结让位；攒满出暗影核心、扣 10 万 MRU、吃掉 4 个影晶；
 *       影晶不够 / 地点不对照旧走虚空核心；黑暗天灾 ×3、别的天灾不加快；暗影核心那一堆满一组停；状态检测器写差什么；地点采样 9 点。</li>
 *   <li>暗渎柱的魔力工作台配方、天灾信标的有序配方；暗渎柱不再是只放创造栏的物品。</li>
 * </ul>
 * 配置一律用测试覆盖值，同一段同步代码里设、测、置回。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch13eShadeTests {

    private static final String LARGE = "empty_large";
    private static final String EMPTY = "empty";
    static final String BATCH = "c5_shade";
    private static final double FULL = 10000D;
    private static final double EPS = 1.0E-3D;

    // ---- 五芒星（同 PentacleTests：五芒星在 (5, 2, 5)，护板在 y = 1） ----
    private static final BlockPos PENTACLE = new BlockPos(5, 2, 5);
    private static final BlockPos CRUCIBLE = new BlockPos(1, 2, 1);

    // ---- 富集仓（同 MidgameStepTests） ----
    private static final BlockPos MIN = new BlockPos(3, 1, 3);
    private static final BlockPos MAX = new BlockPos(7, 3, 7);
    private static final BlockPos EC_CONTROLLER = new BlockPos(5, 2, 3);
    private static final BlockPos HOLDING = new BlockPos(3, 2, 5);
    private static final BlockPos BALANCER = new BlockPos(7, 2, 5);
    private static final BlockPos STATE_CHECKER = new BlockPos(5, 1, 5);
    private static final BlockPos CENTER = new BlockPos(5, 2, 5);
    private static final List<BlockPos> VOID_SPOTS = List.of(
            new BlockPos(4, 3, 4), new BlockPos(5, 3, 4), new BlockPos(6, 3, 4), new BlockPos(4, 3, 5),
            new BlockPos(6, 3, 5), new BlockPos(4, 3, 6), new BlockPos(5, 3, 6), new BlockPos(6, 3, 6));
    /** 坍缩默认值，进度从 1800 秒改成 20 秒（暗影分支的秒数另由 SHADE 定） */
    private static final CollapseValues COLLAPSE = new CollapseValues(true, 100_000, 20, 3, 8, List.of(ECHoanna.ID.toString()),
            MRUPresenceEntity.MAX_MRU, 0.5D, 1.5D);
    /** 暗影核心默认值，只把 1800 秒改成 3 秒 */
    private static final ShadeValues SHADE = new ShadeValues(true, 100_000, 3, 4, 32, 8);
    private static final CondenseValues CONDENSE = new CondenseValues(true, 50_000, 900, 0, 15_000, 0.8D, 1.2D, 1, List.of());

    private Batch13eShadeTests() {
    }

    // ================================================================ 共用：五芒星

    private static BlockPos at(BlockPos offset) {
        return PENTACLE.offset(offset);
    }

    /** 搭 0 ~ upTo 档的五芒星（柱子都是这一档该用的），最后放五芒星 */
    private static PlayerPentacleBlockEntity pentacle(GameTestHelper helper, int upTo) {
        StructurePattern full = ECPentacle.TIER_PATTERNS.get(upTo);
        put(helper, full.offsets(ECPentacle.PLATING_RULE), ECMithriline.MITHRILINE_PLATING.get());
        if (upTo >= 1) {
            put(helper, full.offsets(ECPentacle.PALE_RULE), ECPentacle.PALE_BLOCK.get());
        }
        if (upTo >= 2) {
            put(helper, full.offsets(ECPentacle.VOID_STONE_RULE), ECVoid.VOID_STONE.get());
        }
        if (upTo >= 3) {
            put(helper, full.offsets(ECPentacle.DEMONIC_RULE), ECDemonic.DEMONIC_PLATING.get());
        }
        List<Block> pillars = List.of(ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get(), ECPentacle.PALE_CRYSTAL_PILLAR.get(),
                ECCrystalPillars.VOID_CRYSTAL_PILLAR.get(), ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR.get(), ECCrystalPillars.SHADE_CRYSTAL_PILLAR.get());
        for (int t = 0; t <= upTo; t++) {
            put(helper, ECPentacle.PILLAR_OFFSETS.get(t), pillars.get(t));
        }
        helper.setBlock(PENTACLE, ECPentacle.PLAYER_PENTACLE.get());
        return (PlayerPentacleBlockEntity) helper.getBlockEntity(PENTACLE);
    }

    private static void put(GameTestHelper helper, List<BlockPos> offsets, Block block) {
        for (BlockPos offset : offsets) {
            helper.setBlock(at(offset), block);
        }
    }

    private static List<MithrilineCrystalPillarBlockEntity> pillars(GameTestHelper helper, int tier) {
        List<MithrilineCrystalPillarBlockEntity> out = new ArrayList<>();
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(tier)) {
            out.add((MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(at(offset)));
        }
        return out;
    }

    private static void fill(GameTestHelper helper, int fromTier, int toTier, double espe) {
        for (int t = fromTier; t <= toTier; t++) {
            for (MithrilineCrystalPillarBlockEntity pillar : pillars(helper, t)) {
                pillar.getESPEStorage().setESPE(espe);
            }
        }
    }

    private static double total(GameTestHelper helper, int fromTier, int toTier) {
        double sum = 0D;
        for (int t = fromTier; t <= toTier; t++) {
            for (MithrilineCrystalPillarBlockEntity pillar : pillars(helper, t)) {
                sum += pillar.getESPEStorage().getESPE();
            }
        }
        return sum;
    }

    private static boolean done(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static IPlayerData data(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("玩家应挂着玩家数据");
        }
        return data;
    }

    private static ServerPlayer playerAt(GameTestHelper helper, Vec3 relative, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        Vec3 abs = helper.absoluteVec(relative);
        player.moveTo(abs.x, abs.y, abs.z, 0F, 0F);
        return player;
    }

    // ================================================================ 4 档五芒星

    /**
     * 结构：4 档 58 格、抽 24 根柱子；第五份柱位就是结构里 4 档柱子那一格；四根在 3 档外框外一格的斜角（|x| = |z| = 3），
     * 不出外框 2 格、和前四档的任何一格都不重叠；柱下是虚空石。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void c5PentacleTier4Layout(GameTestHelper helper) {
        helper.assertTrue(ECPentacle.MAX_TIER == 4 && ECPentacle.TIER_PATTERNS.size() == 5, "最高档应是 4、五份结构");
        int[] required = {22, 30, 42, 50, 58};
        for (int t = 0; t < required.length; t++) {
            int count = ECPentacle.TIER_PATTERNS.get(t).requiredCount();
            helper.assertTrue(count == required[t], t + " 档应有 " + required[t] + " 格，实际 " + count);
        }
        helper.assertTrue(ECPentacle.pillarOffsets(4).size() == 24 && ECPentacle.pillarOffsets(3).size() == 20, "3 / 4 档抽 20 / 24 根");
        StructurePattern full = ECPentacle.TIER_PATTERNS.get(4);
        Set<BlockPos> shadePillars = new HashSet<>(full.offsets(ECPentacle.PILLAR_RULES.get(4)));
        helper.assertTrue(shadePillars.equals(new HashSet<>(ECPentacle.PILLAR_OFFSETS.get(4))), "4 档柱位应和抽 ESPE 的位置一致");
        Set<BlockPos> lower = new HashSet<>(ECPentacle.TIER_PATTERNS.get(3).rules().keySet());
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(4)) {
            helper.assertTrue(Math.abs(offset.getX()) == 3 && Math.abs(offset.getZ()) == 3 && offset.getY() == 0, "暗渎柱应在 (±3, 0, ±3)：" + offset);
            for (int dy = 0; dy < 3; dy++) {
                helper.assertFalse(lower.contains(offset.above(dy)), "暗渎柱（三格高）不该压到前四档的格子：" + offset.above(dy));
            }
            BlockPos base = offset.below();
            helper.assertTrue(full.offsets(ECPentacle.VOID_STONE_RULE).contains(base) && !lower.contains(base), "暗渎柱下应垫虚空石：" + base);
        }
        for (BlockPos offset : full.rules().keySet()) {
            helper.assertTrue(Math.abs(offset.getX()) <= 4 && Math.abs(offset.getZ()) <= 4, "不出 3 档外框 2 格：" + offset);
        }
        helper.succeed();
    }

    /** 搭满 4 档成形；少一根暗渎柱、柱下的虚空石换成石头、柱位放恶魔柱都退回 3 档；3 档照旧成形 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5PentacleTier4Forms(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = pentacle(helper, 4);
        helper.assertTrue(be.recheckTier() == 4, "4 档应成形，实际 " + be.getTier());
        BlockPos pillar = at(ECPentacle.PILLAR_OFFSETS.get(4).get(1));
        helper.setBlock(pillar, Blocks.AIR);
        helper.assertTrue(be.recheckTier() == 3, "少一根暗渎柱退回 3 档，实际 " + be.getTier());
        helper.setBlock(pillar, ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR.get());
        helper.assertTrue(be.recheckTier() == 3, "暗渎柱位放恶魔柱不够档，实际 " + be.getTier());
        helper.setBlock(pillar, Blocks.AIR);
        helper.setBlock(pillar, ECCrystalPillars.SHADE_CRYSTAL_PILLAR.get());
        helper.assertTrue(be.recheckTier() == 4, "换回暗渎柱重新 4 档");
        BlockPos base = at(ECPentacle.PILLAR_OFFSETS.get(4).get(2).below());
        helper.setBlock(base, Blocks.STONE);
        helper.assertTrue(be.recheckTier() == 3, "暗渎柱下面没垫虚空石退回 3 档，实际 " + be.getTier());
        helper.setBlock(base, ECVoid.VOID_STONE.get());
        helper.assertTrue(be.recheckTier() == 4, "补上虚空石重新 4 档");
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(4)) {
            helper.setBlock(at(offset), Blocks.AIR);
        }
        helper.assertTrue(be.recheckTier() == 3, "拆掉四根暗渎柱，3 档照旧成形，实际 " + be.getTier());
        helper.succeed();
    }

    /** 4 档每点 ESPE 同 3 档按 2 倍：满柱有效 172 800（24 × 1 万 × 2 × 0.36）；档位存盘读档不丢；4 档颜色和 3 档不同 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5PentacleTier4EspeAndSave(GameTestHelper helper) {
        helper.assertTrue(PlayerPentacleBlockEntity.multiplier(3) == 2 && PlayerPentacleBlockEntity.multiplier(4) == 2
                && PlayerPentacleBlockEntity.multiplier(2) == 1, "3、4 档 ×2，2 档 ×1");
        helper.assertTrue(PlayerPentacleBlockEntity.maxEffectiveESPE(4) == 172_800L && PlayerPentacleBlockEntity.maxEffectiveESPE(3) == 144_000L,
                "满柱有效 ESPE：3 档 144000、4 档 172800，实际 " + PlayerPentacleBlockEntity.maxEffectiveESPE(4));
        PlayerPentacleBlockEntity be = pentacle(helper, 4);
        helper.assertTrue(be.recheckTier() == 4, "4 档应成形");
        fill(helper, 0, 4, FULL);
        helper.assertTrue(be.effectiveESPE() == 172_800, "24 根满柱有效 ESPE 172800，实际 " + be.effectiveESPE());
        helper.assertTrue(be.consumeESPE(36_000), "抽得出 36000 有效 ESPE");
        double drawn = 24 * FULL - total(helper, 0, 4);
        helper.assertTrue(Math.abs(drawn - PlayerPentacleBlockEntity.rawCost(36_000) / 2D) < 1D, "按 2 倍折算：原始只抽一半，实际 " + drawn);

        CompoundTag saved = be.saveWithoutMetadata();
        PlayerPentacleBlockEntity loaded = new PlayerPentacleBlockEntity(helper.absolutePos(PENTACLE), be.getBlockState());
        loaded.load(saved);
        helper.assertTrue(loaded.getTier() == 4, "存盘读档后仍是 4 档，实际 " + loaded.getTier());
        float[] c3 = PlayerPentacleBlockEntity.tierColor(3);
        float[] c4 = PlayerPentacleBlockEntity.tierColor(4);
        helper.assertTrue(c3[0] != c4[0] || c3[1] != c4[1] || c3[2] != c4[2], "4 档有自己的颜色");
        helper.succeed();
    }

    /** 打开 4 档五芒星的界面（真的打开以后）：手册 1 ~ 4 档都打勾；3 档的不给 4 档的勾 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5PentacleTier4Advancement(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = pentacle(helper, 4);
        ServerPlayer player = playerAt(helper, new Vec3(5.5D, 2D, 9.5D), "c5-tier4");
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, be.createMenu(0, player.getInventory(), player)));
        helper.assertTrue(be.getTier() == 4, "createMenu 当场重判成 4 档");
        helper.assertTrue(done(player, PlayerPentacleBlockEntity.GUIDE_TIER3) && done(player, PlayerPentacleBlockEntity.GUIDE_TIER4),
                "4 档应给手册 3 档、4 档打勾");
        helper.assertTrue(data(player).getPentacleTier() == 4, "记下打开过的档位（4 档）");

        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(4)) {
            helper.setBlock(at(offset), Blocks.AIR);
        }
        ServerPlayer other = playerAt(helper, new Vec3(5.5D, 2D, 9.5D), "c5-tier3");
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(other, be.createMenu(0, other.getInventory(), other)));
        helper.assertTrue(done(other, PlayerPentacleBlockEntity.GUIDE_TIER3) && !done(other, PlayerPentacleBlockEntity.GUIDE_TIER4),
                "3 档只给 3 档的勾");
        helper.succeed();
    }

    /**
     * 治疗能力同 3 档：NEVER 写死 4、cureTier 只算到 3 档；换一张「只有 4 档付得起」的柱子容量表，效果仍是 NEVER（不会因为多了 4 档变成可抽）；
     * 默认容量下每个效果的档位都 ≤ 3。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void c5CureTierStopsAtThree(GameTestHelper helper) {
        helper.assertTrue(CorruptionGate.NEVER == 4 && CorruptionGate.CURE_MAX_TIER == 3, "NEVER 应写死 4，治疗只算到 3 档");
        long cost = 100_000L;
        helper.assertTrue(CorruptionGate.cureTier(0, cost, tier -> tier >= 4 ? 200_000L : 50_000L) == CorruptionGate.NEVER,
                "只有 4 档付得起的效果仍是 NEVER");
        helper.assertTrue(CorruptionGate.cureTier(2, cost, tier -> tier >= 3 ? 200_000L : 50_000L) == 3, "3 档付得起就是 3");
        helper.assertTrue(CorruptionGate.cureTier(0, 1L, tier -> 10L) == 0, "类别要求 0、0 档就够");
        for (CorruptionEffects.Effect effect : CorruptionEffects.ALL) {
            int tier = CorruptionGate.cureTier(effect);
            helper.assertTrue(tier <= 3, effect.id() + " 默认容量下应 ≤ 3 档治得了，实际 " + tier);
        }
        helper.succeed();
    }

    /**
     * 矩阵之影的 ESPE 预扣 / 退还走同一组柱位（0 ~ 4 档 24 个）：前四档的柱子空着、只有暗渎柱有 ESPE 时，4 档照样抽得出（从暗渎柱抽），
     * 按 4 档的倍数折回原始量退回去一点不少；前四档都满、暗渎柱被抽空时，退还能退进暗渎柱位。
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5ShadeContractUsesTier4Pillars(GameTestHelper helper) {
        PlayerPentacleBlockEntity be = pentacle(helper, 4);
        helper.assertTrue(be.recheckTier() == 4, "4 档应成形");
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(PENTACLE);
        fill(helper, 0, 3, 0D);
        fill(helper, 4, 4, FULL);
        int cost = 7_200;
        helper.assertTrue(be.consumeESPE(cost), "只有暗渎柱有 ESPE 时也抽得出");
        double raw = PlayerPentacleBlockEntity.rawCost(cost) / (double) PlayerPentacleBlockEntity.multiplier(4);
        helper.assertTrue(Math.abs(4 * FULL - total(helper, 4, 4) - raw) < 1D, "从暗渎柱抽走折算后的原始量 " + raw);
        double left = MatrixShadeContracts.refundEspe(level, abs, raw);
        helper.assertTrue(left <= EPS && Math.abs(total(helper, 0, 4) - 4 * FULL) < 1D, "退还一点不少，实际剩 " + left);

        fill(helper, 0, 3, FULL);
        fill(helper, 4, 4, 0D);
        double back = MatrixShadeContracts.refundEspe(level, abs, 3_000D);
        helper.assertTrue(back <= EPS && Math.abs(total(helper, 4, 4) - 3_000D) < 1D, "前四档满着时退进暗渎柱位，实际剩 " + back);
        IESPEHandler shade = ESPETransfer.loadedHandler(level, helper.absolutePos(at(ECPentacle.PILLAR_OFFSETS.get(4).get(0))),
                ESPETransfer.sideFacing(helper.absolutePos(at(ECPentacle.PILLAR_OFFSETS.get(4).get(0))), abs));
        helper.assertTrue(shade != null && shade.getESPE() > 0D, "第一根暗渎柱收到了退还");
        helper.succeed();
    }

    // ================================================================ 八 / 九阶

    /** 八阶站点要 4 档：8 格内只有 3 档五芒星时报档位不够，补成 4 档就 OK（自己的、ESPE 够） */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5RankEightNeedsTier4Site(GameTestHelper helper) {
        MatrixRankData.Entry eight = MatrixRankData.get(8);
        helper.assertTrue(eight != null && eight.pentacleTier() == 4, "八阶数据应生效、站点 4 档");
        ServerPlayer player = playerAt(helper, new Vec3(1.5D, 2D, 3.5D), "c5-rank8");
        PlayerPentacleBlockEntity be = pentacle(helper, 4);
        be.setOwner(player.getUUID(), player.getGameProfile().getName());
        helper.setBlock(CRUCIBLE, ECMatrix.MATRIX_CRUCIBLE.get());
        MatrixCrucibleBlockEntity crucible = (MatrixCrucibleBlockEntity) helper.getBlockEntity(CRUCIBLE);
        fill(helper, 0, 4, FULL);
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(4)) {
            helper.setBlock(at(offset), Blocks.AIR);
        }
        helper.assertTrue(be.recheckTier() == 3, "先搭成 3 档");
        MatrixTrialChecks.Site site = MatrixTrialChecks.findSite(player, crucible, eight.pentacleTier(), eight.espe());
        helper.assertTrue(site.state() == MatrixTrialChecks.SiteState.TIER && site.foundTier() == 3, "3 档不算八阶站点，实际 " + site.state());
        put(helper, ECPentacle.PILLAR_OFFSETS.get(4), ECCrystalPillars.SHADE_CRYSTAL_PILLAR.get());
        fill(helper, 4, 4, FULL);
        helper.assertTrue(be.recheckTier() == 4, "补成 4 档");
        site = MatrixTrialChecks.findSite(player, crucible, eight.pentacleTier(), eight.espe());
        helper.assertTrue(site.state() == MatrixTrialChecks.SiteState.OK && site.foundTier() == 4, "4 档自己的五芒星是站点，实际 " + site.state());
        helper.succeed();
    }

    /** 八 / 九阶数据逐项对 batch13.md §3.1.3（图谱、站点、祭品、能量、回响） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void c5RankEightNineData(GameTestHelper helper) {
        MatrixRankData.Entry eight = MatrixRankData.get(8);
        MatrixRankData.Entry nine = MatrixRankData.get(9);
        helper.assertTrue(eight != null && nine != null, "八、九阶数据都应生效（暗影核心、天灾残核注册了）");
        helper.assertTrue(eight.codex().total() == 30 && eight.codex().hoanna() == 8 && eight.codex().end() == 1 && eight.codex().mod() == 3,
                "八阶图谱：总 30、海纳尔 8、末地 1、本模组 3");
        helper.assertTrue(eight.mru() == 100_000 && eight.espe() == 12_000 && eight.ubmru() == 0, "八阶能量 MRU 10 万 + ESPE 1.2 万");
        helper.assertTrue(eight.echoes().count() == 9 && eight.echoes().alive() == 4 && eight.echoes().health() == 104
                && eight.echoes().damage() == 11 && eight.echoes().timeSeconds() == 140, "八阶回响 9 / 4 / 104 / 11 / 140");
        helper.assertTrue(offeringCount(eight, new ItemStack(item("condensed_matrix"))) == 3
                && offeringCount(eight, new ItemStack(ECShade.SHADE_CORE.get())) == 1
                && offeringCount(eight, new ItemStack(item("matter_of_eternity"))) == 1
                && offeringCount(eight, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get())) == 8 && eight.offerings().size() == 4,
                "八阶祭品：凝结矩阵 ×3、暗影核心、永恒物质、影晶 ×8");
        helper.assertTrue(nine.codex().total() == 33 && nine.codex().include().contains(EssentialCraft5.id("calamity_heart"))
                && nine.pentacleTier() == 4, "九阶：总 33、含天灾之心、站点 4 档");
        helper.assertTrue(nine.mru() == 100_000 && nine.espe() == 16_000, "九阶能量 MRU 10 万 + ESPE 1.6 万");
        helper.assertTrue(nine.echoes().count() == 10 && nine.echoes().alive() == 4 && nine.echoes().health() == 116
                && nine.echoes().damage() == 12 && nine.echoes().timeSeconds() == 150, "九阶回响 10 / 4 / 116 / 12 / 150");
        helper.assertTrue(offeringCount(nine, new ItemStack(item("condensed_matrix"))) == 3
                && offeringCount(nine, new ItemStack(ECShade.SHADE_CORE.get())) == 2
                && offeringCount(nine, new ItemStack(item("calamity_remnant"))) == 1 && nine.offerings().size() == 3,
                "九阶祭品：凝结矩阵 ×3、暗影核心 ×2、天灾残核");
        helper.succeed();
    }

    private static int offeringCount(MatrixRankData.Entry entry, ItemStack stack) {
        for (MatrixRankData.Offering offering : entry.offerings()) {
            if (offering.ingredient().test(stack)) {
                return offering.count();
            }
        }
        return 0;
    }

    private static Item item(String path) {
        Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
        if (item == null || item == Items.AIR) {
            throw new IllegalStateException("没有物品 " + path);
        }
        return item;
    }

    /** 八 / 九阶回报：暗影相性七阶 0、八阶 II、九阶 III（别的属性九阶 III）；容量 3.5 万 / 4 万；耐受 1.8 / 1.9；阶名、阶称到九 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void c5RankEightNineRewards(GameTestHelper helper) {
        helper.assertTrue(MatrixResonance.level(7, MatrixResonance.SHADE) == 0 && MatrixResonance.level(8, MatrixResonance.SHADE) == 2
                && MatrixResonance.level(9, MatrixResonance.SHADE) == 3, "暗影相性：七阶 0、八阶 II、九阶 III");
        helper.assertTrue(MatrixResonance.level(8, MatrixResonance.CHAOS) == 2 && MatrixResonance.level(9, MatrixResonance.CHAOS) == 3, "混沌八阶 II、九阶 III");
        helper.assertTrue(MatrixPool.capacity(8) == 35_000 && MatrixPool.capacity(9) == 40_000,
                "容量八阶 35000、九阶 40000，实际 " + MatrixPool.capacity(8) + " / " + MatrixPool.capacity(9));
        helper.assertTrue(Math.abs(1D + MatrixConfig.tolerancePerRank() * 8 - 1.8D) < 1.0E-9D
                && Math.abs(1D + MatrixConfig.tolerancePerRank() * 9 - 1.9D) < 1.0E-9D, "耐受八阶 ÷1.8、九阶 ÷1.9");
        helper.assertTrue(key(MatrixRank.rankName(9)).equals("matrix_rank.essentialcraft5.rank.9")
                && key(MatrixRank.title(9)).equals("matrix_rank.essentialcraft5.title.9")
                && key(MatrixRank.rankName(12)).equals("matrix_rank.essentialcraft5.rank.9"), "阶名到九阶（多了夹到九阶）");
        helper.succeed();
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : component.getString();
    }

    /** 用掉暗影投影：变成暗影属性、发第二十二章 attune；矩阵阶开着、不到八阶时聊天栏多一句提示（属性照样变） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void c5ShadeProjectionAttune(GameTestHelper helper) {
        ServerPlayer player = playerAt(helper, new Vec3(1.5D, 2D, 1.5D), "c5-attune");
        player.setGameMode(GameType.SURVIVAL);
        ItemStack projection = new ItemStack(ECMaterials.SHADE_MATRIX_PROJECTION.get());
        MatrixProjectionItem.setOwner(projection, player);
        helper.assertFalse(done(player, MatrixProjectionItem.GUIDE_SHADE_ATTUNE), "用之前没打勾");
        ECMaterials.SHADE_MATRIX_PROJECTION.get().finishUsingItem(projection, helper.getLevel(), player);
        helper.assertTrue(data(player).getMatrixType() == MatrixResonance.SHADE, "属性变成暗影");
        helper.assertTrue(done(player, MatrixProjectionItem.GUIDE_SHADE_ATTUNE), "第二十二章 attune 打勾");
        helper.assertTrue(projection.isEmpty(), "投影用掉");

        ItemStack frozen = new ItemStack(ECMaterials.FROZEN_MATRIX_PROJECTION.get());
        ServerPlayer other = playerAt(helper, new Vec3(1.5D, 2D, 1.5D), "c5-frozen");
        MatrixProjectionItem.setOwner(frozen, other);
        ECMaterials.FROZEN_MATRIX_PROJECTION.get().finishUsingItem(frozen, helper.getLevel(), other);
        helper.assertFalse(done(other, MatrixProjectionItem.GUIDE_SHADE_ATTUNE), "别的投影不打这一步");
        helper.succeed();
    }

    // ================================================================ 挚契

    private static DemonEntity demon(GameTestHelper helper, ServerPlayer summoner) {
        ServerLevel level = helper.getLevel();
        DemonEntity demon = ECDemon.DEMON.get().create(level);
        if (demon == null) {
            throw new IllegalStateException("建不出恶魔");
        }
        Vec3 abs = helper.absoluteVec(new Vec3(5.5D, 2D, 5.5D));
        demon.moveTo(abs.x, abs.y, abs.z, 0F, 0F);
        demon.setTestClearOverrides(0, null);
        demon.setSummoner(summoner.getUUID());
        demon.setWants(List.of(DemonWant.item(Items.IRON_BLOCK, 4, false)));
        level.addFreshEntity(demon);
        return demon;
    }

    private static ServerPlayer oathPlayer(GameTestHelper helper, String name, int standing) {
        ServerPlayer player = playerAt(helper, new Vec3(4.5D, 2D, 5.5D), name);
        player.setGameMode(GameType.SURVIVAL);
        data(player).setDemonStanding(standing);
        return player;
    }

    private static int oaths(ServerPlayer player) {
        return player.getInventory().countItem(ECShade.DEMON_OATH.get());
    }

    /** 69 不能立誓、不扣、恶魔还在（动作栏说要 70）；70 起影晶分在两格（主手 10 + 背包 7）也凑得齐：扣 16、给 1 个恶魔之誓、恶魔离开、声望不变 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5OathSwearsAtSeventy(GameTestHelper helper) {
        DemonStanding.setTestEnabledOverride(true);
        DemonEntity low = null;
        DemonEntity demon = null;
        try {
            ServerPlayer player = oathPlayer(helper, "c5-oath", 69);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 10));
            player.getInventory().setItem(9, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 7));
            player.setShiftKeyDown(true);
            low = demon(helper, player);
            InteractionResult result = low.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(result == InteractionResult.CONSUME && low.isAvailable(), "69 立不了誓，恶魔还在");
            helper.assertTrue(DemonEntity.umbralCount(player) == 17 && oaths(player) == 0, "69 什么都不扣");
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "潜行拿影晶不开交易界面");
            low.discard();

            data(player).setDemonStanding(70);
            demon = demon(helper, player);
            demon.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(DemonEntity.umbralCount(player) == 1, "扣 16 个影晶（两格凑齐），实际剩 " + DemonEntity.umbralCount(player));
            helper.assertTrue(oaths(player) == 1, "给 1 个恶魔之誓");
            helper.assertTrue(demon.isRemoved() || !demon.isAvailable(), "这次召唤结束，恶魔离开");
            helper.assertTrue(data(player).getDemonStanding() == 70, "立誓不算一笔交易、不加声望，实际 " + data(player).getDemonStanding());
            helper.assertTrue(player.getInventory().countItem(ECDemonic.ACKRONITE_INGOT.get()) == 0, "不给阿克罗尼特");
        } finally {
            if (low != null) {
                low.discard();
            }
            if (demon != null) {
                demon.discard();
            }
            DemonStanding.setTestEnabledOverride(null);
        }
        helper.succeed();
    }

    /** 影晶只有 15 个时不扣不给；不是召唤者的创造模式玩家立不了（交易可以，立誓不行）；没潜行照常开交易界面 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5OathRefusals(GameTestHelper helper) {
        DemonStanding.setTestEnabledOverride(true);
        DemonEntity demon = null;
        try {
            ServerPlayer owner = oathPlayer(helper, "c5-oath-owner", 80);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 15));
            owner.setShiftKeyDown(true);
            demon = demon(helper, owner);
            demon.interact(owner, InteractionHand.MAIN_HAND);
            helper.assertTrue(DemonEntity.umbralCount(owner) == 15 && oaths(owner) == 0 && demon.isAvailable(), "影晶不够：不扣、不给、恶魔还在");

            ServerPlayer admin = oathPlayer(helper, "c5-oath-admin", 80);
            admin.setGameMode(GameType.CREATIVE);
            admin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 32));
            admin.setShiftKeyDown(true);
            helper.assertTrue(demon.canTrade(admin), "创造模式的旁人能交易");
            helper.assertFalse(demon.swearOath(admin), "但立不了誓（只有召唤者）");
            helper.assertTrue(oaths(admin) == 0 && demon.isAvailable(), "旁人什么都没拿到，恶魔还在");

            owner.setShiftKeyDown(false);
            demon.interact(owner, InteractionHand.MAIN_HAND);
            helper.assertTrue(owner.containerMenu instanceof DemonTradeMenu, "不潜行右键照常开交易界面");
            owner.closeContainer();
            helper.assertTrue(DemonEntity.umbralCount(owner) == 15, "开界面不扣影晶");
        } finally {
            if (demon != null) {
                demon.discard();
            }
            DemonStanding.setTestEnabledOverride(null);
        }
        helper.succeed();
    }

    /** 挚契一档：门槛读 oath（70）、档名「挚契」、到档那句带 oath_cost（16） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void c5OathTier(GameTestHelper helper) {
        DemonStanding.setTestEnabledOverride(true);
        try {
            helper.assertTrue(DemonStanding.Tier.OATH.threshold() == 70, "挚契门槛 70");
            helper.assertTrue(DemonStanding.tier(69) == DemonStanding.Tier.BLOOD && DemonStanding.tier(70) == DemonStanding.Tier.OATH, "70 起是挚契");
            Component perk = DemonStanding.perk(DemonStanding.Tier.OATH);
            helper.assertTrue(perk.getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals("gui.essentialcraft5.demon_standing.perk.oath")
                    && String.valueOf(contents.getArgs()[0]).equals("16"), "挚契那句带要交 16 个影晶");
            helper.assertTrue(DemonStanding.Tier.OATH.key().equals("gui.essentialcraft5.demon_standing.tier.oath"), "档名键");
        } finally {
            DemonStanding.setTestEnabledOverride(null);
        }
        helper.succeed();
    }

    // ================================================================ 暗影核心

    private static void shell(GameTestHelper helper, Block block) {
        for (int x = MIN.getX(); x <= MAX.getX(); x++) {
            for (int y = MIN.getY(); y <= MAX.getY(); y++) {
                for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
                    if (x == MIN.getX() || x == MAX.getX() || y == MIN.getY() || y == MAX.getY() || z == MIN.getZ() || z == MAX.getZ()) {
                        helper.setBlock(new BlockPos(x, y, z), block);
                    }
                }
            }
        }
    }

    /** 一座 8 块虚空石、带稳定器的仓，底面正中放状态检测器；存 12 万冰冻 MRU（平衡值 0.4） */
    private static EnrichmentControllerBlockEntity chamber(GameTestHelper helper) {
        Block stone = ECMaterials.FORTIFIED_STONE.get();
        shell(helper, stone);
        helper.setBlock(EC_CONTROLLER, ECEnrichment.EC_CONTROLLER.get());
        helper.setBlock(HOLDING, ECEnrichment.EC_HOLDING_CHAMBER.get());
        helper.setBlock(BALANCER, ECPentacle.EC_BALANCER.get());
        helper.setBlock(STATE_CHECKER, ECEnrichment.EC_STATE_CHECKER.get());
        for (BlockPos spot : VOID_SPOTS) {
            helper.setBlock(spot, ECVoid.VOID_STONE.get());
        }
        EnrichmentControllerBlockEntity controller = (EnrichmentControllerBlockEntity) helper.getBlockEntity(EC_CONTROLLER);
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH && controller.voidCasing() == 8, "仓应成形、8 块虚空石");
        controller.storage().setMRU(120_000);
        controller.storage().setBalance(0.4F);
        return controller;
    }

    private static MRUPresenceEntity presence(GameTestHelper helper) {
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(CENTER));
        MRUPresenceEntity presence = MRUPresenceEntity.create(helper.getLevel(), at.x, at.y, at.z, MRUPresenceEntity.MAX_MRU, 1F, false, true);
        helper.getLevel().addFreshEntity(presence);
        return presence;
    }

    private static ItemEntity drop(GameTestHelper helper, ItemStack stack) {
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(CENTER));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack, 0D, 0D, 0D);
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    private static AABB chamberBox(GameTestHelper helper) {
        return new AABB(helper.absolutePos(MIN), helper.absolutePos(MAX).offset(1, 1, 1));
    }

    private static int count(GameTestHelper helper, Item item) {
        int sum = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, chamberBox(helper), e -> e.isAlive() && e.getItem().is(item))) {
            sum += entity.getItem().getCount();
        }
        return sum;
    }

    /** 当成海纳尔、暗影地点按 site 定、换上测试数值跑 body，最后置回并清掉仓里的聚集体和物品 */
    private static void shadeCase(GameTestHelper helper, boolean site, ShadeValues shade, Runnable body) {
        ServerLevel level = helper.getLevel();
        ECHoanna.setTestHoannaOverride(level);
        EnrichmentControllerBlockEntity.Config.setCollapseTestOverride(COLLAPSE);
        EnrichmentControllerBlockEntity.Config.setCondenseTestOverride(CONDENSE);
        EnrichmentControllerBlockEntity.Config.setShadeTestOverride(shade);
        EnrichmentControllerBlockEntity.Config.setShadeSiteTestOverride(site);
        HoannaTide.setTestOverride(0F);
        try {
            body.run();
        } finally {
            HoannaEvents.stop(level);
            HoannaTide.setTestOverride(null);
            EnrichmentControllerBlockEntity.Config.setShadeSiteTestOverride(null);
            EnrichmentControllerBlockEntity.Config.setShadeTestOverride(null);
            EnrichmentControllerBlockEntity.Config.setCondenseTestOverride(null);
            EnrichmentControllerBlockEntity.Config.setCollapseTestOverride(null);
            ECHoanna.clearTestOverride();
            AABB box = chamberBox(helper).inflate(1D);
            level.getEntitiesOfClass(ItemEntity.class, box, e -> true).forEach(ItemEntity::discard);
            level.getEntitiesOfClass(MRUPresenceEntity.class, box, e -> true).forEach(MRUPresenceEntity::discard);
        }
    }

    private static void second(GameTestHelper helper, EnrichmentControllerBlockEntity controller, CollapseState expected, int progress, String when) {
        controller.chamberSecond(List.of());
        helper.assertTrue(controller.collapseState() == expected, when + "：状态应为 " + expected + "，实际 " + controller.collapseState());
        helper.assertTrue(controller.collapseProgress() == progress, when + "：进度应为 " + progress + "，实际 " + controller.collapseProgress());
    }

    /**
     * 暗影地点 + 外壳里 5 个影晶（3 + 2 两堆）：冰冻仓（平衡值 0.4）也走暗影分支；凝结（同一座仓护板要求 0）让位；
     * 攒满 3 秒出 1 个永不消失的暗影核心、扣 10 万 MRU、聚集体消失、吃掉 4 个影晶（剩 1）、不出虚空核心；附近的人收到提示、不打第十六章的勾。
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5ShadeCoreCollapses(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper);
        ServerPlayer near = playerAt(helper, new Vec3(5.5D, 1D, 1.5D), "c5-shade-near");
        shadeCase(helper, true, SHADE, () -> {
            MRUPresenceEntity cu = presence(helper);
            ItemEntity a = drop(helper, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 3));
            drop(helper, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 2));
            second(helper, controller, CollapseState.SHADE, 1, "第 1 秒");
            helper.assertTrue(controller.shadeHint() == ShadeHint.ACTIVE && controller.umbralCount() == 5, "暗影分支生效、数到 5 个影晶");
            helper.assertTrue(a.getAge() < 0, "暗影地点里的影晶设成永不消失");
            helper.assertTrue(controller.condenseState() == CondenseState.SHADE, "凝结让位，实际 " + controller.condenseState());
            second(helper, controller, CollapseState.SHADE, 2, "第 2 秒");
            controller.collapseSecond(List.of(near));
            helper.assertFalse(cu.isAlive(), "第 3 秒坍缩：聚集体消失");
            helper.assertTrue(count(helper, ECShade.SHADE_CORE.get()) == 1, "仓里出 1 个暗影核心");
            helper.assertTrue(count(helper, ECVoid.VOID_CORE.get()) == 0, "不出虚空核心");
            helper.assertTrue(count(helper, ECHoannaMaterials.UMBRAL_CRYSTAL.get()) == 1, "吃掉 4 个影晶、剩 1，实际 "
                    + count(helper, ECHoannaMaterials.UMBRAL_CRYSTAL.get()));
            helper.assertTrue(controller.storage().getMRU() == 20_000, "扣 10 万 MRU，实际 " + controller.storage().getMRU());
            helper.assertTrue(controller.collapseProgress() == 0, "进度归零");
            helper.assertFalse(done(near, EnrichmentControllerBlockEntity.GUIDE_COLLAPSE), "暗影核心不打第十六章「坍缩」的勾");
            for (ItemEntity core : helper.getLevel().getEntitiesOfClass(ItemEntity.class, chamberBox(helper), e -> e.getItem().is(ECShade.SHADE_CORE.get()))) {
                helper.assertTrue(core.getAge() < 0, "暗影核心永不消失");
            }
            controller.chamberSecond(List.of());
            helper.assertTrue(controller.shadeHint() == ShadeHint.UMBRAL && controller.umbralCount() == 1, "剩 1 个影晶：提示影晶不够");
        });
        helper.succeed();
    }

    /**
     * 影晶只有 3 个：照旧按平衡值凝虚空核心（FROZEN），提示「影晶 3 / 4」；地点不对、外壳里有 4 个影晶：也照旧，提示地点不对；
     * 暗影核心关掉：不数影晶、不提示；外壳里虚空石不够暗影的要求时写 CASING、带暗影的块数要求。
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5ShadeFallsBack(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper);
        shadeCase(helper, true, SHADE, () -> {
            presence(helper);
            ItemEntity umbral = drop(helper, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 3));
            second(helper, controller, CollapseState.FROZEN, 1, "影晶 3 个");
            helper.assertTrue(controller.shadeHint() == ShadeHint.UMBRAL && controller.umbralCount() == 3, "提示影晶不够");
            assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.shade.umbral", new Object[] {3, 4});

            umbral.setItem(umbral.getItem().copyWithCount(4));
            EnrichmentControllerBlockEntity.Config.setShadeSiteTestOverride(false);
            second(helper, controller, CollapseState.FROZEN, 2, "地点不对");
            helper.assertTrue(controller.shadeHint() == ShadeHint.NOT_SITE, "提示地点不对，实际 " + controller.shadeHint());
            assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.shade.not_site", new Object[] {32});

            EnrichmentControllerBlockEntity.Config.setShadeSiteTestOverride(true);
            EnrichmentControllerBlockEntity.Config.setShadeTestOverride(new ShadeValues(false, 100_000, 3, 4, 32, 8));
            second(helper, controller, CollapseState.FROZEN, 3, "暗影核心关掉");
            helper.assertTrue(controller.shadeHint() == ShadeHint.NONE && controller.umbralCount() == 0, "关掉时不数影晶");

            EnrichmentControllerBlockEntity.Config.setShadeTestOverride(new ShadeValues(true, 100_000, 3, 4, 32, 9));
            second(helper, controller, CollapseState.CASING, 3, "暗影要 9 块虚空级外壳");
            assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.collapse.casing", new Object[] {8, 9});
            EnrichmentControllerBlockEntity.Config.setShadeTestOverride(new ShadeValues(true, 130_000, 3, 4, 32, 8));
            second(helper, controller, CollapseState.LOW_MRU, 3, "暗影要 13 万 MRU");
            assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.collapse.low_mru",
                    new Object[] {120_000, 130_000});
            // 进度三个分支共用：换成 50 秒，免得接着的 3 秒进度当场攒满
            EnrichmentControllerBlockEntity.Config.setShadeTestOverride(new ShadeValues(true, 100_000, 50, 4, 32, 8));
            second(helper, controller, CollapseState.SHADE, 4, "都齐了：换到暗影分支，进度接着走");
            helper.assertTrue(count(helper, ECVoid.VOID_CORE.get()) == 0, "没出虚空核心");
        });
        helper.succeed();
    }

    /** 不在海纳尔：暗影分支不起作用（坍缩在主世界本来就不走） */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5ShadeOnlyInHoanna(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper);
        shadeCase(helper, true, SHADE, () -> {
            ECHoanna.clearTestOverride();
            presence(helper);
            drop(helper, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 8));
            second(helper, controller, CollapseState.WRONG_DIMENSION, 0, "主世界");
            helper.assertTrue(controller.shadeHint() == ShadeHint.NONE, "主世界不提示暗影核心");
        });
        helper.succeed();
    }

    /** 黑暗天灾中每秒 +3（collapse.event_multiplier）；别的天灾（日阵）不加快，仍 +1；同一座仓凝虚空核心时任何天灾都 ×3（照旧） */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5ShadeDarknessTriples(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper);
        ServerLevel level = helper.getLevel();
        shadeCase(helper, true, new ShadeValues(true, 100_000, 50, 4, 32, 8), () -> {
            presence(helper);
            drop(helper, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 4));
            second(helper, controller, CollapseState.SHADE, 1, "平时");
            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.SUN_ARRAY, 200), "开始一场日阵");
            second(helper, controller, CollapseState.SHADE, 2, "日阵不加快暗影核心");
            HoannaEvents.stop(level);
            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.DARKNESS, 200), "开始一场黑暗");
            second(helper, controller, CollapseState.SHADE, 5, "黑暗 ×3");
            helper.assertTrue(controller.collapseEvent(), "记下这一秒按天灾算");
            HoannaEvents.stop(level);
            second(helper, controller, CollapseState.SHADE, 6, "天灾结束");
        });
        helper.succeed();
    }

    /** 仓里的暗影核心那一堆满一组：攒满时停在 OUTPUT_FULL，不扣 MRU、不吃影晶、聚集体还在 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5ShadeStopsWhenFull(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper);
        shadeCase(helper, true, SHADE, () -> {
            ItemEntity pile = drop(helper, new ItemStack(ECShade.SHADE_CORE.get(), 64));
            pile.setUnlimitedLifetime();
            MRUPresenceEntity cu = presence(helper);
            drop(helper, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 4));
            second(helper, controller, CollapseState.SHADE, 1, "第 1 秒");
            second(helper, controller, CollapseState.SHADE, 2, "第 2 秒");
            second(helper, controller, CollapseState.OUTPUT_FULL, 3, "攒满、那一堆满一组");
            helper.assertTrue(cu.isAlive() && controller.storage().getMRU() == 120_000, "满一组时不坍缩、不扣 MRU");
            helper.assertTrue(count(helper, ECHoannaMaterials.UMBRAL_CRYSTAL.get()) == 4, "不吃影晶");
            helper.assertTrue(count(helper, ECShade.SHADE_CORE.get()) == 64, "不另生成");
        });
        helper.succeed();
    }

    /** 状态检测器：界面数据带暗影提示和影晶数，坍缩那一支要攒的秒数换成暗影的；暗影分支时界面上显示坍缩那一条 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5ShadeStatusReadout(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper);
        ServerPlayer viewer = playerAt(helper, new Vec3(5.5D, 1D, 1.5D), "c5-shade-view");
        EnrichmentStateCheckerBlockEntity checker = (EnrichmentStateCheckerBlockEntity) helper.getBlockEntity(STATE_CHECKER);
        EnrichmentStateCheckerMenu menu = new EnrichmentStateCheckerMenu(0, viewer.getInventory(), checker);
        shadeCase(helper, true, SHADE, () -> {
            presence(helper);
            ItemEntity umbral = drop(helper, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 2));
            controller.chamberSecond(List.of());
            helper.assertTrue(checker.isLinked() && menu.showsCollapse(), "连着仓、显示坍缩那一条");
            helper.assertTrue(menu.shadeHint() == ShadeHint.UMBRAL && menu.umbral() == 2, "界面数据：影晶 2 个不够，实际 " + menu.shadeHint() + " / " + menu.umbral());
            helper.assertTrue(menu.collapseSeconds() == COLLAPSE.seconds(), "没走暗影分支时按坍缩的秒数");
            umbral.setItem(umbral.getItem().copyWithCount(4));
            controller.chamberSecond(List.of());
            helper.assertTrue(menu.collapseState() == CollapseState.SHADE && menu.shadeHint() == ShadeHint.ACTIVE, "界面数据：正在凝暗影核心");
            helper.assertTrue(menu.collapseSeconds() == SHADE.seconds(), "要攒的秒数换成暗影核心的 " + SHADE.seconds() + "，实际 " + menu.collapseSeconds());
            helper.assertTrue(menu.condenseState() == CondenseState.SHADE, "凝结那一条写让位");
            List<Component> lines = controller.describe(StructureResult.MATCH);
            assertLine(helper, lines, "message.essentialcraft5.ec_controller.collapse.shade", null);
            assertLine(helper, lines, "message.essentialcraft5.ec_controller.condense.shade", null);
            List<Component> tooltip = EnrichmentControllerBlockEntity.collapseLines(menu.collapseState(), menu.collapseProgress(), menu.collapseSeconds(),
                    false, menu.voidCasing(), menu.presenceMRU(), 120_000, 0.4F, COLLAPSE, menu.shadeHint(), menu.umbral(), SHADE);
            assertLine(helper, tooltip, "message.essentialcraft5.ec_controller.collapse.progress", new Object[] {2, SHADE.seconds()});
        });
        helper.succeed();
    }

    /**
     * 平衡值 1.0（落在凝结区间里）、凝结的条件也都齐（凝结 2 秒）：暗影分支生效时凝结照样让位，进度停在 0、聚集体留着、不出凝结矩阵，
     * 第 3 秒出的是暗影核心；影晶吃完、暗影分支不再生效以后，同一座仓的凝结照常走（说明凝结的条件确实是齐的）
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5ShadeCondenseYieldsInBalance(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper);
        controller.storage().setBalance(1.0F);
        shadeCase(helper, true, SHADE, () -> {
            EnrichmentControllerBlockEntity.Config.setCondenseTestOverride(new CondenseValues(true, 50_000, 2, 0, 15_000, 0.8D, 1.2D, 1, List.of()));
            MRUPresenceEntity cu = presence(helper);
            drop(helper, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 4));
            Item matrix = ECEnrichment.CONDENSED_MATRIX.get();
            for (int t = 1; t <= 2; t++) {
                second(helper, controller, CollapseState.SHADE, t, "第 " + t + " 秒");
                helper.assertTrue(controller.condenseState() == CondenseState.SHADE && controller.condenseProgress() == 0,
                        "第 " + t + " 秒凝结让位、进度不动，实际 " + controller.condenseState() + " / " + controller.condenseProgress());
                helper.assertTrue(cu.isAlive() && count(helper, matrix) == 0, "第 " + t + " 秒聚集体还在、没出凝结矩阵");
            }
            controller.chamberSecond(List.of());
            helper.assertFalse(cu.isAlive(), "第 3 秒暗影坍缩吃掉聚集体");
            helper.assertTrue(count(helper, ECShade.SHADE_CORE.get()) == 1 && count(helper, matrix) == 0, "出 1 个暗影核心、不出凝结矩阵");

            presence(helper);
            controller.storage().setMRU(120_000);
            controller.storage().setBalance(1.0F);
            controller.chamberSecond(List.of());
            helper.assertTrue(controller.shadeHint() == ShadeHint.UMBRAL, "影晶吃完，暗影分支不再生效，实际 " + controller.shadeHint());
            helper.assertTrue(controller.condenseState() == CondenseState.CONDENSING && controller.condenseProgress() == 1,
                    "不走暗影分支时照常凝结，实际 " + controller.condenseState() + " / " + controller.condenseProgress());
            controller.chamberSecond(List.of());
            helper.assertTrue(count(helper, matrix) == 1, "凝出 1 个凝结矩阵");
        });
        helper.succeed();
    }

    /**
     * 先拿 3 个影晶在日阵里按虚空核心攒（×3），再补第 4 个换到暗影分支：暗影核心不认别的天灾的倍数，进度从头算；这个记号随存档读回。
     * 在黑暗里攒的（暗影那支同样 ×3）换过去照旧接着走
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5ShadeBoostedProgressRestarts(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper);
        ServerLevel level = helper.getLevel();
        shadeCase(helper, true, new ShadeValues(true, 100_000, 50, 4, 32, 8), () -> {
            presence(helper);
            ItemEntity umbral = drop(helper, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), 3));
            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.SUN_ARRAY, 200), "开始一场日阵");
            second(helper, controller, CollapseState.FROZEN, 3, "影晶 3 个、日阵里凝虚空核心 ×3");
            controller.load(controller.saveWithoutMetadata());
            helper.assertTrue(controller.collapseProgress() == 3 && controller.isFormed(), "读档后进度还在");
            HoannaEvents.stop(level);
            umbral.setItem(umbral.getItem().copyWithCount(4));
            second(helper, controller, CollapseState.SHADE, 1, "补上第 4 个：日阵里攒的不算，从头算");
            second(helper, controller, CollapseState.SHADE, 2, "接着走");

            umbral.setItem(umbral.getItem().copyWithCount(3));
            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.DARKNESS, 200), "开始一场黑暗");
            second(helper, controller, CollapseState.FROZEN, 5, "黑暗里凝虚空核心 ×3");
            HoannaEvents.stop(level);
            umbral.setItem(umbral.getItem().copyWithCount(4));
            second(helper, controller, CollapseState.SHADE, 6, "黑暗里攒的换到暗影分支照旧算");
            helper.assertTrue(count(helper, ECVoid.VOID_CORE.get()) == 0 && count(helper, ECShade.SHADE_CORE.get()) == 0, "都还没攒满");
        });
        helper.succeed();
    }

    /** 4 档的颜色：档位文字六种颜色各不相同（4 档不再借 3 档的红），越界夹到两头；IPlayerData 的默认实现当成全都解锁，到 MAX_TIER */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void c5PentacleTier4Colors(GameTestHelper helper) {
        Set<Integer> colors = new HashSet<>();
        for (int t = -1; t <= ECPentacle.MAX_TIER; t++) {
            colors.add(PlayerPentacleBlockEntity.tierTextColor(t));
        }
        helper.assertTrue(colors.size() == ECPentacle.MAX_TIER + 2, "每档文字颜色都不同，实际 " + colors.size() + " 种");
        helper.assertTrue(PlayerPentacleBlockEntity.tierTextColor(ECPentacle.MAX_TIER + 3) == PlayerPentacleBlockEntity.tierTextColor(ECPentacle.MAX_TIER)
                && PlayerPentacleBlockEntity.tierTextColor(-5) == PlayerPentacleBlockEntity.tierTextColor(-1), "越界夹到两头");
        IPlayerData bare = (IPlayerData) Proxy.newProxyInstance(IPlayerData.class.getClassLoader(), new Class<?>[] {IPlayerData.class},
                (proxy, method, args) -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : zero(method.getReturnType()));
        helper.assertTrue(bare.getPentacleTier() == ECPentacle.MAX_TIER, "默认实现到 " + ECPentacle.MAX_TIER + " 档，实际 " + bare.getPentacleTier());
        helper.succeed();
    }

    /** 代理接口时非默认方法的返回值：基本类型给 0 / false，其余 null */
    @Nullable
    private static Object zero(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }

    /**
     * 两只手各拿一样（被俘灵魂、影晶）潜行右键自己的恶魔：这次交互那只手里的那样优先。契约之誉 50 时主手灵魂 → 血契；
     * 主手影晶、副手灵魂，立不了誓 → 改走血契；契约之誉 80 时主手影晶 → 立誓，主手灵魂 → 血契（不扣影晶）
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void c5OathHandPriority(GameTestHelper helper) {
        DemonStanding.setTestEnabledOverride(true);
        DemonTrades.setTestOverride(List.of(DemonTrades.Entry.item(Items.IRON_BLOCK, 8, false, 1)));
        List<DemonEntity> demons = new ArrayList<>();
        try {
            Item umbral = ECHoannaMaterials.UMBRAL_CRYSTAL.get();
            ServerPlayer mid = oathPlayer(helper, "c5-oath-hand50", 50);
            mid.setShiftKeyDown(true);
            mid.setItemInHand(InteractionHand.MAIN_HAND, CapturedSoulItem.of(EntityType.VILLAGER, 3));
            mid.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(umbral, 16));
            DemonEntity a = demon(helper, mid);
            demons.add(a);
            InteractionResult result = a.interact(mid, InteractionHand.MAIN_HAND);
            helper.assertTrue(result == InteractionResult.CONSUME && a.hasRerolled() && mid.getMainHandItem().getCount() == 2,
                    "50：主手灵魂 → 血契，交 1 个灵魂");
            helper.assertTrue(DemonEntity.umbralCount(mid) == 16 && oaths(mid) == 0, "不扣影晶");

            mid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(umbral, 16));
            mid.setItemInHand(InteractionHand.OFF_HAND, CapturedSoulItem.of(EntityType.VILLAGER, 3));
            DemonEntity b = demon(helper, mid);
            demons.add(b);
            b.interact(mid, InteractionHand.MAIN_HAND);
            helper.assertTrue(b.hasRerolled() && mid.getOffhandItem().getCount() == 2 && DemonEntity.umbralCount(mid) == 16,
                    "50：主手影晶立不了誓，副手灵魂 → 血契");

            ServerPlayer high = oathPlayer(helper, "c5-oath-hand80", 80);
            high.setShiftKeyDown(true);
            high.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(umbral, 16));
            high.setItemInHand(InteractionHand.OFF_HAND, CapturedSoulItem.of(EntityType.VILLAGER, 3));
            DemonEntity c = demon(helper, high);
            demons.add(c);
            c.interact(high, InteractionHand.MAIN_HAND);
            helper.assertTrue(oaths(high) == 1 && DemonEntity.umbralCount(high) == 0 && !c.hasRerolled() && high.getOffhandItem().getCount() == 3,
                    "80：主手影晶 → 立誓，灵魂不动");

            high.setItemInHand(InteractionHand.MAIN_HAND, CapturedSoulItem.of(EntityType.VILLAGER, 3));
            high.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(umbral, 16));
            int oathsBefore = oaths(high);
            DemonEntity d = demon(helper, high);
            demons.add(d);
            d.interact(high, InteractionHand.MAIN_HAND);
            helper.assertTrue(d.hasRerolled() && high.getMainHandItem().getCount() == 2 && DemonEntity.umbralCount(high) == 16 && oaths(high) == oathsBefore,
                    "80：主手灵魂 → 血契，不立誓");
        } finally {
            demons.forEach(DemonEntity::discard);
            DemonTrades.setTestOverride(null);
            DemonStanding.setTestEnabledOverride(null);
        }
        helper.succeed();
    }

    /** 地点采样：中心、正方向 32 格、斜方向（23, 23）算；33 格外不算；半径 0 只看中心；最多 9 点 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void c5ShadeSiteSampling(GameTestHelper helper) {
        BlockPos c = new BlockPos(100, 64, -200);
        helper.assertTrue(EnrichmentControllerBlockEntity.shadeSamplePoints(c, 32).size() == 9, "采 9 点");
        helper.assertTrue(EnrichmentControllerBlockEntity.shadeSiteAt(p -> p.equals(c), c, 32), "中心在暗影里");
        helper.assertTrue(EnrichmentControllerBlockEntity.shadeSiteAt(p -> p.getX() >= c.getX() + 32, c, 32), "东边 32 格是暗影");
        helper.assertFalse(EnrichmentControllerBlockEntity.shadeSiteAt(p -> p.getX() >= c.getX() + 33, c, 32), "33 格外不算");
        helper.assertTrue(EnrichmentControllerBlockEntity.shadeSiteAt(p -> p.getX() <= c.getX() - 23 && p.getZ() <= c.getZ() - 23, c, 32),
                "西北斜 32 格（每轴 23）是暗影");
        helper.assertFalse(EnrichmentControllerBlockEntity.shadeSiteAt(p -> p.getZ() >= c.getZ() + 10, c, 0), "半径 0 只看中心");
        helper.assertTrue(EnrichmentControllerBlockEntity.SHADE_CORE_SITES.location().equals(EssentialCraft5.id("shade_core_sites")), "标签 id");
        helper.succeed();
    }

    // ================================================================ 配方

    /**
     * 暗渎柱：魔力工作台，中心恶魔柱、四角暗影核心 + 影晶 ×3，2 万 MRU；物品是史诗、不再写「没有合成配方」。
     * 天灾信标：有序合成，闪耀榍石 ×4、暗影核心 ×2、恶魔之誓、故障核心，一格空。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void c5ShadeRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Recipe<?> pillar = level.getRecipeManager().byKey(EssentialCraft5.id("crystal_pillars/shade_crystal_pillar")).orElse(null);
        helper.assertTrue(pillar instanceof MagicianTableRecipe, "暗渎柱应有魔力工作台配方");
        MagicianTableRecipe table = (MagicianTableRecipe) pillar;
        helper.assertTrue(table.getResult().is(ECCrystalPillars.SHADE_CRYSTAL_PILLAR.get().asItem()) && table.getMRU() == 20_000, "产暗渎柱、2 万 MRU");
        List<Ingredient> in = table.getIngredients();
        helper.assertTrue(in.get(0).test(new ItemStack(ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR.get())), "中心恶魔柱");
        int cores = 0;
        int umbral = 0;
        for (int i = 1; i < in.size(); i++) {
            cores += in.get(i).test(new ItemStack(ECShade.SHADE_CORE.get())) ? 1 : 0;
            umbral += in.get(i).test(new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get())) ? 1 : 0;
        }
        helper.assertTrue(cores == 1 && umbral == 3, "四角暗影核心 ×1、影晶 ×3，实际 " + cores + " / " + umbral);
        ItemStack stack = new ItemStack(ECCrystalPillars.SHADE_CRYSTAL_PILLAR.get());
        helper.assertTrue(stack.getRarity() == Rarity.EPIC, "暗渎柱仍是史诗");
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, level, tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.stream().noneMatch(line -> line.getContents() instanceof TranslatableContents c && c.getKey().equals(CreativeOnly.TOOLTIP_KEY)),
                "不再写「没有合成配方」");
        helper.assertTrue(Math.abs(MithrilineCrystalPillarBlockEntity.tierMultiplier(4) - 4D) < 1.0E-9D, "暗渎柱产 ESPE ×4");

        Recipe<?> beacon = level.getRecipeManager().byKey(EssentialCraft5.id("shade/calamity_beacon")).orElse(null);
        helper.assertTrue(beacon != null && beacon.getResultItem(level.registryAccess()).is(item("calamity_beacon")), "天灾信标有配方");
        int titanite = 0;
        int shadeCores = 0;
        int oaths = 0;
        int glitchy = 0;
        int empty = 0;
        for (Ingredient ingredient : beacon.getIngredients()) {
            if (ingredient.isEmpty()) {
                empty++;
                continue;
            }
            titanite += ingredient.test(new ItemStack(item("twinkling_titanite"))) ? 1 : 0;
            shadeCores += ingredient.test(new ItemStack(ECShade.SHADE_CORE.get())) ? 1 : 0;
            oaths += ingredient.test(new ItemStack(ECShade.DEMON_OATH.get())) ? 1 : 0;
            glitchy += ingredient.test(new ItemStack(item("glitchy_core"))) ? 1 : 0;
        }
        helper.assertTrue(titanite == 4 && shadeCores == 2 && oaths == 1 && glitchy == 1 && empty == 1,
                "信标：闪耀榍石 4、暗影核心 2、恶魔之誓 1、故障核心 1、空 1，实际 " + titanite + "/" + shadeCores + "/" + oaths + "/" + glitchy + "/" + empty);
        helper.succeed();
    }

    private static void assertLine(GameTestHelper helper, List<Component> lines, String key, Object[] args) {
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)) {
                if (args != null) {
                    Object[] actual = contents.getArgs();
                    helper.assertTrue(actual.length == args.length, key + " 的参数个数应为 " + args.length);
                    for (int i = 0; i < args.length; i++) {
                        helper.assertTrue(String.valueOf(args[i]).equals(String.valueOf(actual[i])),
                                key + " 第 " + (i + 1) + " 个参数应为 " + args[i] + "，实际 " + actual[i]);
                    }
                }
                return;
            }
        }
        helper.fail("没有这一行：" + key);
    }
}
