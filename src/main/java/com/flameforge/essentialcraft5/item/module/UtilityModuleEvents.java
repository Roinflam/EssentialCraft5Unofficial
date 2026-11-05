package com.flameforge.essentialcraft5.item.module;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.MagnetizingStaffItem;
import com.flameforge.essentialcraft5.registry.ECUtilityModules;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 四块生活模块的效果（第十二批，batch12.md §4.7.2；policy §4.15）。是否生效一律只问 {@link ModuleCache#active}；按秒的电费由模块平台
 * {@link ModuleTicker} 每秒统一先算后扣，这里不扣。
 *
 * <h2>吸附（服务端）</h2>
 * 每名开着吸附的玩家每 {@code interval_ticks} 刻一次（{@code radius} 超过 8 时至少 10 刻，见 {@link UtilityModuleConfig.Attraction#effectiveIntervalTicks}；
 * 玩家刻末尾，按 {@code (tickCount + 实体 id) % interval} 错开）：
 * 潜行、旁观、假玩家不吸；调 {@link MagnetizingStaffItem#pulse(net.minecraft.world.level.Level, Player, double, java.util.function.Predicate)}，
 * 水平范围 {@code radius}，掉落物另加「没人扔过」（{@link #unthrown}）。遮挡检查、别人的掉落物、以玩家身份逐格探测领地（预算按玩家、缓存 200 刻）、
 * 每次件数上限全部沿用法杖。实体查询框 (2 × radius + 1) × 13 × (2 × radius + 1)，默认 13³。
 * <p>
 * 原版里设丢出者（thrower）的只有玩家按 Q / 在界面里扔出 / 拖出物品栏 / 创造模式扔出的（{@code Player.drop} 第三个参数为真），
 * 和生物扔出来的：猪灵换出的东西、悦灵送来的、村民送给英雄的礼物和村民之间互相扔的（{@code BehaviorUtils.throwItem}）、狐狸和海豚吐出来的，记的是那只生物。
 * 方块掉落、生物死亡掉落、玩家死亡掉落（{@code Inventory.dropAll}）、背包放不下退回到地上的东西都没有丢出者，这些才吸。自己按 Q 扔掉的不会马上被吸回来。
 *
 * <h2>攀行（服务端，属性自动同步客户端）</h2>
 * 每个玩家刻末尾按 {@code active} 在 {@code ForgeMod.STEP_HEIGHT_ADDITION} 上加 / 摘一个固定 UUID 的临时修饰符（+{@code step_bonus}，数值变了换新的）。
 * 只是查表和比对，每刻做一次：拆模块、关掉、缺电、脱靴子、配置把它停用，下一刻就摘掉；下线时也摘（临时修饰符本来就不存盘）。
 * 死亡重生、从末地回来换了新的玩家对象，新对象上没有修饰符，等这一秒的电付上再加。
 *
 * <h2>稳手（两端）</h2>
 * {@code PlayerEvent.BreakSpeed}：不站在地上 × {@value #STEADY_HAND_FACTOR}、头在水里且没有水下速掘 × {@value #STEADY_HAND_FACTOR}，
 * 正好抵掉 {@code Player.getDigSpeed} 里那两次 ÷ 5。不收电、倍数写死成常量：挖掘速度两端各算一次，客户端不看电量、配置也不一定两端一致，
 * 任何一边算得不一样都会让方块挖完又弹回来。
 *
 * <h2>隐匿（服务端）</h2>
 * {@code LivingEvent.LivingVisibilityEvent}（来自 {@code LivingEntity.getVisibilityPercent}）：被看的是开着隐匿的玩家、看的是 {@link Mob} 时
 * 乘 {@code multiplier}，潜行时改乘 {@code sneak_multiplier}；和原版潜行 × 0.8、隐身 × 0.7、戴对应生物头颅 × 0.5 连乘。
 * 看的是玩家（不是 Mob）时不管，PvP 不受影响。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class UtilityModuleEvents {

    /** 攀行挂在步高属性上的修饰符（固定 UUID，加 / 摘都认它） */
    public static final UUID STRIDE_MODIFIER = UUID.fromString("6f1d3a52-9c7e-4b08-a3d1-5e2b7c904f16");
    private static final String STRIDE_MODIFIER_NAME = "essentialcraft5 stride module";
    /** 稳手抵掉的倍数：{@code Player.getDigSpeed} 里悬空、头在水里各 ÷ 5 */
    public static final float STEADY_HAND_FACTOR = 5F;

    private UtilityModuleEvents() {
    }

    // ------------------------------------------------------------------ 玩家刻：吸附、攀行

    @SubscribeEvent
    public static void onUtilityModulePlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        updateStride(player);
        int interval = UtilityModuleConfig.attraction().effectiveIntervalTicks();
        if (Math.floorMod(player.tickCount + player.getId(), interval) == 0) {
            attract(player);
        }
    }

    @SubscribeEvent
    public static void onUtilityModuleLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        removeStride(event.getEntity());
    }

    // ------------------------------------------------------------------ 吸附

    /**
     * 推一次（不看节奏，GameTest 直接调）：吸附生效、真玩家、活着、没潜行、不是旁观才推。
     *
     * @return 这次推了多少；没推（条件不满足）返回 null
     */
    @Nullable
    public static MagnetizingStaffItem.Pulse attract(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isShiftKeyDown() || !ECProtection.isRealPlayer(player)
                || !ModuleCache.active(player, ECUtilityModules.ATTRACTION)) {
            return null;
        }
        return MagnetizingStaffItem.pulse(player.level(), player, UtilityModuleConfig.attraction().radius(), UtilityModuleEvents::unthrown);
    }

    /** 没人扔过的掉落物（方块掉落、生物掉落）；见类注释 */
    public static boolean unthrown(ItemEntity item) {
        return ECItemEntities.thrower(item) == null;
    }

    // ------------------------------------------------------------------ 攀行

    /** 按攀行生不生效加 / 摘步高修饰符（每刻调；GameTest 直接调） */
    public static void updateStride(Player player) {
        AttributeInstance attribute = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (attribute == null) {
            return;
        }
        AttributeModifier current = attribute.getModifier(STRIDE_MODIFIER);
        double bonus = UtilityModuleConfig.stride().stepBonus();
        if (bonus > 0D && ModuleCache.active(player, ECUtilityModules.STRIDE)) {
            if (current != null && current.getAmount() == bonus) {
                return;
            }
            if (current != null) {
                attribute.removeModifier(STRIDE_MODIFIER);
            }
            attribute.addTransientModifier(new AttributeModifier(STRIDE_MODIFIER, STRIDE_MODIFIER_NAME, bonus, AttributeModifier.Operation.ADDITION));
        } else if (current != null) {
            attribute.removeModifier(STRIDE_MODIFIER);
        }
    }

    /** 摘掉攀行的步高修饰符（没有就什么都不做） */
    public static void removeStride(Player player) {
        AttributeInstance attribute = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (attribute != null && attribute.getModifier(STRIDE_MODIFIER) != null) {
            attribute.removeModifier(STRIDE_MODIFIER);
        }
    }

    // ------------------------------------------------------------------ 稳手

    /** 两端都监听（客户端预测挖掘进度、服务端判挖完，两边要一样） */
    @SubscribeEvent
    public static void onUtilityModuleBreakSpeed(PlayerEvent.BreakSpeed event) {
        float factor = steadyHandFactor(event.getEntity());
        if (factor != 1F) {
            event.setNewSpeed(event.getNewSpeed() * factor);
        }
    }

    /** 稳手给的倍数：没生效 1；悬空 × 5、头在水里且没有水下速掘再 × 5（判法照 {@code Player.getDigSpeed}） */
    public static float steadyHandFactor(Player player) {
        if (!ModuleCache.active(player, ECUtilityModules.STEADY_HAND)) {
            return 1F;
        }
        float factor = 1F;
        if (!player.onGround()) {
            factor *= STEADY_HAND_FACTOR;
        }
        if (player.isEyeInFluid(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)) {
            factor *= STEADY_HAND_FACTOR;
        }
        return factor;
    }

    // ------------------------------------------------------------------ 隐匿

    @SubscribeEvent
    public static void onUtilityModuleVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide && event.getLookingEntity() instanceof Mob) {
            double multiplier = veilMultiplier(player);
            if (multiplier != 1D) {
                event.modifyVisibility(multiplier);
            }
        }
    }

    /** 隐匿给的倍数：没生效 1；潜行（{@code isDiscrete}，和原版 × 0.8 那条同一个判断）{@code sneak_multiplier}，否则 {@code multiplier} */
    public static double veilMultiplier(Player player) {
        if (!ModuleCache.active(player, ECUtilityModules.VEIL)) {
            return 1D;
        }
        UtilityModuleConfig.Veil cfg = UtilityModuleConfig.veil();
        return player.isDiscrete() ? cfg.sneakMultiplier() : cfg.multiplier();
    }
}
