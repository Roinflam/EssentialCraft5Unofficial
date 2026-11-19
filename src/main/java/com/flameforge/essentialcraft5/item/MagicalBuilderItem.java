package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 创世神之杖（原作 ItemMagicalBuilder，meta 0~4 拆成 5 个物品，见 {@link BuilderMode}）：点两个方块圈出选区，潜行点一个方块把它选定，
 * 对着空气右键开工，魔杖按模式一格一格地填、清或换（{@link BuilderJobs}，分帧做，几秒做完），拆下来的东西进背包；潜行对空气右键停下或清掉两个点。
 * 自带 {@value MRUStaffItem#MAX_MRU} MRU（三阶 20000，见 {@link BuilderTier}），扣费先扣身上的储存物品（{@code MRUDrain}），创造模式不扣。
 * 一次能改多大、多远、每刻几格、每格多少 MRU 都按档位（{@link BuilderTier#stats}）：NBT 没有 {@value BuilderTier#TAG_TIER} 的是二阶，
 * 和以前的魔杖一模一样；魔力工作台升格后写上 3。一阶铺设魔杖是子类 {@link PavingWandItem}。
 * <h2>交互（只在服务端执行，提示由服务端发）</h2>
 * <ul>
 *   <li>右键方块走 {@link #onItemUseFirst}：在方块自己的右键之前，选点、选定方块不会打开箱子、开门。客户端只挥手；
 *       不潜行依次设第一点、第二点（点里存维度；两点距离超过 {@code max_point_distance}、选区超过 {@code max_volume} 格就不设），
 *       两点都有再点提示「已设置」；潜行点方块把它选定（{@link #recordFailure} 列出哪些不能选）。</li>
 *   <li>右键空气走 {@link #use}：不潜行开工；潜行时有活在做就停下（两个点留着），没有就清掉两个点（选定方块留着，同原作）。</li>
 * </ul>
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>原作两端都执行（客户端也改方块、也扣 MRU，留下幽灵方块），点里不存维度，不查人离多远，整个选区在一刻里做完、每格掉落撒一地；
 *       现在只在服务端、分帧、离选区 {@code max_reach} 格内、同维度才开工，掉落进背包（{@link BuilderJobs}）。</li>
 *   <li>原作选定方块时连朝向（meta）一起记，楼梯、横放的原木因此常常找不到材料；现在只记方块，放的时候按玩家视线和物品自己的规则算朝向。
 *       门、床、高花（要靠放下时补另一半）、带方块实体的方块（箱子、告示牌、机器：放出来没有主人、初始化不全）不能选定。</li>
 *   <li>原作右键方块写在 onItemRightClick 里，看着箱子点会先打开箱子；现在不会。</li>
 *   <li>工作台换模式时 MRU、两个点、选定方块都留着（原作全丢）。</li>
 * </ul>
 */
public class MagicalBuilderItem extends MRUStaffItem {

    private final BuilderMode mode;

    public MagicalBuilderItem(Properties properties, BuilderMode mode) {
        super(properties);
        this.mode = mode;
    }

    public BuilderMode mode() {
        return mode;
    }

    /** 容量按档位（三阶 20000）：每次读写时按物品当时的 NBT 算，升格后已存的 MRU 照留、上限立刻变大 */
    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, s -> BuilderTier.of(s).mruCapacity(), null);
    }

    /** 三阶叫「创世神之杖·升格（模式）」 */
    @Override
    public Component getName(ItemStack stack) {
        if (BuilderTier.of(stack) == BuilderTier.ASCENDED) {
            return Component.translatable(getDescriptionId(stack) + ".ascended");
        }
        return super.getName(stack);
    }

    /** 创造栏：空、满两份；填充模式另放一根满 MRU 的三阶 */
    @Override
    public List<ItemStack> creativeVariants() {
        List<ItemStack> out = new ArrayList<>(super.creativeVariants());
        if (mode == BuilderMode.FILL && !(this instanceof PavingWandItem)) {
            ItemStack ascended = new ItemStack(this);
            ascended.getOrCreateTag().putInt(BuilderTier.TAG_TIER, BuilderTier.ASCENDED.level());
            ascended.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(handler.getMaxMRU()));
            out.add(ascended);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 右键方块：选点、选定方块
    // ------------------------------------------------------------------

    /**
     * 右键方块（两端，在 RightClickBlock 事件之后、方块自己的右键之前）。客户端返回 SUCCESS（挥手，不预演方块的右键）；
     * 服务端做成返回 CONSUME、没做成返回 FAIL，这一下都到此为止，不会再开箱子、开门或调 {@link #use}。假玩家返回 PASS（什么都不做）。
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!ECProtection.isRealPlayer(player)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        if (player.isSecondaryUseActive()) {
            return recordBlock(player, stack, level, pos) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if (BuilderSelection.shape(stack).sized()) {
            // 铺面 / 直线 / 竖墙 / 沿面：右键方块就是按点中的格子和面开工（大单、材料不够的先确认，沿面先测量）；按住右键的自动连发不算（BuilderJobs.requestThrottled）
            if (player instanceof ServerPlayer serverPlayer) {
                if (BuilderJobs.requestThrottled(serverPlayer)) {
                    return InteractionResult.CONSUME;
                }
                BuilderJobs.StartResult result = BuilderJobs.request(serverPlayer, context.getHand(),
                        new BuilderShapes.Hit(pos, context.getClickedFace()), true);
                return accepted(result) ? InteractionResult.CONSUME : InteractionResult.FAIL;
            }
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer && BuilderSelection.second(stack) != null
                && BuilderSelection.inDimension(stack, level.dimension())) {
            // 两个点都已设置时点方块：有一单在等确认就当这一下是确认（和对空气右键一样），在施工就说正在施工，都不是才提示两个点都已设置
            if (BuilderJobs.awaitingConfirm(serverPlayer, stack)) {
                if (BuilderJobs.requestThrottled(serverPlayer)) {
                    return InteractionResult.CONSUME;
                }
                BuilderJobs.StartResult result = BuilderJobs.request(serverPlayer, context.getHand(), null, true);
                return accepted(result) ? InteractionResult.CONSUME : InteractionResult.FAIL;
            }
            if (BuilderJobs.isRunning(serverPlayer.getUUID())) {
                tell(player, Component.translatable("message.essentialcraft5.builder.busy"));
                return InteractionResult.CONSUME;
            }
        }
        return selectPoint(player, stack, level, pos) != PointResult.REJECTED ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** {@link #selectPoint} 的结果 */
    public enum PointResult {
        FIRST,
        SECOND,
        /** 两个点都已经设了，什么都没改 */
        BOTH_SET,
        /** 太远或选区太大，第二个点没设 */
        REJECTED
    }

    /**
     * 不潜行右键方块：设点（服务端，GameTest 直接调）。没有点、或者已有的点在别的维度时，这一格当第一个点（换一次选区）；
     * 只有第一个点时这一格当第二个点，先查两点距离和选区格数；两个点都有时只提示。
     */
    public static PointResult selectPoint(Player player, ItemStack stack, Level level, BlockPos pos) {
        BlockPos first = BuilderSelection.first(stack);
        if (first == null || !BuilderSelection.inDimension(stack, level.dimension())) {
            BuilderSelection.setFirst(stack, pos, level.dimension());
            tell(player, Component.translatable("message.essentialcraft5.builder.first", pos.getX(), pos.getY(), pos.getZ()));
            return PointResult.FIRST;
        }
        if (BuilderSelection.second(stack) != null) {
            tell(player, Component.translatable("message.essentialcraft5.builder.both_set"));
            return PointResult.BOTH_SET;
        }
        BuilderTier.Stats stats = BuilderTier.stats(stack);
        double distance = BuilderSelection.distance(first, pos);
        int maxDistance = stats.maxPointDistance();
        if (distance > maxDistance) {
            tellError(player, Component.translatable("message.essentialcraft5.builder.too_far", String.format(Locale.ROOT, "%.1f", distance), maxDistance));
            return PointResult.REJECTED;
        }
        long volume = BuilderSelection.volume(first, pos);
        int maxVolume = stats.maxVolume();
        if (volume > maxVolume) {
            tellError(player, Component.translatable("message.essentialcraft5.builder.too_big", volume, maxVolume));
            return PointResult.REJECTED;
        }
        BuilderSelection.setSecond(stack, pos);
        tell(player, Component.translatable("message.essentialcraft5.builder.second", pos.getX(), pos.getY(), pos.getZ()));
        return PointResult.SECOND;
    }

    /** 潜行右键方块：把它选定（服务端，GameTest 直接调）。不能选的提示一句、返回 false，原来选定的不变 */
    public static boolean recordBlock(Player player, ItemStack stack, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (recordFailure(state, level, pos) != null) {
            tellError(player, Component.translatable("message.essentialcraft5.builder.cannot_select", state.getBlock().getName()));
            return false;
        }
        BuilderSelection.setStoredBlock(stack, ForgeRegistries.BLOCKS.getKey(state.getBlock()));
        tell(player, Component.translatable("message.essentialcraft5.builder.block", state.getBlock().getName()));
        return true;
    }

    /**
     * 这个方块为什么不能选定；能选返回 null。不能选的：空气；液体（水、岩浆、气泡柱）；没有物品形态的（火、盆栽里的花、活塞头）；
     * 硬度 &lt; 0（基岩、屏障）；带方块实体的（箱子、营火、告示牌、机器：放置只改方块、不调 setPlacedBy，放出来的机器没有主人，
     * 别的模组靠 setPlacedBy 初始化的方块会坏）；分上下两半或头尾两半的（门、床、高花、垂滴叶：另一半要靠 setPlacedBy 补）；
     * 放下会吞掉物品本身的（细雪桶）；{@code essentialcraft5:builder_blacklist} 里的。
     */
    @Nullable
    public static String recordFailure(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) {
            return "air";
        }
        if (state.liquid()) {
            return "liquid";
        }
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return "no_item";
        }
        if (state.getDestroySpeed(level, pos) < 0F) {
            return "unbreakable";
        }
        if (state.hasBlockEntity()) {
            return "block_entity";
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)) {
            return "two_part";
        }
        if (item instanceof SolidBucketItem) {
            return "bucket";
        }
        if (state.is(ECBuilder.BUILDER_BLACKLIST)) {
            return "blacklisted";
        }
        return null;
    }

    /**
     * 魔杖里选定的方块（开工时复查一遍：标签改了、方块被别的模组换了之后不再能选的当没选）；没选、或者已经不能选返回 null
     */
    @Nullable
    public static Block storedBlock(ItemStack stack) {
        ResourceLocation id = BuilderSelection.storedBlock(stack);
        if (id == null || !ForgeRegistries.BLOCKS.containsKey(id)) {
            return null;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        return recordFailure(block.defaultBlockState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == null ? block : null;
    }

    // ------------------------------------------------------------------
    // 右键空气：开工、停下、清点
    // ------------------------------------------------------------------

    /**
     * 右键空气（对着方块时 {@link #onItemUseFirst} 已经处理完，不会走到这里）。客户端只挥手；服务端：
     * 不潜行开工（{@link BuilderJobs#start}，提示由它发）；潜行时有活在做就停下，没有就清掉两个点。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !ECProtection.isRealPlayer(player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isSecondaryUseActive()) {
            if (!BuilderJobs.stop(serverPlayer)) {
                if (BuilderSelection.shape(stack).sized()) {
                    tell(player, Component.translatable("message.essentialcraft5.builder.undo_hint"));
                } else {
                    BuilderSelection.clearPoints(stack);
                    tell(player, Component.translatable("message.essentialcraft5.builder.reset"));
                }
            }
            return InteractionResultHolder.consume(stack);
        }
        if (BuilderJobs.requestThrottled(serverPlayer)) {
            return InteractionResultHolder.consume(stack);   // 按住右键的自动连发：不开工、不提示
        }
        BuilderJobs.StartResult result = BuilderJobs.request(serverPlayer, hand, null, true);
        return accepted(result) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    /** 这一下被接下了：开工、排上队、等确认，或沿面开始测量 */
    private static boolean accepted(BuilderJobs.StartResult result) {
        return result == BuilderJobs.StartResult.STARTED || result == BuilderJobs.StartResult.QUEUED
                || result == BuilderJobs.StartResult.NEEDS_CONFIRM || result == BuilderJobs.StartResult.SURVEYING;
    }

    // ------------------------------------------------------------------
    // 提示
    // ------------------------------------------------------------------

    /** 原作提示的顺序：模式、两个点、选定方块，再是 MRU；模式下面一行写档位，最后几行是用法 */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BuilderTier.Stats stats = BuilderTier.stats(stack);
        if (mode == BuilderMode.REPLACE_ALL) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.mode." + mode.id(), stats.placeCost(), stats.replaceCost())
                    .withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.mode." + mode.id(), stats.modeCost(mode)).withStyle(ChatFormatting.DARK_PURPLE));
        }
        tierTooltip(stats, tooltip);
        moduleTooltip(stack, stats, tooltip);
        BuilderShape shape = BuilderSelection.shape(stack);
        Component shapeName = Component.translatable("message.essentialcraft5.builder.shape." + shape.id());
        if (shape.resizable()) {
            int[] size = BuilderSelection.size(stack, shape);
            tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.shape_sized", shapeName, size[0], size[1]).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.shape", shapeName).withStyle(ChatFormatting.GRAY));
        }
        BlockPos first = BuilderSelection.first(stack);
        BlockPos second = BuilderSelection.second(stack);
        if (first != null && !shape.sized()) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.p1", first.getX(), first.getY(), first.getZ()).withStyle(ChatFormatting.GRAY));
        }
        if (second != null && !shape.sized()) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.p2", second.getX(), second.getY(), second.getZ()).withStyle(ChatFormatting.GRAY));
        }
        ResourceLocation dimension = BuilderSelection.dimension(stack);
        if (first != null && dimension != null && level != null && !level.dimension().location().equals(dimension)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.other_dimension", dimension.toString()).withStyle(ChatFormatting.GRAY));
        }
        ResourceLocation stored = BuilderSelection.storedBlock(stack);
        if (stored != null && ForgeRegistries.BLOCKS.containsKey(stored)) {
            Block block = ForgeRegistries.BLOCKS.getValue(stored);
            if (block != null) {
                tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.block", block.getName()).withStyle(ChatFormatting.GRAY));
            }
        }
        mruTooltip(stack, tooltip);
        if (shape == BuilderShape.FACE) {
            tooltip.add(usage("tooltip.essentialcraft5.builder.use_face"));
        } else if (shape.sized()) {
            tooltip.add(usage("tooltip.essentialcraft5.builder.use_plane"));
        } else {
            tooltip.add(usage("tooltip.essentialcraft5.builder.use_blocks"));
            tooltip.add(usage("tooltip.essentialcraft5.builder.use_air"));
        }
        // 魔杖键的名字由客户端按当前绑定显示（keybind 文本）
        tooltip.add(usage(shape.resizable() ? "tooltip.essentialcraft5.builder.wand_key_sized" : "tooltip.essentialcraft5.builder.wand_key",
                Component.keybind("key.essentialcraft5.builder_wand")));
        tooltip.add(usage(stats.tier() == BuilderTier.PAVER ? "tooltip.essentialcraft5.builder.paver_upgrade" : "tooltip.essentialcraft5.builder.cycle"));
    }

    /**
     * 档位那一行：「一阶 / 二阶 / 三阶：一次最多 N 格，边长 S 格，施工距离 R 格」；一阶再写只有填充、形状只有三种，三阶再写能撤销几次
     */
    static void tierTooltip(BuilderTier.Stats stats, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.tier." + stats.tier().level(),
                stats.maxVolume(), stats.maxSide(), stats.maxReach()).withStyle(ChatFormatting.GRAY));
        if (stats.tier() == BuilderTier.PAVER) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.tier.paver_limits").withStyle(ChatFormatting.GRAY));
        } else if (stats.undoDepth() > 1) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.tier.undo_depth", stats.undoDepth()).withStyle(ChatFormatting.GRAY));
        }
    }

    /** 装了模块时一行「模块（已装 / 槽数）：扩域、疾工」，只列生效的（{@link BuilderModules#active}）；没装不显示 */
    public static void moduleTooltip(ItemStack stack, BuilderTier.Stats stats, List<Component> tooltip) {
        List<BuilderModules.Kind> modules = BuilderModules.active(stack);
        if (modules.isEmpty()) {
            return;
        }
        MutableComponent names = Component.empty();
        for (int i = 0; i < modules.size(); i++) {
            if (i > 0) {
                names.append(Component.translatable("tooltip.essentialcraft5.builder.modules.separator"));
            }
            names.append(Component.translatable("tooltip.essentialcraft5.builder.modules." + modules.get(i).id()));
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.builder.modules", modules.size(), stats.moduleSlots(), names)
                .withStyle(ChatFormatting.GRAY));
    }

    /** 魔杖的提示一律紫色（原作 DARK_PURPLE），显示在动作栏 */
    static void tell(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /** 没做成的提示（红色，动作栏） */
    static void tellError(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
    }
}
