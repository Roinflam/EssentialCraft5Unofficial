package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.LeyCondenserBlock;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.LeyCondenserMenu;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 地脉凝聚器模块（第九批，batch9.md；policy §4.9）：EC5 新机器，原作没有。后期按需凝聚指定的原版矿物（含远古残骸）和下界之星，
 * 世界里一格不动；配方吃掉一台地脉投影仪（玩法上是投影仪的升级），要第十八章打倒全息投影掉的数学核心。
 * <p>
 * 注册名：{@code ley_condenser}（方块、方块实体、菜单同名；命名表里是 EC5 新增的一条，old_id 为 null）。
 * 方块属性照投影仪（硬度 1、抗爆按 1.12 规则换算、透天空光、MapColor.PURPLE）；模型 {@code models/block/ley_condenser.json}
 * 继承投影仪的模型、只换外壳贴图（强化石 → 虚空石，魔力护板 → 恶魔护板）。
 * <p>
 * 文字在 {@code tools/lang/keys/ecleycondenser.json}，数学核心标签在 {@code tools/tags/ecleycondenser.json}，
 * 配方 {@code data/essentialcraft5/recipes/leycondenser/}，手册支线 {@code guide/<语言>/ley.json} 的 ley_condenser、condensing 两条。
 * 客户端部分（界面、影像与光束）见 {@code client/LeyCondenserClient}。
 */
public final class ECLeyCondenser {

    public static final RegistryObject<LeyCondenserBlock> LEY_CONDENSER = ECBlocks.register("ley_condenser",
            () -> new LeyCondenserBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion()),
            BlockSpec.of(BlockSpec.existing("ley_condenser")));

    public static final RegistryObject<BlockEntityType<LeyCondenserBlockEntity>> LEY_CONDENSER_BE =
            ECBlockEntities.type("ley_condenser", LeyCondenserBlockEntity::new, LEY_CONDENSER);

    public static final RegistryObject<MenuType<LeyCondenserMenu>> LEY_CONDENSER_MENU =
            ECMenus.register("ley_condenser", LeyCondenserMenu::new);

    /** 四种数学核心（加 / 减 / 乘 / 除法核心）：凝聚器配方里任选一种 */
    public static final TagKey<Item> MATH_CORES = ECTags.modItem("math_cores");

    private ECLeyCondenser() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 machines.ley_condenser */
    public static void init() {
        ECConfig.machine(LeyCondenserBlockEntity.Config::define);
    }
}
