package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.blockentity.WeaponMakerBlockEntity;
import com.flameforge.essentialcraft5.gun.GunAssembly;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.menu.WeaponMakerMenu;
import com.flameforge.essentialcraft5.registry.ECWeaponMaker;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 武器工作台模块（ECWeaponMaker，batch5.md §3.2）的 GameTest：
 * <ul>
 *   <li>组装：魔力锭枪托 + 米斯莱握把 + 淡青机栓的手枪，按钮 0 出枪、部件写对、用到的格各少 1、没用到的可选格不动；输出格有东西、部件混了材料、
 *       可选格放了不认识的东西（绕过槽位规则塞进去）都不组装、什么都不扣。</li>
 *   <li>狙击要镜座 + 两格同种狙击镜，加特林要透镜座 + 两格同种透镜（只查组装条件，不依赖枪物品）。</li>
 *   <li>槽位规则：泥土放不进任何格（玩家、菜单槽、漏斗、Shift 点击都一样），灵魂碎片进不了灵魂格，输出格谁都放不进；漏斗只能从输出格往外抽。</li>
 *   <li>Shift 点击按用途分格（审查 MAKER-1）：狙击台的魔力锭先进枪托、不整组塞进镜材格，一组轮流补齐；加特林两颗透镜分进两个透镜格。</li>
 *   <li>按钮校验：别的按钮号、客户端菜单、旁观者、离太远一律不做；组装不发任何破坏 / 放置事件（模拟领地里照常，领地管的是开不开得了界面）。</li>
 *   <li>灵魂格的 MRU 转进新枪（3 000 → 3 000，10 万 → 封顶 5 000）；四台工作台的槽数、只带坐标打开的客户端菜单按方块认枪种；5 个配方与循环顺序。</li>
 * </ul>
 * 枪物品（ECGuns 模块）按注册名取：还没注册时出枪那一半断言跳过，改成断言「不出枪、什么都不扣」（porting_guide §6）。
 * 灵魂格、核心格的标签由 datagen 生成（tools/tags/ecweaponmaker.json），先跑 runData。
 * 玩家用 ProtectionFrameworkTests.realPlayer（真 ServerPlayer，站在 (0, 1, 0)，离工作台 2 格多，菜单的 stillValid 放行）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class WeaponMakerTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "ec5_weapon_maker";
    private static final BlockPos MAKER = new BlockPos(2, 1, 2);

    private static final String MRU_INGOT = "magical_ingot";
    private static final String PALE_INGOT = "pale_ingot";
    private static final String MITHRILINE_INGOT = "mithriline_ingot";

    private WeaponMakerTests() {
    }

    // ------------------------------------------------------------------ 工具

    private static WeaponMakerBlockEntity place(GameTestHelper helper, GunType type) {
        helper.setBlock(MAKER, ECWeaponMaker.maker(type).get().defaultBlockState());
        if (!(helper.getBlockEntity(MAKER) instanceof WeaponMakerBlockEntity be)) {
            throw new GameTestAssertException(type.id() + " 工作台应带 WeaponMakerBlockEntity，实际 " + helper.getBlockEntity(MAKER));
        }
        return be;
    }

    private static Item item(String path) {
        ResourceLocation id = EssentialCraft5.id(path);
        if (!ForgeRegistries.ITEMS.containsKey(id)) {
            throw new GameTestAssertException("物品 " + id + " 应已注册");
        }
        return ForgeRegistries.ITEMS.getValue(id);
    }

    private static ItemStack stack(String path, int count) {
        return new ItemStack(item(path), count);
    }

    private static int first(GunType type, GunType.SlotRole role) {
        return type.layout().indicesOf(role)[0];
    }

    private static void put(WeaponMakerBlockEntity be, int slot, ItemStack stack) {
        be.getInventory().setStackInSlot(slot, stack);
    }

    private static ItemStack cell(WeaponMakerBlockEntity be, int slot) {
        return be.getInventory().getStackInSlot(slot);
    }

    /** 放好核心、灵魂球（1 个）和三个部件（每格 count 个）；透镜、瞄具不放 */
    private static void fillBody(WeaponMakerBlockEntity be, String base, String handle, String device, int count) {
        GunType type = be.gunType();
        put(be, first(type, GunType.SlotRole.CORE), stack("elemental_core", count));
        put(be, first(type, GunType.SlotRole.SOUL), stack("soul_sphere", 1));
        for (int slot : type.layout().indicesOf(GunType.SlotRole.BASE)) {
            put(be, slot, stack(base, count));
        }
        for (int slot : type.layout().indicesOf(GunType.SlotRole.HANDLE)) {
            put(be, slot, stack(handle, count));
        }
        for (int slot : type.layout().indicesOf(GunType.SlotRole.DEVICE)) {
            put(be, slot, stack(device, count));
        }
    }

    private static List<ItemStack> snapshot(WeaponMakerBlockEntity be) {
        List<ItemStack> copy = new ArrayList<>();
        for (int slot = 0; slot < be.getInventory().getSlots(); slot++) {
            copy.add(cell(be, slot).copy());
        }
        return copy;
    }

    private static void assertUnchanged(GameTestHelper helper, WeaponMakerBlockEntity be, List<ItemStack> before, String what) {
        for (int slot = 0; slot < before.size(); slot++) {
            helper.assertTrue(ItemStack.matches(before.get(slot), cell(be, slot)),
                    what + "：第 " + slot + " 格不该变，原来 " + before.get(slot) + "，现在 " + cell(be, slot));
        }
    }

    private static IItemHandler automation(WeaponMakerBlockEntity be) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("工作台应暴露物品栏"));
    }

    /** 照 Forge 的漏斗逐格试着塞；返回塞没塞进去 */
    private static boolean hopperInsert(IItemHandler handler, ItemStack one) {
        ItemStack stack = one.copy();
        for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); slot++) {
            if (handler.insertItem(slot, stack, true).isEmpty()) {
                stack = handler.insertItem(slot, stack, false);
            }
        }
        return stack.isEmpty();
    }

    private static boolean gunRegistered(GunType type) {
        return GunAssembly.gunItem(type) != null;
    }

    private static int mruOf(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandler::getMRU).orElse(-1);
    }

    private static ItemStack soulWithMRU(String path, int mru) {
        ItemStack soul = stack(path, 1);
        soul.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return soul;
    }

    // ------------------------------------------------------------------ 组装

    /** 核心、灵魂球、3 魔力锭枪托、1 米斯莱握把、1 淡青机栓（每格 2 个）→ 出枪，部件写对，用到的格各少 1，透镜 / 瞄具格不动 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void assemblePistol(GameTestHelper helper) {
        WeaponMakerBlockEntity be = place(helper, GunType.PISTOL);
        fillBody(be, MRU_INGOT, MITHRILINE_INGOT, PALE_INGOT, 2);
        GunAssembly.Check check = GunAssembly.check(GunType.PISTOL, be.getInventory());
        helper.assertTrue(check.ok(), "材料齐了应能组装，实际原因 " + check.problems());
        GunParts parts = check.plan().parts();
        helper.assertTrue(parts.equals(new GunParts("mru", "mithriline", "pale", null, null)), "部件应是 枪托 mru / 握把 mithriline / 机栓 pale，实际 " + parts);
        helper.assertTrue(Arrays.equals(check.plan().consumeSlots(), new int[] {1, 2, 5, 6, 7, 8, 9}),
                "只扣核心、灵魂和 5 格部件（不扣空着的透镜 3、瞄具 4），实际 " + Arrays.toString(check.plan().consumeSlots()));

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        WeaponMakerMenu menu = new WeaponMakerMenu(0, player.getInventory(), be);
        if (!gunRegistered(GunType.PISTOL)) {
            List<ItemStack> before = snapshot(be);
            helper.assertFalse(menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "手枪物品还没注册时不出枪");
            assertUnchanged(helper, be, before, "枪没注册");
            helper.succeed();
            return;
        }
        helper.assertTrue(menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "按钮 0 应组装出枪");
        ItemStack gun = cell(be, GunType.OUTPUT_SLOT);
        helper.assertTrue(GunType.of(gun).orElse(null) == GunType.PISTOL, "输出格应是手枪，实际 " + gun);
        helper.assertTrue(GunParts.read(gun).map(parts::equals).orElse(false), "枪的部件应照工作台写，实际 " + GunParts.read(gun));
        helper.assertTrue(GunStats.of(gun).isPresent(), "组装好的枪应能算出属性");
        helper.assertTrue(GunParts.fired(gun) == 0 && GunParts.wear(gun) == 0, "新枪没打过、没磨损");
        for (int slot : new int[] {1, 5, 6, 7, 8, 9}) {
            helper.assertTrue(cell(be, slot).getCount() == 1, "第 " + slot + " 格应只扣 1 个（剩 1），实际 " + cell(be, slot));
        }
        helper.assertTrue(cell(be, 2).isEmpty(), "灵魂球应被用掉");
        helper.assertTrue(cell(be, 3).isEmpty() && cell(be, 4).isEmpty(), "透镜 / 瞄具格空着，不该冒出东西");
        helper.assertFalse(menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "输出格有枪、灵魂格空了，不该再组装");
        helper.succeed();
    }

    /** 枪托 魔力 / 淡青 / 魔力 → 不组装、什么都不扣（原作只要一格认得就算） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void assembleRejectsMixedPart(GameTestHelper helper) {
        WeaponMakerBlockEntity be = place(helper, GunType.PISTOL);
        fillBody(be, MRU_INGOT, MRU_INGOT, MRU_INGOT, 1);
        int[] base = GunType.PISTOL.layout().indicesOf(GunType.SlotRole.BASE);
        put(be, base[1], stack(PALE_INGOT, 1));
        GunAssembly.Check check = GunAssembly.check(GunType.PISTOL, be.getInventory());
        helper.assertTrue(!check.ok() && check.problems().contains(GunAssembly.Problem.BASE), "枪托混了材料应报 BASE，实际 " + check.problems());
        helper.assertTrue(GunAssembly.assemble(GunType.PISTOL, be.getInventory()).isEmpty(), "不该组装");

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        WeaponMakerMenu menu = new WeaponMakerMenu(0, player.getInventory(), be);
        List<ItemStack> before = snapshot(be);
        helper.assertFalse(menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "枪托混了材料，按钮不该生效");
        assertUnchanged(helper, be, before, "混材料");

        // 缺一格也不行
        put(be, base[1], ItemStack.EMPTY);
        helper.assertTrue(GunAssembly.check(GunType.PISTOL, be.getInventory()).problems().contains(GunAssembly.Problem.BASE), "枪托缺一格应报 BASE");
        helper.succeed();
    }

    /** 可选格：放了不认识的东西（绕过槽位规则）不组装、什么都不扣；放合法透镜和瞄具时一起用掉、写进枪 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void optionalSlots(GameTestHelper helper) {
        WeaponMakerBlockEntity be = place(helper, GunType.PISTOL);
        fillBody(be, MRU_INGOT, MRU_INGOT, MRU_INGOT, 2);
        int lens = first(GunType.PISTOL, GunType.SlotRole.LENS_OPT);
        int scope = first(GunType.PISTOL, GunType.SlotRole.SCOPE_OPT);
        helper.assertTrue(lens == 3 && scope == 4, "手枪的透镜 / 瞄具格应是 3、4 号（照原作）");

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        WeaponMakerMenu menu = new WeaponMakerMenu(0, player.getInventory(), be);

        put(be, lens, new ItemStack(Items.DIRT));
        helper.assertTrue(GunAssembly.check(GunType.PISTOL, be.getInventory()).problems().contains(GunAssembly.Problem.LENS),
                "透镜格里有泥土应报 LENS");
        List<ItemStack> before = snapshot(be);
        helper.assertFalse(menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "透镜格里有泥土不该组装");
        assertUnchanged(helper, be, before, "透镜格有泥土");

        put(be, lens, ItemStack.EMPTY);
        put(be, scope, new ItemStack(Items.DIRT));
        helper.assertTrue(GunAssembly.check(GunType.PISTOL, be.getInventory()).problems().contains(GunAssembly.Problem.SCOPE),
                "瞄具格里有泥土应报 SCOPE");
        before = snapshot(be);
        helper.assertFalse(menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "瞄具格里有泥土不该组装");
        assertUnchanged(helper, be, before, "瞄具格有泥土");

        put(be, lens, stack("chaos_matrix_projection", 2));
        put(be, scope, stack("magic_purified_glass_alloy", 2));
        GunAssembly.Check check = GunAssembly.check(GunType.PISTOL, be.getInventory());
        helper.assertTrue(check.ok(), "合法的透镜和瞄具应能组装，实际 " + check.problems());
        helper.assertTrue("chaos".equals(check.plan().parts().lens()) && "mru".equals(check.plan().parts().scope()),
                "部件应带 chaos 透镜、mru 瞄具，实际 " + check.plan().parts());
        helper.assertTrue(Arrays.equals(check.plan().consumeSlots(), new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9}), "用到的透镜、瞄具也要扣");
        if (gunRegistered(GunType.PISTOL)) {
            helper.assertTrue(menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "应组装出带透镜和瞄具的枪");
            helper.assertTrue(cell(be, lens).getCount() == 1 && cell(be, scope).getCount() == 1, "透镜、瞄具各扣 1 个");
            GunParts parts = GunParts.read(cell(be, GunType.OUTPUT_SLOT)).orElse(null);
            helper.assertTrue(parts != null && "chaos".equals(parts.lens()) && "mru".equals(parts.scope()), "枪应带 chaos 透镜、mru 瞄具，实际 " + parts);
        }
        helper.succeed();
    }

    /** 输出格有东西：不组装、什么都不扣 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void outputOccupied(GameTestHelper helper) {
        WeaponMakerBlockEntity be = place(helper, GunType.PISTOL);
        fillBody(be, MRU_INGOT, MRU_INGOT, MRU_INGOT, 1);
        put(be, GunType.OUTPUT_SLOT, new ItemStack(Items.DIRT));
        helper.assertTrue(GunAssembly.check(GunType.PISTOL, be.getInventory()).problems().contains(GunAssembly.Problem.OUTPUT_OCCUPIED),
                "输出格有东西应报 OUTPUT_OCCUPIED");
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        WeaponMakerMenu menu = new WeaponMakerMenu(0, player.getInventory(), be);
        List<ItemStack> before = snapshot(be);
        helper.assertFalse(menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "输出格有东西不该组装");
        assertUnchanged(helper, be, before, "输出格占着");
        helper.succeed();
    }

    /** 狙击：镜座 + 两格同种狙击镜缺一不可，镜座和两格镜材各报各的；只查组装条件，不依赖枪物品 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sniperNeedsScope(GameTestHelper helper) {
        GunType type = GunType.SNIPER;
        WeaponMakerBlockEntity be = place(helper, type);
        fillBody(be, MRU_INGOT, MRU_INGOT, MRU_INGOT, 1);
        int housing = first(type, GunType.SlotRole.SCOPE_HOUSING);
        int[] scopes = type.layout().indicesOf(GunType.SlotRole.SCOPE);
        Set<GunAssembly.Problem> empty = GunAssembly.check(type, be.getInventory()).problems();
        helper.assertTrue(empty.contains(GunAssembly.Problem.SCOPE_SET) && empty.contains(GunAssembly.Problem.SCOPE_HOUSING),
                "什么都没放应同时报 SCOPE_SET 和 SCOPE_HOUSING，实际 " + empty);

        put(be, scopes[0], stack(MRU_INGOT, 1));
        put(be, scopes[1], stack(MRU_INGOT, 1));
        Set<GunAssembly.Problem> noHousing = GunAssembly.check(type, be.getInventory()).problems();
        helper.assertTrue(noHousing.contains(GunAssembly.Problem.SCOPE_HOUSING) && !noHousing.contains(GunAssembly.Problem.SCOPE_SET),
                "两格镜材放对、没有镜座只应报 SCOPE_HOUSING，实际 " + noHousing);

        put(be, housing, stack("magic_purified_glass_alloy", 1));
        put(be, scopes[1], stack(PALE_INGOT, 1));
        Set<GunAssembly.Problem> mixed = GunAssembly.check(type, be.getInventory()).problems();
        helper.assertTrue(mixed.contains(GunAssembly.Problem.SCOPE_SET) && !mixed.contains(GunAssembly.Problem.SCOPE_HOUSING),
                "镜座放对、两格镜材不一样只应报 SCOPE_SET，实际 " + mixed);

        put(be, scopes[1], stack(MRU_INGOT, 1));
        GunAssembly.Check check = GunAssembly.check(type, be.getInventory());
        helper.assertTrue(check.ok() && "mru".equals(check.plan().parts().scope()), "镜座 + 两格魔力锭应组装出 mru 狙击镜，实际 " + check);
        int[] consume = check.plan().consumeSlots();
        helper.assertTrue(Arrays.stream(consume).anyMatch(s -> s == housing) && Arrays.stream(consume).anyMatch(s -> s == scopes[0])
                && Arrays.stream(consume).anyMatch(s -> s == scopes[1]), "镜座和两格镜材都要扣");
        helper.assertTrue(consume.length == type.layout().size() - 2, "狙击不放透镜时，除输出格和透镜格外每格都扣，实际 " + Arrays.toString(consume));
        helper.succeed();
    }

    /** 加特林：透镜座（日光玻璃）+ 两格同种透镜缺一不可，透镜座和两格投影各报各的 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void gatlingNeedsLens(GameTestHelper helper) {
        GunType type = GunType.GATLING;
        WeaponMakerBlockEntity be = place(helper, type);
        fillBody(be, MRU_INGOT, MRU_INGOT, MRU_INGOT, 1);
        int housing = first(type, GunType.SlotRole.LENS_HOUSING);
        int[] lenses = type.layout().indicesOf(GunType.SlotRole.LENS);
        Set<GunAssembly.Problem> empty = GunAssembly.check(type, be.getInventory()).problems();
        helper.assertTrue(empty.contains(GunAssembly.Problem.LENS_SET) && empty.contains(GunAssembly.Problem.LENS_HOUSING),
                "什么都没放应同时报 LENS_SET 和 LENS_HOUSING，实际 " + empty);

        put(be, housing, stack("sun_imbued_glass", 1));
        put(be, lenses[0], stack("chaos_matrix_projection", 1));
        helper.assertTrue(GunAssembly.check(type, be.getInventory()).problems().contains(GunAssembly.Problem.LENS_SET), "只有一格透镜应报 LENS_SET");

        // 透镜座放对了、两格投影不一样：只报两格投影，不再连带说透镜座
        put(be, lenses[1], stack("shade_matrix_projection", 1));
        Set<GunAssembly.Problem> mixed = GunAssembly.check(type, be.getInventory()).problems();
        helper.assertTrue(mixed.contains(GunAssembly.Problem.LENS_SET) && !mixed.contains(GunAssembly.Problem.LENS_HOUSING),
                "透镜座放对、两格投影不一样只应报 LENS_SET，实际 " + mixed);

        put(be, lenses[1], stack("chaos_matrix_projection", 1));
        GunAssembly.Check check = GunAssembly.check(type, be.getInventory());
        helper.assertTrue(check.ok() && "chaos".equals(check.plan().parts().lens()) && check.plan().parts().scope() == null,
                "透镜座 + 两格混沌投影应组装出 chaos 透镜，实际 " + check);
        helper.assertTrue(check.plan().consumeSlots().length == type.layout().size() - 1, "加特林每格都用到（除输出格）");

        put(be, housing, stack("magic_purified_glass_alloy", 1));
        Set<GunAssembly.Problem> wrongHousing = GunAssembly.check(type, be.getInventory()).problems();
        helper.assertTrue(wrongHousing.contains(GunAssembly.Problem.LENS_HOUSING) && !wrongHousing.contains(GunAssembly.Problem.LENS_SET),
                "透镜座不是日光玻璃（绕过槽位规则塞进去）、两格投影相同只应报 LENS_HOUSING，实际 " + wrongHousing);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 槽位规则

    /** 泥土放不进任何格（槽位规则、菜单槽、漏斗、Shift 点击）；灵魂碎片进不了灵魂格；输出格谁都放不进；漏斗只能从输出格抽 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void junkRejected(GameTestHelper helper) {
        GunType type = GunType.PISTOL;
        WeaponMakerBlockEntity be = place(helper, type);
        ItemStack dirt = new ItemStack(Items.DIRT);
        for (int slot = 0; slot < type.layout().size(); slot++) {
            helper.assertFalse(be.isItemValid(slot, dirt), "泥土不该能放进第 " + slot + " 格");
        }
        helper.assertFalse(hopperInsert(automation(be), dirt), "漏斗不该能塞进泥土");

        int core = first(type, GunType.SlotRole.CORE);
        int soul = first(type, GunType.SlotRole.SOUL);
        int base = first(type, GunType.SlotRole.BASE);
        helper.assertFalse(be.isItemValid(core, stack(MRU_INGOT, 1)), "核心格不收魔力锭");
        helper.assertTrue(be.isItemValid(core, stack("elemental_core", 1)), "核心格收元素核心");
        helper.assertFalse(be.isItemValid(soul, stack("soul_shard", 1)), "灵魂格不收灵魂碎片（原作 meta ≥ 1）");
        for (String path : new String[] {"soul_sphere", "dark_soul_matter", "red_soul_matter", "matter_of_eternity"}) {
            helper.assertTrue(be.isItemValid(soul, stack(path, 1)), "灵魂格应收 " + path);
        }
        helper.assertTrue(be.isItemValid(base, stack(MRU_INGOT, 1)), "枪托格收魔力锭");
        helper.assertFalse(be.isItemValid(GunType.OUTPUT_SLOT, stack(MRU_INGOT, 1)), "输出格谁都放不进");

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        WeaponMakerMenu menu = new WeaponMakerMenu(0, player.getInventory(), be);
        helper.assertFalse(menu.getSlot(base).mayPlace(dirt), "菜单的枪托格不该收泥土");
        helper.assertFalse(menu.getSlot(GunType.OUTPUT_SLOT).mayPlace(stack(MRU_INGOT, 1)), "菜单的输出格只能拿");

        // Shift 点击：泥土留在背包；5 个魔力锭按用途各放 1 个（枪托 3 格、机栓、握把，MAKER-1）
        int machineSlots = type.layout().size();
        player.getInventory().setItem(0, dirt.copy());
        helper.assertTrue(menu.quickMoveStack(player, machineSlots + 27).isEmpty(), "Shift 点泥土应什么都不动");
        helper.assertTrue(player.getInventory().getItem(0).is(Items.DIRT), "泥土应还在快捷栏");
        player.getInventory().setItem(1, stack(MRU_INGOT, 5));
        menu.quickMoveStack(player, machineSlots + 28);
        for (GunType.SlotRole role : new GunType.SlotRole[] {GunType.SlotRole.BASE, GunType.SlotRole.DEVICE, GunType.SlotRole.HANDLE}) {
            for (int slot : type.layout().indicesOf(role)) {
                helper.assertTrue(cell(be, slot).getCount() == 1, "Shift 点 5 个魔力锭应在每个" + role + "格各放 1 个，第 " + slot + " 格实际 " + cell(be, slot));
            }
        }
        helper.assertTrue(player.getInventory().getItem(1).isEmpty(), "5 个魔力锭应全部放进去");
        for (int slot = 0; slot < machineSlots; slot++) {
            helper.assertFalse(cell(be, slot).is(Items.DIRT), "第 " + slot + " 格不该有泥土");
        }

        IItemHandler auto = automation(be);
        helper.assertTrue(auto.extractItem(base, 1, false).isEmpty(), "漏斗不该能从枪托格抽东西");
        helper.assertTrue(auto.insertItem(core, stack("elemental_core", 1), false).isEmpty(), "漏斗照原作能往核心格塞核心");
        put(be, GunType.OUTPUT_SLOT, new ItemStack(Items.STICK));
        helper.assertTrue(auto.extractItem(GunType.OUTPUT_SLOT, 1, false).is(Items.STICK), "漏斗能从输出格抽");
        helper.succeed();
    }

    /**
     * Shift 点击按用途分格（审查 MAKER-1）：狙击台上魔力锭既能当枪身又能当狙击镜材。3 个先进枪托格（镜材、机栓、握把格不动）；
     * 再点一组 64 个：空着的机栓、握把、镜材格各放 1 个，剩下的轮流补，9 格数量齐平（差不超过 1），不会整组塞进一格；
     * 然后核心、灵魂球、玻璃合金各 Shift 一次就能组装。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void quickMoveByRole(GameTestHelper helper) {
        GunType type = GunType.SNIPER;
        WeaponMakerBlockEntity be = place(helper, type);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        WeaponMakerMenu menu = new WeaponMakerMenu(0, player.getInventory(), be);
        int hotbar = type.layout().size() + 27;   // 菜单里快捷栏第 0 格
        GunType.SlotRole[] bodyRoles = {GunType.SlotRole.BASE, GunType.SlotRole.DEVICE, GunType.SlotRole.HANDLE, GunType.SlotRole.SCOPE};

        player.getInventory().setItem(0, stack(MRU_INGOT, 3));
        menu.quickMoveStack(player, hotbar);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "3 个魔力锭应全部放进去");
        for (GunType.SlotRole role : bodyRoles) {
            for (int slot : type.layout().indicesOf(role)) {
                ItemStack in = cell(be, slot);
                if (role == GunType.SlotRole.BASE) {
                    helper.assertTrue(in.is(item(MRU_INGOT)) && in.getCount() == 1, "3 个魔力锭应先进三个枪托格各 1 个，第 " + slot + " 格实际 " + in);
                } else {
                    helper.assertTrue(in.isEmpty(), "枪托格还没放满时" + role + "格（第 " + slot + " 格）不该有东西，实际 " + in);
                }
            }
        }

        player.getInventory().setItem(0, stack(MRU_INGOT, 64));
        menu.quickMoveStack(player, hotbar);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "一组魔力锭应全部放进去，剩 " + player.getInventory().getItem(0));
        int total = 0;
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (GunType.SlotRole role : bodyRoles) {
            for (int slot : type.layout().indicesOf(role)) {
                ItemStack in = cell(be, slot);
                helper.assertTrue(in.is(item(MRU_INGOT)), role + "格（第 " + slot + " 格）应有魔力锭，实际 " + in);
                total += in.getCount();
                min = Math.min(min, in.getCount());
                max = Math.max(max, in.getCount());
            }
        }
        helper.assertTrue(total == 67, "9 格合计应是 67 个，实际 " + total);
        helper.assertTrue(max - min <= 1, "剩下的应轮流补齐（各格差不超过 1），实际最少 " + min + "、最多 " + max);
        int base = first(type, GunType.SlotRole.BASE);
        int scope = first(type, GunType.SlotRole.SCOPE);
        helper.assertTrue(cell(be, base).getCount() >= cell(be, scope).getCount(),
                "补不齐的零头应先给枪托：枪托 " + cell(be, base) + "，镜材 " + cell(be, scope));
        helper.assertTrue(cell(be, first(type, GunType.SlotRole.SCOPE_HOUSING)).isEmpty(), "镜座格不收魔力锭");

        for (String path : new String[] {"elemental_core", "soul_sphere", "magic_purified_glass_alloy"}) {
            player.getInventory().setItem(0, stack(path, 1));
            menu.quickMoveStack(player, hotbar);
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), path + " 应被 Shift 进工作台");
        }
        helper.assertTrue(cell(be, first(type, GunType.SlotRole.SCOPE_HOUSING)).is(item("magic_purified_glass_alloy")), "玻璃合金应进镜座格");
        GunAssembly.Check check = GunAssembly.check(type, be.getInventory());
        helper.assertTrue(check.ok(), "Shift 放好的狙击台应能组装，实际原因 " + check.problems());
        helper.assertTrue(cell(be, GunType.OUTPUT_SLOT).isEmpty(), "输出格不收任何东西");
        helper.succeed();
    }

    /** Shift 点击（MAKER-1）：加特林的两颗透镜（不能叠）各 Shift 一次，分进两个透镜格；透镜座格不收透镜，日光玻璃进透镜座 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void quickMoveGatlingLenses(GameTestHelper helper) {
        GunType type = GunType.GATLING;
        WeaponMakerBlockEntity be = place(helper, type);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        WeaponMakerMenu menu = new WeaponMakerMenu(0, player.getInventory(), be);
        int hotbar = type.layout().size() + 27;
        player.getInventory().setItem(0, stack("chaos_matrix_projection", 1));
        player.getInventory().setItem(1, stack("chaos_matrix_projection", 1));
        menu.quickMoveStack(player, hotbar);
        menu.quickMoveStack(player, hotbar + 1);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty() && player.getInventory().getItem(1).isEmpty(), "两颗透镜都应放进去");
        for (int slot : type.layout().indicesOf(GunType.SlotRole.LENS)) {
            helper.assertTrue(cell(be, slot).is(item("chaos_matrix_projection")) && cell(be, slot).getCount() == 1,
                    "两颗透镜应各占一个透镜格，第 " + slot + " 格实际 " + cell(be, slot));
        }
        int housing = first(type, GunType.SlotRole.LENS_HOUSING);
        helper.assertTrue(cell(be, housing).isEmpty(), "透镜座格不收透镜");
        player.getInventory().setItem(2, stack("sun_imbued_glass", 1));
        menu.quickMoveStack(player, hotbar + 2);
        helper.assertTrue(cell(be, housing).is(item("sun_imbued_glass")), "日光玻璃应进透镜座格，实际 " + cell(be, housing));
        helper.succeed();
    }

    // ------------------------------------------------------------------ 按钮

    /** 别的按钮号、客户端菜单、旁观者、离太远都不做、什么都不扣；组装不发破坏 / 放置事件 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void badButtonId(GameTestHelper helper) {
        WeaponMakerBlockEntity be = place(helper, GunType.PISTOL);
        fillBody(be, MRU_INGOT, MRU_INGOT, MRU_INGOT, 1);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        WeaponMakerMenu menu = new WeaponMakerMenu(0, player.getInventory(), be);
        List<ItemStack> before = snapshot(be);
        for (int id : new int[] {-1, 1, 5, 127}) {
            helper.assertFalse(menu.clickMenuButton(player, id), "按钮号 " + id + " 不该生效");
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        WeaponMakerMenu.writeOpenData(buf, helper.absolutePos(MAKER), GunType.PISTOL);
        WeaponMakerMenu clientMenu = new WeaponMakerMenu(1, player.getInventory(), buf);
        helper.assertFalse(clientMenu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "没有方块实体的（客户端）菜单什么都不做");

        player.setGameMode(GameType.SPECTATOR);
        helper.assertFalse(menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "旁观者不能组装");
        player.setGameMode(GameType.SURVIVAL);

        Vec3 far = helper.absoluteVec(new Vec3(2.5D, 1D, 30D));
        player.moveTo(far.x, far.y, far.z, 0F, 0F);
        helper.assertFalse(menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "离工作台太远不能组装");
        assertUnchanged(helper, be, before, "按钮被拒");

        Vec3 near = helper.absoluteVec(new Vec3(0.5D, 1D, 0.5D));
        player.moveTo(near.x, near.y, near.z, 0F, 0F);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(MAKER)) {
            boolean made = menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE);
            helper.assertTrue(made == gunRegistered(GunType.PISTOL), "站回来、枪已注册时应能组装（工作台不碰世界，领地里也照常）");
            helper.assertTrue(claims.breaks(MAKER) == 0 && claims.places(MAKER) == 0, "组装不该发破坏 / 放置事件");
        }
        helper.succeed();
    }

    /** 灵魂格的 MRU 转进新枪：3 000 → 3 000；永恒物质 10 万 → 封顶 5 000 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void soulMruTransfer(GameTestHelper helper) {
        GunType type = GunType.PISTOL;
        WeaponMakerBlockEntity be = place(helper, type);
        fillBody(be, MRU_INGOT, MRU_INGOT, MRU_INGOT, 1);
        int soul = first(type, GunType.SlotRole.SOUL);
        put(be, soul, soulWithMRU("soul_sphere", 3000));
        helper.assertTrue(GunAssembly.soulMRU(cell(be, soul)) == 3000, "灵魂球应读出 3000 MRU");
        if (!gunRegistered(type)) {
            helper.succeed();
            return;
        }
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        WeaponMakerMenu menu = new WeaponMakerMenu(0, player.getInventory(), be);
        helper.assertTrue(menu.clickMenuButton(player, WeaponMakerMenu.BUTTON_ASSEMBLE), "应组装");
        helper.assertTrue(mruOf(cell(be, GunType.OUTPUT_SLOT)) == 3000, "新枪应带着灵魂球的 3000 MRU，实际 " + mruOf(cell(be, GunType.OUTPUT_SLOT)));

        put(be, GunType.OUTPUT_SLOT, ItemStack.EMPTY);
        fillBody(be, MRU_INGOT, MRU_INGOT, MRU_INGOT, 1);
        put(be, soul, soulWithMRU("matter_of_eternity", 100_000));
        Optional<GunAssembly.Result> result = GunAssembly.assemble(type, be.getInventory());
        helper.assertTrue(result.isPresent() && mruOf(result.get().gun()) == GunAssembly.MAX_SOUL_TRANSFER,
                "永恒物质 10 万 MRU 转进新枪应封顶 5000，实际 " + result.map(r -> mruOf(r.gun())).orElse(-1));
        helper.succeed();
    }

    // ------------------------------------------------------------------ 注册与配方

    /** 四台工作台的槽数照布局；菜单槽数 = 布局 + 背包 36；只带坐标打开的客户端菜单按那一格的方块认枪种 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void layoutsAndClientMenu(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        int[] sizes = {10, 13, 14, 19};
        for (GunType type : GunType.values()) {
            WeaponMakerBlockEntity be = place(helper, type);
            helper.assertTrue(be.gunType() == type, "方块实体应认出 " + type);
            helper.assertTrue(be.getInventory().getSlots() == sizes[type.ordinal()], type + " 的槽数应是 " + sizes[type.ordinal()]);
            WeaponMakerMenu menu = new WeaponMakerMenu(0, player.getInventory(), be);
            helper.assertTrue(menu.slots.size() == sizes[type.ordinal()] + 36, type + " 菜单槽数应是布局 + 36");

            FriendlyByteBuf posOnly = new FriendlyByteBuf(Unpooled.buffer());
            posOnly.writeBlockPos(helper.absolutePos(MAKER));
            WeaponMakerMenu clientMenu = new WeaponMakerMenu(1, player.getInventory(), posOnly);
            helper.assertTrue(clientMenu.gunType() == type && clientMenu.slots.size() == menu.slots.size(),
                    "只带坐标的客户端菜单应按方块认出 " + type + "，实际 " + clientMenu.gunType());

            // 展示厅用的一整套材料应能组装（02_gui 截图才有预览）
            be.fillShowroomDemo();
            GunAssembly.Check check = GunAssembly.check(type, be.getInventory());
            helper.assertTrue(check.ok(), type + " 的展示厅材料应能组装，实际原因 " + check.problems());
            helper.assertTrue(GunAssembly.soulMRU(be.getInventory().getStackInSlot(first(type, GunType.SlotRole.SOUL))) == 2500,
                    "展示厅的灵魂球应带 2500 MRU");
            be.clearContent();   // 下一轮换方块时别掉一地
        }
        helper.succeed();
    }

    /** 5 个配方：手枪工作台 SIS/SCS/SSS；单独合成 手枪 → 步枪 → 狙击 → 加特林 → 手枪 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void recipesCycle(GameTestHelper helper) {
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        Optional<? extends Recipe<?>> bench = recipes.byKey(EssentialCraft5.id("weaponmaker/pistol_weapon_maker"));
        helper.assertTrue(bench.isPresent(), "手枪工作台配方应已加载");
        helper.assertTrue(bench.get().getResultItem(helper.getLevel().registryAccess()).is(ECWeaponMaker.PISTOL_WEAPON_MAKER.get().asItem()),
                "手枪工作台配方应出手枪工作台");
        GunType[] order = {GunType.PISTOL, GunType.RIFLE, GunType.SNIPER, GunType.GATLING};
        for (int i = 0; i < order.length; i++) {
            GunType from = order[i];
            GunType to = order[(i + 1) % order.length];
            String name = "weaponmaker/" + to.id() + "_weapon_maker_from_" + from.id();
            Optional<? extends Recipe<?>> recipe = recipes.byKey(EssentialCraft5.id(name));
            helper.assertTrue(recipe.isPresent(), "配方 " + name + " 应已加载");
            helper.assertTrue(recipe.get().getResultItem(helper.getLevel().registryAccess()).is(ECWeaponMaker.maker(to).get().asItem()),
                    name + " 应出 " + to.id() + " 工作台");
            helper.assertTrue(recipe.get().getIngredients().size() == 1
                            && recipe.get().getIngredients().get(0).test(new ItemStack(ECWeaponMaker.maker(from).get()))
                            && !recipe.get().getIngredients().get(0).test(new ItemStack(ECWeaponMaker.maker(to).get())),
                    name + " 应只用一台 " + from.id() + " 工作台");
        }
        helper.succeed();
    }
}
