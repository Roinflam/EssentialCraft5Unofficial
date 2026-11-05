package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IElementalCrystal.Element;
import com.flameforge.essentialcraft5.blockentity.ElementalDecomposerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ElementalDecomposerBlockEntity.Status;
import com.flameforge.essentialcraft5.registry.ECDecomposer;
import com.flameforge.essentialcraft5.registry.ECEssence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * 元素分解器（batch12.md §4.8.8）的 GameTest：按标签分元素、32 件出一颗、每种第 16 颗是罕见；同时在几个标签里时火优先；
 * 一种元素的输出格满了只停这一种；没标签的东西漏斗塞不进；每件扣的 MRU；红石暂停。
 * 速度和价格用 {@link ElementalDecomposerBlockEntity#setTestOverrides}（每刻一件），出货数按默认配置（32 / 16）算，不改真配置。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class DecomposerTests {

    private static final String EMPTY = "empty";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);

    private DecomposerTests() {
    }

    // ---------------- 工具 ----------------

    /** 放一台分解器：每刻一件、价格 mruPerItem（−1 = 配置），MRU 加满 */
    private static ElementalDecomposerBlockEntity place(GameTestHelper helper, int mruPerItem) {
        helper.setBlock(CENTER, ECDecomposer.ELEMENTAL_DECOMPOSER.get());
        ElementalDecomposerBlockEntity be = (ElementalDecomposerBlockEntity) helper.getBlockEntity(CENTER);
        be.setTestOverrides(1, mruPerItem);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        return be;
    }

    private static void input(ElementalDecomposerBlockEntity be, int index, Item item, int count) {
        be.getInventory().setStackInSlot(ElementalDecomposerBlockEntity.FIRST_INPUT + index, new ItemStack(item, count));
    }

    private static int inputCount(ElementalDecomposerBlockEntity be, Item item) {
        int count = 0;
        for (int slot = ElementalDecomposerBlockEntity.FIRST_INPUT; slot < ElementalDecomposerBlockEntity.FIRST_OUTPUT; slot++) {
            ItemStack stack = be.getInventory().getStackInSlot(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ItemStack output(ElementalDecomposerBlockEntity be, Element element, boolean uncommon) {
        return be.getInventory().getStackInSlot(ElementalDecomposerBlockEntity.outputSlot(element, uncommon));
    }

    private static boolean outputIs(ElementalDecomposerBlockEntity be, Element element, boolean uncommon, int count) {
        ItemStack stack = output(be, element, uncommon);
        if (count == 0) {
            return stack.isEmpty();
        }
        return stack.is(ECEssence.essence(uncommon ? 1 : 0, element)) && stack.getCount() == count;
    }

    // ---------------- 测试 ----------------

    /** 32 个圆石 → 1 颗普通地之精华，点数清零；默认配置 32 件 / 颗、16 颗一颗罕见 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void routesByTag(GameTestHelper helper) {
        helper.assertTrue(ElementalDecomposerBlockEntity.pointsPerEssence() == 32, "默认 32 件出一颗");
        helper.assertTrue(ElementalDecomposerBlockEntity.elementOf(new ItemStack(Items.COBBLESTONE)) == Element.EARTH, "圆石是地");
        helper.assertTrue(ElementalDecomposerBlockEntity.elementOf(new ItemStack(Items.NETHERRACK)) == Element.FIRE, "下界岩是火");
        helper.assertTrue(ElementalDecomposerBlockEntity.elementOf(new ItemStack(Items.KELP)) == Element.WATER, "海带是水");
        helper.assertTrue(ElementalDecomposerBlockEntity.elementOf(new ItemStack(Items.FEATHER)) == Element.AIR, "羽毛是风");
        helper.assertTrue(ElementalDecomposerBlockEntity.elementOf(new ItemStack(Items.OAK_LEAVES)) == Element.AIR, "树叶（标签）是风");
        ElementalDecomposerBlockEntity be = place(helper, -1);
        input(be, 0, Items.COBBLESTONE, 32);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(inputCount(be, Items.COBBLESTONE) == 0, "圆石应分完"))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(outputIs(be, Element.EARTH, false, 1), "应出 1 颗普通地之精华，实际 " + output(be, Element.EARTH, false));
                    helper.assertTrue(be.getPoints(Element.EARTH) == 0 && be.getMade(Element.EARTH) == 1, "点数应清零、出货计 1");
                    for (Element other : new Element[]{Element.FIRE, Element.WATER, Element.AIR}) {
                        helper.assertTrue(outputIs(be, other, false, 0) && be.getPoints(other) == 0, other + " 不该有东西");
                    }
                    helper.assertTrue(be.getStatus() == Status.NO_INPUT, "分完应是「没东西可拆」，实际 " + be.getStatus());
                })
                .thenSucceed();
    }

    /** 512 个圆石 → 16 颗：15 普通 + 1 罕见（第 16 颗） */
    @GameTest(template = EMPTY, timeoutTicks = 700)
    public static void uncommonEvery16(GameTestHelper helper) {
        helper.assertTrue(!ElementalDecomposerBlockEntity.isUncommon(15, 16) && ElementalDecomposerBlockEntity.isUncommon(16, 16)
                && ElementalDecomposerBlockEntity.isUncommon(32, 16) && !ElementalDecomposerBlockEntity.isUncommon(16, 0), "罕见判定公式");
        ElementalDecomposerBlockEntity be = place(helper, 0);
        for (int i = 0; i < 8; i++) {
            input(be, i, Items.COBBLESTONE, 64);
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(inputCount(be, Items.COBBLESTONE) == 0, "512 个圆石应分完，还剩 " + inputCount(be, Items.COBBLESTONE)))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(outputIs(be, Element.EARTH, false, 15), "应 15 颗普通，实际 " + output(be, Element.EARTH, false));
                    helper.assertTrue(outputIs(be, Element.EARTH, true, 1), "应 1 颗罕见，实际 " + output(be, Element.EARTH, true));
                    helper.assertTrue(be.getMade(Element.EARTH) == 16 && be.getPoints(Element.EARTH) == 0, "出货计 16、点数 0");
                })
                .thenSucceed();
    }

    /** 同时在火、风两个标签里 → 火；水和风 → 水；都不在 → 没有；默认标签里没有一件同时属于两种 */
    @GameTest(template = EMPTY)
    public static void priorityFireFirst(GameTestHelper helper) {
        helper.assertTrue(ElementalDecomposerBlockEntity.elementOf(tag -> tag == ECDecomposer.FIRE || tag == ECDecomposer.AIR) == Element.FIRE,
                "火和风都有时应取火");
        helper.assertTrue(ElementalDecomposerBlockEntity.elementOf(tag -> tag == ECDecomposer.WATER || tag == ECDecomposer.AIR) == Element.WATER,
                "水和风都有时应取水");
        helper.assertTrue(ElementalDecomposerBlockEntity.elementOf(tag -> tag == ECDecomposer.EARTH || tag == ECDecomposer.AIR) == Element.EARTH,
                "地和风都有时应取地");
        helper.assertTrue(ElementalDecomposerBlockEntity.elementOf(tag -> false) == null, "都不在应没有元素");
        Map<Item, Element> seen = new HashMap<>();
        for (Element element : Element.values()) {
            int size = 0;
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(ECDecomposer.tag(element))) {
                Element before = seen.put(holder.value(), element);
                helper.assertTrue(before == null, ForgeRegistries.ITEMS.getKey(holder.value()) + " 同时在 " + before + " 和 " + element + " 里");
                helper.assertTrue(!ForgeRegistries.ITEMS.getKey(holder.value()).getNamespace().equals(EssentialCraft5.MODID),
                        "默认标签里不该有本模组物品：" + ForgeRegistries.ITEMS.getKey(holder.value()));
                size++;
            }
            helper.assertTrue(size > 0, element + " 的标签不该是空的");
        }
        helper.succeed();
    }

    /** 地之精华普通格满了：圆石不再消耗，羽毛照样分；状态还是在干活 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void skipsFullOutput(GameTestHelper helper) {
        ElementalDecomposerBlockEntity be = place(helper, 0);
        be.getInventory().setStackInSlot(ElementalDecomposerBlockEntity.outputSlot(Element.EARTH, false),
                new ItemStack(ECEssence.essence(0, Element.EARTH), 64));
        input(be, 0, Items.COBBLESTONE, 16);
        input(be, 1, Items.FEATHER, 16);
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(inputCount(be, Items.COBBLESTONE) == 16, "地的输出格满了，圆石不该少，实际 " + inputCount(be, Items.COBBLESTONE));
                    helper.assertTrue(inputCount(be, Items.FEATHER) < 16 && be.getPoints(Element.AIR) > 0, "羽毛应照样分");
                })
                .thenWaitUntil(() -> helper.assertTrue(inputCount(be, Items.FEATHER) == 0, "羽毛应分完"))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(be.getStatus() == Status.OUTPUT_FULL, "只剩圆石时应是「输出满了」，实际 " + be.getStatus());
                    helper.assertTrue(inputCount(be, Items.COBBLESTONE) == 16 && be.getPoints(Element.EARTH) == 0, "圆石一件都不该动");
                })
                .thenSucceed();
    }

    /** 漏斗：钻石塞不进输入格、圆石塞得进；输出格和宝石格都塞不进；输入格抽不出来，精华格抽得出来 */
    @GameTest(template = EMPTY)
    public static void rejectsUntagged(GameTestHelper helper) {
        ElementalDecomposerBlockEntity be = place(helper, -1);
        be.setTestOverrides(1200, -1);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("分解器应暴露物品能力"));
        int first = ElementalDecomposerBlockEntity.FIRST_INPUT;
        helper.assertTrue(handler.insertItem(first, new ItemStack(Items.DIAMOND), true).getCount() == 1, "钻石不该塞得进");
        helper.assertTrue(handler.insertItem(first, new ItemStack(Items.IRON_INGOT), true).getCount() == 1, "铁锭不该塞得进");
        helper.assertTrue(handler.insertItem(first, new ItemStack(Items.COBBLESTONE, 8), false).isEmpty(), "圆石应塞得进");
        for (int slot = ElementalDecomposerBlockEntity.FIRST_OUTPUT; slot < ElementalDecomposerBlockEntity.SLOTS; slot++) {
            helper.assertTrue(handler.insertItem(slot, new ItemStack(Items.COBBLESTONE), true).getCount() == 1, "输出格 " + slot + " 不该塞得进");
        }
        Item gem = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("bound_gem"));
        helper.assertTrue(gem != null && gem != Items.AIR, "绑定宝石应已注册");
        helper.assertTrue(handler.insertItem(ElementalDecomposerBlockEntity.SLOT_GEM, new ItemStack(gem), true).getCount() == 1, "宝石格不收漏斗");
        helper.assertTrue(handler.extractItem(first, 1, true).isEmpty(), "输入格不该被抽走");
        helper.assertTrue(!be.getUpdateTag().contains("inventory"), "物品栏不该随更新包发给附近的客户端");
        int out = ElementalDecomposerBlockEntity.outputSlot(Element.FIRE, false);
        be.getInventory().setStackInSlot(out, new ItemStack(ECEssence.essence(0, Element.FIRE), 3));
        ItemStack pulled = handler.extractItem(out, 3, false);
        helper.assertTrue(pulled.getCount() == 3, "精华格应抽得出来，实际 " + pulled);
        helper.succeed();
    }

    /** 分 10 件扣 300 MRU（默认每件 30）；不够一件的钱时不拿东西 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void mruPerItem(GameTestHelper helper) {
        ElementalDecomposerBlockEntity be = place(helper, -1);
        int start = be.getMRUStorage().getMRU();
        input(be, 0, Items.COBBLESTONE, 10);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(inputCount(be, Items.COBBLESTONE) == 0, "10 件应分完"))
                .thenExecuteAfter(2, () -> {
                    int spent = start - be.getMRUStorage().getMRU();
                    helper.assertTrue(spent == 300, "10 件应正好扣 300 MRU，实际 " + spent);
                    helper.assertTrue(be.getPoints(Element.EARTH) == 10, "应记 10 点地");
                    be.getMRUStorage().setMRU(29);
                    input(be, 0, Items.COBBLESTONE, 5);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.getStatus() == Status.NO_MRU, "不够 30 应是「MRU 不够」，实际 " + be.getStatus());
                    helper.assertTrue(inputCount(be, Items.COBBLESTONE) == 5 && be.getMRUStorage().getMRU() == 29, "没钱时不拿东西、不扣钱");
                })
                .thenSucceed();
    }

    /** 红石信号：暂停、什么都不拿；去掉信号接着分 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void redstonePauses(GameTestHelper helper) {
        ElementalDecomposerBlockEntity be = place(helper, 0);
        BlockPos power = CENTER.east();
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        input(be, 0, Items.COBBLESTONE, 16);
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(be.getStatus() == Status.PAUSED, "有红石信号应暂停，实际 " + be.getStatus());
                    helper.assertTrue(inputCount(be, Items.COBBLESTONE) == 16, "暂停时不该拿东西");
                    helper.setBlock(power, Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(inputCount(be, Items.COBBLESTONE) < 16, "去掉信号应接着分"))
                .thenExecute(() -> helper.assertTrue(be.getStatus() == Status.WORKING, "应在干活，实际 " + be.getStatus()))
                .thenSucceed();
    }
}
