package com.flameforge.essentialcraft5.client.vfx.gear;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
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
 * 装备特效的演示开关（开发用，只在客户端，不碰服务端）：TestHelper {@code setstatic ...GearVfxDemo request "<指令>"}，分号隔开可以写几条。
 * <pre>
 * wings &lt;n&gt; [半径] [up]     身边摆 n 个张着翅膀的假人（压测用；up = 往上飞的扇法）
 * boards &lt;n&gt; [半径] [一圈几刻] n 个踩着反重力板绕圈飞的假人
 * me wings|board on|off      本地玩家强制挂上翅膀 / 尾迹（不用真戴）
 * dash | dodge | jump | convict | surge | storm | afterimage | sentence | overcharge   在本地玩家身上放一次（convict / sentence 打最近的怪，没有就打自己）
 * dome &lt;半径&gt; [standby] [far] | dome off   本地玩家身上的封禁场穹顶（far = 摆到面前从外面看）
 * stat                       上一个统计窗口（120 帧）：本批特效 CPU 耗时、帧时间、面片数
 * clear                      撤掉假人和强制挂的东西
 * </pre>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class GearVfxDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialCraft5/GearVfxDemo");

    public static String request = "";
    public static String result = "";

    private static final List<GearAnchor.Fake> FAKES = new ArrayList<>();
    private static boolean meWings;
    private static boolean meBoard;
    private static SealDomeFx meDome;
    private static GearAnchor.Fake pendingImage;
    private static int imageAt;
    private static int clock;

    private GearVfxDemo() {
    }

    @SubscribeEvent
    public static void onGearVfxDemoTick(TickEvent.ClientTickEvent event) {
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
                    result = run(mc.player, command.trim());
                } catch (RuntimeException e) {
                    result = "error " + e;
                    LOGGER.warn("[装备特效演示] 指令出错：{}", command, e);
                }
            }
        }
        for (GearAnchor.Fake fake : FAKES) {
            fake.tick();
        }
        if (pendingImage != null && clock >= imageAt) {
            Vec3 p = pendingImage.pos(0F);
            AwakeningFx.afterimage(null, p.add(0D, 0.9D, 0D), 3.5F, GearVfx.armorTint(ECAttunement.VOID_ARMOR));
            pendingImage = null;
        }
    }

    private static String run(LocalPlayer player, String command) {
        String[] a = command.toLowerCase(Locale.ROOT).split("\\s+");
        Vec3 feet = player.position();
        GearAnchor me = GearAnchor.of(player);
        switch (a[0]) {
            case "wings" -> {
                int n = Integer.parseInt(a[1]);
                double r = a.length > 2 ? Double.parseDouble(a[2]) : 4D;
                boolean up = a.length > 3 && a[3].equals("up");
                for (int i = 0; i < n; i++) {
                    GearAnchor.Fake fake = new GearAnchor.Fake(feet.add(0D, 1.5D, 0D), r + (i % 3) * 1.5D, 0, (float) (i * 2.399D));
                    FAKES.add(fake);
                    WingsFx wings = new WingsFx(fake, () -> fake.alive, i * 7);
                    wings.setRising(up);
                    VfxEffects.add(wings);
                }
                return "wings " + n;
            }
            case "boards" -> {
                int n = Integer.parseInt(a[1]);
                double r = a.length > 2 ? Double.parseDouble(a[2]) : 8D;
                int period = a.length > 3 ? Integer.parseInt(a[3]) : 140;
                for (int i = 0; i < n; i++) {
                    GearAnchor.Fake fake = new GearAnchor.Fake(feet.add(0D, 2D + (i % 4) * 1.2D, 0D), r + (i % 3) * 2D, period,
                            (float) (i * 2.399D));
                    FAKES.add(fake);
                    VfxEffects.add(new BoardJetFx(fake, () -> fake.alive));
                }
                return "boards " + n;
            }
            case "me" -> {
                boolean on = a.length < 3 || a[2].equals("on");
                if (a[1].equals("wings")) {
                    if (on && !meWings) {
                        VfxEffects.add(new WingsFx(me, () -> meWings, 3));
                    }
                    meWings = on;
                } else if (a[1].equals("board")) {
                    if (on && !meBoard) {
                        VfxEffects.add(new BoardJetFx(me, () -> meBoard));
                    }
                    meBoard = on;
                }
                return "me " + a[1] + " " + on;
            }
            case "dash" -> GearVfx.jetDash(player, feet, 3F);
            case "dodge" -> VfxEffects.add(new MotionFx.VoidDodge(player, feet, GearVfx.armorTint(ECAttunement.VOID_ARMOR)));
            case "jump" -> GearVfx.localAirJump(player);
            case "convict" -> {
                LivingEntity target = nearestMonster(player);
                VfxEffects.add(new MotionFx.Convict(target, target.position().add(0D, target.getBbHeight() * 0.6D, 0D), 40));
            }
            case "surge" -> AwakeningFx.surge(player, feet.add(0D, 0.1D, 0D), 4F, GearVfx.armorTint(ECAttunement.MHS_ARMOR));
            case "storm" -> AwakeningFx.stormStep(feet, 4F, GearVfx.armorTint(ECAttunement.WIND_ARMOR));
            case "afterimage" -> {
                AwakeningFx.afterimage(player, feet, 0F, GearVfx.armorTint(ECAttunement.VOID_ARMOR));
                pendingImage = new GearAnchor.Fake(feet, 0D, 0, 0F);
                imageAt = clock + 20;
            }
            case "sentence" -> {
                LivingEntity target = nearestMonster(player);
                AwakeningFx.sentence(target.position().add(0D, target.getBbHeight() * 0.5D, 0D), 10F, GearVfx.armorTint(ECAttunement.INQUISITORIUM_ARMOR));
            }
            case "overcharge" -> AwakeningFx.overcharge(player, feet.add(0D, 0.9D, 0D), 5F, GearVfx.armorTint(ECAttunement.COMPUTER_ARMOR));
            case "dome" -> {
                if (meDome != null) {
                    meDome.stop();
                    meDome = null;
                }
                if (!a[1].equals("off")) {
                    float r = Float.parseFloat(a[1]);
                    boolean standby = command.contains("standby");
                    GearAnchor at = me;
                    if (command.contains("far")) {
                        // 摆在面前 r + 10 格，从外面看
                        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
                        GearAnchor.Fake fake = new GearAnchor.Fake(feet.add(-Mth.sin(yaw) * (r + 10D), 0D, Mth.cos(yaw) * (r + 10D)), 0D, 0, 0F);
                        FAKES.add(fake);
                        at = fake;
                    }
                    meDome = new SealDomeFx(at, standby ? -r : r);
                    VfxEffects.add(new KeepAlive(meDome));
                }
            }
            case "stat" -> {
                return GearVfx.stats();
            }
            case "clear" -> {
                for (GearAnchor.Fake fake : FAKES) {
                    fake.alive = false;
                }
                FAKES.clear();
                meWings = false;
                meBoard = false;
                if (meDome != null) {
                    meDome.stop();
                    meDome = null;
                }
            }
            default -> {
                return "unknown " + a[0];
            }
        }
        return "ok " + command;
    }

    /** 给 TestHelper 的 pcall 读统计用（它只认一个能接住本地玩家的参数） */
    public static String stat(net.minecraft.world.entity.player.Player player) {
        return GearVfx.stats();
    }

    private static LivingEntity nearestMonster(LocalPlayer player) {
        List<Entity> list = player.level().getEntities(player, player.getBoundingBox().inflate(12D), e -> e instanceof Monster && e.isAlive());
        return list.stream().min(Comparator.comparingDouble(player::distanceToSqr)).map(e -> (LivingEntity) e).orElse(player);
    }

    /** 演示的穹顶不等服务端续命：每秒自己续一次 */
    private static final class KeepAlive implements VfxEffect {

        private final SealDomeFx dome;
        private int age;

        KeepAlive(SealDomeFx dome) {
            this.dome = dome;
        }

        @Override
        public boolean tick() {
            if (++age % 20 == 0 && !dome.stopping()) {
                dome.keepAlive();
            }
            return dome.tick();
        }

        @Override
        public void render(VfxDraw draw) {
            dome.render(draw);
        }

        @Override
        public AABB bounds() {
            return dome.bounds();
        }
    }
}
