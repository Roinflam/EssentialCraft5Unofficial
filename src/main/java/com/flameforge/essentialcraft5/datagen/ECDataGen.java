package com.flameforge.essentialcraft5.datagen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 数据生成（./gradlew runData，产物在 src/generated/resources，要提交进仓库）。
 * 方块状态、物品模型、战利品表、标签、中英文语言文件都从这里生成，不要手改生成出来的文件。
 * <p>
 * 原作一个方块战利品表都没有（掉落全写在代码里），1.20.1 每个方块都必须有，漏了就是破坏后什么都不掉。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ECDataGen {

    private ECDataGen() {
    }

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper files = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        generator.addProvider(event.includeClient(), new ECBlockStateProvider(output, files));
        generator.addProvider(event.includeClient(), new ECItemModelProvider(output, files));
        generator.addProvider(event.includeClient(), new ECLanguageProvider(output, "en_us"));
        generator.addProvider(event.includeClient(), new ECLanguageProvider(output, "zh_cn"));
        generator.addProvider(event.includeClient(), new ECSoundsProvider(output));
        // 研究手册校验脚本用的配置键表（assets/essentialcraft5/guide/config_keys.json）
        generator.addProvider(event.includeClient(), new ConfigKeysProvider(output));

        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(ECBlockLoot::new, LootContextParamSets.BLOCK))));
        ECTagProviders.Blocks blockTags = new ECTagProviders.Blocks(output, lookup, files);
        generator.addProvider(event.includeServer(), blockTags);
        generator.addProvider(event.includeServer(), new ECTagProviders.Items(output, lookup, blockTags.contentsGetter(), files));
        // 第六批 6a：海纳尔的噪声设置 + 原版主世界噪声 / 密度函数的副本（D1 A′，见 ECWorldgenProvider、worldgen/hoanna/HoannaTerrain）
        generator.addProvider(event.includeServer(), new ECWorldgenProvider(output, lookup));
    }
}
