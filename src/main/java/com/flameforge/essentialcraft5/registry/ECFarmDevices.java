package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.FarmDeviceBlock;
import com.flameforge.essentialcraft5.blockentity.BreederBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ItemShufflerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ShearingStationBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 农牧红石设备模块（第四批 4a，batch4.md §3.1）：魔力物品洗牌器、魔力繁殖器、魔力剪毛器（方块、方块实体同名）。
 * <p>
 * 原作是一个方块 {@code essentialcraft:device} 带 9 个 meta（{@code BlockRedstoneDeviceNotSided}），这里只移植 meta 1 / 3 / 5：
 * device:1 → {@code item_shuffler}；device:3 → {@code breeder}；device:5 → {@code shearing_station}。
 * 补种器（meta 0）2026-09-28 去掉（机器不放方块，batch4 §0.0）；创造 MRU / ESPE 源（meta 4 / 8）早已移植；合成器、分离器（meta 2 / 6 / 7）在别的模块。
 * 三个方块原作都是硬度 1、抗爆 1（{@code BlocksCore} L168：先 setHardness 再 setResistance），Material.ROCK，整块不透明、六面同贴图。
 * <p>
 * 共同规则（上升沿、主人、探测、候选表）见 {@code blockentity/FarmDeviceBlockEntity}；服务端配置 machines.item_shuffler / breeder / shearing_station。
 * 工作台配方在 data/essentialcraft5/recipes/farmdevices/，实体类型标签 data/essentialcraft5/tags/entity_types/shearing_station_blacklist.json，
 * 手册「农牧自动化」章 assets/essentialcraft5/guide/farming.json（文字在 tools/lang/keys/ecfarmdevices.json）。没有客户端部分（没有界面、没有特殊渲染）。
 */
public final class ECFarmDevices {

    /** 剪毛器不剪的生物（默认哞菇、雪傀儡；服主可用数据包往里加）。静态 JSON：data/essentialcraft5/tags/entity_types/shearing_station_blacklist.json */
    public static final TagKey<EntityType<?>> SHEARING_STATION_BLACKLIST =
            TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id("shearing_station_blacklist"));

    // ---------------- 魔力物品洗牌器 ----------------

    public static final RegistryObject<FarmDeviceBlock<ItemShufflerBlockEntity>> ITEM_SHUFFLER = ECBlocks.register("item_shuffler",
            () -> new FarmDeviceBlock<>(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, false)),
                    () -> ECFarmDevices.ITEM_SHUFFLER_BE.get()),
            BlockSpec.of(BlockSpec.cubeAll("block/itemshuffler")));

    public static final RegistryObject<BlockEntityType<ItemShufflerBlockEntity>> ITEM_SHUFFLER_BE =
            ECBlockEntities.type("item_shuffler", ItemShufflerBlockEntity::new, ITEM_SHUFFLER);

    // ---------------- 魔力繁殖器 ----------------

    public static final RegistryObject<FarmDeviceBlock<BreederBlockEntity>> BREEDER = ECBlocks.register("breeder",
            () -> new FarmDeviceBlock<>(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, false)),
                    () -> ECFarmDevices.BREEDER_BE.get()),
            BlockSpec.of(BlockSpec.cubeAll("block/breeder")));

    public static final RegistryObject<BlockEntityType<BreederBlockEntity>> BREEDER_BE =
            ECBlockEntities.type("breeder", BreederBlockEntity::new, BREEDER);

    // ---------------- 魔力剪毛器 ----------------

    public static final RegistryObject<FarmDeviceBlock<ShearingStationBlockEntity>> SHEARING_STATION = ECBlocks.register("shearing_station",
            () -> new FarmDeviceBlock<>(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, false)),
                    () -> ECFarmDevices.SHEARING_STATION_BE.get()),
            BlockSpec.of(BlockSpec.cubeAll("block/shearingstation")));

    public static final RegistryObject<BlockEntityType<ShearingStationBlockEntity>> SHEARING_STATION_BE =
            ECBlockEntities.type("shearing_station", ShearingStationBlockEntity::new, SHEARING_STATION);

    private ECFarmDevices() {
    }

    public static void init() {
        ECConfig.machine(ItemShufflerBlockEntity.Config::define);
        ECConfig.machine(BreederBlockEntity.Config::define);
        ECConfig.machine(ShearingStationBlockEntity.Config::define);
    }
}
