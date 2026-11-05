package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.MRUConverterBlock;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.Mode;
import com.flameforge.essentialcraft5.compat.botania.ManaBridge;
import com.flameforge.essentialcraft5.menu.MRUConverterMenu;
import com.flameforge.essentialcraft5.registry.ECConverters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * MRU 转换器的魔力输出（植物魔法）GameTest（规划 mru_converter.md §9.2 第 21~27 条）。只在运行期挂了植物魔法时生成
 * （{@code -PbotaniaRuntimeJar=… -PpatchouliRuntimeJar=…}，见 build.gradle）；默认运行里 {@link #converterBotaniaTests()} 返回空表，一条都不跑。
 * <p>
 * <b>这个类不许写任何植物魔法的类型</b>：GameTest 扫描时会加载它，没装植物魔法的运行里一碰就炸。植物魔法的方块按注册名从 Forge 注册表取，
 * 魔力只经 {@link ManaBridge} 读写（{@code find} / {@code accept} / {@code currentMana} / {@code maxMana}），能量走 Forge 的能量能力。
 */
@GameTestHolder(EssentialCraft5.MODID)
public final class ConverterBotaniaTests {

    private static final String EMPTY = EssentialCraft5.MODID + ":empty";
    private static final String BATCH = "defaultBatch";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final BlockPos EAST = new BlockPos(3, 1, 2);

    private ConverterBotaniaTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> converterBotaniaTests() {
        if (!ModList.get().isLoaded(ManaBridge.MODID)) {
            return List.of();
        }
        List<TestFunction> tests = new ArrayList<>();
        add(tests, "manaintopool", 200, ConverterBotaniaTests::manaIntoPool);
        add(tests, "manapoolfullnowaste", 200, ConverterBotaniaTests::manaPoolFullNoWaste);
        add(tests, "manaintospreader", 200, ConverterBotaniaTests::manaIntoSpreader);
        add(tests, "manaintofluxfield", 200, ConverterBotaniaTests::manaIntoFluxfield);
        add(tests, "manarespectscanreceive", 200, ConverterBotaniaTests::manaRespectsCanReceive);
        add(tests, "manabuttonworks", 100, ConverterBotaniaTests::manaButtonWorks);
        add(tests, "manamodeswitchclears", 100, ConverterBotaniaTests::manaModeSwitchClears);
        add(tests, "manadistributornowaste", 200, ConverterBotaniaTests::manaDistributorNoWaste);
        add(tests, "manafluxfieldcapnowaste", 200, ConverterBotaniaTests::manaFluxfieldCapNoWaste);
        return tests;
    }

    private static void add(List<TestFunction> tests, String name, int ticks, Consumer<GameTestHelper> body) {
        tests.add(new TestFunction(BATCH, "converterbotaniatests." + name, EMPTY, ticks, 0L, true, body));
    }

    // ---------------- 工具 ----------------

    private static Block botania(String path) {
        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, path));
        if (block == null || block == Blocks.AIR) {
            throw new IllegalStateException("植物魔法里找不到方块 " + path);
        }
        return block;
    }

    private static Item botaniaItem(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, path));
        if (item == null) {
            throw new IllegalStateException("植物魔法里找不到物品 " + path);
        }
        return item;
    }

    /** 放一台魔力模式的低档转换器（主人随机、MRU 加满），按钮 1 切过去 */
    private static MRUConverterBlockEntity manaConverter(GameTestHelper helper, BlockPos rel) {
        MRUConverterBlockEntity be = ConverterTests.place(helper, rel, ECConverters.MRU_CONVERTER.get(), UUID.randomUUID(), 0L);
        helper.assertTrue(be.setMode(Mode.MANA), "挂了植物魔法时应能切到魔力");
        return be;
    }

    /** 方块实体上的魔力接收方句柄（从转换器那一面看过去） */
    @Nullable
    private static Object handle(GameTestHelper helper, BlockPos rel) {
        BlockEntity be = helper.getBlockEntity(rel);
        return be == null ? null : ManaBridge.get().find(be, Direction.WEST);
    }

    // ---------------- 测试 ----------------

    /** 低档魔力模式贴魔力池：稳定后 100 刻池里多 100 魔力、扣 4 000 MRU（40 MRU = 1 魔力） */
    public static void manaIntoPool(GameTestHelper helper) {
        helper.assertTrue(ManaBridge.get().available(), "挂了植物魔法时桥应可用");
        helper.setBlock(EAST, botania("mana_pool"));
        Object pool = handle(helper, EAST);
        helper.assertTrue(pool != null, "魔力池应挂着魔力接收能力");
        MRUConverterBlockEntity be = manaConverter(helper, CENTER);
        long[] mark = new long[2];
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    mark[0] = ManaBridge.get().currentMana(pool);
                    mark[1] = be.totalMruSpent();
                })
                .thenExecuteAfter(100, () -> {
                    long got = ManaBridge.get().currentMana(pool) - mark[0];
                    long spent = be.totalMruSpent() - mark[1];
                    helper.assertTrue(got == 100, "100 刻池里应多 100 魔力，实际 " + got);
                    helper.assertTrue(spent == 4000, "100 刻应扣 4000 MRU，实际 " + spent);
                })
                .thenSucceed();
    }

    /**
     * 稀释魔力池（上限 1 万）先灌到 9 990：只再推 10，剩下留在缓存，MRU 随之停住，池子不溢出。
     * 魔力池的上限在它第一次 tick 时才定下（之前 getMaxMana 是 −1、isFull 为真，转换器这时不推），所以先等一刻再灌、再放转换器
     */
    public static void manaPoolFullNoWaste(GameTestHelper helper) {
        helper.setBlock(EAST, botania("diluted_pool"));
        Object pool = handle(helper, EAST);
        helper.assertTrue(pool != null, "稀释魔力池应挂着魔力接收能力");
        helper.assertTrue(!ManaBridge.get().receiving(pool), "上限还没定下的魔力池算满，不该收");
        MRUConverterBlockEntity[] be = new MRUConverterBlockEntity[1];
        long[] mark = new long[1];
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(ManaBridge.get().maxMana(pool) == 10_000, "稀释魔力池上限应是 1 万，实际 " + ManaBridge.get().maxMana(pool));
                    ManaBridge.get().accept(pool, 9_990);
                    helper.assertTrue(ManaBridge.get().currentMana(pool) == 9_990, "先灌到 9990");
                    be[0] = manaConverter(helper, CENTER);
                })
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(ManaBridge.get().currentMana(pool) == 10_000, "池子应正好满，实际 " + ManaBridge.get().currentMana(pool));
                    helper.assertTrue(be[0].totalOutput() == 10, "只该推进去 10，实际 " + be[0].totalOutput());
                    helper.assertTrue(be[0].buffer() == be[0].bufferCap(), "剩下的留在缓存");
                    mark[0] = be[0].totalMruSpent();
                })
                .thenExecuteAfter(40, () -> helper.assertTrue(be[0].totalMruSpent() == mark[0], "池满、缓存满以后不该再吃 MRU"))
                .thenSucceed();
    }

    /** 贴魔力发射器：发射器的魔力始终不超过它的上限（推多少按剩余空间算） */
    public static void manaIntoSpreader(GameTestHelper helper) {
        helper.setBlock(EAST, botania("mana_spreader"));
        Object spreader = handle(helper, EAST);
        helper.assertTrue(spreader != null, "魔力发射器应挂着魔力接收能力");
        int max = ManaBridge.get().maxMana(spreader);
        helper.assertTrue(max > 0, "魔力发射器应有上限");
        MRUConverterBlockEntity be = manaConverter(helper, CENTER);
        be.setRatiosForTest(-1, 1000);
        helper.onEachTick(() -> helper.assertTrue(ManaBridge.get().currentMana(spreader) <= max,
                "发射器的魔力不该超过上限 " + max + "，实际 " + ManaBridge.get().currentMana(spreader)));
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(be.totalOutput() > 0, "应推进去一些");
            helper.succeed();
        });
    }

    /** 贴魔力通量场：通量场的能量按 1 魔力 = 10 FE 增加（核对手册里的比价） */
    public static void manaIntoFluxfield(GameTestHelper helper) {
        helper.setBlock(EAST, botania("mana_fluxfield"));
        BlockEntity flux = helper.getBlockEntity(EAST);
        IEnergyStorage energy = flux == null ? null : flux.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElse(null);
        helper.assertTrue(energy != null, "通量场应挂着能量能力");
        MRUConverterBlockEntity be = manaConverter(helper, CENTER);
        long[] mark = new long[2];
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    mark[0] = energy.getEnergyStored();
                    mark[1] = be.totalOutput();
                })
                .thenExecuteAfter(40, () -> {
                    long fe = energy.getEnergyStored() - mark[0];
                    long mana = be.totalOutput() - mark[1];
                    helper.assertTrue(mana > 0 && fe == mana * 10, "通量场应按 1 魔力 = 10 FE 增加，实际 " + mana + " 魔力 → " + fe + " FE");
                })
                .thenSucceed();
    }

    /** 泰拉凝聚板没摆配方时不推（不收）；摆上三样原料以后开始推 */
    public static void manaRespectsCanReceive(GameTestHelper helper) {
        helper.setBlock(EAST, botania("terra_plate"));
        Object plate = handle(helper, EAST);
        helper.assertTrue(plate != null, "泰拉凝聚板应挂着魔力接收能力");
        MRUConverterBlockEntity be = manaConverter(helper, CENTER);
        helper.startSequence()
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(!ManaBridge.get().receiving(plate) && ManaBridge.get().currentMana(plate) == 0, "没摆配方时不该收");
                    helper.assertTrue(be.totalOutput() == 0, "没摆配方时不该推");
                    BlockPos abs = helper.absolutePos(EAST);
                    for (String path : List.of("manasteel_ingot", "mana_diamond", "mana_pearl")) {
                        ItemEntity item = new ItemEntity(helper.getLevel(), abs.getX() + 0.5D, abs.getY() + 0.2D, abs.getZ() + 0.5D,
                                new ItemStack(botaniaItem(path)));
                        item.setDeltaMovement(0D, 0D, 0D);
                        helper.getLevel().addFreshEntity(item);
                    }
                })
                .thenExecuteAfter(30, () -> helper.assertTrue(ManaBridge.get().currentMana(plate) > 0, "摆上配方以后应开始推"))
                .thenSucceed();
    }

    /**
     * 贴魔力分配器、分配器两边各一个没满的魔力池：分配器把收到的按池子数整除分下去（1 ÷ 2 = 0），存量恒为 0，认不出收了多少。
     * 转换器推过一次就不再往那一面推，不会一直白吃 MRU、显示「工作中」：只记那一次的量，之后旁边没人收、缓存满了就停
     */
    public static void manaDistributorNoWaste(GameTestHelper helper) {
        helper.setBlock(EAST, botania("mana_distributor"));
        helper.setBlock(EAST.north(), botania("mana_pool"));
        helper.setBlock(EAST.south(), botania("mana_pool"));
        MRUConverterBlockEntity[] be = new MRUConverterBlockEntity[1];
        long[] mark = new long[1];
        helper.startSequence()
                .thenExecuteAfter(2, () -> be[0] = manaConverter(helper, CENTER))
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(be[0].totalOutput() <= 1, "分配器那一面只该推一次（低档 1 魔力），实际 " + be[0].totalOutput());
                    helper.assertTrue(be[0].status() == MRUConverterBlockEntity.Status.NO_RECEIVER, "之后算旁边没人收，实际 " + be[0].status());
                    mark[0] = be[0].totalMruSpent();
                })
                .thenExecuteAfter(40, () -> helper.assertTrue(be[0].totalMruSpent() == mark[0], "缓存满了以后不该再吃 MRU"))
                .thenSucceed();
    }

    /**
     * 魔力通量场快满时会截掉超出 12 800 FE 的部分：先灌到 12 700 FE，转换器（每刻 160 魔力）只该记实收的 10 魔力，
     * 其余留在缓存，扣的 MRU 和送出去的对得上
     */
    public static void manaFluxfieldCapNoWaste(GameTestHelper helper) {
        helper.setBlock(EAST, botania("mana_fluxfield"));
        Object flux = handle(helper, EAST);
        helper.assertTrue(flux != null, "通量场应挂着魔力接收能力");
        MRUConverterBlockEntity[] be = new MRUConverterBlockEntity[1];
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    ManaBridge.get().accept(flux, 1270);
                    helper.assertTrue(ManaBridge.get().currentMana(flux) == 1270, "先灌到 12 700 FE，实际 " + ManaBridge.get().currentMana(flux) + " 魔力");
                    be[0] = manaConverter(helper, CENTER);
                    be[0].setRatiosForTest(-1, 1000);
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(ManaBridge.get().currentMana(flux) == 1280, "通量场应正好满，实际 " + ManaBridge.get().currentMana(flux));
                    helper.assertTrue(be[0].totalOutput() == 10, "只该记实收的 10 魔力，实际 " + be[0].totalOutput());
                    ConverterTests.assertConserved(helper, be[0], 1000, "通量场截掉的不算送出去");
                })
                .thenSucceed();
    }

    /** 挂了植物魔法时按钮 1 成功、方块状态 mode=mana */
    public static void manaButtonWorks(GameTestHelper helper) {
        MRUConverterBlockEntity be = ConverterTests.place(helper, CENTER, ECConverters.MRU_CONVERTER.get(), UUID.randomUUID(), 0L);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        MRUConverterMenu menu = new MRUConverterMenu(0, player.getInventory(), be);
        helper.assertTrue(menu.botaniaAvailable() && menu.modeUsable(Mode.MANA), "菜单应同步「植物魔法可用」");
        helper.assertTrue(menu.clickMenuButton(player, Mode.MANA.ordinal()), "按钮 1 应成功");
        helper.assertTrue(be.mode() == Mode.MANA && be.getBlockState().getValue(MRUConverterBlock.MODE) == Mode.MANA, "方块状态应是 mode=mana");
        helper.succeed();
    }

    /** 真桥：能量模式攒下的缓存和零头，切到魔力以后清零 */
    public static void manaModeSwitchClears(GameTestHelper helper) {
        MRUConverterBlockEntity be = ConverterTests.place(helper, CENTER, ECConverters.MRU_CONVERTER.get(), UUID.randomUUID(), 0L);
        be.setRatiosForTest(37, -1);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.buffer() > 0 || be.carry() > 0, "能量模式应先攒下一些");
            helper.assertTrue(be.setMode(Mode.MANA), "切到魔力应成功");
            helper.assertTrue(be.buffer() == 0 && be.carry() == 0, "切换应清掉缓存和零头");
            helper.succeed();
        });
    }
}
