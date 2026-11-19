package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.gun.GunAttunement;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.AttunementLook;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;

/**
 * 物品格上的同调阶位小菱形（tools/plan/gear_growth.md 第 5 节）：物品格<b>左上</b>一排，每颗 3×3 像素的十字菱形、步距 3，
 * 只画到当前阶（没亮的不画），颜色是这一阶的名字颜色（{@link AttunementLook#stageColor}，按家族从贴图取的那套），满阶逐颗取这一家族的渐变色。
 * <ul>
 *   <li>不放左下：盔甲有耐久条（y+13~14），右下有数量；</li>
 *   <li>一般从 x+2 起（第 i 颗占 x+2+3i ~ x+4+3i、y+1 ~ y+3）；最高 5 阶的家族从 x+1 起，五颗正好落在 x+1 ~ x+15，不出格；</li>
 *   <li>枪：四颗最右到 x+13，让开右边的热量条（x+14~15，{@code GunsClient.HeatBar}）；左边 x+0~1 画过载充能条（和热量条同样式，
 *       黑底 2 像素宽，充能从下往上涨），菱形从 x+2 起不压它。</li>
 * </ul>
 * 登记表：装饰器要在物品注册以后、标签加载以前登记，按家族标签取不到成员，所以成员的注册名写死在 {@link #MEMBERS}（和 {@code tools/tags/attunement.json} 对应），
 * 找不到的打警告。画的时候再按标签确认是同调家族成员。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AttunementDecorator implements IItemDecorator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 同调家族成员的注册名（本模组命名空间） */
    public static final List<String> MEMBERS = List.of(
            "mhs_helmet", "mhs_chestplate", "mhs_leggings", "mhs_boots",
            "wind_helmet", "wind_chestplate", "wind_leggings", "wind_boots",
            "void_helmet", "void_chestplate", "void_leggings", "void_boots",
            "inquisitorium_helmet", "inquisitorium_chestplate", "inquisitorium_leggings", "inquisitorium_boots",
            "computer_helmet", "computer_chestplate", "computer_leggings", "computer_boots",
            "forged_elemental_sword", "verdict_blade",
            "pistol", "rifle", "sniper", "gatling");

    static final AttunementDecorator INSTANCE = new AttunementDecorator();

    private AttunementDecorator() {
    }

    @SubscribeEvent
    public static void registerAttunementDecorations(RegisterItemDecorationsEvent event) {
        for (String name : MEMBERS) {
            Item item = BuiltInRegistries.ITEM.get(EssentialCraft5.id(name));
            if (item == null || BuiltInRegistries.ITEM.getKey(item).getPath().equals("air")) {
                LOGGER.warn("[EC5] 同调阶位装饰器：找不到物品 {}:{}，这件不画阶位", EssentialCraft5.MODID, name);
                continue;
            }
            event.register(item, INSTANCE);
        }
    }

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        AttunementFamily family = AttunementFamily.of(stack);
        if (family == null) {
            return false;
        }
        int stage = Attunement.stage(stack);
        boolean gun = family.line() == AttunementFamily.Line.GUN;
        if (gun) {
            int max = GunAttunement.chargeMax(stack);
            int charge = GunAttunement.charge(stack);
            if (max > 0 && charge > 0) {
                chargeBar(graphics, x, y, Math.min(1D, charge / (double) max));
            }
        }
        if (stage <= 0) {
            return false;
        }
        int left = family.maxStage() >= 5 && !gun ? x + 1 : x + 2;
        boolean maxed = stage >= family.maxStage();
        int color = AttunementLook.stageColor(family, stage);
        int[] stops = AttunementLook.palette(family).gradient();
        for (int i = 0; i < stage; i++) {
            int c = maxed ? AttunementLook.sample(stops, stage <= 1 ? 1F : i / (float) (stage - 1)) : color;
            diamond(graphics, left + 3 * i, y + 1, 0xFF000000 | c);
        }
        return false;
    }

    /** 3×3 十字菱形，左上角 (left, top) */
    private static void diamond(GuiGraphics graphics, int left, int top, int argb) {
        graphics.fill(RenderType.guiOverlay(), left + 1, top, left + 2, top + 3, argb);
        graphics.fill(RenderType.guiOverlay(), left, top + 1, left + 3, top + 2, argb);
    }

    /** 过载充能条：左边 x+0~1，黑底，MRU 紫罗兰从下往上、充满变矩阵品红（和热量条同尺寸：底 14 高、条 13 高） */
    private static void chargeBar(GuiGraphics graphics, int x, int y, double ratio) {
        int top = (int) Math.round(13D - ratio * 13D);
        graphics.fill(RenderType.guiOverlay(), x, y, x + 2, y + 14, 0xFF000000);
        graphics.fill(RenderType.guiOverlay(), x, y, x + 1, y + 13, 0xFF333333);
        if (top < 13) {
            graphics.fill(RenderType.guiOverlay(), x, y + top, x + 1, y + 13, 0xFF000000 | (ratio >= 1D ? AttunementLook.MAGENTA : AttunementLook.VIOLET));
        }
    }
}
