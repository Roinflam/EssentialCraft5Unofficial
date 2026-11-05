package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.LevitatorBlock;
import com.flameforge.essentialcraft5.block.ParticleCooldown;
import com.flameforge.essentialcraft5.block.SpreaderBlock;
import com.flameforge.essentialcraft5.item.bauble.MysticalAccessoryItem;
import com.flameforge.essentialcraft5.registry.ECVoid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;

/**
 * ECVoid 的客户端部分（第六批 6a M7）：
 * <ul>
 *   <li>神秘饰品的模型属性 {@link MysticalAccessoryItem#MODEL_PREDICATE}（形态 × 36 + 底 × 6 + 面，整数，用不夹 0~1 的 {@link ItemPropertyFunction}）。</li>
 *   <li>MRU 悬浮器 / 散射器的粒子（方块 {@code animateTick} 经 DistExecutor 调过来）：每次至多 {@value LevitatorBlock#MAX_PARTICLES} /
 *       {@value SpreaderBlock#MAX_PARTICLES} 个、同一格 {@value ParticleCooldown#COOLDOWN_TICKS} tick 冷却（{@link ParticleCooldown}）、
 *       离镜头超过 {@value LevitatorBlock#MAX_CAMERA_DISTANCE} 格不冒；粒子设置「最少 / 少量」由原版 {@code addParticle} 自己按比例丢。
 *       颜色取研究手册的 MRU 渐变（{@code CodexTheme.MRU_BLUE} #6A40FF → {@code MRU_VIOLET} #A050FF → {@code MRU_MAGENTA} #F050F0），
 *       用原版的渐变粉尘（{@link DustColorTransitionOptions}，大小 {@value LevitatorBlock#DUST_SCALE} / {@value SpreaderBlock#DUST_SCALE}），
 *       不新注册粒子类型。原作的 mruFX 是会沿路线移动的自绘粒子，粉尘基本停在原地，所以把粒子<b>摆</b>在路线上：
 *       悬浮器从顶面往上一条竖线 + 腰上一圈，被实心方块挡住的面不冒、埋着的一颗都不冒（{@link LevitatorBlock#openFaces}）；散射器在顶上一个球壳。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VoidClient {

    /** MRU 蓝 → 紫罗兰（悬浮器） */
    private static final DustColorTransitionOptions LEVITATOR_DUST = new DustColorTransitionOptions(
            rgb(0x6A40FF), rgb(0xA050FF), LevitatorBlock.DUST_SCALE);
    /** MRU 紫罗兰 → 品红（散射器） */
    private static final DustColorTransitionOptions SPREADER_DUST = new DustColorTransitionOptions(
            rgb(0xA050FF), rgb(0xF050F0), SpreaderBlock.DUST_SCALE);
    /** 水平四面，和 {@link LevitatorBlock#openFaces} 的位对应 */
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private static final ParticleCooldown COOLDOWN = new ParticleCooldown();
    private static WeakReference<Level> cooldownLevel = new WeakReference<>(null);

    private VoidClient() {
    }

    @SubscribeEvent
    public static void voidClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ECVoid.MYSTICAL_ACCESSORY.get(), MysticalAccessoryItem.MODEL_PREDICATE,
                (ItemPropertyFunction) (stack, level, entity, seed) -> MysticalAccessoryItem.modelIndex(stack)));
    }

    /**
     * 悬浮器：原作 12 颗往下落 5~10 格 + 36 颗往四周散。这里竖线摆在顶面往上 0~1 格（中心 ±0.1），带一点往上的初速度；
     * 腰上那圈摆在 y + 0.25、半径 0.5~0.7，只落在没被挡住的那几面（每颗随机挑一面，角度在那一面 ±45° 内），初速度朝外。
     * 竖线 / 腰圈各几颗见 {@link LevitatorBlock#columnParticles} / {@link LevitatorBlock#ringParticles}，五面全被挡就直接返回。
     */
    public static void levitatorParticles(Level level, BlockPos pos, RandomSource random) {
        if (!ready(level, pos)) {
            return;
        }
        int open = LevitatorBlock.openFaces(level, pos);
        int column = LevitatorBlock.columnParticles(open);
        int ring = LevitatorBlock.ringParticles(open);
        double cx = pos.getX() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        double top = pos.getY() + 1D;
        for (int i = 0; i < column; i++) {
            level.addParticle(LEVITATOR_DUST, cx + (random.nextDouble() - 0.5D) * 0.2D, top + random.nextDouble(),
                    cz + (random.nextDouble() - 0.5D) * 0.2D, 0D, 1D, 0D);
        }
        if (ring == 0) {
            return;
        }
        Direction[] sides = new Direction[SIDES.length];
        int count = 0;
        for (Direction side : SIDES) {
            if ((open & LevitatorBlock.faceBit(side)) != 0) {
                sides[count++] = side;
            }
        }
        for (int i = 0; i < ring; i++) {
            Direction side = sides[random.nextInt(count)];
            double angle = Math.atan2(side.getStepZ(), side.getStepX()) + (random.nextDouble() - 0.5D) * (Math.PI / 2D);
            double radius = 0.5D + random.nextDouble() * 0.2D;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            level.addParticle(LEVITATOR_DUST, cx + dx * radius, pos.getY() + 0.25D, cz + dz * radius, dx, 0D, dz);
        }
    }

    /** 散射器：原作 50 颗从顶上往随机方向射出 10 格；这里每颗摆在顶上 (0.5, 0.9, 0.5) 半径 0.3~0.9 的球壳上随机一点 */
    public static void spreaderParticles(Level level, BlockPos pos, RandomSource random) {
        if (!ready(level, pos)) {
            return;
        }
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.9D;
        double cz = pos.getZ() + 0.5D;
        for (int i = 0; i < SpreaderBlock.MAX_PARTICLES; i++) {
            double yaw = random.nextDouble() * Math.PI * 2D;
            double pitch = Math.asin(random.nextDouble() * 2D - 1D);
            double radius = 0.3D + random.nextDouble() * 0.6D;
            double dx = Math.cos(pitch) * Math.cos(yaw);
            double dy = Math.sin(pitch);
            double dz = Math.cos(pitch) * Math.sin(yaw);
            level.addParticle(SPREADER_DUST, cx + dx * radius, cy + dy * radius, cz + dz * radius, dx, dy, dz);
        }
    }

    /** 离镜头够近、这一格过了冷却 */
    private static boolean ready(Level level, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer == null) {
            return false;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double max = LevitatorBlock.MAX_CAMERA_DISTANCE;
        if (camera.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > max * max) {
            return false;
        }
        if (cooldownLevel.get() != level) {
            COOLDOWN.clear();
            cooldownLevel = new WeakReference<>(level);
        }
        return COOLDOWN.tryFire(pos.asLong(), level.getGameTime());
    }

    private static Vector3f rgb(int color) {
        return new Vector3f(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F);
    }
}
