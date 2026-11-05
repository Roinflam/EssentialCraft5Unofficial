package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MagmaticFurnaceBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMagmatic;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * 岩浆熔炉：强化 / 魔力 / 淡青 / 虚空四档（原作 BlockFurnaceMagic 的 TYPE 0~3，一个方块类、四个注册名；虚空级第六批 6b 注册）。
 * 档位只影响速度（{@link Tier#speed()}）。原作 setLightOpacity(15)：实心、不透光；外观是原版 orientable 模型，炉口朝放置者。
 * <p>
 * 原作 onBlockAdded 里的 setBlockRotation 把类型号当朝向号加进 meta，非玩家放置（结构、指令）时会把档位改成强化级；
 * 这里档位就是方块本身，朝向单独一个属性，没有这个问题。
 */
public class MagmaticFurnaceBlock extends MachineBlock<MagmaticFurnaceBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * 档位：速度倍率（原作 meta / 4 + 1）、地图颜色、外观贴图（原作 blockstates/furnacemagic.json：侧面和顶面、炉口）。
     */
    public enum Tier {
        FORTIFIED(1, MapColor.STONE, "block/fortifiedstone", "block/furnace_fortified"),
        MAGIC(2, MapColor.COLOR_PURPLE, "block/magicplatingblock", "block/furnace_magic"),
        PALE(3, MapColor.LAPIS, "block/paleplatingblock", "block/furnace_pale"),
        /** 虚空级：虚空板只出海纳尔遗迹的箱子，第六批 6b 注册（每道工序 400 / 4 = 100 tick） */
        VOID(4, MapColor.COLOR_BLACK, "block/voidstone", "block/furnace_void");

        private final int speed;
        private final MapColor mapColor;
        private final String sideTexture;
        private final String frontTexture;

        Tier(int speed, MapColor mapColor, String sideTexture, String frontTexture) {
            this.speed = speed;
            this.mapColor = mapColor;
            this.sideTexture = sideTexture;
            this.frontTexture = frontTexture;
        }

        /** 每道工序的时间 = 配置的 tick 数 ÷ 这个数（整数除法） */
        public int speed() {
            return speed;
        }

        public MapColor mapColor() {
            return mapColor;
        }

        /** 侧面和顶面的贴图（相对 textures/） */
        public String sideTexture() {
            return sideTexture;
        }

        /** 炉口的贴图（相对 textures/） */
        public String frontTexture() {
            return frontTexture;
        }
    }

    private final Tier tier;

    public MagmaticFurnaceBlock(Properties properties, Tier tier) {
        super(properties, false);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Tier tier() {
        return tier;
    }

    @Override
    protected BlockEntityType<MagmaticFurnaceBlockEntity> blockEntityType() {
        return ECMagmatic.MAGMATIC_FURNACE_BE.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 炉口朝向放置者（原作 placer.getHorizontalFacing().getOpposite()） */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
