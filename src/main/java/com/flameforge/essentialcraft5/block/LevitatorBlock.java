package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.client.VoidClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * MRU 悬浮器（原作 BlockMRULevitator，63 行）：海纳尔城镇废墟里房屋地基四角的装饰（原作 StructureTownPieces L390），没有配方、没有方块实体，
 * 生存里只能从城镇里挖（正常掉自己，要镐）。模型照原作 models/block/levitator.json（强化石顶板 + 四条腿 + 信标芯 + 魔力护板核心）。
 * <p>
 * 和原作的差别（policy §4.4）：
 * <ul>
 *   <li>原作开着随机刻（{@code setTickRandomly(true)}）但 updateTick 是空的，服务端白白空转；这里不开。</li>
 *   <li>粒子：原作每次 {@code randomDisplayTick} 冒 48 个 MRU 粒子（12 个往下落 + 36 个往四周散）；这里每次至多 {@value #MAX_PARTICLES} 个，
 *       同一格 {@value ParticleCooldown#COOLDOWN_TICKS} tick 冷却，离镜头超过 {@value #MAX_CAMERA_DISTANCE} 格不冒（{@link VoidClient#levitatorParticles}）。
 *       原作的 mruFX 粒子只有戴着能看见 MRU 的东西（单片镜）才画得出来；框架还没有 MRU 粒子，这里用原版彩色粉尘画 MRU 紫，谁都看得见
 *       （同聚集体「人人可见」的处理），粉尘放大到 {@value #DUST_SCALE}（原版红石粉 1），离几格远也看得清。</li>
 *   <li>粒子的去向：原作往下落，城镇里悬浮器埋在墙角正下方，往下落的全钻进地里。这里改成一条竖线从顶面往上冒、外加腰上一圈；
 *       被实心方块挡住的面不冒（{@link #openFaces}：顶上被挡就没有竖线，腰上那圈只冒在没被挡住的几面），五面全被挡（埋着）的一颗都不冒，
 *       省下的名额给还看得见的那部分，每次总数仍是 {@value #MAX_PARTICLES}（{@link #columnParticles} / {@link #ringParticles}）。</li>
 * </ul>
 * 原作不透光度 0（光和天空光直接穿过）：不遮挡（noOcclusion）、天空光往下透；碰撞照原作整格（上面能站人）。
 */
public class LevitatorBlock extends Block {

    /** 每次 animateTick 至多冒几个粒子（原作 48） */
    public static final int MAX_PARTICLES = 12;
    /** 离镜头超过这么多格就不冒 */
    public static final int MAX_CAMERA_DISTANCE = 24;
    /** 上下左右都露着时，竖线分几颗（其余在腰上那一圈） */
    public static final int COLUMN_PARTICLES = 8;
    /** 粉尘粒子的大小（DustParticleOptions 的 scale，原版红石粉 1；0.7 在几格外几乎看不见） */
    public static final float DUST_SCALE = 1.3F;
    /** {@link #openFaces} 的位：第 {@code Direction#get3DDataValue} 位，顶面 = 1 &lt;&lt; 1 */
    public static final int FACE_UP = 1 << 1;
    /** 四个水平面（北、南、西、东）的位 */
    public static final int FACE_SIDES = 0b111100;

    public LevitatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1F;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VoidClient.levitatorParticles(level, pos, random));
    }

    // ---------------------------------------------------------------- 粒子往哪冒（纯逻辑，两端都能加载，GameTest 直接测）

    /** 顶面和四个水平面里，哪几面没被实心方块（{@code isSolidRender}：整格不透光）挡住；按 {@link #FACE_UP} / {@link #FACE_SIDES} 的位 */
    public static int openFaces(BlockGetter level, BlockPos pos) {
        int mask = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction face : FACES) {
            cursor.setWithOffset(pos, face);
            if (!level.getBlockState(cursor).isSolidRender(level, cursor)) {
                mask |= faceBit(face);
            }
        }
        return mask;
    }

    public static int faceBit(Direction face) {
        return 1 << face.get3DDataValue();
    }

    /** 竖线（顶面往上）冒几颗：顶上被挡 0；四周全被挡、只剩顶上露着时全给竖线 */
    public static int columnParticles(int openFaces) {
        if ((openFaces & FACE_UP) == 0) {
            return 0;
        }
        return (openFaces & FACE_SIDES) == 0 ? MAX_PARTICLES : COLUMN_PARTICLES;
    }

    /** 腰上那圈冒几颗（只冒在没被挡住的水平面那边）：四周全被挡 0；顶上被挡时全给这一圈 */
    public static int ringParticles(int openFaces) {
        if ((openFaces & FACE_SIDES) == 0) {
            return 0;
        }
        return MAX_PARTICLES - columnParticles(openFaces);
    }

    private static final Direction[] FACES = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
}
