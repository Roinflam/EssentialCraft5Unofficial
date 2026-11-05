package com.flameforge.essentialcraft5.api;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 魔力工作台的升级。对应原作 MagicianTableUpgrades（登记在 ItemsCore L299-307）。
 * <p>
 * 拿着升级物品右键魔力工作台装上（每台只能装一个），潜行空手右键卸下；装上后工作台里画一个贴着升级贴图的方块。
 * 速度系数 = 每 tick 的进度增量，每 tick 耗的 MRU 也按同一个系数放大（见 MagicianTableBlockEntity）。
 * <p>
 * 原作按「物品 + meta」认升级、方块实体里存升级在列表里的下标；这里按物品注册名认，方块实体里存物品本身。
 * 表里按新注册名列全了原作的 9 种，后面批次的物品（虚空板、恶魔核心……）注册上以后自动生效，不用再改这里。
 * 贴图是原作 textures/models/ 下的立方体展开图（导入后在 textures/block/model/）。
 */
public final class MagicianTableUpgrades {

    /**
     * @param item       升级物品的注册名
     * @param efficiency 速度系数
     * @param texture    工作台里那个方块的贴图（完整路径，含 textures/ 与 .png）
     */
    public record Upgrade(ResourceLocation item, double efficiency, ResourceLocation texture) {
    }

    private static final Map<ResourceLocation, Upgrade> BY_ITEM = new LinkedHashMap<>();

    static {
        // 顺序、系数与原作 ItemsCore 一致
        add("elemental_core", 1.2D, "elementalcore");
        add("combined_magical_alloys", 5D, "combinedmagicalalloys");
        add("magic_purified_ender_scale_alloy", 2.5D, "enderalloy");
        add("eye_of_absorption", 3D, "eyeofabsorbtion");
        add("void_plating", 6D, "voidplating");
        add("void_core", 7.5D, "voidcore");
        add("void_mru_reactor", 10D, "voidreactor");
        add("mru_resonating_crystal", 4D, "crystal");
        add("demonic_core", 16D, "demoniccore");
    }

    private MagicianTableUpgrades() {
    }

    private static void add(String item, double efficiency, String texture) {
        register(EssentialCraft5.id(item), efficiency, EssentialCraft5.id("textures/block/model/" + texture + ".png"));
    }

    /** 登记一种升级（在模组构造阶段调用；同一物品再登记会覆盖） */
    public static void register(ResourceLocation item, double efficiency, ResourceLocation texture) {
        BY_ITEM.put(item, new Upgrade(item, efficiency, texture));
    }

    /** 这个物品是哪种升级，不是升级返回 null（原作 isItemUpgrade / getUpgradeIDByItemStack） */
    @Nullable
    public static Upgrade get(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return BY_ITEM.get(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /** 装着这个升级时的速度系数；没装（空物品）或不认识的物品为 1 */
    public static double efficiency(ItemStack stack) {
        Upgrade upgrade = get(stack);
        return upgrade == null ? 1D : upgrade.efficiency();
    }

    /** 全部升级（按登记顺序），给指南页面之类列表用 */
    public static Collection<Upgrade> all() {
        return Collections.unmodifiableCollection(BY_ITEM.values());
    }

    /** 速度系数的显示格式：1.2、2.5、3（去掉多余的 0，最多两位小数） */
    public static String format(double efficiency) {
        return BigDecimal.valueOf(efficiency).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
