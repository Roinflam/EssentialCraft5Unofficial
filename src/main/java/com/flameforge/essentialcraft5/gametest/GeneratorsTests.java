package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.RayTowerBlock;
import com.flameforge.essentialcraft5.blockentity.ChargingChamberBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ColdDistillatorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.HeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.NaturalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.RayTowerBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.menu.NaturalFurnaceMenu;
import com.flameforge.essentialcraft5.mru.MRULink;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.UUID;

/**
 * 产能器与射线塔模块的 GameTest：各台产能器的产能条件、平衡值只随机一次、射线塔中继与两格结构。
 * 数值按默认配置算（热力产能器基础 20、冷凝器倍率 1）。结构模板 empty（5×4×5 空气），方块都在测试里自己放。
 * <p>
 * 第三批安全回补（policy §5）：绑定宝石被拒不绑、假玩家和没有玩家不绑。
 * 第四批改造（batch4.md §0.0）：自然熔炉只烧放进格子里的植物物品——放花每刻 +10、少一株，泥土放不进，漏斗能从上 / 侧面塞，
 * 有红石不烧、满了不烧，没有主人（直接 setBlock）照常工作，周围全被模拟领地拒绝也照烧、不发任何事件，旧存档的授权表读档时忽略。
 * 第三批审查 RB-1：冷凝器的寒气只影响主人本人和无主的生物（判定 + 在 empty_large 里实跑一遍，3 格的实体查询不伸出模板）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GeneratorsTests {

    private static final String EMPTY = "empty";
    /** 11×8×11 空气，(0, 0, 0) 是结构方块，从 y=1 起放 */
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);

    private GeneratorsTests() {
    }

    // ---------------- 热力产能器 ----------------

    private static HeatGeneratorBlockEntity heatGenerator(GameTestHelper helper, Block around) {
        helper.setBlock(CENTER, ECGenerators.HEAT_GENERATOR.get());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CENTER.relative(direction, 2), around);
        }
        return (HeatGeneratorBlockEntity) helper.getBlockEntity(CENTER);
    }

    /** 四面相隔一格都是下界岩：20 × 0.75⁴ = 6.33 → 每 tick 6；烧一块煤出一个魔力炉渣 */
    @GameTest(template = EMPTY)
    public static void heatGeneratorNetherrack(GameTestHelper helper) {
        HeatGeneratorBlockEntity be = heatGenerator(helper, Blocks.NETHERRACK);
        be.getInventory().setStackInSlot(HeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        helper.succeedWhen(() -> {
            helper.assertTrue(be.getGeneration() == 6, "产量应为 6 MRU/t，实际 " + be.getGeneration());
            helper.assertTrue(be.getMRUStorage().getMRU() >= 60, "应已产出至少 60 MRU，实际 " + be.getMRUStorage().getMRU());
            ItemStack slag = be.getInventory().getStackInSlot(HeatGeneratorBlockEntity.SLOT_SLAG);
            helper.assertTrue(slag.is(ECMaterials.MAGICAL_SLAG.get()) && slag.getCount() == 1, "应出 1 个魔力炉渣，实际 " + slag);
            helper.assertTrue(be.getInventory().getStackInSlot(HeatGeneratorBlockEntity.SLOT_FUEL).isEmpty(), "煤应被烧掉");
        });
    }

    /** 有一面是空气就不产出（系数 0），但燃料照烧、炉渣照出（同原作） */
    @GameTest(template = EMPTY)
    public static void heatGeneratorNeedsAllSides(GameTestHelper helper) {
        HeatGeneratorBlockEntity be = heatGenerator(helper, Blocks.NETHERRACK);
        helper.setBlock(CENTER.relative(Direction.NORTH, 2), Blocks.AIR);
        be.getInventory().setStackInSlot(HeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(be.getGeneration() == 0, "一面是空气时产量应为 0，实际 " + be.getGeneration());
            helper.assertTrue(be.getMRUStorage().getMRU() == 0, "不应产出 MRU");
            helper.assertTrue(be.getBurnTime() > 0, "燃料照烧");
            helper.assertTrue(be.getInventory().getStackInSlot(HeatGeneratorBlockEntity.SLOT_SLAG).getCount() == 1, "炉渣照出");
            helper.succeed();
        });
    }

    /** 岩浆桶烧完还一个空桶（原作桶直接没了），空桶可以被漏斗抽走 */
    @GameTest(template = EMPTY)
    public static void heatGeneratorReturnsBucket(GameTestHelper helper) {
        HeatGeneratorBlockEntity be = heatGenerator(helper, Blocks.STONE);
        be.getInventory().setStackInSlot(HeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.LAVA_BUCKET));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(be.getBurnTime() > 0, "岩浆桶应在烧");
            helper.assertTrue(be.getInventory().getStackInSlot(HeatGeneratorBlockEntity.SLOT_FUEL).is(Items.BUCKET), "燃料槽应剩一个空桶");
            IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
            helper.assertTrue(handler.insertItem(HeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.DIRT), true).getCount() == 1,
                    "燃料槽不应接受不能烧的东西");
            helper.assertTrue(handler.extractItem(HeatGeneratorBlockEntity.SLOT_FUEL, 1, false).is(Items.BUCKET), "空桶应能被抽走");
            helper.succeed();
        });
    }

    /** 有红石信号时不烧燃料、不产出 */
    @GameTest(template = EMPTY)
    public static void heatGeneratorStopsWithRedstone(GameTestHelper helper) {
        HeatGeneratorBlockEntity be = heatGenerator(helper, Blocks.NETHERRACK);
        helper.setBlock(CENTER.above(), Blocks.REDSTONE_BLOCK);
        be.getInventory().setStackInSlot(HeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(be.getInventory().getStackInSlot(HeatGeneratorBlockEntity.SLOT_FUEL).getCount() == 1, "有红石信号时不应烧燃料");
            helper.assertTrue(be.getMRUStorage().getMRU() == 0, "有红石信号时不应产出");
            helper.succeed();
        });
    }

    /** 平衡值第一次 tick 随机一次，存盘后重新加载不再变（原作每次加载都重新随机） */
    @GameTest(template = EMPTY)
    public static void heatGeneratorBalanceRolledOnce(GameTestHelper helper) {
        helper.setBlock(CENTER, ECGenerators.HEAT_GENERATOR.get());
        HeatGeneratorBlockEntity be = (HeatGeneratorBlockEntity) helper.getBlockEntity(CENTER);
        helper.assertTrue(!be.isBalanceRolled(), "放下后第一次 tick 之前还没随机");
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(be.isBalanceRolled(), "第一次 tick 后应已随机");
            float balance = be.getMRUStorage().getBalance();
            helper.assertTrue(balance >= 0F && balance < 2F, "平衡值应在 [0, 2)，实际 " + balance);
            // 模拟区块重新加载：新建方块实体读回存档，再 tick 一次
            CompoundTag saved = be.saveWithoutMetadata();
            HeatGeneratorBlockEntity reloaded = new HeatGeneratorBlockEntity(be.getBlockPos(), be.getBlockState());
            reloaded.load(saved);
            reloaded.setLevel(helper.getLevel());
            MRUBlockEntity.tickServer(helper.getLevel(), reloaded.getBlockPos(), reloaded.getBlockState(), reloaded);
            helper.assertTrue(reloaded.getMRUStorage().getBalance() == balance,
                    "重新加载后平衡值不应变，原 " + balance + "，现 " + reloaded.getMRUStorage().getBalance());
            helper.succeed();
        });
    }

    // ---------------- 自然熔炉 ----------------
    // 第四批改造（batch4.md §0.0）：只烧放进格子里的植物物品，不碰世界里的方块，没有主人规则。

    /** 直接 setBlock 放一台（没有主人：像 /setblock、结构、别的模组放的） */
    private static NaturalFurnaceBlockEntity naturalFurnace(GameTestHelper helper) {
        helper.setBlock(CENTER, ECGenerators.NATURAL_FURNACE.get());
        return (NaturalFurnaceBlockEntity) helper.getBlockEntity(CENTER);
    }

    private static ItemStack plantSlot(NaturalFurnaceBlockEntity be) {
        return be.getInventory().getStackInSlot(NaturalFurnaceBlockEntity.SLOT_PLANT);
    }

    /**
     * 放进三朵花：点燃一朵、格子里少一朵，燃烧期间每刻产出 10 MRU，烧着的时候不再拿第二朵。
     * 熔炉是直接 setBlock 的、没有主人，照常工作（不碰世界，不需要主人）。
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void naturalFurnaceBurnsInsertedFlower(GameTestHelper helper) {
        NaturalFurnaceBlockEntity be = naturalFurnace(helper);
        helper.assertTrue(be.getOwner() == null, "直接 setBlock 放的熔炉应没有主人");
        be.getInventory().setStackInSlot(NaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.POPPY, 3));
        int perTick = ECConfig.get(NaturalFurnaceBlockEntity.Config.mruGenerated);
        int[] start = new int[2];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.getBurnTime() > 0, "没有主人也应点燃一朵花"))
                .thenExecute(() -> {
                    helper.assertTrue(plantSlot(be).is(Items.POPPY) && plantSlot(be).getCount() == 2,
                            "点燃一朵应少一朵，实际 " + plantSlot(be));
                    helper.assertTrue(be.getBurningItem() == Items.POPPY, "正在烧的应是虞美人，实际 " + be.getBurningItem());
                    helper.assertTrue(be.getMaxBurnTime() == ECConfig.get(NaturalFurnaceBlockEntity.Config.flowerBurnTime),
                            "花应烧 " + ECConfig.get(NaturalFurnaceBlockEntity.Config.flowerBurnTime) + " 刻，实际 " + be.getMaxBurnTime());
                    start[0] = be.getMRUStorage().getMRU();
                    start[1] = be.getBurnTime();
                })
                .thenExecuteAfter(10, () -> {
                    int burnt = start[1] - be.getBurnTime();
                    int gained = be.getMRUStorage().getMRU() - start[0];
                    helper.assertTrue(burnt > 0, "应一直在烧");
                    helper.assertTrue(gained == burnt * perTick,
                            "每刻应产出 " + perTick + " MRU，实际 " + burnt + " 刻共 " + gained);
                    helper.assertTrue(plantSlot(be).getCount() == 2, "一次只烧一株，烧着的时候不应再拿，实际 " + plantSlot(be));
                })
                .thenSucceed();
    }

    /**
     * 能放什么：物品形态的方块在三个标签里的（花 600、树苗 900、草 150，高花、高草丛照算），别的放不进；
     * 漏斗 / 管道塞泥土被拒、塞花能进、不能往外抽；界面格子和 Shift 点击用同一条规则
     */
    @GameTest(template = EMPTY)
    public static void naturalFurnaceAcceptsOnlyPlants(GameTestHelper helper) {
        int flower = ECConfig.get(NaturalFurnaceBlockEntity.Config.flowerBurnTime);
        int sapling = ECConfig.get(NaturalFurnaceBlockEntity.Config.saplingBurnTime);
        int grass = ECConfig.get(NaturalFurnaceBlockEntity.Config.grassBurnTime);
        helper.assertTrue(NaturalFurnaceBlockEntity.burnTicks(new ItemStack(Items.POPPY)) == flower, "小花按花算");
        helper.assertTrue(NaturalFurnaceBlockEntity.burnTicks(new ItemStack(Items.ROSE_BUSH)) == flower, "高花按花算");
        helper.assertTrue(NaturalFurnaceBlockEntity.burnTicks(new ItemStack(Items.OAK_SAPLING)) == sapling, "树苗按树苗算");
        helper.assertTrue(NaturalFurnaceBlockEntity.burnTicks(new ItemStack(Items.GRASS)) == grass, "草按草算");
        helper.assertTrue(NaturalFurnaceBlockEntity.burnTicks(new ItemStack(Items.TALL_GRASS)) == grass, "高草丛按草算");
        helper.assertTrue(NaturalFurnaceBlockEntity.burnTicks(new ItemStack(Items.FERN)) == grass, "蕨按草算");
        for (ItemStack other : List.of(new ItemStack(Items.DIRT), new ItemStack(Items.STICK), new ItemStack(Items.OAK_LEAVES),
                new ItemStack(Items.DEAD_BUSH), new ItemStack(Items.WHEAT_SEEDS))) {
            helper.assertTrue(NaturalFurnaceBlockEntity.burnTicks(other) == 0, other + " 不应能烧");
            helper.assertFalse(NaturalFurnaceBlockEntity.slotRule(NaturalFurnaceBlockEntity.SLOT_PLANT, other), other + " 不应能放进格子");
        }

        NaturalFurnaceBlockEntity be = naturalFurnace(helper);
        // 界面：格子只收植物，Shift 点击泥土不动、点击花进格子
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 4));
        player.getInventory().setItem(1, new ItemStack(Items.DANDELION, 4));
        NaturalFurnaceMenu menu = new NaturalFurnaceMenu(0, player.getInventory(), be);
        helper.assertTrue(menu.slots.size() == 1 + 36, "应是 1 个植物格 + 玩家背包，实际 " + menu.slots.size());
        helper.assertFalse(menu.slots.get(0).mayPlace(new ItemStack(Items.DIRT)), "植物格不应接受泥土");
        helper.assertTrue(menu.slots.get(0).mayPlace(new ItemStack(Items.DANDELION)), "植物格应接受花");
        // 快捷栏 0、1 在菜单里是 1 + 27 + 0、1 + 27 + 1
        menu.quickMoveStack(player, 28);
        helper.assertTrue(player.getInventory().getItem(0).is(Items.DIRT) && player.getInventory().getItem(0).getCount() == 4,
                "Shift 点击泥土不应移动");
        menu.quickMoveStack(player, 29);
        helper.assertTrue(player.getInventory().getItem(1).isEmpty(), "Shift 点击花应整组移进格子");
        helper.assertTrue(plantSlot(be).is(Items.DANDELION) && plantSlot(be).getCount() == 4, "格子里应有 4 朵蒲公英，实际 " + plantSlot(be));

        // 漏斗 / 管道
        be.getInventory().setStackInSlot(NaturalFurnaceBlockEntity.SLOT_PLANT, ItemStack.EMPTY);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        helper.assertTrue(handler.insertItem(NaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.DIRT), false).getCount() == 1,
                "漏斗塞泥土应被拒");
        helper.assertTrue(plantSlot(be).isEmpty(), "被拒的泥土不应进格子");
        helper.assertTrue(handler.insertItem(NaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.POPPY, 5), false).isEmpty(),
                "漏斗塞花应能进");
        helper.assertTrue(handler.extractItem(NaturalFurnaceBlockEntity.SLOT_PLANT, 1, false).isEmpty(), "漏斗不应能把植物抽出来");
        helper.assertTrue(plantSlot(be).getCount() == 5, "格子里应有 5 朵花，实际 " + plantSlot(be));
        helper.succeed();
    }

    /** 真的漏斗：上面一个朝下、东边一个朝西，各塞一朵花；东边那个里的泥土塞不进去 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void naturalFurnaceFedByHoppers(GameTestHelper helper) {
        NaturalFurnaceBlockEntity be = naturalFurnace(helper);
        BlockPos top = CENTER.above();
        BlockPos side = CENTER.east();
        helper.setBlock(top, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(side, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
        HopperBlockEntity topHopper = (HopperBlockEntity) helper.getBlockEntity(top);
        HopperBlockEntity sideHopper = (HopperBlockEntity) helper.getBlockEntity(side);
        topHopper.setItem(0, new ItemStack(Items.POPPY));
        sideHopper.setItem(0, new ItemStack(Items.POPPY));
        sideHopper.setItem(1, new ItemStack(Items.DIRT));
        helper.succeedWhen(() -> {
            helper.assertTrue(topHopper.isEmpty(), "上面的漏斗应把花塞进去");
            helper.assertTrue(sideHopper.countItem(Items.POPPY) == 0, "侧面的漏斗应把花塞进去");
            helper.assertTrue(sideHopper.countItem(Items.DIRT) == 1, "泥土不应被塞进去");
            helper.assertTrue(be.getBurnTime() > 0 && be.getBurningItem() == Items.POPPY, "应在烧一朵花");
            helper.assertTrue(plantSlot(be).is(Items.POPPY) && plantSlot(be).getCount() == 1, "格子里应还剩一朵在等，实际 " + plantSlot(be));
        });
    }

    /** 有红石信号时不点新的；烧到一半来了信号就停住（燃烧时间不减、不产出），信号撤了接着烧 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void naturalFurnaceStopsWithRedstone(GameTestHelper helper) {
        NaturalFurnaceBlockEntity be = naturalFurnace(helper);
        BlockPos power = CENTER.above();
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        be.getInventory().setStackInSlot(NaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.OAK_SAPLING));
        int[] paused = new int[2];
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(plantSlot(be).getCount() == 1, "有红石信号时不应点燃");
                    helper.assertTrue(be.getBurnTime() == 0 && be.getMRUStorage().getMRU() == 0, "有红石信号时不应产出");
                    helper.setBlock(power, Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.getBurnTime() > 0, "信号撤了应点燃"))
                .thenExecuteAfter(3, () -> {
                    helper.setBlock(power, Blocks.REDSTONE_BLOCK);
                    paused[0] = be.getBurnTime();
                    paused[1] = be.getMRUStorage().getMRU();
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(be.getBurnTime() == paused[0], "有信号时燃烧时间不应减少");
                    helper.assertTrue(be.getMRUStorage().getMRU() == paused[1], "有信号时不应产出");
                    helper.setBlock(power, Blocks.AIR);
                })
                .thenExecuteAfter(5, () -> helper.assertTrue(be.getBurnTime() < paused[0], "信号撤了应接着烧"))
                .thenSucceed();
    }

    /**
     * 红石信号有缓存（MRUBlockEntity.neighborPowered）：邻居更新会让它作废、信号变化下一刻就认（上一条测的）；
     * 万一变化没通知到（这里用不发邻居更新的标志放红石块），最慢 POWER_BACKSTOP 刻内也会重读、停下来
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void naturalFurnaceRedstoneCacheBackstop(GameTestHelper helper) {
        NaturalFurnaceBlockEntity be = naturalFurnace(helper);
        BlockPos power = CENTER.above();
        be.getInventory().setStackInSlot(NaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.OAK_SAPLING));
        int[] paused = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.getBurnTime() > 0, "应点燃"))
                .thenExecuteAfter(2, () -> helper.getLevel().setBlock(helper.absolutePos(power), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS))
                .thenExecuteAfter(MRUBlockEntity.POWER_BACKSTOP + 2, () -> paused[0] = be.getBurnTime())
                .thenExecuteAfter(5, () -> helper.assertTrue(be.getBurnTime() == paused[0] && paused[0] > 0,
                        "没通知到的红石信号，" + MRUBlockEntity.POWER_BACKSTOP + " 刻内也该认到而停住，前后 " + paused[0] + " → " + be.getBurnTime()))
                .thenSucceed();
    }

    /** MRU 满了不点新的（植物留在格子里）；原作同样只在没满时找新植物 */
    @GameTest(template = EMPTY)
    public static void naturalFurnaceWaitsWhenFull(GameTestHelper helper) {
        NaturalFurnaceBlockEntity be = naturalFurnace(helper);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        be.getInventory().setStackInSlot(NaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.POPPY));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(be.getBurnTime() == 0, "满了不应点燃");
            helper.assertTrue(plantSlot(be).getCount() == 1, "满了花应留在格子里");
            helper.succeed();
        });
    }

    /**
     * 不碰世界：真玩家放下（有主人、但主人不在线），周围种满花、模拟领地拒绝所有格子——
     * 照样烧格子里的草、产出 MRU；周围的花一株不少、底下的草方块不变，一个破坏 / 放置事件都不发
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void naturalFurnaceLeavesSceneryAlone(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
                BlockPos pos = new BlockPos(x, 1, z);
                if (!pos.equals(CENTER)) {
                    helper.setBlock(pos, Blocks.POPPY);
                }
            }
        }
        NaturalFurnaceBlockEntity be = naturalFurnace(helper);
        ServerPlayer placer = ProtectionFrameworkTests.realPlayer(helper);
        BlockState state = be.getBlockState();
        state.getBlock().setPlacedBy(helper.getLevel(), be.getBlockPos(), state, placer, new ItemStack(state.getBlock()));
        helper.assertTrue(placer.getUUID().equals(be.getOwner()), "真玩家放下应记成主人");
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                claims.deny(new BlockPos(x, 1, z));
            }
        }
        be.getInventory().setStackInSlot(NaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.GRASS, 2));
        helper.runAtTickTime(40, () -> {
            try {
                helper.assertTrue(be.getBurnTime() > 0 && be.getBurningItem() == Items.GRASS, "主人不在线、周围全被拒也应照烧格子里的草");
                helper.assertTrue(plantSlot(be).getCount() == 1, "应只拿了一丛草");
                helper.assertTrue(be.getMRUStorage().getMRU() > 0, "应已产出 MRU");
                for (int x = 0; x < 5; x++) {
                    for (int z = 0; z < 5; z++) {
                        BlockPos pos = new BlockPos(x, 1, z);
                        if (!pos.equals(CENTER)) {
                            helper.assertBlockPresent(Blocks.POPPY, pos);
                        }
                        helper.assertBlockPresent(Blocks.GRASS_BLOCK, pos.below());
                        helper.assertTrue(claims.breaks(pos) == 0 && claims.places(pos) == 0, "不应对 " + pos + " 发任何事件");
                    }
                }
            } finally {
                claims.close();
            }
            helper.succeed();
        });
    }

    /**
     * 存盘：新格式的燃烧进度和正在烧的植物读得回来；旧存档（第三批 ~ 第四批以前）的授权表读档时忽略、下次存盘消失，
     * 「正在烧场景里某株植物」的记录作废（那株植物没被消耗）
     */
    @GameTest(template = EMPTY)
    public static void naturalFurnaceSaveFormat(GameTestHelper helper) {
        NaturalFurnaceBlockEntity be = naturalFurnace(helper);
        CompoundTag current = be.saveWithoutMetadata();
        current.putInt("burn", 120);
        current.putInt("burn_max", 600);
        current.putString("burn_item", "minecraft:poppy");
        NaturalFurnaceBlockEntity reloaded = new NaturalFurnaceBlockEntity(be.getBlockPos(), be.getBlockState());
        reloaded.load(current);
        helper.assertTrue(reloaded.getBurnTime() == 120 && reloaded.getMaxBurnTime() == 600, "燃烧进度应读回来");
        helper.assertTrue(reloaded.getBurningItem() == Items.POPPY, "正在烧的植物应读回来，实际 " + reloaded.getBurningItem());

        CompoundTag legacy = be.saveWithoutMetadata();
        ListTag grants = new ListTag();
        CompoundTag grant = new CompoundTag();
        grant.put("pos", NbtUtils.writeBlockPos(be.getBlockPos().east()));
        grant.putBoolean("allowed", true);
        grant.putLong("time", 0L);
        grants.add(grant);
        legacy.put(NaturalFurnaceBlockEntity.LEGACY_TAG_GRANTS, grants);
        legacy.put(NaturalFurnaceBlockEntity.LEGACY_TAG_BURNING, NbtUtils.writeBlockPos(be.getBlockPos().east()));
        legacy.putInt("burn", 300);
        legacy.putInt("burn_max", 600);
        NaturalFurnaceBlockEntity old = new NaturalFurnaceBlockEntity(be.getBlockPos(), be.getBlockState());
        old.load(legacy);
        helper.assertTrue(old.getBurnTime() == 0 && old.getMaxBurnTime() == 0, "旧存档里烧场景植物的进度应作废");
        CompoundTag resaved = old.saveWithoutMetadata();
        helper.assertFalse(resaved.contains(NaturalFurnaceBlockEntity.LEGACY_TAG_GRANTS), "旧授权表存盘后应消失");
        helper.assertFalse(resaved.contains(NaturalFurnaceBlockEntity.LEGACY_TAG_BURNING), "旧的植物坐标存盘后应消失");
        helper.succeed();
    }

    // ---------------- 绑定宝石 ----------------

    /** 拿主手的东西右键 relative 那格（点上表面） */
    private static InteractionResult useGemOn(GameTestHelper helper, Player player, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** 背包里有没有绑到 target 的宝石 */
    private static boolean holdsGemBoundTo(Player player, BlockPos target) {
        for (ItemStack stack : player.getInventory().items) {
            GlobalPos bound = BoundGemItem.getTarget(stack);
            if (bound != null && bound.pos().equals(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 第三批安全回补：绑之前按使用者身份问一次那一格（P1）。被模拟领地拒绝的不绑、宝石不少；
     * 空地照常绑；假玩家、没有玩家（发射器之类）一律不绑
     */
    @GameTest(template = EMPTY)
    public static void boundGemRespectsClaims(GameTestHelper helper) {
        BlockPos free = new BlockPos(1, 1, 2);
        BlockPos claimed = new BlockPos(3, 1, 2);
        helper.setBlock(free, ECBlocks.CREATIVE_MRU_STORAGE.get());
        helper.setBlock(claimed, ECBlocks.CREATIVE_MRU_STORAGE.get());
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(claimed)) {
            ItemStack gems = new ItemStack(ECItems.BOUND_GEM.get(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, gems);

            InteractionResult denied = useGemOn(helper, player, claimed);
            helper.assertTrue(denied == InteractionResult.FAIL, "被拒应返回 FAIL，实际 " + denied);
            helper.assertTrue(gems.getCount() == 3 && !BoundGemItem.isBound(gems), "被拒时宝石不应少、不应绑上");
            helper.assertFalse(holdsGemBoundTo(player, helper.absolutePos(claimed)), "被拒时不应拿到绑好的宝石");
            helper.assertTrue(claims.probes(claimed) == 1, "应以使用者身份探测一次");

            InteractionResult bound = useGemOn(helper, player, free);
            helper.assertTrue(bound.consumesAction(), "空地应能绑，实际 " + bound);
            helper.assertTrue(gems.getCount() == 2, "绑一颗应少一颗，实际 " + gems.getCount());
            helper.assertTrue(holdsGemBoundTo(player, helper.absolutePos(free)), "应拿到绑到空地那格的宝石");

            FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
            ItemStack fakeGems = new ItemStack(ECItems.BOUND_GEM.get(), 2);
            fake.setItemInHand(InteractionHand.MAIN_HAND, fakeGems);
            helper.assertTrue(useGemOn(helper, fake, free) == InteractionResult.FAIL, "假玩家不应能绑");
            helper.assertTrue(fakeGems.getCount() == 2 && !BoundGemItem.isBound(fakeGems), "假玩家的宝石不应变");

            ItemStack dispensed = new ItemStack(ECItems.BOUND_GEM.get(), 2);
            BlockPos abs = helper.absolutePos(free);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            UseOnContext noPlayer = new UseOnContext(helper.getLevel(), null, InteractionHand.MAIN_HAND, dispensed, hit);
            helper.assertTrue(dispensed.getItem().useOn(noPlayer) == InteractionResult.FAIL, "没有玩家（发射器之类）不应能绑");
            helper.assertTrue(dispensed.getCount() == 2 && !BoundGemItem.isBound(dispensed), "没有玩家时宝石不应变");
        }
        helper.succeed();
    }

    // ---------------- 冷凝器 ----------------

    /** 周围 24 块浮冰：24 × 0.3 = 7.2 → 每 tick 7 */
    @GameTest(template = EMPTY)
    public static void coldDistillatorPackedIce(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                BlockPos pos = new BlockPos(x, 1, z);
                if (!pos.equals(CENTER)) {
                    helper.setBlock(pos, Blocks.PACKED_ICE);
                }
            }
        }
        helper.setBlock(CENTER, ECGenerators.COLD_DISTILLATOR.get());
        ColdDistillatorBlockEntity be = (ColdDistillatorBlockEntity) helper.getBlockEntity(CENTER);
        helper.succeedWhen(() -> {
            helper.assertTrue(be.getGeneration() == 7, "产量应为 7 MRU/t，实际 " + be.getGeneration());
            helper.assertTrue(be.getMRUStorage().getMRU() >= 70, "应已产出 MRU");
            helper.assertTrue(be.getMRUStorage().getBalance() == 0F, "平衡值应为 0（冰冻）");
        });
    }

    /** 霜冻石（IColdBlock）冷值 0.5：4 块 = 2；一层雪 0.05 不够凑整 */
    @GameTest(template = EMPTY)
    public static void coldDistillatorColdStone(GameTestHelper helper) {
        helper.setBlock(CENTER, ECGenerators.COLD_DISTILLATOR.get());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CENTER.relative(direction), ECMaterials.COLD_STONE.get());
        }
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.SNOW_BLOCK);
        helper.setBlock(new BlockPos(0, 2, 0), Blocks.SNOW);
        ColdDistillatorBlockEntity be = (ColdDistillatorBlockEntity) helper.getBlockEntity(CENTER);
        helper.succeedWhen(() -> helper.assertTrue(be.getGeneration() == 2,
                "4 块霜冻石 + 雪块 + 雪应为 2.25 → 2 MRU/t，实际 " + be.getGeneration()));
    }

    /** 10 块浮冰 = 3（浮点累加是 2.9999…，不能取整成 2） */
    @GameTest(template = EMPTY)
    public static void coldDistillatorFloatSum(GameTestHelper helper) {
        helper.setBlock(CENTER, ECGenerators.COLD_DISTILLATOR.get());
        for (int x = 0; x < 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 0), Blocks.PACKED_ICE);
            helper.setBlock(new BlockPos(x, 1, 4), Blocks.PACKED_ICE);
        }
        ColdDistillatorBlockEntity be = (ColdDistillatorBlockEntity) helper.getBlockEntity(CENTER);
        helper.succeedWhen(() -> helper.assertTrue(be.getGeneration() == 3,
                "10 块浮冰应为 3 MRU/t，实际 " + be.getGeneration()));
    }

    /** 1.13 以后的冷源：蓝冰 0.4、细雪 0.2（同雪块）。5 块蓝冰 + 5 格细雪 = 3 */
    @GameTest(template = EMPTY)
    public static void coldDistillatorModernColdBlocks(GameTestHelper helper) {
        helper.setBlock(CENTER, ECGenerators.COLD_DISTILLATOR.get());
        for (int x = 0; x < 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 0), Blocks.BLUE_ICE);
            helper.setBlock(new BlockPos(x, 1, 4), Blocks.POWDER_SNOW);
        }
        ColdDistillatorBlockEntity be = (ColdDistillatorBlockEntity) helper.getBlockEntity(CENTER);
        helper.succeedWhen(() -> helper.assertTrue(be.getGeneration() == 3,
                "5 块蓝冰 + 5 格细雪应为 3 MRU/t，实际 " + be.getGeneration()));
    }

    /** 有红石信号时不产出 */
    @GameTest(template = EMPTY)
    public static void coldDistillatorStopsWithRedstone(GameTestHelper helper) {
        helper.setBlock(CENTER, ECGenerators.COLD_DISTILLATOR.get());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CENTER.relative(direction), Blocks.PACKED_ICE);
        }
        helper.setBlock(CENTER.above(), Blocks.REDSTONE_BLOCK);
        ColdDistillatorBlockEntity be = (ColdDistillatorBlockEntity) helper.getBlockEntity(CENTER);
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(be.getGeneration() == 1, "4 块浮冰应为 1 MRU/t，实际 " + be.getGeneration());
            helper.assertTrue(be.getMRUStorage().getMRU() == 0, "有红石信号时不应产出");
            helper.succeed();
        });
    }

    /**
     * 第三批审查 RB-1：寒气不影响谁（直接查判定）。玩家只影响主人本人（生存模式），别的玩家不影响，没有主人的机器不影响任何玩家；
     * 别的生物跳过光束的豁免名单：起了名字的、村民、玩家造的铁傀儡、雪傀儡、装了鞍的骆驼（4a 审查 FW-7）不影响，
     * 野生的牛、村庄自己刷的铁傀儡、没装鞍的骆驼照旧
     */
    @GameTest(template = EMPTY)
    public static void coldDistillatorSparesOthers(GameTestHelper helper) {
        ServerPlayer owner = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer neighbour = ProtectionFrameworkTests.realPlayer(helper);
        UUID ownerId = owner.getUUID();
        helper.assertFalse(ColdDistillatorBlockEntity.sparedFromCold(owner, ownerId), "主人本人应受影响");
        helper.assertTrue(ColdDistillatorBlockEntity.sparedFromCold(neighbour, ownerId), "别的玩家不应受影响");
        helper.assertTrue(ColdDistillatorBlockEntity.sparedFromCold(owner, null), "没有主人的机器不应影响任何玩家");

        ServerLevel level = helper.getLevel();
        Cow wild = EntityType.COW.create(level);
        Cow named = EntityType.COW.create(level);
        Villager villager = EntityType.VILLAGER.create(level);
        IronGolem villageGolem = EntityType.IRON_GOLEM.create(level);
        IronGolem builtGolem = EntityType.IRON_GOLEM.create(level);
        SnowGolem snowGolem = EntityType.SNOW_GOLEM.create(level);
        Camel wildCamel = EntityType.CAMEL.create(level);
        Camel saddledCamel = EntityType.CAMEL.create(level);
        helper.assertTrue(wild != null && named != null && villager != null && villageGolem != null && builtGolem != null
                && snowGolem != null && wildCamel != null && saddledCamel != null, "造不出测试用的生物");
        named.setCustomName(Component.literal("Daisy"));
        builtGolem.setPlayerCreated(true);
        helper.assertFalse(ColdDistillatorBlockEntity.sparedFromCold(wild, ownerId), "野生的牛应受影响");
        helper.assertFalse(ColdDistillatorBlockEntity.sparedFromCold(villageGolem, ownerId), "村庄自己刷的铁傀儡应受影响");
        helper.assertTrue(ColdDistillatorBlockEntity.sparedFromCold(named, ownerId), "起了名字的牛不应受影响");
        helper.assertTrue(ColdDistillatorBlockEntity.sparedFromCold(villager, ownerId), "村民不应受影响");
        helper.assertTrue(ColdDistillatorBlockEntity.sparedFromCold(builtGolem, ownerId), "玩家造的铁傀儡不应受影响");
        helper.assertTrue(ColdDistillatorBlockEntity.sparedFromCold(snowGolem, ownerId), "雪傀儡不应受影响");
        saddledCamel.equipSaddle(null);
        helper.assertTrue(saddledCamel.isSaddled(), "骆驼的鞍没装上");
        helper.assertTrue(ColdDistillatorBlockEntity.sparedFromCold(saddledCamel, ownerId), "装了鞍的骆驼不应受影响");
        helper.assertFalse(ColdDistillatorBlockEntity.sparedFromCold(wildCamel, ownerId), "没装鞍的骆驼应受影响");
        helper.succeed();
    }

    /**
     * 同上，在世界里实跑：有主人的冷凝器（4 块浮冰，每刻 1 MRU）3 格内有野生的牛、村民、玩家造的铁傀儡，
     * 每 {@value ColdDistillatorBlockEntity#HARM_INTERVAL} 刻随机挑一个、挑中后两者就作罢。看满 60 刻、牛挨过冻就算过：
     * 那时村民和傀儡没有任何效果、没掉血。60 刻里只挑十来次，一次都没挑中牛的几率约 (2/3)^10 ≈ 2%，所以不在固定那一刻判，
     * 牛还没挨冻就接着等（300 刻约 60 次，一直挑不中的几率约 (2/3)^60，可以忽略）。牛在第 60 刻前最多挨 6 下 1 点的伤害，不会冻死
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 300)
    public static void coldDistillatorHarmsOnlyUnowned(GameTestHelper helper) {
        BlockPos center = new BlockPos(5, 1, 5);
        helper.setBlock(center, ECGenerators.COLD_DISTILLATOR.get());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(center.relative(direction), Blocks.PACKED_ICE);
        }
        ColdDistillatorBlockEntity be = (ColdDistillatorBlockEntity) helper.getBlockEntity(center);
        ECProtection.recordOwner(be, ProtectionFrameworkTests.realPlayer(helper));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 3));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(7, 1, 3));
        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(3, 1, 7));
        golem.setPlayerCreated(true);
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() >= 60, "至少看满 60 刻");
            helper.assertTrue(be.getGeneration() == 1 && be.getMRUStorage().getMRU() > 0, "冷凝器应在产出，产量 " + be.getGeneration());
            helper.assertTrue(villager.getActiveEffects().isEmpty() && villager.getHealth() == villager.getMaxHealth(),
                    "村民不应受影响，效果 " + villager.getActiveEffects());
            helper.assertTrue(golem.getActiveEffects().isEmpty() && golem.getHealth() == golem.getMaxHealth(),
                    "玩家造的铁傀儡不应受影响，效果 " + golem.getActiveEffects());
            helper.assertTrue(cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "野生的牛应挨冻");
        });
    }

    // ---------------- 射线塔 ----------------

    private static ItemStack gemBoundTo(GameTestHelper helper, BlockPos absolute) {
        ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(gem, GlobalPos.of(helper.getLevel().dimension(), absolute), true);
        return gem;
    }

    /**
     * 创造 MRU 源 → 射线塔 → 充能仓。充能仓的宝石「点在射线塔上半格」，按 IBoundGemTarget 换成下半格。
     * 放下半格时自动补上半格，上半格没有方块实体。
     */
    @GameTest(template = EMPTY)
    public static void rayTowerRelays(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        BlockPos tower = new BlockPos(2, 1, 0);
        BlockPos chamber = new BlockPos(4, 1, 0);
        helper.setBlock(source, ECBlocks.CREATIVE_MRU_STORAGE.get());
        helper.setBlock(tower, ECGenerators.RAY_TOWER.get());
        helper.setBlock(chamber, ECBlocks.CHARGING_CHAMBER.get());
        helper.assertBlockProperty(tower, RayTowerBlock.LAYER, RayTowerBlock.Layer.BOTTOM);
        helper.assertBlockProperty(tower.above(), RayTowerBlock.LAYER, RayTowerBlock.Layer.TOP);
        helper.assertTrue(helper.getBlockEntity(tower.above()) == null, "上半格不应有方块实体");

        BlockPos topAbs = helper.absolutePos(tower.above());
        BlockState topState = helper.getLevel().getBlockState(topAbs);
        BlockPos linkTarget = ((RayTowerBlock) topState.getBlock()).boundGemTarget(helper.getLevel(), topAbs, topState);
        helper.assertTrue(linkTarget.equals(helper.absolutePos(tower)), "点上半格应绑到下半格");

        RayTowerBlockEntity towerBe = (RayTowerBlockEntity) helper.getBlockEntity(tower);
        towerBe.getInventory().setStackInSlot(RayTowerBlockEntity.SLOT_GEM, gemBoundTo(helper, helper.absolutePos(source)));
        ChargingChamberBlockEntity chamberBe = (ChargingChamberBlockEntity) helper.getBlockEntity(chamber);
        chamberBe.getInventory().setStackInSlot(ChargingChamberBlockEntity.SLOT_GEM, gemBoundTo(helper, linkTarget));

        helper.succeedWhen(() -> {
            helper.assertTrue(towerBe.getLinkStatus() == MRULink.Status.WORKING, "射线塔应在拉 MRU，实际 " + towerBe.getLinkStatus());
            helper.assertTrue(chamberBe.getLinkStatus() == MRULink.Status.WORKING, "充能仓应从射线塔拉 MRU，实际 " + chamberBe.getLinkStatus());
            helper.assertTrue(chamberBe.getMRUStorage().getMRU() > 0, "充能仓应拉到 MRU");
        });
    }

    /** 拆上半格：下半格跟着没，只掉一个射线塔 */
    @GameTest(template = EMPTY)
    public static void rayTowerBreaksTogether(GameTestHelper helper) {
        BlockPos tower = new BlockPos(2, 1, 2);
        helper.setBlock(tower, ECGenerators.RAY_TOWER.get());
        helper.getLevel().destroyBlock(helper.absolutePos(tower.above()), true);
        helper.assertBlockPresent(Blocks.AIR, tower);
        helper.assertBlockPresent(Blocks.AIR, tower.above());
        helper.runAtTickTime(2, () -> {
            helper.assertItemEntityCountIs(ECGenerators.RAY_TOWER.get().asItem(), tower, 2D, 1);
            helper.succeed();
        });
    }
}
