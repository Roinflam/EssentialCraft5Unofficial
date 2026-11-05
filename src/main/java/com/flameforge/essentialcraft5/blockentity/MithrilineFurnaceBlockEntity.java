package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.espe.ESPEAccess;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.menu.MachineData;
import com.flameforge.essentialcraft5.menu.MithrilineFurnaceMenu;
import com.flameforge.essentialcraft5.recipe.MithrilineFurnaceRecipe;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.structure.StructureCache;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructureResult;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 米斯莱熔炉。对应原作 TileMithrilineFurnace。
 * <p>
 * 0 号槽原料、1 号槽产物；没有 MRU（上限 0）、没有绑定宝石槽，自存 10000 ESPE（原作 MaxESPE）。
 * 结构（{@link ECMithriline#FURNACE_STRUCTURE}：24 块米斯莱护板）完整时，每 tick 依次从 13 个固定位置（{@link #SOURCES}，
 * 原作 possiblePowerSources 的顺序）的 ESPE 源（一般是米斯莱水晶，任何带 ESPE 的方块实体都行，比如创造 ESPE 源）抽到自己满为止；
 * 输入格的东西对得上某条配方、数量够、产物放得下时，从存储里取「还差多少」的 ESPE 加进进度，进度够了就出一份产物、扣掉原料。
 * 存储够的话一 tick 就做完一份，所以实际速度看 ESPE 进账（主世界一根水晶柱 0.025 ESPE/t）。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li><b>换了配方，进度清零</b>（原作 bug）：原作的进度不记属于哪条配方，直接把输入格的东西换成另一种（界面里点一下对调），
 *       进度会带到新配方上。这里进度连同配方 ID 一起记，选中的配方变了就从 0 开始。</li>
 *   <li><b>ESPE 只进不出</b>：原作熔炉的 ESPE capability 不分面，谁都能从熔炉里抽走 ESPE；这里对外只收不给（{@link ESPEAccess#INSERT}）。</li>
 *   <li><b>结构不再每 tick 查</b>：原作每 tick（客户端也）查 24 块方块；这里用 {@link StructureCache}，至多每 20 tick 重算一次，
 *       区块没加载时保留上次的结果。ESPE 源的位置也跟着每 20 tick 重新找一次（只记有 ESPE 的格子），平时只查这几格。</li>
 *   <li>漏斗、管道只能往输入格塞对得上配方的东西（原作什么都收，塞进转化不了的东西会把输入格堵住）；玩家手放不受限制。</li>
 *   <li>产物合并要求 NBT 一致（原作只比物品），放不下时停在原处、进度保留（原作同样不推进）。</li>
 *   <li>逻辑只在服务端；界面数值走菜单同步，另加了一条状态（结构、原料、ESPE 哪里不够），原作界面上什么都不写。</li>
 *   <li>粒子（结构完整时熔炉冒绿色的光点、水晶柱上下各一个）改在客户端按同步来的「结构是否完整」冒，数量比原作少（原作每 tick 10 个火焰）。</li>
 * </ul>
 * 结构第一次成形时，给 16 格内的玩家发进度「绿焰」（main/mithriline_furnace）；结构完整时打开界面的玩家也会得到。
 */
public class MithrilineFurnaceBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.mithrilinefurnace */
    public static final class Config {
        public static ForgeConfigSpec.DoubleValue maxESPE;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("mithriline_furnace");
            // 上限按界面同步能表示的范围（MachineData 的 ESPE 是 ×100 定点放进 int，约 2147 万）；再大界面上的数字和 ESPE 球都会算错
            maxESPE = builder.defineInRange("max_espe", 10000D, 1D, MachineData.MAX_SYNCED_ESPE);
            builder.pop();
        }
    }

    /** 界面上显示的状态（服务端算好，随菜单同步）。字色：红色要玩家处理，黄色是暂时条件不够，绿色在做，白色没事做 */
    public enum Status {
        /** 结构不完整（上次确定的结果是不符） */
        INCOMPLETE(0xFF0000),
        /** 还没确定过结构：有一部分在没加载的区块里 */
        UNLOADED(0xFFFF00),
        /** 输入格空着 */
        IDLE(0xFFFFFF),
        /** 输入格的东西没有配方 */
        NO_RECIPE(0xFFFFFF),
        /** 有配方，但原料数量不够（比如铁锭要 8 个） */
        NEED_MORE(0xFFFF00),
        OUTPUT_FULL(0xFF0000),
        /** 这一 tick 一点 ESPE 都没取到 */
        NO_ESPE(0xFFFF00),
        WORKING(0x00FF00);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public MutableComponent label() {
            return Component.translatable("gui.essentialcraft5.mithriline_furnace." + name().toLowerCase(Locale.ROOT));
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
        }
    }

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOTS = 2;

    public static final String TAG_PROGRESS = "progress";
    public static final String TAG_RECIPE = "recipe";

    /** 原作 new ESPEStorage(maxEnergy, 0) */
    public static final int ESPE_TIER = 0;

    /**
     * 原作 possiblePowerSources（顺序照抄，抽的时候按这个顺序）：正上方 1、同层四个斜角、外圈四个方向高 1 格、外圈四角高 2 格。
     * 和结构里的水晶柱位置（{@link ECMithriline#FURNACE_PILLAR}）是同一组格子，GameTest 核对。
     */
    public static final List<Vec3i> SOURCES = List.of(
            new Vec3i(0, 1, 0),
            new Vec3i(1, 0, 1), new Vec3i(-1, 0, 1), new Vec3i(1, 0, -1), new Vec3i(-1, 0, -1),
            new Vec3i(2, 1, 0), new Vec3i(-2, 1, 0), new Vec3i(0, 1, 2), new Vec3i(0, 1, -2),
            new Vec3i(2, 2, 2), new Vec3i(-2, 2, 2), new Vec3i(2, 2, -2), new Vec3i(-2, 2, -2));

    /** 多少 tick 重新找一次 ESPE 源（和结构复查同频） */
    public static final int SOURCE_REFRESH_INTERVAL = StructureCache.MIN_INTERVAL;
    /** 结构第一次成形时，多远以内的玩家得到进度 */
    public static final double AWARD_RADIUS = 16D;
    public static final ResourceLocation ADVANCEMENT = EssentialCraft5.id("main/mithriline_furnace");
    /** 进度 JSON 里的条件名（minecraft:impossible，只能由这里发） */
    public static final String ADVANCEMENT_CRITERION = "formed";

    /** 浮点累加的容差：进度差这么一点也算够了（免得 59.99999999 再等一 tick） */
    private static final double EPSILON = 1.0E-6D;
    /** 粒子颜色：原作水晶柱上的 REDSTONE 粒子传了 (-1, 1, 0)，就是纯绿 */
    private static final DustParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.1F, 1F, 0.1F), 1F);

    private final StructureCache structure = new StructureCache(ECMithriline.FURNACE_STRUCTURE);

    /** 已经投进去的 ESPE（原作 progress） */
    private double progress;
    /** 进度属于哪条配方；选中的配方换了就清零 */
    @Nullable
    private ResourceLocation progressRecipe;

    private Status status = Status.IDLE;
    /** 界面显示用：输入格的东西对应的配方（结构不完整、数量不够时也填，告诉玩家要什么） */
    @Nullable
    private MithrilineFurnaceRecipe displayRecipe;

    /** 有 ESPE 的源头位置（世界坐标，按 SOURCES 的顺序），每 {@link #SOURCE_REFRESH_INTERVAL} tick 重新找 */
    private final List<BlockPos> sources = new ArrayList<>();
    private int sourceRefresh;

    // ---- 配方缓存：输入格的物品（引用、NBT、是否为空）或配方表换了才重找 ----
    @Nullable
    private RecipeManager seenRecipes;
    private ItemStack seenStack = ItemStack.EMPTY;
    @Nullable
    private CompoundTag seenTag;
    private boolean seenEmpty = true;
    @Nullable
    private MithrilineFurnaceRecipe cachedRecipe;

    public MithrilineFurnaceBlockEntity(BlockPos pos, BlockState state) {
        // 原作熔炉没有 MRU：上限 0
        super(ECMithriline.MITHRILINE_FURNACE_BE.get(), pos, state, () -> 0, SLOTS, () -> ECConfig.get(Config.maxESPE), ESPE_TIER);
    }

    // ------------------------------------------------------------------
    // 槽位与对外规则
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用）：0 号放原料（什么都能放，原作如此），1 号输出槽什么都不能放 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_INPUT;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == SLOT_OUTPUT;
    }

    /**
     * 漏斗、管道只往输入格塞有配方的东西。
     * <p>
     * 输入格堵满以后漏斗每 tick 都会重试（塞不进去不进冷却，一次最多试 5 格），所以先做便宜的判断，不每次都查全表：
     * 输入格已有东西时，不是同一种（物品和 NBT 都一样）或者已经堆满，反正塞不进去，直接拒绝；是同一种就用
     * {@link #refreshRecipe()} 缓存的配方（同种物品对得上的配方一样）。只有输入格空着时才调 {@link #findRecipe} 查全表。
     */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        if (slot != SLOT_INPUT || level == null) {
            return false;
        }
        ItemStack current = inventory.getStackInSlot(SLOT_INPUT);
        if (!current.isEmpty()) {
            if (!ItemHandlerHelper.canItemStacksStack(current, stack)
                    || current.getCount() >= Math.min(current.getMaxStackSize(), inventory.getSlotLimit(SLOT_INPUT))) {
                return false;
            }
            return refreshRecipe() != null;
        }
        return findRecipe(level.getRecipeManager(), stack) != null;
    }

    /** 只进不出：别的机器、管道抽不走熔炉的 ESPE（原作谁都能抽） */
    @Override
    protected ESPEAccess espeAccess(@Nullable Direction side) {
        return ESPEAccess.INSERT;
    }

    public StructureCache structure() {
        return structure;
    }

    // ------------------------------------------------------------------
    // 配方（静态，GameTest 直接调）
    // ------------------------------------------------------------------

    /**
     * 输入格里这件东西对应的配方，没有返回 null。原作按注册顺序取第一条（getRecipeByInput 的 findAny）；
     * 1.20 的配方表没有稳定顺序，几条都对得上时按配方 ID 取第一条，至少每次结果一样（自带的配方没有重叠）。
     */
    @Nullable
    public static MithrilineFurnaceRecipe findRecipe(RecipeManager recipes, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return recipes.getAllRecipesFor(ECMithriline.MITHRILINE_FURNACE_TYPE.get()).stream()
                .filter(recipe -> recipe.getInput().test(stack))
                .min(Comparator.comparing(MithrilineFurnaceRecipe::getId))
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (level == null) {
            return;
        }
        boolean wasFormed = structure.formed();
        structure.check(level, worldPosition, Direction.NORTH);
        boolean formed = structure.formed();
        if (formed != wasFormed) {
            // 客户端靠这个冒粒子（getUpdateTag 里带着结构结果）
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            sourceRefresh = 0;
            if (formed) {
                awardNearby();
            }
        }
        MithrilineFurnaceRecipe recipe = refreshRecipe();
        displayRecipe = recipe;
        if (!formed) {
            // 原作：结构不对时什么都不做，进度和 ESPE 原样留着
            status = structure.lastKnown() == StructureResult.MISMATCH ? Status.INCOMPLETE : Status.UNLOADED;
            return;
        }
        pullESPE();
        process(recipe);
    }

    /** 从 ESPE 源往自己抽，满了就停（原作每 tick 从 13 个位置抽） */
    private void pullESPE() {
        if (--sourceRefresh <= 0) {
            sourceRefresh = SOURCE_REFRESH_INTERVAL;
            sources.clear();
            for (Vec3i offset : SOURCES) {
                BlockPos pos = worldPosition.offset(offset);
                if (ESPETransfer.loadedHandler(level, pos, ESPETransfer.sideFacing(pos, worldPosition)) != null) {
                    sources.add(pos);
                }
            }
        }
        ESPEStorage storage = getESPEStorage();
        for (BlockPos source : sources) {
            if (storage.getESPE() >= storage.getMaxESPE()) {
                break;
            }
            ESPETransfer.pull(level, worldPosition, storage, source);
        }
    }

    private void process(@Nullable MithrilineFurnaceRecipe recipe) {
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (input.isEmpty() || recipe == null) {
            setProgress(0D, null);
            status = input.isEmpty() ? Status.IDLE : Status.NO_RECIPE;
            return;
        }
        // 原作：数量不够时进度清零
        if (input.getCount() < recipe.getCount()) {
            setProgress(0D, null);
            status = Status.NEED_MORE;
            return;
        }
        if (!recipe.getId().equals(progressRecipe)) {
            // 修原作 bug：换了配方，旧配方的进度不能带过去
            setProgress(0D, recipe.getId());
        }
        if (!canOutput(recipe.getResult())) {
            status = Status.OUTPUT_FULL;
            return;
        }
        double required = recipe.getEspe();
        double got = required - progress > 0 ? getESPEStorage().extractESPE(required - progress, true) : 0D;
        double next = progress + got;
        if (next >= required - EPSILON) {
            craft(recipe);
            setProgress(0D, null);
            status = Status.WORKING;
        } else {
            setProgress(next, recipe.getId());
            status = got > 0 ? Status.WORKING : Status.NO_ESPE;
        }
    }

    /** 输入格的物品换了（或配方表重载了）才重找配方 */
    @Nullable
    private MithrilineFurnaceRecipe refreshRecipe() {
        RecipeManager recipes = level.getRecipeManager();
        ItemStack stack = inventory.getStackInSlot(SLOT_INPUT);
        // 引用变了是换了一堆；NBT 引用变了是物品被改了；原地 shrink 到 0 引用不变，要单独看是否为空
        if (recipes != seenRecipes || stack != seenStack || stack.getTag() != seenTag || stack.isEmpty() != seenEmpty) {
            seenRecipes = recipes;
            seenStack = stack;
            seenTag = stack.getTag();
            seenEmpty = stack.isEmpty();
            cachedRecipe = findRecipe(recipes, stack);
        }
        return cachedRecipe;
    }

    /** 输出槽空着且一份产物不超过堆叠上限，或者是同一种物品（NBT 一致）且放得下 */
    private boolean canOutput(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        int limit = Math.min(inventory.getSlotLimit(SLOT_OUTPUT), result.getMaxStackSize());
        ItemStack out = inventory.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            return result.getCount() <= limit;
        }
        return ItemStack.isSameItemSameTags(out, result) && out.getCount() + result.getCount() <= limit;
    }

    private void craft(MithrilineFurnaceRecipe recipe) {
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        ItemStack result = recipe.assemble(new SimpleContainer(input), level.registryAccess());
        ItemStack out = inventory.getStackInSlot(SLOT_OUTPUT);
        // 用 setStackInSlot 写回（不原地 grow），ItemStackHandler 才知道变了：存盘、客户端同步都靠它
        inventory.setStackInSlot(SLOT_OUTPUT, out.isEmpty() ? result : out.copyWithCount(out.getCount() + result.getCount()));
        inventory.extractItem(SLOT_INPUT, recipe.getCount(), false);
    }

    private void setProgress(double value, @Nullable ResourceLocation recipe) {
        if (value != progress || (recipe == null ? progressRecipe != null : !recipe.equals(progressRecipe))) {
            progress = value;
            progressRecipe = recipe;
            level.blockEntityChanged(worldPosition);
        }
    }

    // ------------------------------------------------------------------
    // 进度「绿焰」
    // ------------------------------------------------------------------

    private void awardNearby() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(worldPosition);
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(center) <= AWARD_RADIUS * AWARD_RADIUS) {
                award(player);
            }
        }
    }

    /** 发进度（进度文件不存在时什么都不做，整合包删了它也不报错） */
    public static void award(ServerPlayer player) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(ADVANCEMENT);
        if (advancement != null) {
            player.getAdvancements().award(advancement, ADVANCEMENT_CRITERION);
        }
    }

    // ------------------------------------------------------------------
    // 客户端粒子
    // ------------------------------------------------------------------

    /**
     * 结构完整时冒粒子（客户端的结构结果随方块实体更新包同步过来）：熔炉周围几个绿色光点（原作每 tick 10 个绿色火焰），
     * 每 5 tick 在每根米斯莱水晶柱的上下各冒一个，高度跟着水晶的浮动走（原作每 tick 两个）。
     */
    @Override
    protected void clientTick() {
        if (level == null || !structure.formed()) {
            return;
        }
        RandomSource random = level.random;
        for (int i = 0; i < 2; i++) {
            level.addParticle(GREEN_DUST,
                    worldPosition.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.8D,
                    worldPosition.getY() + 0.2D + random.nextDouble() * 0.6D,
                    worldPosition.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.8D,
                    0D, 0.01D, 0D);
        }
        if (innerRotation % 5 != 0) {
            return;
        }
        long movement = level.getGameTime() % 60;
        double bob = (movement > 30 ? 60 - movement : movement) / 30D;
        for (Vec3i offset : SOURCES) {
            BlockPos pos = worldPosition.offset(offset);
            if (level.getBlockEntity(pos) instanceof MithrilineCrystalPillarBlockEntity) {
                level.addParticle(GREEN_DUST, pos.getX() + 0.5D, pos.getY() + bob, pos.getZ() + 0.5D, 0D, 0D, 0D);
                level.addParticle(GREEN_DUST, pos.getX() + 0.5D, pos.getY() + 2D + bob, pos.getZ() + 0.5D, 0D, 0D, 0D);
            }
        }
    }

    // ------------------------------------------------------------------
    // 访问（界面数值、GameTest）
    // ------------------------------------------------------------------

    public double getProgress() {
        return progress;
    }

    @Nullable
    public ResourceLocation getProgressRecipe() {
        return progressRecipe;
    }

    /** 界面显示的进度：只配它所属的配方（结构拆了以后换了原料，存着的是旧配方的进度） */
    public double getDisplayProgress() {
        return displayRecipe != null && displayRecipe.getId().equals(progressRecipe) ? progress : 0D;
    }

    /** 界面显示的配方要多少 ESPE，没有配方时为 0 */
    public double getDisplayRequired() {
        return displayRecipe == null ? 0D : displayRecipe.getEspe();
    }

    /** 界面显示的配方要几个原料，没有配方时为 0 */
    public int getDisplayCount() {
        return displayRecipe == null ? 0 : displayRecipe.getCount();
    }

    @Nullable
    public MithrilineFurnaceRecipe getDisplayRecipe() {
        return displayRecipe;
    }

    public Status getStatus() {
        return status;
    }

    /** 最近一次重新找到的 ESPE 源有几处 */
    public int getSourceCount() {
        return sources.size();
    }

    /** 结构上次确定为不符时，第一个不符的格子（北向偏移）；没有时为 null */
    @Nullable
    public BlockPos getMismatchOffset() {
        StructurePattern.Mismatch mismatch = structure.mismatch();
        return mismatch == null ? null : mismatch.offset();
    }

    // ------------------------------------------------------------------
    // 存盘
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        structure.save(tag);
        if (progressRecipe != null && progress > 0) {
            tag.putDouble(TAG_PROGRESS, progress);
            tag.putString(TAG_RECIPE, progressRecipe.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        structure.load(tag);
        // 配方 ID 读回来先不解析：第一次 tick 选出的配方还是它，进度就接着算；换了（数据包删改）就清零
        ResourceLocation recipe = tag.contains(TAG_RECIPE, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(TAG_RECIPE)) : null;
        double value = tag.getDouble(TAG_PROGRESS);
        progress = recipe != null && Double.isFinite(value) && value > 0 ? value : 0D;
        progressRecipe = progress > 0 ? recipe : null;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        // 结构完整时打开界面也发进度：结构成形那一刻玩家可能不在附近
        if (structure.formed() && player instanceof ServerPlayer serverPlayer) {
            award(serverPlayer);
        }
        return new MithrilineFurnaceMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return status == Status.WORKING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.MITHRILINE;
    }
}
