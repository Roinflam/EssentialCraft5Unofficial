package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.demon.DemonTrades;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.ComputerAttunement;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorEvents;
import com.flameforge.essentialcraft5.item.MHSArmorItem;
import com.flameforge.essentialcraft5.item.MatrixTraits;
import com.flameforge.essentialcraft5.item.MobMaterialItem;
import com.flameforge.essentialcraft5.item.VoidArmorEvents;
import com.flameforge.essentialcraft5.item.VoidArmorItem;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.item.attunement.ArmorGrowth;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.matrix.MatrixCodex;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CBossFxPacket;
import com.flameforge.essentialcraft5.player.PlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECTesla;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十三批 13c 预备（batch13.md §9.3）的 GameTest：
 * <ul>
 *   <li>玩家数据新键 {@code mob_ledger}、{@code boss_first_kills}：默认值（老存档读出来都是空的、空的不写进存档、{@code DATA_VERSION} 不变）、往返、
 *       台账不进同步包而首杀进、死亡复制两样都留着；</li>
 *   <li>首领画面包编解码、类型按字节截、非有限数读成 0；协议号 11；</li>
 *   <li>三种新伤害类型：注册表里有、按难度缩放的口径、标签、构造出来的攻击者 / 直接实体、物理 / 魔法 / 能不能闪；</li>
 *   <li>按 batch13.md §5.1 的表核对各套护盾与审判庭对它们的挡法（全靠标签和已有判定得出）；</li>
 *   <li>改过的五个实体标签照常加载（{@code required: false} 的条目没注册时不报错；注册了以后必须在标签里）；</li>
 *   <li>两种掉落材料已注册；</li>
 *   <li>「打实」统一判定 {@link ECLandedHit}：黄心整下吃掉算、取消的和 0 伤害又没吃黄心的不算、同一下问几次都一样；
 *       矩阵特性、凝滞反冻、同调点（武器 / 盔甲）、图谱首领参战四处都认被黄心整下吃掉的一下。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch13cPrepTests {

    private static final String EMPTY = "empty";
    private static final double EPS = 1.0E-4D;

    private Batch13cPrepTests() {
    }

    // ================================================================== 玩家数据

    /** 新键的默认值、往返、同步拆分、死亡复制 */
    @GameTest(template = EMPTY)
    public static void mobPlayerDataKeys(GameTestHelper helper) {
        PlayerData fresh = new PlayerData();
        helper.assertTrue(fresh.getMobLedger().isEmpty() && fresh.getBossFirstKills().isEmpty(), "新建的玩家数据：台账、首杀都是空的");
        CompoundTag freshTag = fresh.serializeNBT();
        helper.assertFalse(freshTag.contains("mob_ledger") || freshTag.contains("boss_first_kills"), "空的不写进存档");
        helper.assertTrue(PlayerData.DATA_VERSION == 1 && freshTag.getInt("data_version") == 1, "13c 不升 DATA_VERSION");

        CompoundTag old = new CompoundTag();
        old.putInt("matrix_damage", 1200);
        old.putInt("data_version", 1);
        PlayerData loaded = new PlayerData();
        loaded.deserializeNBT(old);
        helper.assertTrue(loaded.getMobLedger().isEmpty() && loaded.getBossFirstKills().isEmpty() && loaded.getMatrixDamage() == 1200,
                "没有这两个键的老存档读成空的，别的照旧");

        PlayerData data = new PlayerData();
        CompoundTag group = new CompoundTag();
        group.putIntArray("buckets", new int[]{1, 2, 3});
        CompoundTag ledger = new CompoundTag();
        ledger.put("overworld_common", group);
        data.setMobLedger(ledger);
        data.getMobLedger().putLong("cursor", 99L);
        helper.assertTrue(data.getMobLedger().getLong("cursor") == 99L, "getMobLedger 给的是活的那份，原地改就记上了");
        helper.assertTrue(data.addBossFirstKill("minecraft:wither"), "第一次记首杀返回 true");
        helper.assertFalse(data.addBossFirstKill("minecraft:wither"), "同一种再记返回 false");
        data.addBossFirstKill("essentialcraft5:hologram");

        PlayerData back = new PlayerData();
        back.deserializeNBT(data.serializeNBT());
        helper.assertTrue(back.getMobLedger().equals(data.getMobLedger()), "台账往返不变：" + back.getMobLedger());
        helper.assertTrue(List.copyOf(back.getBossFirstKills()).equals(List.of("minecraft:wither", "essentialcraft5:hologram")),
                "首杀往返不变、按记下的先后：" + back.getBossFirstKills());
        back.getMobLedger().putInt("extra", 1);
        helper.assertFalse(data.getMobLedger().contains("extra"), "读出来的是自己的一份，不和存档那份共用");

        CompoundTag sync = data.serializeForSync();
        helper.assertFalse(sync.contains("mob_ledger"), "台账只在服务端，不进同步包");
        helper.assertTrue(sync.contains("boss_first_kills"), "首杀随普通同步包发");
        PlayerData client = new PlayerData();
        client.deserializeSync(sync);
        helper.assertTrue(client.getBossFirstKills().contains("minecraft:wither") && client.getMobLedger().isEmpty(), "客户端收到首杀、没有台账");

        data.setMobLedger(null);
        helper.assertTrue(data.getMobLedger().isEmpty(), "setMobLedger(null) 当空表");

        Player original = helper.makeMockPlayer();
        IPlayerData from = data(original);
        from.setMobLedger(ledger.copy());
        from.addBossFirstKill("minecraft:ender_dragon");
        Player reborn = helper.makeMockPlayer();
        PlayerDataEvents.clone(new PlayerEvent.Clone(reborn, original, true));
        IPlayerData to = data(reborn);
        helper.assertTrue(to.getMobLedger().contains("overworld_common") && to.getBossFirstKills().contains("minecraft:ender_dragon"),
                "死亡复制：台账和首杀都留着（死一次不清按人递减的计数）");
        helper.succeed();
    }

    // ================================================================== 网络

    /** 首领画面包：编解码不变；类型按一个字节截；非有限数读成 0；协议号 11 */
    @GameTest(template = EMPTY)
    public static void bossFxPacketRoundTrip(GameTestHelper helper) {
        S2CBossFxPacket packet = new S2CBossFxPacket(S2CBossFxPacket.PARADOX, 123_456, 0.5F, -3.25F);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        S2CBossFxPacket.encode(packet, buf);
        S2CBossFxPacket back = S2CBossFxPacket.decode(buf);
        helper.assertTrue(back.equals(packet) && buf.readableBytes() == 0, "编解码不变、读完不剩：" + back);

        S2CBossFxPacket odd = new S2CBossFxPacket(300, 7, Float.NaN, Float.POSITIVE_INFINITY);
        helper.assertTrue(odd.type() == (300 & 0xFF) && odd.a() == 0F && odd.b() == 0F, "类型按字节截、非有限数读成 0：" + odd);
        FriendlyByteBuf buf2 = new FriendlyByteBuf(Unpooled.buffer());
        S2CBossFxPacket.encode(new S2CBossFxPacket(255, 1, 1F, 2F), buf2);
        helper.assertTrue(S2CBossFxPacket.decode(buf2).type() == 255, "类型 255 读回来还是 255（无符号）");

        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        helper.assertTrue(S2CBossFxPacket.of(S2CBossFxPacket.PARADOX, zombie, 1F, 0F).entityId() == zombie.getId(), "of 取实体 id");
        helper.assertTrue(Integer.parseInt(ECNetwork.PROTOCOL) >= 11, "第十三批 13c 起协议号至少是 11，实际 " + ECNetwork.PROTOCOL);
        helper.succeed();
    }

    // ================================================================== 伤害类型

    /** 三种新伤害类型：注册表里有、按难度缩放（只对非玩家的活物攻击者）、标签、构造出来的伤害源、种类判定 */
    @GameTest(template = EMPTY)
    public static void damageTypes13c(GameTestHelper helper) {
        Registry<DamageType> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for (ResourceKey<DamageType> key : List.of(ECDamageTypes.MOB_BOLT, ECDamageTypes.ABERRANT_STRIKE, ECDamageTypes.MATRIX_REND)) {
            DamageType type = registry.get(key);
            helper.assertTrue(type != null, "数据包里要有伤害类型 " + key.location());
            helper.assertTrue(type.scaling() == DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, key.location() + " 的 scaling 应是 when_caused_by_living_non_player");
            helper.assertTrue(type.msgId().equals("essentialcraft5." + key.location().getPath()), key.location() + " 的 message_id");
        }
        helper.assertTrue(registry.getTag(ECDamageTypes.UNDODGEABLE).isPresent(), "伤害类型标签 undodgeable 应存在");

        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Arrow arrow = new Arrow(helper.getLevel(), zombie);
        DamageSource bolt = ECDamageTypes.mobBolt(arrow, zombie);
        DamageSource beam = ECDamageTypes.mobBolt(zombie);
        DamageSource strike = ECDamageTypes.aberrantStrike(zombie);
        DamageSource strikeVia = ECDamageTypes.aberrantStrike(arrow, zombie);
        DamageSource rend = ECDamageTypes.matrixRend(zombie);
        DamageSource discharge = ECDamageTypes.mruDischarge(zombie);

        helper.assertTrue(bolt.getEntity() == zombie && bolt.getDirectEntity() == arrow, "mobBolt(弹射物, 射手)");
        helper.assertTrue(beam.getEntity() == zombie && beam.getDirectEntity() == null && beam.getSourcePosition() != null
                        && beam.getSourcePosition().distanceToSqr(zombie.getEyePosition()) < 1.0E-6D,
                "mobBolt(射手)：光束没有直接实体（不算近战），来源位置是射手的眼睛（盾牌照样能挡）");
        helper.assertFalse(ECDamageKinds.isMelee(beam), "光束不算近战");
        helper.assertTrue(strike.getEntity() == zombie && strikeVia.getEntity() == zombie && strikeVia.getDirectEntity() == arrow, "aberrantStrike 两种写法");
        helper.assertTrue(rend.getEntity() == zombie && rend.getDirectEntity() == zombie, "matrixRend 的直接实体就是攻击者");
        helper.assertTrue(discharge.getEntity() == zombie && discharge.is(ECDamageTypes.MRU_DISCHARGE), "mruDischarge(攻击者)");

        helper.assertTrue(bolt.is(DamageTypeTags.IS_PROJECTILE) && beam.is(DamageTypeTags.IS_PROJECTILE), "mob_bolt 在 is_projectile 里");
        helper.assertFalse(bolt.is(DamageTypeTags.BYPASSES_ARMOR) || bolt.is(ECDamageTypes.UNDODGEABLE), "mob_bolt 吃护甲、能闪");
        helper.assertTrue(strike.is(DamageTypeTags.BYPASSES_ARMOR) && strike.is(ECDamageTypes.UNDODGEABLE) && !strike.is(ECDamageKinds.FORGE_IS_MAGIC),
                "aberrant_strike：无视护甲、不能闪、不是魔法");
        helper.assertTrue(rend.is(DamageTypeTags.BYPASSES_ARMOR) && rend.is(ECDamageTypes.UNDODGEABLE) && rend.is(ECDamageKinds.FORGE_IS_MAGIC),
                "matrix_rend：无视护甲、不能闪、forge:is_magic");
        helper.assertTrue(strike.is(DamageTypeTags.BYPASSES_SHIELD) && rend.is(DamageTypeTags.BYPASSES_SHIELD), "两种大招盾牌挡不住（bypasses_shield 含 #bypasses_armor）");
        helper.assertFalse(bolt.is(DamageTypeTags.BYPASSES_SHIELD), "mob_bolt 盾牌挡得住");

        helper.assertTrue(ECDamageKinds.isPhysical(bolt) && ECDamageKinds.isPhysical(beam) && !ECDamageKinds.isMagic(bolt), "mob_bolt 算物理");
        helper.assertTrue(ECDamageKinds.isMagic(rend) && !ECDamageKinds.isPhysical(rend), "matrix_rend 算魔法");
        helper.assertFalse(ECDamageKinds.isMagic(strike) || ECDamageKinds.isMagic(strikeVia), "aberrant_strike 不是魔法");
        helper.assertTrue(VoidArmorEvents.dodgeable(bolt) && !VoidArmorEvents.dodgeable(strike) && !VoidArmorEvents.dodgeable(rend),
                "虚空的闪避 / 相位：mob_bolt 能闪，两种大招不能");
        helper.succeed();
    }

    /**
     * batch13.md §5.1 的表：宅男 0~4 阶、魔力防护、虚空对三种伤害怎么挡；新覆盖面只对非玩家攻击者；审判庭 0 / 1 / 3 / 4 阶的倍数。
     * 这些都不是按类型写死的分支，是标签和已有判定一起得出的。
     */
    @GameTest(template = EMPTY)
    public static void damageTypes13cShieldTable(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        Player rival = mock(helper, 2.5D, 3.5D);
        Arrow arrow = new Arrow(helper.getLevel(), zombie);
        DamageSource bolt = ECDamageTypes.mobBolt(arrow, zombie);
        DamageSource strike = ECDamageTypes.aberrantStrike(zombie);
        DamageSource rend = ECDamageTypes.matrixRend(zombie);
        ECComputer.Config.Attuned cfg = ECComputer.Config.attuned();
        ArmorItem.Type chestType = ArmorItem.Type.CHESTPLATE;

        for (int stage = 0; stage <= 4; stage++) {
            ItemStack chest = attuned(new ItemStack(ECComputer.armor().get(chestType.ordinal()).get()), stage);
            double price = cfg.priceMultiplier(stage);
            IMRUShieldArmor.Coverage onBolt = ComputerAttunement.coverage(chest, chestType, bolt);
            helper.assertTrue(onBolt != null && !onBolt.extended() && Math.abs(onBolt.priceMultiplier() - price) < EPS,
                    "宅男 " + stage + " 阶按物理挡 mob_bolt（原价、不是新覆盖面）：" + onBolt);

            IMRUShieldArmor.Coverage onRend = ComputerAttunement.coverage(chest, chestType, rend);
            if (stage >= ComputerAttunement.MAGIC_STAGE) {
                helper.assertTrue(onRend != null && onRend.extended() && Math.abs(onRend.priceMultiplier() - price * cfg.magicPriceMultiplier()) < EPS,
                        "宅男 " + stage + " 阶按魔法挡 matrix_rend（×" + cfg.magicPriceMultiplier() + " 价）：" + onRend);
            } else {
                helper.assertTrue(onRend == null, "宅男 " + stage + " 阶不挡 matrix_rend");
            }

            IMRUShieldArmor.Coverage onStrike = ComputerAttunement.coverage(chest, chestType, strike);
            if (stage >= ComputerAttunement.PIERCING_STAGE) {
                helper.assertTrue(onStrike != null && onStrike.extended()
                                && Math.abs(onStrike.share() - ComputerArmorItem.share(chestType) * cfg.piercingShare()) < EPS
                                && Math.abs(onStrike.priceMultiplier() - price * cfg.piercingPriceMultiplier()) < EPS,
                        "宅男 " + stage + " 阶挡 aberrant_strike 的一半（×" + cfg.piercingPriceMultiplier() + " 价）：" + onStrike);
            } else {
                helper.assertTrue(onStrike == null, "宅男 " + stage + " 阶不挡 aberrant_strike");
            }
        }

        ItemStack topChest = attuned(new ItemStack(ECComputer.armor().get(chestType.ordinal()).get()), 4);
        helper.assertTrue(MRUShield.coverageFor(victim, topChest, rend) != null && MRUShield.coverageFor(victim, topChest, strike) != null,
                "新覆盖面：怪打来的照挡");
        helper.assertTrue(MRUShield.coverageFor(victim, topChest, ECDamageTypes.matrixRend(rival)) == null
                        && MRUShield.coverageFor(victim, topChest, ECDamageTypes.aberrantStrike(rival)) == null,
                "新覆盖面不对玩家攻击者生效");

        for (int stage : new int[]{0, MHSArmorItem.DISCHARGE_STAGE}) {
            ItemStack mhs = attuned(new ItemStack(ECGear.MHS_CHESTPLATE.get()), stage);
            MHSArmorItem item = (MHSArmorItem) mhs.getItem();
            IMRUShieldArmor.Coverage onBolt = item.coverage(mhs, bolt);
            helper.assertTrue(onBolt != null && !onBolt.extended(), "魔力防护 " + stage + " 阶按物理挡 mob_bolt：" + onBolt);
            helper.assertTrue(item.coverage(mhs, strike) == null && item.coverage(mhs, rend) == null, "魔力防护 " + stage + " 阶不挡两种大招");
        }
        ItemStack mhsTwo = attuned(new ItemStack(ECGear.MHS_CHESTPLATE.get()), MHSArmorItem.DISCHARGE_STAGE);
        IMRUShieldArmor.Coverage arc = MRUShield.coverageFor(victim, mhsTwo, ECDamageTypes.mruDischarge(zombie));
        helper.assertTrue(arc != null && arc.extended(), "魔力防护 2 阶挡怪打出的 mru_discharge（新覆盖面）：" + arc);

        // 虚空 3 阶起分走怪物打来的无视护甲伤害的 40%（gear_growth.void_armor.piercing_*，新覆盖面）：aberrant_strike 照份额挡；
        // matrix_rend 是魔法，虚空照旧不挡（放到最后断言，挂了不挡住后面审判庭那段）
        ItemStack voidThree = attuned(new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()), VoidArmorItem.ELEMENTAL_STAGE);
        for (int stage : new int[]{0, VoidArmorItem.ELEMENTAL_STAGE}) {
            ItemStack voidChest = attuned(new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()), stage);
            VoidArmorItem item = (VoidArmorItem) voidChest.getItem();
            IMRUShieldArmor.Coverage onBolt = item.coverage(voidChest, bolt);
            helper.assertTrue(onBolt != null && !onBolt.extended(), "虚空 " + stage + " 阶按物理挡 mob_bolt：" + onBolt);
            IMRUShieldArmor.Coverage onStrike = item.coverage(voidChest, strike);
            if (stage >= ArmorGrowth.voidPiercingStage()) {
                helper.assertTrue(onStrike != null && onStrike.extended()
                                && Math.abs(onStrike.share() - VoidArmorItem.share(chestType) * ArmorGrowth.voidPiercingShare()) < EPS
                                && Math.abs(onStrike.priceMultiplier() - VoidArmorItem.priceMultiplier(stage) * ArmorGrowth.voidPiercingPrice()) < EPS,
                        "虚空 " + stage + " 阶分走 aberrant_strike 的 40%（×3 价、新覆盖面）：" + onStrike);
                helper.assertTrue(MRUShield.coverageFor(victim, voidChest, ECDamageTypes.aberrantStrike(rival)) == null, "虚空分担无视护甲不对玩家攻击者生效");
            } else {
                helper.assertTrue(onStrike == null && item.coverage(voidChest, rend) == null, "虚空 " + stage + " 阶不挡两种大招");
            }
        }

        int[] stages = {0, InquisitoriumArmorEvents.ABSOLUTION_STAGE, InquisitoriumArmorEvents.BULWARK_STAGE, InquisitoriumArmorEvents.TOP_STAGE};
        // 抗魔按阶读 general.gear_growth.inquisitorium_armor.magic_resistance（0 / 1 / 3 / 4 阶 35 / 45 / 50 / 55%）
        double[] resist = {0.35D, 0.45D, 0.5D, 0.55D};
        ECInquisitorium.Config.Attuned inq = ECInquisitorium.Config.attuned();
        try {
            for (int i = 0; i < stages.length; i++) {
                int stage = stages[i];
                inquisitoriumSuit(victim, stage);
                double magic = 1D - resist[i];
                close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, bolt), 1D, "审判庭 " + stage + " 阶不减 mob_bolt");
                close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, rend), magic, "审判庭 " + stage + " 阶按抗魔减 matrix_rend");
                close(helper, InquisitoriumArmorEvents.incomingMultiplier(victim, strike),
                        stage >= InquisitoriumArmorEvents.TOP_STAGE ? 1D - inq.piercingResistance() : 1D, "审判庭 " + stage + " 阶对 aberrant_strike");
            }
        } finally {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                victim.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        IMRUShieldArmor.Coverage voidRend = ((VoidArmorItem) voidThree.getItem()).coverage(voidThree, rend);
        helper.assertTrue(voidRend == null, "虚空 3 阶照旧不挡魔法（matrix_rend 带 bypasses_armor 也不算无视护甲那一条）：" + voidRend);
        helper.succeed();
    }

    /**
     * 真挨一下：虚空胸甲闪避掷骰钉成必中时，mob_bolt（弹射物、光束）整下闪掉，aberrant_strike、matrix_rend 照样掉血；
     * 审判庭全套 0 阶挨 matrix_rend 按抗魔少掉，挨 mob_bolt 不减（真走 LivingHurtEvent 的那条路）
     */
    @GameTest(template = EMPTY)
    public static void damageTypes13cEndToEnd(GameTestHelper helper) {
        Player victim = mock(helper, 1.5D, 1.5D);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        Arrow arrow = new Arrow(helper.getLevel(), zombie);
        victim.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()));
        VoidArmorEvents.setTestDodgeRoll(0F);
        try {
            helper.assertTrue(lost(victim, ECDamageTypes.mobBolt(arrow, zombie), 4F) == 0F, "必中闪避：弹射物 mob_bolt 闪掉");
            helper.assertTrue(lost(victim, ECDamageTypes.mobBolt(zombie), 4F) == 0F, "必中闪避：光束 mob_bolt 闪掉");
            helper.assertTrue(lost(victim, ECDamageTypes.aberrantStrike(zombie), 4F) > 0F, "必中闪避也闪不掉 aberrant_strike");
            helper.assertTrue(lost(victim, ECDamageTypes.matrixRend(zombie), 4F) > 0F, "必中闪避也闪不掉 matrix_rend");
        } finally {
            VoidArmorEvents.setTestDodgeRoll(null);
            victim.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }

        float bare = lost(victim, ECDamageTypes.matrixRend(zombie), 4F);
        float bareBolt = lost(victim, ECDamageTypes.mobBolt(zombie), 4F);
        inquisitoriumSuit(victim, 0);
        try {
            double expected = bare * (1D - ArmorGrowth.inqMagicResistance(0));
            close(helper, lost(victim, ECDamageTypes.matrixRend(zombie), 4F), expected, "审判庭 0 阶挨 matrix_rend 按抗魔少掉（裸身掉 " + bare + "）");
            // 光束没有直接实体、不碰护甲以外的东西；审判庭不减，掉的血只看护甲，比裸身少但不为 0
            float suitBolt = lost(victim, ECDamageTypes.mobBolt(zombie), 4F);
            helper.assertTrue(suitBolt > 0F && suitBolt <= bareBolt, "审判庭挨 mob_bolt 照常掉血（只吃护甲）：裸身 " + bareBolt + "，穿着 " + suitBolt);
        } finally {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                victim.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        helper.succeed();
    }

    /** 满血挨一下，返回掉了多少血（之后补满） */
    private static float lost(Player victim, DamageSource source, float amount) {
        victim.setHealth(victim.getMaxHealth());
        victim.invulnerableTime = 0;
        victim.hurt(source, amount);
        float lost = victim.getMaxHealth() - victim.getHealth();
        victim.setHealth(victim.getMaxHealth());
        return lost;
    }

    // ================================================================== 标签、材料

    /** 改过的五个实体标签照常加载：原有成员都在；13c 的条目没注册时不报错，注册了以后必须在标签里 */
    @GameTest(template = EMPTY)
    public static void entityTags13c(GameTestHelper helper) {
        Registry<EntityType<?>> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
        helper.assertTrue(entity("essentialcraft5:hologram").is(Tags.EntityTypes.BOSSES)
                && entity("essentialcraft5:inquisitorium_sentinel").is(Tags.EntityTypes.BOSSES), "forge:bosses 原有的全息投影、哨兵还在");
        helper.assertTrue(entity("essentialcraft5:hologram").is(ECHoloWeapons.DIVIDER_IMMUNE), "divider_immune 原有的全息投影还在");
        helper.assertTrue(EntityType.VILLAGER.is(DemonTrades.SOUL_CAPTURABLE) && entity("essentialcraft5:wind_mage").is(DemonTrades.SOUL_CAPTURABLE),
                "soul_capturable 原有的村民、风法师还在");
        helper.assertTrue(entity("essentialcraft5:player_clone").is(ECTags.GROWTH_EXCLUDED)
                && entity("essentialcraft5:matrix_echo").is(ECTags.GROWTH_EXCLUDED), "growth_excluded 原有的克隆体、矩阵回响还在");
        helper.assertTrue(registry.getTag(ECTesla.MRU_COIL_IMMUNE).isPresent(), "mru_coil_immune 加载出来了（条目都还没注册时是空的）");

        Object[][] expected = {
                {"mru_golem", List.of(Tags.EntityTypes.BOSSES, ECHoloWeapons.DIVIDER_IMMUNE, ECTesla.MRU_COIL_IMMUNE)},
                {"matrix_shade", List.of(Tags.EntityTypes.BOSSES, ECHoloWeapons.DIVIDER_IMMUNE, ECTesla.MRU_COIL_IMMUNE)},
                {"nether_imp", List.of(DemonTrades.SOUL_CAPTURABLE)},
                {"shade_mirror", List.of(ECTags.GROWTH_EXCLUDED)},
                {"mind_spike", List.of(ECTags.GROWTH_EXCLUDED)},
                {"disorder_pillar", List.of(ECTags.GROWTH_EXCLUDED)},
        };
        for (Object[] row : expected) {
            ResourceLocation id = EssentialCraft5.id((String) row[0]);
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
                continue;
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            @SuppressWarnings("unchecked")
            List<TagKey<EntityType<?>>> tags = (List<TagKey<EntityType<?>>>) row[1];
            for (TagKey<EntityType<?>> tag : tags) {
                helper.assertTrue(type != null && type.is(tag), id + " 注册了，应在 " + tag.location() + " 里");
            }
        }
        helper.assertFalse(EntityType.ZOMBIE.is(Tags.EntityTypes.BOSSES) || EntityType.ZOMBIE.is(ECTesla.MRU_COIL_IMMUNE), "普通怪不在这些标签里");
        helper.succeed();
    }

    /** 上古晶核、矩阵结晶已注册：怪物材料类、稀有度、提示框键 */
    @GameTest(template = EMPTY)
    public static void mobDropMaterials(GameTestHelper helper) {
        helper.assertTrue(ECMobDrops.ANCIENT_CORE.getId().equals(EssentialCraft5.id("ancient_core"))
                && ECMobDrops.MATRIX_CRYSTAL.getId().equals(EssentialCraft5.id("matrix_crystal")), "注册名照命名表");
        helper.assertTrue(new ItemStack(ECMobDrops.ANCIENT_CORE.get()).getRarity() == Rarity.UNCOMMON
                && new ItemStack(ECMobDrops.MATRIX_CRYSTAL.get()).getRarity() == Rarity.RARE, "稀有度：晶核 UNCOMMON、结晶 RARE");
        helper.assertTrue(MobMaterialItem.tooltipKey("ancient_core").equals("tooltip.essentialcraft5.ancient_core.desc"), "提示框键");
        helper.assertTrue(ECMobDrops.ANCIENT_CORE.get().getMaxStackSize() == 64 && ECMobDrops.MATRIX_CRYSTAL.get().getMaxStackSize() == 64, "能堆 64");
        helper.succeed();
    }

    // ================================================================== 打实

    /**
     * 判定本身：黄心整下吃掉的一下（事件里是 0）算、吃掉的量对得上；同一下问几次都一样（中途黄心又变了也不影响）；
     * 没有黄心的普通一下算；被取消的不算；直接发的 0 伤害、没吃黄心的不算
     */
    @GameTest(template = EMPTY)
    public static void landedHitDefinition(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-lh-def");
        Zombie shielded = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        Zombie plain = zombie(helper, new Vec3(1.5D, 1D, 3.5D));
        shielded.setAbsorptionAmount(10F);
        float[] seen = {-1F, -1F, -1F};
        boolean[] landed = {false, false};
        Consumer<LivingDamageEvent> probe = event -> {
            if (event.getEntity() == shielded) {
                seen[0] = event.getAmount();
                landed[0] = ECLandedHit.landed(event);
                seen[1] = ECLandedHit.absorbed(event);
                shielded.setAbsorptionAmount(100F);
                seen[2] = ECLandedHit.absorbed(event);
            } else if (event.getEntity() == plain) {
                landed[1] = ECLandedHit.landed(event) && ECLandedHit.absorbed(event) == 0F;
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, probe);
        try {
            shielded.hurt(player.damageSources().playerAttack(player), 4F);
            plain.hurt(player.damageSources().playerAttack(player), 3F);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        helper.assertTrue(seen[0] == 0F, "测试前提：黄心整下吃掉，LivingDamageEvent 里是 0，实际 " + seen[0]);
        helper.assertTrue(landed[0], "黄心整下吃掉的一下算打实");
        close(helper, seen[1], 4D, "吃掉的黄心");
        close(helper, seen[2], 4D, "同一下再问还是第一次的数（中途黄心变了也一样）");
        helper.assertTrue(landed[1], "没有黄心的普通一下算打实、吃掉 0");

        DamageSource source = player.damageSources().playerAttack(player);
        LivingDamageEvent canceled = new LivingDamageEvent(plain, source, 5F);
        canceled.setCanceled(true);
        helper.assertFalse(ECLandedHit.landed(canceled), "被取消的不算");
        helper.assertFalse(ECLandedHit.landed(new LivingDamageEvent(plain, source, 0F)), "0 伤害、没吃黄心的不算");
        shielded.setAbsorptionAmount(5F);
        helper.assertFalse(ECLandedHit.landed(new LivingDamageEvent(shielded, source, 0F)), "没走 LivingHurtEvent 的 0 伤害，身上有黄心也不算（没有记录）");

        // 一下的两个事件之间又有一下打同一个目标（别的伤害源）：各认各的，后来的不把先来的记录顶掉
        Zombie nested = zombie(helper, new Vec3(3.5D, 1D, 3.5D));
        nested.setAbsorptionAmount(10F);
        DamageSource first = player.damageSources().playerAttack(player);
        DamageSource second = player.damageSources().thorns(player);
        ECLandedHit.onLandedHitHurtNote(new LivingHurtEvent(nested, first, 4F));
        nested.setAbsorptionAmount(6F);
        ECLandedHit.onLandedHitHurtNote(new LivingHurtEvent(nested, second, 2F));
        nested.setAbsorptionAmount(4F);
        close(helper, ECLandedHit.absorbed(new LivingDamageEvent(nested, second, 0F)), 2D, "后来那一下按自己的记录算吃掉 2");
        close(helper, ECLandedHit.absorbed(new LivingDamageEvent(nested, first, 0F)), 6D, "先来那一下的记录还在（10 → 4）");
        helper.succeed();
    }

    /**
     * 13a 那几处都认黄心整下吃掉的一下：矩阵特性（混沌腐蚀、扣 50）、凝滞反冻、同调点（武器、盔甲）、图谱首领参战（吃掉的黄心算有效伤害）；
     * 直接发的 0 伤害、没吃黄心的不记矩阵特性
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void landedHitAbsorptionCounts(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-lh-all");
        ItemStack storage = storage(1000);
        player.getInventory().setItem(9, storage);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECGear.ELEMENTAL_SWORD.get()));
        matrix(player, 3, MatrixResonance.CHAOS);

        Zombie chaosTarget = zombie(helper, new Vec3(2.5D, 1D, 2.5D));
        chaosTarget.setAbsorptionAmount(20F);
        MatrixTraits.flush();
        float dealt = hurtAndRead(chaosTarget, player.damageSources().playerAttack(player), 2F);
        helper.assertTrue(dealt == 0F, "测试前提：黄心整下吃掉，实际 " + dealt);
        helper.assertTrue(MatrixTraits.pendingCount() >= 1, "矩阵特性：黄心整下吃掉的一下也记下");
        MatrixTraits.flush();
        MobEffectInstance corrosion = chaosTarget.getEffect(ECEffects.CORROSION.get());
        helper.assertTrue(corrosion != null, "矩阵特性：黄心整下吃掉的一下照样挂腐蚀");
        helper.assertTrue(mru(storage) == 950, "矩阵特性：照扣 50，剩 " + mru(storage));

        Zombie bare = zombie(helper, new Vec3(1.5D, 1D, 3.5D));
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(bare, player.damageSources().playerAttack(player), 0F));
        helper.assertTrue(MatrixTraits.pendingCount() == 0, "0 伤害、没吃黄心的不记");

        matrix(player, 3, MatrixResonance.FROZEN);
        Zombie biter = zombie(helper, new Vec3(3.5D, 1D, 1.5D));
        player.setAbsorptionAmount(20F);
        player.invulnerableTime = 0;
        float onPlayer = hurtAndRead(player, player.damageSources().mobAttack(biter), 4F);
        helper.assertTrue(onPlayer == 0F, "测试前提：玩家的黄心整下吃掉，实际 " + onPlayer);
        MatrixTraits.flush();
        helper.assertTrue(biter.getTicksFrozen() == 30, "凝滞反冻：黄心整下吃掉的一下照样反冻 +30，实际 " + biter.getTicksFrozen());

        AttunementGains.setTestTime(-1L);
        data(player).setAttunementBudget(IPlayerData.BudgetKind.ARMOR, AttunementConfig.budgetCap(), true);
        data(player).setAttunementBudget(IPlayerData.BudgetKind.WEAPON, AttunementConfig.budgetCap(), true);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECGear.MHS_HELMET.get()));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()));
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 1, 1));
        husk.setTarget(player);
        try {
            husk.setAbsorptionAmount(20F);
            float onHusk = hurtAndRead(husk, player.damageSources().playerAttack(player), 1F);
            helper.assertTrue(onHusk == 0F, "测试前提：尸壳的黄心整下吃掉，实际 " + onHusk);
            helper.assertTrue(Math.abs(AttunementGains.pending(player, EquipmentSlot.MAINHAND) - 1F) < EPS,
                    "同调武器点：砍掉追着你的尸壳的黄心也算打实，主手 +1，实际 " + AttunementGains.pending(player, EquipmentSlot.MAINHAND));
            player.setAbsorptionAmount(20F);
            player.invulnerableTime = 0;
            float onWearer = hurtAndRead(player, husk.damageSources().mobAttack(husk), 4F);
            helper.assertTrue(onWearer == 0F, "测试前提：玩家的黄心整下吃掉，实际 " + onWearer);
            helper.assertTrue(Math.abs(AttunementGains.pending(player, EquipmentSlot.HEAD) - 1F) < EPS,
                    "同调盔甲点：被打过的尸壳打掉你的黄心也算，头盔 +1，实际 " + AttunementGains.pending(player, EquipmentSlot.HEAD));
        } finally {
            husk.discard();
            AttunementGains.forget(husk);
            AttunementGains.setTestTime(-1L);
            AttunementGains.forget(player);
            AttunementGains.forgetTypeCounts(player);
        }

        WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
        helper.assertTrue(wither != null, "造得出凋灵");
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        wither.moveTo(at.x, at.y, at.z, 0F, 0F);
        wither.setAbsorptionAmount(30F);
        float onWither = hurtAndRead(wither, player.damageSources().playerAttack(player), 6F);
        float eaten = 30F - wither.getAbsorptionAmount();
        helper.assertTrue(onWither == 0F && eaten > 0F, "测试前提：凋灵的黄心整下吃掉（伤害 " + onWither + "，黄心少了 " + eaten + "）");
        Float share = MatrixCodex.bossDamage(wither).get(player.getUUID());
        helper.assertTrue(share != null && Math.abs(share - eaten) < 1.0E-3F, "图谱首领参战：吃掉的黄心记成有效伤害 " + eaten + "，实际 " + share);
        MatrixCodex.record(wither, player.damageSources().playerAttack(player));
        helper.succeed();
    }

    // ================================================================== 工具

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家应挂着玩家数据");
        }
        return data;
    }

    private static Player mock(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 能挨打的真玩家（出生无敌只在 ServerPlayer.tick 里减，先空跑 61 次），生存模式、满血 */
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 1D, 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void matrix(ServerPlayer player, int rank, int type) {
        IPlayerData data = data(player);
        data.setMatrixRank(rank);
        data.setMatrixType(type);
    }

    /** 200 血、没有护甲的僵尸，不会动 */
    private static Zombie zombie(GameTestHelper helper, Vec3 relative) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, relative);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0D);
        zombie.setHealth(200F);
        return zombie;
    }

    private static ItemStack storage(int mru) {
        ItemStack stack = new ItemStack(ECItems.DARK_SOUL_MATTER.get());
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    private static int mru(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
    }

    private static ItemStack attuned(ItemStack stack, int stage) {
        if (stage > 0) {
            Attunement.set(stack, stage, 0);
        }
        return stack;
    }

    /** 穿四件 stage 阶的审判庭（只放进槽，不加属性） */
    private static void inquisitoriumSuit(LivingEntity entity, int stage) {
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            entity.setItemSlot(type.getSlot(), attuned(new ItemStack(ECInquisitorium.all().get(type.ordinal()).get()), stage));
        }
    }

    /** 挨一下（先清受伤冷却），返回这一下最后一次 LivingDamageEvent 的数（LOWEST、所有人改完以后）；没发事件时是 -1 */
    private static float hurtAndRead(LivingEntity victim, DamageSource source, float amount) {
        float[] last = {-1F};
        Consumer<LivingDamageEvent> probe = event -> {
            if (event.getEntity() == victim) {
                last[0] = event.getAmount();
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDamageEvent.class, probe);
        try {
            victim.invulnerableTime = 0;
            victim.hurt(source, amount);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
        return last[0];
    }

    private static EntityType<?> entity(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !ForgeRegistries.ENTITY_TYPES.containsKey(key)) {
            throw new IllegalStateException("没有实体 " + id);
        }
        return ForgeRegistries.ENTITY_TYPES.getValue(key);
    }

    private static void close(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EPS, what + "：应 " + expected + "，实际 " + actual);
    }
}
