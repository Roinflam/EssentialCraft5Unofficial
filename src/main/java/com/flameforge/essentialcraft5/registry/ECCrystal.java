package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.CrystalFormerBlock;
import com.flameforge.essentialcraft5.block.ElementalCrystalBlock;
import com.flameforge.essentialcraft5.blockentity.CrystalFormerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ElementalCrystalBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.ElementalCrystalItem;
import com.flameforge.essentialcraft5.menu.CrystalFormerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyNbtFunction;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.RegistryObject;

/**
 * 元素水晶模块（第二批）：元素水晶、水晶成型器，外加月亮井的工作台配方（月亮井本身一批已移植，见 ECBlocks.MOON_WELL）。
 * <p>
 * 新旧注册名：elementalCrystal → elemental_crystal（方块、方块实体同名；物品是 {@link ElementalCrystalItem}）；
 * crystalFormer → crystal_former（方块、方块实体、菜单同名）。
 * <p>
 * 水晶的数据（大小、四元素）存在方块实体里，挖下来走战利品表的 copy_nbt 放进物品的 BlockEntityTag，放回去时原版 BlockItem 自动读回。
 * 别的二批模块（ECEssence 的水晶控制器、提取器）只通过框架接口 {@code api/IElementalCrystal} 读写水晶，不 import 这里的类。
 * <p>
 * 配方在 data/essentialcraft5/recipes/crystal/（crystal_former、moon_well），文字在 tools/lang/keys/eccrystal.json，
 * 研究手册章节在 assets/essentialcraft5/guide/crystal.json。客户端部分（界面、两个 BER、物品颜色和大小图标）见 client/CrystalClient。
 */
public final class ECCrystal {

    /** 物品按大小分 21 档图标（原作 elementalcrystalinv 的 size=0~20 变体），图标贴图 textures/block/icons/elementalcrystal0~20 */
    private static final String ICON = "block/icons/elementalcrystal";

    // ---------------- 元素水晶 ----------------

    /**
     * 原作 elementalCrystal：Material.ROCK，setHardness(3).setResistance(15)（先硬度后抗爆 → 抗爆 9），不透光（isOpaqueCube=false → 不透光度 0）。
     * 碰撞箱是整格（原作如此），但原作 isFullCube=false，所以不导红石、不算窒息、不挡视线：1.20.1 这三项默认看「碰撞箱是不是整格」，
     * 不关掉的话水晶会像石头一样被拉杆、红石粉充能，贴着它的机器（成型器、水晶控制器、提取器）就会红石停机。三项照原版玻璃显式关掉。
     * 模型只有破坏粒子（原作 blockstate 同样是空模型 + 玻璃粒子），水晶本体由 BER 画。
     * 物品模型：大小 0 档是本体，1~20 档按 {@link ElementalCrystalItem#SIZE_PREDICATE} 覆盖（阈值 = 档位 / 20，从小到大写，后面的优先）。
     * 掉落：自己，并把方块实体的 size / fire / water / earth / air 复制进 BlockEntityTag（原作挖下来和支撑方块没了两条路径，后者丢数据，这里统一走战利品表）。
     */
    public static final RegistryObject<ElementalCrystalBlock> ELEMENTAL_CRYSTAL = ECBlocks.register("elemental_crystal",
            () -> new ElementalCrystalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .sound(SoundType.STONE)
                    .strength(3F, ECBlocks.legacyResistance(3F, 15F, false))
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .lightLevel(ElementalCrystalBlock::lightEmission)),
            BlockSpec.of((provider, block) -> {
                ModelFile model = provider.models().getBuilder("elemental_crystal")
                        .texture("particle", provider.mcLoc("block/glass"));
                provider.simpleBlock(block, model);
                ItemModelBuilder item = provider.itemModels().withExistingParent("elemental_crystal", provider.mcLoc("item/generated"))
                        .texture("layer0", provider.modLoc(ICON + 0));
                for (int stage = 1; stage <= ElementalCrystalItem.STAGES; stage++) {
                    ModelFile stageModel = provider.itemModels().withExistingParent("elemental_crystal_" + stage, provider.mcLoc("item/generated"))
                            .texture("layer0", provider.modLoc(ICON + stage));
                    item.override().predicate(ElementalCrystalItem.SIZE_PREDICATE, ElementalCrystalItem.stageThreshold(stage)).model(stageModel).end();
                }
            }).loot((loot, block) -> {
                CopyNbtFunction.Builder copy = CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY);
                for (String key : ElementalCrystalBlockEntity.DATA_KEYS) {
                    copy.copy(key, "BlockEntityTag." + key);
                }
                loot.table(block, LootTable.lootTable().withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1F))
                        .when(ExplosionCondition.survivesExplosion())
                        .add(LootItem.lootTableItem(block).apply(copy))));
            }).item(block -> new ElementalCrystalItem(block, new Item.Properties())));

    public static final RegistryObject<BlockEntityType<ElementalCrystalBlockEntity>> ELEMENTAL_CRYSTAL_BE =
            ECBlockEntities.type("elemental_crystal", ElementalCrystalBlockEntity::new, ELEMENTAL_CRYSTAL);

    // ---------------- 水晶成型器 ----------------

    /**
     * 原作 crystalFormer：registerBlockSimple(…, 1, 1, 0)（先抗爆后硬度 → 抗爆 1.0，不透光度 0 → 透天空光），
     * Material.ROCK，OBJ 模型（原作 blockstate flip-v）。顶上 4 颗小水晶由 BER 画（和元素水晶共用模型）。
     */
    public static final RegistryObject<CrystalFormerBlock> CRYSTAL_FORMER = ECBlocks.register("crystal_former",
            () -> new CrystalFormerBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("crystalformer", "block/model/crystalformer")));

    public static final RegistryObject<BlockEntityType<CrystalFormerBlockEntity>> CRYSTAL_FORMER_BE =
            ECBlockEntities.type("crystal_former", CrystalFormerBlockEntity::new, CRYSTAL_FORMER);

    public static final RegistryObject<MenuType<CrystalFormerMenu>> CRYSTAL_FORMER_MENU =
            ECMenus.register("crystal_former", CrystalFormerMenu::new);

    private ECCrystal() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记两个配置小节 */
    public static void init() {
        ECConfig.machine(ElementalCrystalBlockEntity.Config::define);
        ECConfig.machine(CrystalFormerBlockEntity.Config::define);
    }
}
