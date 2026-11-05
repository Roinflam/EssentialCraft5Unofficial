package com.flameforge.essentialcraft5.gun;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * 铁砧修枪（ECGuns，原作 {@code ECEventHandler.anvilEvent} L137~157）：左格是组装好、有磨损的枪，右格是<b>枪托那种材料</b>的物品
 * → 出一把磨损清零的枪，花 {@value #COST} 级经验、1 个材料。改名照原版铁砧的规则一起生效。
 * <p>
 * 原作右格放任意一种枪身材料都能修，和原作手册「用枪托同种材料修」（{@code essentialcraft.page.weaponMaker_11}）不符，这里按手册；
 * 没磨损的枪不给修（原作照样出一把一模一样的枪、白扣经验）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class GunAnvilEvents {

    /** 修一次的经验等级（原作 setCost(4)） */
    public static final int COST = 4;

    private GunAnvilEvents() {
    }

    @SubscribeEvent
    public static void onGunAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty() || GunType.of(left).isEmpty() || GunParts.wear(left) <= 0) {
            return;
        }
        Optional<GunParts> parts = GunParts.read(left);
        if (parts.isEmpty()) {
            return;
        }
        Optional<GunMaterials.Body> base = GunMaterials.body(parts.get().base());
        Optional<GunMaterials.Body> offered = GunMaterials.bodyByItem(right);
        if (base.isEmpty() || offered.isEmpty() || !base.get().id().equals(offered.get().id())) {
            return;
        }
        ItemStack result = left.copy();
        result.setCount(1);
        GunParts.setWear(result, 0);
        String name = event.getName();
        if (name != null && !Util.isBlank(name)) {
            if (!name.equals(left.getHoverName().getString())) {
                result.setHoverName(Component.literal(name));
            }
        } else if (left.hasCustomHoverName()) {
            result.resetHoverName();
        }
        event.setOutput(result);
        event.setCost(COST);
        event.setMaterialCost(1);
    }
}
