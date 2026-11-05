package com.flameforge.essentialcraft5.loot;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootTableReference;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * 往原版箱子的战利品表里注入本模组的表（第三批框架 G4）。
 * <p>
 * 原作 {@code inject/chests/simple_dungeon.json}（LootTableLoadEvent 里加一个池）：地牢箱子里出特殊饰品。这里同样在
 * {@link LootTableLoadEvent} 给 {@code minecraft:chests/simple_dungeon} 加一个名为 {@value #POOL_NAME} 的池，
 * 这个池抽 1 次、唯一的条目是引用 {@code essentialcraft5:inject/simple_dungeon} 的 loot_table 条目；
 * 真正的抽法（几抽、权重、空条目、每件的 {@code essentialcraft5:not_disabled} 条件）全写在被引用的表里：
 * {@code data/essentialcraft5/loot_tables/inject/simple_dungeon.json}，由 ECBaubles 模块填（框架只放了一个空表）。
 * 上古 MRU 聚集体遗迹笼子里的 4 个箱子用 {@code essentialcraft5:chests/elder_mrucu}，那张表整张引用 simple_dungeon，所以也会出。
 * <p>
 * 注意（Forge 的行为）：服务器装了<b>覆盖</b> simple_dungeon 的数据包时（ForgeHooks.loadLootTable 的 custom = true），
 * Forge 不发 LootTableLoadEvent，注入不生效；要在那种数据包里自己加这个池。
 * <p>
 * 以后别的箱子要注入，在 {@link #INJECTIONS} 里加一行「原版表 → 本模组的 inject 表」，再建对应的 JSON。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class DungeonLootInjection {

    public static final String POOL_NAME = "essentialcraft5:inject";
    public static final ResourceLocation SIMPLE_DUNGEON = EssentialCraft5.id("inject/simple_dungeon");

    /** 原版表 → 注入的本模组表 */
    public static final Map<ResourceLocation, ResourceLocation> INJECTIONS = Map.of(
            BuiltInLootTables.SIMPLE_DUNGEON, SIMPLE_DUNGEON);

    private DungeonLootInjection() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation inject = INJECTIONS.get(event.getName());
        if (inject == null || event.getTable().getPool(POOL_NAME) != null) {
            return;
        }
        event.getTable().addPool(LootPool.lootPool()
                .name(POOL_NAME)
                .setRolls(ConstantValue.exactly(1F))
                .add(LootTableReference.lootTableReference(inject))
                .build());
    }
}
