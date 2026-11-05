package com.flameforge.essentialcraft5.compat.curio;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.network.S2CCurioSealSlotPacket;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Multimap;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotAttribute;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 槽位数修饰符的对账（服务端，tools/plan/batch10.md §4.1.5），顺带补发压制期间的单格同步。
 * <p>
 * 饰品给的额外槽位（例：腰带多给两个戒指槽）如果跟着压制去掉，Curios 会缩槽，多出来的物品被挤进「失效物品」退回背包、背包满了掉地上——
 * PvP 里等于把装备扔给对手。所以遮罩视图不转发「去掉槽位修饰符」，改由这里按<b>真物品</b>对账：
 * 每格记下上次对账时的真物品，变了（{@link ItemStack#matches} 不等）就把旧物品给的、新物品不再给或者数量 / 运算变了的槽位修饰符去掉，
 * 再按新物品补上（只补槽位处理器里还没有这个 UUID 的，免得每刻触发同步）。第一次对账（压制开始那一刻）对每格真物品「确保已加上」。
 * 堵住「戴腰带 → 被压制 → 摘腰带 → 解除后白留两个槽」，也堵住「被压制期间按 NBT 降级的腰带，解除后还按旧数量给槽」。
 * <p>
 * 单格同步：压制期间 Curios 比对的是遮罩视图（被压制的格子一直是空），真物品变了它看不见、不发单格包，追踪这名玩家的人客户端里
 * 那件饰品就一直挂着（摘掉的照画、换上的看不到），解除时真槽和「上一刻」都空也不补。所以这里发现某格真物品变了，就发
 * {@link S2CCurioSealSlotPacket} 给本人和看得到他的人；新旧两件有一件在豁免标签里的不发（遮罩视图照常显示豁免物品，Curios 自己会发）。
 * <p>
 * {@code LivingTickEvent} HIGH，排在 Curios 的 tick（NORMAL）前面；只对被压制的玩家。解除时丢掉记录，Curios 的「空 → 真」会再加一遍（同 UUID 覆盖，幂等）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class SealSlotKeeper {

    /**
     * 玩家 → 槽位类型 → 每格上次对账的真物品（拷贝）；只在服务端线程读写。
     * 弱键按对象比（Guava 的 weakKeys 用 ==）：Entity 的 equals 按实体 id，重生后的新对象和旧的 id 相同，不能让新对象接着用旧对象的记录
     */
    private static final Map<ServerPlayer, Map<String, ItemStack[]>> RECORDS = new MapMaker().weakKeys().makeMap();

    private SealSlotKeeper() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSealSlotKeeperTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!CurioSeal.isSealed(player)) {
            if (!RECORDS.isEmpty()) {
                RECORDS.remove(player);
            }
            return;
        }
        reconcile(player);
    }

    /** 对账一次（GameTest 可直接调） */
    public static void reconcile(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            Map<String, ItemStack[]> record = RECORDS.computeIfAbsent(player, key -> new HashMap<>());
            for (Map.Entry<String, ICurioStacksHandler> entry : inventory.getCurios().entrySet()) {
                String id = entry.getKey();
                IDynamicStackHandler stacks = entry.getValue().getStacks();
                NonNullList<Boolean> renders = entry.getValue().getRenders();
                int slots = stacks.getSlots();
                ItemStack[] before = record.get(id);
                if (before == null || before.length != slots) {
                    before = before == null ? new ItemStack[slots] : Arrays.copyOf(before, slots);
                    record.put(id, before);
                }
                for (int i = 0; i < slots; i++) {
                    ItemStack now = stacks.getStackInSlot(i);
                    ItemStack old = before[i];
                    if (old != null && ItemStack.matches(old, now)) {
                        continue;
                    }
                    SlotContext context = new SlotContext(id, player, i, false, renders.size() > i && renders.get(i));
                    Multimap<String, AttributeModifier> wanted = now.isEmpty() ? HashMultimap.create() : slotModifiers(context, now);
                    if (old != null && !old.isEmpty()) {
                        // 数量、运算都没变的不去：先去后加会白白 flagUpdate、多发一次槽位同步。同 UUID 但值变了的必须先去再加：
                        // Curios 按 UUID 判修饰符相等（AttributeModifier.equals 只比 id），直接加只换掉 getModifiers() 里那份，
                        // 算槽数用的那张多重表留着旧对象，槽数一直按旧数量算
                        Multimap<String, AttributeModifier> gone = HashMultimap.create();
                        for (Map.Entry<String, AttributeModifier> mod : slotModifiers(context, old).entries()) {
                            AttributeModifier same = find(wanted, mod.getKey(), mod.getValue().getId());
                            if (same == null || same.getAmount() != mod.getValue().getAmount()
                                    || same.getOperation() != mod.getValue().getOperation()) {
                                gone.put(mod.getKey(), mod.getValue());
                            }
                        }
                        if (!gone.isEmpty()) {
                            inventory.removeSlotModifiers(gone);
                        }
                    }
                    // 紧接着补：两步之间不调 getStacks / getSlots（它们会按「只去不加」的中间状态重算槽数）
                    ensure(inventory, wanted);
                    if (old != null && !CurioSeal.isExempt(old) && !CurioSeal.isExempt(now)) {
                        S2CCurioSealSlotPacket.send(player, id, i, now);
                    }
                    before[i] = now.copy();
                }
            }
        });
    }

    /** 这件物品放在这一格时给的槽位修饰符（槽位类型 → 修饰符） */
    static Multimap<String, AttributeModifier> slotModifiers(SlotContext context, ItemStack stack) {
        Multimap<String, AttributeModifier> slots = HashMultimap.create();
        UUID uuid = CuriosApi.getSlotUuid(context);
        Multimap<Attribute, AttributeModifier> map = CuriosApi.getAttributeModifiers(context, uuid, stack);
        for (Map.Entry<Attribute, AttributeModifier> entry : map.entries()) {
            if (entry.getKey() instanceof SlotAttribute slot) {
                slots.put(slot.getIdentifier(), entry.getValue());
            }
        }
        return slots;
    }

    @Nullable
    private static AttributeModifier find(Multimap<String, AttributeModifier> mods, String identifier, UUID id) {
        for (AttributeModifier mod : mods.get(identifier)) {
            if (mod.getId().equals(id)) {
                return mod;
            }
        }
        return null;
    }

    /** 只补槽位处理器里还没有这个 UUID 的 */
    private static void ensure(ICuriosItemHandler inventory, Multimap<String, AttributeModifier> wanted) {
        if (wanted.isEmpty()) {
            return;
        }
        Multimap<String, AttributeModifier> missing = HashMultimap.create();
        for (Map.Entry<String, AttributeModifier> entry : wanted.entries()) {
            Optional<ICurioStacksHandler> handler = inventory.getStacksHandler(entry.getKey());
            if (handler.isEmpty() || !handler.get().getModifiers().containsKey(entry.getValue().getId())) {
                missing.put(entry.getKey(), entry.getValue());
            }
        }
        if (!missing.isEmpty()) {
            inventory.addTransientSlotModifiers(missing);
        }
    }

    /** 解除、下线时丢掉记录 */
    static void forget(ServerPlayer player) {
        RECORDS.remove(player);
    }

    static void clear() {
        RECORDS.clear();
    }
}
