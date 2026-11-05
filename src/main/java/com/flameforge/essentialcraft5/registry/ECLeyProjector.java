package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.LeyProjectorBlock;
import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.LeyProjectorMenu;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 地脉投影仪模块（第五批，batch5.md §3.4；policy §4.8）：原作魔力采石场的继承者，EC5 新机器（2026-09-28 定，removed_redesign §3.3）。
 * 世界里一格不动：用 MRU 在机器里「投影」出所在维度岩层会有的矿，进机器自己的 9 个输出格。
 * <p>
 * 注册名：{@code ley_projector}（方块、方块实体、菜单同名；命名表里是 EC5 新增的一条，old_id 为 null）。原作 {@code magicalQuarry} 维持 removed。
 * 方块照原作 {@code registerBlockSimple(new BlockMagicalQuarry(), "magicalQuarry", 1, 1, 0)}：硬度 1、抗爆按 1.12 规则换算、不透光度 0（透天空光），
 * MapColor.PURPLE；模型是原作 magicalquarry.json（{@code tools/import_assets.py model magicalquarry ley_projector minecraft:cutout_mipped}，
 * 导入后把 cube_7 下表面的 {@code #-1} 改成了 {@code #3}：原作模型 bug，1.20.1 会画成紫黑块）。
 * <p>
 * 文字在 {@code tools/lang/keys/ecleyprojector.json}，透镜标签在 {@code tools/tags/ecleyprojector.json}，
 * 配方 {@code data/essentialcraft5/recipes/leyprojector/}，投影表 {@code data/essentialcraft5/loot_tables/ley_projector/}
 * （由 {@code tools/gen/ley_projector_tables.py} 生成，改数值改脚本再重跑），手册支线 {@code guide/<语言>/ley.json}（order 83）。
 * 客户端部分（界面、光束）见 {@code client/LeyProjectorClient}。
 */
public final class ECLeyProjector {

    public static final RegistryObject<LeyProjectorBlock> LEY_PROJECTOR = ECBlocks.register("ley_projector",
            () -> new LeyProjectorBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion()),
            BlockSpec.of(BlockSpec.existing("ley_projector")));

    public static final RegistryObject<BlockEntityType<LeyProjectorBlockEntity>> LEY_PROJECTOR_BE =
            ECBlockEntities.type("ley_projector", LeyProjectorBlockEntity::new, LEY_PROJECTOR);

    public static final RegistryObject<MenuType<LeyProjectorMenu>> LEY_PROJECTOR_MENU =
            ECMenus.register("ley_projector", LeyProjectorMenu::new);

    /** 透镜格收的物品（淡青宝石）：放 1 个 = 投影出的矿按时运 I 掉落 */
    public static final TagKey<Item> LENSES = ECTags.modItem("ley_projector_lenses");

    private ECLeyProjector() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 machines.ley_projector */
    public static void init() {
        ECConfig.machine(LeyProjectorBlockEntity.Config::define);
    }
}
