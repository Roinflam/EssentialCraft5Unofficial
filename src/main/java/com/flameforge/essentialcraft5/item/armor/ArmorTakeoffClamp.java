package com.flameforge.essentialcraft5.item.armor;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.VoidArmorItem;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 脱下本模组的盔甲以后，下一刻把当前生命夹回上限。只在服务端。
 * <p>
 * 同调家族里的盔甲（魔力防护、风之护甲、审判庭、宅男套装）每阶每件 +{@code health_per_stage} 最大生命
 * （{@code item/attunement/AttunementBonuses}），魔力防护、审判庭胸甲、宅男套装自己还按配置加生命（{@link ConfiguredArmorStats}）。
 * 原版换装时按 UUID 去掉修饰符，最大生命降了，当前生命却不跟着夹，要等到下一次挨打或回血才夹：满血时脱下一件，白得一下缓冲。
 * 只认本模组的盔甲（别的模组的盔甲照原版），不去分哪一件加没加生命：没加的那件脱下以后生命本来就不超上限，夹一下什么都不改；
 * 也不靠同调家族的标签认，数据包把哪件挪出家族，它自己按配置加的那份照样夹。
 * <p>
 * 「脱下」= 换成别的物品、空手，或者换成<b>同一种但阶更低</b>的一件（{@link #samePiece}）。同一件只是 NBT 变了（扣电、充电、同调度写盘、阶戳、升阶），
 * 原版照样当成换装、发事件，这些不算：修饰符同一刻删了又按同一个 UUID 加回来，生命上限没变。
 * 第十三批 13b 以前魔力防护、宅男套装各有一份只比「是不是同一种物品」的判断，换成同种低阶件时不夹，风之护甲、审判庭没有这一步，现在统一在这里。
 * 虚空套装的那一份在 {@code VoidArmorEvents} 里，判定也用 {@link #samePiece}，这里跳过它，不重复记。
 * <p>
 * 换装事件里只记下这个生物，下一刻的 {@code LivingTickEvent} 再夹：事件发出时修饰符还没换完（原版先发事件、再按旧物品去掉修饰符、按新物品加上）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ArmorTakeoffClamp {

    /** 刚脱下、下一刻要夹生命的生物（弱引用；只在服务端线程读写） */
    private static final Set<LivingEntity> PENDING = Collections.newSetFromMap(new WeakHashMap<>());

    private ArmorTakeoffClamp() {
    }

    /**
     * 换装前后是不是「同一件」：同一种物品、阶没往下掉。扣电、同调度写盘、阶戳、升阶都只改 NBT，这些不算脱下；
     * 换成同种低阶的一件时同调加的生命少了，算脱下。
     */
    public static boolean samePiece(ItemStack from, ItemStack to) {
        return to.is(from.getItem()) && Attunement.stage(to) >= Attunement.stage(from);
    }

    /** 这里管的：本模组的盔甲（虚空套装除外，见类注释） */
    static boolean handles(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem) || stack.getItem() instanceof VoidArmorItem) {
            return false;
        }
        return EssentialCraft5.MODID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    @SubscribeEvent
    public static void onArmorTakeoffChanged(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.getSlot().getType() != EquipmentSlot.Type.ARMOR) {
            return;
        }
        ItemStack from = event.getFrom();
        if (handles(from) && !samePiece(from, event.getTo())) {
            PENDING.add(entity);
        }
    }

    @SubscribeEvent
    public static void onArmorTakeoffClampTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        // 先判断客户端：单人时客户端线程也发这个事件，WeakHashMap 的 isEmpty 会清过期的键（改内部表），客户端线程一下都不能碰
        if (entity.level().isClientSide || PENDING.isEmpty() || !PENDING.remove(entity)) {
            return;
        }
        if (entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    @SubscribeEvent
    public static void onArmorTakeoffServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** GameTest 用：这个生物在不在「等夹生命」的名单里 */
    public static boolean pendingForTest(LivingEntity entity) {
        return PENDING.contains(entity);
    }
}
