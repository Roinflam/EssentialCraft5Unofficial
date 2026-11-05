package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.CurioSealClient;
import com.flameforge.essentialcraft5.client.screen.ComputerTerminalScreen;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.bauble.SpecialBaubleItem;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.Entry;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.FieldState;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.TargetState;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECComputer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * 展示厅的第十批 10a 部分（饰品封禁场与宅男终端，只在 -PecShowroom10a 时跑；batch10 §5.4 第 5 条）。
 * <p>
 * 展示玩家穿满电的宅男全套、饰品栏里戴几件本模组的饰品（项链 ζ、腰带 θ、两个戒指 ω / σ），被压制的样子用客户端覆盖
 * {@link CurioSealClient#setShowroomSealed}（只改本客户端画面、不发包；来源写成「Steve」），终端名单用 {@link CurioSealClient#setShowroomTerminal}。
 * 两样覆盖在每一张自己的图之前设，展示厅退出时不用撤（全量展示厅不跑这一部分：覆盖会让本地玩家的饰品按被压制处理，影响后面别的批次的画面）。
 * <ul>
 *   <li>场景 {@code 04_scene_seal_hud}：开着 HUD 的第一人称，快捷栏左边的禁用图标、屏幕顶上亮着的紫色小圆。</li>
 *   <li>界面 {@code 02_gui_curios_sealed}（Curios 饰品栏，功能槽罩暗紫 + 禁用图标）、{@code 02_gui_curios_sealed_tooltip}（饰品栏里那件 ω 指环的提示框，
 *       多两行「被压制」「来自 Steve」；容器界面里的假鼠标没有做，提示框用单独的画面画同一个物品对象）、
 *       {@code 02_gui_inventory_curios_button_sealed}（背包里 Curios 按钮右上角的角标；这一张临时换成生存模式，创造模式开的是创造栏）、{@code 02_gui_computer_terminal}（终端，名单五种状态各一行）。</li>
 * </ul>
 * 手册页（{@code 03_guide_computer_seal_field_*}、{@code 03_guide_from_ec4_*}）见 {@link DevShowroom} 的 {@code sealGuidePages}。
 */
public final class DevShowroomSeal {

    private DevShowroomSeal() {
    }

    /** 本类占几个场景位 */
    static final int SCENE_SLOTS = 1;
    private static final List<String> SOURCES = List.of("Steve");

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        BlockPos base = sceneBase.apply(firstIndex);
        dress(player);
        Consumer<Minecraft> hudClient = mc -> {
            applyOverrides();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
        };
        DevShowroom.scenes.add(new DevShowroom.SceneShot("seal_hud", DevShowroomSeal::dress, DevShowroom.at(base, 0.5, 1.6, 4.5),
                DevShowroom.at(base, 0.5, 1.0, -6), DevShowroom.FIRST_SCENE_WAIT, hudClient, null));
        buildScreens();
    }

    /** 客户端覆盖：被压制（来源 Steve）、终端在运转、名单五种状态各一行 */
    private static void applyOverrides() {
        CurioSealClient.setShowroomSealed(true, SOURCES);
        CurioSealClient.setShowroomTerminal(new S2CComputerTerminalPacket((byte) FieldState.RUNNING.ordinal(), (byte) 0, 600, 735_000L, 2, true,
                List.of(new Entry("Steve", (byte) TargetState.SEALED.ordinal(), 6, ""),
                        new Entry("Alex", (byte) TargetState.SEALED_SHARED.ordinal(), 11, "Notch"),
                        new Entry("Kai", (byte) TargetState.NO_FIGHT.ordinal(), 14, ""),
                        new Entry("Sunny", (byte) TargetState.CANT_HARM.ordinal(), 19, ""),
                        new Entry("Ari", (byte) TargetState.NO_PERMISSION.ordinal(), 27, ""))));
    }

    /** 服务端：穿满电的宅男全套，饰品栏里戴 ζ、θ、ω、σ */
    private static void dress(ServerPlayer player) {
        for (RegistryObject<? extends ComputerArmorItem> piece : ECComputer.armor()) {
            ComputerArmorItem item = piece.get();
            player.setItemSlot(item.getEquipmentSlot(), MRUStaffItem.full(item));
        }
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (inventory == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 展示玩家没有 Curios 饰品栏");
            return;
        }
        wear(inventory, ECBaubles.ZETA_AMULET, 0);
        wear(inventory, ECBaubles.THETA_BELT, 0);
        wear(inventory, ECBaubles.OMEGA_RING, 0);
        wear(inventory, ECBaubles.SIGMA_RING, 1);
        CuriosCompat.invalidate(player);
    }

    private static void wear(ICuriosItemHandler inventory, RegistryObject<? extends SpecialBaubleItem> item, int index) {
        SpecialBaubleItem bauble = item.get();
        ICurioStacksHandler handler = inventory.getCurios().get(bauble.slot());
        if (handler == null || handler.getSlots() <= index) {
            EssentialCraft5.LOGGER.warn("[showroom] 饰品栏 {} 没有第 {} 格", bauble.slot(), index + 1);
            return;
        }
        handler.getStacks().setStackInSlot(index, new ItemStack(bauble));
    }

    // ================================================================ 界面

    private static void buildScreens() {
        DevShowroom.screens.add(new DevShowroom.ScreenShot("curios_sealed", p -> {
            dress(p);
            Minecraft.getInstance().execute(DevShowroomSeal::applyOverrides);
        }, DevShowroomSeal::openCurios));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("curios_sealed_tooltip", p -> Minecraft.getInstance().execute(DevShowroomSeal::applyOverrides),
                p -> Minecraft.getInstance().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.getToasts().clear();
                    ItemStack worn = ItemStack.EMPTY;
                    if (mc.player != null) {
                        worn = CuriosApi.getCuriosInventory(mc.player).resolve()
                                .flatMap(inventory -> inventory.getStacksHandler(ECBaubles.OMEGA_RING.get().slot()))
                                .map(handler -> handler.getStacks().getStackInSlot(0)).orElse(ItemStack.EMPTY);
                    }
                    mc.setScreen(new TooltipShot(worn));
                })));
        // 创造模式开背包会换成创造栏（那里不在「物品栏」页时没有 Curios 按钮），这一张临时换成生存模式，下一张换回来
        DevShowroom.screens.add(new DevShowroom.ScreenShot("inventory_curios_button_sealed", p -> {
            p.setGameMode(GameType.SURVIVAL);
            Minecraft.getInstance().execute(DevShowroomSeal::applyOverrides);
        },
                p -> Minecraft.getInstance().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.getToasts().clear();
                    if (mc.player != null) {
                        mc.setScreen(new InventoryScreen(mc.player));
                    }
                })));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("computer_terminal", p -> {
            p.setGameMode(GameType.CREATIVE);
            Minecraft.getInstance().execute(DevShowroomSeal::applyOverrides);
        },
                p -> Minecraft.getInstance().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.getToasts().clear();
                    mc.setScreen(new ComputerTerminalScreen());
                })));
    }

    /** 开 Curios 饰品栏：容器在 Curios 的运行时包里（编译只依赖它的 api），按名字建它的 MenuProvider，由服务端开 */
    private static void openCurios(ServerPlayer player) {
        try {
            Object provider = Class.forName("top.theillusivec4.curios.common.inventory.container.CuriosContainerProvider")
                    .getConstructor().newInstance();
            NetworkHooks.openScreen(player, (MenuProvider) provider);
        } catch (ReflectiveOperationException | ClassCastException e) {
            EssentialCraft5.LOGGER.warn("[showroom] 打不开 Curios 饰品栏：{}", e.toString());
        }
    }

    /** 只画一个物品图标和它的提示框（用游戏自己的提示内容和样式），截图用，不动鼠标 */
    private static final class TooltipShot extends Screen {
        private final ItemStack stack;

        TooltipShot(ItemStack stack) {
            super(Component.empty());
            this.stack = stack;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.renderItem(stack, 16, 16);
            graphics.renderTooltip(font, stack, 30, 30);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
