package com.flameforge.essentialcraft5.worldgen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.MobSpawnSettingsBuilder;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.Map;

/**
 * 生物群系修改器 {@code essentialcraft5:add_spawn_costs}（13c-Fa，batch13.md §5.1「生成规则」）：给群系的刷怪设置加「刷怪花费」
 * （原版群系 JSON 里的 {@code spawn_costs}：每只占多少「电荷」、附近的电荷总预算是多少，原版灵魂沙峡谷、诡异森林用它压密度）。
 * Forge 47 只有 {@code forge:add_spawns}（加刷怪表）、没有自带加花费的修改器，所以自己写一个，和 {@code forge:add_spawns} 搭着用：
 * <pre>
 * {
 *   "type": "essentialcraft5:add_spawn_costs",
 *   "biomes": "#minecraft:is_nether",                       // 群系 id、[列表] 或 #标签
 *   "spawn_costs": {
 *     "essentialcraft5:nether_imp": { "energy_budget": 0.12, "charge": 0.7 }
 *   }
 * }
 * </pre>
 * 写法和原版群系 JSON 的 {@code spawn_costs} 一样（键是实体类型，值是 {@code energy_budget} 和 {@code charge}）；同一种实体已经有花费的，后写的盖掉前面的。
 * 在 ADD 阶段改，和 {@code forge:add_spawns} 同阶段。只改刷怪设置，不碰地形。
 */
public record AddSpawnCostsBiomeModifier(HolderSet<Biome> biomes, Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> costs) implements BiomeModifier {

    public static final ResourceLocation ID = EssentialCraft5.id("add_spawn_costs");

    public static final Codec<AddSpawnCostsBiomeModifier> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(AddSpawnCostsBiomeModifier::biomes),
            Codec.simpleMap(BuiltInRegistries.ENTITY_TYPE.byNameCodec(), MobSpawnSettings.MobSpawnCost.CODEC, BuiltInRegistries.ENTITY_TYPE)
                    .fieldOf("spawn_costs").forGetter(AddSpawnCostsBiomeModifier::costs)
    ).apply(builder, AddSpawnCostsBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD || !biomes.contains(biome)) {
            return;
        }
        apply(builder.getMobSpawnSettings());
    }

    /** 把花费写进刷怪设置（GameTest 也直接调） */
    public void apply(MobSpawnSettingsBuilder spawns) {
        costs.forEach((type, cost) -> spawns.addMobCharge(type, cost.charge(), cost.energyBudget()));
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return CODEC;
    }

    /** 把编解码器登记进 Forge 的生物群系修改器类型表（Forge 自动发现） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class SpawnCostsRegistration {

        private SpawnCostsRegistration() {
        }

        @SubscribeEvent
        public static void registerSpawnCosts(RegisterEvent event) {
            event.register(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, ID, () -> CODEC);
        }
    }
}
