package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity.Target;
import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.client.screen.LeyCondenserScreen;
import com.flameforge.essentialcraft5.registry.ECCreative;
import com.flameforge.essentialcraft5.registry.ECCrystalPillars;
import com.flameforge.essentialcraft5.registry.ECLeyCondenser;
import com.flameforge.essentialcraft5.registry.ECLeyProjector;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 展示厅的第九批部分（地脉凝聚器，{@link DevShowroom} 调，只在 -PecShowroom / -PecShowroom9 时跑；batch9 §9.2）。
 * <p>
 * <b>场景</b>（04_scene_*，沿 X 排在方块排北边，镜头从南边朝北拍）：凝下界之星（一圈 8 根水晶柱，米斯莱、淡青、虚空、恶魔各两根，
 * 柱子预先存满 ESPE，旁边无尽 MRU 源供电）远景和近景（头顶的星影、光束、柱顶飘向机器的粒子）；凝粗铁，下面漏斗接魔力箱；
 * 没选目标的凝聚器和投影仪并排，对照换过贴图的外壳。
 * <p>
 * <b>界面</b>（02_gui_*）：凝钻石、工作中的那台；悬停下界之星按钮的价格提示（展示厅假悬停，不动鼠标）；凝下界之星那台（ESPE 球）；
 * 没有地脉（钩子）时的界面（要地脉的图标右上角红点、状态「这里没有地脉」）；凝粗铁那台下面的魔力箱（漏斗抽出来的粗铁）；凝聚器的物品提示。
 * 所有凝聚器都由展示玩家放下（setPlacedBy 记主人），全服 4 台的名额够用。
 */
public final class DevShowroomCondenser {

    private DevShowroomCondenser() {
    }

    /** 全量展示厅里本类占几个场景位（后面的部分从 firstIndex + 这个数开始） */
    static final int SCENE_SLOTS = 3;

    /** 下界之星那圈柱子：米斯莱、淡青、虚空、恶魔各两根（顺序对应 {@link LeyCondenserBlockEntity#SOURCES}） */
    private static List<Supplier<? extends Block>> pillars() {
        return List.of(ECMithriline.MITHRILINE_CRYSTAL_PILLAR, ECMithriline.MITHRILINE_CRYSTAL_PILLAR,
                ECPentacle.PALE_CRYSTAL_PILLAR, ECPentacle.PALE_CRYSTAL_PILLAR,
                ECCrystalPillars.VOID_CRYSTAL_PILLAR, ECCrystalPillars.VOID_CRYSTAL_PILLAR,
                ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR, ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR);
    }

