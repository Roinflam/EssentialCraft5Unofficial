package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MagicalChestBlock;
import com.flameforge.essentialcraft5.block.MagicalDisplayBlock;
import com.flameforge.essentialcraft5.blockentity.MagicalChestBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalDisplayBlockEntity;
import com.flameforge.essentialcraft5.menu.MagicalChestMenu;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.RegistryObject;

/**
 * 魔力箱与显示屏模块（第四批 4a）：魔力箱（54 格一格大箱子）、魔力显示屏（贴墙展示一件物品）；第六批 6b 加虚空箱（117 格，batch6b §4.4）。
 * 方块实体都<b>不继承 MRUBlockEntity</b>（不是 MRU 机器；MRUBlockEntity 的更新包是整份存档数据，会把箱子内容广播出去）。
 * <p>
 * 新旧注册名：chest（meta 0，ChestType.MAGICAL）→ magical_chest；chest（meta 1，ChestType.VOID）→ void_chest；magicalDisplay → magical_display。
 * 虚空箱和魔力箱是同一个方块类、方块实体类、菜单类、界面、渲染器，只换 {@link MagicalChestBlock.Layout}；方块实体类型和菜单类型各开一个
 * （不动魔力箱已有的 magical_chest 类型，老存档里的魔力箱不受影响）。魔力箱已修的问题（不广播整箱 NBT、不移植远程看箱子的包、开合计数、
 * 朝向用方块状态、格数取自 Layout 并在读档时统一、Clearable、撒出时交出那一堆、ChestSlot.setChanged）虚空箱直接继承；
 * 原作虚空箱「格数取方块实体 NBT、菜单取方块 meta，不一致就越界崩」从结构上不会再出现。
 * 三个方块原作都是 registerBlockSimple(…, 1, 1, 0)（先抗爆后硬度 → 硬度 1、抗爆 1.0，透光），Material.ROCK；
 * 魔力箱 MapColor.PURPLE，虚空箱 MapColor.BLACK，显示屏 MapColor.AIR（→ NONE）。
 * <p>
 * 不需要主人、不动世界（policy §4.3 规划补 ②：只动自己的格子和物品栏）；别人开你的箱子、点你的屏是真玩家的交互，由领地插件管。
 * 工作台配方在 data/essentialcraft5/recipes/storage/，文字在 tools/lang/keys/ecstorage.json，
 * 手册是「物流」章的部件 tools/guide/parts/ECStorage.json（章归 ECLogistics）。客户端部分见 client/StorageClient。
 */
public final class ECStorage {

    // ---------------- 魔力箱 ----------------

