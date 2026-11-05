package com.flameforge.essentialcraft5.item.module;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宅男套装模块的登记表和盔甲上的模块数据（第十批 10c-F「模块平台」，tools/plan/batch10.md §4.3）。
 *
 * <h2>登记</h2>
 * 每种模块一个 {@link ModuleType}，在模块注册类的静态字段里 {@link #register}（模组构造阶段，之后只读）。id 就是模块物品的注册名。
 *
 * <h2>存在哪</h2>
 * 盔甲物品 NBT 根上的 {@value #TAG}：{@code [{id:"essentialcraft5:rewind_module", on:1b}, …]}，按装上的先后排，下标就是格子号（终端包里的 {@code slot}）；
 * 拆掉一块后面的往前挪。模块物品本身不带 NBT 进去，拆下来给一块新的。认不出的 id（模块被删掉了）照留，不生效，能拆（拆了什么都不给）。
 *
 * <h2>装 / 拆 / 开关</h2>
 * 装：工作台特殊配方 {@code recipe/ArmorModuleRecipe}（序列化器 {@code essentialcraft5:armor_module_install}），一件能装模块的盔甲 + 一块模块，
 * 条件见 {@link #check}（被 {@code general.computer_modules.disabled} 停用的装不上，已经装着的照样能拆）；拆、开关：宅男终端「模块」页 → {@code C2SComputerTerminalPacket} 的 {@code MODULE_REMOVE} / {@code MODULE_TOGGLE}
 * → {@link ModuleTerminal}（拆 = 先删 NBT、同一刻把模块物品给玩家，不会复制）。
 * <p>
 * 生不生效、耗电、冷却分别在 {@link ModuleCache}、{@link ModuleTicker}、{@link ModuleCooldowns}。
 */
public final class ArmorModules {

    /** 盔甲物品 NBT 里的键 */
    public static final String TAG = "essentialcraft5:modules";
    private static final String KEY_ID = "id";
    private static final String KEY_ON = "on";
    /** NBT 里的 id 解析不了时用的名字（照样列出来、能拆，拆了什么都不给） */
    public static final ResourceLocation UNKNOWN_ID = ResourceLocation.fromNamespaceAndPath("essentialcraft5", "unknown_module");

    private static final Map<ResourceLocation, ModuleType> TYPES = new LinkedHashMap<>();
    /** GameTest 用：把一样现成的物品临时当成模块（运行中不能再造新的物品，模块物品要等各模块注册以后才有） */
    private static final Map<Item, ModuleType> TEST_ITEMS = new LinkedHashMap<>();

    /** 装不装得上（配方的判断）。只往末尾加 */
    public enum InstallCheck {
        /** 能装 */
        OK,
        /** 这件不是能装模块的盔甲 */
        NOT_MODULAR,
        /** 那块不是登记过的模块 */
        NOT_MODULE,
        /** 这块模块不能装在这个部位 */
        WRONG_PART,
        /** 这件的模块槽满了（或者这个部位没有槽） */
        FULL,
        /** 这件上已经有同种模块 */
        DUPLICATE,
        /** 盔甲叠了不止一件 */
        STACKED,
        /** 这块模块被 {@code general.computer_modules.disabled} 停用了（装上也是白占一格；已经装着的照样能拆） */
        DISABLED
    }

    /**
     * 盔甲上的一格。
     *
     * @param slot 格子号（从 0 起，按装上的先后）
     * @param id   模块注册名
     * @param on   开着（不能开关的模块恒为 true）
     */
    public record Installed(int slot, ResourceLocation id, boolean on) {

        /** 对应的模块；认不出时 null */
        @Nullable
        public ModuleType type() {
            return get(id);
        }
    }

    private ArmorModules() {
    }

    // ------------------------------------------------------------------ 登记

    /** 登记一种模块（模组构造阶段调；同一个 id 登记两次直接报错） */
    public static synchronized ModuleType register(ModuleType type) {
        if (TYPES.putIfAbsent(type.id(), type) != null) {
            throw new IllegalStateException("模块登记重复：" + type.id());
        }
        return type;
    }

    @Nullable
    public static ModuleType get(@Nullable ResourceLocation id) {
        return id == null ? null : TYPES.get(id);
    }

    /** 所有登记过的模块（按登记顺序） */
    public static Collection<ModuleType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    /** 这个物品是哪种模块（{@link ArmorModuleItem}，且它的种类登记过）；不是模块为 null */
    @Nullable
    public static ModuleType moduleOf(ItemStack stack) {
        if (stack.getItem() instanceof ArmorModuleItem item) {
            return get(item.moduleType().id()) == item.moduleType() ? item.moduleType() : null;
        }
        return TEST_ITEMS.isEmpty() ? null : TEST_ITEMS.get(stack.getItem());
    }

    /**
     * GameTest 用：临时登记一种模块，并把 item 当成它的模块物品（type 的 id 必须是 item 的注册名，拆下来按 id 还的就是它）。
     * 在 {@code @BeforeBatch} 登记、{@code @AfterBatch} 用 {@link #unregisterForTest} 撤掉（同一个对象重复登记不报错）。
     */
    public static synchronized void registerForTest(ModuleType type, Item item) {
        ModuleType existing = TYPES.get(type.id());
        if (existing != null && existing != type) {
            throw new IllegalStateException("测试模块和已登记的模块撞名：" + type.id());
        }
        TYPES.put(type.id(), type);
        TEST_ITEMS.put(item, type);
    }

    /** GameTest 用：撤掉 {@link #registerForTest} 登记的模块 */
    public static synchronized void unregisterForTest(ModuleType type) {
        TYPES.remove(type.id(), type);
        TEST_ITEMS.values().removeIf(type::equals);
    }

    // ------------------------------------------------------------------ 读

    /** 这件的部位；不是盔甲时 null */
    @Nullable
    public static ArmorItem.Type partOf(ItemStack armor) {
        return armor.getItem() instanceof ArmorItem item ? item.getType() : null;
    }

    /** 这件能装几个模块（不是能装模块的盔甲为 0；宅男套装按这一件的同调阶加，batch13.md §4.4） */
    public static int slots(ItemStack armor) {
        if (armor.getItem() instanceof IModularArmor modular && armor.getItem() instanceof ArmorItem) {
            return Math.max(0, Math.min(ModuleConfig.MAX_SLOTS, modular.moduleSlots(armor)));
        }
        return 0;
    }

    /** 这件上装着的模块（按格子号；没有为空表）。id 解析不了的条目记成 {@link #UNKNOWN_ID}（认不出、能拆） */
    public static List<Installed> installed(ItemStack armor) {
        CompoundTag tag = armor.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = tag.getList(TAG, Tag.TAG_COMPOUND);
        List<Installed> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(KEY_ID));
            if (id == null) {
                id = UNKNOWN_ID;
            }
            ModuleType type = get(id);
            boolean on = !entry.contains(KEY_ON) || entry.getBoolean(KEY_ON) || (type != null && !type.toggleable());
            out.add(new Installed(i, id, on));
        }
        return out;
    }

    /** 这件上有没有这种模块（不管开没开、在不在格数以内） */
    public static boolean has(ItemStack armor, ModuleType type) {
        for (Installed installed : installed(armor)) {
            if (installed.id().equals(type.id())) {
                return true;
            }
        }
        return false;
    }

    /** 列表里的条目数（含认不出的） */
    public static int count(ItemStack armor) {
        CompoundTag tag = armor.getTag();
        return tag == null || !tag.contains(TAG, Tag.TAG_LIST) ? 0 : tag.getList(TAG, Tag.TAG_COMPOUND).size();
    }

    // ------------------------------------------------------------------ 装

    /** 一件盔甲 + 一块模块能不能装（配方的 matches 用） */
    public static InstallCheck check(ItemStack armor, ItemStack module) {
        ModuleType type = moduleOf(module);
        return type == null ? InstallCheck.NOT_MODULE : check(armor, type);
    }

    /** 同 {@link #check(ItemStack, ItemStack)}，按模块种类 */
    public static InstallCheck check(ItemStack armor, ModuleType type) {
        ArmorItem.Type part = partOf(armor);
        if (part == null || !(armor.getItem() instanceof IModularArmor)) {
            return InstallCheck.NOT_MODULAR;
        }
        if (armor.getCount() != 1) {
            return InstallCheck.STACKED;
        }
        if (!type.fits(part)) {
            return InstallCheck.WRONG_PART;
        }
        if (ModuleConfig.isDisabled(type.id())) {
            return InstallCheck.DISABLED;
        }
        if (has(armor, type)) {
            return InstallCheck.DUPLICATE;
        }
        if (count(armor) >= slots(armor)) {
            return InstallCheck.FULL;
        }
        return InstallCheck.OK;
    }

    /**
     * 装上以后的盔甲：拷贝一件（原有 NBT、电量、耐久、附魔都在），末尾追加 {@code {id, on:1b}}。装不上时返回空。
     *
     * @param armor 原来的那件（不改它）
     * @param type  模块
     * @return 新的一件，或 {@link ItemStack#EMPTY}
     */
    public static ItemStack install(ItemStack armor, ModuleType type) {
        if (check(armor, type) != InstallCheck.OK) {
            return ItemStack.EMPTY;
        }
        ItemStack out = armor.copyWithCount(1);
        CompoundTag tag = out.getOrCreateTag();
        ListTag list = tag.contains(TAG, Tag.TAG_LIST) ? tag.getList(TAG, Tag.TAG_COMPOUND) : new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putString(KEY_ID, type.id().toString());
        entry.putBoolean(KEY_ON, true);
        list.add(entry);
        tag.put(TAG, list);
        return out;
    }

    // ------------------------------------------------------------------ 改（直接改这件，调用方负责它是谁的）

    /**
     * 开 / 关一格（就地改这件）。
     *
     * @return 改了（格子不存在、模块不能开关、本来就是这个状态都返回 false）
     */
    public static boolean setOn(ItemStack armor, int slot, boolean on) {
        CompoundTag tag = armor.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_LIST)) {
            return false;
        }
        ListTag list = tag.getList(TAG, Tag.TAG_COMPOUND);
        if (slot < 0 || slot >= list.size()) {
            return false;
        }
        CompoundTag entry = list.getCompound(slot);
        ModuleType type = get(ResourceLocation.tryParse(entry.getString(KEY_ID)));
        if (type == null || !type.toggleable()) {
            return false;
        }
        boolean was = !entry.contains(KEY_ON) || entry.getBoolean(KEY_ON);
        if (was == on) {
            return false;
        }
        entry.putBoolean(KEY_ON, on);
        return true;
    }

    /**
     * 拿掉一格（就地改这件；列表空了连键一起删，NBT 空了整个删，免得和没装过的同种物品叠不起来）。
     *
     * @return 拿掉的那格的注册名（解析不了时也删、返回 null）；格子不存在返回 null 且什么都不改
     */
    @Nullable
    public static ResourceLocation remove(ItemStack armor, int slot) {
        CompoundTag tag = armor.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_LIST)) {
            return null;
        }
        ListTag list = tag.getList(TAG, Tag.TAG_COMPOUND);
        if (slot < 0 || slot >= list.size()) {
            return null;
        }
        CompoundTag entry = list.getCompound(slot);
        ResourceLocation id = ResourceLocation.tryParse(entry.getString(KEY_ID));
        list.remove(slot);
        if (list.isEmpty()) {
            tag.remove(TAG);
            if (tag.isEmpty()) {
                armor.setTag(null);
            }
        }
        return id;
    }

    /** 这一格存在（列表里有这个下标） */
    public static boolean hasSlot(ItemStack armor, int slot) {
        return slot >= 0 && slot < count(armor);
    }
}
