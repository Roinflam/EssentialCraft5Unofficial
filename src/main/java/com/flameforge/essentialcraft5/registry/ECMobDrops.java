package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.item.MobMaterialItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;

/**
 * 新怪物的掉落材料（第十三批，batch13.md §5.4 掉落经济）：EC5 新物品，原作没有。几路怪物都要掉、手册和升阶配方都要引用，
 * 所以单独放一个注册类，不归哪一只怪。
 * <ul>
 *   <li>{@link #ANCIENT_CORE} 上古晶核：聚能魔像必掉 1 个（13c），13e 的废城守望者、渊潮领主也掉；没有补充来源，刷怪塔拿不到。
 *       装备升阶的材料（batch13.md §9.6：魔力防护 2 阶、虚空 4 阶、裁决之刃 4 阶、宅男 4 阶）。</li>
 *   <li>{@link #MATRIX_CRYSTAL} 矩阵结晶：矩阵之影掉，召唤者拿 1 + ⌊转进去的矩阵损伤 / 24 000⌋ 个、别的参战者各 1 个（§5.3.2）。
 *       升阶材料（风之 2 阶、审判庭 4 阶、裁决之刃升格）。</li>
 *   <li>{@link #ABERRANT_RESIDUE} 异变残质（13d，batch13.md §5.2、§6.6）：海纳尔八种本地怪的常见掉落（{@code fair_kill} + {@code hoanna_common} 递减），
 *       巢穴奖励 2~4 个；虚空 / 审判庭 3 阶、裁决之刃 2 阶的升阶材料。</li>
 *   <li>{@link #ABERRANT_HEART} 异变之心（13d）：海纳尔本地怪的精英 50% 左右、巢穴奖励每人 1 个；虚空 / 审判庭 / 宅男 3 阶、裁决之刃 3 阶、
 *       渊潮领主召唤（13e）。</li>
 * </ul>
 * 13d 的两样是预备时注册的（几路掉落表、升阶配方都要引用）。名字在命名表 tools/id_map_items.json（{@code old_id} 为 null），提示框文字在 tools/lang/keys/ecmobdrops.json；
 * 上古晶核、矩阵结晶已是正式贴图；异变残质、异变之心现在是换色的占位图（{@code textures/item/aberrant_residue.png}、{@code aberrant_heart.png}），
 * 正式贴图另画后同名覆盖。
 */
public final class ECMobDrops {

    public static final RegistryObject<MobMaterialItem> ANCIENT_CORE = ECItems.register("ancient_core",
            () -> new MobMaterialItem(new Item.Properties().rarity(Rarity.UNCOMMON), "ancient_core"), ItemSpec.generated("item/ancient_core"));

    public static final RegistryObject<MobMaterialItem> MATRIX_CRYSTAL = ECItems.register("matrix_crystal",
            () -> new MobMaterialItem(new Item.Properties().rarity(Rarity.RARE), "matrix_crystal") {
                @Override
                protected String descriptionKey() {
                    return matrixCrystalTooltipKey();
                }
            }, ItemSpec.generated("item/matrix_crystal"));

    public static final RegistryObject<MobMaterialItem> ABERRANT_RESIDUE = ECItems.register("aberrant_residue",
            () -> new MobMaterialItem(new Item.Properties().rarity(Rarity.UNCOMMON), "aberrant_residue"), ItemSpec.generated("item/aberrant_residue"));

    public static final RegistryObject<MobMaterialItem> ABERRANT_HEART = ECItems.register("aberrant_heart",
            () -> new MobMaterialItem(new Item.Properties().rarity(Rarity.RARE), "aberrant_heart"), ItemSpec.generated("item/aberrant_heart"));

    private ECMobDrops() {
    }

    /**
     * 矩阵结晶提示框那一行的键：召唤者按转进去的损伤多拿（{@code matrix_shade.crystal_step} &gt; 0）时说「转得越多拿得越多」，
     * 填 0 不多给时换成不提这一句的 {@code .desc_no_bonus}
     */
    public static String matrixCrystalTooltipKey() {
        String key = MobMaterialItem.tooltipKey("matrix_crystal");
        return ECMatrixShade.Config.values().summon().crystalStep() > 0 ? key : key + "_no_bonus";
    }

    /** 由 ECRegistries.register() 调，只为触发类加载 */
    public static void init() {
    }
}
