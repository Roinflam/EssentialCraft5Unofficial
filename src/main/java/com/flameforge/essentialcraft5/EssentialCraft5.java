package com.flameforge.essentialcraft5;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.registry.ECRegistries;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 源质魔法5非官方版（EssentialCraft 5 Unofficial）主类。
 * <p>
 * 这是 EssentialCraft 4 Unofficial（TheLMiffy1111，1.12.2）的 1.20.1 移植，EC4U 又源自 Modbder 的 EssentialCraft 3。
 * 原作按 CC BY-SA 4.0 授权，本模组同样按 CC BY-SA 4.0 授权，署名见 CREDITS.md。
 * <p>
 * 原作重度依赖 DummyCore（626 个类里 403 个引用它），这里不移植 DummyCore，
 * 用到的部分按 1.20.1 的写法重写在各自的包里（注册走 DeferredRegister、同步走原版方块实体包、界面见 client/screen）。
 */
@Mod(EssentialCraft5.MODID)
public final class EssentialCraft5 {

    public static final String MODID = "essentialcraft5";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EssentialCraft5(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ECRegistries.register(modBus);
        ECConfig.register(context);
        modBus.addListener(ECCapabilities::register);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ECNetwork.register();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
