package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.AmbienceParticles;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.block.MagmaticFurnaceBlock;
import com.flameforge.essentialcraft5.blockentity.OreProcessing.Status;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.MagmaticAlloyItem;
import com.flameforge.essentialcraft5.menu.MagmaticFurnaceMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.recipe.OreSmeltingRecipe;
import com.flameforge.essentialcraft5.registry.ECMagmatic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 岩浆熔炉（强化 / 魔力 / 淡青 / 虚空四档共用）。对应原作 TileFurnaceMagic（furnaceMagic 的 meta 0/4/8/12 → 四个注册名）。
 * <p>
 * 只有一个输入格：放矿石（或粗矿）时每 tick 耗 {@code mru_usage}（25）MRU，做满 {@code ticks_required} ÷ 档位 tick
 * （强化 400、魔力 200、淡青 133、虚空 100，整数除法同原作）出 掉落数 × 1 个岩浆合金；把合金取出来再放回输入格，同样的时间和耗能炼成 2 个成品。
 * 不要岩浆、不出炉渣。收到红石信号时停机，进度保留。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li><b>输出放不下就不做</b>：原作只看「+1 放得下」，实际加 掉落数 个再截到 64（青金石矿一次 16 个）。</li>
 *   <li>粗矿倍率减半（D8）：粗铁、粗金、粗铜在这里两个出一个合金，和原版熔炉持平；粗矿请送冶炼炉。</li>
 *   <li>进度存盘时连同所属的配方、工序一起存，换了东西清零（原作换矿种进度照算）。</li>
 *   <li>认矿石看「在不在配方里」（原作只认第一个矿辞）；界面进度条的总长由服务端算（原作读客户端配置）。</li>
 *   <li>冒火粒子在客户端放、只在工作时冒（原作在服务端调，从来没显示过）。</li>
 *   <li>档位存在方块本身（四个注册名），不会像原作那样被 setBlockRotation 改成强化级。</li>
 * </ul>
 */
