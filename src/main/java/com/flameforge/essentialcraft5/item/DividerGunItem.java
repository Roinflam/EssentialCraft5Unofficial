package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.flameforge.essentialcraft5.entity.DividerProjectileEntity;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECGuns;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * 二分枪（原作 {@code ItemDividerGun}，64 行；注册名 {@code divider_gun}，batch8 §4.3、policy §4.6）。
 * 自带 {@value #CAPACITY} MRU（原作 setMaxMRU(20000)），创造栏放空 / 满两份（{@link MRUStaffItem}）。只有全息投影的稀有掉落，没有配方。
 * <ul>
 *   <li><b>拉弓</b>：右键开始拉（{@link UseAnim#BOW}，第三人称是拉弓的姿势），只认真玩家（{@code ECProtection.isRealPlayer}，同枪械）。
 *       <b>开始拉时就查</b>扣不扣得起 {@code general.divider_gun.mru_per_shot}，不够就提示、不开始（原作拉满才查，白拉 1 秒）。</li>
 *   <li><b>发射</b>：拉满 {@value #CHARGE_TICKS} 刻自动发射（{@link #finishUsingItem}，中途松手什么都不发生）：再确认一次扣得起，
 *       射出一颗 {@link DividerProjectileEntity}（初速 {@value #SHOT_SPEED}、散布 {@value #SHOT_INACCURACY}，同原作 {@code shoot(…, 0, 1.5, 1)}），
 *       弹丸进了世界才扣 MRU（生成被别的模组取消、或者在加入事件里被直接移除时不扣）；开火声 {@code sound.beam}（音高 2）广播给周围所有人——原作在客户端放，只有射手自己听得到。</li>
 *   <li><b>二分场的目标</b>（{@link #isTarget}，弹丸落地时传给 {@code DividerFieldEntity.spawn}）：敌对生物（{@link Enemy}）、活着、不是旁观者、
 *       不在 {@code ECEntityRules.isSpared} 里（起了名字的、拴着绳的……）、不在 {@code essentialcraft5:divider_immune} 里、
 *       不在 {@code #forge:bosses} 里（{@code general.divider_gun.affect_bosses} 打开时例外）。射手、玩家、村民、动物、宠物都不是目标；
 *       原作的场没存攻击者，什么活物都减半，射手自己站在 3 格内也被减半。场对每个目标先走一次 0 伤害探测（{@code gated} = true），
 *       PvP、领地模组、混合端插件按射手拦得下，见 {@code DividerFieldEntity}。
 *       末影龙打开 {@code affect_bosses} 也不受影响：它按规则算目标，但 0 伤害探测过不去（{@code EnderDragon.hurt} 折算后不到 0.01 就直接返回 false，
 *       不走活体的受伤流程），弹丸也打不中它（本体不可拾取、身体部件不是活体，直接穿过去）。</li>
 *   <li><b>首领模式</b>（第十批 10b，batch10.md §5.7.3，{@link #isBossModeTarget}）：{@code affect_bosses} 关着时，场里的首领（{@code #forge:bosses}，
 *       同样要是敌对生物、不在 {@code isSpared} / {@code divider_immune} 里）不减半，探测放行后改挂 {@code general.divider_gun.boss_mode_ticks} 刻「二分」：
 *       它造成的伤害减半、受到的治疗减半。时长设 0 就回到以前的「首领不受影响」。二分只走 {@code addEffect}，对状态效果免疫的首领照它的设计不吃：
 *       原版凋灵把 {@code addEffect} 覆盖成一律拒绝，挂不上；末影龙照旧探测不过去、什么都不挂。所以原版里首领模式只对能挂上状态效果的模组首领生效。</li>
 * </ul>
 * 没有冷却（照原作）：拉弓 1 秒 + 张开前 2 秒，一个人同时最多两三个场。
 */
public class DividerGunItem extends MRUStaffItem {

    /** 原作 setMaxMRU(20000) */
    public static final int CAPACITY = 20000;
    /** 拉满几刻自动发射（原作 getMaxItemUseDuration 20） */
    public static final int CHARGE_TICKS = 20;
    /** 二分弹初速（原作 shoot 的 velocity 1.5） */
    public static final float SHOT_SPEED = 1.5F;
    /** 二分弹散布（原作 shoot 的 inaccuracy 1） */
    public static final float SHOT_INACCURACY = 1F;
    /** 开火声的音高（原作 playSound(…, 1, 2, false)） */
    public static final float SHOT_PITCH = 2F;
    /** 展示厅停住拉弓时的持续时间（同原版弓的 72000 刻） */
    private static final int SHOWROOM_HOLD_TICKS = 72000;

    /** 展示厅用：拉满以后不自动发射，一直停在拉弓的姿势（第三人称拍拉弓）；开发环境里客户端和服务端在同一个进程，共用这一个开关 */
    private static volatile boolean showroomHoldDraw;
    /** GameTest 用的首领判定覆盖（见 {@link #setTestBossOverride}）；null = 看 {@code #forge:bosses} */
    @Nullable
    private static volatile Predicate<EntityType<?>> testBossOverride;

    public DividerGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, CAPACITY);
    }

    // ------------------------------------------------------------------ 拉弓、发射

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && !ECProtection.isRealPlayer(player)) {
            return InteractionResultHolder.fail(stack);
        }
        int cost = ECHoloWeapons.Config.values().mruPerShot();
        if (!MRUDrain.canAfford(player, stack, cost)) {
            if (!level.isClientSide) {
                tellNoMRU(player, cost);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return showroomHoldDraw ? SHOWROOM_HOLD_TICKS : CHARGE_TICKS;
    }

    /** 展示厅用：hold 为真时拉弓不自动发射、一直停在拉满的姿势；拍完置回 false（见 {@link #showroomHoldDraw}） */
    public static void setShowroomHoldDraw(boolean hold) {
        showroomHoldDraw = hold;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    /** 拉满：服务端发射（客户端什么都不做，等服务端生成的弹丸） */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) {
            fire(player, stack);
        }
        return stack;
    }

    /**
     * 射一发（服务端）：真玩家、扣得起才射；弹丸进了世界才扣 MRU、放开火声。
     *
     * @return 射出去了没有（生成被取消、或者在加入事件里被移除时 false，什么都没扣）
     */
    public boolean fire(Player player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level) || !ECProtection.isRealPlayer(player)) {
            return false;
        }
        int cost = ECHoloWeapons.Config.values().mruPerShot();
        if (!MRUDrain.canAfford(player, stack, cost)) {
            tellNoMRU(player, cost);
            return false;
        }
        DividerProjectileEntity shell = new DividerProjectileEntity(level, player);
        shell.shootFromRotation(player, player.getXRot(), player.getYRot(), 0F, SHOT_SPEED, SHOT_INACCURACY);
        // 有的模组在加入事件里直接 discard 而不取消，addFreshEntity 照样返回 true，弹丸却不会飞：一并算没射出去
        if (!level.addFreshEntity(shell) || shell.isRemoved()) {
            return false;
        }
        if (!MRUDrain.playerUseMRU(player, stack, cost)) {
            shell.discard();
            return false;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ECGuns.BEAM.get(), SoundSource.PLAYERS, 1F, SHOT_PITCH);
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    // ------------------------------------------------------------------ 目标

    /**
     * 在 pos 放下一个玩家射出的二分场（服务端）：枪的减半规则 {@link #isTarget}、首领模式 {@link #isBossModeTarget}（时长读这一刻的配置）、要先探测。
     * 二分弹落地时调，GameTest 也用它。
     */
    @Nullable
    public static DividerFieldEntity spawnField(ServerLevel level, Vec3 pos, int delayTicks, LivingEntity shooter) {
        return DividerFieldEntity.spawn(level, pos, delayTicks, shooter, DividerGunItem::isTarget, true,
                DividerGunItem::isBossModeTarget, ECHoloWeapons.Config.values().bossModeTicks());
    }

    /** 玩家射出的二分场作用于哪些生物（见类注释）；二分场自己另外跳过射手、创造 / 旁观玩家、无敌标记的生物 */
    public static boolean isTarget(LivingEntity living) {
        if (!(living instanceof Enemy) || !living.isAlive() || living.isSpectator() || ECEntityRules.isSpared(living)) {
            return false;
        }
        EntityType<?> type = living.getType();
        if (type.is(ECHoloWeapons.DIVIDER_IMMUNE)) {
            return false;
        }
        return !isBoss(type) || ECHoloWeapons.Config.values().affectBosses();
    }

    /** 算不算首领：{@code #forge:bosses}；GameTest 设了 {@link #setTestBossOverride} 时按覆盖算 */
    private static boolean isBoss(EntityType<?> type) {
        Predicate<EntityType<?>> override = testBossOverride;
        return override != null ? override.test(type) : type.is(Tags.EntityTypes.BOSSES);
    }

    /**
     * GameTest 用：换掉首领判定（null = 恢复看 {@code #forge:bosses}）。原版首领对状态效果免疫（凋灵）或探测不过去（末影龙），
     * 测首领模式要拿一个挂得上效果的生物（僵尸）当首领。只在同一段同步代码里设、测、置回 null。
     */
    public static void setTestBossOverride(@Nullable Predicate<EntityType<?>> override) {
        testBossOverride = override;
    }

    /**
     * 首领模式的对象（见类注释）：{@code affect_bosses} 关着、{@code boss_mode_ticks} &gt; 0 时，敌对的、活着的、不是旁观者、
     * 不在 {@code isSpared} / {@code divider_immune} 里的 {@code #forge:bosses}。和 {@link #isTarget} 互斥。
     */
    public static boolean isBossModeTarget(LivingEntity living) {
        ECHoloWeapons.Config.Values config = ECHoloWeapons.Config.values();
        if (config.affectBosses() || config.bossModeTicks() <= 0) {
            return false;
        }
        if (!(living instanceof Enemy) || !living.isAlive() || living.isSpectator() || ECEntityRules.isSpared(living)) {
            return false;
        }
        EntityType<?> type = living.getType();
        return isBoss(type) && !type.is(ECHoloWeapons.DIVIDER_IMMUNE);
    }

    // ------------------------------------------------------------------ 提示

    @Override
    protected void addUsage(ItemStack stack, List<Component> tooltip) {
        tooltip.add(usage("tooltip.essentialcraft5.divider_gun.use", ECHoloWeapons.Config.values().mruPerShot()));
        tooltip.add(usage("tooltip.essentialcraft5.divider_gun.field", DividerProjectileEntity.FIELD_DELAY / 20,
                OrbitalRemoteItem.number(DividerFieldEntity.RADIUS)));
        tooltip.add(usage("tooltip.essentialcraft5.divider_gun.only_hostile"));
        ECHoloWeapons.Config.Values config = ECHoloWeapons.Config.values();
        if (!config.affectBosses() && config.bossModeTicks() > 0) {
            tooltip.add(usage("tooltip.essentialcraft5.divider_gun.boss_mode", OrbitalRemoteItem.number(config.bossModeTicks() / 20D)));
        }
    }
}