    /**
     * 方块模型只有破坏粒子（原作 blockstate chest.json：minecraft:block + magicplatingblock），箱子由方块实体渲染器画；
     * 物品模型是原作的 chestinv（导入为 models/block/legacy_chest.json，箱子贴图的 UV 直接写在模型里，贴图变量 #chest）。
     */
    public static final RegistryObject<MagicalChestBlock> MAGICAL_CHEST = ECBlocks.register("magical_chest",
            () -> new MagicalChestBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true)),
                    MagicalChestBlock.Layout.MAGICAL, () -> ECStorage.MAGICAL_CHEST_BE.get(), () -> ECStorage.MAGICAL_CHEST_MENU.get()),
            BlockSpec.of((provider, block) -> {
                ModelFile particle = provider.models().getBuilder(provider.name(block))
                        .texture("particle", provider.modLoc("block/magicplatingblock"));
                provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder().modelFile(particle).build());
                provider.itemModels().withExistingParent(provider.name(block), provider.modLoc("block/legacy_chest"))
                        .texture("chest", provider.modLoc("block/chests/magical"))
                        .texture("particle", provider.modLoc("block/magicplatingblock"));
            }));

    public static final RegistryObject<BlockEntityType<MagicalChestBlockEntity>> MAGICAL_CHEST_BE =
            ECBlockEntities.type("magical_chest",
                    (pos, state) -> new MagicalChestBlockEntity(ECStorage.MAGICAL_CHEST_BE.get(), pos, state), MAGICAL_CHEST);

    public static final RegistryObject<MenuType<MagicalChestMenu>> MAGICAL_CHEST_MENU = ECMenus.register("magical_chest",
            (containerId, playerInventory, buf) -> MagicalChestMenu.client(ECStorage.MAGICAL_CHEST_MENU.get(), containerId,
                    playerInventory, MagicalChestBlock.Layout.MAGICAL));

    // ---------------- 虚空箱 ----------------

    /**
     * 原作 chest meta 1：117 格（9 行 × 13 列），界面 256 × 256，地图色黑；要镐、潜行右键不开、挖掉撒出，都和魔力箱一样。
     * 方块模型只有破坏粒子（虚空石），物品模型同魔力箱的 legacy_chest，箱子贴图换成 chests/void。配方 recipes/storage/void_chest.json（8 块虚空石）。
     */
    public static final RegistryObject<MagicalChestBlock> VOID_CHEST = ECBlocks.register("void_chest",
            () -> new MagicalChestBlock(ECBlocks.machine(MapColor.COLOR_BLACK, 1F, ECBlocks.legacyResistance(1F, 1F, true)),
                    MagicalChestBlock.Layout.VOID, () -> ECStorage.VOID_CHEST_BE.get(), () -> ECStorage.VOID_CHEST_MENU.get()),
            BlockSpec.of((provider, block) -> {
                ModelFile particle = provider.models().getBuilder(provider.name(block))
                        .texture("particle", provider.modLoc("block/voidstone"));
                provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder().modelFile(particle).build());
                provider.itemModels().withExistingParent(provider.name(block), provider.modLoc("block/legacy_chest"))
                        .texture("chest", provider.modLoc("block/chests/void"))
                        .texture("particle", provider.modLoc("block/voidstone"));
            }));

    public static final RegistryObject<BlockEntityType<MagicalChestBlockEntity>> VOID_CHEST_BE =
            ECBlockEntities.type("void_chest",
                    (pos, state) -> new MagicalChestBlockEntity(ECStorage.VOID_CHEST_BE.get(), pos, state), VOID_CHEST);

    public static final RegistryObject<MenuType<MagicalChestMenu>> VOID_CHEST_MENU = ECMenus.register("void_chest",
            (containerId, playerInventory, buf) -> MagicalChestMenu.client(ECStorage.VOID_CHEST_MENU.get(), containerId,
                    playerInventory, MagicalChestBlock.Layout.VOID));

    // ---------------- 魔力显示屏 ----------------

    /**
     * 原作 JSON 模型（导入为 models/block/magical_display.json：薄板贴在南边、正面朝北），blockstate 照原作按朝向转
     * （北 0、南 y180、西 y270、东 y90、下 x90、上 x270；原作的负角度换成 270）。掉自己，屏上的物品在 onRemove 里掉。
     */
    public static final RegistryObject<MagicalDisplayBlock> MAGICAL_DISPLAY = ECBlocks.register("magical_display",
            () -> new MagicalDisplayBlock(ECBlocks.machine(MapColor.NONE, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion(),
                    () -> ECStorage.MAGICAL_DISPLAY_BE.get()),
            BlockSpec.of((provider, block) -> {
                ModelFile model = provider.models().getExistingFile(provider.modLoc("block/magical_display"));
                provider.getVariantBuilder(block).forAllStates(state -> {
                    Direction facing = state.getValue(MagicalDisplayBlock.FACING);
                    int x = facing == Direction.DOWN ? 90 : facing == Direction.UP ? 270 : 0;
                    int y = switch (facing) {
                        case SOUTH -> 180;
                        case WEST -> 270;
                        case EAST -> 90;
                        default -> 0;
                    };
                    return ConfiguredModel.builder().modelFile(model).rotationX(x).rotationY(y).build();
                });
                provider.simpleBlockItem(block, model);
            }));

    public static final RegistryObject<BlockEntityType<MagicalDisplayBlockEntity>> MAGICAL_DISPLAY_BE =
            ECBlockEntities.type("magical_display",
                    (pos, state) -> new MagicalDisplayBlockEntity(ECStorage.MAGICAL_DISPLAY_BE.get(), pos, state), MAGICAL_DISPLAY);

    private ECStorage() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister（三个方块都没有配置项） */
    public static void init() {
    }
}
