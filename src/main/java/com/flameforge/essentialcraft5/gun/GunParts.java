package com.flameforge.essentialcraft5.gun;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 一把枪的部件（第五批框架 F5-5），存在枪物品 NBT 的子标签 {@value #TAG} 里：
 * <pre>
 * gun: { base: "mru", handle: "pale", device: "mithriline", lens: "chaos"（可无）, scope: "mru"（可无）, fired: 0, wear: 0 }
 * </pre>
 * <ul>
 *   <li>{@code base / handle / device}：枪托 / 握把 / 机栓的材料 id（{@link GunMaterials#BODIES}）；三个都有才算「组装好」（{@link #isAssembled}）。</li>
 *   <li>{@code lens}：透镜 id（{@link GunMaterials#LENSES}）；{@code scope}：瞄具 id（狙击查 {@link GunMaterials#SNIPER_SCOPES}，手枪 / 步枪查 {@link GunMaterials#SCOPES}）。</li>
 *   <li>{@code fired}：这一匣已经打了几发（原作 gunShots）；{@code wear}：磨损（原作 gunDamage，到属性里的耐久以后开始卡壳）。</li>
 * </ul>
 * 属性<b>不存</b>（原作把算好的 stats 写死在 NBT 里）：每次用 {@link GunStats#of} 现算，以后调数值老枪跟着变。
 * MRU 仍由 {@code MRUItemStorage} 存在顶层的 {@code mru} 键，和这个子标签不冲突。
 * 这里的方法只读写 NBT，不判断材料开没开放（开放只管工作台认不认材料，{@link GunMaterials#bodyByItem}）。
 */
public record GunParts(String base, String handle, String device, @Nullable String lens, @Nullable String scope) {

    /** 子标签名 */
    public static final String TAG = "gun";
    private static final String KEY_BASE = "base";
    private static final String KEY_HANDLE = "handle";
    private static final String KEY_DEVICE = "device";
    private static final String KEY_LENS = "lens";
    private static final String KEY_SCOPE = "scope";
    private static final String KEY_FIRED = "fired";
    private static final String KEY_WEAR = "wear";

    /** 同一种材料做三个部件、不带透镜和瞄具（测试、展示厅用） */
    public static GunParts uniform(String material) {
        return new GunParts(material, material, material, null, null);
    }

    public GunParts withLens(@Nullable String lensId) {
        return new GunParts(base, handle, device, lensId, scope);
    }

    public GunParts withScope(@Nullable String scopeId) {
        return new GunParts(base, handle, device, lens, scopeId);
    }

    // ------------------------------------------------------------------
    // 读写
    // ------------------------------------------------------------------

    /** 读部件；没有 {@value #TAG} 子标签或三个枪身部件缺任何一个时为空 */
    public static Optional<GunParts> read(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? Optional.empty() : load(tag);
    }

    public static Optional<GunParts> load(CompoundTag tag) {
        String base = tag.getString(KEY_BASE);
        String handle = tag.getString(KEY_HANDLE);
        String device = tag.getString(KEY_DEVICE);
        if (base.isEmpty() || handle.isEmpty() || device.isEmpty()) {
            return Optional.empty();
        }
        String lens = tag.contains(KEY_LENS, Tag.TAG_STRING) && !tag.getString(KEY_LENS).isEmpty() ? tag.getString(KEY_LENS) : null;
        String scope = tag.contains(KEY_SCOPE, Tag.TAG_STRING) && !tag.getString(KEY_SCOPE).isEmpty() ? tag.getString(KEY_SCOPE) : null;
        return Optional.of(new GunParts(base, handle, device, lens, scope));
    }

    /** 把部件写进枪的 {@value #TAG} 子标签（保留已有的 fired / wear；透镜、瞄具为 null 时删掉对应键） */
    public void writeTo(ItemStack stack) {
        save(stack.getOrCreateTagElement(TAG));
    }

    /** 写进给定的标签（只写部件五个键） */
    public void save(CompoundTag tag) {
        tag.putString(KEY_BASE, base);
        tag.putString(KEY_HANDLE, handle);
        tag.putString(KEY_DEVICE, device);
        if (lens != null) {
            tag.putString(KEY_LENS, lens);
        } else {
            tag.remove(KEY_LENS);
        }
        if (scope != null) {
            tag.putString(KEY_SCOPE, scope);
        } else {
            tag.remove(KEY_SCOPE);
        }
    }

    /** 组装好了没有（三个枪身部件都在） */
    public static boolean isAssembled(ItemStack stack) {
        return read(stack).isPresent();
    }

    /** 这一匣已经打了几发（没有子标签为 0） */
    public static int fired(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? 0 : Math.max(0, tag.getInt(KEY_FIRED));
    }

    public static void setFired(ItemStack stack, int fired) {
        stack.getOrCreateTagElement(TAG).putInt(KEY_FIRED, Math.max(0, fired));
    }

    /** 磨损（没有子标签为 0） */
    public static int wear(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag == null ? 0 : Math.max(0, tag.getInt(KEY_WEAR));
    }

    public static void setWear(ItemStack stack, int wear) {
        stack.getOrCreateTagElement(TAG).putInt(KEY_WEAR, Math.max(0, wear));
    }
}
