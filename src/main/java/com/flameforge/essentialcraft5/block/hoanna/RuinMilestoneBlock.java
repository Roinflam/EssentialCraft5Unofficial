package com.flameforge.essentialcraft5.block.hoanna;

import com.flameforge.essentialcraft5.hoanna.survey.SurveySearches;
import com.flameforge.essentialcraft5.item.HoannaSurveyMapItem;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 里程碑 {@code ruin_milestone}（第十三批 13d-H3，batch13.md §6.3「里程碑」；HS §2.3.2）：废墟间路边柱子顶上的那一格，自己发光（亮度 10），
 * 外观是强化石的侧面和底、顶面一块元素灯（全用现有贴图）。地表的柱子由 13d-H1b 按注册名摆（3 格强化石 + 顶上这一格）。
 * <p>
 * 真玩家右键：找最近一座城镇废墟（半径 {@value #RADIUS} 格、最多 {@value #SAMPLE_CAP} 个群系采样，走结构预测和全服预算，不加载区块），
 * 找完在动作栏写「东北方约 380 格外有一座城的残骸」。每人每块冷却 {@value #COOLDOWN_TICKS} 刻。不改方块，不放东西出来。
 * 给刚进海纳尔、还没有勘测图的人一个方向；不在海纳尔（创造模式拿出去摆的）时只说一句认不出方向。
 * <p>
 * 挖掉掉一块强化石（要镐）：柱顶本身不给拿走，免得搬回主世界当路标。
 */
public class RuinMilestoneBlock extends Block {

    /** 找城镇的半径（格） */
    public static final int RADIUS = 1024;
    /** 最多看几个群系采样点 */
    public static final int SAMPLE_CAP = 16;
    /** 同一人同一块的冷却（刻） */
    public static final int COOLDOWN_TICKS = 40;
    /** 冷却表超过这么多条时清一遍过期的 */
    private static final int PRUNE_SIZE = 256;

    public static final String MSG_HEADING = "message.essentialcraft5.ruin_milestone.heading";
    public static final String MSG_NOT_FOUND = "message.essentialcraft5.ruin_milestone.not_found";
    public static final String MSG_NOT_HOANNA = "message.essentialcraft5.ruin_milestone.not_hoanna";

    private record Use(UUID player, ResourceKey<Level> dimension, long pos) {
    }

    /** (玩家, 世界, 方块) → 上一次右键的服务器刻数 */
    private static final Map<Use, Long> LAST_USE = new HashMap<>();

    public RuinMilestoneBlock(Properties properties) {
        super(properties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !ECProtection.isRealPlayer(serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (!tryUse(serverPlayer.getUUID(), serverLevel.dimension(), pos, serverLevel.getServer().getTickCount())) {
            return InteractionResult.CONSUME;
        }
        if (!ECHoanna.isHoanna(serverLevel)) {
            serverPlayer.displayClientMessage(Component.translatable(MSG_NOT_HOANNA), true);
            return InteractionResult.CONSUME;
        }
        startSearch(serverPlayer, serverLevel, pos);
        return InteractionResult.CONSUME;
    }

    /**
     * 冷却：这名玩家在这一块上距上次右键不到 {@value #COOLDOWN_TICKS} 刻就返回 false；否则记下 now、返回 true。
     * now 是服务器刻数（GameTest 直接传）。
     */
    public static boolean tryUse(UUID player, ResourceKey<Level> dimension, BlockPos pos, long now) {
        Use key = new Use(player, dimension, pos.asLong());
        Long last = LAST_USE.get(key);
        if (last != null && now - last >= 0 && now - last < COOLDOWN_TICKS) {
            return false;
        }
        if (LAST_USE.size() >= PRUNE_SIZE) {
            Iterator<Map.Entry<Use, Long>> it = LAST_USE.entrySet().iterator();
            while (it.hasNext()) {
                long age = now - it.next().getValue();
                if (age < 0 || age >= COOLDOWN_TICKS) {
                    it.remove();
                }
            }
        }
        LAST_USE.put(key, now);
        return true;
    }

    /** 服务器停了：清冷却表 */
    public static void clearCooldowns() {
        LAST_USE.clear();
    }

    /**
     * 发起找城镇的搜索（中心是这块里程碑），找完在动作栏写方向距离。这名玩家已有搜索在跑（勘测图或别的里程碑发起的，每人只许一个）、
     * 这个世界没有城镇时返回 null：前者说「还在找上一处」（和勘测图同一句），后者直接说找不到。GameTest 直接调。
     */
    @Nullable
    public static SurveySearches.Job startSearch(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (SurveySearches.busy(player.getUUID())) {
            player.displayClientMessage(Component.translatable(HoannaSurveyMapItem.MSG_SEARCHING), true);
            return null;
        }
        StructureForecast.World world = SurveySearches.world(level);
        HoannaSurveyMapItem.Kind town = HoannaSurveyMapItem.TOWN;
        List<StructureForecast.Target> targets = HoannaSurveyMapItem.targets(level, world, town);
        if (targets.isEmpty()) {
            player.displayClientMessage(Component.translatable(MSG_NOT_FOUND, RADIUS), true);
            return null;
        }
        BlockPos from = pos.immutable();
        return SurveySearches.start(player, level, world, targets, from, RADIUS, chunk -> false, SAMPLE_CAP,
                (who, result) -> who.displayClientMessage(line(from, result), true));
    }

    /** 动作栏那一句：找到写八方位和距离（从里程碑算），没找到写半径内没有 */
    public static MutableComponent line(BlockPos from, Optional<BlockPos> result) {
        if (result.isEmpty()) {
            return Component.translatable(MSG_NOT_FOUND, RADIUS);
        }
        BlockPos to = result.get();
        double dx = to.getX() + 0.5D - (from.getX() + 0.5D);
        double dz = to.getZ() + 0.5D - (from.getZ() + 0.5D);
        int heading = InquisitoriumCoordinatesItem.heading(dx, dz);
        return Component.translatable(MSG_HEADING, Component.translatable(InquisitoriumCoordinatesItem.headingKey(heading)),
                Mth.floor(Math.sqrt(dx * dx + dz * dz))).withStyle(ChatFormatting.LIGHT_PURPLE);
    }
}
