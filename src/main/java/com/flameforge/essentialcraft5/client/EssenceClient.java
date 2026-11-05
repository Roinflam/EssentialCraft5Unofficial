package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.client.screen.CrystalControllerScreen;
import com.flameforge.essentialcraft5.client.screen.CrystalExtractorScreen;
import com.flameforge.essentialcraft5.registry.ECEssence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

/**
 * 精华与焦点模块的客户端部分（模组总线，Forge 自动发现）：水晶控制器、水晶提取器的界面；魔力光源的粒子。
 * 两台机器原作的 TESR 只是把 OBJ 模型再画一遍，这里直接用 OBJ 方块模型，没有 BER。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EssenceClient {

    /** 原作 ParticleMRU 对能看见 MRU 的人画成品红（1, 0, 1）。框架还没有 MRU 粒子类型，先用原版尘埃粒子顶上 */
    private static final DustParticleOptions MRU_DUST = new DustParticleOptions(new Vector3f(1F, 0F, 1F), 0.75F);

    private EssenceClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECEssence.CRYSTAL_CONTROLLER_MENU.get(), CrystalControllerScreen::new);
            MenuScreens.register(ECEssence.CRYSTAL_EXTRACTOR_MENU.get(), CrystalExtractorScreen::new);
        });
    }

    /**
     * 永久魔力光源的随机显示刻（原作 BlockMagicLight.randomDisplayTick）：从中心往随机方向飞出 5 个 MRU 粒子，
     * 只有能看见 MRU 的玩家（{@link MRUVision#canSee}，单片镜等）看得到，和原作 ParticleMRU 一样。
     * 只从 MagicLightBlock.animateTick（客户端才调）进来。
     */
    public static void magicLightParticles(Level level, BlockPos pos, RandomSource random) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !MRUVision.canSee(player)) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            // 原作：(1, 1, 1) 随机绕两个轴转，速度 /5
            Vec3 direction = new Vec3(1D, 1D, 1D)
                    .xRot(random.nextFloat() * Mth.TWO_PI)
                    .yRot(random.nextFloat() * Mth.TWO_PI);
            level.addParticle(MRU_DUST, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    direction.x / 5D, direction.y / 5D, direction.z / 5D);
        }
    }
}
