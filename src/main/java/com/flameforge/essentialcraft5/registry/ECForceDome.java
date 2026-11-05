package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.ForceDomeBlock;
import com.flameforge.essentialcraft5.blockentity.ForceDomeBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.ForceDomeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 力场穹顶（第十批 10c-M4，batch10.md §5.13；policy 第十批 10c）：EC5 新方块，原作没有。吃 MRU，水平半径内（整根柱子上下）不再自然刷出敌对生物，
 * 只在主人自己能动方块的区块里生效；世界里一格不动（只拦刷怪事件）。宅男套装一章的可选步骤。
 * <p>
 * 注册名：{@code force_dome}（方块、方块实体、菜单同名；命名表里是 EC5 新增的一条，old_id 为 null）。
 * 硬度、抗爆照魔力传送器（硬度 1、抗爆按 1.12 规则换算，MapColor.PURPLE），是完整的不透明方块；模型是 cube_bottom_top，顶 / 侧两张贴图（{@code textures/block/force_dome_top.png}、
 * {@code force_dome_side.png}），底面用虚空石。
 * <p>
 * 文字在 {@code tools/lang/keys/ecforcedome.json}，配方 {@code data/essentialcraft5/recipes/computer/force_dome.json}，
 * 手册进度 {@code data/essentialcraft5/advancements/guide/computer/force_dome.json}（第一次开始工作时代码发），
 * 豁免标签 {@code data/essentialcraft5/tags/entity_types/dome_exempt.json}（默认空）。刷怪拦截见 {@code util/ForceDomeIndex}，
 * 客户端界面见 {@code client/ForceDomeClient}。
 */
public final class ECForceDome {

    public static final RegistryObject<ForceDomeBlock> FORCE_DOME = ECBlocks.register("force_dome",
            () -> new ForceDomeBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true))),
            BlockSpec.of((provider, block) -> provider.simpleBlockWithItem(block,
                    provider.models().cubeBottomTop(provider.name(block), provider.tex("block/force_dome_side"),
                            provider.tex("block/voidstone"), provider.tex("block/force_dome_top")))));

    public static final RegistryObject<BlockEntityType<ForceDomeBlockEntity>> FORCE_DOME_BE =
            ECBlockEntities.type("force_dome", ForceDomeBlockEntity::new, FORCE_DOME);

    public static final RegistryObject<MenuType<ForceDomeMenu>> FORCE_DOME_MENU =
            ECMenus.register("force_dome", ForceDomeMenu::new);

    /** 穹顶不拦的敌对生物（默认空；{@code #forge:bosses} 本来就不拦）：给整合包作者留口子 */
    public static final TagKey<EntityType<?>> DOME_EXEMPT = TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id("dome_exempt"));

    /** 手册步骤 computer/force_dome：穹顶第一次开始工作时发给（在线的）主人 */
    public static final ResourceLocation GUIDE_FORCE_DOME = EssentialCraft5.id("guide/computer/force_dome");
    public static final String GUIDE_CRITERION = "awarded_by_code";

    private ECForceDome() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 machines.force_dome */
    public static void init() {
        ECConfig.machine(ForceDomeBlockEntity.Config::define);
    }
}
