package com.flameforge.essentialcraft5.hoanna.nest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.advancement.ECAdvancements;
import com.flameforge.essentialcraft5.blockentity.NestCoreBlockEntity;
import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 巢穴的公共部分（第十三批 13d-HF，batch13.md §6.4「巢穴核心」）：世界生成摆核心、「活动巢穴」表、怪身上的标签、死亡与读档的事件、
 * 奖励、测试钩子。状态机在 {@link NestCoreBlockEntity}，类型数据在 {@link HoannaNestTypes}。
 * <ul>
 *   <li><b>摆核心</b>（给中景观地物、13e 的拼件）：{@link #placeCore}，世界生成的 {@code WorldGenRegion} 里也能用。</li>
 *   <li><b>活动巢穴表</b>：世界 → 正在 ACTIVE 的核心坐标（方块实体加载 / 进 ACTIVE 时登记，离开 ACTIVE / 卸载时删）；
 *       {@link #activeNear} 数某一点附近几个，ARMED 激活前看「每名玩家附近同时 ≤ 2 个」，不扫方块。只在内存里，关服清空。</li>
 *   <li><b>怪身上的标签</b>：持久数据 {@value #KEY} = {世界、核心坐标、拴绳半径}（{@link #tagMember}）。死的时候（{@code LivingDeathEvent} LOWEST，
 *       没被取消的）按它找到已加载的核心、报给 {@link NestCoreBlockEntity#onMemberDied}；从存档读进来时（{@code EntityJoinLevelEvent}，loadedFromDisk）
 *       重新拴上，核心已加载而这只不在它这一波里（被收回、核心被挖掉）就不让它进世界。</li>
 * </ul>
 * GameTest 造的真玩家不在世界的玩家表里：候选人用 {@link #setTestPlayers} 换；和平难度用 {@link #setTestPeaceful}、主世界时间用 {@link #setTestTime}、
 * 奖励表用 {@link #setTestLootTables} 覆盖。
 * 都在同一段同步代码里设、测、置回（{@link #clearTestOverrides}）。
 */
public final class HoannaNests {

    /** 怪的持久数据里记核心的键 */
    public static final String KEY = EssentialCraft5.MODID + ":nest";
    private static final String TAG_DIM = "dim";
    private static final String TAG_POS = "pos";
    private static final String TAG_LEASH = "leash";

    /** 世界 → 正在 ACTIVE 的核心（只在服务端主线程读写） */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE = new HashMap<>();

    @Nullable
    private static List<? extends Player> testPlayers;
    @Nullable
    private static Boolean testPeaceful;
    @Nullable
    private static Long testTime;
    @Nullable
    private static Map<ResourceLocation, LootTable> testLootTables;

    private HoannaNests() {
    }

    /**
     * 怪身上记的核心。
     *
     * @param dimension 核心所在世界
     * @param pos       核心坐标
     * @param leash     拴绳半径
     */
    public record Link(ResourceKey<Level> dimension, BlockPos pos, int leash) {
    }

    // ================================================================== 摆核心

    /**
     * 在 pos 放一个巢穴核心（ARMED）并写上类型（世界生成的 {@code WorldGenRegion}、普通世界都能用；只写这一格）。
     * 类型 id 指 {@code data/<ns>/hoanna_nests/<id>.json}，这里不查它在不在（数据包可能后加载）。放不下返回 false。
     */
    public static boolean placeCore(WorldGenLevel level, BlockPos pos, ResourceLocation type) {
        BlockState state = ECHoannaFramework.NEST_CORE.get().defaultBlockState();
        if (!level.setBlock(pos, state, Block.UPDATE_CLIENTS)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof NestCoreBlockEntity core) {
            core.setType(type);
            return true;
        }
        return false;
    }

    // ================================================================== 时间、玩家

    /** 到期刻用的时间：主世界的 gameTime（测试覆盖优先） */
    public static long now(ServerLevel level) {
        Long test = testTime;
        return test != null ? test : level.getServer().overworld().getGameTime();
    }

    /** 和平难度（测试覆盖优先） */
    public static boolean peaceful(ServerLevel level) {
        Boolean test = testPeaceful;
        return test != null ? test : level.getDifficulty() == Difficulty.PEACEFUL;
    }

    /** 候选玩家：测试覆盖或这个世界的玩家表 */
    public static List<? extends Player> candidates(ServerLevel level) {
        List<? extends Player> test = testPlayers;
        return test != null ? test : level.players();
    }

    /** 能放出、留住一波的人：同一世界、活着、生存 / 冒险的真玩家 */
    public static boolean isFighter(ServerLevel level, Player player) {
        return ECProtection.isRealPlayer(player) && player.level() == level && player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    /** center 周围 radius 格内的 {@link #isFighter} 玩家 */
    public static List<Player> fighters(ServerLevel level, Vec3 center, double radius) {
        List<Player> out = new ArrayList<>();
        double max = radius * radius;
        for (Player player : candidates(level)) {
            if (isFighter(level, player) && player.position().distanceToSqr(center) <= max) {
                out.add(player);
            }
        }
        return out;
    }

    /** 清巢时分奖励的人：radius 格内、同一世界、活着、不是旁观的真玩家，而且记录里打出过有效伤害 */
    public static List<ServerPlayer> rewardees(ServerLevel level, Vec3 center, double radius, Map<UUID, Float> credit) {
        List<ServerPlayer> out = new ArrayList<>();
        double max = radius * radius;
        for (Player player : candidates(level)) {
            if (player instanceof ServerPlayer serverPlayer && ECProtection.isRealPlayer(player) && player.level() == level && player.isAlive()
                    && !player.isSpectator() && player.position().distanceToSqr(center) <= max) {
                Float dealt = credit.get(player.getUUID());
                if (dealt != null && dealt > 0F) {
                    out.add(serverPlayer);
                }
            }
        }
        return out;
    }

    /** 给一个人掷一份奖励（参数见 {@link NestCoreBlockEntity} 类注释「奖励怎么掷」），发手册进度 */
    public static void giveReward(ServerLevel level, HoannaNestType type, LivingEntity lastKilled, ServerPlayer player) {
        Map<ResourceLocation, LootTable> test = testLootTables;
        LootTable table = test != null && test.containsKey(type.lootTable()) ? test.get(type.lootTable())
                : level.getServer().getLootData().getLootTable(type.lootTable());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, lastKilled)
                .withParameter(LootContextParams.ORIGIN, lastKilled.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                .withParameter(LootContextParams.KILLER_ENTITY, player)
                .withParameter(LootContextParams.DIRECT_KILLER_ENTITY, player)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.ENTITY);
        table.getRandomItems(params, stack -> ECItemEntities.giveOrDrop(player, stack));
        ECAdvancements.awardIfPresent(player, NestCoreBlockEntity.ADVANCEMENT);
    }

    // ================================================================== 活动巢穴表

    public static void setActive(ServerLevel level, BlockPos pos, boolean active) {
        if (active) {
            ACTIVE.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
        } else {
            Set<BlockPos> set = ACTIVE.get(level.dimension());
            if (set != null && set.remove(pos) && set.isEmpty()) {
                ACTIVE.remove(level.dimension());
            }
        }
    }

    /** center 周围 radius 格内正在 ACTIVE 的核心数（不算 except 那一个） */
    public static int activeNear(ServerLevel level, Vec3 center, double radius, @Nullable BlockPos except) {
        Set<BlockPos> set = ACTIVE.get(level.dimension());
        if (set == null) {
            return 0;
        }
        double max = radius * radius;
        int count = 0;
        for (BlockPos pos : set) {
            if (!pos.equals(except) && Vec3.atCenterOf(pos).distanceToSqr(center) <= max) {
                count++;
            }
        }
        return count;
    }

    /** 这个世界登记着几个 ACTIVE 的核心（测试用） */
    public static int activeCount(ServerLevel level) {
        Set<BlockPos> set = ACTIVE.get(level.dimension());
        return set == null ? 0 : set.size();
    }

    // ================================================================== 怪身上的标签

    public static void tagMember(Mob mob, ServerLevel level, BlockPos core, int leash) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIM, level.dimension().location().toString());
        tag.putLong(TAG_POS, core.asLong());
        tag.putInt(TAG_LEASH, leash);
        mob.getPersistentData().put(KEY, tag);
    }

    /** 这只是哪个巢穴放出来的（没有标签返回 null） */
    @Nullable
    public static Link linkOf(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = data.getCompound(KEY);
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString(TAG_DIM));
        if (dim == null || !tag.contains(TAG_POS, Tag.TAG_LONG)) {
            return null;
        }
        return new Link(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(tag.getLong(TAG_POS)), Math.max(1, tag.getInt(TAG_LEASH)));
    }

    /** 拴在核心周围（会寻路的怪才有这一项；拴绳不随实体存盘，读档时 {@link HoannaNestEvents} 重新拴） */
    public static void leash(Mob mob, BlockPos core, int radius) {
        if (mob instanceof PathfinderMob pathfinder) {
            pathfinder.restrictTo(core, radius);
        }
    }

    // ================================================================== 测试钩子

    /** GameTest 用：候选玩家（null = 世界的玩家表） */
    public static void setTestPlayers(@Nullable List<? extends Player> players) {
        testPlayers = players == null ? null : List.copyOf(players);
    }

    /** GameTest 用：当成和平 / 不是和平（null = 照世界难度） */
    public static void setTestPeaceful(@Nullable Boolean peaceful) {
        testPeaceful = peaceful;
    }

    /** GameTest 用：到期刻用的「现在」（null = 主世界 gameTime） */
    public static void setTestTime(@Nullable Long time) {
        testTime = time;
    }

    /** GameTest 用：奖励表 id → 代码里建的表（优先于数据包里的同名表；null = 照数据包） */
    public static void setTestLootTables(@Nullable Map<ResourceLocation, LootTable> tables) {
        testLootTables = tables == null ? null : Map.copyOf(tables);
    }

    public static void clearTestOverrides() {
        testPlayers = null;
        testPeaceful = null;
        testTime = null;
        testLootTables = null;
    }

    // ================================================================== 事件

    /** 巢穴怪的死亡、读档、关服（Forge 总线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class HoannaNestEvents {

        private HoannaNestEvents() {
        }

        /** 这一波的怪死了：报给核心（核心没加载就算了，那时也没人在旁边分奖励） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void hoannaNestMemberDeath(LivingDeathEvent event) {
            LivingEntity entity = event.getEntity();
            if (!(entity.level() instanceof ServerLevel level)) {
                return;
            }
            Link link = linkOf(entity);
            if (link == null || !link.dimension().equals(level.dimension())) {
                return;
            }
            if (ECLevels.loadedBlockEntity(level, link.pos()) instanceof NestCoreBlockEntity core) {
                core.onMemberDied(level, entity);
            }
        }

        /** 从存档读进来的巢穴怪：重新拴上；核心已加载、而它不在这一波里（被收回了、核心被挖了）就不让它进世界 */
        @SubscribeEvent
        public static void hoannaNestMemberLoad(EntityJoinLevelEvent event) {
            if (!event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob) || !(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            Link link = linkOf(mob);
            if (link == null) {
                return;
            }
            leash(mob, link.pos(), link.leash());
            if (!link.dimension().equals(level.dimension()) || !ECLevels.isFullyLoaded(level, link.pos())) {
                return;
            }
            if (!(ECLevels.loadedBlockEntity(level, link.pos()) instanceof NestCoreBlockEntity core) || !core.waveIds().contains(mob.getUUID())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void hoannaNestServerStopped(ServerStoppedEvent event) {
            ACTIVE.clear();
            clearTestOverrides();
        }
    }
}
