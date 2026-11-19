package com.flameforge.essentialcraft5.client.vfx.gear;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.ComputerClient;
import com.flameforge.essentialcraft5.client.GearFxClient;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.item.MagicalWingsItem;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.AttunementLook;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECCharms;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 装备 / 工具特效（特效第 5 批）的客户端入口：
 * <ul>
 *   <li>常驻的：反重力板尾迹、魔力翅膀（每刻扫一遍世界里的玩家，开始飞了挂上、停了自己收掉）、封禁场穹顶（跟着服务端的 {@code SEAL_FIELD} 续命）；</li>
 *   <li>一次性的：喷射、闪避、定罪、空中跳和五个盔甲觉醒，在 {@link GearFxClient} 里按事件种类登记（{@link #registerAll}）。</li>
 * </ul>
 * 远近分档 {@link #lod}：24 格内 0（全画）、56 格内 1（去掉装饰层）、96 格内 2（只留主体）、再远 3（不画）。
 * 翅膀同屏最多 {@value #MAX_WINGS} 对、尾迹 {@value #MAX_BOARDS} 道、穹顶 {@value #MAX_DOMES} 个，多了只挂最近的。
 * 别人用不用翅膀服务端不同步，按「戴着 / 拿着翅膀、离地超过半秒、下落不比翅膀缓降快」认（普通跳一下不会闪出来）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class GearVfx {

    static final int MAX_WINGS = 32;
    static final int MAX_BOARDS = 24;
    static final int MAX_DOMES = 6;
    /** 别人离地这么多刻以后才认翅膀（普通跳一下 12 刻落地，下落速度第 9 刻就超过缓降了） */
    private static final int WINGS_AIR_TICKS = 10;
    /** 翅膀缓降压到 -0.2 格 / 刻，留一点余量 */
    private static final double WINGS_FALL_LIMIT = -0.24D;

    private static final Int2ObjectOpenHashMap<Tracked> BOARDS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Tracked> WINGS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Tracked> DOMES = new Int2ObjectOpenHashMap<>();
    private static final IntOpenHashSet WINGS_ON = new IntOpenHashSet();
    private static final Int2IntOpenHashMap AIR_TICKS = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap LAST_DASH = new Int2IntOpenHashMap();
    private static final List<Player> CANDIDATES = new ArrayList<>();
    private static int clock;

    // 统计（演示开关的 stat 读）：这一批的特效在 CPU 上画了多久
    private static long frameNanos;
    private static long windowNanos;
    private static long windowFrameNanos;
    private static int windowFrames;
    private static long lastFrameAt;
    static double lastGearMs;
    static double lastFrameMs;

    private GearVfx() {
    }

    // ------------------------------------------------------------------ 远近、计时

    /** 离镜头多远：0 = 24 格内，1 = 56 格内，2 = 96 格内，3 = 更远（不画） */
    static int lod(Vec3 p) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = camera.distanceToSqr(p);
        return d <= 24D * 24D ? 0 : d <= 56D * 56D ? 1 : d <= 96D * 96D ? 2 : 3;
    }

    static long beginTiming() {
        return System.nanoTime();
    }

    static void endTiming(long start) {
        frameNanos += System.nanoTime() - start;
    }

    // ------------------------------------------------------------------ 事件画法

    /** 在 {@link GearFxClient} 的静态初始化里调：登记盔甲觉醒和装备动作的画法 */
    public static void registerAll() {
        GearFxClient.register(GearFx.SURGE, (level, entity, pos, param) -> AwakeningFx.surge(entity, pos, param, armorTint(ECAttunement.MHS_ARMOR)));
        GearFxClient.register(GearFx.STORM_STEP, (level, entity, pos, param) -> AwakeningFx.stormStep(pos, param, armorTint(ECAttunement.WIND_ARMOR)));
        GearFxClient.register(GearFx.AFTERIMAGE, (level, entity, pos, param) -> AwakeningFx.afterimage(entity, pos, param, armorTint(ECAttunement.VOID_ARMOR)));
        GearFxClient.register(GearFx.SENTENCE, (level, entity, pos, param) -> AwakeningFx.sentence(pos, param, armorTint(ECAttunement.INQUISITORIUM_ARMOR)));
        GearFxClient.register(GearFx.OVERCHARGE_PULSE,
                (level, entity, pos, param) -> AwakeningFx.overcharge(entity, pos, param, armorTint(ECAttunement.COMPUTER_ARMOR)));
        GearFxClient.register(GearFx.JET_DASH, (level, entity, pos, param) -> jetDash(entity, pos, param));
        GearFxClient.register(GearFx.VOID_DODGE, (level, entity, pos, param) -> {
            if (lod(pos) <= 2) {
                VfxEffects.add(new MotionFx.VoidDodge(entity, entity == null ? pos : entity.position(), armorTint(ECAttunement.VOID_ARMOR)));
            }
        });
        GearFxClient.register(GearFx.CONVICT, (level, entity, pos, param) -> {
            if (lod(pos) <= 2) {
                VfxEffects.add(new MotionFx.Convict(entity, pos, (int) param));
            }
        });
        GearFxClient.register(GearFx.AIR_JUMP, (level, entity, pos, param) -> {
            // 跳的人自己在本地已经画过了（WindArmorClient）
            if (entity == null || entity != Minecraft.getInstance().player) {
                gust(pos);
            }
        });
        GearFxClient.register(GearFx.SEAL_FIELD, (level, entity, pos, param) -> sealField(entity, pos, param));
    }

    /** 本地玩家空中跳（client/WindArmorClient 调，不等服务端） */
    public static void localAirJump(Player player) {
        gust(player.position());
    }

    private static void gust(Vec3 feet) {
        if (lod(feet) <= 2) {
            VfxEffects.add(new MotionFx.Gust(feet, 1.5F, 3, armorTint(ECAttunement.WIND_ARMOR)));
        }
    }

    static void jetDash(@Nullable Entity entity, Vec3 feet, float speed) {
        if (lod(feet) > 2) {
            return;
        }
        if (entity != null) {
            int last = LAST_DASH.getOrDefault(entity.getId(), Integer.MIN_VALUE);
            if (clock - last >= 0 && clock - last < 4) {
                return;
            }
            LAST_DASH.put(entity.getId(), clock);
        }
        Vec3 dir = entity != null ? entity.getLookAngle() : new Vec3(0D, 0D, 1D);
        VfxEffects.add(new MotionFx.JetDash(entity, entity != null ? entity.position() : feet, dir, Math.max(0.5F, speed)));
    }

    private static void sealField(@Nullable Entity entity, Vec3 pos, float param) {
        int key = entity != null ? entity.getId() : Integer.MIN_VALUE + (int) (Double.doubleToLongBits(pos.x * 31D + pos.z) & 0xFFFF);
        Tracked tracked = DOMES.get(key);
        if (tracked != null && !tracked.done && tracked.inner instanceof SealDomeFx dome && !dome.stopping()) {
            dome.refresh(param);
            return;
        }
        if (param == 0F || DOMES.size() >= MAX_DOMES) {
            return;
        }
        GearAnchor anchor = entity instanceof LivingEntity living ? GearAnchor.of(living) : new GearAnchor.Fake(pos, 0D, 0, 0F);
        track(DOMES, key, new SealDomeFx(anchor, param));
    }

    /** 挂一个常驻特效并记进表；特效列表满了就不挂（不然表里留一个永远不会结束的） */
    private static void track(Int2ObjectOpenHashMap<Tracked> map, int key, VfxEffect effect) {
        if (VfxEffects.size() >= VfxEffects.MAX_EFFECTS) {
            return;
        }
        Tracked tracked = new Tracked(effect);
        map.put(key, tracked);
        VfxEffects.add(tracked);
    }

    /** 盔甲家族满阶前一阶的颜色配的一套等离子层次（同 GearFxClient 原来的占位） */
    static VfxPlasma.Tint armorTint(AttunementFamily family) {
        int[] stages = AttunementLook.palette(family).stages();
        return VfxPlasma.tint(stages[stages.length - 1]);
    }

    // ------------------------------------------------------------------ 常驻：每刻扫玩家

    @SubscribeEvent
    public static void onGearVfxClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }
        clock++;
        prune(BOARDS);
        prune(WINGS);
        prune(DOMES);
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        CANDIDATES.clear();
        WINGS_ON.clear();
        if (clock % 1200 == 0) {
            LAST_DASH.clear();
        }
        for (AbstractClientPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            updateWings(player);
            if (camera.distanceToSqr(player.position()) < 96D * 96D) {
                CANDIDATES.add(player);
            }
        }
        if (CANDIDATES.size() > Math.min(MAX_WINGS, MAX_BOARDS)) {
            CANDIDATES.sort(Comparator.comparingDouble(p -> p.distanceToSqr(camera)));
        }
        for (Player player : CANDIDATES) {
            int id = player.getId();
            if (!BOARDS.containsKey(id) && BOARDS.size() < MAX_BOARDS && ComputerClient.ridingBoard(player)) {
                track(BOARDS, id, new BoardJetFx(GearAnchor.of(player), () -> ComputerClient.ridingBoard(player)));
            }
            if (!WINGS.containsKey(id) && WINGS.size() < MAX_WINGS && WINGS_ON.contains(id)) {
                track(WINGS, id, new WingsFx(GearAnchor.of(player), () -> WINGS_ON.contains(id), id * 7));
            }
            Tracked wings = WINGS.get(id);
            if (wings != null && wings.inner instanceof WingsFx w) {
                w.setRising(player.getY() - player.yo > 0.02D);
            }
        }
    }

    private static void updateWings(AbstractClientPlayer player) {
        int id = player.getId();
        boolean on;
        if (player instanceof LocalPlayer) {
            int last = MagicalWingsItem.clientFlightTick;
            on = last != Integer.MIN_VALUE && player.tickCount >= last && player.tickCount - last <= 2;
        } else {
            boolean airborne = !player.onGround() && !player.isFallFlying() && !player.isPassenger() && !player.isInWater() && !player.onClimbable();
            int air = airborne ? AIR_TICKS.get(id) + 1 : 0;
            if (air > 0) {
                AIR_TICKS.put(id, air);
            } else {
                AIR_TICKS.remove(id);
            }
            on = air >= WINGS_AIR_TICKS && player.getY() - player.yo >= WINGS_FALL_LIMIT && hasWings(player);
        }
        if (on) {
            WINGS_ON.add(id);
        }
    }

    private static boolean hasWings(Player player) {
        return player.getMainHandItem().getItem() instanceof MagicalWingsItem || player.getOffhandItem().getItem() instanceof MagicalWingsItem
                || CuriosCompat.has(player, ECCharms.MAGICAL_WINGS.get());
    }

    private static void prune(Int2ObjectOpenHashMap<Tracked> map) {
        if (!map.isEmpty()) {
            map.values().removeIf(t -> t.done);
        }
    }

    // ------------------------------------------------------------------ 统计

    @SubscribeEvent
    public static void onGearVfxFrame(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrameAt != 0L && now - lastFrameAt < 1_000_000_000L) {
            windowFrameNanos += now - lastFrameAt;
            windowNanos += frameNanos;
            windowFrames++;
        }
        lastFrameAt = now;
        frameNanos = 0L;
        if (windowFrames >= 120) {
            lastGearMs = windowNanos / 1.0E6D / windowFrames;
            lastFrameMs = windowFrameNanos / 1.0E6D / windowFrames;
            windowNanos = 0L;
            windowFrameNanos = 0L;
            windowFrames = 0;
        }
    }

    static String stats() {
        return String.format(java.util.Locale.ROOT, "gear %.3f ms/frame, frame %.2f ms, quads %d, effects %d, boards %d, wings %d, domes %d",
                lastGearMs, lastFrameMs, VfxEffects.lastQuads, VfxEffects.size(), BOARDS.size(), WINGS.size(), DOMES.size());
    }

    // ------------------------------------------------------------------ 清理

    static void clear() {
        BOARDS.clear();
        WINGS.clear();
        DOMES.clear();
        WINGS_ON.clear();
        AIR_TICKS.clear();
        LAST_DASH.clear();
        AwakeningFx.clear();
    }

    @SubscribeEvent
    public static void onGearVfxLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clear();
        }
    }

    @SubscribeEvent
    public static void onGearVfxLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    /** 包一层，记下「已经结束」（VfxEffects 移除时不通知），表里好删 */
    static final class Tracked implements VfxEffect {

        final VfxEffect inner;
        boolean done;

        Tracked(VfxEffect inner) {
            this.inner = inner;
        }

        @Override
        public boolean tick() {
            if (done) {
                return false;
            }
            done = !inner.tick();
            return !done;
        }

        @Override
        public void render(VfxDraw draw) {
            inner.render(draw);
        }

        @Override
        public AABB bounds() {
            return inner.bounds();
        }
    }
}
