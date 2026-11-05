package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.api.IColdBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 花纹方块（原作 BlockFancy，一个注册名 × meta 0~15 → 这里每种底材 × 每种花纹一个注册名 {@code fancy_<底材>_<花纹>}，第五批 6 × 16 = 96 个）。
 * 普通实心方块，没有逻辑、没有方块实体；放、挖都走原版流程（领地照管）。
 * <p>
 * 和原作的区别：
 * <ul>
 *   <li>挖什么掉什么（原作 BlockFancy 没覆盖 damageDropped，挖任何花纹都掉 meta 0 的远古砖）：掉落表由 datagen 生成，默认掉自己。</li>
 *   <li>冷值按底材字段给（原作 {@code this == BlocksCore.fancyBlocks.get(3)}，按注册列表的下标认霜冻石，列表顺序一变就错）；
 *       冷凝器按 {@code instanceof IColdBlock} 取，不用改冷凝器。</li>
 *   <li>不能当底材用：不进任何原料标签或结构标签（富集仓外壳、线圈底座、{@code forge:stone} 都不行），也拆不回底材
 *       （原作手册说能用在结构里，那几行在 1.12 的结构登记里已经注释掉了）。</li>
 * </ul>
 * 属性见 {@link FancyMaterial#properties()}；模型是 datagen 按 {@code models/block/fancy_overlayed.json} 模板生成的「底图 + 花纹」两层整格。
 */
public class FancyBlock extends Block implements IColdBlock {

    private final FancyMaterial material;
    private final FancyPattern pattern;

    public FancyBlock(FancyMaterial material, FancyPattern pattern) {
        super(material.properties());
        this.material = material;
        this.pattern = pattern;
    }

    public FancyMaterial material() {
        return material;
    }

    public FancyPattern pattern() {
        return pattern;
    }

    /** 花纹霜冻石 0.5（同底材霜冻石），其余 0 */
    @Override
    public float getColdModifier(BlockGetter level, BlockPos pos, BlockState state) {
        return material.coldModifier();
    }
}
