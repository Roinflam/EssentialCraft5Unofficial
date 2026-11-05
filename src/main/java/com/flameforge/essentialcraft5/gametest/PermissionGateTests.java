package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.corruption.CorruptionBehaviors;
import com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.function.Consumer;

/**
 * 第十批权限修补里不归单个模块测试类的几条：戒指槽默认 2 格（本模组的 Curios 槽位数据）、σ 反弹要等原来那一下真的落下（A5）、
 * 腐化 #13 的爆炸不碰本人伤不到的玩家（A8）。
 * 别的几条在各模块的测试类里：线圈只打闯入者、只打主人能动的格子里的怪（A1 / A6，{@code TeslaTests}）、恶魔清怪（A6，{@code DemonTests}）、
 * 太阳棱镜只烧敌对生物与末影产能器不碰起了名字的末影人（A2 / A7，{@code SolarTests}）、扩散器有害效果只给敌对生物（A3，{@code DevicesTests}）、
 * 磁化法杖不吸别人领地里的掉落物（A4，{@code StaffsTests}）。
 * 改全局状态的（σ 的掷骰、服务器 PvP 开关）只在同一段同步代码里设、测、还原。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class PermissionGateTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 挨爆炸的真玩家生命上限（普通难度贴脸 43 点，别把它炸死） */
    private static final float VICTIM_HEALTH = 200F;

    private PermissionGateTests() {
    }

    private static Player mockPlayer(GameTestHelper helper, double x, double y, double z) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static ICurioStacksHandler rings(GameTestHelper helper, Player player) {
        ICurioStacksHandler handler = CuriosApi.getCuriosInventory(player).resolve()
                .map(inventory -> inventory.getCurios().get(CuriosCompat.RING)).orElse(null);
        if (handler == null) {
            helper.fail("玩家没有 Curios 戒指槽");
        }
        return handler;
    }

    // ------------------------------------------------------------------ 戒指槽

    /**
     * 戒指槽默认 2 格（{@code data/essentialcraft5/curios/slots/ring.json}，原作 Baubles 两个戒指位；Curios 自己的 ring 不写 size，默认 1 格）；
     * 项链、腰带仍是 Curios 默认的 1 格；玩家身上的戒指栏真有 2 格，两格各戴一枚不同的特殊戒指都生效。
     * 植物魔法自带 {@code data/botania/curios/slots/ring.json}（operation ADD，+1 格），挂上它跑 MRU 转换器魔力那组测试时是 3 格
     */
    @GameTest(template = EMPTY)
    public static void curiosRingSlotsDefaultTwo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int ring = CuriosApi.getSlot(CuriosCompat.RING, level).map(ISlotType::getSize).orElse(-1);
        int necklace = CuriosApi.getSlot(CuriosCompat.NECKLACE, level).map(ISlotType::getSize).orElse(-1);
        int belt = CuriosApi.getSlot(CuriosCompat.BELT, level).map(ISlotType::getSize).orElse(-1);
        int expected = 2 + (ModList.get().isLoaded("botania") ? 1 : 0);
        helper.assertTrue(ring == expected, "戒指槽应默认 " + expected + " 格，实际 " + ring);
        helper.assertTrue(necklace == 1 && belt == 1, "项链、腰带不改（各 1 格），实际 " + necklace + " / " + belt);

        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        ICurioStacksHandler handler = rings(helper, player);
        helper.assertTrue(handler.getSlots() == expected, "玩家身上应有 " + expected + " 个戒指格，实际 " + handler.getSlots());
        helper.assertTrue(CuriosCompat.RING.equals(ECBaubles.TAU_RING.get().slot()) && CuriosCompat.RING.equals(ECBaubles.PSI_RING.get().slot()),
                "测试前提：τ、ψ 都是戒指");
        handler.getStacks().setStackInSlot(0, new ItemStack(ECBaubles.TAU_RING.get()));
        handler.getStacks().setStackInSlot(1, new ItemStack(ECBaubles.PSI_RING.get()));
        CuriosCompat.invalidate(player);
        helper.assertTrue(CuriosCompat.has(player, ECBaubles.TAU_RING.get()) && CuriosCompat.has(player, ECBaubles.PSI_RING.get()),
                "两格各戴一枚，两枚都应生效");
        helper.succeed();
    }

    // ------------------------------------------------------------------ σ（A5）

    /**
     * A5：σ 的反弹等原来那一下真的落到佩戴者身上才打回去（本 tick 末 {@code flushSigma}）。
     * 落下了：佩戴者免伤、攻击者当场不挨、结算时挨原样的 4 点；
     * 没落下（只发了 {@code LivingHurtEvent}、伤害流程没走完——混合端的插件在 LivingHurtEvent 之后拦下原伤害就是这样）：
     * 这一下照样被 σ 改成 0，攻击者不挨反弹。掷骰用测试覆盖值固定成必中。
     * 另外几条：
     * <ul>
     *   <li>别的模组排在 σ 后面（LOWEST、运行时注册的监听器）取消原伤害的 {@code LivingHurtEvent}：真走一遍 {@code hurt}，纯 Forge 照样记最近一次伤害，
     *       但事件最后被取消了，不反。</li>
     *   <li>混合端那条信号（按混合端的顺序手动发 {@code LivingHurtEvent} → {@code LivingDamageEvent(0)}，不走 {@code hurt}、不记最近一次伤害）：反 1 下；
     *       换一个伤害源对象、或发给没戴 σ 的人的 {@code LivingDamageEvent(0)} 不反。</li>
     *   <li>混合端举盾整下挡掉（{@code LivingHurtEvent} → {@code ShieldBlockEvent} → {@code LivingDamageEvent(0)}）：不反。</li>
     * </ul>
     */
    @GameTest(template = EMPTY)
    public static void sigmaReflectsOnlyLandedHits(GameTestHelper helper) {
        Player attacker = mockPlayer(helper, 1.5D, 1D, 1.5D);
        Player wearer = mockPlayer(helper, 2.5D, 1D, 1.5D);
        rings(helper, wearer).getStacks().setStackInSlot(0, new ItemStack(ECBaubles.SIGMA_RING.get()));
        CuriosCompat.invalidate(wearer);
        BaubleCombatEvents.setSigmaRollForTest(0F);
        try {
            BaubleCombatEvents.flushSigma();
            wearer.hurt(wearer.damageSources().playerAttack(attacker), 4F);
            helper.assertTrue(wearer.getHealth() == wearer.getMaxHealth(), "σ 触发时自己免伤，实际 " + wearer.getHealth());
            helper.assertTrue(attacker.getHealth() == attacker.getMaxHealth(), "反弹不在当场打");
            int landed = BaubleCombatEvents.flushSigma();
            helper.assertTrue(landed == 1 && attacker.getHealth() == attacker.getMaxHealth() - 4F,
                    "落下了的那一下应在结算时原样反回 4 点，反了 " + landed + " 下、攻击者剩 " + attacker.getHealth());

            attacker.setHealth(attacker.getMaxHealth());
            attacker.invulnerableTime = 0;
            DamageSource blocked = wearer.damageSources().playerAttack(attacker);
            LivingHurtEvent event = new LivingHurtEvent(wearer, blocked, 4F);
            MinecraftForge.EVENT_BUS.post(event);
            helper.assertTrue(event.getAmount() == 0F, "σ 照样把这一下改成 0，实际 " + event.getAmount());
            int notLanded = BaubleCombatEvents.flushSigma();
            helper.assertTrue(notLanded == 0 && attacker.getHealth() == attacker.getMaxHealth(),
                    "原伤害没落下（被拦在 LivingHurtEvent 之后）时不该反弹，反了 " + notLanded + " 下");

            // 排在 σ 后面的模组取消了原伤害：真走 hurt（纯 Forge 照样记最近一次伤害），但 LivingHurtEvent 最后是取消的
            DamageSource canceled = wearer.damageSources().playerAttack(attacker);
            Consumer<LivingHurtEvent> lateCancel = e -> {
                if (e.getEntity() == wearer && e.getSource() == canceled) {
                    e.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, lateCancel);
            try {
                wearer.invulnerableTime = 0;
                wearer.hurt(canceled, 4F);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(lateCancel);
            }
            helper.assertTrue(wearer.getLastDamageSource() == canceled, "测试前提：纯 Forge 的 hurt 照样把它记成最近一次伤害");
            int afterCancel = BaubleCombatEvents.flushSigma();
            helper.assertTrue(afterCancel == 0 && attacker.getHealth() == attacker.getMaxHealth(),
                    "原伤害的 LivingHurtEvent 在 σ 之后被取消时不该反弹，反了 " + afterCancel + " 下、攻击者剩 " + attacker.getHealth());

            // 混合端的信号：LivingHurtEvent → LivingDamageEvent(0)，不走 hurt、不记最近一次伤害
            DamageSource hybrid = wearer.damageSources().playerAttack(attacker);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(wearer, hybrid, 4F));
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(wearer, hybrid, 0F));
            helper.assertTrue(wearer.getLastDamageSource() != hybrid, "测试前提：这一下没记成最近一次伤害");
            int hybridLanded = BaubleCombatEvents.flushSigma();
            helper.assertTrue(hybridLanded == 1 && attacker.getHealth() == attacker.getMaxHealth() - 4F,
                    "只来了 LivingDamageEvent(0)（混合端放行）也应反 1 下，反了 " + hybridLanded + " 下、攻击者剩 " + attacker.getHealth());

            // 别的伤害源对象、没戴 σ 的人的 LivingDamageEvent(0) 不算
            attacker.setHealth(attacker.getMaxHealth());
            attacker.invulnerableTime = 0;
            Player plain = mockPlayer(helper, 3.5D, 1D, 1.5D);
            DamageSource pendingSource = wearer.damageSources().playerAttack(attacker);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(wearer, pendingSource, 4F));
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(wearer, wearer.damageSources().playerAttack(attacker), 0F));
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(plain, pendingSource, 0F));
            int strangers = BaubleCombatEvents.flushSigma();
            helper.assertTrue(strangers == 0 && attacker.getHealth() == attacker.getMaxHealth(),
                    "别的伤害源对象、别人的 LivingDamageEvent 不该让这一下算落下，反了 " + strangers + " 下");

            // 混合端举盾整下挡掉：LivingHurtEvent → ShieldBlockEvent → LivingDamageEvent(0)
            DamageSource shielded = wearer.damageSources().playerAttack(attacker);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(wearer, shielded, 4F));
            MinecraftForge.EVENT_BUS.post(new ShieldBlockEvent(wearer, shielded, 0F));
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(wearer, shielded, 0F));
            int blockedByShield = BaubleCombatEvents.flushSigma();
            helper.assertTrue(blockedByShield == 0 && attacker.getHealth() == attacker.getMaxHealth(),
                    "被盾牌整下挡掉的一下不该反弹，反了 " + blockedByShield + " 下");
        } finally {
            BaubleCombatEvents.setSigmaRollForTest(null);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 腐化 #13（A8）

    /** 要挨爆炸的真玩家：先空跑 61 刻去掉出生无敌，生命上限调到 200；脚站在 relativeFeet */
    private static ServerPlayer victim(GameTestHelper helper, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH);
        player.setHealth(VICTIM_HEALTH);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 放进世界的旁观者（模拟玩家没有出生无敌；真玩家那种 ServerPlayer 不在世界里，爆炸查不到），生命上限 200（别被炸死），速度清零 */
    private static Player bystander(GameTestHelper helper, Vec3 relativeFeet) {
        Player player = mockPlayer(helper, relativeFeet.x, relativeFeet.y, relativeFeet.z);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH);
        player.setHealth(VICTIM_HEALTH);
        player.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(player);
        return player;
    }

    /**
     * A8：腐化 #13 的爆炸不碰本人伤不到的玩家。判定本身：服务器关了 PvP 时别的玩家不在爆炸里（温和不温和都一样），本人、牛照常；
     * 真炸一次：PvP 关着时旁边的玩家不掉血、不被推开（纯 Forge 上原版爆炸不管伤害成没成都会推人）；PvP 开着时照常被炸、被推开（对照）
     */
    @GameTest(template = EMPTY_LARGE)
    public static void corruptionBlastSkipsUnharmablePlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer self = victim(helper, new Vec3(5.5D, 1D, 5.5D));
        ServerPlayer other = ProtectionFrameworkTests.realPlayer(helper);
        Cow cow = EntityType.COW.create(level);
        IPlayerData data = PlayerDataEvents.get(self);
        CorruptionEffects.Effect blast = CorruptionEffects.byId("matrix_13");
        helper.assertTrue(cow != null && data != null && blast != null, "测试前提：牛、玩家数据、#13 都在");
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(false);
            helper.assertFalse(CorruptionBehaviors.blastMayHit(self, other, false) || CorruptionBehaviors.blastMayHit(self, other, true),
                    "PvP 关着时别的玩家不在爆炸里");
            helper.assertTrue(CorruptionBehaviors.blastMayHit(self, cow, false) && CorruptionBehaviors.blastMayHit(self, cow, true),
                    "牛照常在爆炸里");
            Player spared = bystander(helper, new Vec3(7.5D, 1D, 5.5D));
            try {
                float before = spared.getHealth();
                helper.assertTrue(CorruptionBehaviors.trigger(self, data, blast), "#13 应发作");
                helper.assertTrue(spared.getHealth() == before && spared.getDeltaMovement().lengthSqr() < 1.0E-8D,
                        "PvP 关着时旁边的玩家不该掉血、被推开，实际生命 " + spared.getHealth() + " 速度 " + spared.getDeltaMovement());
            } finally {
                spared.discard();
            }

            server.setPvpAllowed(true);
            helper.assertTrue(CorruptionBehaviors.blastMayHit(self, other, false), "PvP 开着时别的玩家照常在爆炸里");
            self.setHealth(VICTIM_HEALTH);
            Player hit = bystander(helper, new Vec3(7.5D, 1D, 5.5D));
            try {
                float before = hit.getHealth();
                helper.assertTrue(CorruptionBehaviors.trigger(self, data, blast), "#13 应发作");
                helper.assertTrue(hit.getHealth() < before && hit.getDeltaMovement().lengthSqr() > 1.0E-6D,
                        "对照：PvP 开着时旁边的玩家照常被炸、被推开，实际生命 " + hit.getHealth() + " 速度 " + hit.getDeltaMovement());
            } finally {
                hit.discard();
            }
        } finally {
            server.setPvpAllowed(pvp);
        }
        helper.succeed();
    }
}
