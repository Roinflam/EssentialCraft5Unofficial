package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.effect.ArmorShredEffect;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十批 10b 四个机制效果（腐蚀、破甲、二分、锚定）和效果标签 {@code essentialcraft5:unfilterable} 的 GameTest（batch10.md §4.2.1）。
 * <p>
 * 效果都直接用 {@code addEffect} 加上（谁加的都一样生效），走的是原版的真实路径：{@code heal}、{@code hurt}、末影人的随机瞬移、珍珠落地、吃紫颂果、喝牛奶。
 * 传送那两条另挂一个最低优先级、收已取消事件的监听器：记下事件到它那里时有没有被取消（= 是不是本模组取消的），然后一律取消——
 * 对照组的末影人、玩家也就不会真的传走（末影人一传就是 32 格，会落进别的测试的场地）。监听器在同一段同步代码里挂上、摘掉，不影响并行的测试。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class EffectsFrameworkTests {

    private static final String EMPTY = "empty";
    /** EnderMan#teleport()（随机瞬移，protected）的 SRG 名；开发环境里 ObfuscationReflectionHelper 自动换成官方名 */
    private static final String ENDERMAN_RANDOM_TELEPORT = "m_32529_";

    private EffectsFrameworkTests() {
    }

    /** 腐蚀：heal 不回血（LivingHealEvent 被取消，不是数值改成 0）；去掉效果以后照常回 */
    @GameTest(template = EMPTY)
    public static void effectsCorrosionBlocksHealing(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setHealth(10F);
        zombie.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), 200));
        zombie.heal(4F);
        helper.assertTrue(zombie.getHealth() == 10F, "腐蚀时 heal(4) 不应回血，实际 " + zombie.getHealth());
        LivingHealEvent event = new LivingHealEvent(zombie, 4F);
        MinecraftForge.EVENT_BUS.post(event);
        helper.assertTrue(event.isCanceled(), "腐蚀时治疗事件应被取消");
        zombie.removeEffect(ECEffects.CORROSION.get());
        zombie.heal(4F);
        helper.assertTrue(zombie.getHealth() == 14F, "去掉腐蚀以后应照常回 4 点，实际 " + zombie.getHealth());
        helper.succeed();
    }

    /** 破甲：I 级护甲 −2；升到 III 护甲 −6、韧性 −3（固定 UUID 的修饰符各一个，升级不叠）；到期以后修饰符摘掉、属性复原 */
    @GameTest(template = EMPTY)
    public static void effectsArmorShredLowersArmor(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        AttributeInstance armor = zombie.getAttribute(Attributes.ARMOR);
        AttributeInstance toughness = zombie.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (armor == null || toughness == null) {
            helper.fail("僵尸没有护甲 / 韧性属性");
            return;
        }
        armor.setBaseValue(10D);
        toughness.setBaseValue(4D);
        zombie.addEffect(new MobEffectInstance(ECEffects.ARMOR_SHRED.get(), 2, 0));
        helper.assertTrue(zombie.getAttributeValue(Attributes.ARMOR) == 8D, "破甲 I 后护甲应 10 → 8，实际 " + zombie.getAttributeValue(Attributes.ARMOR));
        // 升到 III：原版先摘再挂，同一个 UUID 只有一份（−6，不是 −2 − 6）
        zombie.addEffect(new MobEffectInstance(ECEffects.ARMOR_SHRED.get(), 2, 2));
        AttributeModifier armorModifier = armor.getModifier(ArmorShredEffect.ARMOR_MODIFIER);
        AttributeModifier toughnessModifier = toughness.getModifier(ArmorShredEffect.TOUGHNESS_MODIFIER);
        helper.assertTrue(armorModifier != null && armorModifier.getAmount() == -6D, "破甲 III 护甲修饰符应是 −6，实际 " + armorModifier);
        helper.assertTrue(toughnessModifier != null && toughnessModifier.getAmount() == -3D, "破甲 III 韧性修饰符应是 −3，实际 " + toughnessModifier);
        helper.assertTrue(zombie.getAttributeValue(Attributes.ARMOR) == 4D && zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 1D,
                "破甲 III 后护甲应 10 → 4、韧性 4 → 1，实际 " + zombie.getAttributeValue(Attributes.ARMOR) + " / " + zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        helper.runAfterDelay(5, () -> {
            helper.assertFalse(zombie.hasEffect(ECEffects.ARMOR_SHRED.get()), "破甲应已到期");
            helper.assertTrue(armor.getModifier(ArmorShredEffect.ARMOR_MODIFIER) == null && toughness.getModifier(ArmorShredEffect.TOUGHNESS_MODIFIER) == null,
                    "到期以后两个修饰符都应摘掉");
            helper.assertTrue(zombie.getAttributeValue(Attributes.ARMOR) == 10D && zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 4D,
                    "到期以后护甲、韧性应复原，实际 " + zombie.getAttributeValue(Attributes.ARMOR) + " / " + zombie.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            helper.succeed();
        });
    }

    /** 二分：带二分的攻击者（近战、射箭都算射手）打出的伤害减半；带二分的目标受到的治疗减半；没带的照常 */
    @GameTest(template = EMPTY)
    public static void effectsBisectedHalvesDamageAndHealing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie plain = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
        Zombie halved = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 1, 0));
        halved.addEffect(new MobEffectInstance(ECEffects.BISECTED.get(), 200));
        Pig hitByPlain = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(0, 1, 2));
        Pig hitByHalved = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 2));
        Pig shotByHalved = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(4, 1, 2));

        hitByPlain.hurt(level.damageSources().mobAttack(plain), 6F);
        helper.assertTrue(hitByPlain.getHealth() == hitByPlain.getMaxHealth() - 6F, "对照：没带二分的攻击者照常打 6 点，实际剩 " + hitByPlain.getHealth());
        hitByHalved.hurt(level.damageSources().mobAttack(halved), 6F);
        helper.assertTrue(hitByHalved.getHealth() == hitByHalved.getMaxHealth() - 3F, "带二分的攻击者近战应只打 3 点，实际剩 " + hitByHalved.getHealth());
        Arrow arrow = new Arrow(level, halved);
        shotByHalved.hurt(level.damageSources().arrow(arrow, halved), 6F);
        helper.assertTrue(shotByHalved.getHealth() == shotByHalved.getMaxHealth() - 3F, "带二分的射手射出的箭应只打 3 点，实际剩 " + shotByHalved.getHealth());

        Pig healed = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 4));
        Pig healedPlain = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(0, 1, 4));
        healed.addEffect(new MobEffectInstance(ECEffects.BISECTED.get(), 200));
        healed.setHealth(4F);
        healedPlain.setHealth(4F);
        healed.heal(4F);
        healedPlain.heal(4F);
        helper.assertTrue(healed.getHealth() == 6F, "带二分的目标 heal(4) 应只回 2 点，实际 " + healed.getHealth());
        helper.assertTrue(healedPlain.getHealth() == 8F, "对照：没带二分的照常回 4 点，实际 " + healedPlain.getHealth());
        helper.succeed();
    }

    /** 锚定：末影人的随机瞬移被取消（不动）；对照的末影人瞬移事件到最低优先级时没被取消 */
    @GameTest(template = EMPTY)
    public static void effectsAnchoredStopsEnderTeleport(GameTestHelper helper) {
        EnderMan anchored = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(1, 1, 1));
        EnderMan free = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, new BlockPos(3, 1, 3));
        anchored.addEffect(new MobEffectInstance(ECEffects.ANCHORED.get(), 200));
        Method teleport = ObfuscationReflectionHelper.findMethod(EnderMan.class, ENDERMAN_RANDOM_TELEPORT);
        Map<LivingEntity, int[]> seen = new HashMap<>();   // [事件数, 到这里时已被取消的数]
        Consumer<EntityTeleportEvent.EnderEntity> watch = event -> {
            LivingEntity entity = event.getEntityLiving();
            if (entity == anchored || entity == free) {
                int[] count = seen.computeIfAbsent(entity, e -> new int[2]);
                count[0]++;
                if (event.isCanceled()) {
                    count[1]++;
                }
                event.setCanceled(true);
            }
        };
        Vec3 before = anchored.position();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTeleportEvent.EnderEntity.class, watch);
        try {
            for (int i = 0; i < 64; i++) {
                teleport.invoke(anchored);
                teleport.invoke(free);
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("调不到末影人的随机瞬移：" + e);
            return;
        } finally {
            MinecraftForge.EVENT_BUS.unregister(watch);
        }
        int[] a = seen.getOrDefault(anchored, new int[2]);
        int[] f = seen.getOrDefault(free, new int[2]);
        helper.assertTrue(a[0] > 0 && a[1] == a[0], "锚定的末影人每次瞬移都应被取消，实际事件 " + a[0] + "、已取消 " + a[1]);
        helper.assertTrue(f[0] > 0 && f[1] == 0, "对照：没锚定的末影人瞬移不应被本模组取消，实际事件 " + f[0] + "、已取消 " + f[1]);
        helper.assertTrue(anchored.position().equals(before), "锚定的末影人不应动，实际 " + before + " → " + anchored.position());
        helper.succeed();
    }

    /**
     * 锚定：带锚定的玩家扔的珍珠落地不传送、珍珠照常用掉（同原版事件被取消时）；带锚定的僵尸吃紫颂果不传送。
     * 对照组（没锚定）的事件到最低优先级时没被取消。珍珠走真的飞行和落地（逐刻 tick 到撞上方块）；玩家的连接改成「收消息」，
     * 否则原版在发事件之前就跳过了（测试玩家没有网络通道）
     */
    @GameTest(template = EMPTY)
    public static void effectsAnchoredStopsPearlsAndChorus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer anchored = pearlThrower(helper, "ec5-eff-anchor", new Vec3(0.5D, 1D, 0.5D));
        ServerPlayer free = pearlThrower(helper, "ec5-eff-free", new Vec3(4.5D, 1D, 0.5D));
        anchored.addEffect(new MobEffectInstance(ECEffects.ANCHORED.get(), 200));
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);

        Map<LivingEntity, int[]> seen = new HashMap<>();
        Consumer<EntityTeleportEvent.EnderPearl> watchPearl = event -> {
            if (event.getPlayer() == anchored || event.getPlayer() == free) {
                note(seen, event.getPlayer(), event.isCanceled());
                event.setCanceled(true);
            }
        };
        Vec3 before = anchored.position();
        ThrownEnderpearl anchoredPearl = dropPearl(helper, anchored, new Vec3(1.5D, 3.4D, 3.5D));
        ThrownEnderpearl freePearl = dropPearl(helper, free, new Vec3(3.5D, 3.4D, 3.5D));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTeleportEvent.EnderPearl.class, watchPearl);
        try {
            for (int i = 0; i < 40 && !(anchoredPearl.isRemoved() && freePearl.isRemoved()); i++) {
                if (!anchoredPearl.isRemoved()) {
                    anchoredPearl.tick();
                }
                if (!freePearl.isRemoved()) {
                    freePearl.tick();
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(watchPearl);
        }
        helper.assertTrue(anchoredPearl.isRemoved() && freePearl.isRemoved(), "两颗珍珠都应落地用掉");
        int[] a = seen.getOrDefault(anchored, new int[2]);
        int[] f = seen.getOrDefault(free, new int[2]);
        helper.assertTrue(a[0] == 1 && a[1] == 1, "锚定的玩家的珍珠落地事件应被取消，实际事件 " + a[0] + "、已取消 " + a[1]);
        helper.assertTrue(f[0] == 1 && f[1] == 0, "对照：没锚定的玩家的珍珠不应被本模组取消，实际事件 " + f[0] + "、已取消 " + f[1]);
        helper.assertTrue(anchored.position().equals(before), "锚定的玩家不应被传走，实际 " + before + " → " + anchored.position());

        Zombie anchoredZombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie freeZombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        anchoredZombie.addEffect(new MobEffectInstance(ECEffects.ANCHORED.get(), 200));
        Consumer<EntityTeleportEvent.ChorusFruit> watchChorus = event -> {
            LivingEntity entity = event.getEntityLiving();
            if (entity == anchoredZombie || entity == freeZombie) {
                note(seen, entity, event.isCanceled());
                event.setCanceled(true);
            }
        };
        Vec3 zombieBefore = anchoredZombie.position();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityTeleportEvent.ChorusFruit.class, watchChorus);
        try {
            Items.CHORUS_FRUIT.finishUsingItem(new ItemStack(Items.CHORUS_FRUIT), level, anchoredZombie);
            Items.CHORUS_FRUIT.finishUsingItem(new ItemStack(Items.CHORUS_FRUIT), level, freeZombie);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(watchChorus);
        }
        int[] az = seen.getOrDefault(anchoredZombie, new int[2]);
        int[] fz = seen.getOrDefault(freeZombie, new int[2]);
        helper.assertTrue(az[0] == 1 && az[1] == 1, "锚定的僵尸吃紫颂果应被取消（取消后不再试），实际事件 " + az[0] + "、已取消 " + az[1]);
        helper.assertTrue(fz[0] == 1 && fz[1] == 0, "对照：没锚定的僵尸吃紫颂果不应被本模组取消，实际事件 " + fz[0] + "、已取消 " + fz[1]);
        helper.assertTrue(anchoredZombie.position().equals(zombieBefore), "锚定的僵尸不应被传走");
        helper.succeed();
    }

    /** 牛奶清掉四种效果，破甲的修饰符跟着摘掉 */
    @GameTest(template = EMPTY)
    public static void effectsMilkClearsAll(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        List<MobEffect> effects = List.of(ECEffects.CORROSION.get(), ECEffects.ARMOR_SHRED.get(), ECEffects.BISECTED.get(), ECEffects.ANCHORED.get());
        double armorBefore = zombie.getAttributeValue(Attributes.ARMOR);
        for (MobEffect effect : effects) {
            zombie.addEffect(new MobEffectInstance(effect, 600));
        }
        for (MobEffect effect : effects) {
            helper.assertTrue(zombie.hasEffect(effect), "测试前提：僵尸身上有 " + ForgeRegistries.MOB_EFFECTS.getKey(effect));
        }
        helper.assertTrue(zombie.getAttributeValue(Attributes.ARMOR) < armorBefore, "测试前提：破甲 I 扣了护甲");
        Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), helper.getLevel(), zombie);
        for (MobEffect effect : effects) {
            helper.assertFalse(zombie.hasEffect(effect), "牛奶应清掉 " + ForgeRegistries.MOB_EFFECTS.getKey(effect));
        }
        helper.assertTrue(zombie.getAttributeValue(Attributes.ARMOR) == armorBefore, "清掉破甲以后护甲应复原，实际 " + zombie.getAttributeValue(Attributes.ARMOR));
        helper.succeed();
    }

    /** 效果标签 unfilterable：辐射、MRU 腐化、心智悖论、锚定在里面；腐蚀、破甲、二分不在（矩阵过滤要能拦它们） */
    @GameTest(template = EMPTY)
    public static void effectsUnfilterableTag(GameTestHelper helper) {
        ITagManager<MobEffect> tags = ForgeRegistries.MOB_EFFECTS.tags();
        if (tags == null) {
            helper.fail("效果注册表没有标签管理器");
            return;
        }
        for (String id : List.of("radiation", "mru_corruption", "mindfold_paradox", "anchored")) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(EssentialCraft5.id(id));
            helper.assertTrue(effect != null && tags.getTag(ECEffects.UNFILTERABLE).contains(effect), "unfilterable 应含 essentialcraft5:" + id);
        }
        for (String id : List.of("corrosion", "armor_shred", "bisected")) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(EssentialCraft5.id(id));
            helper.assertTrue(effect != null && !tags.getTag(ECEffects.UNFILTERABLE).contains(effect), "unfilterable 不应含 essentialcraft5:" + id);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------

    private static void note(Map<LivingEntity, int[]> seen, LivingEntity entity, boolean canceled) {
        int[] count = seen.computeIfAbsent(entity, e -> new int[2]);
        count[0]++;
        if (canceled) {
            count[1]++;
        }
    }

    /**
     * 扔珍珠的测试玩家：同 {@link ProtectionFrameworkTests#realPlayer}，但连接说自己「收消息」——原版珍珠落地先看
     * {@code connection.isAcceptingMessages()}，测试玩家没有网络通道，不改的话根本不发传送事件。发出去的包照旧只进队列
     */
    private static ServerPlayer pearlThrower(GameTestHelper helper, String name, Vec3 relativeFeet) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player) {
            @Override
            public boolean isAcceptingMessages() {
                return true;
            }
        };
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** owner 扔出的珍珠，放在 relative 处、往下落 */
    private static ThrownEnderpearl dropPearl(GameTestHelper helper, ServerPlayer owner, Vec3 relative) {
        ThrownEnderpearl pearl = new ThrownEnderpearl(helper.getLevel(), owner);
        Vec3 at = helper.absoluteVec(relative);
        pearl.moveTo(at.x, at.y, at.z, 0F, 0F);
        pearl.setDeltaMovement(0D, -0.5D, 0D);
        return pearl;
    }
}
