package com.flameforge.essentialcraft5.enrichment;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 富集仓稳定器（原作 BlockMRUCUECBalancer / TileMRUCUECBalancer）：<b>方块类</b>实现这个接口，方块放在富集仓外壳上
 * （方块本身要在 {@code essentialcraft5:mrucuec_casing} 标签里，否则外壳判不成形），控制器扫描外壳时数到它，
 * 成形期间每 {@link com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity#STABILIZE_INTERVAL} tick
 * 给仓里（整个外壳范围内）的 MRU 聚集体续 {@link com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity#STABILIZE_TICKS} tick 稳定期
 * （{@link com.flameforge.essentialcraft5.entity.MRUPresenceEntity#stabilize}：期间不给玩家加矩阵损伤）。
 * <p>
 * 不需要方块实体、不需要 tick（原作的方块实体每 tick 去设聚集体的 flag，这里改由控制器统一做）。第三批 ECPentacle 注册 {@code ec_balancer}：
 * <pre>{@code
 * public class ECBalancerBlock extends Block implements EnrichmentStabilizer {
 *     public ECBalancerBlock(Properties properties) {
 *         super(properties);
 *     }
 * }
 * // 标签：tools/tags/ecpentacle.json 里 "essentialcraft5:mrucuec_casing": ["essentialcraft5:ec_balancer"]
 * }</pre>
 * 放进外壳以后最多等一次外壳重扫（放下外壳方块会标脏，最快 20 tick）才生效；拆掉后外壳重扫（或者外壳不完整）就停，已续的最多再管 40 tick。
 */
public interface EnrichmentStabilizer {

    /** 这个状态下起不起作用（默认总是起作用；以后要做开关、红石控制的变种时覆盖） */
    default boolean stabilizes(BlockState state) {
        return true;
    }
}
