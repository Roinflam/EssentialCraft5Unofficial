package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.boss.BossLoot;
import com.flameforge.essentialcraft5.entity.boss.BossTier;
import com.flameforge.essentialcraft5.entity.boss.ECBoss;
import com.flameforge.essentialcraft5.entity.boss.SentryTurretEntity;
import com.flameforge.essentialcraft5.entity.boss.TownGuardians;
import com.flameforge.essentialcraft5.entity.boss.TownSentryEntity;
import com.flameforge.essentialcraft5.entity.boss.TownSentryTrigger;
import com.flameforge.essentialcraft5.entity.mob.hoanna.HoannaScavengerEntity;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECTownSentry;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.worldgen.hoanna.town.HoannaTownPieces;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 废城守望者（第十三批 13e-T，batch13.md §5.3.3、§12.2 13e 行）和全息投影 / 审判庭哨兵拉回的两处口子（batch13.md「13e 已定事项」第 2 条定为 A）：
 * 属性与小 Boss 规矩、城镇触发（只在海纳尔、只认能动起点那格的人、落点权限、存档只醒一次、生成被取消不记、和平散去可再醒、{@code trigger_radius} 0 不触发）、
 * 炮台上限与补、正面挡弹射物、警报只一次（读档不重复）、横扫与镇压、掉落 {@code fair_kill}、打倒后异兽录与存档；
 * 全息投影 / 审判庭哨兵：落点没权限不拉不扣血、拉不回来满 {@code abandon_ticks} 移出。
 * <p>
 * 城镇是手搭的：在测试区域的几个区块里塞一个只有起点件的城镇结构起点、把引用挂到这几个区块上（{@link FakeTown}，用完原样换回）。
 * 测试结构在超平坦主世界，「在海纳尔」用 {@code ECHoanna.setTestHoannaOverride}，每条测试都在同一段同步代码里设、测、清。
 * 守望者除了触发出来的那几只都是 NoAI（技能由测试直接调）。配置一律走 {@link ECTownSentry.Config#setTestOverrides}，用完置回 null。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class TownSentryTests {

    private static final String LARGE = "empty_large";
    private static final String BATCH = "town_sentry";
    private static final ECTownSentry.Config.Values D = ECTownSentry.Config.DEFAULTS;
    private static final BlockPos ORIGIN = new BlockPos(5, 1, 5);
    private static final double EPS = 1.0E-3D;

    private TownSentryTests() {
    }

    // ================================================================== 工具

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /** 生存模式的真玩家：先空跑 61 刻把出生无敌跑掉、回满血，脚在 relative；online 为真时算在线 */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relative, boolean online) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        prepare(helper, player, relative, online);
        return player;
    }

    private static void prepare(GameTestHelper helper, ServerPlayer player, Vec3 relative, boolean online) {
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        if (online) {
            ECProtection.setOnlineOverride(player);
        }
    }

    private static void offline(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            ECProtection.clearOnlineOverride(player.getUUID());
            ECProtection.forget(player.getUUID());
            TownSentryTrigger.forgetPlayer(player.getUUID());
        }
    }

    private static void moveTo(GameTestHelper helper, Entity entity, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        entity.moveTo(at.x, at.y, at.z, entity.getYRot(), entity.getXRot());
    }

    /** 记下动作栏消息的真玩家（测试玩家没有网络，发出去的消息只能在这里截下来） */
    private static final class Listening extends ServerPlayer {
        final List<Component> bar = new ArrayList<>();

        Listening(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
            new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), this);
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            if (actionBar) {
                bar.add(message);
            }
            super.displayClientMessage(message, actionBar);
        }

        int heard(String key) {
            return (int) bar.stream().filter(line -> line.getContents() instanceof TranslatableContents t && key.equals(t.getKey())).count();
        }
    }

    private static Listening listening(GameTestHelper helper, String name, Vec3 relative, boolean online) {
        Listening player = new Listening(helper.getLevel(), name);
        prepare(helper, player, relative, online);
        return player;
    }

    /** NoAI 的守望者，脚在 relative、朝南（+Z）；来源记结构；town 不是 null 时守着它 */
    private static TownSentryEntity sentry(GameTestHelper helper, Vec3 relative, @Nullable TownGuardians.Town town) {
        ServerLevel level = helper.getLevel();
        TownSentryEntity sentry = ECTownSentry.TOWN_SENTRY.get().create(level);
        if (sentry == null) {
            throw new IllegalStateException("守望者没建出来");
        }
        Vec3 at = helper.absoluteVec(relative);
        sentry.moveTo(at.x, at.y, at.z, 0F, 0F);
        sentry.setYBodyRot(0F);
        sentry.setYHeadRot(0F);
        sentry.setNoAi(true);
        sentry.applyConfiguredStats();
        SpawnOrigin.set(sentry, SpawnOrigin.Origin.STRUCTURE);
        if (town != null) {
            sentry.guardTown(town);
        }
        level.addFreshEntity(sentry);
        return sentry;
    }

    /** 一座只用来记存档的城（不挂结构）：区块坐标取这条测试的位置再偏一大截，不和真的、别的测试的撞 */
    private static TownGuardians.Town looseTown(GameTestHelper helper, int salt) {
        BlockPos at = helper.absolutePos(BlockPos.ZERO);
        return new TownGuardians.Town(helper.getLevel().dimension(), ChunkPos.asLong((at.getX() >> 4) + 100_000 + salt, (at.getZ() >> 4) - 100_000));
    }

    private static void withConfig(ECTownSentry.Config.Values values, Runnable body) {
        ECTownSentry.Config.setTestOverrides(values);
        try {
            body.run();
        } finally {
            ECTownSentry.Config.setTestOverrides(null);
        }
    }

    private static ECTownSentry.Config.Values withTrigger(int radius) {
        return new ECTownSentry.Config.Values(new ECTownSentry.Config.Trigger(radius, D.trigger().retryTicks()), D.stats(), D.attacks(), D.turrets(), D.alarm());
    }

    private static void hit(net.minecraft.world.entity.LivingEntity victim, DamageSource source, float amount) {
        victim.invulnerableTime = 0;
        victim.hurt(source, amount);
    }

    /** Player.hurt 按难度缩放怪物打来的、会缩放的伤害：普通难度原样 */
    private static float scaled(GameTestHelper helper, float amount) {
        Difficulty difficulty = helper.getLevel().getDifficulty();
        return switch (difficulty) {
            case EASY -> Math.min(amount / 2F + 1F, amount);
            case HARD -> amount * 1.5F;
            case PEACEFUL -> 0F;
            default -> amount;
        };
    }

    private static <T extends Entity> List<T> around(GameTestHelper helper, Class<T> type, double margin) {
        AABB box = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(11D, 8D, 11D))).inflate(margin);
        return helper.getLevel().getEntitiesOfClass(type, box, Entity::isAlive);
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private static Item item(String path) {
        return ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
    }

    /**
     * 手搭的城镇：在测试区域盖到的每个区块里挂一条城镇引用，起点（只有一个 {@code town_start} 件，包围盒中心 = origin、地面 = origin 的 y）
     * 放在 origin 所在的区块里。close 时把这几个区块的结构起点 / 引用原样换回，并清掉这座城的存档条目和触发器的记忆。
     */
    private static final class FakeTown implements AutoCloseable {
        final TownGuardians.Town town;
        final BlockPos origin;
        private final ServerLevel level;
        private final Structure structure;
        private final Map<LevelChunk, Map<Structure, StructureStart>> starts = new HashMap<>();
        private final Map<LevelChunk, Map<Structure, LongSet>> refs = new HashMap<>();

        FakeTown(GameTestHelper helper, BlockPos relativeOrigin) {
            this.level = helper.getLevel();
            this.origin = helper.absolutePos(relativeOrigin);
            this.structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(EssentialCraft5.id("hoanna_town"));
            if (structure == null) {
                throw new IllegalStateException("没有 hoanna_town 结构");
            }
            ChunkPos startChunk = new ChunkPos(origin);
            this.town = new TownGuardians.Town(level.dimension(), startChunk.toLong());
            HoannaTownPieces.Start piece = new HoannaTownPieces.Start(0, new BoundingBox(origin.getX() - 2, origin.getY() - 3, origin.getZ() - 2,
                    origin.getX() + 2, origin.getY() + 3, origin.getZ() + 2), Direction.NORTH, 1L);
            piece.setGroundY(origin.getY());
            StructureStart start = new StructureStart(structure, startChunk, 0, new PiecesContainer(List.of(piece)));
            BlockPos lo = helper.absolutePos(new BlockPos(-2, 0, -2));
            BlockPos hi = helper.absolutePos(new BlockPos(13, 0, 13));
            for (int cx = SectionPos.blockToSectionCoord(Math.min(lo.getX(), hi.getX())); cx <= SectionPos.blockToSectionCoord(Math.max(lo.getX(), hi.getX())); cx++) {
                for (int cz = SectionPos.blockToSectionCoord(Math.min(lo.getZ(), hi.getZ())); cz <= SectionPos.blockToSectionCoord(Math.max(lo.getZ(), hi.getZ())); cz++) {
                    LevelChunk chunk = level.getChunk(cx, cz);
                    starts.put(chunk, new HashMap<>(chunk.getAllStarts()));
                    Map<Structure, LongSet> copy = new HashMap<>();
                    chunk.getAllReferences().forEach((key, value) -> copy.put(key, new LongOpenHashSet(value)));
                    refs.put(chunk, copy);
                    chunk.addReferenceForStructure(structure, startChunk.toLong());
                }
            }
            level.getChunk(startChunk.x, startChunk.z).setStartForStructure(structure, start);
            forget();
        }

        void forget() {
            TownGuardians.get(level.getServer()).clear(town);
            TownSentryTrigger.forgetTown(town);
        }

        @Override
        public void close() {
            starts.forEach(LevelChunk::setAllStarts);
            refs.forEach(LevelChunk::setAllReferences);
            forget();
        }
    }

    // ================================================================== 属性、规矩、存读档

    /**
     * 生成时按配置设属性（260 / 14 / 4 / 1、横扫 13、经验 60）；小 Boss、守卫式名单；单次封顶 30（一下 100 点魔法只扣 30）；同调点照普通怪的上限；
     * 炮台在 growth_excluded 里（不算公平来源）；存读档带着城、醒来的位置、警报用没用过
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void townSentryStatsAndRules(GameTestHelper helper) {
        floor(helper);
        TownGuardians.Town town = looseTown(helper, 1);
        TownSentryEntity sentry = sentry(helper, new Vec3(5.5D, 1D, 5.5D), town);
        ServerPlayer player = player(helper, "ts-stats", new Vec3(3.5D, 1D, 5.5D), true);
        SentryTurretEntity turret = ECTownSentry.SENTRY_TURRET.get().create(helper.getLevel());
        try {
            helper.assertTrue(sentry.getMaxHealth() == 260F && Math.abs(sentry.getArmorValue() - 14) == 0, "260 血、14 护甲，实际 " + sentry.getMaxHealth());
            helper.assertTrue(Math.abs(sentry.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS) - 4D) < EPS
                    && Math.abs(sentry.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) - 1D) < EPS
                    && Math.abs(sentry.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) - 13D) < EPS, "韧性 4、击退抗性 1、横扫 13");
            helper.assertTrue(sentry.bossTier() == BossTier.MINI && !sentry.rosterSettings().abandons() && sentry.rosterSettings().pullRange() == 0D,
                    "小 Boss、守卫式名单（不散去、不拽）");
            helper.assertTrue(sentry.getExperienceReward() == 60, "经验 60");
            hit(sentry, player.damageSources().indirectMagic(player, player), 100F);
            helper.assertTrue(Math.abs(sentry.getHealth() - 230F) < 0.01F, "单次封顶 30，实际剩 " + sentry.getHealth());
            helper.assertFalse(AttunementGains.uncappedBoss(sentry, player, false) || AttunementGains.uncappedBoss(sentry, player, true),
                    "小 Boss 档：同调点照普通怪的上限");
            helper.assertTrue(AttunementGains.isBoss(sentry), "在 #forge:bosses 里");
            helper.assertTrue(turret != null && !AttunementGains.fair(turret), "炮台在 growth_excluded 里，不算公平来源");

            sentry.soundAlarm();
            CompoundTag tag = new CompoundTag();
            sentry.saveWithoutId(tag);
            TownSentryEntity copy = ECTownSentry.TOWN_SENTRY.get().create(helper.getLevel());
            copy.load(tag);
            helper.assertTrue(town.equals(copy.town()) && sentry.home().equals(copy.home()) && copy.alarmUsed(), "存读档带着城、醒来的位置、警报用过");
        } finally {
            offline(player);
            discard(sentry, turret);
            around(helper, HoannaScavengerEntity.class, 4D).forEach(Entity::discard);
            TownGuardians.get(helper.getLevel().getServer()).clear(town);
        }
        helper.succeed();
    }

    // ================================================================== 触发

    /**
     * 走近城镇起点就醒、只醒一次：不在海纳尔不醒；海纳尔里第一次检查放出一只（站在起点附近的地面上、来源结构、触发的人进名单、动作栏说一句、存档记「已醒」），
     * 再查不出第二只；存档有条目的城（不管是哪种状态）不醒
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void townSentryTriggerWakesOnce(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        Listening player = listening(helper, "ts-wake", new Vec3(2.5D, 1D, 5.5D), true);
        List<TownSentryEntity> spawned = new ArrayList<>();
        try (FakeTown fake = new FakeTown(helper, ORIGIN)) {
            spawned.addAll(TownSentryTrigger.check(player));
            helper.assertTrue(spawned.isEmpty(), "不在海纳尔：不醒");
            ECHoanna.setTestHoannaOverride(level);
            try {
                helper.assertTrue(TownSentryTrigger.townsAt(level, player.blockPosition()).size() == 1, "找得到这座城");
                spawned.addAll(TownSentryTrigger.check(player));
                helper.assertTrue(spawned.size() == 1, "第一次走近：醒一只，实际 " + spawned.size());
                TownSentryEntity sentry = spawned.get(0);
                helper.assertTrue(sentry.blockPosition().equals(fake.origin), "站在起点上，实际 " + sentry.blockPosition() + " / " + fake.origin);
                helper.assertTrue(SpawnOrigin.of(sentry) == SpawnOrigin.Origin.STRUCTURE && fake.town.equals(sentry.town()), "来源结构、守着这座城");
                helper.assertTrue(sentry.bossRoster().contains(player.getUUID()) && player.heard(TownSentryTrigger.MSG_AWAKENED) == 1, "触发的人进名单、听到醒了");
                helper.assertTrue(TownGuardians.get(level.getServer()).state(fake.town) == TownGuardians.State.SPAWNED, "存档记「已醒」");
                TownSentryTrigger.forgetTown(fake.town);
                spawned.addAll(TownSentryTrigger.check(player));
                helper.assertTrue(spawned.size() == 1, "再查不出第二只");
                for (TownGuardians.State state : TownGuardians.State.values()) {
                    TownGuardians.get(level.getServer()).set(fake.town, state);
                    TownSentryTrigger.forgetTown(fake.town);
                    spawned.addAll(TownSentryTrigger.check(player));
                    helper.assertTrue(spawned.size() == 1, "存档是 " + state + " 的城不醒");
                }
            } finally {
                ECHoanna.clearTestOverride();
            }
        } finally {
            offline(player);
            spawned.forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * 不该触发的人和情形：创造模式、假玩家、离起点超过 trigger_radius、trigger_radius 填 0、和平难度。
     * 被别的模组 / 插件取消进世界（EntityJoinLevelEvent）时不记存档，取消撤掉、过了重试间隔以后照常醒
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void townSentryTriggerFiltersAndCancel(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "ts-filter", new Vec3(2.5D, 1D, 5.5D), true);
        ServerPlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        List<TownSentryEntity> spawned = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof TownSentryEntity) {
                event.setCanceled(true);
            }
        };
        try (FakeTown town = new FakeTown(helper, ORIGIN)) {
            ECHoanna.setTestHoannaOverride(level);
            try {
                fake.setGameMode(GameType.SURVIVAL);
                moveTo(helper, fake, new Vec3(3.5D, 1D, 5.5D));
                spawned.addAll(TownSentryTrigger.check(fake));
                helper.assertTrue(spawned.isEmpty(), "假玩家不触发");
                player.setGameMode(GameType.CREATIVE);
                spawned.addAll(TownSentryTrigger.check(player));
                helper.assertTrue(spawned.isEmpty(), "创造模式不触发");
                player.setGameMode(GameType.SURVIVAL);
                withConfig(withTrigger(2), () -> spawned.addAll(TownSentryTrigger.check(player)));
                helper.assertTrue(spawned.isEmpty(), "离起点 3 格、半径 2：不触发");
                withConfig(withTrigger(0), () -> spawned.addAll(TownSentryTrigger.check(player)));
                helper.assertTrue(spawned.isEmpty(), "trigger_radius 0：不触发");
                TownSentryTrigger.setTestPeaceful(true);
                try {
                    spawned.addAll(TownSentryTrigger.check(player));
                } finally {
                    TownSentryTrigger.setTestPeaceful(null);
                }
                helper.assertTrue(spawned.isEmpty(), "和平难度不触发");
                helper.assertTrue(TownGuardians.get(level.getServer()).state(town.town) == null, "这些都不记存档");

                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, cancel);
                try {
                    spawned.addAll(TownSentryTrigger.check(player));
                } finally {
                    MinecraftForge.EVENT_BUS.unregister(cancel);
                }
                helper.assertTrue(spawned.isEmpty() && TownGuardians.get(level.getServer()).state(town.town) == null, "进世界被取消：不出、不记存档");
                spawned.addAll(TownSentryTrigger.check(player));
                helper.assertTrue(spawned.isEmpty(), "重试间隔没到不再试");
                TownSentryTrigger.forgetTown(town.town);
                spawned.addAll(TownSentryTrigger.check(player));
                helper.assertTrue(spawned.size() == 1 && TownGuardians.get(level.getServer()).state(town.town) == TownGuardians.State.SPAWNED,
                        "取消撤掉以后照常醒，实际 " + spawned.size());
            } finally {
                ECHoanna.clearTestOverride();
            }
        } finally {
            offline(player);
            spawned.forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * 权限：起点那一格动不了的人走近不醒（以他的身份问过起点）；起点能动、但最近的落点在别人领地里时，以触发者身份问过那一格、退到下一格出来
     * （起点那一列插了一块玻璃板，站不了）
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void townSentryTriggerClaims(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "ts-claim", new Vec3(2.5D, 1D, 5.5D), true);
        List<TownSentryEntity> spawned = new ArrayList<>();
        try (FakeTown town = new FakeTown(helper, ORIGIN)) {
            ECHoanna.setTestHoannaOverride(level);
            try {
                try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(ORIGIN)) {
                    spawned.addAll(TownSentryTrigger.check(player));
                    helper.assertTrue(claims.breaksBy(ORIGIN, player.getUUID()) >= 1, "以触发者的身份问过起点那一格");
                    helper.assertTrue(spawned.isEmpty() && TownGuardians.get(level.getServer()).state(town.town) == null, "起点动不了：不醒");
                }
                offline(player);
                ECProtection.setOnlineOverride(player);
                town.forget();
                helper.setBlock(ORIGIN, Blocks.GLASS_PANE);
                BlockPos nearest = ORIGIN.west();
                try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(nearest)) {
                    spawned.addAll(TownSentryTrigger.check(player));
                    helper.assertTrue(claims.breaksBy(nearest, player.getUUID()) >= 1, "以触发者的身份问过最近的落点");
                    helper.assertTrue(spawned.size() == 1, "退到下一格出来，实际 " + spawned.size());
                    BlockPos at = spawned.get(0).blockPosition();
                    helper.assertTrue(!at.equals(helper.absolutePos(nearest)) && !at.equals(helper.absolutePos(ORIGIN))
                            && at.getY() == helper.absolutePos(ORIGIN).getY(), "不站进领地、不站在玻璃板上，实际 " + at);
                }
            } finally {
                ECHoanna.clearTestOverride();
            }
        } finally {
            offline(player);
            spawned.forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** 和平散去：存档改回「还没醒」、它自己消失；被指令删掉记「放弃了」；死了记「打倒了」；区块卸载（只存进区块）不改存档 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void townSentryEndStates(GameTestHelper helper) {
        floor(helper);
        TownGuardians guardians = TownGuardians.get(helper.getLevel().getServer());
        TownGuardians.Town peace = looseTown(helper, 2);
        TownGuardians.Town gone = looseTown(helper, 3);
        TownGuardians.Town dead = looseTown(helper, 4);
        TownSentryEntity a = sentry(helper, new Vec3(2.5D, 1D, 2.5D), peace);
        TownSentryEntity b = sentry(helper, new Vec3(8.5D, 1D, 2.5D), gone);
        TownSentryEntity c = sentry(helper, new Vec3(5.5D, 1D, 8.5D), dead);
        try {
            for (TownGuardians.Town town : List.of(peace, gone, dead)) {
                guardians.set(town, TownGuardians.State.SPAWNED);
            }
            a.endBoss(ECBoss.BossEnd.PEACEFUL);
            helper.assertTrue(a.isRemoved() && guardians.state(peace) == null, "和平散去：消失、回到还没醒，实际 " + guardians.state(peace));
            b.discard();
            helper.assertTrue(guardians.state(gone) == TownGuardians.State.ABANDONED, "被删掉：放弃了");
            BossFrameworkTests.asKillCommand(c::kill);
            helper.assertTrue(c.isDeadOrDying() && guardians.state(dead) == TownGuardians.State.DEFEATED, "死了：打倒了");
            guardians.set(gone, TownGuardians.State.SPAWNED);
            TownSentryEntity d = sentry(helper, new Vec3(8.5D, 1D, 8.5D), gone);
            d.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(guardians.state(gone) == TownGuardians.State.SPAWNED, "区块卸载不改存档");
            CompoundTag saved = guardians.save(new CompoundTag());
            TownGuardians loaded = TownGuardians.load(saved);
            helper.assertTrue(loaded.state(dead) == TownGuardians.State.DEFEATED && loaded.state(gone) == TownGuardians.State.SPAWNED
                    && loaded.state(peace) == null, "存档读回来一样");
        } finally {
            discard(a, b, c);
            for (TownGuardians.Town town : List.of(peace, gone, dead)) {
                guardians.clear(town);
            }
            around(helper, ExperienceOrb.class, 2D).forEach(Entity::discard);
        }
        helper.succeed();
    }

    // ================================================================== 炮台

    /**
     * 炮台：名单里有人时一口气架两座（左右两侧各一座，都站在地上）；满了不再架；炮台打名单里看得见的人（mob_bolt，攻击者是炮台），不打名单外的；
     * 打掉一座 20 秒后补；名单空了 100 刻收回；找不到主人的炮台自己消失
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void townSentryTurrets(GameTestHelper helper) {
        floor(helper);
        TownSentryEntity sentry = sentry(helper, new Vec3(5.5D, 1D, 5.5D), null);
        ServerPlayer fighter = player(helper, "ts-turret", new Vec3(5.5D, 1D, 9.5D), true);
        ServerPlayer bystander = player(helper, "ts-bystand", new Vec3(4.5D, 1D, 8.5D), true);
        SentryTurretEntity orphan = ECTownSentry.SENTRY_TURRET.get().create(helper.getLevel());
        try {
            sentry.tickCombat();
            helper.assertTrue(sentry.turrets().isEmpty(), "名单空着不架");
            sentry.bossRoster().add(fighter);
            sentry.tickCombat();
            List<Entity> turrets = sentry.turrets();
            helper.assertTrue(turrets.size() == 2, "架两座，实际 " + turrets.size());
            double side = (turrets.get(0).getX() - sentry.getX()) * (turrets.get(1).getX() - sentry.getX());
            helper.assertTrue(side < 0D, "左右各一座");
            for (Entity turret : turrets) {
                helper.assertTrue(turret.onGround() || Math.abs(turret.getY() - sentry.getY()) < EPS, "站在地上，实际 " + turret.position());
            }
            helper.assertTrue(sentry.deployTurret() == null && sentry.turrets().size() == 2, "满两座不再架");

            SentryTurretEntity turret = (SentryTurretEntity) turrets.get(0);
            helper.assertTrue(sentry.turretTarget(turret) == fighter, "炮台挑名单里的人，不挑更近的路人");
            float before = fighter.getHealth();
            helper.assertTrue(turret.fire(fighter), "打中");
            DamageSource last = fighter.getLastDamageSource();
            helper.assertTrue(fighter.getHealth() < before && last != null && last.is(DamageTypeTags.IS_PROJECTILE) && last.getEntity() == turret,
                    "mob_bolt、攻击者是炮台");

            turret.kill();
            sentry.tickCombat();
            helper.assertTrue(sentry.turrets().size() == 1 && sentry.turretRespawnAt() > 0, "打掉一座：开始等着补");
            sentry.tickCount += D.turrets().respawnTicks() - 1;
            sentry.tickCombat();
            helper.assertTrue(sentry.turrets().size() == 1, "不到 20 秒不补");
            sentry.tickCount += 1;
            sentry.tickCombat();
            helper.assertTrue(sentry.turrets().size() == 2, "20 秒补回一座，实际 " + sentry.turrets().size());

            sentry.bossRoster().remove(fighter.getUUID());
            for (int i = 0; i < TownSentryEntity.TURRET_RECALL_TICKS; i++) {
                sentry.tickCombat();
            }
            helper.assertTrue(sentry.turrets().isEmpty(), "名单空了 100 刻收回");

            Vec3 at = helper.absoluteVec(new Vec3(1.5D, 1D, 1.5D));
            orphan.moveTo(at.x, at.y, at.z, 0F, 0F);
            helper.getLevel().addFreshEntity(orphan);
            orphan.tickTurret();
            helper.assertTrue(orphan.isRemoved(), "没有主人的炮台自己消失");
        } finally {
            offline(fighter, bystander);
            sentry.recallTurrets();
            discard(sentry, orphan);
        }
        helper.succeed();
    }

    /** 正面挡弹射物：炮台在时朝向左右 60° 以内打来的箭弹开（不掉血），背后打来的、近战照常吃；炮台收了以后正面也吃 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void townSentryFrontGuard(GameTestHelper helper) {
        floor(helper);
        TownSentryEntity sentry = sentry(helper, new Vec3(5.5D, 1D, 5.5D), null);
        ServerPlayer player = player(helper, "ts-guard", new Vec3(5.5D, 1D, 9.5D), true);
        Arrow front = EntityType.ARROW.create(helper.getLevel());
        Arrow behind = EntityType.ARROW.create(helper.getLevel());
        try {
            moveTo(helper, front, new Vec3(6.5D, 2D, 8.5D));
            moveTo(helper, behind, new Vec3(5.5D, 2D, 2.5D));
            sentry.bossRoster().add(player);
            sentry.tickCombat();
            helper.assertTrue(sentry.turretsUp(), "测试前提：炮台架起来了");
            float full = sentry.getHealth();
            sentry.invulnerableTime = 0;
            helper.assertFalse(sentry.hurt(sentry.damageSources().arrow(front, player), 10F), "正面的箭被挡下");
            helper.assertTrue(sentry.getHealth() == full, "不掉血");
            hit(sentry, sentry.damageSources().arrow(behind, player), 10F);
            helper.assertTrue(sentry.getHealth() < full, "背后的箭照常吃");
            float mid = sentry.getHealth();
            hit(sentry, player.damageSources().playerAttack(player), 10F);
            helper.assertTrue(sentry.getHealth() < mid, "正面近战照常吃");
            sentry.recallTurrets();
            float now = sentry.getHealth();
            hit(sentry, sentry.damageSources().arrow(front, player), 10F);
            helper.assertTrue(sentry.getHealth() < now, "炮台没了：正面的箭也吃");
        } finally {
            offline(player);
            sentry.recallTurrets();
            discard(sentry, front, behind);
        }
        helper.succeed();
    }

    // ================================================================== 警报、横扫、镇压

    /** 警报：半血以上不响；第一次降到一半以下叫来两名拾荒者（来源召唤）、名单里的人听到；再降不再叫；读档后的那只也不再叫 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void townSentryAlarmOnce(GameTestHelper helper) {
        floor(helper);
        TownSentryEntity sentry = sentry(helper, new Vec3(5.5D, 1D, 5.5D), null);
        Listening player = listening(helper, "ts-alarm", new Vec3(5.5D, 1D, 9.5D), true);
        TownSentryEntity copy = null;
        try {
            withConfig(new ECTownSentry.Config.Values(D.trigger(), D.stats(), D.attacks(),
                    new ECTownSentry.Config.Turrets(0, 30D, 6D, 30, 16D, 400, 120D), D.alarm()), () -> {
                sentry.bossRoster().add(player);
                sentry.setHealth(sentry.getMaxHealth() * 0.51F);
                sentry.tickCombat();
                helper.assertTrue(!sentry.alarmUsed() && around(helper, HoannaScavengerEntity.class, 4D).isEmpty(), "半血以上不响");
                sentry.setHealth(sentry.getMaxHealth() * 0.49F);
                sentry.tickCombat();
            });
            List<HoannaScavengerEntity> called = around(helper, HoannaScavengerEntity.class, 4D);
            helper.assertTrue(sentry.alarmUsed() && called.size() == 2, "降到一半以下：叫来两名，实际 " + called.size());
            for (HoannaScavengerEntity scavenger : called) {
                helper.assertTrue(SpawnOrigin.of(scavenger) == SpawnOrigin.Origin.SUMMONED && !scavenger.isElite(), "来源召唤、不当精英");
            }
            helper.assertTrue(player.heard(TownSentryEntity.MSG_ALARM) == 1, "名单里的人听到警报");
            sentry.setHealth(sentry.getMaxHealth() * 0.2F);
            sentry.tickCombat();
            helper.assertTrue(around(helper, HoannaScavengerEntity.class, 4D).size() == 2, "再降不再叫");
            CompoundTag tag = new CompoundTag();
            sentry.saveWithoutId(tag);
            copy = ECTownSentry.TOWN_SENTRY.get().create(helper.getLevel());
            copy.load(tag);
            Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
            copy.setUUID(UUID.randomUUID());
            copy.moveTo(at.x, at.y, at.z, 0F, 0F);
            copy.setNoAi(true);
            helper.getLevel().addFreshEntity(copy);
            copy.tickCombat();
            helper.assertTrue(copy.alarmUsed() && around(helper, HoannaScavengerEntity.class, 4D).size() == 2, "读档后的那只不再叫");
        } finally {
            offline(player);
            discard(sentry, copy);
            around(helper, HoannaScavengerEntity.class, 4D).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * 横扫：朝主目标 120°、3 格（从身体边缘量）以内名单里的人也挨一下（吃护甲），背后的、太远的、名单外的不挨；
     * 镇压：半径 4 内名单里的人挨 10 + 最大生命 12%（aberrant_strike，攻击者是守望者），圈外、名单外的不挨，上 14 秒冷却
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void townSentrySweepAndSuppress(GameTestHelper helper) {
        floor(helper);
        TownSentryEntity sentry = sentry(helper, new Vec3(5.5D, 1D, 5.5D), null);
        ServerPlayer main = player(helper, "ts-main", new Vec3(5.5D, 1D, 8.0D), true);
        ServerPlayer flank = player(helper, "ts-flank", new Vec3(7.5D, 1D, 7.5D), true);
        ServerPlayer back = player(helper, "ts-back", new Vec3(5.5D, 1D, 3.0D), true);
        ServerPlayer far = player(helper, "ts-far", new Vec3(5.5D, 1D, 10.5D), true);
        ServerPlayer outsider = player(helper, "ts-out", new Vec3(4.5D, 1D, 7.5D), true);
        try {
            withConfig(new ECTownSentry.Config.Values(D.trigger(), D.stats(), D.attacks(),
                    new ECTownSentry.Config.Turrets(0, 30D, 6D, 30, 16D, 400, 120D), D.alarm()), () -> {
                for (ServerPlayer player : List.of(main, flank, back, far)) {
                    sentry.bossRoster().add(player);
                }
                List<ServerPlayer> swept = sentry.sweepTargets(main);
                helper.assertTrue(swept.size() == 2 && swept.contains(main) && swept.contains(flank), "扇形里只有主目标和侧前方那个，实际 " + swept.size());
                helper.assertTrue(sentry.sweep(main) == 1 && flank.getHealth() < flank.getMaxHealth(), "横扫带到侧前方那个");
                helper.assertTrue(back.getHealth() == back.getMaxHealth() && outsider.getHealth() == outsider.getMaxHealth(), "背后、名单外的不挨");
                DamageSource sweepSource = flank.getLastDamageSource();
                helper.assertTrue(sweepSource != null && !sweepSource.is(DamageTypeTags.BYPASSES_ARMOR) && sweepSource.getEntity() == sentry, "横扫吃护甲、攻击者是守望者");

                for (ServerPlayer player : List.of(main, flank, back, far, outsider)) {
                    player.setHealth(player.getMaxHealth());
                    player.invulnerableTime = 0;
                }
                int hits = sentry.suppressImpact();
                float expected = scaled(helper, (float) (D.attacks().suppressDamage() + main.getMaxHealth() * D.attacks().suppressHealthFraction()));
                helper.assertTrue(hits == 3, "镇压打中圈里名单内的三个，实际 " + hits);
                helper.assertTrue(Math.abs(main.getMaxHealth() - main.getHealth() - expected) < 0.05F, "10 + 12%，实际扣 " + (main.getMaxHealth() - main.getHealth()));
                DamageSource last = main.getLastDamageSource();
                helper.assertTrue(last != null && last.is(ECDamageTypes.ABERRANT_STRIKE) && last.getEntity() == sentry, "aberrant_strike、攻击者是守望者");
                helper.assertTrue(far.getHealth() == far.getMaxHealth() && outsider.getHealth() == outsider.getMaxHealth(), "圈外、名单外的不挨");
                helper.assertTrue(sentry.suppressCooldown() == D.attacks().suppressCooldownTicks(), "上 14 秒冷却");
            });
        } finally {
            offline(main, flank, back, far, outsider);
            discard(sentry);
        }
        helper.succeed();
    }

    // ================================================================== 掉落、打倒

    @GameTest(template = LARGE, batch = BATCH)
    public static void townSentryLootNeedsFairKill(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        TownSentryEntity sentry = sentry(helper, new Vec3(5.5D, 1D, 5.5D), null);
        ServerPlayer player = player(helper, "ts-loot", new Vec3(3.5D, 1D, 5.5D), false);
        LootTable table = level.getServer().getLootData().getLootTable(EssentialCraft5.id("entities/town_sentry"));
        try {
            helper.assertTrue(table != LootTable.EMPTY, "战利品表 entities/town_sentry 应存在");
            helper.assertTrue(roll(level, table, sentry, player).isEmpty(), "一点没打（占比 0）不掉");
            hit(sentry, player.damageSources().playerAttack(player), 10F);
            List<ItemStack> drops = roll(level, table, sentry, player);
            int titanite = count(drops, "titanite");
            int twinkling = count(drops, "twinkling_titanite");
            helper.assertTrue(count(drops, "ancient_core") == 1 && count(drops, "aberrant_heart") == 1 && titanite >= 4 && titanite <= 8
                    && twinkling >= 1 && twinkling <= 2, "上古晶核 1、异变之心 1、榍石 4~8、闪耀榍石 1~2，实际 " + drops);
            helper.assertTrue(roll(level, table, sentry, null).isEmpty(), "没有玩家最后一下不掉");
            SpawnOrigin.set(sentry, SpawnOrigin.Origin.SPAWN_EGG);
            helper.assertTrue(roll(level, table, sentry, player).isEmpty(), "刷怪蛋来的不掉");
        } finally {
            discard(sentry);
        }
        helper.succeed();
    }

    /** 打倒：打的人一份进背包、记首杀、异兽录那一步打勾，地上不掉；存档记「打倒了」，再走近这座城不再醒 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void townSentryDefeat(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "ts-kill", new Vec3(2.5D, 1D, 5.5D), true);
        List<TownSentryEntity> spawned = new ArrayList<>();
        try (FakeTown town = new FakeTown(helper, ORIGIN)) {
            ECHoanna.setTestHoannaOverride(level);
            try {
                spawned.addAll(TownSentryTrigger.check(player));
                helper.assertTrue(spawned.size() == 1, "测试前提：醒了一只");
                TownSentryEntity sentry = spawned.get(0);
                sentry.setNoAi(true);
                for (int i = 0; i < 20 && sentry.isAlive(); i++) {
                    hit(sentry, player.damageSources().playerAttack(player), 40F);
                }
                helper.assertTrue(sentry.isDeadOrDying() && sentry.defeated() && sentry.defeatedByPlayers(), "应被打倒");
                int titanite = player.getInventory().countItem(item("titanite"));
                helper.assertTrue(player.getInventory().countItem(item("ancient_core")) == 1 && player.getInventory().countItem(item("aberrant_heart")) == 1
                        && titanite >= 4 && titanite <= 8, "一份进背包");
                helper.assertTrue(PlayerDataEvents.get(player).getBossFirstKills().contains("essentialcraft5:town_sentry"), "记首杀");
                var bestiary = level.getServer().getAdvancements().getAdvancement(BossLoot.bestiaryAdvancement(sentry.getType()));
                helper.assertTrue(bestiary != null && player.getAdvancements().getOrStartProgress(bestiary).isDone(), "异兽录那一步打勾");
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, sentry.getBoundingBox().inflate(4D), Entity::isAlive).isEmpty(), "地上不掉东西");
                helper.assertTrue(TownGuardians.get(level.getServer()).state(town.town) == TownGuardians.State.DEFEATED, "存档记「打倒了」");
                TownSentryTrigger.forgetTown(town.town);
                List<TownSentryEntity> again = TownSentryTrigger.check(player);
                spawned.addAll(again);
                helper.assertTrue(again.isEmpty(), "打倒以后不再醒");
            } finally {
                ECHoanna.clearTestOverride();
            }
        } finally {
            offline(player);
            spawned.forEach(Entity::discard);
            around(helper, ExperienceOrb.class, 4D).forEach(Entity::discard);
        }
        helper.succeed();
    }

    private static List<ItemStack> roll(ServerLevel level, LootTable table, TownSentryEntity sentry, @Nullable ServerPlayer player) {
        DamageSource source = player != null ? player.damageSources().playerAttack(player) : level.damageSources().generic();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, sentry)
                .withParameter(LootContextParams.ORIGIN, sentry.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, player)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, player)
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .create(LootContextParamSets.ENTITY);
        return table.getRandomItems(params);
    }

    private static int count(List<ItemStack> stacks, String path) {
        Item item = item(path);
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    // ================================================================== 全息投影 / 审判庭哨兵的拉回

    /** NoAI 的全息投影 / 哨兵，从 pad 召出来（召唤者进名单、拉回落点是 pad 那一格） */
    private static HologramEntity holoBoss(GameTestHelper helper, EntityType<? extends HologramEntity> type, ServerPlayer summoner, BlockPos pad) {
        ServerLevel level = helper.getLevel();
        HologramEntity boss = type.create(level);
        if (boss == null) {
            throw new IllegalStateException("建不出 " + type);
        }
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(pad)).add(0D, HologramEntity.SUMMON_HEIGHT, 0D);
        boss.moveTo(at.x, at.y, at.z, 0F, 0F);
        boss.setNoAi(true);
        boss.prepareSummon(summoner, helper.absolutePos(pad));
        level.addFreshEntity(boss);
        return boss;
    }

    /** 被拉的人在落点（投影台原位）没有权限：以他的身份问过、不拉、不扣血、人留名单，按拉不回来计 10 刻；领地撤掉后照常拉回 */
    private static void pullDenied(GameTestHelper helper, EntityType<? extends HologramEntity> type, String name) {
        floor(helper);
        helper.setBlock(ORIGIN.below(), Blocks.STONE);
        ServerPlayer player = player(helper, name, new Vec3(35.5D, 1D, 5.5D), true);
        HologramEntity boss = holoBoss(helper, type, player, ORIGIN);
        Vec3 out = player.position();
        ECHologram.Config.setTestOverrides(ECHologram.Config.DEFAULTS);
        try {
            try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(ORIGIN)) {
                boss.tickRoster();
                helper.assertTrue(claims.breaksBy(ORIGIN, player.getUUID()) >= 1, "以被拉的人的身份问过落点");
            }
            helper.assertTrue(player.position().distanceToSqr(out) < EPS && player.getHealth() == player.getMaxHealth(), "没权限：不拉、不扣血");
            helper.assertTrue(boss.isParticipant(player.getUUID()) && boss.strandedTicks(player.getUUID()) == HologramEntity.ROSTER_INTERVAL,
                    "人留在名单里，按拉不回来计时，实际 " + boss.strandedTicks(player.getUUID()));
            ECProtection.forget(player.getUUID());
            boss.tickRoster();
            Vec3 landing = Vec3.atBottomCenterOf(helper.absolutePos(ORIGIN));
            helper.assertTrue(player.position().distanceTo(landing) < 1D && player.getHealth() < player.getMaxHealth(), "领地撤掉：照常拉回、扣血，实际 " + player.position());
            helper.assertTrue(boss.strandedTicks(player.getUUID()) == 0, "拉回来清零");
        } finally {
            ECHologram.Config.setTestOverrides(null);
            offline(player);
            boss.discard();
        }
        helper.succeed();
    }

    /** 拉回点被堵：不拉、不扣血、人留名单，连着拉不回来满 abandon_ticks（600 刻）按脱战移出；存读档带着累计 */
    private static void pullStranded(GameTestHelper helper, EntityType<? extends HologramEntity> type, String name) {
        floor(helper);
        helper.setBlock(ORIGIN.below(), Blocks.STONE);
        helper.setBlock(ORIGIN, Blocks.STONE);
        ServerPlayer player = player(helper, name, new Vec3(35.5D, 1D, 5.5D), true);
        HologramEntity boss = holoBoss(helper, type, player, ORIGIN);
        Vec3 out = player.position();
        ECHologram.Config.setTestOverrides(ECHologram.Config.DEFAULTS);
        int limit = ECHologram.Config.DEFAULTS.abandonTicks();
        try {
            int checks = limit / HologramEntity.ROSTER_INTERVAL;
            for (int i = 0; i < checks - 1; i++) {
                boss.tickRoster();
            }
            helper.assertTrue(player.position().distanceToSqr(out) < EPS && player.getHealth() == player.getMaxHealth(), "被堵：不拉、不扣血");
            helper.assertTrue(boss.isParticipant(player.getUUID()) && boss.strandedTicks(player.getUUID()) == limit - HologramEntity.ROSTER_INTERVAL,
                    "差一次满时长时还在名单里，实际 " + boss.strandedTicks(player.getUUID()));
            CompoundTag tag = new CompoundTag();
            boss.saveWithoutId(tag);
            HologramEntity copy = type.create(helper.getLevel());
            copy.load(tag);
            helper.assertTrue(copy.strandedTicks(player.getUUID()) == limit - HologramEntity.ROSTER_INTERVAL, "存读档带着累计");
            boss.tickRoster();
            helper.assertFalse(boss.isParticipant(player.getUUID()), "满 abandon_ticks 按脱战移出");
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "移出不扣血");
        } finally {
            ECHologram.Config.setTestOverrides(null);
            offline(player);
            boss.discard();
        }
        helper.succeed();
    }

    @GameTest(template = LARGE, batch = BATCH)
    public static void hologramPullDeniedByClaim(GameTestHelper helper) {
        pullDenied(helper, ECHologram.HOLOGRAM.get(), "ts-holo-deny");
    }

    @GameTest(template = LARGE, batch = BATCH)
    public static void hologramPullStrandedLeaves(GameTestHelper helper) {
        pullStranded(helper, ECHologram.HOLOGRAM.get(), "ts-holo-strd");
    }

    @GameTest(template = LARGE, batch = BATCH)
    public static void sentinelPullDeniedByClaim(GameTestHelper helper) {
        pullDenied(helper, ECInquisitorium.INQUISITORIUM_SENTINEL.get(), "ts-sent-deny");
    }

    @GameTest(template = LARGE, batch = BATCH)
    public static void sentinelPullStrandedLeaves(GameTestHelper helper) {
        pullStranded(helper, ECInquisitorium.INQUISITORIUM_SENTINEL.get(), "ts-sent-strd");
    }
}
