package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.command.CorruptionCommands;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.corruption.Affliction;
import com.flameforge.essentialcraft5.corruption.Afflictions;
import com.flameforge.essentialcraft5.corruption.CorruptionBehaviors;
import com.flameforge.essentialcraft5.corruption.MRUCorruption;
import com.flameforge.essentialcraft5.corruption.CorruptionGate;
import com.flameforge.essentialcraft5.corruption.CorruptionTicker;
import com.flameforge.essentialcraft5.corruption.PresenceClaimMemory;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.mru.LeakFeedRecord;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECMRUCU;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 腐化效果模块（ECCorruption，batch3.md §3.6）的 GameTest：抽取（扣成本、矩阵类开关）、配置关掉的不发作、指令增删后照常发作、手册打勾；
 * #13 爆炸不改方块、不伤盔甲架 / 展示框 / 掉落物、伤旁边的牛（记在本人头上）也伤本人；#15 闪电不进世界、不改铜块、不点火、只伤本人；
 * #8 只打乱主物品栏；#9 只扔主手；#10 走 connection.handleUseItem（和玩家自己右键同一段代码）；旁观者、死人不抽取；条件与月相；心智悖论。
 * <p>
 * 玩家用 {@link ProtectionFrameworkTests#realPlayer}（真 ServerPlayer）。要它挨打的测试先用 {@link #victim} 跑掉 60 tick 的出生无敌、把生命上限调到 200，免得被打死。
 * 配置只用 {@link CorruptionEffects#setTestOverrides} / {@link CorruptionEffects#setTestSafetyOverrides}（不许 ConfigValue.set），测完在 finally 里还原。
 * <p>
 * 第十批「不毁号」的几道闸：开过五芒星才抽（第一次打开 0 档就什么类别都抽得到）、潜伏、抽中后的冷却、损伤上限（含每秒夹回）、#8 / #9 / #10 / #13 的温和版、
 * 聚集体与泄漏照领地走、打开五芒星记档位。测老行为（关掉温和版）的几条显式传 gentle = false；要抽到效果的先把档位设成 0 以上。
 * <p>
 * 加强（2026-10-16）：抽取门槛 = 成本 × 3、发作概率 × 2、扣血 × 1.5（MRU 腐化状态不跟）。按原作比例核对扣血、概率的几条先用 {@link #atBaseStrength} 把三个倍率压成 1，
 * 倍率本身另有 {@link #corruptionDrawThresholdMultiplier}、{@link #corruptionEffectStrengthMultipliers} 守着。
 * <p>
 * 领地那几关每问一次就是一次破坏事件（混合端上插件当成真挖），所以另外数探测次数（{@code Claims.probes}、{@code BreakCounter}）：
 * 「MRU 腐化」只叠给漏的那个人、不探测；泄漏往哪团汇的结论记在机器上（存盘、主人下线照认、诱饵跳过、不反复新建）；
 * 聚集体伤人的结论按人记 5 分钟；#13 被拦下时不掀人、「MRU 腐化」被别的模组加成也打不死人；提示跟着配置走。
 * 往世界里结算泄漏的几条各占一个批次（结算是整个维度一起算的）。
 * <p>
 * 扣血按最大生命的比例（2026-10-13 起）：最大生命 200 / 1000 的玩家各效果按比例扣、月相、配置覆盖（{@link CorruptionEffects#setTestHealthCost}）、
 * #13 本人那一下（按难度缩放、温和版封顶一半）、「MRU 腐化」按比例且照旧打不死人。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CorruptionTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final float VICTIM_HEALTH = 200F;

    private CorruptionTests() {
    }

    // ---------------------------------------------------------------- 工具

    private static IPlayerData data(GameTestHelper helper, ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            helper.fail("玩家没有 EC 数据");
        }
        return data;
    }

    private static CorruptionEffects.Effect effect(String id) {
        CorruptionEffects.Effect effect = CorruptionEffects.byId(id);
        if (effect == null) {
            throw new IllegalStateException("没有腐化效果 " + id);
        }
        return effect;
    }

    /**
     * 要挨打的真玩家：刚 new 出来的 ServerPlayer 有 60 tick 出生无敌（spawnInvulnerableTime，只在 ServerPlayer.tick 里减），
     * 先空跑 61 次 tick；生命上限调到 200（腐化扣血按最大生命的比例，爆炸本人那一下普通难度 60%、困难 90%，打不死）。脚站在 relativeFeet。
     */
    private static ServerPlayer victim(GameTestHelper helper, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH);
        player.setHealth(VICTIM_HEALTH);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static CommandSourceStack source(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .withPermission(ECCommands.PERMISSION_LEVEL)
                .withSuppressedOutput();
    }

    private static boolean advancementDone(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** 把抽取门槛、发作概率、扣血三个倍率压成 1（按原作的数值核对的测试用），测完恢复；方法体里的 helper.succeed() 照常调 */
    private static void atBaseStrength(GameTestHelper helper, Consumer<GameTestHelper> body) {
        CorruptionEffects.setTestStrength(1D, 1D, 1D);
        try {
            body.accept(helper);
        } finally {
            CorruptionEffects.setTestStrength(null, null, null);
        }
    }

    /** 每次 nextFloat 都是 0：所有「每秒 p 的几率」都判中（原作 {@code nextFloat() <= p}） */
    private static RandomSource alwaysHits() {
        return new FixedFloatRandom(0F);
    }

    /** nextFloat 总是返回同一个数（掷骰判 {@code nextFloat() <= p}，p 比它大才中）；别的方法都返回 0 */
    private static final class FixedFloatRandom implements RandomSource {
        private final float value;

        FixedFloatRandom(float value) {
            this.value = value;
        }

        @Override
        public RandomSource fork() {
            return this;
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            return new SingleThreadedRandomSource(0L).forkPositional();
        }

        @Override
        public void setSeed(long seed) {
        }

        @Override
        public int nextInt() {
            return 0;
        }

        @Override
        public int nextInt(int bound) {
            return 0;
        }

        @Override
        public long nextLong() {
            return 0L;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }

        @Override
        public float nextFloat() {
            return value;
        }

        @Override
        public double nextDouble() {
            return 0D;
        }

        @Override
        public double nextGaussian() {
            return 0D;
        }
    }

    // ---------------------------------------------------------------- 抽取

    /**
     * 抽取（门槛倍率压成 1，按原作核对）：概率 min(1, (损伤 + 1) / 36000)；损伤 ≥ 35999 必定判中；抽中扣掉成本、加进列表；
     * 矩阵类关着时不抽；只够 6000 时只会是身体类；不够任何成本时抽不到、损伤不动
     */
    @GameTest(template = EMPTY)
    public static void corruptionDrawStep(GameTestHelper helper) {
        helper.assertTrue(Math.abs(CorruptionEffects.drawChance(0) - 1D / 36000D) < 1.0E-12D, "损伤 0 → 1/36000");
        helper.assertTrue(CorruptionEffects.drawChance(36000) == 1D, "损伤 36000 → 必定");

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        data.setPentacleTier(3);   // 开过五芒星才抽：这条测的是原作的抽法
        RandomSource random = RandomSource.create(20260925L);
        try {
            CorruptionEffects.setTestStrength(1D, 1D, 1D);
            CorruptionEffects.setTestOverrides(false, List.of());

            data.setMatrixDamage(1999);
            for (int i = 0; i < 50; i++) {
                helper.assertTrue(CorruptionTicker.draw(player, data, random) == null, "损伤不够任何成本时抽不到");
            }
            helper.assertTrue(data.getMatrixDamage() == 1999 && data.getCorruptionEffects().isEmpty(), "抽不到时损伤、列表都不动");

            for (int i = 0; i < 60; i++) {
                data.clearCorruptionEffects();
                data.setMatrixDamage(40000);
                CorruptionEffects.Effect drawn = CorruptionTicker.draw(player, data, random);
                helper.assertTrue(drawn != null, "损伤 4 万时必定抽到");
                helper.assertTrue(drawn.category() != CorruptionEffects.Category.MATRIX, "矩阵类关着时不应抽到 " + drawn.id());
                helper.assertTrue(data.getMatrixDamage() == 40000 - drawn.cost(), "应扣掉成本 " + drawn.cost() + "，实际剩 " + data.getMatrixDamage());
                helper.assertTrue(data.getCorruptionEffects().equals(List.of(drawn.id())), "抽到的应加进列表");
            }

            data.clearCorruptionEffects();
            data.setMatrixDamage(6000);
            int drawnAt6000 = 0;
            for (int i = 0; i < 2000 && data.getCorruptionEffects().isEmpty(); i++) {
                CorruptionEffects.Effect drawn = CorruptionTicker.draw(player, data, random);
                if (drawn != null) {
                    drawnAt6000++;
                    helper.assertTrue(drawn.category() == CorruptionEffects.Category.BODY, "6000 时只有身体类，实际 " + drawn.id());
                    helper.assertTrue(data.getMatrixDamage() == 0, "6000 − 6000 = 0");
                }
            }
            helper.assertTrue(drawnAt6000 == 1, "6000 时（约 1/6 的几率）2000 次内应抽到一次，实际 " + drawnAt6000);
        } finally {
            CorruptionEffects.setTestOverrides(null, null);
            CorruptionEffects.setTestStrength(null, null, null);
        }
        helper.succeed();
    }

    /**
     * 旁观者、死人不参与抽取（第三批审查 COR-2：以前只在发作那一步查，旁观模式的管理员身上有损伤会被抽到效果）。
     * 走真的玩家 tick 事件（{@link CorruptionTicker#onPlayerTick}），tickCount 放在抽取那一刻；损伤 4 万时抽取必定判中。
     * 依赖总开关 effects_enabled 是默认的开着（和本类别的测试一样；总开关没有测试覆盖值）。
     */
    @GameTest(template = EMPTY)
    public static void corruptionSpectatorsAndDeadDoNotDraw(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        TickEvent.PlayerTickEvent tick = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player);
        data.setPentacleTier(3);
        try {
            CorruptionEffects.setTestOverrides(false, List.of());
            data.setMatrixDamage(40000);
            player.tickCount = CorruptionEffects.DRAW_INTERVAL;

            player.setGameMode(GameType.SPECTATOR);
            CorruptionTicker.onPlayerTick(tick);
            helper.assertTrue(data.getCorruptionEffects().isEmpty() && data.getMatrixDamage() == 40000,
                    "旁观者不应抽到效果，实际 " + data.getCorruptionEffects() + " / " + data.getMatrixDamage());

            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(0F);
            CorruptionTicker.onPlayerTick(tick);
            helper.assertTrue(data.getCorruptionEffects().isEmpty() && data.getMatrixDamage() == 40000,
                    "死人不应抽到效果，实际 " + data.getCorruptionEffects() + " / " + data.getMatrixDamage());

            player.setHealth(player.getMaxHealth());
            CorruptionTicker.onPlayerTick(tick);
            helper.assertTrue(data.getCorruptionEffects().size() == 1 && data.getMatrixDamage() < 40000,
                    "活着的生存玩家照常抽，实际 " + data.getCorruptionEffects() + " / " + data.getMatrixDamage());
        } finally {
            CorruptionEffects.setTestOverrides(null, null);
        }
        helper.succeed();
    }

    /** 矩阵类开关、disabled_effects 关掉的效果留在列表里但不发作；重新打开后照常发作 */
    @GameTest(template = EMPTY)
    public static void corruptionSwitchesStopEffects(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        data.setPentacleTier(3);   // 不然两个都潜伏（另有 corruptionDormantEffects 测）
        data.addCorruptionEffect("matrix_16");
        data.addCorruptionEffect("body_4");
        data.setMatrixDamage(100);
        try {
            CorruptionEffects.setTestOverrides(false, List.of("body_4"));
            player.fallDistance = 2F;
            helper.assertTrue(CorruptionTicker.runEffects(player, data, alwaysHits()) == 0, "矩阵类关着、body_4 被单独关掉：一个都不发作");
            helper.assertTrue(data.getMatrixDamage() == 100 && player.fallDistance == 2F, "关着的不应有后果");
            helper.assertTrue(data.getCorruptionEffects().size() == 2, "关着的效果留在列表里");

            CorruptionEffects.setTestOverrides(true, List.of());
            helper.assertTrue(CorruptionTicker.runEffects(player, data, alwaysHits()) == 2, "都打开后两个都发作");
            helper.assertTrue(data.getMatrixDamage() == 101, "matrix_16：矩阵损伤 +1，实际 " + data.getMatrixDamage());
            helper.assertTrue(player.fallDistance == 3F, "body_4：下落中摔落距离 +1，实际 " + player.fallDistance);
        } finally {
            CorruptionEffects.setTestOverrides(null, null);
        }
        helper.succeed();
    }

    /** /ec5 player … corruption add / remove / clear 之后，每秒的发作跟着变；手册「矩阵损伤」「身体类」两步打勾 */
    @GameTest(template = EMPTY)
    public static void corruptionCommandsDriveEffects(GameTestHelper helper) throws CommandSyntaxException {
        CommandSourceStack source = source(helper);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        data.setPentacleTier(3);
        List<ServerPlayer> targets = List.of(player);

        helper.assertTrue(CorruptionCommands.add(source, targets, "body_4") == 1, "add");
        player.fallDistance = 2F;
        CorruptionTicker.runEffects(player, data, alwaysHits());
        helper.assertTrue(player.fallDistance == 3F, "加上 body_4 后应发作，实际 " + player.fallDistance);

        CorruptionTicker.updateGuideProgress(player, data);
        helper.assertTrue(advancementDone(player, CorruptionTicker.STEP_MATRIX_DAMAGE), "身上有效果：手册「矩阵损伤」一步应打勾");
        helper.assertTrue(advancementDone(player, CorruptionTicker.STEP_BODY), "身上有身体类：手册「身体类」一步应打勾");
        helper.assertFalse(advancementDone(player, CorruptionTicker.STEP_MIND), "没有心智类：那一步不应打勾");

        helper.assertTrue(CorruptionCommands.remove(source, targets, "body_4") == 1, "remove");
        CorruptionTicker.runEffects(player, data, alwaysHits());
        helper.assertTrue(player.fallDistance == 3F, "删掉后不应再发作");

        CorruptionCommands.add(source, targets, "body_0");
        CorruptionCommands.add(source, targets, "mind_7");
        helper.assertTrue(CorruptionCommands.clear(source, targets) == 1 && data.getCorruptionEffects().isEmpty(), "clear 清空");
        helper.assertTrue(CorruptionTicker.runEffects(player, data, alwaysHits()) == 0, "清空后什么都不发作");
        helper.succeed();
    }

    // ---------------------------------------------------------------- #13 爆炸、#15 闪电

    /** #13：周围方块不变、不起火；盔甲架、展示框、掉落物不受损；旁边的牛受伤（伤害记在本人头上）；本人也挨了爆炸 */
    @GameTest(template = EMPTY_LARGE)
    public static void corruptionExplosionKeepsScenery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos glass = new BlockPos(5, 1, 3);
        BlockPos planks = new BlockPos(3, 1, 5);
        BlockPos dirt = new BlockPos(7, 1, 4);
        BlockPos wall = new BlockPos(5, 2, 8);
        helper.setBlock(glass, Blocks.GLASS);
        helper.setBlock(planks, Blocks.OAK_PLANKS);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(wall, Blocks.STONE);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(5, 1, 6));
        ItemFrame frame = new ItemFrame(level, helper.absolutePos(wall.north()), Direction.NORTH);
        frame.setItem(new ItemStack(Items.DIAMOND));
        level.addFreshEntity(frame);
        ItemEntity stick = helper.spawnItem(Items.STICK, 6.5F, 1.2F, 6.5F);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(8, 1, 5));
        ServerPlayer player = victim(helper, new Vec3(5.5D, 1D, 5.5D));
        IPlayerData data = data(helper, player);

        helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_13")), "#13 没有条件，应发作");

        helper.assertBlockPresent(Blocks.GLASS, glass);
        helper.assertBlockPresent(Blocks.OAK_PLANKS, planks);
        helper.assertBlockPresent(Blocks.DIRT, dirt);
        helper.assertBlockPresent(Blocks.STONE, wall);
        for (BlockPos pos : BlockPos.betweenClosed(0, 1, 0, 10, 7, 10)) {
            helper.assertBlockNotPresent(Blocks.FIRE, pos);
        }
        helper.assertTrue(stand.isAlive() && stand.getHealth() == stand.getMaxHealth(), "盔甲架不应受损");
        helper.assertTrue(frame.isAlive() && frame.getItem().is(Items.DIAMOND), "展示框和里面的物品不应受损");
        helper.assertTrue(stick.isAlive(), "掉落物不应被炸掉");
        helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "旁边的牛应受伤，实际 " + cow.getHealth());
        helper.assertTrue(cow.getLastDamageSource() != null && cow.getLastDamageSource().getEntity() == player, "牛的伤害应记在中效果的玩家头上");
        helper.assertTrue(player.getHealth() < VICTIM_HEALTH, "本人应挨爆炸，实际生命 " + player.getHealth());
        helper.assertTrue(player.getLastDamageSource() != null && player.getLastDamageSource().is(DamageTypes.EXPLOSION),
                "本人挨的是普通爆炸伤害（不带攻击者）");
        helper.succeed();
    }

    /** #15：闪电不进世界（服务端没有闪电实体）、脚下的氧化铜不脱氧、周围不起火；本人挨闪电伤害（最大生命的 37.5%）并着火 */
    @GameTest(template = EMPTY)
    public static void corruptionLightningIsVisualOnly(GameTestHelper helper) {
        atBaseStrength(helper, CorruptionTests::lightningIsVisualOnly);
    }

    private static void lightningIsVisualOnly(GameTestHelper helper) {
        BlockPos copper = new BlockPos(2, 1, 2);
        helper.setBlock(copper, Blocks.OXIDIZED_COPPER);
        ServerPlayer player = victim(helper, new Vec3(2.5D, 2D, 2.5D));
        IPlayerData data = data(helper, player);

        helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_15")), "#15 没有条件，应发作");

        helper.assertBlockPresent(Blocks.OXIDIZED_COPPER, copper);
        for (BlockPos pos : BlockPos.betweenClosed(0, 1, 0, 4, 3, 4)) {
            helper.assertBlockNotPresent(Blocks.FIRE, pos);
        }
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16D);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(LightningBolt.class, area).isEmpty(), "服务端不应有闪电实体");
        helper.assertTrue(near(player.getHealth(), VICTIM_HEALTH * 0.625F), "本人应挨最大生命 37.5% 的闪电，实际生命 " + player.getHealth());
        helper.assertTrue(player.getLastDamageSource() != null && player.getLastDamageSource().is(DamageTypes.LIGHTNING_BOLT), "伤害类型是闪电");
        helper.assertTrue(player.isOnFire(), "本人应着火");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 心智类的三个修复

    /** #8（温和版关掉时）：只打乱主物品栏 36 格；盔甲栏、副手不动；东西一样不少 */
    @GameTest(template = EMPTY)
    public static void corruptionShuffleKeepsArmor(GameTestHelper helper) {
        CorruptionEffects.setTestSafetyOverrides(null, null, null, false, null);
        try {
            shuffleKeepsArmor(helper);
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
    }

    private static void shuffleKeepsArmor(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        List<ItemStack> before = new ArrayList<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND)) {
            before.add(player.getItemBySlot(slot).copy());
        }
        for (int i = 0; i < 36; i++) {
            player.getInventory().items.set(i, new ItemStack(Items.STONE, i + 1));   // 数量各不相同，看得出换没换位置
        }

        helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("mind_8")), "#8 没有条件，应发作");

        int index = 0;
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND)) {
            helper.assertTrue(ItemStack.matches(before.get(index++), player.getItemBySlot(slot)), slot + " 不应被动");
        }
        boolean[] seen = new boolean[37];
        boolean moved = false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            helper.assertTrue(stack.is(Items.STONE) && !seen[stack.getCount()], "主物品栏的东西应一样不少、不重复");
            seen[stack.getCount()] = true;
            moved |= stack.getCount() != i + 1;
        }
        helper.assertTrue(moved, "36 格应被打乱（完全不动的几率是 1/36!）");
        helper.succeed();
    }

    /** #9（温和版关掉时）：只扔主手那一组（掉在地上），别的格子、盔甲、副手不动；主手是空的就不发作 */
    @GameTest(template = EMPTY)
    public static void corruptionDropOnlyMainHand(GameTestHelper helper) {
        CorruptionEffects.setTestSafetyOverrides(null, null, null, false, null);
        try {
            dropOnlyMainHand(helper);
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
    }

    private static void dropOnlyMainHand(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        player.getInventory().selected = 0;
        player.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 5));
        player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 7));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));

        helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("mind_9")), "主手有东西时 #9 应发作");

        helper.assertTrue(player.getMainHandItem().isEmpty(), "主手应空了");
        helper.assertTrue(player.getInventory().items.get(1).is(Items.COBBLESTONE) && player.getInventory().items.get(1).getCount() == 7, "别的格子不动");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "盔甲不动");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD), "副手不动");
        AABB area = new AABB(player.blockPosition()).inflate(8D);
        int diamonds = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.DIAMOND))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(diamonds == 5, "地上应有扔出来的 5 颗钻石，实际 " + diamonds);
        helper.assertFalse(CorruptionBehaviors.trigger(player, data, effect("mind_9")), "主手空了就不发作");
        helper.succeed();
    }

    /**
     * #10：替玩家右键主手（雪球扔出去、少一个），走 {@code player.connection.handleUseItem}（和玩家自己右键同一段代码，混合端的领地事件在那里发）；
     * 主手是空的就不发作。走没走 handleUseItem 看空闲计时：它会 resetLastActionTime，直接调 gameMode.useItem 不会。
     * 混合端上的领地 / banitem 拦截 GameTest 测不了，上线前实测（policy §2.4 第 5 条）。温和版关掉时才是这个行为。
     */
    @GameTest(template = EMPTY)
    public static void corruptionUsesMainHandItem(GameTestHelper helper) {
        CorruptionEffects.setTestSafetyOverrides(null, null, null, false, null);
        try {
            usesMainHandItem(helper);
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
    }

    private static void usesMainHandItem(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SNOWBALL, 4));
        long idleSince = player.getLastActionTime();
        while (Util.getMillis() <= idleSince) {
            Thread.onSpinWait();   // 等过这一毫秒，空闲计时被刷新才看得出来（最多 1 毫秒）
        }

        helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("mind_10")), "主手有东西时 #10 应发作");

        helper.assertTrue(player.getMainHandItem().is(Items.SNOWBALL) && player.getMainHandItem().getCount() == 3,
                "雪球应少一个，实际 " + player.getMainHandItem());
        helper.assertTrue(player.getLastActionTime() > idleSince,
                "应走 connection.handleUseItem（会刷新空闲计时），不是直接调 gameMode.useItem");
        AABB area = new AABB(player.blockPosition()).inflate(8D);
        helper.assertFalse(helper.getLevel().getEntitiesOfClass(Snowball.class, area).isEmpty(), "应扔出一个雪球");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertFalse(CorruptionBehaviors.trigger(player, data, effect("mind_10")), "主手空了就不发作");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 条件、月相、心智悖论

    /**
     * 条件：#12 / #19 只在末地 / 下界（测试在主世界）、#20 在主世界；#4 要在下落；#11 要刚挨过打；#5 已在悖论里不再给。
     * 后果：#0 双手迟钝 II 20 秒（原作误给缓慢）、#11 清掉无敌帧、#20 溺水伤害（最大生命的 7.5%）、#5 心智悖论 100 秒并给手册那一步打勾；
     * #0、#5 都是矩阵后遗症，牛奶去不掉。月相点数表（E8，按比例的换算见 corruptionMoonlightByPhase）
     */
    @GameTest(template = EMPTY)
    public static void corruptionConditionsAndMoon(GameTestHelper helper) {
        atBaseStrength(helper, CorruptionTests::conditionsAndMoon);
    }

    private static void conditionsAndMoon(GameTestHelper helper) {
        ServerPlayer player = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        IPlayerData data = data(helper, player);

        helper.assertFalse(CorruptionBehaviors.trigger(player, data, effect("matrix_12")), "主世界里 #12（末地）不发作");
        helper.assertFalse(CorruptionBehaviors.trigger(player, data, effect("matrix_19")), "主世界里 #19（下界）不发作");
        helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_20")), "主世界里 #20 发作");
        helper.assertTrue(near(player.getHealth(), VICTIM_HEALTH * 0.925F) && player.getLastDamageSource().is(DamageTypes.DROWN),
                "#20：最大生命 7.5% 的溺水伤害，实际生命 " + player.getHealth());

        player.fallDistance = 0F;
        helper.assertFalse(CorruptionBehaviors.trigger(player, data, effect("body_4")), "没在下落时 #4 不发作");

        player.hurtTime = 0;
        helper.assertFalse(CorruptionBehaviors.trigger(player, data, effect("matrix_11")), "没挨打时 #11 不发作");
        player.hurtTime = 5;
        player.invulnerableTime = 15;
        helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_11")), "刚挨过打时 #11 发作");
        helper.assertTrue(player.hurtTime == 0 && player.invulnerableTime == 0, "#11 清掉受击动画和无敌帧");

        helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("body_0")), "#0 发作");
        helper.assertTrue(Afflictions.remaining(player, Affliction.HEAVY_HANDS) == 400 && Afflictions.level(player, Affliction.HEAVY_HANDS) == 1,
                "#0：双手迟钝 II 20 秒，实际 " + Afflictions.remaining(player, Affliction.HEAVY_HANDS));
        helper.assertTrue(player.getActiveEffects().isEmpty(), "#0 不挂药水效果");
        helper.assertFalse(Afflictions.has(player, Affliction.LEADEN_LEGS), "#0 不再给走得慢（原作的 bug）");

        helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("mind_5")), "#5 发作");
        helper.assertTrue(Afflictions.remaining(player, Affliction.PARADOX) == Afflictions.PARADOX_TICKS, "#5：心智悖论 2000 tick");
        helper.assertFalse(player.hasEffect(com.flameforge.essentialcraft5.registry.ECCorruption.MINDFOLD_PARADOX.get()), "#5 不挂药水效果");
        helper.assertFalse(CorruptionBehaviors.trigger(player, data, effect("mind_5")), "已经在悖论里不再给");
        helper.assertTrue(advancementDone(player, CorruptionTicker.STEP_PARADOX), "陷入心智悖论：手册那一步应打勾");
        player.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
        player.removeAllEffects();
        helper.assertTrue(Afflictions.has(player, Affliction.PARADOX) && Afflictions.has(player, Affliction.HEAVY_HANDS), "牛奶、清空效果都去不掉后遗症");
        Afflictions.clear(player);

        int[] expected = {5, 3, 2, 1, 0, 1, 2, 3};
        for (int phase = 0; phase < 8; phase++) {
            helper.assertTrue(CorruptionBehaviors.moonDamage(phase) == expected[phase], "月相 " + phase + " 的点数应是 " + expected[phase]);
        }
        helper.assertTrue(ForgeRegistries.SOUND_EVENTS.containsKey(EssentialCraft5.id("sound.tinnitus"))
                && ForgeRegistries.SOUND_EVENTS.containsKey(EssentialCraft5.id("sound.heartbeat")), "耳鸣、心跳两个音效已注册");
        helper.succeed();
    }
    // ---------------------------------------------------------------- 按最大生命的比例扣血（2026-10-13 起）

    /** 打之前复位：满血、清无敌帧、灭火（每一下都按满血、没有无敌帧算） */
    private static void refresh(ServerPlayer player) {
        player.invulnerableTime = 0;
        player.hurtTime = 0;
        player.clearFire();
        player.setHealth(player.getMaxHealth());
    }

    /** 浮点比较（相对误差 0.1%，至少按 1 算） */
    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < 1.0E-3F * Math.max(1F, Math.abs(expected));
    }

    /** 原版 Player.hurt 对「随难度缩放」的伤害（爆炸）做的换算：和平 0、简单 min(a / 2 + 1, a)、普通 a、困难 1.5a */
    private static float scaledForDifficulty(Difficulty difficulty, float amount) {
        return switch (difficulty) {
            case PEACEFUL -> 0F;
            case EASY -> Math.min(amount / 2F + 1F, amount);
            case NORMAL -> amount;
            case HARD -> amount * 3F / 2F;
        };
    }

    /**
     * 最大生命 200 和 1000 的玩家：#20 溺水 7.5%、#24 凋零 7.5%、#2 挥手时仙人掌 15%、#18 身上有 MRU 腐化时魔法 30%、#14 着火同时扣 7.5%（着火类、抗火时不扣）、
     * #15 闪电 37.5% 并着火；#12 / #19 / #21 / #22 / #23 / #13 的量按 damageOf 核对（7.5% / 7.5% / 7.5% / 7.5% / 22.5% / 60%）；伤害类型照旧
     */
    @GameTest(template = EMPTY)
    public static void corruptionDamageScalesWithMaxHealth(GameTestHelper helper) {
        atBaseStrength(helper, CorruptionTests::damageScalesWithMaxHealth);
    }

    private static void damageScalesWithMaxHealth(GameTestHelper helper) {
        ServerPlayer player = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        IPlayerData data = data(helper, player);
        for (float max : new float[]{200F, 1000F}) {
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);

            refresh(player);
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_20")), "主世界里 #20 发作");
            helper.assertTrue(near(player.getHealth(), max * 0.925F) && player.getLastDamageSource().is(DamageTypes.DROWN),
                    "最大 " + max + "：#20 扣 7.5% 溺水，实际生命 " + player.getHealth());

            refresh(player);
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_24")), "#24 没有条件，应发作");
            helper.assertTrue(near(player.getHealth(), max * 0.925F) && player.getLastDamageSource().is(DamageTypes.WITHER),
                    "最大 " + max + "：#24 扣 7.5% 凋零，实际生命 " + player.getHealth());

            refresh(player);
            player.swinging = true;
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("body_2")), "挥手时 #2 发作");
            player.swinging = false;
            helper.assertTrue(near(player.getHealth(), max * 0.85F) && player.getLastDamageSource().is(DamageTypes.CACTUS),
                    "最大 " + max + "：#2 扣 15% 仙人掌伤害，实际生命 " + player.getHealth());

            refresh(player);
            data.setAffliction(Affliction.MRU_CORRUPTION.id(), 200, 0);
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_18")), "身上有 MRU 腐化时 #18 发作");
            data.setAffliction(Affliction.MRU_CORRUPTION.id(), 0, 0);
            helper.assertTrue(near(player.getHealth(), max * 0.7F) && player.getLastDamageSource().is(DamageTypes.MAGIC),
                    "最大 " + max + "：#18 扣 30% 魔法，实际生命 " + player.getHealth());

            refresh(player);
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_14")), "#14 没有条件，应发作");
            helper.assertTrue(player.isOnFire() && near(player.getHealth(), max * 0.925F)
                            && player.getLastDamageSource().is(DamageTypeTags.IS_FIRE),
                    "最大 " + max + "：#14 着火并扣 7.5% 着火伤害，实际生命 " + player.getHealth());

            refresh(player);
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_14")), "抗火时 #14 照样发作");
            player.removeEffect(MobEffects.FIRE_RESISTANCE);
            helper.assertTrue(player.getHealth() == max, "抗火挡得住 #14 那一下，实际生命 " + player.getHealth());

            refresh(player);
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_15")), "#15 没有条件，应发作");
            helper.assertTrue(player.isOnFire() && near(player.getHealth(), max * 0.625F)
                            && player.getLastDamageSource().is(DamageTypes.LIGHTNING_BOLT),
                    "最大 " + max + "：#15 扣 37.5% 闪电伤害并着火，实际生命 " + player.getHealth());

            float[][] expected = {{12, 0.075F}, {19, 0.075F}, {21, 0.075F}, {22, 0.075F}, {23, 0.225F}, {13, 0.6F}, {11, 0F}, {16, 0F}};
            for (float[] row : expected) {
                float amount = CorruptionBehaviors.damageOf(player, (int) row[0], 0);
                helper.assertTrue(near(amount, max * row[1]), "最大 " + max + "：#" + (int) row[0] + " 应扣 " + max * row[1] + "，实际 " + amount);
            }
        }
        refresh(player);
        helper.succeed();
    }

    /** #17 按月相：最大 1000 时满月 375、凸月 225、弦月 150、蛾眉月 75、新月 0（原作 5 / 3 / 2 / 1 / 0 点，每点按 7.5% 换算） */
    @GameTest(template = EMPTY)
    public static void corruptionMoonlightByPhase(GameTestHelper helper) {
        atBaseStrength(helper, CorruptionTests::moonlightByPhase);
    }

    private static void moonlightByPhase(GameTestHelper helper) {
        float[] expected = {375F, 225F, 150F, 75F, 0F, 75F, 150F, 225F};
        for (int phase = 0; phase < 8; phase++) {
            float amount = CorruptionBehaviors.moonlightDamage(1000F, phase);
            helper.assertTrue(near(amount, expected[phase]), "月相 " + phase + "：最大 1000 应扣 " + expected[phase] + "，实际 " + amount);
        }
        helper.assertTrue(near(CorruptionBehaviors.moonlightDamage(20F, 0), 7.5F) && near(CorruptionBehaviors.moonlightDamage(200F, 2), 30F),
                "最大 20 满月 7.5、最大 200 弦月 30");
        ServerPlayer player = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        helper.assertTrue(near(CorruptionBehaviors.damageOf(player, 17, 1), 200F * 0.225F), "damageOf 按传进去的月相算（凸月 22.5%）");
        helper.succeed();
    }

    /**
     * 比例是配置：凋零改成 50% 时 #24 扣一半；改成 0 时照样发作（发话）但不扣血；「MRU 腐化」改成 10% 时按 10% 算；清掉覆盖值回到默认
     */
    @GameTest(template = EMPTY)
    public static void corruptionHealthCostConfig(GameTestHelper helper) {
        atBaseStrength(helper, CorruptionTests::healthCostConfig);
    }

    private static void healthCostConfig(GameTestHelper helper) {
        ServerPlayer player = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        IPlayerData data = data(helper, player);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        try {
            CorruptionEffects.setTestHealthCost(CorruptionEffects.HealthCost.WITHERING, 0.5D);
            refresh(player);
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_24")), "#24 应发作");
            helper.assertTrue(near(player.getHealth(), 500F), "凋零配成 50%：最大 1000 应剩 500，实际 " + player.getHealth());

            CorruptionEffects.setTestHealthCost(CorruptionEffects.HealthCost.WITHERING, 0D);
            refresh(player);
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_24")), "配成 0 照样发作");
            helper.assertTrue(player.getHealth() == 1000F, "配成 0 不扣血，实际 " + player.getHealth());

            CorruptionEffects.setTestHealthCost(CorruptionEffects.HealthCost.MRU_CORRUPTION, 0.1D);
            helper.assertTrue(near(MRUCorruption.damage(player), 100F),
                    "「MRU 腐化」配成 10%：最大 1000、没有抗性时一下 100");
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
        CorruptionEffects.setTestStrength(1D, 1D, 1D);   // 上面 finally 里的清理把基准强度也清了，这里按原作比例核对
        helper.assertTrue(near(CorruptionEffects.HealthCost.WITHERING.amount(1000F), 75F)
                        && near(MRUCorruption.damage(player), 250F),
                "清掉覆盖值回到默认：凋零 7.5%、MRU 腐化 25%");
        refresh(player);
        helper.succeed();
    }

    /**
     * #13 本人那一下按最大生命：最大 1000、不温和时扣 60%（再按难度缩放：简单约一半、困难 1.5 倍）；温和时满血也最多掉一半；
     * 残血 50 时温和版最多掉 25
     */
    @GameTest(template = EMPTY_LARGE)
    public static void corruptionSelfBlastScalesWithMaxHealth(GameTestHelper helper) {
        atBaseStrength(helper, CorruptionTests::selfBlastScalesWithMaxHealth);
    }

    private static void selfBlastScalesWithMaxHealth(GameTestHelper helper) {
        ServerPlayer player = victim(helper, new Vec3(5.5D, 1D, 5.5D));
        IPlayerData data = data(helper, player);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        Difficulty difficulty = helper.getLevel().getDifficulty();
        try {
            float seen = Explosion.getSeenPercent(player.position(), player);
            float raw = CorruptionBehaviors.selfBlastDamage(1000F, seen, CorruptionBehaviors.BLAST_POWER * 2D);
            helper.assertTrue(raw <= 600F + 1.0E-3F && (seen < 1F || near(raw, 600F)), "站在爆心没被挡住时是最大生命的 60%，实际 " + raw + "（可见 " + seen + "）");
            float scaled = scaledForDifficulty(difficulty, raw);

            CorruptionEffects.setTestSafetyOverrides(null, null, null, false, null);
            refresh(player);
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_13")), "#13 应发作");
            helper.assertTrue(near(player.getHealth(), Math.max(0F, 1000F - scaled)),
                    "不温和：" + difficulty + " 难度下应扣 " + scaled + "，实际生命 " + player.getHealth());

            CorruptionEffects.setTestSafetyOverrides(null, null, null, true, null);
            refresh(player);
            player.setDeltaMovement(Vec3.ZERO);
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_13")), "#13 应发作");
            helper.assertTrue(near(player.getHealth(), 1000F - Math.min(scaled, 500F)),
                    "温和：满血 1000 最多掉一半，实际生命 " + player.getHealth());

            refresh(player);
            player.setHealth(50F);
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_13")), "#13 应发作");
            helper.assertTrue(player.isAlive() && near(player.getHealth(), 50F - Math.min(scaled, 25F)),
                    "温和：残血 50 最多掉 25，实际生命 " + player.getHealth());
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
        refresh(player);
        helper.succeed();
    }

    /**
     * 「MRU 腐化」按最大生命：最大 1000 时一下 250（25% × 易伤 1）；打开 mru_corruption_lethal 时满血掉到 750；默认打不死人，100 点血只掉到 1
     */
    @GameTest(template = EMPTY)
    public static void corruptionMruCorruptionScales(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockSurvivalPlayer();
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        try {
            helper.assertTrue(near(MRUCorruption.damage(player), 250F),
                    "最大 1000、没有抗性：一下 250，实际 " + MRUCorruption.damage(player));
            player.setHealth(100F);
            player.invulnerableTime = 0;
            MRUCorruption.pulse(player, 16);
            helper.assertTrue(player.isAlive() && near(player.getHealth(), 1F), "默认打不死人：100 点血只掉到 1，实际 " + player.getHealth());

            CorruptionEffects.setTestMruCorruptionLethal(true);
            player.setHealth(1000F);
            player.invulnerableTime = 0;
            MRUCorruption.pulse(player, 16);
            helper.assertTrue(near(player.getHealth(), 750F), "打开以后满血 1000 掉到 750，实际 " + player.getHealth());
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    /**
     * 打不死人的上限再加一道兜底：受上限的这一下要是还把目标打死了（别的模组在同一档 LOWEST 之后又放大了伤害），取消死亡、生命设回 1。
     * 这里直接给一个比生命大的上限来模拟「上限没夹住」。另外生命 ≥ 2^24 时「打到剩 1 点」不能在 float 里舍入成整条命
     */
    @GameTest(template = EMPTY)
    public static void corruptionCappedHurtNeverKills(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        cow.setHealth(10F);
        com.flameforge.essentialcraft5.corruption.CappedHurt.hurt(cow, cow.damageSources().magic(), 100F, e -> 1000D);
        helper.assertTrue(cow.isAlive() && !cow.isDeadOrDying() && near(cow.getHealth(), 1F), "受上限的这一下不该打死，实际生命 " + cow.getHealth());
        float big = 2.0E7F;
        helper.assertTrue(big - 1F == big, "前提：2e7 在 float 里减 1 舍入回原值");
        float leave = com.flameforge.essentialcraft5.corruption.CappedHurt.leavingOne(big);
        helper.assertTrue(leave < big && big - leave > 0F, "2e7 血打到剩 1 点：扣的量要小于生命，实际 " + leave);
        helper.assertTrue(com.flameforge.essentialcraft5.corruption.CappedHurt.leavingOne(10F) == 9F, "10 血打到剩 1 点扣 9");
        helper.succeed();
    }

    /** #15 的比例配成 0：只着火、不走受击（没有伤害源记录、肩上的鹦鹉不会被赶下来） */
    @GameTest(template = EMPTY)
    public static void corruptionLightningZeroSkipsHurt(GameTestHelper helper) {
        ServerPlayer player = victim(helper, new Vec3(2.5D, 2D, 2.5D));
        IPlayerData data = data(helper, player);
        CorruptionEffects.setTestHealthCost(CorruptionEffects.HealthCost.LIGHTNING, 0D);
        try {
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_15")), "#15 没有条件，应发作");
            helper.assertTrue(near(player.getHealth(), VICTIM_HEALTH), "比例 0 不掉血，实际 " + player.getHealth());
            helper.assertTrue(player.getLastDamageSource() == null, "比例 0 不该走受击");
            helper.assertTrue(player.isOnFire(), "照样着火");
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    // ================================================================ 第十批：不毁号的几道闸

    /** 治这个效果至少要几档：类别要求 + 柱子攒满时付得起（默认柱子上限 1 万：0 档 28800、1 档 43200、2 档 57600、3 档 144000） */
    @GameTest(template = EMPTY)
    public static void corruptionCureTiers(GameTestHelper helper) {
        long[] expected = {28800L, 43200L, 57600L, 144000L};
        for (int tier = 0; tier < expected.length; tier++) {
            helper.assertTrue(PlayerPentacleBlockEntity.maxEffectiveESPE(tier) == expected[tier],
                    tier + " 档攒满应能用 " + expected[tier] + "，实际 " + PlayerPentacleBlockEntity.maxEffectiveESPE(tier));
        }
        helper.assertTrue(PlayerPentacleBlockEntity.maxEffectiveESPE(-1) == 0L, "没成形是 0");
        String[][] cases = {{"body_0", "0"}, {"body_4", "0"}, {"mind_9", "1"}, {"mind_5", "1"}, {"matrix_16", "2"}, {"matrix_13", "2"},
                {"matrix_17", "2"}, {"matrix_12", "3"}, {"matrix_19", "3"}, {"matrix_20", "3"}};
        for (String[] c : cases) {
            int tier = CorruptionGate.cureTier(effect(c[0]));
            helper.assertTrue(tier == Integer.parseInt(c[1]), c[0] + " 应要 " + c[1] + " 档，实际 " + tier);
        }
        for (CorruptionEffects.Effect e : CorruptionEffects.ALL) {
            int tier = CorruptionGate.cureTier(e);
            helper.assertTrue(tier >= e.category().minPentacleTier() && PlayerPentacleBlockEntity.maxEffectiveESPE(tier) >= e.cost(),
                    e.id() + " 的档位 " + tier + " 应够类别、也付得起成本");
        }
        helper.succeed();
    }

    /**
     * 开过五芒星才抽：没打开过时 7.2 万损伤也抽不到；第一次打开（0 档就行）以后身体、心智、矩阵三类都抽得到，不再按档位筛；
     * 最贵的三种（成本 72000）门槛是 21.6 万，7.2 万、21.5 万都抽不到；关掉这道闸就照原作什么都抽。
     */
    @GameTest(template = EMPTY)
    public static void corruptionDrawRequiresCureTier(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        RandomSource random = RandomSource.create(0x9E3779B97F4A7C15L);
        try {
            CorruptionEffects.setTestOverrides(true, List.of());
            CorruptionEffects.setTestSafetyOverrides(true, 0, null, null, null);
            data.setMatrixDamage(72000);
            helper.assertTrue(data.getPentacleTier() == -1, "新玩家没打开过五芒星，档位 −1");
            for (int i = 0; i < 50; i++) {
                helper.assertTrue(CorruptionTicker.draw(player, data, random) == null, "没打开过五芒星时不应抽到任何效果");
            }
            helper.assertTrue(data.getMatrixDamage() == 72000 && data.getCorruptionEffects().isEmpty(), "抽不到时损伤、列表都不动");

            // 第一次打开（0 档）：三类都抽得到。损伤给满（默认上限 28.8 万），每个效果的门槛都够得着
            data.setPentacleTier(0);
            int full = CorruptionEffects.maxMatrixDamage();
            Set<CorruptionEffects.Category> seen = new HashSet<>();
            for (int i = 0; i < 400; i++) {
                data.clearCorruptionEffects();
                data.setMatrixDamage(full);
                CorruptionEffects.Effect drawn = CorruptionTicker.draw(player, data, random);
                helper.assertTrue(drawn != null, "0 档、损伤满格时必定抽到");
                seen.add(drawn.category());
                helper.assertTrue(data.getMatrixDamage() == full - CorruptionEffects.drawThreshold(drawn),
                        "应扣掉门槛 " + CorruptionEffects.drawThreshold(drawn) + "，实际剩 " + data.getMatrixDamage());
            }
            helper.assertTrue(seen.size() == 3, "0 档就三类都抽得到，实际只见到 " + seen);
            for (CorruptionEffects.Effect e : CorruptionEffects.ALL) {
                helper.assertTrue(CorruptionGate.drawable(e, data), e.id() + " 打开过五芒星以后都该抽得到");
            }

            // 别的都已经有了，只剩成本 72000 的三种：门槛 21.6 万，7.2 万、21.5 万抽不到，21.6 万抽得到
            data.setPentacleTier(3);
            data.clearCorruptionEffects();
            for (CorruptionEffects.Effect e : CorruptionEffects.ALL) {
                if (e.cost() < 72000) {
                    data.addCorruptionEffect(e.id());
                }
            }
            data.setMatrixDamage(72000);
            helper.assertTrue(CorruptionTicker.draw(player, data, random) == null, "7.2 万够不到最贵三种的门槛");
            data.setMatrixDamage(215999);
            helper.assertTrue(CorruptionTicker.draw(player, data, random) == null, "差 1 点也不行");
            data.setMatrixDamage(216000);
            CorruptionEffects.Effect top = CorruptionTicker.draw(player, data, random);
            helper.assertTrue(top != null && top.cost() == 72000, "21.6 万应抽得到成本 72000 的三种，实际 " + top);
            helper.assertTrue(data.getMatrixDamage() == 0, "扣掉门槛 216000");

            // 关掉闸：档位 −1 照样抽
            CorruptionEffects.setTestSafetyOverrides(false, 0, null, null, null);
            data.setPentacleTier(-1);
            data.clearCorruptionEffects();
            data.setMatrixDamage(40000);
            helper.assertTrue(CorruptionTicker.draw(player, data, random) != null, "闸关着时照原作，没打开过五芒星也抽");
        } finally {
            CorruptionEffects.setTestOverrides(null, null);
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    /** 潜伏：没打开过五芒星的玩家身上的效果不发作，留在列表里；第一次打开（0 档）以后什么类别都照常发作 */
    @GameTest(template = EMPTY)
    public static void corruptionDormantEffects(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        data.addCorruptionEffect("body_4");
        data.addCorruptionEffect("matrix_16");
        data.setMatrixDamage(100);
        try {
            CorruptionEffects.setTestOverrides(true, List.of());
            CorruptionEffects.setTestSafetyOverrides(true, null, null, null, null);
            player.fallDistance = 2F;
            helper.assertTrue(CorruptionTicker.runEffects(player, data, alwaysHits()) == 0, "档位 −1：两个都潜伏");
            helper.assertTrue(player.fallDistance == 2F && data.getMatrixDamage() == 100, "潜伏的不应有后果");
            helper.assertFalse(CorruptionGate.active(effect("body_4"), data) || CorruptionGate.active(effect("matrix_16"), data), "没打开过：都不算在发作");

            data.setPentacleTier(0);
            helper.assertTrue(CorruptionTicker.runEffects(player, data, alwaysHits()) == 2, "0 档：身体类、矩阵类都发作，不再按档位筛");
            helper.assertTrue(player.fallDistance == 3F && data.getMatrixDamage() == 101, "body_4 摔落距离 +1、matrix_16 损伤 +1");
            helper.assertTrue(data.getCorruptionEffects().size() == 2, "潜伏不会把效果删掉");

            data.setPentacleTier(-1);
            CorruptionEffects.setTestSafetyOverrides(false, null, null, null, null);
            helper.assertTrue(CorruptionTicker.runEffects(player, data, alwaysHits()) == 2, "闸关着时照原作都发作");
        } finally {
            CorruptionEffects.setTestOverrides(null, null);
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    /** 抽中以后冷却 N 分钟（N − 1 次抽取机会只扣冷却），第 N 次机会才可能再中；0 = 每次机会都可能中；冷却存进玩家数据 */
    @GameTest(template = EMPTY)
    public static void corruptionDrawCooldown(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        data.setPentacleTier(3);
        RandomSource random = RandomSource.create(20261007L);
        try {
            CorruptionEffects.setTestOverrides(false, List.of());
            CorruptionEffects.setTestSafetyOverrides(true, 5, null, null, null);
            data.setMatrixDamage(72000);
            helper.assertTrue(CorruptionTicker.drawSlot(player, data, random) != null, "第 1 次机会：7.2 万必中");
            helper.assertTrue(data.getCorruptionCooldown() == 4, "冷却 5 分钟 = 跳过 4 次，实际 " + data.getCorruptionCooldown());
            for (int i = 0; i < 4; i++) {
                data.setMatrixDamage(72000);
                helper.assertTrue(CorruptionTicker.drawSlot(player, data, random) == null, "冷却中第 " + (i + 2) + " 次机会不应抽中");
            }
            helper.assertTrue(data.getCorruptionCooldown() == 0 && data.getCorruptionEffects().size() == 1, "冷却走完，只中过 1 个");
            data.setMatrixDamage(72000);
            helper.assertTrue(CorruptionTicker.drawSlot(player, data, random) != null, "第 6 次机会（5 分钟后）又能中");

            CompoundTagCheck.roundTrip(helper, data);

            CorruptionEffects.setTestSafetyOverrides(true, 0, null, null, null);
            data.setCorruptionCooldown(0);
            data.setMatrixDamage(72000);
            helper.assertTrue(CorruptionTicker.drawSlot(player, data, random) != null && data.getCorruptionCooldown() == 0,
                    "冷却 0：照原作每次机会都可能中");
        } finally {
            CorruptionEffects.setTestOverrides(null, null);
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    /** 冷却和档位存盘、读回；旧数据（没有这两个键）读成档位 −1、冷却 0，不是 0 档 */
    private static final class CompoundTagCheck {
        static void roundTrip(GameTestHelper helper, IPlayerData data) {
            data.setCorruptionCooldown(7);
            com.flameforge.essentialcraft5.player.PlayerData copy = new com.flameforge.essentialcraft5.player.PlayerData();
            copy.deserializeNBT(data.serializeNBT());
            helper.assertTrue(copy.getCorruptionCooldown() == 7 && copy.getPentacleTier() == data.getPentacleTier(), "冷却、档位应存盘");
            net.minecraft.nbt.CompoundTag old = data.serializeNBT();
            old.remove("pentacle_tier");
            old.remove("corruption_cooldown");
            com.flameforge.essentialcraft5.player.PlayerData legacy = new com.flameforge.essentialcraft5.player.PlayerData();
            legacy.deserializeNBT(old);
            helper.assertTrue(legacy.getPentacleTier() == -1 && legacy.getCorruptionCooldown() == 0, "旧数据：档位 −1、冷却 0");
            data.setCorruptionCooldown(0);
        }
    }

    /** 损伤上限：加到上限为止、返回实际加了多少；超过上限的不再加，玩家 tick 夹回上限；不会溢出 int */
    @GameTest(template = EMPTY)
    public static void corruptionMatrixDamageCap(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        try {
            helper.assertTrue(CorruptionEffects.maxMatrixDamage() == 288000 && CorruptionEffects.damageBarMax() == 288000,
                    "默认上限 288000（最贵的效果门槛 216000 之上，五芒星损伤条满格）");
            CorruptionEffects.setTestSafetyOverrides(null, null, 1000, null, null);
            helper.assertTrue(CorruptionEffects.addMatrixDamage(data, 600) == 600 && data.getMatrixDamage() == 600, "600");
            helper.assertTrue(CorruptionEffects.matrixRoom(data) == 400, "还能记 400");
            helper.assertTrue(CorruptionEffects.addMatrixDamage(data, 600) == 400 && data.getMatrixDamage() == 1000, "到上限为止");
            helper.assertTrue(CorruptionEffects.addMatrixDamage(data, 600) == 0 && CorruptionEffects.matrixRoom(data) == 0, "满了不再加");
            helper.assertTrue(CorruptionEffects.addMatrixDamage(data, -5) == 0 && data.getMatrixDamage() == 1000, "负数不扣");

            data.setMatrixDamage(5000);
            helper.assertTrue(CorruptionEffects.addMatrixDamage(data, 10) == 0 && data.getMatrixDamage() == 5000, "已经超过上限的不加也不扣");
            player.tickCount = CorruptionEffects.TICK_INTERVAL;
            CorruptionTicker.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            helper.assertTrue(data.getMatrixDamage() == 1000, "玩家 tick 夹回上限，实际 " + data.getMatrixDamage());

            CorruptionEffects.setTestSafetyOverrides(null, null, Integer.MAX_VALUE, null, null);
            data.setMatrixDamage(Integer.MAX_VALUE - 10);
            helper.assertTrue(CorruptionEffects.addMatrixDamage(data, 100) == 10 && data.getMatrixDamage() == Integer.MAX_VALUE, "不溢出");
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    /**
     * 温和版 #8 / #9 / #10：#8 只打乱快捷栏（主物品栏 27 格、盔甲、副手不动）；#9 主手收进背包空格、不扔，背包满了就和一格对调；
     * #10 雪球不扔、拉着的弓被打断且不射箭，说的话换成「手抽了一下」
     */
    @GameTest(template = EMPTY)
    public static void corruptionGentleMindEffects(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        AABB area = new AABB(player.blockPosition()).inflate(8D);
        try {
            CorruptionEffects.setTestSafetyOverrides(null, null, null, true, null);

            // #8
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            for (int i = 0; i < 36; i++) {
                player.getInventory().items.set(i, new ItemStack(Items.STONE, i + 1));
            }
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("mind_8")), "#8 应发作");
            boolean[] seen = new boolean[10];
            for (int i = 0; i < 9; i++) {
                int count = player.getInventory().items.get(i).getCount();
                helper.assertTrue(count >= 1 && count <= 9 && !seen[count], "快捷栏的东西应一样不少、只在快捷栏里换");
                seen[count] = true;
            }
            for (int i = 9; i < 36; i++) {
                helper.assertTrue(player.getInventory().items.get(i).getCount() == i + 1, "主物品栏第 " + i + " 格不应被动");
            }
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                    && player.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD), "盔甲、副手不动");

            // #9：有空格
            player.getInventory().clearContent();
            player.getInventory().selected = 0;
            player.getInventory().items.set(0, new ItemStack(Items.NAUTILUS_SHELL, 5));
            player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 7));
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("mind_9")), "主手有东西时 #9 应发作");
            helper.assertTrue(player.getMainHandItem().isEmpty(), "主手应空了");
            helper.assertTrue(player.getInventory().countItem(Items.NAUTILUS_SHELL) == 5, "鹦鹉螺壳应还在背包里，一个不少");
            int stowedAt = player.getInventory().findSlotMatchingItem(new ItemStack(Items.NAUTILUS_SHELL));
            helper.assertTrue(stowedAt >= 9, "先收进主物品栏的空格，实际第 " + stowedAt + " 格");
            helper.assertTrue(player.getInventory().items.get(1).is(Items.COBBLESTONE), "别的格子不动");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.NAUTILUS_SHELL)).isEmpty(),
                    "不应扔在地上");
            // #9：背包满了 → 对调
            for (int i = 0; i < 36; i++) {
                player.getInventory().items.set(i, new ItemStack(Items.DIRT));
            }
            player.getInventory().items.set(0, new ItemStack(Items.EMERALD, 3));
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("mind_9")), "背包满了也发作");
            helper.assertTrue(player.getMainHandItem().is(Items.DIRT) && player.getInventory().countItem(Items.EMERALD) == 3,
                    "背包满了就和主物品栏一格对调，东西一样不少");

            // #10
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SNOWBALL, 4));
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("mind_10")), "主手有东西时 #10 应发作");
            helper.assertTrue(player.getMainHandItem().getCount() == 4, "温和版不替你扔雪球");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Snowball.class, area.inflate(32D), e -> e.getOwner() == player).isEmpty(),
                    "不应有雪球飞出去");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            player.getInventory().items.set(9, new ItemStack(Items.ARROW, 8));
            player.startUsingItem(InteractionHand.MAIN_HAND);
            helper.assertTrue(player.isUsingItem(), "开始拉弓");
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("mind_10")), "#10 发作");
            helper.assertFalse(player.isUsingItem(), "拉着的弓应被打断");
            helper.assertTrue(player.getInventory().countItem(Items.ARROW) == 8
                    && helper.getLevel().getEntitiesOfClass(Arrow.class, area.inflate(32D), e -> e.getOwner() == player).isEmpty(), "打断不射箭");
            helper.assertTrue(CorruptionBehaviors.messageKey(effect("mind_10")).equals(CorruptionBehaviors.TWITCH_MESSAGE_KEY), "说的话换成手抽");
            CorruptionEffects.setTestSafetyOverrides(null, null, null, false, null);
            helper.assertTrue(CorruptionBehaviors.messageKey(effect("mind_10")).equals(effect("mind_10").messageKey()), "关掉温和版说原来那句");
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    /** 温和版 #13：满血 20 的人站在爆心（本人那一下普通难度是 12 点 = 60%），挨完至少还剩一半；驯服的狼、村民不受伤；旁边的牛照样受伤、记在本人头上；方块不变 */
    @GameTest(template = EMPTY_LARGE)
    public static void corruptionGentleExplosion(GameTestHelper helper) {
        BlockPos glass = new BlockPos(5, 1, 3);
        helper.setBlock(glass, Blocks.GLASS);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(7, 1, 5));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(3, 1, 5));
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(5, 1, 7));
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(new Vec3(5.5D, 1D, 5.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        wolf.tame(player);
        IPlayerData data = data(helper, player);
        try {
            CorruptionEffects.setTestSafetyOverrides(null, null, null, true, null);
            float before = player.getHealth();
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_13")), "#13 应发作");
            helper.assertTrue(player.isAlive() && player.getHealth() >= before * 0.5F - 1.0E-3F && player.getHealth() < before,
                    "温和版：挨了爆炸，但至少还剩一半，实际 " + player.getHealth() + " / " + before);
            helper.assertTrue(wolf.getHealth() == wolf.getMaxHealth(), "驯服的狼不应受伤");
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "村民不应受伤");
            helper.assertTrue(cow.getHealth() < cow.getMaxHealth() && cow.getLastDamageSource() != null
                    && cow.getLastDamageSource().getEntity() == player, "牛照样受伤、记在本人头上");
            helper.assertBlockPresent(Blocks.GLASS, glass);
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    /** 打开五芒星记档位：只升不降；第一次打开（之前没记过）发话、冷却拉满，往后升档只记；/ec5 指令能查、能往下改 */
    @GameTest(template = EMPTY)
    public static void corruptionPentacleRecordsTier(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        try {
            CorruptionEffects.setTestSafetyOverrides(true, 20, null, null, null);
            helper.assertFalse(CorruptionGate.recordPentacle(player, -1), "没成形不记");
            helper.assertTrue(CorruptionGate.recordPentacle(player, 1) && data.getPentacleTier() == 1, "记下 1 档");
            helper.assertTrue(data.getCorruptionCooldown() == CorruptionTicker.cooldownSlots(), "刚升档冷却拉满，实际 " + data.getCorruptionCooldown());
            helper.assertFalse(CorruptionGate.recordPentacle(player, 0), "低的不记");
            helper.assertTrue(data.getPentacleTier() == 1, "只升不降");
            data.setCorruptionCooldown(0);
            helper.assertTrue(CorruptionGate.recordPentacle(player, 7) && data.getPentacleTier() == ECPentacle.MAX_TIER, "超出的夹到最高档（ECPentacle.MAX_TIER）");
            helper.assertTrue(data.getCorruptionCooldown() == 0, "只有第一次打开拉满冷却，往后升档不再拉，实际 " + data.getCorruptionCooldown());
            CommandSourceStack source = source(helper);
            helper.assertTrue(CorruptionCommands.setTier(source, List.of(player), 0) == 1 && data.getPentacleTier() == 0, "指令能往下改");
            helper.assertTrue(CorruptionCommands.getTier(source, List.of(player)) == 0, "指令查得到");
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    /** 聚集体照领地走：站在能动那格的人照加（有上限）、动作栏提醒；聚集体所在格是别人领地时不加；闸关着照原作加 */
    @GameTest(template = EMPTY)
    public static void corruptionPresenceRespectsClaims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 2.5D, 2.5D));
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, at.x, at.y, at.z, 20000, 1F, false, true);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.moveTo(at.x + 2D, at.y - 1D, at.z, 0F, 0F);
        IPlayerData data = data(helper, player);
        int perCycle = MRUPresenceEntity.matrixDamage(2, 1F);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            CorruptionEffects.setTestSafetyOverrides(null, null, null, null, true);
            presence.runCycle(List.of(player));
            helper.assertTrue(data.getMatrixDamage() == perCycle, "能动那一格：一轮加 " + perCycle + "，实际 " + data.getMatrixDamage());

            claims.denyAt(presence.blockPosition());
            ECProtection.forget(player.getUUID());
            PresenceClaimMemory.forget(player.getUUID());
            data.setMatrixDamage(0);
            presence.runCycle(List.of(player));
            helper.assertTrue(data.getMatrixDamage() == 0, "聚集体在别人领地里：不加，实际 " + data.getMatrixDamage());

            CorruptionEffects.setTestSafetyOverrides(null, null, null, null, false);
            presence.runCycle(List.of(player));
            helper.assertTrue(data.getMatrixDamage() == perCycle, "闸关着照原作加");

            CorruptionEffects.setTestSafetyOverrides(null, null, 100, null, false);
            data.setMatrixDamage(90);
            presence.runCycle(List.of(player));
            helper.assertTrue(data.getMatrixDamage() == 100, "聚集体加的也有上限，实际 " + data.getMatrixDamage());
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
            ECProtection.forget(player.getUUID());
            PresenceClaimMemory.forget(player.getUUID());
            presence.discard();
        }
        helper.succeed();
    }

    /**
     * 泄漏照领地走：机器主人能动那团聚集体时照旧汇进去，结论记在机器上，一小时内那一格改成别人的也照旧（不再问）；
     * 记录过期后重新问，不能动就不喂它，在机器处另凝一团，以后泄漏直接汇进机器那一格的这团、不再问、不再新建；
     * 闸关着照原作汇进最近的。独占一个批次（结算是整个维度一起算的，别的测试的泄漏会混进来）。
     */
    @GameTest(template = EMPTY_LARGE, batch = "essentialcraft5_corruption_leak")
    public static void corruptionLeakRespectsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 清场（同 MRUCUTests 的泄漏测试）：先结算掉全维度攒着的泄漏，再删掉附近别的测试留下的聚集体，免得「最近的那团」不是这里放的
        ECCorruption.settle(level);
        level.getEntitiesOfClass(MRUPresenceEntity.class, new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(64D), Entity::isAlive)
                .forEach(Entity::discard);
        BlockPos machinePos = new BlockPos(2, 1, 2);
        helper.setBlock(machinePos, ECRadiatingChamber.RADIATING_CHAMBER.get());
        MRUBlockEntity machine = (MRUBlockEntity) helper.getBlockEntity(machinePos);
        UUID ownerId = UUID.randomUUID();
        ServerPlayer owner = ProtectionFrameworkTests.realPlayer(helper, ownerId, "ec5-leak");
        machine.setOwner(ownerId, "ec5-leak");
        ECProtection.setOnlineOverride(owner);
        BlockPos machineAbs = helper.absolutePos(machinePos);
        Vec3 far = helper.absoluteVec(new Vec3(8.5D, 2.5D, 8.5D));
        MRUPresenceEntity foreign = MRUPresenceEntity.create(level, far.x, far.y, far.z, 1000, 1F, false, true);
        level.addFreshEntity(foreign);
        List<MRUPresenceEntity> created = new ArrayList<>();
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            CorruptionEffects.setTestSafetyOverrides(null, null, null, null, true);
            ECCorruption.increaseAt(level, machineAbs, 100);
            ECCorruption.settle(level);
            helper.assertTrue(foreign.getMRU() == 1100, "主人能动那一格：照旧汇进去，实际 " + foreign.getMRU());

            helper.assertTrue(claims.probes(rel(helper, foreign.blockPosition())) == 1, "以主人身份问过一次那团的格子");
            helper.assertTrue(machine.leakFeedRecord().size() == 1, "结论记在机器上");

            claims.denyAt(foreign.blockPosition());
            ECProtection.forget(ownerId);
            ECCorruption.increaseAt(level, machineAbs, 100);
            ECCorruption.settle(level);
            helper.assertTrue(foreign.getMRU() == 1200, "一小时内照记着的结论汇，实际 " + foreign.getMRU());
            helper.assertTrue(claims.probes(rel(helper, foreign.blockPosition())) == 1, "记着的时候不再问");

            machine.leakFeedRecord().ageForTest(ECCorruption.FEED_RECORD_TICKS);
            ECCorruption.increaseAt(level, machineAbs, 100);
            ECCorruption.settle(level);
            helper.assertTrue(foreign.getMRU() == 1200, "记录过期、重新问下来那团在别人领地里：不喂它，实际 " + foreign.getMRU());
            helper.assertTrue(claims.probes(rel(helper, foreign.blockPosition())) == 2, "过期后重新问了一次");
            AABB around = new AABB(machineAbs).inflate(1D);
            created.addAll(level.getEntitiesOfClass(MRUPresenceEntity.class, around, Entity::isAlive));
            helper.assertTrue(created.size() == 1 && created.get(0).getMRU() == 100, "应在机器处另凝一团 100，实际 " + created.size());

            for (int i = 0; i < 3; i++) {
                ECCorruption.increaseAt(level, machineAbs, 10);
                ECCorruption.settle(level);
            }
            helper.assertTrue(level.getEntitiesOfClass(MRUPresenceEntity.class, around, Entity::isAlive).size() == 1
                    && created.get(0).getMRU() == 130, "以后直接汇进机器那一格的这团，不再新建，实际 " + created.get(0).getMRU());
            helper.assertTrue(claims.probes(rel(helper, foreign.blockPosition())) == 2, "不能动的结论同样记着，不再问");

            created.forEach(Entity::discard);
            CorruptionEffects.setTestSafetyOverrides(null, null, null, null, false);
            ECCorruption.increaseAt(level, machineAbs, 50);
            ECCorruption.settle(level);
            helper.assertTrue(foreign.getMRU() == 1250, "闸关着照原作汇进最近的，实际 " + foreign.getMRU());
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
            ECProtection.clearOnlineOverride(ownerId);
            ECProtection.forget(ownerId);
            created.forEach(Entity::discard);
            foreign.discard();
        }
        helper.succeed();
    }
    /**
     * 「MRU 腐化」状态（机器泄漏叠的）默认像中毒一样打不死人：3 点血挨 16 级只剩 1 点、1 点血不再掉；别的模组在 LivingDamageEvent 里把伤害翻倍也只剩 1 点；
     * 打开 mru_corruption_lethal 照原样掉 5 点（mock 玩家最大生命 20，25% 正好 5 点）
     */
    @GameTest(template = EMPTY)
    public static void corruptionMruCorruptionNotLethal(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockSurvivalPlayer();
        try {
            helper.assertFalse(CorruptionEffects.mruCorruptionLethal(), "默认打不死人");
            player.setHealth(3F);
            MRUCorruption.pulse(player, 16);
            helper.assertTrue(player.isAlive() && Math.abs(player.getHealth() - 1F) < 1.0E-4F, "3 点血只掉到 1 点，实际 " + player.getHealth());
            player.invulnerableTime = 0;
            MRUCorruption.pulse(player, 16);
            helper.assertTrue(Math.abs(player.getHealth() - 1F) < 1.0E-4F, "剩 1 点时不再掉，实际 " + player.getHealth());

            // 别的模组在伤害事件里加成（例如按环境伤害加倍的战斗调整模组，LOW 优先级）：伤害真落下时再夹一次，照样剩 1 点
            Consumer<LivingDamageEvent> doubler = event -> {
                if (event.getEntity() == player && event.getSource().is(DamageTypes.MAGIC)) {
                    event.setAmount(event.getAmount() * 2F);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, LivingDamageEvent.class, doubler);
            try {
                player.setHealth(3F);
                player.invulnerableTime = 0;
                MRUCorruption.pulse(player, 16);
                helper.assertTrue(player.isAlive() && Math.abs(player.getHealth() - 1F) < 1.0E-4F,
                        "别的模组把这一下翻倍（2 → 4）也只掉到 1 点，实际 " + player.getHealth());
            } finally {
                MinecraftForge.EVENT_BUS.unregister(doubler);
            }

            CorruptionEffects.setTestMruCorruptionLethal(true);
            player.setHealth(10F);
            player.invulnerableTime = 0;
            MRUCorruption.pulse(player, 16);
            helper.assertTrue(Math.abs(player.getHealth() - 5F) < 1.0E-4F, "打开以后照原样掉 5 点（最大生命 20 的 25%），实际 " + player.getHealth());
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }


    // ---------------------------------------------------------------- 领地：少探测、长记忆

    /** 清场：先结算掉全维度攒着的泄漏，再删掉附近别的测试留下的聚集体（同 corruptionLeakRespectsOwner） */
    private static void clearLeaks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ECCorruption.settle(level);
        level.getEntitiesOfClass(MRUPresenceEntity.class, new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(64D), Entity::isAlive)
                .forEach(Entity::discard);
    }

    /** 在相对坐标处放一团聚集体（进世界，不跳过第一轮） */
    private static MRUPresenceEntity presenceAt(GameTestHelper helper, Vec3 relative, int mru) {
        Vec3 at = helper.absoluteVec(relative);
        MRUPresenceEntity presence = MRUPresenceEntity.create(helper.getLevel(), at.x, at.y, at.z, mru, 1F, false, true);
        helper.getLevel().addFreshEntity(presence);
        return presence;
    }

    /**
     * 绝对坐标换回本测试的相对坐标。原版 1.20.1 的 {@code GameTestHelper.relativePos} 先多转了 180°，不是 {@code absolutePos} 的逆，
     * 不旋转的测试拿它去查格子会查到原点对面；这里的测试都不旋转，直接减掉原点。
     */
    private static BlockPos rel(GameTestHelper helper, BlockPos absolute) {
        return absolute.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    /** 绝对坐标 center 周围 radius 格内活着的聚集体 */
    private static List<MRUPresenceEntity> presencesNear(GameTestHelper helper, BlockPos center, double radius) {
        return helper.getLevel().getEntitiesOfClass(MRUPresenceEntity.class, new AABB(center).inflate(radius), Entity::isAlive);
    }

    /** 正好飘在这一格（绝对坐标）里的聚集体 */
    private static List<MRUPresenceEntity> presencesIn(GameTestHelper helper, BlockPos cell) {
        return helper.getLevel().getEntitiesOfClass(MRUPresenceEntity.class, new AABB(cell), e -> e.isAlive() && e.blockPosition().equals(cell));
    }

    /** 数这几名玩家身上发出的破坏事件（含探测、含被取消的），用完 close 摘掉 */
    private static final class BreakCounter implements AutoCloseable {
        private final Set<UUID> players;
        private final Consumer<BlockEvent.BreakEvent> listener;
        private int count;

        BreakCounter(UUID... players) {
            this.players = new HashSet<>(List.of(players));
            this.listener = event -> {
                if (event.getPlayer() != null && this.players.contains(event.getPlayer().getUUID())) {
                    count++;
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, BlockEvent.BreakEvent.class, listener);
        }

        int count() {
            return count;
        }

        @Override
        public void close() {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
    }

    /**
     * 「MRU 腐化」只叠给漏的那个人，而且不为此探测任何格子：机器 → 主人（模拟领地把周围全拒了也照叠），站在旁边的访客不叠；
     * 仪式 → 丢东西的人；发射器吐的（没有人）谁都不叠；别的模组直接调（既不是机器也没给人）照旧都叠；闸关着照旧都叠。
     */
    @GameTest(template = EMPTY_LARGE, batch = "essentialcraft5_corruption_leak_stack")
    public static void corruptionStackOnlyLeakOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeaks(helper);
        Affliction effect = Affliction.MRU_CORRUPTION;
        BlockPos machinePos = new BlockPos(2, 1, 2);
        helper.setBlock(machinePos, ECRadiatingChamber.RADIATING_CHAMBER.get());
        MRUBlockEntity machine = (MRUBlockEntity) helper.getBlockEntity(machinePos);
        UUID ownerId = UUID.randomUUID();
        ServerPlayer owner = ProtectionFrameworkTests.realPlayer(helper, ownerId, "ec5-stack");
        ServerPlayer visitor = ProtectionFrameworkTests.realPlayer(helper);
        machine.setOwner(ownerId, "ec5-stack");
        ECProtection.setOnlineOverride(owner);
        BlockPos machineAbs = helper.absolutePos(machinePos);
        BlockPos ritualAbs = helper.absolutePos(new BlockPos(8, 1, 8));
        List<ServerPlayer> players = List.of(owner, visitor);
        BoundingBox everything = new BoundingBox(machineAbs.getX() - 40, machineAbs.getY() - 20, machineAbs.getZ() - 40,
                machineAbs.getX() + 40, machineAbs.getY() + 20, machineAbs.getZ() + 40);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyBox(everything);
             BreakCounter counter = new BreakCounter(ownerId, visitor.getUUID())) {
            CorruptionEffects.setTestSafetyOverrides(null, null, null, null, true);
            owner.moveTo(machineAbs.getX() + 1.5D, machineAbs.getY(), machineAbs.getZ() + 0.5D, 0F, 0F);
            visitor.moveTo(machineAbs.getX() - 1.5D, machineAbs.getY(), machineAbs.getZ() + 0.5D, 0F, 0F);
            for (int i = 0; i < 2; i++) {   // 第二轮机器那一格已经有聚集体，泄漏直接汇进去
                ECCorruption.increaseAt(level, machineAbs, 10);
                ECCorruption.settle(level, players);
            }
            helper.assertTrue(Afflictions.has(owner, effect), "机器的主人照叠（领地全拒也不影响）");
            helper.assertFalse(Afflictions.has(visitor, effect), "站在旁边的访客不叠");
            helper.assertTrue(counter.count() == 0, "叠 MRU 腐化、汇进机器那一格的聚集体都不探测，实际发了 " + counter.count() + " 次破坏事件");

            Afflictions.clear(owner);
            owner.moveTo(ritualAbs.getX() + 1.5D, ritualAbs.getY(), ritualAbs.getZ() + 0.5D, 0F, 0F);
            visitor.moveTo(ritualAbs.getX() - 1.5D, ritualAbs.getY(), ritualAbs.getZ() + 0.5D, 0F, 0F);
            ECCorruption.increaseAt(level, ritualAbs, 10, visitor);
            ECCorruption.settle(level, players);
            helper.assertTrue(Afflictions.has(visitor, effect) && !Afflictions.has(owner, effect), "仪式只叠给丢东西的人");

            Afflictions.clear(visitor);
            ECCorruption.increaseAt(level, ritualAbs, 10, null);
            ECCorruption.settle(level, players);
            helper.assertTrue(!Afflictions.has(visitor, effect) && !Afflictions.has(owner, effect), "发射器吐的（没有人）谁都不叠");

            ECCorruption.increaseAt(level, ritualAbs, 10);
            ECCorruption.settle(level, players);
            helper.assertTrue(Afflictions.has(visitor, effect) && Afflictions.has(owner, effect), "别的模组直接调的照旧都叠");

            Afflictions.clear(owner);
            Afflictions.clear(visitor);
            owner.moveTo(machineAbs.getX() + 1.5D, machineAbs.getY(), machineAbs.getZ() + 0.5D, 0F, 0F);
            visitor.moveTo(machineAbs.getX() - 1.5D, machineAbs.getY(), machineAbs.getZ() + 0.5D, 0F, 0F);
            CorruptionEffects.setTestSafetyOverrides(null, null, null, null, false);
            ECCorruption.increaseAt(level, machineAbs, 10);
            ECCorruption.settle(level, players);
            helper.assertTrue(Afflictions.has(visitor, effect) && Afflictions.has(owner, effect), "闸关着照原作都叠");
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
            ECProtection.clearOnlineOverride(ownerId);
            ECProtection.forget(ownerId);
            ECProtection.forget(visitor.getUUID());
            presencesNear(helper, machineAbs, 40).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * 最近的那团（诱饵）不能动时不在机器旁另凝，而是往远一点的、主人能动的那团里汇；两团的结论都记在机器上，之后不再问；
     * 主人连自己那团也动不了（记录过期、重新问）时在机器那一格凝一团，以后直接汇进它、不再问、不再新建；
     * 没主人的机器（多人服）第一次在机器那一格凝一团，之后一直汇进这团，实体数不变。
     */
    @GameTest(template = EMPTY_LARGE, batch = "essentialcraft5_corruption_leak_decoy")
    public static void corruptionLeakSkipsDecoy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeaks(helper);
        BlockPos machinePos = new BlockPos(2, 1, 2);
        helper.setBlock(machinePos, ECRadiatingChamber.RADIATING_CHAMBER.get());
        MRUBlockEntity machine = (MRUBlockEntity) helper.getBlockEntity(machinePos);
        UUID ownerId = UUID.randomUUID();
        ServerPlayer owner = ProtectionFrameworkTests.realPlayer(helper, ownerId, "ec5-decoy");
        machine.setOwner(ownerId, "ec5-decoy");
        ECProtection.setOnlineOverride(owner);
        BlockPos machineAbs = helper.absolutePos(machinePos);
        MRUPresenceEntity decoy = presenceAt(helper, new Vec3(5.5D, 2.5D, 2.5D), 1);
        MRUPresenceEntity own = presenceAt(helper, new Vec3(9.5D, 2.5D, 9.5D), 1000);
        BlockPos decoyRel = rel(helper, decoy.blockPosition());
        BlockPos ownRel = rel(helper, own.blockPosition());
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyAt(decoy.blockPosition())) {
            CorruptionEffects.setTestSafetyOverrides(null, null, null, null, true);
            int before = presencesNear(helper, machineAbs, 40).size();
            ECCorruption.increaseAt(level, machineAbs, 100);
            ECCorruption.settle(level);
            helper.assertTrue(own.getMRU() == 1100 && decoy.getMRU() == 1,
                    "更近的诱饵不能动：跳过它，汇进主人能动的那团，实际 " + own.getMRU() + " / " + decoy.getMRU());
            helper.assertTrue(presencesNear(helper, machineAbs, 40).size() == before, "不在机器旁另凝");
            helper.assertTrue(claims.probes(decoyRel) == 1 && claims.probes(ownRel) == 1, "两团各问一次");
            for (int i = 0; i < 3; i++) {
                ECCorruption.increaseAt(level, machineAbs, 100);
                ECCorruption.settle(level);
            }
            helper.assertTrue(own.getMRU() == 1400, "照旧汇进自己那团，实际 " + own.getMRU());
            helper.assertTrue(claims.probes(decoyRel) == 1 && claims.probes(ownRel) == 1,
                    "结论记在机器上：之后不再问（不能动的诱饵也不再问），实际 " + claims.probes(decoyRel) + " / " + claims.probes(ownRel));
            helper.assertTrue(presencesNear(helper, machineAbs, 40).size() == before, "实体数不变");

            claims.denyAt(own.blockPosition());
            ECProtection.forget(ownerId);
            machine.leakFeedRecord().ageForTest(ECCorruption.FEED_RECORD_TICKS);
            ECCorruption.increaseAt(level, machineAbs, 100);
            ECCorruption.settle(level);
            List<MRUPresenceEntity> spawned = presencesIn(helper, machineAbs);
            helper.assertTrue(spawned.size() == 1 && spawned.get(0).getMRU() == 100 && own.getMRU() == 1400,
                    "记录过期、重新问下来两团都不能动：在机器那一格凝一团 100，实际 " + spawned.size());
            int probes = claims.probes(decoyRel) + claims.probes(ownRel);
            helper.assertTrue(probes == 4, "过期后两团各重新问一次，实际 " + probes);
            for (int i = 0; i < 3; i++) {
                ECCorruption.increaseAt(level, machineAbs, 10);
                ECCorruption.settle(level);
            }
            helper.assertTrue(spawned.get(0).getMRU() == 130 && presencesNear(helper, machineAbs, 40).size() == before + 1,
                    "以后直接汇进机器那一格的这团，不再新建，实际 " + spawned.get(0).getMRU());
            helper.assertTrue(claims.probes(decoyRel) + claims.probes(ownRel) == probes, "不再问");

            BlockPos orphanPos = new BlockPos(2, 1, 8);
            helper.setBlock(orphanPos, ECRadiatingChamber.RADIATING_CHAMBER.get());
            BlockPos orphanAbs = helper.absolutePos(orphanPos);
            ECCorruption.increaseAt(level, orphanAbs, 50);
            ECCorruption.settle(level);
            List<MRUPresenceEntity> orphanSpawn = presencesIn(helper, orphanAbs);
            helper.assertTrue(orphanSpawn.size() == 1 && orphanSpawn.get(0).getMRU() == 50, "没主人的机器：谁的都不汇，在机器那一格凝一团");
            int total = presencesNear(helper, machineAbs, 40).size();
            for (int i = 0; i < 3; i++) {
                ECCorruption.increaseAt(level, orphanAbs, 50);
                ECCorruption.settle(level);
            }
            helper.assertTrue(presencesNear(helper, machineAbs, 40).size() == total && orphanSpawn.get(0).getMRU() == 200,
                    "之后一直汇进这团，不反复新建，实际 " + orphanSpawn.get(0).getMRU());
            helper.assertTrue(claims.probes(decoyRel) + claims.probes(ownRel) == probes, "没主人的机器不探测");
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
            ECProtection.clearOnlineOverride(ownerId);
            ECProtection.forget(ownerId);
            presencesNear(helper, machineAbs, 40).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * 主人下线以后：只认记在机器上的「能动」（过期了也认、存盘读回来也认），没有记录就在机器处另凝，不再按「同一区块」照喂；换主人记录作废。
     * 仪式：发射器吐的（没有人）不汇进同一区块里别人的那团，在仪式那一格另凝、以后再吐就汇进这团；丢东西的人在线、附近哪团都动不了时同样另凝。
     */
    @GameTest(template = EMPTY_LARGE, batch = "essentialcraft5_corruption_leak_offline")
    public static void corruptionLeakOfflineAndRitual(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearLeaks(helper);
        BlockPos machinePos = new BlockPos(2, 1, 2);
        helper.setBlock(machinePos, ECRadiatingChamber.RADIATING_CHAMBER.get());
        MRUBlockEntity machine = (MRUBlockEntity) helper.getBlockEntity(machinePos);
        UUID ownerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ServerPlayer owner = ProtectionFrameworkTests.realPlayer(helper, ownerId, "ec5-offline");
        ServerPlayer actor = ProtectionFrameworkTests.realPlayer(helper, actorId, "ec5-ritual");
        machine.setOwner(ownerId, "ec5-offline");
        ECProtection.setOnlineOverride(owner);
        BlockPos machineAbs = helper.absolutePos(machinePos);
        MRUPresenceEntity own = presenceAt(helper, new Vec3(8.5D, 2.5D, 8.5D), 1000);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            CorruptionEffects.setTestSafetyOverrides(null, null, null, null, true);
            ECCorruption.increaseAt(level, machineAbs, 100);
            ECCorruption.settle(level);
            helper.assertTrue(own.getMRU() == 1100 && machine.leakFeedRecord().size() == 1, "主人在线：问过、汇进去、记在机器上");

            ECProtection.clearOnlineOverride(ownerId);
            ECCorruption.increaseAt(level, machineAbs, 100);
            ECCorruption.settle(level);
            helper.assertTrue(own.getMRU() == 1200, "主人下线：认记着的「能动」，实际 " + own.getMRU());
            machine.leakFeedRecord().ageForTest(ECCorruption.FEED_RECORD_TICKS * 2L);
            ECCorruption.increaseAt(level, machineAbs, 100);
            ECCorruption.settle(level);
            helper.assertTrue(own.getMRU() == 1300, "主人不在线时过期的「能动」也认，实际 " + own.getMRU());

            CompoundTag saved = machine.saveWithoutMetadata();
            helper.assertFalse(machine.getUpdateTag().contains(LeakFeedRecord.TAG), "记录不发给客户端");
            machine.leakFeedRecord().clear();
            machine.load(saved);
            helper.assertTrue(machine.leakFeedRecord().size() == 1, "记录存盘、读回（重启以后还在）");
            ECCorruption.increaseAt(level, machineAbs, 100);
            ECCorruption.settle(level);
            helper.assertTrue(own.getMRU() == 1400, "读回来的记录照样认，实际 " + own.getMRU());

            machine.leakFeedRecord().clear();
            ECCorruption.increaseAt(level, machineAbs, 100);
            ECCorruption.settle(level);
            List<MRUPresenceEntity> spawned = presencesIn(helper, machineAbs);
            helper.assertTrue(own.getMRU() == 1400 && spawned.size() == 1 && spawned.get(0).getMRU() == 100,
                    "主人不在线、没有记录：不汇进别处，在机器那一格另凝一团，实际 " + own.getMRU() + " / " + spawned.size());
            spawned.forEach(Entity::discard);

            ECProtection.setOnlineOverride(owner);
            ECCorruption.increaseAt(level, machineAbs, 100);
            ECCorruption.settle(level);
            helper.assertTrue(own.getMRU() == 1500 && machine.leakFeedRecord().size() == 1, "主人回来：重新问、重新记");
            machine.setOwner(UUID.randomUUID(), "ec5-other");
            helper.assertTrue(machine.leakFeedRecord().isEmpty(), "换了主人：以前那个人问出来的记录作废");

            BlockPos ritualAbs = helper.absolutePos(new BlockPos(6, 1, 6));
            Vec3 sameChunk = new Vec3((ritualAbs.getX() & ~15) + 8.5D, ritualAbs.getY() + 2.5D, (ritualAbs.getZ() & ~15) + 8.5D);
            MRUPresenceEntity neighbour = MRUPresenceEntity.create(level, sameChunk.x, sameChunk.y, sameChunk.z, 1000, 1F, false, true);
            level.addFreshEntity(neighbour);
            ECCorruption.increaseAt(level, ritualAbs, 5000, null);
            ECCorruption.settle(level);
            List<MRUPresenceEntity> ritualSpawn = presencesIn(helper, ritualAbs);
            helper.assertTrue(neighbour.getMRU() == 1000 && ritualSpawn.size() == 1 && ritualSpawn.get(0).getMRU() == 5000,
                    "发射器吐的：同一区块里的那团也不汇，在仪式那一格另凝，实际 " + neighbour.getMRU() + " / " + ritualSpawn.size());
            ECCorruption.increaseAt(level, ritualAbs, 100, null);
            ECCorruption.settle(level);
            helper.assertTrue(ritualSpawn.get(0).getMRU() == 5100 && neighbour.getMRU() == 1000, "以后再吐就汇进仪式那一格的这团");

            ECProtection.setOnlineOverride(actor);
            claims.denyBox(new BoundingBox(machineAbs.getX() - 40, machineAbs.getY() - 20, machineAbs.getZ() - 40,
                    machineAbs.getX() + 40, machineAbs.getY() + 20, machineAbs.getZ() + 40));
            BlockPos ritual2 = helper.absolutePos(new BlockPos(4, 1, 8));
            ECCorruption.increaseAt(level, ritual2, 100, actor);
            ECCorruption.settle(level);
            List<MRUPresenceEntity> ritual2Spawn = presencesIn(helper, ritual2);
            helper.assertTrue(ritual2Spawn.size() == 1 && ritual2Spawn.get(0).getMRU() == 100 && ritualSpawn.get(0).getMRU() == 5100
                    && neighbour.getMRU() == 1000 && own.getMRU() == 1500, "丢东西的人哪团都动不了：在仪式那一格另凝");
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
            ECProtection.clearOnlineOverride(ownerId);
            ECProtection.clearOnlineOverride(actorId);
            ECProtection.forget(ownerId);
            ECProtection.forget(actorId);
            presencesNear(helper, machineAbs, 40).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * 聚集体伤人那一关的长记忆：同一人对同一团的结论记 5 分钟（探测本身的缓存清掉了也不再问），领地刚变的这几分钟照记着的；
     * 过期后重新问，不能动的结论同样记着；聚集体换了格子重新问；下线忘掉
     */
    @GameTest(template = EMPTY)
    public static void corruptionPresenceClaimMemory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 2.5D, 2.5D));
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, at.x, at.y, at.z, 20000, 1F, false, true);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.moveTo(at.x + 2D, at.y - 1D, at.z, 0F, 0F);
        IPlayerData data = data(helper, player);
        int perCycle = MRUPresenceEntity.matrixDamage(2, 1F);
        BlockPos cell = rel(helper, presence.blockPosition());
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            CorruptionEffects.setTestSafetyOverrides(null, null, null, null, true);
            presence.runCycle(List.of(player));
            helper.assertTrue(data.getMatrixDamage() == perCycle && claims.probes(cell) == 1, "第一次：问一次、加上");
            ECProtection.forget(player.getUUID());
            presence.runCycle(List.of(player));
            helper.assertTrue(data.getMatrixDamage() == 2 * perCycle && claims.probes(cell) == 1,
                    "5 分钟内同一人只问一次（探测本身的缓存清掉了也不问），实际问了 " + claims.probes(cell));

            claims.denyAt(presence.blockPosition());
            ECProtection.forget(player.getUUID());
            presence.runCycle(List.of(player));
            helper.assertTrue(data.getMatrixDamage() == 3 * perCycle && claims.probes(cell) == 1, "记着的这几分钟照旧");
            PresenceClaimMemory.ageForTest(PresenceClaimMemory.REMEMBER_TICKS);
            presence.runCycle(List.of(player));
            helper.assertTrue(data.getMatrixDamage() == 3 * perCycle && claims.probes(cell) == 2, "过期后重新问：不能动就不加");
            ECProtection.forget(player.getUUID());
            presence.runCycle(List.of(player));
            helper.assertTrue(data.getMatrixDamage() == 3 * perCycle && claims.probes(cell) == 2, "不能动的结论也记着，不再问");

            presence.moveTo(at.x + 1D, at.y, at.z, 0F, 0F);
            BlockPos moved = rel(helper, presence.blockPosition());
            presence.runCycle(List.of(player));
            helper.assertTrue(claims.probes(moved) == 1 && data.getMatrixDamage() == 4 * perCycle, "聚集体换了格子：重新问");

            int before = PresenceClaimMemory.size();
            PresenceClaimMemory.onPresenceClaimLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            helper.assertTrue(PresenceClaimMemory.size() == before - 1, "下线忘掉这个人的结论");
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
            ECProtection.forget(player.getUUID());
            PresenceClaimMemory.forget(player.getUUID());
            presence.discard();
        }
        helper.succeed();
    }

    /**
     * #13 本人那一下被拦下（纯 Forge 上 LivingAttackEvent 被取消；混合端插件取消伤害事件同理，hurt 返回 false）时不掀人，温和、不温和都一样；
     * 没被拦下时照旧掀起来
     */
    @GameTest(template = EMPTY_LARGE)
    public static void corruptionSelfBlastBlockedNoKnockback(GameTestHelper helper) {
        ServerPlayer player = victim(helper, new Vec3(5.5D, 1D, 5.5D));
        IPlayerData data = data(helper, player);
        Consumer<LivingAttackEvent> blocker = event -> {
            if (event.getEntity() == player && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, blocker);
        boolean registered = true;
        try {
            for (boolean gentle : new boolean[]{true, false}) {
                CorruptionEffects.setTestSafetyOverrides(null, null, null, gentle, null);
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = false;
                float before = player.getHealth();
                helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_13")), "#13 应发作");
                helper.assertTrue(player.getHealth() == before, "被拦下：不掉血");
                helper.assertTrue(player.getDeltaMovement().lengthSqr() < 1.0E-9D && !player.hurtMarked,
                        (gentle ? "温和" : "不温和") + "：被拦下的那一下不掀人，实际速度 " + player.getDeltaMovement());
            }
            MinecraftForge.EVENT_BUS.unregister(blocker);
            registered = false;
            for (boolean gentle : new boolean[]{true, false}) {
                CorruptionEffects.setTestSafetyOverrides(null, null, null, gentle, null);
                player.invulnerableTime = 0;
                player.setHealth(player.getMaxHealth());   // 本人那一下是最大生命的 60%，温和那一下掉过一半以后不补满就会被不温和那一下炸死
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = false;
                float before = player.getHealth();
                helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_13")), "#13 应发作");
                helper.assertTrue(player.getHealth() < before && player.getDeltaMovement().y > 0D && player.hurtMarked,
                        (gentle ? "温和" : "不温和") + "：没被拦下照旧挨炸、被掀起来，实际速度 " + player.getDeltaMovement());
            }
        } finally {
            if (registered) {
                MinecraftForge.EVENT_BUS.unregister(blocker);
            }
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    /**
     * 提示跟着配置走：第一次有矩阵损伤的说明只发一次、腐化效果总开关关着时不发；聚集体的动作栏提醒同一个玩家对象 5 分钟内只一次；
     * 升档那句话在矩阵类不抽的世界里换成只讲身体、心智类的那句
     */
    @GameTest(template = EMPTY)
    public static void corruptionNoticesFollowSwitches(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        ServerPlayer quiet = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData quietData = data(helper, quiet);
        try {
            data.setMatrixDamage(100);
            helper.assertTrue(CorruptionTicker.updateGuideProgress(player, data), "第一次有损伤：发说明");
            helper.assertFalse(CorruptionTicker.updateGuideProgress(player, data), "只发一次");

            CorruptionEffects.setTestEffectsEnabled(false);
            quietData.setMatrixDamage(100);
            helper.assertFalse(CorruptionTicker.updateGuideProgress(quiet, quietData), "腐化效果关着：不说「会凝成腐化效果」");
            helper.assertTrue(advancementDone(quiet, CorruptionTicker.STEP_MATRIX_DAMAGE), "手册照样打勾");
            CorruptionEffects.setTestEffectsEnabled(null);

            helper.assertTrue(CorruptionTicker.noticePresence(player), "动作栏第一次提醒");
            helper.assertFalse(CorruptionTicker.noticePresence(player), "5 分钟内同一个玩家对象不再提醒");

            CorruptionEffects.setTestOverrides(true, null);
            helper.assertTrue(CorruptionGate.unlockedKey().equals(CorruptionGate.UNLOCKED_KEY + "first"), "矩阵类开着：第一次打开说什么都可能凝成，含矩阵类");
            CorruptionEffects.setTestOverrides(false, null);
            helper.assertTrue(CorruptionGate.unlockedKey().equals(CorruptionGate.UNLOCKED_KEY + "first_no_matrix"),
                    "矩阵类不抽：换成只讲身体、心智类的那句");
        } finally {
            CorruptionEffects.setTestOverrides(null, null);
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    // ================================================================ 加强：门槛、概率、扣血

    /**
     * 抽取门槛 = 成本 × draw_threshold_multiplier（默认 3，向上取整）：矩阵类 5999 时没有候选、6000 时正好 matrix_16；
     * 抽中扣的是门槛不是成本；倍率改成 1.0001 时向上取整（2000 → 2001）；最贵的门槛 21.6 万在默认损伤上限 28.8 万以内
     */
    @GameTest(template = EMPTY)
    public static void corruptionDrawThresholdMultiplier(GameTestHelper helper) {
        helper.assertTrue(CorruptionEffects.drawThresholdMultiplier() == 3D, "默认门槛倍率 3，实际 " + CorruptionEffects.drawThresholdMultiplier());
        helper.assertTrue(CorruptionEffects.drawThreshold(effect("matrix_16")) == 6000 && CorruptionEffects.drawThreshold(effect("body_0")) == 18000
                        && CorruptionEffects.drawThreshold(effect("matrix_12")) == 216000, "门槛：2000 → 6000、6000 → 18000、72000 → 216000");
        helper.assertTrue(CorruptionEffects.maxMatrixDamage() >= 216000, "默认损伤上限要够得着最贵的门槛");

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = data(helper, player);
        RandomSource random = RandomSource.create(20261016L);
        try {
            CorruptionEffects.setTestOverrides(true, List.of());
            CorruptionEffects.setTestSafetyOverrides(true, 0, null, null, null);
            helper.assertTrue(CorruptionEffects.drawCandidates(5999, List.of()).isEmpty(), "5999 够不到任何门槛（最低 matrix_16 的 6000）");
            List<CorruptionEffects.Effect> first = CorruptionEffects.drawCandidates(6000, List.of());
            helper.assertTrue(first.size() == 1 && first.get(0).id().equals("matrix_16"), "6000 时只有 matrix_16，实际 " + first);

            data.setPentacleTier(0);
            data.setMatrixDamage(6000);
            CorruptionEffects.Effect drawn = null;
            for (int i = 0; i < 4000 && drawn == null; i++) {
                drawn = CorruptionTicker.draw(player, data, random);
            }
            helper.assertTrue(drawn != null && drawn.id().equals("matrix_16"), "6000 时（约 1/6 的几率）4000 次内应抽到 matrix_16，实际 " + drawn);
            helper.assertTrue(data.getMatrixDamage() == 0, "扣的是门槛 6000 不是成本 2000，实际剩 " + data.getMatrixDamage());

            CorruptionEffects.setTestStrength(1.0001D, null, null);
            helper.assertTrue(CorruptionEffects.drawThreshold(effect("matrix_16")) == 2001, "向上取整：2000 × 1.0001 → 2001");
            CorruptionEffects.setTestStrength(1D, null, null);
            helper.assertTrue(CorruptionEffects.drawThreshold(effect("matrix_16")) == 2000, "倍率 1 回到成本");
        } finally {
            CorruptionEffects.setTestOverrides(null, null);
            CorruptionEffects.clearTestSafetyOverrides();
        }
        helper.succeed();
    }

    /**
     * 发作概率 × effect_chance_multiplier（默认 2，封顶 1）：matrix_16 原作每秒 1%，掷到 1.5% 时默认发作、倍率 1 时不发作；
     * 扣血 × effect_damage_multiplier（默认 1.5）：凋零 7.5% → 11.25%，一下最多扣到整条最大生命；「MRU 腐化」状态不跟着加
     */
    @GameTest(template = EMPTY)
    public static void corruptionEffectStrengthMultipliers(GameTestHelper helper) {
        helper.assertTrue(CorruptionEffects.effectChanceMultiplier() == 2D && CorruptionEffects.effectDamageMultiplier() == 1.5D,
                "默认概率 ×2、扣血 ×1.5");
        ServerPlayer player = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        IPlayerData data = data(helper, player);
        try {
            RandomSource roll = new FixedFloatRandom(0.015F);
            data.setMatrixDamage(100);
            helper.assertTrue(CorruptionBehaviors.tick(player, data, effect("matrix_16"), roll) && data.getMatrixDamage() == 101,
                    "默认 2%：掷到 1.5% 发作，损伤 +1，实际 " + data.getMatrixDamage());
            CorruptionEffects.setTestStrength(null, 1D, null);
            helper.assertFalse(CorruptionBehaviors.tick(player, data, effect("matrix_16"), roll), "倍率 1（原作 1%）：掷到 1.5% 不发作");
            CorruptionEffects.setTestStrength(null, 1000D, null);
            helper.assertTrue(CorruptionEffects.effectChance(effect("matrix_16")) == 1F, "概率封顶 1");
            CorruptionEffects.setTestStrength(null, 0D, null);
            helper.assertFalse(CorruptionBehaviors.tick(player, data, effect("matrix_16"), new FixedFloatRandom(0.5F)), "倍率 0：概率 0 的不发作");

            // 扣血：最大 1000 时凋零 #24 默认 7.5% × 1.5
            CorruptionEffects.setTestStrength(null, null, null);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
            helper.assertTrue(near(CorruptionBehaviors.damageOf(player, 24, 0), 112.5F), "凋零 7.5% × 1.5 = 112.5，实际 " + CorruptionBehaviors.damageOf(player, 24, 0));
            helper.assertTrue(near(CorruptionBehaviors.moonlightDamage(1000F, 0), 375F * 1.5F), "满月魔法伤害也 ×1.5");
            helper.assertTrue(near(MRUCorruption.damage(player), 250F), "「MRU 腐化」状态不跟着加：仍是 25%");
            CorruptionEffects.setTestStrength(null, null, 100D);
            helper.assertTrue(near(CorruptionBehaviors.damageOf(player, 24, 0), 1000F), "再大的倍率，一下最多扣到整条最大生命，实际 " + CorruptionBehaviors.damageOf(player, 24, 0));
            CorruptionEffects.setTestStrength(null, null, 0D);
            helper.assertTrue(CorruptionBehaviors.damageOf(player, 24, 0) == 0F, "倍率 0 不扣血");

            // 真打一下：最大 200，凋零 11.25% = 22.5
            CorruptionEffects.setTestStrength(null, null, null);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH);
            refresh(player);
            helper.assertTrue(CorruptionBehaviors.trigger(player, data, effect("matrix_24")), "#24 没有条件，应发作");
            helper.assertTrue(near(player.getHealth(), VICTIM_HEALTH - 22.5F) && player.getLastDamageSource().is(DamageTypes.WITHER),
                    "最大 200：#24 扣 11.25% 凋零，实际生命 " + player.getHealth());
        } finally {
            CorruptionEffects.clearTestSafetyOverrides();
        }
        refresh(player);
        helper.succeed();
    }
    // ---------------------------------------------------------------- 矩阵后遗症

    /**
     * 后遗症的三个属性修饰符（走得慢 −30%、伤害 −50%、攻速 −20%）和挖掘速度 ×0.09；叠加规矩同原版效果（同级更短、更低级的不改）；
     * 牛奶去不掉；每 tick 减 1，走完时报告、修饰符跟着拿掉
     */
    @GameTest(template = EMPTY)
    public static void corruptionAfflictionsTickAndModify(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockSurvivalPlayer();
        IPlayerData data = PlayerDataEvents.get(player);
        helper.assertTrue(data != null, "模拟玩家应有玩家数据");
        double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        Afflictions.give(player, Affliction.LEADEN_LEGS, 3, CorruptionBehaviors.DEBUFF_LEVEL);
        Afflictions.give(player, Affliction.WEAK_GRIP, 3, CorruptionBehaviors.DEBUFF_LEVEL);
        Afflictions.give(player, Affliction.HEAVY_HANDS, 3, CorruptionBehaviors.DEBUFF_LEVEL);
        Afflictions.updateModifiers(player, data);
        helper.assertTrue(near((float) player.getAttributeValue(Attributes.MOVEMENT_SPEED), (float) (speed * 0.7D)), "双腿迟缓 II：移速 ×0.7，实际 " + player.getAttributeValue(Attributes.MOVEMENT_SPEED));
        helper.assertTrue(near((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), (float) (damage * 0.5D)), "双手无力 II：攻击 ×0.5，实际 " + player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        helper.assertTrue(near((float) player.getAttributeValue(Attributes.ATTACK_SPEED), (float) (attackSpeed * 0.8D)), "双手迟钝 II：攻速 ×0.8，实际 " + player.getAttributeValue(Attributes.ATTACK_SPEED));
        Afflictions.updateModifiers(player, data);
        helper.assertTrue(near((float) player.getAttributeValue(Attributes.MOVEMENT_SPEED), (float) (speed * 0.7D)), "再补一次不叠");

        helper.assertFalse(Afflictions.give(player, Affliction.LEADEN_LEGS, 2, 1), "同级更短的不改");
        helper.assertFalse(Afflictions.give(player, Affliction.LEADEN_LEGS, 100, 0), "更低级的不改");
        helper.assertTrue(Afflictions.remaining(player, Affliction.LEADEN_LEGS) == 3, "还是 3 tick");

        PlayerEvent.BreakSpeed dig = new PlayerEvent.BreakSpeed(player, Blocks.STONE.defaultBlockState(), 1F, BlockPos.ZERO);
        MinecraftForge.EVENT_BUS.post(dig);
        helper.assertTrue(near(dig.getNewSpeed(), 0.09F), "双手迟钝 II：挖掘 ×0.09，实际 " + dig.getNewSpeed());

        player.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
        helper.assertTrue(data.getAfflictionIds().size() == 3, "牛奶去不掉");
        helper.assertFalse(data.tickAfflictions(), "3 → 2 没走完");
        helper.assertFalse(data.tickAfflictions(), "2 → 1 没走完");
        helper.assertTrue(data.tickAfflictions() && data.getAfflictionIds().isEmpty(), "1 → 0 走完");
        Afflictions.updateModifiers(player, data);
        helper.assertTrue(near((float) player.getAttributeValue(Attributes.MOVEMENT_SPEED), (float) speed)
                && near((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), (float) damage), "走完以后修饰符拿掉");
        PlayerEvent.BreakSpeed free = new PlayerEvent.BreakSpeed(player, Blocks.STONE.defaultBlockState(), 1F, BlockPos.ZERO);
        MinecraftForge.EVENT_BUS.post(free);
        helper.assertTrue(free.getNewSpeed() == 1F, "走完以后挖掘不减");
        helper.succeed();
    }

    /** 后遗症随存档、从末地回来照留，死亡清掉；旧存档里的两个药水效果折算成后遗症（无限时长按一小时）；指令 afflictions clear */
    @GameTest(template = EMPTY)
    public static void corruptionAfflictionsPersistAndConvert(GameTestHelper helper) throws CommandSyntaxException {
        net.minecraft.world.entity.player.Player player = helper.makeMockSurvivalPlayer();
        IPlayerData data = PlayerDataEvents.get(player);
        data.setAffliction(Affliction.PARADOX.id(), 1500, 0);
        data.setAffliction(Affliction.MRU_CORRUPTION.id(), 300, 2);
        IPlayerData copy = PlayerDataEvents.get(helper.makeMockSurvivalPlayer());
        copy.deserializeNBT(data.serializeNBT());
        helper.assertTrue(copy.getAfflictionTicks(Affliction.PARADOX.id()) == 1500 && copy.getAfflictionLevel(Affliction.MRU_CORRUPTION.id()) == 2,
                "存档读回来应一样");

        net.minecraft.world.entity.player.Player back = helper.makeMockSurvivalPlayer();
        PlayerDataEvents.clone(new PlayerEvent.Clone(back, player, false));
        helper.assertTrue(Afflictions.has(back, Affliction.PARADOX), "从末地回来照留");
        net.minecraft.world.entity.player.Player reborn = helper.makeMockSurvivalPlayer();
        PlayerDataEvents.clone(new PlayerEvent.Clone(reborn, player, true));
        helper.assertTrue(PlayerDataEvents.get(reborn).getAfflictionIds().isEmpty(), "死亡清掉");

        net.minecraft.world.entity.player.Player legacy = helper.makeMockSurvivalPlayer();
        legacy.addEffect(new MobEffectInstance(ECMRUCU.MRU_CORRUPTION.get(), 4500, 2));
        legacy.addEffect(new MobEffectInstance(com.flameforge.essentialcraft5.registry.ECCorruption.MINDFOLD_PARADOX.get(), MobEffectInstance.INFINITE_DURATION, 1));
        Afflictions.convertLegacy(legacy);
        helper.assertTrue(legacy.getActiveEffects().isEmpty(), "旧药水效果折算后去掉");
        helper.assertTrue(Afflictions.remaining(legacy, Affliction.MRU_CORRUPTION) == 4500 && Afflictions.level(legacy, Affliction.MRU_CORRUPTION) == 2,
                "MRU 腐化照搬时长和等级，实际 " + Afflictions.remaining(legacy, Affliction.MRU_CORRUPTION));
        helper.assertTrue(Afflictions.remaining(legacy, Affliction.PARADOX) == Afflictions.LEGACY_INFINITE_TICKS, "无限时长按一小时");

        ServerPlayer target = ProtectionFrameworkTests.realPlayer(helper);
        Afflictions.give(target, Affliction.WEAK_GRIP, 400, 1);
        Afflictions.give(target, Affliction.PARADOX, 2000, 0);
        CommandSourceStack source = source(helper);
        helper.assertTrue(CorruptionCommands.clearAfflictions(source, List.of(target)) == 1 && data(helper, target).getAfflictionIds().isEmpty(),
                "afflictions clear 清空");
        boolean failed = false;
        try {
            CorruptionCommands.clearAfflictions(source, List.of(target));
        } catch (CommandSyntaxException e) {
            failed = true;
        }
        helper.assertTrue(failed, "身上没有时报错");
        helper.succeed();
    }
}
