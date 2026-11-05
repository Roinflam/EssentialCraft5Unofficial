package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.ElementalCrystalItem;
import com.flameforge.essentialcraft5.menu.CrystalFormerMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECCrystal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * 水晶成型器。对应原作 TileCrystalFormer。
 * <p>
 * 8 格：0 绑定宝石，1 产物，2~4 水桶，5~6 玻璃（forge:glass，含染色玻璃，原作矿辞 blockGlass），7 钻石。
 * 产物格空着、材料齐全、没有红石信号时，每 tick 扣 MRUUsage（100）MRU、进度 +1，进度到 TicksRequired（1 000）时
 * 出一颗大小 1、四元素全 0 的元素水晶：三个水桶变成空桶留在原格，两块玻璃和钻石各扣 1。一颗共 10 万 MRU。
 * 工作的每一 tick 向周围泄漏 0~MaxCorruptionGen（2）MRU（原作 genCorruption，见 {@link ECCorruption}）。
 * 材料不齐时进度保留（原作如此），补齐后接着做。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li><b>进度存盘</b>：原作 progressLevel 不存盘，区块卸载最多白扔 10 万 MRU。</li>
 *   <li>漏斗、管道只能从 2~4 号格抽<b>空桶</b>（原作 getOutputSlots 包括这三格，漏斗会把没用过的水桶抽走）。</li>
 *   <li>漏斗、管道补玻璃时 5、6 号格轮流补（见 {@link #canAutomationInsert}）。原作先把 5 号格叠满 64 块才轮到 6 号格，
 *       玻璃不到 65 块就一直缺料停机。</li>
 *   <li>原作三个水桶格放的是同一个 ItemStack 对象（改一个三个一起变），这里各放一个。</li>
 *   <li>界面加了进度条（原作没有进度显示），悬停看状态、进度和耗能。</li>
 *   <li>工作粒子：原作客户端按「材料齐全且有 MRU」每 tick 冒 10 个青色红石粉粒子；这里客户端不知道 MRU 够不够，
 *       改成服务端最近 20 tick 里真的做过才冒（状态翻转时发一次更新包），数量减到每 tick 4 个。</li>
 * </ul>
 */
public class CrystalFormerBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.crystalformer */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruUsage;
        public static ForgeConfigSpec.IntValue ticksRequired;
        public static ForgeConfigSpec.BooleanValue generateCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("crystal_former");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used per working tick.", "工作时每 tick 耗能")
                    .defineInRange("mru_usage", 100, 0, Integer.MAX_VALUE);
            ticksRequired = builder.comment("Working ticks needed to form one crystal.", "做一颗水晶要工作多少 tick")
                    .defineInRange("ticks_required", 1000, 1, Integer.MAX_VALUE);
            generateCorruption = builder.comment("Whether the former leaks MRU into nearby MRUCUs while working.",
                            "工作时是否向附近的 MRU 聚集体泄漏 MRU")
                    .define("generate_corruption", true);
            maxCorruptionGen = builder.comment("Max MRU leaked per working tick (a random amount from 0 to this).",
                            "每个工作 tick 最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 2, 0, 10000);
            builder.pop();
        }
    }

    /** 界面上显示的机器状态（服务端算好，随菜单同步） */
    public enum Status {
        /** 材料不齐 */
        IDLE(0xFFFFFF),
        WORKING(0x00FF00),
        NO_MRU(0xFFFF00),
        /** 产物格里还有东西（原作要求产物格空着） */
        OUTPUT_FULL(0xFF0000),
        /** 有红石信号，停机 */
        POWERED(0xFF0000);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public MutableComponent label() {
            return Component.translatable("gui.essentialcraft5.crystal_former." + name().toLowerCase(Locale.ROOT));
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_BUCKET_FIRST = 2;
    public static final int SLOT_BUCKET_LAST = 4;
    public static final int SLOT_GLASS_FIRST = 5;
    public static final int SLOT_GLASS_LAST = 6;
    public static final int SLOT_DIAMOND = 7;
    public static final int SLOTS = 8;

    public static final String TAG_PROGRESS = "progress";
    /** 只在更新包里：客户端据此冒工作粒子 */
    private static final String TAG_WORKING = "working";
    /** 最近多少 tick 内做过就算「在工作」（粒子用，免得 MRU 供不上时做一 tick 停几 tick 来回发包） */
    private static final int WORKING_WINDOW = 20;
    private static final DustParticleOptions PARTICLE = new DustParticleOptions(new Vector3f(0F, 1F, 1F), 1F);

    /** 已工作的 tick 数（原作 progressLevel） */
    private int progress;
    private Status status = Status.IDLE;
    private long lastWorked = Long.MIN_VALUE / 2;
    /** 服务端：上次发给客户端的工作状态；客户端：收到的工作状态 */
    private boolean working;

    public CrystalFormerBlockEntity(BlockPos pos, BlockState state) {
        super(ECCrystal.CRYSTAL_FORMER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    // ------------------------------------------------------------------
    // 槽位规则
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用，原作 isItemValidForSlot）：宝石、水桶、玻璃、钻石各归各位，产物格什么都不能放 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_GEM -> defaultSlotRule(slot, stack);
            case 2, 3, 4 -> stack.is(Items.WATER_BUCKET);
            case 5, 6 -> stack.is(Tags.Items.GLASS);
            case SLOT_DIAMOND -> stack.is(Items.DIAMOND);
            default -> false;
        };
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /**
     * 漏斗、管道往两格玻璃里补料时均摊（玩家手放不走这里）：另一格也收得下这件玻璃、而且数量更少（相同时槽号小的优先）时，
     * 这一格不收。漏斗按槽号从小到大一件件试，结果就是 5、6 号格轮流补。
     * 原作两格都照槽位规则收，漏斗会先把 5 号格叠满 64 块才往 6 号格放，塞进去的玻璃不到 65 块时机器一直停在「缺少材料」。
     * 写法和按位置匹配的机器（{@link com.flameforge.essentialcraft5.recipe.PositionalInsertRule}）的均摊一致。
     * 管道一次推一整叠时这一叠全进数量少的那格（只能决定收不收，分不了量），下一次再推就进另一格。
     */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        if (!stack.isEmpty() && (slot == SLOT_GLASS_FIRST || slot == SLOT_GLASS_LAST)) {
            int other = SLOT_GLASS_FIRST + SLOT_GLASS_LAST - slot;
            int mine = inventory.getStackInSlot(slot).getCount();
            int theirs = inventory.getStackInSlot(other).getCount();
            // 模拟插入已经包含槽位规则、同种物品（含 NBT）、没叠满这几项
            if ((theirs < mine || (theirs == mine && other < slot))
                    && inventory.insertItem(other, stack.copyWithCount(1), true).isEmpty()) {
                return false;
            }
        }
        return super.canAutomationInsert(slot, stack);
    }

    /** 产物格随便抽；水桶格只许抽空桶（原作连没用过的水桶也能被漏斗抽走） */
    @Override
    protected boolean isOutputSlot(int slot) {
        if (slot == SLOT_OUTPUT) {
            return true;
        }
        return slot >= SLOT_BUCKET_FIRST && slot <= SLOT_BUCKET_LAST && inventory.getStackInSlot(slot).is(Items.BUCKET);
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (level == null) {
            return;
        }
        // 原作 getRedstonePowerFromNeighbors(pos) == 0 才工作；停机时进度保留
        if (neighborPowered()) {
            status = Status.POWERED;
        } else if (!hasIngredients()) {
            status = Status.IDLE;
        } else if (!inventory.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
            status = Status.OUTPUT_FULL;
        } else {
            int usage = ECConfig.get(Config.mruUsage);
            if (mru.getMRU() < usage) {
                status = Status.NO_MRU;
            } else {
                status = Status.WORKING;
                work(usage);
            }
        }
        boolean nowWorking = level.getGameTime() - lastWorked < WORKING_WINDOW;
        if (nowWorking != working) {
            working = nowWorking;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void work(int usage) {
        mru.extractMRU(usage, true);
        lastWorked = level.getGameTime();
        if (ECConfig.get(Config.generateCorruption)) {
            ECCorruption.randomIncreaseAt(level, worldPosition, level.random, ECConfig.get(Config.maxCorruptionGen));
        }
        progress++;
        if (progress >= ECConfig.get(Config.ticksRequired)) {
            progress = 0;
            createCrystal();
        }
        // 耗能配成 0 时 MRU 不变、基类不会标记存盘，这里自己标
        level.blockEntityChanged(worldPosition);
    }

    /** 原作 canWork 的材料部分：三桶水、两块玻璃、一颗钻石 */
    public boolean hasIngredients() {
        for (int slot = SLOT_BUCKET_FIRST; slot <= SLOT_DIAMOND; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || !slotRule(slot, stack)) {
                return false;
            }
        }
        return true;
    }

    /** 原作 createItem：水桶换空桶（各是各的堆叠），玻璃、钻石各扣 1，产物是大小 1、四元素 0 的水晶 */
    private void createCrystal() {
        for (int slot = SLOT_BUCKET_FIRST; slot <= SLOT_BUCKET_LAST; slot++) {
            inventory.setStackInSlot(slot, new ItemStack(Items.BUCKET));
        }
        for (int slot = SLOT_GLASS_FIRST; slot <= SLOT_DIAMOND; slot++) {
            inventory.extractItem(slot, 1, false);
        }
        inventory.setStackInSlot(SLOT_OUTPUT, ElementalCrystalItem.create(1D, 0D, 0D, 0D, 0D));
    }

    @Override
    protected void clientTick() {
        if (!working || level == null) {
            return;
        }
        // 原作 spawnParticles：从底到顶一竖列青色红石粉
        RandomSource random = level.random;
        for (int i = 0; i < 4; i++) {
            level.addParticle(PARTICLE, worldPosition.getX() + 0.1D + random.nextDouble() / 1.1D,
                    worldPosition.getY() + random.nextDouble() * 0.9D,
                    worldPosition.getZ() + 0.1D + random.nextDouble() / 1.1D, 0D, 0D, 0D);
        }
    }

    // ------------------------------------------------------------------
    // 访问（界面数值、GameTest）
    // ------------------------------------------------------------------

    public int getProgress() {
        return progress;
    }

    public static int required() {
        return ECConfig.get(Config.ticksRequired);
    }

    public static int costPerTick() {
        return ECConfig.get(Config.mruUsage);
    }

    public Status getStatus() {
        return status;
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
        if (progress > 0) {
            tag.putInt(TAG_PROGRESS, progress);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        // 存档里没有这个键（只在更新包里带），读档后按「没在工作」算
        working = tag.getBoolean(TAG_WORKING);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean(TAG_WORKING, working);
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CrystalFormerMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return status == Status.WORKING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.CRYSTAL_FORMER;
    }
}
