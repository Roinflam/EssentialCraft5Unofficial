package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.HoannaWard;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.entity.PoisonFumeEntity;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.hoanna.event.LeyStorm;
import com.flameforge.essentialcraft5.network.S2CHoannaJoltPacket;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 海纳尔对玩家的每秒结算（第六批 6a M4，batch6a.md §5.4 / §4.3）：辐射（{@link Radiation}）、日阵灼烧、地震推人。
 * {@code PlayerTickEvent} END、服务端、排除 FakePlayer、{@code tickCount % 20 == 0}（按玩家 tickCount 天然错开），每人每次约几微秒。
 * 辐射压生命上限另外每 tick 判一次（{@link Radiation#pulse}，剂量不到 1 万时只读一下剂量就返回）。
 * <p>
 * <b>日阵</b>（原作 {@code WorldEventSunArray.playerTick}，在 {@code PlayerTickHandler} L528 的 {@code % 20} 之后，所以是每 20 tick 一次，
 * policy §4.4 写的「每 tick」是误读）：头顶 +2 格那一格看得见天空（{@code canSeeSky}，按天光判断：玻璃挡不住、树叶和不透光的屋顶挡得住）、
 * 不是创造 / 旁观、没戴 μ 指环 → {@code hurt(onFire, 1)}，<b>伤害生效了才</b>着火 10 秒（抗火、被保护模组拦下的不着火；原作不管伤害生效没有都点着）；
 * 露天时动作栏每 {@value #SUN_HINT_TICKS} tick 提示一次躲到屋顶下。<b>不落火</b>：原作每人每秒在 ±128 格、y 255 生成一个落地变火的方块（P1），删掉，
 * 天火的观感由客户端余烬粒子做（M3）。着火走实体的 {@code setSecondsOnFire}，燃烧的实体不点方块。
 * 这里不看时间：日阵天一黑就由 {@code HoannaEvents.tick} 结束（6a 审查 M4-1，世界 tick 在玩家 tick 之前跑完），所以轮不到夜里灼烧。
 * <p>
 * <b>地震</b>（D7 A；原作在客户端改真实视角、每 tick 乱推、约 5 秒一次 ±90° 猛甩 + ±3 格 / tick 抛飞，能摔死）：站在地上、不骑乘、不鞘翅、不飞行、
 * 不是创造 / 旁观时，每 20 tick 一下水平 ≤ {@value #SMALL_PUSH} 的小踉跄；以 {@value #STRONG_CHANCE} 的概率（平均约 6.7 秒）在没戴 μ 时换成一次强震：
 * 水平 {@value #STRONG_MIN}~{@value #STRONG_MAX}、竖直 {@value #STRONG_UP_MIN}~{@value #STRONG_UP_MAX}（跳不过一格高，摔不伤），
 * 并单发 {@code S2CHoannaJoltPacket} 给本人（客户端画一次镜头冲击，只动画面不动准星）。速度走 {@code push} + {@code hurtMarked}（服务端下发速度包，
 * 每 20 tick 至多一次，反作弊不会判移动过快）；配置 {@code earthquake_push} 关掉后不推人，强震照样发包（只剩画面）。
 * <p>
 * <b>地脉风暴</b>（第十三批 13d，{@link LeyStorm#exposure}）：头顶露天（同日阵的 {@link #exposedToSky}）每秒加 {@code ley_storm_radiation × max(易伤, 0.5)} 剂量，
 * 不造成伤害、不进 {@link HoannaWard}（它是剂量，走辐射抗性那条链）。落晶的生成在 {@code LeyStorm} 自己的玩家 tick 里。
 * <p>
 * 事件状态同步（登录 / 换维度 / 重生）在 {@code HoannaEvents}；毒雾在 {@code entity/PoisonFumeEntity} 和 {@link FumeSpawns}；ξ 腰带在 {@link RadiationBaubleEvents}。
 * <p>
 * <b>海纳尔环境抗性</b>（第十三批 13b，batch13.md §4.4；{@link HoannaWard}，虚空套装全套给）：三种惩罚按 {@code ×(1 − 抵挡)} 结算，天灾本身不变。
 * <ul>
 *   <li>日阵：灼烧照常 {@code hurt(onFire, 1)}、着火 10 秒，被日阵烧过的玩家在随后 {@value #SUN_WARD_TICKS} 刻里受到的 {@code on_fire} 伤害
 *       在 {@link LivingHurtEvent} HIGH 乘 {@code 1 − 抵挡}（{@link #onHazardsSunWard}）：日阵那一下和着火的余烧一起减。不直接把 1 点改小，
 *       是因为 1 刻后着火那一跳（1 点）会把差额补上（受伤冷却里只扣「比上一下多的部分」），露天时等于没减。
 *       抵挡满了不烧、不点火、也不提示。这段时间里别的东西点着的火（火焰附加）同样按这个减，算在虚空套装头上：
 *       火焰附加、着火的箭、火焰方块都不会把日阵的 10 秒火延长，真正多减的只有离开露天后被岩浆续上的那几秒，不值得另记一套着火截止。
 *       抵挡取「被烧那一刻」和「这一跳」里小的那个：烧完换上更高阶的全套不会让这 11 秒里的火变成全免，脱掉了也不再减。</li>
 *   <li>地震：踉跄、强震的速度乘 {@code 1 − 抵挡}（{@link #earthquake}，随机数用量不变）；抵挡满了不推、也不发镜头冲击。</li>
 *   <li>毒雾：{@link #fumePoison}（{@code PoisonFumeEntity} 调）。毒雾每 {@code AFFECT_INTERVAL}（10）刻判一次，只缩时长和续毒门槛的话门槛仍远大于 10 刻，
 *       人站在雾里中毒 II 一直挂着、掉血和没穿一样，只有离开后的余毒变短。所以有抵挡、没满时改成「隔开着给」：每次给 {@code 200 × (1 − 抵挡)} 刻，
 *       离上次因毒雾中毒不到 200 刻就不再给，人在雾里中毒的时间约占 {@code 1 − 抵挡}，离开后的余毒也是 {@code ×(1 − 抵挡)}。
 *       没有抵挡时一字不改（没有、比 II 低、剩的不到 100 刻才给 200 刻）；抵挡满了不中毒。毒雾加的辐射不在这里减。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class HoannaHazards {

    /** 结算间隔（tick） */
    public static final int CYCLE = Radiation.CYCLE;
    /** 日阵：伤害、着火秒数（原作 attackEntityFrom(ON_FIRE, 1) + setFire(10)） */
    public static final float SUN_DAMAGE = 1F;
    public static final int SUN_FIRE_SECONDS = 10;
    /** 日阵露天提示的间隔（tick） */
    public static final int SUN_HINT_TICKS = 600;
    public static final String SUN_HINT_KEY = "message.essentialcraft5.hoanna_event.sun_array.exposed";
    /** 被日阵烧过以后多少刻里的 on_fire 伤害按海纳尔环境抗性减（着火 10 秒 + 一个结算间隔） */
    public static final int SUN_WARD_TICKS = SUN_FIRE_SECONDS * 20 + 20;
    /** 地震：小踉跄的水平速度上限（每轴） */
    public static final double SMALL_PUSH = 0.15D;
    /** 地震：每次结算换成强震的概率 */
    public static final float STRONG_CHANCE = 0.15F;
    public static final double STRONG_MIN = 0.3D;
    public static final double STRONG_MAX = 0.6D;
    public static final double STRONG_UP_MIN = 0.25D;
    public static final double STRONG_UP_MAX = 0.35D;

    /** 一次地震冲量（服务端算，GameTest 测上限） */
    public record Quake(double x, double y, double z, boolean strong) {

        public double horizontal() {
            return Math.sqrt(x * x + z * z);
        }
    }

    /** 玩家 UUID → 上次日阵露天提示的 gameTime（下线、关服清） */
    private static final Map<UUID, Long> SUN_HINTS = new HashMap<>();
    /** 玩家 UUID → 上次被日阵灼烧的 gameTime 和那时的日阵抵挡（海纳尔环境抗性按它认「日阵烧的火」；下线、关服清） */
    private static final Map<UUID, SunBurn> SUN_BURNS = new HashMap<>();
    /** 玩家 UUID → 上次因毒雾中毒（有抵挡、没满时）的服务器刻；下线、关服清 */
    private static final Map<UUID, Long> FUME_POISONED = new HashMap<>();

    private record SunBurn(long time, double ward) {
    }

    /** GameTest 用：毒雾计时用的服务器刻，-1 = 照常读服务器 */
    private static volatile long testTime = -1L;

    private HoannaHazards() {
    }

    @SubscribeEvent
    public static void onHazardsPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side == LogicalSide.SERVER
                && event.player instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            Radiation.pulse(player);
            if (player.tickCount % CYCLE == 0) {
                tickPlayer(player);
            }
        }
    }

    /** 每 20 tick 一次的结算（GameTest 直接调；测试玩家不在世界里，事件不会自己跑） */
    public static void tickPlayer(ServerPlayer player) {
        if (player instanceof FakePlayer || !player.isAlive()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean inHoanna = ECHoanna.isHoanna(level);
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            Radiation.tick(player, data, inHoanna);
        }
        if (!inHoanna) {
            return;
        }
        HoannaEvent event = HoannaEvents.current(level);
        if (event == HoannaEvent.SUN_ARRAY) {
            sunArray(player, level);
        } else if (event == HoannaEvent.EARTHQUAKE) {
            earthquake(player, player.getRandom());
        } else if (event == HoannaEvent.LEY_STORM) {
            LeyStorm.exposure(player, level);
        }
    }

    // ------------------------------------------------------------------ 日阵

    /** 头顶 +2 格那一格看不看得见天空（原作 canBlockSeeSky(floor(x), floor(y + 2), floor(z))） */
    public static boolean exposedToSky(Level level, Player player) {
        return level.canSeeSky(BlockPos.containing(player.getX(), player.getY() + 2D, player.getZ()));
    }

    /**
     * 日阵的一次灼烧：露天、生存 / 冒险、没戴 μ、海纳尔环境抗性没满 → 提示（限频）+ 1 点火焰伤害，伤害生效才着火。
     * 抗性没满时伤害在 {@link #onHazardsSunWard} 里按比例减（见类注释）。
     *
     * @return 这一次有没有打中（伤害生效）
     */
    public static boolean sunArray(ServerPlayer player, ServerLevel level) {
        if (player.isCreative() || player.isSpectator() || CuriosCompat.has(player, ECBaubles.HOANNA_ATTUNED) || !exposedToSky(level, player)
                || HoannaWard.remaining(player, HoannaWard.SUN_ARRAY) <= 0D) {
            return false;
        }
        long now = level.getGameTime();
        SUN_BURNS.put(player.getUUID(), new SunBurn(now, HoannaWard.level(player, HoannaWard.SUN_ARRAY)));
        Long last = SUN_HINTS.get(player.getUUID());
        if (last == null || now - last >= SUN_HINT_TICKS || now < last) {
            SUN_HINTS.put(player.getUUID(), now);
            player.displayClientMessage(Component.translatable(SUN_HINT_KEY).withStyle(ChatFormatting.GOLD), true);
        }
        if (player.hurt(level.damageSources().onFire(), SUN_DAMAGE)) {
            player.setSecondsOnFire(SUN_FIRE_SECONDS);
            return true;
        }
        return false;
    }

    /**
     * 海纳尔里、{@value #SUN_WARD_TICKS} 刻内被日阵烧过的玩家受到的 {@code on_fire} 伤害 × (1 − 日阵抵挡)（HIGH，在护盾之前；见类注释）。
     * 抵挡取被烧那一刻和现在里小的那个；是 0（没穿虚空全套）时不动。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHazardsSunWard(LivingHurtEvent event) {
        if (SUN_BURNS.isEmpty() || !(event.getEntity() instanceof ServerPlayer player) || !event.getSource().is(DamageTypes.ON_FIRE)) {
            return;
        }
        SunBurn burned = SUN_BURNS.get(player.getUUID());
        if (burned == null || !ECHoanna.isHoanna(player.serverLevel())) {
            return;
        }
        long since = player.serverLevel().getGameTime() - burned.time();
        if (since < 0L || since > SUN_WARD_TICKS) {
            return;
        }
        double ward = Math.min(burned.ward(), HoannaWard.level(player, HoannaWard.SUN_ARRAY));
        if (ward > 0D) {
            event.setAmount((float) (event.getAmount() * Math.max(0D, 1D - ward)));
        }
    }

    // ------------------------------------------------------------------ 地震

    /** 能不能被地震推：站在地上、不骑乘、不鞘翅、不飞行、不是创造 / 旁观 */
    public static boolean canBeShaken(Player player) {
        return player.onGround() && !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying
                && !player.isCreative() && !player.isSpectator();
    }

    /**
     * 这一次的冲量：以 {@value #STRONG_CHANCE} 的概率（strongAllowed 时）强震，否则小踉跄。随机数用量固定（一次 nextFloat、三次 nextDouble）。
     */
    public static Quake quakeImpulse(RandomSource random, boolean strongAllowed) {
        boolean strong = random.nextFloat() < STRONG_CHANCE && strongAllowed;
        double a = random.nextDouble();
        double b = random.nextDouble();
        double c = random.nextDouble();
        if (strong) {
            double angle = a * Math.PI * 2D;
            double speed = STRONG_MIN + b * (STRONG_MAX - STRONG_MIN);
            double up = STRONG_UP_MIN + c * (STRONG_UP_MAX - STRONG_UP_MIN);
            return new Quake(Math.cos(angle) * speed, up, Math.sin(angle) * speed, true);
        }
        return new Quake((a * 2D - 1D) * SMALL_PUSH, 0D, (b * 2D - 1D) * SMALL_PUSH, false);
    }

    /**
     * 地震的一次结算：推不动的人（{@link #canBeShaken}）什么都不做；戴 μ 不会强震。冲量 × (1 − 地震抵挡)（{@link HoannaWard}）；
     * 抵挡满了不推、不发包，返回的冲量是 0。强震给本人发镜头冲击包；{@code earthquake_push} 关掉时不改速度。
     *
     * @return 这一次实际的冲量（已经乘过抵挡；没推返回 null）
     */
    @Nullable
    public static Quake earthquake(ServerPlayer player, RandomSource random) {
        if (!canBeShaken(player)) {
            return null;
        }
        Quake raw = quakeImpulse(random, !CuriosCompat.has(player, ECBaubles.HOANNA_ATTUNED));
        double scale = Math.max(0D, HoannaWard.remaining(player, HoannaWard.QUAKE));
        Quake quake = new Quake(raw.x() * scale, raw.y() * scale, raw.z() * scale, raw.strong());
        if (scale <= 0D) {
            return quake;
        }
        if (quake.strong()) {
            S2CHoannaJoltPacket.send(player, S2CHoannaJoltPacket.FULL);
        }
        if (HoannaHazardsConfig.earthquakePush()) {
            player.push(quake.x(), quake.y(), quake.z());
            player.hurtMarked = true;
        }
        return quake;
    }

    // ------------------------------------------------------------------ 毒雾

    /**
     * 毒雾给一名玩家中毒（{@code PoisonFumeEntity.affectPlayers} 调，只管中毒，辐射那边照旧；见类注释「毒雾」）。
     * 没有抵挡：没有中毒、等级比 II 低、或者剩的不到 {@code POISON_REFRESH_BELOW} 才给 {@code POISON_TICKS}（原样）。
     * 有抵挡、没满：时长 {@code POISON_TICKS × (1 − 抵挡)}（四舍五入），离这名玩家上次因毒雾中毒不到 {@code POISON_TICKS} 刻就不给
     * （几团毒雾叠着也只按这一个计时），给上了才记下这一刻。抵挡满了（时长算出来是 0）不给。
     *
     * @return 这次给了中毒
     */
    public static boolean fumePoison(Player player, @Nullable Entity fume) {
        double scale = Math.max(0D, HoannaWard.remaining(player, HoannaWard.FUME));
        int ticks = (int) Math.round(PoisonFumeEntity.POISON_TICKS * scale);
        if (ticks <= 0) {
            return false;
        }
        if (scale >= 1D) {
            MobEffectInstance poison = player.getEffect(MobEffects.POISON);
            if (poison == null || poison.getAmplifier() < PoisonFumeEntity.POISON_AMPLIFIER
                    || (!poison.isInfiniteDuration() && poison.getDuration() < PoisonFumeEntity.POISON_REFRESH_BELOW)) {
                return player.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, PoisonFumeEntity.POISON_AMPLIFIER), fume);
            }
            return false;
        }
        long now = now(player);
        Long last = FUME_POISONED.get(player.getUUID());
        if (last != null && now >= last && now - last < PoisonFumeEntity.POISON_TICKS) {
            return false;
        }
        boolean given = player.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, PoisonFumeEntity.POISON_AMPLIFIER), fume);
        if (given) {
            FUME_POISONED.put(player.getUUID(), now);
        }
        return given;
    }

    /** 毒雾计时用服务器刻（各世界的 gameTime 不一样，换维度会错）；GameTest 可以覆盖 */
    private static long now(Player player) {
        long override = testTime;
        if (override >= 0L) {
            return override;
        }
        MinecraftServer server = player.level().getServer();
        return server != null ? server.getTickCount() : player.level().getGameTime();
    }

    /** GameTest 用：毒雾计时读这个刻数，-1 = 照常读服务器。同一段同步代码里设、测、置回 */
    public static void setTestTime(long tick) {
        testTime = tick;
    }

    /** GameTest 用：忘掉这名玩家上次因毒雾中毒的时刻 */
    public static void forgetFumeForTest(Player player) {
        FUME_POISONED.remove(player.getUUID());
    }

    // ------------------------------------------------------------------ 清理

    @SubscribeEvent
    public static void onHazardsLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SUN_HINTS.remove(event.getEntity().getUUID());
        SUN_BURNS.remove(event.getEntity().getUUID());
        FUME_POISONED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onHazardsServerStopped(ServerStoppedEvent event) {
        SUN_HINTS.clear();
        SUN_BURNS.clear();
        FUME_POISONED.clear();
    }
}
