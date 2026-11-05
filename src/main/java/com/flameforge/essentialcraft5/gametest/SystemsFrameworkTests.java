package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.IWindAffinityItem;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.block.MithrilineCrystalPillarBlock;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.command.CorruptionCommands;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.corruption.MRUCorruption;
import com.flameforge.essentialcraft5.enrichment.EnrichmentStabilizer;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.item.MagicalWaterItem;
import com.flameforge.essentialcraft5.player.PlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import com.flameforge.essentialcraft5.registry.ECMRUCU;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructureResult;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 第三批框架 G8~G12（代号 Systems）的 GameTest：
 * <ul>
 *   <li>G8 水晶柱档位：米斯莱柱按方块取档位 0、附魔 7.5、方块实体类型不变、存档格式不变；档位表；结构规则只认底座、按档位筛。</li>
 *   <li>G9 玩家腐化数据：增删查、NBT 往返（顺序、去重、认不出的 id 保留）、死亡复制、D14 迁移只清一次；25 行效果表；抽取概率与候选；指令。</li>
 *   <li>G10 聚集体稳定：稳定期间一轮结算不给玩家加矩阵损伤、存盘、吞并继承；富集仓控制器只稳定外壳范围里的、没有稳定器时不稳定。</li>
 *   <li>G11 风之好感：系数、原作的取整、加好感夹上限、FakePlayer 不加。</li>
 *   <li>G12 魔力水 α、MRU 腐化 γ 的钩子：没戴饰品时照旧。</li>
 * </ul>
 * 别的模块的物品 / 方块（淡青水晶柱、稳定器、α / γ 指环、风法师兜帽）按注册名取，没注册就跳过那部分断言（porting_guide §6）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class SystemsFrameworkTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    private static final double EPS = 1.0E-6D;

    /** 富集仓：5×3×5 外壳，控制器在北墙正中（同 EnrichmentTests） */
    private static final BlockPos SHELL_MIN = new BlockPos(3, 1, 3);
    private static final BlockPos SHELL_MAX = new BlockPos(7, 3, 7);
    private static final BlockPos CONTROLLER = new BlockPos(5, 2, 3);
    private static final BlockPos WEST_WALL = new BlockPos(3, 2, 5);

    private SystemsFrameworkTests() {
    }

    // ================================================================ 工具

    private static CommandSourceStack source(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .withPermission(ECCommands.PERMISSION_LEVEL)
                .withSuppressedOutput();
    }

    private static IPlayerData data(GameTestHelper helper, Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("模拟玩家应挂着玩家数据");
        }
        return data;
    }

    private static Block block(String path) {
        ResourceLocation id = EssentialCraft5.id(path);
        return ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null;
    }

    private static Item item(String path) {
        ResourceLocation id = EssentialCraft5.id(path);
        return ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
    }

    /** 把 stack 戴进第一个戒指 / 项链 / 腰带槽（Curios），戴不上返回 false */
    private static boolean equipCurio(Player player, ItemStack stack) {
        return CuriosApi.getCuriosInventory(player).map(inventory -> {
            for (String slot : List.of("ring", "necklace", "belt")) {
                if (inventory.getStacksHandler(slot).map(handler -> handler.getSlots() > 0).orElse(false)) {
                    inventory.setEquippedCurio(slot, 0, stack);
                    // 模拟玩家不 tick，Curios 不会发换装事件：手动让 CuriosCompat 的缓存作废
                    CuriosCompat.invalidate(player);
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private static void shell(GameTestHelper helper, Block block) {
        for (int x = SHELL_MIN.getX(); x <= SHELL_MAX.getX(); x++) {
            for (int y = SHELL_MIN.getY(); y <= SHELL_MAX.getY(); y++) {
                for (int z = SHELL_MIN.getZ(); z <= SHELL_MAX.getZ(); z++) {
                    if (x == SHELL_MIN.getX() || x == SHELL_MAX.getX() || y == SHELL_MIN.getY() || y == SHELL_MAX.getY()
                            || z == SHELL_MIN.getZ() || z == SHELL_MAX.getZ()) {
                        helper.setBlock(new BlockPos(x, y, z), block);
                    }
                }
            }
        }
    }

    private static MRUPresenceEntity presence(GameTestHelper helper, Vec3 relative, int mru) {
        Vec3 at = helper.absoluteVec(relative);
        MRUPresenceEntity presence = MRUPresenceEntity.create(helper.getLevel(), at.x, at.y, at.z, mru, 1F, false, true);
        helper.getLevel().addFreshEntity(presence);
        return presence;
    }

    // ================================================================ G8 水晶柱档位

    /** 档位表照原作 getEnchantPowerBonus：7.5 / 15 / 30 / 60 / 120，序号 0~4 */
    @GameTest(template = EMPTY)
    public static void pillarTierTable(GameTestHelper helper) {
        float[] expected = {7.5F, 15F, 30F, 60F, 120F};
        MithrilineCrystalPillarBlock.Tier[] tiers = MithrilineCrystalPillarBlock.Tier.values();
        helper.assertTrue(tiers.length == 5, "应有 5 档");
        for (int i = 0; i < tiers.length; i++) {
            helper.assertTrue(tiers[i].index() == i && MithrilineCrystalPillarBlock.Tier.byIndex(i) == tiers[i], "序号应为 " + i);
            helper.assertTrue(tiers[i].enchantPower() == expected[i], tiers[i] + " 的附魔加成应为 " + expected[i]);
        }
        helper.assertTrue(MithrilineCrystalPillarBlock.Tier.byIndex(5) == null && MithrilineCrystalPillarBlock.Tier.byIndex(-1) == null, "越界应为 null");
        helper.succeed();
    }

    /**
     * 米斯莱柱的现有行为不变：档位 0（ESPE tier 0）、附魔 7.5 只在底座、方块实体类型还是 mithriline_crystal_pillar、
     * 存盘只有 espe_storage 一个键（档位不存盘）、读回来 ESPE 不变；espe_pillars 标签和结构规则只认底座、按档位筛
     */
    @GameTest(template = EMPTY)
    public static void mithrilinePillarKeepsTierZero(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        ServerLevel level = helper.getLevel();
        BlockState bottom = level.getBlockState(helper.absolutePos(base));
        BlockState middle = level.getBlockState(helper.absolutePos(base.above()));
        helper.assertTrue(ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get().tier() == MithrilineCrystalPillarBlock.Tier.MITHRILINE, "米斯莱柱应是 0 档");
        helper.assertTrue(MithrilineCrystalPillarBlock.tierOf(bottom) == 0 && MithrilineCrystalPillarBlock.tierOf(middle) == 0, "三段的 tierOf 都是 0");
        helper.assertTrue(Math.abs(bottom.getEnchantPowerBonus(level, helper.absolutePos(base)) - 7.5F) < EPS, "底座附魔加成应为 7.5");
        helper.assertTrue(middle.getEnchantPowerBonus(level, helper.absolutePos(base.above())) == 0F, "中段没有附魔加成");

        MithrilineCrystalPillarBlockEntity be = (MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(base);
        helper.assertTrue(be.getType() == ECMithriline.MITHRILINE_CRYSTAL_PILLAR_BE.get(), "方块实体类型应还是米斯莱柱那个");
        helper.assertTrue(be.getTier() == 0 && be.getESPEStorage().getTier() == 0, "方块实体和 ESPE 存储都应是 0 档");
        IESPEHandler handler = be.getCapability(ECCapabilities.ESPE, null).orElse(null);
        helper.assertTrue(handler != null && handler.getTier() == 0, "capability 报的档位应是 0");

        be.getESPEStorage().setESPE(1234.5D);
        CompoundTag saved = be.saveWithoutMetadata();
        helper.assertTrue(saved.contains(MithrilineCrystalPillarBlockEntity.TAG_ESPE, Tag.TAG_COMPOUND)
                        && saved.getAllKeys().stream().noneMatch(key -> key.toLowerCase(Locale.ROOT).contains("tier")),
                "存档里应只有 espe_storage、没有档位，实际 " + saved);
        MithrilineCrystalPillarBlockEntity copy = new MithrilineCrystalPillarBlockEntity(helper.absolutePos(base), bottom);
        copy.load(saved);
        helper.assertTrue(Math.abs(copy.getESPEStorage().getESPE() - 1234.5D) < EPS && copy.getTier() == 0, "读回来 ESPE 和档位不变");

        helper.assertTrue(bottom.is(MithrilineCrystalPillarBlock.ESPE_PILLARS),
                "米斯莱柱应在 essentialcraft5:espe_pillars 标签里（先跑 runData 生成标签）");
        helper.assertTrue(MithrilineCrystalPillarBlock.isPillarBase(bottom, 0), "底座算 0 档以上的柱子");
        helper.assertFalse(MithrilineCrystalPillarBlock.isPillarBase(middle, 0), "中段不算柱子的底座");
        helper.assertFalse(MithrilineCrystalPillarBlock.isPillarBase(bottom, 1), "米斯莱柱不算 1 档以上");
        StructurePattern.Rule any = MithrilineCrystalPillarBlock.pillarRule("structure.essentialcraft5.test");
        StructurePattern.Rule pale = MithrilineCrystalPillarBlock.pillarRule(1,
                () -> ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get().defaultBlockState(), "structure.essentialcraft5.test");
        helper.assertTrue(any.test(level, helper.absolutePos(base), bottom), "任意档规则应认米斯莱柱底座");
        helper.assertFalse(any.test(level, helper.absolutePos(base.above()), middle), "任意档规则不认中段");
        helper.assertFalse(pale.test(level, helper.absolutePos(base), bottom), "1 档规则不认米斯莱柱");
        helper.succeed();
    }

    /** ECPentacle 注册了淡青水晶柱时：1 档、附魔 15、ESPE tier 1、自己的方块实体类型（没注册就只验米斯莱） */
    @GameTest(template = EMPTY)
    public static void palePillarTierOneIfRegistered(GameTestHelper helper) {
        Block pale = block("pale_crystal_pillar");
        if (!(pale instanceof MithrilineCrystalPillarBlock pillar)) {
            helper.succeed();
            return;
        }
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, pale);
        ServerLevel level = helper.getLevel();
        BlockState bottom = level.getBlockState(helper.absolutePos(base));
        helper.assertTrue(pillar.tier() == MithrilineCrystalPillarBlock.Tier.PALE, "淡青柱应是 1 档");
        helper.assertTrue(Math.abs(bottom.getEnchantPowerBonus(level, helper.absolutePos(base)) - 15F) < EPS, "淡青柱附魔加成应为 15");
        MithrilineCrystalPillarBlockEntity be = (MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(base);
        helper.assertTrue(be.getType() == pillar.blockEntityType() && be.getType() != ECMithriline.MITHRILINE_CRYSTAL_PILLAR_BE.get(),
                "淡青柱应用自己注册的方块实体类型");
        helper.assertTrue(be.getESPEStorage().getTier() == 1, "淡青柱的 ESPE 存储应是 1 档");
        helper.assertTrue(MithrilineCrystalPillarBlock.isPillarBase(bottom, 1), "淡青柱底座算 1 档以上的柱子（要在 espe_pillars 标签里）");
        helper.succeed();
    }

    // ================================================================ G9 玩家腐化数据

    /** 效果表：25 行，id「类别_序号」，成本照 CERegistry，三类 5 / 6 / 14 种，五芒星档位 0 / 1 / 2 */
    @GameTest(template = EMPTY)
    public static void corruptionTableMatchesOriginal(GameTestHelper helper) {
        int[] costs = {6000, 6000, 6000, 6000, 6000, 36000, 36000, 14400, 18000, 8000, 8000,
                20000, 72000, 20000, 4000, 8000, 2000, 36000, 14400, 72000, 72000, 36000, 36000, 8000, 4000};
        List<CorruptionEffects.Effect> all = CorruptionEffects.ALL;
        helper.assertTrue(all.size() == 25, "应有 25 种，实际 " + all.size());
        int[] perCategory = new int[3];
        for (int i = 0; i < all.size(); i++) {
            CorruptionEffects.Effect effect = all.get(i);
            CorruptionEffects.Category category = i < 5 ? CorruptionEffects.Category.BODY
                    : i < 11 ? CorruptionEffects.Category.MIND : CorruptionEffects.Category.MATRIX;
            helper.assertTrue(effect.index() == i && effect.category() == category && effect.cost() == costs[i],
                    "#" + i + " 应是 " + category + "、成本 " + costs[i] + "，实际 " + effect);
            helper.assertTrue(effect.id().equals(category.key() + "_" + i), "#" + i + " 的 id 应为 " + category.key() + "_" + i);
            helper.assertTrue(CorruptionEffects.byId(effect.id()) == effect && CorruptionEffects.byIndex(i) == effect, "按 id / 序号应查得到");
            helper.assertTrue(effect.chance() > 0F && effect.chance() <= 1F, "#" + i + " 的概率应在 (0, 1]");
            helper.assertTrue(effect.icon().getPath().startsWith("textures/special/corruptioneffects/"), "图标路径");
            perCategory[category.ordinal()]++;
        }
        helper.assertTrue(perCategory[0] == 5 && perCategory[1] == 6 && perCategory[2] == 14, "三类应为 5 / 6 / 14 种");
        helper.assertTrue(CorruptionEffects.Category.BODY.minPentacleTier() == 0 && CorruptionEffects.Category.MIND.minPentacleTier() == 1
                && CorruptionEffects.Category.MATRIX.minPentacleTier() == 2, "五芒星档位 0 / 1 / 2");
        helper.assertTrue(CorruptionEffects.byId("nope") == null && CorruptionEffects.byIndex(25) == null, "查不到的返回 null");
        helper.assertTrue(CorruptionEffects.byId("matrix_13").chance() == 0.0006F && CorruptionEffects.byId("body_4").chance() == 1F,
                "#13 原作 0.06%、#4 下落中每秒必定");
        helper.succeed();
    }

    /**
     * 抽取：概率 min(1, (损伤 + 1) / 36000)；候选 = 门槛 ≤ 损伤、还没有、开着的，身体类 ×3（门槛 = 成本 × 倍率，这里压成 1 按原作核对，
     * 默认倍率 3 的门槛见 CorruptionTests.corruptionDrawThresholdMultiplier）；
     * 矩阵类默认开（E2，第七批起）；用测试覆盖值关掉矩阵类、单独关掉一种都生效（测完还原）
     */
    @GameTest(template = EMPTY)
    public static void corruptionDrawRules(GameTestHelper helper) {
        CorruptionEffects.setTestStrength(1D, 1D, 1D);
        try {
            drawRules(helper);
        } finally {
            CorruptionEffects.setTestStrength(null, null, null);
        }
    }

    private static void drawRules(GameTestHelper helper) {
        helper.assertTrue(Math.abs(CorruptionEffects.drawChance(0) - 1D / 36000D) < 1.0E-12D, "损伤 0 → 1/36000");
        helper.assertTrue(CorruptionEffects.drawChance(35999) == 1D && CorruptionEffects.drawChance(100000) == 1D, "损伤 ≥ 35999 → 必定");
        RandomSource random = RandomSource.create(42L);
        helper.assertTrue(CorruptionEffects.rollDraw(36000, random) && !CorruptionEffects.rollDraw(-1, random), "rollDraw 按原作 nextInt(36000) <= 损伤");

        helper.assertTrue(CorruptionEffects.matrixEffectsEnabled(), "矩阵类默认开（E2，第七批起；海纳尔之门默认开着）");
        // 默认开着时，5999 只有矩阵类候选：matrix_14（4000）、matrix_16（2000）、matrix_24（4000）
        List<CorruptionEffects.Effect> cheap = CorruptionEffects.drawCandidates(5999, List.of());
        helper.assertTrue(cheap.size() == 3 && cheap.stream().allMatch(e -> e.category() == CorruptionEffects.Category.MATRIX),
                "矩阵类开着时 5999 只有三种矩阵类，实际 " + cheap);

        // 用内存覆盖值，不改真配置（改真配置会被 Forge 的文件监视器在后台重读冲掉，见 CorruptionEffects.setTestOverrides）
        try {
            CorruptionEffects.setTestOverrides(false, List.of());
            helper.assertTrue(CorruptionEffects.drawCandidates(5999, List.of()).isEmpty(), "矩阵类关着时损伤不到 6000 没有候选");
            List<CorruptionEffects.Effect> body = CorruptionEffects.drawCandidates(6000, List.of());
            helper.assertTrue(body.size() == 15 && body.stream().allMatch(e -> e.category() == CorruptionEffects.Category.BODY),
                    "6000 时只有 5 种身体类、各 3 份，实际 " + body.size());
            List<CorruptionEffects.Effect> withMind = CorruptionEffects.drawCandidates(8000, List.of("body_0"));
            helper.assertTrue(withMind.size() == 4 * 3 + 2, "8000 且已有 body_0 时：4 种身体 ×3 + mind_9、mind_10，实际 " + withMind.size());
            helper.assertTrue(CorruptionEffects.draw(8000, List.of("body_0"), random) != null, "有候选时抽得到");
            helper.assertTrue(CorruptionEffects.draw(100, List.of(), random) == null, "没有候选时返回 null");
            helper.assertTrue(CorruptionEffects.drawCandidates(100000, List.of()).stream()
                    .noneMatch(e -> e.category() == CorruptionEffects.Category.MATRIX), "矩阵类关着时一个都不抽");

            CorruptionEffects.setTestOverrides(true, List.of());
            List<CorruptionEffects.Effect> matrix = CorruptionEffects.drawCandidates(2000, List.of());
            helper.assertTrue(matrix.size() == 1 && matrix.get(0).id().equals("matrix_16"), "打开后 2000 时只有 matrix_16，实际 " + matrix);
            CorruptionEffects.setTestOverrides(false, List.of("body_0"));
            helper.assertFalse(CorruptionEffects.byId("body_0").isEnabled(), "disabled_effects 里的应关着");
            helper.assertTrue(CorruptionEffects.drawCandidates(6000, List.of()).size() == 12, "关掉 body_0 后 6000 时剩 4 种身体 ×3");
        } finally {
            CorruptionEffects.setTestOverrides(null, null);
        }
        helper.succeed();
    }

    /** 增删查、markDirty、NBT 往返（顺序、去重、空 id、认不出的 id 原样保留），版本号写进存档 */
    @GameTest(template = EMPTY)
    public static void corruptionListRoundTrip(GameTestHelper helper) {
        IPlayerData data = data(helper, helper.makeMockPlayer());
        data.clearDirty();
        helper.assertTrue(data.getCorruptionEffects().isEmpty(), "新玩家没有腐化效果");
        helper.assertTrue(data.addCorruptionEffect("mind_5") && data.addCorruptionEffect("body_0"), "应能加");
        helper.assertTrue(data.isDirty(), "加了应标脏");
        helper.assertFalse(data.addCorruptionEffect("mind_5"), "同一种不重复加");
        helper.assertFalse(data.addCorruptionEffect("") || data.addCorruptionEffect(null), "空 id 不加");
        helper.assertTrue(data.getCorruptionEffects().equals(List.of("mind_5", "body_0")), "按得到的先后排，实际 " + data.getCorruptionEffects());
        boolean readOnly;
        try {
            data.getCorruptionEffects().add("x");
            readOnly = false;
        } catch (UnsupportedOperationException e) {
            readOnly = true;
        }
        helper.assertTrue(readOnly, "getCorruptionEffects 应是只读视图");

        CompoundTag tag = data.serializeNBT();
        helper.assertTrue(tag.getInt("data_version") == PlayerData.DATA_VERSION, "存档应写版本号 " + PlayerData.DATA_VERSION);
        ListTag list = tag.getList("corruption_effects", Tag.TAG_STRING);
        list.add(StringTag.valueOf("future_effect"));
        list.add(StringTag.valueOf("mind_5"));
        list.add(StringTag.valueOf(""));
        PlayerData copy = new PlayerData();
        copy.deserializeNBT(tag);
        helper.assertTrue(copy.getCorruptionEffects().equals(List.of("mind_5", "body_0", "future_effect")),
                "读回来顺序不变、去掉重复和空的、认不出的保留，实际 " + copy.getCorruptionEffects());
        helper.assertTrue(copy.hasCorruptionEffect("body_0") && !copy.hasCorruptionEffect("body_1"), "has");
        helper.assertTrue(copy.removeCorruptionEffect("body_0") && !copy.removeCorruptionEffect("body_0"), "remove 一次成功、第二次失败");
        helper.assertTrue(copy.clearCorruptionEffects() == 2 && copy.getCorruptionEffects().isEmpty(), "clear 返回清掉的个数");
        helper.assertTrue(copy.clearCorruptionEffects() == 0, "空的再 clear 返回 0");
        helper.succeed();
    }

    /** D14：没有版本号的旧存档读进来时矩阵损伤清零（记下清了多少），有版本号的不动；复制（死亡重生）不会再清 */
    @GameTest(template = EMPTY)
    public static void corruptionMigrationRunsOnce(GameTestHelper helper) {
        CompoundTag old = new CompoundTag();
        old.putInt("matrix_damage", 54321);
        old.putInt("ubmru", 77);
        old.putInt("wind_points", 12);
        PlayerData migrated = new PlayerData();
        migrated.deserializeNBT(old);
        helper.assertTrue(migrated.getMatrixDamage() == 0, "旧存档的矩阵损伤应清零，实际 " + migrated.getMatrixDamage());
        helper.assertTrue(migrated.getUBMRU() == 77 && migrated.getWindPoints() == 12, "别的字段不动");
        helper.assertTrue(migrated.isDirty(), "清了应标脏（登录时同步）");
        helper.assertTrue(migrated.takeMigratedMatrixDamage() == 54321 && migrated.takeMigratedMatrixDamage() == 0, "记下清了多少，取一次归零");

        migrated.setMatrixDamage(300);
        PlayerData again = new PlayerData();
        again.deserializeNBT(migrated.serializeNBT());
        helper.assertTrue(again.getMatrixDamage() == 300 && again.takeMigratedMatrixDamage() == 0, "新存档（有版本号）不再清");

        // 死亡复制：走 PlayerDataEvents.clone，效果列表和损伤都跟过去
        Player original = helper.makeMockPlayer();
        Player respawned = helper.makeMockPlayer();
        IPlayerData from = data(helper, original);
        from.setMatrixDamage(4000);
        from.addCorruptionEffect("body_3");
        PlayerDataEvents.clone(new PlayerEvent.Clone(respawned, original, true));
        IPlayerData to = data(helper, respawned);
        helper.assertTrue(to.getMatrixDamage() == 4000 && to.getCorruptionEffects().equals(List.of("body_3")),
                "死亡复制应带上损伤和效果，实际 " + to.getMatrixDamage() + " / " + to.getCorruptionEffects());

        // 真实的移除路径（第六批 6a 集成补）：换维度 = remove(CHANGED_DIMENSION) 再 revive()，数据还在、同一份；
        // 死亡重生 = 旧玩家 remove(DISCARDED)（Forge 作废 capability）之后才 Clone，也要复制得过去
        Player traveller = helper.makeMockPlayer();
        IPlayerData before = data(helper, traveller);
        before.setMatrixDamage(1234);
        traveller.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        helper.assertTrue(PlayerDataEvents.get(traveller) == null, "移除后（revive 之前）拿不到玩家数据");
        traveller.revive();
        IPlayerData after = PlayerDataEvents.get(traveller);
        helper.assertTrue(after != null && after.getMatrixDamage() == 1234,
                "换维度（remove + revive）后应还拿得到同一份玩家数据，实际 " + (after == null ? "null" : after.getMatrixDamage()));
        Player dead = helper.makeMockPlayer();
        data(helper, dead).setMatrixDamage(2345);
        dead.remove(Entity.RemovalReason.DISCARDED);
        Player reborn = helper.makeMockPlayer();
        PlayerDataEvents.clone(new PlayerEvent.Clone(reborn, dead, true));
        helper.assertTrue(data(helper, reborn).getMatrixDamage() == 2345,
                "旧玩家已被移除（capability 作废）时死亡复制也要带上数据，实际 " + data(helper, reborn).getMatrixDamage());
        helper.assertTrue(PlayerDataEvents.get(dead) == null, "复制完旧玩家的 capability 应重新作废");
        helper.succeed();
    }

    /** /ec5 player … corruption：add / list / remove / clear，未知 id、重复加、没有可删的都报错；多个目标返回人数 */
    @GameTest(template = EMPTY)
    public static void corruptionCommands(GameTestHelper helper) throws CommandSyntaxException {
        CommandSourceStack source = source(helper);
        Player player = helper.makeMockPlayer();
        Player other = helper.makeMockPlayer();
        IPlayerData data = data(helper, player);
        IPlayerData otherData = data(helper, other);
        List<Player> one = List.of(player);
        List<Player> both = List.of(player, other);

        helper.assertTrue(CorruptionCommands.list(source, one) == 0, "没有效果时 list 返回 0");
        helper.assertTrue(CorruptionCommands.add(source, one, "matrix_13") == 1 && data.hasCorruptionEffect("matrix_13"), "add 按 id 加（关着的矩阵类也能加）");
        helper.assertTrue(CorruptionCommands.add(source, both, "matrix_13") == 1 && otherData.hasCorruptionEffect("matrix_13"),
                "已有的人不算，只改了 1 个");
        helper.assertTrue(throwsSyntax(() -> CorruptionCommands.add(source, one, "matrix_13")), "都已经有了应报错");
        helper.assertTrue(throwsSyntax(() -> CorruptionCommands.add(source, one, "matrix_99")), "未知 id 应报错");
        CorruptionCommands.add(source, one, "body_1");
        helper.assertTrue(CorruptionCommands.list(source, one) == 2, "一个目标时 list 返回效果个数");
        helper.assertTrue(CorruptionCommands.list(source, both) == 2, "多个目标时 list 返回人数");
        helper.assertTrue(CorruptionCommands.remove(source, both, "body_1") == 1 && !data.hasCorruptionEffect("body_1"), "remove");
        helper.assertTrue(throwsSyntax(() -> CorruptionCommands.remove(source, both, "body_1")), "都没有应报错");
        helper.assertTrue(CorruptionCommands.clear(source, both) == 2 && data.getCorruptionEffects().isEmpty()
                && otherData.getCorruptionEffects().isEmpty(), "clear 两个人都清掉");
        helper.assertTrue(throwsSyntax(() -> CorruptionCommands.clear(source, both)), "没有可清的应报错");
        data.addCorruptionEffect("gone_effect");
        helper.assertTrue(CorruptionCommands.remove(source, one, "gone_effect") == 1, "表里没有的旧 id 也能删");
        helper.succeed();
    }

    @FunctionalInterface
    private interface CommandCall {
        int run() throws CommandSyntaxException;
    }

    private static boolean throwsSyntax(CommandCall call) {
        try {
            call.run();
            return false;
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    // ================================================================ G10 聚集体稳定

    /** 2 万 MRU 的聚集体：平时一轮给旁边的生存玩家加矩阵损伤；稳定期间不加；期限存盘、吞并时取较晚的 */
    @GameTest(template = EMPTY)
    public static void stabilizedPresenceSparesPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 2.5D, 2.5D));
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, at.x, at.y, at.z, 20000, 1F, false, true);
        Player player = helper.makeMockSurvivalPlayer();
        player.moveTo(at.x + 2D, at.y - 1D, at.z, 0F, 0F);
        IPlayerData data = data(helper, player);
        int expected = MRUPresenceEntity.matrixDamage(2, 1F);

        presence.runCycle(List.of(player));
        helper.assertTrue(data.getMatrixDamage() == expected, "没稳定时一轮应加 " + expected + "，实际 " + data.getMatrixDamage());

        data.setMatrixDamage(0);
        helper.assertFalse(presence.isStabilized(), "还没稳定");
        presence.stabilize(40);
        helper.assertTrue(presence.isStabilized() && presence.getStabilizedUntil() == level.getGameTime() + 40, "稳定 40 tick");
        presence.stabilize(10);
        helper.assertTrue(presence.getStabilizedUntil() == level.getGameTime() + 40, "短的不会缩短已有的期限");
        presence.runCycle(List.of(player));
        helper.assertTrue(data.getMatrixDamage() == 0, "稳定期间不应加矩阵损伤，实际 " + data.getMatrixDamage());

        CompoundTag saved = new CompoundTag();
        presence.saveWithoutId(saved);
        MRUPresenceEntity loaded = MRUPresenceEntity.create(level, at.x, at.y, at.z, 0, 1F, false, true);
        loaded.load(saved);
        helper.assertTrue(loaded.getStabilizedUntil() == presence.getStabilizedUntil() && loaded.getMRU() == 20000, "期限和 MRU 应存盘");
        CompoundTag plain = new CompoundTag();
        MRUPresenceEntity.create(level, at.x, at.y, at.z, 100, 1F, false, true).saveWithoutId(plain);
        helper.assertFalse(plain.contains("stabilized_until"), "没稳定过的不写这个键（旧存档格式不变）");

        MRUPresenceEntity big = MRUPresenceEntity.create(level, at.x, at.y, at.z, 5000, 1F, false, true);
        big.absorb(presence);
        helper.assertTrue(big.getStabilizedUntil() == loaded.getStabilizedUntil() && big.isStabilized(), "吞并时继承较晚的期限");
        helper.succeed();
    }

    /**
     * 富集仓控制器：stabilizeContents 只稳定外壳范围里的聚集体；外壳上没有稳定器时 tick 不去稳定；
     * ECPentacle 注册了 ec_balancer（实现 EnrichmentStabilizer）时，放进外壳后控制器数到它、仓里的聚集体被续上稳定期
     */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void controllerStabilizesChamber(GameTestHelper helper) {
        shell(helper, ECMaterials.FORTIFIED_STONE.get());
        helper.setBlock(CONTROLLER, ECEnrichment.EC_CONTROLLER.get());
        EnrichmentControllerBlockEntity controller = (EnrichmentControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "5×3×5 外壳应成形");
        helper.assertTrue(controller.stabilizers() == 0, "没有稳定器");

        MRUPresenceEntity inside = presence(helper, new Vec3(5.5D, 2.5D, 5.5D), 500);
        MRUPresenceEntity outside = presence(helper, new Vec3(1.5D, 2.5D, 1.5D), 500);
        MRUPresenceEntity later = presence(helper, new Vec3(4.5D, 2.5D, 6.5D), 500);
        helper.assertTrue(controller.stabilizeContents() == 2, "外壳里有 2 个聚集体");
        helper.assertTrue(inside.isStabilized() && later.isStabilized() && !outside.isStabilized(), "只稳定外壳里的");

        Block balancer = block("ec_balancer");
        boolean hasBalancer = balancer instanceof EnrichmentStabilizer;
        helper.startSequence()
                .thenExecuteAfter(EnrichmentControllerBlockEntity.STABILIZE_TICKS + 2, () -> {
                    // 手动续的 40 tick 过去了；外壳上没有稳定器，控制器的 tick 不会再续
                    helper.assertFalse(inside.isStabilized(), "没有稳定器时控制器不续稳定期");
                    if (hasBalancer) {
                        helper.setBlock(WEST_WALL, balancer);
                        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "稳定器在外壳标签里，换上后仍成形");
                        helper.assertTrue(controller.stabilizers() == 1, "应数到 1 个稳定器，实际 " + controller.stabilizers());
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(!hasBalancer || inside.isStabilized(), "等控制器给仓里的聚集体续稳定期"))
                .thenExecute(() -> {
                    helper.assertFalse(outside.isStabilized(), "外面的不稳定");
                    inside.discard();
                    outside.discard();
                    later.discard();
                })
                .thenSucceed();
    }

    // ================================================================ G11 风之好感

    /** 系数 0.1、原作的 float 取整（10 → 1、25 → 2、100 → 10、1000 → 100）、系数 ≤ 0 不倒扣、加好感夹 int 上限、FakePlayer 不加 */
    @GameTest(template = EMPTY)
    public static void windAffinityCoefficient(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerData data = data(helper, player);
        helper.assertTrue(WindAffinity.coefficient(player) == WindAffinity.baseCoefficient(), "什么都没穿时系数是基础值");
        helper.assertTrue(WindAffinity.baseCoefficient() == 0.1F, "默认基础系数 0.1");
        helper.assertTrue(WindAffinity.scaled(10, 0.1F) == 1 && WindAffinity.scaled(25, 0.1F) == 2
                && WindAffinity.scaled(100, 0.1F) == 10 && WindAffinity.scaled(1000, 0.1F) == 100, "原作表：10→1、25→2、100→10、1000→100");
        helper.assertTrue(WindAffinity.scaled(100, 0F) == 0 && WindAffinity.scaled(100, -0.1F) == 0, "系数 ≤ 0 时实得 0");
        // 全套风法师（原作 0.1F + 0.23F × 4 按 float 累加）
        float full = 0.1F;
        for (int i = 0; i < 4; i++) {
            full += 0.23F;
        }
        helper.assertTrue(WindAffinity.scaled(1000, full) == (int) (1000 * full), "按 float 乘完截断");

        data.setWindPoints(5);
        helper.assertTrue(WindAffinity.add(player, 100) == 10 && data.getWindPoints() == 15, "风之药水 100 → +10，实际 " + data.getWindPoints());
        helper.assertTrue(WindAffinity.points(player) == 15, "points 读玩家数据");
        MobEffectInstance touch = ForgeRegistries.MOB_EFFECTS.containsKey(WindAffinity.WIND_TOUCH)
                ? player.getEffect(ForgeRegistries.MOB_EFFECTS.getValue(WindAffinity.WIND_TOUCH)) : null;
        helper.assertTrue(!ForgeRegistries.MOB_EFFECTS.containsKey(WindAffinity.WIND_TOUCH)
                || touch != null && touch.getDuration() == WindAffinity.WIND_TOUCH_TICKS, "注册了风之触时应给 1000 tick");
        data.setWindPoints(Integer.MAX_VALUE - 5);
        WindAffinity.add(player, 1000);
        helper.assertTrue(data.getWindPoints() == Integer.MAX_VALUE, "夹到 int 上限，实际 " + data.getWindPoints());

        Player fake = FakePlayerFactory.getMinecraft(helper.getLevel());
        helper.assertTrue(WindAffinity.add(fake, 1000) == 0 && WindAffinity.points(fake) == 0, "FakePlayer 不加好感");
        for (var tag : List.of(WindAffinity.WIND_RESISTANT, WindAffinity.WIND_BOON, WindAffinity.WIND_CATCHER)) {
            helper.assertFalse(WindAffinity.isWearing(player, tag), "什么都没戴时不算戴着 " + tag.location());
        }

        // ECWindMage 注册了风法师兜帽时：戴上系数 +0.23
        Item hood = item("wind_mage_hood");
        if (hood instanceof IWindAffinityItem) {
            player.getInventory().armor.set(3, new ItemStack(hood));
            helper.assertTrue(Math.abs(WindAffinity.coefficient(player) - 0.33F) < 1.0E-5F, "戴兜帽系数应为 0.33，实际 " + WindAffinity.coefficient(player));
        }
        helper.succeed();
    }

    // ================================================================ G12 已移植类的饰品钩子

    /**
     * 没戴饰品时：魔力水不加 UBMRU、MRU 腐化照常掉血（放大等级 16 必中：5 × 易伤 1 = 5 点）；
     * ECBaubles 注册了 α / γ 指环并加进标签时：戴 α 喝魔力水 +500，戴 γ 时 MRU 腐化改成回血
     */
    @GameTest(template = EMPTY)
    public static void baubleHooksInPortedClasses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        IPlayerData data = data(helper, player);
        data.setUBMRU(100);
        ItemStack water = new ItemStack(ECMaterials.MAGICAL_WATER.get(), 2);
        water.getItem().finishUsingItem(water, level, player);
        helper.assertTrue(data.getUBMRU() == 100, "没戴 α 时喝魔力水不加 UBMRU，实际 " + data.getUBMRU());
        helper.assertTrue(MagicalWaterItem.grantAlphaUBMRU(player) == 0, "没戴 α 时 grantAlphaUBMRU 返回 0");

        helper.assertFalse(MRUCorruption.healsInstead(player), "没戴 γ 时不回血");
        player.setHealth(20F);
        MRUCorruption.pulse(player, 16);
        helper.assertTrue(Math.abs(player.getHealth() - 15F) < 1.0E-4F, "MRU 腐化 16 级应掉 5 点，实际 " + player.getHealth());

        Item alpha = item("alpha_ring");
        if (alpha != null && new ItemStack(alpha).is(MagicalWaterItem.UBMRU_FROM_MAGIC_WATER)) {
            Player wearer = helper.makeMockSurvivalPlayer();
            IPlayerData wearerData = data(helper, wearer);
            helper.assertTrue(equipCurio(wearer, new ItemStack(alpha)), "应能戴上 α 指环");
            wearerData.setUBMRU(Integer.MAX_VALUE - 100);
            helper.assertTrue(MagicalWaterItem.grantAlphaUBMRU(wearer) == MagicalWaterItem.ALPHA_UBMRU
                    && wearerData.getUBMRU() == Integer.MAX_VALUE, "戴 α 喝魔力水 +500，夹到 int 上限");
        }
        Item gamma = item("gamma_ring");
        if (gamma != null && new ItemStack(gamma).is(MRUCorruption.HEALS_FROM_MRU_CORRUPTION)) {
            Player wearer = helper.makeMockSurvivalPlayer();
            helper.assertTrue(equipCurio(wearer, new ItemStack(gamma)), "应能戴上 γ 指环");
            helper.assertTrue(MRUCorruption.healsInstead(wearer), "戴 γ 时回血");
            wearer.setHealth(10F);
            MRUCorruption.pulse(wearer, 16);
            helper.assertTrue(wearer.getHealth() > 10F, "戴 γ 时 MRU 腐化应回血，实际 " + wearer.getHealth());
        }
        helper.succeed();
    }

    /** 标签常量和 tools/tags/framework3c.json 里建的名字一致（名字错了模块往标签里加成员也不会生效） */
    @GameTest(template = EMPTY)
    public static void frameworkTagNames(GameTestHelper helper) {
        Map<String, ResourceLocation> expected = Map.of(
                "espe_pillars", MithrilineCrystalPillarBlock.ESPE_PILLARS.location(),
                "wind_resistant", WindAffinity.WIND_RESISTANT.location(),
                "wind_boon", WindAffinity.WIND_BOON.location(),
                "wind_catcher", WindAffinity.WIND_CATCHER.location(),
                "ubmru_from_magic_water", MagicalWaterItem.UBMRU_FROM_MAGIC_WATER.location(),
                "heals_from_mru_corruption", MRUCorruption.HEALS_FROM_MRU_CORRUPTION.location());
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, ResourceLocation> entry : expected.entrySet()) {
            if (!entry.getValue().equals(EssentialCraft5.id(entry.getKey()))) {
                wrong.add(entry.getKey() + " → " + entry.getValue());
            }
        }
        helper.assertTrue(wrong.isEmpty(), "标签名应为 essentialcraft5:<名字>，不对的：" + wrong);
        helper.assertTrue(Set.copyOf(CorruptionEffects.ids()).size() == 25, "25 个 id 不重复");
        helper.succeed();
    }
}
