package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.blockentity.ChargingChamberBlockEntity;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.armor.ArmorCharge;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 第十三批 13b「维持方便」（batch13.md §4.7）的 GameTest：
 * <ul>
 *   <li>充能仓给站在正上方的主人充身上的盔甲：和物品格轮流（物品格放一件永远充不满的，盔甲照样轮得到）、改的是穿着的那个物品对象；
 *       别人站上去、主人站在旁边、没有主人、有红石信号都不充；charge_worn_armor 关掉时只充物品格（自己的批次，覆盖值跨刻）。</li>
 *   <li>充电改的只是 NBT：照原版换装的写法发一次「同一件、NBT 变了」的换装事件，全套宅男加的生命不被夹回去。</li>
 *   <li>「满电约挡」：按阶的单价（宅男 0 / 1 / 4 阶）、单价配成 0 时不耗电、价格倍数配成 0 时不耗电；样本伤害是物理；
 *       不挡的伤害、只给新覆盖面的伤害（样本没有攻击者）、不是护盾盔甲都返回「不挡」。</li>
 *   <li>电量合计只算穿在自己部位的护盾盔甲，普通盔甲、穿错部位的不算。</li>
 *   <li>补测：魔力防护按阶的容量（2 阶每步 400、充到 8 000）；记下的主人不在线不充；每刻只充一样（有没有人站着花费一样）；
 *       两台叠放只认上面那台（正上方是地毯时跳起来照样算）。</li>
 * </ul>
 * 主人用 {@link ProtectionFrameworkTests#realPlayer}（不在世界的实体表里）加在线覆盖：充能仓只认主人这个对象、按坐标判站没站在上面，不查实体，所以不用真把人放进世界。
 * 充能仓不放绑定宝石，每刻把它自己的 MRU 补满，数好算。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ArmorChargeTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final String BATCH_OFF = "ec5_armor_charge_off";
    private static final BlockPos CHAMBER = new BlockPos(2, 1, 2);

    private ArmorChargeTests() {
    }

    // ================================================================ 工具

    /** 摆一台充能仓（owner 为 null 时没有主人），每刻把它的 MRU 补满 */
    private static ChargingChamberBlockEntity chamber(GameTestHelper helper, BlockPos relative, @Nullable Player owner) {
        helper.setBlock(relative, ECBlocks.CHARGING_CHAMBER.get());
        ChargingChamberBlockEntity be = (ChargingChamberBlockEntity) helper.getBlockEntity(relative);
        if (owner != null) {
            be.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        }
        helper.onEachTick(() -> be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU()));
        return be;
    }

    /** 当成在线的真玩家，站在 relative 那一格的正中 */
    private static ServerPlayer online(GameTestHelper helper, String name, BlockPos relative) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        ECProtection.setOnlineOverride(player);
        Vec3 at = helper.absoluteVec(new Vec3(relative.getX() + 0.5D, relative.getY(), relative.getZ() + 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static ItemStack withMRU(ItemStack stack, int mru) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    private static int mru(ItemStack stack) {
        return MRUStaffItem.storedMRU(stack);
    }

    private static ComputerArmorItem computer(ArmorItem.Type type) {
        return ECComputer.armor().get(type.ordinal()).get();
    }

    // ================================================================ 充能仓

    /** 主人站在上面：物品格（放一件 100 万的宅男靴，测试里永远充不满）和身上两件 MRU 盔甲轮流充；普通盔甲不算；穿着的还是原来那个物品对象 */
    @GameTest(template = EMPTY)
    public static void chamberChargesOwnerArmorInTurns(GameTestHelper helper) {
        ServerPlayer owner = online(helper, "ec5-charge-own", CHAMBER.above());
        ChargingChamberBlockEntity be = chamber(helper, CHAMBER, owner);
        ItemStack slotItem = withMRU(new ItemStack(computer(ArmorItem.Type.BOOTS)), 0);
        be.getInventory().setStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM, slotItem);
        ItemStack helmet = withMRU(new ItemStack(ECVoidArmor.VOID_HELMET.get()), 0);
        ItemStack chest = withMRU(new ItemStack(computer(ArmorItem.Type.CHESTPLATE)), 0);
        ItemStack legs = new ItemStack(Items.IRON_LEGGINGS);
        // 能掉耐久的物品一建出来就带 Damage:0，比的是整件
        ItemStack legsBefore = legs.copy();
        owner.setItemSlot(EquipmentSlot.HEAD, helmet);
        owner.setItemSlot(EquipmentSlot.CHEST, chest);
        owner.setItemSlot(EquipmentSlot.LEGS, legs);

        helper.succeedWhen(() -> {
            helper.assertTrue(be.wearer() == owner, "站在上面的主人应被认下");
            ItemStack slot = be.getInventory().getStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM);
            helper.assertTrue(mru(slot) > 0, "物品格照样充");
            helper.assertTrue(mru(slot) < slot.getCapability(ECCapabilities.MRU_ITEM).map(h -> h.getMaxMRU()).orElse(0), "物品格的那件测试里不该满（前提）");
            helper.assertTrue(owner.getItemBySlot(EquipmentSlot.HEAD) == helmet && owner.getItemBySlot(EquipmentSlot.CHEST) == chest,
                    "充电改的是穿着的那个物品对象，不换掉它");
            helper.assertTrue(mru(helmet) > 0, "虚空头盔应轮到、充上电，实际 " + mru(helmet));
            helper.assertTrue(mru(chest) > 0, "宅男胸甲应轮到、充上电，实际 " + mru(chest));
            helper.assertTrue(ItemStack.matches(legs, legsBefore), "铁护腿不是 MRU 盔甲，不该被动");
            ECProtection.clearOnlineOverride(owner.getUUID());
        });
    }

    /** 只认站在正上方的主人：别人站上去、主人站在旁边、没有主人（专用服没有房主）、有红石信号都不充；对照组照常充 */
    @GameTest(template = EMPTY_LARGE)
    public static void chamberChargesOnlyOwnerOnTop(GameTestHelper helper) {
        BlockPos strangerPos = new BlockPos(1, 1, 1);
        BlockPos besidePos = new BlockPos(4, 1, 1);
        BlockPos unownedPos = new BlockPos(7, 1, 1);
        BlockPos poweredPos = new BlockPos(1, 1, 6);
        BlockPos controlPos = new BlockPos(6, 1, 6);

        ServerPlayer owner = online(helper, "ec5-charge-o1", besidePos.east().above());
        ServerPlayer stranger = online(helper, "ec5-charge-s", strangerPos.above());
        ServerPlayer unownedRider = online(helper, "ec5-charge-u", unownedPos.above());
        ServerPlayer poweredOwner = online(helper, "ec5-charge-p", poweredPos.above());
        ServerPlayer controlOwner = online(helper, "ec5-charge-c", controlPos.above(2));
        ChargingChamberBlockEntity strangerChamber = chamber(helper, strangerPos, owner);
        ChargingChamberBlockEntity besideChamber = chamber(helper, besidePos, owner);
        ChargingChamberBlockEntity unowned = chamber(helper, unownedPos, null);
        ChargingChamberBlockEntity powered = chamber(helper, poweredPos, poweredOwner);
        helper.setBlock(poweredPos.west(), Blocks.REDSTONE_BLOCK);
        ChargingChamberBlockEntity control = chamber(helper, controlPos, controlOwner);

        List<ServerPlayer> idle = List.of(owner, stranger, unownedRider, poweredOwner);
        for (ServerPlayer player : idle) {
            player.setItemSlot(EquipmentSlot.CHEST, withMRU(new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()), 0));
        }
        // 对照：脚在上面第二格（跳起来）也算
        ItemStack controlChest = withMRU(new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()), 0);
        controlOwner.setItemSlot(EquipmentSlot.CHEST, controlChest);

        helper.runAtTickTime(40, () -> {
            try {
                helper.assertTrue(mru(controlChest) > 0, "对照：主人脚在上面第二格时应充上电（前提）");
                helper.assertTrue(control.wearer() == controlOwner, "对照：应认下主人");
                for (ServerPlayer player : idle) {
                    helper.assertTrue(mru(player.getItemBySlot(EquipmentSlot.CHEST)) == 0,
                            player.getGameProfile().getName() + " 不该被充电，实际 " + mru(player.getItemBySlot(EquipmentSlot.CHEST)));
                }
                helper.assertTrue(strangerChamber.wearer() == null && besideChamber.wearer() == null && unowned.wearer() == null
                        && powered.wearer() == null, "这几台都不该认下人");
                helper.assertTrue(!ChargingChamberBlockEntity.standsOn(helper.absolutePos(besidePos), owner), "站在旁边一格不算站在上面");
                // 1 × 2 × 1 的边界：脚正好在充能仓顶面算，高出三格不算；重心挪到旁边那格上不算
                BlockPos abs = helper.absolutePos(controlPos);
                double cx = abs.getX() + 0.5D;
                double cz = abs.getZ() + 0.5D;
                controlOwner.setPos(cx, abs.getY() + 1D, cz);
                helper.assertTrue(ChargingChamberBlockEntity.standsOn(abs, controlOwner), "脚踩在顶面上算");
                controlOwner.setPos(cx, abs.getY() + 3D, cz);
                helper.assertTrue(!ChargingChamberBlockEntity.standsOn(abs, controlOwner), "脚高出三格不算");
                controlOwner.setPos(abs.getX() + 1.05D, abs.getY() + 1D, cz);
                helper.assertTrue(!ChargingChamberBlockEntity.standsOn(abs, controlOwner), "重心在旁边那格上不算");
            } finally {
                for (ServerPlayer player : List.of(owner, stranger, unownedRider, poweredOwner, controlOwner)) {
                    ECProtection.clearOnlineOverride(player.getUUID());
                }
            }
            helper.succeed();
        });
    }

    /**
     * 充电只改 NBT：全套宅男（最大生命 120）站上去充电，照原版换装的写法发「同一件、只是 NBT 变了」的换装事件，下一刻生命还是 120、胸甲还是那个对象。
     * 原版对 NBT 变化也发 {@code LivingEquipmentChangeEvent}，各套靠「换下的和换上的是不是同一种物品」认脱装。
     */
    @GameTest(template = EMPTY)
    public static void chamberChargeIsNotUnequip(GameTestHelper helper) {
        ServerPlayer owner = online(helper, "ec5-charge-nbt", CHAMBER.above());
        ChargingChamberBlockEntity be = chamber(helper, CHAMBER, owner);
        ItemStack[] suit = new ItemStack[4];
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            suit[type.ordinal()] = withMRU(new ItemStack(computer(type)), 0);
            owner.setItemSlot(type.getSlot(), suit[type.ordinal()]);
            owner.getAttributes().addTransientAttributeModifiers(suit[type.ordinal()].getAttributeModifiers(type.getSlot()));
        }
        float full = owner.getMaxHealth();
        helper.assertTrue(full > 20F, "全套宅男应加最大生命（前提），实际 " + full);
        owner.setHealth(full);
        ItemStack chest = suit[ArmorItem.Type.CHESTPLATE.ordinal()];
        ItemStack before = chest.copy();

        helper.succeedWhen(() -> {
            helper.assertTrue(mru(chest) > 0, "胸甲应充上电");
            helper.assertTrue(owner.getItemBySlot(EquipmentSlot.CHEST) == chest, "还是原来那件");
            MinecraftForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(owner, EquipmentSlot.CHEST, before, chest));
            MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(owner));
            helper.assertTrue(owner.getHealth() == full, "充电不算脱装，生命应还是 " + full + "，实际 " + owner.getHealth());
            ECProtection.clearOnlineOverride(owner.getUUID());
        });
    }

    /** charge_worn_armor 关掉：主人站上去也只充物品格（覆盖值跨刻，自己的批次） */
    @GameTest(template = EMPTY, batch = BATCH_OFF)
    public static void chamberArmorChargingOff(GameTestHelper helper) {
        ChargingChamberBlockEntity.Config.setTestChargeWornArmor(false);
        ServerPlayer owner = online(helper, "ec5-charge-off", CHAMBER.above());
        ChargingChamberBlockEntity be = chamber(helper, CHAMBER, owner);
        ItemStack slotItem = withMRU(new ItemStack(computer(ArmorItem.Type.BOOTS)), 0);
        be.getInventory().setStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM, slotItem);
        ItemStack chest = withMRU(new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()), 0);
        owner.setItemSlot(EquipmentSlot.CHEST, chest);

        helper.runAtTickTime(30, () -> {
            try {
                helper.assertTrue(mru(be.getInventory().getStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM)) > 0, "物品格照样充");
                helper.assertTrue(mru(chest) == 0 && be.wearer() == null, "关掉以后不给盔甲充电，实际 " + mru(chest));
            } finally {
                ChargingChamberBlockEntity.Config.setTestChargeWornArmor(null);
                ECProtection.clearOnlineOverride(owner.getUUID());
            }
            helper.succeed();
        });
    }

    // ================================================================ 满电约挡、合计

    /**
     * 「满电约挡」按这件的阶和配置算：宅男胸甲 0 阶 = 容量 ÷ (8 × 单价)，1 / 4 阶按阶的单价倍数；单价、阶倍数配成 0 时不耗电；
     * 样本是物理伤害；宅男 0 阶不挡魔法、2 阶挡魔法也算「不挡」（新覆盖面要有攻击者，样本没有）；虚空也挡样本；不是护盾盔甲的不挡。
     */
    @GameTest(template = EMPTY)
    public static void fullChargeHitsFollowStageAndConfig(GameTestHelper helper) {
        DamageSource sample = ArmorCharge.samplePhysical(helper.getLevel());
        helper.assertTrue(sample != null && ECDamageKinds.isPhysical(sample) && !MRUShield.excluded(sample), "样本应是物理伤害");

        ECComputer.Config.Values values = ECComputer.Config.values();
        ECComputer.Config.Attuned attuned = ECComputer.Config.attuned();
        int share = ComputerArmorItem.share(ArmorItem.Type.CHESTPLATE);
        for (int stage : new int[]{0, 1, 4}) {
            ItemStack chest = new ItemStack(computer(ArmorItem.Type.CHESTPLATE));
            if (stage > 0) {
                Attunement.set(chest, stage, 0);
            }
            int perHit = share * attuned.price(values.mruPerDamage(), stage);
            int expected = values.maxMru() / perHit;
            int hits = ArmorCharge.fullChargeHits(chest, sample);
            helper.assertTrue(hits == expected, stage + " 阶胸甲满电应挡 " + expected + " 下，实际 " + hits);
        }
        helper.assertTrue(ArmorCharge.fullChargeHits(new ItemStack(computer(ArmorItem.Type.CHESTPLATE)), sample) == 500,
                "默认配置 0 阶胸甲：1 000 000 ÷ (8 × 250) = 500");
        helper.assertTrue(ArmorCharge.fullChargeHits(new ItemStack(computer(ArmorItem.Type.HELMET)), sample) == 1_000_000 / (3 * 250),
                "默认配置 0 阶头盔：1 000 000 ÷ (3 × 250)");

        ECComputer.Config.setTestOverrides(ECComputer.Config.DEFAULTS.toBuilder().mruPerDamage(0).build());
        try {
            helper.assertTrue(ArmorCharge.fullChargeHits(new ItemStack(computer(ArmorItem.Type.CHESTPLATE)), sample) == ArmorCharge.FREE,
                    "单价配成 0：不耗电");
        } finally {
            ECComputer.Config.setTestOverrides(null);
        }
        ECComputer.Config.Attuned d = ECComputer.Config.ATTUNED_DEFAULTS;
        ECComputer.Config.setAttunedTestOverrides(new ECComputer.Config.Attuned(List.of(1D, 0D), d.magicPriceMultiplier(), d.piercingShare(),
                d.piercingPriceMultiplier(), d.pvpShieldMaxShare(), d.pvpShieldCostMultiplier(), d.repairDelay(), d.repairPercent(),
                d.repairMruPerHealth(), d.overloadThreshold(), d.overloadDuration(), d.overloadCooldown()));
        try {
            ItemStack stageOne = new ItemStack(computer(ArmorItem.Type.CHESTPLATE));
            Attunement.set(stageOne, 1, 0);
            helper.assertTrue(ArmorCharge.fullChargeHits(stageOne, sample) == ArmorCharge.FREE, "1 阶单价倍数配成 0：不耗电");
            helper.assertTrue(ArmorCharge.fullChargeHits(new ItemStack(computer(ArmorItem.Type.CHESTPLATE)), sample) == 500, "0 阶照旧");
        } finally {
            ECComputer.Config.setAttunedTestOverrides(null);
        }

        DamageSource magic = helper.getLevel().damageSources().magic();
        helper.assertTrue(ArmorCharge.fullChargeHits(new ItemStack(computer(ArmorItem.Type.CHESTPLATE)), magic) == ArmorCharge.NOT_SHIELDED,
                "0 阶不挡魔法");
        ItemStack stageTwo = new ItemStack(computer(ArmorItem.Type.CHESTPLATE));
        Attunement.set(stageTwo, 2, 0);
        helper.assertTrue(ArmorCharge.fullChargeHits(stageTwo, magic) == ArmorCharge.NOT_SHIELDED, "没有攻击者的魔法伤害新覆盖面也不挡");

        ItemStack voidChest = new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get());
        IMRUShieldArmor voidArmor = (IMRUShieldArmor) voidChest.getItem();
        IMRUShieldArmor.Coverage coverage = voidArmor.coverage(voidChest, sample);
        helper.assertTrue(coverage != null, "虚空胸甲挡样本（物理）");
        int voidMax = voidChest.getCapability(ECCapabilities.MRU_ITEM).map(h -> h.getMaxMRU()).orElse(0);
        int voidCost = MRUShield.cost(ArmorCharge.SAMPLE_DAMAGE, coverage.share(), voidArmor.mruPerDamage(voidChest), coverage.priceMultiplier());
        helper.assertTrue(ArmorCharge.fullChargeHits(voidChest, sample) == voidMax / voidCost, "虚空胸甲按自己的容量、份额、单价算");
        helper.assertTrue(ArmorCharge.fullChargeHits(new ItemStack(Items.DIAMOND_CHESTPLATE), sample) == ArmorCharge.NOT_SHIELDED,
                "钻石胸甲不是护盾盔甲");
        helper.succeed();
    }

    /**
     * 魔力防护、虚空套装的「满电约挡」跟着各自的同调阶走（默认配置，20 点物理样本）：
     * 魔力防护胸甲 0 / 1 / 2 阶 2 000 / 4 000 / 8 000 MRU、份额 2 / 3 / 4、每点 100（1 阶起 −10% 是 90）→ 10 / 14 / 22 下，头盔 0 阶 20 下、靴子 2 阶 88 下；
     * 虚空胸甲 0 阶 5 万 ÷ 3 200 = 15 下，1 阶容量翻倍、单价 380 → 32 下，3 阶单价 333 → 37 下，头盔 0 阶 41 下。手册同调章和装备章的表照这些数写
     */
    @GameTest(template = EMPTY)
    public static void fullChargeHitsForMhsAndVoidStages(GameTestHelper helper) {
        DamageSource sample = ArmorCharge.samplePhysical(helper.getLevel());
        int[][] mhs = {{0, 10}, {1, 14}, {2, 22}};
        for (int[] row : mhs) {
            ItemStack chest = new ItemStack(ECGear.MHS_CHESTPLATE.get());
            Attunement.set(chest, row[0], 0);
            int hits = ArmorCharge.fullChargeHits(chest, sample);
            helper.assertTrue(hits == row[1], "魔力防护胸甲 " + row[0] + " 阶应约挡 " + row[1] + " 下，实际 " + hits);
        }
        helper.assertTrue(ArmorCharge.fullChargeHits(new ItemStack(ECGear.MHS_HELMET.get()), sample) == 20, "魔力防护头盔 0 阶 20 下");
        ItemStack boots = new ItemStack(ECGear.MHS_BOOTS.get());
        Attunement.set(boots, 2, 0);
        helper.assertTrue(ArmorCharge.fullChargeHits(boots, sample) == 88, "魔力防护靴子 2 阶 8 000 ÷ 90 = 88 下，实际 " + ArmorCharge.fullChargeHits(boots, sample));

        int[][] voids = {{0, 15}, {1, 32}, {3, 37}};
        for (int[] row : voids) {
            ItemStack chest = new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get());
            Attunement.set(chest, row[0], 0);
            int hits = ArmorCharge.fullChargeHits(chest, sample);
            helper.assertTrue(hits == row[1], "虚空胸甲 " + row[0] + " 阶应约挡 " + row[1] + " 下，实际 " + hits);
        }
        helper.assertTrue(ArmorCharge.fullChargeHits(new ItemStack(ECVoidArmor.VOID_HELMET.get()), sample) == 41, "虚空头盔 0 阶 41 下");
        helper.succeed();
    }

    /** 电量合计：只算穿在自己部位的护盾盔甲；普通盔甲、穿错部位的不算；一件都没有是 NONE */
    @GameTest(template = EMPTY)
    public static void armorChargeTotalCountsWornShieldPieces(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        helper.assertTrue(!ArmorCharge.worn(player).any(), "什么都没穿：没有电量条");

        ItemStack chest = withMRU(new ItemStack(computer(ArmorItem.Type.CHESTPLATE)), 250_000);
        ItemStack boots = new ItemStack(ECVoidArmor.VOID_BOOTS.get());
        int bootsMax = boots.getCapability(ECCapabilities.MRU_ITEM).map(h -> h.getMaxMRU()).orElse(0);
        withMRU(boots, bootsMax / 2);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        player.setItemSlot(EquipmentSlot.HEAD, withMRU(new ItemStack(computer(ArmorItem.Type.LEGGINGS)), 1_000_000));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));

        ArmorCharge.Total total = ArmorCharge.worn(player);
        long max = 1_000_000L + bootsMax;
        long stored = 250_000L + bootsMax / 2;
        helper.assertTrue(total.pieces() == 2, "只算胸甲和靴子两件，实际 " + total.pieces());
        helper.assertTrue(total.max() == max && total.stored() == stored, "合计应是 " + stored + " / " + max + "，实际 " + total.stored() + " / " + total.max());
        helper.assertTrue(total.percent() == (int) (stored * 100L / max), "百分比向下取整，实际 " + total.percent());
        helper.assertTrue(Math.abs(total.fraction() - (float) stored / max) < 1.0E-4F, "比例");

        withMRU(chest, 1_000_000);
        withMRU(boots, bootsMax - 1);
        helper.assertTrue(ArmorCharge.worn(player).percent() == 99, "差 1 MRU 没满写 99");
        withMRU(boots, bootsMax);
        helper.assertTrue(ArmorCharge.worn(player).percent() == 100, "满了写 100");
        clear(player);
        helper.assertTrue(ArmorCharge.worn(player) == ArmorCharge.Total.NONE, "脱光以后是 NONE");
        helper.succeed();
    }

    // ================================================================ 容量、不在线的主人、花费、叠放

    /** 摆一台充能仓，MRU 只在开头充满一次（数花费用） */
    private static ChargingChamberBlockEntity chamberOnce(GameTestHelper helper, BlockPos relative, Player owner) {
        helper.setBlock(relative, ECBlocks.CHARGING_CHAMBER.get());
        ChargingChamberBlockEntity be = (ChargingChamberBlockEntity) helper.getBlockEntity(relative);
        be.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        return be;
    }

    /** 魔力防护的容量随阶：主人站上去充 2 阶胸甲，每步 400（8 000 的 1/20），充到 8 000 停 */
    @GameTest(template = EMPTY)
    public static void chamberChargesMhsByStage(GameTestHelper helper) {
        ServerPlayer owner = online(helper, "ec5-charge-mhs", CHAMBER.above());
        chamber(helper, CHAMBER, owner);
        ItemStack chest = new ItemStack(ECGear.MHS_CHESTPLATE.get());
        Attunement.set(chest, 2, 0);
        owner.setItemSlot(EquipmentSlot.CHEST, chest);
        int[] last = {0};
        List<String> steps = new ArrayList<>();
        helper.onEachTick(() -> {
            int now = mru(chest);
            if (now != last[0] && now - last[0] != 400) {
                steps.add(last[0] + " → " + now);
            }
            last[0] = now;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(steps.isEmpty(), "2 阶胸甲每步应充 400，实际 " + steps);
            helper.assertTrue(mru(chest) == 8000, "应充到 8 000，实际 " + mru(chest));
            ECProtection.clearOnlineOverride(owner.getUUID());
        });
    }

    /** 记下的主人不在线（专用服没有房主）：站上去的那个对象就算 UUID 对得上也不认、不充；物品格照常充 */
    @GameTest(template = EMPTY)
    public static void chamberSkipsOfflineOwner(GameTestHelper helper) {
        ServerPlayer offline = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-charge-gone");
        Vec3 at = helper.absoluteVec(new Vec3(CHAMBER.getX() + 0.5D, CHAMBER.getY() + 1D, CHAMBER.getZ() + 0.5D));
        offline.moveTo(at.x, at.y, at.z, 0F, 0F);
        ChargingChamberBlockEntity be = chamber(helper, CHAMBER, offline);
        ItemStack chest = withMRU(new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()), 0);
        offline.setItemSlot(EquipmentSlot.CHEST, chest);
        be.getInventory().setStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM, withMRU(new ItemStack(computer(ArmorItem.Type.BOOTS)), 0));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(ECProtection.onlineOwner(be) == null, "记下的主人不在线：没有在线主人（前提）");
            helper.assertTrue(be.wearer() == null && mru(chest) == 0, "不在线的主人不充，实际 " + mru(chest));
            helper.assertTrue(mru(be.getInventory().getStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM)) > 0, "物品格照常充");
            helper.succeed();
        });
    }

    /**
     * 每刻只充一样：两台充能仓物品格各放一件空的 0 阶魔力防护胸甲（每步 100），主人站在其中一台上、身上也穿一件。
     * 同样的刻数里两台花掉的 MRU 一样多，站着人的那台花的 = 物品格 + 盔甲充进去的
     */
    @GameTest(template = EMPTY_LARGE)
    public static void chamberSpendsSameWithArmor(GameTestHelper helper) {
        BlockPos withArmor = new BlockPos(1, 1, 1);
        BlockPos itemOnly = new BlockPos(6, 1, 1);
        ServerPlayer owner = online(helper, "ec5-charge-cost", withArmor.above());
        ChargingChamberBlockEntity a = chamberOnce(helper, withArmor, owner);
        ChargingChamberBlockEntity b = chamberOnce(helper, itemOnly, owner);
        ItemStack slotA = new ItemStack(ECGear.MHS_CHESTPLATE.get());
        ItemStack slotB = new ItemStack(ECGear.MHS_CHESTPLATE.get());
        a.getInventory().setStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM, slotA);
        b.getInventory().setStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM, slotB);
        ItemStack worn = new ItemStack(ECGear.MHS_CHESTPLATE.get());
        owner.setItemSlot(EquipmentSlot.CHEST, worn);
        int full = a.getMRUStorage().getMaxMRU();
        helper.runAtTickTime(16, () -> {
            try {
                int spentA = full - a.getMRUStorage().getMRU();
                int spentB = full - b.getMRUStorage().getMRU();
                ItemStack chargedA = a.getInventory().getStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM);
                ItemStack chargedB = b.getInventory().getStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM);
                helper.assertTrue(a.wearer() == owner && b.wearer() == null, "只有脚下那台认下主人（前提）");
                helper.assertTrue(mru(worn) > 0 && mru(chargedB) < 2000, "盔甲轮到过、物品格没充满（前提），实际 " + mru(worn) + " / " + mru(chargedB));
                helper.assertTrue(spentA > 0 && spentA == spentB, "两台每刻花费一样，实际 " + spentA + " / " + spentB);
                helper.assertTrue(spentA == mru(chargedA) + mru(worn) && spentB == mru(chargedB), "花掉的正好是充进去的");
            } finally {
                ECProtection.clearOnlineOverride(owner.getUUID());
            }
            helper.succeed();
        });
    }

    /**
     * 两台叠着放、主人站在上面那台顶上：脚正好在下面那台的「再上一格」里，可那一格被上面那台整格占着，只算上面那台。
     * 正上方是地毯这类矮东西时，跳起来（脚在第二格）照样算；正上方是整格方块时，站在它上面不算
     */
    @GameTest(template = EMPTY_LARGE)
    public static void chamberStackedOnlyUpperCharges(GameTestHelper helper) {
        BlockPos lowerPos = new BlockPos(2, 1, 2);
        BlockPos upperPos = lowerPos.above();
        BlockPos carpetPos = new BlockPos(6, 1, 2);
        ServerPlayer owner = online(helper, "ec5-charge-stk", upperPos.above());
        ChargingChamberBlockEntity lower = chamber(helper, lowerPos, owner);
        ChargingChamberBlockEntity upper = chamber(helper, upperPos, owner);
        owner.setItemSlot(EquipmentSlot.CHEST, withMRU(new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()), 0));
        helper.runAtTickTime(30, () -> {
            try {
                helper.assertTrue(upper.wearer() == owner, "上面那台认下主人");
                helper.assertTrue(lower.wearer() == null, "下面那台不认（脚在它的第二格，可那一格是上面那台）");
                helper.assertTrue(!ChargingChamberBlockEntity.standsOn(helper.absolutePos(lowerPos), owner)
                        && ChargingChamberBlockEntity.standsOn(helper.absolutePos(upperPos), owner), "standsOn：只算上面那台");

                helper.setBlock(carpetPos, ECBlocks.CHARGING_CHAMBER.get());
                helper.setBlock(carpetPos.above(), Blocks.WHITE_CARPET);
                BlockPos abs = helper.absolutePos(carpetPos);
                owner.setPos(abs.getX() + 0.5D, abs.getY() + 2.3D, abs.getZ() + 0.5D);
                helper.assertTrue(ChargingChamberBlockEntity.standsOn(abs, owner), "上面铺着地毯：跳起来（脚在第二格）照样算");
                helper.setBlock(carpetPos.above(), Blocks.STONE);
                owner.setPos(abs.getX() + 0.5D, abs.getY() + 2D, abs.getZ() + 0.5D);
                helper.assertTrue(!ChargingChamberBlockEntity.standsOn(abs, owner), "上面是整格石头：站在石头上不算");
            } finally {
                ECProtection.clearOnlineOverride(owner.getUUID());
            }
            helper.succeed();
        });
    }

    private static void clear(LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                entity.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }
}
