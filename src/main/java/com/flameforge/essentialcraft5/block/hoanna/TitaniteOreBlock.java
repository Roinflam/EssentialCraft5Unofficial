package com.flameforge.essentialcraft5.block.hoanna;

import com.flameforge.essentialcraft5.client.HoannaResourcesClient;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 榍石矿 {@code titanite_ore}（第十三批 13d，batch13.md §6.1 海纳尔沙漠、§6.2「天灾采集窗口」；HS §2.1.2）：要铁镐；精准采集掉本体，
 * 否则掉已有的榍石 1（时运加）+ 闪耀榍石，日阵中露天挖 {@link #TWINKLING_CHANCE_WINDOW}，否则 {@link #TWINKLING_CHANCE}
 * （战利品条件 {@code hoanna_event{sun_array}} + {@code sky_visible}，表在 {@code ECHoannaResources#titaniteLoot}）。
 * 模型是原版砂岩 + 本模组的透明叠加层。不开随机刻，不改方块状态。
 * <p>
 * 客户端：日阵中露天的榍石矿上冒小闪光（{@link HoannaResourcesClient#titaniteGlint}，只看客户端的日阵淡入强度）。
 */
public class TitaniteOreBlock extends Block {

    /** 日阵中、头顶露天时闪耀榍石的几率 */
    public static final float TWINKLING_CHANCE_WINDOW = 0.40F;
    /** 平时闪耀榍石的几率 */
    public static final float TWINKLING_CHANCE = 0.08F;

    public TitaniteOreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        HoannaResourcesClient.titaniteGlint(level, pos, random);
    }
}
