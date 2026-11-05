package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import com.flameforge.essentialcraft5.compat.curio.CurioSealConfig;
import com.flameforge.essentialcraft5.compat.curio.CurioSealMixinStatus;
import com.flameforge.essentialcraft5.compat.curio.CurioSlotCaches;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.ComputerSealField;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.SealNotifier;
import com.flameforge.essentialcraft5.network.C2SComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.FieldState;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.TargetState;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.util.ECCombatTags;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.OwnerCellMemory;
import com.mojang.authlib.GameProfile;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 饰品封禁场（第十批 10a-M1，tools/plan/batch10.md §5.1.10）的 GameTest：场的评估、门（PvP / 队伍、最近交过手、领地）、先算后扣、提示、
 * 最少保持、多个场、缓降、终端包，以及解除压制时清按格缓存的反射调用。
 * <p>
 * 玩家都是 {@link ProtectionFrameworkTests#realPlayer}（真 ServerPlayer，不进世界），候选人列表自己传给 {@link ComputerSealField#evaluate}；
 * 时钟用压制表的 {@link CurioSeal#now}，跨好几次评估的直接把「现在」往后推（evaluate、resolveForTest、交手记录都按传进去的刻算），
 * 所以每条测试都在同一刻里做完、收尾时撤掉压制和交手记录（并行的别的测试看不到这些假时刻）。
 * 配置用 {@link CurioSealConfig#setTestOverrides}，同一段同步代码里设、测、置回。
 * 交手记录多半直接 {@link ECCombatTags#record}（「真的打一下才记」由 {@link CombatTagTests} 守着）；
 * {@link #sealFieldNeedsRecentFight} 用真伤害走一遍：插件 / 领地模组拦下的伤害不算交手。
 * 测试玩家不进世界、没有追踪关系，要断言终端名单的先 {@link ComputerSealField#trackForTest} 登记；「追踪着、看得见才进名单」由
 * {@link #sealFieldListsOnlyVisible} 走事件总线守着。
 * 探测减量（被拒的格子记 5 分钟、按人沿用「没有权限」、付不起不探测、每次评估最多新探测 4 格）的几条用 {@link #evaluateFresh}：
 * ECProtection 自己的缓存按真实刻数过期，测试在同一刻里推进假时刻，每次评估前清掉它，看的是场自己的缓存。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CurioSealFieldTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "curio_seal_field";
    private static final int FULL = 1_000_000;

    private CurioSealFieldTests() {
    }

    // ================================================================ 工具

    private static void requireAvailable(GameTestHelper helper) {
        helper.assertTrue(CurioSeal.availability() == CurioSeal.Availability.AVAILABLE,
                "压制核心应可用，实际 " + CurioSeal.availability() + " " + CurioSealMixinStatus.problems());
    }

    /** 站在测试区 (x, 1, z) 的真玩家（站在地上：免得压制开始时被当成在空中给缓降） */
    private static ServerPlayer player(GameTestHelper helper, String name, double x, double z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        move(helper, player, x, z);
        player.setOnGround(true);
        return player;
    }

    private static void move(GameTestHelper helper, ServerPlayer player, double x, double z) {
        Vec3 at = helper.absoluteVec(new Vec3(x, 1D, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
    }

    /** 挨得了打的真玩家（新建的 ServerPlayer 有 60 刻出生无敌，只在 ServerPlayer.tick 里减，先空跑 61 次） */
    private static ServerPlayer hittable(GameTestHelper helper, String name, double x, double z) {
        ServerPlayer player = player(helper, name, x, z);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        move(helper, player, x, z);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** 穿齐四件宅男套装（胸甲存 chestMru，其余空），返回胸甲 */
    private static ItemStack suit(ServerPlayer player, int chestMru) {
        ItemStack chest = ItemStack.EMPTY;
        for (var piece : ECComputer.armor()) {
            ComputerArmorItem item = piece.get();
            ItemStack stack = new ItemStack(item);
            if (item.getType() == ArmorItem.Type.CHESTPLATE) {
                stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(chestMru));
                chest = stack;
            }
            player.setItemSlot(item.getType().getSlot(), stack);
        }
        return chest;
    }

    /** 穿全套、开着场的穿戴者，站在 (0.5, 0.5) */
    private static ServerPlayer wearer(GameTestHelper helper, String name, int chestMru) {
        requireAvailable(helper);
        ServerPlayer wearer = player(helper, name, 0.5D, 0.5D);
        suit(wearer, chestMru);
        ComputerSealField.setOn(wearer, true);
        return wearer;
    }

    private static long now(GameTestHelper helper) {
        return CurioSeal.now(helper.getLevel().getServer());
    }

    private static void fought(ServerPlayer a, ServerPlayer b, long tick) {
        ECCombatTags.record(a, b, (int) tick);
    }

    private static int mru(ItemStack stack) {
        return MRUStaffItem.storedMRU(stack);
    }

    private static void setMru(ItemStack stack, int mru) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
    }

    private static void resolve(long now, ServerPlayer... players) {
        CurioSeal.resolveForTest(now, players);
    }

    private static void cleanup(ServerPlayer... players) {
        CurioSealConfig.setTestOverrides(null);
        for (ServerPlayer player : players) {
            ComputerSealField.forgetForTest(player);
            SealNotifier.stopCapture(player.getUUID());
        }
        for (ServerPlayer player : players) {
            CurioSeal.releaseForTest(player);
            ECCombatTags.forget(player.getUUID());
        }
    }

    /** 打开服务器 PvP（canHarmPlayer 要它），返回原来的值，测试收尾时 {@link #restorePvp} 放回 */
    private static boolean pvpOn(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean before = server.isPvpAllowed();
        server.setPvpAllowed(true);
        return before;
    }

    private static void restorePvp(GameTestHelper helper, boolean before) {
        helper.getLevel().getServer().setPvpAllowed(before);
    }

    /** 这组测试多半是在「要交过手」那道门开着时写的（默认关，sealFieldDefaultNoCombatGate 守着默认值），这里统一打开 */
    private static CurioSealConfig.Builder config() {
        return CurioSealConfig.DEFAULTS.toBuilder().requireRecentCombat(true);
    }

    private static long count(List<SealNotifier.Sent> sent, String key) {
        synchronized (sent) {
            return sent.stream().filter(entry -> entry.key().equals(key)).count();
        }
    }

    // ================================================================ 1：门

    /**
     * 门：A（交过手）压制；B（同队、不许友伤）不能互相伤害；C（脚下那格被领地拒了）没有权限；D 创造、E 旁观不受影响；
     * F（33 格）、G（别的维度）不在名单里；H（没交过手）不压、对 H 那格一次探测都没发；I（穿戴者先动手）压制。
     * 压制 2 人、自己也失效，扣 200 + 200 × 2
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldGates(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer w = wearer(helper, "sf-gate-w", FULL);
        ItemStack chest = w.getItemBySlot(EquipmentSlot.CHEST);
        ServerPlayer a = player(helper, "sf-gate-a", 3.5D, 0.5D);
        ServerPlayer b = player(helper, "sf-gate-b", 0.5D, 3.5D);
        ServerPlayer c = player(helper, "sf-gate-c", -2.5D, 0.5D);
        ServerPlayer d = player(helper, "sf-gate-d", 2.5D, 2.5D);
        ServerPlayer e = player(helper, "sf-gate-e", -1.5D, 2.5D);
        ServerPlayer f = player(helper, "sf-gate-f", 33.5D, 0.5D);
        ServerPlayer h = player(helper, "sf-gate-h", -2.5D, 3.5D);
        ServerPlayer i = player(helper, "sf-gate-i", 3.5D, 3.5D);
        ServerLevel nether = server.getLevel(Level.NETHER);
        ServerPlayer g = null;
        if (nether != null) {
            g = new ServerPlayer(server, nether, new GameProfile(UUID.randomUUID(), "sf-gate-g"));
            new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), g);
            g.moveTo(w.getX() + 1D, w.getY(), w.getZ(), 0F, 0F);
        }
        d.setGameMode(GameType.CREATIVE);
        e.setGameMode(GameType.SPECTATOR);
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("ec5sealgate");
        team.setAllowFriendlyFire(false);
        scoreboard.addPlayerToTeam(w.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(b.getScoreboardName(), team);
        boolean pvp = server.isPvpAllowed();
        long now = now(helper);
        List<ServerPlayer> all = new ArrayList<>(List.of(w, a, b, c, d, e, f, h, i));
        if (g != null) {
            all.add(g);
        }
        ComputerSealField.Result result;
        int hBreaks;
        int cBreaks;
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyAt(c.blockPosition())) {
            server.setPvpAllowed(true);
            CurioSealConfig.setTestOverrides(config().build());
            fought(a, w, now);
            for (ServerPlayer target : new ServerPlayer[]{b, c, d, e, f, i}) {
                fought(w, target, now);
            }
            if (g != null) {
                fought(w, g, now);
            }
            result = ComputerSealField.evaluate(w, all, now);
            resolve(now, w, a, i, h, c);
            hBreaks = claims.breaksAt(h.blockPosition());
            cBreaks = claims.breaksAt(c.blockPosition());
            helper.assertTrue(result.state() == FieldState.RUNNING, "有人可压制时应运转，实际 " + result.state());
            helper.assertTrue(result.statusOf(a) == TargetState.SEALED, "A（他先动手）应压制，实际 " + result.statusOf(a));
            helper.assertTrue(result.statusOf(b) == TargetState.CANT_HARM, "B（同队不许友伤）应「不能互相伤害」，实际 " + result.statusOf(b));
            helper.assertTrue(result.statusOf(c) == TargetState.NO_PERMISSION, "C（脚下被领地拒了）应「没有权限」，实际 " + result.statusOf(c));
            helper.assertTrue(result.statusOf(d) == TargetState.EXEMPT_MODE && result.statusOf(e) == TargetState.EXEMPT_MODE,
                    "创造、旁观不受影响，实际 " + result.statusOf(d) + " / " + result.statusOf(e));
            helper.assertTrue(result.statusOf(f) == null, "33 格外的不在名单里");
            if (g != null) {
                helper.assertTrue(result.statusOf(g) == null, "别的维度的不在名单里");
            }
            helper.assertTrue(result.statusOf(h) == TargetState.NO_FIGHT, "H（没交过手）应「没有交手」，实际 " + result.statusOf(h));
            helper.assertTrue(result.statusOf(i) == TargetState.SEALED, "I（穿戴者先动手）应压制，实际 " + result.statusOf(i));
            helper.assertTrue(result.sealed().size() == 2 && result.sealed().contains(a) && result.sealed().contains(i),
                    "这一秒只压制 A、I，实际 " + result.sealed().size() + " 人");
            helper.assertTrue(result.selfSealed(), "运转时穿戴者自己也失效");
            helper.assertTrue(result.cost() == 600 && mru(chest) == FULL - 600, "应扣 200 + 200 × 2 = 600，实际 cost " + result.cost()
                    + "，胸甲剩 " + mru(chest));
            helper.assertTrue(CurioSeal.isSealed(a) && CurioSeal.isSealed(i) && CurioSeal.isSealed(w), "结算后 A、I 和穿戴者被压制");
            helper.assertFalse(CurioSeal.isSealed(h) || CurioSeal.isSealed(c), "H、C 不被压制");
            helper.assertTrue(hBreaks == 0, "没交过手的人不探测领地，H 那格收到 " + hBreaks + " 次破坏事件");
            helper.assertTrue(cBreaks >= 1, "交过手的 C 要探测，C 那格应收到破坏事件");
        } finally {
            server.setPvpAllowed(pvp);
            scoreboard.removePlayerTeam(team);
            cleanup(all.toArray(ServerPlayer[]::new));
        }
        helper.succeed();
    }

    // ================================================================ 2：边界

    /** 边界：31 格压制；挪到 33 格仍压制（出 34 格才算离开）；一开始就在 33 格的不算进来；挪到 35 格，过了最少保持后解除 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldBoundary(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-edge-w", FULL);
        ServerPlayer t = player(helper, "sf-edge-t", 31.5D, 0.5D);
        ServerPlayer u = player(helper, "sf-edge-u", 0.5D, 33.5D);
        List<ServerPlayer> all = List.of(w, t, u);
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(config().respectClaims(false).build());
            fought(w, t, now);
            fought(w, u, now);
            ComputerSealField.Result first = ComputerSealField.evaluate(w, all, now);
            resolve(now, w, t, u);
            helper.assertTrue(first.statusOf(t) == TargetState.SEALED && CurioSeal.isSealed(t), "31 格应压制");
            helper.assertTrue(first.statusOf(u) == null && !CurioSeal.isSealed(u), "一开始就在 33 格的不算进来");
            move(helper, t, 33.5D, 0.5D);
            ComputerSealField.Result second = ComputerSealField.evaluate(w, all, now + 20);
            resolve(now + 20, w, t);
            helper.assertTrue(second.statusOf(t) == TargetState.SEALED && CurioSeal.isSealed(t), "已在场内的人到 33 格仍压制");
            move(helper, t, 35.5D, 0.5D);
            ComputerSealField.Result third = ComputerSealField.evaluate(w, all, now + 40);
            resolve(now + 40, w, t);
            helper.assertTrue(third.statusOf(t) == null, "35 格已离开");
            helper.assertTrue(CurioSeal.isSealed(t), "离开后最少保持时间内仍压制");
            resolve(now + CurioSealConfig.DEFAULTS.minHoldTicks(), w, t);
            helper.assertFalse(CurioSeal.isSealed(t), "过了最少保持时间解除");
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, t, u);
        }
        helper.succeed();
    }

    // ================================================================ 3：待机

    /** 待机：范围内没人 → 不扣电、穿戴者不被压制；来一个交过手的人 → 运转、穿戴者也被压制、扣 200 + 200 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldStandby(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-idle-w", FULL);
        ItemStack chest = w.getItemBySlot(EquipmentSlot.CHEST);
        ServerPlayer t = player(helper, "sf-idle-t", 3.5D, 0.5D);
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            ComputerSealField.Result idle = ComputerSealField.evaluate(w, List.of(w), now);
            resolve(now, w);
            helper.assertTrue(idle.state() == FieldState.STANDBY && idle.cost() == 0, "没人时待机、不扣电，实际 " + idle.state());
            helper.assertTrue(mru(chest) == FULL, "待机不扣电，胸甲剩 " + mru(chest));
            helper.assertFalse(CurioSeal.isSealed(w), "待机时不压自己");
            fought(w, t, now);
            ComputerSealField.Result running = ComputerSealField.evaluate(w, List.of(w, t), now);
            resolve(now, w, t);
            helper.assertTrue(running.state() == FieldState.RUNNING && running.cost() == 400, "来一人：运转、扣 400，实际 "
                    + running.state() + " " + running.cost());
            helper.assertTrue(mru(chest) == FULL - 400, "胸甲应扣 400，剩 " + mru(chest));
            helper.assertTrue(CurioSeal.isSealed(w) && CurioSeal.isSealed(t), "运转时目标和穿戴者都被压制");
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    /**
     * 穿戴者自己被不被封看游戏模式（affects_self 开着）：生存模式运转时目标和他自己都被压制；换成创造模式以后照样压制目标，
     * 自己不再被压制（过了最少保持时间解除）、不扣电。创造模式下「别人的格子罩上了、自己的没有」是这条规则，不是客户端没收到。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldCreativeWearerNotSelfSealed(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-crea-w", FULL);
        ItemStack chest = w.getItemBySlot(EquipmentSlot.CHEST);
        ServerPlayer t = player(helper, "sf-crea-t", 3.5D, 0.5D);
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(config().requireRecentCombat(false).respectClaims(false).build());
            ComputerSealField.Result survival = ComputerSealField.evaluate(w, List.of(w, t), now);
            resolve(now, w, t);
            helper.assertTrue(survival.state() == FieldState.RUNNING && survival.selfSealed(), "生存模式：运转、自己也被压制，实际 "
                    + survival.state() + " " + survival.selfSealed());
            helper.assertTrue(CurioSeal.isSealed(w) && CurioSeal.isSealed(t), "生存模式：结算后目标和穿戴者都被压制");
            w.setGameMode(GameType.CREATIVE);
            int before = mru(chest);
            long next = now + 20;
            ComputerSealField.Result creative = ComputerSealField.evaluate(w, List.of(w, t), next);
            resolve(next, w, t);
            helper.assertTrue(creative.state() == FieldState.RUNNING && !creative.selfSealed() && creative.sealed().contains(t),
                    "创造模式：照样压制目标、自己不再登记，实际 " + creative.state() + " " + creative.selfSealed());
            helper.assertTrue(mru(chest) == before, "创造模式不扣电，胸甲从 " + before + " 变成 " + mru(chest));
            // 再评估一次给目标续期（到期刻 = 现在 + 间隔 + HOLD_SLACK），再结算到穿戴者的最少保持时间之后
            ComputerSealField.evaluate(w, List.of(w, t), now + 40);
            resolve(now + 40, w, t);
            resolve(now + CurioSealConfig.DEFAULTS.minHoldTicks(), w, t);
            helper.assertFalse(CurioSeal.isSealed(w), "创造模式的穿戴者过了最少保持时间解除");
            helper.assertTrue(CurioSeal.isSealed(t), "目标还被压制");
        } finally {
            w.setGameMode(GameType.SURVIVAL);
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    // ================================================================ 4：先算后扣

    /** 先算后扣：电不够 → 谁都不压、一分不扣、「电量不足」只提示一次（聊天栏 + 动作栏各一条）；补足后恢复 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldPaysFirst(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-pay-w", 399);
        ItemStack chest = w.getItemBySlot(EquipmentSlot.CHEST);
        ServerPlayer t = player(helper, "sf-pay-t", 3.5D, 0.5D);
        List<SealNotifier.Sent> sent = SealNotifier.captureForTest(w.getUUID());
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            fought(w, t, now);
            ComputerSealField.Result poor = ComputerSealField.evaluate(w, List.of(w, t), now);
            resolve(now, w, t);
            helper.assertTrue(poor.state() == FieldState.NO_POWER && poor.sealed().isEmpty(), "电不够：电量不足、谁都不压，实际 " + poor.state());
            helper.assertTrue(poor.statusOf(t) == TargetState.UNPOWERED, "名单上标「电量不足」");
            helper.assertTrue(mru(chest) == 399, "一分不扣，胸甲剩 " + mru(chest));
            helper.assertFalse(CurioSeal.isSealed(t) || CurioSeal.isSealed(w), "电不够时不压制任何人");
            ComputerSealField.evaluate(w, List.of(w, t), now + 20);
            long told = count(sent, "message.essentialcraft5.computer_seal.no_power");
            helper.assertTrue(told == 2, "电量不足只在进入时提示一次（聊天栏 + 动作栏），实际 " + told + " 条");
            setMru(chest, 1000);
            ComputerSealField.Result rich = ComputerSealField.evaluate(w, List.of(w, t), now + 40);
            resolve(now + 40, w, t);
            helper.assertTrue(rich.state() == FieldState.RUNNING && CurioSeal.isSealed(t), "补足后恢复运转");
            helper.assertTrue(mru(chest) == 600, "补足后扣 400，剩 " + mru(chest));
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    // ================================================================ 5：PvP 关

    /** 服务器 PvP 关着：交过手也不压制（canHarmPlayer 不过） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldPvpOff(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer w = wearer(helper, "sf-pvp-w", FULL);
        ServerPlayer t = player(helper, "sf-pvp-t", 3.5D, 0.5D);
        boolean pvp = server.isPvpAllowed();
        long now = now(helper);
        try {
            server.setPvpAllowed(false);
            fought(w, t, now);
            ComputerSealField.Result result = ComputerSealField.evaluate(w, List.of(w, t), now);
            resolve(now, w, t);
            helper.assertTrue(result.statusOf(t) == TargetState.CANT_HARM, "PvP 关着应「不能互相伤害」，实际 " + result.statusOf(t));
            helper.assertTrue(result.state() == FieldState.STANDBY && !CurioSeal.isSealed(t), "PvP 关着不压制、场待机");
        } finally {
            server.setPvpAllowed(pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    // ================================================================ 6：提示合并与去抖

    /**
     * 提示：同一次评估进来 3 人 → 穿戴者只收到 1 条动作栏；有人离开（隔了 40 刻以上，再提一条）又在 200 刻内回来 → 不提，他再离开也不提。
     * 被压制的一方：每次登录第一次聊天栏说明、之后动作栏 + 一声，解除后 200 刻内又被压制什么都不出
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldNotifierMerges(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-note-w", FULL);
        ServerPlayer t1 = player(helper, "sf-note-1", 3.5D, 0.5D);
        ServerPlayer t2 = player(helper, "sf-note-2", 0.5D, 3.5D);
        ServerPlayer t3 = player(helper, "sf-note-3", -2.5D, 0.5D);
        List<ServerPlayer> all = List.of(w, t1, t2, t3);
        List<SealNotifier.Sent> sent = SealNotifier.captureForTest(w.getUUID());
        String status = "message.essentialcraft5.computer_seal.status";
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(config().respectClaims(false).build());
            fought(w, t1, now);
            fought(w, t2, now);
            fought(w, t3, now);
            ComputerSealField.evaluate(w, all, now);
            helper.assertTrue(count(sent, status) == 1, "同一次进来 3 人只发 1 条，实际 " + count(sent, status));
            helper.assertTrue(sent.get(sent.size() - 1).actionBar(), "进出提示在动作栏");
            move(helper, t1, 40.5D, 0.5D);
            ComputerSealField.evaluate(w, all, now + 60);
            helper.assertTrue(count(sent, status) == 2, "隔了 60 刻有人离开，再提 1 条，实际 " + count(sent, status));
            move(helper, t1, 3.5D, 0.5D);
            ComputerSealField.evaluate(w, all, now + 120);
            helper.assertTrue(count(sent, status) == 2, "离开后 200 刻内回来不提，实际 " + count(sent, status));
            move(helper, t1, 40.5D, 0.5D);
            ComputerSealField.evaluate(w, all, now + 180);
            helper.assertTrue(count(sent, status) == 2, "没提过进来的人再离开也不提，实际 " + count(sent, status));
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, t1, t2, t3);
        }

        SealNotifier.VictimDebounce victim = new SealNotifier.VictimDebounce();
        helper.assertTrue(victim.onSealed(0) == SealNotifier.VictimDebounce.Kind.FIRST, "登录后第一次：聊天栏说明");
        helper.assertTrue(victim.onReleased(100), "解除提示");
        helper.assertTrue(victim.onSealed(400) == SealNotifier.VictimDebounce.Kind.SEALED, "隔了 300 刻再被压制：动作栏 + 一声");
        helper.assertTrue(victim.onReleased(450), "解除提示");
        helper.assertTrue(victim.onSealed(500) == SealNotifier.VictimDebounce.Kind.QUIET, "解除后 50 刻又被压制：不出字");
        helper.assertFalse(victim.onReleased(520), "没出声的那次解除也不出字");
        helper.assertTrue(victim.onSealed(800) == SealNotifier.VictimDebounce.Kind.SEALED, "隔久了照常提示");
        victim.reset();
        helper.assertTrue(victim.onSealed(810) == SealNotifier.VictimDebounce.Kind.FIRST, "重新登录后又是第一次");
        helper.succeed();
    }

    // ================================================================ 7：最少保持

    /** 最少保持：压制后马上离开，60 刻内仍被压制，到 60 刻解除 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldMinHold(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-hold-w", FULL);
        ServerPlayer t = player(helper, "sf-hold-t", 3.5D, 0.5D);
        List<ServerPlayer> all = List.of(w, t);
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            fought(w, t, now);
            ComputerSealField.evaluate(w, all, now);
            resolve(now, w, t);
            helper.assertTrue(CurioSeal.isSealed(t), "前提：压制了");
            move(helper, t, 40.5D, 0.5D);
            ComputerSealField.evaluate(w, all, now + 20);
            resolve(now + 20, w, t);
            helper.assertTrue(CurioSeal.isSealed(t), "离开后 20 刻仍压制");
            resolve(now + 59, w, t);
            helper.assertTrue(CurioSeal.isSealed(t), "59 刻仍压制");
            resolve(now + 60, w, t);
            helper.assertFalse(CurioSeal.isSealed(t), "60 刻解除");
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    // ================================================================ 8：两个场

    /** 两个场：A、B 都压制 T（A 的名单里标「也被 B 压制」）；A 关 → T 仍被压制；B 也关 → 过最少保持后解除 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldTwoFields(GameTestHelper helper) {
        ServerPlayer a = wearer(helper, "sf-two-a", FULL);
        ServerPlayer b = wearer(helper, "sf-two-b", FULL);
        move(helper, b, 4.5D, 0.5D);
        ServerPlayer t = player(helper, "sf-two-t", 2.5D, 2.5D);
        List<ServerPlayer> all = List.of(a, b, t);
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(config().build());
            ComputerSealField.trackForTest(a, b, t);
            fought(a, t, now);
            fought(t, b, now);
            ComputerSealField.evaluate(a, all, now);
            ComputerSealField.Result byB = ComputerSealField.evaluate(b, all, now);
            ComputerSealField.Result byA = ComputerSealField.evaluate(a, all, now);
            resolve(now, a, b, t);
            helper.assertTrue(byB.statusOf(t) == TargetState.SEALED_SHARED || byB.statusOf(t) == TargetState.SEALED, "B 也压制 T");
            helper.assertTrue(byB.statusOf(a) == TargetState.NO_FIGHT, "两个穿戴者没交过手，不互相压制");
            helper.assertTrue(byA.statusOf(t) == TargetState.SEALED_SHARED, "A 的名单里 T 标「也被别人压制」，实际 " + byA.statusOf(t));
            S2CComputerTerminalPacket packet = ComputerSealField.packet(a, true);
            helper.assertTrue(packet.entries().stream().anyMatch(entry -> entry.other().equals(b.getGameProfile().getName())),
                    "A 的终端名单写出 B 的名字");
            helper.assertTrue(CurioSeal.isSealed(t), "T 被压制");
            ComputerSealField.setOn(a, false);
            ComputerSealField.Result off = ComputerSealField.evaluate(a, all, now + 100);
            ComputerSealField.evaluate(b, all, now + 100);
            resolve(now + 100, a, b, t);
            helper.assertTrue(off.state() == FieldState.OFF, "A 关了");
            helper.assertTrue(CurioSeal.isSealed(t), "A 关了以后 T 仍被 B 压制");
            ComputerSealField.setOn(b, false);
            ComputerSealField.evaluate(b, all, now + 120);
            resolve(now + 120, a, b, t);
            helper.assertFalse(CurioSeal.isSealed(t), "两个都关了、过了最少保持时间解除");
        } finally {
            restorePvp(helper, pvp);
            cleanup(a, b, t);
        }
        helper.succeed();
    }

    // ================================================================ 9：缓降

    /** 缓降：压制开始时在空中 → 缓降 160 刻；在地上 → 没有；同一次压制不重复给 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldSlowFall(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-fall-w", FULL);
        ServerPlayer air = player(helper, "sf-fall-a", 3.5D, 0.5D);
        air.setOnGround(false);
        ServerPlayer ground = player(helper, "sf-fall-g", 0.5D, 3.5D);
        List<ServerPlayer> all = List.of(w, air, ground);
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            helper.assertTrue(ComputerSealField.isAirborne(air) && !ComputerSealField.isAirborne(ground), "前提：一个在空中、一个在地上");
            fought(w, air, now);
            fought(w, ground, now);
            ComputerSealField.evaluate(w, all, now);
            resolve(now, w, air, ground);
            MobEffectInstance effect = air.getEffect(MobEffects.SLOW_FALLING);
            helper.assertTrue(effect != null && effect.getDuration() == CurioSealConfig.DEFAULTS.slowFallTicks(),
                    "在空中被压制应得缓降 " + CurioSealConfig.DEFAULTS.slowFallTicks() + " 刻，实际 " + (effect == null ? "没有" : effect.getDuration()));
            helper.assertTrue(ground.getEffect(MobEffects.SLOW_FALLING) == null, "在地上的不给");
            air.removeEffect(MobEffects.SLOW_FALLING);
            ComputerSealField.evaluate(w, all, now + 20);
            resolve(now + 20, w, air, ground);
            helper.assertTrue(air.getEffect(MobEffects.SLOW_FALLING) == null, "同一次压制不重复给");
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, air, ground);
        }
        helper.succeed();
    }

    /**
     * 靠饰品栏里的鞘翅滑翔的人被压制：滑翔停了、人还在空中才给缓降（压制那一刻还在滑翔，先不给）；
     * 胸甲位穿着能飞的鞘翅的照样滑翔，不给；靠别处来的滑翔属性（盔甲、状态效果）滑翔的压制撤不掉，一直滑翔、不给，
     * 过了观察时间才停下的也不给（滑翔时有缓降重力只剩八分之一，被封反而飞得更远）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldSlowFallCurioGlide(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-glide-w", FULL);
        ServerPlayer curio = player(helper, "sf-glide-c", 3.5D, 0.5D);
        ServerPlayer elytra = player(helper, "sf-glide-e", 0.5D, 3.5D);
        ServerPlayer attribute = player(helper, "sf-glide-a", 3.5D, 3.5D);
        elytra.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        for (ServerPlayer glider : List.of(curio, elytra, attribute)) {
            glider.setOnGround(false);
            glider.startFallFlying();
        }
        List<ServerPlayer> all = List.of(w, curio, elytra, attribute);
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            helper.assertTrue(curio.isFallFlying() && !ComputerSealField.isAirborne(curio) && ComputerSealField.glidingOnCurio(curio)
                    && !ComputerSealField.glidingOnCurio(elytra), "前提：都在滑翔，只有没穿鞘翅的算可能靠饰品滑翔");
            for (ServerPlayer target : List.of(curio, elytra, attribute)) {
                fought(w, target, now);
            }
            ComputerSealField.evaluate(w, all, now);
            resolve(now, w, curio, elytra, attribute);
            helper.assertTrue(CurioSeal.isSealed(curio) && CurioSeal.isSealed(elytra) && CurioSeal.isSealed(attribute), "前提：都被压制");
            helper.assertTrue(curio.getEffect(MobEffects.SLOW_FALLING) == null && attribute.getEffect(MobEffects.SLOW_FALLING) == null,
                    "压制那一刻还在滑翔：先不给缓降");
            long t = curio.serverLevel().getGameTime();
            // 饰品的飞行属性撤了，滑翔停下
            curio.stopFallFlying();
            ComputerSealField.followUpGlide(curio, t + 1);
            MobEffectInstance effect = curio.getEffect(MobEffects.SLOW_FALLING);
            helper.assertTrue(effect != null && effect.getDuration() == CurioSealConfig.DEFAULTS.slowFallTicks(),
                    "靠饰品滑翔的滑翔停了应得缓降，实际 " + (effect == null ? "没有" : effect.getDuration()));
            ComputerSealField.followUpGlide(elytra, t + 1);
            helper.assertTrue(elytra.getEffect(MobEffects.SLOW_FALLING) == null, "胸甲位的鞘翅不受压制，不给");
            // 靠别处来的属性滑翔：压制撤不掉，接着滑翔
            ComputerSealField.followUpGlide(attribute, t + 1);
            helper.assertTrue(attribute.getEffect(MobEffects.SLOW_FALLING) == null, "还在滑翔的不给");
            attribute.stopFallFlying();
            ComputerSealField.followUpGlide(attribute, t + ComputerSealField.GLIDE_WATCH_TICKS + 1);
            helper.assertTrue(attribute.getEffect(MobEffects.SLOW_FALLING) == null, "过了观察时间才停下的不给");
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, curio, elytra, attribute);
        }
        helper.succeed();
    }

    // ================================================================ 10：终端包

    /**
     * 终端包：没穿齐时开关被拒、开关不变；穿齐时切换、同一刻第二次不认、发手册进度；关掉不要求穿齐；
     * 刷新没戴头盔不回、戴着回一份带名单的、10 刻内第二次不回；未知动作丢弃
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldTerminalPacket(GameTestHelper helper) {
        requireAvailable(helper);
        ServerPlayer p = player(helper, "sf-term-p", 0.5D, 0.5D);
        ServerPlayer q = player(helper, "sf-term-q", 2.5D, 0.5D);
        C2SComputerTerminalPacket toggle = new C2SComputerTerminalPacket(C2SComputerTerminalPacket.TOGGLE_SEAL);
        C2SComputerTerminalPacket refresh = new C2SComputerTerminalPacket(C2SComputerTerminalPacket.REFRESH);
        List<SealNotifier.Sent> sent = SealNotifier.captureForTest(p.getUUID());
        try {
            ComputerSealField.handleTerminalPacket(p, toggle);
            helper.assertFalse(ComputerSealField.isOn(p), "没穿齐时开不了");
            helper.assertTrue(count(sent, "message.essentialcraft5.computer_seal.incomplete") == 1, "提示要穿齐四件");

            suit(p, FULL);
            ComputerSealField.handleTerminalPacket(p, toggle);
            helper.assertTrue(ComputerSealField.isOn(p), "穿齐后打开");
            Advancement step = helper.getLevel().getServer().getAdvancements().getAdvancement(ComputerSealField.GUIDE_SEAL_FIELD);
            helper.assertTrue(step != null && p.getAdvancements().getOrStartProgress(step).isDone(), "第一次打开发手册进度 guide/computer/seal_field");
            ComputerSealField.handleTerminalPacket(p, toggle);
            helper.assertTrue(ComputerSealField.isOn(p), "同一刻第二次开关不认");

            List<S2CComputerTerminalPacket> qPackets = ComputerSealField.captureTerminalForTest(q.getUUID());
            ComputerSealField.handleTerminalPacket(q, refresh);
            helper.assertTrue(qPackets.isEmpty(), "没戴头盔要数据不回");

            List<S2CComputerTerminalPacket> pPackets = ComputerSealField.captureTerminalForTest(p.getUUID());
            ComputerSealField.handleTerminalPacket(p, refresh);
            ComputerSealField.handleTerminalPacket(p, refresh);
            helper.assertTrue(pPackets.size() == 1 && pPackets.get(0).detail(), "戴着头盔回一份带名单的，10 刻内第二次不回，实际 " + pPackets.size());
            helper.assertTrue(pPackets.get(0).state() == FieldState.STANDBY, "开着、没人时是待机，实际 " + pPackets.get(0).state());

            ComputerSealField.handleTerminalPacket(p, new C2SComputerTerminalPacket((byte) 9));
            helper.assertTrue(ComputerSealField.isOn(p) && pPackets.size() == 1, "未知动作丢弃");

            // 关掉不要求穿齐：脱了胸甲照样关得掉（先清掉开关间隔，等于过了 5 刻）
            ComputerSealField.forgetForTest(p);
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            helper.assertTrue(ComputerSealField.toggle(p) && !ComputerSealField.isOn(p), "没穿齐也能关掉");
        } finally {
            cleanup(p, q);
        }
        helper.succeed();
    }

    // ================================================================ 11 ~ 13：交手

    /**
     * 最近交过手（真伤害）：L 挨了穿戴者一下（放行、打实）→ 压制；J 的攻击事件被拦（Forge 端领地 / PvP 模组的拦法）→ 不算交手；
     * K 的攻击放行了、受伤事件被后面的处理取消 → 也算交手（第十批 10c 起纯 Forge 和混合端一致：混合端在 LivingHurtEvent 被取消时照发
     * LivingDamageEvent(0)），照常走领地探测、压制；H 没交过手；J、H 那格都没收到领地探测
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldNeedsRecentFight(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer w = wearer(helper, "sf-fight-w", FULL);
        ServerPlayer l = hittable(helper, "sf-fight-l", 3.5D, 0.5D);
        ServerPlayer j = hittable(helper, "sf-fight-j", 0.5D, 3.5D);
        ServerPlayer k = hittable(helper, "sf-fight-k", -2.5D, 0.5D);
        ServerPlayer h = hittable(helper, "sf-fight-h", -2.5D, 3.5D);
        List<ServerPlayer> all = List.of(w, l, j, k, h);
        Consumer<LivingAttackEvent> claimAttack = event -> {
            if (event.getEntity() == j && event.getSource().getEntity() == w) {
                event.setCanceled(true);
            }
        };
        Consumer<LivingHurtEvent> claimHurt = event -> {
            if (event.getEntity() == k && event.getSource().getEntity() == w) {
                event.setCanceled(true);
            }
        };
        boolean pvp = server.isPvpAllowed();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, claimAttack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingHurtEvent.class, claimHurt);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            server.setPvpAllowed(true);
            CurioSealConfig.setTestOverrides(config().build());
            for (ServerPlayer target : new ServerPlayer[]{l, j, k}) {
                target.invulnerableTime = 0;
                target.hurt(w.damageSources().playerAttack(w), 2F);
            }
            helper.assertTrue(l.getHealth() < l.getMaxHealth(), "前提：L 真的挨了一下");
            helper.assertTrue(j.getHealth() == j.getMaxHealth() && k.getHealth() == k.getMaxHealth(), "前提：J、K 那一下被拦了");
            long now = now(helper);
            ComputerSealField.Result result = ComputerSealField.evaluate(w, all, now);
            resolve(now, w, l, j, k, h);
            helper.assertTrue(result.statusOf(l) == TargetState.SEALED && CurioSeal.isSealed(l), "L 交过手应压制，实际 " + result.statusOf(l));
            helper.assertTrue(result.statusOf(j) == TargetState.NO_FIGHT, "攻击事件被拦的不算交手，实际 " + result.statusOf(j));
            helper.assertTrue(result.statusOf(k) == TargetState.SEALED && CurioSeal.isSealed(k), "攻击放行、受伤事件被取消的也算交手，应压制，实际 " + result.statusOf(k));
            helper.assertTrue(result.statusOf(h) == TargetState.NO_FIGHT, "没交过手的「没有交手」，实际 " + result.statusOf(h));
            helper.assertFalse(CurioSeal.isSealed(j) || CurioSeal.isSealed(h), "J、H 不压制");
            helper.assertTrue(claims.breaksAt(j.blockPosition()) == 0 && claims.breaksAt(h.blockPosition()) == 0, "没交过手的人不探测领地");
            helper.assertTrue(claims.breaksAt(k.blockPosition()) > 0, "K 算交过手，照常探测他那格");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(claimAttack);
            MinecraftForge.EVENT_BUS.unregister(claimHurt);
            server.setPvpAllowed(pvp);
            cleanup(w, l, j, k, h);
        }
        helper.succeed();
    }

    /** 交手窗口：过了 200 刻不再算交手，再过最少保持就解除；之后对方打穿戴者一下（反方向）又续上 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldCombatWindow(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-win-w", FULL);
        ServerPlayer t = player(helper, "sf-win-t", 3.5D, 0.5D);
        List<ServerPlayer> all = List.of(w, t);
        long now = now(helper);
        int window = CurioSealConfig.DEFAULTS.combatWindowTicks();
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(config().build());
            fought(w, t, now);
            ComputerSealField.evaluate(w, all, now);
            resolve(now, w, t);
            helper.assertTrue(CurioSeal.isSealed(t), "前提：交过手、压制了");
            ComputerSealField.Result stale = ComputerSealField.evaluate(w, all, now + window);
            helper.assertTrue(stale.statusOf(t) == TargetState.SEALED, "刚好 " + window + " 刻时还算交手");
            ComputerSealField.Result expired = ComputerSealField.evaluate(w, all, now + window + 1);
            resolve(now + window + 1, w, t);
            helper.assertTrue(expired.statusOf(t) == TargetState.NO_FIGHT, "过了窗口应「没有交手」，实际 " + expired.statusOf(t));
            helper.assertFalse(CurioSeal.isSealed(t), "过了窗口、也过了最少保持，解除");
            fought(t, w, now + window + 10);
            ComputerSealField.Result renewed = ComputerSealField.evaluate(w, all, now + window + 10);
            resolve(now + window + 10, w, t);
            helper.assertTrue(renewed.statusOf(t) == TargetState.SEALED && CurioSeal.isSealed(t), "对方先动手也算，重新压制");
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    /** 默认不要求交手：没交过手的人也压制（先发制人的玩法）；require_recent_combat 只在服主打开时生效 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldDefaultNoCombatGate(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-nogate-w", FULL);
        ServerPlayer h = player(helper, "sf-nogate-h", 3.5D, 0.5D);
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            helper.assertFalse(CurioSealConfig.DEFAULTS.requireRecentCombat(), "默认不要求交过手");
            CurioSealConfig.setTestOverrides(CurioSealConfig.DEFAULTS);
            ComputerSealField.Result result = ComputerSealField.evaluate(w, List.of(w, h), now);
            resolve(now, w, h);
            helper.assertTrue(result.statusOf(h) == TargetState.SEALED && CurioSeal.isSealed(h), "不要求交手时直接压制，实际 " + result.statusOf(h));
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, h);
        }
        helper.succeed();
    }

    /**
     * 不起作用的维度：名单里带冒号的按完整 id 比、不带的按路径比；在名单里的维度谁都不压、一分不扣，
     * 正在运转的场转「这里不起作用」时撤掉压制、提示一次；默认名单（zc）不碰主世界
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldDisabledDimension(GameTestHelper helper) {
        CurioSealConfig.Values defaults = CurioSealConfig.DEFAULTS;
        helper.assertTrue(defaults.disabledIn(ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", "zc")))
                && defaults.disabledIn(ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("mohist", "zc"))), "默认名单对得上任何命名空间的 zc");
        helper.assertFalse(defaults.disabledIn(ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", "zc2")))
                || defaults.disabledIn(Level.OVERWORLD), "zc2、主世界不在默认名单里");
        ServerPlayer w = wearer(helper, "sf-dim-w", FULL);
        ItemStack chest = w.getItemBySlot(EquipmentSlot.CHEST);
        ServerPlayer t = player(helper, "sf-dim-t", 3.5D, 0.5D);
        List<ServerPlayer> all = List.of(w, t);
        List<SealNotifier.Sent> sent = SealNotifier.captureForTest(w.getUUID());
        String path = w.level().dimension().location().getPath();
        String full = w.level().dimension().location().toString();
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(defaults);
            ComputerSealField.Result running = ComputerSealField.evaluate(w, all, now);
            resolve(now, w, t);
            helper.assertTrue(running.state() == FieldState.RUNNING && CurioSeal.isSealed(t), "默认名单下照常运转，实际 " + running.state());
            int afterRun = mru(chest);

            CurioSealConfig.setTestOverrides(defaults.toBuilder().disabledDimensions(path).build());
            ComputerSealField.Result byPath = ComputerSealField.evaluate(w, all, now + 20);
            resolve(now + 100, w, t);
            helper.assertTrue(byPath.state() == FieldState.DISABLED_WORLD && byPath.sealed().isEmpty() && byPath.cost() == 0,
                    "只写路径「" + path + "」就对得上，实际 " + byPath.state());
            helper.assertFalse(CurioSeal.isSealed(t) || CurioSeal.isSealed(w), "这里不起作用时撤掉压制");
            helper.assertTrue(mru(chest) == afterRun, "这里不起作用时不扣电，剩 " + mru(chest));
            helper.assertTrue(count(sent, "message.essentialcraft5.computer_seal.disabled_world") == 1, "运转中转「这里不起作用」提示一次");
            helper.assertTrue(ComputerSealField.packet(w, false).state() == FieldState.DISABLED_WORLD, "终端状态是「这里不起作用」");

            CurioSealConfig.setTestOverrides(defaults.toBuilder().disabledDimensions(full).build());
            helper.assertTrue(ComputerSealField.evaluate(w, all, now + 120).state() == FieldState.DISABLED_WORLD, "写完整 id 也对得上");
            helper.assertTrue(count(sent, "message.essentialcraft5.computer_seal.disabled_world") == 1, "一直不起作用时不重复提示");

            CurioSealConfig.setTestOverrides(defaults.toBuilder().disabledDimensions("ec5test:" + path).build());
            ComputerSealField.Result other = ComputerSealField.evaluate(w, all, now + 140);
            resolve(now + 140, w, t);
            helper.assertTrue(other.state() == FieldState.RUNNING && CurioSeal.isSealed(t), "命名空间不同的完整 id 对不上，照常运转，实际 " + other.state());
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    /**
     * 在不起作用的维度里打开开关：照样打开（开关跟人走），只提示一条「这里不起作用」、不出「已开启」，终端状态是「这里不起作用」，
     * 评估谁都不压、不再重复提示；名单换掉（等于离开那个维度）以后接着运转、压住目标
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldToggleInDisabledDimension(GameTestHelper helper) {
        requireAvailable(helper);
        ServerPlayer w = player(helper, "sf-dimtog-w", 0.5D, 0.5D);
        suit(w, FULL);
        ServerPlayer t = player(helper, "sf-dimtog-t", 3.5D, 0.5D);
        List<ServerPlayer> all = List.of(w, t);
        List<SealNotifier.Sent> sent = SealNotifier.captureForTest(w.getUUID());
        String path = w.level().dimension().location().getPath();
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(CurioSealConfig.DEFAULTS.toBuilder().disabledDimensions(path).build());
            helper.assertTrue(ComputerSealField.toggle(w) && ComputerSealField.isOn(w), "不起作用的维度里照样打得开");
            helper.assertTrue(count(sent, "message.essentialcraft5.computer_seal.disabled_world") == 1, "打开时提示一条「这里不起作用」");
            helper.assertTrue(count(sent, "message.essentialcraft5.computer_seal.on") == 0, "不出「已开启」");
            helper.assertTrue(ComputerSealField.packet(w, false).state() == FieldState.DISABLED_WORLD,
                    "终端状态是「这里不起作用」，实际 " + ComputerSealField.packet(w, false).state());
            ComputerSealField.Result here = ComputerSealField.evaluate(w, all, now);
            resolve(now, w, t);
            helper.assertTrue(here.state() == FieldState.DISABLED_WORLD && here.sealed().isEmpty() && !CurioSeal.isSealed(t),
                    "在那里评估：这里不起作用、谁都不压，实际 " + here.state());
            helper.assertTrue(count(sent, "message.essentialcraft5.computer_seal.disabled_world") == 1, "一直待在那里不重复提示");

            CurioSealConfig.setTestOverrides(CurioSealConfig.DEFAULTS);
            ComputerSealField.Result away = ComputerSealField.evaluate(w, all, now + 20);
            resolve(now + 20, w, t);
            helper.assertTrue(away.state() == FieldState.RUNNING && CurioSeal.isSealed(t), "离开那个维度就按开着算、接着运转，实际 " + away.state());
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    // ================================================================ 探测减量（policy §2.4：少探测、长缓存）

    /**
     * 被拒的格子按格记 5 分钟：默认配置（不要求交手）、目标一直站在别人领地里，连续评估 15 次（300 刻）那格只收到 1 次破坏事件、每次都「没有权限」。
     * ECProtection 自己的缓存按真实的刻算，这里在同一刻里做完，每次评估前清掉它（等于真过了 20 刻），看的是场自己的缓存
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldDenyRememberedPerCell(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-deny-w", FULL);
        ServerPlayer t = player(helper, "sf-deny-t", 3.5D, 0.5D);
        List<ServerPlayer> all = List.of(w, t);
        BlockPos cell = t.blockPosition();
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyAt(cell)) {
            CurioSealConfig.setTestOverrides(CurioSealConfig.DEFAULTS);
            helper.assertTrue(OwnerCellMemory.DENY_TICKS > 300, "前提：被拒的格子记得比 300 刻久");
            for (int i = 0; i < 15; i++) {
                ECProtection.forget(w.getUUID());
                ComputerSealField.Result result = ComputerSealField.evaluate(w, all, now + 20L * i);
                helper.assertTrue(result.statusOf(t) == TargetState.NO_PERMISSION, "第 " + i + " 次应「没有权限」，实际 " + result.statusOf(t));
                helper.assertTrue(result.state() == FieldState.STANDBY, "没人可压时待机，实际 " + result.state());
            }
            helper.assertTrue(claims.breaksAt(cell) == 1, "300 刻里那格只该收到 1 次破坏事件，实际 " + claims.breaksAt(cell));
            resolve(now + 300, w, t);
            helper.assertFalse(CurioSeal.isSealed(t), "站在别人领地里的不压制");
        } finally {
            ECProtection.forget(w.getUUID());
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    /**
     * 按人沿用「没有权限」：目标在被拒的格子 A 上被拒以后，200 刻内挪到另一个被拒的格子 B 不探测；过了 200 刻在 B 上再探测一次；
     * 之后 200 刻内挪到野外的 C 也不探测、照旧不压（最多晚 10 秒罩住），过了 200 刻探测放行、压制；
     * 放行的结论不沿用：从 C 挪进被拒的 D 马上探测、马上「没有权限」；回到 A 走场的按格缓存，不再探测
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldDenyCarriesPerTarget(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-carry-w", FULL);
        ServerPlayer t = player(helper, "sf-carry-t", 3.5D, 0.5D);
        List<ServerPlayer> all = List.of(w, t);
        BlockPos a = t.blockPosition();
        move(helper, t, 3.5D, 3.5D);
        BlockPos b = t.blockPosition();
        move(helper, t, -2.5D, 0.5D);
        BlockPos c = t.blockPosition();
        move(helper, t, 0.5D, 3.5D);
        BlockPos d = t.blockPosition();
        int carry = ECProtection.Config.probeCacheTicks();
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyAt(a).denyAt(b).denyAt(d)) {
            CurioSealConfig.setTestOverrides(CurioSealConfig.DEFAULTS);
            move(helper, t, 3.5D, 0.5D);
            ComputerSealField.Result atA = evaluateFresh(w, all, now);
            helper.assertTrue(atA.statusOf(t) == TargetState.NO_PERMISSION && claims.breaksAt(a) == 1, "A：探测一次、被拒，实际 " + atA.statusOf(t));

            move(helper, t, 3.5D, 3.5D);
            ComputerSealField.Result toB = evaluateFresh(w, all, now + 20);
            helper.assertTrue(toB.statusOf(t) == TargetState.NO_PERMISSION && claims.breaksAt(b) == 0,
                    "被拒后 " + carry + " 刻内换到 B：沿用、不探测，实际 " + toB.statusOf(t) + "，B 收到 " + claims.breaksAt(b));
            ComputerSealField.Result againB = evaluateFresh(w, all, now + carry + 20);
            helper.assertTrue(againB.statusOf(t) == TargetState.NO_PERMISSION && claims.breaksAt(b) == 1,
                    "过了 " + carry + " 刻在 B 上再探测一次，B 收到 " + claims.breaksAt(b));

            move(helper, t, -2.5D, 0.5D);
            ComputerSealField.Result toC = evaluateFresh(w, all, now + carry + 40);
            helper.assertTrue(toC.statusOf(t) == TargetState.NO_PERMISSION && claims.breaksAt(c) == 0,
                    "刚被拒过、换到野外的 C：照旧不压、不探测，实际 " + toC.statusOf(t) + "，C 收到 " + claims.breaksAt(c));
            long sealAt = now + 2L * carry + 40;
            ComputerSealField.Result atC = evaluateFresh(w, all, sealAt);
            resolve(sealAt, w, t);
            helper.assertTrue(atC.statusOf(t) == TargetState.SEALED && claims.breaksAt(c) == 1 && CurioSeal.isSealed(t),
                    "过了 " + carry + " 刻探测 C、放行、压制，实际 " + atC.statusOf(t));

            move(helper, t, 0.5D, 3.5D);
            ComputerSealField.Result toD = evaluateFresh(w, all, sealAt + 20);
            helper.assertTrue(toD.statusOf(t) == TargetState.NO_PERMISSION && claims.breaksAt(d) == 1,
                    "放行不沿用：进了被拒的 D 马上探测、马上没有权限，实际 " + toD.statusOf(t) + "，D 收到 " + claims.breaksAt(d));
            move(helper, t, 3.5D, 0.5D);
            ComputerSealField.Result backA = evaluateFresh(w, all, sealAt + 40 + carry);
            helper.assertTrue(backA.statusOf(t) == TargetState.NO_PERMISSION && claims.breaksAt(a) == 1,
                    "回到 A：场记着 A 被拒（5 分钟），不再探测，A 收到 " + claims.breaksAt(a));
        } finally {
            ECProtection.forget(w.getUUID());
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    /**
     * 付不起一人份就不探测：胸甲没电时范围里没人 → 待机、不提示电量不足；目标站在被拒的格子上 → 那格 0 次破坏事件、场「电量不足」、
     * 目标「电量不足」，提示一次（聊天栏 + 动作栏）；充上电以后才探测（被拒、待机）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldNoPowerNoProbe(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-nopro-w", 0);
        ItemStack chest = w.getItemBySlot(EquipmentSlot.CHEST);
        ServerPlayer t = player(helper, "sf-nopro-t", 3.5D, 0.5D);
        List<ServerPlayer> all = List.of(w, t);
        BlockPos cell = t.blockPosition();
        List<SealNotifier.Sent> sent = SealNotifier.captureForTest(w.getUUID());
        String noPower = "message.essentialcraft5.computer_seal.no_power";
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyAt(cell)) {
            CurioSealConfig.setTestOverrides(CurioSealConfig.DEFAULTS);
            ComputerSealField.Result alone = evaluateFresh(w, List.of(w), now);
            helper.assertTrue(alone.state() == FieldState.STANDBY && count(sent, noPower) == 0,
                    "范围里没人：待机、不提示电量不足，实际 " + alone.state() + "，提示 " + count(sent, noPower) + " 条");
            ComputerSealField.Result poor = evaluateFresh(w, all, now + 20);
            helper.assertTrue(claims.breaksAt(cell) == 0, "付不起一人份时不探测，那格收到 " + claims.breaksAt(cell) + " 次破坏事件");
            helper.assertTrue(poor.state() == FieldState.NO_POWER && poor.statusOf(t) == TargetState.UNPOWERED,
                    "场「电量不足」、目标「电量不足」，实际 " + poor.state() + " / " + poor.statusOf(t));
            helper.assertTrue(count(sent, noPower) == 2, "电量不足提示一次（聊天栏 + 动作栏），实际 " + count(sent, noPower) + " 条");
            evaluateFresh(w, all, now + 40);
            helper.assertTrue(claims.breaksAt(cell) == 0 && count(sent, noPower) == 2, "一直没电：照旧不探测、不重复提示");
            setMru(chest, FULL);
            ComputerSealField.Result rich = evaluateFresh(w, all, now + 60);
            helper.assertTrue(claims.breaksAt(cell) == 1 && rich.statusOf(t) == TargetState.NO_PERMISSION && rich.state() == FieldState.STANDBY,
                    "充上电以后才探测：被拒、待机，实际 " + rich.statusOf(t) + " / " + rich.state() + "，那格收到 " + claims.breaksAt(cell));
            helper.assertTrue(mru(chest) == FULL, "没压任何人，一分不扣，剩 " + mru(chest));
        } finally {
            ECProtection.forget(w.getUUID());
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    /**
     * 每次评估最多新探测 max_probes_per_tick 格（默认 4）：6 个人各站野外一格，第一次评估探测 4 格、压制 4 人，另 2 人「判定中」；
     * 下一次评估补上那 2 格，6 人都压制，每格各收到 1 次破坏事件
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldProbeBudgetDefault(GameTestHelper helper) {
        helper.assertTrue(CurioSealConfig.DEFAULTS.maxProbesPerTick() == 4, "默认每次评估最多新探测 4 格，实际 " + CurioSealConfig.DEFAULTS.maxProbesPerTick());
        ServerPlayer w = wearer(helper, "sf-budget-w", FULL);
        double[][] spots = {{3.5D, 0.5D}, {0.5D, 3.5D}, {-2.5D, 0.5D}, {0.5D, -2.5D}, {3.5D, 3.5D}, {-2.5D, -2.5D}};
        List<ServerPlayer> targets = new ArrayList<>();
        for (int i = 0; i < spots.length; i++) {
            targets.add(player(helper, "sf-budget-" + i, spots[i][0], spots[i][1]));
        }
        List<ServerPlayer> all = new ArrayList<>(targets);
        all.add(w);
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            CurioSealConfig.setTestOverrides(CurioSealConfig.DEFAULTS);
            ComputerSealField.Result first = evaluateFresh(w, all, now);
            long sealed = targets.stream().filter(p -> first.statusOf(p) == TargetState.SEALED).count();
            long pending = targets.stream().filter(p -> first.statusOf(p) == TargetState.PENDING).count();
            int probed = targets.stream().mapToInt(p -> claims.breaksAt(p.blockPosition())).sum();
            helper.assertTrue(sealed == 4 && pending == 2 && probed == 4, "第一次：探测 4 格、压 4 人、2 人判定中，实际探测 " + probed
                    + "、压 " + sealed + "、判定中 " + pending);
            ComputerSealField.Result second = evaluateFresh(w, all, now + 20);
            resolve(now + 20, all.toArray(ServerPlayer[]::new));
            helper.assertTrue(targets.stream().allMatch(p -> second.statusOf(p) == TargetState.SEALED && CurioSeal.isSealed(p)),
                    "第二次补上另外 2 格，6 人都压制");
            helper.assertTrue(targets.stream().allMatch(p -> claims.breaksAt(p.blockPosition()) == 1), "每格各 1 次破坏事件");
        } finally {
            ECProtection.forget(w.getUUID());
            restorePvp(helper, pvp);
            cleanup(all.toArray(ServerPlayer[]::new));
        }
        helper.succeed();
    }

    /** 评估一次之前先清掉穿戴者在 ECProtection 里的探测缓存（测试在同一刻里推进假时刻，等于它按真实刻数过期了），只看场自己的缓存 */
    private static ComputerSealField.Result evaluateFresh(ServerPlayer w, List<ServerPlayer> all, long at) {
        ECProtection.forget(w.getUUID());
        return ComputerSealField.evaluate(w, all, at);
    }

    // ================================================================ 14：按格缓存

    /** 有自带按格缓存的槽位处理器（有的 Curios 构建有 invalidateCache(int)） */
    public static final class CachingHandler {
        final List<Integer> cleared = new ArrayList<>();

        public void invalidateCache(int slot) {
            cleared.add(slot);
        }
    }

    /** 解除时清按格缓存：找得到 invalidateCache(int) 就调、找不到什么都不做（官方 Curios 没有这个方法） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldResetsSlotCaches(GameTestHelper helper) {
        CachingHandler handler = new CachingHandler();
        helper.assertTrue(CurioSlotCaches.reset(handler, 3), "有这个方法时调用成功");
        helper.assertTrue(handler.cleared.equals(List.of(3)), "清的是第 3 格，实际 " + handler.cleared);
        helper.assertFalse(CurioSlotCaches.reset(new Object(), 0), "没有这个方法时不调");
        ServerPlayer player = player(helper, "sf-cache-p", 0.5D, 0.5D);
        CurioSlotCaches.resetAll(player);
        helper.assertTrue(CurioSlotCaches.reset(new CachingHandler(), 0), "对真饰品栏跑一遍以后（官方 Curios 找不到方法）照样能用");
        helper.succeed();
    }

    // ================================================================ 15 ~ 20：自封、互封、名单只列看得见的人、穿戴者的生命周期、电量

    /**
     * 自己的场压自己：发给本人的来源名里没有自己（名单为空、余数 0，客户端就不出「被封」的提示），服务端的完整来源里照样有；
     * 别的穿戴者也罩上来以后名单里只有他。被压制方的去抖：只被自己的场压着不算一次「被压制」——不占「这次登录第一次」、
     * 解除不出「恢复生效」、不记解除时刻；自封期间别人罩上来按一次新的压制提示
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldSelfSealNames(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-self-w", FULL);
        ServerPlayer a = wearer(helper, "sf-self-a", FULL);
        move(helper, a, 4.5D, 0.5D);
        ServerPlayer t = player(helper, "sf-self-t", 0.5D, 3.5D);
        List<ServerPlayer> all = List.of(w, a, t);
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(config().respectClaims(false).build());
            fought(w, t, now);
            ComputerSealField.Result result = ComputerSealField.evaluate(w, all, now);
            resolve(now, w, a, t);
            helper.assertTrue(result.selfSealed() && CurioSeal.isSealed(w), "前提：W 运转、自己也被压制");
            helper.assertTrue(CurioSeal.sourcesOf(w).containsKey(w.getUUID()), "服务端的完整来源里有 W 自己");
            helper.assertTrue(CurioSeal.sourceNames(w).isEmpty() && CurioSeal.moreSources(w) == 0,
                    "发给 W 的名单不含他自己，实际 " + CurioSeal.sourceNames(w) + " / " + CurioSeal.moreSources(w));
            helper.assertTrue(CurioSeal.sourceNames(t).equals(List.of(w.getGameProfile().getName())), "T 的名单是 W，实际 " + CurioSeal.sourceNames(t));
            fought(a, w, now);
            ComputerSealField.evaluate(a, all, now);
            resolve(now, w, a, t);
            helper.assertTrue(CurioSeal.sourceNames(w).equals(List.of(a.getGameProfile().getName())) && CurioSeal.moreSources(w) == 0,
                    "A 也罩住 W 以后，W 的名单只有 A，实际 " + CurioSeal.sourceNames(w) + " / " + CurioSeal.moreSources(w));
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, a, t);
        }

        SealNotifier.VictimDebounce victim = new SealNotifier.VictimDebounce();
        helper.assertTrue(victim.onSealed(0, true) == SealNotifier.VictimDebounce.Kind.SELF, "只被自己的场压着：不出字");
        helper.assertFalse(victim.onReleased(10), "自封解除不出「恢复生效」");
        helper.assertTrue(victim.onSealed(20, false) == SealNotifier.VictimDebounce.Kind.FIRST, "别人第一次封：这次登录的第一次照常出（自封不占）");
        helper.assertTrue(victim.onReleased(100), "解除提示");
        helper.assertTrue(victim.onSealed(350, true) == SealNotifier.VictimDebounce.Kind.SELF, "又只被自己的场压着");
        helper.assertFalse(victim.onReleased(360), "自封解除不出字");
        helper.assertTrue(victim.onSealed(380, false) == SealNotifier.VictimDebounce.Kind.SEALED,
                "自封的解除不记时刻：距上次真解除（100）已过 280 刻，别人来封照常提示");
        helper.assertTrue(victim.onReleased(400), "解除提示");
        helper.assertTrue(victim.onSealed(700, true) == SealNotifier.VictimDebounce.Kind.SELF, "自封");
        helper.assertTrue(victim.onOthersJoined(710) == SealNotifier.VictimDebounce.Kind.SEALED, "自封期间别人也罩上来：按一次新的压制提示");
        helper.assertTrue(victim.onOthersJoined(720) == null, "提示过了，不再重复");
        helper.assertTrue(victim.onReleased(800), "这次解除照常提示");
        helper.succeed();
    }

    /** 两名穿戴者互相交过手、都开着场：互相压制；名单里对方是「压制中」，不会把对方自己的场当成「也被别人压制」 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldWearersFightEachOther(GameTestHelper helper) {
        ServerPlayer a = wearer(helper, "sf-duel-a", FULL);
        ServerPlayer b = wearer(helper, "sf-duel-b", FULL);
        move(helper, b, 4.5D, 0.5D);
        List<ServerPlayer> all = List.of(a, b);
        String aName = a.getGameProfile().getName();
        String bName = b.getGameProfile().getName();
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(config().respectClaims(false).build());
            ComputerSealField.trackForTest(a, b);
            fought(a, b, now);
            ComputerSealField.evaluate(b, all, now);
            ComputerSealField.Result byA = ComputerSealField.evaluate(a, all, now);
            resolve(now, a, b);
            helper.assertTrue(CurioSeal.sourcesOf(b).containsKey(a.getUUID()) && CurioSeal.sourcesOf(b).containsKey(b.getUUID()),
                    "前提：B 身上有 A 和他自己两份来源");
            helper.assertTrue(byA.statusOf(b) == TargetState.SEALED, "B 自己的场不算「别人」，A 的名单里 B 是「压制中」，实际 " + byA.statusOf(b));
            S2CComputerTerminalPacket packet = ComputerSealField.packet(a, true);
            helper.assertTrue(packet.entries().stream().anyMatch(entry -> entry.name().equals(bName)
                    && entry.state() == TargetState.SEALED.ordinal() && entry.other().isEmpty()), "A 的终端里 B 那一行没有「也被 B 压制」");
            helper.assertTrue(CurioSeal.sourceNames(b).equals(List.of(aName)), "发给 B 的名单只有 A，实际 " + CurioSeal.sourceNames(b));
            helper.assertTrue(CurioSeal.sourceNames(a).equals(List.of(bName)), "发给 A 的名单只有 B，实际 " + CurioSeal.sourceNames(a));
        } finally {
            restorePvp(helper, pvp);
            cleanup(a, b);
        }
        helper.succeed();
    }

    /**
     * 终端名单只列看得见的人（追踪着、没隐身、不是旁观者）：U（没被追踪，例如被插件藏起来的人）、V（隐身）、X（追踪过又停了）照常被压制、
     * 照常算钱，只是名单里没有；S（旁观）不在名单里；D（创造）照列「创造模式」。追踪关系走事件总线的 StartTracking / StopTracking
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldListsOnlyVisible(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-see-w", FULL);
        ServerPlayer t = player(helper, "sf-see-t", 3.5D, 0.5D);
        ServerPlayer u = player(helper, "sf-see-u", 0.5D, 3.5D);
        ServerPlayer v = player(helper, "sf-see-v", -2.5D, 0.5D);
        ServerPlayer x = player(helper, "sf-see-x", 2.5D, 2.5D);
        ServerPlayer s = player(helper, "sf-see-s", -1.5D, 2.5D);
        ServerPlayer d = player(helper, "sf-see-d", -2.5D, 3.5D);
        List<ServerPlayer> all = List.of(w, t, u, v, x, s, d);
        v.setInvisible(true);
        s.setGameMode(GameType.SPECTATOR);
        d.setGameMode(GameType.CREATIVE);
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(config().respectClaims(false).build());
            for (ServerPlayer target : new ServerPlayer[]{t, v, x, s, d}) {
                MinecraftForge.EVENT_BUS.post(new PlayerEvent.StartTracking(w, target));
            }
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.StopTracking(w, x));
            for (ServerPlayer target : new ServerPlayer[]{t, u, v, x, s, d}) {
                fought(w, target, now);
            }
            ComputerSealField.Result result = ComputerSealField.evaluate(w, all, now);
            resolve(now, w, t, u, v, x);
            for (ServerPlayer target : new ServerPlayer[]{t, u, v, x}) {
                helper.assertTrue(result.statusOf(target) == TargetState.SEALED && CurioSeal.isSealed(target),
                        target.getGameProfile().getName() + " 照常过门、被压制，实际 " + result.statusOf(target));
            }
            helper.assertTrue(result.cost() == 200 + 200 * 4, "看不见的人照样算钱（4 人），实际 " + result.cost());
            S2CComputerTerminalPacket packet = ComputerSealField.packet(w, true);
            List<String> names = packet.entries().stream().map(S2CComputerTerminalPacket.Entry::name).toList();
            helper.assertTrue(names.size() == 2 && names.contains(t.getGameProfile().getName()) && names.contains(d.getGameProfile().getName()),
                    "名单只列 T 和 D，实际 " + names);
            helper.assertTrue(packet.entries().stream().anyMatch(entry -> entry.name().equals(d.getGameProfile().getName())
                    && entry.state() == TargetState.EXEMPT_MODE.ordinal()), "D 标「创造模式」");
            helper.assertTrue(packet.sealedOthers() == 4, "运转人数照实是 4，实际 " + packet.sealedOthers());
        } finally {
            v.setInvisible(false);
            restorePvp(helper, pvp);
            cleanup(w, t, u, v, x, s, d);
        }
        helper.succeed();
    }

    /**
     * 穿戴者这一侧的生命周期：死亡（走事件总线，订阅和优先级一起测）、换维度、下线时撤掉他登记的全部压制，过了最少保持被压制的人解除；
     * 开关被外部关掉以后，下一次玩家刻发现了就停场、撤压制、推「关」
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldWearerLifecycle(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-life-w", FULL);
        ServerPlayer t = player(helper, "sf-life-t", 3.5D, 0.5D);
        List<ServerPlayer> all = List.of(w, t);
        long now = now(helper);
        int minHold = CurioSealConfig.DEFAULTS.minHoldTicks();
        long step = minHold + 20L;
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(config().respectClaims(false).build());

            long t1 = now;
            sealOnce(helper, w, t, all, t1);
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(w, w.damageSources().generic()));
            helper.assertFalse(CurioSeal.sourcesOf(t).containsKey(w.getUUID()), "穿戴者死了：撤掉他压制 T 的那份");
            resolve(t1 + minHold, w, t);
            helper.assertFalse(CurioSeal.isSealed(t), "死亡后过了最少保持，T 解除");

            long t2 = t1 + step;
            sealOnce(helper, w, t, all, t2);
            ComputerSealField.onSealFieldChangeDimension(new PlayerEvent.PlayerChangedDimensionEvent(w, Level.OVERWORLD, Level.NETHER));
            helper.assertFalse(CurioSeal.sourcesOf(t).containsKey(w.getUUID()), "穿戴者换维度：撤掉他压制 T 的那份");
            resolve(t2 + minHold, w, t);
            helper.assertFalse(CurioSeal.isSealed(t), "换维度后过了最少保持，T 解除");

            long t3 = t2 + step;
            sealOnce(helper, w, t, all, t3);
            PlayerEvent.PlayerLoggedOutEvent logout = new PlayerEvent.PlayerLoggedOutEvent(w);
            CurioSeal.CurioSealEvents.onCurioSealLogout(logout);
            ComputerSealField.onSealFieldLogout(logout);
            helper.assertFalse(CurioSeal.sourcesOf(t).containsKey(w.getUUID()), "穿戴者下线：撤掉他压制 T 的那份");
            resolve(t3 + minHold, w, t);
            helper.assertFalse(CurioSeal.isSealed(t), "下线后过了最少保持，T 解除");

            long t4 = t3 + step;
            sealOnce(helper, w, t, all, t4);
            List<S2CComputerTerminalPacket> pushed = ComputerSealField.captureTerminalForTest(w.getUUID());
            ComputerSealField.setOn(w, false);
            int interval = ComputerSealField.evaluateIntervalThisTick();
            w.tickCount = interval * 1000 - Math.floorMod(w.getId(), interval);
            ComputerSealField.onSealFieldPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, w));
            helper.assertFalse(CurioSeal.sourcesOf(t).containsKey(w.getUUID()), "开关被关掉：下一次玩家刻撤掉他压制 T 的那份");
            helper.assertTrue(pushed.stream().anyMatch(packet -> packet.state() == FieldState.OFF), "停场时给本人推「关」");
            resolve(t4 + minHold, w, t);
            helper.assertFalse(CurioSeal.isSealed(t), "关掉后过了最少保持，T 解除");
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    /** sealFieldWearerLifecycle 用：交手、评估、结算，T 被 W 压制 */
    private static void sealOnce(GameTestHelper helper, ServerPlayer w, ServerPlayer t, List<ServerPlayer> all, long at) {
        fought(w, t, at);
        ComputerSealField.evaluate(w, all, at);
        resolve(at, w, t);
        helper.assertTrue(CurioSeal.isSealed(t) && CurioSeal.sourcesOf(t).containsKey(w.getUUID()), "前提：T 被 W 压制（第 " + at + " 刻）");
    }

    /**
     * 运转会封住自己的饰品时，自己饰品栏里的储存物品从第一次就不付：胸甲没电、电全在戒指槽的灵魂之球里 → 第一次评估就是电量不足、
     * 球一点不动；十秒里来回评估也不会「这一秒付得起、下一秒被封了付不起」地启停，「电量不足」只提示一次。
     * affects_self 关掉时饰品栏里的储存照付
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldSkipsOwnCurioStorage(GameTestHelper helper) {
        ServerPlayer w = wearer(helper, "sf-ring-w", 0);
        ServerPlayer t = player(helper, "sf-ring-t", 3.5D, 0.5D);
        List<ServerPlayer> all = List.of(w, t);
        ItemStack sphere = new ItemStack(ECItems.SOUL_SPHERE.get());
        setMru(sphere, 5000);
        CuriosApi.getCuriosInventory(w).resolve().orElseThrow().setEquippedCurio(CuriosCompat.RING, 0, sphere);
        CuriosCompat.invalidate(w);
        List<SealNotifier.Sent> sent = SealNotifier.captureForTest(w.getUUID());
        long now = now(helper);
        boolean pvp = pvpOn(helper);
        try {
            CurioSealConfig.setTestOverrides(config().respectClaims(false).build());
            helper.assertTrue(mru(ringStack(w)) == 5000, "前提：戒指槽的灵魂之球存 5000");
            for (int i = 0; i <= 10; i++) {
                long at = now + 20L * i;
                fought(w, t, at);
                ComputerSealField.Result result = ComputerSealField.evaluate(w, all, at);
                resolve(at, w, t);
                helper.assertTrue(result.state() == FieldState.NO_POWER, "第 " + i + " 次评估应是电量不足，实际 " + result.state());
                helper.assertFalse(CurioSeal.isSealed(w) || CurioSeal.isSealed(t), "付不起就谁都不压（第 " + i + " 次）");
            }
            helper.assertTrue(mru(ringStack(w)) == 5000, "自己饰品栏里的储存一点不动，剩 " + mru(ringStack(w)));
            long told = count(sent, "message.essentialcraft5.computer_seal.no_power");
            helper.assertTrue(told == 2, "十秒里「电量不足」只提示一次（聊天栏 + 动作栏），实际 " + told + " 条");

            CurioSealConfig.setTestOverrides(config().respectClaims(false).affectsSelf(false).build());
            long later = now + 240;
            fought(w, t, later);
            ComputerSealField.Result paid = ComputerSealField.evaluate(w, all, later);
            resolve(later, w, t);
            helper.assertTrue(paid.state() == FieldState.RUNNING && mru(ringStack(w)) == 4600,
                    "不封自己时饰品栏里的储存照付 400，实际 " + paid.state() + "，剩 " + mru(ringStack(w)));
        } finally {
            restorePvp(helper, pvp);
            cleanup(w, t);
        }
        helper.succeed();
    }

    private static ItemStack ringStack(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CuriosCompat.RING))
                .map(handler -> handler.getStacks().getStackInSlot(0)).orElse(ItemStack.EMPTY);
    }

    /**
     * 「电量不足」的去抖：提示过以后，运转中要连续供电满 200 刻才再提（一秒够一秒不够地来回切不刷屏）；转回待机马上可以再提
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldNoPowerDebounce(GameTestHelper helper) {
        ServerPlayer w = player(helper, "sf-nop-w", 0.5D, 0.5D);
        List<SealNotifier.Sent> sent = SealNotifier.captureForTest(w.getUUID());
        String key = "message.essentialcraft5.computer_seal.no_power";
        try {
            SealNotifier.noPower(w, 400);
            helper.assertTrue(count(sent, key) == 2, "第一次：聊天栏 + 动作栏");
            SealNotifier.powered(w, true, 20);
            SealNotifier.noPower(w, 400);
            SealNotifier.powered(w, true, 60);
            SealNotifier.powered(w, true, 140);
            SealNotifier.noPower(w, 400);
            helper.assertTrue(count(sent, key) == 2, "运转没连续满 200 刻又缺电：不再提，实际 " + count(sent, key));
            SealNotifier.powered(w, true, 200);
            SealNotifier.powered(w, true, 399);
            SealNotifier.noPower(w, 400);
            helper.assertTrue(count(sent, key) == 2, "连续 199 刻：还不提，实际 " + count(sent, key));
            SealNotifier.powered(w, true, 500);
            SealNotifier.powered(w, true, 700);
            SealNotifier.noPower(w, 400);
            helper.assertTrue(count(sent, key) == 4, "连续运转满 200 刻以后再缺电：再提一次，实际 " + count(sent, key));
            SealNotifier.powered(w, false, 720);
            SealNotifier.noPower(w, 400);
            helper.assertTrue(count(sent, key) == 6, "中间转回过待机：再提一次，实际 " + count(sent, key));
        } finally {
            SealNotifier.reset(w.getUUID());
            SealNotifier.stopCapture(w.getUUID());
        }
        helper.succeed();
    }
}
