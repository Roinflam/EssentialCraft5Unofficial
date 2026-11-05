package com.flameforge.essentialcraft5.gun;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.registry.ECWeaponMaker;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * 武器工作台的组装规则（第五批 ECWeaponMaker，计划 batch5.md §3.2），两端共用：服务端的菜单按钮、客户端界面的预览和「完成」按钮都用这一份。
 * 槽位布局来自 {@link GunType#layout()}，材料认不认来自 {@link GunMaterials}（只认<b>已开放</b>的 = 材料物品注册了）。
 *
 * <h2>组装条件（{@link #check}）</h2>
 * <ul>
 *   <li>0 号输出格是空的；核心格是 {@code #essentialcraft5:cores/magic}；灵魂格是 {@code #essentialcraft5:gun_souls}。</li>
 *   <li>枪托（B）、握把（H）、机栓（M）：<b>这个部件的每一格都放着同一种已开放的枪身材料</b>。
 *       原作（{@code TileWeaponMaker.getBase / getHandle / getDevice} L62~255）只要有一格认得就算，别的格放不认识的东西会被跳过，组装时照样吃掉。</li>
 *   <li>可选的透镜 / 瞄具格（手枪、步枪、狙击的透镜，手枪、步枪的瞄具）：要么空着，要么放合法的；放了不合法的东西不组装（原作照样组装、把它吃掉）。</li>
 *   <li>狙击：镜座是魔力纯化玻璃合金，两格狙击镜（S）同一种已开放的狙击镜材料；加特林：透镜座是日光玻璃，两格透镜同一种矩阵投影。
 *       镜座 / 透镜座和两格镜材 / 透镜各报各的原因（{@link Problem#SCOPE_HOUSING} / {@link Problem#LENS_HOUSING}）。</li>
 * </ul>
 * 满足了就得到 {@link Plan}：部件（写进枪的 {@link GunParts}）和<b>实际用到的格</b>（组装时每格扣 1）。
 * 原作 {@code makeWeapon}（L408~417）把 1~18 号格各扣 1，没用上的可选格、不认识的东西也一起吃掉。
 *
 * <h2>出枪（{@link #assemble}）</h2>
 * 枪物品按 {@link GunType#itemId()} 从注册表取（不 import 枪物品的类）；没注册时不出枪。新枪写部件（不写属性，属性由 {@link GunStats} 现算），
 * <b>灵魂格物品里存的 MRU 转进新枪</b>，最多 {@link #MAX_SOUL_TRANSFER}（枪本身只装 5000；原作连 MRU 一起吃掉，永恒物质能存 100 万）。
 * 这里只算结果、不改物品栏；扣料、放枪在方块实体里（{@code WeaponMakerBlockEntity.assemble}）。
 */
public final class GunAssembly {

    /** 灵魂格的 MRU 最多转进新枪多少（= 枪的容量，MRUStaffItem.MAX_MRU） */
    public static final int MAX_SOUL_TRANSFER = 5000;

    /** 不能组装的原因（界面按这个顺序列出来） */
    public enum Problem {
        /** 输出格里还有东西 */
        OUTPUT_OCCUPIED("output"),
        /** 核心格空着或放的不是核心 */
        CORE("core"),
        /** 灵魂格空着或放的不是灵魂格物品 */
        SOUL("soul"),
        /** 枪托没放满或材料不一样 */
        BASE("base"),
        /** 握把没放满或材料不一样 */
        HANDLE("handle"),
        /** 机栓没放满或材料不一样 */
        DEVICE("device"),
        /** 可选透镜格里的东西不是透镜 */
        LENS("lens"),
        /** 加特林：透镜座不是日光玻璃 */
        LENS_HOUSING("lens_housing"),
        /** 加特林：两格透镜缺了 / 不一样（和透镜座分开报，玩家才知道是哪一处不对） */
        LENS_SET("lens_set"),
        /** 可选瞄具格里的东西不是瞄具 */
        SCOPE("scope"),
        /** 狙击：镜座不是玻璃合金 */
        SCOPE_HOUSING("scope_housing"),
        /** 狙击：两格狙击镜缺了 / 不一样 */
        SCOPE_SET("scope_set"),
        /** 这种枪的物品没注册（只在 {@link #problems} 里出现） */
        NO_GUN_ITEM("no_gun_item");

        private final String key;

        Problem(String key) {
            this.key = key;
        }

        /** 界面文字键 {@code gui.essentialcraft5.weapon_maker.problem.<key>} */
        public String translationKey() {
            return "gui.essentialcraft5.weapon_maker.problem." + key;
        }
    }

    /** 组装方案：部件、要扣的格（升序，每格扣 1）、灵魂格的下标 */
    public record Plan(GunType type, GunParts parts, int[] consumeSlots, int soulSlot) {
    }

    /** 检查结果：{@code plan} 为 null 时 {@code problems} 至少有一条 */
    public record Check(@Nullable Plan plan, Set<Problem> problems) {
        public boolean ok() {
            return plan != null;
        }
    }

    /** 组装结果：新枪（已写部件、已转 MRU）和要扣的格 */
    public record Result(ItemStack gun, int[] consumeSlots) {
    }

    private GunAssembly() {
    }

    // ------------------------------------------------------------------
    // 槽位规则
    // ------------------------------------------------------------------

    /**
     * 这种工作台的第 slot 格能不能放这个物品（玩家、漏斗、菜单共用；方块实体的 {@code slotRule} 委托到这里）。
     * 输出格谁都放不进；越界的格子不收；各格只收自己那一类已开放的材料。只比物品，不比 NBT、数量。
     */
    public static boolean accepts(GunType type, int slot, ItemStack stack) {
        GunType.SlotRole role = type.layout().role(slot);
        if (role == null || stack.isEmpty()) {
            return false;
        }
        return switch (role) {
            case OUTPUT -> false;
            case CORE -> stack.is(ECWeaponMaker.MAGIC_CORES);
            case SOUL -> stack.is(ECWeaponMaker.GUN_SOULS);
            case LENS_OPT, LENS -> GunMaterials.lensByItem(stack).isPresent();
            case LENS_HOUSING -> GunMaterials.isGatlingLensHousing(stack);
            case SCOPE_OPT, SCOPE -> GunMaterials.scopeByItem(type, stack).isPresent();
            case SCOPE_HOUSING -> GunMaterials.isSniperScopeHousing(stack);
            case BASE, DEVICE, HANDLE -> GunMaterials.bodyByItem(stack).isPresent();
        };
    }

    // ------------------------------------------------------------------
    // 检查与组装
    // ------------------------------------------------------------------

    /** 按类注释的条件检查物品栏（下标按 {@link GunType#layout()}）；不看枪物品注册了没有 */
    public static Check check(GunType type, IItemHandler inv) {
        GunType.Layout layout = type.layout();
        if (inv.getSlots() < layout.size()) {
            // 物品栏和布局对不上（不该发生）：当成什么都没放
            return new Check(null, Collections.unmodifiableSet(EnumSet.of(Problem.CORE, Problem.SOUL)));
        }
        EnumSet<Problem> problems = EnumSet.noneOf(Problem.class);
        Set<Integer> consume = new TreeSet<>();

        if (!inv.getStackInSlot(GunType.OUTPUT_SLOT).isEmpty()) {
            problems.add(Problem.OUTPUT_OCCUPIED);
        }
        int core = first(layout, GunType.SlotRole.CORE);
        if (core < 0 || !inv.getStackInSlot(core).is(ECWeaponMaker.MAGIC_CORES)) {
            problems.add(Problem.CORE);
        } else {
            consume.add(core);
        }
        int soul = first(layout, GunType.SlotRole.SOUL);
        if (soul < 0 || !inv.getStackInSlot(soul).is(ECWeaponMaker.GUN_SOULS)) {
            problems.add(Problem.SOUL);
        } else {
            consume.add(soul);
        }

        String base = uniform(inv, layout.indicesOf(GunType.SlotRole.BASE), stack -> GunMaterials.bodyByItem(stack).map(GunMaterials.Body::id));
        String handle = uniform(inv, layout.indicesOf(GunType.SlotRole.HANDLE), stack -> GunMaterials.bodyByItem(stack).map(GunMaterials.Body::id));
        String device = uniform(inv, layout.indicesOf(GunType.SlotRole.DEVICE), stack -> GunMaterials.bodyByItem(stack).map(GunMaterials.Body::id));
        partSlots(base, layout, GunType.SlotRole.BASE, Problem.BASE, consume, problems);
        partSlots(handle, layout, GunType.SlotRole.HANDLE, Problem.HANDLE, consume, problems);
        partSlots(device, layout, GunType.SlotRole.DEVICE, Problem.DEVICE, consume, problems);

        // 透镜：可选的一格（手枪 / 步枪 / 狙击），或加特林的透镜座 + 两格透镜
        String lens = null;
        for (int slot : layout.indicesOf(GunType.SlotRole.LENS_OPT)) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Optional<GunMaterials.Lens> found = GunMaterials.lensByItem(stack);
            if (found.isPresent()) {
                lens = found.get().id();
                consume.add(slot);
            } else {
                problems.add(Problem.LENS);
            }
        }
        if (layout.has(GunType.SlotRole.LENS_HOUSING) || layout.has(GunType.SlotRole.LENS)) {
            int[] housing = layout.indicesOf(GunType.SlotRole.LENS_HOUSING);
            int[] lenses = layout.indicesOf(GunType.SlotRole.LENS);
            boolean housingOk = Arrays.stream(housing).allMatch(slot -> GunMaterials.isGatlingLensHousing(inv.getStackInSlot(slot)));
            String id = uniform(inv, lenses, stack -> GunMaterials.lensByItem(stack).map(GunMaterials.Lens::id));
            if (housingOk && id != null) {
                lens = id;
                Arrays.stream(housing).forEach(consume::add);
                Arrays.stream(lenses).forEach(consume::add);
            }
            if (!housingOk) {
                problems.add(Problem.LENS_HOUSING);
            }
            if (id == null) {
                problems.add(Problem.LENS_SET);
            }
        }

        // 瞄具：可选的一格（手枪 / 步枪，存材料 id「mru」），或狙击的镜座 + 两格狙击镜（存狙击镜 id）
        String scope = null;
        for (int slot : layout.indicesOf(GunType.SlotRole.SCOPE_OPT)) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Optional<GunMaterials.Scope> found = GunMaterials.scopeByItem(type, stack);
            if (found.isPresent()) {
                scope = found.get().id();
                consume.add(slot);
            } else {
                problems.add(Problem.SCOPE);
            }
        }
        if (layout.has(GunType.SlotRole.SCOPE_HOUSING) || layout.has(GunType.SlotRole.SCOPE)) {
            int[] housing = layout.indicesOf(GunType.SlotRole.SCOPE_HOUSING);
            int[] scopes = layout.indicesOf(GunType.SlotRole.SCOPE);
            boolean housingOk = Arrays.stream(housing).allMatch(slot -> GunMaterials.isSniperScopeHousing(inv.getStackInSlot(slot)));
            String id = uniform(inv, scopes, stack -> GunMaterials.scopeByItem(type, stack).map(GunMaterials.Scope::id));
            if (housingOk && id != null) {
                scope = id;
                Arrays.stream(housing).forEach(consume::add);
                Arrays.stream(scopes).forEach(consume::add);
            }
            if (!housingOk) {
                problems.add(Problem.SCOPE_HOUSING);
            }
            if (id == null) {
                problems.add(Problem.SCOPE_SET);
            }
        }

        if (!problems.isEmpty() || base == null || handle == null || device == null) {
            return new Check(null, Collections.unmodifiableSet(problems));
        }
        int[] slots = consume.stream().mapToInt(Integer::intValue).toArray();
        return new Check(new Plan(type, new GunParts(base, handle, device, lens, scope), slots, soul), Collections.unmodifiableSet(problems));
    }

    /** 界面要列的原因：{@link #check} 的原因，外加枪物品没注册 */
    public static Set<Problem> problems(GunType type, IItemHandler inv) {
        Check check = check(type, inv);
        if (gunItem(type) != null) {
            return check.problems();
        }
        EnumSet<Problem> all = check.problems().isEmpty() ? EnumSet.noneOf(Problem.class) : EnumSet.copyOf(check.problems());
        all.add(Problem.NO_GUN_ITEM);
        return Collections.unmodifiableSet(all);
    }

    /** 能组装就给出新枪和要扣的格；不改物品栏（两端都能调，客户端拿来画预览） */
    public static Optional<Result> assemble(GunType type, IItemHandler inv) {
        Check check = check(type, inv);
        Item item = gunItem(type);
        if (check.plan() == null || item == null) {
            return Optional.empty();
        }
        Plan plan = check.plan();
        ItemStack gun = new ItemStack(item);
        plan.parts().writeTo(gun);
        int transfer = Math.min(soulMRU(inv.getStackInSlot(plan.soulSlot())), MAX_SOUL_TRANSFER);
        if (transfer > 0) {
            gun.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(Math.min(transfer, handler.getMaxMRU())));
        }
        return Optional.of(new Result(gun, plan.consumeSlots()));
    }

    /**
     * 界面右上角的预览（原作 previewWeapon L374~406）：能组装时是新枪；核心和灵魂格都放好了、别的还没齐时是一把没装部件的枪（只画底图）；
     * 否则为空。
     */
    public static ItemStack preview(GunType type, IItemHandler inv) {
        Optional<Result> result = assemble(type, inv);
        if (result.isPresent()) {
            return result.get().gun();
        }
        Item item = gunItem(type);
        Check check = check(type, inv);
        if (item != null && !check.problems().contains(Problem.CORE) && !check.problems().contains(Problem.SOUL)) {
            return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    /** 这种枪的物品；没注册（ECGuns 还没接上）返回 null */
    @Nullable
    public static Item gunItem(GunType type) {
        return ForgeRegistries.ITEMS.containsKey(type.itemId()) ? ForgeRegistries.ITEMS.getValue(type.itemId()) : null;
    }

    /** 灵魂格物品里存的 MRU（不是 MRU 储存物品为 0） */
    public static int soulMRU(ItemStack soul) {
        return soul.isEmpty() ? 0 : soul.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandler::getMRU).orElse(0);
    }

    // ------------------------------------------------------------------

    private static int first(GunType.Layout layout, GunType.SlotRole role) {
        int[] slots = layout.indicesOf(role);
        return slots.length == 0 ? -1 : slots[0];
    }

    /** 这些格都不空、都认得、而且认出来是同一种：返回那一种的 id；否则（含没有格子）null */
    @Nullable
    private static String uniform(IItemHandler inv, int[] slots, Function<ItemStack, Optional<String>> idOf) {
        if (slots.length == 0) {
            return null;
        }
        String id = null;
        for (int slot : slots) {
            Optional<String> found = idOf.apply(inv.getStackInSlot(slot));
            if (found.isEmpty() || id != null && !id.equals(found.get())) {
                return null;
            }
            id = found.get();
        }
        return id;
    }

    private static void partSlots(@Nullable String id, GunType.Layout layout, GunType.SlotRole role, Problem problem,
                                  Set<Integer> consume, Set<Problem> problems) {
        if (id == null) {
            problems.add(problem);
        } else {
            IntStream.of(layout.indicesOf(role)).forEach(consume::add);
        }
    }
}
