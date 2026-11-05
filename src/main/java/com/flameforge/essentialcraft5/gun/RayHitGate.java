package com.flameforge.essentialcraft5.gun;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 射线打玩家时「这一下算不算打中」（GUNS-4 修订，batch5.md §8.1）：打中就加矩阵损伤，<b>不看伤害生效没有</b>——
 * 无敌帧里 {@code hurt} 返回 false、伤害被 {@code LivingHurtEvent} / {@code LivingDamageEvent} 取消、被护甲吸收、伤害倍率为 0 都照加；
 * 但<b>攻击本身被拦下</b>（PvP 关着、队伍不许、出生无敌、领地 / 保护模组、混合端的领地插件）就不算打中。
 * <p>
 * 做法：{@link RayShot#hit} 调 {@code hurt} 之前 {@link #begin} 登记这一发（伤害源对象 + 目标），{@code hurt} 走下去的时候本类的三个监听器按
 * 「同一个伤害源对象、同一个目标」认出这一发、记下伤害流程走到了哪一步，{@code hurt} 返回后 {@link #end} 再由 {@link #verdict} 判。
 * <b>不另发任何事件</b>（再发一次 {@code LivingAttackEvent} 会让别的模组的命中效果触发两遍）。
 * <ol>
 *   <li><b>{@code LivingAttackEvent}</b>（{@link EventPriority#LOWEST}、不收已取消的）：玩家挨打时它在 {@code Player.hurt} 的第一行发；
 *       PvP 开关和队伍友伤（{@code ServerPlayer.canHarmPlayer}）、出生无敌在它<b>之前</b>就返回了——被拦下的根本看不到这个事件。
 *       Forge 端的领地模组在这里取消。记下事件对象，{@code hurt} 返回后看它<b>最终</b>有没有被取消：排在本监听器后面的（同为 LOWEST、注册得晚）取消了也算数。</li>
 *   <li><b>{@code LivingHurtEvent} / {@code LivingDamageEvent}</b>：只用来认混合端插件的拦截。Mohist 系混合端（{@code LivingEntity.damageEntity0}，
 *       2026-09-29 核对）把 Bukkit 的 {@code EntityDamageByEntityEvent} 发在两者<b>之间</b>：{@code LivingHurtEvent} → Bukkit 事件（取消就直接返回 false，
 *       不设无敌帧）→ {@code LivingDamageEvent}（哪怕伤害是 0 也发）。纯 Forge 里 {@code LivingHurtEvent} 放行、伤害 &gt; 0 时一定接着发 {@code LivingDamageEvent}
 *       （{@code Player.actuallyHurt}），中间不可能停。所以「到了 LivingHurtEvent、没到 LivingDamageEvent」：
 *       混合端上就是插件拦了；纯 Forge 上只在 LivingHurtEvent 被取消或伤害被改成 0 时才会出现（那是「伤害没生效」，照加）。</li>
 *   <li><b>一步都没到 LivingHurtEvent</b>（无敌帧：{@code invulnerableTime > 10} 且这一下不比上一下重；原版滤掉 0 伤害）：纯 Forge 上攻击事件放行就算；
 *       混合端上插件这一下没被问到，按<b>同一射手最近 {@value #RECENT_ALLOWED_TICKS} 刻内完整走过一遍并被放行</b>算（{@link #recentlyAllowed}）——
 *       插件拦下时不会设无敌帧，所以连射时的无敌帧总是紧跟着一次被放行的命中；别的伤害（摔落、怪）留下的无敌帧里没有这条记录，不加。</li>
 * </ol>
 * 混合端的判断：服务端类加载器里找得到 {@code org.bukkit.Bukkit}（{@link #isBukkitHybrid}，只按名字反射找，不引用任何 Bukkit / Mohist 类，policy §2.3a）。
 * <p>
 * 只在服务端主线程用：{@code begin} / {@code end} 成对、可嵌套（伤害流程里别的模组又让同一个人挨一发射线也不串）。
 *
 * <h2>另外两个判定（第八批 8-Fa，batch8 §3.1 第 2 条）</h2>
 * 轨道打击、二分场按「这一下有没有真落下」决定后续效果，口径和上面射线的 {@link #allowed} 不同（那个是「伤害没生效也算打中」，不动）。
 * 用 {@link #observe} 一步做完登记 → {@code hurt} → 撤登记，再看返回的 {@link Watch}：
 * <ul>
 *   <li>{@link Watch#landed()}：攻击没被拦、{@code LivingDamageEvent} 到了而且最终没被取消——伤害真的落到了生命或伤害吸收上。
 *       纯 Forge 里 {@code LivingHurtEvent} 被取消或伤害被改成 0 时根本不发 {@code LivingDamageEvent}；混合端插件拦下 Bukkit 伤害事件时也不发。
 *       {@code LivingDamageEvent} 的伤害被别的监听器改成 0（没取消）时这里仍算落下（伤害吸收可能已经扣了），调用方要更严就自己再比一下生命 + 伤害吸收。</li>
 *   <li>{@link Watch#probePassed()}：给 0 伤害的探测用（二分场）。攻击没被拦、{@code LivingHurtEvent} 到了而且最终没被取消；
 *       混合端另要 {@code LivingDamageEvent} 到了（混合端在 {@code onLivingHurt} 之后只对 &lt; 0 提前返回，0 照样发 Bukkit 事件、放行后照常发
 *       {@code LivingDamageEvent}；纯 Forge 里 0 伤害在 {@code actuallyHurt} 的 {@code <= 0} 处就返回了，不会有 {@code LivingDamageEvent}）。
 *       纯 Forge 里玩家挨 0 伤害时 {@code Player.hurt} 在攻击事件之后直接返回 false、到不了 {@code LivingHurtEvent}，所以对玩家永远不放行；
 *       Mohist 系混合端的 {@code Player.hurt} 不滤 0 伤害，0 伤害探测会一路走到 Bukkit 伤害事件和 {@code LivingDamageEvent(0)}（插件、战斗标记插件都看得见），
 *       放行时这里为真。所以不要拿 0 伤害去探测玩家。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class RayHitGate {

    /** 混合端上「最近被放行过」的有效期（刻）：覆盖无敌帧（原版 20 刻、超过一半才挡） */
    public static final int RECENT_ALLOWED_TICKS = 20;
    /** 记录超过这么多条时清一次过期的 */
    private static final int PRUNE_THRESHOLD = 256;

    /** 一发射线打一个玩家的观察记录 */
    public static final class Watch {
        final DamageSource source;
        final LivingEntity target;
        @Nullable
        final Watch outer;
        @Nullable
        LivingAttackEvent attack;
        @Nullable
        LivingHurtEvent hurt;
        @Nullable
        LivingDamageEvent damage;
        boolean damageReached;
        /** {@link #observe} 里 {@code hurt} 的返回值（{@link #begin} / {@link #end} 自己调 hurt 的不填） */
        boolean hurtResult;

        Watch(DamageSource source, LivingEntity target, @Nullable Watch outer) {
            this.source = source;
            this.target = target;
            this.outer = outer;
        }

        /** {@link #observe} 里 {@code hurt} 的返回值 */
        public boolean hurtResult() {
            return hurtResult;
        }

        /**
         * 伤害真的落下了：攻击没被拦、{@code LivingDamageEvent} 到了而且最终没被取消（见类注释「另外两个判定」）。
         * 只在 {@code hurt} 返回以后看才有意义。
         */
        public boolean landed() {
            return attackAllowed() && damage != null && !damage.isCanceled();
        }

        /**
         * 0 伤害探测放行了：攻击没被拦、{@code LivingHurtEvent} 到了而且最终没被取消；Mohist 系混合端另要 {@code LivingDamageEvent} 到了、没被取消
         * （插件拦下 Bukkit 伤害事件时它不发）。纯 Forge 里对玩家永远是 false（{@code Player.hurt} 对 0 伤害直接返回）；混合端不滤 0 伤害，
         * 对玩家也会真的发 Bukkit 伤害事件，放行时为真。
         */
        public boolean probePassed() {
            if (!attackAllowed() || hurt == null || hurt.isCanceled()) {
                return false;
            }
            return !isBukkitHybrid() || (damage != null && !damage.isCanceled());
        }

        /** 攻击事件发到了本监听器、而且最终没被取消 */
        public boolean attackAllowed() {
            return attack != null && !attack.isCanceled();
        }

        public boolean hurtReached() {
            return hurt != null;
        }

        /** LivingHurtEvent 最终被取消或伤害被改成 ≤ 0（纯 Forge 这时不会再发 LivingDamageEvent） */
        public boolean hurtZeroed() {
            return hurt != null && (hurt.isCanceled() || hurt.getAmount() <= 0F);
        }

        public boolean damageReached() {
            return damageReached;
        }

        /**
         * 这一下最终扣到生命上的量：{@code LivingDamageEvent} 跑完所有监听器以后的值（已过护甲、附魔、伤害吸收）；
         * 没到 {@code LivingDamageEvent}、或者它被取消了为 0。只在 {@code hurt} 返回以后看。
         */
        public float dealt() {
            return damage == null || damage.isCanceled() ? 0F : Math.max(0F, damage.getAmount());
        }
    }

    /**
     * 一下伤害的 {@code LivingHurtEvent} 最终被取消、或者伤害被改成 ≤ 0（没取消）：这一下没有打实。
     * 纯 Forge 在这两种情况下都不再发 {@code LivingDamageEvent}（{@code actuallyHurt} 的 {@code <= 0} 处返回）；
     * Mohist 系混合端在 {@code onLivingHurt} 之后只对 &lt; 0 提前返回，0 照样发 Bukkit 事件、放行后照发 {@code LivingDamageEvent(0)}。
     * 在 {@code LivingDamageEvent} 里认「打实了」的地方（ζ、ι、ξ）拿事先记下的 LivingHurtEvent 问它，两种环境的结论就一样。
     */
    public static boolean hurtZeroed(LivingHurtEvent event) {
        return event.isCanceled() || !(event.getAmount() > 0F);
    }

    private record Pair(UUID target, UUID shooter) {
    }

    @Nullable
    private static Watch current;
    /** 混合端上完整走过伤害流程并被放行的（目标, 射手）→ 服务端刻数 */
    private static final Map<Pair, Integer> RECENT = new HashMap<>();
    @Nullable
    private static Boolean hybrid;
    @Nullable
    private static Boolean hybridOverride;

    private RayHitGate() {
    }

    // ------------------------------------------------------------------
    // 登记 / 判定
    // ------------------------------------------------------------------

    /** {@code hurt} 之前登记（服务端主线程；和 {@link #end} 成对，放在 try / finally 里） */
    public static Watch begin(DamageSource source, LivingEntity target) {
        Watch watch = new Watch(source, target, current);
        current = watch;
        return watch;
    }

    /** {@code hurt} 之后撤掉登记 */
    public static void end(Watch watch) {
        current = watch.outer;
    }

    /**
     * 登记 → {@code target.hurt(source, amount)} → 撤登记（finally），返回这一下的观察记录，
     * 再按需要看 {@link Watch#landed()} / {@link Watch#probePassed()} / {@link Watch#hurtResult()}（服务端主线程）。
     * {@code source} 每次新建一个实例（监听器按实例身份认这一发）。
     */
    public static Watch observe(LivingEntity target, DamageSource source, float amount) {
        return observe(target, source, amount, null);
    }

    /**
     * 同 {@link #observe(LivingEntity, DamageSource, float)}，登记好以后、{@code hurt} 之前先把观察记录交给 started：
     * 要在 {@code hurt} 中途（例：{@code Zombie.hurt} 在 {@code super.hurt} 返回后发的叫增援事件）看这一下落没落下的调用方用。
     * {@code LivingDamageEvent} 发过以后 {@link Watch#landed()} 就是定论。
     */
    public static Watch observe(LivingEntity target, DamageSource source, float amount, @Nullable Consumer<Watch> started) {
        Watch watch = begin(source, target);
        try {
            if (started != null) {
                started.accept(watch);
            }
            watch.hurtResult = target.hurt(source, amount);
        } finally {
            end(watch);
        }
        return watch;
    }

    /**
     * {@code hurt} 返回以后：这一下算不算打中（见类注释）。混合端上完整走过一遍并被放行的记下来，供之后无敌帧里的命中用。
     *
     * @param shooter 射手（混合端「最近被放行过」按射手分开记）
     * @param tick    服务端刻数（{@code MinecraftServer.getTickCount()}）
     */
    public static boolean allowed(Watch watch, LivingEntity shooter, int tick) {
        boolean bukkit = isBukkitHybrid();
        Pair pair = new Pair(watch.target.getUUID(), shooter.getUUID());
        boolean recent = bukkit && !watch.hurtReached() && recentlyAllowed(RECENT.get(pair), tick);
        boolean ok = verdict(watch.attackAllowed(), watch.hurtReached(), watch.hurtZeroed(), watch.damageReached(), bukkit, recent);
        if (ok && bukkit && watch.hurtReached()) {
            if (RECENT.size() >= PRUNE_THRESHOLD) {
                RECENT.values().removeIf(at -> !recentlyAllowed(at, tick));
            }
            RECENT.put(pair, tick);
        }
        return ok;
    }

    /**
     * 判定表（纯函数，GameTest 直接测混合端的几种情况）。
     *
     * @param attackAllowed   攻击事件到了、最终没被取消
     * @param hurtReached     到了 LivingHurtEvent
     * @param hurtZeroed      LivingHurtEvent 最终被取消或伤害 ≤ 0
     * @param damageReached   到了 LivingDamageEvent
     * @param bukkitHybrid    Mohist 系混合端
     * @param recentlyAllowed 混合端上同一射手最近被放行过（只在没到 LivingHurtEvent 时看）
     */
    public static boolean verdict(boolean attackAllowed, boolean hurtReached, boolean hurtZeroed, boolean damageReached,
                                  boolean bukkitHybrid, boolean recentlyAllowed) {
        if (!attackAllowed) {
            return false;
        }
        if (hurtReached) {
            if (damageReached) {
                return true;
            }
            // 到了 LivingHurtEvent、没到 LivingDamageEvent：混合端 = 插件拦了；纯 Forge 只有「伤害没生效」才会这样
            return !bukkitHybrid && hurtZeroed;
        }
        return !bukkitHybrid || recentlyAllowed;
    }

    /** 记录时刻 at（可能为 null）离现在 tick 不超过 {@value #RECENT_ALLOWED_TICKS} 刻（服务器重启后刻数从 0 算起，比现在还晚的当过期） */
    static boolean recentlyAllowed(@Nullable Integer at, int tick) {
        if (at == null) {
            return false;
        }
        int age = tick - at;
        return age >= 0 && age <= RECENT_ALLOWED_TICKS;
    }

    /** Mohist 系混合端（服务端类加载器里有 {@code org.bukkit.Bukkit}）；结果缓存 */
    public static boolean isBukkitHybrid() {
        if (hybridOverride != null) {
            return hybridOverride;
        }
        Boolean known = hybrid;
        if (known == null) {
            known = detectBukkit();
            hybrid = known;
        }
        return known;
    }

    /** 服务端核心的类加载器（Mohist 系把 Bukkit 和打过补丁的原版类放在一起）、线程上下文加载器、本模组的加载器，哪个找得到都算 */
    private static boolean detectBukkit() {
        for (ClassLoader loader : new ClassLoader[]{MinecraftServer.class.getClassLoader(), Thread.currentThread().getContextClassLoader(),
                RayHitGate.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try {
                Class.forName("org.bukkit.Bukkit", false, loader);
                return true;
            } catch (ClassNotFoundException | LinkageError e) {
                // 这个加载器看不到，试下一个
            }
        }
        return false;
    }

    /** GameTest 用：临时当成（不）是混合端，测完置回 null（全局，只在同一段同步代码里设、测、置回） */
    public static void setHybridForTest(@Nullable Boolean value) {
        hybridOverride = value;
    }

    // ------------------------------------------------------------------
    // 监听
    // ------------------------------------------------------------------

    @Nullable
    private static Watch find(DamageSource source, LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return null;
        }
        for (Watch watch = current; watch != null; watch = watch.outer) {
            if (watch.source == source && watch.target == entity) {
                return watch;
            }
        }
        return null;
    }

    /** 攻击被放行到了最后（LOWEST、不收已取消的）：记下事件对象，hurt 返回后再看它最终是否被取消 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGunRayAttackReached(LivingAttackEvent event) {
        if (current == null) {
            return;
        }
        Watch watch = find(event.getSource(), event.getEntity());
        if (watch != null && watch.attack == null) {
            watch.attack = event;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onGunRayHurtReached(LivingHurtEvent event) {
        if (current == null) {
            return;
        }
        Watch watch = find(event.getSource(), event.getEntity());
        if (watch != null && watch.hurt == null) {
            watch.hurt = event;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onGunRayDamageReached(LivingDamageEvent event) {
        if (current == null) {
            return;
        }
        Watch watch = find(event.getSource(), event.getEntity());
        if (watch != null) {
            watch.damageReached = true;
            if (watch.damage == null) {
                // 记下事件对象，hurt 返回后再看它最终是否被取消（landed / probePassed 用）
                watch.damage = event;
            }
        }
    }

    @SubscribeEvent
    public static void onGunRayServerStopping(ServerStoppingEvent event) {
        RECENT.clear();
        current = null;
    }
}
