package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MagicalRepairerBlockEntity;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.registry.ECDevices;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * 能同调的四套盔甲不可修理（第十三批 13b，batch13.md §4.9；{@code ECTags.MRU_REPAIRABLE}）：
 * <ul>
 *   <li>魔力防护、风之护甲、虚空、审判庭每一件 {@code isRepairable} 都是假，都在 {@code essentialcraft5:mru_repairable} 里；宅男盔甲不在里面。</li>
 *   <li>工作台修理配方不匹配：两件都带阶、带阶的放第二格（修理配方只问第一件）都不行；对照两件铁头盔照常能修。</li>
 *   <li>砂轮不合并两件（一样的、不一样的都不出产物）；对照两件铁头盔照常合并。</li>
 *   <li>魔力修复器照样修，修完同调阶、同调度原样留着。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class AttunedArmorRepairTests {

    private static final String EMPTY = "empty";
    private static final String[] FAMILIES = {"mhs", "wind", "void", "inquisitorium"};
    private static final String[] PARTS = {"helmet", "chestplate", "leggings", "boots"};

    private AttunedArmorRepairTests() {
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
        if (item == null || item == Items.AIR) {
            throw new IllegalStateException("没有物品 " + path);
        }
        return item;
    }

    private static ItemStack attuned(Item item, int stage, int points) {
        ItemStack stack = new ItemStack(item);
        Attunement.set(stack, stage, points);
        return stack;
    }

    private static ItemStack damaged(Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(damage);
        return stack;
    }

    private static CraftingContainer grid(ItemStack first, ItemStack second) {
        CraftingContainer grid = new TransientCraftingContainer(new DummyMenu(), 3, 3);
        grid.setItem(0, first.copy());
        grid.setItem(4, second.copy());
        return grid;
    }

    private static ItemStack grindstone(Player player, ItemStack top, ItemStack bottom) {
        GrindstoneMenu menu = new GrindstoneMenu(0, player.getInventory());
        menu.getSlot(0).set(top.copy());
        menu.getSlot(1).set(bottom.copy());
        return menu.getSlot(2).getItem();
    }

    /** 十六件都不可修理、都在 mru_repairable 里、魔力修复器认；工作台修理配方、砂轮都合不了；铁头盔对照照常；宅男盔甲不在标签里 */
    @GameTest(template = EMPTY)
    public static void attunedArmorNotRepairable(GameTestHelper helper) {
        Level level = helper.getLevel();
        Player player = helper.makeMockPlayer();
        for (String family : FAMILIES) {
            for (String part : PARTS) {
                String name = family + "_" + part;
                Item item = item(name);
                ItemStack plain = damaged(item, 10);
                ItemStack staged = attuned(item, 2, 7);
                helper.assertFalse(plain.isRepairable() || staged.isRepairable(), name + " 不应可修理");
                helper.assertTrue(plain.is(ECTags.MRU_REPAIRABLE), name + " 应在 essentialcraft5:mru_repairable 里");
                helper.assertTrue(MagicalRepairerBlockEntity.canRepair(staged) && MagicalRepairerBlockEntity.needsRepair(plain),
                        name + "：魔力修复器应认它、有损耗时要修");

                for (ItemStack[] pair : new ItemStack[][]{{staged, staged}, {plain, staged}, {staged, plain}, {plain, plain}}) {
                    Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid(pair[0], pair[1]), level);
                    helper.assertTrue(recipe.isEmpty(), name + "：工作台里两件不该匹配任何配方（修理配方会新建一件、丢掉同调），实际 "
                            + recipe.map(CraftingRecipe::getId).orElse(null));
                }
                helper.assertTrue(grindstone(player, staged, staged).isEmpty() && grindstone(player, plain, staged).isEmpty(),
                        name + "：砂轮不该合并两件（只留上面那件，下面那件的同调会丢）");
            }
        }

        ItemStack iron = damaged(Items.IRON_HELMET, 50);
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid(iron, iron), level).isPresent(), "对照：两件铁头盔照常能修理");
        helper.assertFalse(grindstone(player, iron, iron).isEmpty(), "对照：砂轮照常合并两件铁头盔");

        ItemStack computer = new ItemStack(item("computer_helmet"));
        helper.assertFalse(computer.is(ECTags.MRU_REPAIRABLE) || MagicalRepairerBlockEntity.canRepair(computer),
                "宅男盔甲不在 mru_repairable 里，魔力修复器照旧不修它");
        helper.succeed();
    }

    /** 魔力修复器照样修：5000 MRU 修 10 点，3 阶审判庭头盔、1 阶魔力防护胸甲、0 阶虚空靴子都修，阶和同调度原样留着 */
    @GameTest(template = EMPTY)
    public static void attunedArmorMagicalRepairerKeepsAttunement(GameTestHelper helper) {
        ItemStack[] stacks = {attuned(item("inquisitorium_helmet"), 3, 11), attuned(item("mhs_chestplate"), 1, 5), new ItemStack(item("void_boots"))};
        int[] stages = {3, 1, 0};
        int[] points = {11, 5, 0};
        MagicalRepairerBlockEntity[] repairers = new MagicalRepairerBlockEntity[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            BlockPos pos = new BlockPos(i * 2, 1, 1);
            helper.setBlock(pos, ECDevices.MAGICAL_REPAIRER.get());
            repairers[i] = (MagicalRepairerBlockEntity) helper.getBlockEntity(pos);
            repairers[i].getMRUStorage().setMRU(5000);
            stacks[i].setDamageValue(100);
            repairers[i].getInventory().setStackInSlot(MagicalRepairerBlockEntity.SLOT_ITEM, stacks[i]);
        }
        helper.startSequence()
                .thenExecuteAfter(25, () -> {
                    for (int i = 0; i < repairers.length; i++) {
                        ItemStack stack = repairers[i].getInventory().getStackInSlot(MagicalRepairerBlockEntity.SLOT_ITEM);
                        String name = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
                        helper.assertTrue(stack.getDamageValue() == 90, name + "：5000 MRU 应正好修 10 点，剩余损耗 " + stack.getDamageValue());
                        helper.assertTrue(Attunement.stage(stack) == stages[i] && Attunement.points(stack) == points[i],
                                name + "：修完同调阶、同调度应原样留着，实际 " + Attunement.stage(stack) + " 阶 " + Attunement.points(stack) + " 点");
                    }
                })
                .thenSucceed();
    }

    /** TransientCraftingContainer 要一个菜单通知格子变化，测试里用个空的 */
    private static final class DummyMenu extends AbstractContainerMenu {
        DummyMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