    // ================================================================ 入口

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        BlockPos star = buildStarScene(level, player, sceneBase.apply(firstIndex));
        BlockPos iron = buildIronScene(level, player, sceneBase.apply(firstIndex + 1));
        BlockPos[] gui = buildCompareScene(level, player, sceneBase.apply(firstIndex + 2));
        buildScreens(star, iron, gui[0], gui[1]);
    }

    /** 客户端：场景全部拍完（或中途结束）时把展示用的悬停撤掉 */
    static void clientFinish() {
        LeyCondenserScreen.showroomHover = -1;
    }

    // ================================================================ 小工具

    private static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return DevShowroom.at(base, dx, dy, dz);
    }

    /** 由展示玩家放下一个方块（走 setPlacedBy：记主人、放下时间，三格高的柱子补齐） */
    private static void placeAs(ServerLevel level, ServerPlayer player, BlockPos pos, Block block) {
        BlockState state = block.defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        block.setPlacedBy(level, pos, state, player, new ItemStack(block));
    }

    @Nullable
    private static LeyCondenserBlockEntity condenser(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof LeyCondenserBlockEntity be) {
            return be;
        }
        EssentialCraft5.LOGGER.warn("[showroom] {} 没有地脉凝聚器方块实体", pos);
        return null;
    }

    private static LeyCondenserBlockEntity placeCondenser(ServerLevel level, ServerPlayer player, BlockPos pos, @Nullable Target target) {
        placeAs(level, player, pos, ECLeyCondenser.LEY_CONDENSER.get());
        LeyCondenserBlockEntity be = condenser(level, pos);
        if (be != null) {
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            be.setTarget(target);
        }
        return be;
    }

    // ================================================================ 场景

    /** 凝下界之星：一圈 8 根柱子（存满 ESPE），凝聚器自己的 ESPE 也先放满，东边 4 格一台无尽 MRU 源 */
    private static BlockPos buildStarScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos machine = base.offset(0, 0, 0);
        List<Supplier<? extends Block>> kinds = pillars();
        for (int i = 0; i < LeyCondenserBlockEntity.SOURCES.size(); i++) {
            Vec3i offset = LeyCondenserBlockEntity.SOURCES.get(i);
            BlockPos pillar = machine.offset(offset);
            placeAs(level, player, pillar, kinds.get(i).get());
            if (level.getBlockEntity(pillar) != null) {
                level.getBlockEntity(pillar).getCapability(ECCapabilities.ESPE, null).ifPresent(h -> h.addESPE(h.getMaxESPE(), true));
            }
        }
        level.setBlockAndUpdate(machine.offset(4, 0, 0), ECCreative.INFINITE_MRU_SOURCE.get().defaultBlockState());
        LeyCondenserBlockEntity be = placeCondenser(level, player, machine, Target.NETHER_STAR);
        if (be != null && be.getESPEStorage() != null) {
            be.getESPEStorage().setESPE(be.getESPEStorage().getMaxESPE());
        }
        DevShowroom.scenes.add(new DevShowroom.SceneShot("ley_condenser_star", null, at(machine, 0.5, 5.5, 8.5), at(machine, 0.5, 1.2, 0.5),
                DevShowroom.FIRST_SCENE_WAIT));
        DevShowroom.scenes.add(new DevShowroom.SceneShot("ley_condenser_star_close", null, at(machine, 1.6, 2.2, 3.4), at(machine, 0.5, 1.4, 0.5), 30));
        return machine;
    }

    /** 凝粗铁：机器在上、漏斗朝下、魔力箱在下（漏斗从机器任意一面抽输出格）；西边 4 格一台无尽 MRU 源 */
    private static BlockPos buildIronScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos chest = base;
        BlockPos hopper = base.above();
        BlockPos machine = base.above(2);
        placeAs(level, player, chest, ECStorage.MAGICAL_CHEST.get());
        level.setBlockAndUpdate(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        level.setBlockAndUpdate(base.offset(-4, 0, 0), ECCreative.INFINITE_MRU_SOURCE.get().defaultBlockState());
        placeCondenser(level, player, machine, Target.RAW_IRON);
        DevShowroom.scenes.add(new DevShowroom.SceneShot("ley_condenser_iron", null, at(base, 2.5, 3.6, 5.5), at(base, 0.5, 1.5, 0.5), 200));
        return machine;
    }

    /**
     * 对照：没选目标的凝聚器（看换过贴图的外壳）和投影仪并排；另放两台给界面用（凝钻石、没有地脉），各隔 16 格往北放
     * （同一区块里同一个主人只有一台工作；单人里所有凝聚器的主人都算房主，只能用区块隔开），凝钻石那台旁边一台无尽 MRU 源。
     * 返回 {凝钻石那台, 没有地脉那台}
     */
    private static BlockPos[] buildCompareScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        placeCondenser(level, player, base, null);
        placeAs(level, player, base.offset(2, 0, 0), ECLeyProjector.LEY_PROJECTOR.get());
        if (level.getBlockEntity(base.offset(2, 0, 0)) instanceof LeyProjectorBlockEntity projector) {
            projector.getMRUStorage().setMRU(0);
        }
        BlockPos diamond = base.offset(0, 0, -16);
        BlockPos noLey = base.offset(0, 0, -32);
        level.setBlockAndUpdate(diamond.offset(3, 0, 0), ECCreative.INFINITE_MRU_SOURCE.get().defaultBlockState());
        LeyCondenserBlockEntity working = placeCondenser(level, player, diamond, Target.DIAMOND);
        if (working != null) {
            working.getInventory().setStackInSlot(LeyCondenserBlockEntity.FIRST_OUTPUT, new ItemStack(Items.DIAMOND, 7));
        }
        LeyCondenserBlockEntity dry = placeCondenser(level, player, noLey, Target.RAW_IRON);
        if (dry != null) {
            dry.setLeyForTest(false);
        }
        DevShowroom.scenes.add(new DevShowroom.SceneShot("ley_condenser_compare", null, at(base, 1.5, 1.9, 3.2), at(base, 1.5, 0.5, 0.5), 30));
        return new BlockPos[] {diamond, noLey};
    }

    // ================================================================ 界面

    private static void open(ServerPlayer player, BlockPos pos) {
        if (player.serverLevel().getBlockEntity(pos) instanceof MRUBlockEntity be) {
            NetworkHooks.openScreen(player, be, pos);
        }
    }

    private static void buildScreens(BlockPos star, BlockPos iron, BlockPos diamond, BlockPos noLey) {
        Runnable unhover = () -> Minecraft.getInstance().execute(() -> LeyCondenserScreen.showroomHover = -1);
        DevShowroom.screens.add(new DevShowroom.ScreenShot("ley_condenser_diamond", p -> {
            unhover.run();
            p.teleportTo(diamond.getX() + 0.5, diamond.getY(), diamond.getZ() + 2.5);
        }, p -> open(p, diamond)));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("ley_condenser_hover", p -> {
            p.teleportTo(diamond.getX() + 0.5, diamond.getY(), diamond.getZ() + 2.5);
            Minecraft.getInstance().execute(() -> LeyCondenserScreen.showroomHover = Target.NETHER_STAR.ordinal());
        }, p -> open(p, diamond)));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("ley_condenser_star", p -> {
            unhover.run();
            p.teleportTo(star.getX() + 0.5, star.getY(), star.getZ() + 3.5);
        }, p -> open(p, star)));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("ley_condenser_no_ley", p -> {
            unhover.run();
            p.teleportTo(noLey.getX() + 0.5, noLey.getY(), noLey.getZ() + 2.5);
        }, p -> open(p, noLey)));
        BlockPos chest = iron.below(2);
        DevShowroom.screens.add(new DevShowroom.ScreenShot("ley_condenser_iron_chest", p -> {
            unhover.run();
            p.teleportTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 2.5);
        }, p -> {
            if (p.serverLevel().getBlockEntity(chest) instanceof MenuProvider provider) {
                NetworkHooks.openScreen(p, provider, chest);
            }
        }));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("tooltip_ley_condenser", p -> unhover.run(), p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().clear();
            mc.setScreen(new TooltipShot(new ItemStack(ECLeyCondenser.LEY_CONDENSER.get())));
        })));
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
