package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.GearFxClient;
import com.flameforge.essentialcraft5.client.GunsClient;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.flameforge.essentialcraft5.entity.DividerProjectileEntity;
import com.flameforge.essentialcraft5.entity.MRUArrowEntity;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.entity.StormGustEntity;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 武器特效的演示开关（开发用）：TestHelper {@code setstatic ...WeaponVfxDemo request "<指令>"}，下一个客户端刻读走，分号隔开可写几条。
 * 打准星对着的地方；要服务端实体的几条（轨道打击、二分场、弹丸）只在单人游戏里能用，走的是正式的服务端代码。
 * <pre>
 * gun &lt;透镜 0~4&gt; &lt;pistol|rifle|sniper|gatling&gt; [刻数] [间隔]   本地玩家朝准星连发（假射手，不扣 MRU、不打伤害）
 * gunall &lt;刻数&gt;                    四种枪各一名假射手并排对着准星连发（压测用）
 * orbital [延迟刻]                  准星处放一次轨道打击（0 伤害）
 * divider [个数]                    准星处放二分场（个数 &gt; 1 时排成一排，间隔 7 格）
 * verdict [蓄力刻]                  本地玩家蓄力后断罪 6 格内的敌对生物（只在客户端画）
 * gear &lt;execute|lifesteal|armor_pen|verdict_seal|verdict_burst|element_burst|overload_ready|overload_fire&gt; [参数]
 * ember &lt;0 火|1 水|2 地|3 风|4 复合&gt;   最近的敌对生物挨一下余烬剑命中
 * wind                              本地玩家挥一下风之剑的风痕
 * arrow | gust | dproj              朝准星射一支 MRU 箭 / 疾风弹 / 二分弹
 * wstat [reset|off]                 本包特效每帧耗时、面片数、粒子数
 * quality &lt;-1|0|1|2&gt;                强制质量档（-1 按配置）；governor on|off；clear
 * </pre>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class WeaponVfxDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialCraft5/WeaponVfxDemo");

    public static String request = "";
    public static String result = "";

    private record Burst(GunType type, int balance, int interval, int left, int start, double side) {
    }

    private static final List<Burst> BURSTS = new ArrayList<>();
    private static int clock;

    private WeaponVfxDemo() {
    }

    @SubscribeEvent
    public static void onWeaponVfxDemoTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        clock++;
        String line = request;
        if (line != null && !line.isBlank()) {
            request = "";
            for (String command : line.split(";")) {
                try {
                    result = run(mc, command.trim());
                } catch (RuntimeException e) {
                    result = "error " + e;
                    LOGGER.warn("[武器特效演示] 指令出错：{}", command, e);
                }
                LOGGER.info("[武器特效演示] {} -> {}", command.trim(), result);
            }
        }
        for (int i = BURSTS.size() - 1; i >= 0; i--) {
            Burst b = BURSTS.get(i);
            int since = clock - b.start();
            if (since >= b.left()) {
                BURSTS.remove(i);
            } else if (since % b.interval() == 0) {
                fire(mc.player, b.type(), b.balance(), b.side());
            }
        }
    }

    private static String run(Minecraft mc, String command) {
        String[] a = command.toLowerCase(Locale.ROOT).split("\\s+");
        LocalPlayer player = mc.player;
        switch (a[0]) {
            case "gun" -> {
                GunType type = GunType.valueOf(a[2].toUpperCase(Locale.ROOT));
                int ticks = a.length > 3 ? Integer.parseInt(a[3]) : 1;
                int interval = a.length > 4 ? Integer.parseInt(a[4]) : 1;
                BURSTS.add(new Burst(type, Integer.parseInt(a[1]), Math.max(1, interval), ticks, clock + 1, 0D));
                return "gun " + type + " x" + ticks;
            }
            case "gunall" -> {
                int ticks = Integer.parseInt(a[1]);
                GunType[] types = {GunType.PISTOL, GunType.RIFLE, GunType.SNIPER, GunType.GATLING};
                int[] intervals = {6, 3, 20, 1};
                for (int i = 0; i < types.length; i++) {
                    BURSTS.add(new Burst(types[i], i + 1, intervals[i], ticks, clock + 1, (i - 1.5D) * 1.5D));
                }
                return "gunall " + ticks;
            }
            case "orbital" -> {
                int delay = a.length > 1 ? Integer.parseInt(a[1]) : 60;
                Vec3 at = aim(player);
                return server(mc, sp -> OrbitalStrikeEntity.spawn(sp.serverLevel(), at, 0F, 0F, delay, sp, e -> false)) ? "orbital " + at : "需要单人游戏";
            }
            case "divider" -> {
                int n = a.length > 1 ? Integer.parseInt(a[1]) : 1;
                Vec3 at = aim(player);
                Vec3 right = new Vec3(-Math.cos(Math.toRadians(player.getYRot())), 0D, -Math.sin(Math.toRadians(player.getYRot())));
                return server(mc, sp -> {
                    for (int i = 0; i < n; i++) {
                        DividerFieldEntity.spawn(sp.serverLevel(), at.add(right.scale((i - (n - 1) / 2D) * 7D)), 40, sp, e -> false, false);
                    }
                }) ? "divider x" + n : "需要单人游戏";
            }
            case "verdict" -> {
                int ticks = a.length > 1 ? Integer.parseInt(a[1]) : 24;
                List<LivingEntity> targets = monsters(mc, player.position(), 6D, 16);
                VerdictClient.demo(player, ticks, targets, 6D);
                return "verdict targets " + targets.size();
            }
            case "gear" -> {
                GearFx type = GearFx.valueOf(a[1].toUpperCase(Locale.ROOT));
                float param = a.length > 2 ? Float.parseFloat(a[2]) : 0F;
                List<LivingEntity> near = monsters(mc, player.position(), 12D, 1);
                Entity target = switch (type) {
                    case LIFESTEAL, OVERLOAD_READY -> player;
                    default -> near.isEmpty() ? null : near.get(0);
                };
                Vec3 pos = type == GearFx.LIFESTEAL || type == GearFx.OVERLOAD_READY
                        ? (near.isEmpty() ? aim(player) : center(near.get(0)))
                        : target == null ? aim(player) : type == GearFx.ELEMENT_BURST ? target.position() : center(target);
                GearFxClient.handle(new S2CGearFxPacket(type.ordinal(), target == null ? -1 : target.getId(), pos.x, pos.y, pos.z, param));
                return "gear " + type;
            }
            case "ember" -> {
                List<LivingEntity> near = monsters(mc, player.position(), 12D, 1);
                if (near.isEmpty()) {
                    return "附近没有敌对生物";
                }
                ElementFx.hit(near.get(0), ForgedSwordData.Element.values()[Integer.parseInt(a[1])]);
                return "ember";
            }
            case "wind" -> {
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                VfxEffects.add(new WindSlashFx(player));
                return "wind";
            }
            case "arrow", "gust", "dproj" -> {
                String kind = a[0];
                return server(mc, sp -> {
                    ServerLevel level = sp.serverLevel();
                    Projectile p = switch (kind) {
                        case "arrow" -> new MRUArrowEntity(ECWindMage.MRU_ARROW.get(), level, sp);
                        case "gust" -> new StormGustEntity(level, sp);
                        default -> new DividerProjectileEntity(level, sp);
                    };
                    p.setPos(sp.getEyePosition().add(sp.getLookAngle().scale(1.5D)));
                    p.shootFromRotation(sp, sp.getXRot(), sp.getYRot(), 0F, kind.equals("gust") ? 1.3F : 1.6F, 0F);
                    level.addFreshEntity(p);
                }) ? kind : "需要单人游戏";
            }
            case "wstat" -> {
                if (a.length > 1 && a[1].equals("reset")) {
                    WeaponVfxStats.reset();
                    return "wstat reset";
                }
                if (a.length > 1 && a[1].equals("off")) {
                    WeaponVfxStats.enabled = false;
                    return "wstat off";
                }
                String s = WeaponVfxStats.report() + String.format(Locale.ROOT, " tier %d safe %s", Vfx.tier(), Vfx.safeMode());
                return s;
            }
            case "quality" -> {
                Vfx.forceQuality = Integer.parseInt(a[1]);
                return "quality " + Vfx.forceQuality;
            }
            case "governor" -> {
                Vfx.governorEnabled = !a[1].equals("off");
                return "governor " + Vfx.governorEnabled;
            }
            case "view" -> {
                mc.options.setCameraType(CameraType.values()[Integer.parseInt(a[1])]);
                return "view " + mc.options.getCameraType();
            }
            case "clear" -> {
                BURSTS.clear();
                VfxEffects.clearAll();
                GunFireFx.clear();
                VerdictClient.clear();
                return "clear";
            }
            default -> throw new IllegalArgumentException("不认识的演示指令：" + command);
        }
    }

    private static void fire(LocalPlayer player, GunType type, int balance, double side) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1F);
        Vec3 right = new Vec3(-Math.cos(Math.toRadians(player.getYRot())), 0D, -Math.sin(Math.toRadians(player.getYRot())));
        Vec3 origin = eye.add(right.scale(side));
        HitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(origin, origin.add(look.scale(64D)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 to = hit.getLocation();
        Vec3 from = side == 0D ? GunsClient.muzzle(player, eye, look) : origin.add(look.scale(0.5D));
        GunFireFx.shot(-1000 - type.ordinal() * 10 - (int) Math.round(side * 2D), origin, from, to, balance, 0.5F, 0F, 0F, false, type);
    }

    private static Vec3 aim(LocalPlayer player) {
        HitResult hit = player.pick(64D, 0F, false);
        return hit.getLocation();
    }

    private static Vec3 center(Entity e) {
        return e.position().add(0D, e.getBbHeight() * 0.55D, 0D);
    }

    private static List<LivingEntity> monsters(Minecraft mc, Vec3 c, double r, int max) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity living && e instanceof Enemy && e.isAlive() && e.position().distanceTo(c) <= r) {
                out.add(living);
            }
        }
        out.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(c)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    private static boolean server(Minecraft mc, Consumer<ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return false;
        }
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) {
                action.accept(sp);
            }
        });
        return true;
    }
}
