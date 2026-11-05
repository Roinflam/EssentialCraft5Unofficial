package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.MRUConverterBlock;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.ConverterTier;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.Mode;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.Status;
import com.flameforge.essentialcraft5.compat.botania.ManaBridge;
import com.flameforge.essentialcraft5.gametest.ConverterTestEnergy.Receiver;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.item.creative.CreativeOnly;
import com.flameforge.essentialcraft5.menu.MRUConverterMenu;
import com.flameforge.essentialcraft5.mru.MRULink;
import com.flameforge.essentialcraft5.registry.ECConverters;
import com.flameforge.essentialcraft5.registry.ECCreative;
import com.flameforge.essentialcraft5.registry.ECItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * MRU 转换器（ECConverters、ECCreative 的无尽档；规划 tools/plan/mru_converter.md §9.2）的 GameTest，默认运行（不挂植物魔法）：
 * <ul>
 *   <li>注册与配方：五台都在、属性、方块状态、无尽档提示最后一行；四档配方逐档吃上一档、只用原版和本模组材料，没有产出无尽档的配方。</li>
 *   <li>换算：默认 2 MRU = 1 FE（每刻 80 FE、160 MRU）；比率覆盖成 1 000 时 1 : 1；除不尽的比率（37）跑 100 刻，吃掉的 MRU × 比率 = 输出 × 1 000 + 零头，误差 0。</li>
 *   <li>输出：没人收只吃「填满 1 秒缓存」的那一点 MRU；三个面轮流分、谁都不饿着；电缆抽走的计入每刻额度、不收能量；红石暂停；
 *       接收方拆了不报错、放回去接着推；没人收 20 刻后退避，放上接收方立即恢复；四档和无尽档每刻输出 = 各自上限。</li>
 *   <li>植物魔法隔离：没装时桥不可用、魔力按钮被拒、存档里的魔力模式停在「植物魔法没有加载」、tick 和开菜单都不抛 NoClassDefFoundError。</li>
 *   <li>名额与主人：同主人上限 2 时第 3 台排队（无尽档也算一台），拆掉一台 120 刻内接上，两个主人互不影响；专用服上没有主人的不工作。</li>
 *   <li>泄漏、方块状态、存盘：生存档工作时每 20 刻漏一次、没工作和无尽档不漏；active 两次切换至少隔 20 刻、不发邻居更新；存读档一致、配置改小后截到上限、清空后挖掉只掉方块。</li>
 * </ul>
 * 接收方用 {@link ConverterTestEnergy}（登记绝对坐标再放木桶）。每个测试的主人都是新的随机 UUID（名额表是全服共用的，同主人才会互相挡）。
 * MRU 的扣量看方块实体自己的统计 {@code totalMruSpent}，不看存量：别的测试里的无尽 MRU 源会给半径 8 内带宝石槽的机器补满，存量不可靠。
 * 比率、每刻上限、台数、植物魔法桥都用方块实体的实例钩子，不改真配置。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ConverterTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final BlockPos EAST = new BlockPos(3, 1, 2);

    private ConverterTests() {
    }

    // ---------------- 工具 ----------------

    /**
     * 放一台转换器：主人（null = 没有主人）、放下时间，MRU 加满，并且每刻补满。
     * 一档的存量只够顶 31 刻左右（5 000 MRU ÷ 每刻 160），测试里跑得比这长的都得有人补，所以默认每刻补一次；
     * 要看「没 MRU 时怎样」的用 {@link #placeUnfed}。
     */
    static MRUConverterBlockEntity place(GameTestHelper helper, BlockPos rel, Block block, @Nullable UUID owner, long placedTick) {
        MRUConverterBlockEntity be = placeUnfed(helper, rel, block, owner, placedTick);
        helper.onEachTick(() -> {
            if (!be.isRemoved()) {
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            }
        });
        return be;
    }

    /** 同 {@link #place}，但放下时加满一次，之后没人补 */
    static MRUConverterBlockEntity placeUnfed(GameTestHelper helper, BlockPos rel, Block block, @Nullable UUID owner, long placedTick) {
        helper.setBlock(rel, block);
        MRUConverterBlockEntity be = (MRUConverterBlockEntity) helper.getBlockEntity(rel);
        if (owner != null) {
            be.setOwner(owner, "converter-test");
        }
        be.markPlaced(placedTick);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        return be;
    }

    static MRUConverterBlockEntity basic(GameTestHelper helper, BlockPos rel) {
        return place(helper, rel, ECConverters.MRU_CONVERTER.get(), UUID.randomUUID(), 0L);
    }

    /** 在 rel 放一个测试接收方（登记绝对坐标再放木桶），并确认能力挂上了 */
    static Receiver receiver(GameTestHelper helper, BlockPos rel, int capacity, int maxReceive) {
        Receiver receiver = ConverterTestEnergy.expect(helper.absolutePos(rel), capacity, maxReceive);
        helper.setBlock(rel, Blocks.BARREL);
        BlockEntity barrel = helper.getBlockEntity(rel);
        helper.assertTrue(barrel != null && barrel.getCapability(ForgeCapabilities.ENERGY, null).isPresent(),
                "测试接收方没挂上能量能力（AttachCapabilitiesEvent 没触发）：" + rel);
        return receiver;
    }

    static Receiver bigReceiver(GameTestHelper helper, BlockPos rel) {
        return receiver(helper, rel, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2);
    }

    static void forget(GameTestHelper helper, BlockPos... rels) {
        for (BlockPos rel : rels) {
            ConverterTestEnergy.forget(helper.absolutePos(rel));
        }
    }

    /** 扣掉的 MRU × 比率 = (送出去的 + 缓存) × 1000 + 零头（从 0 开始的转换器） */
    static void assertConserved(GameTestHelper helper, MRUConverterBlockEntity be, int ratio, String what) {
        long left = be.totalMruSpent() * ratio;
        long right = (be.totalOutput() + be.buffer()) * 1000L + be.carry();
        helper.assertTrue(left == right, what + "：扣的 MRU × 比率 = " + left + "，输出 + 缓存 + 零头 = " + right);
    }

    /** 一个总是「可用」、但旁边一个接收方都找不到的假桥（不挂植物魔法时测魔力模式的切换用） */
    static ManaBridge availableEmptyBridge() {
        return new ManaBridge() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public @Nullable Object find(BlockEntity blockEntity, Direction side) {
                return null;
            }

            @Override
            public boolean isPresent(Object handle) {
                return false;
            }

            @Override
            public int accept(Object handle, int amount) {
                return 0;
            }

            @Override
            public void onInvalidate(Object handle, Runnable listener) {
            }

            @Override
            public int currentMana(Object handle) {
                return 0;
            }

            @Override
            public int maxMana(Object handle) {
                return -1;
            }

            @Override
            public boolean receiving(Object handle) {
                return false;
            }
        };
    }

    /**
     * 像植物魔法那样的假桥：句柄就是邻居方块实体本身，从不作废（isPresent 恒真、onInvalidate 不挂监听）；accept 照单全收，
     * blind 时带 {@link ManaBridge#BLIND}（推完存量不动，像魔力分配器）。记下 find / accept 的对象
     */
    static final class LoyalBridge extends ManaBridge {
        final List<Object> finds = new ArrayList<>();
        final List<Object> accepts = new ArrayList<>();
        boolean blind;

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public @Nullable Object find(BlockEntity blockEntity, Direction side) {
            finds.add(blockEntity);
            return blockEntity;
        }

        @Override
        public boolean isPresent(Object handle) {
            return true;
        }

        @Override
        public int accept(Object handle, int amount) {
            accepts.add(handle);
            return blind ? amount | BLIND : amount;
        }

        @Override
        public void onInvalidate(Object handle, Runnable listener) {
        }

        @Override
        public int currentMana(Object handle) {
            return 0;
        }

        @Override
        public int maxMana(Object handle) {
            return -1;
        }

        @Override
        public boolean receiving(Object handle) {
            return true;
        }
    }

    private static List<Block> allBlocks() {
        return List.of(ECConverters.MRU_CONVERTER.get(), ECConverters.PALE_MRU_CONVERTER.get(), ECConverters.MITHRILINE_MRU_CONVERTER.get(),
                ECConverters.DEMONIC_MRU_CONVERTER.get(), ECCreative.INFINITE_MRU_CONVERTER.get());
    }

    private static boolean isKey(Component line, String key) {
        return line.getContents() instanceof TranslatableContents translatable && key.equals(translatable.getKey());
    }

    // ---------------- 注册与配方 ----------------

    /** 五台都注册、档位对、硬度 1、无尽档抗爆 1200；方块状态有 mode / active；方块实体类型分两种；无尽档物品提示最后一行是「没有合成配方」 */
    @GameTest(template = EMPTY)
    public static void converterRegistry(GameTestHelper helper) {
        ConverterTier[] tiers = ConverterTier.values();
        List<Block> blocks = allBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            helper.assertTrue(block instanceof MRUConverterBlock converter && converter.tier() == tiers[i], "第 " + i + " 台的档位不对：" + block);
            BlockState state = block.defaultBlockState();
            helper.assertTrue(state.hasProperty(MRUConverterBlock.MODE) && state.hasProperty(MRUConverterBlock.ACTIVE), "方块状态应有 mode / active：" + block);
            helper.assertTrue(state.getValue(MRUConverterBlock.MODE) == Mode.ENERGY && !state.getValue(MRUConverterBlock.ACTIVE), "默认应是能量、待机");
            helper.assertTrue(state.getDestroySpeed(helper.getLevel(), helper.absolutePos(CENTER)) == 1F, "硬度应是 1：" + block);
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            helper.assertTrue(id != null && EssentialCraft5.MODID.equals(id.getNamespace()), "应注册在本模组下：" + id);
        }
        helper.assertTrue(ECCreative.INFINITE_MRU_CONVERTER.get().getExplosionResistance() == ECCreative.BLAST_RESISTANCE, "无尽档抗爆应是 1200");
        helper.assertTrue(ECConverters.MRU_CONVERTER.get().getExplosionResistance() < 10F, "生存档抗爆照地脉凝聚器");
        helper.assertTrue(ECConverters.MRU_CONVERTER_BE.get().isValid(ECConverters.DEMONIC_MRU_CONVERTER.get().defaultBlockState())
                && !ECConverters.MRU_CONVERTER_BE.get().isValid(ECCreative.INFINITE_MRU_CONVERTER.get().defaultBlockState())
                && ECCreative.INFINITE_MRU_CONVERTER_BE.get().isValid(ECCreative.INFINITE_MRU_CONVERTER.get().defaultBlockState()),
                "四档生存共用 mru_converter 方块实体类型，无尽档用自己的");
        List<Component> tooltip = new ArrayList<>();
        ItemStack infinite = new ItemStack(ECCreative.INFINITE_MRU_CONVERTER.get());
        infinite.getItem().appendHoverText(infinite, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(!tooltip.isEmpty() && isKey(tooltip.get(tooltip.size() - 1), CreativeOnly.TOOLTIP_KEY), "无尽档提示最后一行应是「没有合成配方」");
        List<Component> basicTip = new ArrayList<>();
        ItemStack basic = new ItemStack(ECConverters.MRU_CONVERTER.get());
        basic.getItem().appendHoverText(basic, helper.getLevel(), basicTip, TooltipFlag.NORMAL);
        helper.assertTrue(basicTip.stream().noneMatch(line -> isKey(line, CreativeOnly.TOOLTIP_KEY)), "生存档不该写「没有合成配方」");
        BlockEntity placed = place(helper, CENTER, ECCreative.INFINITE_MRU_CONVERTER.get(), UUID.randomUUID(), 0L);
        helper.assertTrue(placed instanceof MRUConverterBlockEntity be && be.getInventory().getSlots() == 0 && be.getMRUStorage().getMaxMRU() == 0,
                "无尽档没有格子、没有 MRU 容量");
        helper.succeed();
    }

    /** 四档配方存在、各自吃上一档（低档吃转换矩阵）、原料只来自 minecraft / essentialcraft5；没有任何配方产出无尽档 */
    @GameTest(template = EMPTY)
    public static void converterRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String[][] chain = {
                {"mru_converter", "mru_conversion_matrix"},
                {"pale_mru_converter", "mru_converter"},
                {"mithriline_mru_converter", "pale_mru_converter"},
                {"demonic_mru_converter", "mithriline_mru_converter"}};
        Set<String> namespaces = Set.of("minecraft", EssentialCraft5.MODID);
        for (String[] link : chain) {
            Recipe<?> recipe = level.getRecipeManager().byKey(EssentialCraft5.id("converters/" + link[0])).orElse(null);
            helper.assertTrue(recipe != null, "缺配方 converters/" + link[0]);
            ItemStack result = recipe.getResultItem(level.registryAccess());
            helper.assertTrue(EssentialCraft5.id(link[0]).equals(ForgeRegistries.ITEMS.getKey(result.getItem())), "配方产物不对：" + result);
            boolean hasPrevious = false;
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack option : ingredient.getItems()) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(option.getItem());
                    helper.assertTrue(id != null && namespaces.contains(id.getNamespace()), link[0] + " 的原料不该来自别的模组：" + id);
                    if (EssentialCraft5.id(link[1]).equals(id)) {
                        hasPrevious = true;
                    }
                }
            }
            helper.assertTrue(hasPrevious, link[0] + " 的配方应吃 " + link[1]);
        }
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            helper.assertTrue(!recipe.getResultItem(level.registryAccess()).is(ECCreative.INFINITE_MRU_CONVERTER.get().asItem()),
                    "配方 " + recipe.getId() + " 做出了无尽档");
        }
        helper.succeed();
    }

    // ---------------- 换算 ----------------

    /** 低档 + 一个收得下的接收方：稳定后 40 刻收到 3 200 FE、扣 6 400 MRU（2 : 1）；接收方收到的就是转换器送出去的 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void converterFeRate(GameTestHelper helper) {
        Receiver receiver = bigReceiver(helper, EAST);
        MRUConverterBlockEntity be = basic(helper, CENTER);
        long[] mark = new long[2];
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.status() == Status.WORKING, "应在工作，实际 " + be.status());
                    mark[0] = receiver.received();
                    mark[1] = be.totalMruSpent();
                })
                .thenExecuteAfter(40, () -> {
                    long got = receiver.received() - mark[0];
                    long spent = be.totalMruSpent() - mark[1];
                    helper.assertTrue(got == 3200, "40 刻应收到 3200 FE，实际 " + got);
                    helper.assertTrue(spent == 6400, "40 刻应扣 6400 MRU，实际 " + spent);
                    helper.assertTrue(receiver.received() == be.totalOutput(), "接收方收到的应等于转换器送出去的");
                    assertConserved(helper, be, MRUConverterBlockEntity.Config.DEFAULT_FE_PER_1000, "默认比率");
                    forget(helper, EAST);
                })
                .thenSucceed();
    }

    /** 比率覆盖成 1000：扣的 MRU = 收到的 FE；比率 37（除不尽）跑 100 刻，扣量 × 比率 = 输出 + 缓存 + 零头，误差 0 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 300)
    public static void converterFeRatioOverride(GameTestHelper helper) {
        BlockPos oneA = new BlockPos(2, 1, 2);
        BlockPos oneR = new BlockPos(3, 1, 2);
        BlockPos oddA = new BlockPos(7, 1, 7);
        BlockPos oddR = new BlockPos(8, 1, 7);
        Receiver one = bigReceiver(helper, oneR);
        Receiver odd = bigReceiver(helper, oddR);
        MRUConverterBlockEntity a = basic(helper, oneA);
        MRUConverterBlockEntity b = basic(helper, oddA);
        a.setRatiosForTest(1000, -1);
        b.setRatiosForTest(37, -1);
        long[] mark = new long[2];
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    mark[0] = one.received();
                    mark[1] = a.totalMruSpent();
                })
                .thenExecuteAfter(40, () -> {
                    long got = one.received() - mark[0];
                    long spent = a.totalMruSpent() - mark[1];
                    helper.assertTrue(got == 6400 && spent == 6400, "比率 1000 时 40 刻应收 6400、扣 6400，实际 " + got + " / " + spent);
                })
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(b.totalMruSpent() > 3000, "比率 37 的那台应一直在吃 MRU，实际 " + b.totalMruSpent());
                    assertConserved(helper, b, 37, "比率 37");
                    helper.assertTrue(odd.received() == b.totalOutput(), "比率 37：接收方收到的应等于送出去的");
                    helper.assertTrue(b.carry() >= 0 && b.carry() < 1000, "零头应在 0~999，实际 " + b.carry());
                    forget(helper, oneR, oddR);
                })
                .thenSucceed();
    }

    /** 没有接收方：只吃「填满 1 秒缓存」的那一点（低档 1 600 FE = 3 200 MRU），之后不再吃；状态「旁边没人收」 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void converterNoReceiverNoDrain(GameTestHelper helper) {
        MRUConverterBlockEntity be = basic(helper, CENTER);
        helper.startSequence()
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(be.buffer() == be.bufferCap() && be.bufferCap() == 1600, "缓存应攒满 1600 FE，实际 " + be.buffer() + " / " + be.bufferCap());
                    helper.assertTrue(be.totalMruSpent() == 3200, "只该吃 3200 MRU，实际 " + be.totalMruSpent());
                    helper.assertTrue(be.status() == Status.NO_RECEIVER, "应是「旁边没人收」，实际 " + be.status());
                })
                .thenExecuteAfter(40, () -> helper.assertTrue(be.totalMruSpent() == 3200, "攒满以后不该再吃 MRU，实际 " + be.totalMruSpent()))
                .thenSucceed();
    }

    // ---------------- 输出 ----------------

    /**
     * 中档、三个面各只收 10：每个都正好每刻 10（合计 30 < 额度 80）。
     * 低档比率 1000（额度 160）、三个面各收 100：合计正好每刻 160，轮流起点让三个都分得到。
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 200)
    public static void converterSplitSixFaces(GameTestHelper helper) {
        BlockPos paleAt = new BlockPos(2, 1, 2);
        BlockPos[] paleFaces = {new BlockPos(3, 1, 2), new BlockPos(1, 1, 2), new BlockPos(2, 1, 1)};
        BlockPos basicAt = new BlockPos(7, 1, 7);
        BlockPos[] basicFaces = {new BlockPos(8, 1, 7), new BlockPos(6, 1, 7), new BlockPos(7, 1, 8)};
        Receiver[] small = new Receiver[3];
        Receiver[] wide = new Receiver[3];
        for (int i = 0; i < 3; i++) {
            small[i] = receiver(helper, paleFaces[i], 1_000_000, 10);
            wide[i] = receiver(helper, basicFaces[i], 1_000_000, 100);
        }
        MRUConverterBlockEntity pale = place(helper, paleAt, ECConverters.PALE_MRU_CONVERTER.get(), UUID.randomUUID(), 0L);
        MRUConverterBlockEntity basic = basic(helper, basicAt);
        basic.setRatiosForTest(1000, -1);
        long[] smallMark = new long[3];
        long[] wideMark = new long[3];
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    for (int i = 0; i < 3; i++) {
                        smallMark[i] = small[i].received();
                        wideMark[i] = wide[i].received();
                    }
                })
                .thenExecuteAfter(24, () -> {
                    long sum = 0;
                    for (int i = 0; i < 3; i++) {
                        long got = small[i].received() - smallMark[i];
                        helper.assertTrue(got == 240, "中档第 " + i + " 面 24 刻应正好收 240，实际 " + got);
                        long share = wide[i].received() - wideMark[i];
                        helper.assertTrue(share > 0, "低档第 " + i + " 面应分得到，实际 " + share);
                        sum += share;
                    }
                    helper.assertTrue(sum == 24L * 160, "低档 24 刻合计应正好 3840（每刻额度 160），实际 " + sum);
                    forget(helper, paleFaces);
                    forget(helper, basicFaces);
                })
                .thenSucceed();
    }

    /** 分法本身：从起点轮流、跳过 null 和不收的、按实收扣、合计不超过给的量 */
    @GameTest(template = EMPTY)
    public static void converterDistributeStatic(GameTestHelper helper) {
        EnergyStorage a = new EnergyStorage(100, 10, 0);
        EnergyStorage b = new EnergyStorage(100, 10, 0);
        EnergyStorage c = new EnergyStorage(100, 10, 0);
        EnergyStorage closed = new EnergyStorage(100, 0, 0);
        List<IEnergyStorage> targets = Arrays.asList(null, a, b, null, c, closed);
        int sent = MRUConverterBlockEntity.distribute(25, targets, 1);
        helper.assertTrue(sent == 25 && a.getEnergyStored() == 10 && b.getEnergyStored() == 10 && c.getEnergyStored() == 5,
                "从第 1 个起：10 / 10 / 5，实际 " + sent + "：" + a.getEnergyStored() + " / " + b.getEnergyStored() + " / " + c.getEnergyStored());
        sent = MRUConverterBlockEntity.distribute(25, targets, 4);
        helper.assertTrue(sent == 25 && c.getEnergyStored() == 15 && a.getEnergyStored() == 20 && b.getEnergyStored() == 15,
                "从第 4 个起绕回开头：c +10、a +10、b +5，实际 " + c.getEnergyStored() + " / " + a.getEnergyStored() + " / " + b.getEnergyStored());
        helper.assertTrue(closed.getEnergyStored() == 0, "不收的那个不该收到");
        sent = MRUConverterBlockEntity.distribute(1000, targets, 0);
        helper.assertTrue(sent == 30, "三个各收 10：合计 30，实际 " + sent);
        helper.assertTrue(MRUConverterBlockEntity.distribute(0, targets, 0) == 0 && MRUConverterBlockEntity.distribute(10, List.of(), 0) == 0, "没得分时是 0");
        helper.succeed();
    }

    /** 电缆从它这里抽：每刻合计不超过额度（低档 80）；不收能量；魔力模式下抽不出 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void converterExtractByPipe(GameTestHelper helper) {
        MRUConverterBlockEntity be = basic(helper, CENTER);
        IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElse(null);
        helper.assertTrue(cap != null, "两种模式都该挂能量能力");
        helper.assertTrue(!cap.canReceive() && cap.receiveEnergy(1000, false) == 0, "不收能量");
        helper.startSequence()
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(cap.canExtract() && cap.getEnergyStored() == 1600, "缓存应满 1600，实际 " + cap.getEnergyStored());
                    int first = cap.extractEnergy(1000, false);
                    int second = cap.extractEnergy(1000, false);
                    helper.assertTrue(first == 80 && second == 0, "同一刻合计不超过 80，实际 " + first + " + " + second);
                    helper.assertTrue(cap.extractEnergy(1000, true) == 0, "模拟也该按额度算");
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(cap.extractEnergy(5, false) == 5, "下一刻额度清零，能再抽");
                    be.setManaBridgeForTest(availableEmptyBridge());
                    helper.assertTrue(be.setMode(Mode.MANA), "假桥可用时应能切到魔力");
                    helper.assertTrue(!cap.canExtract() && cap.extractEnergy(1000, false) == 0 && cap.getEnergyStored() == 0, "魔力模式下抽不出能量");
                })
                .thenSucceed();
    }

    /** 红石：信号期间不输出、不吃 MRU；撤掉接着走 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void converterRedstonePauses(GameTestHelper helper) {
        Receiver receiver = bigReceiver(helper, EAST);
        MRUConverterBlockEntity be = basic(helper, CENTER);
        BlockPos torch = CENTER.above();
        long[] mark = new long[2];
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.status() == Status.WORKING, "应在工作，实际 " + be.status());
                    helper.setBlock(torch, Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(2, () -> {
                    mark[0] = receiver.received();
                    mark[1] = be.totalMruSpent();
                })
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(be.status() == Status.POWERED, "应是红石暂停，实际 " + be.status());
                    helper.assertTrue(receiver.received() == mark[0] && be.totalMruSpent() == mark[1], "暂停时不该输出、不该吃 MRU");
                    helper.setBlock(torch, Blocks.AIR);
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(receiver.received() > mark[0], "撤掉红石后应接着输出");
                    forget(helper, EAST);
                })
                .thenSucceed();
    }

    /** 绑定宝石：绑到无尽 MRU 源，宝石状态「工作中」、MRU 进来，转换器照常工作 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void converterBoundGemPull(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        helper.setBlock(source, ECCreative.INFINITE_MRU_SOURCE.get());
        Receiver receiver = bigReceiver(helper, EAST);
        MRUConverterBlockEntity be = placeUnfed(helper, CENTER, ECConverters.MRU_CONVERTER.get(), UUID.randomUUID(), 0L);
        be.getMRUStorage().setMRU(0);
        ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(gem, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(source)), true);
        be.getInventory().setStackInSlot(MRUConverterBlockEntity.SLOT_GEM, gem);
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(be.getLinkStatus() == MRULink.Status.WORKING, "宝石应在工作，实际 " + be.getLinkStatus());
                    helper.assertTrue(be.status() == Status.WORKING && receiver.received() > 0, "接上 MRU 后应在输出，实际 " + be.status());
                    forget(helper, EAST);
                })
                .thenSucceed();
    }

    /** 接收方拆掉：不报错、不再推；原处放回（同坐标的新方块实体）接着推 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void converterNeighborChange(GameTestHelper helper) {
        Receiver receiver = bigReceiver(helper, EAST);
        MRUConverterBlockEntity be = basic(helper, CENTER);
        long[] mark = new long[1];
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(receiver.received() > 0, "应先推进去一些");
                    helper.setBlock(EAST, Blocks.AIR);
                    mark[0] = receiver.received();
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(receiver.received() == mark[0], "拆掉以后不该再推");
                    helper.assertTrue(be.status() == Status.NO_RECEIVER, "应是「旁边没人收」，实际 " + be.status());
                    helper.setBlock(EAST, Blocks.BARREL);
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(receiver.received() > mark[0], "放回以后应接着推");
                    forget(helper, EAST);
                })
                .thenSucceed();
    }

    /** 没人收 20 刻后退避；放上接收方立即恢复（不等下一个 20 刻） */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void converterIdleBackoff(GameTestHelper helper) {
        MRUConverterBlockEntity be = basic(helper, CENTER);
        Receiver[] receiver = new Receiver[1];
        helper.startSequence()
                .thenExecuteAfter(45, () -> {
                    helper.assertTrue(be.isBackingOff(), "没人收 20 刻后应退避");
                    receiver[0] = bigReceiver(helper, EAST);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(receiver[0].received() > 0, "放上接收方 2 刻内应开始推，实际 " + receiver[0].received());
                    helper.assertTrue(!be.isBackingOff(), "推出去了应退出退避");
                    forget(helper, EAST);
                })
                .thenSucceed();
    }

    /** 四档每刻输出 = 各自上限（80 / 320 / 1 280 / 5 120 FE）；无尽档不放宝石、不吃 MRU，每刻 5 120 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 200)
    public static void converterTierRates(GameTestHelper helper) {
        List<Block> blocks = allBlocks();
        int[] perTick = {80, 320, 1280, 5120, 5120};
        UUID owner = UUID.randomUUID();
        List<MRUConverterBlockEntity> converters = new ArrayList<>();
        List<Receiver> receivers = new ArrayList<>();
        List<BlockPos> receiverAt = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos at = new BlockPos(1 + i * 2, 1, 5);
            BlockPos r = at.south();
            receivers.add(bigReceiver(helper, r));
            receiverAt.add(r);
            MRUConverterBlockEntity be = place(helper, at, blocks.get(i), owner, i);
            converters.add(be);
        }
        long[] mark = new long[blocks.size()];
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    for (int i = 0; i < blocks.size(); i++) {
                        mark[i] = receivers.get(i).received();
                        helper.assertTrue(converters.get(i).outputQuota() == perTick[i], "第 " + i + " 台每刻上限应是 " + perTick[i]
                                + "，实际 " + converters.get(i).outputQuota());
                    }
                })
                .thenExecuteAfter(20, () -> {
                    for (int i = 0; i < blocks.size(); i++) {
                        long got = receivers.get(i).received() - mark[i];
                        helper.assertTrue(got == 20L * perTick[i], "第 " + i + " 台 20 刻应收 " + 20L * perTick[i] + "，实际 " + got);
                    }
                    MRUConverterBlockEntity infinite = converters.get(4);
                    helper.assertTrue(infinite.totalMruSpent() == 0 && infinite.getMRUStorage().getMRU() == 0, "无尽档不吃 MRU");
                    forget(helper, receiverAt.toArray(BlockPos[]::new));
                })
                .thenSucceed();
    }

    // ---------------- 植物魔法隔离 ----------------

    /**
     * 不挂植物魔法时：桥不可用；按钮 1 被拒、模式不变；存档里强行写成魔力模式读进来 → 停在「植物魔法没有加载」、不吃 MRU、不出 FE；
     * 开菜单、tick 200 刻都不抛异常（这条就是「没装时不加载植物魔法的类」的回归）。挂了植物魔法的那一轮直接通过。
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void converterManaWithoutBotania(GameTestHelper helper) {
        if (ModList.get().isLoaded(ManaBridge.MODID)) {
            helper.succeed();
            return;
        }
        helper.assertTrue(!ManaBridge.get().available(), "没装植物魔法时桥不该可用");
        Receiver receiver = bigReceiver(helper, EAST);
        MRUConverterBlockEntity be = basic(helper, CENTER);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        MRUConverterMenu menu = new MRUConverterMenu(0, player.getInventory(), be);
        helper.assertTrue(!menu.clickMenuButton(player, Mode.MANA.ordinal()) && be.mode() == Mode.ENERGY, "没装植物魔法时魔力按钮应被拒");
        helper.assertTrue(!menu.botaniaAvailable() && !menu.modeUsable(Mode.MANA), "菜单应同步「植物魔法不可用」");
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putString(MRUConverterBlockEntity.TAG_MODE, Mode.MANA.getSerializedName());
        be.load(tag);
        long[] mark = new long[2];
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    mark[0] = receiver.received();
                    mark[1] = be.totalMruSpent();
                })
                .thenExecuteAfter(200, () -> {
                    helper.assertTrue(be.mode() == Mode.MANA && be.status() == Status.NO_BOTANIA, "应停在「植物魔法没有加载」，实际 " + be.status());
                    helper.assertTrue(be.totalMruSpent() == mark[1] && receiver.received() == mark[0], "不该吃 MRU、不该出 FE");
                    helper.assertTrue(be.getBlockState().getValue(MRUConverterBlock.MODE) == Mode.MANA, "方块状态应跟着读进来的模式");
                    MRUConverterMenu reopened = new MRUConverterMenu(1, player.getInventory(), be);
                    helper.assertTrue(reopened.status() == Status.NO_BOTANIA, "菜单应同步状态");
                    helper.assertTrue(reopened.clickMenuButton(player, Mode.ENERGY.ordinal()) && be.mode() == Mode.ENERGY, "点「能量」就能用");
                    forget(helper, EAST);
                })
                .thenSucceed();
    }

    /** 按钮：−1 / 2 被拒；比率 0 的那种按不上；和当前相同被拒；客户端菜单一律拒 */
    @GameTest(template = EMPTY)
    public static void converterButtonValidation(GameTestHelper helper) {
        MRUConverterBlockEntity be = basic(helper, CENTER);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        MRUConverterMenu menu = new MRUConverterMenu(0, player.getInventory(), be);
        helper.assertTrue(!menu.clickMenuButton(player, -1) && !menu.clickMenuButton(player, 2) && !menu.clickMenuButton(player, 99), "越界的按钮号应被拒");
        helper.assertTrue(!menu.clickMenuButton(player, Mode.ENERGY.ordinal()), "和当前相同应返回 false");
        be.setManaBridgeForTest(availableEmptyBridge());
        be.setRatiosForTest(-1, 0);
        helper.assertTrue(!menu.clickMenuButton(player, Mode.MANA.ordinal()) && be.mode() == Mode.ENERGY, "比率 0 的魔力按不上");
        be.setRatiosForTest(-1, -1);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(be.getBlockPos());
        buf.writeByte(be.tier().ordinal());
        MRUConverterMenu clientMenu = new MRUConverterMenu(1, player.getInventory(), buf);
        helper.assertTrue(!clientMenu.clickMenuButton(player, Mode.MANA.ordinal()) && be.mode() == Mode.ENERGY, "客户端菜单的按钮应被拒");
        helper.assertTrue(clientMenu.tier() == ConverterTier.BASIC && clientMenu.slots.size() == 1 + 36, "客户端菜单按额外数据定档位和槽数");
        helper.assertTrue(menu.clickMenuButton(player, Mode.MANA.ordinal()) && be.mode() == Mode.MANA, "比率恢复后魔力应能选");
        be.setRatiosForTest(0, -1);
        helper.assertTrue(!menu.clickMenuButton(player, Mode.ENERGY.ordinal()) && be.mode() == Mode.MANA, "比率 0 的能量按不上");
        MRUConverterBlockEntity infinite = place(helper, new BlockPos(0, 1, 0), ECCreative.INFINITE_MRU_CONVERTER.get(), UUID.randomUUID(), 0L);
        MRUConverterMenu infiniteMenu = new MRUConverterMenu(2, player.getInventory(), infinite);
        helper.assertTrue(infiniteMenu.slots.size() == 36, "无尽档菜单没有机器格");
        helper.succeed();
    }

    /** 切模式：能量缓存和零头清零，方块状态立即改成 mana（用「可用但找不到接收方」的假桥） */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void converterModeSwitchClears(GameTestHelper helper) {
        MRUConverterBlockEntity be = basic(helper, CENTER);
        be.setRatiosForTest(37, -1);
        be.setManaBridgeForTest(availableEmptyBridge());
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(be.buffer() > 0 || be.carry() > 0, "能量模式应先攒下一些");
                    ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
                    MRUConverterMenu menu = new MRUConverterMenu(0, player.getInventory(), be);
                    helper.assertTrue(menu.clickMenuButton(player, Mode.MANA.ordinal()), "切到魔力应成功");
                    helper.assertTrue(be.buffer() == 0 && be.carry() == 0, "切换应清掉缓存和零头，实际 " + be.buffer() + " / " + be.carry());
                    helper.assertTrue(be.getBlockState().getValue(MRUConverterBlock.MODE) == Mode.MANA, "方块状态应立即改成 mana");
                })
                .thenExecuteAfter(5, () -> helper.assertTrue(be.status() == Status.NO_RECEIVER, "魔力模式旁边没人收，实际 " + be.status()))
                .thenSucceed();
    }

    // ---------------- 名额与主人 ----------------

    /**
     * 魔力推完存量不动的面（魔力分配器、魔力虚空这类，桥返回 BLIND）：推过一次就不再推，只记那一次；
     * 这一面的邻居换了（邻居更新）才重新查、重新试一次
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void converterManaBlindFace(GameTestHelper helper) {
        helper.setBlock(EAST, Blocks.BARREL);
        MRUConverterBlockEntity be = basic(helper, CENTER);
        LoyalBridge bridge = new LoyalBridge();
        bridge.blind = true;
        be.setManaBridgeForTest(bridge);
        helper.assertTrue(be.setMode(Mode.MANA), "假桥可用时应能切到魔力");
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(bridge.accepts.size() == 1, "存量不动的面只该推一次，实际 " + bridge.accepts.size());
                    helper.assertTrue(be.totalOutput() == 4, "只记那一次（低档每刻 4 魔力），实际 " + be.totalOutput());
                    helper.assertTrue(be.status() == Status.NO_RECEIVER, "之后算旁边没人收，实际 " + be.status());
                    helper.setBlock(EAST, Blocks.CHEST);
                })
                .thenExecuteAfter(5, () -> helper.assertTrue(bridge.accepts.size() == 2, "邻居换了应重新试一次，实际 " + bridge.accepts.size()))
                .thenSucceed();
    }

    /**
     * 邻居的能力从不作废（植物魔法就是这样）、邻居方块实体却被移除了（区块卸载时 setRemoved，没有邻居更新）：
     * 不再往已移除的旧对象里推，下一次用缓存前就发现、重新查
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void converterStaleNeighbourRequery(GameTestHelper helper) {
        helper.setBlock(EAST, Blocks.BARREL);
        BlockEntity old = helper.getBlockEntity(EAST);
        MRUConverterBlockEntity be = basic(helper, CENTER);
        LoyalBridge bridge = new LoyalBridge();
        be.setManaBridgeForTest(bridge);
        helper.assertTrue(be.setMode(Mode.MANA), "假桥可用时应能切到魔力");
        int[] mark = new int[1];
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(!bridge.accepts.isEmpty() && bridge.accepts.stream().allMatch(h -> h == old), "先往木桶里推");
                    old.setRemoved();
                    mark[0] = bridge.accepts.size();
                })
                .thenExecuteAfter(20, () -> {
                    long stale = bridge.accepts.subList(mark[0], bridge.accepts.size()).stream().filter(h -> h == old).count();
                    helper.assertTrue(stale == 0, "移除以后不该再往旧的方块实体里推，实际推了 " + stale + " 次");
                    helper.setBlock(EAST, Blocks.AIR);
                })
                .thenSucceed();
    }

    /** 同主人上限 2、3 台（第二台是无尽档）：第 3 台排队；拆掉第一台，第 3 台 120 刻内接上；另一个主人的不受影响 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 400)
    public static void converterOwnerLimit(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        BlockPos firstAt = new BlockPos(1, 1, 5);
        MRUConverterBlockEntity first = place(helper, firstAt, ECConverters.MRU_CONVERTER.get(), owner, 100L);
        MRUConverterBlockEntity second = place(helper, new BlockPos(3, 1, 5), ECCreative.INFINITE_MRU_CONVERTER.get(), owner, 200L);
        MRUConverterBlockEntity third = place(helper, new BlockPos(5, 1, 5), ECConverters.MRU_CONVERTER.get(), owner, 300L);
        MRUConverterBlockEntity other = place(helper, new BlockPos(7, 1, 5), ECConverters.MRU_CONVERTER.get(), UUID.randomUUID(), 400L);
        for (MRUConverterBlockEntity be : List.of(first, second, third, other)) {
            be.setOwnerLimitForTest(2);
        }
        long[] removedAt = {-1L};
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(first.status() != Status.OWNER_LIMIT && second.status() != Status.OWNER_LIMIT, "最早的两台应在工作");
                    helper.assertTrue(third.status() == Status.OWNER_LIMIT, "第 3 台应排队（无尽档也算一台），实际 " + third.status());
                    helper.assertTrue(third.totalMruSpent() == 0, "排队的不该吃 MRU");
                    helper.assertTrue(other.status() != Status.OWNER_LIMIT, "另一个主人的不受影响，实际 " + other.status());
                    helper.assertTrue(first.isListed(), "占着名额的应在名额表里");
                    helper.assertTrue(!third.isListed(), "排队的不占名额，不在名额表里");
                    helper.setBlock(firstAt, Blocks.AIR);
                    helper.assertTrue(!first.isListed(), "拆掉的应从名额表里移除");
                    removedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getTick() - removedAt[0] <= 120, "拆掉一台 120 刻内第 3 台应接上");
                    helper.assertTrue(third.status() != Status.OWNER_LIMIT, "第 3 台应接上，实际 " + third.status());
                })
                .thenSucceed();
    }

    /** 专用服（GameTest 服）上没有主人的：NO_OWNER，不吃 MRU */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void converterNoOwnerIdles(GameTestHelper helper) {
        Receiver receiver = bigReceiver(helper, EAST);
        MRUConverterBlockEntity be = place(helper, CENTER, ECConverters.MRU_CONVERTER.get(), null, 0L);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(be.status() == Status.NO_OWNER, "没有主人应不工作，实际 " + be.status());
            helper.assertTrue(be.totalMruSpent() == 0 && receiver.received() == 0, "不该吃 MRU、不该输出");
            forget(helper, EAST);
            helper.succeed();
        });
    }

    // ---------------- 泄漏、方块状态、存盘 ----------------

    /** 生存档工作时每 20 刻漏一次；没人收（没工作）不漏；无尽档从不漏 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 200)
    public static void converterCorruption(GameTestHelper helper) {
        BlockPos workingAt = new BlockPos(2, 1, 2);
        BlockPos workingR = new BlockPos(3, 1, 2);
        BlockPos infiniteAt = new BlockPos(7, 1, 7);
        BlockPos infiniteR = new BlockPos(8, 1, 7);
        bigReceiver(helper, workingR);
        bigReceiver(helper, infiniteR);
        MRUConverterBlockEntity working = basic(helper, workingAt);
        MRUConverterBlockEntity idle = basic(helper, new BlockPos(2, 1, 8));
        MRUConverterBlockEntity infinite = place(helper, infiniteAt, ECCreative.INFINITE_MRU_CONVERTER.get(), UUID.randomUUID(), 0L);
        helper.runAfterDelay(61, () -> {
            helper.assertTrue(working.leakCount() >= 2 && working.leakCount() <= 3, "工作 60 刻应漏 2~3 次，实际 " + working.leakCount());
            helper.assertTrue(idle.leakCount() == 0, "没工作不该漏，实际 " + idle.leakCount());
            helper.assertTrue(infinite.status() == Status.WORKING && infinite.leakCount() == 0, "无尽档工作时也不漏，实际 " + infinite.leakCount());
            forget(helper, workingR, infiniteR);
            helper.succeed();
        });
    }

    /** active：推出去以后变 true、没人收以后变 false，两次切换至少隔 20 刻；切换不给邻居发更新（旁边那台转换器收不到邻居变化） */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void converterActiveState(GameTestHelper helper) {
        BlockPos watcherAt = CENTER.west();
        MRUConverterBlockEntity watcher = place(helper, watcherAt, ECConverters.MRU_CONVERTER.get(), null, 0L);
        bigReceiver(helper, EAST);
        MRUConverterBlockEntity be = basic(helper, CENTER);
        int[] events = new int[1];
        long[] onAt = {-1L};
        helper.startSequence()
                .thenExecute(() -> events[0] = watcher.neighbourEvents())
                .thenWaitUntil(() -> helper.assertTrue(be.getBlockState().getValue(MRUConverterBlock.ACTIVE), "推出去以后应变 active"))
                .thenExecute(() -> {
                    onAt[0] = helper.getTick();
                    helper.assertTrue(watcher.neighbourEvents() == events[0], "切 active 不该给邻居发更新");
                    helper.setBlock(EAST, Blocks.AIR);
                    events[0] = watcher.neighbourEvents();
                })
                .thenWaitUntil(() -> helper.assertTrue(!be.getBlockState().getValue(MRUConverterBlock.ACTIVE), "没人收以后应变回待机"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getTick() - onAt[0] >= MRUConverterBlockEntity.ACTIVE_WINDOW, "两次切换至少隔 20 刻");
                    helper.assertTrue(watcher.neighbourEvents() == events[0], "切回待机也不该给邻居发更新");
                    forget(helper, EAST);
                })
                .thenSucceed();
    }

    /** 存读档：模式、缓存、零头一致；每刻上限改小后读档缓存截到上限；清空后挖掉只掉方块本身（这里 setBlock 成空气，一件都不掉） */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void converterSaveLoad(GameTestHelper helper) {
        MRUConverterBlockEntity be = basic(helper, CENTER);
        be.setRatiosForTest(37, -1);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.buffer() > 0, "应先攒下一些");
            CompoundTag tag = be.saveWithoutMetadata();
            MRUConverterBlockEntity copy = new MRUConverterBlockEntity(be.getBlockPos(), be.getBlockState());
            copy.setRatiosForTest(37, -1);
            copy.load(tag);
            helper.assertTrue(copy.mode() == be.mode() && copy.buffer() == be.buffer() && copy.carry() == be.carry()
                    && copy.placedTick() == be.placedTick(), "读回来应一致");
            MRUConverterBlockEntity shrunk = new MRUConverterBlockEntity(be.getBlockPos(), be.getBlockState());
            shrunk.setRatiosForTest(37, -1);
            shrunk.setPerTickForTest(5);
            shrunk.load(tag);
            helper.assertTrue(shrunk.buffer() <= shrunk.bufferCap() && shrunk.bufferCap() < be.buffer(),
                    "上限改小后应截到上限，实际 " + shrunk.buffer() + " / " + shrunk.bufferCap());
            ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
            be.getInventory().setStackInSlot(MRUConverterBlockEntity.SLOT_GEM, gem);
            be.clearContent();
            helper.assertTrue(be.getInventory().getStackInSlot(MRUConverterBlockEntity.SLOT_GEM).isEmpty(), "clearContent 应清空宝石格");
            helper.setBlock(CENTER, Blocks.AIR);
            AABB box = new AABB(helper.absolutePos(CENTER)).inflate(2D);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).isEmpty(), "清空后移除不该掉任何东西");
            helper.succeed();
        });
    }
}
