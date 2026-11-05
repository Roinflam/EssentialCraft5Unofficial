package com.flameforge.essentialcraft5.block.hoanna;

import com.flameforge.essentialcraft5.client.HoannaResourcesClient;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.loot.condition.HoannaLootConditions;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECHoannaResources;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 影晶簇 {@code umbral_cluster}（第十三批 13d，batch13.md §6.1 暗影腐化之地「可再生写法」、§6.2「天灾采集窗口」）：
 * <ul>
 *   <li>收取（{@link #use}）：成熟（{@link #RIPE} = true）时真玩家右键 → {@link #harvestCount}：夜里或黑暗天灾中影晶 1~2，
 *       白天 {@code umbral.day_chance}（0.5）几率 1；判定和战利品条件 {@code night_or_darkness} 是同一个静态方法
 *       {@link HoannaLootConditions#isNightOrDarkness}。收完簇退回残簇（false）留在原地。残簇、假玩家都 PASS。</li>
 *   <li>回长（{@link #canRegrow}）：残簇随机刻，只在海纳尔、暗影腐化之地、夜里或黑暗天灾中，{@code umbral.regrow_chance}（0.14）几率变回成熟；
 *       只改这一格自己的状态，O(1)。默认值约一夜长回（算法见 {@code ECHoannaResources.Config}）。</li>
 *   <li>镐挖：一次性。精准采集掉本体（照原状态带 ripe，可移栽，移栽到别的群系不回长）；否则成熟簇照右键同一规则掉（{@link #getDrops}），
 *       残簇什么都不掉。</li>
 *   <li>客户端：黑暗天灾中成熟簇周围冒暗紫尘（{@link HoannaResourcesClient#umbralDust}）。方块亮度固定 2，成熟 / 残簇一样（见 {@code ECHoannaResources.UMBRAL_CLUSTER}）。</li>
 * </ul>
 * 状态属性 {@code facing} / {@code waterlogged} / {@code ripe} 是预备定下的，世界生成放 {@code ripe = true}。
 */
public class UmbralClusterBlock extends HoannaClusterBlock {

    /** true = 成熟、能收；false = 收过的残簇 */
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");
    public static final int HEIGHT = 7;
    public static final int INSET = 3;

    public UmbralClusterBlock(Properties properties) {
        super(HEIGHT, INSET, properties);
        registerDefaultState(defaultBlockState().setValue(RIPE, true));
    }

    /** 这时候收一簇得几个影晶：夜里或黑暗天灾中 1~2，白天 {@code day_chance} 几率 1、否则 0 */
    public static int harvestCount(ServerLevel level, RandomSource random) {
        if (HoannaLootConditions.isNightOrDarkness(level)) {
            return 1 + random.nextInt(2);
        }
        return random.nextDouble() < ECHoannaResources.Config.umbralDayChance() ? 1 : 0;
    }

    /** 残簇能不能长回（不含几率）：海纳尔、暗影腐化之地、夜里或黑暗天灾中 */
    public static boolean canRegrow(ServerLevel level, BlockPos pos) {
        return ECHoanna.isHoanna(level)
                && ECHoannaResources.inBiome(level, pos, ECHoanna.SHADOW_CORRUPTION)
                && (HoannaLootConditions.isNight(level.getDayTime()) || HoannaEvents.isActive(level, HoannaEvent.DARKNESS));
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        tryRegrow(state, level, pos, random.nextFloat());
    }

    /**
     * 随机刻的判定（roll 是这次掷出的 [0, 1) 随机数，GameTest 直接传）：残簇、条件满足、roll &lt; {@code regrow_chance} 时变回成熟。
     *
     * @return 长回了没有
     */
    public static boolean tryRegrow(BlockState state, ServerLevel level, BlockPos pos, float roll) {
        if (state.getValue(RIPE) || roll >= ECHoannaResources.Config.umbralRegrowChance() || !canRegrow(level, pos)) {
            return false;
        }
        level.setBlock(pos, state.setValue(RIPE, true), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, ECHoannaResources.UMBRAL_REGROW.get(), SoundSource.BLOCKS, 0.3F, 1F);
        return true;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!state.getValue(RIPE)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!ECProtection.isRealPlayer(player)) {
            return InteractionResult.PASS;
        }
        ServerLevel server = (ServerLevel) level;
        level.setBlock(pos, state.setValue(RIPE, false), Block.UPDATE_CLIENTS);
        int count = harvestCount(server, server.random);
        if (count > 0) {
            ECItemEntities.giveOrDrop((ServerPlayer) player, new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), count));
        }
        level.playSound(null, pos, ECHoannaResources.UMBRAL_HARVEST.get(), SoundSource.BLOCKS, 0.8F, 1F);
        return InteractionResult.CONSUME;
    }

    /**
     * 战利品表只管精准采集（掉本体）；不精准时成熟簇另按 {@link #harvestCount} 掉影晶（右键收取同一条规则，白天的几率读配置，表里写不了），
     * 残簇什么都不掉。爆炸、活塞推碎也走这里，同样只是一次性的。
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        boolean silk = tool != null && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0;
        if (silk || !state.getValue(RIPE)) {
            return drops;
        }
        ServerLevel level = params.getLevel();
        int count = harvestCount(level, level.random);
        if (count <= 0) {
            return drops;
        }
        List<ItemStack> result = new ArrayList<>(drops);
        result.add(new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get(), count));
        return result;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(RIPE)) {
            HoannaResourcesClient.umbralDust(level, pos, random);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RIPE);
    }
}
