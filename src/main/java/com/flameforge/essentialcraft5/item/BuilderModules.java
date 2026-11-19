package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 创世神之杖的模块（tools/plan/builder_upgrade.md §2.2）：NBT、校验、装、拆。
 * <p>
 * NBT {@code builder_modules: ["essentialcraft5:builder_module_range", …]}，字符串列表，按装上的先后存注册名。规矩：
 * <ul>
 *   <li>同一种只装一块；装上的块数不超过这一档的槽数（{@link BuilderTier#moduleSlots}：一阶 2、二阶 3、三阶 4）；回收一阶装不上。</li>
 *   <li>读数时只认「生效的」（{@link #active}）：认得的注册名、去重、这一档能装的，按列表顺序取前「槽数」块。NBT 被手改成重复、超槽数、
 *       一阶带回收时，多出来的不生效（也不会白白加成）；认不得的注册名留在 NBT 里不动。</li>
 *   <li>装：工作台特殊配方 {@code recipe/BuilderModuleRecipe}（魔杖 + 一块模块 → 魔杖拷一份、末尾追加注册名，整份 NBT 照留），
 *       装不装得上看 {@link #check}，数块数看 {@link #installed}（认得的、去重，不管生效没有）。</li>
 *   <li>拆：{@code C2SBuilderActionPacket.REMOVE_MODULE}（a = 模块序号 {@link Kind#ordinal}），服务端 {@link #removeFromHand}：
 *       校验手里还是魔杖、没在施工（做、暂停、排队都算），<b>先删 NBT、同一刻 {@link ECItemEntities#giveOrDrop} 一块新的</b>（背包满了掉在脚下）。
 *       这种注册名在列表里出现几次都一起删、只给一块：模块只在 NBT 里存在一份，删了才给，不会复制。</li>
 *   <li>停用：面板的开关写 {@value #TAG_OFF}（{@link #toggleFromHand}，施工中不改）。停用的照样占槽（{@link #slotted}），
 *       只是不算进 {@link #active}；拆下时一并去掉停用记录。</li>
 * </ul>
 * 施工中不许拆装：拆由 {@link #removeFromHand} 拦；装要把魔杖从手里拿进工作台，正在做 / 排队的那一单认的是手里那个物品对象，
 * 拿走的同时就停了（{@code BuilderJobs.stillHolding}），合出来的是新对象。
 * 换模式（工作台无序配方）、铺设魔杖升二阶（有形配方）、升格三阶（魔力工作台）都整份抄 NBT，模块跟着走；升档槽数只多不少。
 * <p>
 * 效果在 {@link BuilderTier#stats} 里叠（{@code BuilderTier.withModules}）：扩域、疾工、节能、远域按 {@code general.builder.modules.*} 的倍率；
 * 混铺见 {@link BuilderMix}（每格按表选方块），回收见 {@link BuilderSalvage}（拆下的先进随身容器、丢弃清单）。
 */
public final class BuilderModules {

    /** NBT 键：装上的模块（字符串列表，注册名） */
    public static final String TAG = "builder_modules";
    /** NBT 键：停用的模块（字符串列表，注册名）。停用的照样占槽，只是不生效（面板的开关写它） */
    public static final String TAG_OFF = "builder_modules_off";

    /** 六种模块。序号就是拆模块包里的 a，<b>只能往后追加</b>，不能改顺序 */
    public enum Kind {
        /** 扩域：一次的格数 ×2、铺面 / 直线边长 ×1.4（向下取整），仍不超过 hard_max_volume */
        RANGE("range", BuilderTier.PAVER),
        /** 疾工：每刻上限 ×2（仍受全服每刻格数和耗时预算） */
        HASTE("haste", BuilderTier.PAVER),
        /** 节能：每格 MRU ×0.6（向下取整） */
        EFFICIENCY("efficiency", BuilderTier.PAVER),
        /** 远域：施工距离 +32（瞄准距离等于施工距离，跟着变远） */
        REACH("reach", BuilderTier.PAVER),
        /** 混铺：最多 4 种方块按比例（{@link BuilderMix}） */
        MIX("mix", BuilderTier.PAVER),
        /** 回收：拆下的东西先进背包类随身容器、可设丢弃清单（{@link BuilderSalvage}）；一阶装不上 */
        SALVAGE("salvage", BuilderTier.BUILDER);

        private final String id;
        private final BuilderTier minTier;

        Kind(String id, BuilderTier minTier) {
            this.id = id;
            this.minTier = minTier;
        }

        /** 短名（配置节名、语言键里用），如 range */
        public String id() {
            return id;
        }

        /** 物品注册名 {@code essentialcraft5:builder_module_<短名>} */
        public ResourceLocation registryName() {
            return EssentialCraft5.id("builder_module_" + id);
        }

        /** 这一档能不能装 */
        public boolean fits(BuilderTier tier) {
            return tier.level() >= minTier.level();
        }

        /** 拆下来给的那块物品 */
        public ItemStack newItem() {
            return new ItemStack(ECBuilder.module(this));
        }

        /** 按注册名找；认不得返回 null */
        @Nullable
        public static Kind byName(@Nullable ResourceLocation name) {
            if (name == null) {
                return null;
            }
            for (Kind kind : values()) {
                if (kind.registryName().equals(name)) {
                    return kind;
                }
            }
            return null;
        }

        /** 按序号找（拆模块包的 a）；越界返回 null */
        @Nullable
        public static Kind byIndex(int index) {
            Kind[] all = values();
            return index >= 0 && index < all.length ? all[index] : null;
        }
    }

    /** 装得上装不上 */
    public enum Check {
        OK,
        /** 不是创世神之杖 */
        NOT_WAND,
        /** 魔杖叠了不止一件 */
        STACKED,
        /** 这一档装不了（回收装一阶） */
        TIER,
        /** 已经装了同一种 */
        DUPLICATE,
        /** 槽满了 */
        FULL
    }

    /** 拆的结果（GameTest 断言用） */
    public enum RemoveResult {
        OK,
        /** 已经死了（死亡界面）或是旁观者：给的模块会掉在死的地方，不拆 */
        UNAVAILABLE,
        /** 那只手里不是创世神之杖 */
        NOT_WAND,
        /** 模块序号不对 */
        BAD_MODULE,
        /** 有活在做、暂停或排队 */
        BUSY,
        /** 这根魔杖没装这种 */
        NOT_INSTALLED
    }

    private BuilderModules() {
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    /** NBT 里原样的注册名列表（含认不得的、重复的）；没有返回空表 */
    public static List<String> raw(ItemStack wand) {
        CompoundTag tag = wand.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = tag.getList(TAG, Tag.TAG_STRING);
        List<String> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    /** 装上的模块：认得的、去重，按列表顺序（不管这一档能不能装、超没超槽数）。装的时候数槽用它 */
    public static List<Kind> installed(ItemStack wand) {
        List<String> raw = raw(wand);
        if (raw.isEmpty()) {
            return List.of();
        }
        Set<Kind> seen = EnumSet.noneOf(Kind.class);
        List<Kind> out = new ArrayList<>(raw.size());
        for (String name : raw) {
            Kind kind = Kind.byName(ResourceLocation.tryParse(name));
            if (kind != null && seen.add(kind)) {
                out.add(kind);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * 占着槽的模块：{@link #installed} 里这一档能装的，按顺序取前「槽数」块（停用的也在里面，停用不腾槽）。不是魔杖返回空表。
     * 面板的模块页按它列，开关只认它里面的
     */
    public static List<Kind> slotted(ItemStack wand) {
        if (BuilderMode.of(wand) == null) {
            return List.of();
        }
        List<Kind> installed = installed(wand);
        if (installed.isEmpty()) {
            return List.of();
        }
        BuilderTier tier = BuilderTier.of(wand);
        List<Kind> out = new ArrayList<>(installed.size());
        for (Kind kind : installed) {
            if (out.size() >= tier.moduleSlots()) {
                break;
            }
            if (kind.fits(tier)) {
                out.add(kind);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** 生效的模块：{@link #slotted} 里没停用的。不是魔杖返回空表 */
    public static List<Kind> active(ItemStack wand) {
        List<Kind> slotted = slotted(wand);
        if (slotted.isEmpty()) {
            return slotted;
        }
        Set<Kind> off = disabled(wand);
        if (off.isEmpty()) {
            return slotted;
        }
        List<Kind> out = new ArrayList<>(slotted.size());
        for (Kind kind : slotted) {
            if (!off.contains(kind)) {
                out.add(kind);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** 停用的模块（NBT {@value #TAG_OFF}，认得的；装没装不管）。没有返回空集 */
    public static Set<Kind> disabled(ItemStack wand) {
        CompoundTag tag = wand.getTag();
        if (tag == null || !tag.contains(TAG_OFF, Tag.TAG_LIST)) {
            return Set.of();
        }
        ListTag list = tag.getList(TAG_OFF, Tag.TAG_STRING);
        Set<Kind> out = EnumSet.noneOf(Kind.class);
        // 手改 NBT 塞一长串也只看前面几项（客户端每刻都会读）
        for (int i = 0; i < Math.min(list.size(), Kind.values().length * 4); i++) {
            Kind kind = Kind.byName(ResourceLocation.tryParse(list.getString(i)));
            if (kind != null) {
                out.add(kind);
            }
        }
        return out;
    }

    /** 开 / 关一种模块（就地改；关了的写进 {@value #TAG_OFF}，开了删掉，删空连键去掉） */
    public static void setEnabled(ItemStack wand, Kind kind, boolean enabled) {
        CompoundTag tag = wand.getOrCreateTag();
        ListTag list = tag.contains(TAG_OFF, Tag.TAG_LIST) ? tag.getList(TAG_OFF, Tag.TAG_STRING) : new ListTag();
        String name = kind.registryName().toString();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (name.equals(list.getString(i))) {
                list.remove(i);
            }
        }
        if (!enabled) {
            list.add(StringTag.valueOf(name));
        }
        if (list.isEmpty()) {
            tag.remove(TAG_OFF);
        } else {
            tag.put(TAG_OFF, list);
        }
    }

    /** 这种模块生效没有 */
    public static boolean has(ItemStack wand, Kind kind) {
        return active(wand).contains(kind);
    }

    // ------------------------------------------------------------------
    // 装
    // ------------------------------------------------------------------

    /** 一根魔杖能不能装上这种模块（配方的 matches 用） */
    public static Check check(ItemStack wand, Kind kind) {
        if (BuilderMode.of(wand) == null) {
            return Check.NOT_WAND;
        }
        if (wand.getCount() != 1) {
            return Check.STACKED;
        }
        BuilderTier tier = BuilderTier.of(wand);
        if (!kind.fits(tier)) {
            return Check.TIER;
        }
        List<Kind> installed = installed(wand);
        if (installed.contains(kind)) {
            return Check.DUPLICATE;
        }
        if (installed.size() >= tier.moduleSlots()) {
            return Check.FULL;
        }
        return Check.OK;
    }

    /**
     * 装上以后的魔杖：拷一件（MRU、选区、选定方块、档位、别的键都在），列表末尾追加这种的注册名。装不上返回空。
     *
     * @param wand 原来的那根（不改它）
     */
    public static ItemStack install(ItemStack wand, Kind kind) {
        if (check(wand, kind) != Check.OK) {
            return ItemStack.EMPTY;
        }
        ItemStack out = wand.copyWithCount(1);
        CompoundTag tag = out.getOrCreateTag();
        ListTag list = tag.contains(TAG, Tag.TAG_LIST) ? tag.getList(TAG, Tag.TAG_STRING) : new ListTag();
        list.add(StringTag.valueOf(kind.registryName().toString()));
        tag.put(TAG, list);
        return out;
    }

    // ------------------------------------------------------------------
    // 拆
    // ------------------------------------------------------------------

    /**
     * 从这根魔杖的 NBT 里删掉这种模块（就地改；列表里出现几次都删，删空了连键一起去掉）。
     *
     * @return 删掉了（本来就没有返回 false）
     */
    public static boolean remove(ItemStack wand, Kind kind) {
        CompoundTag tag = wand.getTag();
        if (tag == null || !tag.contains(TAG, Tag.TAG_LIST)) {
            return false;
        }
        ListTag list = tag.getList(TAG, Tag.TAG_STRING);
        String name = kind.registryName().toString();
        boolean removed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (name.equals(list.getString(i))) {
                list.remove(i);
                removed = true;
            }
        }
        if (list.isEmpty()) {
            tag.remove(TAG);
        }
        if (removed && tag.contains(TAG_OFF, Tag.TAG_LIST)) {
            // 拆下的那块停没停用都不再记着（再装上时是开着的）
            setEnabled(wand, kind, true);
        }
        return removed;
    }

    /**
     * 玩家在面板里开 / 关手里魔杖上的一块模块（{@code C2SBuilderActionPacket.TOGGLE_MODULE}）：校验同 {@link #removeFromHand}，
     * 只认占着槽的（{@link #slotted}）。停用的不生效但照样占槽，拆、装的规矩不变。
     */
    public static RemoveResult toggleFromHand(ServerPlayer player, InteractionHand hand, int index, boolean enabled) {
        if (!player.isAlive() || player.isSpectator()) {
            return RemoveResult.UNAVAILABLE;
        }
        ItemStack wand = player.getItemInHand(hand);
        if (BuilderMode.of(wand) == null) {
            return RemoveResult.NOT_WAND;
        }
        Kind kind = Kind.byIndex(index);
        if (kind == null) {
            return RemoveResult.BAD_MODULE;
        }
        if (BuilderJobs.isRunning(player.getUUID())) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.panel_busy"));
            return RemoveResult.BUSY;
        }
        if (!slotted(wand).contains(kind)) {
            return RemoveResult.NOT_INSTALLED;
        }
        setEnabled(wand, kind, enabled);
        MagicalBuilderItem.tell(player, Component.translatable(enabled ? "message.essentialcraft5.builder.module_on" : "message.essentialcraft5.builder.module_off",
                kind.newItem().getHoverName()));
        return RemoveResult.OK;
    }

    /**
     * 玩家拆下手里魔杖上的一块模块（拆模块包、GameTest 调；见类注释）：死了、旁观、手里不是魔杖、序号不对、在施工、没装这种都不动；
     * 做成时先删 NBT，同一刻给一块新的（背包满了掉在脚下），动作栏提示一句。
     */
    public static RemoveResult removeFromHand(ServerPlayer player, InteractionHand hand, int index) {
        if (!player.isAlive() || player.isSpectator()) {
            return RemoveResult.UNAVAILABLE;
        }
        ItemStack wand = player.getItemInHand(hand);
        if (BuilderMode.of(wand) == null) {
            return RemoveResult.NOT_WAND;
        }
        Kind kind = Kind.byIndex(index);
        if (kind == null) {
            return RemoveResult.BAD_MODULE;
        }
        if (BuilderJobs.isRunning(player.getUUID())) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.module_busy"));
            return RemoveResult.BUSY;
        }
        if (!remove(wand, kind)) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.module_missing",
                    kind.newItem().getHoverName()));
            return RemoveResult.NOT_INSTALLED;
        }
        ItemStack given = kind.newItem();
        Component name = given.getHoverName();
        ECItemEntities.giveOrDrop(player, given);
        MagicalBuilderItem.tell(player, Component.translatable("message.essentialcraft5.builder.module_removed", name));
        return RemoveResult.OK;
    }
}
