package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.advancement.ECAdvancements;
import com.flameforge.essentialcraft5.hoanna.survey.SurveySearches;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaSurvey;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * 海纳尔勘测图 {@code hoanna_survey_map}（第十三批 13d-H3，batch13.md §6.3「勘测图」；HS §2.3.1）：三张残破勘测图加一颗地脉结晶拼成。
 * <ul>
 *   <li><b>只在海纳尔里用</b>。右键按「要找的种类」找最近一处并绑定；潜行右键换种类（{@link #KINDS} 的顺序：封印宝库 → 沉没城区 →
 *       审判庭前哨 → 城镇废墟，这个世界里没有那种结构的自动跳过——13d 只有城镇废墟，13e 的三座结构注册以后自己出现）。</li>
 *   <li>找的过程走 {@link SurveySearches}（结构预测 + 全服预算，每人同时一个，不加载区块），半径 {@code general.hoanna_survey.search_radius}；
 *       跳过本图 {@code Skip} 表里的点。</li>
 *   <li>拿在主手或副手：客户端动作栏写八方位和距离（{@code client/HoannaSurveyClient}，按物品 NBT 自己算，不发包）；夜里或黑暗天灾中、
 *       目标在 32~512 格时只给自己画一道淡紫光柱。</li>
 *   <li>到点：服务端每 {@value #ARRIVAL_INTERVAL} 刻查一次，进到 {@code arrive_radius} 以内发进度
 *       {@code guide/hoanna_explore/survey_arrive}、把这一处记进 {@code Skip}（≤ {@value #SKIP_LIMIT} 条，满了挤掉最早的）、解绑。
 *       城镇偶尔「布局不成立」只剩一片空地（预测不模拟），到点照样记进 Skip。</li>
 * </ul>
 * NBT（根标签）：{@code Kind}（种类 id，缺省 = 第一个可用的）、{@code X} / {@code Z}（绑定的点，起点区块中心）、{@code Skip}（起点区块的
 * {@code ChunkPos.toLong}）、{@code Pending}（正在找时的令牌，找完写回同一张图）。物品模型三态（{@link #STATE_PREDICATE}）：
 * 0 没绑定、0.5 没绑定但走到过、1 已绑定。
 */
public class HoannaSurveyMapItem extends Item {

    public static final String TAG_KIND = "Kind";
    public static final String TAG_X = "X";
    public static final String TAG_Z = "Z";
    public static final String TAG_SKIP = "Skip";
    public static final String TAG_PENDING = "Pending";
    /** Skip 表最多几条 */
    public static final int SKIP_LIMIT = 16;
    /** 服务端多久查一次到没到 */
    public static final int ARRIVAL_INTERVAL = 20;
    /** 物品模型的属性：0 没绑定、{@link #STATE_VISITED} 走到过、{@link #STATE_BOUND} 已绑定 */
    public static final ResourceLocation STATE_PREDICATE = EssentialCraft5.id("survey_state");
    public static final float STATE_VISITED = 0.5F;
    public static final float STATE_BOUND = 1F;
    /** 进度：走到勘测图标的地方 */
    public static final ResourceLocation ADVANCEMENT_ARRIVE = EssentialCraft5.id("guide/hoanna_explore/survey_arrive");

    public static final String MSG_NOT_HOANNA = "message.essentialcraft5.hoanna_survey_map.not_hoanna";
    public static final String MSG_SEARCHING = "message.essentialcraft5.hoanna_survey_map.searching";
    public static final String MSG_BOUND = "message.essentialcraft5.hoanna_survey_map.bound";
    public static final String MSG_NOT_FOUND = "message.essentialcraft5.hoanna_survey_map.not_found";
    public static final String MSG_KIND = "message.essentialcraft5.hoanna_survey_map.kind";
    public static final String MSG_NO_KIND = "message.essentialcraft5.hoanna_survey_map.no_kind";
    public static final String MSG_ARRIVED = "message.essentialcraft5.hoanna_survey_map.arrived";

    /**
     * 能找的一种结构。
     *
     * @param id        种类 id（NBT 里存它，也是名字的语言键 {@code survey.essentialcraft5.kind.<id>} 的尾巴）
     * @param structure 结构（{@code worldgen/structure} 的注册名）
     */
    public record Kind(String id, ResourceLocation structure) {

        public String nameKey() {
            return "survey.essentialcraft5.kind." + id;
        }

        public ResourceKey<Structure> key() {
            return ResourceKey.create(Registries.STRUCTURE, structure);
        }
    }

    /** 种类的顺序（batch13.md §6.3）：宝库 → 沉城 → 前哨 → 城镇。前三种是 13e 的结构，没注册时跳过 */
    public static final List<Kind> KINDS = List.of(
            new Kind("sealed_vault", EssentialCraft5.id("sealed_vault")),
            new Kind("sunken_district", EssentialCraft5.id("sunken_district")),
            new Kind("inquisitorium_outpost", EssentialCraft5.id("inquisitorium_outpost")),
            new Kind("hoanna_town", EssentialCraft5.id("hoanna_town")));
    /** 城镇废墟（13d 唯一有的一种；里程碑也找它） */
    public static final Kind TOWN = KINDS.get(KINDS.size() - 1);

    public HoannaSurveyMapItem(Properties properties) {
        super(properties);
    }

    // ================================================================== 种类

    /** 种类的下标（不认得返回 −1） */
    public static int kindIndex(String id) {
        for (int i = 0; i < KINDS.size(); i++) {
            if (KINDS.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** current 之后（循环）第一个可用的种类；current = −1 时从头找；一个都不可用返回 −1。纯函数 */
    public static int nextKind(int current, IntPredicate available) {
        int n = KINDS.size();
        for (int step = 1; step <= n; step++) {
            int i = Math.floorMod(current + step, n);
            if (current < 0) {
                i = step - 1;
            }
            if (available.test(i)) {
                return i;
            }
        }
        return -1;
    }

    /** 物品上记着的种类，不可用（或没记）就取第一个可用的；一个都不可用返回 −1 */
    public static int currentKind(ItemStack stack, IntPredicate available) {
        CompoundTag tag = stack.getTag();
        int stored = tag == null ? -1 : kindIndex(tag.getString(TAG_KIND));
        return stored >= 0 && available.test(stored) ? stored : nextKind(-1, available);
    }

    /** 这种结构在这个世界里的预测目标（没注册、这个世界不会生成它的返回空表） */
    public static List<StructureForecast.Target> targets(ServerLevel level, StructureForecast.World world, Kind kind) {
        Optional<Holder.Reference<Structure>> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(kind.key());
        return holder.<List<StructureForecast.Target>>map(h -> StructureForecast.targets(world, HolderSet.direct(h))).orElse(List.of());
    }

    /** 这个世界里能找的种类 */
    public static IntPredicate available(ServerLevel level, StructureForecast.World world) {
        boolean[] ok = new boolean[KINDS.size()];
        for (int i = 0; i < ok.length; i++) {
            ok[i] = !targets(level, world, KINDS.get(i)).isEmpty();
        }
        return i -> i >= 0 && i < ok.length && ok[i];
    }

    // ================================================================== 绑定与跳过表

    /** 绑定的点（起点区块中心），没绑定为 null */
    @Nullable
    public static BlockPos target(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_X, Tag.TAG_ANY_NUMERIC) || !tag.contains(TAG_Z, Tag.TAG_ANY_NUMERIC)) {
            return null;
        }
        return new BlockPos(tag.getInt(TAG_X), 0, tag.getInt(TAG_Z));
    }

    public static void bind(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_X, pos.getX());
        tag.putInt(TAG_Z, pos.getZ());
    }

    public static void unbind(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_X);
            tag.remove(TAG_Z);
        }
    }

    /** 跳过表（先进的在前） */
    public static List<ChunkPos> skipped(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        List<ChunkPos> out = new ArrayList<>();
        if (tag != null && tag.contains(TAG_SKIP, Tag.TAG_LONG_ARRAY)) {
            for (long packed : tag.getLongArray(TAG_SKIP)) {
                out.add(new ChunkPos(packed));
            }
        }
        return out;
    }

    /** 记进跳过表：已有的不重复记，超过 {@value #SKIP_LIMIT} 条挤掉最早的 */
    public static void addSkip(ItemStack stack, ChunkPos chunk) {
        Set<Long> list = new LinkedHashSet<>();
        for (ChunkPos old : skipped(stack)) {
            list.add(old.toLong());
        }
        list.add(chunk.toLong());
        List<Long> all = new ArrayList<>(list);
        List<Long> kept = all.subList(Math.max(0, all.size() - SKIP_LIMIT), all.size());
        stack.getOrCreateTag().put(TAG_SKIP, new LongArrayTag(kept.stream().mapToLong(Long::longValue).toArray()));
    }

    /** 物品模型的那个值 */
    public static float modelState(ItemStack stack) {
        if (target(stack) != null) {
            return STATE_BOUND;
        }
        return skipped(stack).isEmpty() ? 0F : STATE_VISITED;
    }

    /** 水平距离（玩家到目标那一格的中心） */
    public static double horizontalDistance(BlockPos target, double x, double z) {
        double dx = target.getX() + 0.5D - x;
        double dz = target.getZ() + 0.5D - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 记着的种类名（给提示框、动作栏）；还没记（新拼的图，第一次右键时才定）为 null */
    @Nullable
    public static Component kindName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        int index = tag == null ? -1 : kindIndex(tag.getString(TAG_KIND));
        return index < 0 ? null : Component.translatable(KINDS.get(index).nameKey());
    }

    // ================================================================== 用

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !ECProtection.isRealPlayer(serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            cycleKind(serverPlayer, serverLevel, stack);
        } else {
            startSurvey(serverPlayer, serverLevel, stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 潜行右键：换到下一个这个世界里有的种类，解绑、作废正在找的；返回换到的下标（不在海纳尔、一个都没有返回 −1）。
     * 图上还没记种类（或记着的这个世界里没有）时，这一下先定成第一个有的种类、不往后跳，免得新图第一下就把默认的那种跳过去。
     */
    public static int cycleKind(ServerPlayer player, ServerLevel level, ItemStack stack) {
        if (!ECHoanna.isHoanna(level)) {
            player.displayClientMessage(Component.translatable(MSG_NOT_HOANNA), true);
            return -1;
        }
        IntPredicate available = available(level, SurveySearches.world(level));
        CompoundTag stored = stack.getTag();
        int storedIndex = stored == null ? -1 : kindIndex(stored.getString(TAG_KIND));
        int next = storedIndex >= 0 && available.test(storedIndex) ? nextKind(storedIndex, available) : nextKind(-1, available);
        if (next < 0) {
            player.displayClientMessage(Component.translatable(MSG_NO_KIND), true);
            return -1;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_KIND, KINDS.get(next).id());
        tag.remove(TAG_PENDING);
        unbind(stack);
        SurveySearches.cancel(player.getUUID());
        player.displayClientMessage(Component.translatable(MSG_KIND, Component.translatable(KINDS.get(next).nameKey())), true);
        return next;
    }

    /**
     * 右键：找当前种类最近的一处（结果下一刻起回到这张图上）。返回发起的搜索；不在海纳尔、没有可找的、已经在找时返回 null。
     * level 是找的世界（平时就是玩家所在的世界）；中心是玩家脚下。
     */
    @Nullable
    public static SurveySearches.Job startSurvey(ServerPlayer player, ServerLevel level, ItemStack stack) {
        if (!ECHoanna.isHoanna(level)) {
            player.displayClientMessage(Component.translatable(MSG_NOT_HOANNA), true);
            return null;
        }
        if (SurveySearches.busy(player.getUUID())) {
            player.displayClientMessage(Component.translatable(MSG_SEARCHING), true);
            return null;
        }
        StructureForecast.World world = SurveySearches.world(level);
        IntPredicate available = available(level, world);
        int kindIndex = currentKind(stack, available);
        if (kindIndex < 0) {
            player.displayClientMessage(Component.translatable(MSG_NO_KIND), true);
            return null;
        }
        Kind kind = KINDS.get(kindIndex);
        int radius = ECHoannaSurvey.Config.values().searchRadius();
        Set<ChunkPos> skip = new LinkedHashSet<>(skipped(stack));
        long token = player.getRandom().nextLong() | 1L;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_KIND, kind.id());
        tag.putLong(TAG_PENDING, token);
        return SurveySearches.start(player, level, world, targets(level, world, kind), player.blockPosition(), radius, skip::contains, 0,
                (who, result) -> finishSurvey(who, token, kind, radius, result));
    }

    /** 找完：写回带着这个令牌的那张图（手上优先，再找背包；已经扔掉、换过种类的不管） */
    static void finishSurvey(ServerPlayer player, long token, Kind kind, int radius, Optional<BlockPos> result) {
        ItemStack stack = findPending(player, token);
        if (stack.isEmpty()) {
            return;
        }
        stack.getOrCreateTag().remove(TAG_PENDING);
        if (result.isEmpty()) {
            unbind(stack);
            player.displayClientMessage(Component.translatable(MSG_NOT_FOUND, radius, Component.translatable(kind.nameKey())), true);
            return;
        }
        BlockPos pos = result.get();
        bind(stack, pos);
        int heading = InquisitoriumCoordinatesItem.heading(pos.getX() + 0.5D - player.getX(), pos.getZ() + 0.5D - player.getZ());
        player.displayClientMessage(Component.translatable(MSG_BOUND, Component.translatable(kind.nameKey()),
                Component.translatable(InquisitoriumCoordinatesItem.headingKey(heading)),
                Mth.floor(horizontalDistance(pos, player.getX(), player.getZ()))).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                SoundSource.PLAYERS, 0.6F, 1.1F);
    }

    private static ItemStack findPending(Player player, long token) {
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (isPending(stack, token)) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (isPending(stack, token)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isPending(ItemStack stack, long token) {
        return stack.getItem() instanceof HoannaSurveyMapItem && stack.getTag() != null && stack.getTag().getLong(TAG_PENDING) == token;
    }

    // ================================================================== 到点

    /**
     * 拿着已绑定的图的玩家到了没有：在海纳尔、水平距离 ≤ {@code arrive_radius} 就发进度、把这一处记进 Skip、解绑，返回 true。
     * 服务端每 {@value #ARRIVAL_INTERVAL} 刻调（{@link #inventoryTick}；GameTest 直接调）。
     */
    public static boolean checkArrival(ServerPlayer player, ItemStack stack) {
        BlockPos target = target(stack);
        if (target == null || !ECProtection.isRealPlayer(player) || !ECHoanna.isHoanna(player.level())) {
            return false;
        }
        if (horizontalDistance(target, player.getX(), player.getZ()) > ECHoannaSurvey.Config.values().arriveRadius()) {
            return false;
        }
        addSkip(stack, new ChunkPos(target));
        unbind(stack);
        ECAdvancements.awardIfPresent(player, ADVANCEMENT_ARRIVE);
        player.displayClientMessage(Component.translatable(MSG_ARRIVED).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (!selected && player.getOffhandItem() != stack) {
            return;
        }
        if ((player.tickCount + player.getId()) % ARRIVAL_INTERVAL == 0) {
            checkArrival(player, stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Component kind = kindName(stack);
        if (kind != null) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.hoanna_survey_map.kind", kind).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        BlockPos target = target(stack);
        if (target != null) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.hoanna_survey_map.bound", target.getX(), target.getZ())
                    .withStyle(ChatFormatting.GRAY));
        }
        int visited = skipped(stack).size();
        if (visited > 0) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.hoanna_survey_map.visited", visited).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.hoanna_survey_map.usage").withStyle(ChatFormatting.GRAY));
    }
}
