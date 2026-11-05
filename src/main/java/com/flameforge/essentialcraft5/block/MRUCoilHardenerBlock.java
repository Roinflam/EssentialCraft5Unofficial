package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.client.TeslaClient;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * MRU 特斯拉线圈基座。对应原作 BlockMRUCoilHardener（46 行）+ TileMRUCoilHardener（50 行）+ RenderMRUCoilHardener（44 行）。
 * <p>
 * 叠在线圈正下方，每层给线圈加射程（原作 +1、无上限；移植后 +2、封顶 32，见 MRUCoilBlockEntity）。自己没有服务端逻辑。
 * <b>不要方块实体</b>：原作的方块实体只在客户端每 tick 1% 概率往上找线圈、冒一道小火花（L24~40），渲染盒还是无限大。
 * 这里改在 {@link #animateTick}（客户端随机刻）里做，见 {@link TeslaClient#hardenerSpark}：
 * 1.20.1 客户端每 tick 在玩家周围抽 667 × 2 个位置做 animateTick（{@code ClientLevel.animateTick}，两个随机数相减，越靠近玩家越容易抽中，
 * 离玩家两三格的方块每 tick 约 10%~15%），所以先按 {@link #SPARK_CHANCE} 压一道，再按位置记 40 tick 的冷却（原作电弧存活 40 tick、期间不再冒）。
 * <p>
 * 原作 setLightOpacity(0)：天空光直接穿过、相邻方块的 AO 和原作一样（同 MachineBlock 的 transmitsSkylight）。碰撞照原作整格。
 */
public class MRUCoilHardenerBlock extends Block {

    /** animateTick 里先过这一道概率 */
    public static final float SPARK_CHANCE = 0.1F;

    public MRUCoilHardenerBlock(Properties properties) {
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
        if (random.nextFloat() < SPARK_CHANCE) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TeslaClient.hardenerSpark(level, pos, random));
        }
    }
}
