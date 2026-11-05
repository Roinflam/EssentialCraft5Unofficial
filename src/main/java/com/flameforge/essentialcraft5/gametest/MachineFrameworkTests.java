package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.blockentity.ChargingChamberBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.espe.ESPEAccess;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.menu.MachineData;
import com.flameforge.essentialcraft5.registry.ECBlockEntities;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * 二批框架「机器与界面」部分的测试：MRUBlockEntity 的可选 ESPE（存读档、按面暴露、菜单同步）、ESPETransfer 抽取、
 * 模块自建配方类型的注册入口。界面元素和颜色处理器是客户端类，专用服测不了（加载就会报错），靠 runClient -PecShowroom 看。
 * <p>
 * 一批没有带 ESPE 的机器，这里用一个只在测试里的 {@link TestESPEMachine}：借充能仓的方块实体类型，
 * 需要放进世界时先放一个充能仓，再用 {@code level.setBlockEntity} 换成测试机器（充能仓的 ticker 按 MRUBlockEntity 调，照样能 tick）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MachineFrameworkTests {

    private static final String EMPTY = "empty";
    private static final double EPSILON = 1e-9;

    private MachineFrameworkTests() {
    }

    /** 只在测试里用的带 ESPE 的机器：没有物品栏、MRU 上限 1000，按面规则由构造时给定 */
    private static final class TestESPEMachine extends MRUBlockEntity {
        private final Function<Direction, ESPEAccess> access;

        TestESPEMachine(BlockPos pos, double maxESPE, Function<Direction, ESPEAccess> access) {
            super(ECBlockEntities.CHARGING_CHAMBER.get(), pos, chamberState(), () -> 1000, 0, () -> maxESPE, 3);
            this.access = access;
        }

        @Override
        protected ESPEAccess espeAccess(@Nullable Direction side) {
            return access.apply(side);
        }

        @Override
        public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return null;
        }
    }

    private static BlockState chamberState() {
        return ECBlocks.CHARGING_CHAMBER.get().defaultBlockState();
    }

    private static TestESPEMachine machine(double maxESPE, Function<Direction, ESPEAccess> access) {
        return new TestESPEMachine(BlockPos.ZERO, maxESPE, access);
    }

    /** 在 relative 处放一台测试机器（先放充能仓再换方块实体），返回它 */
    private static TestESPEMachine placeMachine(GameTestHelper helper, BlockPos relative, double maxESPE, double espe,
                                                Function<Direction, ESPEAccess> access) {
        helper.setBlock(relative, ECBlocks.CHARGING_CHAMBER.get());
        TestESPEMachine be = new TestESPEMachine(helper.absolutePos(relative), maxESPE, access);
        helper.getLevel().setBlockEntity(be);
        be.getESPEStorage().setESPE(espe);
        helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(relative)) == be, "测试机器应已放进世界");
        return be;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }

    /** 不开 ESPE 的机器（一批的全部）：不存 espe_storage、不暴露 ESPE capability、菜单格数不变 */
    @GameTest(template = EMPTY)
    public static void machineWithoutESPEUnchanged(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ECBlocks.CHARGING_CHAMBER.get());
        ChargingChamberBlockEntity be = (ChargingChamberBlockEntity) helper.getBlockEntity(pos);

        helper.assertTrue(!be.hasESPE() && be.getESPEStorage() == null, "充能仓不应有 ESPE");
        helper.assertTrue(!be.getCapability(ECCapabilities.ESPE, null).isPresent(), "不应暴露 ESPE capability（null 面）");
        for (Direction side : Direction.values()) {
            helper.assertTrue(!be.getCapability(ECCapabilities.ESPE, side).isPresent(), "不应暴露 ESPE capability：" + side);
        }
        helper.assertTrue(!be.saveWithoutMetadata().contains("espe_storage"), "存档里不应有 espe_storage");
        helper.assertTrue(MachineData.server(be).getCount() == MachineData.BASE_COUNT, "菜单格数应不变");
        helper.assertTrue(MachineData.client(0).getCount() == MachineData.BASE_COUNT, "客户端格数应不变");
        helper.succeed();
    }

    /** ESPE 存读档；旧存档没有 espe_storage 时按 0；NaN 读成 0 */
    @GameTest(template = EMPTY)
    public static void espeSavesAndLoads(GameTestHelper helper) {
        TestESPEMachine be = machine(10000D, side -> ESPEAccess.BOTH);
        helper.assertTrue(be.hasESPE() && be.getESPEStorage().getTier() == 3, "测试机器应有 ESPE、档位 3");
        be.getESPEStorage().setESPE(1234.56D);
        CompoundTag saved = be.saveWithoutMetadata();
        helper.assertTrue(saved.contains("espe_storage"), "存档里应有 espe_storage");

        TestESPEMachine loaded = machine(10000D, side -> ESPEAccess.BOTH);
        loaded.load(saved);
        helper.assertTrue(near(loaded.getESPEStorage().getESPE(), 1234.56D), "读档后应是 1234.56，实际 " + loaded.getESPEStorage().getESPE());

        // 旧存档：这台机器当时还没有 ESPE
        CompoundTag old = saved.copy();
        old.remove("espe_storage");
        loaded.load(old);
        helper.assertTrue(loaded.getESPEStorage().getESPE() == 0D, "没有 espe_storage 时应为 0");

        CompoundTag nan = saved.copy();
        nan.getCompound("espe_storage").putDouble("espe", Double.NaN);
        loaded.load(nan);
        helper.assertTrue(loaded.getESPEStorage().getESPE() == 0D, "NaN 应读成 0");

        // 上限被调低：原样读入，读数按新上限夹住
        TestESPEMachine smaller = machine(1000D, side -> ESPEAccess.BOTH);
        smaller.load(saved);
        helper.assertTrue(smaller.getESPEStorage().getESPE() == 1000D, "超过上限应夹到 1000，实际 " + smaller.getESPEStorage().getESPE());
        helper.succeed();
    }

    /** 按面暴露：NONE 面没有 capability；只进不出的面加得进、抽不走、setESPE 不能调低；只出不进的面反过来 */
    @GameTest(template = EMPTY)
    public static void espeSidedAccess(GameTestHelper helper) {
        TestESPEMachine be = machine(1000D, side -> {
            if (side == Direction.UP) {
                return ESPEAccess.NONE;
            }
            return side == Direction.DOWN ? ESPEAccess.EXTRACT : ESPEAccess.INSERT;
        });
        ESPEStorage storage = be.getESPEStorage();
        storage.setESPE(500D);

        helper.assertTrue(!be.getCapability(ECCapabilities.ESPE, Direction.UP).isPresent(), "UP 面不应暴露");

        IESPEHandler insertOnly = be.getCapability(ECCapabilities.ESPE, Direction.NORTH).orElseThrow(IllegalStateException::new);
        helper.assertTrue(near(insertOnly.getESPE(), 500D) && near(insertOnly.getMaxESPE(), 1000D), "读数应照实返回");
        helper.assertTrue(near(insertOnly.addESPE(100D, true), 0D) && near(storage.getESPE(), 600D), "只进的面应能加");
        helper.assertTrue(insertOnly.extractESPE(100D, true) == 0D && near(storage.getESPE(), 600D), "只进的面不应能抽");
        insertOnly.setESPE(0D);
        helper.assertTrue(near(storage.getESPE(), 600D), "只进的面不能用 setESPE 调低");
        insertOnly.setESPE(700D);
        helper.assertTrue(near(storage.getESPE(), 700D), "只进的面能用 setESPE 调高");

        IESPEHandler nullSide = be.getCapability(ECCapabilities.ESPE, null).orElseThrow(IllegalStateException::new);
        helper.assertTrue(nullSide.extractESPE(100D, true) == 0D, "null 面同样按规则（这里是只进）");

        IESPEHandler extractOnly = be.getCapability(ECCapabilities.ESPE, Direction.DOWN).orElseThrow(IllegalStateException::new);
        helper.assertTrue(near(extractOnly.addESPE(50D, true), 50D) && near(storage.getESPE(), 700D), "只出的面不应能加");
        helper.assertTrue(near(extractOnly.extractESPE(200D, true), 200D) && near(storage.getESPE(), 500D), "只出的面应能抽");

        // 机器自己的存储不受限
        helper.assertTrue(near(storage.extractESPE(500D, true), 500D), "机器自己应能随意取");

        // 失效后同一面的 LazyOptional 不再可用（方块被拆）
        var cap = be.getCapability(ECCapabilities.ESPE, Direction.NORTH);
        be.setRemoved();
        helper.assertTrue(!cap.isPresent(), "移除后旧的 capability 应失效");
        helper.assertTrue(!be.getCapability(ECCapabilities.ESPE, Direction.NORTH).isPresent(), "移除后不应再给新的");
        be.clearRemoved();
        helper.assertTrue(be.getCapability(ECCapabilities.ESPE, Direction.NORTH).isPresent(), "放回后应能重新取到");
        helper.succeed();
    }

    /** 菜单同步：ESPE 追加在额外数值之后，额外数值序号不变；×100 定点精度 0.01 */
    @GameTest(template = EMPTY)
    public static void espeMachineDataSync(GameTestHelper helper) {
        TestESPEMachine be = machine(10000D, side -> ESPEAccess.BOTH);
        be.getESPEStorage().setESPE(1234.56D);
        be.getMRUStorage().setMRU(777);

        IntSupplier[] big = MachineData.wide(() -> 70000);
        IntSupplier[] fixed = MachineData.wideFixed(() -> 60.25D);
        MachineData server = MachineData.serverWithESPE(be, big[0], big[1], () -> -5, fixed[0], fixed[1]);
        MachineData client = MachineData.clientWithESPE(5);
        helper.assertTrue(server.getCount() == MachineData.BASE_COUNT + 5 + MachineData.ESPE_COUNT, "服务端格数应为 7 + 5 + 4，实际 " + server.getCount());
        helper.assertTrue(client.getCount() == server.getCount(), "两端格数应一致");
        for (int i = 0; i < server.getCount(); i++) {
            // 原版菜单数据包按 short 传
            client.set(i, (short) server.get(i));
        }
        helper.assertTrue(client.hasESPE(), "客户端应带 ESPE");
        helper.assertTrue(near(client.espe(), 1234.56D), "ESPE 应同步为 1234.56，实际 " + client.espe());
        helper.assertTrue(near(client.maxESPE(), 10000D), "ESPE 上限应同步为 10000，实际 " + client.maxESPE());
        helper.assertTrue(client.extraWide(0) == 70000, "额外数值（wide）序号应不受影响");
        helper.assertTrue(client.extra(2) == -5, "额外数值（short）应为 -5，实际 " + client.extra(2));
        helper.assertTrue(near(client.extraFixed(3), 60.25D), "wideFixed 应还原 60.25，实际 " + client.extraFixed(3));
        helper.assertTrue(client.mru() == 777 && client.maxMRU() == 1000, "MRU 应照常同步");

        // 不带 ESPE 的工厂方法：即使方块实体有 ESPE 也不追加（两端显式成对）
        helper.assertTrue(MachineData.server(be).getCount() == MachineData.BASE_COUNT, "server() 不应追加 ESPE 格");
        helper.assertTrue(MachineData.server(be).espe() == 0D, "不带 ESPE 的菜单读 ESPE 为 0");
        helper.succeed();
    }

    /**
     * ESPE 同步的范围：到 {@link MachineData#MAX_SYNCED_ESPE}（米斯莱熔炉 max_espe 配置的上限）为止原样同步，精度 0.01；
     * 超出定点范围的夹到约 2147 万，不会溢出成负数。
     */
    @GameTest(template = EMPTY)
    public static void espeMachineDataRange(GameTestHelper helper) {
        double max = MachineData.MAX_SYNCED_ESPE;
        TestESPEMachine be = machine(max, side -> ESPEAccess.BOTH);
        be.getESPEStorage().setESPE(max - 0.01D);
        MachineData client = syncESPE(be);
        helper.assertTrue(client.maxESPE() == max, "上限 " + max + " 应原样同步，实际 " + client.maxESPE());
        helper.assertTrue(client.espe() == max - 0.01D, "ESPE 应同步为 " + (max - 0.01D) + "，实际 " + client.espe());

        TestESPEMachine huge = machine(1.0E8D, side -> ESPEAccess.BOTH);
        huge.getESPEStorage().setESPE(5.0E7D);
        MachineData clamped = syncESPE(huge);
        double limit = Integer.MAX_VALUE / MachineData.FIXED_SCALE;
        helper.assertTrue(clamped.maxESPE() == limit && clamped.espe() == limit,
                "超出定点范围应夹到 " + limit + "，实际 " + clamped.espe() + "/" + clamped.maxESPE());
        helper.assertTrue(limit >= max, "MAX_SYNCED_ESPE 应在定点范围内");
        helper.succeed();
    }

    /** 不带额外数值的 ESPE 菜单：服务端数值按 short 传给客户端，返回客户端那份 */
    private static MachineData syncESPE(MRUBlockEntity be) {
        MachineData server = MachineData.serverWithESPE(be);
        MachineData client = MachineData.clientWithESPE(0);
        for (int i = 0; i < server.getCount(); i++) {
            client.set(i, (short) server.get(i));
        }
        return client;
    }

    /** ESPE 显示格式 */
    @GameTest(template = EMPTY)
    public static void espeFormat(GameTestHelper helper) {
        helper.assertTrue(ESPEStorage.format(1234.56D).equals("1234.56"), "应为 1234.56，实际 " + ESPEStorage.format(1234.56D));
        helper.assertTrue(ESPEStorage.format(60D).equals("60"), "整数不带小数点，实际 " + ESPEStorage.format(60D));
        helper.assertTrue(ESPEStorage.format(10000D).equals("10000"), "不分组，实际 " + ESPEStorage.format(10000D));
        helper.assertTrue(ESPEStorage.format(Double.NaN).equals("0"), "NaN 显示 0");
        helper.succeed();
    }

    /**
     * 从周围抽 ESPE：只抽到满、跳过只进不出的源、按源头朝向抽取方的那一面查、空气和没有 ESPE 的机器跳过。
     * 源头布置（相对抽取方 (2,1,2)，按遍历顺序）：正上方 300（只有 DOWN 面给）、(1,0,1) 500（只进不出）、(1,0,-1) 普通充能仓、(-1,0,-1) 空气、(-1,0,1) 800。
     */
    @GameTest(template = EMPTY)
    public static void espePullFromAround(GameTestHelper helper) {
        BlockPos self = new BlockPos(2, 1, 2);
        TestESPEMachine above = placeMachine(helper, self.above(), 1000D, 300D,
                side -> side == Direction.DOWN ? ESPEAccess.BOTH : ESPEAccess.NONE);
        TestESPEMachine insertOnly = placeMachine(helper, self.offset(1, 0, 1), 1000D, 500D, side -> ESPEAccess.INSERT);
        TestESPEMachine rich = placeMachine(helper, self.offset(-1, 0, 1), 1000D, 800D, side -> ESPEAccess.BOTH);
        helper.setBlock(self.offset(1, 0, -1), ECBlocks.CHARGING_CHAMBER.get());

        TestESPEMachine sink = new TestESPEMachine(helper.absolutePos(self), 1000D, side -> ESPEAccess.INSERT);
        // 普通充能仓和空气排在第三个源之前，确认它们被跳过而不是中断遍历
        Vec3i[] offsets = {new Vec3i(0, 1, 0), new Vec3i(1, 0, 1), new Vec3i(1, 0, -1), new Vec3i(-1, 0, -1), new Vec3i(-1, 0, 1)};
        double pulled = ESPETransfer.pullFromAround(helper.getLevel(), helper.absolutePos(self), sink.getESPEStorage(), offsets);

        helper.assertTrue(near(pulled, 1000D), "应抽到 1000（抽满为止），实际 " + pulled);
        helper.assertTrue(near(sink.getESPEStorage().getESPE(), 1000D), "抽取方应满");
        helper.assertTrue(near(above.getESPEStorage().getESPE(), 0D), "正上方的源应被抽空（按 DOWN 面查），剩 " + above.getESPEStorage().getESPE());
        helper.assertTrue(near(insertOnly.getESPEStorage().getESPE(), 500D), "只进不出的源不应被抽");
        helper.assertTrue(near(rich.getESPEStorage().getESPE(), 100D), "第三个源应只被抽走缺的 700，剩 " + rich.getESPEStorage().getESPE());

        // 满了再抽什么也不动
        double again = ESPETransfer.pullFromAround(helper.getLevel(), helper.absolutePos(self), sink.getESPEStorage(), offsets);
        helper.assertTrue(again == 0D && near(rich.getESPEStorage().getESPE(), 100D), "满了不应再抽");
        // 源头就是自己时跳过
        helper.assertTrue(ESPETransfer.pull(helper.getLevel(), helper.absolutePos(self.above()), above.getESPEStorage(),
                helper.absolutePos(self.above())) == 0D, "不应从自己抽");
        helper.assertTrue(ESPETransfer.sideFacing(helper.absolutePos(self.above()), helper.absolutePos(self)) == Direction.DOWN,
                "正上方的源朝向抽取方的应是 DOWN 面");
        helper.succeed();
    }

    /** 模块自建配方类型走的 ECRecipes.type / serializer：一批的两种照常注册在 essentialcraft5 名下 */
    @GameTest(template = EMPTY)
    public static void recipeTypesRegistered(GameTestHelper helper) {
        helper.assertTrue(EssentialCraft5.id("magician_table").equals(ForgeRegistries.RECIPE_TYPES.getKey(ECRecipes.MAGICIAN_TABLE.get())),
                "魔力工作台配方类型应注册为 essentialcraft5:magician_table");
        helper.assertTrue(EssentialCraft5.id("radiating_chamber").equals(ForgeRegistries.RECIPE_SERIALIZERS.getKey(ECRecipes.RADIATING_CHAMBER_SERIALIZER.get())),
                "辐射仓序列化器应注册为 essentialcraft5:radiating_chamber");
        helper.assertTrue(ECRecipes.MAGICIAN_TABLE.get().toString().equals("essentialcraft5:magician_table"),
                "RecipeType.simple 的 toString 应是注册名");
        helper.succeed();
    }
}
