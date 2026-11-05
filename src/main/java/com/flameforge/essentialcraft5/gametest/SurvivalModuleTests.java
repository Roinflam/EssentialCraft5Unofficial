package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.corruption.Affliction;
import com.flameforge.essentialcraft5.corruption.Afflictions;
import com.flameforge.essentialcraft5.corruption.CorruptionBehaviors;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.MatrixFilterEvents;
import com.flameforge.essentialcraft5.item.module.MatrixShuntEvents;
import com.flameforge.essentialcraft5.item.module.ModuleCache;
import com.flameforge.essentialcraft5.item.module.ModuleConfig;
import com.flameforge.essentialcraft5.item.module.ModuleCooldowns;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.item.module.RewindEvents;
import com.flameforge.essentialcraft5.item.module.SurvivalModuleConfig;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECSurvivalModules;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 三块生存模块（第十批 10c-M2，batch10.md §5.11）：回溯 10 条、矩阵过滤 9 条、矩阵分流 6 条。
 * <p>
 * 用真注册的模块（{@link ECSurvivalModules}）装到宅男套装上、穿齐四件。测试玩家不进世界：致命一击有一条走真的 {@code hurt}，别的直接发
 * {@link LivingDeathEvent}（照 CurioSealCoreTests 的写法，不真的死）；快照用 {@link RewindEvents#record} 记在「几秒前」的刻数上。
 * 配置一律用 {@link SurvivalModuleConfig} 的测试覆盖值，在同一段同步代码里设、测、置回；临时挂的监听器只认本测试的玩家，用完摘掉。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class SurvivalModuleTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "survival_modules";
    /** 胸甲里放的 MRU（够救四次） */
    private static final int CHEST_MRU = 200_000;
    /** 快照的落脚格（相对坐标，y = 0 那层摆石头） */
    private static final BlockPos A = new BlockPos(1, 0, 1);
    private static final BlockPos B = new BlockPos(3, 0, 3);
    private static final BlockPos C = new BlockPos(1, 0, 3);

    private SurvivalModuleTests() {
    }

    // ------------------------------------------------------------------ 工具

    /** 穿齐四件宅男盔甲的真玩家（跑掉出生无敌），模块按部位装上去；胸甲自存 chestMru */
    private static ServerPlayer suited(GameTestHelper helper, int chestMru, ModuleType... modules) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-survival");
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            ItemStack stack = new ItemStack(ECComputer.armor().get(type.ordinal()).get());
            for (ModuleType module : modules) {
                if (module.fits(type)) {
                    stack = ArmorModules.install(stack, module);
                    if (stack.isEmpty()) {
                        throw new IllegalStateException("装不上 " + module + " 到 " + type);
                    }
                }
            }
            player.setItemSlot(type.getSlot(), stack);
        }
        setMru(player, chestMru);
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        return player;
    }

    private static void setMru(ServerPlayer player, int mru) {
        player.getItemBySlot(EquipmentSlot.CHEST).getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
    }

    private static int mru(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
    }

    private static IPlayerData data(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家没有玩家数据");
        }
        return data;
    }

    private static int matrix(ServerPlayer player) {
        return data(player).getMatrixDamage();
    }

    /** 开 / 关这名玩家身上那块模块（就地改 NBT，再作废快照） */
    private static void setOn(ServerPlayer player, ArmorItem.Type part, ModuleType type, boolean on) {
        ItemStack stack = player.getItemBySlot(part.getSlot());
        for (ArmorModules.Installed installed : ArmorModules.installed(stack)) {
            if (installed.id().equals(type.id())) {
                ArmorModules.setOn(stack, installed.slot(), on);
            }
        }
        ModuleCache.invalidate(player);
    }

    private static void cleanup(ServerPlayer player) {
        RewindEvents.forgetForTest(player);
        MatrixFilterEvents.forgetForTest(player);
        ModuleCache.invalidate(player);
        player.getAdvancements().stopListening();
    }

    /** 在相对坐标 floor 那格摆上 state（落脚格），头顶两格清空 */
    private static void floor(GameTestHelper helper, BlockPos floor, net.minecraft.world.level.block.state.BlockState state) {
        helper.setBlock(floor, state);
        helper.setBlock(floor.above(), Blocks.AIR.defaultBlockState());
        helper.setBlock(floor.above(2), Blocks.AIR.defaultBlockState());
    }

    /** 落脚格 floor 顶上的站立点（绝对坐标，ECTeleport 的落点：方块列正中、顶面） */
    private static Vec3 standing(GameTestHelper helper, BlockPos floor) {
        BlockPos abs = helper.absolutePos(floor);
        return new Vec3(abs.getX() + 0.5D, abs.getY() + 1D, abs.getZ() + 0.5D);
    }

    /** 让玩家站到 floor 顶上、生命 health，记一条 age 刻以前的快照 */
    private static void snapshot(GameTestHelper helper, ServerPlayer player, BlockPos floor, float health, long age) {
        Vec3 at = standing(helper, floor);
        player.moveTo(at.x, at.y, at.z, 90F, 10F);
        player.setOnGround(true);
        player.setHealth(health);
        RewindEvents.record(player, helper.getLevel().getGameTime() - age);
    }

    /** 站到 floor 顶上（出事的地方） */
    private static void standAt(GameTestHelper helper, ServerPlayer player, BlockPos floor) {
        Vec3 at = standing(helper, floor);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
    }

    /** 不真的死：生命设 0，发一次死亡事件，返回有没有被取消 */
    private static boolean postDeath(ServerPlayer player, DamageSource source) {
        player.setHealth(0F);
        LivingDeathEvent event = new LivingDeathEvent(player, source);
        MinecraftForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) {
            player.setHealth(player.getMaxHealth());
        }
        return event.isCanceled();
    }

    private static boolean near(Vec3 a, Vec3 b) {
        return a.distanceToSqr(b) < 0.01D;
    }

    // ------------------------------------------------------------------ 回溯

    /** 走真的 hurt：致命一击退回 5 秒前那条快照（不是 2 秒前那条），生命、吸收回到当时，火和摔落清掉，扣 5 万 MRU、加 5 000 矩阵损伤、冷却 5 分钟 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void rewindReturnsToSnapshot(GameTestHelper helper) {
        floor(helper, A, Blocks.STONE.defaultBlockState());
        floor(helper, B, Blocks.STONE.defaultBlockState());
        ServerPlayer player = suited(helper, CHEST_MRU, ECSurvivalModules.REWIND);
        try {
            SurvivalModuleConfig.Rewind cfg = SurvivalModuleConfig.rewind();
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
            player.setAbsorptionAmount(4F);
            snapshot(helper, player, A, 14F, 100);
            snapshot(helper, player, B, 20F, 40);
            helper.assertTrue(RewindEvents.historySizeForTest(player) == 2, "应记下两条快照");
            standAt(helper, player, B);
            player.setHealth(6F);
            player.setAbsorptionAmount(0F);
            player.setRemainingFireTicks(100);
            player.fallDistance = 12F;
            player.hurt(spell(player), 1000F);
            helper.assertFalse(player.isDeadOrDying(), "致命一击后应被救下，生命 " + player.getHealth());
            helper.assertTrue(near(player.position(), standing(helper, A)), "应退回 5 秒前站的地方 A，实际 " + player.position());
            helper.assertTrue(player.getHealth() == 14F, "生命应回到当时的 14，实际 " + player.getHealth());
            helper.assertTrue(player.getAbsorptionAmount() == 4F, "伤害吸收应补回 4，实际 " + player.getAbsorptionAmount());
            helper.assertTrue(player.getRemainingFireTicks() <= 0, "火应熄掉");
            helper.assertTrue(player.fallDistance == 0F, "摔落距离应清零");
            helper.assertTrue(mru(player) == CHEST_MRU - cfg.mruCost(), "应扣 " + cfg.mruCost() + " MRU，剩 " + mru(player));
            helper.assertTrue(matrix(player) == cfg.matrixDamage(), "应加 " + cfg.matrixDamage() + " 矩阵损伤，实际 " + matrix(player));
            helper.assertTrue(ModuleCooldowns.remaining(player, ECSurvivalModules.REWIND) == cfg.cooldownTicks(),
                    "应进冷却 " + cfg.cooldownTicks() + " 刻，实际 " + ModuleCooldowns.remaining(player, ECSurvivalModules.REWIND));
            helper.assertTrue(RewindEvents.historySizeForTest(player) == 0, "退回以后旧快照应作废");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** /kill（generic_kill）不救、不扣；对照：同样的快照，普通伤害救得下 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void rewindSkipsKillCommand(GameTestHelper helper) {
        floor(helper, A, Blocks.STONE.defaultBlockState());
        ServerPlayer player = suited(helper, CHEST_MRU, ECSurvivalModules.REWIND);
        try {
            snapshot(helper, player, A, 20F, 100);
            standAt(helper, player, B);
            helper.assertTrue(RewindEvents.attempt(player, player.damageSources().genericKill()) == RewindEvents.Result.KILL_COMMAND, "/kill 应判 KILL_COMMAND");
            helper.assertFalse(postDeath(player, player.damageSources().genericKill()), "/kill 不救");
            helper.assertTrue(mru(player) == CHEST_MRU && matrix(player) == 0 && ModuleCooldowns.ready(player, ECSurvivalModules.REWIND), "/kill 时什么都不扣");
            helper.assertTrue(near(player.position(), standing(helper, B)), "/kill 时不该被传走");
            helper.assertTrue(postDeath(player, player.damageSources().generic()), "对照：普通伤害救得下");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 冷却中不救、MRU 不够不救，都不扣；冷却清掉、电补上以后救得下 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void rewindCooldownAndPower(GameTestHelper helper) {
        floor(helper, A, Blocks.STONE.defaultBlockState());
        ServerPlayer player = suited(helper, CHEST_MRU, ECSurvivalModules.REWIND);
        try {
            SurvivalModuleConfig.Rewind cfg = SurvivalModuleConfig.rewind();
            snapshot(helper, player, A, 20F, 100);
            standAt(helper, player, B);
            ModuleCooldowns.start(player, ECSurvivalModules.REWIND, 200);
            helper.assertTrue(RewindEvents.attempt(player, player.damageSources().generic()) == RewindEvents.Result.COOLDOWN, "冷却中应判 COOLDOWN");
            helper.assertFalse(postDeath(player, player.damageSources().generic()), "冷却中不救");
            helper.assertTrue(mru(player) == CHEST_MRU && matrix(player) == 0, "冷却中不扣");
            ModuleCooldowns.clear(player, ECSurvivalModules.REWIND);
            setMru(player, cfg.mruCost() - 1);
            helper.assertTrue(RewindEvents.attempt(player, player.damageSources().generic()) == RewindEvents.Result.NO_POWER, "差 1 MRU 应判 NO_POWER");
            helper.assertFalse(postDeath(player, player.damageSources().generic()), "MRU 不够不救");
            helper.assertTrue(mru(player) == cfg.mruCost() - 1 && matrix(player) == 0 && ModuleCooldowns.ready(player, ECSurvivalModules.REWIND),
                    "MRU 不够时不扣、不进冷却");
            helper.assertTrue(near(player.position(), standing(helper, B)), "没救时不该被传走");
            setMru(player, cfg.mruCost());
            helper.assertTrue(postDeath(player, player.damageSources().generic()), "电正好够时救得下");
            helper.assertTrue(mru(player) == 0, "正好扣光");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 找不到合格的快照不救、不扣：落脚格是岩浆块、太新的、没着地的都不用；把岩浆块换成石头就救得下 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void rewindNeedsSafeSnapshot(GameTestHelper helper) {
        floor(helper, A, Blocks.MAGMA_BLOCK.defaultBlockState());
        floor(helper, B, Blocks.STONE.defaultBlockState());
        floor(helper, C, Blocks.STONE.defaultBlockState());
        ServerPlayer player = suited(helper, CHEST_MRU, ECSurvivalModules.REWIND);
        try {
            SurvivalModuleConfig.Rewind cfg = SurvivalModuleConfig.rewind();
            // C 那条记在半空（没着地）
            Vec3 air = standing(helper, C).add(0D, 1.5D, 0D);
            RewindEvents.injectForTest(player, new RewindEvents.Snapshot(helper.getLevel().dimension(), air, 0F, 0F, 20F, 0F, false,
                    BlockPos.containing(air).below(), helper.getLevel().getGameTime() - 140));
            snapshot(helper, player, A, 20F, 100);
            snapshot(helper, player, B, 20F, cfg.minAgeTicks() - 1);
            standAt(helper, player, B);
            helper.assertTrue(RewindEvents.attempt(player, player.damageSources().generic()) == RewindEvents.Result.NO_SNAPSHOT,
                    "岩浆块 / 太新 / 没着地都不合格，应判 NO_SNAPSHOT");
            helper.assertFalse(postDeath(player, player.damageSources().generic()), "没有合格的快照不救");
            helper.assertTrue(mru(player) == CHEST_MRU && matrix(player) == 0 && ModuleCooldowns.ready(player, ECSurvivalModules.REWIND), "不救时不扣");
            helper.setBlock(A, Blocks.STONE.defaultBlockState());
            helper.assertTrue(postDeath(player, player.damageSources().generic()), "落脚格换成石头后救得下");
            helper.assertTrue(near(player.position(), standing(helper, A)), "应退回 A，实际 " + player.position());
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 别的监听器（图腾这类免死）先取消了死亡事件：回溯不处理，不传、不扣、不进冷却 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void rewindIgnoresCancelledDeath(GameTestHelper helper) {
        floor(helper, A, Blocks.STONE.defaultBlockState());
        ServerPlayer player = suited(helper, CHEST_MRU, ECSurvivalModules.REWIND);
        Consumer<LivingDeathEvent> saver = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingDeathEvent.class, saver);
        try {
            snapshot(helper, player, A, 20F, 100);
            standAt(helper, player, B);
            player.setHealth(0F);
            LivingDeathEvent event = new LivingDeathEvent(player, player.damageSources().generic());
            MinecraftForge.EVENT_BUS.post(event);
            player.setHealth(player.getMaxHealth());
            helper.assertTrue(event.isCanceled(), "对照：别的监听器取消了死亡");
            helper.assertTrue(near(player.position(), standing(helper, B)), "回溯不该再传送");
            helper.assertTrue(mru(player) == CHEST_MRU && matrix(player) == 0 && ModuleCooldowns.ready(player, ECSurvivalModules.REWIND), "回溯不该扣");
            helper.assertTrue(RewindEvents.historySizeForTest(player) == 1, "快照应还在");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(saver);
            cleanup(player);
        }
        helper.succeed();
    }

    /** 别的维度的快照不用（坐标照样合格也不用）；同维度更早的一条照用；生命不足 1 的按 1 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void rewindSameDimensionOnly(GameTestHelper helper) {
        floor(helper, A, Blocks.STONE.defaultBlockState());
        floor(helper, C, Blocks.STONE.defaultBlockState());
        ServerPlayer player = suited(helper, CHEST_MRU, ECSurvivalModules.REWIND);
        try {
            long now = helper.getLevel().getGameTime();
            BlockPos floorA = helper.absolutePos(A);
            RewindEvents.injectForTest(player, new RewindEvents.Snapshot(Level.NETHER, standing(helper, A), 0F, 0F, 20F, 0F, true, floorA, now - 100));
            standAt(helper, player, B);
            helper.assertTrue(RewindEvents.historySizeForTest(player) == 1, "下界那条应记着");
            helper.assertTrue(RewindEvents.candidates(player, now).isEmpty(), "别的维度的快照不该是候选");
            helper.assertFalse(postDeath(player, player.damageSources().generic()), "只有别的维度的快照时不救");
            helper.assertTrue(mru(player) == CHEST_MRU, "不救时不扣");
            BlockPos floorC = helper.absolutePos(C);
            RewindEvents.injectForTest(player, new RewindEvents.Snapshot(helper.getLevel().dimension(), standing(helper, C), 0F, 0F, 0.5F, 0F, true,
                    floorC, now - 140));
            helper.assertTrue(RewindEvents.historySizeForTest(player) == 1, "换了维度的快照应把旧维度的清掉");
            helper.assertTrue(postDeath(player, player.damageSources().generic()), "同维度的那条救得下");
            helper.assertTrue(near(player.position(), standing(helper, C)), "应退回 C，实际 " + player.position());
            helper.assertTrue(player.getHealth() == 1F, "快照生命不足 1 时按 1，实际 " + player.getHealth());
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** EntityTeleportEvent 被取消（插件 / 别的模组拦传送）：不救、不扣、不进冷却 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void rewindTeleportCancelled(GameTestHelper helper) {
        floor(helper, A, Blocks.STONE.defaultBlockState());
        ServerPlayer player = suited(helper, CHEST_MRU, ECSurvivalModules.REWIND);
        Consumer<EntityTeleportEvent> blocker = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityTeleportEvent.class, blocker);
        try {
            snapshot(helper, player, A, 20F, 100);
            standAt(helper, player, B);
            helper.assertTrue(RewindEvents.attempt(player, player.damageSources().generic()) == RewindEvents.Result.TELEPORT_FAILED, "传送被取消应判 TELEPORT_FAILED");
            helper.assertFalse(postDeath(player, player.damageSources().generic()), "传送被取消不救");
            helper.assertTrue(near(player.position(), standing(helper, B)), "人应留在原地");
            helper.assertTrue(mru(player) == CHEST_MRU && matrix(player) == 0 && ModuleCooldowns.ready(player, ECSurvivalModules.REWIND), "不扣、不进冷却");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(blocker);
            cleanup(player);
        }
        helper.succeed();
    }

    /** 掉进虚空按 rescue_void：关着不救、开着救 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void rewindVoidFollowsConfig(GameTestHelper helper) {
        floor(helper, A, Blocks.STONE.defaultBlockState());
        ServerPlayer player = suited(helper, CHEST_MRU, ECSurvivalModules.REWIND);
        SurvivalModuleConfig.Rewind d = SurvivalModuleConfig.REWIND_DEFAULTS;
        try {
            snapshot(helper, player, A, 20F, 100);
            standAt(helper, player, B);
            SurvivalModuleConfig.setTestRewind(new SurvivalModuleConfig.Rewind(d.secondsBack(), d.minAgeTicks(), d.cooldownTicks(), d.mruCost(),
                    d.matrixDamage(), false));
            boolean savedWhenOff;
            try {
                helper.assertTrue(RewindEvents.attempt(player, player.damageSources().fellOutOfWorld()) == RewindEvents.Result.VOID, "关着时应判 VOID");
                savedWhenOff = postDeath(player, player.damageSources().fellOutOfWorld());
            } finally {
                SurvivalModuleConfig.setTestRewind(null);
            }
            helper.assertFalse(savedWhenOff, "rescue_void 关着时掉进虚空不救");
            helper.assertTrue(mru(player) == CHEST_MRU, "不救时不扣");
            SurvivalModuleConfig.setTestRewind(new SurvivalModuleConfig.Rewind(d.secondsBack(), d.minAgeTicks(), d.cooldownTicks(), d.mruCost(),
                    d.matrixDamage(), true));
            boolean savedWhenOn;
            try {
                savedWhenOn = postDeath(player, player.damageSources().fellOutOfWorld());
            } finally {
                SurvivalModuleConfig.setTestRewind(null);
            }
            helper.assertTrue(savedWhenOn, "rescue_void 开着时掉进虚空救得下");
            helper.assertTrue(near(player.position(), standing(helper, A)), "应退回 A");
        } finally {
            SurvivalModuleConfig.setTestRewind(null);
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 领地探测最多问 2 个落点：6 条落点不同的快照，脚下那格都算别人的领地（落脚格放行，每个落点 2 个破坏事件）——不救、不扣，
     * 收到的破坏事件合计 ≤ 4，第 3 条以后的落点一个事件都没有；把第 2 条的落点放行以后，救到那里
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void rewindProbesAtMostTwoSpots(GameTestHelper helper) {
        BlockPos[] floors = {new BlockPos(0, 0, 0), new BlockPos(2, 0, 0), new BlockPos(4, 0, 0),
                new BlockPos(0, 0, 4), new BlockPos(2, 0, 4), new BlockPos(4, 0, 4)};
        for (BlockPos floor : floors) {
            floor(helper, floor, Blocks.STONE.defaultBlockState());
        }
        ServerPlayer player = suited(helper, CHEST_MRU, ECSurvivalModules.REWIND);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            for (int i = 0; i < floors.length; i++) {
                // 都在「seconds_back 秒前及更早」那一组里，按年龄从小到大就是这里的顺序
                snapshot(helper, player, floors[i], 20F, 100 + 20L * i);
                claims.denyAt(helper.absolutePos(floors[i]));
            }
            standAt(helper, player, C);
            helper.assertTrue(RewindEvents.candidates(player, helper.getLevel().getGameTime()).size() == floors.length, "6 条都应是候选");
            helper.assertTrue(RewindEvents.attempt(player, player.damageSources().generic()) == RewindEvents.Result.NO_SNAPSHOT,
                    "落点都在别人的领地里，应判 NO_SNAPSHOT");
            int total = 0;
            for (int i = 0; i < floors.length; i++) {
                BlockPos floor = helper.absolutePos(floors[i]);
                int events = claims.breaksAt(floor) + claims.breaksAt(floor.above());
                total += events;
                if (i >= RewindEvents.MAX_CLAIM_PROBES) {
                    helper.assertTrue(events == 0, "第 " + (i + 1) + " 条落点不该被探测，收到 " + events + " 个事件");
                }
            }
            helper.assertTrue(total > 0 && total <= 2 * RewindEvents.MAX_CLAIM_PROBES, "一次死亡最多 4 个破坏事件，实际 " + total);
            helper.assertTrue(mru(player) == CHEST_MRU && matrix(player) == 0 && ModuleCooldowns.ready(player, ECSurvivalModules.REWIND), "不救时不扣");
            claims.allowAt(helper.absolutePos(floors[1]));
            ECProtection.forget(player.getUUID());
            helper.assertTrue(postDeath(player, player.damageSources().generic()), "第 2 条的落点放行以后救得下");
            helper.assertTrue(near(player.position(), standing(helper, floors[1])), "应退回第 2 条的落点，实际 " + player.position());
        } finally {
            ECProtection.forget(player.getUUID());
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 快照由每刻的处理记（不靠测试直接塞）：回溯生效、(tickCount + 实体 id) % 20 对齐的那一刻（END 阶段）记一条，没对齐的刻、START 阶段不记；
     * 模块关掉以后对齐的那一刻把快照清空
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void rewindRecordsOnlyWhenActive(GameTestHelper helper) {
        floor(helper, A, Blocks.STONE.defaultBlockState());
        ServerPlayer player = suited(helper, CHEST_MRU, ECSurvivalModules.REWIND);
        try {
            standAt(helper, player, A);
            int aligned = Math.floorMod(-player.getId(), RewindEvents.INTERVAL);
            player.tickCount = aligned;
            RewindEvents.onRewindPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            helper.assertTrue(RewindEvents.historySizeForTest(player) == 1, "对齐的那一刻应记一条，实际 " + RewindEvents.historySizeForTest(player));
            player.tickCount = aligned + 1;
            RewindEvents.onRewindPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            helper.assertTrue(RewindEvents.historySizeForTest(player) == 1, "没对齐的刻不记");
            player.tickCount = aligned + RewindEvents.INTERVAL;
            RewindEvents.onRewindPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
            helper.assertTrue(RewindEvents.historySizeForTest(player) == 1, "START 阶段不记");
            RewindEvents.onRewindPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            helper.assertTrue(RewindEvents.historySizeForTest(player) == 2, "下一个对齐的刻再记一条");
            setOn(player, ArmorItem.Type.HELMET, ECSurvivalModules.REWIND, false);
            player.tickCount = aligned + 2 * RewindEvents.INTERVAL;
            RewindEvents.onRewindPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            helper.assertTrue(RewindEvents.historySizeForTest(player) == 0, "回溯不生效时清空，实际 " + RewindEvents.historySizeForTest(player));
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 矩阵过滤

    /**
     * 虚弱被拦：矩阵损伤 = 秒 ×（等级 + 1）× 20、扣 500；同一个效果在拦下的时间里再来免费，更晚结束只补记多出的几秒，等级更高当新的一个。
     * （中毒、凋零、瞬间伤害、饥饿穿着套装时不拦，见 {@link #filterSkipsSuitNegated}；瞬间效果按 1 秒记也在那一条里测）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void filterBlocksHarmfulEffect(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 10_000, ECSurvivalModules.MATRIX_FILTER);
        try {
            SurvivalModuleConfig.Filter cfg = SurvivalModuleConfig.filter();
            int rate = cfg.matrixPerEffectSecond();
            int cost = cfg.mruPerEffect();
            helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1)), "虚弱 II 10 秒应被拦");
            helper.assertFalse(player.hasEffect(MobEffects.WEAKNESS), "身上不该有虚弱");
            helper.assertTrue(matrix(player) == 10 * 2 * rate, "矩阵损伤应是 10 × 2 × " + rate + "，实际 " + matrix(player));
            helper.assertTrue(mru(player) == 10_000 - cost, "应扣 " + cost + " MRU，剩 " + mru(player));
            helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0)), "拦下的时间里再来一次虚弱 I 也拦");
            helper.assertTrue(matrix(player) == 10 * 2 * rate && mru(player) == 10_000 - cost, "落在拦下的时间里的不收");
            helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 260, 1)), "更晚结束的照拦");
            helper.assertTrue(matrix(player) == 13 * 2 * rate, "只补记多出的 3 秒，实际 " + matrix(player));
            helper.assertTrue(mru(player) == 10_000 - cost, "延长不再收 MRU");
            helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 2)), "等级更高的照拦");
            helper.assertTrue(matrix(player) == 13 * 2 * rate + 2 * 3 * rate, "等级更高当新的一个：+2 × 3 × " + rate + "，实际 " + matrix(player));
            helper.assertTrue(mru(player) == 10_000 - 2 * cost, "等级更高的再收一次 MRU");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 有益、中性的效果不拦、不收 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void filterIgnoresBeneficial(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 10_000, ECSurvivalModules.MATRIX_FILTER);
        try {
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1)), "生命恢复应照常加上");
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0)), "发光（中性）应照常加上");
            helper.assertTrue(player.hasEffect(MobEffects.REGENERATION) && player.hasEffect(MobEffects.GLOWING), "两个效果都在身上");
            helper.assertTrue(matrix(player) == 0 && mru(player) == 10_000, "不收");
            helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0)), "对照：虚弱被拦");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** unfilterable 标签里的（锚定）、无限时长的不拦、不收 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void filterSkipsUnfilterable(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 10_000, ECSurvivalModules.MATRIX_FILTER);
        try {
            helper.assertTrue(MatrixFilterEvents.isUnfilterable(ECEffects.ANCHORED.get()), "锚定应在 unfilterable 里");
            helper.assertTrue(player.addEffect(new MobEffectInstance(ECEffects.ANCHORED.get(), 200, 0)), "锚定应照常加上");
            helper.assertTrue(player.hasEffect(ECEffects.ANCHORED.get()), "身上有锚定");
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MobEffectInstance.INFINITE_DURATION, 0)),
                    "无限时长的缓慢记不下，照常加上");
            helper.assertTrue(matrix(player) == 0 && mru(player) == 10_000, "不收");
            helper.assertFalse(player.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), 200, 0)), "对照：腐蚀不在标签里，被拦");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 矩阵损伤记不下这一个、MRU 不够：都不拦、不扣；正好记得下、正好付得起时拦 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void filterNeedsRoomAndPower(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 10_000, ECSurvivalModules.MATRIX_FILTER);
        try {
            SurvivalModuleConfig.Filter cfg = SurvivalModuleConfig.filter();
            int need = 10 * 2 * cfg.matrixPerEffectSecond();
            int max = CorruptionEffects.maxMatrixDamage();
            data(player).setMatrixDamage(max - need + 1);
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1)), "差 1 点记不下：虚弱照常加上");
            helper.assertTrue(matrix(player) == max - need + 1 && mru(player) == 10_000, "记不下时不加损伤、不扣 MRU");
            player.removeEffect(MobEffects.WEAKNESS);
            data(player).setMatrixDamage(0);
            setMru(player, cfg.mruPerEffect() - 1);
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1)), "MRU 不够：虚弱照常加上");
            helper.assertTrue(matrix(player) == 0 && mru(player) == cfg.mruPerEffect() - 1, "付不起时不加损伤、不扣 MRU");
            player.removeEffect(MobEffects.WEAKNESS);
            data(player).setMatrixDamage(max - need);
            setMru(player, cfg.mruPerEffect());
            helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1)), "正好记得下、正好付得起：拦");
            helper.assertTrue(matrix(player) == max && mru(player) == 0, "损伤到满格、MRU 扣光，实际 " + matrix(player) + " / " + mru(player));
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 凋零骷髅打出的凋零（同 {@code WitherSkeleton.doHurtTarget} 的 {@code addEffect(凋零 10 秒, 骷髅)}）：宅男胸甲本来就不掉凋零的血，不拦、不收，
     * 凋零照常上身，它打出的 wither 伤害被胸甲整下取消。连续来源换成挖掘疲劳（套装挡不住）：每刻一次的 2 秒落在拦下的时间里，不再收；
     * 过了 21 刻再来一次 10 秒的，只补记多出的 2 秒、不收 MRU
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 60)
    public static void filterWitherSkeletonAndRepeats(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 10_000, ECSurvivalModules.MATRIX_FILTER);
        SurvivalModuleConfig.Filter cfg = SurvivalModuleConfig.filter();
        int rate = cfg.matrixPerEffectSecond();
        WitherSkeleton skeleton = helper.spawnWithNoFreeWill(EntityType.WITHER_SKELETON, new BlockPos(3, 1, 3));
        try {
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200), skeleton), "凋零骷髅给的凋零套装本来就挡住：不拦，照常上身");
            helper.assertTrue(player.hasEffect(MobEffects.WITHER), "身上有凋零");
            helper.assertTrue(matrix(player) == 0 && mru(player) == 10_000, "不拦就不收：矩阵损伤 " + matrix(player) + "、MRU " + mru(player));
            float health = player.getHealth();
            player.invulnerableTime = 0;
            helper.assertFalse(player.hurt(player.damageSources().wither(), 1F), "凋零打出的 wither 伤害被胸甲整下取消");
            helper.assertTrue(player.getHealth() == health, "生命不变，实际 " + player.getHealth());
            player.removeEffect(MobEffects.WITHER);
            helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200)), "挖掘疲劳 10 秒应被拦");
            helper.assertTrue(matrix(player) == 10 * rate && mru(player) == 10_000 - cfg.mruPerEffect(), "10 秒 × 1 × " + rate + "、扣一次 MRU");
            for (int i = 0; i < 20; i++) {
                helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40)), "每刻一次的 2 秒挖掘疲劳也拦");
            }
            helper.assertTrue(matrix(player) == 10 * rate && mru(player) == 10_000 - cfg.mruPerEffect(), "落在拦下的时间里的 20 次都不收");
        } catch (RuntimeException e) {
            cleanup(player);
            throw e;
        } finally {
            skeleton.discard();
        }
        helper.runAfterDelay(21, () -> {
            try {
                helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200)), "21 刻后再来 10 秒的挖掘疲劳照拦");
                helper.assertTrue(matrix(player) == 12 * rate, "从第一次算起共 221 刻 = 12 秒，只补记 2 秒，实际 " + matrix(player));
                helper.assertTrue(mru(player) == 10_000 - cfg.mruPerEffect(), "延长不再收 MRU");
            } finally {
                cleanup(player);
            }
            helper.succeed();
        });
    }

    /** 终端里关掉模块后虚弱照常加上；打开后照拦 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void filterOffWhenModuleOff(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 10_000, ECSurvivalModules.MATRIX_FILTER);
        try {
            setOn(player, ArmorItem.Type.HELMET, ECSurvivalModules.MATRIX_FILTER, false);
            helper.assertFalse(ModuleCache.active(player, ECSurvivalModules.MATRIX_FILTER), "关掉后不生效");
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0)), "关着时虚弱照常加上");
            helper.assertTrue(matrix(player) == 0 && mru(player) == 10_000, "关着时不收");
            player.removeEffect(MobEffects.WEAKNESS);
            setOn(player, ArmorItem.Type.HELMET, ECSurvivalModules.MATRIX_FILTER, true);
            helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0)), "打开后照拦");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 套装本来就挡住的不拦、不收：穿齐套装时中毒、饥饿照常上身，药水箭那种经 addEffect 加上来的瞬间伤害（时长 1）判 SUIT_NEGATED，什么都不扣；
     * require_full_set 关掉、只戴头盔不穿胸甲时（创造模式，不看 MRU），中毒、瞬间伤害照常被过滤（瞬间效果按 1 秒记），饥饿仍不拦（头盔管饱）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void filterSkipsSuitNegated(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 10_000, ECSurvivalModules.MATRIX_FILTER);
        try {
            int rate = SurvivalModuleConfig.filter().matrixPerEffectSecond();
            helper.assertTrue(MatrixFilterEvents.filter(player, new MobEffectInstance(MobEffects.POISON, 200, 1)) == MatrixFilterEvents.Result.SUIT_NEGATED,
                    "穿着宅男胸甲：中毒应判 SUIT_NEGATED");
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1)), "中毒照常上身");
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0)), "饥饿照常上身");
            helper.assertTrue(MatrixFilterEvents.filter(player, new MobEffectInstance(MobEffects.HARM, 1, 0)) == MatrixFilterEvents.Result.SUIT_NEGATED,
                    "药水箭式的瞬间伤害应判 SUIT_NEGATED");
            helper.assertTrue(MatrixFilterEvents.filter(player, new MobEffectInstance(MobEffects.WITHER, 200, 0)) == MatrixFilterEvents.Result.SUIT_NEGATED,
                    "凋零应判 SUIT_NEGATED");
            helper.assertTrue(matrix(player) == 0 && mru(player) == 10_000, "套装本来就挡住的什么都不扣：矩阵损伤 " + matrix(player) + "、MRU " + mru(player));
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.HUNGER);

            ModuleConfig.Values d = ModuleConfig.DEFAULTS;
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            player.setGameMode(GameType.CREATIVE);
            ModuleConfig.setTestOverrides(new ModuleConfig.Values(d.disabled(), d.slotsHelmet(), d.slotsChest(), d.slotsLegs(), d.slotsBoots(), false));
            boolean active;
            boolean poisonAdded;
            MatrixFilterEvents.Result harm;
            MatrixFilterEvents.Result hunger;
            try {
                active = ModuleCache.active(player, ECSurvivalModules.MATRIX_FILTER);
                poisonAdded = player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
                harm = MatrixFilterEvents.filter(player, new MobEffectInstance(MobEffects.HARM, 1, 0));
                hunger = MatrixFilterEvents.filter(player, new MobEffectInstance(MobEffects.HUNGER, 200, 0));
            } finally {
                ModuleConfig.setTestOverrides(null);
            }
            helper.assertTrue(active, "不要求穿齐时只戴头盔，矩阵过滤也生效");
            helper.assertFalse(poisonAdded, "没穿胸甲：中毒照常被拦");
            helper.assertTrue(harm == MatrixFilterEvents.Result.FILTERED, "没穿胸甲：瞬间伤害照常被过滤，实际 " + harm);
            helper.assertTrue(matrix(player) == 10 * rate + rate, "中毒 10 秒 + 瞬间伤害按 1 秒，实际 " + matrix(player));
            helper.assertTrue(hunger == MatrixFilterEvents.Result.SUIT_NEGATED, "戴着头盔：饥饿仍不拦，实际 " + hunger);
        } finally {
            ModuleConfig.setTestOverrides(null);
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 腐化效果留下的是矩阵后遗症、不是药水效果，过滤碰不到：过滤开着时腐化 #1 发作，双手无力照常上身，矩阵损伤和 MRU 不变
     * （不然矩阵损伤 → 腐化效果 → 又记回矩阵损伤）；紧接着别处来的虚弱照拦
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void filterSkipsCorruptionDebuff(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 10_000, ECSurvivalModules.MATRIX_FILTER);
        try {
            int rate = SurvivalModuleConfig.filter().matrixPerEffectSecond();
            CorruptionEffects.Effect weakness = CorruptionEffects.byIndex(1);
            helper.assertTrue(weakness != null, "应有腐化效果 #1");
            helper.assertTrue(CorruptionBehaviors.trigger(player, data(player), weakness), "腐化 #1 应发作");
            helper.assertTrue(Afflictions.level(player, Affliction.WEAK_GRIP) == 1 && Afflictions.has(player, Affliction.WEAK_GRIP),
                    "腐化 #1 的双手无力 II 应照常上身");
            helper.assertTrue(matrix(player) == 0 && mru(player) == 10_000, "腐化效果带来的不收：矩阵损伤 " + matrix(player) + "、MRU " + mru(player));
            Afflictions.clear(player);
            helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0)), "紧接着别处来的虚弱照拦");
            helper.assertTrue(matrix(player) == 10 * rate, "照常记 10 秒，实际 " + matrix(player));
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 身上已经挂着同一个效果时：模块关着时中的虚弱 I 60 秒，打开模块以后再来虚弱 I 20 秒——原版本来就不改身上的效果，判 ALREADY，不拦、不收；
     * 再来虚弱 I 90 秒——照拦，只补记多出的 30 秒、不收 MRU，身上那段还是 60 秒
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void filterExistingEffectNotCharged(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 10_000, ECSurvivalModules.MATRIX_FILTER);
        try {
            int rate = SurvivalModuleConfig.filter().matrixPerEffectSecond();
            setOn(player, ArmorItem.Type.HELMET, ECSurvivalModules.MATRIX_FILTER, false);
            helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0)), "模块关着时虚弱 I 60 秒照常上身");
            setOn(player, ArmorItem.Type.HELMET, ECSurvivalModules.MATRIX_FILTER, true);
            helper.assertTrue(MatrixFilterEvents.filter(player, new MobEffectInstance(MobEffects.WEAKNESS, 400, 0)) == MatrixFilterEvents.Result.ALREADY,
                    "身上已经有更长的同级虚弱：应判 ALREADY");
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
            helper.assertTrue(matrix(player) == 0 && mru(player) == 10_000, "不拦、不收：矩阵损伤 " + matrix(player) + "、MRU " + mru(player));
            helper.assertTrue(player.getEffect(MobEffects.WEAKNESS).getDuration() == 1200, "身上那段不变");
            helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1800, 0)), "更长的 90 秒照拦");
            helper.assertTrue(matrix(player) == 30 * rate, "只补记多出的 30 秒，实际 " + matrix(player));
            helper.assertTrue(mru(player) == 10_000, "补记不收 MRU");
            helper.assertTrue(player.getEffect(MobEffects.WEAKNESS).getDuration() == 1200, "拦下以后身上那段还是 60 秒");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 矩阵分流

    /** 记下这名玩家的 LivingHurtEvent（LOWEST，收已取消的）、LivingDamageEvent（LOWEST）最后的伤害数 */
    private static final class Capture implements AutoCloseable {
        final List<Float> hurt = new ArrayList<>();
        final List<Float> damage = new ArrayList<>();
        final List<Float> attack = new ArrayList<>();
        private final Consumer<LivingHurtEvent> onHurt;
        private final Consumer<LivingDamageEvent> onDamage;
        private final Consumer<LivingAttackEvent> onAttack;

        Capture(ServerPlayer player) {
            onHurt = event -> {
                if (event.getEntity() == player && !event.isCanceled()) {
                    hurt.add(event.getAmount());
                }
            };
            onDamage = event -> {
                if (event.getEntity() == player && !event.isCanceled()) {
                    damage.add(event.getAmount());
                }
            };
            onAttack = event -> {
                if (event.getEntity() == player && !event.isCanceled()) {
                    attack.add(event.getAmount());
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingHurtEvent.class, onHurt);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, onDamage);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingAttackEvent.class, onAttack);
        }

        void clear() {
            hurt.clear();
            damage.clear();
            attack.clear();
        }

        float lastHurt() {
            return hurt.isEmpty() ? -1F : hurt.get(hurt.size() - 1);
        }

        @Override
        public void close() {
            MinecraftForge.EVENT_BUS.unregister(onHurt);
            MinecraftForge.EVENT_BUS.unregister(onDamage);
            MinecraftForge.EVENT_BUS.unregister(onAttack);
        }
    }

    /**
     * 一下别人放过来的魔法伤害（indirect_magic，不带实体，每次新建一个伤害源对象）。不用 {@code damageSources().magic()}：宅男胸甲对 magic 类型
     * （自己喝的伤害药水、中毒）整下免疫（{@code ComputerArmorEvents.isImmune}），根本走不到分流；别人扔的喷溅伤害药水是 indirect_magic，照样打得到
     */
    private static DamageSource spell(ServerPlayer player) {
        return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.INDIRECT_MAGIC));
    }

    private static void hit(ServerPlayer player, DamageSource source, float amount) {
        player.invulnerableTime = 0;
        player.setHealth(player.getMaxHealth());
        player.hurt(source, amount);
    }

    /** 按最大生命比例记的矩阵损伤：分走 shunted 点记 ⌈shunted ÷ 最大生命 × matrix_per_max_health⌉ */
    private static long shuntMatrix(double shunted, float maxHealth, SurvivalModuleConfig.Shunt cfg) {
        return (long) Math.ceil(shunted / maxHealth * cfg.matrixPerMaxHealth() - 1.0E-6D);
    }

    /** 魔法伤害分流一半、矩阵损伤按占最大生命的比例记；只剩一点余量时只分流记得下的部分；满了整下照常扣血 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shuntHalvesMagic(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 0, ECSurvivalModules.MATRIX_SHUNT);
        try (Capture capture = new Capture(player)) {
            SurvivalModuleConfig.Shunt cfg = SurvivalModuleConfig.shunt();
            float max = player.getMaxHealth();
            hit(player, spell(player), 10F);
            float expected = (float) (10D * (1D - cfg.ratio()));
            helper.assertTrue(Math.abs(capture.lastHurt() - expected) < 1.0E-4F, "伤害应剩 " + expected + "，实际 " + capture.lastHurt());
            helper.assertTrue(Math.abs(max - player.getHealth() - expected) < 1.0E-4F, "只掉 " + expected + " 血，实际 " + (max - player.getHealth()));
            long want = shuntMatrix(10D * cfg.ratio(), max, cfg);
            helper.assertTrue(matrix(player) == want, "矩阵损伤应是 " + want + "（最大生命 " + max + "），实际 " + matrix(player));
            int cap = CorruptionEffects.maxMatrixDamage();
            // 只剩大约 2 点伤害的余量：只分流记得下的那部分
            int room = (int) Math.round(2D * cfg.matrixPerMaxHealth() / max);
            double fits = room * (double) max / cfg.matrixPerMaxHealth();
            data(player).setMatrixDamage(cap - room);
            capture.clear();
            hit(player, spell(player), 10F);
            helper.assertTrue(Math.abs(capture.lastHurt() - (10D - fits)) < 1.0E-3D,
                    "只剩 " + room + " 点余量：只分流 " + fits + "，伤害剩 " + (10D - fits) + "，实际 " + capture.lastHurt());
            helper.assertTrue(matrix(player) == cap, "损伤到满格，实际 " + matrix(player));
            capture.clear();
            hit(player, spell(player), 10F);
            helper.assertTrue(Math.abs(capture.lastHurt() - 10F) < 1.0E-4F, "满了：整下照常，实际 " + capture.lastHurt());
            helper.assertTrue(matrix(player) == cap, "满了不再加");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 分流按最大生命的比例换算（MatrixShuntEvents.split 纯函数）：原版 20 点生命时一下 10 点记 500（和最早每点 100 一样）；
     * 1000 点生命一下 200 点记 200（每点 100 的话是 10000）；余量只够一部分时只分记得下的；最大生命不是有限正数时按 20 算；换算 0 时只分不记
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shuntScalesWithMaxHealth(GameTestHelper helper) {
        SurvivalModuleConfig.Shunt cfg = SurvivalModuleConfig.SHUNT_DEFAULTS;
        int big = 1_000_000;
        MatrixShuntEvents.Split vanilla = MatrixShuntEvents.split(10F, 20F, big, cfg);
        helper.assertTrue(Math.abs(vanilla.shunted() - 5F) < 1.0E-4F && vanilla.matrix() == 500L,
                "20 血一下 10 点：分 5、记 500，实际 " + vanilla);
        MatrixShuntEvents.Split high = MatrixShuntEvents.split(200F, 1000F, big, cfg);
        helper.assertTrue(Math.abs(high.shunted() - 100F) < 1.0E-3F && high.matrix() == 200L,
                "1000 血一下 200 点：分 100、记 200，实际 " + high);
        MatrixShuntEvents.Split partial = MatrixShuntEvents.split(200F, 1000F, 50, cfg);
        helper.assertTrue(Math.abs(partial.shunted() - 25F) < 1.0E-3F && partial.matrix() == 50L,
                "余量 50：只分 25（50 ÷ 2000 × 1000），实际 " + partial);
        MatrixShuntEvents.Split broken = MatrixShuntEvents.split(10F, Float.NaN, big, cfg);
        helper.assertTrue(broken.matrix() == 500L, "最大生命 NaN 按 20 算：记 500，实际 " + broken);
        MatrixShuntEvents.Split full = MatrixShuntEvents.split(10F, 20F, 0, cfg);
        helper.assertTrue(full.shunted() == 0F && full.matrix() == 0L, "余量 0：不分流，实际 " + full);
        MatrixShuntEvents.Split free = MatrixShuntEvents.split(10F, 20F, big, new SurvivalModuleConfig.Shunt(0.5D, 0));
        helper.assertTrue(Math.abs(free.shunted() - 5F) < 1.0E-4F && free.matrix() == 0L, "换算 0：只分不记，实际 " + free);
        helper.succeed();
    }

    /** 照 Player.hurt 按难度缩放：随难度缩放的伤害，和平 0、简单 min(一半 + 1, 原数)、困难 1.5 倍、普通不变 */
    private static float difficultyScaled(ServerPlayer player, DamageSource source, float amount) {
        if (!source.scalesWithDifficulty()) {
            return amount;
        }
        return switch (player.level().getDifficulty()) {
            case PEACEFUL -> 0F;
            case EASY -> Math.min(amount / 2F + 1F, amount);
            case HARD -> amount * 3F / 2F;
            default -> amount;
        };
    }

    /** 物理伤害（僵尸近战；难度是和平时用普通伤害）不分流、不记 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shuntIgnoresPhysical(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 0, ECSurvivalModules.MATRIX_SHUNT);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        try (Capture capture = new Capture(player)) {
            DamageSource melee = player.damageSources().mobAttack(zombie);
            helper.assertFalse(ECDamageKinds.isMagic(melee), "前提：僵尸近战不是魔法伤害");
            DamageSource used = melee;
            hit(player, melee, 4F);
            if (capture.attack.isEmpty()) {
                // 和平难度下玩家挨怪打按 0 算、根本不发事件：换普通伤害测同一件事
                DamageSource generic = player.damageSources().generic();
                helper.assertFalse(ECDamageKinds.isMagic(generic), "前提：普通伤害不是魔法伤害");
                used = generic;
                hit(player, generic, 4F);
            }
            helper.assertFalse(capture.attack.isEmpty() || capture.hurt.isEmpty(), "应收到攻击和受伤事件");
            // 攻击事件里是原伤害，Player.hurt 随后按难度缩放（简单难度 4 → 3）才发受伤事件：比的是缩放以后的数
            float expected = difficultyScaled(player, used, capture.attack.get(capture.attack.size() - 1));
            helper.assertTrue(capture.lastHurt() == expected,
                    "物理伤害不分流：受伤 " + capture.lastHurt() + " 应等于按难度缩放后的攻击 " + expected + "（攻击 " + capture.attack + "）");
            helper.assertTrue(matrix(player) == 0, "不记矩阵损伤");
            helper.assertFalse(MatrixShuntEvents.hasPendingForTest(player), "没有待记");
        } finally {
            zombie.discard();
            cleanup(player);
        }
        helper.succeed();
    }

    /** LivingAttackEvent 被拦（领地 / PvP 模组）：伤害没打出来，不分流、不记 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shuntAttackCancelledNoRecord(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 0, ECSurvivalModules.MATRIX_SHUNT);
        Consumer<LivingAttackEvent> blocker = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingAttackEvent.class, blocker);
        try {
            float before = player.getHealth();
            helper.assertFalse(player.hurt(spell(player), 10F), "攻击被拦，hurt 返回 false");
            helper.assertTrue(player.getHealth() == before && matrix(player) == 0, "不掉血、不记");
            helper.assertFalse(MatrixShuntEvents.hasPendingForTest(player), "没有待记");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(blocker);
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 伤害没真落下就不记：① LivingDamageEvent 被取消；② 混合端那条路：LivingHurtEvent 在分流之后被取消、照样按 0 发 LivingDamageEvent；
     * ③ 插件拦下（只发了 LivingHurtEvent、没有 LivingDamageEvent）。对照：都不拦时照记。
     * 四步用同一个伤害源对象：同一个人、同一个伤害源再来一下时，上一条没走到 LivingDamageEvent 的待记会被换掉，每一步看到的只有自己那条
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shuntDamageCancelledNoRecord(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 0, ECSurvivalModules.MATRIX_SHUNT);
        DamageSource source = spell(player);
        Consumer<LivingDamageEvent> cancelDamage = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDamageEvent.class, cancelDamage);
        try {
            hit(player, source, 10F);
            helper.assertTrue(matrix(player) == 0, "① LivingDamageEvent 被取消：不记，实际 " + matrix(player));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancelDamage);
        }
        Consumer<LivingHurtEvent> cancelHurt = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, LivingHurtEvent.class, cancelHurt);
        try {
            hit(player, source, 10F);
            helper.assertFalse(MatrixShuntEvents.hasPendingForTest(player), "② 分流后 LivingHurtEvent 被取消：待记应丢掉");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, source, 0F));
            helper.assertTrue(matrix(player) == 0, "② 按 0 发来的 LivingDamageEvent 不记，实际 " + matrix(player));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancelHurt);
        }
        try {
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(player, source, 10F));
            helper.assertTrue(MatrixShuntEvents.hasPendingForTest(player), "③ 只发 LivingHurtEvent：有一条待记");
            helper.assertTrue(matrix(player) == 0, "③ 没有 LivingDamageEvent 就不记");
            hit(player, source, 10F);
            helper.assertTrue(matrix(player) > 0, "对照：都不拦时照记");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 矩阵过滤、矩阵分流同时开着各算各的 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shuntWithFilterBothCount(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 10_000, ECSurvivalModules.MATRIX_FILTER, ECSurvivalModules.MATRIX_SHUNT);
        try {
            SurvivalModuleConfig.Shunt shunt = SurvivalModuleConfig.shunt();
            SurvivalModuleConfig.Filter filter = SurvivalModuleConfig.filter();
            hit(player, spell(player), 10F);
            long fromShunt = shuntMatrix(10D * shunt.ratio(), player.getMaxHealth(), shunt);
            helper.assertTrue(matrix(player) == fromShunt, "分流记 " + fromShunt + "，实际 " + matrix(player));
            helper.assertFalse(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0)), "虚弱被拦");
            long fromFilter = 5L * filter.matrixPerEffectSecond();
            helper.assertTrue(matrix(player) == fromShunt + fromFilter, "两边相加 " + (fromShunt + fromFilter) + "，实际 " + matrix(player));
            helper.assertTrue(mru(player) == 10_000 - filter.mruPerEffect(), "只有过滤收 MRU");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 关掉模块后魔法伤害整下照常、不记；打开后照分流 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void shuntOffWhenModuleOff(GameTestHelper helper) {
        ServerPlayer player = suited(helper, 0, ECSurvivalModules.MATRIX_SHUNT);
        try (Capture capture = new Capture(player)) {
            setOn(player, ArmorItem.Type.CHESTPLATE, ECSurvivalModules.MATRIX_SHUNT, false);
            hit(player, spell(player), 10F);
            helper.assertTrue(Math.abs(capture.lastHurt() - 10F) < 1.0E-4F, "关着时整下照常，实际 " + capture.lastHurt());
            helper.assertTrue(matrix(player) == 0, "关着时不记");
            setOn(player, ArmorItem.Type.CHESTPLATE, ECSurvivalModules.MATRIX_SHUNT, true);
            capture.clear();
            hit(player, spell(player), 10F);
            helper.assertTrue(capture.lastHurt() < 10F && matrix(player) > 0, "打开后照分流");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }
}
