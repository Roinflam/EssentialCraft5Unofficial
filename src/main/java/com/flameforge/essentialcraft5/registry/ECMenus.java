package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.menu.ChargingChamberMenu;
import com.flameforge.essentialcraft5.menu.MoonWellMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.RegistryObject;

/** 菜单类型。原作的 GUI 走 IGuiHandler + guiID，这里每台机器一个 MenuType。 */
public final class ECMenus {

    public static final RegistryObject<MenuType<MoonWellMenu>> MOON_WELL = register("moon_well", MoonWellMenu::new);
    public static final RegistryObject<MenuType<ChargingChamberMenu>> CHARGING_CHAMBER =
            register("charging_chamber", ChargingChamberMenu::new);

    private ECMenus() {
    }

    static void init() {
    }

    /** 给模块用：注册菜单类型（客户端工厂读 BlockPos，见 MachineMenu） */
    public static <M extends AbstractContainerMenu> RegistryObject<MenuType<M>> register(String name, IContainerFactory<M> factory) {
        return ECRegistries.MENU_TYPES.register(name, () -> IForgeMenuType.create(factory));
    }
}
