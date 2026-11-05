package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.block.MagicLightBlock;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/**
 * 魔力灯笼（原作 ItemMagicLantern，ItemMRUGeneric 系：自带 5000 MRU，在充能仓里充）。
 * <ul>
 *   <li>右键方块的一面：那一面外侧是空气（或者灯笼自己的临时光源）时，消耗 1 个魔力炉渣和 {@value #LIGHT_COST} MRU，放一个永久的魔力光源
 *       （MRU 先从身上的灵魂储存物品扣，不够再扣灯笼自己的，见 MRUDrain；创造模式不扣）。</li>
 *   <li>拿在手上时，脚下那格是空气、而且亮度不到 {@value #BRIGHT_ENOUGH} 就放一个临时光源（{@link MagicLightBlock#LIFETIME} tick 后消失，不耗 MRU）。</li>
 * </ul>
 * 移植改动：
 * <ul>
 *   <li>先放方块、发放置事件（{@code BlockEvent.EntityPlaceEvent}，混合端会转给领地插件），被拦下或 MRU 不够就撤回，都通过了才扣 MRU 和炉渣。
 *       放置事件只发这一次：服务端右键时 ForgeHooks 会开着方块快照捕获调 useOn、事后再发一次，这里放方块时暂停捕获，见 {@link #placePermanent}。
 *       原作不发事件，并且先扣 MRU、后找炉渣。</li>
 *   <li>低头右键脚下的地面也能放：目标格是灯笼自己放在脚下的临时光源时，直接顶替（{@link MagicLightBlock#canPlacePermanentAt}）。
 *       临时光源没有选取框，射线穿过它点中的是地面，原作点中的是临时光源本身、光源放在头部那格。</li>
 *   <li>判空气改成 {@code isAir()}（洞穴空气也算），排除世界外的虚空空气；要检查世界边界、出生点保护和冒险模式的放置限制。</li>
 *   <li>创造模式不需要炉渣（原作创造模式也要身上有炉渣）。</li>
 *   <li>副手拿着也照亮脚下。原作只认主手（isCurrentItem）。</li>
 *   <li>临时光源只在脚下是空气时才放：站着不动时由光源自己续期（见 MagicLightBlock），不会每 tick 改方块。</li>
 *   <li>脚下已经够亮（{@link #BRIGHT_ENOUGH} 以上，天空光和方块光取大的：白天露天、火把旁、刚放过的临时光源附近）或者正在鞘翅滑翔时不放临时光源。
 *       1.20 的光照在服务端算、整区段发给客户端，原作每走一格放拆一次在这里是一串光照包，照明效果却是零；
 *       现在走路大约每 4 格放一个（上一个光源的余光降到 {@value #BRIGHT_ENOUGH} 以下才放）。怪物生成要求方块光为 0，跳过的地方本来就够亮，不影响防刷怪。</li>
 *   <li>缺炉渣、MRU 不够时在快捷栏上方提示一句（原作什么都不说）。</li>
 * </ul>
 */
public class MagicalLanternItem extends Item implements CreativeVariants {

    /** 原作 ItemMRUGeneric.maxMRU 的默认值 */
    public static final int MAX_MRU = 5000;
    /** 放一个永久光源的 MRU（原作 ECUtils.playerUseMRU(…, 100)） */
    public static final int LIGHT_COST = 100;
    /** 脚下亮度（{@code getMaxLocalRawBrightness}，天空光按当前时间折算后和方块光取大的）到这个值就不放临时光源（原作不看亮度） */
    public static final int BRIGHT_ENOUGH = 12;

    /**
     * Mohist 系混合端给 ForgeEventFactory 加了带手的 4 参 onBlockPlace，转成 Bukkit BlockPlaceEvent 时按它报放置用的手和物品；
     * 3 参版不设手，事件里是默认值 MAIN_HAND（混合端 {@code BlockEvent.EntityPlaceEvent}），灯笼拿在副手时就会被报成主手和主手的物品，
     * 按手里物品判断的插件就会判错。纯 Forge 没有这个重载，找不到时为 null，用 3 参版。
     */
    @Nullable
    private static final MethodHandle PLACE_EVENT_WITH_HAND = findPlaceEventWithHand();

