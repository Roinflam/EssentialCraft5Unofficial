package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MagicalCultivatorBlock;
import com.flameforge.essentialcraft5.blockentity.MagicalCultivatorBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MagicalCultivatorMenu;
import com.flameforge.essentialcraft5.recipe.CultivatorRecipe;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 魔力栽培器模块（第十二批，batch12.md §4.2；policy §4.15）：EC5 新机器，原作没有。种子 / 树苗放进格子当样本（不消耗），
 * 接上 MRU 和水，作物在机器里按配方计时长成，产物进输出格；世界里一格方块都不动。
 * <p>
 * 注册名：{@code magical_cultivator}（方块、方块实体、菜单同名；命名表里是 EC5 新增的一条，old_id 为 null）。
 * 方块属性照地脉凝聚器（硬度 1、抗爆按 1.12 规则换算、透天空光、不遮挡），模型手写（{@code models/block/magical_cultivator.json}，cutout）：
 * 侧面板框 + 土槽、底面不透明，顶上日光玻璃中间 10×10 透明；玻璃窗下面有一层湿耕地（顶面高 1.6 像素，同渲染器的 SOIL_Y）、
 * 几株嫩芽和四面朝里的内壁，斜着往里看也不会透出下面的世界。种下的作物由 {@code client/render/MagicalCultivatorRenderer} 画。
 * <p>
 * 配方类型 {@code essentialcraft5:cultivator}（{@link CultivatorRecipe}），31 个显式配方由 {@code tools/gen/cultivator_recipes.py}
 * 生成到 {@code data/essentialcraft5/recipes/cultivator/}；机器本身的工作台配方也在那个目录（{@code magical_cultivator.json}）。
 * 没有显式配方的作物走通用路线（batch12.md §4.2.2 与定稿 D5：只收 {@code CropBlock} 子类、并且种得在普通耕地上的），
 * 物品标签 {@link #BLACKLIST}（{@code tools/tags/eccultivator.json}，默认空）里的不收。
 * <p>
 * 文字在 {@code tools/lang/keys/eccultivator.json}，手册支线 {@code guide/<语言>/farming.json} 的 magical_cultivator 一条，
 * 进度 {@code advancements/guide/farming/magical_cultivator.json}。客户端部分（界面、作物渲染、手册与 JEI 的配方画法）见 {@code client/CultivatorClient}。
 */
public final class ECCultivator {

    // ---------------- 配方类型 ----------------

    public static final RegistryObject<RecipeType<CultivatorRecipe>> CULTIVATOR = ECRecipes.type("cultivator");
    public static final RegistryObject<RecipeSerializer<CultivatorRecipe>> CULTIVATOR_SERIALIZER =
            ECRecipes.serializer("cultivator", CultivatorRecipe.Serializer::new);

    // ---------------- 机器 ----------------

    public static final RegistryObject<MagicalCultivatorBlock> MAGICAL_CULTIVATOR = ECBlocks.register("magical_cultivator",
            () -> new MagicalCultivatorBlock(ECBlocks.machine(MapColor.COLOR_LIGHT_BLUE, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion()),
            BlockSpec.of(BlockSpec.existing("magical_cultivator")));

    public static final RegistryObject<BlockEntityType<MagicalCultivatorBlockEntity>> MAGICAL_CULTIVATOR_BE =
            ECBlockEntities.type("magical_cultivator", MagicalCultivatorBlockEntity::new, MAGICAL_CULTIVATOR);

    public static final RegistryObject<MenuType<MagicalCultivatorMenu>> MAGICAL_CULTIVATOR_MENU =
            ECMenus.register("magical_cultivator", MagicalCultivatorMenu::new);

    /** 通用路线不收的物品（默认空）：整合包作者发现某种作物不该种时往这里加。显式配方不看它，要去掉显式配方用数据包删配方 */
    public static final TagKey<Item> BLACKLIST = ECTags.modItem("cultivator_blacklist");

    private ECCultivator() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 machines.magical_cultivator */
    public static void init() {
        ECConfig.machine(MagicalCultivatorBlockEntity.Config::define);
    }
}
