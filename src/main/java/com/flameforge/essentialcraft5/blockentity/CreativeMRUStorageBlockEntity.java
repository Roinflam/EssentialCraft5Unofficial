package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/** 创造 MRU 源：每 tick 充满。对应原作 TileCreativeMRUSource（上限默认 1,000,000）。 */
public class CreativeMRUStorageBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.creativemrusource */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("creative_mru_storage");
            maxMRU = builder.defineInRange("max_mru", ECConfig.GENERATOR_MAX_MRU * 100, 1, Integer.MAX_VALUE);
            builder.pop();
        }
    }

    public CreativeMRUStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ECBlockEntities.CREATIVE_MRU_STORAGE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 0);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    protected void serverTick() {
        mru.setMRU(mru.getMaxMRU());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }
}
