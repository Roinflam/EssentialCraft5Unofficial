package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.advancement.StructureFormedTrigger;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.block.RedstonePulse;
import com.flameforge.essentialcraft5.client.render.SpellFx;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.menu.MagicalFurnaceMenu;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import com.flameforge.essentialcraft5.structure.StructureCache;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 魔力熔炉（第六批 6b M2，batch6b.md §4.2）。对应原作 TileMagicalFurnace。
 * <p>
 * 玩家视角：搭好结构（{@link ECVoidMachines#FURNACE_STRUCTURE}：下一层 5×5 虚空石、同层四角虚空石、上一层四角普通热力产能器），
 * 0 号格放一颗连着 MRU 源的绑定宝石；往机器顶上扔要熔的东西，一件 {@code ticks_required} 刻（默认 20 刻 = 1 秒），
 * 产物和魔力炉渣落在四角热力产能器的顶上；{@code double_output_chance}（30%）多出一份产物、{@code double_slag_chance}（10%）多出一个炉渣；不给经验。
 *
 * <h2>每刻（只在服务端）</h2>
 * <ol>
 *   <li>有红石：停机，进度留着（原作有红石时整段不跑）。</li>
 *   <li>结构没成形：进度清零。</li>
 *   <li>输入：照原作的框（从机器本格起往上两格高；下半格是机器本身、整块，掉落物进不去，实际吃的是顶面上那一格）里
 *       第一个有熔炼配方（{@link RecipeType#SMELTING}）的掉落物；跳过永远捡不起来的（商店插件的展示物品）
 *       和指定了别人拾取的（按 {@link ECProtection#actingOwner} 问，没有主人时这类一律不碰，policy §4.3 ④）。
 *       没有输入、或者换了一种东西：进度清零。</li>
 *   <li>MRU 够 {@code mru_usage}：扣掉、进度 +1（从 0 起时放火焰声）；不够：进度冻结，补上接着走（原作清零，花掉的白花）。</li>
 *   <li>到点：先看出口——这件东西可能落到的每个角（几率为 0 的角不看）1 格内已经有 ≥ {@value #OUTPUT_LIMIT} 个掉落物实体就暂停
 *       （状态「出口堆满」，进度停在满格，每 {@value #OUTPUT_RECHECK} 刻再看一次）；先看角、再掷骰，多出那份要落的角堆满了也会停，不会悄悄不出。
 *       都没满就输入减 1（{@code setItem} 换成缩过的副本，空了就 {@code discard}）、放岩浆冒泡声、
 *       产物用配方的 {@code assemble}（保留配方自带的数量和 NBT）：必出 1 份落在 (+2, +2, +2)，几率再出 1 份落在 (−2, +2, −2)；
 *       炉渣必出 1 个落在 (+2, +2, −2)，几率再出 1 个落在 (−2, +2, +2)。出东西前先并进落点附近同种、没满的掉落物，并不完的才生成新实体（速度为零）；
 *       并进去的那一堆寿命跟着延长（同原版合并取较小的年龄：并进去的东西至少能放满一次正常寿命）。</li>
 * </ol>
 * 输入在物品标签 {@link ECVoidMachines#NO_BONUS}（默认湿海绵、{@code #forge:dusts}）里的不多出产物（炉渣几率不变）：
 * 湿海绵 ↔ 海绵、整合包里「锭 → 粉 → 锭」这类一圈能凭空多 30% 的循环（batch6b.md §9 D2）。
 * 配方查询有上限（谁都能往别人的熔炉顶上扔东西，policy §1.2）：不带 NBT 的按物品缓存，各台共用一张表（数据包重载后作废）；
 * 带 NBT 的（1.20.1 里有耐久的物品一 new 就带 {@code Damage:0}，弓、盔甲、钓竿都算）不进共用表，按掉落物记「查过、没有」，物品对象没换就不再查；
 * 缓存没命中的真查询每台每刻最多 {@value #MAX_LOOKUPS_PER_TICK} 次，超出的候选这一刻跳过，起点每刻轮换，保证每个都轮得到。
 * 进度和正在熔的那种东西存盘。
 *
 * <h2>和原作不同</h2>
 * <ul>
 *   <li>原作吃任何人的掉落物（指定了拾取人的、永不可拾取的商店展示品）；这里这两类不碰（改玩法）。</li>
 *   <li>原作产物不合并、没有上限，每件最多生成 4 个掉落物实体；这里先合并、出口堆满就暂停（改玩法）。</li>
 *   <li>原作 MRU 半路不够进度清零；这里冻结（改玩法）。原作直接改掉落物数量（不同步给客户端）、两端都跑、每刻 33 格结构判定 × 2 端；
 *       这里 {@code setItem}、只在服务端、结构走 {@link StructureCache}。</li>
 *   <li>原作 30% 翻倍对所有熔炼都生效，形成上面说的复制循环；这里排除标签里的不翻倍。</li>
 * </ul>
 * 客户端只收「结构成形」一个标记（更新包不带物品栏），成形时偶尔冒法术粒子（{@link SpellFx}，原作 cSpellFX）。
 */
public class MagicalFurnaceBlockEntity extends MRUBlockEntity {

    /** 服务端配置 machines.magical_furnace（原作 tileentities.magicalfurnace，默认值照原作） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruUsage;
        public static ForgeConfigSpec.IntValue ticksRequired;
        public static ForgeConfigSpec.DoubleValue doubleOutputChance;
        public static ForgeConfigSpec.DoubleValue doubleSlagChance;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("magical_furnace");
            maxMRU = builder.comment("MRU capacity.", "MRU 容量")
                    .defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU * 10, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used per tick while smelting.", "熔炼时每刻用多少 MRU")
                    .defineInRange("mru_usage", 25, 0, Integer.MAX_VALUE);
            ticksRequired = builder.comment("Ticks to smelt one item.", "熔一件要多少刻")
                    .defineInRange("ticks_required", 20, 1, 72_000);
            doubleOutputChance = builder.comment("Chance of one extra result (inputs tagged essentialcraft5:magical_furnace_no_bonus never get it).",
                            "多出一份产物的几率（物品标签 essentialcraft5:magical_furnace_no_bonus 里的输入不会多出）")
                    .defineInRange("double_output_chance", 0.3D, 0D, 1D);
            doubleSlagChance = builder.comment("Chance of one extra magical slag.", "多出一个魔力炉渣的几率")
                    .defineInRange("double_slag_chance", 0.1D, 0D, 1D);
            builder.pop();
        }
    }

    /** 界面状态（按序号同步，只往后加） */
    public enum Status {
        /** 顶上没有能熔的东西 */
        IDLE(0xFFFFFF),
        INCOMPLETE(0xFF0000),
        /** 有红石信号：停机，进度留着 */
        POWERED(0xFFFF00),
        /** MRU 不够这一刻的用量：进度冻结 */
        NO_MRU(0xFFFF00),
        SMELTING(0x00FF00),
        /** 落点附近堆了太多掉落物：暂停 */
        OUTPUT_FULL(0xFF0000);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public MutableComponent label() {
            return Component.translatable("gui.essentialcraft5.magical_furnace." + name().toLowerCase(Locale.ROOT));
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
        }
    }

    public static final int SLOTS = 1;
    /** 落点 1 格内有这么多个掉落物实体就暂停 */
    public static final int OUTPUT_LIMIT = 8;
    /** 出口堆满时隔几刻再看一次 */
    public static final int OUTPUT_RECHECK = 20;
    /** 缓存没命中时，每台每刻最多真查几次配方 */
    public static final int MAX_LOOKUPS_PER_TICK = 4;
    /** 「查过、没有配方」的带 NBT 掉落物最多记几个（满了清空重来） */
    private static final int NO_RECIPE_LIMIT = 256;

    /** 四个落点相对机器的偏移（照原作：x/z ±2，y +2，就是四台热力产能器的顶上） */
    public static final BlockPos PRODUCT = new BlockPos(2, 2, 2);
    public static final BlockPos BONUS_PRODUCT = new BlockPos(-2, 2, -2);
    public static final BlockPos SLAG = new BlockPos(2, 2, -2);
    public static final BlockPos BONUS_SLAG = new BlockPos(-2, 2, 2);
    /** 掉落物生成的高度：原作 pos.y + 2.15（落在热力产能器顶面稍上） */
    private static final double DROP_HEIGHT = 0.15D;

    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_SMELTING = "smelting";
    private static final String TAG_FORMED = "formed";

    private final StructureCache structure = new StructureCache(ECVoidMachines.FURNACE_STRUCTURE);

    // ---- 服务端状态 ----
    private int progress;
    /** 正在熔的那种东西（数量 1）；换了种类进度清零。存盘 */
    private ItemStack smelting = ItemStack.EMPTY;
    private Status status = Status.IDLE;
    /** 出口堆满后下一次再看的游戏时间 */
    private long nextOutputCheck = Long.MIN_VALUE;

    // 不带 NBT 的物品 → 配方（Optional.empty 表示查过、没有）。各台共用、只在服务端主线程用，条目数不超过物品注册数；
    // 数据包重载（RecipeManager 换了一个）就整张作废
    private static final Map<Item, Optional<SmeltingRecipe>> RECIPE_CACHE = new HashMap<>();
    @Nullable
    private static RecipeManager cacheManager;

    // 带 NBT 的掉落物：UUID → 当时查的那个 ItemStack 对象（查过、没有配方）。合并、setItem 都会换对象，换了就重查
    private final Map<UUID, ItemStack> noRecipe = new HashMap<>();
    @Nullable
    private RecipeManager noRecipeManager;
    /** 找输入时从第几个候选看起（每刻 +1） */
    private int scanOffset;
    private long lookupTick = Long.MIN_VALUE;
    private int lookupsThisTick;
    /** 这一台真查配方的总次数（只给测试看） */
    private long lookups;

    // ---- 发给客户端的 ----
    private boolean formed;

    // ---- 只给 GameTest 的这一台自己的覆盖（null = 用配置） ----
    @Nullable
    private Double testDoubleOutput;
    @Nullable
    private Double testDoubleSlag;
    @Nullable
    private SmeltingRecipe testRecipe;

    public MagicalFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ECVoidMachines.MAGICAL_FURNACE_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    // ------------------------------------------------------------------
    // 配置与覆盖
    // ------------------------------------------------------------------

    public int mruUsage() {
        return ECConfig.get(Config.mruUsage);
    }

    public int ticksRequired() {
        return ECConfig.get(Config.ticksRequired);
    }

    public double doubleOutputChance() {
        return testDoubleOutput != null ? testDoubleOutput : ECConfig.get(Config.doubleOutputChance);
    }

    public double doubleSlagChance() {
        return testDoubleSlag != null ? testDoubleSlag : ECConfig.get(Config.doubleSlagChance);
    }

    /**
     * 只给 GameTest：这一台的两个几率（null = 用配置）。只改这一台，不碰配置文件（服务端配置自动存盘，文件监视器会冲掉测试里设的值）。
     */
    public void setTestChances(@Nullable Double doubleOutput, @Nullable Double doubleSlag) {
        testDoubleOutput = doubleOutput;
        testDoubleSlag = doubleSlag;
    }

    /**
     * 只给 GameTest：这一台先试这条配方，匹配就用它、不匹配再查配方表（测「数据包配方产物数量为 2 → 出 2」，不用真改数据包）。null 摘掉。
     */
    public void setTestRecipe(@Nullable SmeltingRecipe recipe) {
        testRecipe = recipe;
    }

    // ------------------------------------------------------------------
    // 槽位
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用）：唯一的格子只放绑定宝石（MRU 源） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return stack.getItem() instanceof BoundGemItem;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 渲染用不到物品栏 */
    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MagicalFurnaceMenu(id, inventory, this);
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        updateStructure(server);
        if (RedstonePulse.isPowered(server, worldPosition)) {
            status = Status.POWERED;
            return;
        }
        if (!structure.formed()) {
            resetProgress();
            status = Status.INCOMPLETE;
            return;
        }
        ItemEntity input = findInput(server);
        if (input == null) {
            resetProgress();
            status = Status.IDLE;
            return;
        }
        ItemStack stack = input.getItem();
        if (!smelting.isEmpty() && !ItemStack.isSameItemSameTags(smelting, stack)) {
            resetProgress();
        }
        if (smelting.isEmpty()) {
            smelting = stack.copyWithCount(1);
            markSaveDirty();
        }
        int ticks = ticksRequired();
        if (progress < ticks) {
            int usage = mruUsage();
            if (mru.getMRU() < usage) {
                status = Status.NO_MRU;
                return;
            }
            mru.extractMRU(usage, true);
            if (progress == 0) {
                server.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1F, 1F);
            }
            progress++;
            markSaveDirty();
            status = Status.SMELTING;
            if (progress < ticks) {
                return;
            }
        }
        finish(server, input);
    }

    /** 到点：看出口 → 输入减 1 → 出产物和炉渣 */
    private void finish(ServerLevel server, ItemEntity input) {
        long now = server.getGameTime();
        if (status == Status.OUTPUT_FULL && now < nextOutputCheck) {
            return;
        }
        // 这里不受每刻查询次数限制（一刻最多一次）：被限额挡掉会当成「没配方」把进度清零
        SmeltingRecipe recipe = recipeFor(server, server.getRecipeManager(), input.getItem(), input, false);
        if (recipe == null) {
            resetProgress();
            status = Status.IDLE;
            return;
        }
        ItemStack one = input.getItem().copyWithCount(1);
        ItemStack result = recipe.assemble(new SimpleContainer(one), server.registryAccess());
        boolean mayBonusProduct = !result.isEmpty() && !one.is(ECVoidMachines.NO_BONUS) && doubleOutputChance() > 0D;
        boolean mayBonusSlag = doubleSlagChance() > 0D;

        // 先看这件东西可能落到的每个角，再掷骰：只看掷中的角的话，多出那份要落的角堆满时暂停完重掷，没掷中就照常出，多出的那份就悄悄没了
        List<BlockPos> corners = new ArrayList<>(4);
        if (!result.isEmpty()) {
            corners.add(PRODUCT);
        }
        if (mayBonusProduct) {
            corners.add(BONUS_PRODUCT);
        }
        corners.add(SLAG);
        if (mayBonusSlag) {
            corners.add(BONUS_SLAG);
        }
        List<ItemEntity> nearby = server.getEntitiesOfClass(ItemEntity.class, outputArea(), entity -> entity.isAlive() && entity != input);
        for (BlockPos corner : corners) {
            if (countNear(nearby, dropPoint(corner)) >= OUTPUT_LIMIT) {
                status = Status.OUTPUT_FULL;
                nextOutputCheck = now + OUTPUT_RECHECK;
                return;
            }
        }
        RandomSource random = server.random;
        boolean bonusProduct = mayBonusProduct && random.nextDouble() < doubleOutputChance();
        boolean bonusSlag = mayBonusSlag && random.nextDouble() < doubleSlagChance();

        ItemStack remaining = input.getItem().copy();
        remaining.shrink(1);
        if (remaining.isEmpty()) {
            input.discard();
        } else {
            input.setItem(remaining);
        }
        server.playSound(null, worldPosition, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1F, 1F);
        if (!result.isEmpty()) {
            drop(server, nearby, PRODUCT, result.copy());
            if (bonusProduct) {
                drop(server, nearby, BONUS_PRODUCT, result.copy());
            }
        }
        drop(server, nearby, SLAG, new ItemStack(ECMaterials.MAGICAL_SLAG.get()));
        if (bonusSlag) {
            drop(server, nearby, BONUS_SLAG, new ItemStack(ECMaterials.MAGICAL_SLAG.get()));
        }
        progress = 0;
        markSaveDirty();
        status = Status.SMELTING;
    }

    /**
     * 第一个能熔、能拿的掉落物；没有返回 null。框照原作：从机器本格起往上两格高（原作 {@code AxisAlignedBB(x, y, z, x+1, y+2, z+1)}）。
     * 下半格是机器本身（整块），掉落物进不去，实际吃的就是顶面上那一格；再往上一格悬着的不碰。
     */
    @Nullable
    private ItemEntity findInput(ServerLevel server) {
        AABB box = new AABB(worldPosition).expandTowards(0D, 1D, 0D);
        List<ItemEntity> candidates = server.getEntitiesOfClass(ItemEntity.class, box, entity -> entity.isAlive() && !entity.getItem().isEmpty());
        if (candidates.isEmpty()) {
            return null;
        }
        // 正在熔的那件还在就接着熔它（同一格里有几种东西时不来回换、进度不清零）
        if (!smelting.isEmpty()) {
            for (ItemEntity entity : candidates) {
                if (ItemStack.isSameItemSameTags(smelting, entity.getItem()) && usable(entity)) {
                    return entity;
                }
            }
        }
        // 起点每刻轮换：真查询每刻有限额，排在后面的候选也轮得到
        RecipeManager manager = server.getRecipeManager();
        int size = candidates.size();
        int start = Math.floorMod(scanOffset++, size);
        for (int i = 0; i < size; i++) {
            ItemEntity entity = candidates.get((start + i) % size);
            if (usable(entity) && recipeFor(server, manager, entity.getItem(), entity, true) != null) {
                return entity;
            }
        }
        return null;
    }

    /** 能不能拿这个掉落物：不是永不可拾取的，没指定拾取人或者指定的就是机器替谁干活的那个人 */
    private boolean usable(ItemEntity entity) {
        if (ECItemEntities.neverPickUp(entity)) {
            return false;
        }
        UUID owner = ECProtection.actingOwner(this);
        return ECItemEntities.pickableBy(entity, owner);
    }

    /**
     * 这件东西的熔炼配方；没有、或者这一刻的查询限额用完了返回 null。
     * 不带 NBT 的查共用表；带 NBT 的不进共用表，{@code source} 给了时记住「这个掉落物、这个物品对象查过、没有」。
     *
     * @param budgeted 缓存没命中时要不要占这一刻的限额（找输入时占；到点确认配方时不占，一刻最多一次）
     */
    @Nullable
    private SmeltingRecipe recipeFor(ServerLevel server, RecipeManager manager, ItemStack stack, @Nullable ItemEntity source, boolean budgeted) {
        if (testRecipe != null && testRecipe.matches(new SimpleContainer(stack.copyWithCount(1)), server)) {
            return testRecipe;
        }
        if (manager != cacheManager) {
            RECIPE_CACHE.clear();
            cacheManager = manager;
        }
        if (manager != noRecipeManager) {
            noRecipe.clear();
            noRecipeManager = manager;
        }
        if (!stack.hasTag()) {
            Optional<SmeltingRecipe> cached = RECIPE_CACHE.get(stack.getItem());
            if (cached == null) {
                if (budgeted && !spendLookup(server)) {
                    return null;
                }
                cached = lookup(server, manager, stack);
                RECIPE_CACHE.put(stack.getItem(), cached);
            }
            return cached.orElse(null);
        }
        if (source != null && noRecipe.get(source.getUUID()) == stack) {
            return null;
        }
        if (budgeted && !spendLookup(server)) {
            return null;
        }
        Optional<SmeltingRecipe> found = lookup(server, manager, stack);
        if (found.isEmpty() && source != null) {
            if (noRecipe.size() >= NO_RECIPE_LIMIT) {
                noRecipe.clear();
            }
            noRecipe.put(source.getUUID(), stack);
        }
        return found.orElse(null);
    }

    /** 占这一刻的一次查询限额；用完了返回 false */
    private boolean spendLookup(ServerLevel server) {
        long now = server.getGameTime();
        if (now != lookupTick) {
            lookupTick = now;
            lookupsThisTick = 0;
        }
        if (lookupsThisTick >= MAX_LOOKUPS_PER_TICK) {
            return false;
        }
        lookupsThisTick++;
        return true;
    }

    /** 真去配方表里找（逐条 matches）；计数给测试看 */
    private Optional<SmeltingRecipe> lookup(ServerLevel server, RecipeManager manager, ItemStack stack) {
        lookups++;
        return manager.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack.copyWithCount(1)), server);
    }

    /** 四个落点外扩 1 格的范围（一次查询，按落点分开数） */
    private AABB outputArea() {
        Vec3 a = dropPoint(BONUS_PRODUCT);
        Vec3 b = dropPoint(PRODUCT);
        return new AABB(a.x, a.y, a.z, b.x, b.y, b.z).inflate(1D);
    }

    /** 落点（掉落物生成的位置）：那一格水平正中、底面 + 0.15 */
    private Vec3 dropPoint(BlockPos offset) {
        BlockPos pos = worldPosition.offset(offset);
        return new Vec3(pos.getX() + 0.5D, pos.getY() + DROP_HEIGHT, pos.getZ() + 0.5D);
    }

    private static boolean near(ItemEntity entity, Vec3 point) {
        return Math.abs(entity.getX() - point.x) <= 1D && Math.abs(entity.getY() - point.y) <= 1D && Math.abs(entity.getZ() - point.z) <= 1D;
    }

    private static int countNear(List<ItemEntity> entities, Vec3 point) {
        int count = 0;
        for (ItemEntity entity : entities) {
            if (entity.isAlive() && near(entity, point)) {
                count++;
            }
        }
        return count;
    }

    /** 落到 offset 那个角上：先并进附近同种、没满的掉落物，剩下的生成新实体（速度为零） */
    private void drop(ServerLevel server, List<ItemEntity> nearby, BlockPos offset, ItemStack stack) {
        Vec3 point = dropPoint(offset);
        int lifespan = stack.getEntityLifespan(server);
        for (ItemEntity entity : nearby) {
            if (stack.isEmpty()) {
                return;
            }
            ItemStack existing = entity.getItem();
            if (!entity.isAlive() || !near(entity, point) || !ItemStack.isSameItemSameTags(existing, stack)
                    || ECItemEntities.neverPickUp(entity) || ECItemEntities.target(entity) != null) {
                continue;
            }
            int room = existing.getMaxStackSize() - existing.getCount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, stack.getCount());
            entity.setItem(existing.copyWithCount(existing.getCount() + moved));
            stack.shrink(moved);
            // 原版合并取两者里较小的年龄；这里只改那一堆，就把它的寿命延长到「并进去的东西能放满一次正常寿命」，
            // 不然一直往同一堆里并，最早那份到 6000 刻时会连着后来并进去的一起消失。年龄 -32768 是永不消失，不动
            if (entity.getAge() != Short.MIN_VALUE) {
                entity.lifespan = Math.max(entity.lifespan, entity.getAge() + lifespan);
            }
        }
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(stack.getMaxStackSize());
            ItemEntity entity = new ItemEntity(server, point.x, point.y, point.z, part, 0D, 0D, 0D);
            server.addFreshEntity(entity);
            nearby.add(entity);
        }
    }

    /** 进度变了只标记区块要存盘，不像 setChanged 那样每刻通知比较器（同 MRUBlockEntity 对 MRU 的处理） */
    private void markSaveDirty() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    private void resetProgress() {
        if (progress != 0 || !smelting.isEmpty()) {
            progress = 0;
            smelting = ItemStack.EMPTY;
            markSaveDirty();
        }
    }

    private void updateStructure(ServerLevel server) {
        boolean was = structure.formed();
        structure.check(server, worldPosition, Direction.NORTH);
        boolean now = structure.formed();
        if (!was && now) {
            StructureFormedTrigger.triggerNearby(server, worldPosition, ECVoidMachines.FURNACE_STRUCTURE_ID, 0);
        }
        if (formed != now) {
            formed = now;
            markClientDirty();
        }
    }

    // ------------------------------------------------------------------
    // 客户端
    // ------------------------------------------------------------------

    /** 成形时每 4 刻冒一团法术粒子（原作每刻一团，从机器那层往上飘两格）；离镜头 32 格以外不放 */
    @Override
    protected void clientTick() {
        if (level == null || !formed || innerRotation % 4 != 0) {
            return;
        }
        double cx = worldPosition.getX() + 0.5D;
        double cy = worldPosition.getY();
        double cz = worldPosition.getZ() + 0.5D;
        if (!SpellFx.nearCamera(cx, cy, cz, MagicalTeleporterBlockEntity.PARTICLE_RANGE)) {
            return;
        }
        RandomSource random = level.random;
        SpellFx.spawn(level, cx + (random.nextFloat() - random.nextFloat()) * 3F, cy, cz + (random.nextFloat() - random.nextFloat()) * 3F, 0D, 2D, 0D);
    }

    // ------------------------------------------------------------------
    // 访问
    // ------------------------------------------------------------------

    public StructureCache structure() {
        return structure;
    }

    /** 服务端：结构是否成形（最近一次确定的结论）；客户端：更新包里的成形标记 */
    public boolean isFormed() {
        return formed;
    }

    public int progress() {
        return progress;
    }

    public Status status() {
        return status;
    }

    @Nullable
    public BlockPos mismatchOffset() {
        return structure.mismatch() == null ? null : structure.mismatch().offset();
    }

    /** 只给测试：这一台真查配方的总次数（缓存命中、「查过、没有」跳过的都不算） */
    public long recipeLookupsForTest() {
        return lookups;
    }

    /** 只给测试：用指定的配方表查一次（不占限额、不记「查过、没有」），用来测换了配方表以后共用缓存作废 */
    @Nullable
    public SmeltingRecipe recipeForTest(ServerLevel server, RecipeManager manager, ItemStack stack) {
        return recipeFor(server, manager, stack, null, false);
    }

    /** 某个落点的世界坐标（测试和展示厅用）：那一格的方块坐标 */
    public BlockPos dropCell(BlockPos offset) {
        return worldPosition.offset(offset);
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        structure.save(tag);
        if (progress > 0) {
            tag.putInt(TAG_PROGRESS, progress);
        }
        if (!smelting.isEmpty()) {
            tag.put(TAG_SMELTING, smelting.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        structure.load(tag);
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        smelting = tag.contains(TAG_SMELTING, Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound(TAG_SMELTING)) : ItemStack.EMPTY;
        // 只在更新包里（客户端）；存档里没有
        if (tag.contains(TAG_FORMED, Tag.TAG_BYTE)) {
            formed = tag.getBoolean(TAG_FORMED);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        // 进度、正在熔的东西只有界面要（走菜单同步），附近的人用不到
        tag.remove(TAG_PROGRESS);
        tag.remove(TAG_SMELTING);
        tag.putBoolean(TAG_FORMED, formed);
        return tag;
    }

    @Override
    protected boolean ambientWorking() {
        return status == Status.SMELTING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.MAGICAL_FURNACE;
    }
}
