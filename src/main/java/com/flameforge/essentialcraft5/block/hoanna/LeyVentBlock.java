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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 地脉喷口 {@code ley_vent}（第十三批 13d，batch13.md §6.1 混沌腐化之地 + 其余陆地稀疏、§6.2「天灾采集窗口」；HS §2.1.2、§2.2.2）：
 * 只在海纳尔里靠随机刻慢慢充能（{@link #CHARGE} 0~3），真玩家空手右键收 charge × {@code crystals_per_charge} 个地脉结晶、charge 归 0。
 * 挖掉什么都不掉（精准采集也不掉，搬不走）。亮度 = charge × 2（{@link #lightLevel}）。世界生成时 charge = 2，混沌裂隙两侧的是 3。
 * <ul>
 *   <li>充能几率（{@link #chargeChance}）：地震或地脉风暴中 {@code charge_chance_calamity}（1.0），否则夜里（一天的 13 000~23 000 刻）
 *       {@code charge_chance_night}（0.16）、白天 {@code charge_chance_day}（0.08）。充到 {@code max_charge} 停；不在海纳尔不充。
 *       只改这一格自己的状态，每次随机刻 O(1)。</li>
 *   <li>收取走 {@link #use}：领地插件管的右键方块事件在它之前，拦下了就到不了这里。假玩家、主手拿着别的东西（地脉结晶不算，见 {@link #emptyHanded}）、
 *       副手那一下、charge 0 一律 PASS。</li>
 *   <li>客户端：charge ≥ 2 时顶面冒淡紫尘（{@link HoannaResourcesClient#ventDust}，每次 ≤ 2 颗、24 格外不冒、同一格 20 刻冷却）。</li>
 * </ul>
 */
public class LeyVentBlock extends Block {

    public static final int MAX_CHARGE = 3;
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, MAX_CHARGE);

    public LeyVentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    /** 方块属性的亮度函数：charge × 2 */
    public static int lightLevel(BlockState state) {
        return state.hasProperty(CHARGE) ? state.getValue(CHARGE) * 2 : 0;
    }

    /** 这个世界此刻每次随机刻充一格的几率；不是海纳尔返回 0 */
    public static double chargeChance(ServerLevel level) {
        if (!ECHoanna.isHoanna(level)) {
            return 0D;
        }
        if (HoannaEvents.isActive(level, HoannaEvent.EARTHQUAKE) || HoannaEvents.isActive(level, HoannaEvent.LEY_STORM)) {
            return ECHoannaResources.Config.chargeChanceCalamity();
        }
        return HoannaLootConditions.isNight(level.getDayTime())
                ? ECHoannaResources.Config.chargeChanceNight() : ECHoannaResources.Config.chargeChanceDay();
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        tryCharge(state, level, pos, random.nextFloat());
    }

    /**
     * 随机刻的判定（roll 是这次掷出的 [0, 1) 随机数，GameTest 直接传）：roll &lt; 充能几率、还没充到 {@code max_charge} 时 charge + 1。
     *
     * @return 充了没有
     */
    public static boolean tryCharge(BlockState state, ServerLevel level, BlockPos pos, float roll) {
        int charge = state.getValue(CHARGE);
        if (charge >= ECHoannaResources.Config.maxCharge() || roll >= chargeChance(level)) {
            return false;
        }
        level.setBlock(pos, state.setValue(CHARGE, charge + 1), Block.UPDATE_CLIENTS);
        return true;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int charge = state.getValue(CHARGE);
        if (hand != InteractionHand.MAIN_HAND || !emptyHanded(player.getItemInHand(hand)) || charge <= 0) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!ECProtection.isRealPlayer(player)) {
            return InteractionResult.PASS;
        }
        level.setBlock(pos, state.setValue(CHARGE, 0), Block.UPDATE_CLIENTS);
        int count = charge * ECHoannaResources.Config.crystalsPerCharge();
        // 每格最多 16 个、最多 3 格，乘出来不超过 48，一组装得下；照最大堆叠拆开只是兜底
        int max = ECHoannaMaterials.LEY_CRYSTAL.get().getMaxStackSize();
        while (count > 0) {
            int stack = Math.min(count, max);
            ECItemEntities.giveOrDrop((ServerPlayer) player, new ItemStack(ECHoannaMaterials.LEY_CRYSTAL.get(), stack));
            count -= stack;
        }
        level.playSound(null, pos, ECHoannaResources.VENT_RELEASE.get(), SoundSource.BLOCKS, 0.8F, 1F);
        return InteractionResult.CONSUME;
    }

    /**
     * 算不算空手：主手空着，或者拿的就是地脉结晶。收下的结晶多半落进当前选中的空格子，不放宽这一条的话，
     * 收完一个喷口就得先切开手里的结晶才能收下一个；地脉结晶对着方块右键本来就什么都不做，不会和别的用法抢。
     */
    public static boolean emptyHanded(ItemStack held) {
        return held.isEmpty() || held.is(ECHoannaMaterials.LEY_CRYSTAL.get());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(CHARGE) >= 2) {
            HoannaResourcesClient.ventDust(level, pos, random);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }
}
