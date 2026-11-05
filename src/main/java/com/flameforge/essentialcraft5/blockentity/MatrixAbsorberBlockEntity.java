package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.menu.MatrixAbsorberMenu;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 矩阵换能器（英文显示名 Matrix Destructor）。对应原作 TileMatrixAbsorber。整条进度链里第一份 MRU 从这里来。
 * <p>
 * 0 号槽放一块绑定了玩家的灵魂石。没有红石信号、灵魂石的主人在线（任何维度）、自己没满时，
 * 每 tick 从主人身上扣 {@code ubmru_usage}（默认 10）UBMRU，产出 {@code mru_generated}（默认 1）MRU；主人 UBMRU 不够一次就不换。
 * 平衡值固定为配置值（默认 1.0，纯净）。不拉 MRU，没有绑定宝石槽；0 号槽对漏斗是输出槽（原作 getOutputSlots = {0}）。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>0 号槽只收灵魂石（原作任何物品都能放进去，漏斗会往里塞杂物）。</li>
 *   <li>原作工作时的粒子（品红色红石粉末）和低鸣声在客户端/服务端判断写反了，实际上从来没出现过。
 *       这里改由服务端每 5 tick 发一次粒子包；声音（音量 0.01，本来也听不见）不移植。</li>
 *   <li>原作配置 RequestImmediateSync（每次扣 UBMRU 都立即同步给主人）去掉：玩家数据变了本来就会在下一个玩家 tick 同步。</li>
 * </ul>
 */
public class MatrixAbsorberBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.matrixabsorber */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue balance;
        public static ForgeConfigSpec.IntValue mruGenerated;
        public static ForgeConfigSpec.IntValue ubmruUsage;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("matrix_absorber");
            // 原作 ApiCore.GENERATOR_MAX_MRU_GENERIC / 10
            maxMRU = builder.defineInRange("max_mru", ECConfig.GENERATOR_MAX_MRU / 10, 1, Integer.MAX_VALUE);
            balance = builder.defineInRange("balance", 1D, 0D, 2D);
            mruGenerated = builder.comment("MRU produced per conversion (one conversion per tick).",
                            "每次转换（每 tick 一次）产出多少 MRU")
                    .defineInRange("mru_generated", 1, 0, Integer.MAX_VALUE);
            ubmruUsage = builder.comment("UBMRU taken from the Soul Stone's owner per conversion.",
                            "每次转换从灵魂石主人身上扣多少 UBMRU")
                    .defineInRange("ubmru_usage", 10, 0, Integer.MAX_VALUE);
            builder.pop();
        }
    }

    public static final int SLOT_SOUL_STONE = 0;

    /** 工作时粒子的颜色：原作 REDSTONE 粒子参数 (1, 0, 1)，品红 */
    private static final DustParticleOptions PARTICLE = new DustParticleOptions(new Vector3f(1F, 0F, 1F), 1F);
    /** 工作时多少 tick 发一次粒子包 */
    private static final int PARTICLE_INTERVAL = 5;

    /** 这一刻从主人身上换成了 MRU（工作声音和粒子看它，见 {@link #ambientWorking}） */
    private boolean absorbedThisTick;

    public MatrixAbsorberBlockEntity(BlockPos pos, BlockState state) {
        super(ECBootstrap.MATRIX_ABSORBER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 1);
    }

    /** 槽位规则（两端共用）：只收灵魂石 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return stack.getItem() instanceof SoulStoneItem;
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == SLOT_SOUL_STONE;
    }

    @Override
    protected void serverTick() {
        mru.setBalance((float) ECConfig.get(Config.balance));
        absorbedThisTick = false;
        if (level == null || level.getServer() == null || neighborPowered()) {
            return;
        }
        UUID owner = SoulStoneItem.getOwner(inventory.getStackInSlot(SLOT_SOUL_STONE));
        if (owner == null) {
            return;
        }
        // 主人必须在线（原作 MiscUtils.getPlayerFromUUID，查的是整个服务器的玩家列表，不限维度）
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        IPlayerData data = player == null ? null : PlayerDataEvents.get(player);
        absorbedThisTick = data != null && absorb(data);
        if (absorbedThisTick && level.getGameTime() % PARTICLE_INTERVAL == 0 && level instanceof ServerLevel server) {
            // 原作每 tick 在方块中心 0.25~0.7 格高处画 10 个；这里服务端每 5 tick 发 5 个，散布范围差不多
            server.sendParticles(PARTICLE, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D,
                    5, 0.12D, 0.12D, 0.12D, 0D);
        }
    }

    /**
     * 从 owner 身上换一次 MRU：自己没满、UBMRU 够扣一次时扣 UBMRU、加 MRU。
     * 抽出来单独写，GameTest 用假的玩家数据直接测比例（测试环境里造不出「在线」的玩家）。
     *
     * @return 这次是否换成了
     */
    public boolean absorb(IPlayerData owner) {
        if (mru.getMRU() >= mru.getMaxMRU()) {
            return false;
        }
        int usage = ECConfig.get(Config.ubmruUsage);
        int current = owner.getUBMRU();
        // 原作 current - usage >= 0 才换
        if (current < usage) {
            return false;
        }
        owner.setUBMRU(current - usage);
        mru.addMRU(ECConfig.get(Config.mruGenerated), true);
        return true;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MatrixAbsorberMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return absorbedThisTick;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.MATRIX_ABSORBER;
    }
}
