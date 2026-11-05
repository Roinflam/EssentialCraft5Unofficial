package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.item.CalamityBeaconItem;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECShade;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 用天灾信标在海纳尔召天灾之心（batch13.md §5.3.5、§2.5 天灾信标门）。只在服务端调。
 * <p>
 * 顺序：真玩家 → 生存 / 冒险 → 非和平 → 在海纳尔 → 八阶门（{@link MatrixRank#mayUseCalamityBeacon}；矩阵阶位关掉时不挡）→
 * 天灾（{@code require_calamity} 开着时海纳尔此刻要有天灾，地脉风暴也算、主灾按种子从四种里取一种；关掉时随机主灾）→
 * 召唤点头顶露天 → 平整度（召唤点为中心、以 {@code pillar_offset} 为半径的方阵里抽 5 × 5 = 25 列高度，最高最低差 ≤ {@code max_unevenness}）→
 * 五处权限（召唤点 + 四根灾柱的落脚点，{@link ECProtection#check} 都要 ALLOW；给不出结论的当不行、提示稍后再试）→
 * {@code exclusive_radius}（128 格）内没有别的天灾之心 / 矩阵之影 → 这一游戏日还没打倒过（{@link CalamityHeartContracts#defeatedToday}）→
 * 名下没有还开着的契约（同一时间只召一只）→ 暗影核心够（满的信标 0 个、{@code spent} 的 {@code cores_per_resummon} 个；关掉 {@code require_calamity} 时都再加 {@code cores_extra_without_calamity}）→
 * 召唤点上方站得下 → 生成。不满足时什么都不扣，动作栏提示原因（{@link Result#messageKey}）。
 * <p>
 * 生成成功（本体和四根灾柱的加入事件都没被取消）以后才扣：信标从手里收走（原样记进契约）、背包扣暗影核心，召唤者进名单，记一份契约（{@link CalamityHeartContracts}）。
 * 生成来源记成首领（{@link SpawnOrigin.Origin#BOSS}）。
 */
public final class CalamityHeartSummon {

    /** 平整度抽样：每条边 5 列，取 −o、−o/2、0、o/2、o */
    public static final int GRID = 5;

    @Nullable
    private static HoannaEvent testCalamity;

    public enum Result {
        OK,
        NOT_REAL_PLAYER,
        GAME_MODE,
        PEACEFUL,
        NOT_HOANNA,
        RANK,
        NO_CALAMITY,
        NO_SKY,
        UNEVEN,
        DENIED,
        PENDING,
        OCCUPIED,
        DAILY,
        ALREADY_SUMMONED,
        NO_CORES,
        BLOCKED,
        SPAWN_FAILED;

        /** 提示的键：message.essentialcraft5.calamity_beacon.&lt;小写名&gt;（NOT_REAL_PLAYER 不提示，RANK 用矩阵阶位门那句） */
        public String messageKey() {
            return "message.essentialcraft5.calamity_beacon." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 召唤的结果。
     *
     * @param value 随结果不同：UNEVEN 是高度差，NO_CORES 是要几个暗影核心，OK 是扣了几个
     * @param have  NO_CORES 时背包里有几个
     */
    public record Outcome(Result result, @Nullable CalamityHeartEntity heart, @Nullable CalamityHeartEntity.Aspect main, int value, int have) {

        public boolean ok() {
            return result == Result.OK;
        }

        /** 给召唤者看的提示 */
        public Component message(ServerPlayer player) {
            ECCalamityHeart.Config.Summon summon = ECCalamityHeart.Config.values().summon();
            return switch (result) {
                case RANK -> MatrixRank.gateMessage(player, MatrixRank.Gate.CALAMITY_BEACON);
                case UNEVEN -> Component.translatable(result.messageKey(), value, summon.maxUnevenness());
                case OCCUPIED -> Component.translatable(result.messageKey(), summon.exclusiveRadius());
                case NO_CORES -> Component.translatable(result.messageKey(), value, have);
                case OK -> Component.translatable(result.messageKey(),
                        Component.translatable("hoanna_event.essentialcraft5." + (main == null ? "sun_array" : main.event().key())), value);
                default -> Component.translatable(result.messageKey());
            };
        }
    }

    private CalamityHeartSummon() {
    }

    /** 只给 GameTest / 展示厅：把「海纳尔此刻的天灾」当成 event（null = 照实际） */
    public static void setTestCalamity(@Nullable HoannaEvent event) {
        testCalamity = event;
    }

    public static void clearTestCalamity() {
        testCalamity = null;
    }

    /** 海纳尔此刻的天灾（测试覆盖优先） */
    public static HoannaEvent calamity(ServerLevel level) {
        HoannaEvent forced = testCalamity;
        return forced != null ? forced : HoannaEvents.current(level);
    }

    /**
     * 这一次的主灾：四种天灾就是它自己；地脉风暴按世界种子和游戏日取一种（同一场风暴里召几次都一样）；
     * {@code require_calamity} 关掉时没有天灾就随机。返回 null = 召不了（要天灾而此刻没有）。
     */
    @Nullable
    public static CalamityHeartEntity.Aspect mainFor(ServerLevel level, boolean requireCalamity) {
        HoannaEvent event = calamity(level);
        CalamityHeartEntity.Aspect aspect = CalamityHeartEntity.Aspect.of(event);
        if (aspect != null) {
            return aspect;
        }
        if (event == HoannaEvent.LEY_STORM) {
            long mixed = level.getSeed() * 0x9E3779B97F4A7C15L + BossDaily.dayIndex(level.getServer()) * 31L;
            return CalamityHeartEntity.Aspect.byId((int) Math.floorMod(mixed ^ (mixed >>> 29), 4L));
        }
        return requireCalamity ? null : CalamityHeartEntity.Aspect.byId(level.getRandom().nextInt(4));
    }

    /** 四根灾柱的落脚点（召唤点往四相各自的方向 pillar_offset 格、按高度图落地；没加载的那根是 null） */
    public static BlockPos[] pillarFeet(ServerLevel level, BlockPos floor) {
        int offset = ECCalamityHeart.Config.values().summon().pillarOffset();
        BlockPos[] out = new BlockPos[4];
        for (int i = 0; i < 4; i++) {
            CalamityHeartEntity.Aspect aspect = CalamityHeartEntity.Aspect.byId(i);
            BlockPos column = floor.offset(aspect.dx() * offset, 0, aspect.dz() * offset);
            if (ECLevels.isFullyLoaded(level, column)) {
                out[i] = new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()),
                        column.getZ());
            }
        }
        return out;
    }

    /** 平整度：25 列高度里最高减最低；有一列没加载返回 −1 */
    public static int unevenness(ServerLevel level, BlockPos floor) {
        int offset = ECCalamityHeart.Config.values().summon().pillarOffset();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int ix = 0; ix < GRID; ix++) {
            for (int iz = 0; iz < GRID; iz++) {
                int x = floor.getX() + step(ix, offset);
                int z = floor.getZ() + step(iz, offset);
                if (!ECLevels.isFullyLoaded(level, new BlockPos(x, floor.getY(), z))) {
                    return -1;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        return max - min;
    }

    private static int step(int i, int offset) {
        return switch (i) {
            case 0 -> -offset;
            case 1 -> -offset / 2;
            case 2 -> 0;
            case 3 -> offset / 2;
            default -> offset;
        };
    }

    /**
     * 召唤这一刻要不要正有天灾：{@code require_calamity} 开着、且天灾能自己发生（{@link HoannaHazardsConfig#calamitiesCanStartNaturally}）。
     * 服主把 {@code chance_multiplier} 设成 0 或者天灾全关时，天灾永远不来，按关掉 require_calamity 走（随机主灾、多扣核心），
     * 不让第二十三章卡死。召唤、扣核心、信标提示都按这个判，三处必须一致。
     */
    public static boolean requiresCalamity(ECCalamityHeart.Config.Summon s) {
        return s.requireCalamity() && HoannaHazardsConfig.calamitiesCanStartNaturally();
    }

    /** 这一次要扣几个暗影核心（满的信标 0，spent 的 cores_per_resummon；不要求天灾时再加 cores_extra_without_calamity，见 {@link #requiresCalamity}） */
    public static int coreCost(ItemStack beacon) {
        ECCalamityHeart.Config.Summon s = ECCalamityHeart.Config.values().summon();
        int cost = CalamityBeaconItem.isSpent(beacon) ? s.coresPerResummon() : 0;
        if (!requiresCalamity(s)) {
            cost += s.coresExtraWithoutCalamity();
        }
        return Math.max(0, cost);
    }

    /** 在 floor（点的那一格地面）召唤；成功时信标从 beacon 那叠里收走一个、背包扣暗影核心 */
    public static Outcome summon(ServerPlayer player, ServerLevel level, BlockPos floor, ItemStack beacon) {
        if (!ECProtection.isRealPlayer(player)) {
            return fail(Result.NOT_REAL_PLAYER);
        }
        if (player.isCreative() || player.isSpectator()) {
            return fail(Result.GAME_MODE);
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return fail(Result.PEACEFUL);
        }
        if (!ECHoanna.isHoanna(level)) {
            return fail(Result.NOT_HOANNA);
        }
        if (!MatrixRank.mayUseCalamityBeacon(player)) {
            return fail(Result.RANK);
        }
        ECCalamityHeart.Config.Summon config = ECCalamityHeart.Config.values().summon();
        CalamityHeartEntity.Aspect main = mainFor(level, requiresCalamity(config));
        if (main == null) {
            return fail(Result.NO_CALAMITY);
        }
        if (!level.canSeeSky(floor.above())) {
            return fail(Result.NO_SKY);
        }
        int uneven = unevenness(level, floor);
        if (uneven < 0) {
            return fail(Result.PENDING);
        }
        if (uneven > config.maxUnevenness()) {
            return new Outcome(Result.UNEVEN, null, main, uneven, 0);
        }
        BlockPos[] feet = pillarFeet(level, floor);
        BlockPos[] checks = {floor, feet[0], feet[1], feet[2], feet[3]};
        for (BlockPos at : checks) {
            if (at == null) {
                return fail(Result.PENDING);
            }
            ECProtection.Verdict verdict = ECProtection.check(player, level, at, null);
            if (verdict == ECProtection.Verdict.UNKNOWN) {
                return fail(Result.PENDING);
            }
            if (!verdict.allowed()) {
                return fail(Result.DENIED);
            }
        }
        if (MatrixShadeSummon.occupied(level, Vec3.atCenterOf(floor), config.exclusiveRadius())) {
            return fail(Result.OCCUPIED);
        }
        if (CalamityHeartContracts.defeatedToday(player, level.getServer())) {
            return fail(Result.DAILY);
        }
        // 每日一次只算打倒的那次（失败退还是为了能重试），所以同一时间名下只许开着一份契约：
        // 不然拿几只信标在 128 格外各召一只、叫队友分头打，同一天就能结算好几次
        if (!CalamityHeartContracts.get(level.getServer()).contractsOf(player.getUUID()).isEmpty()) {
            return fail(Result.ALREADY_SUMMONED);
        }
        int cost = coreCost(beacon);
        int have = player.getInventory().countItem(ECShade.SHADE_CORE.get());
        if (have < cost) {
            return new Outcome(Result.NO_CORES, null, main, cost, have);
        }
        CalamityHeartEntity heart = ECCalamityHeart.CALAMITY_HEART.get().create(level);
        if (heart == null) {
            return fail(Result.SPAWN_FAILED);
        }
        Vec3 spawn = new Vec3(floor.getX() + 0.5D, floor.getY() + 1D, floor.getZ() + 0.5D);
        AABB box = heart.getType().getDimensions().makeBoundingBox(spawn);
        if (!level.noCollision(box)) {
            return fail(Result.BLOCKED);
        }
        heart.moveTo(spawn.x, spawn.y, spawn.z, player.getYRot() + 180F, 0F);
        heart.prepareSummon(player, floor, main, feet);
        SpawnOrigin.set(heart, SpawnOrigin.Origin.BOSS);
        // 加入事件被取消时 addFreshEntity 返回 false；在加入事件里直接 discard 而不取消的，返回值仍是 true，一并算失败
        if (!level.addFreshEntity(heart) || heart.isRemoved()) {
            return new Outcome(Result.SPAWN_FAILED, null, main, cost, have);
        }
        // 四根灾柱也要立得起来（插件可能只放行本体、拦下灾柱）：缺一根就不召，整只收回，什么都不扣，免得本体永远在护盾里打不动
        heart.syncPillars(level);
        if (heart.pillarsSpawned() < 4) {
            heart.skills().discardAll();
            heart.discard();
            return new Outcome(Result.SPAWN_FAILED, null, main, cost, have);
        }
        ItemStack taken = beacon.split(1);
        if (cost > 0) {
            player.getInventory().clearOrCountMatchingItems(stack -> stack.is(ECShade.SHADE_CORE.get()), cost,
                    player.inventoryMenu.getCraftSlots());
        }
        heart.bossRoster().add(player);
        heart.setTarget(player);
        CalamityHeartContracts.open(level, heart, player, floor, taken, cost);
        level.playSound(null, spawn.x, spawn.y, spawn.z, ECCalamityHeart.SOUND_SUMMON.get(), SoundSource.HOSTILE, 3F, 0.8F);
        CriteriaTriggers.SUMMONED_ENTITY.trigger(player, heart);
        return new Outcome(Result.OK, heart, main, cost, have);
    }

    private static Outcome fail(Result result) {
        return new Outcome(result, null, null, 0, 0);
    }
}
