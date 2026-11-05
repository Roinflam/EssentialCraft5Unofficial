package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.DemonicPentacleBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.demon.DemonTrades;
import com.flameforge.essentialcraft5.demon.DemonWant;
import com.flameforge.essentialcraft5.entity.DemonEntity;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.menu.DemonTradeMenu;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECCrystalPillars;
import com.flameforge.essentialcraft5.registry.ECDemon;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructureResult;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 恶魔模块（ECDemon，batch7.md §4.1）的 GameTest：
 * <ul>
 *   <li>结构：格数照原作；每类方块缺一块都判不过；虚空以上的柱位放淡青柱判不过、放虚空柱判过。</li>
 *   <li>召唤：结构不完整、头顶被挡、已有恶魔、交易池为空、ESPE 不够、生成被别的模组拦下都不出恶魔，柱子一点不少；假玩家右键不召唤；
 *       成功时 16 根合计正好少 666、按原作 coords 顺序抽；需求、召唤者、五芒星位置记下且读档不变，读档时没注册的实体读成 null、数量夹回上限。</li>
 *   <li>守阵：成形时留着；拆五芒星正下方那块 ≤ 41 刻离开、拆外圈一块 ≤ 81 刻离开；不在阵上的 40 刻内离开；五芒星还没判定过时不算坏。</li>
 *   <li>伤害：别的玩家打、箭、岩浆、爆炸、召唤者的横扫都打不掉；召唤者、创造模式玩家打一下离开；{@code genericKill} 能杀；拴不住、推不动。</li>
 *   <li>清怪：目标判定逐个过；击杀不掉落（怪捡的装备照掉）、爆炸只伤同类怪、不推旁人、不破坏方块；间隔与范围 0；
 *       只清召唤者能动的格子里的怪（第十批 A6：在线时探测、离线时只认记下的格子，没有召唤者的不清；要清怪的测试把召唤者设成在线覆盖）。</li>
 *   <li>交易池：默认 45 条；解析的 remove / weight / 夹数量 / 跳过没注册的 / 字段类型写错只跳那一条；按权重抽，权重合计超过 int 也抽得出。</li>
 *   <li>交易：只收要的数量、成交给报酬（背包满了掉脚下）；匹配规则；资格（D2 = A）；界面失效退回，恶魔离开后格子不再收东西。</li>
 *   <li>五芒星配方里是虚空核心、挡液体；刷怪蛋；环境音与交易声的间隔。</li>
 * </ul>
 * 模板 empty_large（11×8×11 空气）：五芒星放在 (5, 2, 5)，虚空石 / 淡青护板在 y=1，柱子在 y=2~5。
 * <b>测试里生成的恶魔都先把清怪范围设成 0</b>（{@link DemonEntity#setTestClearOverrides}）：同一批测试的结构挨得很近，默认 12 格会打到隔壁测试里的怪。
 * 要被打的僵尸不用 {@code helper.spawn}（它会 {@code setPersistenceRequired()}，恶魔不打持久生物），用 {@link #zombie}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class DemonTests {

    private static final String LARGE = "empty_large";
    private static final String EMPTY = "empty";
    private static final BlockPos PENTACLE = new BlockPos(5, 2, 5);
    private static final double EPS = 1.0E-6D;
    /** 原作 53 条删 8 条（removed_redesign §3.6），第十三批加炼狱小鬼灵魂 ×2 一条（batch13.md §5.2） */
    private static final int DEFAULT_POOL_SIZE = 47;
    private static final ResourceLocation WOOL_TABLE = ResourceLocation.withDefaultNamespace("entities/sheep/white");

    private DemonTests() {
    }

    // ================================================================ 工具

    private static BlockPos at(BlockPos offset) {
        return PENTACLE.offset(offset);
    }

    /** 搭整个结构：虚空石、淡青护板、荧石、米斯莱护板，16 根柱子（任意档放米斯莱柱、淡青以上放淡青柱、虚空以上放虚空柱），最后放五芒星 */
    private static DemonicPentacleBlockEntity build(GameTestHelper helper) {
        place(helper, ECDemon.VOID_STONE_OFFSETS, ECVoid.VOID_STONE.get());
        place(helper, ECDemon.PALE_PLATING_OFFSETS, ECAlloys.PALE_PLATING.get());
        place(helper, ECDemon.GLOWSTONE_OFFSETS, Blocks.GLOWSTONE);
        place(helper, ECDemon.MITHRILINE_PLATING_OFFSETS, ECMithriline.MITHRILINE_PLATING.get());
        for (int i = 0; i < ECDemon.PILLAR_OFFSETS.size(); i++) {
            helper.setBlock(at(ECDemon.PILLAR_OFFSETS.get(i)), pillarFor(i));
        }
        helper.setBlock(PENTACLE, ECDemon.DEMONIC_PENTACLE.get());
        return (DemonicPentacleBlockEntity) helper.getBlockEntity(PENTACLE);
    }

    private static Block pillarFor(int index) {
        return index < 4 ? ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get()
                : index < 12 ? ECPentacle.PALE_CRYSTAL_PILLAR.get() : ECCrystalPillars.VOID_CRYSTAL_PILLAR.get();
    }

    private static void place(GameTestHelper helper, List<BlockPos> offsets, Block block) {
        for (BlockPos offset : offsets) {
            helper.setBlock(at(offset), block);
        }
    }

    private static MithrilineCrystalPillarBlockEntity pillar(GameTestHelper helper, int index) {
        return (MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(at(ECDemon.PILLAR_OFFSETS.get(index)));
    }

    private static void fill(GameTestHelper helper, double each) {
        for (int i = 0; i < ECDemon.PILLAR_OFFSETS.size(); i++) {
            pillar(helper, i).getESPEStorage().setESPE(each);
        }
    }

    private static double total(GameTestHelper helper) {
        double sum = 0D;
        for (int i = 0; i < ECDemon.PILLAR_OFFSETS.size(); i++) {
            sum += pillar(helper, i).getESPEStorage().getESPE();
        }
        return sum;
    }

    private static StructureResult match(GameTestHelper helper) {
        return ECDemon.STRUCTURE.match(helper.getLevel(), helper.absolutePos(PENTACLE), Direction.NORTH).result();
    }

    private static List<DemonEntity> demonsOnPentacle(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(PENTACLE);
        return helper.getLevel().getEntitiesOfClass(DemonEntity.class, new AABB(abs).inflate(2D, 3D, 2D));
    }

    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        Vec3 abs = helper.absoluteVec(relativeFeet);
        player.moveTo(abs.x, abs.y, abs.z, 0F, 0F);
        return player;
    }

    /** 一只放进世界的恶魔（清怪关掉），站在 relativeFeet；summoner 为 null 时等于刷怪蛋出来的 */
    private static DemonEntity demon(GameTestHelper helper, Vec3 relativeFeet, @Nullable UUID summoner,
                                     @Nullable DemonWant want) {
        ServerLevel level = helper.getLevel();
        DemonEntity demon = ECDemon.DEMON.get().create(level);
        if (demon == null) {
            throw new IllegalStateException("建不出恶魔");
        }
        Vec3 abs = helper.absoluteVec(relativeFeet);
        demon.moveTo(abs.x, abs.y, abs.z, 0F, 0F);
        demon.setTestClearOverrides(0, null);
        demon.setSummoner(summoner);
        if (want != null) {
            demon.setWant(want);
        }
        level.addFreshEntity(demon);
        return demon;
    }

    /** 会被恶魔打的僵尸：EntityType.create + addFreshEntity（不持久）、关 AI、戴皮革帽白天不烧 */
    private static Zombie zombie(GameTestHelper helper, BlockPos relative) {
        return zombie(helper, relative, false);
    }

    /** 同上；woolLoot 为真时放进世界之前把战利品表换成白羊的（每次必掉 1 块白羊毛），用来确定地看「掉没掉」 */
    private static Zombie zombie(GameTestHelper helper, BlockPos relative, boolean woolLoot) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new IllegalStateException("建不出僵尸");
        }
        if (woolLoot) {
            CompoundTag tag = zombie.saveWithoutId(new CompoundTag());
            tag.putString("DeathLootTable", WOOL_TABLE.toString());
            zombie.load(tag);
        }
        Vec3 abs = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
        zombie.moveTo(abs.x, abs.y, abs.z, 0F, 0F);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static List<ItemEntity> items(GameTestHelper helper, Vec3 around, double radius, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(around, around).inflate(radius),
                e -> e.isAlive() && e.getItem().is(item));
    }

    private static DemonWant ironBlocks(int count) {
        return DemonWant.item(Items.IRON_BLOCK, count, false);
    }

    // ================================================================ 结构

    /** 格数照原作 tier0Checker：五芒星 1 + 虚空石 13 + 淡青护板 20 + 荧石 4 + 米斯莱护板 4 + 柱子 16（4 / 8 / 4） */
    @GameTest(template = LARGE)
    public static void demonStructureMatchesOriginal(GameTestHelper helper) {
        StructurePattern pattern = ECDemon.STRUCTURE;
        helper.assertTrue(pattern.requiredCount() == 58, "结构应有 58 格，实际 " + pattern.requiredCount());
        helper.assertTrue(pattern.offsets(ECDemon.VOID_STONE_RULE).size() == 13, "13 块虚空石");
        helper.assertTrue(pattern.offsets(ECDemon.PALE_PLATING_RULE).size() == 20, "20 块淡青护板");
        helper.assertTrue(pattern.offsets(ECDemon.GLOWSTONE_RULE).size() == 4, "4 块荧石");
        helper.assertTrue(pattern.offsets(ECDemon.MITHRILINE_PLATING_RULE).size() == 4, "4 块米斯莱护板");
        int[] counts = {4, 8, 4};
        for (int t = 0; t < 3; t++) {
            Set<BlockPos> inPattern = new HashSet<>(pattern.offsets(ECDemon.PILLAR_RULES.get(t)));
            Set<BlockPos> expected = new HashSet<>();
            for (int i = 0; i < ECDemon.PILLAR_OFFSETS.size(); i++) {
                if (ECDemon.pillarRule(i) == ECDemon.PILLAR_RULES.get(t)) {
                    expected.add(ECDemon.PILLAR_OFFSETS.get(i));
                }
            }
            helper.assertTrue(inPattern.size() == counts[t] && inPattern.equals(expected), "第 " + t + " 段柱位应和抽 ESPE 的位置一致");
        }
        // 原作 coords：前 4 根在米斯莱护板上、中间 8 根在淡青护板上、最后 4 根在荧石上
        for (int i = 0; i < ECDemon.PILLAR_OFFSETS.size(); i++) {
            BlockPos below = ECDemon.PILLAR_OFFSETS.get(i).below();
            StructurePattern.Rule support = pattern.rules().get(below);
            StructurePattern.Rule expected = i < 4 ? ECDemon.MITHRILINE_PLATING_RULE : i < 12 ? ECDemon.PALE_PLATING_RULE : ECDemon.GLOWSTONE_RULE;
            helper.assertTrue(support == expected, "第 " + i + " 根柱子脚下的方块不对");
        }
        helper.succeed();
    }

    /** 每类方块缺一块都判不过，补上恢复；虚空以上的柱位放淡青柱判不过、放回虚空柱判过 */
    @GameTest(template = LARGE)
    public static void demonStructureEachKindRequired(GameTestHelper helper) {
        build(helper);
        helper.assertTrue(match(helper) == StructureResult.MATCH, "整个结构应成形，实际 " + match(helper));
        Map<BlockPos, Block> probes = new HashMap<>();
        probes.put(ECDemon.VOID_STONE_OFFSETS.get(5), ECVoid.VOID_STONE.get());
        probes.put(ECDemon.PALE_PLATING_OFFSETS.get(7), ECAlloys.PALE_PLATING.get());
        probes.put(ECDemon.GLOWSTONE_OFFSETS.get(2), Blocks.GLOWSTONE);
        probes.put(ECDemon.MITHRILINE_PLATING_OFFSETS.get(1), ECMithriline.MITHRILINE_PLATING.get());
        for (Map.Entry<BlockPos, Block> probe : probes.entrySet()) {
            helper.setBlock(at(probe.getKey()), Blocks.STONE);
            helper.assertTrue(match(helper) == StructureResult.MISMATCH, "缺一块 " + probe.getValue() + " 应判不过");
            helper.setBlock(at(probe.getKey()), probe.getValue());
            helper.assertTrue(match(helper) == StructureResult.MATCH, "补上 " + probe.getValue() + " 应重新成形");
        }
        for (int index : new int[]{0, 6, 13}) {
            BlockPos pillar = at(ECDemon.PILLAR_OFFSETS.get(index));
            helper.setBlock(pillar, Blocks.AIR);
            helper.assertTrue(match(helper) == StructureResult.MISMATCH, "缺第 " + index + " 根柱子应判不过");
            helper.setBlock(pillar, pillarFor(index));
            helper.assertTrue(match(helper) == StructureResult.MATCH, "补上第 " + index + " 根柱子应重新成形");
        }
        BlockPos voidSlot = at(ECDemon.PILLAR_OFFSETS.get(12));
        helper.setBlock(voidSlot, Blocks.AIR);
        helper.setBlock(voidSlot, ECPentacle.PALE_CRYSTAL_PILLAR.get());
        helper.assertTrue(match(helper) == StructureResult.MISMATCH, "虚空以上的柱位放淡青柱应判不过");
        helper.setBlock(voidSlot, Blocks.AIR);
        helper.setBlock(voidSlot, ECCrystalPillars.VOID_CRYSTAL_PILLAR.get());
        helper.assertTrue(match(helper) == StructureResult.MATCH, "换回虚空柱应成形");
        helper.succeed();
    }

    // ================================================================ 召唤

    /** 结构不完整、头顶被挡、已有恶魔、交易池为空、ESPE 不够：都不出恶魔、柱子一点不少，提示写在快捷栏 */
    @GameTest(template = LARGE)
    public static void demonSummonRefusals(GameTestHelper helper) {
        DemonicPentacleBlockEntity be = build(helper);
        fill(helper, 100D);
        ServerPlayer player = player(helper, "ec5-summoner", new Vec3(5.5D, 2D, 9.5D));

        BlockPos stone = at(ECDemon.VOID_STONE_OFFSETS.get(3));
        helper.setBlock(stone, Blocks.STONE);
        expectRefusal(helper, be, player, DemonicPentacleBlockEntity.Result.NOT_FORMED, "结构不完整");
        helper.setBlock(stone, ECVoid.VOID_STONE.get());

        helper.setBlock(PENTACLE.above(), Blocks.STONE);
        expectRefusal(helper, be, player, DemonicPentacleBlockEntity.Result.BLOCKED, "头顶被挡");
        helper.setBlock(PENTACLE.above(), Blocks.AIR);

        DemonTrades.setTestOverride(List.of());
        try {
            expectRefusal(helper, be, player, DemonicPentacleBlockEntity.Result.NO_TRADES, "交易池为空");
        } finally {
            DemonTrades.setTestOverride(null);
        }

        ECDemon.Config.setTestOverrides(5000, null, null);
        try {
            expectRefusal(helper, be, player, DemonicPentacleBlockEntity.Result.NO_ESPE, "ESPE 不够（1 600 < 5 000）");
        } finally {
            ECDemon.Config.setTestOverrides(null, null, null);
        }

        DemonEntity existing = demon(helper, Vec3.atBottomCenterOf(PENTACLE), player.getUUID(), ironBlocks(8));
        helper.assertTrue(be.summon(player).result() == DemonicPentacleBlockEntity.Result.OCCUPIED, "已有恶魔时应拒绝");
        helper.assertTrue(Math.abs(total(helper) - 1600D) < EPS, "已有恶魔时不扣 ESPE");
        helper.assertTrue(demonsOnPentacle(helper).size() == 1, "不应召出第二只");
        existing.discard();
        helper.succeed();
    }

    private static void expectRefusal(GameTestHelper helper, DemonicPentacleBlockEntity be, ServerPlayer player,
                                      DemonicPentacleBlockEntity.Result expected, String what) {
        DemonicPentacleBlockEntity.Outcome outcome = be.summon(player);
        helper.assertTrue(outcome.result() == expected, what + "：结果应是 " + expected + "，实际 " + outcome.result());
        helper.assertTrue(outcome.demon() == null && outcome.message() != null, what + "：不应召出恶魔、应有提示");
        helper.assertTrue(demonsOnPentacle(helper).isEmpty(), what + "：世界里不应出现恶魔");
        helper.assertTrue(Math.abs(total(helper) - 1600D) < EPS, what + "：柱子应一点不少，实际合计 " + total(helper));
    }

    /** 成功：16 根各 100，合计正好少 666，按原作 coords 顺序抽（前 6 根抽空、第 7 根剩 34）；需求、召唤者、五芒星位置记下 */
    @GameTest(template = LARGE)
    public static void demonSummonDrainsInOrder(GameTestHelper helper) {
        DemonicPentacleBlockEntity be = build(helper);
        fill(helper, 100D);
        ServerPlayer player = player(helper, "ec5-summoner", new Vec3(5.5D, 2D, 9.5D));
        ECDemon.Config.setTestOverrides(666, null, null);
        DemonicPentacleBlockEntity.Outcome outcome;
        try {
            outcome = be.summon(player);
        } finally {
            ECDemon.Config.setTestOverrides(null, null, null);
        }
        DemonEntity demon = outcome.demon();
        helper.assertTrue(outcome.result() == DemonicPentacleBlockEntity.Result.OK && demon != null, "应召唤成功，实际 " + outcome.result());
        demon.setTestClearOverrides(0, null);
        helper.assertTrue(Math.abs(total(helper) - (1600D - 666D)) < EPS, "合计应正好少 666，实际 " + total(helper));
        for (int i = 0; i < ECDemon.PILLAR_OFFSETS.size(); i++) {
            double expected = i < 6 ? 0D : i == 6 ? 34D : 100D;
            double actual = pillar(helper, i).getESPEStorage().getESPE();
            helper.assertTrue(Math.abs(actual - expected) < EPS, "第 " + i + " 根柱子应剩 " + expected + "，实际 " + actual);
        }
        DemonWant want = demon.want();
        helper.assertTrue(want != null, "召出来的恶魔应已掷好需求");
        if (want.isSoul()) {
            helper.assertTrue(want.count() >= 1 && want.count() <= 7, "灵魂数量应在 1~7，实际 " + want.count());
        }
        helper.assertTrue(player.getUUID().equals(demon.summoner()), "应记下召唤者");
        helper.assertTrue(helper.absolutePos(PENTACLE).equals(demon.pentacle()), "应记下五芒星位置");
        Vec3 expectedPos = Vec3.atLowerCornerOf(helper.absolutePos(PENTACLE)).add(0.5D, 0.1D, 0.5D);
        helper.assertTrue(demon.position().distanceTo(expectedPos) < EPS, "恶魔应生成在五芒星中心上方 0.1 格");
        helper.assertTrue(be.summon(player).result() == DemonicPentacleBlockEntity.Result.OCCUPIED, "一阵一只");
        demon.discard();
        helper.succeed();
    }

    /**
     * 生成被拦：别的模组取消 {@code EntityJoinLevelEvent}，或者在加入事件里直接把它移除（不取消，addFreshEntity 仍返回 true）——
     * 结果都是 SPAWN_FAILED、16 根柱子一点不少、阵上没有恶魔。对照：不拦时照常召出。监听器只认落在这个五芒星上的恶魔，别的测试不受影响。
     */
    @GameTest(template = LARGE)
    public static void demonSummonSpawnBlockedKeepsEspe(GameTestHelper helper) {
        DemonicPentacleBlockEntity be = build(helper);
        fill(helper, 100D);
        ServerPlayer player = player(helper, "ec5-summoner", new Vec3(5.5D, 2D, 9.5D));
        BlockPos pentacleAbs = helper.absolutePos(PENTACLE);
        List<DemonEntity> joined = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof DemonEntity d && pentacleAbs.equals(d.pentacle())) {
                event.setCanceled(true);
            }
        };
        Consumer<EntityJoinLevelEvent> discard = event -> {
            if (event.getEntity() instanceof DemonEntity d && pentacleAbs.equals(d.pentacle())) {
                joined.add(d);
                d.discard();
            }
        };
        for (Consumer<EntityJoinLevelEvent> deny : List.of(cancel, discard)) {
            String what = deny == cancel ? "加入事件被取消" : "加入时就被移除";
            DemonicPentacleBlockEntity.Outcome outcome = summonWith(be, player, deny);
            // 加入时就被移除的那只其实进了实体分区（事件之后才登记回调），再移除一次才真正摘掉
            joined.forEach(Entity::discard);
            joined.clear();
            helper.assertTrue(outcome.result() == DemonicPentacleBlockEntity.Result.SPAWN_FAILED && outcome.demon() == null
                    && outcome.message() != null, what + "：结果应是 SPAWN_FAILED，实际 " + outcome.result());
            helper.assertTrue(Math.abs(total(helper) - 1600D) < EPS, what + "：柱子应一点不少，实际合计 " + total(helper));
            helper.assertTrue(demonsOnPentacle(helper).isEmpty(), what + "：阵上不应有恶魔");
        }
        DemonicPentacleBlockEntity.Outcome control = summonWith(be, player, null);
        DemonEntity summoned = control.demon();
        helper.assertTrue(control.result() == DemonicPentacleBlockEntity.Result.OK && summoned != null, "对照：不拦时应召唤成功，实际 " + control.result());
        summoned.discard();
        helper.assertTrue(Math.abs(total(helper) - (1600D - 666D)) < EPS, "对照：成功时才扣 666，实际合计 " + total(helper));
        helper.succeed();
    }

    /** 花费固定 666 召唤一次；deny 不为 null 时召唤期间挂上这个加入事件监听器 */
    private static DemonicPentacleBlockEntity.Outcome summonWith(DemonicPentacleBlockEntity be, ServerPlayer player,
                                                                @Nullable Consumer<EntityJoinLevelEvent> deny) {
        ECDemon.Config.setTestOverrides(666, null, null);
        if (deny != null) {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, deny);
        }
        try {
            return be.summon(player);
        } finally {
            if (deny != null) {
                MinecraftForge.EVENT_BUS.unregister(deny);
            }
            ECDemon.Config.setTestOverrides(null, null, null);
        }
    }

    /** 召唤只认真玩家：假玩家右键五芒星返回 PASS、不出恶魔、柱子不少；同一个阵真玩家右键照常召出（扣 666） */
    @GameTest(template = LARGE)
    public static void demonPentacleFakePlayerCannotSummon(GameTestHelper helper) {
        build(helper);
        fill(helper, 100D);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(PENTACLE);
        BlockState state = level.getBlockState(abs);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        ServerPlayer real = player(helper, "ec5-summoner", new Vec3(5.5D, 2D, 9.5D));
        ECDemon.Config.setTestOverrides(666, null, null);
        InteractionResult byFake;
        InteractionResult byReal;
        List<DemonEntity> afterFake;
        double totalAfterFake;
        try {
            byFake = state.use(level, ProtectionFrameworkTests.fakePlayer(helper), InteractionHand.MAIN_HAND, hit);
            afterFake = demonsOnPentacle(helper);
            totalAfterFake = total(helper);
            byReal = state.use(level, real, InteractionHand.MAIN_HAND, hit);
        } finally {
            ECDemon.Config.setTestOverrides(null, null, null);
        }
        List<DemonEntity> afterReal = demonsOnPentacle(helper);
        afterReal.forEach(Entity::discard);
        helper.assertTrue(byFake == InteractionResult.PASS, "假玩家右键应返回 PASS，实际 " + byFake);
        helper.assertTrue(afterFake.isEmpty(), "假玩家不应召出恶魔");
        helper.assertTrue(Math.abs(totalAfterFake - 1600D) < EPS, "假玩家右键不应扣 ESPE，实际合计 " + totalAfterFake);
        helper.assertTrue(byReal == InteractionResult.CONSUME && afterReal.size() == 1, "真玩家右键应召出一只，实际 " + byReal + " / " + afterReal.size());
        helper.assertTrue(real.getUUID().equals(afterReal.get(0).summoner()), "召唤者应记成这个真玩家");
        helper.assertTrue(Math.abs(total(helper) - (1600D - 666D)) < EPS, "真玩家召唤应扣 666，实际合计 " + total(helper));
        helper.succeed();
    }

    /** 读档后需求、召唤者、五芒星位置不变；物品需求的 any_damage 也在 */
    @GameTest(template = LARGE)
    public static void demonSaveLoadKeepsState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID summoner = UUID.randomUUID();
        BlockPos pentacle = helper.absolutePos(PENTACLE);
        DemonEntity original = ECDemon.DEMON.get().create(level);
        DemonEntity copy = ECDemon.DEMON.get().create(level);
        helper.assertTrue(original != null && copy != null, "应能建出恶魔");
        original.setWant(DemonWant.soul(ResourceLocation.withDefaultNamespace("villager"), 5));
        original.setSummoner(summoner);
        original.setPentacle(pentacle);
        copy.load(original.saveWithoutId(new CompoundTag()));
        DemonWant want = copy.want();
        helper.assertTrue(want != null && want.isSoul() && want.count() == 5
                && ResourceLocation.withDefaultNamespace("villager").equals(want.soul()), "灵魂需求读档后应不变");
        helper.assertTrue(summoner.equals(copy.summoner()) && pentacle.equals(copy.pentacle()), "召唤者、五芒星位置读档后应不变");

        Item pistol = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("pistol"));
        original.setWant(DemonWant.item(pistol, 1, true));
        DemonEntity third = ECDemon.DEMON.get().create(level);
        helper.assertTrue(third != null, "应能建出恶魔");
        third.load(original.saveWithoutId(new CompoundTag()));
        DemonWant gun = third.want();
        helper.assertTrue(gun != null && gun.item() == pistol && gun.anyDamage() && gun.count() == 1, "物品需求（含 any_damage）读档后应不变");

        // 读档容错：实体已经不存在（卸了模组）时读成 null，恶魔下一刻重掷；数量照交易池的规则夹
        CompoundTag missingMob = new CompoundTag();
        missingMob.putString("soul", "essentialcraft5:no_such_mob");
        missingMob.putInt("count", 3);
        helper.assertTrue(DemonWant.load(missingMob) == null, "没注册的实体的灵魂需求读档应返回 null");
        CompoundTag manyGuns = new CompoundTag();
        manyGuns.putString("item", "essentialcraft5:pistol");
        manyGuns.putInt("count", 5);
        DemonWant clampedGun = DemonWant.load(manyGuns);
        helper.assertTrue(clampedGun != null && clampedGun.count() == 1, "手枪一组只有 1 把，要 5 把应夹到 1，实际 " + clampedGun);
        CompoundTag manySouls = new CompoundTag();
        manySouls.putString("soul", "minecraft:villager");
        manySouls.putInt("count", 200);
        DemonWant clampedSouls = DemonWant.load(manySouls);
        helper.assertTrue(clampedSouls != null && clampedSouls.count() == DemonTrades.MAX_SOUL_COUNT,
                "灵魂数量应夹到 " + DemonTrades.MAX_SOUL_COUNT + "，实际 " + clampedSouls);
        helper.succeed();
    }

    // ================================================================ 守阵

    /** 成形时留着（过了第 40 刻那次检查）；第 45 刻拆掉五芒星正下方的虚空石，41 刻内离开 */
    @GameTest(template = LARGE, timeoutTicks = 140)
    public static void demonGuardLeavesWhenCenterBroken(GameTestHelper helper) {
        build(helper);
        DemonEntity demon = demon(helper, Vec3.atBottomCenterOf(PENTACLE), UUID.randomUUID(), ironBlocks(8));
        demon.setPentacle(helper.absolutePos(PENTACLE));
        helper.runAtTickTime(44, () -> {
            helper.assertTrue(demon.isAlive() && !demon.isRemoved(), "五芒星成形时恶魔应留着");
            helper.setBlock(at(new BlockPos(0, -1, 0)), Blocks.STONE);
        });
        helper.runAtTickTime(44 + 41, () -> {
            helper.assertTrue(demon.isRemoved(), "拆掉五芒星正下方的方块后 41 刻内应离开");
            helper.succeed();
        });
    }

    /** 拆外圈的一块（不挨着五芒星，靠结构 40 刻复查）：81 刻内离开 */
    @GameTest(template = LARGE, timeoutTicks = 180)
    public static void demonGuardLeavesWhenOuterBroken(GameTestHelper helper) {
        build(helper);
        DemonEntity demon = demon(helper, Vec3.atBottomCenterOf(PENTACLE), UUID.randomUUID(), ironBlocks(8));
        helper.runAtTickTime(44, () -> {
            helper.assertTrue(!demon.isRemoved(), "五芒星成形时恶魔应留着");
            helper.setBlock(at(new BlockPos(3, -1, 1)), Blocks.STONE);
        });
        helper.runAtTickTime(44 + 81, () -> {
            helper.assertTrue(demon.isRemoved(), "拆掉外圈一块后 81 刻内应离开");
            helper.succeed();
        });
    }

    /** 刷怪蛋出来、不在阵上的：40 刻那次检查就离开（原作行为） */
    @GameTest(template = LARGE, timeoutTicks = 80)
    public static void demonLeavesWithoutPentacle(GameTestHelper helper) {
        DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), null, null);
        helper.runAtTickTime(30, () -> helper.assertTrue(!demon.isRemoved(), "40 刻之前还在"));
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(demon.isRemoved(), "不在阵上应在第 40 刻离开");
            helper.succeed();
        });
    }

    /**
     * 五芒星还没判定过（刚放下，{@code guardState()} 是 UNKNOWN）时不算坏，恶魔这次守阵检查留下；五芒星判定一次（没搭结构，BROKEN）后再查就离开。
     * 直接调恶魔的 tick（刚加进世界 tickCount 是 0，正好是守阵检查那一刻），全在同一段同步代码里，五芒星不会自己先 tick。
     * （「格子没加载」那条分支走不到：恶魔只在实体 tick 区块里 tick，四周区块一定已加载，结构也伸不出相邻区块。）
     */
    @GameTest(template = LARGE)
    public static void demonGuardUnknownIsNotBroken(GameTestHelper helper) {
        // 脚下垫一块石头：tick 里先走原版移动再守阵，悬空的话这一刻就往下掉出五芒星那一格
        helper.setBlock(PENTACLE.below(), Blocks.STONE);
        helper.setBlock(PENTACLE, ECDemon.DEMONIC_PENTACLE.get());
        DemonicPentacleBlockEntity be = (DemonicPentacleBlockEntity) helper.getBlockEntity(PENTACLE);
        helper.assertTrue(be.guardState() == DemonicPentacleBlockEntity.GuardState.UNKNOWN, "刚放下、还没判定的五芒星应是 UNKNOWN，实际 " + be.guardState());
        DemonEntity demon = demon(helper, Vec3.atBottomCenterOf(PENTACLE), null, ironBlocks(8));
        helper.assertTrue(demon.tickCount % DemonEntity.GUARD_INTERVAL == 0, "前提：这一刻是守阵检查，tickCount = " + demon.tickCount);
        demon.tick();
        helper.assertTrue(!demon.isRemoved(), "五芒星还不知道成没成形时恶魔应留着");
        be.serverTick();
        helper.assertTrue(be.guardState() == DemonicPentacleBlockEntity.GuardState.BROKEN, "没搭结构的五芒星判定后应是 BROKEN，实际 " + be.guardState());
        demon.tick();
        helper.assertTrue(demon.isRemoved(), "五芒星判定成坏了以后，下一次守阵检查恶魔应离开");
        helper.succeed();
    }

    // ================================================================ 伤害

    /** 别的玩家打、箭（召唤者射的也算）、岩浆、爆炸都打不掉；召唤者、创造模式玩家近战一下离开；genericKill 能杀；拴不住、推不动 */
    @GameTest(template = LARGE)
    public static void demonDamageImmunity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer summoner = player(helper, "ec5-summoner", new Vec3(4.5D, 2D, 5.5D));
        ServerPlayer stranger = player(helper, "ec5-stranger", new Vec3(6.5D, 2D, 5.5D));
        DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), summoner.getUUID(), ironBlocks(8));

        helper.assertFalse(demon instanceof Enemy, "恶魔不应是敌对生物（线圈、反应堆不打它）");
        stranger.attack(demon);
        helper.assertTrue(demon.isAlive() && !demon.isRemoved(), "别的玩家打一下不应打掉");
        helper.assertFalse(demon.hurt(level.damageSources().playerAttack(stranger), 10F), "别的玩家的攻击应无效");
        Arrow arrow = new Arrow(level, summoner);
        helper.assertFalse(demon.hurt(level.damageSources().arrow(arrow, summoner), 10F), "箭（召唤者射的也一样）打不掉");
        helper.assertFalse(demon.hurt(level.damageSources().lava(), 10F), "岩浆打不掉");
        helper.assertFalse(demon.hurt(level.damageSources().explosion(null, null), 30F), "爆炸打不掉");
        helper.assertTrue(demon.getHealth() == demon.getMaxHealth() && !demon.isRemoved(), "以上都不应掉血、离开");
        helper.assertTrue(demon.ignoreExplosion() && !demon.isPushable() && !demon.isPushedByFluid(), "爆炸、玩家、水流都推不动");
        helper.assertFalse(demon.canBeLeashed(summoner), "拴不住");

        // 横扫：Player.attack 对周围的目标只调 hurt、不调 skipAttackInteraction；以玩家名义直接调 hurt 的范围伤害也走这条路。
        // 测试玩家不 tick，攻击冷却走不满、真 attack 打不出横扫，所以直接用 playerAttack 伤害源调 hurt（同 SoulScriberTests）
        summoner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        helper.assertFalse(demon.hurt(level.damageSources().playerAttack(summoner), 5F), "召唤者横扫打到它不算伤害");
        helper.assertTrue(!demon.isRemoved(), "召唤者横扫打到它不算打发");

        summoner.attack(demon);
        helper.assertTrue(demon.isRemoved(), "召唤者近战打一下应离开");

        ServerPlayer creative = player(helper, "ec5-creative", new Vec3(4.5D, 2D, 4.5D));
        creative.setGameMode(GameType.CREATIVE);
        DemonEntity second = demon(helper, new Vec3(5.5D, 2D, 4.5D), summoner.getUUID(), ironBlocks(8));
        creative.attack(second);
        helper.assertTrue(second.isRemoved(), "创造模式玩家近战打一下应离开");

        DemonEntity third = demon(helper, new Vec3(5.5D, 2D, 6.5D), summoner.getUUID(), ironBlocks(8));
        third.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(third.isDeadOrDying(), "/kill（genericKill）应能杀死");
        third.discard();
        helper.succeed();
    }

    // ================================================================ 清怪

    /** 目标判定：僵尸、史莱姆中；村民、起名的僵尸、捡过东西的僵尸、驯服的狼、猪、凋灵、恶魔、玩家、demon_immune 里的不中 */
    @GameTest(template = LARGE)
    public static void demonClearTargetRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        Slime slime = EntityType.SLIME.create(level);
        Villager villager = EntityType.VILLAGER.create(level);
        Zombie named = EntityType.ZOMBIE.create(level);
        Zombie looter = EntityType.ZOMBIE.create(level);
        Wolf wolf = EntityType.WOLF.create(level);
        Pig pig = EntityType.PIG.create(level);
        WitherBoss wither = EntityType.WITHER.create(level);
        DemonEntity other = ECDemon.DEMON.get().create(level);
        helper.assertTrue(zombie != null && slime != null && villager != null && named != null && looter != null && wolf != null
                && pig != null && wither != null && other != null, "应能建出这些生物");
        named.setCustomName(Component.literal("Bob"));
        looter.setPersistenceRequired();
        wolf.setTame(true);
        wolf.setOwnerUUID(UUID.randomUUID());
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);

        helper.assertTrue(DemonEntity.isClearTarget(zombie), "普通僵尸应中");
        helper.assertTrue(DemonEntity.isClearTarget(slime), "史莱姆应中");
        helper.assertFalse(DemonEntity.isClearTarget(villager), "村民不中");
        helper.assertFalse(DemonEntity.isClearTarget(named), "起了名字的僵尸不中");
        helper.assertFalse(DemonEntity.isClearTarget(looter), "捡过东西（持久）的僵尸不中");
        helper.assertFalse(DemonEntity.isClearTarget(wolf), "驯服的狼不中");
        helper.assertFalse(DemonEntity.isClearTarget(pig), "猪不中");
        helper.assertFalse(DemonEntity.isClearTarget(wither), "Boss（凋灵）不中");
        helper.assertFalse(DemonEntity.isClearTarget(other), "别的恶魔不中");
        helper.assertFalse(DemonEntity.isClearTarget(player), "玩家不中");
        DemonEntity.setTestImmune(EntityType.ZOMBIE);
        try {
            helper.assertFalse(DemonEntity.isClearTarget(zombie), "在 demon_immune 里的不中");
        } finally {
            DemonEntity.setTestImmune(null);
        }
        helper.assertTrue(DemonEntity.isClearTarget(zombie), "移出 demon_immune 后又中");
        helper.succeed();
    }

    /**
     * 清一次：被打的僵尸死了、不掉战利品（换成必掉白羊毛的表）也不给经验（先被玩家打过），掉落率 2.0 的帽子（捡来的）照掉；
     * 旁边另一只僵尸被炸伤；离两只僵尸都在 2.3 格内的真玩家（先空跑 61 刻去掉出生无敌，否则过滤坏了也看不出掉血）、村民、驯服的狼、
     * 盔甲架、展示框、掉落物不受伤、不被推、都还在；玻璃、草方块不变。对照组：普通伤害打死的僵尸掉白羊毛。
     */
    @GameTest(template = LARGE)
    public static void demonClearKillsWithoutLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "ec5-hunter", new Vec3(9.5D, 2D, 1.5D));
        // 召唤者在线（A6：清怪要问召唤者能不能动怪所在的格子；这里没有领地，都能动）
        ECProtection.setOnlineOverride(player);
        DemonEntity demon = demon(helper, new Vec3(2.5D, 2D, 2.5D), player.getUUID(), ironBlocks(8));
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.GLASS);
        helper.setBlock(new BlockPos(6, 1, 4), Blocks.GRASS_BLOCK);

        Zombie first = zombie(helper, new BlockPos(5, 2, 5), true);
        Zombie second = zombie(helper, new BlockPos(6, 2, 5), true);
        for (Zombie zombie : List.of(first, second)) {
            zombie.setDropChance(EquipmentSlot.HEAD, 2.0F);
            zombie.hurt(level.damageSources().playerAttack(player), 1F);
            zombie.invulnerableTime = 0;
        }
        Zombie control = zombie(helper, new BlockPos(9, 2, 9), true);

        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 6));
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(4, 2, 5));
        wolf.setTame(true);
        wolf.setOwnerUUID(player.getUUID());
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(5, 2, 4));
        Vec3 itemAt = helper.absoluteVec(new Vec3(6.5D, 2D, 6.5D));
        ItemEntity cobble = new ItemEntity(level, itemAt.x, itemAt.y, itemAt.z, new ItemStack(Items.COBBLESTONE));
        cobble.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(cobble);
        // 玩家站在两只僵尸 2~2.3 格处（威力 2 的爆炸伤害半径 4 格）。realPlayer 那种 ServerPlayer 不在世界里，爆炸查不到它；
        // 这里把模拟玩家放进世界（它没有出生无敌），只在这一段同步代码里待着，最后移除，不让它 tick
        Player bystander = helper.makeMockPlayer();
        Vec3 bystanderAt = helper.absoluteVec(new Vec3(5.5D, 2D, 3.5D));
        bystander.moveTo(bystanderAt.x, bystanderAt.y, bystanderAt.z, 0F, 0F);
        bystander.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(bystander);
        // 展示框贴在 (4, 1, 4) 那块玻璃的顶面上
        ItemFrame frame = new ItemFrame(level, helper.absolutePos(new BlockPos(4, 2, 4)), Direction.UP);
        frame.setItem(new ItemStack(Items.DIAMOND), false);
        level.addFreshEntity(frame);
        try {
            clearAndCheckBystanders(helper, demon, first, second, control, List.of(villager, wolf, stand, cobble, bystander, frame));
            helper.assertTrue(frame.getItem().is(Items.DIAMOND), "展示框里的东西不应被炸出来");
        } finally {
            bystander.discard();
            ECProtection.clearOnlineOverride(player.getUUID());
        }
        helper.succeed();
    }

    /** 清一次并核对：被打的那只死了、不掉战利品不给经验，帽子照掉，旁边另一只僵尸挨炸，bystanders 不受伤、不被推、都还在，方块不变 */
    private static void clearAndCheckBystanders(GameTestHelper helper, DemonEntity demon, Zombie first, Zombie second, Zombie control,
                                                List<Entity> bystanders) {
        ServerLevel level = helper.getLevel();
        List<Vec3> motions = new ArrayList<>();
        List<Float> healths = new ArrayList<>();
        for (Entity e : bystanders) {
            motions.add(e.getDeltaMovement());
            healths.add(e instanceof LivingEntity living ? living.getHealth() : 0F);
        }

        float firstHealth = first.getHealth();
        float secondHealth = second.getHealth();
        LivingEntity struck = demon.clearOnce(4);
        helper.assertTrue(struck == first || struck == second, "应打范围里两只僵尸中的一只，实际 " + struck);
        Zombie other = struck == first ? second : first;
        float otherBefore = struck == first ? secondHealth : firstHealth;
        helper.assertTrue(!struck.isAlive(), "被打的僵尸挨 1.6 倍最大生命应死");
        helper.assertTrue(other.getHealth() < otherBefore || !other.isAlive(), "旁边另一只僵尸应被炸伤");
        Vec3 struckAt = struck.position();
        helper.assertTrue(items(helper, struckAt, 3D, Items.WHITE_WOOL).isEmpty(), "被恶魔打死、炸死的不掉战利品");
        helper.assertFalse(items(helper, struckAt, 3D, Items.LEATHER_HELMET).isEmpty(), "掉落率 2.0 的帽子（捡来的装备）照掉");
        helper.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class, new AABB(struckAt, struckAt).inflate(3D), Entity::isAlive).isEmpty(),
                "被恶魔打死的不给经验");
        for (int i = 0; i < bystanders.size(); i++) {
            Entity e = bystanders.get(i);
            helper.assertTrue(e.isAlive() && !e.isRemoved() && e.getDeltaMovement().equals(motions.get(i)), e.getType() + " 不应受伤、被推");
            if (e instanceof LivingEntity living) {
                helper.assertTrue(living.getHealth() == healths.get(i), e.getType() + " 不应掉血");
            }
        }
        helper.assertBlockPresent(Blocks.GLASS, new BlockPos(4, 1, 4));
        helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(6, 1, 4));
        helper.assertTrue(control.isAlive(), "范围外的僵尸不该被打");

        control.hurt(level.damageSources().generic(), 100F);
        helper.assertFalse(items(helper, control.position(), 3D, Items.WHITE_WOOL).isEmpty(), "对照：普通伤害打死的照常掉战利品");
    }

    /** 间隔：每 10 刻打一只（三只僵尸互相隔得比爆炸范围远），第 15 / 25 / 35 刻分别死 1 / 2 / 3 只（召唤者在线） */
    @GameTest(template = LARGE, timeoutTicks = 60)
    public static void demonClearInterval(GameTestHelper helper) {
        List<Zombie> zombies = List.of(zombie(helper, new BlockPos(2, 2, 5)), zombie(helper, new BlockPos(8, 2, 5)),
                zombie(helper, new BlockPos(5, 2, 2)));
        ServerPlayer summoner = player(helper, "ec5-summoner", new Vec3(9.5D, 2D, 9.5D));
        ECProtection.setOnlineOverride(summoner);
        DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), summoner.getUUID(), ironBlocks(8));
        demon.setTestClearOverrides(4, 10);
        int[][] checks = {{15, 1}, {25, 2}, {35, 3}};
        for (int[] check : checks) {
            helper.runAtTickTime(check[0], () -> {
                long dead = zombies.stream().filter(z -> !z.isAlive()).count();
                helper.assertTrue(dead == check[1], "第 " + check[0] + " 刻应死 " + check[1] + " 只，实际 " + dead);
                if (check[1] == 3) {
                    ECProtection.clearOnlineOverride(summoner.getUUID());
                    helper.succeed();
                }
            });
        }
    }

    /**
     * A6：只清召唤者能动的格子里的怪。东边那只在模拟领地里（召唤者的探测被拒）、西边那只能动：清怪打西边那只；
     * 西边那只旁边、同在爆炸范围里的第三只若在领地里也不受爆炸伤害。探测以召唤者身份发在怪脚下那一格
     */
    @GameTest(template = LARGE)
    public static void demonClearsOnlySummonersCells(GameTestHelper helper) {
        ServerPlayer summoner = player(helper, "ec5-summoner", new Vec3(9.5D, 2D, 9.5D));
        ECProtection.setOnlineOverride(summoner);
        BlockPos claimed = new BlockPos(8, 2, 5);
        BlockPos claimedNearFree = new BlockPos(4, 2, 4);
        BlockPos free = new BlockPos(3, 2, 5);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(claimed).deny(claimedNearFree)) {
            Zombie inClaim = zombie(helper, claimed);
            Zombie nearby = zombie(helper, claimedNearFree);
            Zombie target = zombie(helper, free);
            DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), summoner.getUUID(), ironBlocks(8));
            helper.assertTrue(demon.clearPermission(inClaim) == ECProtection.Verdict.DENY, "领地里那只应被拒");
            helper.assertTrue(claims.breaksBy(claimed, summoner.getUUID()) == 1, "应以召唤者身份探测领地那格");
            LivingEntity struck = demon.clearOnce(4);
            helper.assertTrue(struck == target && !target.isAlive(), "应打能动的那只，实际 " + struck);
            helper.assertTrue(inClaim.isAlive() && inClaim.getHealth() == inClaim.getMaxHealth(), "领地里那只不该挨打");
            helper.assertTrue(nearby.isAlive() && nearby.getHealth() == nearby.getMaxHealth(),
                    "爆炸范围里、在领地里的那只不该受伤，剩 " + nearby.getHealth());
            helper.assertTrue(claims.breaksBy(claimed, summoner.getUUID()) == 1, "记着不能动的那格清怪时不再探测，实际 "
                    + claims.breaksBy(claimed, summoner.getUUID()));
            int nearbyProbes = claims.breaksBy(claimedNearFree, summoner.getUUID());
            helper.assertTrue(nearbyProbes == 1, "爆炸范围里领地那格应以召唤者身份探测一次，实际 " + nearbyProbes);
            // 探测缓存过了（forget 代替等 200 刻）再清一次：被拒的两格记录命中，召唤者不再收到探测
            ECProtection.forget(summoner.getUUID());
            Zombie second = zombie(helper, free);
            helper.assertTrue(demon.clearOnce(4) == second, "第二次照样打能动的那只");
            helper.assertTrue(claims.breaksBy(claimed, summoner.getUUID()) == 1 && claims.breaksBy(claimedNearFree, summoner.getUUID()) == 1,
                    "被拒的格子只探测一次，实际 " + claims.breaksBy(claimed, summoner.getUUID()) + " / " + claims.breaksBy(claimedNearFree, summoner.getUUID()));
            helper.assertTrue(inClaim.getHealth() == inClaim.getMaxHealth() && nearby.getHealth() == nearby.getMaxHealth(), "领地里的两只始终不受伤");
        } finally {
            ECProtection.clearOnlineOverride(summoner.getUUID());
        }
        helper.succeed();
    }

    /**
     * A6 召唤者离线：在线时探测过、能动的格子离线后照清；没探测过的格子不清；没有召唤者的恶魔（刷怪蛋）在专用服里不清怪
     */
    @GameTest(template = LARGE)
    public static void demonOfflineSummonerUsesRememberedCells(GameTestHelper helper) {
        ServerPlayer summoner = player(helper, "ec5-summoner", new Vec3(9.5D, 2D, 9.5D));
        BlockPos known = new BlockPos(3, 2, 5);
        BlockPos unknown = new BlockPos(8, 2, 8);
        DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), summoner.getUUID(), ironBlocks(8));
        ECProtection.setOnlineOverride(summoner);
        Zombie first = zombie(helper, known);
        try {
            helper.assertTrue(demon.clearOnce(4) == first, "召唤者在线时应清掉那只");
        } finally {
            ECProtection.clearOnlineOverride(summoner.getUUID());
            ECProtection.forget(summoner.getUUID());
        }
        helper.assertTrue(demon.ownerCells().remembers(helper.getLevel(), helper.absolutePos(known)), "应记下能动的那格");

        Zombie fresh = zombie(helper, unknown);
        helper.assertTrue(demon.clearOnce(4) == null && fresh.isAlive() && fresh.getHealth() == fresh.getMaxHealth(),
                "召唤者离线时没探测过的格子不清");
        Zombie again = zombie(helper, known);
        LivingEntity struck = demon.clearOnce(4);
        helper.assertTrue(struck == again && !again.isAlive(), "召唤者离线时记着的格子照清，实际 " + struck);

        // 刷怪蛋出来的那只站在 fresh 旁边（fresh 在它 4 格的清怪范围里）
        DemonEntity stray = demon(helper, new Vec3(7.5D, 2D, 6.5D), null, ironBlocks(8));
        helper.assertTrue(stray.clearPermission(fresh) == ECProtection.Verdict.DENY && stray.clearOnce(4) == null && fresh.isAlive(),
                "没有召唤者的恶魔在专用服里不清怪");
        stray.discard();
        helper.succeed();
    }

    /**
     * 一次清怪以召唤者身份的新探测最多 {@link DemonEntity#PROBES_PER_CLEAR} 格：6 只怪各在一格领地里，第一次清怪只探 4 格、谁都不打，
     * 第二次（探测缓存过了）只探剩下的 2 格，第三次一格都不探
     */
    @GameTest(template = LARGE)
    public static void demonClearProbesCapped(GameTestHelper helper) {
        ServerPlayer summoner = player(helper, "ec5-summoner", new Vec3(9.5D, 2D, 9.5D));
        ECProtection.setOnlineOverride(summoner);
        List<BlockPos> cells = List.of(new BlockPos(2, 2, 2), new BlockPos(8, 2, 2), new BlockPos(2, 2, 8),
                new BlockPos(8, 2, 8), new BlockPos(5, 2, 2), new BlockPos(5, 2, 8));
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        cells.forEach(claims::deny);
        try (claims) {
            List<Zombie> zombies = cells.stream().map(cell -> zombie(helper, cell)).toList();
            DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), summoner.getUUID(), ironBlocks(8));
            int[] expected = {DemonEntity.PROBES_PER_CLEAR, cells.size(), cells.size()};
            for (int i = 0; i < expected.length; i++) {
                helper.assertTrue(demon.clearOnce(4) == null, "领地里的怪都不该打");
                int probes = cells.stream().mapToInt(cell -> claims.breaksBy(cell, summoner.getUUID())).sum();
                helper.assertTrue(probes == expected[i], "第 " + (i + 1) + " 次清怪后以召唤者身份累计应探测 " + expected[i] + " 格，实际 " + probes);
                helper.assertTrue(cells.stream().allMatch(cell -> claims.breaksBy(cell, summoner.getUUID()) <= 1), "同一格不该探测两次");
                ECProtection.forget(summoner.getUUID());
            }
            helper.assertTrue(zombies.stream().allMatch(z -> z.getHealth() == z.getMaxHealth()), "领地里的怪都没受伤");
            demon.discard();
            zombies.forEach(Entity::discard);
        } finally {
            ECProtection.clearOnlineOverride(summoner.getUUID());
        }
        helper.succeed();
    }

    /** clear_radius = 0 不清怪 */
    @GameTest(template = LARGE, timeoutTicks = 60)
    public static void demonClearRadiusZero(GameTestHelper helper) {
        List<Zombie> zombies = List.of(zombie(helper, new BlockPos(3, 2, 5)), zombie(helper, new BlockPos(7, 2, 5)));
        DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), null, ironBlocks(8));
        demon.setTestClearOverrides(0, 10);
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(zombies.stream().allMatch(Entity::isAlive), "范围 0 时不应清怪");
            helper.succeed();
        });
    }

    // ================================================================ 交易池

    /** 默认池 47 条：删掉的 8 条不在；传送器 ×2、终极热力产能器 ×1 在；5 条灵魂是村民 / 末影人 / 风法师 / 炼狱小鬼（×2）/ 遗民拾荒者（×2）且都在 soul_capturable 里；枪不看耐久 */
    @GameTest(template = LARGE)
    public static void demonDefaultTradePool(GameTestHelper helper) {
        List<DemonTrades.Entry> pool = DemonTrades.entries();
        helper.assertTrue(pool.size() == DEFAULT_POOL_SIZE, "默认交易池应有 47 条，实际 " + pool.size());
        Set<ResourceLocation> ids = new HashSet<>();
        Set<ResourceLocation> souls = new HashSet<>();
        for (DemonTrades.Entry entry : pool) {
            helper.assertTrue(entry.isSoul() || (entry.item() != null && entry.item() != Items.AIR), "每条物品都应解析成已注册的物品");
            if (entry.isSoul()) {
                souls.add(entry.soul());
            } else {
                ids.add(entry.id());
            }
        }
        for (String removed : new String[]{"bedrock", "end_portal_frame", "command_block", "chain_command_block", "repeating_command_block",
                "dragon_egg"}) {
            helper.assertFalse(ids.contains(ResourceLocation.withDefaultNamespace(removed)), "删掉的 " + removed + " 不应在池里");
        }
        helper.assertTrue(ids.stream().noneMatch(id -> id.getPath().contains("obelisk") || id.getPath().contains("everlasting")),
                "黑暗方尖碑、「永恒的夏日」唱片不应在池里");
        helper.assertTrue(count(pool, EssentialCraft5.id("magical_teleporter")) == 2, "魔力传送器 ×2");
        helper.assertTrue(count(pool, EssentialCraft5.id("ultra_heat_generator")) == 1, "终极热力产能器 ×1");
        helper.assertTrue(count(pool, EssentialCraft5.id("void_crystal_pillar")) == 3, "虚空水晶柱 ×3");
        Set<ResourceLocation> expectedSouls = Set.of(ResourceLocation.withDefaultNamespace("villager"),
                ResourceLocation.withDefaultNamespace("enderman"), EssentialCraft5.id("wind_mage"), EssentialCraft5.id("nether_imp"),
                EssentialCraft5.id("hoanna_scavenger"));
        helper.assertTrue(souls.equals(expectedSouls), "5 条灵魂应是村民、末影人、风法师、炼狱小鬼、遗民拾荒者，实际 " + souls);
        helper.assertTrue(pool.stream().anyMatch(e -> e.isSoul() && EssentialCraft5.id("nether_imp").equals(e.soul()) && e.min() == 2 && e.max() == 2),
                "炼狱小鬼灵魂固定要 2 个");
        for (ResourceLocation soul : souls) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(soul);
            helper.assertTrue(type != null && type.is(DemonTrades.SOUL_CAPTURABLE), soul + " 应在 essentialcraft5:soul_capturable 里（灵魂刻刀收得到）");
        }
        for (String gun : new String[]{"pistol", "rifle", "sniper", "gatling"}) {
            helper.assertTrue(pool.stream().anyMatch(e -> EssentialCraft5.id(gun).equals(e.id()) && e.anyDamage()), gun + " 应不看耐久");
        }
        helper.succeed();
    }

    private static int count(List<DemonTrades.Entry> pool, ResourceLocation id) {
        return pool.stream().filter(e -> !e.isSoul() && id.equals(e.id())).mapToInt(DemonTrades.Entry::count).findFirst().orElse(-1);
    }

    /**
     * 解析：没注册的物品 / 实体跳过并警告、数量夹到一组以内、weight 0 不收、remove 跨文件生效、灵魂 min / max 填反照样认；
     * 字段类型写错（数字加引号、entries 写成对象、remove 里 soul 写成数组）只跳那一条 / 那一项、不抛异常（以前整次数据包加载失败）；
     * item 写成被俘灵魂的跳过；权重超过上限按上限算；按权重抽，权重合计超过 int 也抽得出来。
     */
    @GameTest(template = LARGE)
    public static void demonTradePoolParse(GameTestHelper helper) {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(EssentialCraft5.id("a"), JsonParser.parseString("""
                {"entries": [
                  {"item": "minecraft:iron_block", "count": 8, "weight": 3},
                  {"item": "minecraft:tnt", "count": 100},
                  {"item": "essentialcraft5:no_such_item"},
                  {"soul": "minecraft:villager", "min": 9, "max": 2},
                  {"soul": "essentialcraft5:no_such_mob"},
                  {"item": "minecraft:diamond", "weight": 0},
                  {"item": "minecraft:emerald", "count": 3}
                ]}"""));
        files.put(EssentialCraft5.id("b"), JsonParser.parseString("""
                {"remove": ["minecraft:emerald", {"soul": "minecraft:enderman"}],
                 "entries": [{"soul": "minecraft:enderman"}]}"""));
        // 写错类型的：count 加了引号、remove 里 soul 写成数组；item 写成被俘灵魂；权重超过上限。同一文件里写对的金块照常进池
        files.put(EssentialCraft5.id("c"), JsonParser.parseString("""
                {"entries": [
                  {"item": "minecraft:dirt", "count": "8"},
                  {"item": "minecraft:gold_block", "weight": 2000000000},
                  {"item": "essentialcraft5:captured_soul"}
                ],
                 "remove": [{"soul": ["a"]}]}"""));
        // entries 写成对象：整项跳过，文件里别的不受影响（这个文件没有别的）
        files.put(EssentialCraft5.id("d"), JsonParser.parseString("""
                {"entries": {}}"""));
        DemonTrades.Result result;
        try {
            result = DemonTrades.parse(files);
        } catch (RuntimeException e) {
            throw new IllegalStateException("字段类型写错不应让整个解析抛异常：" + e, e);
        }
        List<DemonTrades.Entry> pool = result.entries();
        List<String> warnings = result.warnings();
        DemonTrades.Entry iron = pool.stream().filter(e -> e.item() == Items.IRON_BLOCK).findFirst().orElse(null);
        DemonTrades.Entry tnt = pool.stream().filter(e -> e.item() == Items.TNT).findFirst().orElse(null);
        DemonTrades.Entry villager = pool.stream().filter(DemonTrades.Entry::isSoul).findFirst().orElse(null);
        DemonTrades.Entry gold = pool.stream().filter(e -> e.item() == Items.GOLD_BLOCK).findFirst().orElse(null);
        helper.assertTrue(iron != null && iron.count() == 8 && iron.weight() == 3, "铁块 ×8、权重 3");
        helper.assertTrue(tnt != null && tnt.count() == 64, "TNT 数量应夹到一组（64）");
        helper.assertTrue(villager != null && villager.min() == 2 && villager.max() == 9, "灵魂 min / max 填反应换过来");
        helper.assertTrue(gold != null && gold.weight() == DemonTrades.MAX_WEIGHT, "金块应进池、权重夹到上限，实际 " + gold);
        helper.assertTrue(pool.stream().noneMatch(e -> e.item() == Items.DIRT), "count 写成字符串的泥土应跳过");
        helper.assertTrue(pool.stream().noneMatch(e -> e.item() == ECDemonic.CAPTURED_SOUL.get()), "item 写成被俘灵魂的应跳过");
        helper.assertTrue(pool.size() == 4, "应剩铁块、TNT、村民灵魂、金块 4 条，实际 " + pool);
        helper.assertTrue(warnings.size() >= 3, "没注册的物品、实体和被夹的数量都应有警告，实际 " + warnings);
        helper.assertTrue(hasWarning(warnings, "essentialcraft5:c entries[0]", "count"), "count 类型错应有警告，实际 " + warnings);
        helper.assertTrue(hasWarning(warnings, "essentialcraft5:c entries[1]", "weight"), "权重超过上限应有警告，实际 " + warnings);
        helper.assertTrue(hasWarning(warnings, "essentialcraft5:c entries[2]", "captured_soul"), "被俘灵魂写成 item 应有警告，实际 " + warnings);
        helper.assertTrue(hasWarning(warnings, "essentialcraft5:c remove[0]", "soul"), "remove 里 soul 写成数组应有警告，实际 " + warnings);
        helper.assertTrue(hasWarning(warnings, "essentialcraft5:d", "entries"), "entries 不是数组应有警告，实际 " + warnings);

        List<DemonTrades.Entry> weighted = List.of(DemonTrades.Entry.item(Items.DIAMOND, 1, false, 1),
                DemonTrades.Entry.item(Items.EMERALD, 1, false, 3));
        DemonTrades.setTestOverride(weighted);
        int emeralds = 0;
        try {
            RandomSource random = RandomSource.create(7L * 0x9E3779B97F4A7C15L);
            for (int i = 0; i < 400; i++) {
                DemonWant want = DemonTrades.roll(random);
                if (want != null && want.item() == Items.EMERALD) {
                    emeralds++;
                }
            }
        } finally {
            DemonTrades.setTestOverride(null);
        }
        helper.assertTrue(emeralds > 250 && emeralds < 350, "权重 1 : 3 抽 400 次，绿宝石应约 300 次，实际 " + emeralds);
        // 权重合计超过 int：以前 int 累加溢出成负数，永远抽不出东西（召唤报失败、刷怪蛋恶魔每刻重掷）
        DemonTrades.setTestOverride(List.of(DemonTrades.Entry.item(Items.DIAMOND, 1, false, Integer.MAX_VALUE),
                DemonTrades.Entry.item(Items.EMERALD, 1, false, Integer.MAX_VALUE)));
        try {
            RandomSource random = RandomSource.create(11L * 0x9E3779B97F4A7C15L);
            for (int i = 0; i < 20; i++) {
                helper.assertTrue(DemonTrades.roll(random) != null, "权重合计超过 int 时也应抽得出东西");
            }
        } finally {
            DemonTrades.setTestOverride(null);
        }
        DemonTrades.setTestOverride(List.of());
        try {
            helper.assertTrue(DemonTrades.roll(RandomSource.create(1L)) == null, "空池抽不出东西");
        } finally {
            DemonTrades.setTestOverride(null);
        }
        helper.succeed();
    }

    private static boolean hasWarning(List<String> warnings, String where, String mention) {
        return warnings.stream().anyMatch(w -> w.startsWith(where) && w.contains(mention));
    }

    // ================================================================ 交易

    /** 要 8 个铁块、放 20 个：格子只收 8 个、成交，剩下 12 个还在手上；报酬 3~8 个阿克罗尼特锭进交易者背包；恶魔离开 */
    @GameTest(template = LARGE)
    public static void demonTradeTakesOnlyWhatItWants(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-trader", new Vec3(4.5D, 2D, 5.5D));
        DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), player.getUUID(), ironBlocks(8));
        DemonTradeMenu menu = new DemonTradeMenu(1, player.getInventory(), demon);
        helper.assertTrue(menu.stillValid(player), "召唤者开着界面应有效");
        menu.setCarried(new ItemStack(Items.IRON_BLOCK, 20));
        ECDemon.Config.setTestOverrides(null, 3, 8);
        try {
            menu.clicked(0, 0, ClickType.PICKUP, player);
        } finally {
            ECDemon.Config.setTestOverrides(null, null, null);
        }
        helper.assertTrue(menu.getCarried().is(Items.IRON_BLOCK) && menu.getCarried().getCount() == 12, "多给的 12 个应还在手上，实际 " + menu.getCarried());
        helper.assertTrue(menu.offered().isEmpty(), "成交后格子应清空");
        int reward = player.getInventory().countItem(ECDemonic.ACKRONITE_INGOT.get());
        helper.assertTrue(reward >= 3 && reward <= 8, "报酬应是 3~8 个阿克罗尼特锭，实际 " + reward);
        helper.assertTrue(demon.isRemoved(), "成交后恶魔应离开");
        helper.assertFalse(menu.stillValid(player), "恶魔走了界面应失效");
        menu.removed(player);
        helper.succeed();
    }

    /**
     * Shift 点进正好够数、被点的那一格又是背包里第一个空位：报酬照样到手。搬的过程中那一格先变成 0 个，成交时报酬按「第一个空格」
     * 放进了这一格，以前 quickMoveStack 随后把这一格清空，报酬就没了。两条路都测：投放格是空的（整堆放进空格）、投放格里已有一部分（合并进去）。
     */
    @GameTest(template = LARGE)
    public static void demonShiftTradeKeepsReward(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-shift-pay", new Vec3(4.5D, 2D, 5.5D));
        DemonEntity empty = demon(helper, new Vec3(5.5D, 2D, 5.5D), player.getUUID(), ironBlocks(8));
        DemonEntity partial = demon(helper, new Vec3(6.5D, 2D, 5.5D), player.getUUID(), ironBlocks(8));
        ECDemon.Config.setTestOverrides(null, 3, 8);
        try {
            // 快捷栏第一格（菜单 28 号槽）正好 8 个铁块，身上别的格子都空着，它就是序号最小的空位
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.IRON_BLOCK, 8));
            DemonTradeMenu menu = new DemonTradeMenu(1, player.getInventory(), empty);
            menu.quickMoveStack(player, 28);
            helper.assertTrue(empty.isRemoved(), "Shift 点进正好 8 个应成交");
            int reward = player.getInventory().countItem(ECDemonic.ACKRONITE_INGOT.get());
            helper.assertTrue(reward >= 3 && reward <= 8, "整堆放进空的投放格：报酬应是 3~8 个，实际 " + reward);
            helper.assertTrue(player.getInventory().countItem(Items.IRON_BLOCK) == 0, "8 个铁块应全部交掉");
            menu.removed(player);

            player.getInventory().clearContent();
            DemonTradeMenu mergeMenu = new DemonTradeMenu(2, player.getInventory(), partial);
            mergeMenu.setCarried(new ItemStack(Items.IRON_BLOCK, 5));
            mergeMenu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(mergeMenu.offered().getCount() == 5 && !partial.isRemoved(), "前提：先放 5 个不成交");
            player.getInventory().setItem(0, new ItemStack(Items.IRON_BLOCK, 3));
            mergeMenu.quickMoveStack(player, 28);
            helper.assertTrue(partial.isRemoved(), "再 Shift 点进 3 个凑够 8 个应成交");
            reward = player.getInventory().countItem(ECDemonic.ACKRONITE_INGOT.get());
            helper.assertTrue(reward >= 3 && reward <= 8, "合并进已有的那一堆：报酬应是 3~8 个，实际 " + reward);
            mergeMenu.removed(player);
        } finally {
            ECDemon.Config.setTestOverrides(null, null, null);
            empty.discard();
            partial.discard();
        }
        helper.succeed();
    }

    /** 背包满了：报酬掉在交易者脚下 */
    @GameTest(template = LARGE)
    public static void demonTradeRewardDropsWhenFull(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-trader", new Vec3(4.5D, 2D, 5.5D));
        DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), player.getUUID(), ironBlocks(1));
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            player.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        }
        DemonTradeMenu menu = new DemonTradeMenu(1, player.getInventory(), demon);
        menu.setCarried(new ItemStack(Items.IRON_BLOCK, 1));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(demon.isRemoved(), "应成交");
        helper.assertTrue(player.getInventory().countItem(ECDemonic.ACKRONITE_INGOT.get()) == 0, "背包满了不该塞进去");
        helper.assertFalse(items(helper, player.position(), 2D, ECDemonic.ACKRONITE_INGOT.get()).isEmpty(), "报酬应掉在交易者脚下");
        helper.succeed();
    }

    /** 匹配：改名 / 带 NBT 照收；会损坏的要满耐久（风之镐耐久不满不收）；玩家头颅带不带主人都收、任意地图都收、枪不看耐久；灵魂只认实体，改名的照收 */
    @GameTest(template = LARGE)
    public static void demonTradeMatching(GameTestHelper helper) {
        ItemStack renamed = new ItemStack(Items.IRON_BLOCK);
        renamed.setHoverName(Component.literal("Shiny"));
        helper.assertTrue(ironBlocks(8).matches(renamed), "改过名的铁块照收");
        helper.assertFalse(ironBlocks(8).matches(new ItemStack(Items.GOLD_BLOCK)), "金块不收");

        Item pick = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("wind_elemental_pickaxe"));
        ItemStack fresh = new ItemStack(pick);
        helper.assertTrue(fresh.isDamageableItem(), "前提：风之镐会损坏");
        DemonWant wantPick = DemonWant.item(pick, 1, false);
        helper.assertTrue(wantPick.needsFullDurability(), "风之镐需求应注明要满耐久");
        ItemStack worn = fresh.copy();
        worn.setDamageValue(1);
        helper.assertFalse(wantPick.matches(worn), "耐久不满的风之镐不收");
        ItemStack fancy = fresh.copy();
        fancy.enchant(Enchantments.BLOCK_EFFICIENCY, 2);
        fancy.setHoverName(Component.literal("Pick"));
        helper.assertTrue(wantPick.matches(fancy), "满耐久的风之镐带附魔、改名照收");

        DemonWant head = DemonWant.item(Items.PLAYER_HEAD, 1, false);
        ItemStack owned = new ItemStack(Items.PLAYER_HEAD);
        owned.getOrCreateTag().putString("SkullOwner", "Steve");
        helper.assertTrue(head.matches(owned) && head.matches(new ItemStack(Items.PLAYER_HEAD)), "玩家头颅带不带主人都收");
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.getOrCreateTag().putInt("map", 42);
        helper.assertTrue(DemonWant.item(Items.FILLED_MAP, 1, false).matches(map), "任意已填充地图都收");

        Item pistol = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("pistol"));
        ItemStack gun = new ItemStack(pistol);
        gun.getOrCreateTag().putString("custom", "parts");
        if (gun.isDamageableItem()) {
            gun.setDamageValue(gun.getMaxDamage() / 2);
        }
        helper.assertTrue(DemonWant.item(pistol, 1, true).matches(gun), "枪（any_damage）不看耐久和组装");

        DemonWant villagerSoul = DemonWant.soul(ResourceLocation.withDefaultNamespace("villager"), 3);
        ItemStack enderman = CapturedSoulItem.of(EntityType.ENDERMAN, 3);
        ItemStack villager = CapturedSoulItem.of(EntityType.VILLAGER, 1);
        villager.setHoverName(Component.literal("Soul"));
        helper.assertFalse(villagerSoul.matches(enderman), "要村民灵魂时末影人灵魂不收");
        helper.assertTrue(villagerSoul.matches(villager), "改过名的村民灵魂照收");
        helper.assertFalse(villagerSoul.matches(new ItemStack(ECDemonic.CAPTURED_SOUL.get())), "没记实体的灵魂不收");

        // 界面的格子也按同一规则：放不进不收的东西
        ServerPlayer player = player(helper, "ec5-trader", new Vec3(4.5D, 2D, 5.5D));
        DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), player.getUUID(), villagerSoul);
        DemonTradeMenu menu = new DemonTradeMenu(1, player.getInventory(), demon);
        helper.assertFalse(menu.getSlot(0).mayPlace(enderman), "界面格子不收末影人灵魂");
        helper.assertTrue(menu.getSlot(0).mayPlace(villager), "界面格子收村民灵魂");
        helper.assertTrue(menu.getSlot(0).getMaxStackSize(villager) == 3, "格子上限 = 要的数量");
        demon.discard();
        helper.succeed();
    }

    /** 资格（D2 = A）：召唤者能开、别的真玩家右键不开、创造模式玩家能开、刷怪蛋的谁都能开、假玩家不开 */
    @GameTest(template = LARGE)
    public static void demonTradeAccess(GameTestHelper helper) {
        ServerPlayer summoner = player(helper, "ec5-summoner", new Vec3(4.5D, 2D, 5.5D));
        ServerPlayer stranger = player(helper, "ec5-stranger", new Vec3(6.5D, 2D, 5.5D));
        ServerPlayer creative = player(helper, "ec5-creative", new Vec3(5.5D, 2D, 6.5D));
        creative.setGameMode(GameType.CREATIVE);
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), summoner.getUUID(), ironBlocks(8));
        DemonEntity eggDemon = demon(helper, new Vec3(5.5D, 2D, 3.5D), null, ironBlocks(8));

        helper.assertTrue(demon.canTrade(summoner) && demon.canTrade(creative), "召唤者、创造模式玩家能交易");
        helper.assertFalse(demon.canTrade(stranger), "别的玩家不能交易");
        helper.assertFalse(demon.canTrade(fake), "假玩家不能交易");
        helper.assertTrue(eggDemon.canTrade(stranger) && !eggDemon.canTrade(fake), "没有召唤者的恶魔真玩家都能交易，假玩家不行");

        InteractionResult refused = demon.interact(stranger, InteractionHand.MAIN_HAND);
        helper.assertTrue(refused == InteractionResult.CONSUME && stranger.containerMenu == stranger.inventoryMenu, "别的玩家右键不开界面");
        helper.assertTrue(demon.interact(fake, InteractionHand.MAIN_HAND) == InteractionResult.PASS && fake.containerMenu == fake.inventoryMenu,
                "假玩家不开界面");
        InteractionResult opened = demon.interact(summoner, InteractionHand.MAIN_HAND);
        helper.assertTrue(opened == InteractionResult.CONSUME && summoner.containerMenu instanceof DemonTradeMenu, "召唤者右键打开交易界面");
        summoner.closeContainer();
        helper.assertTrue(eggDemon.interact(stranger, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME
                && stranger.containerMenu instanceof DemonTradeMenu, "刷怪蛋出来的恶魔谁都能开");
        stranger.closeContainer();
        demon.discard();
        eggDemon.discard();
        helper.succeed();
    }

    /** 界面失效退回：创造模式玩家开着界面切回生存就失效、放了一半的退回；离太远失效；交易中途恶魔离开，格子不再收东西（Shift 点击也不成交）、东西退回不丢 */
    @GameTest(template = LARGE)
    public static void demonTradeMenuReturnsItems(GameTestHelper helper) {
        ServerPlayer summoner = player(helper, "ec5-summoner", new Vec3(4.5D, 2D, 5.5D));
        ServerPlayer creative = player(helper, "ec5-creative", new Vec3(6.5D, 2D, 5.5D));
        creative.setGameMode(GameType.CREATIVE);
        DemonEntity demon = demon(helper, new Vec3(5.5D, 2D, 5.5D), summoner.getUUID(), ironBlocks(8));

        DemonTradeMenu creativeMenu = new DemonTradeMenu(1, creative.getInventory(), demon);
        creativeMenu.setCarried(new ItemStack(Items.IRON_BLOCK, 4));
        creativeMenu.clicked(0, 0, ClickType.PICKUP, creative);
        helper.assertTrue(creativeMenu.offered().getCount() == 4 && !demon.isRemoved(), "放一半不成交");
        helper.assertTrue(creativeMenu.stillValid(creative), "创造模式玩家开着界面有效");
        creative.setGameMode(GameType.SURVIVAL);
        helper.assertFalse(creativeMenu.stillValid(creative), "切回生存后界面应失效");
        creativeMenu.removed(creative);
        helper.assertTrue(creative.getInventory().countItem(Items.IRON_BLOCK) == 4, "关界面时放进去的 4 个铁块应退回");

        DemonTradeMenu menu = new DemonTradeMenu(2, summoner.getInventory(), demon);
        helper.assertTrue(menu.stillValid(summoner), "召唤者在 8 格内有效");
        Vec3 far = helper.absoluteVec(new Vec3(5.5D, 2D, 5.5D)).add(9D, 0D, 0D);
        summoner.moveTo(far.x, far.y, far.z);
        helper.assertFalse(menu.stillValid(summoner), "离恶魔超过 8 格应失效");
        Vec3 near = helper.absoluteVec(new Vec3(4.5D, 2D, 5.5D));
        summoner.moveTo(near.x, near.y, near.z);
        menu.setCarried(new ItemStack(Items.IRON_BLOCK, 5));
        menu.clicked(0, 0, ClickType.PICKUP, summoner);
        helper.assertTrue(menu.offered().getCount() == 5, "格子里应有 5 个");
        demon.depart();
        helper.assertFalse(menu.stillValid(summoner), "恶魔离开后界面应失效");
        // 恶魔离开后格子不再收东西：Shift 点击背包里的一组铁块不会凑够 8 个成交
        helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_BLOCK)), "恶魔离开后投放格不应再收东西");
        summoner.getInventory().setItem(9, new ItemStack(Items.IRON_BLOCK, 64));
        int bagSlot = 1; // 菜单第 1 格 = 背包第 9 格（主背包第一格）
        helper.assertTrue(menu.getSlot(bagSlot).getItem().getCount() == 64, "前提：背包那格是 64 个铁块");
        menu.quickMoveStack(summoner, bagSlot);
        helper.assertTrue(menu.offered().getCount() == 5, "恶魔离开后 Shift 点击不应再往格子里放，实际 " + menu.offered());
        helper.assertTrue(menu.getSlot(bagSlot).getItem().getCount() == 64, "背包里的铁块应原样不动");
        helper.assertTrue(summoner.getInventory().countItem(ECDemonic.ACKRONITE_INGOT.get()) == 0, "恶魔离开后不应成交");
        menu.removed(summoner);
        helper.assertTrue(summoner.getInventory().countItem(Items.IRON_BLOCK) == 5 + 64, "恶魔中途离开，放进去的东西应退回");
        helper.assertTrue(summoner.getInventory().countItem(ECDemonic.ACKRONITE_INGOT.get()) == 0, "没成交不给报酬");
        helper.succeed();
    }

    // ================================================================ 配方与注册

    /** 五芒星配方：FVF / VCV / FVF，原料里是虚空核心（原作矿辞笔误 core_void）；产物是恶魔五芒星 */
    @GameTest(template = LARGE)
    public static void demonPentacleRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Recipe<?> recipe = level.getRecipeManager().byKey(EssentialCraft5.id("demon/demonic_pentacle")).orElse(null);
        helper.assertTrue(recipe != null, "应有恶魔五芒星配方");
        helper.assertTrue(recipe.getResultItem(level.registryAccess()).is(ECDemon.DEMONIC_PENTACLE.get().asItem()), "产物应是恶魔五芒星");
        int voidCores = 0;
        int foci = 0;
        int plates = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.test(new ItemStack(ECVoid.VOID_CORE.get()))) {
                voidCores++;
            }
            if (ingredient.test(new ItemStack(ECVoid.VOID_PLATING.get()))) {
                plates++;
            }
            if (ingredient.test(new ItemStack(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("fire_focus"))))) {
                foci++;
            }
        }
        helper.assertTrue(voidCores == 1 && plates == 4 && foci == 4, "应是虚空核心 1、虚空板 4、火之焦点 4，实际 " + voidCores + " / " + plates + " / " + foci);
        helper.succeed();
    }

    /**
     * 五芒星挡液体（原作 Material.ROCK；ECDemon.DEMONIC_PENTACLE 的 forceSolidOn，同 WindTests.windRuneBlocksFluids）：两个五芒星各用玻璃围一圈，
     * 正上方分别放水源、岩浆源（只能往下流），等过岩浆的流动间隔（主世界 30 刻）两轮，五芒星都还在、格子里没有液体、没冲出掉落物；
     * 发射器 / 水桶走的 canBeReplaced(液体) 也是 false，碰撞箱照旧是空的。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void demonPentacleBlocksFluids(GameTestHelper helper) {
        BlockState state = ECDemon.DEMONIC_PENTACLE.get().defaultBlockState();
        BlockPos waterPentacle = new BlockPos(1, 2, 1);
        BlockPos lavaPentacle = new BlockPos(3, 2, 3);
        helper.assertTrue(state.blocksMotion(), "五芒星应挡液体（blocksMotion）");
        helper.assertTrue(!state.canBeReplaced(Fluids.WATER) && !state.canBeReplaced(Fluids.LAVA), "水桶、发射器倒液体不应能直接替换五芒星");
        helper.assertTrue(state.getCollisionShape(helper.getLevel(), helper.absolutePos(waterPentacle)).isEmpty(), "五芒星仍然没有碰撞箱");

        // 两个 3×3 的小池（石头底、两层玻璃墙），共用 (2, y, 2) 那根角柱
        for (BlockPos pentacle : List.of(waterPentacle, lavaPentacle)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.setBlock(pentacle.offset(dx, -1, dz), Blocks.STONE);
                    if (dx != 0 || dz != 0) {
                        helper.setBlock(pentacle.offset(dx, 0, dz), Blocks.GLASS);
                        helper.setBlock(pentacle.offset(dx, 1, dz), Blocks.GLASS);
                    }
                }
            }
            helper.setBlock(pentacle, ECDemon.DEMONIC_PENTACLE.get());
        }
        helper.setBlock(waterPentacle.above(), Blocks.WATER);
        helper.setBlock(lavaPentacle.above(), Blocks.LAVA);

        helper.runAfterDelay(70, () -> {
            for (BlockPos pentacle : List.of(waterPentacle, lavaPentacle)) {
                helper.assertBlockPresent(ECDemon.DEMONIC_PENTACLE.get(), pentacle);
                helper.assertTrue(helper.getBlockState(pentacle).getFluidState().isEmpty(), pentacle + " 的五芒星格里不应有液体");
            }
            helper.assertItemEntityNotPresent(ECDemon.DEMONIC_PENTACLE.get().asItem(), waterPentacle, 3D);
            // 液体源收掉，别在测试区留岩浆
            helper.setBlock(waterPentacle.above(), Blocks.AIR);
            helper.setBlock(lavaPentacle.above(), Blocks.AIR);
            helper.succeed();
        });
    }

    /** 刷怪蛋生成恶魔、点刷怪笼不改类型；恶魔有属性、不怕火 */
    @GameTest(template = LARGE)
    public static void demonSpawnEgg(GameTestHelper helper) {
        helper.assertTrue(ECDemon.DEMON_SPAWN_EGG.get().getType(null) == ECDemon.DEMON.get(), "刷怪蛋应生成恶魔");
        helper.assertFalse(ECDemon.DEMON_SPAWN_EGG.get().changesSpawner(), "点刷怪笼不改类型");
        DemonEntity demon = ECDemon.DEMON.get().create(helper.getLevel());
        helper.assertTrue(demon != null && demon.fireImmune() && demon.getMaxHealth() == 20F, "恶魔不怕火、20 点生命");
        helper.succeed();
    }

    // ================================================================ 声音

    /**
     * 环境音间隔 240 刻、只用说话声（召唤声留给真召唤）；交易声同一只恶魔 2 秒内只放一次，过了 2 秒再开照常放。
     * 恶魔站在成形的五芒星上（不然第 40 刻守阵检查就走了）。声音按 {@link PlayLevelSoundEvent.AtPosition} 录，只认这只恶魔脚下的。
     */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void demonSounds(GameTestHelper helper) {
        build(helper);
        ServerPlayer summoner = player(helper, "ec5-summoner", new Vec3(4.5D, 2D, 5.5D));
        DemonEntity demon = demon(helper, Vec3.atBottomCenterOf(PENTACLE), summoner.getUUID(), ironBlocks(8));
        helper.assertTrue(demon.getAmbientSoundInterval() == DemonEntity.AMBIENT_SOUND_INTERVAL && DemonEntity.AMBIENT_SOUND_INTERVAL == 240,
                "环境音间隔应是 240 刻，实际 " + demon.getAmbientSoundInterval());
        List<ResourceLocation> ambient = recordSounds(helper, demon, () -> {
            for (int i = 0; i < 40; i++) {
                demon.playAmbientSound();
            }
        });
        ResourceLocation say = ECDemon.SOUND_SAY.get().getLocation();
        helper.assertTrue(ambient.size() == 40 && ambient.stream().allMatch(say::equals), "环境音应全是说话声，实际 " + ambient);

        ResourceLocation trade = ECDemon.SOUND_TRADE.get().getLocation();
        List<ResourceLocation> twice = recordSounds(helper, demon, () -> {
            for (int i = 0; i < 2; i++) {
                demon.interact(summoner, InteractionHand.MAIN_HAND);
                summoner.closeContainer();
            }
        });
        helper.assertTrue(twice.stream().filter(trade::equals).count() == 1, "同一刻开两次界面只放一次交易声，实际 " + twice);
        helper.runAfterDelay(DemonEntity.TRADE_SOUND_COOLDOWN + 1, () -> {
            List<ResourceLocation> later = recordSounds(helper, demon, () -> {
                demon.interact(summoner, InteractionHand.MAIN_HAND);
                summoner.closeContainer();
            });
            demon.discard();
            helper.assertTrue(later.stream().filter(trade::equals).count() == 1, "过了 2 秒再开应照常放交易声，实际 " + later);
            helper.succeed();
        });
    }

    /** 做 action 期间，在 source 脚下放的声音（按顺序） */
    private static List<ResourceLocation> recordSounds(GameTestHelper helper, Entity source, Runnable action) {
        ServerLevel level = helper.getLevel();
        Vec3 at = source.position();
        List<ResourceLocation> heard = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == level && event.getSound() != null && event.getPosition().distanceToSqr(at) < 1.0E-4D) {
                heard.add(event.getSound().get().getLocation());
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayLevelSoundEvent.AtPosition.class, listener);
        try {
            action.run();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
        return heard;
    }
}