public class MagmaticFurnaceBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.furnacemagic */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruUsage;
        public static ForgeConfigSpec.IntValue ticksRequired;
        public static ForgeConfigSpec.BooleanValue generateCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("magmatic_furnace");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used per working tick (melting ore and smelting alloy alike).",
                            "工作时每 tick 耗多少 MRU（熔矿、炼合金一样）")
                    .defineInRange("mru_usage", 25, 0, Integer.MAX_VALUE);
            ticksRequired = builder.comment("Ticks per operation for the fortified furnace; the magic, pale and void ones divide it by 2, 3 and 4.",
                            "强化岩浆熔炉每道工序要多少 tick；魔力、淡青、虚空级分别除以 2、3、4（整数除法）")
                    .defineInRange("ticks_required", 400, 1, 1_000_000);
            generateCorruption = builder.comment("Whether the furnace leaks MRU into nearby MRUCUs while working.",
                            "工作时是否向附近的 MRU 聚集体泄漏 MRU")
                    .define("generate_corruption", false);
            maxCorruptionGen = builder.comment("Max MRU leaked per working tick (a random amount from 0 to this).",
                            "每个工作 tick 最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 3, 0, 10000);
            builder.pop();
        }
    }

    /** 当前工序 */
    public enum Mode {
        ORE,
        ALLOY;

        private static final Mode[] VALUES = values();

        public MutableComponent label() {
            return Component.translatable("gui.essentialcraft5.magmatic_furnace.mode." + name().toLowerCase(Locale.ROOT));
        }

        public static Mode byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : ORE;
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_INPUT = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOTS = 3;

    public static final String TAG_PROGRESS = "progress";
    private static final String TAG_RECIPE = "recipe";
    private static final String TAG_MODE = "mode";
    private static final String TAG_WORKING = "working";

    private int progress;
    /** 进度属于哪条配方、哪道工序；换了就清零 */
    @Nullable
    private ResourceLocation progressRecipe;
    private Mode progressMode = Mode.ORE;
    private Mode mode = Mode.ORE;
    private Status status = Status.IDLE;
    private final OreSmeltingRecipe.Cache oreCache = new OreSmeltingRecipe.Cache();
    private final MagmaticAlloyItem.Cache alloyCache = new MagmaticAlloyItem.Cache();
    private final OreProcessing.Results results = new OreProcessing.Results();

    private int idleTicks = MagmaticSmelteryBlockEntity.WORKING_LINGER;
    private boolean working;

    public MagmaticFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ECMagmatic.MAGMATIC_FURNACE_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    // ------------------------------------------------------------------
    // 槽位规则（两端共用）
    // ------------------------------------------------------------------

    /** 0 号绑定宝石；1 号什么都收（同原作，有没有配方由机器判断）；2 号输出槽不能放 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_INPUT || slot == SLOT_GEM && defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == SLOT_OUTPUT;
    }

    /** 漏斗只能往输入格塞有配方的矿石和认得的岩浆合金 */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        if (slot == SLOT_INPUT) {
            if (level == null) {
                return false;
            }
            if (stack.is(ECMagmatic.MAGMATIC_ALLOY.get())) {
                return MagmaticAlloyItem.recipe(level, stack) != null;
            }
            return OreSmeltingRecipe.find(level.getRecipeManager(), stack) != null;
        }
        return super.canAutomationInsert(slot, stack);
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    public MagmaticFurnaceBlock.Tier tier() {
        return getBlockState().getBlock() instanceof MagmaticFurnaceBlock furnace ? furnace.tier() : MagmaticFurnaceBlock.Tier.FORTIFIED;
    }

    /** 每道工序的 tick 数：配置 ÷ 档位（原作 smeltingTime / (meta / 4 + 1)，整数除法），至少 1 */
    public static int requiredTicks(MagmaticFurnaceBlock.Tier tier) {
        return Math.max(1, ECConfig.get(Config.ticksRequired) / tier.speed());
    }

    public int requiredTicks() {
        return requiredTicks(tier());
    }

    public static int cost() {
        return ECConfig.get(Config.mruUsage);
    }

    @Override
    protected void serverTick() {
        if (level == null) {
            return;
        }
        boolean worked = false;
        if (neighborPowered()) {
            status = Status.POWERED;
        } else {
            worked = work();
        }
        updateWorking(worked);
    }

    private boolean work() {
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (input.isEmpty()) {
            setProgress(0, null, mode);
            status = Status.IDLE;
            return false;
        }
        OreSmeltingRecipe recipe;
        ItemStack result;
        int consumed;
        if (input.is(ECMagmatic.MAGMATIC_ALLOY.get())) {
            mode = Mode.ALLOY;
            recipe = alloyCache.get(level, input);
            result = recipe == null ? ItemStack.EMPTY : results.products(recipe);
            if (result.isEmpty()) {
                recipe = null;
            }
            consumed = 1;
        } else {
            mode = Mode.ORE;
            recipe = oreCache.get(level, input);
            OreSmeltingRecipe.Yield yield = recipe == null ? null : recipe.yieldFor(input, OreSmeltingRecipe.FURNACE_MULTIPLIER);
            if (yield == null) {
                recipe = null;
                result = ItemStack.EMPTY;
                consumed = 1;
            } else {
                result = results.alloys(recipe, yield.alloys());
                consumed = yield.consumed();
            }
        }
        if (recipe == null) {
            setProgress(0, null, mode);
            status = Status.NO_RECIPE;
            return false;
        }
        if (!recipe.getId().equals(progressRecipe) || progressMode != mode) {
            setProgress(0, recipe.getId(), mode);
        }
        if (input.getCount() < consumed) {
            status = Status.NEED_MORE;
            return false;
        }
        if (!OreProcessing.fits(inventory, SLOT_OUTPUT, result)) {
            status = Status.OUTPUT_FULL;
            return false;
        }
        int cost = cost();
        if (mru.getMRU() < cost) {
            status = Status.NO_MRU;
            return false;
        }
        status = Status.WORKING;
        mru.extractMRU(cost, true);
        if (ECConfig.get(Config.generateCorruption)) {
            ECCorruption.randomIncreaseAt(level, worldPosition, level.random, ECConfig.get(Config.maxCorruptionGen));
        }
        if (progress + 1 >= requiredTicks()) {
            inventory.extractItem(SLOT_INPUT, consumed, false);
            OreProcessing.put(inventory, SLOT_OUTPUT, result);
            setProgress(0, null, mode);
        } else {
            setProgress(progress + 1, recipe.getId(), mode);
        }
        return true;
    }

    private void setProgress(int value, @Nullable ResourceLocation recipe, Mode forMode) {
        if (value != progress || forMode != progressMode || (recipe == null ? progressRecipe != null : !recipe.equals(progressRecipe))) {
            progress = value;
            progressRecipe = recipe;
            progressMode = forMode;
            level.blockEntityChanged(worldPosition);
        }
    }

    private void updateWorking(boolean worked) {
        idleTicks = worked ? 0 : Math.min(idleTicks + 1, MagmaticSmelteryBlockEntity.WORKING_LINGER);
        boolean now = idleTicks < MagmaticSmelteryBlockEntity.WORKING_LINGER;
        if (now != working) {
            working = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 工作时炉口冒火和烟（照原版熔炉的位置） */
    @Override
    protected void clientTick() {
        if (level == null || !working) {
            return;
        }
        RandomSource random = level.random;
        if (random.nextInt(4) != 0) {
            return;
        }
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(MagmaticFurnaceBlock.FACING) ? state.getValue(MagmaticFurnaceBlock.FACING) : Direction.NORTH;
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + random.nextDouble() * 6.0 / 16.0;
        double z = worldPosition.getZ() + 0.5;
        double along = random.nextDouble() * 0.6 - 0.3;
        double dx = facing.getAxis() == Direction.Axis.X ? facing.getStepX() * 0.52 : along;
        double dz = facing.getAxis() == Direction.Axis.Z ? facing.getStepZ() * 0.52 : along;
        AmbienceParticles.add(level, ParticleTypes.SMOKE, x + dx, y, z + dz, 0, 0, 0);
        AmbienceParticles.add(level, ParticleTypes.FLAME, x + dx, y, z + dz, 0, 0, 0);
    }

    // ------------------------------------------------------------------
    // 访问（界面数值、GameTest）
    // ------------------------------------------------------------------

    public int getProgress() {
        return progress;
    }

    public Status getStatus() {
        return status;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isWorking() {
        return working;
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (progressRecipe != null && progress > 0) {
            tag.putInt(TAG_PROGRESS, progress);
            tag.putString(TAG_RECIPE, progressRecipe.toString());
            tag.putString(TAG_MODE, progressMode.name().toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        progressRecipe = tag.contains(TAG_RECIPE, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(TAG_RECIPE)) : null;
        progress = progressRecipe != null ? Math.max(0, tag.getInt(TAG_PROGRESS)) : 0;
        progressMode = "alloy".equals(tag.getString(TAG_MODE)) ? Mode.ALLOY : Mode.ORE;
        mode = progressMode;
        if (tag.contains(TAG_WORKING, Tag.TAG_BYTE)) {
            working = tag.getBoolean(TAG_WORKING);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean(TAG_WORKING, working);
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MagmaticFurnaceMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return working;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.MAGMATIC_FURNACE;
    }
}
