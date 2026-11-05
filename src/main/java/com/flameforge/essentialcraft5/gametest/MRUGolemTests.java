package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.corruption.PresenceClaimMemory;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.boss.BossLoot;
import com.flameforge.essentialcraft5.entity.boss.BossRoster;
import com.flameforge.essentialcraft5.entity.boss.BossTier;
import com.flameforge.essentialcraft5.entity.boss.ECBoss;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemEntity;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemGuardian;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemGuardian.State;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemTrigger;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.item.LeyDetectorItem;
import com.flameforge.essentialcraft5.item.MHSArmorItem;
import com.flameforge.essentialcraft5.item.MRUCUContainerItem;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.registry.ECLeyDetector;
import com.flameforge.essentialcraft5.registry.ECMRUCU;
import com.flameforge.essentialcraft5.registry.ECMRUGolem;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.registry.ECTesla;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 聚能魔像（第十三批 13c-M2，batch13.md §5.3.1、§12.2 13c 行）：属性与小 Boss 规矩、聚能护盾、遗迹触发（露天 / 旁边 / 站不下 / 被取消 /
 * 领地 / 落点在领地里 / 各种不该触发的人和情形）、守护记录的合并与存读档、追索、名单按水平脱战、和平难度、掉落、两个技能、2 阶魔力防护挡电弧鞭（13b 审查 B1-01）、
 * 地脉探测仪「聚集体」档、遗迹生成时的守护几率。
 * <p>
 * 测试都在 {@code empty_large}（11 × 8 × 11）里自己铺地；魔像除了触发出来的那几只都是 NoAI（不走名单、不出招，技能由测试直接调）。
 * 配置一律走 {@link ECMRUGolem.Config#setTestOverrides}，用完置回 null。用完的实体都收掉，不留给相邻的测试。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MRUGolemTests {

    private static final String LARGE = "empty_large";
    private static final float EPS = 1.0E-3F;
    private static final ECMRUGolem.Config.Values D = ECMRUGolem.Config.DEFAULTS;
    /** 聚集体放在 (5.5, 1.5, 5.5)：所在格子是 (5, 1, 5) */
    private static final Vec3 CLUSTER = new Vec3(5.5D, 1.5D, 5.5D);
    private static final BlockPos CLUSTER_CELL = new BlockPos(5, 1, 5);

    private MRUGolemTests() {
    }

    // ================================================================== 工具

    /** 铺一层石头：相对 y 层、x / z 从 0 到 10 */
    private static void layer(GameTestHelper helper, int y) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    private static MRUPresenceEntity presence(GameTestHelper helper, Vec3 relative, int mru, boolean pending) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(relative);
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, at.x, at.y, at.z, mru, 1F, false, true);
        if (pending) {
            presence.setGuardian(MRUGolemGuardian.pending(presence.blockPosition()));
        }
        level.addFreshEntity(presence);
        return presence;
    }

    /** NoAI 的魔像，脚在 relative；guarded 不是 null 时守着它；来源记结构 */
    private static MRUGolemEntity golem(GameTestHelper helper, Vec3 relative, @Nullable MRUPresenceEntity guarded) {
        ServerLevel level = helper.getLevel();
        MRUGolemEntity golem = ECMRUGolem.MRU_GOLEM.get().create(level);
        if (golem == null) {
            throw new IllegalStateException("魔像没建出来");
        }
        Vec3 at = helper.absoluteVec(relative);
        golem.moveTo(at.x, at.y, at.z, 0F, 0F);
        golem.setNoAi(true);
        golem.applyConfiguredStats();
        SpawnOrigin.set(golem, SpawnOrigin.Origin.STRUCTURE);
        if (guarded != null) {
            golem.guard(guarded);
        }
        level.addFreshEntity(golem);
        return golem;
    }

    /** 生存模式的真玩家，脚在 relative */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relative) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 要挨打的真玩家：先空跑 61 刻把出生无敌跑掉（同 CorruptionTests.victim），回满 20 血 */
    private static ServerPlayer victim(GameTestHelper helper, String name, Vec3 relative) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static void withConfig(ECMRUGolem.Config.Values values, Runnable body) {
        ECMRUGolem.Config.setTestOverrides(values);
        try {
            body.run();
        } finally {
            ECMRUGolem.Config.setTestOverrides(null);
        }
    }

    private static ECMRUGolem.Config.Values withTrigger(ECMRUGolem.Config.Trigger trigger) {
        return new ECMRUGolem.Config.Values(trigger, D.stats(), D.shield(), D.attacks(), D.detector());
    }

    private static ECMRUGolem.Config.Values withShield(ECMRUGolem.Config.Shield shield) {
        return new ECMRUGolem.Config.Values(D.trigger(), D.stats(), shield, D.attacks(), D.detector());
    }

    private static ECMRUGolem.Config.Trigger searchHeight(int height) {
        ECMRUGolem.Config.Trigger t = D.trigger();
        return new ECMRUGolem.Config.Trigger(t.radius(), t.hoannaChance(), height, t.retryTicks(), t.abandonDistance());
    }

    /** 再打一下前清掉受击无敌（同步测试里两下之间没有刻） */
    private static void hit(net.minecraft.world.entity.LivingEntity victim, DamageSource source, float amount) {
        victim.invulnerableTime = 0;
        victim.hurt(source, amount);
    }

    /** Player.hurt 按难度缩放怪物打来的、会缩放的伤害：普通难度 wanted 原样 */
    private static float scaled(GameTestHelper helper, float amount) {
        Difficulty difficulty = helper.getLevel().getDifficulty();
        return switch (difficulty) {
            case EASY -> Math.min(amount / 2F + 1F, amount);
            case HARD -> amount * 1.5F;
            case PEACEFUL -> 0F;
            default -> amount;
        };
    }

    /** 记某个实体挨的每一下 LivingDamageEvent（护盾、护甲都算完以后的数和伤害源） */
    private static final class DamageLog implements Consumer<LivingDamageEvent> {
        private final net.minecraft.world.entity.LivingEntity target;
        final List<Float> amounts = new ArrayList<>();
        final List<DamageSource> sources = new ArrayList<>();

        DamageLog(net.minecraft.world.entity.LivingEntity target) {
            this.target = target;
        }

        @Override
        public void accept(LivingDamageEvent event) {
            if (event.getEntity() == target) {
                amounts.add(event.getAmount());
                sources.add(event.getSource());
            }
        }

        float last() {
            return amounts.isEmpty() ? -1F : amounts.get(amounts.size() - 1);
        }
    }

    private static DamageLog log(net.minecraft.world.entity.LivingEntity target) {
        DamageLog log = new DamageLog(target);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDamageEvent.class, log);
        return log;
    }

    private static void discard(net.minecraft.world.entity.Entity... entities) {
        for (net.minecraft.world.entity.Entity entity : entities) {
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private static void discardAll(List<MRUGolemEntity> golems) {
        golems.forEach(MRUGolemEntity::discard);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
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

        /** 动作栏收到过几条这个语言键的消息 */
        int heard(String translationKey) {
            return (int) bar.stream().filter(line -> translationKey.equals(key(line))).count();
        }

        /** 最后一条动作栏消息（没有返回 null） */
        @Nullable
        Component last() {
            return bar.isEmpty() ? null : bar.get(bar.size() - 1);
        }
    }

    /** 生存模式、记动作栏消息的真玩家，脚在 relative */
    private static Listening listening(GameTestHelper helper, String name, Vec3 relative) {
        Listening player = new Listening(helper.getLevel(), name);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relative);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 相对坐标两个角围成的一整块（绝对坐标），给 {@code Claims.denyBox} 用 */
    private static BoundingBox box(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1) {
        return BoundingBox.fromCorners(helper.absolutePos(new BlockPos(x0, y0, z0)), helper.absolutePos(new BlockPos(x1, y1, z1)));
    }

    // ================================================================== 属性、规矩、存读档

    /**
     * 生成时按配置设属性（120 / 10 / 2 / 0.8、重锤 9、经验 40）；小 Boss；三个标签里都有；没有精英；单次封顶 25（一下 100 点魔法只扣 25）；
     * 存读档：配置改成 150 血时生成的那只读回来还是 150（属性是存下来的、不按新配置重设、也不叠加），守着的聚集体和追索中心读回来
     */
    @GameTest(template = LARGE)
    public static void golemStatsRulesAndReload(GameTestHelper helper) {
        layer(helper, 0);
        MRUGolemEntity golem = golem(helper, new Vec3(2.5D, 1D, 2.5D), null);
        MRUGolemEntity reloaded = null;
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, false);
        try {
            helper.assertTrue(golem.getMaxHealth() == 120F && golem.getHealth() == 120F, "生命 120，实际 " + golem.getMaxHealth());
            helper.assertTrue(golem.getAttributeBaseValue(Attributes.ARMOR) == 10D && golem.getAttributeBaseValue(Attributes.ARMOR_TOUGHNESS) == 2D
                    && Math.abs(golem.getAttributeBaseValue(Attributes.KNOCKBACK_RESISTANCE) - 0.8D) < 1.0E-9D
                    && golem.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 9D, "护甲 10、韧性 2、击退抗性 0.8、重锤 9");
            helper.assertTrue(golem.getExperienceReward() == 40, "经验 40，实际 " + golem.getExperienceReward());
            helper.assertTrue(golem.bossTier() == BossTier.MINI && !golem.hasEliteVariant(), "小 Boss、没有精英");
            helper.assertTrue(golem.getType().is(Tags.EntityTypes.BOSSES) && golem.getType().is(ECHoloWeapons.DIVIDER_IMMUNE)
                    && golem.getType().is(ECTesla.MRU_COIL_IMMUNE), "进 #forge:bosses、divider_immune、mru_coil_immune");
            hit(golem, helper.getLevel().damageSources().magic(), 100F);
            helper.assertTrue(Math.abs(golem.getHealth() - 95F) < EPS, "一下 100 只扣 25（单次封顶），实际剩 " + golem.getHealth());

            withConfig(new ECMRUGolem.Config.Values(D.trigger(), new ECMRUGolem.Config.Stats(150D, 10D, 2D, 0.8D, 25D, 40, 16),
                    D.shield(), D.attacks(), D.detector()), () -> {
                golem.guard(presence);
                golem.applyConfiguredStats();
            });
            helper.assertTrue(golem.getMaxHealth() == 150F, "按当时的配置设成 150");
            CompoundTag tag = golem.saveWithoutId(new CompoundTag());
            reloaded = ECMRUGolem.MRU_GOLEM.get().create(helper.getLevel());
            reloaded.load(tag);
            helper.assertTrue(reloaded.getMaxHealth() == 150F && reloaded.getHealth() == 150F, "读回来还是 150（不按现在的配置重设），实际 " + reloaded.getMaxHealth());
            helper.assertTrue(reloaded.getAttribute(Attributes.MAX_HEALTH).getModifiers().isEmpty(), "读档不叠修饰符");
            helper.assertTrue(presence.getUUID().equals(reloaded.guardedPresenceId()) && presence.blockPosition().equals(reloaded.home())
                    && golem.returnPoint().equals(reloaded.returnPoint()), "守着的聚集体、追索中心、回去的点读回来");
            // 经验不存盘：读回来的那只按打倒时的配置给（不是构造器里的默认 40）
            MRUGolemEntity back = reloaded;
            helper.assertTrue(back.getExperienceReward() == 40, "默认配置下经验 40，实际 " + back.getExperienceReward());
            withConfig(new ECMRUGolem.Config.Values(D.trigger(), new ECMRUGolem.Config.Stats(150D, 10D, 2D, 0.8D, 25D, 200, 16),
                    D.shield(), D.attacks(), D.detector()), () -> helper.assertTrue(back.getExperienceReward() == 200,
                    "经验改成 200 后读回来的那只按 200 给，实际 " + back.getExperienceReward()));
        } finally {
            discard(golem, reloaded, presence);
        }
        helper.succeed();
    }

    // ================================================================== 聚能护盾

    /**
     * 护盾：聚集体 1 万、3 格外。一下 10 点魔法全抵（扣 400 MRU、不掉血）；一下 100 先按封顶夹成 25 再抵（扣 1 000）；
     * 玩家近战 10 点过护甲剩 7.6，整下抵掉：不算打实，名单、伤害账、玩家占比都不记
     */
    @GameTest(template = LARGE)
    public static void golemShieldAbsorbs(GameTestHelper helper) {
        layer(helper, 0);
        MRUPresenceEntity presence = presence(helper, CLUSTER, 10000, false);
        MRUGolemEntity golem = golem(helper, new Vec3(2.5D, 1D, 5.5D), presence);
        try {
            golem.refreshGuard();
            helper.assertTrue(golem.shieldSource() == presence && golem.isShielded(), "3 格外、1 万 MRU 时护盾在");
            hit(golem, helper.getLevel().damageSources().magic(), 10F);
            helper.assertTrue(golem.getHealth() == 120F && presence.getMRU() == 9600, "10 点全抵、扣 400，实际血 " + golem.getHealth() + "、MRU " + presence.getMRU());
            hit(golem, helper.getLevel().damageSources().magic(), 100F);
            helper.assertTrue(golem.getHealth() == 120F && presence.getMRU() == 8600, "100 先夹成 25、扣 1000，实际 MRU " + presence.getMRU());

            ServerPlayer player = player(helper, "ec5-golem-shield", new Vec3(1.5D, 1D, 5.5D));
            hit(golem, player.damageSources().playerAttack(player), 10F);
            helper.assertTrue(golem.getHealth() == 120F && presence.getMRU() == 8600 - 304, "过护甲剩 7.6，整下抵掉扣 304，实际 MRU " + presence.getMRU());
            helper.assertTrue(golem.bossRoster().totalDealt() == 0F && golem.bossRoster().dealt(player.getUUID()) == 0F
                    && golem.playerDamage(player.getUUID()) == 0F && !golem.bossRoster().contains(player.getUUID()),
                    "整下抵掉不算打实：不进名单、不记账");
        } finally {
            discard(golem, presence);
        }
        helper.succeed();
    }

    /**
     * 护盾抵到聚集体扣光为止：520 MRU 时一下 25 只抵 13 点、聚集体扣到 0、掉 12 血；之后不到 500 不再抵；
     * 一开始就只有 499 的不抵；离聚集体水平 17 格不抵；守的不是这团（旁边另一团）不抵；配置每点 0 MRU = 没有护盾
     */
    @GameTest(template = LARGE)
    public static void golemShieldLimits(GameTestHelper helper) {
        layer(helper, 0);
        ServerLevel level = helper.getLevel();
        MRUPresenceEntity low = presence(helper, CLUSTER, 520, false);
        MRUGolemEntity golem = golem(helper, new Vec3(2.5D, 1D, 5.5D), low);
        MRUPresenceEntity poor = presence(helper, new Vec3(8.5D, 1.5D, 8.5D), 499, false);
        MRUGolemEntity second = golem(helper, new Vec3(8.5D, 1D, 6.5D), poor);
        MRUPresenceEntity rich = presence(helper, new Vec3(2.5D, 1.5D, 9.5D), 10000, false);
        MRUGolemEntity far = golem(helper, new Vec3(2.5D, 1D, 9.5D).add(17D, 0D, 0D), rich);
        MRUGolemEntity stranger = golem(helper, new Vec3(3.5D, 1D, 9.5D), null);
        MRUGolemEntity configured = golem(helper, new Vec3(1.5D, 1D, 9.5D), rich);
        try {
            hit(golem, level.damageSources().magic(), 25F);
            helper.assertTrue(low.getMRU() == 0 && Math.abs(golem.getHealth() - 108F) < EPS, "520 只够抵 13 点：扣光、掉 12，实际 MRU "
                    + low.getMRU() + "、血 " + golem.getHealth());
            hit(golem, level.damageSources().magic(), 10F);
            helper.assertTrue(Math.abs(golem.getHealth() - 98F) < EPS, "扣光以后不再抵，实际血 " + golem.getHealth());

            hit(second, level.damageSources().magic(), 10F);
            helper.assertTrue(Math.abs(second.getHealth() - 110F) < EPS && poor.getMRU() == 499, "499 不到 500 不抵");

            hit(far, level.damageSources().magic(), 10F);
            helper.assertTrue(Math.abs(far.getHealth() - 110F) < EPS && rich.getMRU() == 10000, "水平 17 格外不抵");

            hit(stranger, level.damageSources().magic(), 10F);
            helper.assertTrue(Math.abs(stranger.getHealth() - 110F) < EPS && rich.getMRU() == 10000, "没守着的那团不给护盾");

            withConfig(withShield(new ECMRUGolem.Config.Shield(16, 500, 0)), () -> hit(configured, level.damageSources().magic(), 10F));
            helper.assertTrue(Math.abs(configured.getHealth() - 110F) < EPS && rich.getMRU() == 10000, "每点 0 MRU = 没有护盾");
            hit(configured, level.damageSources().magic(), 10F);
            helper.assertTrue(Math.abs(configured.getHealth() - 110F) < EPS && rich.getMRU() == 9600, "配置还原以后照常抵");
        } finally {
            discard(golem, second, far, stranger, configured, low, poor, rich);
        }
        helper.succeed();
    }

    // ================================================================== 触发

    /**
     * 埋着的遗迹（头顶 3 格是石板）：玩家走到 2 格内，魔像出现在聚集体正上方的露天地面（同一 x / z，石板上面）；
     * 聚集体记成「已触发」、来源是结构（公平）、触发的人进名单、魔像守着这团；同一个人、另一个人再查都不会再出来第二只
     */
    @GameTest(template = LARGE)
    public static void golemTriggerOpenAir(GameTestHelper helper) {
        layer(helper, 0);
        layer(helper, 4);
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, true);
        Listening player = listening(helper, "ec5-golem-a", new Vec3(3.5D, 1D, 5.5D));
        ServerPlayer other = player(helper, "ec5-golem-b", new Vec3(7.5D, 1D, 5.5D));
        List<MRUGolemEntity> golems = new ArrayList<>();
        try {
            golems.addAll(MRUGolemTrigger.check(player));
            helper.assertTrue(golems.size() == 1, "应出来一只，实际 " + golems.size());
            MRUGolemEntity golem = golems.get(0);
            BlockPos top = helper.absolutePos(new BlockPos(5, 5, 5));
            helper.assertTrue(golem.blockPosition().equals(top), "应站在聚集体正上方的露天地面 " + top + "，实际 " + golem.blockPosition());
            helper.assertTrue(golem.isAlive() && helper.getLevel().getEntity(golem.getUUID()) == golem, "真的进了世界");
            helper.assertTrue(presence.getGuardian().state() == State.SPAWNED, "聚集体记成已触发，实际 " + presence.getGuardian());
            helper.assertTrue(SpawnOrigin.of(golem) == SpawnOrigin.Origin.STRUCTURE && SpawnOrigin.isFair(golem), "来源是结构、公平");
            helper.assertTrue(golem.bossRoster().contains(player.getUUID()) && presence.getUUID().equals(golem.guardedPresenceId()),
                    "触发的人进名单、守着这团");
            helper.assertTrue(golem.getMaxHealth() == 120F, "按配置设了属性");
            Component told = player.last();
            helper.assertTrue(told != null && MRUGolemTrigger.MSG_AWAKENED_ABOVE.equals(key(told))
                            && told.getContents() instanceof TranslatableContents tr && tr.getArgs().length == 1 && Integer.valueOf(4).equals(tr.getArgs()[0]),
                    "露天地面比他高 4 格：动作栏说在头顶 4 格，实际 " + told);
            golems.addAll(MRUGolemTrigger.check(player));
            golems.addAll(MRUGolemTrigger.check(other));
            helper.assertTrue(golems.size() == 1, "已触发的不再出来，实际 " + golems.size());
        } finally {
            discardAll(golems);
            discard(presence);
        }
        helper.succeed();
    }

    /**
     * 往上找不到露天地面（石板 4 层厚、只往上找 2 格），聚集体正上方那格又被一块石头堵住：在旁边找站得下的地方（水平 2 格），
     * 站在聚集体那一层
     */
    @GameTest(template = LARGE)
    public static void golemTriggerBeside(GameTestHelper helper) {
        layer(helper, 0);
        for (int y = 4; y <= 7; y++) {
            layer(helper, y);
        }
        helper.setBlock(new BlockPos(5, 2, 5), Blocks.STONE);
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, true);
        Listening player = listening(helper, "ec5-golem-side", new Vec3(8.5D, 1D, 5.5D));
        List<MRUGolemEntity> golems = new ArrayList<>();
        try {
            withConfig(withTrigger(searchHeight(2)), () -> golems.addAll(MRUGolemTrigger.check(player)));
            helper.assertTrue(golems.size() == 1, "应在旁边出来，实际 " + golems.size());
            MRUGolemEntity golem = golems.get(0);
            double distance = horizontal(golem.position(), presence.position());
            helper.assertTrue(Math.abs(distance - 2D) < 1.0E-6D && golem.blockPosition().getY() == presence.blockPosition().getY(),
                    "应在水平 2 格、同一层，实际 " + distance + " / " + golem.blockPosition());
            helper.assertTrue(presence.getGuardian().state() == State.SPAWNED, "已触发");
            helper.assertTrue(player.heard(MRUGolemTrigger.MSG_AWAKENED) == 1 && player.heard(MRUGolemTrigger.MSG_AWAKENED_ABOVE) == 0, "旁边出来的只说醒了");
            // 旁边找的站位比触发的人高也不说「在头顶的地面上」（它在洞里）；露天那一列的才说
            helper.assertTrue(MRUGolemTrigger.MSG_AWAKENED.equals(key(MRUGolemTrigger.awakenedMessage(1, 9, false)))
                    && MRUGolemTrigger.MSG_AWAKENED_ABOVE.equals(key(MRUGolemTrigger.awakenedMessage(1, 9, true)))
                    && MRUGolemTrigger.MSG_AWAKENED.equals(key(MRUGolemTrigger.awakenedMessage(1, 3, true))), "按站位来源和高度选那句");
        } finally {
            discardAll(golems);
            discard(presence);
        }
        helper.succeed();
    }

    /**
     * 站不下：聚集体埋在实心石头里（只往上找 0 格）→ 不出来、仍然等着；腾出地方后 40 刻内再查不试（重试间隔），
     * 清掉重试计时再查就出来
     */
    @GameTest(template = LARGE)
    public static void golemTriggerNoRoomRetries(GameTestHelper helper) {
        layer(helper, 0);
        for (int y = 1; y <= 6; y++) {
            layer(helper, y);
        }
        helper.setBlock(CLUSTER_CELL, Blocks.AIR);
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, true);
        Listening player = listening(helper, "ec5-golem-room", new Vec3(5.5D, 1D, 2.5D));
        List<MRUGolemEntity> golems = new ArrayList<>();
        try {
            withConfig(withTrigger(searchHeight(0)), () -> {
                golems.addAll(MRUGolemTrigger.check(player));
                helper.assertTrue(golems.isEmpty() && presence.getGuardian().pending(), "站不下时不出来、仍然等着");
                helper.assertTrue(player.heard(MRUGolemTrigger.MSG_NO_ROOM) == 1, "动作栏说找不到地方站，实际 " + player.bar);
                for (int y = 1; y <= 3; y++) {
                    for (int x = 0; x <= 10; x++) {
                        for (int z = 0; z <= 10; z++) {
                            helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                        }
                    }
                }
                golems.addAll(MRUGolemTrigger.check(player));
                helper.assertTrue(golems.isEmpty(), "重试间隔没到不再试");
                helper.assertTrue(player.heard(MRUGolemTrigger.MSG_NO_ROOM) == 1, "没再试也就没再说");
                MRUGolemTrigger.forgetRetry(presence.getUUID());
                golems.addAll(MRUGolemTrigger.check(player));
            });
            helper.assertTrue(golems.size() == 1 && presence.getGuardian().state() == State.SPAWNED, "腾出地方、到点以后出来");
        } finally {
            discardAll(golems);
            discard(presence);
        }
        helper.succeed();
    }

    /**
     * 生成被拦下（混合端插件取消、别的模组取消整次生成）：加进世界失败 → 不记已触发，仍然等着；拿掉拦截、到点以后再查就出来
     */
    @GameTest(template = LARGE)
    public static void golemTriggerCancelledStaysPending(GameTestHelper helper) {
        layer(helper, 0);
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, true);
        ServerPlayer player = player(helper, "ec5-golem-veto", new Vec3(3.5D, 1D, 5.5D));
        List<MRUGolemEntity> golems = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> join = event -> {
            if (event.getEntity() instanceof MRUGolemEntity) {
                event.setCanceled(true);
            }
        };
        Consumer<MobSpawnEvent.FinalizeSpawn> finalize = event -> {
            if (event.getEntity() instanceof MRUGolemEntity) {
                event.setSpawnCancelled(true);
            }
        };
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, join);
            try {
                golems.addAll(MRUGolemTrigger.check(player));
            } finally {
                MinecraftForge.EVENT_BUS.unregister(join);
            }
            helper.assertTrue(golems.isEmpty() && presence.getGuardian().pending(), "进世界被取消：不出来、仍然等着，实际 " + presence.getGuardian());

            MRUGolemTrigger.forgetRetry(presence.getUUID());
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MobSpawnEvent.FinalizeSpawn.class, finalize);
            try {
                golems.addAll(MRUGolemTrigger.check(player));
            } finally {
                MinecraftForge.EVENT_BUS.unregister(finalize);
            }
            helper.assertTrue(golems.isEmpty() && presence.getGuardian().pending(), "整次生成被取消：仍然等着");

            MRUGolemTrigger.forgetRetry(presence.getUUID());
            golems.addAll(MRUGolemTrigger.check(player));
            helper.assertTrue(golems.size() == 1 && presence.getGuardian().state() == State.SPAWNED, "拦截拿掉以后出来");
        } finally {
            discardAll(golems);
            discard(presence);
        }
        helper.succeed();
    }

    /**
     * 不该触发的：创造、旁观、假玩家、9 格外、和平难度、触发半径配成 0、没有守护的聚集体（老存档）、已放弃的；
     * 聚集体那格是别人领地（这名玩家动不了，batch13.md §12.4 C67）。最后换一名能动的生存玩家走近，出来了
     */
    @GameTest(template = LARGE)
    public static void golemTriggerFilters(GameTestHelper helper) {
        layer(helper, 0);
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, true);
        MRUPresenceEntity old = presence(helper, new Vec3(1.5D, 1.5D, 1.5D), 8000, false);
        MRUPresenceEntity abandoned = presence(helper, new Vec3(9.5D, 1.5D, 1.5D), 8000, true);
        abandoned.setGuardian(abandoned.getGuardian().with(State.ABANDONED));
        Vec3 near = new Vec3(3.5D, 1D, 5.5D);
        List<MRUGolemEntity> golems = new ArrayList<>();
        try {
            ServerPlayer creative = player(helper, "ec5-golem-cr", near);
            creative.setGameMode(GameType.CREATIVE);
            golems.addAll(MRUGolemTrigger.check(creative));
            ServerPlayer spectator = player(helper, "ec5-golem-sp", near);
            spectator.setGameMode(GameType.SPECTATOR);
            golems.addAll(MRUGolemTrigger.check(spectator));
            ServerPlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
            fake.setGameMode(GameType.SURVIVAL);
            Vec3 at = helper.absoluteVec(near);
            fake.moveTo(at.x, at.y, at.z, 0F, 0F);
            golems.addAll(MRUGolemTrigger.check(fake));
            ServerPlayer away = player(helper, "ec5-golem-far", new Vec3(5.5D, 1D, 5.5D).add(9.2D, 0D, 0D));
            golems.addAll(MRUGolemTrigger.check(away));
            helper.assertTrue(golems.isEmpty() && presence.getGuardian().pending(), "创造 / 旁观 / 假玩家 / 9 格外都不触发");

            ServerPlayer survivor = player(helper, "ec5-golem-ok", near);
            MRUGolemTrigger.setTestPeaceful(true);
            try {
                golems.addAll(MRUGolemTrigger.check(survivor));
            } finally {
                MRUGolemTrigger.setTestPeaceful(null);
            }
            ECMRUGolem.Config.Trigger t = D.trigger();
            withConfig(withTrigger(new ECMRUGolem.Config.Trigger(0, t.hoannaChance(), t.searchHeight(), t.retryTicks(), t.abandonDistance())),
                    () -> golems.addAll(MRUGolemTrigger.check(survivor)));
            helper.assertTrue(golems.isEmpty() && presence.getGuardian().pending(), "和平难度、半径 0 都不触发");

            ServerPlayer oldVisitor = player(helper, "ec5-golem-old", new Vec3(1.5D, 1D, 3.5D));
            ServerPlayer abandonVisitor = player(helper, "ec5-golem-ab", new Vec3(9.5D, 1D, 3.5D));
            try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(CLUSTER_CELL)) {
                golems.addAll(MRUGolemTrigger.check(oldVisitor));
                golems.addAll(MRUGolemTrigger.check(abandonVisitor));
                ServerPlayer guest = player(helper, "ec5-golem-gst", near);
                golems.addAll(MRUGolemTrigger.check(guest));
                PresenceClaimMemory.forget(guest.getUUID());
            }
            helper.assertTrue(golems.isEmpty() && presence.getGuardian().pending(), "没有守护、已放弃、在别人领地里都不触发");
            helper.assertTrue(old.getGuardian().state() == State.NONE && abandoned.getGuardian().state() == State.ABANDONED, "那两团不变");

            ServerPlayer owner = player(helper, "ec5-golem-own", near);
            golems.addAll(MRUGolemTrigger.check(owner));
            helper.assertTrue(golems.size() == 1 && presence.getGuardian().state() == State.SPAWNED, "能动的生存玩家走近就出来");
            PresenceClaimMemory.forget(owner.getUUID());
        } finally {
            MRUGolemTrigger.setTestPeaceful(null);
            discardAll(golems);
            discard(presence, old, abandoned);
        }
        helper.succeed();
    }

    /**
     * 落点也要触发的人能动（13c 审查 C3-01，batch13.md §5.3.1）：聚集体那一格能动，正上方露天那一格是别人的领地 → 以他的身份问过那一格、
     * 被拒，退到旁边找，魔像出在聚集体那一层；只说醒了（不是露天那一列，不说「头顶的地面上」）
     */
    @GameTest(template = LARGE)
    public static void golemTriggerSpotClaimed(GameTestHelper helper) {
        layer(helper, 0);
        layer(helper, 4);
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, true);
        Listening player = listening(helper, "ec5-golem-land", new Vec3(3.5D, 1D, 5.5D));
        BlockPos top = new BlockPos(5, 5, 5);
        List<MRUGolemEntity> golems = new ArrayList<>();
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(top)) {
            golems.addAll(MRUGolemTrigger.check(player));
            helper.assertTrue(claims.breaksBy(top, player.getUUID()) >= 1, "应以触发者的身份问过正上方露天那一格");
            helper.assertTrue(golems.size() == 1, "露天那格被拒应退到旁边出来，实际 " + golems.size());
            MRUGolemEntity golem = golems.get(0);
            helper.assertTrue(!golem.blockPosition().equals(helper.absolutePos(top)) && golem.blockPosition().getY() == presence.blockPosition().getY(),
                    "不站进领地、站在聚集体那一层，实际 " + golem.blockPosition());
            helper.assertTrue(presence.getGuardian().state() == State.SPAWNED, "已触发");
            helper.assertTrue(player.heard(MRUGolemTrigger.MSG_AWAKENED) == 1 && player.heard(MRUGolemTrigger.MSG_AWAKENED_ABOVE) == 0,
                    "旁边出来的只说醒了，实际 " + player.bar);
        } finally {
            PresenceClaimMemory.forget(player.getUUID());
            ECProtection.forget(player.getUUID());
            MRUGolemTrigger.forgetLanding(presence.getUUID());
            discardAll(golems);
            discard(presence);
        }
        helper.succeed();
    }

    /**
     * 能站的地方全在别人领地里（聚集体悬在半空、自己那一格站不了，3×3 的房间里能站的 9 格全被拒；只往上找 2 格，碰不到房顶上面）：
     * 第一次只新问 8 格（连聚集体那一格共 9 次探测），问不完 → 不出、不说「找不到地方」；第二次只问剩下那 1 格，仍然不出、聚集体还等着，
     * 动作栏说找不到地方站；第三次一格都不重问（结论记着）。领地撤掉、忘掉结论以后再查就出来，站在房间里
     */
    @GameTest(template = LARGE)
    public static void golemTriggerLandingProbeBudget(GameTestHelper helper) {
        layer(helper, 0);
        for (int y = 1; y <= 7; y++) {
            layer(helper, y);
        }
        for (int x = 3; x <= 7; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 3; z <= 7; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        MRUPresenceEntity presence = presence(helper, new Vec3(5.5D, 2.5D, 5.5D), 6000, true);
        Listening player = listening(helper, "ec5-golem-bud", new Vec3(3.5D, 1D, 5.5D));
        int[] probes = new int[1];
        Consumer<BlockEvent.BreakEvent> counter = event -> {
            if (ECProtection.isProbing() && event.getPlayer() == player) {
                probes[0]++;
            }
        };
        List<MRUGolemEntity> golems = new ArrayList<>();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, counter);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            // 只放过聚集体那一格 (5, 2, 5)
            claims.denyBox(box(helper, 0, -4, 0, 4, 60, 10)).denyBox(box(helper, 6, -4, 0, 10, 60, 10))
                    .denyBox(box(helper, 5, -4, 0, 5, 60, 4)).denyBox(box(helper, 5, -4, 6, 5, 60, 10))
                    .denyBox(box(helper, 5, -4, 5, 5, 1, 5)).denyBox(box(helper, 5, 3, 5, 5, 60, 5));
            withConfig(withTrigger(searchHeight(2)), () -> {
                golems.addAll(MRUGolemTrigger.check(player));
                helper.assertTrue(golems.isEmpty() && presence.getGuardian().pending(), "第一次：不出、还等着");
                helper.assertTrue(probes[0] == 1 + MRUGolemTrigger.MAX_LANDING_PROBES, "第一次：聚集体那格 1 次 + 落点 8 次，实际 " + probes[0]);
                helper.assertTrue(player.heard(MRUGolemTrigger.MSG_NO_ROOM) == 0, "没问完不说找不到地方");

                probes[0] = 0;
                MRUGolemTrigger.forgetRetry(presence.getUUID());
                golems.addAll(MRUGolemTrigger.check(player));
                helper.assertTrue(golems.isEmpty() && presence.getGuardian().pending(), "第二次：还是不出、还等着");
                helper.assertTrue(probes[0] == 1, "第二次：只问剩下的 1 格，实际 " + probes[0]);
                helper.assertTrue(player.heard(MRUGolemTrigger.MSG_NO_ROOM) == 1, "问完都不行：说找不到地方站，实际 " + player.bar);

                probes[0] = 0;
                MRUGolemTrigger.forgetRetry(presence.getUUID());
                golems.addAll(MRUGolemTrigger.check(player));
                helper.assertTrue(golems.isEmpty() && probes[0] == 0, "第三次：结论记着，一格都不重问，实际 " + probes[0]);

                claims.clearBoxes();
                MRUGolemTrigger.forgetLanding(presence.getUUID());
                ECProtection.forget(player.getUUID());
                MRUGolemTrigger.forgetRetry(presence.getUUID());
                golems.addAll(MRUGolemTrigger.check(player));
            });
            helper.assertTrue(golems.size() == 1 && presence.getGuardian().state() == State.SPAWNED, "领地撤掉以后出来，实际 " + golems.size());
            BlockPos at = golems.get(0).blockPosition();
            BlockPos lo = helper.absolutePos(new BlockPos(3, 1, 3));
            BlockPos hi = helper.absolutePos(new BlockPos(7, 1, 7));
            helper.assertTrue(at.getY() == lo.getY() && at.getX() >= Math.min(lo.getX(), hi.getX()) && at.getX() <= Math.max(lo.getX(), hi.getX())
                    && at.getZ() >= Math.min(lo.getZ(), hi.getZ()) && at.getZ() <= Math.max(lo.getZ(), hi.getZ()), "站在房间地面上，实际 " + at);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
            PresenceClaimMemory.forget(player.getUUID());
            ECProtection.forget(player.getUUID());
            MRUGolemTrigger.forgetLanding(presence.getUUID());
            MRUGolemTrigger.forgetRetry(presence.getUUID());
            discardAll(golems);
            discard(presence);
        }
        helper.succeed();
    }

    // ================================================================== 守护记录

    /**
     * 合并（「已触发 / 已放弃」优先，pending 不带进别人那团）、存读档（没有守护不写键；老存档没有键 = 没有；认不出的、pending 缺原位 = 没有）、
     * 实体真的合并、收容瓶放出来的没有守护
     */
    @GameTest(template = LARGE)
    public static void golemGuardianMergeAndSave(GameTestHelper helper) {
        layer(helper, 0);
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos b = helper.absolutePos(new BlockPos(9, 1, 9));
        MRUGolemGuardian pa = MRUGolemGuardian.pending(a);
        MRUGolemGuardian pb = MRUGolemGuardian.pending(b);
        MRUGolemGuardian none = MRUGolemGuardian.NONE;
        helper.assertTrue(MRUGolemGuardian.merge(none, none).state() == State.NONE, "都没有 → 没有");
        helper.assertTrue(MRUGolemGuardian.merge(pa, none) == pa, "自己等着、对方没有 → 留自己的");
        MRUGolemGuardian swallowed = MRUGolemGuardian.merge(none, pa);
        helper.assertTrue(swallowed.state() == State.ABANDONED && a.equals(swallowed.origin()), "等着的被别人吞了 → 放弃，实际 " + swallowed);
        helper.assertTrue(MRUGolemGuardian.merge(pa, pb) == pa, "两团都等着 → 留吞的那团");
        helper.assertTrue(MRUGolemGuardian.merge(pa.with(State.SPAWNED), pb).state() == State.SPAWNED, "已触发压过等着");
        helper.assertTrue(MRUGolemGuardian.merge(pa, pb.with(State.SPAWNED)).state() == State.SPAWNED, "对方已触发 → 取对方");
        helper.assertTrue(MRUGolemGuardian.merge(pa, pb.with(State.ABANDONED)).state() == State.ABANDONED, "对方已放弃 → 取对方");
        helper.assertTrue(MRUGolemGuardian.merge(none, pb.with(State.ABANDONED)).state() == State.ABANDONED, "放弃也压过没有");

        CompoundTag saved = new CompoundTag();
        pa.save(saved);
        MRUGolemGuardian read = MRUGolemGuardian.load(saved);
        helper.assertTrue(read.pending() && a.equals(read.origin()), "等着的存读档带原位");
        CompoundTag empty = new CompoundTag();
        none.save(empty);
        helper.assertTrue(!empty.contains(MRUGolemGuardian.TAG_STATE) && !empty.contains(MRUGolemGuardian.TAG_ORIGIN), "没有守护不写键");
        helper.assertTrue(MRUGolemGuardian.load(new CompoundTag()).state() == State.NONE, "老存档没有键 = 没有守护");
        CompoundTag weird = new CompoundTag();
        weird.putString(MRUGolemGuardian.TAG_STATE, "awake");
        CompoundTag broken = new CompoundTag();
        broken.putString(MRUGolemGuardian.TAG_STATE, "pending");
        helper.assertTrue(MRUGolemGuardian.load(weird).state() == State.NONE && MRUGolemGuardian.load(broken).state() == State.NONE,
                "认不出的、等着却没原位的当没有");

        Vec3 at = helper.absoluteVec(CLUSTER);
        MRUPresenceEntity ruin = MRUPresenceEntity.create(level, at.x, at.y, at.z, 6000, 1F, false, true);
        ruin.setGuardian(MRUGolemGuardian.pending(ruin.blockPosition()));
        MRUPresenceEntity bigger = MRUPresenceEntity.create(level, at.x, at.y, at.z, 9000, 1F, false, true);
        bigger.absorb(ruin);
        helper.assertTrue(bigger.getGuardian().state() == State.ABANDONED && !ruin.isAlive(), "大团吞了遗迹那团：放弃，实际 " + bigger.getGuardian());
        MRUPresenceEntity keeper = MRUPresenceEntity.create(level, at.x, at.y, at.z, 6000, 1F, false, true);
        keeper.setGuardian(MRUGolemGuardian.pending(keeper.blockPosition()));
        MRUPresenceEntity small = MRUPresenceEntity.create(level, at.x, at.y, at.z, 100, 1F, false, true);
        keeper.absorb(small);
        helper.assertTrue(keeper.getGuardian().pending(), "遗迹那团吞了小团：还等着");

        CompoundTag entityTag = keeper.saveWithoutId(new CompoundTag());
        helper.assertTrue("pending".equals(entityTag.getString(MRUGolemGuardian.TAG_STATE)), "聚集体 NBT 里写着 pending");
        MRUPresenceEntity copy = new MRUPresenceEntity(ECMRUCU.MRU_PRESENCE.get(), level);
        copy.load(entityTag);
        helper.assertTrue(copy.getGuardian().pending() && keeper.blockPosition().equals(copy.getGuardian().origin()), "聚集体读档带回守护");
        CompoundTag oldTag = small.saveWithoutId(new CompoundTag());
        helper.assertFalse(oldTag.contains(MRUGolemGuardian.TAG_STATE), "没有守护的聚集体 NBT 里没有 guardian 键");

        ServerPlayer player = player(helper, "ec5-golem-flask", new Vec3(2.5D, 1D, 2.5D));
        ItemStack flask = new ItemStack(ECGear.MRUCU_CONTAINER.get());
        MRUCUContainerItem.setContents(flask, new MRUCUContainerItem.Contents(8000, 1F, true));
        Vec3 dest = helper.absoluteVec(new Vec3(5.5D, 1.5D, 2.5D));
        helper.assertTrue(MRUCUContainerItem.release(flask, level, player, dest), "收容瓶应放成");
        MRUPresenceEntity released = MRUPresenceEntity.findClosest(level, BlockPos.containing(dest), 2);
        helper.assertTrue(released != null && released.getGuardian().state() == State.NONE, "收容瓶放出来的没有守护");
        discard(released);
        helper.succeed();
    }

    /**
     * 挪走：等着的聚集体离原位 16 格还算，17 格就放弃（之后挪回来也还是放弃，走近不触发）；已触发的再挪远不改记录
     */
    @GameTest(template = LARGE)
    public static void golemGuardianDrift(GameTestHelper helper) {
        layer(helper, 0);
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, true);
        MRUPresenceEntity spawned = presence(helper, new Vec3(1.5D, 1.5D, 1.5D), 6000, true);
        spawned.setGuardian(spawned.getGuardian().with(State.SPAWNED));
        List<MRUGolemEntity> golems = new ArrayList<>();
        try {
            Vec3 start = presence.position();
            presence.moveTo(start.x + 16D, start.y, start.z);
            helper.assertFalse(MRUGolemTrigger.checkDrift(presence), "16 格还不算挪走");
            helper.assertTrue(presence.getGuardian().pending(), "还等着");
            presence.moveTo(start.x + 17D, start.y, start.z);
            helper.assertTrue(MRUGolemTrigger.checkDrift(presence) && presence.getGuardian().state() == State.ABANDONED, "17 格算放弃");
            presence.moveTo(start.x, start.y, start.z);
            ServerPlayer player = player(helper, "ec5-golem-drift", new Vec3(3.5D, 1D, 5.5D));
            golems.addAll(MRUGolemTrigger.check(player));
            helper.assertTrue(golems.isEmpty() && presence.getGuardian().state() == State.ABANDONED, "挪回来也不再触发");
            Vec3 there = spawned.position();
            spawned.moveTo(there.x + 40D, there.y, there.z);
            helper.assertFalse(MRUGolemTrigger.checkDrift(spawned), "已触发的不看挪没挪");
            helper.assertTrue(spawned.getGuardian().state() == State.SPAWNED, "已触发的记录不变");
        } finally {
            discardAll(golems);
            discard(presence, spawned);
        }
        helper.succeed();
    }

    // ================================================================== 追索、和平难度

    /**
     * 追索按水平距离量：离聚集体水平 20 格算跑远、10 格不算；站在聚集体正上方 40 格（埋着的遗迹）不算、60 格算；
     * 护盾同样按水平 + 上下余量算（正上方 40 格照样有护盾）
     */
    @GameTest(template = LARGE)
    public static void golemLeashIsHorizontal(GameTestHelper helper) {
        layer(helper, 0);
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, false);
        MRUGolemEntity golem = golem(helper, new Vec3(5.5D, 1D, 5.5D), presence);
        try {
            Vec3 home = Vec3.atBottomCenterOf(presence.blockPosition());
            golem.moveTo(home.x + 20D, home.y, home.z);
            helper.assertTrue(golem.outsideLeash(0D), "水平 20 格算跑远");
            golem.moveTo(home.x + 10D, home.y, home.z);
            helper.assertFalse(golem.outsideLeash(0D), "水平 10 格不算");
            golem.moveTo(home.x, home.y + 40D, home.z);
            helper.assertFalse(golem.outsideLeash(0D), "正上方 40 格不算");
            helper.assertTrue(golem.shieldSource() == presence, "正上方 40 格照样有护盾");
            golem.moveTo(home.x, home.y + 60D, home.z);
            helper.assertTrue(golem.outsideLeash(0D) && golem.shieldSource() == null, "正上方 60 格算跑远、没有护盾");
            golem.moveTo(home.x + 13D, home.y, home.z);
            helper.assertTrue(golem.outsideLeash(-4D) && !golem.outsideLeash(0D), "往回走走到 12 格以内才停");
        } finally {
            discard(golem, presence);
        }
        helper.succeed();
    }

    /**
     * 名单脱战按水平量（13c 审查 C3-02）：聚集体在地下、正上方 41 格铺一块石台，魔像出在石台上（直线距离 41 &gt; 32）。触发的人留在名单里、
     * 不收「退出了战斗」，动作栏说魔像在头顶 41 格；水平 40 格外放箭被加进来的人静默移出；在范围里待过、再跑到水平 40 格外的人移出并收到那句
     */
    @GameTest(template = LARGE)
    public static void golemRosterMeasuresHorizontally(GameTestHelper helper) {
        layer(helper, 0);
        ServerLevel level = helper.getLevel();
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, true);
        List<BlockPos> deck = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = helper.absolutePos(new BlockPos(5 + dx, 41, 5 + dz));
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                deck.add(pos);
            }
        }
        Listening trigger = listening(helper, "ec5-golem-down", new Vec3(4.5D, 1D, 5.5D));
        Listening archer = listening(helper, "ec5-golem-far", new Vec3(5.5D + 40D, 42D, 5.5D));
        Listening runner = listening(helper, "ec5-golem-run", new Vec3(7.5D, 42D, 5.5D));
        List<MRUGolemEntity> golems = new ArrayList<>();
        try {
            for (ServerPlayer player : List.of(trigger, archer, runner)) {
                online(player);
            }
            golems.addAll(MRUGolemTrigger.check(trigger));
            helper.assertTrue(golems.size() == 1, "应出来一只，实际 " + golems.size());
            MRUGolemEntity golem = golems.get(0);
            helper.assertTrue(golem.blockPosition().equals(helper.absolutePos(new BlockPos(5, 42, 5))), "站在石台上，实际 " + golem.blockPosition());
            helper.assertTrue(golem.position().distanceTo(trigger.position()) > MRUGolemEntity.ESCAPE_RANGE, "直线距离超过 32");
            helper.assertTrue(trigger.heard(MRUGolemTrigger.MSG_AWAKENED_ABOVE) == 1, "动作栏说在头顶的地面上，实际 " + trigger.bar);
            BossRoster roster = golem.bossRoster();
            helper.assertTrue(roster.add(archer) && roster.add(runner), "两人加进名单");
            roster.check(level);
            helper.assertTrue(roster.contains(trigger.getUUID()) && roster.contains(runner.getUUID()), "水平在 32 格内的留在名单里");
            helper.assertFalse(roster.contains(archer.getUUID()), "水平 40 格外的移出");
            helper.assertTrue(trigger.heard("message.essentialcraft5.boss.left_fight") == 0, "触发的人不收「退出了战斗」");
            helper.assertTrue(archer.heard("message.essentialcraft5.boss.left_fight") == 0, "一直在范围外的静默移出");
            runner.moveTo(runner.getX() + 40D, runner.getY(), runner.getZ());
            roster.check(level);
            helper.assertFalse(roster.contains(runner.getUUID()), "跑到水平 40 格外的移出");
            helper.assertTrue(runner.heard("message.essentialcraft5.boss.left_fight") == 1, "在范围里待过的人收到「退出了战斗」");
            helper.assertTrue(roster.contains(trigger.getUUID()), "触发的人还在");
        } finally {
            for (ServerPlayer player : List.of(trigger, archer, runner)) {
                ECProtection.clearOnlineOverride(player.getUUID());
                PresenceClaimMemory.forget(player.getUUID());
                ECProtection.forget(player.getUUID());
            }
            MRUGolemTrigger.forgetLanding(presence.getUUID());
            discardAll(golems);
            discard(presence);
            for (BlockPos pos : deck) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        helper.succeed();
    }

    /**
     * 和平难度散去时原位改成聚集体现在那一格（13c 审查 C3-05）：魔像出来以后那团被拽离遗迹原位 20 格（超过放弃距离 16），切和平 → 还是「等着」、
     * 原位是它现在那一格，漂移检查不记成放弃；难度回来以后有人走近照样再出来
     */
    @GameTest(template = LARGE)
    public static void golemPeacefulRearmsAfterDrag(GameTestHelper helper) {
        layer(helper, 0);
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, false);
        BlockPos ruinOrigin = presence.blockPosition().offset(-20, 0, 0);
        presence.setGuardian(MRUGolemGuardian.pending(ruinOrigin).with(State.SPAWNED));
        MRUGolemEntity golem = golem(helper, new Vec3(2.5D, 1D, 5.5D), presence);
        List<MRUGolemEntity> golems = new ArrayList<>();
        try {
            golem.endBoss(ECBoss.BossEnd.PEACEFUL);
            helper.assertTrue(golem.isRemoved() && presence.getGuardian().pending()
                    && presence.blockPosition().equals(presence.getGuardian().origin()), "改回等着、原位是现在那一格，实际 " + presence.getGuardian());
            helper.assertFalse(MRUGolemTrigger.checkDrift(presence), "漂移检查不记成放弃");
            ServerPlayer player = player(helper, "ec5-golem-drag", new Vec3(3.5D, 1D, 5.5D));
            golems.addAll(MRUGolemTrigger.check(player));
            helper.assertTrue(golems.size() == 1 && presence.getGuardian().state() == State.SPAWNED, "难度回来以后再出来，实际 " + golems.size());
            PresenceClaimMemory.forget(player.getUUID());
        } finally {
            discardAll(golems);
            discard(golem, presence);
        }
        helper.succeed();
    }

    /** 电弧鞭最近距离填得比最远还大时按最远距离算（13c 审查 C3-08 ③，不然电弧鞭永远用不出来）；正常填法不变 */
    @GameTest(template = LARGE)
    public static void golemWhipRangeClamped(GameTestHelper helper) {
        ECMRUGolem.Config.Attacks a = D.attacks();
        ECMRUGolem.Config.Attacks bad = new ECMRUGolem.Config.Attacks(a.hammerDamage(), a.slamDamage(), a.slamHealthFraction(), a.slamRadius(),
                a.slamWarningTicks(), a.slamCooldownTicks(), a.whipDamage(), 12D, 10D, a.whipCooldownTicks());
        helper.assertTrue(bad.whipMinRange() == 10D && bad.whipMaxRange() == 10D, "最近 12、最远 10 → 最近按 10 算，实际 " + bad.whipMinRange());
        helper.assertTrue(a.whipMinRange() == 4D && a.whipMaxRange() == 10D, "默认 4~10 不变");
        helper.succeed();
    }

    /** 和平难度散去：守着的那团改回「等着触发」、魔像消失（不掉东西）；难度调回来、有人走近就再出来 */
    @GameTest(template = LARGE)
    public static void golemPeacefulRearms(GameTestHelper helper) {
        layer(helper, 0);
        MRUPresenceEntity presence = presence(helper, CLUSTER, 6000, true);
        presence.setGuardian(presence.getGuardian().with(State.SPAWNED));
        MRUGolemEntity golem = golem(helper, new Vec3(2.5D, 1D, 5.5D), presence);
        List<MRUGolemEntity> golems = new ArrayList<>();
        try {
            golem.endBoss(ECBoss.BossEnd.PEACEFUL);
            helper.assertTrue(golem.isRemoved() && presence.getGuardian().pending(), "和平散去：魔像没了、聚集体改回等着，实际 " + presence.getGuardian());
            ServerPlayer player = player(helper, "ec5-golem-peace", new Vec3(3.5D, 1D, 5.5D));
            golems.addAll(MRUGolemTrigger.check(player));
            helper.assertTrue(golems.size() == 1, "难度回来以后再出来");
        } finally {
            discardAll(golems);
            discard(golem, presence);
        }
        helper.succeed();
    }

    // ================================================================== 掉落

    /**
     * 掉落：结构来的、玩家亲手打（占比够）→ 上古晶核 1、魔力强化板 2~4、MRU 晶块 2；占比不够、刷怪蛋来的、没有玩家最后一下都不掉
     */
    @GameTest(template = LARGE)
    public static void golemLootNeedsFairKill(GameTestHelper helper) {
        layer(helper, 0);
        ServerLevel level = helper.getLevel();
        MRUGolemEntity golem = golem(helper, new Vec3(5.5D, 1D, 5.5D), null);
        ServerPlayer player = player(helper, "ec5-golem-loot", new Vec3(3.5D, 1D, 5.5D));
        LootTable table = level.getServer().getLootData().getLootTable(EssentialCraft5.id("entities/mru_golem"));
        try {
            helper.assertTrue(table != LootTable.EMPTY, "战利品表 entities/mru_golem 应存在");
            helper.assertTrue(roll(level, table, golem, player).isEmpty(), "一点没打（占比 0）不掉");
            hit(golem, player.damageSources().playerAttack(player), 10F);
            List<ItemStack> drops = roll(level, table, golem, player);
            int core = count(drops, "ancient_core");
            int plating = count(drops, "magic_fortified_plating");
            int chunk = count(drops, "mru_chunk");
            helper.assertTrue(core == 1 && plating >= 2 && plating <= 4 && chunk == 2, "应掉晶核 1、强化板 2~4、晶块 2，实际 " + drops);
            helper.assertTrue(roll(level, table, golem, null).isEmpty(), "没有玩家最后一下不掉");
            SpawnOrigin.set(golem, SpawnOrigin.Origin.SPAWN_EGG);
            helper.assertTrue(roll(level, table, golem, player).isEmpty(), "刷怪蛋来的不掉");
        } finally {
            discard(golem);
        }
        helper.succeed();
    }

    /**
     * 打倒（多人）：三人近战打，过护甲后 A 打掉约 104、B 12.6（不少于最大生命的一成）、C 3.4（不到一成）。A、B 各一份进背包
     * （上古晶核 1、魔力强化板 2~4、MRU 晶块 2），C 没有，地上不掉；A、B 记首杀、异兽录那一步打勾（C 不打勾）；守着的那团仍是「已触发」，三人再走近都不出第二只
     */
    @GameTest(template = LARGE)
    public static void golemDefeatSharesLoot(GameTestHelper helper) {
        layer(helper, 0);
        // 300 MRU 不到护盾门槛，这一场没有护盾
        MRUPresenceEntity presence = presence(helper, new Vec3(9.5D, 1.5D, 9.5D), 300, true);
        presence.setGuardian(presence.getGuardian().with(State.SPAWNED));
        MRUGolemEntity golem = golem(helper, new Vec3(5.5D, 1D, 5.5D), presence);
        ServerPlayer a = online(victim(helper, "ec5-golem-ka", new Vec3(3.5D, 1D, 5.5D)));
        ServerPlayer b = online(victim(helper, "ec5-golem-kb", new Vec3(7.5D, 1D, 5.5D)));
        ServerPlayer c = online(victim(helper, "ec5-golem-kc", new Vec3(5.5D, 1D, 7.5D)));
        String typeId = "essentialcraft5:mru_golem";
        List<MRUGolemEntity> golems = new ArrayList<>();
        try {
            // 护甲 10、韧性 2：近战 5 → 3.4，15 → 12.6，25 → 23
            hit(golem, c.damageSources().playerAttack(c), 5F);
            hit(golem, b.damageSources().playerAttack(b), 15F);
            for (int i = 0; i < 6 && golem.isAlive(); i++) {
                hit(golem, a.damageSources().playerAttack(a), 25F);
            }
            helper.assertTrue(golem.isDeadOrDying() && golem.defeated(), "应被打倒");
            helper.assertTrue(Math.abs(golem.bossRoster().dealt(b.getUUID()) - 12.6F) < 0.01F
                    && Math.abs(golem.bossRoster().dealt(c.getUUID()) - 3.4F) < 0.01F,
                    "B 记 12.6、C 记 3.4，实际 " + golem.bossRoster().dealt(b.getUUID()) + " / " + golem.bossRoster().dealt(c.getUUID()));
            for (ServerPlayer winner : List.of(a, b)) {
                int core = winner.getInventory().countItem(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("ancient_core")));
                int plating = winner.getInventory().countItem(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("magic_fortified_plating")));
                int chunk = winner.getInventory().countItem(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("mru_chunk")));
                helper.assertTrue(core == 1 && plating >= 2 && plating <= 4 && chunk == 2,
                        winner.getGameProfile().getName() + " 应各得一份，实际晶核 " + core + "、强化板 " + plating + "、晶块 " + chunk);
                helper.assertTrue(PlayerDataEvents.get(winner).getBossFirstKills().contains(typeId), winner.getGameProfile().getName() + " 记首杀");
            }
            helper.assertTrue(c.getInventory().isEmpty() && !PlayerDataEvents.get(c).getBossFirstKills().contains(typeId), "不到一成的什么都没有");
            net.minecraft.advancements.Advancement bestiary = helper.getLevel().getServer().getAdvancements()
                    .getAdvancement(BossLoot.bestiaryAdvancement(golem.getType()));
            helper.assertTrue(bestiary != null, "异兽录进度 guide/bestiary/mru_golem 应存在");
            helper.assertTrue(a.getAdvancements().getOrStartProgress(bestiary).isDone() && b.getAdvancements().getOrStartProgress(bestiary).isDone(),
                    "异兽录那一步给分到掉落的人");
            helper.assertFalse(c.getAdvancements().getOrStartProgress(bestiary).isDone(), "不到一成的人异兽录那一步不打勾");
            Vec3 at = helper.absoluteVec(new Vec3(5.5D, 1D, 5.5D));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4D), Entity::isAlive).isEmpty(),
                    "地上不掉东西");
            helper.assertTrue(presence.getGuardian().state() == State.SPAWNED, "守着的那团仍是已触发");
            for (ServerPlayer player : List.of(a, b, c)) {
                golems.addAll(MRUGolemTrigger.check(player));
            }
            helper.assertTrue(golems.isEmpty(), "打倒以后不再出第二只");
        } finally {
            for (ServerPlayer player : List.of(a, b, c)) {
                ECProtection.clearOnlineOverride(player.getUUID());
            }
            discardAll(golems);
            discard(golem, presence);
            Vec3 at = helper.absoluteVec(new Vec3(5.5D, 1D, 5.5D));
            helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(6D), Entity::isAlive).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** 让 BossLoot 认这名模拟玩家在线（分掉落只给在线的人） */
    private static ServerPlayer online(ServerPlayer player) {
        ECProtection.setOnlineOverride(player);
        return player;
    }

    private static List<ItemStack> roll(ServerLevel level, LootTable table, MRUGolemEntity golem, @Nullable ServerPlayer player) {
        DamageSource source = player != null ? player.damageSources().playerAttack(player) : level.damageSources().generic();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, golem)
                .withParameter(LootContextParams.ORIGIN, golem.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, player)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, player)
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .create(LootContextParamSets.ENTITY);
        return table.getRandomItems(params);
    }

    private static int count(List<ItemStack> stacks, String path) {
        return stacks.stream().filter(stack -> EssentialCraft5.id(path).equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())))
                .mapToInt(ItemStack::getCount).sum();
    }

    // ================================================================== 招式

    /**
     * 聚能冲击：半径 3.5 内名单里的参战者挨 6 + 最大生命 10%（aberrant_strike，攻击者是魔像），不在名单的、圈外的不挨，上冷却；
     * 电弧鞭：6 格外看得见的参战者挨 6 点 mru_discharge（攻击者是魔像），12 格外、不在名单的不挨，上冷却
     */
    @GameTest(template = LARGE)
    public static void golemSlamAndWhip(GameTestHelper helper) {
        layer(helper, 0);
        MRUGolemEntity golem = golem(helper, new Vec3(2.5D, 1D, 5.5D), null);
        ServerPlayer fighter = online(victim(helper, "ec5-golem-f", new Vec3(4.5D, 1D, 5.5D)));
        ServerPlayer bystander = online(victim(helper, "ec5-golem-by", new Vec3(2.5D, 1D, 7.5D)));
        ServerPlayer outside = online(victim(helper, "ec5-golem-out", new Vec3(2.5D, 1D, 9.5D)));
        golem.bossRoster().add(fighter);
        golem.bossRoster().add(outside);
        DamageLog fighterLog = log(fighter);
        try {
            int hits = golem.slamImpact();
            float slam = scaled(helper, 6F + 20F * 0.10F);
            helper.assertTrue(hits == 1 && Math.abs(fighter.getHealth() - (20F - slam)) < EPS, "冲击只打圈里的参战者 " + slam + "，实际打中 " + hits
                    + "、剩 " + fighter.getHealth());
            helper.assertTrue(fighterLog.sources.get(0).is(ECDamageTypes.ABERRANT_STRIKE) && fighterLog.sources.get(0).getEntity() == golem,
                    "冲击是 aberrant_strike、攻击者是魔像");
            helper.assertTrue(bystander.getHealth() == 20F && outside.getHealth() == 20F, "不在名单的、圈外的不挨");
            helper.assertTrue(golem.slamCooldown() == D.attacks().slamCooldownTicks(), "冲击上冷却");

            fighter.setHealth(20F);
            fighter.invulnerableTime = 0;
            Vec3 six = helper.absoluteVec(new Vec3(8.5D, 1D, 5.5D));
            fighter.moveTo(six.x, six.y, six.z, 0F, 0F);
            helper.assertTrue(golem.whipStrike(fighter), "6 格外看得见：电弧鞭打中");
            helper.assertTrue(Math.abs(fighter.getHealth() - 14F) < EPS, "电弧鞭 6 点，实际剩 " + fighter.getHealth());
            DamageSource whip = fighterLog.sources.get(fighterLog.sources.size() - 1);
            helper.assertTrue(whip.is(ECDamageTypes.MRU_DISCHARGE) && whip.getEntity() == golem, "电弧鞭是带攻击者（魔像）的 mru_discharge");
            helper.assertTrue(golem.whipCooldown() == D.attacks().whipCooldownTicks(), "电弧鞭上冷却");
            Vec3 twelve = helper.absoluteVec(new Vec3(14.5D, 1D, 5.5D));
            fighter.moveTo(twelve.x, twelve.y, twelve.z, 0F, 0F);
            fighter.invulnerableTime = 0;
            helper.assertFalse(golem.whipStrike(fighter), "12 格外够不着");
            helper.assertFalse(golem.whipStrike(bystander), "不在名单的不打");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(fighterLog);
            for (ServerPlayer player : List.of(fighter, bystander, outside)) {
                ECProtection.clearOnlineOverride(player.getUUID());
            }
            discard(golem);
        }
        helper.succeed();
    }

    /**
     * 13b 审查 B1-01：2 阶魔力防护挡得住魔像的电弧鞭（带攻击者的 mru_discharge，份额 45%：6 → 3.3），0 阶、1 阶挡不住（6 → 6）
     */
    @GameTest(template = LARGE)
    public static void golemWhipVersusMagicHardenedStages(GameTestHelper helper) {
        layer(helper, 0);
        MRUGolemEntity golem = golem(helper, new Vec3(2.5D, 1D, 5.5D), null);
        List<DamageLog> logs = new ArrayList<>();
        try {
            for (int stage = 0; stage <= 2; stage++) {
                ServerPlayer target = victim(helper, "ec5-golem-mhs" + stage, new Vec3(8.5D, 1D, 3.5D + stage * 2D));
                for (ArmorItem.Type type : ArmorItem.Type.values()) {
                    target.setItemSlot(type.getSlot(), mhs(type, stage));
                }
                golem.bossRoster().add(target);
                DamageLog log = log(target);
                logs.add(log);
                helper.assertTrue(golem.whipStrike(target), stage + " 阶：电弧鞭应打中");
                float expected = stage >= MHSArmorItem.DISCHARGE_STAGE ? 6F * 0.55F : 6F;
                helper.assertTrue(Math.abs(log.last() - expected) < EPS, stage + " 阶应剩 " + expected + "，实际 " + log.last());
            }
        } finally {
            logs.forEach(MinecraftForge.EVENT_BUS::unregister);
            discard(golem);
        }
        helper.succeed();
    }

    private static ItemStack mhs(ArmorItem.Type type, int stage) {
        Item item = switch (type) {
            case HELMET -> ECGear.MHS_HELMET.get();
            case CHESTPLATE -> ECGear.MHS_CHESTPLATE.get();
            case LEGGINGS -> ECGear.MHS_LEGGINGS.get();
            case BOOTS -> ECGear.MHS_BOOTS.get();
        };
        ItemStack stack = new ItemStack(item);
        if (stage > 0) {
            Attunement.set(stack, stage, 0);
        }
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(Integer.MAX_VALUE));
        return stack;
    }

    // ================================================================== 地脉探测仪「聚集体」档

    /**
     * 「聚集体」档：指 64 格内最近、MRU ≥ 5 000 的那团（2 格外 3 000 的不算），动作栏写方位（东南）、距离、上下差；
     * 右键照扫矿的价扣 1 500、上冷却、不发扫描包；门槛配得比所有聚集体都高时说「没有」
     */
    @GameTest(template = LARGE)
    public static void golemLeyDetectorPointsAtPresence(GameTestHelper helper) {
        layer(helper, 0);
        ServerLevel level = helper.getLevel();
        MRUPresenceEntity decoy = presence(helper, new Vec3(7.5D, 1.5D, 5.5D), 3000, false);
        MRUPresenceEntity target = presence(helper, new Vec3(8.5D, 2.5D, 8.5D), 8000, false);
        ServerPlayer player = player(helper, "ec5-golem-ley", new Vec3(5.5D, 1D, 5.5D));
        try {
            helper.assertTrue(LeyDetectorItem.nearestPresence(player) == target, "应指 8000 那团，不是 2 格外 3000 那团");
            Component line = LeyDetectorItem.describe(player.position(), target, D.detector());
            helper.assertTrue(LeyDetectorItem.MSG_PRESENCE_FOUND.equals(key(line)), "应是找到了的那句，实际 " + key(line));
            Object[] args = ((TranslatableContents) line.getContents()).getArgs();
            helper.assertTrue(InquisitoriumCoordinatesItem.headingKey(3).equals(key((Component) args[0])) && Integer.valueOf(4).equals(args[1]),
                    "东南方约 4 格，实际 " + key((Component) args[0]) + " / " + args[1]);
            helper.assertTrue(LeyDetectorItem.MSG_PRESENCE_LEVEL.equals(key((Component) args[2])), "高出 1 层算差不多高");
            Component below = LeyDetectorItem.describe(player.position().add(0D, -6D, 0D), target, D.detector());
            Object[] belowArgs = ((TranslatableContents) below.getContents()).getArgs();
            Component vertical = (Component) belowArgs[2];
            helper.assertTrue(LeyDetectorItem.MSG_PRESENCE_ABOVE.equals(key(vertical))
                            && Integer.valueOf(7).equals(((TranslatableContents) vertical.getContents()).getArgs()[0]),
                    "从下面 6 格看：往上 7 层，实际 " + key(vertical));

            ItemStack detector = new ItemStack(ECLeyDetector.LEY_DETECTOR.get());
            detector.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(5000));
            LeyDetectorItem.setPresenceMode(detector, true);
            player.setItemInHand(InteractionHand.MAIN_HAND, detector);
            int sent = LeyDetectorItem.testScansSent();
            helper.assertTrue(detector.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "右键应成");
            int left = detector.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
            helper.assertTrue(left == 5000 - ECLeyDetector.Config.DEFAULTS.mruPerScan(), "扣 1500，实际剩 " + left);
            helper.assertTrue(player.getCooldowns().isOnCooldown(detector.getItem()) && LeyDetectorItem.testScansSent() == sent,
                    "上冷却、不发扫描包");

            withConfig(new ECMRUGolem.Config.Values(D.trigger(), D.stats(), D.shield(), D.attacks(), new ECMRUGolem.Config.Detector(64, 20001)), () -> {
                helper.assertTrue(LeyDetectorItem.nearestPresence(player) == null, "门槛比谁都高时找不到");
                helper.assertTrue(LeyDetectorItem.MSG_PRESENCE_NONE.equals(key(LeyDetectorItem.describe(player.position(), null,
                        ECMRUGolem.Config.values().detector()))), "说「没有」");
            });
        } finally {
            discard(decoy, target);
        }
        helper.succeed();
    }

    // ================================================================== 遗迹生成

    /**
     * 遗迹生成时的守护：主世界的一律等着、原位是给的那一格；海纳尔的按种子和原位算，同样的输入同样的结果，
     * 4 000 座里有守护的比例在 20% ± 3% 内；几率 0 一座没有、1 全有
     */
    @GameTest(template = LARGE)
    public static void golemRuinGuardianChance(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(CLUSTER_CELL);
        MRUGolemGuardian overworld = MRUGolemTrigger.ruinGuardian(helper.getLevel(), origin);
        helper.assertTrue(overworld.pending() && origin.equals(overworld.origin()), "主世界的遗迹一律等着，实际 " + overworld);
        long seed = 20261029L;
        int guarded = 0;
        int total = 4000;
        for (int i = 0; i < total; i++) {
            // 遗迹大致每 30 区块一座：坐标按大的奇数步长摊开
            BlockPos pos = new BlockPos((i % 64) * 487 - 15000, 40 + (i % 13), (i / 64) * 503 - 15000);
            boolean first = MRUGolemTrigger.hoannaGuarded(seed, pos, 0.2D);
            helper.assertTrue(first == MRUGolemTrigger.hoannaGuarded(seed, pos, 0.2D), "同样的输入同样的结果");
            if (first) {
                guarded++;
            }
            helper.assertFalse(MRUGolemTrigger.hoannaGuarded(seed, pos, 0D), "几率 0 一座没有");
            helper.assertTrue(MRUGolemTrigger.hoannaGuarded(seed, pos, 1D), "几率 1 全有");
        }
        double ratio = guarded / (double) total;
        helper.assertTrue(ratio > 0.17D && ratio < 0.23D, "20% 左右，实际 " + ratio);
        helper.succeed();
    }
}
