package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.blockentity.ChargingChamberBlockEntity;
import com.flameforge.essentialcraft5.blockentity.CreativeMRUStorageBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MoonWellBlockEntity;
import com.flameforge.essentialcraft5.client.HudClientConfig;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * 方块实体类型。原作注册名是 "essentialcraft:" + 类全名（含大写和点号，1.20.1 不合法），这里与方块同名。
 */
public final class ECBlockEntities {

    public static final RegistryObject<BlockEntityType<MoonWellBlockEntity>> MOON_WELL =
            register("moon_well", MoonWellBlockEntity::new, ECBlocks.MOON_WELL);
    public static final RegistryObject<BlockEntityType<ChargingChamberBlockEntity>> CHARGING_CHAMBER =
            register("charging_chamber", ChargingChamberBlockEntity::new, ECBlocks.CHARGING_CHAMBER);
    public static final RegistryObject<BlockEntityType<CreativeMRUStorageBlockEntity>> CREATIVE_MRU_STORAGE =
            register("creative_mru_storage", CreativeMRUStorageBlockEntity::new, ECBlocks.CREATIVE_MRU_STORAGE);

    private ECBlockEntities() {
    }

    static void init() {
        ECConfig.machine(MoonWellBlockEntity.Config::define);
        ECConfig.machine(ChargingChamberBlockEntity.Config::define);
        ECConfig.machine(CreativeMRUStorageBlockEntity.Config::define);
        // 快捷栏旁的护甲电量条（配着充能仓给主人盔甲充电做的，第十三批 13b）
        ECConfig.client(HudClientConfig::define);
    }

    /** 给模块用：注册方块实体类型（名字一般和方块相同） */
    @SafeVarargs
    public static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> type(
            String name, BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block>... blocks) {
        return register(name, factory, blocks);
    }

    @SafeVarargs
    private static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> register(
            String name, BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block>... blocks) {
        return ECRegistries.BLOCK_ENTITY_TYPES.register(name, () -> {
            Block[] valid = new Block[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                valid[i] = blocks[i].get();
            }
            // 参数是 DataFixer 用的 Type，模组传 null
            return BlockEntityType.Builder.of(factory, valid).build(null);
        });
    }
}
