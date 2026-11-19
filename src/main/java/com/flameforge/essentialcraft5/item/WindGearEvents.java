package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.GearFxLimiter;
import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 风之装备（ECWindGear）的 Forge 总线事件，两端都注册：
 * <ul>
 *   <li>开始挖方块时风之工具换形态（{@link WindToolForms#onStartMining}）。最低优先级、被取消的也收，而且照样换：
 *       别的监听器（领地、保护类模组）取消这次左键只拦挖方块，换形态只动玩家自己手里的物品；两端都换才一致，
 *       只在服务端取消时如果服务端不换，客户端会每个往返重开一次挖掘、工具在两个形态间来回闪。</li>
 *   <li>风之护腿：起跳时额外 +0.2 竖直速度（原作 PlayerTracker.onPlayerJump L87~90，只对玩家；两端都加，同原版跳跃提升的做法）。</li>
 * </ul>
 * 靴子登高、胸甲加速是盔甲的属性修饰符（{@code ArmorBonus}），不在这里。
 * <p>
 * <b>全套「御风」</b>（第十三批 13b，batch13.md §4.3、§4.4；全套阶见 {@link WindArmorItem#fullSetStage}，数值在 {@link ECWindGear.Config}）：
 * <ul>
 *   <li><b>摔落</b>：{@code LivingFallEvent} 里把伤害倍数再乘 {@code fall_damage_multiplier}（默认 0.25），乘在别的倍数上，不覆盖。</li>
 *   <li><b>空中跳</b>：客户端（{@code client/WindArmorClient}）在空中新按下跳跃时调 {@link #airJump}，自己把竖直速度提到 {@code air_jump_velocity}，不加包。
 *       原版服务端照客户端报的位置挪人：上升 0.5 远低于「移动太快」的门槛；原版的「飘浮」踢人线量的是连续离地（下落也算）满 80 刻，
 *       默认 2 阶整跳约三十刻。摔落距离也不用另管：原版 {@code ServerGamePacketListenerImpl.handleMovePlayer} 每接受一个往上走的移动包就把它清零
 *       （{@code if (flag) player.resetFallDistance()}，混合端照留），空中跳那一下的包就把它清了，落地按最高点往下算。
 *       服务端这边只为给附近别的玩家放一团风的粒子和声音（跳的人自己的那份客户端已经放了），从移动包留下的痕迹认空中跳（{@link #trackMotion}，
 *       每刻在 PlayerTickEvent START 看：这时位置、onGround 还是最后一个包报的，END 时已经被服务端自己按重力模拟过一刻）：
 *       上一次看时摔落距离大于 0（这次离地以后往下掉过）、这次是 0（最后一个包往上走）、这一刻净上升够多或比上一刻快得多，就算一次。
 *       只看位置差的话，一刻收 1 个包、下一刻收 2 个包的普通起跳上升段就会被认成空中跳；落地包和下一跳的起跳包挤在同一刻处理，
 *       由服务端的 {@code LivingJumpEvent}（原版逐包判断从着地变离地时调 {@code jumpFromGround}）把次数清零（{@link #onLivingJump}）。
 *       代价是在最高点之前（还在往上走时）按的那一下，旁人看不到这团风；跳的人自己照常跳。一次落地之间最多认全套这一阶的次数。
 *       装了按位置判飞行的反作弊插件的服务器可能会拦：配置 {@code air_jump_velocity} 填 0 就只剩减摔（{@code air_jumps} 是列表，要关得写成 {@code [0]}）。</li>
 *   <li><b>偏转</b>在 {@code player/WindPact}；偏开一颗时回调 {@link #onSetDeflected}：按「被偏转整下吃掉」给盔甲记同调度；
 *       3 阶起，偏开的是敌人射来的（{@link #tailwindSource}：敌对生物、首领，或这里能伤到你的玩家）才开始「顺风」。</li>
 *   <li><b>潜行不被击退</b>（1 阶）也在 {@code player/WindPact.resistsKnockback}。</li>
 *   <li><b>疾跑</b>（2 阶，{@code sprint_speed_bonus}）、<b>顺风</b>（3 阶，{@code tailwind_*}）：临时属性修饰符、固定 UUID、MULTIPLY_TOTAL。
 *       服务端每刻按当时的状态补上或摘掉（{@link #applyModifiers}；属性会同步给客户端），不靠换装事件：同调写盘改了物品 NBT 时原版当成换了一件、
 *       重算盔甲自带的修饰符，这里的状态记在人身上，不跟着变。没穿齐的玩家每刻也查一下这三个 UUID，有就摘（O(1)），不留残余。</li>
 * </ul>
 * 只作用于自己：偏转不改弹射物、不碰射手，所以不过 PvP / 领地门，也不用 {@code pvp_effect_scale}。顺风只认敌人射来的那颗，
 * 免得站在发射器的雪球时钟前、或让朋友对着扔雪球，原地挂一个常驻的加成。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class WindGearEvents {

    /** 风之护腿起跳时额外加的竖直速度（原版起跳 0.42，合计约能跳 2.5 格高） */
    public static final double LEGGINGS_JUMP_BOOST = 0.2D;

    /** 全套几阶起潜行时不被击退 */
    public static final int STEADFAST_STAGE = 1;
    /** 全套几阶起疾跑加速 */
    public static final int SPRINT_STAGE = 2;
    /** 全套几阶起偏开以后顺风 */
    public static final int TAILWIND_STAGE = 3;

    public static final UUID SPRINT_ID = UUID.fromString("7a3e51c2-9d84-4f0b-b6e1-2c5d8f0a9e31");
    public static final UUID TAILWIND_SPEED_ID = UUID.fromString("7a3e51c2-9d84-4f0b-b6e1-2c5d8f0a9e32");
    public static final UUID TAILWIND_ATTACK_ID = UUID.fromString("7a3e51c2-9d84-4f0b-b6e1-2c5d8f0a9e33");

    /** 服务端认空中跳（两条满足一条）：这一刻净上升至少这么多格 */
    public static final double IMPULSE_MIN_RISE = 0.1D;
    /** 服务端认空中跳（两条满足一条）：这一刻比上一刻多上升这么多（只受重力时每刻少约 0.08；空中跳那个包和前一个下落包挤在同一刻时净值可能还是往下） */
    public static final double IMPULSE_MIN_GAIN = 0.15D;
    /** 两次看之间挪动超过这么多格（三维）当成传送，不认空中跳 */
    public static final double TELEPORT_DISTANCE = 4D;

    /** 服务端每名穿着全套的玩家的状态（弱引用，下线、换了玩家对象就没了） */
    private static final Map<Player, SetState> SERVER = new WeakHashMap<>();

    private WindGearEvents() {
    }

    /**
     * 服务端记在人身上的状态：上一次看时的位置、升降和摔落距离、在不在空中，这次离地以后认了几次空中跳，顺风还剩几刻
     */
    private static final class SetState {
        boolean tracked;
        @Nullable
        ResourceKey<Level> dimension;
        double lastX;
        double lastY;
        double lastZ;
        double lastDy;
        float lastFall;
        boolean airborne;
        int impulses;
        int tailwind;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        WindToolForms.onStartMining(event.getEntity(), event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // 按类判断，不碰 ECWindGear 的字段：万一注册类没接线，碰字段会让它在第一次起跳时才去登记物品（注册期已过，直接崩）
        if (player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof WindArmorItem armor && armor.getType() == ArmorItem.Type.LEGGINGS) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, motion.y + LEGGINGS_JUMP_BOOST, motion.z);
        }
        if (!player.level().isClientSide) {
            groundJump(player);
        }
    }

    /**
     * 服务端：玩家从地上起跳（原版 handleMovePlayer 逐包判断，服务端记着在地上、这个包报离地且往上走时调 {@code jumpFromGround}）。
     * 落地包和起跳包挤在同一刻处理时，每刻看一次的 {@link #trackMotion} 看不到中间那次着地，这里把次数清零，
     * 并把「上次看时的摔落距离」记成 0，下一次看时不会把这次起跳当成空中跳。
     * <p>
     * 只认摔落距离是 0 的：真从地上起跳时，前一个包落地已经把它清了。服务端自己按重力模拟的那一刻会把空中的人「落」到地上
     * （onGround 被模拟改成 true），紧接着客户端的空中跳包往上走，原版也会调一次 {@code jumpFromGround}，那时摔落距离还攒着、大于 0，不算。
     */
    private static void groundJump(Player player) {
        SetState state = SERVER.get(player);
        if (state == null || player.fallDistance > 0F) {
            return;
        }
        state.impulses = 0;
        state.airborne = true;
        state.lastFall = 0F;
        state.lastDy = 0D;
    }

    // ------------------------------------------------------------------ 摔落

    /** 全套时摔落伤害倍数再乘一次（乘在别的监听器给的倍数上，谁先谁后结果一样） */
    @SubscribeEvent
    public static void windArmorFall(LivingFallEvent event) {
        if (WindArmorItem.fullSetStage(event.getEntity()) < 0) {
            return;
        }
        double multiplier = ECWindGear.Config.values().fallDamageMultiplier();
        if (multiplier < 1D) {
            event.setDamageMultiplier((float) (event.getDamageMultiplier() * multiplier));
        }
    }

    // ------------------------------------------------------------------ 空中跳

    /**
     * 现在能不能空中跳：穿齐全套、人在空中（不在地上、水里、岩浆里、梯子上、坐骑上）、没在飞（创造飞行、鞘翅）、不是能飞的人（mayfly：双击跳跃是开关飞行）、
     * 这次离地以后已经跳过的次数 used 小于全套这一阶给的次数。两端都能算。
     */
    public static boolean canAirJump(Player player, int used) {
        if (player.onGround() || player.isPassenger() || player.isSpectator() || player.isSleeping() || player.isFallFlying()
                || player.isInWater() || player.isInLava() || player.onClimbable()
                || player.getAbilities().flying || player.getAbilities().mayfly) {
            return false;
        }
        int stage = WindArmorItem.fullSetStage(player);
        return stage >= 0 && used >= 0 && used < ECWindGear.Config.values().airJumps(stage);
    }

    /**
     * 空中跳一次：竖直速度提到 {@code air_jump_velocity}，摔落距离清零，返回 true（算用掉一次）。
     * 已经比它往上快（跳跃提升 II 起跳后头两刻）时什么都不改、返回 false：不让空中跳变成刹车，也不白占一次次数。
     * 客户端在本地玩家身上调（自己的位置由客户端定，不加包）；服务端那边由 {@link #trackMotion} 从移动包的痕迹认出来。
     */
    public static boolean airJump(Player player, int used) {
        if (!canAirJump(player, used)) {
            return false;
        }
        double velocity = ECWindGear.Config.values().airJumpVelocity();
        Vec3 motion = player.getDeltaMovement();
        if (motion.y >= velocity) {
            return false;
        }
        player.setDeltaMovement(motion.x, velocity, motion.z);
        player.resetFallDistance();
        return true;
    }

    /**
     * 客户端这一刻的跳跃键算不算一次空中跳的按键（两端都能调，GameTest 直接测）：新按下（上一刻没按着）、这一刻不在地上，
     * 并且上一刻也不在地上。上一刻还在地上时按下的那一下，原版 {@code aiStep} 已经拿去地面起跳了（它看的是上一刻的 onGround）；
     * 头顶只有两格高时起跳撞上天花板，这一刻竖直速度已经是负的，也还是那一下地面起跳，不能再算一次空中跳。
     *
     * @param pressed        新按下跳跃
     * @param grounded       这一刻着地（地上、水里、岩浆里、梯子上、坐骑上）
     * @param groundedBefore 上一刻着地
     */
    public static boolean isAirJumpPress(boolean pressed, boolean grounded, boolean groundedBefore) {
        return pressed && !grounded && !groundedBefore;
    }

    // ------------------------------------------------------------------ 服务端每刻

    @SubscribeEvent
    public static void windArmorPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.player instanceof FakePlayer) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            serverTickStart(event.player);
        } else {
            serverTick(event.player);
        }
    }

    /**
     * 服务端每刻开头（PlayerTickEvent START，GameTest 直接调）：认空中跳。这时位置和 onGround 还是最后一个移动包报的
     * （原版 {@code ServerGamePacketListenerImpl.tick} 先 {@code doTick} 按重力模拟一刻，END 之后才挪回包里的位置），见 {@link #trackMotion}。
     */
    public static void serverTickStart(Player player) {
        int stage = WindArmorItem.fullSetStage(player);
        if (stage < 0) {
            return;
        }
        trackMotion(player, SERVER.computeIfAbsent(player, key -> new SetState()), stage, ECWindGear.Config.values());
    }

    /** 服务端每刻末尾（PlayerTickEvent END，GameTest 直接调）：顺风倒计时、补 / 摘疾跑和顺风的修饰符；没穿齐就清掉状态 */
    public static void serverTick(Player player) {
        int stage = WindArmorItem.fullSetStage(player);
        if (stage < 0) {
            SERVER.remove(player);
            applyModifiers(player, 0D, 0D, 0D);
            return;
        }
        ECWindGear.Config.Values values = ECWindGear.Config.values();
        SetState state = SERVER.computeIfAbsent(player, key -> new SetState());
        boolean tailwind = stage >= TAILWIND_STAGE && state.tailwind > 0;
        if (state.tailwind > 0) {
            state.tailwind--;
        }
        double sprint = stage >= SPRINT_STAGE && player.isSprinting() ? values.sprintSpeedBonus() : 0D;
        applyModifiers(player, sprint, tailwind ? values.tailwindSpeedBonus() : 0D, tailwind ? values.tailwindAttackSpeedBonus() : 0D);
    }

    /**
     * 服务端从移动包留下的痕迹认空中跳（见类注释），只用来给旁人放一团风；摔落距离由原版逐包处理，这里不碰。
     * <ul>
     *   <li>位置和上一次看时一模一样 = 这一刻没收到移动包：没有新东西，什么都不改。这时的 onGround 是服务端自己按重力模拟出来的
     *       （人在空中离地还有两三格时，模拟这一步就「落」到了地上），不能拿来清次数。</li>
     *   <li>在地上、水里、岩浆里、梯子上、坐骑上、飞行、鞘翅滑翔都算「着地」，次数清零。</li>
     *   <li>认一次：上一次看时在空中、摔落距离大于 0（这次离地以后往下掉过），这次摔落距离是 0（原版每收一个往上走的包就清零，
     *       说明最后一个包往上走），并且这一刻净上升至少 {@link #IMPULSE_MIN_RISE} 或比上一刻多上升 {@link #IMPULSE_MIN_GAIN}；
     *       一次落地之间最多认全套这一阶的次数。普通起跳的上升段摔落距离一直是 0，一刻收几个包都不会被认。</li>
     *   <li>刚开始记、换了维度、两次看之间挪动超过 {@link #TELEPORT_DISTANCE} 格（传送）只重新记位置，不认。</li>
     * </ul>
     */
    private static void trackMotion(Player player, SetState state, int stage, ECWindGear.Config.Values values) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        ResourceKey<Level> dimension = player.level().dimension();
        boolean fresh = !state.tracked || state.dimension != dimension;
        if (!fresh && x == state.lastX && y == state.lastY && z == state.lastZ) {
            return;
        }
        boolean grounded = player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable() || player.isPassenger()
                || player.getAbilities().flying || player.isFallFlying();
        float fall = player.fallDistance;
        if (fresh) {
            state.tracked = true;
            state.dimension = dimension;
            state.impulses = 0;
            remember(state, x, y, z, 0D, fall, grounded);
            return;
        }
        double dx = x - state.lastX;
        double dy = y - state.lastY;
        double dz = z - state.lastZ;
        boolean teleported = dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE * TELEPORT_DISTANCE;
        if (grounded) {
            state.impulses = 0;
        } else if (!teleported && state.airborne && state.lastFall > 0F && fall <= 0F
                && (dy >= IMPULSE_MIN_RISE || dy - state.lastDy >= IMPULSE_MIN_GAIN)
                && state.impulses < values.airJumps(stage)) {
            state.impulses++;
            airJumpEffects(player);
            com.flameforge.essentialcraft5.item.attunement.ArmorAwakenings.stormStep(player, stage);
        }
        remember(state, x, y, z, teleported ? 0D : dy, fall, grounded);
    }

    private static void remember(SetState state, double x, double y, double z, double dy, float fall, boolean grounded) {
        state.lastX = x;
        state.lastY = y;
        state.lastZ = z;
        state.lastDy = dy;
        state.lastFall = grounded ? 0F : fall;
        state.airborne = !grounded;
    }

    /**
     * 空中跳的声音和画面：服务端调时声音放给附近别的玩家（不含跳的人），画面发 {@link GearFx#AIR_JUMP}；
     * 客户端调时只放给自己（原版 playSound(玩家, …) 的预测写法），画面由 client/WindArmorClient 在本地先画，收到自己的事件就跳过
     */
    public static void airJumpEffects(Player player) {
        player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.4F, 1.6F);
        if (player.level() instanceof ServerLevel level) {
            GearFxLimiter.send(level, GearFx.AIR_JUMP, player, player.position(), 0F, 3);
        }
    }

    // ------------------------------------------------------------------ 偏转之后

    /**
     * {@code player/WindPact} 在服务端偏开一颗时、全套参与了这次偏转才调：这一下算「被偏转整下吃掉」，照挨一下给同调盔甲记点
     * （{@link AttunementGains#armorHitAbsorbed} 自己查攻击者、来源、交手、预算）；全套 3 阶起，射来的是敌人（{@link #tailwindSource}）才开始顺风。
     */
    public static void onSetDeflected(Player player, Projectile projectile, int stage) {
        if (player.level().isClientSide) {
            return;
        }
        Entity owner = projectile.getOwner();
        if (owner != null) {
            AttunementGains.armorHitAbsorbed(player, player.damageSources().thrown(projectile, owner));
        }
        if (stage >= TAILWIND_STAGE && tailwindSource(player, owner)) {
            int ticks = ECWindGear.Config.values().tailwindTicks();
            if (ticks > 0) {
                SERVER.computeIfAbsent(player, key -> new SetState()).tailwind = ticks;
            }
        }
    }

    /**
     * 偏开这个射手的弹射物以后给不给顺风：射手是活物、不是自己，并且是敌对生物、首领（{@code #forge:bosses}），
     * 或者是这里能伤到你的玩家（{@code canHarmPlayer}：服务器 PvP 开关、队伍友伤，混合端按世界的 PvP）。
     * 没有主人的（发射器射出的）、同队伍不许互伤的玩家扔来的都不算；女巫的药水、雪傀儡的雪球也按射手自然分开，不另按弹射物种类排除。
     */
    public static boolean tailwindSource(Player player, @Nullable Entity owner) {
        if (!(owner instanceof LivingEntity living) || living == player || living.getUUID().equals(player.getUUID())) {
            return false;
        }
        if (living instanceof Enemy || AttunementGains.isBoss(living)) {
            return true;
        }
        return living instanceof Player shooter && shooter.canHarmPlayer(player);
    }

    /** 顺风还剩几刻（服务端；没有是 0） */
    public static int tailwindLeft(Player player) {
        SetState state = SERVER.get(player);
        return state == null ? 0 : state.tailwind;
    }

    /** 这次离地以后服务端认了几次空中跳（测试用） */
    public static int serverAirJumps(Player player) {
        SetState state = SERVER.get(player);
        return state == null ? 0 : state.impulses;
    }

    /** GameTest 用：清掉一名玩家的服务端状态 */
    public static void forgetForTest(Player player) {
        SERVER.remove(player);
    }

    // ------------------------------------------------------------------ 修饰符

    /** 三个修饰符按给的数补上或摘掉（≤ 0 = 摘；数没变不动） */
    public static void applyModifiers(Player player, double sprint, double tailwindSpeed, double tailwindAttack) {
        setModifier(player, Attributes.MOVEMENT_SPEED, SPRINT_ID, "Wind armor sprint", sprint);
        setModifier(player, Attributes.MOVEMENT_SPEED, TAILWIND_SPEED_ID, "Wind armor tailwind", tailwindSpeed);
        setModifier(player, Attributes.ATTACK_SPEED, TAILWIND_ATTACK_ID, "Wind armor tailwind", tailwindAttack);
    }

    private static void setModifier(Player player, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (amount <= 0D) {
            if (current != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (current != null) {
            if (current.getAmount() == amount && current.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                return;
            }
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
