package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.ConverterTier;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.Mode;
import com.flameforge.essentialcraft5.compat.botania.ManaBridge;
import com.flameforge.essentialcraft5.item.creative.CreativeOnly;
import com.flameforge.essentialcraft5.registry.BlockSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * MRU 转换器（五台共用，档位见 {@link ConverterTier}；逻辑在 {@link MRUConverterBlockEntity}）。
 * <p>
 * 方块状态两个属性：{@link #MODE}（能量 / 魔力，界面上切）和 {@link #ACTIVE}（最近 1 秒推出去过，侧面输出口叠层换成流动的那张）。
 * 没有朝向，六面一样。两个属性只由方块实体改，改的时候只发客户端更新、不发邻居更新（不然邻居的 neighborChanged 又回头作废自己的缓存）。
 * <p>
 * 邻居变化（{@link #neighborChanged}：放下 / 拆掉方块、红石变化；Forge 的 {@link #onNeighborChange}：邻居的方块实体内容变化）转给方块实体，
 * 它按来源坐标作废那一面的能力缓存、重读红石。放下时记主人（{@link MachineBlock}）和放下的时间（每人全服台数按放下先后排，
 * 同地脉凝聚器：创造模式 Ctrl+中键复制出来的带着旧时间也会被改成「现在」，不能插队）。
 * <p>
 * 打开界面时额外数据里除了坐标再写一个档位序号：客户端菜单按它定槽数（无尽档没有宝石槽），不依赖客户端世界里的方块。
 */
public class MRUConverterBlock extends MachineBlock<MRUConverterBlockEntity> {

    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final ConverterTier tier;

    public MRUConverterBlock(ConverterTier tier, Properties properties) {
        super(properties, false);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(MODE, Mode.ENERGY).setValue(ACTIVE, false));
    }

    public ConverterTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE, ACTIVE);
    }

    @Override
    protected BlockEntityType<MRUConverterBlockEntity> blockEntityType() {
        return tier.blockEntityType();
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MRUConverterBlockEntity be) {
            NetworkHooks.openScreen(serverPlayer, be, buf -> {
                buf.writeBlockPos(pos);
                buf.writeByte(tier.ordinal());
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MRUConverterBlockEntity be) {
            be.markPlaced(level.getGameTime());
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, isMoving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MRUConverterBlockEntity be) {
            be.onNeighborChanged(fromPos);
        }
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MRUConverterBlockEntity be) {
            be.onNeighborChanged(neighbor);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        boolean botania = ModList.get() != null && ModList.get().isLoaded(ManaBridge.MODID);
        String fe = MRUConverterBlockEntity.formatMilli(tier.outputMilli(Mode.ENERGY));
        String mana = MRUConverterBlockEntity.formatMilli(tier.outputMilli(Mode.MANA));
        if (tier == ConverterTier.INFINITE) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.creative.infinite_mru_converter").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_converter.infinite_rate", fe).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_converter").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_converter.rate", tier.maxPerTick(), fe).withStyle(ChatFormatting.GRAY));
        }
        if (botania && tier.outputMilli(Mode.MANA) > 0) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_converter.rate_mana", mana).withStyle(ChatFormatting.GRAY));
        }
        if (tier == ConverterTier.INFINITE) {
            // 无尽道具的最后一行（同 CreativeOnly 的方块物品）
            tooltip.add(CreativeOnly.tooltipLine());
        }
    }

    /**
     * datagen 用的模型：每档 4 个（能量 / 魔力 × 待机 / 工作），都继承 {@code block/mru_converter_base}，只换侧面中间输出口那层 {@code #port}；
     * 方块状态按 mode、active 选，物品模型用能量待机那个。贴图路径 {@code block/mru_converter/<prefix>_{top,bottom,side}}、
     * {@code block/mru_converter/port_{energy,mana}[_active]}（画师的图按这些文件名覆盖，模型不用再改）。
     */
    public static BlockSpec.ModelGen models(String prefix) {
        return (provider, block) -> {
            String name = provider.name(block);
            String base = "block/mru_converter/" + prefix;
            ModelFile[][] files = new ModelFile[Mode.values().length][2];
            for (Mode mode : Mode.values()) {
                for (int active = 0; active < 2; active++) {
                    String suffix = "_" + mode.getSerializedName() + (active == 1 ? "_active" : "");
                    files[mode.ordinal()][active] = provider.models()
                            .withExistingParent(name + suffix, provider.modLoc("block/mru_converter_base"))
                            .texture("top", provider.tex(base + "_top"))
                            .texture("bottom", provider.tex(base + "_bottom"))
                            .texture("side", provider.tex(base + "_side"))
                            .texture("particle", provider.tex(base + "_side"))
                            .texture("port", provider.tex("block/mru_converter/port" + suffix))
                            .renderType("cutout");
                }
            }
            provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(files[state.getValue(MODE).ordinal()][state.getValue(ACTIVE) ? 1 : 0]).build());
            provider.simpleBlockItem(block, files[Mode.ENERGY.ordinal()][0]);
        };
    }
}
