package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同调家族（batch13.md §3.3.1）：一组会一起长阶的装备。成员用物品标签 {@code essentialcraft5:attunement/<家族>} 登记
 * （源在 {@code tools/tags/attunement.json}），这里登记家族本身的规格：盔甲还是武器（决定吃哪份预算、按哪个装备槽算）、最高几阶、
 * 档位（同调转移只许从低档转到高档）。
 * <p>
 * 默认七个家族在 {@code registry/ECAttunement.init()} 登记；以后加家族照样调 {@link #register}，再往标签源里加成员、跑 runData。
 * 物品属于哪个家族按标签查（{@link #of}），数据包往标签里加了别的物品也认。标签见 {@link #tagFor}（本模组的家族同 {@code ECTags.attunementFamily}）。
 */
public final class AttunementFamily {

    /** 家族的装备种类：盔甲按身上四个盔甲槽算、吃盔甲预算；武器只认主手、吃武器预算 */
    public enum Kind {
        ARMOR(IPlayerData.BudgetKind.ARMOR),
        WEAPON(IPlayerData.BudgetKind.WEAPON);

        private final IPlayerData.BudgetKind budget;

        Kind(IPlayerData.BudgetKind budget) {
            this.budget = budget;
        }

        public IPlayerData.BudgetKind budget() {
            return budget;
        }
    }

    private static final Map<ResourceLocation, AttunementFamily> FAMILIES = Collections.synchronizedMap(new LinkedHashMap<>());

    private final ResourceLocation id;
    private final Kind kind;
    private final int maxStage;
    private final int tier;
    private final TagKey<Item> tag;

    private AttunementFamily(ResourceLocation id, Kind kind, int maxStage, int tier) {
        this.id = id;
        this.kind = kind;
        this.maxStage = maxStage;
        this.tier = tier;
        this.tag = tagFor(id);
    }

    /** 家族 id 对应的物品标签 {@code <命名空间>:attunement/<路径>}（本模组的就是 {@code ECTags.attunementFamily(路径)}） */
    public static TagKey<Item> tagFor(ResourceLocation id) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "attunement/" + id.getPath()));
    }

    /**
     * 登记一个家族（mod 构造期调，重复登记同一个 id 时后登记的覆盖前面的）。标签固定是 {@code essentialcraft5:attunement/<id 的路径>}。
     *
     * @param maxStage 最高几阶（1~{@link Attunement#MAX_STAGE}）
     * @param tier     档位：同调转移只许从档位小的家族转到档位大的同种家族
     */
    public static AttunementFamily register(ResourceLocation id, Kind kind, int maxStage, int tier) {
        AttunementFamily family = new AttunementFamily(id, kind, Math.max(1, Math.min(Attunement.MAX_STAGE, maxStage)), tier);
        FAMILIES.put(id, family);
        return family;
    }

    /** 本模组的家族：{@code essentialcraft5:<path>} */
    public static AttunementFamily register(String path, Kind kind, int maxStage, int tier) {
        return register(EssentialCraft5.id(path), kind, maxStage, tier);
    }

    @Nullable
    public static AttunementFamily byId(@Nullable ResourceLocation id) {
        return id == null ? null : FAMILIES.get(id);
    }

    /** 登记过的全部家族（按登记顺序） */
    public static Collection<AttunementFamily> all() {
        synchronized (FAMILIES) {
            return java.util.List.copyOf(FAMILIES.values());
        }
    }

    /** 这件物品所属的家族；不在任何家族的标签里返回 null（一件物品只该在一个家族里，真有重复时取先登记的） */
    @Nullable
    public static AttunementFamily of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (AttunementFamily family : all()) {
            if (stack.is(family.tag)) {
                return family;
            }
        }
        return null;
    }

    /** 这件装备算在哪个槽：盔甲按自己的部位，武器是主手 */
    public static EquipmentSlot slotOf(ItemStack stack, Kind kind) {
        if (kind == Kind.ARMOR && stack.getItem() instanceof ArmorItem armor) {
            return armor.getEquipmentSlot();
        }
        return EquipmentSlot.MAINHAND;
    }

    public ResourceLocation id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public int maxStage() {
        return maxStage;
    }

    public int tier() {
        return tier;
    }

    public TagKey<Item> tag() {
        return tag;
    }

    /**
     * 标签里现有的物品（各一件）。展示用：不经 {@code Ingredient.getItems()}，因为 Forge 会给空标签塞一个屏障方块占位，
     * 而还没有成员的家族（裁决之刃在 13b 之前）标签就是空的。
     */
    public java.util.List<ItemStack> members() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (net.minecraft.core.Holder<Item> holder : net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            out.add(new ItemStack(holder));
        }
        return out;
    }

    /** 同调总开关开着、这个家族没被 {@code disabled_families} 关掉：会攒点、升阶配方出产物 */
    public boolean active() {
        return AttunementConfig.familyActive(id);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
