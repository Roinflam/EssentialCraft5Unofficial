package com.flameforge.essentialcraft5.compat.curio;

import com.google.common.collect.Multimap;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Tuple;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * 遮罩视图：被饰品封禁场压制的人，Curios 让饰品生效的那几处（tick、末影人面具、经验修补、挖矿经验、原版钩子）拿到的是这个，
 * 不是真的饰品栏。功能槽里不在豁免标签（{@code essentialcraft5:curio_seal_exempt}）里的东西读出来是空的，别的操作全转给真的饰品栏。
 * <p>
 * 只实现 Curios 公开接口（编译只依赖 api）。默认方法也显式转发：真的实现可能覆盖了它们。
 * <b>不转发</b>的只有 {@link #removeSlotModifiers} / {@link #removeSlotModifier}：Curios 看到「有 → 空」时会顺手去掉饰品给的额外槽位，
 * 槽一缩，多出来的物品就被挤进「失效物品」退回背包、背包满了掉地上——压制期间额外槽位一律保留，增减由 {@link SealSlotKeeper} 按真物品对账。
 * <p>
 * 视图对象只活在一次处理函数调用里（每刻新建），不存。
 */
// 已标为移除的接口方法（槽位锁定、附魔加成那几个）真的实现还在用，照样转发
@SuppressWarnings({"removal", "deprecation"})
public final class SealedCuriosView implements ICuriosItemHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("essentialcraft5/curio_seal");
    private static volatile boolean warnedWrite;

    private final ICuriosItemHandler real;
    @Nullable
    private Map<String, ICurioStacksHandler> wrapped;

    public SealedCuriosView(ICuriosItemHandler real) {
        this.real = real;
    }

    /** 真的饰品栏 */
    public ICuriosItemHandler real() {
        return real;
    }

    /** 这件物品在视图里读不读得到（空栈、豁免标签里的照常读得到） */
    static boolean visible(ItemStack stack) {
        return stack.isEmpty() || CurioSeal.isExempt(stack);
    }

    private ICurioStacksHandler wrap(ICurioStacksHandler handler) {
        return handler instanceof SealedStacksView ? handler : getCurios().getOrDefault(handler.getIdentifier(), new SealedStacksView(handler));
    }

    // ------------------------------------------------------------------ 遮罩的部分

    @Override
    public Map<String, ICurioStacksHandler> getCurios() {
        Map<String, ICurioStacksHandler> map = wrapped;
        if (map == null) {
            map = new LinkedHashMap<>();
            for (Map.Entry<String, ICurioStacksHandler> entry : real.getCurios().entrySet()) {
                map.put(entry.getKey(), new SealedStacksView(entry.getValue()));
            }
            wrapped = map;
        }
        return map;
    }

    @Override
    public Optional<ICurioStacksHandler> getStacksHandler(String identifier) {
        return real.getStacksHandler(identifier).map(this::wrap);
    }

    @Override
    public IItemHandlerModifiable getEquippedCurios() {
        Collection<ICurioStacksHandler> handlers = getCurios().values();
        IItemHandlerModifiable[] parts = new IItemHandlerModifiable[handlers.size()];
        int i = 0;
        for (ICurioStacksHandler handler : handlers) {
            parts[i++] = handler.getStacks();
        }
        return new CombinedInvWrapper(parts);
    }

    @Override
    public void removeSlotModifiers(Multimap<String, AttributeModifier> modifiers) {
        // 压制期间保留饰品给的额外槽位（见类注释），由 SealSlotKeeper 对账
    }

    @Override
    public void removeSlotModifier(String id, UUID uuid) {
        // 同上
    }

    // ------------------------------------------------------------------ 查询：真的饰品栏的查询方法在返回处已经按压制过滤

    @Override
    public boolean isEquipped(Item item) {
        return real.isEquipped(item);
    }

    @Override
    public boolean isEquipped(Predicate<ItemStack> filter) {
        return real.isEquipped(filter);
    }

    @Override
    public Optional<SlotResult> findFirstCurio(Item item) {
        return real.findFirstCurio(item);
    }

    @Override
    public Optional<SlotResult> findFirstCurio(Predicate<ItemStack> filter) {
        return real.findFirstCurio(filter);
    }

    @Override
    public List<SlotResult> findCurios(Item item) {
        return real.findCurios(item);
    }

    @Override
    public List<SlotResult> findCurios(Predicate<ItemStack> filter) {
        return real.findCurios(filter);
    }

    @Override
    public List<SlotResult> findCurios(String... identifiers) {
        return real.findCurios(identifiers);
    }

    @Override
    public Optional<SlotResult> findCurio(String identifier, int index) {
        return real.findCurio(identifier, index);
    }

    @Override
    public int getFortuneLevel(@Nullable LootContext lootContext) {
        return real.getFortuneLevel(lootContext);
    }

    @Override
    public int getLootingLevel(DamageSource source, LivingEntity target, int baseLooting) {
        return real.getLootingLevel(source, target, baseLooting);
    }

    // ------------------------------------------------------------------ 其余全部转发

    @Override
    public void setCurios(Map<String, ICurioStacksHandler> map) {
        real.setCurios(map);
        wrapped = null;
    }

    @Override
    public int getSlots() {
        return real.getSlots();
    }

    @Override
    public int getVisibleSlots() {
        return real.getVisibleSlots();
    }

    @Override
    public void reset() {
        real.reset();
        wrapped = null;
    }

    @Override
    public void setEquippedCurio(String identifier, int index, ItemStack stack) {
        real.setEquippedCurio(identifier, index, stack);
    }

    @Override
    public LivingEntity getWearer() {
        return real.getWearer();
    }

    @Override
    public void loseInvalidStack(ItemStack stack) {
        real.loseInvalidStack(stack);
    }

    @Override
    public void handleInvalidStacks() {
        real.handleInvalidStacks();
    }

    @Override
    public ListTag saveInventory(boolean clear) {
        return real.saveInventory(clear);
    }

    @Override
    public ListTag saveInventory(boolean clear, Predicate<ItemStack> filter) {
        return real.saveInventory(clear, filter);
    }

    @Override
    public ListTag saveInventory(boolean clear, BiPredicate<ItemStack, SlotContext> filter) {
        return real.saveInventory(clear, filter);
    }

    @Override
    public void loadInventory(ListTag data) {
        real.loadInventory(data);
    }

    @Override
    public Set<ICurioStacksHandler> getUpdatingInventories() {
        return real.getUpdatingInventories();
    }

    @Override
    public void addTransientSlotModifier(String id, UUID uuid, String name, double amount, AttributeModifier.Operation operation) {
        real.addTransientSlotModifier(id, uuid, name, amount, operation);
    }

    @Override
    public void addTransientSlotModifiers(Multimap<String, AttributeModifier> modifiers) {
        real.addTransientSlotModifiers(modifiers);
    }

    @Override
    public void addPermanentSlotModifier(String id, UUID uuid, String name, double amount, AttributeModifier.Operation operation) {
        real.addPermanentSlotModifier(id, uuid, name, amount, operation);
    }

    @Override
    public void addPermanentSlotModifiers(Multimap<String, AttributeModifier> modifiers) {
        real.addPermanentSlotModifiers(modifiers);
    }

    @Override
    public void clearSlotModifiers() {
        real.clearSlotModifiers();
    }

    @Override
    public Multimap<String, AttributeModifier> getModifiers() {
        return real.getModifiers();
    }

    @Override
    public Tag writeTag() {
        return real.writeTag();
    }

    @Override
    public void readTag(Tag tag) {
        real.readTag(tag);
        wrapped = null;
    }

    @Override
    public void clearCachedSlotModifiers() {
        real.clearCachedSlotModifiers();
    }

    @Override
    public Set<String> getLockedSlots() {
        return real.getLockedSlots();
    }

    @Override
    public void unlockSlotType(String identifier, int amount, boolean visible, boolean cosmetic) {
        real.unlockSlotType(identifier, amount, visible, cosmetic);
        wrapped = null;
    }

    @Override
    public void lockSlotType(String identifier) {
        real.lockSlotType(identifier);
        wrapped = null;
    }

    @Override
    public void processSlots() {
        real.processSlots();
    }

    @Override
    public int getFortuneBonus() {
        return real.getFortuneBonus();
    }

    @Override
    public int getLootingBonus() {
        return real.getLootingBonus();
    }

    @Override
    public void setEnchantmentBonuses(Tuple<Integer, Integer> fortuneAndLooting) {
        real.setEnchantmentBonuses(fortuneAndLooting);
    }

    @Override
    public void growSlotType(String identifier, int amount) {
        real.growSlotType(identifier, amount);
    }

    @Override
    public void shrinkSlotType(String identifier, int amount) {
        real.shrinkSlotType(identifier, amount);
    }

    // ================================================================== 一种槽位

    /** 一种槽位（戒指、项链……）的视图：功能槽遮罩，装饰槽、修饰符、渲染开关全转发 */
    public static final class SealedStacksView implements ICurioStacksHandler {

        private final ICurioStacksHandler real;
        @Nullable
        private SealedDynamicView stacks;

        SealedStacksView(ICurioStacksHandler real) {
            this.real = real;
        }

        @Override
        public IDynamicStackHandler getStacks() {
            IDynamicStackHandler current = real.getStacks();
            SealedDynamicView view = stacks;
            if (view == null || view.real != current) {
                // 真的槽位处理器换了对象（扩缩容时可能重建）就跟着换
                view = new SealedDynamicView(current);
                stacks = view;
            }
            return view;
        }

        @Override
        public IDynamicStackHandler getCosmeticStacks() {
            // 装饰槽本来就不生效，不遮
            return real.getCosmeticStacks();
        }

        @Override
        public NonNullList<Boolean> getRenders() {
            return real.getRenders();
        }

        @Override
        public boolean canToggleRendering() {
            return real.canToggleRendering();
        }

        @Override
        public ICurio.DropRule getDropRule() {
            return real.getDropRule();
        }

        @Override
        public int getSlots() {
            return real.getSlots();
        }

        @Override
        public boolean isVisible() {
            return real.isVisible();
        }

        @Override
        public boolean hasCosmetic() {
            return real.hasCosmetic();
        }

        @Override
        public CompoundTag serializeNBT() {
            return real.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            real.deserializeNBT(nbt);
        }

        @Override
        public String getIdentifier() {
            return real.getIdentifier();
        }

        @Override
        public Map<UUID, AttributeModifier> getModifiers() {
            return real.getModifiers();
        }

        @Override
        public Set<AttributeModifier> getPermanentModifiers() {
            return real.getPermanentModifiers();
        }

        @Override
        public Set<AttributeModifier> getCachedModifiers() {
            return real.getCachedModifiers();
        }

        @Override
        public Collection<AttributeModifier> getModifiersByOperation(AttributeModifier.Operation operation) {
            return real.getModifiersByOperation(operation);
        }

        @Override
        public void addTransientModifier(AttributeModifier modifier) {
            real.addTransientModifier(modifier);
        }

        @Override
        public void addPermanentModifier(AttributeModifier modifier) {
            real.addPermanentModifier(modifier);
        }

        @Override
        public void removeModifier(UUID uuid) {
            real.removeModifier(uuid);
        }

        @Override
        public void clearModifiers() {
            real.clearModifiers();
        }

        @Override
        public void clearCachedModifiers() {
            real.clearCachedModifiers();
        }

        @Override
        public void copyModifiers(ICurioStacksHandler other) {
            real.copyModifiers(other instanceof SealedStacksView view ? view.real : other);
        }

        @Override
        public void update() {
            real.update();
        }

        @Override
        public CompoundTag getSyncTag() {
            return real.getSyncTag();
        }

        @Override
        public void applySyncTag(CompoundTag tag) {
            real.applySyncTag(tag);
        }

        @Override
        public int getSizeShift() {
            return real.getSizeShift();
        }

        @Override
        public void grow(int amount) {
            real.grow(amount);
        }

        @Override
        public void shrink(int amount) {
            real.shrink(amount);
        }
    }

    // ================================================================== 功能槽

    /**
     * 功能槽的视图：被压制的格子读出来是空的、抽不出东西；写保护：对「被压制、真格非空」的格子写空栈直接忽略
     * （防以后的 Curios 把读到的空写回去，那就真丢了）。上一刻的物品、容量、校验、存读档全转发。
     */
    public static final class SealedDynamicView implements IDynamicStackHandler {

        final IDynamicStackHandler real;

        SealedDynamicView(IDynamicStackHandler real) {
            this.real = real;
        }

        /** 这一格在视图里是不是被遮住了（真格非空、不豁免） */
        boolean masked(int slot) {
            return slot >= 0 && slot < real.getSlots() && !visible(real.getStackInSlot(slot));
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            ItemStack stack = real.getStackInSlot(slot);
            return visible(stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            if (stack.isEmpty() && masked(slot)) {
                if (!warnedWrite) {
                    warnedWrite = true;
                    LOGGER.warn("Ignored a write of an empty stack into a sealed curio slot (slot {}); the item stays where it is.", slot);
                }
                return;
            }
            real.setStackInSlot(slot, stack);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return masked(slot) ? ItemStack.EMPTY : real.extractItem(slot, amount, simulate);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return real.insertItem(slot, stack, simulate);
        }

        @Override
        public void setPreviousStackInSlot(int slot, @NotNull ItemStack stack) {
            real.setPreviousStackInSlot(slot, stack);
        }

        @Override
        public ItemStack getPreviousStackInSlot(int slot) {
            return real.getPreviousStackInSlot(slot);
        }

        @Override
        public int getSlots() {
            return real.getSlots();
        }

        @Override
        public int getSlotLimit(int slot) {
            return real.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return real.isItemValid(slot, stack);
        }

        @Override
        public void grow(int amount) {
            real.grow(amount);
        }

        @Override
        public void shrink(int amount) {
            real.shrink(amount);
        }

        @Override
        public CompoundTag serializeNBT() {
            return real.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            real.deserializeNBT(nbt);
        }
    }

    /** 列出视图里被遮住的功能槽（测试、调试用）：槽位类型 → 序号 */
    List<String> maskedSlots() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, ICurioStacksHandler> entry : getCurios().entrySet()) {
            SealedDynamicView view = (SealedDynamicView) entry.getValue().getStacks();
            for (int i = 0; i < view.getSlots(); i++) {
                if (view.masked(i)) {
                    out.add(entry.getKey() + "#" + i);
                }
            }
        }
        return out;
    }
}
