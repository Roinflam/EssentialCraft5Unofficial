package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.blockentity.MagicalMirrorBlockEntity;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 控制棒（ECMirror）：先右键一面魔力镜记下它，再（潜行）右键 {@code max_distance} 格内的容器，镜子就往那里送东西。对应原作 ItemControlRod（106 行）。
 * <p>
 * NBT：{@value #MIRROR_KEY} = {dim: 维度 id, pos: int[3]}（原作只存坐标，提示行读的 dim 从没写过，永远显示 0，M-8）。
 * 链接成功后清掉，控制棒可以接着链接下一面镜子；潜行右键空气也清掉。
 *
 * <h2>链接时查什么（{@link #tryLink}，原作只看「是容器、8 格内」）</h2>
 * 维度相同（运行时源和目标都在镜子那个世界）→ 距离 ≤ {@code max_distance}（直线）→ 镜子还在 → 不是镜子正下方那个容器（原作没拦，原地倒腾）→
 * 目标顶面有物品栏（运行时也按顶面送）→ 链接的人是真玩家，而且<b>镜子格、镜子正下方的源容器格、目标格三格他都能动</b>（{@link ECProtection#check}，
 * 用他的身份发探测用的破坏事件）。原作任何人都能把别人放在自家箱子上的镜子改链到自己的箱子（每 3 秒偷 1 件，M-3），也能往别人的容器里塞（M-4）；
 * 只查目标格不够：立体领地的顶层箱子上面那格可能在领地外，别人能把镜子放在上面抽东西（logistics.md §4.6）。
 * 三格任一被拒 → 「没有权限」，任一还给不出结论（本刻探测预算用完、区块没加载）→ 「稍后再试」，都不链接、不清控制棒。链接后不再查（同绑定宝石，policy §2.2）。
 * <p>
 * 逻辑都在服务端，{@code useOn} 不改方块（Forge 的放置捕获没有快照，不会发放置事件）。
 */
public class ControlRodItem extends Item {

    public static final String MIRROR_KEY = "mirror";
    private static final String DIM_KEY = "dim";
    private static final String POS_KEY = "pos";

    /** 链接的结果（{@link #tryLink}） */
    public enum LinkResult {
        LINKED,
        /** 记下的镜子在别的维度 */
        OTHER_DIMENSION,
        /** 目标离镜子太远 */
        TOO_FAR,
        /** 记下的那格已经没有魔力镜了（区块已加载）；控制棒忘掉它 */
        MIRROR_MISSING,
        /** 点的就是镜子正下方、镜子从里面取东西的那个容器 */
        SOURCE_ITSELF,
        /** 目标顶面没有物品栏 */
        NOT_CONTAINER,
        /** 不是真玩家（机械手、自动化），或者三格里有他不能动的 */
        NO_PERMISSION,
        /** 现在给不出结论（探测预算用完、区块没加载），稍后再试 */
        TRY_LATER
    }

    public ControlRodItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    /** 记下的魔力镜；没记或数据不对返回 null */
    @Nullable
    public static GlobalPos selectedMirror(ItemStack rod) {
        CompoundTag tag = rod.getTagElement(MIRROR_KEY);
        if (tag == null || !tag.contains(POS_KEY, Tag.TAG_INT_ARRAY) || !tag.contains(DIM_KEY, Tag.TAG_STRING)) {
            return null;
        }
        int[] pos = tag.getIntArray(POS_KEY);
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString(DIM_KEY));
        if (pos.length != 3 || dim == null) {
            return null;
        }
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), new BlockPos(pos[0], pos[1], pos[2]));
    }

    public static void selectMirror(ItemStack rod, ResourceKey<Level> dimension, BlockPos pos) {
        CompoundTag tag = rod.getOrCreateTagElement(MIRROR_KEY);
        tag.putString(DIM_KEY, dimension.location().toString());
        tag.putIntArray(POS_KEY, new int[] {pos.getX(), pos.getY(), pos.getZ()});
    }

    /** 忘掉记下的镜子（只删自己的键，改名之类的留着） */
    public static void clearMirror(ItemStack rod) {
        rod.removeTagKey(MIRROR_KEY);
    }

    // ------------------------------------------------------------------
    // 链接
    // ------------------------------------------------------------------

    /**
     * 服务端：把 mirror 链接到 target（点它的是 player）。顺序见类注释；只有最后一步探测权限，前面的检查不发任何事件。
     * 成功时镜子记下目标和自己的位置、正在送的那件作废。GameTest 直接调它。
     */
    public static LinkResult tryLink(@Nullable Player player, Level level, GlobalPos mirror, BlockPos target) {
        if (!mirror.dimension().equals(level.dimension())) {
            return LinkResult.OTHER_DIMENSION;
        }
        BlockPos mirrorPos = mirror.pos();
        if (!MagicalMirrorBlockEntity.withinRange(mirrorPos, target)) {
            return LinkResult.TOO_FAR;
        }
        BlockEntity mirrorBe = ECLevels.loadedBlockEntity(level, mirrorPos);
        if (!(mirrorBe instanceof MagicalMirrorBlockEntity mirrorEntity) || mirrorBe.isRemoved()) {
            return ECLevels.isFullyLoaded(level, mirrorPos) ? LinkResult.MIRROR_MISSING : LinkResult.TRY_LATER;
        }
        BlockPos source = mirrorPos.below();
        if (target.equals(source)) {
            return LinkResult.SOURCE_ITSELF;
        }
        BlockEntity targetBe = ECLevels.loadedBlockEntity(level, target);
        if (targetBe == null || !targetBe.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent()) {
            return LinkResult.NOT_CONTAINER;
        }
        if (!ECProtection.isRealPlayer(player)) {
            return LinkResult.NO_PERMISSION;
        }
        boolean unknown = false;
        for (BlockPos cell : new BlockPos[] {mirrorPos, source, target}) {
            ECProtection.Verdict verdict = ECProtection.check(player, level, cell, null);
            if (verdict == ECProtection.Verdict.DENY) {
                return LinkResult.NO_PERMISSION;
            }
            if (verdict == ECProtection.Verdict.UNKNOWN) {
                unknown = true;
            }
        }
        if (unknown) {
            return LinkResult.TRY_LATER;
        }
        mirrorEntity.link(target);
        return LinkResult.LINKED;
    }

    /**
     * 点到魔力镜：记下它（点别的镜子就换成那一面）。点到别的方块、手里记着镜子：试着链接过去。
     * 不潜行时箱子这类容器自己的右键会先打开界面，所以要潜行点；原作也是这样。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack rod = context.getItemInHand();
        Player player = context.getPlayer();
        if (level.getBlockEntity(pos) instanceof MagicalMirrorBlockEntity) {
            if (!level.isClientSide) {
                selectMirror(rod, level.dimension(), pos);
                message(player, Component.translatable("message.essentialcraft5.control_rod.mirror_selected").withStyle(ChatFormatting.GREEN));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        GlobalPos mirror = selectedMirror(rod);
        if (mirror == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            // 客户端不知道领地的结论，照常挥手；结果由服务端发到动作栏
            return InteractionResult.SUCCESS;
        }
        LinkResult result = tryLink(player, level, mirror, pos);
        if (result == LinkResult.LINKED || result == LinkResult.MIRROR_MISSING) {
            clearMirror(rod);
        }
        message(player, resultMessage(result));
        if (result == LinkResult.LINKED) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1F, 1.2F);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    /** 潜行右键空气：忘掉记下的镜子 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack rod = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || selectedMirror(rod) == null) {
            return InteractionResultHolder.pass(rod);
        }
        if (!level.isClientSide) {
            clearMirror(rod);
            message(player, Component.translatable("message.essentialcraft5.control_rod.cleared"));
        }
        return InteractionResultHolder.sidedSuccess(rod, level.isClientSide);
    }

    public static Component resultMessage(LinkResult result) {
        return switch (result) {
            case LINKED -> Component.translatable("message.essentialcraft5.control_rod.mirror_linked").withStyle(ChatFormatting.GREEN);
            case OTHER_DIMENSION -> Component.translatable("message.essentialcraft5.control_rod.other_dimension").withStyle(ChatFormatting.RED);
            case TOO_FAR -> Component.translatable("message.essentialcraft5.control_rod.too_far",
                    MagicalMirrorBlockEntity.Config.maxDistance()).withStyle(ChatFormatting.RED);
            case MIRROR_MISSING -> Component.translatable("message.essentialcraft5.control_rod.mirror_missing").withStyle(ChatFormatting.RED);
            case SOURCE_ITSELF -> Component.translatable("message.essentialcraft5.control_rod.source_itself").withStyle(ChatFormatting.RED);
            case NOT_CONTAINER -> Component.translatable("message.essentialcraft5.control_rod.not_container").withStyle(ChatFormatting.RED);
            case NO_PERMISSION -> Component.translatable("message.essentialcraft5.no_permission").withStyle(ChatFormatting.RED);
            case TRY_LATER -> Component.translatable("message.essentialcraft5.control_rod.try_later").withStyle(ChatFormatting.GOLD);
        };
    }

    private static void message(@Nullable Player player, Component text) {
        if (player != null) {
            player.displayClientMessage(text, true);
        }
    }

    /** 记着镜子时发光，一眼看出「这根还没用完」 */
    @Override
    public boolean isFoil(ItemStack stack) {
        return selectedMirror(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos mirror = selectedMirror(stack);
        if (mirror == null) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.control_rod.usage",
                    MagicalMirrorBlockEntity.Config.maxDistance()).withStyle(ChatFormatting.GRAY));
            return;
        }
        BlockPos pos = mirror.pos();
        tooltip.add(Component.translatable("tooltip.essentialcraft5.control_rod.linked").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("x: " + pos.getX()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("y: " + pos.getY()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("z: " + pos.getZ()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.dimension",
                mirror.dimension().location().toString()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.control_rod.next",
                MagicalMirrorBlockEntity.Config.maxDistance()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
