package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.entity.HoloDecoyEntity;
import com.flameforge.essentialcraft5.entity.HoloDecoyEvents;
import com.flameforge.essentialcraft5.item.HoloDecoyProjectorItem;
import com.flameforge.essentialcraft5.registry.ECHoloDecoy;
import com.mojang.authlib.GameProfile;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 全息诱饵（第十批 10b，batch10.md §5.8.2）的 GameTest：
 * <ul>
 *   <li>投影仪右键：诱饵投在准星前的地面上，盯着主人的尸壳（靠目标技能）和猪灵（靠记忆）都改打诱饵，扣 5 000，发手册进度；之后想换回主人的也改成诱饵。</li>
 *   <li>同队玩家被盯的怪也改；不同队的玩家被盯的照旧。</li>
 *   <li>Boss（凋灵）只骗 boss_ticks：期间盯诱饵，到点目标还给主人，之后再盯主人也不改；boss_ticks 0 时 Boss 不上当；
 *       同队几个人的诱饵共用一份被骗时长，接力接不走，过了 max(冷却, boss_ticks) 才能再骗。</li>
 *   <li>{@code decoy_immune}（默认监守者）、起了名字的尸壳不受影响，同场普通尸壳照改。</li>
 *   <li>活够 lifetime 就消失；默认 160 刻。</li>
 *   <li>冷却 600 刻、冷却中按不动不扣；冷却 0 时再投一个，旧的当场消失（每人一个）；对着天空、MRU 不够、生成被取消 / 在加入事件里被移除都不扣、不进冷却。</li>
 *   <li>冷却退出重进不清（持久数据里另记到期刻），登录、从末地回来补回物品冷却，死亡重生清掉。</li>
 *   <li>不存盘、不能召唤。</li>
 *   <li>主人下线（事件当场、或者只是引用被移除也在一刻内）、死了、换维度（事件当场、或者只是已经在别的世界也在一刻内），诱饵消失。</li>
 * </ul>
 * 测试玩家是 {@code ProtectionFrameworkTests.realPlayer}（不在世界里）；怪用 {@code spawnWithNoFreeWill}（不会自己换目标），凋灵、监守者、猪灵再设 {@code NoAI}
 * （不跑它们自己的那套：凋灵放骷髅头、监守者放黑暗、猪灵在主世界变僵尸）。配置覆盖只在同一段同步代码里设、还原——诱饵生成时把寿命、半径、Boss 时长记在自己身上，
 * 跨刻的那几条靠这个。测试世界是共用的，每条都只拦 / 只数自己的东西，用完 discard。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoloDecoyTests {

    private static final String EMPTY = "empty";
    private static final ECHoloDecoy.Config.Values D = ECHoloDecoy.Config.DEFAULTS;

    private HoloDecoyTests() {
    }

    // ================================================================== 骗怪

    /** 右键投出：落在准星前的地面上；尸壳（setTarget）和猪灵（记忆）都改打诱饵；扣 5 000；发进度；再想打主人也改成诱饵 */
    @GameTest(template = EMPTY)
    public static void holoDecoyLuresHostiles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        ServerPlayer owner = player(helper, "ec5-dcy-own", new Vec3(2.5D, 2D, 0.5D), 0F, 45F);
        Husk husk = mob(helper, EntityType.HUSK, new Vec3(1.5D, 2D, 4.5D), false);
        Piglin piglin = mob(helper, EntityType.PIGLIN, new Vec3(3.5D, 2D, 4.5D), true);
        husk.setTarget(owner);
        piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, owner);
        ItemStack stack = projector(HoloDecoyProjectorItem.CAPACITY);
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        List<Entity> cleanup = new ArrayList<>(List.of(husk, piglin));
        try {
            InteractionResult used = stack.use(level, owner, InteractionHand.MAIN_HAND).getResult();
            helper.assertTrue(used == InteractionResult.CONSUME, "应投出诱饵、返回 consume，实际 " + used);
            HoloDecoyEntity decoy = HoloDecoyEntity.of(owner);
            helper.assertTrue(decoy != null && decoy.isActive() && decoy.owner() == owner && owner.getUUID().equals(decoy.ownerId()),
                    "主人名下应有一个开始工作的诱饵");
            cleanup.add(decoy);
            Vec3 at = helper.relativeVec(decoy.position());
            helper.assertTrue(Math.abs(at.y - 2D) < 1.0E-6D && at.z > 1.5D && at.z < 3D && Math.abs(at.x - 2.5D) < 1.0E-6D,
                    "诱饵应站在准星前的石头顶上（约 2.5, 2, 2.1），实际 " + at);
            helper.assertTrue(husk.getTarget() == decoy, "盯着主人的尸壳应改打诱饵，实际 " + husk.getTarget());
            helper.assertTrue(HoloDecoyEntity.currentTarget(piglin) == decoy, "靠记忆找人的猪灵应改打诱饵，实际 " + HoloDecoyEntity.currentTarget(piglin));
            helper.assertTrue(mru(stack) == HoloDecoyProjectorItem.CAPACITY - D.mruPerUse() && D.mruPerUse() == 5000, "应扣 5 000，剩 " + mru(stack));
            helper.assertTrue(done(owner, ECHoloDecoy.ADVANCEMENT_DECOY), "第一次投出应发手册进度 guide/hologram/decoy");

            husk.setTarget(owner);
            helper.assertTrue(husk.getTarget() == decoy, "诱饵在场时尸壳再想打主人也应改成诱饵");
        } finally {
            discard(cleanup);
        }
        helper.succeed();
    }

    /** 同队玩家被盯的怪也改；不同队的照旧（扫描和换目标事件两条路都看） */
    @GameTest(template = EMPTY)
    public static void holoDecoyCoversTeammates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        ServerPlayer owner = player(helper, "ec5-dcy-t1", new Vec3(0.5D, 2D, 0.5D), 0F, 0F);
        ServerPlayer mate = player(helper, "ec5-dcy-t2", new Vec3(4.5D, 2D, 0.5D), 0F, 0F);
        ServerPlayer outsider = player(helper, "ec5-dcy-t3", new Vec3(4.5D, 2D, 4.5D), 0F, 0F);
        ServerScoreboard scoreboard = level.getServer().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("ec5dcy" + Integer.toHexString(UUID.randomUUID().hashCode()));
        scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(mate.getScoreboardName(), team);
        Husk onMate = mob(helper, EntityType.HUSK, new Vec3(1.5D, 2D, 3.5D), false);
        Husk onOutsider = mob(helper, EntityType.HUSK, new Vec3(3.5D, 2D, 3.5D), false);
        Husk later = mob(helper, EntityType.HUSK, new Vec3(2.5D, 2D, 4.5D), false);
        onMate.setTarget(mate);
        onOutsider.setTarget(outsider);
        List<Entity> cleanup = new ArrayList<>(List.of(onMate, onOutsider, later));
        try {
            helper.assertTrue(owner.isAlliedTo(mate) && !owner.isAlliedTo(outsider), "队伍应设好");
            HoloDecoyEntity decoy = decoy(helper, owner, new Vec3(2.5D, 2D, 2.5D), D);
            cleanup.add(decoy);
            helper.assertTrue(onMate.getTarget() == decoy, "盯着同队玩家的尸壳应改打诱饵");
            helper.assertTrue(onOutsider.getTarget() == outsider, "盯着不同队玩家的尸壳照旧");
            later.setTarget(mate);
            helper.assertTrue(later.getTarget() == decoy, "诱饵在场时新盯上同队玩家的也改成诱饵");
            onOutsider.setTarget(outsider);
            decoy.lure();
            helper.assertTrue(onOutsider.getTarget() == outsider, "不同队的玩家被盯着，扫描、换目标都不改");
        } finally {
            discard(cleanup);
            scoreboard.removePlayerTeam(team);
        }
        helper.succeed();
    }

    /** 凋灵只骗 boss_ticks（这里 20）：期间盯诱饵，到点还给主人，之后再盯主人也不改；boss_ticks 0 时不上当 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void holoDecoyFoolsBossesBriefly(GameTestHelper helper) {
        helper.assertTrue(D.bossTicks() == 60, "默认 Boss 只骗 60 刻");
        floor(helper);
        ServerPlayer owner = player(helper, "ec5-dcy-boss", new Vec3(0.5D, 2D, 0.5D), 0F, 0F);
        ServerPlayer other = player(helper, "ec5-dcy-boss2", new Vec3(4.5D, 2D, 0.5D), 0F, 0F);
        WitherBoss wither = mob(helper, EntityType.WITHER, new Vec3(2.5D, 2D, 4D), true);
        wither.setTarget(owner);
        HoloDecoyEntity decoy = decoy(helper, owner, new Vec3(2.5D, 2D, 1.5D),
                new ECHoloDecoy.Config.Values(D.mruPerUse(), D.cooldownTicks(), 200, D.radius(), D.maxRange(), 20));
        HoloDecoyEntity stubborn = decoy(helper, other, new Vec3(4.5D, 2D, 2.5D),
                new ECHoloDecoy.Config.Values(D.mruPerUse(), D.cooldownTicks(), 200, D.radius(), D.maxRange(), 0));
        Runnable cleanup = () -> discard(List.of(wither, decoy, stubborn));
        helper.runAtTickTime(95L, cleanup);
        helper.assertTrue(wither.getTarget() == decoy, "凋灵刚开始应被骗去打诱饵");
        helper.assertFalse(stubborn.mayLure(wither, other), "boss_ticks 0 的诱饵骗不了 Boss");
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(wither.getTarget() == decoy, "20 刻以内还盯着诱饵"))
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.assertTrue(wither.getTarget() == owner, "到点后目标应还给主人，实际 " + wither.getTarget());
                    wither.setTarget(owner);
                    decoy.lure();
                    helper.assertTrue(wither.getTarget() == owner, "识破以后再盯主人，扫描、换目标都不再改");
                    cleanup.run();
                })
                .thenSucceed();
    }

    /** 监守者（decoy_immune）、起了名字的尸壳不受影响；同场普通尸壳照改 */
    @GameTest(template = EMPTY)
    public static void holoDecoySkipsImmuneAndSpared(GameTestHelper helper) {
        floor(helper);
        ServerPlayer owner = player(helper, "ec5-dcy-imm", new Vec3(0.5D, 2D, 0.5D), 0F, 0F);
        Warden warden = mob(helper, EntityType.WARDEN, new Vec3(4D, 2D, 4D), true);
        Husk named = mob(helper, EntityType.HUSK, new Vec3(1.5D, 2D, 4.5D), false);
        named.setCustomName(Component.literal("Bob"));
        Husk plain = mob(helper, EntityType.HUSK, new Vec3(0.5D, 2D, 3.5D), false);
        warden.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, owner);
        named.setTarget(owner);
        plain.setTarget(owner);
        List<Entity> cleanup = new ArrayList<>(List.of(warden, named, plain));
        try {
            helper.assertTrue(warden.getType().is(ECHoloDecoy.DECOY_IMMUNE), "监守者默认在 decoy_immune 里");
            HoloDecoyEntity decoy = decoy(helper, owner, new Vec3(2.5D, 2D, 2.5D), D);
            cleanup.add(decoy);
            helper.assertTrue(plain.getTarget() == decoy, "普通尸壳应改打诱饵");
            helper.assertTrue(HoloDecoyEntity.currentTarget(warden) == owner, "监守者不受诱饵影响");
            helper.assertTrue(named.getTarget() == owner, "起了名字的尸壳不受诱饵影响");
            named.setTarget(owner);
            helper.assertTrue(named.getTarget() == owner, "起了名字的尸壳换目标时也不改");
        } finally {
            discard(cleanup);
        }
        helper.succeed();
    }

    // ================================================================== 寿命、花费、存盘、主人

    /** 默认活 160 刻；寿命 20 的 10 刻时还在、到 20 刻左右消失 */
    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void holoDecoyVanishesAfterLifetime(GameTestHelper helper) {
        floor(helper);
        ServerPlayer owner = player(helper, "ec5-dcy-life", new Vec3(0.5D, 2D, 0.5D), 0F, 0F);
        ServerPlayer owner2 = player(helper, "ec5-dcy-life2", new Vec3(4.5D, 2D, 0.5D), 0F, 0F);
        helper.assertTrue(D.lifetimeTicks() == 160, "默认寿命 160 刻");
        HoloDecoyEntity normal = decoy(helper, owner2, new Vec3(4.5D, 2D, 4.5D), D);
        helper.assertTrue(normal.lifetime() == 160, "默认配置投出的诱饵寿命 160，实际 " + normal.lifetime());
        normal.discard();
        HoloDecoyEntity decoy = decoy(helper, owner, new Vec3(2.5D, 2D, 2.5D),
                new ECHoloDecoy.Config.Values(D.mruPerUse(), D.cooldownTicks(), 20, D.radius(), D.maxRange(), D.bossTicks()));
        long start = helper.getTick();
        helper.runAtTickTime(75L, decoy::discard);
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> helper.assertFalse(decoy.isRemoved(), "10 刻时还应在"))
                .thenWaitUntil(() -> helper.assertTrue(decoy.isRemoved(), "活够 20 刻应消失"))
                .thenExecute(() -> {
                    long lived = helper.getTick() - start;
                    helper.assertTrue(lived >= 19 && lived <= 22, "应在 20 刻左右消失，实际 " + lived);
                    helper.assertTrue(HoloDecoyEntity.of(owner) == null, "消失后主人名下不该还记着它");
                })
                .thenSucceed();
    }

    /**
     * 冷却 600、冷却中按不动不扣；冷却 0 再投一个，旧的当场消失；对着天空、MRU 不够、生成被取消 / 在加入事件里被移除都不扣、不进冷却
     */
    @GameTest(template = EMPTY)
    public static void holoDecoyCostCooldownAndSingle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        ServerPlayer owner = player(helper, "ec5-dcy-cost", new Vec3(2.5D, 2D, 0.5D), 0F, 45F);
        HoloDecoyProjectorItem item = ECHoloDecoy.HOLO_DECOY_PROJECTOR.get();
        ItemStack stack = projector(HoloDecoyProjectorItem.CAPACITY);
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        List<Entity> cleanup = new ArrayList<>();
        try {
            helper.assertTrue(stack.use(level, owner, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "第一次应投出");
            HoloDecoyEntity first = HoloDecoyEntity.of(owner);
            helper.assertTrue(first != null, "应有诱饵");
            cleanup.add(first);
            helper.assertTrue(owner.getCooldowns().isOnCooldown(item), "投完应进冷却");
            helper.assertTrue(stack.use(level, owner, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "冷却中应按不动");
            helper.assertTrue(HoloDecoyEntity.of(owner) == first && mru(stack) == HoloDecoyProjectorItem.CAPACITY - 5000, "冷却中不投不扣");
            for (int i = 0; i < D.cooldownTicks() - 1; i++) {
                owner.getCooldowns().tick();
            }
            helper.assertTrue(owner.getCooldowns().isOnCooldown(item) && D.cooldownTicks() == 600, "599 刻后还在冷却（默认 600）");
            owner.getCooldowns().tick();
            helper.assertFalse(owner.getCooldowns().isOnCooldown(item), "600 刻后冷却结束");
            // 持久数据里的到期刻按 gameTime 算，测试在同一刻里推进物品冷却，这里一起拨到期
            helper.assertTrue(HoloDecoyProjectorItem.remainingTicks(owner) == D.cooldownTicks(), "持久数据里也记着 600 刻");
            HoloDecoyProjectorItem.setReadyAt(owner, level.getGameTime());
            helper.assertFalse(HoloDecoyProjectorItem.coolingDown(owner), "两样都到期才算冷却结束");

            HoloDecoyEntity second;
            try {
                ECHoloDecoy.Config.setTestOverrides(new ECHoloDecoy.Config.Values(D.mruPerUse(), 0, D.lifetimeTicks(), D.radius(), D.maxRange(), D.bossTicks()));
                helper.assertTrue(stack.use(level, owner, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "冷却 0：再投一个");
                second = HoloDecoyEntity.of(owner);
                if (second != null) {
                    cleanup.add(second);
                }
                helper.assertTrue(second != null && second != first, "主人名下应换成新的诱饵");
                helper.assertTrue(first.isRemoved(), "每人同时一个：旧的应当场消失");
                helper.assertFalse(owner.getCooldowns().isOnCooldown(item), "冷却 0 时不进冷却");
                helper.assertTrue(mru(stack) == HoloDecoyProjectorItem.CAPACITY - 10000, "又扣 5 000，剩 " + mru(stack));

                // 生成被取消 / 在加入事件里被直接移除：不扣、不换、不进冷却（配置的冷却也临时打开，看它没进）
                ECHoloDecoy.Config.setTestOverrides(D);
                Consumer<EntityJoinLevelEvent> cancel = event -> {
                    if (event.getEntity() instanceof HoloDecoyEntity d && d.owner() == owner) {
                        event.setCanceled(true);
                    }
                };
                Consumer<EntityJoinLevelEvent> remove = event -> {
                    if (event.getEntity() instanceof HoloDecoyEntity d && d.owner() == owner) {
                        event.getEntity().discard();
                    }
                };
                for (Consumer<EntityJoinLevelEvent> deny : List.of(cancel, remove)) {
                    String way = deny == cancel ? "取消加入事件" : "在加入事件里直接移除";
                    MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, deny);
                    InteractionResult used;
                    try {
                        used = stack.use(level, owner, InteractionHand.MAIN_HAND).getResult();
                    } finally {
                        MinecraftForge.EVENT_BUS.unregister(deny);
                    }
                    helper.assertTrue(used == InteractionResult.FAIL, way + "：应返回 FAIL，实际 " + used);
                    helper.assertTrue(mru(stack) == HoloDecoyProjectorItem.CAPACITY - 10000, way + "：不该扣，剩 " + mru(stack));
                    helper.assertFalse(owner.getCooldowns().isOnCooldown(item), way + "：不该进冷却");
                    helper.assertTrue(HoloDecoyEntity.of(owner) == second && !second.isRemoved(), way + "：原来的诱饵应还在");
                }
            } finally {
                ECHoloDecoy.Config.setTestOverrides(null);
            }

            // 对着天空：没有能站的地方，不扣
            owner.setXRot(-90F);
            helper.assertTrue(HoloDecoyEntity.findSpot(level, owner, D.maxRange()) == null, "对着天空找不到落点");
            helper.assertTrue(stack.use(level, owner, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "对着天空应按不出");
            helper.assertTrue(mru(stack) == HoloDecoyProjectorItem.CAPACITY - 10000, "对着天空不扣");

            // MRU 不够：不投不扣
            owner.setXRot(45F);
            ItemStack poor = projector(D.mruPerUse() - 1);
            owner.setItemInHand(InteractionHand.MAIN_HAND, poor);
            helper.assertTrue(poor.use(level, owner, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "MRU 不够应按不出");
            helper.assertTrue(mru(poor) == D.mruPerUse() - 1 && HoloDecoyEntity.of(owner) == second, "MRU 不够不扣、不换诱饵");
        } finally {
            ECHoloDecoy.Config.setTestOverrides(null);
            discard(cleanup);
        }
        helper.succeed();
    }

    /** 不存盘（区块存档时 save 返回 false，什么都不写）、不能召唤 */
    @GameTest(template = EMPTY)
    public static void holoDecoyIsNotSaved(GameTestHelper helper) {
        floor(helper);
        ServerPlayer owner = player(helper, "ec5-dcy-save", new Vec3(0.5D, 2D, 0.5D), 0F, 0F);
        HoloDecoyEntity decoy = decoy(helper, owner, new Vec3(2.5D, 2D, 2.5D), D);
        try {
            EntityType<HoloDecoyEntity> type = ECHoloDecoy.HOLO_DECOY.get();
            helper.assertFalse(type.canSerialize(), "实体类型不该能存盘");
            helper.assertFalse(type.canSummon(), "实体类型不该能召唤");
            helper.assertFalse(decoy.shouldBeSaved(), "诱饵不该存盘");
            CompoundTag tag = new CompoundTag();
            helper.assertFalse(decoy.save(tag), "区块存档时 save 应返回 false");
            helper.assertTrue(tag.isEmpty(), "什么都不该写进存档，实际 " + tag);
        } finally {
            decoy.discard();
        }
        helper.succeed();
    }

    /** 主人下线：事件里当场消失；主人的引用被移除（没有事件）、主人死了：一刻内消失 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void holoDecoyLeavesWithOwner(GameTestHelper helper) {
        floor(helper);
        ServerPlayer loggedOut = player(helper, "ec5-dcy-out", new Vec3(0.5D, 2D, 0.5D), 0F, 0F);
        ServerPlayer removed = player(helper, "ec5-dcy-rem", new Vec3(4.5D, 2D, 0.5D), 0F, 0F);
        ServerPlayer dead = player(helper, "ec5-dcy-dead", new Vec3(2.5D, 2D, 0.5D), 0F, 0F);
        HoloDecoyEntity a = decoy(helper, loggedOut, new Vec3(0.5D, 2D, 4.5D), D);
        HoloDecoyEntity b = decoy(helper, removed, new Vec3(4.5D, 2D, 4.5D), D);
        HoloDecoyEntity c = decoy(helper, dead, new Vec3(2.5D, 2D, 4.5D), D);
        Runnable cleanup = () -> discard(List.of(a, b, c));
        helper.runAtTickTime(35L, cleanup);
        HoloDecoyEvents.decoyOwnerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(loggedOut));
        helper.assertTrue(a.isRemoved() && HoloDecoyEntity.of(loggedOut) == null, "主人下线（事件）应当场消失");
        helper.assertFalse(b.isRemoved() || c.isRemoved(), "别人的诱饵不受影响");
        removed.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        dead.setHealth(0F);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(b.isRemoved(), "主人的引用被移除后一刻内应消失");
                    helper.assertTrue(c.isRemoved(), "主人死了一刻内应消失");
                    cleanup.run();
                })
                .thenSucceed();
    }

    /**
     * 首领的被骗时长所有诱饵共用：同队两人各投一个诱饵（冷却 40、boss_ticks 20），凋灵被 A 的诱饵骗到点后还给 A，这一刻 B 的诱饵接不走，
     * 识破期里 B 的扫描、凋灵再换目标也接不走；从第一次上当算起过了 max(冷却, boss_ticks) = 40 刻又能被骗一次
     */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void holoDecoyBossBudgetShared(GameTestHelper helper) {
        floor(helper);
        ServerPlayer a = player(helper, "ec5-dcy-sha", new Vec3(0.5D, 2D, 0.5D), 0F, 0F);
        ServerPlayer b = player(helper, "ec5-dcy-shb", new Vec3(4.5D, 2D, 0.5D), 0F, 0F);
        ServerScoreboard scoreboard = helper.getLevel().getServer().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("ec5dcy" + Integer.toHexString(UUID.randomUUID().hashCode()));
        scoreboard.addPlayerToTeam(a.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(b.getScoreboardName(), team);
        WitherBoss wither = mob(helper, EntityType.WITHER, new Vec3(2.5D, 2D, 4D), true);
        wither.setTarget(a);
        ECHoloDecoy.Config.Values quick = new ECHoloDecoy.Config.Values(D.mruPerUse(), 40, 200, D.radius(), D.maxRange(), 20);
        HoloDecoyEntity byA = decoy(helper, a, new Vec3(1.5D, 2D, 2.5D), quick);
        HoloDecoyEntity byB = decoy(helper, b, new Vec3(3.5D, 2D, 2.5D), quick);
        Runnable cleanup = () -> {
            discard(List.of(wither, byA, byB));
            if (scoreboard.getPlayerTeam(team.getName()) != null) {
                scoreboard.removePlayerTeam(team);
            }
        };
        helper.runAtTickTime(115L, cleanup);
        helper.assertTrue(a.isAlliedTo(b), "前提：A、B 同队");
        helper.assertTrue(wither.getTarget() == byA, "凋灵先被 A 的诱饵骗走，实际 " + wither.getTarget());
        helper.startSequence()
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(wither.getTarget() == a, "到点后还给 A，B 的诱饵这一刻接不走，实际 " + wither.getTarget());
                    byB.lure();
                    wither.setTarget(a);
                    helper.assertTrue(wither.getTarget() == a, "识破期里 B 的扫描、凋灵再换目标都接不走，实际 " + wither.getTarget());
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(wither.getTarget() == a, "35 刻时仍在识破期，还盯着 A，实际 " + wither.getTarget()))
                .thenIdle(15)
                .thenExecute(() -> {
                    wither.setTarget(a);
                    helper.assertTrue(wither.getTarget() instanceof HoloDecoyEntity, "过了 40 刻又能被骗一次，实际 " + wither.getTarget());
                    cleanup.run();
                })
                .thenSucceed();
    }

    /** 主人换维度：事件里当场消失；没有事件、只是主人已经在别的世界（tick 里的兜底）时一刻内消失 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void holoDecoyLeavesOnDimensionChange(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer traveller = player(helper, "ec5-dcy-dim", new Vec3(0.5D, 2D, 0.5D), 0F, 0F);
        HoloDecoyEntity first = decoy(helper, traveller, new Vec3(0.5D, 2D, 4.5D), D);
        HoloDecoyEvents.decoyOwnerChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(traveller, Level.OVERWORLD, Level.NETHER));
        helper.assertTrue(first.isRemoved() && HoloDecoyEntity.of(traveller) == null, "主人换维度（事件）应当场消失");
        ServerLevel elsewhere = server.getLevel(level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
        if (elsewhere == null) {
            helper.succeed();
            return;
        }
        ServerPlayer away = new ServerPlayer(server, elsewhere, new GameProfile(UUID.randomUUID(), "ec5-dcy-away"));
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), away);
        away.setGameMode(GameType.SURVIVAL);
        HoloDecoyEntity second = HoloDecoyEntity.spawn(level, away, helper.absoluteVec(new Vec3(4.5D, 2D, 4.5D)), D);
        helper.assertTrue(second != null, "前提：诱饵生成出来了");
        second.activate();
        Runnable cleanup = () -> discard(List.of(second));
        helper.runAtTickTime(35L, cleanup);
        helper.assertTrue(away.level() != level && !second.isRemoved(), "前提：主人在别的世界，诱饵刚生成");
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(second.isRemoved() && HoloDecoyEntity.of(away) == null, "主人已经在别的世界（没有事件）一刻内应消失");
                    cleanup.run();
                })
                .thenSucceed();
    }

    /**
     * 冷却退出重进不清：投出以后清掉物品冷却（等于重进），持久数据里的到期刻还没到，再投按不动、不扣；登录、从末地回来时补回物品冷却；
     * 死亡重生清掉记录（同 θ 腰带）；到期以后登录删掉记录、又能投
     */
    @GameTest(template = EMPTY)
    public static void holoDecoyCooldownSurvivesRelog(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        ServerPlayer owner = player(helper, "ec5-dcy-relog", new Vec3(2.5D, 2D, 0.5D), 0F, 45F);
        HoloDecoyProjectorItem item = ECHoloDecoy.HOLO_DECOY_PROJECTOR.get();
        ItemStack stack = projector(HoloDecoyProjectorItem.CAPACITY);
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        List<Entity> cleanup = new ArrayList<>();
        try {
            helper.assertTrue(D.cooldownTicks() > 0, "前提：默认有冷却");
            helper.assertTrue(stack.use(level, owner, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "第一次应投出");
            cleanup.add(HoloDecoyEntity.of(owner));
            helper.assertTrue(HoloDecoyProjectorItem.remainingTicks(owner) == D.cooldownTicks(),
                    "持久数据里应记下剩 " + D.cooldownTicks() + " 刻，实际 " + HoloDecoyProjectorItem.remainingTicks(owner));

            owner.getCooldowns().removeCooldown(item);   // 物品冷却不存盘：重进以后就没了
            helper.assertTrue(HoloDecoyProjectorItem.coolingDown(owner), "物品冷却没了、持久数据还没到期，仍算冷却中");
            helper.assertTrue(stack.use(level, owner, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "重进以后冷却没到期，按不动");
            helper.assertTrue(mru(stack) == HoloDecoyProjectorItem.CAPACITY - D.mruPerUse(), "按不动不扣，剩 " + mru(stack));

            HoloDecoyEvents.decoyCooldownLogin(new PlayerEvent.PlayerLoggedInEvent(owner));
            helper.assertTrue(owner.getCooldowns().isOnCooldown(item), "登录时把剩下的冷却补回物品冷却");
            owner.getCooldowns().removeCooldown(item);
            HoloDecoyEvents.decoyCooldownRespawn(new PlayerEvent.PlayerRespawnEvent(owner, true));
            helper.assertTrue(owner.getCooldowns().isOnCooldown(item), "从末地回来也补回物品冷却");

            owner.getCooldowns().removeCooldown(item);
            HoloDecoyEvents.decoyCooldownRespawn(new PlayerEvent.PlayerRespawnEvent(owner, false));
            helper.assertFalse(HoloDecoyProjectorItem.coolingDown(owner), "死亡重生清掉冷却（同 θ 腰带）");

            HoloDecoyProjectorItem.setReadyAt(owner, level.getGameTime() - 1L);
            HoloDecoyEvents.decoyCooldownLogin(new PlayerEvent.PlayerLoggedInEvent(owner));
            helper.assertFalse(owner.getCooldowns().isOnCooldown(item)
                            || owner.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(HoloDecoyProjectorItem.READY_KEY),
                    "到期以后登录不补冷却、删掉过期的记录");
            helper.assertTrue(stack.use(level, owner, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "到期以后又能投");
            cleanup.add(HoloDecoyEntity.of(owner));
        } finally {
            discard(cleanup);
        }
        helper.succeed();
    }

    // ================================================================== 工具

    /** y = 1 铺一层石头（5 × 5），东西从 y = 2 起放 */
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet, float yaw, float pitch) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, yaw, pitch);
        player.setYHeadRot(yaw);
        return player;
    }

    /** 不会自己换目标的怪；noAi 再关掉它自己的那套（凋灵、监守者、猪灵） */
    private static <E extends Mob> E mob(GameTestHelper helper, EntityType<E> type, Vec3 relative, boolean noAi) {
        E mob = helper.spawnWithNoFreeWill(type, relative);
        if (noAi) {
            mob.setNoAi(true);
        }
        return mob;
    }

    /** 直接在 relative 生成一个诱饵并开始工作（数值按 values，生成时记在诱饵身上） */
    private static HoloDecoyEntity decoy(GameTestHelper helper, ServerPlayer owner, Vec3 relative, ECHoloDecoy.Config.Values values) {
        HoloDecoyEntity decoy = HoloDecoyEntity.spawn(helper.getLevel(), owner, helper.absoluteVec(relative), values);
        if (decoy == null) {
            throw new IllegalStateException("诱饵没生成出来");
        }
        decoy.activate();
        return decoy;
    }

    private static ItemStack projector(int mru) {
        ItemStack stack = new ItemStack(ECHoloDecoy.HOLO_DECOY_PROJECTOR.get());
        handler(stack).setMRU(mru);
        return stack;
    }

    private static IMRUHandlerItem handler(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).orElseThrow(IllegalStateException::new);
    }

    private static int mru(ItemStack stack) {
        return handler(stack).getMRU();
    }

    private static boolean done(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static void discard(List<? extends Entity> entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }
}
