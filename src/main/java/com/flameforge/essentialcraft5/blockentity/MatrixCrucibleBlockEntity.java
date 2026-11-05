package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MatrixCrucibleMenu;
import com.flameforge.essentialcraft5.registry.ECMatrix;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 矩阵淬炼台（第十三批 13a，batch13.md §3.1.2，限制见 policy §4.16）：突破矩阵阶的台子。本身只是「MRU + 4 个祭品格」，
 * 试炼的规则全在 {@code matrix/MatrixTrials}（状态机）和 {@code matrix/MatrixTrialChecks}（开始前的核对）。
 * <ul>
 *   <li>0 号格是绑定宝石（照常从源头拉 MRU），MRU 上限 {@code machines.matrix_crucible.max_mru}（默认 10 万）；</li>
 *   <li>1~4 号是祭品格：玩家手放什么都行，<b>漏斗、管道一格都插不进</b>（祭品要亲手放），也取不出（{@link MRUBlockEntity} 只许取输出格，这里没有输出格）；</li>
 *   <li>试炼开始后祭品格<b>锁住</b>（{@link #lockedBy()}），界面上拿不走也放不进；成功才消耗，失败原样留着、解锁。锁只在内存里，
 *       试炼本来就不跨重启（下线 = 失败）。挖掉台子时祭品照常掉出来，试炼随之算失败。</li>
 * </ul>
 * 主人照 {@code MachineBlock} 记放下的人；别人能不能用这台由领地的交互保护管（右键开不开得了界面），试炼只看开始的那个人自己的阶和图谱。
 * 不漏 MRU、不改世界方块、没有声音粒子框架的「干活」状态（试炼的声音、粒子由状态机直接放）。
 * <p>
 * 客户端：渲染器只要知道「这台正在试炼」（台上的灵魂石转快一点、亮一点），随更新包带一个布尔（{@link #isTrialActiveClient()}），不存盘；
 * 物品栏、平衡值都不发给附近的人（界面走菜单同步）。
 */
public class MatrixCrucibleBlockEntity extends MRUBlockEntity {

    public static final int SLOT_GEM = 0;
    /** 第一个祭品格 */
    public static final int FIRST_OFFERING = 1;
    /** 祭品格数（同 {@code MatrixRankData.MAX_OFFERINGS}） */
    public static final int OFFERING_SLOTS = 4;
    public static final int SLOTS = FIRST_OFFERING + OFFERING_SLOTS;

    private static final String TAG_TRIAL = "trial_active";

    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("matrix_crucible");
            maxMRU = builder.comment("MRU capacity of the Matrix Crucible (trials from Rank 2 up take MRU from it). Keep it at least as large as the",
                            "biggest MRU cost in the rank data (100000 for Ranks 8 and 9 by default); a rank that needs more than this can never start.",
                            "矩阵淬炼台的 MRU 容量（二阶起的试炼从这里扣 MRU）。不要小于阶数据里最大的 MRU 要求（默认八、九阶各 10 万），",
                            "要的比容量还大的那一阶永远开始不了")
                    .defineInRange("max_mru", 100_000, 1, 100_000_000);
            builder.pop();
        }
    }

    /** 正在这台上试炼的人（只在服务端、只在内存里）；null = 没锁 */
    @Nullable
    private UUID lockedBy;
    /** 客户端：服务端说这台正在试炼 */
    private boolean trialActiveClient;

    public MatrixCrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(ECMatrix.MATRIX_CRUCIBLE_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    // ------------------------------------------------------------------
    // 槽位
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用）：0 号只放绑定宝石，祭品格什么都行 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return defaultSlotRule(slot, stack);
    }

    public static boolean isOfferingSlot(int slot) {
        return slot >= FIRST_OFFERING && slot < SLOTS;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 祭品要亲手放，宝石也只许手放：漏斗、管道一格都插不进 */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    /** 四个祭品格里现在放的东西（直接引用槽里的那一堆，别改） */
    public List<ItemStack> offerings() {
        List<ItemStack> out = new ArrayList<>(OFFERING_SLOTS);
        for (int i = 0; i < OFFERING_SLOTS; i++) {
            out.add(inventory.getStackInSlot(FIRST_OFFERING + i));
        }
        return out;
    }

    /** 从第 index 个祭品格（0~3）扣掉 count 个（试炼成功时由状态机调） */
    public void consumeOffering(int index, int count) {
        if (index < 0 || index >= OFFERING_SLOTS || count <= 0) {
            return;
        }
        inventory.extractItem(FIRST_OFFERING + index, count, false);
    }

    // ------------------------------------------------------------------
    // 锁
    // ------------------------------------------------------------------

    /** 正在这台上试炼的人；null = 没在试炼（服务端） */
    @Nullable
    public UUID lockedBy() {
        return lockedBy;
    }

    public boolean isLocked() {
        return lockedBy != null;
    }

    /** 试炼开始时锁、结束时解（null）；变化时告诉附近的客户端（渲染要知道） */
    public void setLockedBy(@Nullable UUID player) {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean changed = (lockedBy == null) != (player == null);
        lockedBy = player;
        if (changed) {
            markClientDirty();
        }
    }

    /** 客户端：这台正在试炼（渲染器用） */
    public boolean isTrialActiveClient() {
        return trialActiveClient;
    }

    // ------------------------------------------------------------------
    // 存盘、同步
    // ------------------------------------------------------------------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (lockedBy != null) {
            tag.putBoolean(TAG_TRIAL, true);
        }
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 只随更新包来，存档里没有；服务端读档时读成 false，不碰内存里的锁
        trialActiveClient = tag.getBoolean(TAG_TRIAL);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MatrixCrucibleMenu(id, inventory, this);
    }
}
