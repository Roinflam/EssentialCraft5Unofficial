package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 创造 ESPE 源：每 tick 充满，能进能出，测试和创造模式用。对应原作 TileCreativeESPESource（上限默认 1,000,000，
 * 档位 Integer.MAX_VALUE &gt;&gt; 1）。没有 MRU、没有物品栏、没有界面。
 * 放在米斯莱熔炉的水晶柱位置上，熔炉会从它这里抽。
 */
public class CreativeESPEStorageBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.creativeespesource */
    public static final class Config {
        public static ForgeConfigSpec.DoubleValue maxESPE;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("creative_espe_storage");
            maxESPE = builder.defineInRange("max_espe", 1_000_000D, 1D, 1_000_000_000D);
            builder.pop();
        }
    }

    /** 原作 Integer.MAX_VALUE &gt;&gt; 1：比任何机器的档位都高 */
    public static final int ESPE_TIER = Integer.MAX_VALUE >> 1;

    public CreativeESPEStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ECMithriline.CREATIVE_ESPE_STORAGE_BE.get(), pos, state, () -> 0, 0, () -> ECConfig.get(Config.maxESPE), ESPE_TIER);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    protected void serverTick() {
        ESPEStorage storage = getESPEStorage();
        storage.setESPE(storage.getMaxESPE());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }
}
