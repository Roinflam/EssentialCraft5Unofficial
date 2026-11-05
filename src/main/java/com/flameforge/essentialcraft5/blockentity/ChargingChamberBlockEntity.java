package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.ChargingChamberMenu;
import com.flameforge.essentialcraft5.registry.ECBlockEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 充能仓。对应原作 TileChargingChamber。
 * <p>
 * 0 号槽绑定宝石，1 号槽放要充能的物品。没有红石信号时，每 tick 给物品充其上限的 1/20，
 * 充 1 MRU 消耗充能仓 {@code charge_cost_modifier} MRU。1 号槽对漏斗、管道是输出槽（原作 getOutputSlots = {1}）。
 *
 * <h2>给主人身上的盔甲充电（第十三批 13b，batch13.md §4.7 第 1 条）</h2>
 * 每 {@value #WEARER_CHECK_INTERVAL} 刻看一次机器主人（{@link ECProtection#onlineOwner}：记下的主人在线；单人里没记主人、
 * 或者没开局域网时记下的人不在线，算房主）是不是站在正上方：脚在充能仓上面那一格或再上一格里（1 × 2 × 1，站着、跳起来都算），
 * 而且身子的中线落在这一格上（站在边上、重心在旁边那格的不算）。再上一格只在上面那一格没被整格高的方块占着时才算：
 * 两台叠放、人站在上面那台顶上时，脚正好在下面那台的「再上一格」里，只算上面那台，不让叠放变成两台一起充。
 * 别人站上去一概不充（P1，防蹭电）。只认主人这一个对象，
 * 不做范围查询（P2）；两次查看之间只看这个人还在不在这个世界、活着、不是旁观。
 * <p>
 * 站着的时候，物品格和他身上头 / 胸 / 腿 / 靴四件可充能的盔甲（{@link ArmorItem}、穿在自己部位、带 {@code MRU_ITEM}）<b>轮流</b>充：
 * 每刻只充一样，按原来的规则（这一样上限的 1/20、同样的耗费倍数），充完换下一样，满了的跳过。每刻的 MRU 花费和只充物品格时一样多。
 * 充电只改盔甲自己的 NBT（同一个物品对象），原版按「NBT 变了」发换装事件，各套的换装处理器把同一件当成没脱（虚空、宅男都这样判）。
 * 服主关掉 {@code charge_worn_armor} 时只充物品格，和以前一样。
 */
public class ChargingChamberBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.chargingchamber，加上第十三批的 {@code charge_worn_armor} */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue chargeCostModifier;
        public static ForgeConfigSpec.BooleanValue chargeWornArmor;
        /** GameTest 用：不为 null 时代替 charge_worn_armor（不碰真配置：服务端配置会自动存盘，文件监视器重读会冲掉改的值） */
        @Nullable
        private static volatile Boolean testChargeWornArmor;

        /** 给站在上面的主人的盔甲充电（charge_worn_armor） */
        public static boolean chargesWornArmor() {
            Boolean override = testChargeWornArmor;
            return override != null ? override : ECConfig.get(chargeWornArmor);
        }

        /** GameTest 用：null 恢复照配置（会跨刻生效，用它的测试放自己的批次） */
        public static void setTestChargeWornArmor(@Nullable Boolean value) {
            testChargeWornArmor = value;
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("charging_chamber");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            chargeCostModifier = builder.comment("MRU taken from the chamber per 1 MRU charged into the item.",
                            "给物品充 1 MRU 要消耗充能仓多少 MRU")
                    .defineInRange("charge_cost_modifier", 1D, 0.0001D, 1000D);
            chargeWornArmor = builder.comment("Also charge the MRU armor worn by the chamber's owner while they stand on top of it,",
                            "taking turns with the item slot. Nobody else gets charged.",
                            "主人站在充能仓正上方时，和物品格轮流给他身上可充能的盔甲充电（别人站上去不充）")
                    .define("charge_worn_armor", true);
            builder.pop();
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_ITEM = 1;
    /** 多少刻看一次正上方站没站着主人 */
    public static final int WEARER_CHECK_INTERVAL = 10;
    /** 轮流的顺序：0 是物品格，1~4 是这几个盔甲部位 */
    private static final EquipmentSlot[] ARMOR_ORDER = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int TARGETS = 1 + ARMOR_ORDER.length;

    /** 这一刻给物品充进了 MRU（工作声音和粒子看它，见 {@link #ambientWorking}） */
    private boolean chargedThisTick;
    /** 上次查看时站在正上方的主人（只在内存，不存盘）；没人是 null */
    @Nullable
    private ServerPlayer wearer;
    /** 轮到谁（0 是物品格，1~4 见 {@link #ARMOR_ORDER}） */
    private int turn;

    public ChargingChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ECBlockEntities.CHARGING_CHAMBER.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 2);
    }

    /** 槽位规则（两端共用）：0 号绑定宝石，1 号任意物品（原作 isItemValidForSlot） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == SLOT_ITEM;
    }

    @Override
    protected void serverTick() {
        chargedThisTick = false;
        if (level == null || neighborPowered()) {
            wearer = null;
            return;
        }
        refreshWearer();
        for (int i = 0; i < TARGETS; i++) {
            int index = (turn + i) % TARGETS;
            ItemStack stack = target(index);
            if (stack.isEmpty()) {
                continue;
            }
            Charge result = charge(stack);
            if (result == Charge.NOTHING) {
                continue;
            }
            if (result == Charge.CHARGED) {
                chargedThisTick = true;
                if (index == 0) {
                    // 改的是槽里物品自身的 NBT，ItemStackHandler 不知道，手动标记一下（存盘、客户端显示都要）
                    inventoryContentsChanged(SLOT_ITEM);
                }
            }
            turn = (index + 1) % TARGETS;
            return;
        }
    }

    /** 每 {@value #WEARER_CHECK_INTERVAL} 刻重新认一次站在上面的主人；两次之间只剔掉走了的（离开世界、死了、改成旁观） */
    private void refreshWearer() {
        if (!Config.chargesWornArmor()) {
            wearer = null;
            return;
        }
        if (wearer != null && !chargeable(wearer)) {
            wearer = null;
        }
        // 各台错开查看的刻，一片充能仓不挤在同一刻
        if ((level.getGameTime() + Math.floorMod(worldPosition.asLong(), WEARER_CHECK_INTERVAL)) % WEARER_CHECK_INTERVAL != 0) {
            return;
        }
        ServerPlayer owner = ECProtection.onlineOwner(this);
        wearer = owner != null && chargeable(owner) && standsOn(worldPosition, owner) ? owner : null;
    }

    private boolean chargeable(ServerPlayer player) {
        return !player.isRemoved() && player.level() == level && player.isAlive() && !player.isSpectator();
    }

    /**
     * 脚在 pos 正上方 1 × 2 × 1 的格子里，身子的中线落在这一格上（站在充能仓上、在上面跳都算）。
     * 第二格（跳起来时）只在正上方那一格的碰撞箱不到一整格高时算：地毯、台阶这类矮东西上照样能跳，
     * 正上方是整格方块（另一台充能仓之类）时脚在第二格就是站在那个方块上，不算站在这台上。方块按 entity 所在的世界查。
     */
    public static boolean standsOn(BlockPos pos, Entity entity) {
        double y = entity.getY();
        if (Mth.floor(entity.getX()) != pos.getX() || Mth.floor(entity.getZ()) != pos.getZ() || y < pos.getY() + 1 || y >= pos.getY() + 3) {
            return false;
        }
        if (y < pos.getY() + 2) {
            return true;
        }
        BlockPos above = pos.above();
        VoxelShape shape = entity.level().getBlockState(above).getCollisionShape(entity.level(), above);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) < 1D;
    }

    /** 轮到的那一样：0 是物品格，1~4 是站在上面的主人这个部位穿着的盔甲（不是盔甲、穿错部位的不算） */
    private ItemStack target(int index) {
        if (index == 0) {
            return inventory.getStackInSlot(SLOT_ITEM);
        }
        ServerPlayer player = wearer;
        if (player == null) {
            return ItemStack.EMPTY;
        }
        EquipmentSlot slot = ARMOR_ORDER[index - 1];
        ItemStack stack = player.getItemBySlot(slot);
        return stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot ? stack : ItemStack.EMPTY;
    }

    private enum Charge {
        /** 不用充（不是 MRU 物品、已经满了、这一步是 0、充能仓没电）：看下一样 */
        NOTHING,
        /** 动了充能仓的 MRU，可是一点没充进去（耗费倍数大到剩下的电换不来 1 MRU，原作就是这样）：这一刻到此为止 */
        SPENT,
        CHARGED
    }

    /** 按原作的规则给这一样充一步 */
    private Charge charge(ItemStack stack) {
        IMRUHandlerItem target = stack.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        if (target == null) {
            return Charge.NOTHING;
        }
        int current = target.getMRU();
        int max = target.getMaxMRU();
        if (current >= max) {
            return Charge.NOTHING;
        }
        double costModifier = ECConfig.get(Config.chargeCostModifier);
        int step = max / 20;
        // 原作两个分支：离满还差超过一步就充一步，否则只充差的那点
        int wanted = current + step < max ? step : max - current;
        if (wanted <= 0) {
            return Charge.NOTHING;
        }
        int taken = mru.extractMRU((int) (wanted * costModifier), true);
        int charged = (int) (taken / costModifier);
        if (charged > 0) {
            target.addMRU(charged, true);
            return Charge.CHARGED;
        }
        return taken > 0 ? Charge.SPENT : Charge.NOTHING;
    }

    /** GameTest 用：上次查看时认下的、站在上面的主人（没有是 null） */
    @Nullable
    public ServerPlayer wearer() {
        return wearer;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ChargingChamberMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return chargedThisTick;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.CHARGER;
    }
}
