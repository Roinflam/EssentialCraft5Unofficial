package com.flameforge.essentialcraft5.datagen;

import com.flameforge.essentialcraft5.registry.ECBlocks;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 方块战利品表：遍历 ECBlocks 里登记的方块，执行各自 BlockSpec 的掉落生成（默认掉自己）。
 * 原作一个方块战利品表都没有（掉落全写在代码里），1.20.1 每个方块都必须有：
 * getKnownBlocks 返回全部方块，漏写任何一个 datagen 直接报错。
 * <p>
 * BlockLootSubProvider 的工具方法大多是 protected，这里包一层 public 给 BlockSpec 的 lambda 用；不够再加。
 */
public class ECBlockLoot extends BlockLootSubProvider {

    public ECBlockLoot() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        for (ECBlocks.Entry entry : ECBlocks.entries()) {
            entry.spec().lootGen().generate(this, entry.block().get());
        }
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ECBlocks.all().stream().map(RegistryObject::get).collect(Collectors.toList());
    }

    /** 掉自己 */
    public void self(Block block) {
        dropSelf(block);
    }

    /** 不掉任何东西（写一张空表；技术性方块） */
    public void none(Block block) {
        add(block, LootTable.lootTable());
    }

    /** 只有精准采集才掉自己，否则什么都不掉（原版玻璃、冰的写法；第十三批 13d 的霜髓） */
    public void silkTouchOnly(Block block) {
        dropWhenSilkTouch(block);
    }

    /** 直接给整张表 */
    public void table(Block block, LootTable.Builder table) {
        add(block, table);
    }

    /** 精准采集掉自己，否则掉 drop，否则受爆炸衰减（原版矿石的写法） */
    public LootTable.Builder silkTouchOr(Block block, LootPoolSingletonContainer.Builder<?> otherwise) {
        return createSilkTouchDispatchTable(block, applyExplosionDecay(block, otherwise));
    }

    /**
     * 矿石式掉落：精准采集掉矿石本身；否则掉 min~max 个 drop，时运按原版矿石公式加成。
     * 原作的时运公式各不相同，需要精确还原时在模块里自己拼表，用 {@link #table}。
     */
    public void ore(Block block, ItemLike drop, float min, float max) {
        add(block, silkTouchOr(block, LootItem.lootTableItem(drop)
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)))
                .apply(ApplyBonusCount.addOreBonusCount(Enchantments.BLOCK_FORTUNE))));
    }

    /** 掉 min~max 个 drop（不受时运影响，精准采集也一样） */
    public void counted(Block block, ItemLike drop, float min, float max) {
        add(block, LootTable.lootTable().withPool(applyExplosionCondition(block,
                net.minecraft.world.level.storage.loot.LootPool.lootPool().add(LootItem.lootTableItem(drop)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)))))));
    }
}
