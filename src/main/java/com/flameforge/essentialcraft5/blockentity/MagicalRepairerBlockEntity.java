package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MagicalRepairerMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECDevices;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 魔力修复器。对应原作 TileMagicalRepairer。
 * <p>
 * 0 号槽绑定宝石，1 号槽放要修的东西。没有红石信号时，每 tick 修 1 点耐久、扣 {@code mru_usage} MRU（默认 500，D6），
 * 修的那一 tick 向周围泄漏 0~{@code max_corruption_gen} MRU。钻石镐从 0 修满 1561 点要 78 万 MRU。
 * 能修的条件：可损坏、可修理（Forge isRepairable：没设 setNoRepair）、已经有损耗（{@link #canRepair}）。
 * 不可修理、但在 {@code essentialcraft5:mru_repairable} 里的也修（第十三批 13b：能同调的四套盔甲，不可修理是为了挡住工作台修理配方、
 * 砂轮合并吞掉同调数据；这里原地修、不动 NBT，见 {@link ECTags#MRU_REPAIRABLE}）。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>漏斗、管道只能从 1 号槽取走已经修好或根本修不了的东西（原作 1 号槽既进又出，漏斗会把修到一半的工具抽走）；
 *       往里送只收要修的东西（修不了的送进来马上又被抽走，来回倒腾）。玩家手放不受限。</li>
 *   <li>界面状态由服务端算（{@link #statusCode}），不在客户端判断物品。原作 MRU 不到 500 但不为 0 时显示「修理中」，其实停着；
 *       这里 MRU 不够一次就显示「MRU 不足」，另加「红石信号：已暂停」。</li>
 *   <li>修的每一 tick 只标记存盘，不把整份方块实体发给附近玩家（原作每秒比对 NBT 后整包同步）；界面里的耐久条走菜单的槽位同步，
 *       客户端只在「开始修 / 停下」时收到一次状态，用来冒粒子。</li>
 * </ul>
 */
public class MagicalRepairerBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.magicalrepairer */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruUsage;
        public static ForgeConfigSpec.BooleanValue generatesCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("magical_repairer");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used per point of durability repaired (one point per tick).",
                            "每修 1 点耐久消耗的 MRU（每 tick 修 1 点）")
                    .defineInRange("mru_usage", 500, 70, 1_000_000);
            generatesCorruption = builder.comment("Whether the repairer leaks MRU into nearby MRUCUs while working.",
                            "工作时是否向周围泄漏 MRU")
                    .define("generate_corruption", true);
            maxCorruptionGen = builder.comment("Max MRU leaked per working tick (a random amount from 0 to this).",
                            "工作时每 tick 最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 3, 0, 10000);
            builder.pop();
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_ITEM = 1;
    public static final int SLOTS = 2;

    /** 界面状态（原作 GuiRepairState 的五种文字，加一个红石停机） */
    public static final int STATUS_NOTHING = 0;
    public static final int STATUS_CANNOT = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_NO_MRU = 3;
    public static final int STATUS_WORKING = 4;
    public static final int STATUS_REDSTONE = 5;

    /** 停下多少 tick 以后才告诉客户端「不修了」，免得 MRU 时有时无时来回发包 */
    private static final int WORKING_GRACE = 10;
    private static final String TAG_WORKING = "working";

    private int status = STATUS_NOTHING;
    private int workingGrace;
    /** 服务端：上次告诉客户端的状态；客户端：收到的状态（冒粒子用） */
    private boolean working;

    public MagicalRepairerBlockEntity(BlockPos pos, BlockState state) {
        super(ECDevices.MAGICAL_REPAIRER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    /** 槽位规则（两端共用）：0 号绑定宝石，1 号任意物品（原作 SlotGeneric） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return defaultSlotRule(slot, stack);
    }

    /** 这个东西能不能修、要不要修：能修（{@link #canRepair}）、有损耗 */
    public static boolean needsRepair(ItemStack stack) {
        return !stack.isEmpty() && canRepair(stack) && stack.isDamaged();
    }

    /** 这台机器能不能修它（不管有没有损耗）：可损坏，并且可修理或者在 {@link ECTags#MRU_REPAIRABLE} 里 */
    public static boolean canRepair(ItemStack stack) {
        return stack.isDamageableItem() && (stack.isRepairable() || stack.is(ECTags.MRU_REPAIRABLE));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 自动化只往 1 号槽送要修的东西 */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        if (slot == SLOT_ITEM) {
            return needsRepair(stack);
        }
        return super.canAutomationInsert(slot, stack);
    }

    /** 自动化只能取走修好了或修不了的东西（按槽里现在的物品判断） */
    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == SLOT_ITEM && !needsRepair(inventory.getStackInSlot(SLOT_ITEM));
    }

    // ------------------------------------------------------------------
    // 工作
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (level == null) {
            return;
        }
        boolean repaired = false;
        ItemStack stack = inventory.getStackInSlot(SLOT_ITEM);
        int usage = ECConfig.get(Config.mruUsage);
        if (stack.isEmpty()) {
            status = STATUS_NOTHING;
        } else if (!canRepair(stack)) {
            status = STATUS_CANNOT;
        } else if (!stack.isDamaged()) {
            status = STATUS_DONE;
        } else if (neighborPowered()) {
            status = STATUS_REDSTONE;
        } else if (mru.getMRU() < usage) {
            status = STATUS_NO_MRU;
        } else {
            status = STATUS_WORKING;
            mru.extractMRU(usage, true);
            stack.setDamageValue(stack.getDamageValue() - 1);
            // 改的是槽里物品自身，ItemStackHandler 不知道：只标记存盘；界面的耐久条由菜单的槽位同步负责
            level.blockEntityChanged(worldPosition);
            if (ECConfig.get(Config.generatesCorruption)) {
                ECCorruption.randomIncreaseAt(level, worldPosition, level.random, ECConfig.get(Config.maxCorruptionGen));
            }
            repaired = true;
        }
        if (repaired) {
            workingGrace = WORKING_GRACE;
        } else if (workingGrace > 0) {
            workingGrace--;
        }
        boolean nowWorking = workingGrace > 0;
        if (nowWorking != working) {
            working = nowWorking;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 原作 spawnParticles：修的时候冒紫色红石粒子 */
    @Override
    protected void clientTick() {
        if (level == null || !working) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1F, 0F, 1F), 1F);
        for (int i = 0; i < 10; i++) {
            level.addParticle(dust,
                    worldPosition.getX() + 0.25D + level.random.nextDouble() / 2.2D,
                    worldPosition.getY() + 0.25D + i / 20D,
                    worldPosition.getZ() + 0.25D + level.random.nextDouble() / 2.2D,
                    0D, 0D, 0D);
        }
    }

    /** 界面状态码（服务端算，随菜单同步） */
    public int statusCode() {
        return status;
    }

    public boolean isWorking() {
        return working;
    }

    // ------------------------------------------------------------------
    // 同步
    // ------------------------------------------------------------------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean(TAG_WORKING, working);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 只有发给客户端的更新包里有这个键（存档里没有）
        if (tag.contains(TAG_WORKING)) {
            working = tag.getBoolean(TAG_WORKING);
        }
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MagicalRepairerMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return working;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.REPAIRER;
    }
}
