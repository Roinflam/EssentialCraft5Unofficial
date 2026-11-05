package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MagicalCultivatorMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.recipe.CultivatorRecipe;
import com.flameforge.essentialcraft5.registry.ECCultivator;
import com.flameforge.essentialcraft5.registry.ECMachineSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 魔力栽培器（EC5 新机器，第十二批 batch12.md §4.2）。
 * <p>
 * 格子：0 绑定宝石；1 种子格（样本，一叠，株数 = min(数量, 配方株数上限)，<b>不消耗</b>）；2 水格（只收水桶，不消耗）；
 * 3 催长格（只收骨粉）；4~12 输出格。
 * <p>
 * 一轮：
 * <ul>
 *   <li>开始：配方对得上（显式配方，或通用路线）、要水的有水、按最坏情况放得下（每种产物取上限 × 株数，模拟塞进输出格）、
 *       付得起第一刻、没被红石暂停。开始时锁定株数、本轮时长和本轮总价；配方能用骨粉、催长格有骨粉 → 吃掉 1 份，本轮时长 × {@code bonemeal_time_factor}。</li>
 *   <li>进行中：本轮总价 = 每株 MRU × 株数 × {@code mru_multiplier}，边做边扣（第 p 刻付 ⌊总价·(p+1)/T⌋ − ⌊总价·p/T⌋，走满正好是总价，同地脉凝聚器）；
 *       付不起这一刻不前进（NO_MRU），没水了也停在原处（NO_WATER）。</li>
 *   <li>种子格换了物品、或数量少于锁定株数 → 本轮作废（不退 MRU、不出货）；数量变多不影响本轮。</li>
 *   <li>到点：按最坏情况再看一次放不放得下（中途有人往输出格塞了东西），放不下就停在最后一刻（OUTPUT_FULL），腾出地方再出货，不攒待放、不往地上吐。
 *       放得下就每株各掷一遍产物塞进输出格。</li>
 * </ul>
 * 水：要么水格里有水桶，要么四个水平邻格或下方是水源（流体状态是水源水，含含水方块）；邻格所在区块没加载当没有。
 * 每 {@value #WATER_RECHECK} 刻和邻居变化时重查一次，平时用缓存。世界方块只读这 5 格的流体状态，不改任何方块、不碰实体。
 * <p>
 * 通用路线（定稿 D5）：没有显式配方、方块是 {@link CropBlock} 子类且种得在普通耕地上（{@link CultivatorRecipe#genericCropOf}）、
 * 不在 {@link ECCultivator#BLACKLIST} 里、{@code generic_crops} 开着。产物来自成熟状态的方块掉落（每株减 1 个样本、最多
 * {@code generic_max_items_per_plant} 件）。掷表抛异常的物品本次开服记成「种不了」（{@link #UNSUPPORTED_GENERIC}），日志警告一次。
 * <p>
 * 自动化（漏斗、管道）：种子格只收能种、且和格里相同的东西；水格只收水桶；催长格只收骨粉；只能从输出格往外抽。任何一面都一样。
 * <p>
 * 发给附近客户端的更新包只带渲染档和样本种类，物品栏、平衡值不发（物品栏走菜单同步）。
 */
public class MagicalCultivatorBlockEntity extends MRUBlockEntity {

    /** 配置小节 machines.magical_cultivator（batch12.md §4.2.4） */
    public static final class Config {
        public static ForgeConfigSpec.BooleanValue enabled;
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue timeMultiplier;
        public static ForgeConfigSpec.DoubleValue mruMultiplier;
        public static ForgeConfigSpec.DoubleValue bonemealTimeFactor;
        public static ForgeConfigSpec.BooleanValue genericCrops;
        public static ForgeConfigSpec.IntValue genericTicks;
        public static ForgeConfigSpec.IntValue genericMruPerPlant;
        public static ForgeConfigSpec.IntValue genericMaxPlants;
        public static ForgeConfigSpec.IntValue genericMaxItemsPerPlant;
        public static ForgeConfigSpec.BooleanValue generatesCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("magical_cultivator");
            enabled = builder.comment("Whether magical cultivators work at all.", "魔力栽培器是否工作")
                    .define("enabled", true);
            maxMRU = builder.comment("MRU capacity.", "MRU 容量")
                    .defineInRange("max_mru", 10_000, 1, 100_000_000);
            timeMultiplier = builder.comment("Every recipe's round time is multiplied by this.", "所有配方的一轮时长乘以这个数")
                    .defineInRange("time_multiplier", 1.0D, 0.1D, 10.0D);
            mruMultiplier = builder.comment("Every recipe's MRU cost is multiplied by this.", "所有配方的 MRU 花费乘以这个数")
                    .defineInRange("mru_multiplier", 1.0D, 0.0D, 100.0D);
            bonemealTimeFactor = builder.comment("A round that used one bone meal takes this fraction of the normal time.",
                            "用了一份骨粉的那一轮，时长乘以这个数")
                    .defineInRange("bonemeal_time_factor", 0.5D, 0.1D, 1.0D);
            genericCrops = builder.comment("Crops from other mods (CropBlock subclasses that grow on plain farmland) without a recipe are grown from their own mature drops.",
                            "没有配方的别的模组作物（种得在普通耕地上的 CropBlock 子类）按它自己成熟时的掉落来种")
                    .define("generic_crops", true);
            genericTicks = builder.comment("Round time of the generic crop path, in ticks (20 ticks = 1 second).", "通用作物路线一轮多少 tick（20 tick = 1 秒）")
                    .defineInRange("generic_ticks", 2400, 20, 72_000);
            genericMruPerPlant = builder.comment("MRU per plant per round on the generic crop path.", "通用作物路线每株每轮多少 MRU")
                    .defineInRange("generic_mru_per_plant", 800, 0, 1_000_000);
            genericMaxPlants = builder.comment("Max plants on the generic crop path.", "通用作物路线最多几株")
                    .defineInRange("generic_max_plants", 16, 1, 64);
            genericMaxItemsPerPlant = builder.comment("Max items one plant yields on the generic crop path (extra drops are discarded).",
                            "通用作物路线每株最多出几件（多的丢掉）")
                    .defineInRange("generic_max_items_per_plant", 8, 1, 64);
            generatesCorruption = builder.comment("Whether the cultivator leaks MRU into nearby MRUCUs while working.", "工作时是否向周围泄漏 MRU")
                    .define("generate_corruption", true);
            maxCorruptionGen = builder.comment("Max MRU leaked per 20 working ticks (a random amount from 0 to this).",
                            "每工作 20 tick 最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 1, 0, 10_000);
            builder.pop();
        }
    }

    /** 界面上显示的状态（服务端算好，随菜单同步） */
    public enum Status {
        WORKING(0x00FF00),
        NO_SEED(0xFFFFFF),
        /** 种子格里的东西种不了（没有配方、通用路线不收或被关掉、在黑名单里） */
        UNSUPPORTED(0xFF5555),
        NO_WATER(0xFFFF00),
        NO_MRU(0xFFFF00),
        OUTPUT_FULL(0xFFFF00),
        /** 有红石信号 */
        PAUSED(0xFF0000),
        /** enabled = false */
        DISABLED(0xFF0000);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public MutableComponent label() {
            return Component.translatable("gui.essentialcraft5.magical_cultivator.status." + key());
        }

        public MutableComponent hint() {
            return Component.translatable("gui.essentialcraft5.magical_cultivator.status." + key() + ".hint");
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NO_SEED;
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_SEED = 1;
    public static final int SLOT_WATER = 2;
    public static final int SLOT_BONEMEAL = 3;
    public static final int FIRST_OUTPUT = 4;
    public static final int OUTPUTS = CultivatorRecipe.OUTPUT_SLOTS;
    public static final int SLOTS = FIRST_OUTPUT + OUTPUTS;

    /** 水源缓存最多用这么久（刻），到时重查；邻居变化时下一刻就重查 */
    public static final int WATER_RECHECK = 100;
    /** 每工作这么多刻泄漏一次 MRU（照其它机器） */
    private static final int LEAK_INTERVAL = 20;
    /** 渲染分几档（作物最大年龄 7 → 8 档正好一档一龄）；进度跨档时才给客户端发更新包，不每刻发 */
    public static final int RENDER_STAGES = 8;
    /** 通用路线到点掷出来的东西真塞不下时（掉落的堆叠上限比按 64 估的小），隔这么多刻再掷一次，不每刻掷 16 遍掉落表 */
    private static final int GENERIC_RETRY = 20;

    /** 查水源的 5 格：四个水平邻格和下方（上方是日光玻璃顶，不算） */
    private static final Direction[] WATER_SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};

    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_ROUND_TICKS = "round_ticks";
    private static final String TAG_PLANTS = "plants";
    private static final String TAG_ROUND_COST = "round_cost";
    private static final String TAG_LOCKED = "locked_item";
    private static final String TAG_STAGE = "render_stage";
    private static final String TAG_RENDER_SEED = "render_seed";

    /** 绿色尘粒，从日光玻璃顶上慢慢往上飘 */
    private static final DustParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.45F, 0.95F, 0.4F), 0.7F);

    /** 干活时的氛围：细碎的魔法微光声（比魔力工作台轻）+ 往上飘的绿色尘粒（batch12.md §4.2.3：写在本类，不改 MachineAmbiences） */
    public static final MachineAmbience.Profile AMBIENCE = MachineAmbience.Profile.builder()
            .loop(ECMachineSounds.ARCANE_SHIMMER, 0.2F)
            .spark(2.5F, 1.0D, (level, x, y, z, random) -> level.addParticle(GREEN_DUST,
                    x + (random.nextDouble() - 0.5D) * 0.6D, y, z + (random.nextDouble() - 0.5D) * 0.6D, 0.0D, 0.02D, 0.0D))
            .build();

    /** 本次开服掷掉落表出过错的通用路线物品：以后都当「种不了」，免得每轮白扣 MRU、日志刷屏 */
    private static final Set<Item> UNSUPPORTED_GENERIC = ConcurrentHashMap.newKeySet();

    private final CultivatorRecipe.Cache recipeCache = new CultivatorRecipe.Cache();

    // ---- 本轮（lockedItem 为 null = 没在一轮里） ----
    @Nullable
    private Item lockedItem;
    private int lockedPlants;
    private int roundTicks;
    private long roundCost;
    private int progress;

    private Status status = Status.NO_SEED;
    /** 界面那一行「16 株 · 每轮 2 分钟」：一轮中是锁定的值，空闲时是照现在的种子算出来的 */
    private int displayPlants;
    private int displayTicks;

    // ---- 水源缓存 ----
    private boolean waterDirty = true;
    private boolean waterCached;
    private long waterCheckedAt = Long.MIN_VALUE;

    private int leakCounter;
    /** 服务端：上次发给客户端的渲染档；客户端：收到的渲染档（-1 = 不在一轮里） */
    private int renderStage = -1;
    /** 服务端：上次发给客户端的样本种类；客户端：收到的样本种类（渲染画它，数量和 NBT 不发） */
    private Item renderSeed = Items.AIR;
    /** 通用路线塞不下时，到这一刻（游戏时间）再掷 */
    private long genericRetryAt = Long.MIN_VALUE;

    // ---- 通用路线的资格缓存（按物品种类） ----
    @Nullable
    private Item eligibleItem;
    @Nullable
    private CropBlock eligibleCrop;
    @Nullable
    private CultivatorRecipe genericRecipe;
    @Nullable
    private Item genericRecipeItem;

    // ---- GameTest 钩子 ----
    private boolean testGenericOnly;
    @Nullable
    private Predicate<ItemStack> testBlacklist;

    public MagicalCultivatorBlockEntity(BlockPos pos, BlockState state) {
        super(ECCultivator.MAGICAL_CULTIVATOR_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    // ------------------------------------------------------------------
    // 槽位
    // ------------------------------------------------------------------

    /**
     * 玩家和菜单的槽位规则（两端共用）：0 绑定宝石，1 种子格什么都能放（种不了的界面上显示「种不了」），2 只放水桶，3 只放骨粉，输出格不能放。
     * 种子格对漏斗更严（{@link #canAutomationInsert}），客户端菜单不知道配方表，所以玩家这边不在这里判断。
     */
    public static boolean slotRule(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_GEM -> defaultSlotRule(slot, stack);
            case SLOT_SEED -> true;
            case SLOT_WATER -> stack.is(Items.WATER_BUCKET);
            case SLOT_BONEMEAL -> stack.is(Items.BONE_MEAL);
            default -> false;
        };
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 漏斗、管道：种子格只收能种、且和格里相同的东西（batch12.md §4.2.3、§7）；绑定宝石格不收 */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        if (slot == SLOT_SEED) {
            ItemStack present = inventory.getStackInSlot(SLOT_SEED);
            if (!present.isEmpty() && !ItemStack.isSameItemSameTags(present, stack)) {
                return false;
            }
            return level != null && plantable(stack);
        }
        return (slot == SLOT_WATER || slot == SLOT_BONEMEAL) && slotRule(slot, stack);
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot >= FIRST_OUTPUT && slot < SLOTS;
    }

    /**
     * 物品栏不发给附近的客户端（漏斗每抽一下输出格都会触发一次）：渲染只要样本是哪种东西，更新包里单带一个物品 id，
     * 样本种类变了才 {@link #markClientDirty()}。物品栏走菜单同步给开着界面的人。
     */
    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    // ------------------------------------------------------------------
    // 配方
    // ------------------------------------------------------------------

    /** 这样东西能不能种（不查缓存，给漏斗用；漏斗一会儿一种东西，用机器自己的缓存会来回刷） */
    public boolean plantable(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return false;
        }
        if (!testGenericOnly && CultivatorRecipe.find(level.getRecipeManager(), stack) != null) {
            return true;
        }
        return genericAllowed(stack) && CultivatorRecipe.genericCropOf(stack) != null;
    }

    /** 种子格里这样东西的配方：显式配方优先，其次通用路线；种不了返回 null */
    @Nullable
    public CultivatorRecipe recipeFor(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return null;
        }
        if (!testGenericOnly) {
            CultivatorRecipe explicit = recipeCache.get(level, stack);
            if (explicit != null) {
                return explicit;
            }
        }
        CropBlock crop = genericCrop(stack);
        return crop == null ? null : genericRecipe(crop, stack.getItem());
    }

    /** 通用路线的配方：同一种物品、配置没变时复用上次造的（不每刻新建）；配置里的时长、价格、株数改了马上换新的 */
    private CultivatorRecipe genericRecipe(CropBlock crop, Item item) {
        int ticks = ECConfig.get(Config.genericTicks);
        int mruPerPlant = ECConfig.get(Config.genericMruPerPlant);
        int maxPlants = ECConfig.get(Config.genericMaxPlants);
        int maxItems = ECConfig.get(Config.genericMaxItemsPerPlant);
        CultivatorRecipe cached = genericRecipe;
        if (cached == null || genericRecipeItem != item || cached.genericCrop() != crop || cached.ticks() != ticks
                || cached.mruPerPlant() != mruPerPlant || cached.maxPlants() != maxPlants || cached.maxItemsPerPlant() != maxItems) {
            cached = CultivatorRecipe.generic(crop, item, ticks, mruPerPlant, maxPlants, maxItems);
            genericRecipe = cached;
            genericRecipeItem = item;
        }
        return cached;
    }

    /** 通用路线：总开关、黑名单、本次开服出过错的都挡掉，再看作物本身（结果按物品种类缓存） */
    @Nullable
    private CropBlock genericCrop(ItemStack stack) {
        if (!genericAllowed(stack)) {
            return null;
        }
        Item item = stack.getItem();
        if (item != eligibleItem) {
            eligibleItem = item;
            eligibleCrop = CultivatorRecipe.genericCropOf(stack);
        }
        return eligibleCrop;
    }

    private boolean genericAllowed(ItemStack stack) {
        if (!ECConfig.get(Config.genericCrops) || UNSUPPORTED_GENERIC.contains(stack.getItem())) {
            return false;
        }
        return testBlacklist != null ? !testBlacklist.test(stack) : !stack.is(ECCultivator.BLACKLIST);
    }

    // ------------------------------------------------------------------
    // 公式（静态，GameTest 直接调）
    // ------------------------------------------------------------------

    /** 从进度 p 走到 p+1 这一步要付的量：⌊total·(p+1)/T⌋ − ⌊total·p/T⌋，走满正好 total（同 LeyCondenserBlockEntity.stepCost） */
    public static long stepCost(long total, int ticks, int p) {
        if (total <= 0 || ticks <= 0) {
            return 0L;
        }
        return total * (p + 1) / ticks - total * p / ticks;
    }

    /** 一轮总价：每株 MRU × 株数 × mru_multiplier，四舍五入 */
    public static long roundCost(int mruPerPlant, int plants, double multiplier) {
        return Math.max(0L, Math.round((double) mruPerPlant * plants * multiplier));
    }

    /** 一轮时长：配方时长 × time_multiplier，用了骨粉再 × bonemeal_time_factor，至少 1 刻 */
    public static int roundTicks(int recipeTicks, double timeMultiplier, boolean bonemeal, double bonemealFactor) {
        double ticks = Math.max(1L, Math.round(recipeTicks * timeMultiplier));
        if (bonemeal) {
            ticks = Math.round(ticks * bonemealFactor);
        }
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (long) ticks));
    }

    /** 四个水平邻格或下方有没有水源（含含水方块）；邻格所在区块没加载当没有，不会因此加载区块 */
    public static boolean waterSourceAround(Level level, BlockPos pos) {
        for (Direction side : WATER_SIDES) {
            BlockPos at = pos.relative(side);
            if (!level.hasChunkAt(at)) {
                continue;
            }
            FluidState fluid = level.getFluidState(at);
            if (fluid.isSource() && fluid.is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    /** 邻居变了（方块的 neighborChanged 调）：下一刻重查水源 */
    public void markWaterDirty() {
        waterDirty = true;
    }

    private boolean hasWater() {
        if (inventory.getStackInSlot(SLOT_WATER).is(Items.WATER_BUCKET)) {
            return true;
        }
        long now = level.getGameTime();
        if (waterDirty || now < waterCheckedAt || now - waterCheckedAt >= WATER_RECHECK) {
            waterCached = waterSourceAround(level, worldPosition);
            waterDirty = false;
            waterCheckedAt = now;
        }
        return waterCached;
    }

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        tickRound(server);
        updateRenderStage();
        updateRenderSeed();
    }

    private void tickRound(ServerLevel server) {
        ItemStack seed = inventory.getStackInSlot(SLOT_SEED);
        // 种子换了物品或少于锁定株数：本轮作废（不退 MRU、不出货）
        if (lockedItem != null && (seed.isEmpty() || seed.getItem() != lockedItem || seed.getCount() < lockedPlants)) {
            resetRound();
        }
        CultivatorRecipe recipe = recipeFor(seed);
        updateDisplay(seed, recipe);
        if (neighborPowered()) {
            status = Status.PAUSED;
            return;
        }
        if (!ECConfig.get(Config.enabled)) {
            status = Status.DISABLED;
            return;
        }
        if (seed.isEmpty()) {
            status = Status.NO_SEED;
            return;
        }
        if (recipe == null) {
            // 数据包删了配方、黑名单加了它：手上这一轮也作废
            resetRound();
            status = Status.UNSUPPORTED;
            return;
        }
        if (recipe.needsWater() && !hasWater()) {
            status = Status.NO_WATER;
            return;
        }
        if (lockedItem == null && !startRound(seed, recipe)) {
            return;
        }
        if (progress < roundTicks) {
            long step = stepCost(roundCost, roundTicks, progress);
            if (step > mru.getMRU()) {
                status = Status.NO_MRU;
                return;
            }
            if (step > 0) {
                mru.extractMRU((int) step, true);
            }
            progress++;
            status = Status.WORKING;
            // 耗能为 0 时 MRU 不变，基类不会标记存盘，这里自己标
            level.blockEntityChanged(worldPosition);
            if (++leakCounter >= LEAK_INTERVAL) {
                leakCounter = 0;
                if (ECConfig.get(Config.generatesCorruption)) {
                    ECCorruption.randomIncreaseAt(server, worldPosition, server.random, ECConfig.get(Config.maxCorruptionGen));
                }
            }
        }
        if (progress >= roundTicks) {
            deliver(server, recipe);
        }
    }

    /** 开一轮；条件不满足时设好状态、返回 false */
    private boolean startRound(ItemStack seed, CultivatorRecipe recipe) {
        int plants = Math.min(seed.getCount(), recipe.maxPlants());
        if (plants < 1) {
            status = Status.NO_SEED;
            return false;
        }
        if (!worstCaseFits(recipe, plants)) {
            status = Status.OUTPUT_FULL;
            return false;
        }
        boolean bonemeal = recipe.bonemeal() && inventory.getStackInSlot(SLOT_BONEMEAL).is(Items.BONE_MEAL);
        int ticks = roundTicks(recipe.ticks(), ECConfig.get(Config.timeMultiplier), bonemeal, ECConfig.get(Config.bonemealTimeFactor));
        long cost = roundCost(recipe.mruPerPlant(), plants, ECConfig.get(Config.mruMultiplier));
        // 付不起第一刻就先不开（不然骨粉白吃、株数被锁死在没电的时候）
        if (stepCost(cost, ticks, 0) > mru.getMRU()) {
            status = Status.NO_MRU;
            return false;
        }
        if (bonemeal) {
            inventory.extractItem(SLOT_BONEMEAL, 1, false);
        }
        lockedItem = seed.getItem();
        lockedPlants = plants;
        roundTicks = ticks;
        roundCost = cost;
        progress = 0;
        leakCounter = 0;
        displayPlants = plants;
        displayTicks = ticks;
        level.blockEntityChanged(worldPosition);
        return true;
    }

    /** 到点出货：按最坏情况放得下才掷（结果一定塞得进），放不下停在最后一刻 */
    private void deliver(ServerLevel server, CultivatorRecipe recipe) {
        if (!worstCaseFits(recipe, lockedPlants)) {
            status = Status.OUTPUT_FULL;
            return;
        }
        List<ItemStack> products;
        if (recipe.isGeneric()) {
            long now = server.getGameTime();
            if (now < genericRetryAt && genericRetryAt - now <= GENERIC_RETRY) {
                status = Status.OUTPUT_FULL;
                return;
            }
            products = new ArrayList<>();
            Vec3 origin = Vec3.atCenterOf(worldPosition);
            try {
                for (int plant = 0; plant < lockedPlants; plant++) {
                    products.addAll(recipe.rollGenericPlant(server, origin, lockedItem));
                }
            } catch (RuntimeException e) {
                Item item = lockedItem;
                if (UNSUPPORTED_GENERIC.add(item)) {
                    EssentialCraft5.LOGGER.warn("[魔力栽培器] {} 的成熟掉落表掷不出来，这次开服不再种它：{}",
                            ForgeRegistries.ITEMS.getKey(item), e.toString());
                }
                eligibleItem = null;
                resetRound();
                status = Status.UNSUPPORTED;
                return;
            }
            products = merge(products);
            // 掉落的堆叠上限比按 64 估的小时（少见），可能真放不下：照样停在最后一刻，隔 GENERIC_RETRY 刻再掷
            if (!insertOutputs(products, true)) {
                genericRetryAt = now + GENERIC_RETRY;
                status = Status.OUTPUT_FULL;
                return;
            }
        } else {
            products = recipe.rollExplicit(server.random, lockedPlants);
        }
        insertOutputs(products, false);
        resetRound();
        status = Status.WORKING;
    }

    private void resetRound() {
        if (lockedItem != null || progress != 0) {
            lockedItem = null;
            lockedPlants = 0;
            roundTicks = 0;
            roundCost = 0L;
            progress = 0;
            if (level != null) {
                level.blockEntityChanged(worldPosition);
            }
        }
    }

    private void updateDisplay(ItemStack seed, @Nullable CultivatorRecipe recipe) {
        if (lockedItem != null) {
            displayPlants = lockedPlants;
            displayTicks = roundTicks;
        } else if (recipe != null) {
            displayPlants = Math.min(seed.getCount(), recipe.maxPlants());
            displayTicks = roundTicks(recipe.ticks(), ECConfig.get(Config.timeMultiplier), false, 1.0D);
        } else {
            displayPlants = 0;
            displayTicks = 0;
        }
    }

    /** 渲染档变了才请求发更新包（{@link #RENDER_STAGES} 档，一轮最多发 8 次） */
    private void updateRenderStage() {
        int stage = lockedItem == null || roundTicks <= 0 ? -1
                : Math.min(RENDER_STAGES - 1, (int) ((long) progress * RENDER_STAGES / roundTicks));
        if (stage != renderStage) {
            renderStage = stage;
            markClientDirty();
        }
    }

    /** 样本换了种类（含放进、拿空）才请求发更新包；只是数量变了不发 */
    private void updateRenderSeed() {
        Item seed = inventory.getStackInSlot(SLOT_SEED).getItem();
        if (seed != renderSeed) {
            renderSeed = seed;
            markClientDirty();
        }
    }

    // ------------------------------------------------------------------
    // 输出格
    // ------------------------------------------------------------------

    /** 按最坏情况放不放得下：显式配方模拟塞每种产物的上限 × 株数；通用路线看空输出格够不够（每株按上限件数、每格按 64 件算） */
    private boolean worstCaseFits(CultivatorRecipe recipe, int plants) {
        if (recipe.isGeneric()) {
            int empty = 0;
            for (int slot = FIRST_OUTPUT; slot < SLOTS; slot++) {
                if (inventory.getStackInSlot(slot).isEmpty()) {
                    empty++;
                }
            }
            return empty >= recipe.genericSlotsNeeded(plants);
        }
        return insertOutputs(recipe.worstCase(plants), true);
    }

    /**
     * 把 stacks 塞进输出格（先并进同种的堆叠，再放空格）。simulate 时只在副本上算、不改物品栏。返回是不是全部塞得下；
     * 不模拟时调用方已经确认过塞得下。输出格的槽位规则不让任何东西放进来，所以这里直接改格子，不走 insertItem。
     */
    private boolean insertOutputs(List<ItemStack> stacks, boolean simulate) {
        ItemStack[] slots = new ItemStack[OUTPUTS];
        for (int i = 0; i < OUTPUTS; i++) {
            slots[i] = inventory.getStackInSlot(FIRST_OUTPUT + i).copy();
        }
        boolean fits = true;
        for (ItemStack original : stacks) {
            ItemStack rest = original.copy();
            for (int i = 0; i < OUTPUTS && !rest.isEmpty(); i++) {
                ItemStack there = slots[i];
                if (!there.isEmpty() && ItemStack.isSameItemSameTags(there, rest)) {
                    int move = Math.min(rest.getCount(), there.getMaxStackSize() - there.getCount());
                    if (move > 0) {
                        there.grow(move);
                        rest.shrink(move);
                    }
                }
            }
            for (int i = 0; i < OUTPUTS && !rest.isEmpty(); i++) {
                if (slots[i].isEmpty()) {
                    int move = Math.min(rest.getCount(), rest.getMaxStackSize());
                    slots[i] = rest.split(move);
                }
            }
            if (!rest.isEmpty()) {
                fits = false;
            }
        }
        if (!simulate) {
            for (int i = 0; i < OUTPUTS; i++) {
                if (!ItemStack.matches(slots[i], inventory.getStackInSlot(FIRST_OUTPUT + i))) {
                    inventory.setStackInSlot(FIRST_OUTPUT + i, slots[i]);
                }
            }
        }
        return fits;
    }

    /** 同种同 NBT 的合起来（通用路线每株各掷一遍，合起来少占格子） */
    private static List<ItemStack> merge(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack target = null;
            for (ItemStack existing : out) {
                if (ItemStack.isSameItemSameTags(existing, stack)) {
                    target = existing;
                    break;
                }
            }
            if (target == null) {
                out.add(stack.copy());
            } else {
                target.grow(stack.getCount());
            }
        }
        List<ItemStack> split = new ArrayList<>();
        for (ItemStack stack : out) {
            CultivatorRecipe.split(split, stack.copyWithCount(1), stack.getCount());
        }
        return split;
    }

    // ------------------------------------------------------------------
    // 访问（界面数值、渲染、GameTest）
    // ------------------------------------------------------------------

    public Status getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    /** 本轮时长（不在一轮里为 0） */
    public int getRoundTicks() {
        return roundTicks;
    }

    public int getLockedPlants() {
        return lockedPlants;
    }

    public long getRoundCost() {
        return roundCost;
    }

    public boolean inRound() {
        return lockedItem != null;
    }

    public int getDisplayPlants() {
        return displayPlants;
    }

    public int getDisplayTicks() {
        return displayTicks;
    }

    /** 客户端渲染：进度档 0 ~ {@link #RENDER_STAGES} − 1，不在一轮里为 −1 */
    public int getRenderStage() {
        return renderStage;
    }

    /** 客户端渲染：样本是哪种东西（一个，没有 NBT）；空着为 {@link ItemStack#EMPTY} */
    public ItemStack getRenderSeed() {
        return new ItemStack(renderSeed);
    }

    /** 只在 GameTest 用：把本轮进度直接设到 p（不在一轮里时不动） */
    public void setTestProgress(int p) {
        if (lockedItem != null) {
            progress = Math.max(0, Math.min(roundTicks, p));
        }
    }

    /**
     * 只在 GameTest 用：当作没有任何显式配方（像数据包把它们删了），只走通用路线，并用 blacklist 代替物品标签判断黑名单。
     * 测试不改真数据包、真标签（policy：GameTest 不改共享状态）。
     */
    public void setTestGenericOnly(Predicate<ItemStack> blacklist) {
        testGenericOnly = true;
        testBlacklist = blacklist;
        eligibleItem = null;
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ResourceLocation id = lockedItem == null ? null : ForgeRegistries.ITEMS.getKey(lockedItem);
        if (id != null) {
            tag.putString(TAG_LOCKED, id.toString());
            tag.putInt(TAG_PLANTS, lockedPlants);
            tag.putInt(TAG_ROUND_TICKS, roundTicks);
            tag.putLong(TAG_ROUND_COST, roundCost);
            tag.putInt(TAG_PROGRESS, progress);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        lockedItem = null;
        lockedPlants = 0;
        roundTicks = 0;
        roundCost = 0L;
        progress = 0;
        if (tag.contains(TAG_LOCKED)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_LOCKED));
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            int ticks = tag.getInt(TAG_ROUND_TICKS);
            int plants = tag.getInt(TAG_PLANTS);
            // 物品没了（模组卸掉）或数值坏了：当这一轮没开过
            if (item != null && item != Items.AIR && ticks > 0 && plants > 0) {
                lockedItem = item;
                lockedPlants = plants;
                roundTicks = ticks;
                roundCost = Math.max(0L, tag.getLong(TAG_ROUND_COST));
                progress = Math.max(0, Math.min(ticks, tag.getInt(TAG_PROGRESS)));
            }
        }
        // 只有客户端收到的更新包里有这个键（存档里没有，服务端每刻重算）
        if (tag.contains(TAG_STAGE)) {
            renderStage = tag.getInt(TAG_STAGE);
        }
        if (tag.contains(TAG_RENDER_SEED, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_RENDER_SEED));
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            renderSeed = item == null ? Items.AIR : item;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        // 更新包不带本轮的锁定值（客户端用不到），只带渲染档和样本种类（直接取格子里的，区块刚发过去时也对）
        tag.remove(TAG_LOCKED);
        tag.remove(TAG_PLANTS);
        tag.remove(TAG_ROUND_TICKS);
        tag.remove(TAG_ROUND_COST);
        tag.remove(TAG_PROGRESS);
        tag.putInt(TAG_STAGE, renderStage);
        ItemStack seed = inventory.getStackInSlot(SLOT_SEED);
        ResourceLocation seedId = seed.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(seed.getItem());
        tag.putString(TAG_RENDER_SEED, seedId == null ? "" : seedId.toString());
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MagicalCultivatorMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return status == Status.WORKING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return AMBIENCE;
    }
}
