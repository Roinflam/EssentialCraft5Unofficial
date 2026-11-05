package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 魔力挖掘器（原作 ItemMagicalDigger，ECDigger）：MRU 驱动的万能镐，玩家手持的工具（不是机器，policy §1.1「玩家手持工具照玩家权限改方块」）。
 * <ul>
 *   <li>自带 {@value #MAX_MRU} MRU（原作 maxMRU，不是储存物品）；自己存的 MRU ≥ {@value #COST} 时挖任何方块都是 {@value #FAST_SPEED} 速，
 *       否则 {@value #SLOW_SPEED}（原作 L87~92：看的是<b>挖掘器自己</b>存的 MRU，不看身上的储存物品，照原作保留）。</li>
 *   <li>什么方块都算「用对了工具」（原作 canHarvestBlock 恒真）：要特定工具才掉落的方块（石头、矿石、黑曜石、蜘蛛网的线）照常掉落，
 *       但掉什么仍看战利品表，玻璃、冰、树叶本体照样要精准采集 / 剪刀，紫水晶母岩、刷怪笼什么都不掉。
 *       挖掘进度的除数也因此恒取 30（{@code BlockBehaviour.getDestroyProgress}；空手挖要工具的方块是 100），
 *       所以 {@value #SLOW_SPEED} 速时挖石头、黑曜石仍比空手快约 3.3 倍（石头约 45 刻、黑曜石约 1500 刻），不是「和空手一样慢」；
 *       1 速时效率魔咒不起作用（{@code Player.getDigSpeed} 速度 > 1 才加）。手册「怎么用」页照这些数写，测试 DiggerTests.diggerSpeedMatchesGuide。</li>
 *   <li>没有耐久、不会坏（原作 setMaxDamage(0)）。</li>
 *   <li>快速状态下每挖一格扣 {@value #COST} MRU（{@code MRUDrain.playerUseMRU}：先扣身上的储存物品，凑不够再扣挖掘器自己的；创造模式不扣）；
 *       不潜行时再把周围 3×3×3 里和中心<b>同一种方块</b>（比 Block 不比状态，原作 {@code b == id}）一起挖掉，每格同样扣 {@value #COST}。</li>
 * </ul>
 *
 * <h2>范围挖为什么推迟到本 tick 末尾（policy §2.1 ②、batch4 §3.10）</h2>
 * {@link #mineBlock} 在中心那格的 {@code ServerPlayerGameMode.destroyBlock} 里面被调（破坏事件放行之后、方块移除之前）。
 * 在这里再调邻格的 {@code destroyBlock} 就是「在另一次破坏过程里再发破坏事件」：Mohist 系混合端的 Bukkit 破坏事件是全局单槽，
 * 里层会把外层那一下的槽取走，中心那格被删但不掉落。所以这里只登记待办，由 {@link MagicalDiggerEvents} 在 {@code ServerTickEvent} END
 * 逐格调玩家本人的 {@code gameMode.destroyBlock}：每格都是一次完整、独立的玩家破坏（破坏事件照常转给领地插件、算掉落、算附魔、给经验），
 * 被拒的格子返回 false、不扣钱。纯 Forge 和单人下没有这个单槽，推迟一刻也看不出区别。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>原作先扣 9 MRU 再发破坏事件，被拒的格子也扣 → 挖成一格才扣一格。</li>
 *   <li>原作潜行时 32 速挖中心格一点不扣（onBlockDestroyed 直接返回）→ 潜行也扣中心格 {@value #COST}。</li>
 *   <li>原作中心格发两次破坏事件（原版一次、挖掘器自己一次）→ 中心格交给原版，只发一次。</li>
 *   <li>原作邻格不查出生点保护和世界边界 → 另查 {@code level.mayInteract}。</li>
 *   <li>原作每次挖 27 次音效叠放 → 一次。</li>
 *   <li>1.20 的 {@code destroyBlock} 自带掉经验：连带挖的矿石也给经验（原作邻格不给，小）。</li>
 * </ul>
 */
public class MagicalDiggerItem extends PickaxeItem implements CreativeVariants {

    /** 原作 maxMRU */
    public static final int MAX_MRU = 5000;
    /** 每挖一格的 MRU（原作 L88 / L95 / L117） */
    public static final int COST = 9;
    /** 自己存的 MRU ≥ {@link #COST} 时的挖掘速度（原作 L89） */
    public static final float FAST_SPEED = 32F;
    /** 自己存的 MRU 不够时的挖掘速度（原作 L91） */
    public static final float SLOW_SPEED = 1F;
    /** 范围挖的半边长：3×3×3（原作 L107~109） */
    public static final int AREA_RADIUS = 1;

    public MagicalDiggerItem(Tier tier, int attackModifier, float attackSpeed, Properties properties) {
        super(tier, attackModifier, attackSpeed, properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, MAX_MRU);
    }

    /** 挖掘器自己存的 MRU（没有能力时 0） */
    public static int storedMRU(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandlerItem::getMRU).orElse(0);
    }

    /** 快速状态：自己存的 MRU ≥ {@link #COST}（原作 canBreak） */
    public static boolean isCharged(ItemStack stack) {
        return storedMRU(stack) >= COST;
    }

    // ------------------------------------------------------------------
    // 挖掘
    // ------------------------------------------------------------------

    /** 任何方块：快速状态 32，否则 1（原作不看方块种类） */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return isCharged(stack) ? FAST_SPEED : SLOW_SPEED;
    }

    /** 什么方块都算用对了工具（原作 canHarvestBlock 恒真）：要工具才掉落的方块照常掉落（掉什么仍看战利品表），挖掘进度除数取 30，见类注释 */
    @Override
    @SuppressWarnings("deprecation")
    public boolean isCorrectToolForDrops(BlockState state) {
        return true;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return true;
    }

    /**
     * 只在服务端、玩家挖掉一格（破坏事件已放行、方块还没移除）时由原版调用；客户端的 destroyBlock 不调它。
     * 快速状态：扣中心格 {@value #COST}（潜行也扣）；不潜行的真玩家再登记一次范围挖（本 tick 末尾做，见类注释）。
     * 范围挖自己调的 destroyBlock 也会走到这里，那时什么都不做（钱由范围挖在挖成以后扣）。
     * 不调 super：super 扣耐久（本来也扣不动）。
     */
    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof Player player) || MagicalDiggerEvents.isAreaMining() || !isCharged(stack)) {
            return true;
        }
        MRUDrain.playerUseMRU(player, stack, COST);
        // 假玩家（别的模组的自动化拿着它挖）只当普通的镐：它的破坏事件混合端不转给领地插件，不许替它连带挖
        if (!player.isShiftKeyDown() && ECProtection.isRealPlayer(player)) {
            MagicalDiggerEvents.schedule((ServerPlayer) player, pos, state.getBlock(), stack);
        }
        return true;
    }

    /**
     * 挖的过程中 MRU 变了（NBT 变了）不重置挖掘进度（Forge 默认对没有耐久的物品按 NBT 比）；
     * 换了物品、快慢状态变了、魔咒变了才重置。
     */
    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !newStack.is(oldStack.getItem()) || isCharged(oldStack) != isCharged(newStack)
                || !oldStack.getAllEnchantments().equals(newStack.getAllEnchantments());
    }

    // ------------------------------------------------------------------
    // 不掉耐久、照样能附魔（同三把矩阵武器）
    // ------------------------------------------------------------------

    /** 没有耐久（原作 setMaxDamage(0)）：挖方块、打怪都不掉，不显示耐久条，也不能附耐久、经验修补 */
    @Override
    public boolean canBeDepleted() {
        return false;
    }

    /** 原作 isEnchantable 恒为 true（没有耐久的物品原版默认不能附魔）：效率、时运、精准采集照常能附 */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    // ------------------------------------------------------------------
    // 提示、创造栏、动画
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler ->
                tooltip.add(Component.translatable("tooltip.essentialcraft5.mru", handler.getMRU(), handler.getMaxMRU())
                        .withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.magical_digger.cost", COST).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.magical_digger.area").withStyle(ChatFormatting.DARK_GRAY));
        if (!isCharged(stack)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.magical_digger.slow", COST).withStyle(ChatFormatting.RED));
        }
    }

    /** MRU 变化不触发重新举起的动画（每挖一格 NBT 都会变） */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    /** 创造模式物品栏放空、满两份（原作 getSubItems） */
    @Override
    public List<ItemStack> creativeVariants() {
        return List.of(new ItemStack(this), full(this));
    }

    /** 满 MRU 的一把（测试、展示用） */
    public static ItemStack full(MagicalDiggerItem item) {
        ItemStack full = new ItemStack(item);
        full.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(handler.getMaxMRU()));
        return full;
    }
}
