package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MetadataManagerBlock;
import com.flameforge.essentialcraft5.block.MimicBlock;
import com.flameforge.essentialcraft5.block.RedstoneTransmitterBlock;
import com.flameforge.essentialcraft5.block.camo.Camo;
import com.flameforge.essentialcraft5.blockentity.MimicBlockEntity;
import com.flameforge.essentialcraft5.blockentity.RedstoneTransmitterBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.RedstoneTransmitterMenu;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.RegistryObject;

/**
 * 拟态方块与红石小件（第四批 4a，batch4 §3.5；§0.0 范围变更后两种方块破坏器已去掉）：拟态方块、红石传递器、方块状态检测器。
 * <p>
 * 新旧注册名：mimic → mimic；redstoneTransmitter → redstone_transmitter；metadataManager → metadata_manager（中文「方块状态检测器」，原作「元数据管理器」）。
 * 原作属性（BlocksCore）：拟态方块 L178 {@code setResistance(15).setHardness(3).setLightOpacity(15)}；传递器 L149 registerBlockSimple(…, 0, 0, 0)；
 * 检测器 L151 registerBlockSimple(…, 1, 1, 15)（registerBlockSimple 先抗爆后硬度）；三个都是 Material.ROCK。
 * <p>
 * 都只动自己这一格（拟态方块改外观、两个红石件改自己的输出状态），不需要主人、不探测领地（policy §4.3 规划补②）。
 * 红石传递器靠绑定宝石绑定时的权限检查（{@code BoundGemItem.useOn} 对目标格 canEdit），之后只读已加载区块里的红石信号。
 * <p>
 * 配方 data/essentialcraft5/recipes/redstonetools/，文字 tools/lang/keys/ecredstonetools.json，标签 tools/tags/ecredstonetools.json
 * （拟态方块进 {@code essentialcraft5:mimic_blacklist}、{@code minecraft:prevent_mob_spawning_inside}），
 * 手册部件 tools/guide/parts/ECRedstoneTools.json（「自动化」章）。客户端部分见 client/RedstoneToolsClient（传递器界面），伪装渲染由框架 client/camo 统一做。
 */
public final class ECRedstoneTools {

    // ---------------- 拟态方块 ----------------

    /**
     * 模型：没伪装时 cube_all（原作 blockstates/mimic.json，贴图 block/mimic），伪装后由框架的伪装模型换成被模仿方块的模型。
     * 没有碰撞箱，但液体流不进来（forceSolidOn）；不刷怪、不闷人、不挡视线；不当红石导体（本来就不是满格碰撞）。
     * 六个面算结实（火把、按钮、红石线能贴）、寻路当墙，都在 {@link MimicBlock} 里覆盖（照原作 ROCK / SOLID）。
     */
    public static final RegistryObject<MimicBlock> MIMIC = ECBlocks.register("mimic",
            () -> new MimicBlock(ECBlocks.machine(MapColor.STONE, 3F, ECBlocks.legacyResistance(3F, 15F, true))
                    .noOcclusion()
                    .forceSolidOn()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)),
            BlockSpec.of(BlockSpec.cubeAll("block/mimic")));

    public static final RegistryObject<BlockEntityType<MimicBlockEntity>> MIMIC_BE =
            ECBlockEntities.type("mimic", MimicBlockEntity::new, MIMIC);

    // ---------------- 红石传递器 ----------------

    /**
     * 模型：原作 JSON（tools/import_assets.py model redstonetransmitter redstone_transmitter minecraft:cutout_mipped），底板朝北；
     * 方块状态照原作 blockstates/redstonetransmitter.json 按朝向转（北 0、南 y180、西 y270、东 y90、下 x90、上 x270），输出强度不影响模型。
     * 属性：瞬间挖掉、空手也掉（见 {@link RedstoneTransmitterBlock} 类注释）、不透光、透天空光、不当红石导体、液体流不进来。
     */
    public static final RegistryObject<RedstoneTransmitterBlock> REDSTONE_TRANSMITTER = ECBlocks.register("redstone_transmitter",
            () -> new RedstoneTransmitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .sound(SoundType.STONE)
                    .instabreak()
                    .noOcclusion()
                    .forceSolidOn()
                    .isRedstoneConductor((state, level, pos) -> false)),
            BlockSpec.of((provider, block) -> {
                ModelFile model = provider.models().getExistingFile(provider.modLoc("block/redstone_transmitter"));
                provider.getVariantBuilder(block).forAllStatesExcept(state -> {
                    Direction facing = state.getValue(RedstoneTransmitterBlock.FACING);
                    return ConfiguredModel.builder().modelFile(model)
                            .rotationX(rotationX(facing)).rotationY(rotationY(facing)).build();
                }, RedstoneTransmitterBlock.POWER);
                provider.simpleBlockItem(block, model);
            }));

    public static final RegistryObject<BlockEntityType<RedstoneTransmitterBlockEntity>> REDSTONE_TRANSMITTER_BE =
            ECBlockEntities.type("redstone_transmitter", RedstoneTransmitterBlockEntity::new, REDSTONE_TRANSMITTER);

    public static final RegistryObject<MenuType<RedstoneTransmitterMenu>> REDSTONE_TRANSMITTER_MENU =
            ECMenus.register("redstone_transmitter", RedstoneTransmitterMenu::new);

    // ---------------- 方块状态检测器 ----------------

    /**
     * 模型：原版 orientable（原作 blockstates/metadatamanager.json：侧面、顶面魔力强化石，正面 block/metadatamanager，uvlock），
     * 按朝向转，输出强度不影响模型。实心、不透光；不当红石导体（同原版侦测器）。
     */
    public static final RegistryObject<MetadataManagerBlock> METADATA_MANAGER = ECBlocks.register("metadata_manager",
            () -> new MetadataManagerBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .isRedstoneConductor((state, level, pos) -> false)),
            BlockSpec.of((provider, block) -> {
                ModelFile model = provider.models().orientable(provider.name(block),
                        provider.tex("block/fortifiedstone"), provider.tex("block/metadatamanager"), provider.tex("block/fortifiedstone"));
                provider.getVariantBuilder(block).forAllStatesExcept(state -> {
                    Direction facing = state.getValue(MetadataManagerBlock.FACING);
                    return ConfiguredModel.builder().modelFile(model)
                            .rotationX(rotationX(facing)).rotationY(rotationY(facing)).uvLock(true).build();
                }, MetadataManagerBlock.POWER);
                provider.simpleBlockItem(block, model);
            }));

    private ECRedstoneTools() {
    }

    /** 正面朝北的模型转到 {@code facing}：上下绕 x 轴（同原版侦测器），水平绕 y 轴 */
    private static int rotationX(Direction facing) {
        return switch (facing) {
            case DOWN -> 90;
            case UP -> 270;
            default -> 0;
        };
    }

    private static int rotationY(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180;
            case WEST -> 270;
            case EAST -> 90;
            default -> 0;
        };
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节和要包伪装模型的方块 */
    public static void init() {
        ECConfig.machine(RedstoneTransmitterBlockEntity.Config::define);
        Camo.registerCamoBlock(MIMIC);
    }
}
