package com.flameforge.essentialcraft5.block.hoanna;

import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaResources;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 辐晶簇 {@code radiant_cluster}（第十三批 13d，batch13.md §6.1 恐惧之地；HS §2.1.2）：只长在辐射热点区块（{@code Radiation.chunkBase} = 5），
 * 镐挖掉辐晶 1~2（时运加），精准采集掉本体（表在 {@code ECHoannaResources#radiantLoot}）。
 * <p>
 * 在海纳尔里挖掉的真玩家剂量 +{@code radiant_mining_dose}（1 500）× max(易伤, 0.5)（{@link Radiation#addFloored}，任何盔甲都至少吃一半）。
 * 加在 {@link #playerDestroy}：原版在方块已经移除、而且这次挖掘算数之后才调它，破坏事件被领地插件取消时走不到这里；
 * 创造模式挖不走这条路（{@code addFloored} 自己也不给创造加）。搬回主世界当装饰再挖不加。
 * <p>
 * 剂量是这样特产的代价，所以<b>只有真玩家亲手挖才掉东西</b>（{@link #getDrops}）：爆炸、机器钻头（没有 THIS_ENTITY）、假玩家（部署器之类）
 * 弄掉的什么都不掉，精准采集出本体也一样，免得「假玩家精准采集、搬回主世界再亲手挖」整条路都不吃剂量。活塞推不动它
 * （注册时 {@code PushReaction.BLOCK}），不会一推就碎掉、白白没了。
 */
public class RadiantClusterBlock extends HoannaClusterBlock {

    /** 形状照原版紫水晶簇（高 7、四周缩 3） */
    public static final int HEIGHT = 7;
    public static final int INSET = 3;
    /** 易伤的下限（batch13.md §6.1） */
    public static final float DOSE_FLOOR = 0.5F;

    public RadiantClusterBlock(Properties properties) {
        super(HEIGHT, INSET, properties);
    }

    /** 只有真玩家挖掉才掉（见类注释）；别的情况一律什么都不掉 */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Entity breaker = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (!ECProtection.isRealPlayer(breaker)) {
            return List.of();
        }
        return super.getDrops(state, params);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide && ECProtection.isRealPlayer(player) && ECHoanna.isHoanna(level)) {
            Radiation.addFloored(player, ECHoannaResources.Config.radiantMiningDose(), DOSE_FLOOR);
        }
    }
}
