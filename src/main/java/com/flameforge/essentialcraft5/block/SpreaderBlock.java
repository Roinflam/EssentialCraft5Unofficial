package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.client.VoidClient;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * MRU 散射器（原作 BlockMRUSpreader，71 行）：海纳尔城镇废墟里 Spreader 件顶上的发光装饰（原作 StructureTownPieces L334），没有配方、没有方块实体，
 * 生存里只能从城镇里挖（正常掉自己，要镐）。亮度 15（原作 {@code setLightLevel(1F)}），形状照原作 BLOCK_AABB（0.3~0.7 × 0~0.8 × 0.3~0.7）。
 * 模型照原作 models/block/spreader.json（魔力护板的底座 + 顶上的方块）。
 * <p>
 * 和原作的差别（policy §4.4）：原作开着空转的随机刻，这里不开；原作每次 {@code randomDisplayTick} 冒 50 个 MRU 粒子（5 个方向 × 10），
 * 这里每次至多 {@value #MAX_PARTICLES} 个、同一格 {@value ParticleCooldown#COOLDOWN_TICKS} tick 冷却、离镜头超过
 * {@value LevitatorBlock#MAX_CAMERA_DISTANCE} 格不冒（{@link VoidClient#spreaderParticles}）；原作的 mruFX 只有戴单片镜才看得见，这里谁都看得见，
 * 粉尘放大到 {@value #DUST_SCALE}（原版红石粉 1），几格外也看得出 MRU 紫。
 */
public class SpreaderBlock extends Block {

    /** 亮度（原作 setLightLevel(1F) = 15） */
    public static final int LIGHT = 15;
    /** 每次 animateTick 至多冒几个粒子（原作 50） */
    public static final int MAX_PARTICLES = 10;
    /** 粉尘粒子的大小（DustParticleOptions 的 scale，原版红石粉 1） */
    public static final float DUST_SCALE = 1.4F;
    /** 原作 BLOCK_AABB(0.3, 0, 0.3, 0.7, 0.8, 0.7) */
    private static final VoxelShape SHAPE = Block.box(4.8D, 0D, 4.8D, 11.2D, 12.8D, 11.2D);

    public SpreaderBlock(Properties properties) {
        super(properties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1F;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VoidClient.spreaderParticles(level, pos, random));
    }
}
