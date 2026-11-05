package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents;
import com.flameforge.essentialcraft5.item.bauble.SpecialBaubleItem;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.util.ECCombatTags;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 交手记录 {@link ECCombatTags}（饰品封禁场的门「最近交过手」、相位锚模块的「互相交过手」）的 GameTest。
 * <p>
 * 只有「被放行」的玩家之间的伤害才记：拦在 {@code LivingAttackEvent} 的（Forge 端领地、PvP 模组的拦法）、PvP 关着打不出来的、没有攻击者的、
 * 假玩家打的都不记；放行以后被挡掉、被取消的（盾牌、MRU 盔甲、别的模组在 {@code LivingHurtEvent} 里取消）照样记，纯 Forge 和混合端一致。混合端插件取消 Bukkit 伤害事件的那一种在纯 Forge 上测不到，由混合端自检 C36 守着。
 * 挨打后自动打回去的反伤（宅男全套、σ）、{@code hurt} 收到的原值就是 0 的（雪球）不算对方动手。
 * 玩家都是 {@link ProtectionFrameworkTests#realPlayer}，先空跑 61 刻去掉出生无敌；每条测试收尾删掉自己的记录（不清空整张表，并行的测试互不影响）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CombatTagTests {

    private static final String EMPTY = "empty";
    private static final int WINDOW = 200;

    private CombatTagTests() {
    }

    /** 挨得了打的真玩家（去掉 60 刻出生无敌，满血） */
    private static ServerPlayer fighter(GameTestHelper helper, String name, double x) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static int now(GameTestHelper helper) {
        return helper.getLevel().getServer().getTickCount();
    }

    /** a 近战打 b 一下（2 点） */
    private static boolean hit(ServerPlayer a, ServerPlayer b) {
        b.invulnerableTime = 0;
        return b.hurt(a.damageSources().playerAttack(a), 2F);
    }

    private static void forget(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            ECCombatTags.forget(player.getUUID());
        }
    }

    /** 打中一下：两个方向的 landedEither 都真、双方都打过才 inMutualCombat；对方回打一下就成立 */
    @GameTest(template = EMPTY)
    public static void combatTagRecordsLandedHits(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = fighter(helper, "ct-hit-a", 0.5D);
        ServerPlayer b = fighter(helper, "ct-hit-b", 2.5D);
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            helper.assertTrue(hit(a, b), "前提：A 打中 B");
            int now = now(helper);
            helper.assertTrue(ECCombatTags.landedEither(a, b, now, WINDOW) && ECCombatTags.landedEither(b, a, now, WINDOW), "A 打中 B 后两个方向都算交过手");
            helper.assertFalse(ECCombatTags.inMutualCombat(a, b, now, WINDOW), "只有一方动过手，不算互相交过手");
            helper.assertTrue(hit(b, a), "前提：B 回打一下");
            helper.assertTrue(ECCombatTags.inMutualCombat(a, b, now, WINDOW), "B 回打以后双方都交过手");
        } finally {
            server.setPvpAllowed(pvp);
            forget(a, b);
        }
        helper.succeed();
    }

    /** 攻击事件被拦（Forge 端领地 / PvP 模组）：不记 */
    @GameTest(template = EMPTY)
    public static void combatTagIgnoresCanceledAttack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = fighter(helper, "ct-atk-a", 0.5D);
        ServerPlayer b = fighter(helper, "ct-atk-b", 2.5D);
        Consumer<LivingAttackEvent> block = event -> {
            if (event.getEntity() == b) {
                event.setCanceled(true);
            }
        };
        boolean pvp = server.isPvpAllowed();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, block);
        try {
            server.setPvpAllowed(true);
            helper.assertFalse(hit(a, b), "前提：这一下被拦了");
            helper.assertFalse(ECCombatTags.landedEither(a, b, now(helper), WINDOW), "被拦下的攻击不算交手");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(block);
            server.setPvpAllowed(pvp);
            forget(a, b);
        }
        helper.succeed();
    }

    /**
     * 纯 Forge：攻击放行了、受伤事件被后面的处理取消（伤害没落下、不发 LivingDamageEvent）——也算交手，和混合端一致
     * （混合端在 LivingHurtEvent 被取消时按 0 照发 Bukkit 事件，插件放行就发 LivingDamageEvent(0)，第十批 10c 统一）
     */
    @GameTest(template = EMPTY)
    public static void combatTagCountsCanceledHurt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = fighter(helper, "ct-hurt-a", 0.5D);
        ServerPlayer b = fighter(helper, "ct-hurt-b", 2.5D);
        Consumer<LivingHurtEvent> block = event -> {
            if (event.getEntity() == b) {
                event.setCanceled(true);
            }
        };
        boolean pvp = server.isPvpAllowed();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingHurtEvent.class, block);
        try {
            server.setPvpAllowed(true);
            RayHitGate.setHybridForTest(false);
            hit(a, b);
            helper.assertTrue(b.getHealth() == b.getMaxHealth(), "前提：B 没掉血");
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) >= 0, "攻击放行、受伤事件被取消的一下也算 A 打过 B");
            helper.assertTrue(ECCombatTags.lastLanded(b.getUUID(), a.getUUID()) < 0, "B 没还手");
        } finally {
            RayHitGate.setHybridForTest(null);
            MinecraftForge.EVENT_BUS.unregister(block);
            server.setPvpAllowed(pvp);
            forget(a, b);
        }
        helper.succeed();
    }

    /**
     * 纯 Forge：排在交手记录后面（同为最低优先级、登记得晚）的监听器才取消受伤事件——交手记录走到时伤害还在，记下事件对象，
     * 本刻末（{@code flushPending}）看到最后被取消了照样补记；没被取消、照常落下的由 LivingDamageEvent 记，本刻末不重复
     */
    @GameTest(template = EMPTY)
    public static void combatTagCountsLateCanceledHurt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = fighter(helper, "ct-late-a", 0.5D);
        ServerPlayer b = fighter(helper, "ct-late-b", 2.5D);
        ServerPlayer c = fighter(helper, "ct-late-c", 4.5D);
        Consumer<LivingHurtEvent> lateBlock = event -> {
            if (event.getEntity() == b) {
                event.setCanceled(true);
            }
        };
        boolean pvp = server.isPvpAllowed();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, lateBlock);
        try {
            server.setPvpAllowed(true);
            RayHitGate.setHybridForTest(false);
            ECCombatTags.flushPending();
            hit(a, b);
            helper.assertTrue(b.getHealth() == b.getMaxHealth(), "前提：B 没掉血");
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) < 0, "交手记录走到时伤害还在，当场不记");
            helper.assertTrue(hit(a, c), "前提：A 打中 C");
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), c.getUUID()) >= 0, "照常落下的一下由 LivingDamageEvent 当场记");
            ECCombatTags.forget(c.getUUID());
            helper.assertTrue(ECCombatTags.flushPending() == 1, "本刻末只补记被取消的那一下");
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) >= 0, "本刻末看到受伤事件最后被取消，补记 A 打过 B");
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), c.getUUID()) < 0, "照常落下的那一下本刻末不再记一遍");
        } finally {
            RayHitGate.setHybridForTest(null);
            MinecraftForge.EVENT_BUS.unregister(lateBlock);
            server.setPvpAllowed(pvp);
            forget(a, b, c);
        }
        helper.succeed();
    }

    /**
     * 混合端：受伤事件被取消时不在 LivingHurtEvent 里记（插件还没问过），本刻末也不补；插件放行后发来的 LivingDamageEvent(0) 才记
     * （这里手动发一个模拟放行）
     */
    @GameTest(template = EMPTY)
    public static void combatTagHybridWaitsForDamageEvent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = fighter(helper, "ct-hyb-a", 0.5D);
        ServerPlayer b = fighter(helper, "ct-hyb-b", 2.5D);
        Consumer<LivingHurtEvent> block = event -> {
            if (event.getEntity() == b) {
                event.setCanceled(true);
            }
        };
        boolean pvp = server.isPvpAllowed();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingHurtEvent.class, block);
        try {
            server.setPvpAllowed(true);
            RayHitGate.setHybridForTest(true);
            ECCombatTags.flushPending();
            DamageSource source = a.damageSources().playerAttack(a);
            b.invulnerableTime = 0;
            b.hurt(source, 2F);
            ECCombatTags.flushPending();
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) < 0, "混合端上插件还没放行，不记");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(b, source, 0F));
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) >= 0, "插件放行后的 LivingDamageEvent(0) 记下");
        } finally {
            RayHitGate.setHybridForTest(null);
            MinecraftForge.EVENT_BUS.unregister(block);
            server.setPvpAllowed(pvp);
            forget(a, b);
        }
        helper.succeed();
    }

    /** 没有攻击者的伤害（线圈放电、魔法）、假玩家打的：不记 */
    @GameTest(template = EMPTY)
    public static void combatTagIgnoresNonPlayerDamage(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = fighter(helper, "ct-src-a", 0.5D);
        ServerPlayer b = fighter(helper, "ct-src-b", 2.5D);
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            b.invulnerableTime = 0;
            b.hurt(ECDamageTypes.mruDischarge(helper.getLevel()), 2F);
            b.invulnerableTime = 0;
            b.hurt(b.damageSources().magic(), 2F);
            b.invulnerableTime = 0;
            b.hurt(b.damageSources().playerAttack(fake), 2F);
            helper.assertTrue(b.getHealth() < b.getMaxHealth(), "前提：B 挨了这几下");
            int now = now(helper);
            helper.assertFalse(ECCombatTags.landedEither(a, b, now, WINDOW), "A 没动手");
            helper.assertTrue(ECCombatTags.lastLanded(fake.getUUID(), b.getUUID()) < 0, "假玩家打的不记");
        } finally {
            server.setPvpAllowed(pvp);
            forget(a, b);
            ECCombatTags.forget(fake.getUUID());
        }
        helper.succeed();
    }

    /** PvP 关着：打不出伤害，也就不记 */
    @GameTest(template = EMPTY)
    public static void combatTagPvpOff(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = fighter(helper, "ct-pvp-a", 0.5D);
        ServerPlayer b = fighter(helper, "ct-pvp-b", 2.5D);
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(false);
            helper.assertFalse(hit(a, b), "前提：PvP 关着打不出伤害");
            helper.assertFalse(ECCombatTags.landedEither(a, b, now(helper), WINDOW), "PvP 关着不记");
        } finally {
            server.setPvpAllowed(pvp);
            forget(a, b);
        }
        helper.succeed();
    }

    /** 过期：记在 t，到 t + 窗口还算、t + 窗口 + 1 不算；比现在还晚的（重启后刻数从 0 算）不算 */
    @GameTest(template = EMPTY)
    public static void combatTagExpires(GameTestHelper helper) {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ServerPlayer pa = ProtectionFrameworkTests.realPlayer(helper, a, "ct-exp-a");
        ServerPlayer pb = ProtectionFrameworkTests.realPlayer(helper, b, "ct-exp-b");
        int t = now(helper);
        try {
            ECCombatTags.record(a, b, t);
            helper.assertTrue(ECCombatTags.landedEither(pa, pb, t + WINDOW, WINDOW), "到窗口末尾还算");
            helper.assertFalse(ECCombatTags.landedEither(pa, pb, t + WINDOW + 1, WINDOW), "过了窗口不算");
            helper.assertFalse(ECCombatTags.landedEither(pa, pb, t - 1, WINDOW), "记录比现在还晚的不算");
        } finally {
            forget(pa, pb);
        }
        helper.succeed();
    }

    /** 下线：作为攻击者、作为受害者的记录都删掉 */
    @GameTest(template = EMPTY)
    public static void combatTagForgetsOnLogout(GameTestHelper helper) {
        ServerPlayer a = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ct-out-a");
        ServerPlayer b = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ct-out-b");
        int t = now(helper);
        try {
            ECCombatTags.record(a, b, t);
            ECCombatTags.record(b, a, t);
            helper.assertTrue(ECCombatTags.inMutualCombat(a, b, t, WINDOW), "前提：互相交过手");
            ECCombatTags.onCombatTagLogout(new PlayerEvent.PlayerLoggedOutEvent(b));
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) < 0 && ECCombatTags.lastLanded(b.getUUID(), a.getUUID()) < 0,
                    "B 下线后两个方向的记录都没了");
        } finally {
            forget(a, b);
        }
        helper.succeed();
    }

    /** 举着盾、面朝攻击者的真玩家（只把「正在格挡」这一个状态写死，格挡本身走原版 hurt；去掉出生无敌、满血） */
    private static ServerPlayer blocker(GameTestHelper helper, String name, double x, float yaw) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public boolean isBlocking() {
                return true;
            }
        };
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, 0.5D));
        player.moveTo(at.x, at.y, at.z, yaw, 0F);
        player.setYHeadRot(yaw);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /**
     * 纯 Forge：盾牌整下挡掉的一下（放行了、伤害变成 0，到不了 LivingDamageEvent）也算交手，和混合端放行后发 LivingDamageEvent(0) 一致。
     * 只断言 A → B 这一个方向（B 一下都没还手）
     */
    @GameTest(template = EMPTY)
    public static void combatTagCountsShieldBlock(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = fighter(helper, "ct-shd-a", 0.5D);
        // 站在 A 的东边、面朝西（yaw 90 朝 -x）对着 A
        ServerPlayer b = blocker(helper, "ct-shd-b", 2.5D, 90F);
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            RayHitGate.setHybridForTest(false);
            hit(a, b);
            helper.assertTrue(b.getHealth() == b.getMaxHealth(), "前提：盾牌整下挡掉，B 没掉血");
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) >= 0, "盾牌整下挡掉的一下也算 A 打过 B");
            helper.assertTrue(ECCombatTags.lastLanded(b.getUUID(), a.getUUID()) < 0, "B 没还手");
        } finally {
            RayHitGate.setHybridForTest(null);
            server.setPvpAllowed(pvp);
            forget(a, b);
        }
        helper.succeed();
    }

    /**
     * 纯 Forge：被满电的 MRU 盔甲整下挡掉的一下（LivingHurtEvent 被取消、不发 LivingDamageEvent）也算交手。
     * 第十三批起宅男护盾挨玩家打时默认最多挡一半（batch13.md D12），这里把 {@code pvp_shield_max_share} 临时调回 1，才挡得满一整下
     */
    @GameTest(template = EMPTY)
    public static void combatTagCountsMRUShieldAbsorb(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = fighter(helper, "ct-mru-a", 0.5D);
        ServerPlayer b = fighter(helper, "ct-mru-b", 2.5D);
        for (var piece : ECComputer.armor()) {
            ComputerArmorItem item = piece.get();
            ItemStack stack = new ItemStack(item);
            stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(1_000_000));
            b.setItemSlot(item.getType().getSlot(), stack);
        }
        b.setHealth(b.getMaxHealth());
        float before = b.getHealth();
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            RayHitGate.setHybridForTest(false);
            ECComputer.Config.setAttunedTestOverrides(ECComputer.Config.ATTUNED_DEFAULTS.toBuilder().pvp(1D, 1D).build());
            hit(a, b);
            helper.assertTrue(b.getHealth() == before, "前提：MRU 盔甲整下挡掉，B 没掉血（" + before + " → " + b.getHealth() + "）");
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) >= 0, "MRU 盔甲整下挡掉的一下也算 A 打过 B");
            // 宅男全套的反伤以 B 的名义打了 A 一下：反伤不算 B 动过手
            helper.assertTrue(a.getHealth() < a.getMaxHealth(), "前提：B 的套装反伤打到了 A");
            helper.assertTrue(ECCombatTags.lastLanded(b.getUUID(), a.getUUID()) < 0, "反伤不算 B 打过 A");
            helper.assertFalse(ECCombatTags.inMutualCombat(a, b, now(helper), WINDOW), "B 一下都没还手，不算互相交过手");
        } finally {
            ECComputer.Config.setAttunedTestOverrides(null);
            RayHitGate.setHybridForTest(null);
            server.setPvpAllowed(pvp);
            forget(a, b);
        }
        helper.succeed();
    }

    /** σ 的反弹（本刻末以佩戴者的名义打回去，伤害类型和普通近战一样）：不算 B 动过手；A → B 那一下照记 */
    @GameTest(template = EMPTY)
    public static void combatTagIgnoresSigmaReflect(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = fighter(helper, "ct-sig-a", 0.5D);
        ServerPlayer b = fighter(helper, "ct-sig-b", 2.5D);
        SpecialBaubleItem sigma = ECBaubles.SIGMA_RING.get();
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            CuriosApi.getCuriosInventory(b).resolve().orElseThrow().setEquippedCurio(sigma.slot(), 0, new ItemStack(sigma));
            CuriosCompat.invalidate(b);
            BaubleCombatEvents.setSigmaRollForTest(0F);
            BaubleCombatEvents.flushSigma();
            hit(a, b);
            helper.assertTrue(b.getHealth() == b.getMaxHealth(), "前提：σ 必中，B 免伤");
            helper.assertTrue(BaubleCombatEvents.flushSigma() == 1 && a.getHealth() < a.getMaxHealth(), "前提：σ 在结算时反弹打到了 A");
            ECCombatTags.flushPending();
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) >= 0, "A 打 B 那一下照记");
            helper.assertTrue(ECCombatTags.lastLanded(b.getUUID(), a.getUUID()) < 0, "σ 的反弹不算 B 打过 A");
        } finally {
            BaubleCombatEvents.setSigmaRollForTest(null);
            CuriosApi.getCuriosInventory(b).resolve().ifPresent(handler -> handler.setEquippedCurio(sigma.slot(), 0, ItemStack.EMPTY));
            CuriosCompat.invalidate(b);
            server.setPvpAllowed(pvp);
            forget(a, b);
        }
        helper.succeed();
    }

    /**
     * {@code hurt} 收到的原值就是 0 的（雪球、鸡蛋、末影珍珠砸中玩家）不算交手：纯 Forge 的 {@code Player.hurt} 对 0 直接返回、什么都不发；
     * 混合端不滤 0，插件放行后照发 {@code LivingDamageEvent(0)}——按混合端的顺序手动发 {@code LivingAttackEvent(0)} → {@code LivingDamageEvent(0)}，不记。
     * 对照：攻击事件的原值大于 0、到伤害事件变成 0（盾牌、MRU 盔甲整下挡掉）的照记
     */
    @GameTest(template = EMPTY)
    public static void combatTagIgnoresZeroInput(GameTestHelper helper) {
        ServerPlayer a = fighter(helper, "ct-zero-a", 0.5D);
        ServerPlayer b = fighter(helper, "ct-zero-b", 2.5D);
        ServerPlayer c = fighter(helper, "ct-zero-c", 4.5D);
        Snowball snowball = new Snowball(helper.getLevel(), a);
        try {
            RayHitGate.setHybridForTest(true);
            DamageSource thrown = a.damageSources().thrown(snowball, a);
            MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(b, thrown, 0F));
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(b, thrown, 0F));
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) < 0, "原值是 0 的雪球不算 A 打过 B");

            DamageSource blocked = a.damageSources().playerAttack(a);
            MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(c, blocked, 4F));
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(c, blocked, 0F));
            helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), c.getUUID()) >= 0, "对照：原值 4、整下挡掉以后的 LivingDamageEvent(0) 照记");
        } finally {
            RayHitGate.setHybridForTest(null);
            snowball.discard();
            forget(a, b, c);
        }
        helper.succeed();
    }

    /**
     * 正式环境只靠本刻末自动结算（{@code ServerTickEvent} END 的 {@code onCombatTagServerTick}）：纯 Forge 上排在交手记录后面才取消的受伤事件，
     * 不手动 {@code flushPending}，下一刻已经补记了
     */
    @GameTest(template = EMPTY)
    public static void combatTagFlushesAtTickEnd(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer a = fighter(helper, "ct-tick-a", 0.5D);
        ServerPlayer b = fighter(helper, "ct-tick-b", 2.5D);
        Consumer<LivingHurtEvent> lateBlock = event -> {
            if (event.getEntity() == b) {
                event.setCanceled(true);
            }
        };
        boolean pvp = server.isPvpAllowed();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, lateBlock);
        try {
            server.setPvpAllowed(true);
            RayHitGate.setHybridForTest(false);
            hit(a, b);
        } finally {
            RayHitGate.setHybridForTest(null);
            MinecraftForge.EVENT_BUS.unregister(lateBlock);
            server.setPvpAllowed(pvp);
        }
        if (b.getHealth() != b.getMaxHealth() || ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) >= 0) {
            forget(a, b);
            helper.fail("前提：受伤事件在交手记录之后才被取消，当场没记、B 没掉血");
            return;
        }
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(ECCombatTags.lastLanded(a.getUUID(), b.getUUID()) >= 0, "本刻末自动补记了 A 打过 B");
            } finally {
                forget(a, b);
            }
            helper.succeed();
        });
    }

    /** 上限：一名攻击者最多记 32 个受害者，第 33 个进来时丢掉最旧的 */
    @GameTest(template = EMPTY)
    public static void combatTagCapsPerAttacker(GameTestHelper helper) {
        UUID attacker = UUID.randomUUID();
        UUID[] victims = new UUID[ECCombatTags.MAX_PER_ATTACKER + 1];
        int t = now(helper);
        try {
            for (int i = 0; i < victims.length; i++) {
                victims[i] = UUID.randomUUID();
                ECCombatTags.record(attacker, victims[i], t + i);
            }
            helper.assertTrue(ECCombatTags.lastLanded(attacker, victims[0]) < 0, "最旧的那条被丢掉");
            for (int i = 1; i < victims.length; i++) {
                helper.assertTrue(ECCombatTags.lastLanded(attacker, victims[i]) == t + i, "第 " + i + " 条还在");
            }
        } finally {
            ECCombatTags.forget(attacker);
        }
        helper.succeed();
    }
}
