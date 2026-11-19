package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.BossWarnings;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityPillarEntity;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData.Element;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 天灾之心实打（开发用，只在单人游戏的内置服务端起作用，由 {@link BossWarnDemo} 的 {@code fight} 指令启动）：
 * 摆一只带 AI 的天灾之心，给本地玩家穿一档装备，再由这里替玩家走位、出手，记下用时、受伤、死没死，拿来看装备成长和首领强度对不对得上。
 * <p>
 * 打法是固定的：护盾期打最近的灾柱，破盾窗口和之后打心；冷却满了才挥一下（不暴击、不放右键技）；
 * 站在预警的圆、扇形、线、光柱脚下就往外走，安全圈收缩时往圈里走，跟着人走的标记和移动的环不躲；
 * 饱食度一直满、饱和度 0（只有自然回血，不喝药、不吃金苹果）。所以结果是「会走位的普通近战玩家」的大致水平，不是高手打法。
 * <pre>
 * fight &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;none|mid|max&gt; [主灾]   在这块地面上开打；none = 0 阶，mid = 裁决之刃 3 阶 + 审判庭 2 阶，
 *                                          max = 裁决之刃 5 阶 + 审判庭 4 阶（满阶觉醒）+ 每件 10 级战痕
 * fightstop                                停下、删掉首领
 * </pre>
 * 每 20 秒打一行「[实打]」进度日志，结束时打一行「[实打] 结果」。玩家挨到致命一击时记作死亡、拦下这次死亡，回满血等下一档。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class CalamityFightBot {

    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialCraft5/CalamityFightBot");

    /** 最长打多久（刻） */
    private static final int LIMIT = 20 * 60 * 15;
    private static final double STEP = 0.28D;
    private static final double STAND_OFF = 2.5D;
    private static final double REACH = 4.0D;

    private static boolean running;
    /** 玩家挨到了致命一击（死亡被这里拦下，免得停在死亡界面，下一档接着打） */
    private static boolean died;
    @Nullable
    private static CalamityHeartEntity heart;
    private static String tier = "";
    private static int ticks;
    private static double taken;
    private static int hitsTaken;
    private static double biggestHit;
    private static double dealtHeart;
    private static double dealtPillars;
    private static int swings;
    private static float lowestHealth;
    private static int pillarsDownAt = -1;
    private static int collapseAt = -1;
    private static final List<Zone> ZONES = new ArrayList<>();
    /** 受伤分项：键 = 伤害类型 + 攻击者，值 = 合计 / 次数 */
    private static final Map<String, double[]> BY_SOURCE = new HashMap<>();
    /** 上一刻玩家的生命（含伤害吸收） */
    private static float lastTotal;
    /** 这一刻伤害事件记到的量、回血事件记到的量 */
    private static double tickDamage;
    private static double tickHeal;
    /** 这一刻最后一个伤害事件的说明，给对不上账的情况当线索 */
    private static String lastHit = "-";
    /** 掉了血却没有对应伤害事件的合计 */
    private static double unexplained;
    private static int unexplainedLogs;
    private static int hitLogs;

    private CalamityFightBot() {
    }

    /** 一个还在生效的预警区域 */
    private record Zone(S2CBossWarningPacket p, long end) {
    }

    static String start(ServerLevel level, ServerPlayer player, String[] a) {
        stop();
        BlockPos floor = BlockPos.containing(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]));
        tier = a[4];
        CalamityHeartEntity.Aspect main = a.length > 5
                ? CalamityHeartEntity.Aspect.valueOf(a[5].toUpperCase(Locale.ROOT)) : CalamityHeartEntity.Aspect.SUN_ARRAY;
        CalamityHeartEntity h = ECCalamityHeart.CALAMITY_HEART.get().create(level);
        if (h == null) {
            return "fight no heart";
        }
        Vec3 at = Vec3.atBottomCenterOf(floor).add(0D, 7D, 0D);
        h.moveTo(at.x, at.y, at.z, 0F, 0F);
        h.setPersistenceRequired();
        h.setupArena(floor, main, null);
        if (!level.addFreshEntity(h)) {
            return "fight spawn failed";
        }
        h.syncPillars(level);
        h.bossRoster().add(player);
        heart = h;

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        player.getInventory().clearContent();
        // 影棚存档里的玩家可能留着上次的辐射剂量，剂量够了每刻会把生命压到上限的一半（对账时发现的），开打前清零并记下来
        int dose = Radiation.get(player);
        if (dose > 0) {
            LOGGER.info("[实打] 存档里的辐射剂量 {}，清零", dose);
            Radiation.set(player, 0L);
        }
        equip(player);
        player.setHealth(player.getMaxHealth());
        player.connection.teleport(floor.getX() + 0.5D, floor.getY() + 1D, floor.getZ() + 12.5D, 180F, 0F);
        BossWarnings.captureForTest();
        ticks = 0;
        taken = biggestHit = dealtHeart = dealtPillars = 0D;
        hitsTaken = swings = 0;
        lowestHealth = player.getMaxHealth();
        pillarsDownAt = collapseAt = -1;
        died = false;
        ZONES.clear();
        BY_SOURCE.clear();
        tickDamage = tickHeal = unexplained = 0D;
        unexplainedLogs = hitLogs = 0;
        lastHit = "-";
        lastTotal = player.getHealth() + player.getAbsorptionAmount();
        running = true;
        LOGGER.info("[实打] 开始 档 {} 主灾 {} 首领生命 {}", tier, main, fmt(h.getMaxHealth()));
        return "fight " + tier;
    }

    static void stop() {
        if (heart != null && !heart.isRemoved()) {
            for (int i = 0; i < 4; i++) {
                CalamityPillarEntity pillar = heart.pillar(i);
                if (pillar != null) {
                    pillar.discard();
                }
            }
            heart.discard();
        }
        heart = null;
        running = false;
    }

    private static void equip(ServerPlayer player) {
        int blade;
        int armor;
        int scar;
        switch (tier) {
            case "mid" -> {
                blade = 3;
                armor = 2;
                scar = 0;
            }
            case "max" -> {
                blade = -1;
                armor = -1;
                scar = 20_000;
            }
            default -> {
                blade = 0;
                armor = 0;
                scar = 0;
            }
        }
        ItemStack sword = MRUStaffItem.full(ECEmberForge.VERDICT_BLADE.get());
        new ForgedSwordData(List.of(EmberKind.COMMON.id(), EmberKind.FLAME.id(), EmberKind.COMMON.id(), EmberKind.FLAME.id()),
                List.<ResourceLocation>of(Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus()),
                Element.WATER, Element.FIRE).writeTo(sword);
        staged(sword, blade, scar);
        player.setItemSlot(EquipmentSlot.MAINHAND, sword);
        player.setItemSlot(EquipmentSlot.HEAD, piece(ECInquisitorium.INQUISITORIUM_HELMET.get(), armor, scar));
        player.setItemSlot(EquipmentSlot.CHEST, piece(ECInquisitorium.INQUISITORIUM_CHESTPLATE.get(), armor, scar));
        player.setItemSlot(EquipmentSlot.LEGS, piece(ECInquisitorium.INQUISITORIUM_LEGGINGS.get(), armor, scar));
        player.setItemSlot(EquipmentSlot.FEET, piece(ECInquisitorium.INQUISITORIUM_BOOTS.get(), armor, scar));
    }

    private static ItemStack piece(Item item, int stage, int scar) {
        return staged(MRUStaffItem.full(item), stage, scar);
    }

    /** stage 为 -1 时取家族最高阶 */
    private static ItemStack staged(ItemStack stack, int stage, int scar) {
        Attunement.set(stack, stage < 0 ? Attunement.maxStage(stack) : stage, 0);
        if (scar > 0) {
            Attunement.addPoints(stack, scar);
        }
        return stack;
    }

    // ------------------------------------------------------------------ 每刻

    static void tick(MinecraftServer server) {
        if (!running) {
            return;
        }
        ServerPlayer p = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
        CalamityHeartEntity h = heart;
        if (p == null || h == null) {
            stop();
            return;
        }
        ticks++;
        if (died || p.isDeadOrDying()) {
            finish("死亡", p, h);
            return;
        }
        if (h.isRemoved() || h.isDeadOrDying()) {
            finish(h.isDeadOrDying() || h.getHealth() <= 0F ? "打倒" : "首领散去", p, h);
            return;
        }
        if (h.dissipating()) {
            finish("首领散去", p, h);
            return;
        }
        if (ticks > LIMIT) {
            finish("超时", p, h);
            return;
        }
        ServerLevel level = p.serverLevel();
        if (ticks == 5) {
            // 盔甲加的最大生命下一刻才生效，开打时回满要等它
            p.setHealth(p.getMaxHealth());
            lowestHealth = p.getMaxHealth();
            lastTotal = p.getHealth() + p.getAbsorptionAmount();
            LOGGER.info("[实打] 回满 玩家 {}/{} 护甲 {} 韧性 {}", fmt(p.getHealth()), fmt(p.getMaxHealth()), p.getArmorValue(),
                    fmt(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)));
        }
        audit(p);
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(0F);
        lowestHealth = Math.min(lowestHealth, p.getHealth());
        if (pillarsDownAt < 0 && h.pillarsStanding() == 0) {
            pillarsDownAt = ticks;
        }
        if (collapseAt < 0 && h.phase() == CalamityHeartEntity.Phase.COLLAPSE) {
            collapseAt = ticks;
        }

        collectZones(level, h);
        Entity target = target(h, p);
        Vec3 me = p.position();
        Vec3 escape = escape(level, me, p, h);
        Vec3 next = me;
        if (escape.lengthSqr() > 1.0E-6D) {
            next = me.add(escape.normalize().scale(STEP + 0.04D));
        } else if (target != null) {
            Vec3 flat = new Vec3(target.getX() - me.x, 0D, target.getZ() - me.z);
            double dist = flat.length();
            if (dist > STAND_OFF + 0.3D) {
                Vec3 step = me.add(flat.normalize().scale(Math.min(STEP, dist - STAND_OFF)));
                if (escape(level, step, p, h).lengthSqr() < 1.0E-6D) {
                    next = step;
                }
            }
        }
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(next.x), Mth.floor(next.z));
        float yaw = p.getYRot();
        float pitch = 0F;
        if (target != null) {
            double dx = target.getX() - next.x;
            double dz = target.getZ() - next.z;
            double dy = target.getY(0.5D) - (y + p.getEyeHeight());
            yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        }
        p.fallDistance = 0F;
        p.connection.teleport(next.x, y, next.z, yaw, pitch);

        if (target != null && p.getAttackStrengthScale(0.5F) >= 0.95F
                && Math.hypot(target.getX() - p.getX(), target.getZ() - p.getZ()) <= REACH && !h.introActive()) {
            p.attack(target);
            p.swing(InteractionHand.MAIN_HAND, true);
            p.resetAttackStrengthTicker();
            swings++;
        }
        if (ticks % 400 == 0) {
            LOGGER.info("[实打] {} 秒 阶段 {} 灾柱 {} 首领 {}% 玩家 {}/{} 已受伤 {} 打心 {} 打柱 {}", ticks / 20, h.phase(), h.pillarsStanding(),
                    fmt(h.getHealth() / h.getMaxHealth() * 100F), fmt(p.getHealth()), fmt(p.getMaxHealth()), fmt(taken), fmt(dealtHeart),
                    fmt(dealtPillars));
        }
    }

    /**
     * 每刻对一次账：上一刻的生命（含伤害吸收）− 这一刻的生命，应该等于这一刻伤害事件记到的量 − 回血事件记到的量。
     * 差出来的部分说明有掉血没走伤害事件（直接改生命、别的模组的结算），记进「未经伤害事件」并打日志找来源。
     */
    private static void audit(ServerPlayer p) {
        float now = p.getHealth() + p.getAbsorptionAmount();
        double lost = lastTotal - now;
        double diff = lost - (tickDamage - tickHeal);
        // 回血事件报的量可以超过补满的量（已经满血了），这种差不算掉血
        boolean healOverflow = tickHeal > 0D && now >= p.getMaxHealth() - 0.01D;
        if (ticks > 5 && Math.abs(diff) > 0.05D && !healOverflow) {
            if (diff > 0D) {
                unexplained += diff;
                note("未经伤害事件", diff);
            }
            if (unexplainedLogs++ < 40) {
                LOGGER.info("[实打] 对账 {} 刻：生命 {} → {}，伤害事件 {}，回血事件 {}，差 {}，最近一击 {}", ticks, fmt(lastTotal), fmt(now),
                        fmt(tickDamage), fmt(tickHeal), fmt(diff), lastHit);
            }
        }
        lastTotal = now;
        tickDamage = 0D;
        tickHeal = 0D;
    }

    private static void note(String key, double amount) {
        double[] v = BY_SOURCE.computeIfAbsent(key, k -> new double[2]);
        v[0] += amount;
        v[1]++;
    }

    private static String describe(net.minecraft.world.damagesource.DamageSource src) {
        Entity attacker = src.getEntity();
        Entity direct = src.getDirectEntity();
        return src.getMsgId() + "/" + (attacker == null ? "无" : attacker.getType().builtInRegistryHolder().key().location().getPath())
                + (direct != null && direct != attacker ? "<" + direct.getType().builtInRegistryHolder().key().location().getPath() : "");
    }

    @Nullable
    private static Entity target(CalamityHeartEntity h, ServerPlayer p) {
        if (h.introActive()) {
            return h;
        }
        if (h.phase() != CalamityHeartEntity.Phase.SHIELD || h.shieldOpen()) {
            return h;
        }
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            CalamityPillarEntity pillar = h.pillar(i);
            if (pillar != null && h.pillarHealth(i) > 0F) {
                double d = pillar.distanceToSqr(p);
                if (d < bestDist) {
                    bestDist = d;
                    best = pillar;
                }
            }
        }
        return best == null ? h : best;
    }

    // ------------------------------------------------------------------ 躲预警

    private static void collectZones(ServerLevel level, CalamityHeartEntity h) {
        long now = level.getGameTime();
        List<Entity> owners = new ArrayList<>();
        owners.add(h);
        for (int i = 0; i < 4; i++) {
            CalamityPillarEntity pillar = h.pillar(i);
            if (pillar != null) {
                owners.add(pillar);
            }
        }
        for (Entity owner : owners) {
            for (S2CBossWarningPacket packet : BossWarnings.sentForTest(owner)) {
                if (packet.kind() == S2CBossWarningPacket.CANCEL) {
                    ZONES.removeIf(z -> z.p().owner() == owner.getId());
                } else {
                    ZONES.add(new Zone(packet, now + packet.duration() + 2L));
                }
            }
            BossWarnings.clearForTest(owner);
        }
        Iterator<Zone> it = ZONES.iterator();
        while (it.hasNext()) {
            if (it.next().end() < now) {
                it.remove();
            }
        }
    }

    /** 站在 at 时要往哪边走才能出预警区（不用走时是零向量） */
    private static Vec3 escape(ServerLevel level, Vec3 at, ServerPlayer p, CalamityHeartEntity h) {
        Vec3 out = Vec3.ZERO;
        for (Zone zone : ZONES) {
            S2CBossWarningPacket w = zone.p();
            if ((w.flags() & S2CBossWarningPacket.FLAG_TETHER) != 0) {
                continue;
            }
            double cx = w.x();
            double cz = w.z();
            if (w.follow() >= 0) {
                if (w.follow() == p.getId()) {
                    continue;
                }
                Entity f = level.getEntity(w.follow());
                if (f == null) {
                    continue;
                }
                cx = f.getX();
                cz = f.getZ();
            }
            double dx = at.x - cx;
            double dz = at.z - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            Vec3 away = dist < 1.0E-3D ? new Vec3(1D, 0D, 0D) : new Vec3(dx / dist, 0D, dz / dist);
            if (w.kind() == S2CBossWarningPacket.CIRCLE) {
                if ((w.flags() & S2CBossWarningPacket.FLAG_SECTOR) != 0) {
                    if (dist < w.size() + 1D) {
                        double cos = (dx * w.dirX() + dz * w.dirZ()) / Math.max(dist, 1.0E-3D);
                        if (Math.acos(Mth.clamp(cos, -1D, 1D)) < w.size2() + 0.2D) {
                            double side = dx * -w.dirZ() + dz * w.dirX();
                            out = out.add(new Vec3(-w.dirZ(), 0D, w.dirX()).scale(side >= 0D ? 1D : -1D));
                        }
                    }
                } else if (dist < w.size() + 1D) {
                    out = out.add(away);
                }
            } else if (w.kind() == S2CBossWarningPacket.LINE) {
                double along = dx * w.dirX() + dz * w.dirZ();
                double perp = dx * -w.dirZ() + dz * w.dirX();
                if (Math.abs(along) < w.size() + 1D && Math.abs(perp) < w.size2() + 1D) {
                    out = out.add(new Vec3(-w.dirZ(), 0D, w.dirX()).scale(perp >= 0D ? 1D : -1D));
                }
            } else if (w.kind() == S2CBossWarningPacket.COLUMN) {
                if (dist < w.size2() + 1D) {
                    out = out.add(away);
                }
            } else if (w.kind() == S2CBossWarningPacket.SAFE_ZONE) {
                if (dist > w.size() - 1D) {
                    out = out.add(away.scale(-1D));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 记账

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFightBotDamage(LivingDamageEvent event) {
        if (!running || heart == null || event.getAmount() <= 0F) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer) {
            taken += event.getAmount();
            hitsTaken++;
            biggestHit = Math.max(biggestHit, event.getAmount());
            tickDamage += event.getAmount();
            String d = describe(event.getSource());
            note(d, event.getAmount());
            lastHit = d + " " + fmt(event.getAmount());
            if (hitLogs++ < 60) {
                LOGGER.info("[实打] 受击 {} 刻 {} 伤害 {} 生命 {}", ticks, d, fmt(event.getAmount()), fmt(victim.getHealth()));
            }
        } else if (event.getSource().getEntity() instanceof ServerPlayer) {
            if (victim == heart) {
                dealtHeart += event.getAmount();
            } else if (victim instanceof CalamityPillarEntity) {
                dealtPillars += event.getAmount();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFightBotHeal(LivingHealEvent event) {
        if (running && event.getEntity() instanceof ServerPlayer && !event.isCanceled()) {
            tickHeal += event.getAmount();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFightBotDeath(LivingDeathEvent event) {
        if (running && event.getEntity() instanceof ServerPlayer player) {
            event.setCanceled(true);
            player.setHealth(1F);
            died = true;
        }
    }

    private static void finish(String how, ServerPlayer p, CalamityHeartEntity h) {
        String line = String.format(Locale.ROOT,
                "档 %s | %s | 用时 %d 秒 | 灾柱全倒 %s 秒 | 进崩解 %s 秒 | 首领剩 %s%% | 受伤合计 %s（%d 下，最重一下 %s）| 最低生命 %s/%s | 打心 %s 打柱 %s | 挥 %d 下",
                tier, how, ticks / 20, pillarsDownAt < 0 ? "-" : String.valueOf(pillarsDownAt / 20), collapseAt < 0 ? "-" : String.valueOf(collapseAt / 20),
                fmt(Math.max(0F, h.getHealth()) / h.getMaxHealth() * 100F), fmt(taken + unexplained), hitsTaken, fmt(biggestHit), fmt(lowestHealth),
                fmt(p.getMaxHealth()), fmt(dealtHeart), fmt(dealtPillars), swings);
        String top = BY_SOURCE.entrySet().stream()
                .sorted((x, y) -> Double.compare(y.getValue()[0], x.getValue()[0])).limit(6)
                .map(e -> e.getKey() + " " + fmt(e.getValue()[0]) + "×" + (int) e.getValue()[1])
                .collect(Collectors.joining("；"));
        line += " | 分项 " + top + " | 未经伤害事件 " + fmt(unexplained) + " | 结束时辐射剂量 " + Radiation.get(p);
        LOGGER.info("[实打] 结果 {}", line);
        BossWarnDemo.result = "fightdone " + line;
        stop();
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
