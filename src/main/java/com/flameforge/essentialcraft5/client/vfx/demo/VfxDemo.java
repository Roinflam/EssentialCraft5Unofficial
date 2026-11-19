package com.flameforge.essentialcraft5.client.vfx.demo;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MRUCoilBlockEntity;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxRenderTypes;
import com.flameforge.essentialcraft5.client.vfx.fx.BoardTrailFx;
import com.flameforge.essentialcraft5.client.vfx.fx.PillarBacklashFx;
import com.flameforge.essentialcraft5.client.vfx.fx.TeslaDischargeFx;
import com.flameforge.essentialcraft5.client.vfx.fx.VerdictJudgmentFx;
import com.flameforge.essentialcraft5.client.vfx.warn.BossWarningClient;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
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

/**
 * 特效样片的演示开关（开发用，只在客户端，不碰服务端和网络）：录制工具 / TestHelper 用 {@code setstatic} 往 {@link #request} 里写一行指令，
 * 下一个客户端刻读走执行。几条指令可以用分号隔开写在一行。
 * <pre>
 * verdict                                  以本地玩家为中心放一次断罪（打 6 格内的敌对生物），蓄力完自动挥一下手
 * tesla &lt;x&gt; &lt;y&gt; &lt;z&gt; [次数]                 从 (x,y,z) 的特斯拉线圈打最近的 3 个敌对生物，每 20 刻一次
 * backlash &lt;x&gt; &lt;y&gt; &lt;z&gt; [半径] [刻] [柱高]  灾柱反噬预警圈（默认 6 格、30 刻、柱高 3.2）
 * board on|off                             本地玩家的反重力板尾迹
 * flight &lt;cx&gt; &lt;cy&gt; &lt;cz&gt; &lt;半径&gt; &lt;一圈几刻&gt; | flight off   让本地玩家绕圈飞（创造模式飞行）
 * swatch &lt;x&gt; &lt;y&gt; &lt;z&gt; [刻]                   一排所有模式的面片（光影兼容对照用）
 * warn circle &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;半径&gt; &lt;刻&gt; [颜色]                 首领预警组件（和服务端发预警包一样，颜色写十六进制 RGB）：圆形
 * warn pillar &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;半径&gt; &lt;刻&gt; &lt;柱高&gt; [颜色]          圆形 + 柱顶聚能（灾柱反噬）
 * warn sector &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;半径&gt; &lt;刻&gt; &lt;朝向°&gt; &lt;半角°&gt; [颜色] 扇形（朝向 0° = +x，往 +z 转）
 * warn line &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;朝向°&gt; &lt;半长&gt; &lt;半宽&gt; &lt;刻&gt; [颜色]     贴地线（x y z 是中点）
 * warn tether &lt;x1&gt; &lt;y1&gt; &lt;z1&gt; &lt;x2&gt; &lt;y2&gt; &lt;z2&gt; &lt;刻&gt; [颜色]        空中瞄准线
 * warn column &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;柱高&gt; &lt;圈半径&gt; &lt;刻&gt; [颜色]|me     光柱（写 me 跟着本地玩家）
 * warn ring &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;起始半径&gt; &lt;结束半径&gt; &lt;每刻&gt; &lt;带宽&gt; &lt;蓄力刻&gt; [颜色] 移动的环
 * warn safe &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;安全半径&gt; &lt;外圈半径&gt; &lt;刻&gt; [安全色] [危险色] 收缩安全圈
 * clear                                    清掉所有特效
 * </pre>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class VfxDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialCraft5/VfxDemo");

    /** 待执行的指令（TestHelper setstatic 写进来） */
    public static String request = "";
    /** 上一条指令的结果（getstatic 读） */
    public static String result = "";

    private static boolean flight;
    private static Vec3 flightCenter = Vec3.ZERO;
    private static double flightRadius;
    private static int flightPeriod;
    private static int flightTick;
    private static boolean boardOn;
    private static final List<Repeat> REPEATS = new ArrayList<>();
    private static final List<Runnable> LATER = new ArrayList<>();
    private static final List<Integer> LATER_AT = new ArrayList<>();
    private static int clock;

    private VfxDemo() {
    }

    private record Repeat(Vec3 emit, BlockPos coil, int[] left, int start) {
    }

    @SubscribeEvent
    public static void onVfxDemoTick(TickEvent.ClientTickEvent event) {
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
                    LOGGER.warn("[特效演示] 指令出错：{}", command, e);
                }
            }
        }
        for (int i = LATER.size() - 1; i >= 0; i--) {
            if (LATER_AT.get(i) <= clock) {
                Runnable r = LATER.remove(i);
                LATER_AT.remove(i);
                r.run();
            }
        }
        for (int i = REPEATS.size() - 1; i >= 0; i--) {
            Repeat repeat = REPEATS.get(i);
            if ((clock - repeat.start) % 20 == 0) {
                VfxEffects.add(new TeslaDischargeFx(repeat.emit, nearestMonsters(mc, repeat.emit, 12D, 3)));
                if (--repeat.left[0] <= 0) {
                    REPEATS.remove(i);
                }
            }
        }
        if (flight) {
            fly(mc.player);
        }
    }

    private static String run(Minecraft mc, String command) {
        String[] a = command.toLowerCase(Locale.ROOT).split("\\s+");
        LocalPlayer player = mc.player;
        switch (a[0]) {
            case "verdict" -> {
                List<LivingEntity> targets = new ArrayList<>(nearestMonsters(mc, player.position(), 6D, 16).stream()
                        .filter(e -> e instanceof LivingEntity).map(e -> (LivingEntity) e).toList());
                VfxEffects.add(new VerdictJudgmentFx(player, targets, 6D));
                later(VerdictJudgmentFx.CHARGE, () -> player.swing(InteractionHand.MAIN_HAND));
                return "verdict targets " + targets.size();
            }
            case "tesla" -> {
                BlockPos pos = BlockPos.containing(num(a[1]), num(a[2]), num(a[3]));
                Vec3 emit = mc.level.getBlockEntity(pos) instanceof MRUCoilBlockEntity coil ? coil.emitPoint() : Vec3.atCenterOf(pos).add(0D, 0.6D, 0D);
                int times = a.length > 4 ? Integer.parseInt(a[4]) : 1;
                // 下一刻就放第一次（之后每 20 刻一次），每条镜头的节拍一样
                REPEATS.add(new Repeat(emit, pos, new int[]{times}, clock + 1));
                return "tesla x" + times + " emit " + emit;
            }
            case "backlash" -> {
                Vec3 base = new Vec3(num(a[1]), num(a[2]), num(a[3]));
                double radius = a.length > 4 ? num(a[4]) : 6D;
                int warn = a.length > 5 ? Integer.parseInt(a[5]) : 30;
                double height = a.length > 6 ? num(a[6]) : 3.2D;
                VfxEffects.add(new PillarBacklashFx(base, radius, warn, height));
                return "backlash";
            }
            case "board" -> {
                boardOn = a.length < 2 || a[1].equals("on");
                if (boardOn) {
                    VfxEffects.add(new BoardTrailFx(player, () -> boardOn && player.getAbilities().flying));
                }
                return "board " + boardOn;
            }
            case "flight" -> {
                if (a.length < 2 || a[1].equals("off")) {
                    flight = false;
                    return "flight off";
                }
                flightCenter = new Vec3(num(a[1]), num(a[2]), num(a[3]));
                flightRadius = num(a[4]);
                flightPeriod = Math.max(20, Integer.parseInt(a[5]));
                flightTick = 0;
                flight = true;
                player.getAbilities().flying = true;
                return "flight on";
            }
            case "swatch" -> {
                Vec3 at = new Vec3(num(a[1]), num(a[2]), num(a[3]));
                int ticks = a.length > 4 ? Integer.parseInt(a[4]) : 6000;
                VfxEffects.add(new Swatch(at, ticks));
                return "swatch";
            }
            case "warn" -> {
                return warn(mc, a);
            }
            case "clear" -> {
                VfxEffects.clearAll();
                REPEATS.clear();
                flight = false;
                boardOn = false;
                return "clear";
            }
            case "stat" -> {
                // 同时写日志：影棚副本里拿不到返回值，看日志里的「[特效演示] effects ...」
                String stat = String.format(Locale.ROOT, "effects %d quads %d particles %d safe %s variant %d tier %d governor %.2f", VfxEffects.size(), VfxEffects.lastQuads,
                        VfxParticles.count(), Vfx.safeMode(), VfxRenderTypes.safeVariant, Vfx.tier(), Vfx.governor());
                LOGGER.info("[特效演示] {}", stat);
                return stat;
            }
            default -> throw new IllegalArgumentException("不认识的演示指令：" + command);
        }
    }

    /** warn 指令：拼一个和服务端一样的预警包交给客户端处理（不碰服务端） */
    private static String warn(Minecraft mc, String[] a) {
        double x = num(a[2]);
        double y = num(a[3]);
        double z = num(a[4]);
        S2CBossWarningPacket packet = switch (a[1]) {
            case "circle" -> packet(S2CBossWarningPacket.CIRCLE, -1, x, y, z, (float) num(a[5]), 0F, 0F, 0F, 0F, Integer.parseInt(a[6]), 0,
                    color(a, 7, VfxPalette.DANGER), 0, 0);
            case "pillar" -> packet(S2CBossWarningPacket.CIRCLE, -1, x, y, z, (float) num(a[5]), (float) num(a[7]), 0F, 0F, 0F, Integer.parseInt(a[6]), 0,
                    color(a, 8, VfxPalette.DANGER), 0, S2CBossWarningPacket.FLAG_PILLAR);
            case "sector" -> {
                double yaw = Math.toRadians(num(a[7]));
                yield packet(S2CBossWarningPacket.CIRCLE, -1, x, y, z, (float) num(a[5]), (float) Math.toRadians(num(a[8])), (float) Math.cos(yaw), 0F,
                        (float) Math.sin(yaw), Integer.parseInt(a[6]), 0, color(a, 9, VfxPalette.DANGER), 0, S2CBossWarningPacket.FLAG_SECTOR);
            }
            case "line" -> {
                double yaw = Math.toRadians(num(a[5]));
                yield packet(S2CBossWarningPacket.LINE, -1, x, y, z, (float) num(a[6]), (float) num(a[7]), (float) Math.cos(yaw), 0F, (float) Math.sin(yaw),
                        Integer.parseInt(a[8]), 0, color(a, 9, VfxPalette.DANGER), 0, 0);
            }
            case "tether" -> {
                Vec3 d = new Vec3(num(a[5]) - x, num(a[6]) - y, num(a[7]) - z);
                double len = Math.max(1.0E-3D, d.length());
                yield packet(S2CBossWarningPacket.LINE, -1, x, y, z, (float) len, 0F, (float) (d.x / len), (float) (d.y / len), (float) (d.z / len),
                        Integer.parseInt(a[8]), 0, color(a, 9, VfxPalette.DANGER), 0, S2CBossWarningPacket.FLAG_TETHER);
            }
            case "column" -> {
                boolean me = a.length > 8 && a[8].equals("me");
                yield packet(S2CBossWarningPacket.COLUMN, me ? mc.player.getId() : -1, x, y, z, (float) num(a[5]), (float) num(a[6]), 0F, 0F, 0F,
                        Integer.parseInt(a[7]), 0, me ? 0xFFD27A : color(a, 8, 0xFFD27A), 0, 0);
            }
            case "ring" -> {
                float from = (float) num(a[5]);
                float to = (float) num(a[6]);
                float speed = (float) num(a[7]);
                int delay = Integer.parseInt(a[9]);
                int travel = Math.abs(speed) < 1.0E-6F ? 0 : (int) Math.ceil(Math.abs(to - from) / Math.abs(speed));
                yield packet(S2CBossWarningPacket.RING, -1, x, y, z, from, to, speed, (float) num(a[8]), 0F, delay + travel, delay,
                        color(a, 10, 0xFF7A3A), 0, 0);
            }
            case "safe" -> packet(S2CBossWarningPacket.SAFE_ZONE, -1, x, y, z, (float) num(a[5]), (float) num(a[6]), 0F, 0F, 0F, Integer.parseInt(a[7]), 0,
                    color(a, 8, 0xFFD27A), color(a, 9, VfxPalette.DANGER), 0);
            default -> throw new IllegalArgumentException("不认识的预警：" + a[1]);
        };
        BossWarningClient.handle(packet);
        return "warn " + a[1];
    }

    private static S2CBossWarningPacket packet(int kind, int follow, double x, double y, double z, float size, float size2, float dx, float dy, float dz,
                                               int duration, int delay, int color, int color2, int flags) {
        return new S2CBossWarningPacket(kind, -1, follow, x, y, z, size, size2, dx, dy, dz, duration, delay, color, color2, flags);
    }

    private static int color(String[] a, int index, int fallback) {
        return a.length > index ? Integer.parseInt(a[index], 16) : fallback;
    }

    private static void later(int ticks, Runnable r) {
        LATER.add(r);
        LATER_AT.add(clock + ticks);
    }

    /** 绕圈飞：每刻直接摆位置和速度（创造模式飞行，服务端照收），身体朝着前进方向，上下轻轻起伏 */
    private static void fly(LocalPlayer player) {
        flightTick++;
        double w = Mth.TWO_PI / flightPeriod;
        double th = flightTick * w;
        double x = flightCenter.x + Math.cos(th) * flightRadius;
        double z = flightCenter.z + Math.sin(th) * flightRadius;
        double y = flightCenter.y + Math.sin(th * 2D) * 0.8D;
        double vx = -Math.sin(th) * flightRadius * w;
        double vz = Math.cos(th) * flightRadius * w;
        double vy = Math.cos(th * 2D) * 1.6D * w;
        player.getAbilities().flying = true;
        player.setPos(x, y, z);
        player.setDeltaMovement(vx, vy, vz);
        float yaw = (float) (Mth.atan2(-vx, vz) * Mth.RAD_TO_DEG);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.setXRot(8F);
    }

    private static List<Entity> nearestMonsters(Minecraft mc, Vec3 at, double range, int max) {
        List<Entity> found = new ArrayList<>(mc.level.getEntitiesOfClass(Monster.class, new AABB(at, at).inflate(range),
                e -> e.isAlive() && e.position().distanceTo(at) <= range + 0.5D));
        found.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(at)));
        return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
    }

    private static double num(String s) {
        return Double.parseDouble(s);
    }

    /** 一排面片：每种模式一个正对镜头的方片 + 一个躺在地上的方片，看光影包下哪些显示正常 */
    private static final class Swatch implements VfxEffect {
        private final Vec3 at;
        private final int life;
        private int age;

        Swatch(Vec3 at, int life) {
            this.at = at;
            this.life = life;
        }

        @Override
        public boolean tick() {
            return ++age < life;
        }

        @Override
        public void render(VfxDraw draw) {
            for (int mode = 0; mode < VfxMode.COUNT; mode++) {
                Vec3 c = at.add(mode * 1.6D, 1.2D, 0D);
                int param = mode == VfxMode.FILL ? VfxMode.FULL : mode == VfxMode.MOLTEN ? 700 : 0;
                if (VfxMode.isStrip(mode)) {
                    draw.strip(c.add(-0.7D, 0D, 0D), c.add(0.7D, 0D, 0D), 0.3F, 0.3F, Vfx.argb(VfxPalette.MAGENTA, 1F), Vfx.argb(VfxPalette.MAGENTA, 1F),
                            mode, param, 0F, 1F);
                } else {
                    draw.billboard(c.x, c.y, c.z, 0.7F, 0.7F, 0F, mode, param, Vfx.argb(mode == VfxMode.SMOKE ? VfxPalette.SMOKE : VfxPalette.MAGENTA, 1F),
                            mode == VfxMode.SMOKE);
                }
                draw.flat(at.add(mode * 1.6D, 0D, 2D), 0.7F, 0F, mode, param, Vfx.argb(VfxPalette.LAVENDER, 1F));
            }
            draw.distortBillboard(at.add(-2D, 1.2D, 0D), 1F, 0, 140, 1F);
        }

        @Override
        public AABB bounds() {
            return new AABB(at, at.add(VfxMode.COUNT * 1.6D, 3D, 3D)).inflate(1D);
        }
    }
}