    public MagicalLanternItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        // 不是储存物品（isStorage 默认 false）：不会被别的法杖当电池抽
        return new MRUItemStorage(stack, MAX_MRU);
    }

    /** 玩家主手或副手拿着灯笼 */
    public static boolean isHolding(Player player) {
        return player.getMainHandItem().getItem() instanceof MagicalLanternItem
                || player.getOffhandItem().getItem() instanceof MagicalLanternItem;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player) || player.isSpectator()) {
            return;
        }
        // 不能只看 selected：原版给副手、盔甲槽里的物品也会传 selected（按下标和快捷栏选中格比较）
        if (player.getMainHandItem() != stack && player.getOffhandItem() != stack) {
            return;
        }
        // 鞘翅滑翔时每 tick 进一个新格子，放了也照不到什么；脚下已经够亮也不放（见类注释）
        BlockPos feet = player.blockPosition();
        if (player.isFallFlying() || level.getMaxLocalRawBrightness(feet) >= BRIGHT_ENOUGH) {
            return;
        }
        // 只在脚下是空气时才放（已经是光源就什么都不做）；两只手都拿着时第二把看到的已经是光源
        MagicLightBlock.placeTemporary(level, feet);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        Direction face = context.getClickedFace();
        BlockPos target = context.getClickedPos().relative(face);
        ItemStack lantern = context.getItemInHand();
        // 客户端和服务端用同一个判定：空气，或者灯笼自己的临时光源（低头点脚下地面时目标格就是它）
        if (!MagicLightBlock.canPlacePermanentAt(level, target)) {
            return InteractionResult.PASS;
        }
        boolean free = player.getAbilities().instabuild;
        int slagSlot = free ? -1 : findSlag(player.getInventory());
        if (!free && slagSlot < 0) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.essentialcraft5.magical_lantern.no_slag")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            // 客户端只预判到这里（挥手）；MRU 和领地由服务端判断
            return InteractionResult.SUCCESS;
        }
        return placePermanent(level, player, lantern, context.getHand(), target, face, slagSlot) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /**
     * 放一个永久光源（服务端）。先放、发放置事件，被拦下或 MRU 不够就把原来的方块还回去；都通过了才扣 MRU 和炉渣。
     * <p>
     * 服务端右键走 {@code ItemStack#useOn} → {@code ForgeHooks.onPlaceItemIntoWorld}：它开着方块快照捕获（{@code level.captureBlockSnapshots}）调 useOn，
     * 返回「已消耗」以后再对捕获到的方块发一次放置事件，被拦下就撤回方块、只还原手里这盏灯笼。放置事件要在扣费之前发，所以这里自己发，
     * 放方块期间暂停捕获：ForgeHooks 捕获不到，就不会再发第二次事件，也不会在扣完 MRU 和炉渣以后撤回。
     * 捕获本来的作用（没定下来之前不通知客户端和邻居）在 {@link #placeNow} 里自己做。不在捕获里（直接调 Item#useOn）时走同一条路。
     *
     * @param hand     拿灯笼的手（混合端报给插件）
     * @param slagSlot 炉渣所在的物品栏格子；-1 表示不扣（创造模式）
     */
    public static boolean placePermanent(Level level, Player player, ItemStack lantern, InteractionHand hand, BlockPos target, Direction face, int slagSlot) {
        boolean capturing = level.captureBlockSnapshots;
        level.captureBlockSnapshots = false;
        try {
            return placeNow(level, player, lantern, hand, target, face, slagSlot);
        } finally {
            level.captureBlockSnapshots = capturing;
        }
    }

    private static boolean placeNow(Level level, Player player, ItemStack lantern, InteractionHand hand, BlockPos target, Direction face, int slagSlot) {
        if (!level.mayInteract(player, target) || !player.mayUseItemAt(target, face, lantern)) {
            return false;
        }
        // 目标格是空气或灯笼自己的临时光源（canPlacePermanentAt），都没有方块实体：撤回直接放回原来的状态，临时光源照常到期消失；
        // 被顶替后它残留的定时刻落到永久光源上，expire 看到不是临时光源直接返回
        BlockState old = level.getBlockState(target);
        BlockState light = ECEssence.MAGIC_LIGHT.get().defaultBlockState();
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, target);
        // 先只改方块、不发给客户端也不通知邻居（等同 ForgeHooks 捕获期间的做法），放置事件和 MRU 都过了才正式通知；
        // 没放成就同样悄悄改回去：客户端从头到尾看不到变化，侦测器也不会被一次没放成的点击触发
        if (!level.setBlock(target, light, Block.UPDATE_KNOWN_SHAPE)) {
            return false;
        }
        if (postPlaceEvent(player, snapshot, face, hand)) {
            level.setBlock(target, old, Block.UPDATE_KNOWN_SHAPE);
            return false;
        }
        if (!MRUDrain.playerUseMRU(player, lantern, LIGHT_COST)) {
            level.setBlock(target, old, Block.UPDATE_KNOWN_SHAPE);
            player.displayClientMessage(Component.translatable("message.essentialcraft5.magical_lantern.no_mru", LIGHT_COST)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (slagSlot >= 0) {
            player.getInventory().removeItem(slagSlot, 1);
        }
        level.markAndNotifyBlock(target, level.getChunkAt(target), old, light, Block.UPDATE_ALL, 512);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, target);
        return true;
    }

    /** 发放置事件，返回是否被取消。有混合端的带手重载就用它（见 {@link #PLACE_EVENT_WITH_HAND}） */
    private static boolean postPlaceEvent(Player player, BlockSnapshot snapshot, Direction face, InteractionHand hand) {
        if (PLACE_EVENT_WITH_HAND != null) {
            try {
                return (boolean) PLACE_EVENT_WITH_HAND.invoke((Entity) player, snapshot, face, hand);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        }
        return ForgeEventFactory.onBlockPlace(player, snapshot, face);
    }

    @Nullable
    private static MethodHandle findPlaceEventWithHand() {
        try {
            return MethodHandles.publicLookup().findStatic(ForgeEventFactory.class, "onBlockPlace",
                    MethodType.methodType(boolean.class, Entity.class, BlockSnapshot.class, Direction.class, InteractionHand.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    /** 物品栏（含盔甲、副手）里第一格魔力炉渣，没有返回 -1（原作同样按格子顺序找第一个） */
    public static int findSlag(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ECMaterials.MAGICAL_SLAG.get())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler ->
                tooltip.add(Component.translatable("tooltip.essentialcraft5.mru", handler.getMRU(), handler.getMaxMRU())
                        .withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.magical_lantern.use", LIGHT_COST).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.magical_lantern.hold").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 耐久（MRU）变化不触发重新举起的动画（原作 ItemMRUGeneric 只在换物品时触发） */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    public List<ItemStack> creativeVariants() {
        ItemStack empty = new ItemStack(this);
        ItemStack full = new ItemStack(this);
        full.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(MAX_MRU));
        return List.of(empty, full);
    }
}
