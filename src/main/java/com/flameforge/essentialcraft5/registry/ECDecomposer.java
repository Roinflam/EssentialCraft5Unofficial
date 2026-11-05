package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.block.ElementalDecomposerBlock;
import com.flameforge.essentialcraft5.blockentity.ElementalDecomposerBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.ElementalDecomposerMenu;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.RegistryObject;

/**
 * 元素分解器模块（第十二批，batch12.md §4.8；policy §4.15）：EC5 新机器，原作没有。把挖矿、刷怪、砍树攒下的原版杂物按物品标签
 * 分成四元素点数，32 点出一颗普通精华，每种元素第 16 颗是罕见精华；量不大，但能挑元素（喂水晶控制器做定向培育）。
 * <p>
 * 注册名：{@code elemental_decomposer}（方块、方块实体、菜单同名；命名表里是 EC5 新增的一条，old_id 为 null）。
 * 方块属性照地脉凝聚器，有水平朝向（正面朝放置者），正面是四色漏斗口、底面复用顶面贴图。
 * <p>
 * 四个物品标签 {@code essentialcraft5:decomposer/fire|water|earth|air}（{@code tools/tags/ecdecomposer.json}，默认只放原版里大家天天扔的东西，
 * 不放本模组物品、不放值钱的）。文字在 {@code tools/lang/keys/ecdecomposer.json}，配方 {@code data/essentialcraft5/recipes/decomposer/}，
 * 手册第九章 {@code guide/<语言>/essence.json} 的 elemental_decomposer 一条，进度 {@code advancements/guide/essence/elemental_decomposer.json}。
 * 客户端部分见 {@code client/DecomposerClient}。
 */
public final class ECDecomposer {

    public static final RegistryObject<ElementalDecomposerBlock> ELEMENTAL_DECOMPOSER = ECBlocks.register("elemental_decomposer",
            () -> new ElementalDecomposerBlock(ECBlocks.machine(MapColor.COLOR_LIGHT_BLUE, 1F, ECBlocks.legacyResistance(1F, 1F, true))),
            BlockSpec.of((provider, block) -> {
                // 原版 orientable_with_bottom：正面朝北，horizontalBlock 按 facing 转；底面用顶面贴图
                ModelFile model = provider.models().orientableWithBottom(provider.name(block),
                        provider.tex("block/elemental_decomposer_side"), provider.tex("block/elemental_decomposer_front"),
                        provider.tex("block/elemental_decomposer_top"), provider.tex("block/elemental_decomposer_top"));
                provider.horizontalBlock(block, model);
                provider.simpleBlockItem(block, model);
            }));

    public static final RegistryObject<BlockEntityType<ElementalDecomposerBlockEntity>> ELEMENTAL_DECOMPOSER_BE =
            ECBlockEntities.type("elemental_decomposer", ElementalDecomposerBlockEntity::new, ELEMENTAL_DECOMPOSER);

    public static final RegistryObject<MenuType<ElementalDecomposerMenu>> ELEMENTAL_DECOMPOSER_MENU =
            ECMenus.register("elemental_decomposer", ElementalDecomposerMenu::new);

    /** 四种元素的分解标签，下标同 {@link IElementalCrystal.Element#ordinal()}（火、水、地、风） */
    public static final TagKey<Item> FIRE = ECTags.modItem("decomposer/fire");
    public static final TagKey<Item> WATER = ECTags.modItem("decomposer/water");
    public static final TagKey<Item> EARTH = ECTags.modItem("decomposer/earth");
    public static final TagKey<Item> AIR = ECTags.modItem("decomposer/air");

    private ECDecomposer() {
    }

    /** 某种元素的分解标签 */
    public static TagKey<Item> tag(IElementalCrystal.Element element) {
        return switch (element) {
            case FIRE -> FIRE;
            case WATER -> WATER;
            case EARTH -> EARTH;
            case AIR -> AIR;
        };
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 machines.elemental_decomposer */
    public static void init() {
        ECConfig.machine(ElementalDecomposerBlockEntity.Config::define);
    }
}
