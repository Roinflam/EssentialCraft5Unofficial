package com.flameforge.essentialcraft5.util;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.living.ZombieEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

/**
 * 生物从哪来（第十三批 13a 预备，batch13.md §0.4 第 14 条）：所有 {@link Mob} 在 Forge 的 {@code FinalizeSpawn} 里记一次来源，
 * 存在实体的持久数据（{@code getPersistentData()}，随实体存盘）键 {@value #KEY}，原始的生成类型另存 {@value #KEY_TYPE}。
 * 同调度（攻击者不是刷怪笼 / 刷怪蛋来的才算）、第十三批 13c 的公平击杀与精英几率都读它。
 * <p>
 * 分类（{@link #classify}）：
 * <ul>
 *   <li>{@link Origin#SPAWNER}：刷怪笼（{@code SPAWNER}，或事件带着刷怪笼对象；{@code TRIGGERED} 也算——别的模组拿它做类似刷怪笼的东西）；</li>
 *   <li>{@link Origin#SPAWN_EGG}：刷怪蛋（{@code SPAWN_EGG}，以及发射器放刷怪蛋的 {@code DISPENSER}）；</li>
 *   <li>{@link Origin#NATURAL}：自然生成、区块生成时生成、巡逻队（{@code NATURAL}、{@code CHUNK_GENERATION}、{@code PATROL}）；</li>
 *   <li>{@link Origin#EVENT}：袭击、流浪商人这类事件（{@code EVENT}；13c 起从「自然」里拆出来，以前记成 natural 的照旧是自然，两者都公平）；
 *       本模组自己的事件刷怪（海纳尔天灾追加的怪之类）同样用 {@code EVENT} 生成；</li>
 *   <li>{@link Origin#STRUCTURE}：结构自带的（{@code STRUCTURE}）；</li>
 *   <li>{@link Origin#SUMMONED}：别的生物叫来的（{@code MOB_SUMMONED}、{@code REINFORCEMENT}、{@code JOCKEY}）；</li>
 *   <li>{@link Origin#OTHER}：其余（繁殖、转化、桶、指令……）。</li>
 * </ul>
 * 另有两种没有对应生成类型、只由本模组生成它的代码用 {@link #set} 标上的（第十三批 13c，batch13.md §5.4）：
 * {@link Origin#NEST}（巢穴放出的一波，13d）、{@link Origin#BOSS}（玩家用召唤物 / 祭坛召出的首领本体：矩阵之影、天灾之心、渊潮领主）。
 * 首领战里被首领叫出来的小怪不是 {@code BOSS}，照旧记 {@link Origin#SUMMONED}（不掉关键物品）。
 * 没有这个键的读成 {@link Origin#UNKNOWN}：更新前就在世界里的生物、没走 {@code finalizeSpawn} 的生成（别的模组直接 addFreshEntity）。
 * 同调这类「只排除人为来源」的读法把「未知」当成不是刷怪笼 / 刷怪蛋来的（只有明确记着是那两种才排除）；
 * 公平击杀（{@link #isFair}）反过来，只认明确记着的几种公平来源，未知一律不公平。
 * <p>
 * <b>公平来源</b>（{@link #isFair}，13c 的 {@code essentialcraft5:fair_kill} 和精英几率读它）：自然、事件、结构、巢穴、首领本体；
 * 刷怪笼、刷怪蛋（含发射器）、指令、繁殖、桶、召唤、未知都不公平。增援、转化这两种虽然抄了原来那只的来源，也不公平：
 * 增援看 {@value #KEY_TYPE} 是不是 {@code REINFORCEMENT}，转化出来的另记一个标记 {@value #KEY_CONVERTED}。
 * <p>
 * 本模组代码自己放出来的生物：要么 {@code ForgeEventFactory.onFinalizeSpawn(…)} 走一遍事件（直接调 {@code mob.finalizeSpawn} 不发事件，记不上来源），
 * 要么在放进世界之前先 {@link #set}（记过的不会被之后的 {@code finalizeSpawn} 改写）。
 * <p>
 * 已经记过的不再改（同一只生物被别的模组再调一次 {@code finalizeSpawn} 也不会把刷怪笼来的洗成自然的）。下面三种「生物生出来的生物」沿用原来那只的来源，
 * 不然刷怪笼里的怪换个身份就洗白了：
 * <ul>
 *   <li>转化（僵尸溺尸化、僵尸村民治好、骷髅变流浪者……走 Forge 的 {@code LivingConversionEvent}）：原样抄给新的那只；</li>
 *   <li>僵尸叫来的增援（{@code REINFORCEMENT}）：{@code Zombie.hurt} 里先发 {@code ZombieEvent.SummonAidEvent}、再同步生成增援并走 {@code finalizeSpawn}，
 *       同一刻、同一个世界里对得上；召唤者记着来源时抄它的（{@value #KEY_TYPE} 仍记 REINFORCEMENT），召唤者没记过的照旧归 {@link Origin#SUMMONED}；</li>
 *   <li>史莱姆、岩浆怪分裂出来的小个子：分裂不经 {@code finalizeSpawn}，Forge 47 也没有分裂事件。{@code Slime.remove} 先 addFreshEntity 子体、
 *       再移除自己，所以子体进世界（{@code EntityJoinLevelEvent}，不是从存档读出来的）时母体还在原地：在子体附近找同种、已经死了、
 *       个子更大、记着来源的那只，抄它的。只对还没有来源的史莱姆类做一次小范围查找。</li>
 * </ul>
 * 本模组自己不经 {@code finalizeSpawn} 放出来的生物用 {@link #set} 补记。
 * <p>
 * 开销：每只生物生成时一次字符串写入；读是一次 NBT 查找，O(1)。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class SpawnOrigin {

    /** 持久数据里的来源键 */
    public static final String KEY = EssentialCraft5.MODID + ":origin";
    /** 持久数据里的原始生成类型（{@link MobSpawnType} 的名字），排查用，判定只看 {@link #KEY} */
    public static final String KEY_TYPE = EssentialCraft5.MODID + ":origin_type";
    /** 持久数据里「这只是转化出来的」标记（13c）：来源照抄原来那只，但不再算公平 */
    public static final String KEY_CONVERTED = EssentialCraft5.MODID + ":origin_converted";

    /**
     * 来源。存档里存小写名（{@link #id}），<b>只能往后加、不能改名</b>：改了名的旧存档读出来是「未知」。
     */
    public enum Origin {
        SPAWNER,
        SPAWN_EGG,
        NATURAL,
        STRUCTURE,
        SUMMONED,
        OTHER,
        /** 没记过（不是存进去的值） */
        UNKNOWN,
        /** 事件（13c 起：原版 {@code EVENT} 生成类型，以及本模组事件刷的怪） */
        EVENT,
        /** 巢穴放出的（13d 起，巢穴代码用 {@link SpawnOrigin#set} 标上） */
        NEST,
        /** 玩家召出的首领本体（13c 起，召唤代码用 {@link SpawnOrigin#set} 标上） */
        BOSS;

        private final String id = name().toLowerCase(Locale.ROOT);

        /** 存档里的写法（小写） */
        public String id() {
            return id;
        }

        /** 刷怪笼或刷怪蛋来的（防刷怪塔的几处都只排除这两种） */
        public boolean isArtificial() {
            return this == SPAWNER || this == SPAWN_EGG;
        }

        /** 公平来源（见类注释）：自然、事件、结构、巢穴、首领本体。增援、转化另判，见 {@link SpawnOrigin#isFair} */
        public boolean isFair() {
            return this == NATURAL || this == EVENT || this == STRUCTURE || this == NEST || this == BOSS;
        }

        static Origin byId(String id) {
            for (Origin origin : values()) {
                if (origin != UNKNOWN && origin.id.equals(id)) {
                    return origin;
                }
            }
            return UNKNOWN;
        }
    }

    /** 刚发过 SummonAidEvent 的僵尸（只在服务端线程读写；增援在同一次 hurt 里同步生成） */
    @Nullable
    private static WeakReference<Mob> aidSummoner;
    @Nullable
    private static ResourceKey<Level> aidLevel;
    private static long aidTick = Long.MIN_VALUE;

    private SpawnOrigin() {
    }

    /** 这只实体的来源；没记过（或不是生物）返回 {@link Origin#UNKNOWN} */
    public static Origin of(@Nullable Entity entity) {
        if (entity == null) {
            return Origin.UNKNOWN;
        }
        CompoundTag data = entity.getPersistentData();
        return data.contains(KEY, Tag.TAG_STRING) ? Origin.byId(data.getString(KEY)) : Origin.UNKNOWN;
    }

    /** 是刷怪笼或刷怪蛋来的（{@link Origin#isArtificial}）；没记过的不算 */
    public static boolean isArtificial(@Nullable Entity entity) {
        return of(entity).isArtificial();
    }

    /**
     * 来源公平（13c 公平击杀、精英几率）：记着的来源是 {@link Origin#isFair} 的几种，而且不是增援（原始类型 {@code REINFORCEMENT}）、
     * 不是转化出来的（{@value #KEY_CONVERTED}）。没记过的不公平。
     */
    public static boolean isFair(@Nullable Entity entity) {
        if (entity == null || !of(entity).isFair()) {
            return false;
        }
        CompoundTag data = entity.getPersistentData();
        return !data.getBoolean(KEY_CONVERTED) && !MobSpawnType.REINFORCEMENT.name().equals(data.getString(KEY_TYPE));
    }

    /** 记下（或改写）来源；{@code origin} 是 {@link Origin#UNKNOWN} 时去掉记录 */
    public static void set(Entity entity, Origin origin) {
        set(entity, origin, null);
    }

    private static void set(Entity entity, Origin origin, @Nullable String rawType) {
        CompoundTag data = entity.getPersistentData();
        data.remove(KEY_CONVERTED);
        if (origin == Origin.UNKNOWN) {
            data.remove(KEY);
            data.remove(KEY_TYPE);
            return;
        }
        data.putString(KEY, origin.id());
        if (rawType != null) {
            data.putString(KEY_TYPE, rawType);
        } else {
            data.remove(KEY_TYPE);
        }
    }

    /** 生成类型 → 来源（见类注释）；带着刷怪笼对象的一律算刷怪笼 */
    public static Origin classify(@Nullable MobSpawnType type, boolean fromSpawnerBlock) {
        if (fromSpawnerBlock) {
            return Origin.SPAWNER;
        }
        if (type == null) {
            return Origin.OTHER;
        }
        return switch (type) {
            case SPAWNER, TRIGGERED -> Origin.SPAWNER;
            case SPAWN_EGG, DISPENSER -> Origin.SPAWN_EGG;
            case NATURAL, CHUNK_GENERATION, PATROL -> Origin.NATURAL;
            case EVENT -> Origin.EVENT;
            case STRUCTURE -> Origin.STRUCTURE;
            case MOB_SUMMONED, REINFORCEMENT, JOCKEY -> Origin.SUMMONED;
            default -> Origin.OTHER;
        };
    }

    /**
     * 所有生物生成时记一次。LOWEST + 收被取消的：别的模组取消了 {@code finalizeSpawn}（生物照样生成，只是不跑初始化）也要记；
     * 取消了整次生成的，实体本来就不会进世界，记了也无妨。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        Mob mob = event.getEntity();
        if (mob.getPersistentData().contains(KEY, Tag.TAG_STRING)) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.REINFORCEMENT) {
            Mob summoner = takeAidSummoner(mob);
            if (summoner != null && summoner.getPersistentData().contains(KEY, Tag.TAG_STRING)) {
                set(mob, of(summoner), type.name());
                return;
            }
        }
        set(mob, classify(type, event.getSpawner() != null), type == null ? null : type.name());
    }

    /** 僵尸要叫增援了：记下是谁、在哪个世界、哪一刻（增援的 FinalizeSpawn 紧跟着在同一刻发） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSummonAid(ZombieEvent.SummonAidEvent event) {
        Mob summoner = event.getEntity();
        if (summoner.level().isClientSide) {
            return;
        }
        aidSummoner = new WeakReference<>(summoner);
        aidLevel = summoner.level().dimension();
        aidTick = tick(summoner);
    }

    /** 取走记着的召唤者：只认同一个世界、同一刻的；取一次就清掉 */
    @Nullable
    private static Mob takeAidSummoner(Mob reinforcement) {
        WeakReference<Mob> ref = aidSummoner;
        ResourceKey<Level> level = aidLevel;
        long tick = aidTick;
        aidSummoner = null;
        aidLevel = null;
        aidTick = Long.MIN_VALUE;
        Mob summoner = ref == null ? null : ref.get();
        if (summoner == null || summoner == reinforcement || level != reinforcement.level().dimension() || tick != tick(reinforcement)) {
            return null;
        }
        return summoner;
    }

    private static long tick(Entity entity) {
        MinecraftServer server = entity.getServer();
        return server != null ? server.getTickCount() : entity.level().getGameTime();
    }

    /** 分裂出来的小史莱姆、小岩浆怪沿用母体的来源（见类注释） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSlimeSplitJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Slime child) || event.loadedFromDisk() || event.getLevel().isClientSide()
                || child.getPersistentData().contains(KEY, Tag.TAG_STRING)) {
            return;
        }
        Slime parent = splitParent(child);
        if (parent != null) {
            copy(parent, child);
        }
    }

    /**
     * 子体附近（母体把子体撒在水平 ±个子 / 8 格以内、高 0.5 格处）同种、已经死了、个子比它大、记着来源的那只，取最近的；没有返回 null。
     * GameTest 直接调。
     */
    @Nullable
    public static Slime splitParent(Slime child) {
        double reach = Math.max(1.5D, (child.getSize() * 2 + 1) / 8D + 0.5D);
        AABB box = new AABB(child.getX() - reach, child.getY() - 1.5D, child.getZ() - reach,
                child.getX() + reach, child.getY() + 1D, child.getZ() + reach);
        List<Slime> found = child.level().getEntitiesOfClass(Slime.class, box, other -> other != child && other.getType() == child.getType()
                && other.isDeadOrDying() && other.getSize() > child.getSize() && other.getPersistentData().contains(KEY, Tag.TAG_STRING));
        Slime best = null;
        for (Slime other : found) {
            if (best == null || other.distanceToSqr(child) < best.distanceToSqr(child)) {
                best = other;
            }
        }
        return best;
    }

    /**
     * 转化出来的新生物沿用原来那只的来源（不然刷怪笼的僵尸泡水变溺尸就洗白了），另记一个「转化来的」标记：
     * 公平击杀不认转化来的（{@link #isFair}），同调这类只排除人为来源的读法不受影响。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onConversion(LivingConversionEvent.Post event) {
        Entity outcome = event.getOutcome();
        if (outcome != null) {
            copy(event.getEntity(), outcome);
            outcome.getPersistentData().putBoolean(KEY_CONVERTED, true);
        }
    }

    /** 把 from 记着的来源原样抄给 to（from 没记过就什么都不做） */
    private static void copy(Entity from, Entity to) {
        CompoundTag source = from.getPersistentData();
        if (!source.contains(KEY, Tag.TAG_STRING)) {
            return;
        }
        CompoundTag target = to.getPersistentData();
        target.putString(KEY, source.getString(KEY));
        if (source.contains(KEY_TYPE, Tag.TAG_STRING)) {
            target.putString(KEY_TYPE, source.getString(KEY_TYPE));
        } else {
            target.remove(KEY_TYPE);
        }
        if (source.getBoolean(KEY_CONVERTED)) {
            target.putBoolean(KEY_CONVERTED, true);
        }
    }

    @SubscribeEvent
    public static void onSpawnOriginServerStopped(ServerStoppedEvent event) {
        aidSummoner = null;
        aidLevel = null;
        aidTick = Long.MIN_VALUE;
    }
}
