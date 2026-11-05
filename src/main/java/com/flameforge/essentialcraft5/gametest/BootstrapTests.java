package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.MatrixAbsorberBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.player.PlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.UBMRUEvents;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.ritual.GroundRituals;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.UUID;

/**
 * ECBootstrap 模块的自动化测试：地面仪式、UBMRU 计算、矩阵换能器换能、灵魂石绑定。
 * 第三批安全回补（policy §5）：放魔晶簇的元素仪式算在扔东西的人头上——没有扔东西的真玩家不触发、不消耗；
 * 那一格被模拟领地拒绝时（成功、失败两种掷骰）、扔的人不能放方块（冒险模式，审查 RB-6）时都不触发、不消耗。
 * 测试玩家不在世界里，扔东西的人用 check 的 4 参重载直接给。
 * 跑法同 ECGameTests（./gradlew runGameTestServer）。仪式和换能都直接调方法，
 * 因为测试环境里造不出「在线」的玩家（Forge 1.20.1 的 makeMockServerPlayerInLevel 会因为没有网络通道崩掉），
 * 而仪式扫描是以在线玩家为中心的。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BootstrapTests {

    private static final String EMPTY = "empty";
    /** 仪式的中心方块；催化物丢在它正上方 */
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final BlockPos ABOVE = CENTER.above();

    private BootstrapTests() {
    }

    /** 摆仪式台：中心方块，外圈按北、东、南、西的顺序取 ring（不够就重复最后一个） */
    private static void buildAltar(GameTestHelper helper, BlockState center, BlockState... ring) {
        helper.setBlock(CENTER, center);
        int i = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CENTER.relative(direction), ring[Math.min(i++, ring.length - 1)]);
        }
    }

    /** 在中心方块正上方那格放一堆掉落物（静止，所在格就是 ABOVE） */
    private static ItemEntity drop(GameTestHelper helper, Item item, int count) {
        ItemEntity entity = helper.spawnItem(item, 2.5F, 2F, 2.5F);
        entity.setItem(new ItemStack(item, count));
        return entity;
    }

    // ------------------------------------------------------------------
    // 地面仪式
    // ------------------------------------------------------------------

    /** 绿宝石仪式：成功掉灵魂石；失败也消耗；外圈不对时不触发也不消耗；最后一颗用完掉落物消失 */
    @GameTest(template = EMPTY)
    public static void emeraldRitualMakesSoulStone(GameTestHelper helper) {
        buildAltar(helper, Blocks.EMERALD_BLOCK.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState());
        ServerLevel level = helper.getLevel();
        ItemEntity emeralds = drop(helper, Items.EMERALD, 3);

        helper.assertTrue(GroundRituals.check(level, emeralds, () -> 0D), "绿宝石仪式应触发");
        helper.assertTrue(emeralds.getItem().getCount() == 2, "应消耗 1 颗绿宝石，实际剩 " + emeralds.getItem().getCount());
        helper.assertItemEntityCountIs(ECBootstrap.SOUL_STONE.get(), ABOVE, 2D, 1);

        helper.assertTrue(GroundRituals.check(level, emeralds, () -> 0.99D), "掷骰失败也算触发");
        helper.assertTrue(emeralds.getItem().getCount() == 1, "失败也要消耗，实际剩 " + emeralds.getItem().getCount());
        helper.assertItemEntityCountIs(ECBootstrap.SOUL_STONE.get(), ABOVE, 2D, 1);

        // 灵魂土是 1.16 的新方块，原作只认灵魂沙
        helper.setBlock(CENTER.east(), Blocks.SOUL_SOIL);
        helper.assertFalse(GroundRituals.check(level, emeralds, () -> 0D), "外圈有灵魂土时不应触发");
        helper.assertTrue(emeralds.getItem().getCount() == 1, "不触发时不应消耗");

        helper.setBlock(CENTER.east(), Blocks.SOUL_SAND);
        helper.assertTrue(GroundRituals.check(level, emeralds, () -> 0.99D), "外圈改回灵魂沙后应触发");
        helper.assertFalse(emeralds.isAlive(), "最后一颗消耗后掉落物应消失");
        helper.succeed();
    }

    /** 火元素仪式：外圈是流动岩浆也算（原作比较 Material.LAVA），成功时物品所在格变成凝聚火元素 */
    @GameTest(template = EMPTY)
    public static void fireRitualAcceptsFlowingLava(GameTestHelper helper) {
        BlockState flowingLava = Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 2);
        buildAltar(helper, Blocks.NETHERRACK.defaultBlockState(), flowingLava);
        ItemEntity powder = drop(helper, Items.BLAZE_POWDER, 1);
        ServerPlayer thrower = ProtectionFrameworkTests.realPlayer(helper);
        helper.assertTrue(GroundRituals.check(helper.getLevel(), powder, thrower, () -> 0D), "外圈是流动岩浆时应触发");
        helper.assertBlockPresent(ECMaterials.FIRE_DROPS_CLUSTER.get(), ABOVE);
        helper.succeed();
    }

    /** 1.12 → 1.20 的方块对应：地元素要 grass_block；风元素的石英柱、红沙都算 */
    @GameTest(template = EMPTY)
    public static void earthAndAirRitualsUseModernBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer thrower = ProtectionFrameworkTests.realPlayer(helper);
        buildAltar(helper, Blocks.SLIME_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState());
        ItemEntity slime = drop(helper, Items.SLIME_BALL, 1);
        helper.assertTrue(GroundRituals.check(level, slime, thrower, () -> 0D), "四周草方块时应触发地元素仪式");
        helper.assertBlockPresent(ECMaterials.EARTH_DROPS_CLUSTER.get(), ABOVE);

        helper.setBlock(ABOVE, Blocks.AIR);
        buildAltar(helper, Blocks.QUARTZ_PILLAR.defaultBlockState(),
                Blocks.SAND.defaultBlockState(), Blocks.RED_SAND.defaultBlockState());
        ItemEntity gunpowder = drop(helper, Items.GUNPOWDER, 1);
        helper.assertTrue(GroundRituals.check(level, gunpowder, thrower, () -> 0D), "石英柱 + 沙子/红沙时应触发风元素仪式");
        helper.assertBlockPresent(ECMaterials.AIR_DROPS_CLUSTER.get(), ABOVE);
        helper.succeed();
    }

    /**
     * 第三批安全回补：放魔晶簇的仪式没有扔东西的真玩家（发射器吐的、怪物掉的、人已下线；假玩家）就不触发、不消耗；
     * 不改方块的绿宝石仪式照旧不看是谁扔的
     */
    @GameTest(template = EMPTY)
    public static void clusterRitualNeedsThrower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildAltar(helper, Blocks.SLIME_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState());
        ItemEntity slime = drop(helper, Items.SLIME_BALL, 3);
        helper.assertTrue(GroundRituals.throwerOf(level, slime) == null, "helper.spawnItem 生成的掉落物没有扔东西的人");
        helper.assertFalse(GroundRituals.check(level, slime, () -> 0D), "没有扔东西的人不应触发");
        helper.assertFalse(GroundRituals.check(level, slime, () -> 0.99D), "没有扔东西的人，掷骰失败也不应触发");
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        helper.assertFalse(GroundRituals.check(level, slime, fake, () -> 0D), "假玩家扔的不应触发");
        helper.assertTrue(slime.getItem().getCount() == 3, "不触发时不应消耗，实际剩 " + slime.getItem().getCount());
        helper.assertBlockPresent(Blocks.AIR, ABOVE);

        // 不改方块的仪式不看是谁扔的
        helper.setBlock(ABOVE, Blocks.AIR);
        buildAltar(helper, Blocks.EMERALD_BLOCK.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState());
        ItemEntity emerald = drop(helper, Items.EMERALD, 2);
        helper.assertTrue(GroundRituals.check(level, emerald, () -> 0D), "绿宝石仪式不需要扔东西的人");
        helper.succeed();
    }

    /**
     * 第三批安全回补（P1）：魔晶簇以扔东西的人的身份逐格放。那一格被模拟领地拒绝时：掷骰成功 → 放置事件被拒、还原、不消耗；
     * 掷骰失败 → 先问一句那格能不能动，不能动也不消耗。放行以后照常：成功放簇并消耗，失败也消耗（同原作）
     */
    @GameTest(template = EMPTY)
    public static void clusterRitualRespectsClaims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildAltar(helper, Blocks.SLIME_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState());
        ItemEntity slime = drop(helper, Items.SLIME_BALL, 3);
        ServerPlayer thrower = ProtectionFrameworkTests.realPlayer(helper);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(ABOVE)) {
            helper.assertFalse(GroundRituals.check(level, slime, thrower, () -> 0D), "被拒的格子掷骰成功也不应触发");
            helper.assertBlockPresent(Blocks.AIR, ABOVE);
            helper.assertTrue(claims.places(ABOVE) == 1, "应以扔东西的人的身份发一次放置事件，实际 " + claims.places(ABOVE));
            helper.assertFalse(GroundRituals.check(level, slime, thrower, () -> 0.99D), "被拒的格子掷骰失败也不应触发");
            helper.assertTrue(claims.probes(ABOVE) == 1, "掷骰失败时应探测一次那一格");
            helper.assertTrue(slime.getItem().getCount() == 3, "被拒时不应消耗，实际剩 " + slime.getItem().getCount());

            claims.allow(ABOVE);
            ECProtection.forget(thrower.getUUID());
            helper.assertTrue(GroundRituals.check(level, slime, thrower, () -> 0.99D), "放行后掷骰失败也算触发");
            helper.assertTrue(slime.getItem().getCount() == 2, "放行后失败也要消耗（同原作），实际剩 " + slime.getItem().getCount());
            helper.assertBlockPresent(Blocks.AIR, ABOVE);
            helper.assertTrue(GroundRituals.check(level, slime, thrower, () -> 0D), "放行后掷骰成功应触发");
            helper.assertBlockPresent(ECMaterials.EARTH_DROPS_CLUSTER.get(), ABOVE);
            helper.assertTrue(slime.getItem().getCount() == 1, "成功应消耗 1 个，实际剩 " + slime.getItem().getCount());
        }
        helper.succeed();
    }

    /**
     * 第三批审查 RB-6：冒险模式（不能放方块，{@code mayBuild} 为 false）的玩家丢催化物不放魔晶簇，掷骰成功、失败都不触发、不消耗；
     * 能放方块以后照常。游戏模式直接改能力位（GameType.ADVENTURE 同样只是把 mayBuild 置 false），免得给测试玩家发模式切换包
     */
    @GameTest(template = EMPTY)
    public static void clusterRitualNeedsMayBuild(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildAltar(helper, Blocks.SLIME_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState());
        ItemEntity slime = drop(helper, Items.SLIME_BALL, 2);
        ServerPlayer thrower = ProtectionFrameworkTests.realPlayer(helper);
        thrower.getAbilities().mayBuild = false;
        helper.assertFalse(GroundRituals.check(level, slime, thrower, () -> 0D), "不能放方块的玩家掷骰成功也不应触发");
        helper.assertFalse(GroundRituals.check(level, slime, thrower, () -> 0.99D), "不能放方块的玩家掷骰失败也不应触发");
        helper.assertTrue(slime.getItem().getCount() == 2, "不触发时不应消耗，实际剩 " + slime.getItem().getCount());
        helper.assertBlockPresent(Blocks.AIR, ABOVE);

        thrower.getAbilities().mayBuild = true;
        helper.assertTrue(GroundRituals.check(level, slime, thrower, () -> 0D), "能放方块以后应触发");
        helper.assertBlockPresent(ECMaterials.EARTH_DROPS_CLUSTER.get(), ABOVE);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // UBMRU
    // ------------------------------------------------------------------

    /** 杀怪所得按基础最大生命算（加了 +180 生命上限的僵尸仍按 20 算）：(20 ± 10) × 20，平均约 400 */
    @GameTest(template = EMPTY)
    public static void killRewardUsesBaseMaxHealth(GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "造不出僵尸");
        zombie.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(
                new AttributeModifier(UUID.randomUUID(), "gametest", 180D, AttributeModifier.Operation.ADDITION));
        helper.assertTrue(zombie.getMaxHealth() == 200F, "加成后最大生命应为 200，实际 " + zombie.getMaxHealth());

        RandomSource random = RandomSource.create(20260924L);
        long sum = 0;
        int trials = 1000;
        for (int i = 0; i < trials; i++) {
            int reward = UBMRUEvents.killReward(zombie, random);
            helper.assertTrue(reward >= 200 && reward < 600, "单次所得应在 [200, 600)，实际 " + reward);
            sum += reward;
        }
        double mean = (double) sum / trials;
        helper.assertTrue(mean > 380D && mean < 420D, "平均所得应约为 400，实际 " + mean);
        helper.succeed();
    }

    /** 走真实的死亡事件：玩家打死僵尸拿到 UBMRU */
    @GameTest(template = EMPTY)
    public static void killingMobGrantsUBMRU(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerData data = PlayerDataEvents.get(player);
        helper.assertTrue(data != null, "模拟玩家应挂着玩家数据");
        // 图谱里先记上僵尸：这里只看普通击杀的数，首杀多给的那份另见 MatrixFrameworkTests
        data.addMatrixCodex("minecraft:zombie");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ABOVE);
        zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1000F);
        helper.assertFalse(zombie.isAlive(), "僵尸应被打死");
        helper.assertTrue(data.getUBMRU() >= 200 && data.getUBMRU() < 600, "应得到 [200, 600) UBMRU，实际 " + data.getUBMRU());
        helper.succeed();
    }

    /** 杀玩家拿走对方全部；加法不溢出；玩家死亡（不论被谁杀）清零 */
    @GameTest(template = EMPTY)
    public static void pvpTakesAllAndDeathResets(GameTestHelper helper) {
        Player killer = helper.makeMockPlayer();
        Player victim = helper.makeMockPlayer();
        IPlayerData killerData = PlayerDataEvents.get(killer);
        IPlayerData victimData = PlayerDataEvents.get(victim);
        helper.assertTrue(killerData != null && victimData != null, "模拟玩家应挂着玩家数据");
        DamageSources sources = helper.getLevel().damageSources();

        killerData.setUBMRU(50);
        victimData.setUBMRU(1000);
        UBMRUEvents.handleDeath(victim, sources.playerAttack(killer));
        helper.assertTrue(killerData.getUBMRU() == 1050, "击杀者应拿走全部，实际 " + killerData.getUBMRU());
        helper.assertTrue(victimData.getUBMRU() == 0, "被杀者应清零，实际 " + victimData.getUBMRU());

        killerData.setUBMRU(Integer.MAX_VALUE - 10);
        victimData.setUBMRU(1000);
        UBMRUEvents.handleDeath(victim, sources.playerAttack(killer));
        helper.assertTrue(killerData.getUBMRU() == Integer.MAX_VALUE, "加法应夹到 int 上限，实际 " + killerData.getUBMRU());

        UBMRUEvents.handleDeath(killer, sources.generic());
        helper.assertTrue(killerData.getUBMRU() == 0, "玩家不是被玩家杀死也应清零，实际 " + killerData.getUBMRU());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 矩阵换能器、灵魂石
    // ------------------------------------------------------------------

    /** 每次扣 ubmru_usage（默认 10）UBMRU、产 mru_generated（默认 1）MRU；不够一次不换；满了不换也不扣 */
    @GameTest(template = EMPTY)
    public static void matrixAbsorberConvertsUBMRU(GameTestHelper helper) {
        helper.setBlock(CENTER, ECBootstrap.MATRIX_ABSORBER.get());
        MatrixAbsorberBlockEntity be = (MatrixAbsorberBlockEntity) helper.getBlockEntity(CENTER);
        int usage = ECConfig.get(MatrixAbsorberBlockEntity.Config.ubmruUsage);
        int generated = ECConfig.get(MatrixAbsorberBlockEntity.Config.mruGenerated);
        helper.assertTrue(usage > 1, "配置 ubmru_usage 应大于 1，本测试按默认配置写");

        PlayerData owner = new PlayerData();
        owner.setUBMRU(usage * 10 + usage / 2);
        for (int i = 0; i < 10; i++) {
            helper.assertTrue(be.absorb(owner), "第 " + (i + 1) + " 次应换成");
        }
        helper.assertFalse(be.absorb(owner), "剩下的不够一次时不应再换");
        helper.assertTrue(be.getMRUStorage().getMRU() == generated * 10, "应产出 " + generated * 10 + " MRU，实际 " + be.getMRUStorage().getMRU());
        helper.assertTrue(owner.getUBMRU() == usage / 2, "应剩 " + usage / 2 + " UBMRU，实际 " + owner.getUBMRU());

        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        owner.setUBMRU(1000);
        helper.assertFalse(be.absorb(owner), "满了不应再换");
        helper.assertTrue(owner.getUBMRU() == 1000, "满了不应扣 UBMRU，实际 " + owner.getUBMRU());
        helper.succeed();
    }

    /** 灵魂石主人不在线时不产出；槽位只收灵魂石，漏斗能把灵魂石取出来（原作 getOutputSlots = {0}） */
    @GameTest(template = EMPTY)
    public static void matrixAbsorberNeedsOnlineOwner(GameTestHelper helper) {
        helper.setBlock(CENTER, ECBootstrap.MATRIX_ABSORBER.get());
        MatrixAbsorberBlockEntity be = (MatrixAbsorberBlockEntity) helper.getBlockEntity(CENTER);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);

        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.DIAMOND), false).getCount() == 1, "槽位不应接受灵魂石以外的物品");
        ItemStack stone = new ItemStack(ECBootstrap.SOUL_STONE.get());
        SoulStoneItem.bind(stone, UUID.randomUUID(), "offline-player");
        helper.assertTrue(handler.insertItem(0, stone, false).isEmpty(), "槽位应接受灵魂石");

        helper.runAtTickTime(40, () -> {
            helper.assertTrue(be.getMRUStorage().getMRU() == 0, "主人不在线时不应产出，实际 " + be.getMRUStorage().getMRU());
            helper.assertTrue(handler.extractItem(0, 1, false).is(ECBootstrap.SOUL_STONE.get()), "漏斗应能取出灵魂石");
            helper.succeed();
        });
    }

    /** 右键绑定到自己（记 UUID 和名字），潜行右键解绑且不留空 NBT */
    @GameTest(template = EMPTY)
    public static void soulStoneBindsAndUnbinds(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack stone = new ItemStack(ECBootstrap.SOUL_STONE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        ServerLevel level = helper.getLevel();

        stone.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getUUID().equals(SoulStoneItem.getOwner(stone)), "右键应绑定到自己");
        helper.assertTrue(player.getGameProfile().getName().equals(SoulStoneItem.getOwnerName(stone)), "应记下玩家名");

        player.setShiftKeyDown(true);
        stone.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(SoulStoneItem.getOwner(stone) == null, "潜行右键应解绑");
        helper.assertFalse(stone.hasTag(), "解绑后不应留下空 NBT");
        helper.succeed();
    }
}
