package com.flameforge.essentialcraft5.item.module;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * 一种宅男套装模块（batch10.md §4.3 第 1 条）。id 就是模块物品的注册名：盔甲 NBT 里记的是它，拆下来按它找回物品。
 * 用 {@link ArmorModules#register} 登记（模块注册类的静态字段，早于物品注册），物品是 {@link ArmorModuleItem}。
 * <pre>{@code
 * public static final ModuleType PHASE_ANCHOR = ArmorModules.register(ModuleType.builder(EssentialCraft5.id("phase_anchor_module"))
 *         .parts(ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)
 *         .mruPerSecond(() -> PhaseAnchorConfig.values().mruPerSecond())
 *         .build());
 * public static final RegistryObject<ArmorModuleItem> PHASE_ANCHOR_MODULE = ECItems.register("phase_anchor_module",
 *         () -> new ArmorModuleItem(PHASE_ANCHOR, new Item.Properties()), ItemSpec.generated("item/module/phase_anchor_module"));
 * }</pre>
 * <ul>
 *   <li>{@code parts}：能装在哪几件上（配方只认这几件）。</li>
 *   <li>{@code toggleable}：终端里能不能开关（默认能；不能开关的装上就一直开着）。</li>
 *   <li>{@code mruPerSecond}：开着时每秒的电费，由 {@link ModuleTicker} 每人每秒统一扣（先算后扣，扣不起这一秒不生效、终端标「缺电」）；0 = 不按秒扣。</li>
 *   <li>{@code mruPerUse}：一次性花费（回溯、矩阵过滤这类触发时才扣的），只给终端和提示显示用——模块触发时自己用
 *       {@code MRUDrain.canAfford} / {@code playerUseMRU} 先算后扣；身上的电不够这一次时终端标「缺电」。0 = 没有。</li>
 * </ul>
 * 两个耗电量都是现取的（传配置的读取函数），热改配置以后马上生效。
 */
public final class ModuleType {

    private final ResourceLocation id;
    private final Set<ArmorItem.Type> parts;
    private final boolean toggleable;
    private final IntSupplier mruPerSecond;
    private final IntSupplier mruPerUse;

    private ModuleType(Builder builder) {
        this.id = builder.id;
        this.parts = Collections.unmodifiableSet(EnumSet.copyOf(builder.parts));
        this.toggleable = builder.toggleable;
        this.mruPerSecond = builder.mruPerSecond;
        this.mruPerUse = builder.mruPerUse;
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    /** 模块物品的注册名 */
    public ResourceLocation id() {
        return id;
    }

    /** 能装在哪几件上 */
    public Set<ArmorItem.Type> parts() {
        return parts;
    }

    public boolean fits(ArmorItem.Type part) {
        return parts.contains(part);
    }

    public boolean toggleable() {
        return toggleable;
    }

    /** 开着时每秒的电费（≥ 0） */
    public int mruPerSecond() {
        return Math.max(0, mruPerSecond.getAsInt());
    }

    /** 一次性花费（≥ 0，只用来显示） */
    public int mruPerUse() {
        return Math.max(0, mruPerUse.getAsInt());
    }

    /** 模块物品（还没注册、被删掉时是空气） */
    public Item item() {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? Items.AIR : item;
    }

    /** 一块模块物品 */
    public ItemStack stack() {
        return new ItemStack(item());
    }

    /** 显示名（物品名；物品不在时写注册名） */
    public Component displayName() {
        Item item = item();
        return item == Items.AIR ? Component.literal(id.toString()) : item.getDescription();
    }

    /** 说明的语言键：{@code tooltip.<命名空间>.<路径>}（例 tooltip.essentialcraft5.rewind_module） */
    public String descriptionKey() {
        return "tooltip." + id.getNamespace() + "." + id.getPath();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ModuleType other && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ModuleType[" + id + "]";
    }

    /** {@link ModuleType} 的写法（见类注释） */
    public static final class Builder {
        private final ResourceLocation id;
        private Set<ArmorItem.Type> parts = EnumSet.noneOf(ArmorItem.Type.class);
        private boolean toggleable = true;
        private IntSupplier mruPerSecond = () -> 0;
        private IntSupplier mruPerUse = () -> 0;

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id);
        }

        public Builder parts(ArmorItem.Type first, ArmorItem.Type... rest) {
            this.parts = EnumSet.of(first, rest);
            return this;
        }

        public Builder toggleable(boolean value) {
            this.toggleable = value;
            return this;
        }

        public Builder mruPerSecond(IntSupplier value) {
            this.mruPerSecond = Objects.requireNonNull(value);
            return this;
        }

        public Builder mruPerUse(IntSupplier value) {
            this.mruPerUse = Objects.requireNonNull(value);
            return this;
        }

        public ModuleType build() {
            if (parts.isEmpty()) {
                throw new IllegalStateException("模块至少要能装在一件上：" + id);
            }
            return new ModuleType(this);
        }
    }
}
