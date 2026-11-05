package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity.Status;
import com.flameforge.essentialcraft5.menu.LeyProjectorMenu;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECLeyProjector;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 地脉投影仪（ECLeyProjector，第五批 batch5.md §3.4）的 GameTest：
 * <ul>
 *   <li>速度与扣费：跑 3T+2 tick 正好投影 3 格；每格开头一次扣完，所以已扣 = 60 ×（投影过的格数 + 正在投影的那格）；进度条最大值 20。</li>
 *   <li>每人每区块：同主人同区块后放的排队（不扣 MRU、界面指出挡路那台）；第一台拆掉后第二台 ≤120 tick 接上；两个主人互不影响；
 *       放下时间相同按坐标 asLong 决出；专用服上没有主人的不工作、不扣 MRU（D4 A ①）。</li>
 *   <li>投影表：所有 {@code ley_projector/} 表都能加载、type 是 fishing、不引用别的表、每个物品没有燃烧时间且不在 #forge:ores；
 *       真表掷 3 000 次（带透镜、不带各一半）产物都在表里列出的物品之内；运行时过滤丢掉煤和铁矿石方块（不进输出、不进待放）；没有表 = 没有地脉。</li>
 *   <li>输出满了停（待放、不再扣 MRU），取走一格自动接上；红石停（进度保留、不扣 MRU）；透镜 = 时运 I（界面上透镜格上限 1）；
 *       漏斗规则；「保留岩石」按钮（非法按钮号被拒）；待放的产物挖掉时掉一份、先清空就不掉（/clone 不复制）。</li>
 *   <li>岩石（审查 LEY-1）：不进待放、不让机器停——岩石占着输出格时矿照样进空格；每种岩石只占一格、合计最多 2 格，放不下的丢掉；
 *       认岩石的种类存盘、不发给客户端；每张岩石表最多 3 种。</li>
 *   <li>世界不动：四周和下面铺石头、铁矿石、带东西的箱子，模拟领地拒绝所有格子，跑 100 tick：没有任何破坏 / 放置事件，
 *       每格方块状态和箱子内容不变，没有掉落物（「没有领地时照常工作」由速度测试覆盖，「被拒」这台机器没有可被拒的操作，领地怎么设它都照常投影）。</li>
 *   <li>配方用魔力挖掘器（按注册名取）。</li>
 * </ul>
 * 每个测试的主人都是新的随机 UUID（相邻的测试结构可能在同一区块，同主人才会互相挡）。机器的 MRU 在放下时加满，不放绑定宝石。
 * 测试钩子是方块实体的实例字段（{@code setRollerForTest / setTableForTest}），不改真配置。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class LeyProjectorTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation NETHER = ResourceLocation.withDefaultNamespace("the_nether");

    private LeyProjectorTests() {
    }

    // ---------------- 工具 ----------------

    /** 放一台投影仪：主人（null = 没有主人）、放下时间，MRU 加满 */
    private static LeyProjectorBlockEntity place(GameTestHelper helper, BlockPos rel, @Nullable UUID owner, long placedTick) {
        helper.setBlock(rel, ECLeyProjector.LEY_PROJECTOR.get());
        LeyProjectorBlockEntity be = (LeyProjectorBlockEntity) helper.getBlockEntity(rel);
        if (owner != null) {
            be.setOwner(owner, "ley-test");
        }
        be.markPlaced(placedTick);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        return be;
    }

    /** rel 旁边一格、且和 rel 在同一区块（结构可能旋转，按绝对坐标判断） */
    private static BlockPos sameChunkNeighbour(GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = rel.relative(direction);
            BlockPos other = helper.absolutePos(candidate);
            if ((other.getX() >> 4) == (abs.getX() >> 4) && (other.getZ() >> 4) == (abs.getZ() >> 4)) {
                return candidate;
            }
        }
        throw new IllegalStateException("四个方向都跨区块，不可能");
    }

    private static int mru(LeyProjectorBlockEntity be) {
        return be.getMRUStorage().getMRU();
    }

    private static int full(LeyProjectorBlockEntity be) {
        return be.getMRUStorage().getMaxMRU();
    }

    private static boolean outputsEmpty(LeyProjectorBlockEntity be) {
        for (int slot = LeyProjectorBlockEntity.FIRST_OUTPUT; slot < LeyProjectorBlockEntity.SLOTS; slot++) {
            if (!be.getInventory().getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> outputs(LeyProjectorBlockEntity be) {
        List<ItemStack> list = new ArrayList<>();
        for (int slot = LeyProjectorBlockEntity.FIRST_OUTPUT; slot < LeyProjectorBlockEntity.SLOTS; slot++) {
            ItemStack stack = be.getInventory().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }

    @Nullable
    private static Item itemById(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && ForgeRegistries.ITEMS.containsKey(key) ? ForgeRegistries.ITEMS.getValue(key) : null;
    }

    private static ItemStack paleGem(GameTestHelper helper) {
        Item gem = itemById("essentialcraft5:pale_gem");
        helper.assertTrue(gem != null, "淡青宝石应已注册（第四批 ECDigger）");
        return new ItemStack(gem);
    }

    /** 读一张表的 JSON（战利品表 id → data/<ns>/loot_tables/<path>.json） */
    private static JsonObject readTable(GameTestHelper helper, ResourceLocation id) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "loot_tables/" + id.getPath() + ".json");
        Optional<Resource> resource = server.getResourceManager().getResource(file);
        helper.assertTrue(resource.isPresent(), "找不到投影表文件 " + file);
        try (Reader reader = resource.get().openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("读不了 " + file, e);
        }
    }

    /**
     * 递归收集表里写到的物品（item 条目的 name、tag 条目的全部成员）；嵌套的 loot_table 条目记进 nested。
     */
    private static void collect(JsonElement element, Set<Item> items, List<String> nested, List<String> problems) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collect(child, items, nested, problems);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("type") && object.get("type").isJsonPrimitive() && object.has("name")) {
            String type = object.get("type").getAsString();
            String name = object.get("name").getAsString();
            switch (type) {
                case "minecraft:item", "item" -> {
                    Item item = itemById(name);
                    if (item == null) {
                        problems.add("物品没注册：" + name);
                    } else {
                        items.add(item);
                    }
                }
                case "minecraft:tag", "tag" -> {
                    ResourceLocation tagId = ResourceLocation.tryParse(name);
                    if (tagId == null) {
                        problems.add("标签名不对：" + name);
                    } else {
                        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
                        ForgeRegistries.ITEMS.tags().getTag(tag).forEach(items::add);
                    }
                }
                case "minecraft:loot_table", "loot_table" -> nested.add(name);
                default -> {
                }
            }
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            collect(entry.getValue(), items, nested, problems);
        }
    }

    private static Set<Item> tableItems(GameTestHelper helper, ResourceLocation id) {
        Set<Item> items = new HashSet<>();
        List<String> nested = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        collect(readTable(helper, id), items, nested, problems);
        helper.assertTrue(problems.isEmpty(), id + "：" + problems);
        helper.assertTrue(nested.isEmpty(), id + " 不应引用别的表：" + nested);
        return items;
    }

    // ---------------- 速度与扣费 ----------------

    /**
     * 3T+2 tick 正好投影 3 格；每格开头一次扣完 MRU，所以已扣 = 60 ×（3 + 正在投影的那一格）——按 batch5 §3.4 测试 1 的「待核实」，
     * 第 3 格投影完的下一 tick 第 4 格就开头扣了，3T+2 时一般是少 240。没有任何领地时照常工作。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void leyRateAndCost(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        int ticks = be.ticksPerBlock();
        int cost = be.cost();
        helper.assertTrue(ticks == 20, "默认每格 20 tick，实际 " + ticks);
        helper.assertTrue(cost == 60, "默认每格 60 MRU，实际 " + cost);
        int start = mru(be);
        helper.runAfterDelay(3L * ticks + 2, () -> {
            int projected = be.projectedCount();
            helper.assertTrue(projected == 3, "3T+2 tick 应正好投影 3 格，实际 " + projected);
            int started = projected + (be.progress() > 0 ? 1 : 0);
            int spent = start - mru(be);
            helper.assertTrue(spent == cost * started, "已扣应为 " + cost + " × " + started + "，实际 " + spent + "（进度 " + be.progress() + "）");
            helper.assertTrue(be.status() == Status.WORKING, "应在投影，实际 " + be.status());
            LeyProjectorMenu menu = new LeyProjectorMenu(0, ProtectionFrameworkTests.realPlayer(helper).getInventory(), be);
            helper.assertTrue(menu.maxProgress() == 20, "进度条最大值应为 20，实际 " + menu.maxProgress());
            helper.assertTrue(menu.status() == Status.WORKING, "界面状态应同步为工作中");
            helper.succeed();
        });
    }

    /** MRU 不够一格：不工作、不扣、不投影 */
    @GameTest(template = EMPTY)
    public static void leyNoMruWaits(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        be.getMRUStorage().setMRU(be.cost() - 1);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.status() == Status.NO_MRU, "应是 MRU 不足，实际 " + be.status());
            helper.assertTrue(mru(be) == be.cost() - 1, "不该扣 MRU");
            helper.assertTrue(be.projectedCount() == 0 && be.progress() == 0, "不该投影");
            helper.succeed();
        });
    }

    // ---------------- 每人每区块 ----------------

    /** 同主人同区块两台：后放的排队（DUPLICATE），不扣 MRU，挡路坐标 = 第一台；第一台照常工作 */
    @GameTest(template = EMPTY)
    public static void leySameOwnerSecondIdle(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        BlockPos second = sameChunkNeighbour(helper, CENTER);
        LeyProjectorBlockEntity a = place(helper, CENTER, owner, 100L);
        LeyProjectorBlockEntity b = place(helper, second, owner, 200L);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(a.status() == Status.WORKING, "先放的应在工作，实际 " + a.status());
            helper.assertTrue(mru(a) == full(a) - a.cost(), "先放的应已扣一格 MRU");
            helper.assertTrue(b.status() == Status.DUPLICATE, "后放的应排队，实际 " + b.status());
            helper.assertTrue(mru(b) == full(b), "排队的不该扣 MRU");
            helper.assertTrue(helper.absolutePos(CENTER).equals(b.blocker()), "挡路坐标应是第一台，实际 " + b.blocker());
            LeyProjectorMenu menu = new LeyProjectorMenu(0, ProtectionFrameworkTests.realPlayer(helper).getInventory(), b);
            helper.assertTrue(helper.absolutePos(CENTER).equals(menu.blocker()), "界面上还原的挡路坐标应是第一台，实际 " + menu.blocker());
            helper.succeed();
        });
    }

    /** 第一台拆掉后，排队的第二台 ≤120 tick 接上（被挡住的每 100 tick 查一次，不用释放任何全局表） */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void leySecondTakesOver(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        BlockPos second = sameChunkNeighbour(helper, CENTER);
        place(helper, CENTER, owner, 100L);
        LeyProjectorBlockEntity b = place(helper, second, owner, 200L);
        long[] removedAt = {-1L};
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(b.status() == Status.DUPLICATE, "第二台应在排队，实际 " + b.status());
                    helper.setBlock(CENTER, Blocks.AIR);
                    removedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getTick() - removedAt[0] <= 120, "第一台拆掉 120 tick 内第二台应接上");
                    helper.assertTrue(b.status() == Status.WORKING, "第二台应接上，实际 " + b.status());
                })
                .thenSucceed();
    }

    /** 同区块两个主人：互不影响，都工作 */
    @GameTest(template = EMPTY)
    public static void leyTwoOwnersBothWork(GameTestHelper helper) {
        BlockPos second = sameChunkNeighbour(helper, CENTER);
        LeyProjectorBlockEntity a = place(helper, CENTER, UUID.randomUUID(), 100L);
        LeyProjectorBlockEntity b = place(helper, second, UUID.randomUUID(), 200L);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(a.status() == Status.WORKING && b.status() == Status.WORKING,
                    "两个主人应都在工作，实际 " + a.status() + " / " + b.status());
            helper.assertTrue(mru(a) == full(a) - a.cost() && mru(b) == full(b) - b.cost(), "两台都应已扣一格 MRU");
            helper.succeed();
        });
    }

    /** 放下时间相同：坐标 asLong 小的那台工作（/clone 出来的副本也这样决出） */
    @GameTest(template = EMPTY)
    public static void leySameTickTieBreak(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        BlockPos otherRel = sameChunkNeighbour(helper, CENTER);
        LeyProjectorBlockEntity a = place(helper, CENTER, owner, 100L);
        LeyProjectorBlockEntity b = place(helper, otherRel, owner, 100L);
        boolean aFirst = helper.absolutePos(CENTER).asLong() < helper.absolutePos(otherRel).asLong();
        LeyProjectorBlockEntity winner = aFirst ? a : b;
        LeyProjectorBlockEntity loser = aFirst ? b : a;
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(winner.status() == Status.WORKING, "坐标小的应工作，实际 " + winner.status());
            helper.assertTrue(loser.status() == Status.DUPLICATE, "坐标大的应排队，实际 " + loser.status());
            helper.assertTrue(mru(loser) == full(loser), "排队的不该扣 MRU");
            helper.succeed();
        });
    }

    /** 专用服（GameTest 服）上没有主人的：NO_OWNER，不扣 MRU、不投影（D4 A ①） */
    @GameTest(template = EMPTY)
    public static void leyNoOwnerIdles(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, null, 100L);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.status() == Status.NO_OWNER, "没有主人应不工作，实际 " + be.status());
            helper.assertTrue(mru(be) == full(be), "不该扣 MRU");
            helper.assertTrue(be.projectedCount() == 0, "不该投影");
            helper.succeed();
        });
    }

    // ---------------- 投影表 ----------------

    /**
     * 所有 {@code essentialcraft5:ley_projector/} 表：文件都加载成功（数量对得上）、type 是 fishing、不引用别的表，
     * 每个物品没有燃烧时间、不在 #forge:ores；主世界、下界的矿石表和岩石表都在。
     */
    @GameTest(template = EMPTY)
    public static void leyNeverOutputsFuel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<ResourceLocation, Resource> files = server.getResourceManager().listResources("loot_tables/ley_projector",
                file -> file.getNamespace().equals(EssentialCraft5.MODID) && file.getPath().endsWith(".json"));
        helper.assertTrue(!files.isEmpty(), "应有投影表");
        Set<ResourceLocation> loaded = new HashSet<>();
        for (ResourceLocation key : server.getLootData().getKeys(LootDataType.TABLE)) {
            if (key.getNamespace().equals(EssentialCraft5.MODID) && key.getPath().startsWith("ley_projector/")) {
                loaded.add(key);
            }
        }
        helper.assertTrue(loaded.size() == files.size(), "投影表文件 " + files.size() + " 个，加载成功 " + loaded.size() + " 个（有的没通过校验）");
        for (ResourceLocation dimension : List.of(OVERWORLD, NETHER)) {
            helper.assertTrue(loaded.contains(LeyProjectorBlockEntity.tableId(LeyProjectorBlockEntity.ORES_DIR, dimension)), dimension + " 应有矿石表");
            helper.assertTrue(loaded.contains(LeyProjectorBlockEntity.tableId(LeyProjectorBlockEntity.ROCK_DIR, dimension)), dimension + " 应有岩石表");
        }
        for (ResourceLocation id : loaded) {
            JsonObject json = readTable(helper, id);
            helper.assertTrue(json.has("type") && "minecraft:fishing".equals(json.get("type").getAsString()), id + " 的 type 应是 minecraft:fishing");
            Set<Item> items = tableItems(helper, id);
            helper.assertTrue(!items.isEmpty(), id + " 应至少有一种物品");
            if (id.getPath().startsWith(LeyProjectorBlockEntity.ROCK_DIR)) {
                // LEY-1：投影仪里岩石合计最多占 2 格，岩石表种类多了只会被丢掉
                helper.assertTrue(items.size() <= 3, id + " 岩石表最多 3 种，实际 " + items);
            }
            for (Item item : items) {
                ItemStack stack = new ItemStack(item);
                helper.assertTrue(ForgeHooks.getBurnTime(stack, null) <= 0, id + " 不许出燃料：" + item);
                helper.assertTrue(!stack.is(Tags.Items.ORES), id + " 不许出矿石方块：" + item);
                helper.assertTrue(LeyProjectorBlockEntity.allowedOutput(stack), id + " 的产物应能通过运行时过滤：" + item);
            }
        }
        helper.succeed();
    }

    /**
     * 海纳尔（第六批 6a M7，审查 M7-1）：矿石表和岩石表都能加载；产物里没有榍石、虚空物品、余烬、神秘饰品
     * （removed_redesign L172：这些只能去海纳尔探索拿）；矿的种类和主世界相同，但按普查实测，粗铜、粗铁比主世界少
     * （关了铜铁大矿脉）——防止有人又把主世界的表整张照抄过来。
     */
    @GameTest(template = EMPTY)
    public static void leyHoannaTable(GameTestHelper helper) {
        LootDataManager data = helper.getLevel().getServer().getLootData();
        ResourceLocation oresId = LeyProjectorBlockEntity.tableId(LeyProjectorBlockEntity.ORES_DIR, ECHoanna.ID);
        ResourceLocation rockId = LeyProjectorBlockEntity.tableId(LeyProjectorBlockEntity.ROCK_DIR, ECHoanna.ID);
        helper.assertTrue(data.getElementOptional(LootDataType.TABLE, oresId).isPresent(), "海纳尔应有矿石表 " + oresId);
        helper.assertTrue(data.getElementOptional(LootDataType.TABLE, rockId).isPresent(), "海纳尔应有岩石表 " + rockId);
        Set<Item> forbidden = new HashSet<>(List.of(ECVoid.TITANITE.get(), ECVoid.TWINKLING_TITANITE.get(), ECVoid.VOID_PLATING.get(),
                ECVoid.VOID_CORE.get(), ECVoid.VOID_MRU_REACTOR.get(), ECVoid.MYSTICAL_ACCESSORY.get()));
        ForgeRegistries.ITEMS.tags().getTag(ECVoid.EMBERS).forEach(forbidden::add);
        helper.assertTrue(forbidden.size() >= 14, "余烬标签应有 8 种，实际禁用表 " + forbidden);
        Set<Item> oreItems = tableItems(helper, oresId);
        Set<Item> rockItems = tableItems(helper, rockId);
        for (Item item : forbidden) {
            helper.assertTrue(!oreItems.contains(item) && !rockItems.contains(item), "海纳尔投影表不许出 " + item + "（保留探索）");
        }
        Set<Item> overworldOres = tableItems(helper, LeyProjectorBlockEntity.tableId(LeyProjectorBlockEntity.ORES_DIR, OVERWORLD));
        helper.assertTrue(oreItems.equals(overworldOres), "海纳尔的矿种应和主世界相同：海纳尔 " + oreItems + "，主世界 " + overworldOres);
        Map<String, Integer> hoanna = entryWeights(readTable(helper, oresId));
        Map<String, Integer> overworld = entryWeights(readTable(helper, LeyProjectorBlockEntity.tableId(LeyProjectorBlockEntity.ORES_DIR, OVERWORLD)));
        for (String name : List.of("minecraft:raw_copper", "minecraft:raw_iron")) {
            helper.assertTrue(hoanna.getOrDefault(name, 0) > 0 && hoanna.getOrDefault(name, 0) < overworld.getOrDefault(name, 0),
                    "海纳尔关了铜铁大矿脉，" + name + " 的权重应比主世界小：海纳尔 " + hoanna.get(name) + "，主世界 " + overworld.get(name));
        }
        helper.succeed();
    }

    /** 表里每个物品条目的权重（按物品名） */
    private static Map<String, Integer> entryWeights(JsonObject table) {
        Map<String, Integer> weights = new HashMap<>();
        for (JsonElement pool : table.getAsJsonArray("pools")) {
            for (JsonElement entry : pool.getAsJsonObject().getAsJsonArray("entries")) {
                JsonObject object = entry.getAsJsonObject();
                if (object.has("name")) {
                    weights.merge(object.get("name").getAsString(), object.has("weight") ? object.get("weight").getAsInt() : 1, Integer::sum);
                }
            }
        }
        return weights;
    }

    /** 真表掷 3 000 次（带透镜、不带各一半），产物都在表里列出的物品之内、都能通过过滤；岩石表掷 200 次同理 */
    @GameTest(template = EMPTY)
    public static void leyOutputsSubsetOfTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootDataManager data = level.getServer().getLootData();
        ResourceLocation oresId = LeyProjectorBlockEntity.tableId(LeyProjectorBlockEntity.ORES_DIR, OVERWORLD);
        ResourceLocation rockId = LeyProjectorBlockEntity.tableId(LeyProjectorBlockEntity.ROCK_DIR, OVERWORLD);
        LootTable ores = data.getElementOptional(LootDataType.TABLE, oresId).orElse(null);
        LootTable rock = data.getElementOptional(LootDataType.TABLE, rockId).orElse(null);
        helper.assertTrue(ores != null && rock != null, "主世界的矿石表、岩石表应已加载");
        Set<Item> oreItems = tableItems(helper, oresId);
        Set<Item> rockItems = tableItems(helper, rockId);
        helper.assertTrue(oreItems.contains(Items.DIAMOND) && oreItems.contains(Items.RAW_IRON), "主世界表应有钻石和粗铁");
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(CENTER));
        int found = 0;
        for (int i = 0; i < 3000; i++) {
            for (ItemStack stack : LeyProjectorBlockEntity.roll(ores, LeyProjectorBlockEntity.params(level, origin, i % 2 == 0))) {
                helper.assertTrue(oreItems.contains(stack.getItem()), "掷出了表里没有的 " + stack);
                helper.assertTrue(LeyProjectorBlockEntity.allowedOutput(stack), "掷出了过不了过滤的 " + stack);
                helper.assertTrue(stack.getCount() <= stack.getMaxStackSize(), "应按最大堆叠拆好：" + stack);
                found++;
            }
        }
        helper.assertTrue(found > 0, "3000 次应至少出一次矿（每格约 1%）");
        for (int i = 0; i < 200; i++) {
            List<ItemStack> stones = LeyProjectorBlockEntity.roll(rock, LeyProjectorBlockEntity.params(level, origin, false));
            helper.assertTrue(stones.size() == 1 && rockItems.contains(stones.get(0).getItem()), "岩石表每次应出 1 个表里的岩石：" + stones);
        }
        helper.succeed();
    }

    /** 运行时过滤：煤（有燃烧时间）、铁矿石（#forge:ores）丢掉，不进输出、不进待放；同一次的钻石照常放进输出 */
    @GameTest(template = EMPTY)
    public static void leyFilterDropsFuel(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        be.setRollerForTest(params -> List.of(new ItemStack(Items.COAL, 3), new ItemStack(Items.IRON_ORE), new ItemStack(Items.DIAMOND)));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.projectedCount() >= 1, "应已投影一格"))
                .thenExecute(() -> {
                    List<ItemStack> out = outputs(be);
                    helper.assertTrue(out.size() == 1 && out.get(0).is(Items.DIAMOND), "输出里应只有钻石，实际 " + out);
                    helper.assertTrue(be.pendingView().isEmpty(), "待放里不该有东西，实际 " + be.pendingView());
                })
                .thenSucceed();
    }

    /** 这个维度找不到表：没有地脉，不扣 MRU */
    @GameTest(template = EMPTY)
    public static void leyNoTable(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        be.setTableForTest(EssentialCraft5.id("ley_projector/ores/nowhere/none"));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.status() == Status.NO_LEY, "应是没有地脉，实际 " + be.status());
            helper.assertTrue(mru(be) == full(be), "不该扣 MRU");
            helper.assertTrue(be.projectedCount() == 0, "不该投影");
            helper.succeed();
        });
    }

    /** 表 id 的拼法：先维度 id、再维度类型，都是 {@code ley_projector/<ores|rock>/<ns>/<path>} */
    @GameTest(template = EMPTY)
    public static void leyTableIdLayout(GameTestHelper helper) {
        ResourceLocation id = LeyProjectorBlockEntity.tableId(LeyProjectorBlockEntity.ORES_DIR, ResourceLocation.fromNamespaceAndPath("mymod", "mining_world"));
        helper.assertTrue(id.equals(EssentialCraft5.id("ley_projector/ores/mymod/mining_world")), "表 id 拼错了：" + id);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.dimension() == Level.OVERWORLD && level.dimensionTypeId().location().equals(OVERWORLD),
                "GameTest 在主世界跑（维度 id 和维度类型都是 minecraft:overworld）");
        helper.succeed();
    }

    // ---------------- 输出、红石、透镜、漏斗、按钮 ----------------

    /** 9 格塞满不同物品：产物进待放、状态「输出格已满」、不再扣 MRU；取走一格后待放自动放进去、接着工作 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void leyOutputFullStops(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        Item[] fillers = {Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_PLANKS, Items.GLASS, Items.BRICKS, Items.CLAY, Items.WHITE_WOOL};
        for (int i = 0; i < LeyProjectorBlockEntity.OUTPUT_SLOTS; i++) {
            be.getInventory().setStackInSlot(LeyProjectorBlockEntity.FIRST_OUTPUT + i, new ItemStack(fillers[i], 64));
        }
        be.setRollerForTest(params -> List.of(new ItemStack(Items.DIAMOND)));
        int[] mruAt = {0};
        int[] projectedAt = {0};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.status() == Status.OUTPUT_FULL, "应是输出格已满，实际 " + be.status()))
                .thenExecute(() -> {
                    helper.assertTrue(be.pendingView().size() == 1 && be.pendingView().get(0).is(Items.DIAMOND), "钻石应在待放里，实际 " + be.pendingView());
                    mruAt[0] = mru(be);
                    projectedAt[0] = be.projectedCount();
                })
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(mru(be) == mruAt[0], "输出满了不该再扣 MRU");
                    helper.assertTrue(be.projectedCount() == projectedAt[0], "输出满了不该再投影");
                    helper.assertTrue(be.status() == Status.OUTPUT_FULL, "应仍是输出格已满，实际 " + be.status());
                    be.getInventory().setStackInSlot(LeyProjectorBlockEntity.FIRST_OUTPUT + 4, ItemStack.EMPTY);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(be.pendingView().isEmpty(), "腾出一格后待放应自动放进去");
                    helper.assertTrue(be.getInventory().getStackInSlot(LeyProjectorBlockEntity.FIRST_OUTPUT + 4).is(Items.DIAMOND), "钻石应进空出来的格子");
                })
                .thenWaitUntil(() -> helper.assertTrue(be.status() == Status.WORKING, "应接着工作，实际 " + be.status()))
                .thenSucceed();
    }

    /** 投影到一半接上红石：停在原处（进度保留）、不扣 MRU；断开后接着投影 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void leyRedstoneStops(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        BlockPos torch = CENTER.above();
        int[] progressAt = {0};
        int[] mruAt = {0};
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.status() == Status.WORKING && be.progress() > 0, "应在投影第一格，实际 " + be.status() + " 进度 " + be.progress());
                    helper.setBlock(torch, Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(2, () -> {
                    progressAt[0] = be.progress();
                    mruAt[0] = mru(be);
                })
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(be.status() == Status.POWERED, "应是红石暂停，实际 " + be.status());
                    helper.assertTrue(be.progress() == progressAt[0] && be.progress() > 0, "进度应停在原处，实际 " + be.progress());
                    helper.assertTrue(mru(be) == mruAt[0], "暂停时不该扣 MRU");
                    helper.assertTrue(be.projectedCount() == 0, "暂停时不该投影");
                    helper.setBlock(torch, Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.projectedCount() >= 1, "断开红石后应接着投影完这一格"))
                .thenSucceed();
    }

    /** 透镜格空着：TOOL 没有时运；放 1 颗淡青宝石：时运 I；界面上透镜格上限 1、只收透镜标签里的物品 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void leyLensFortune(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        ItemStack gem = paleGem(helper);
        List<Integer> seen = new ArrayList<>();
        be.setRollerForTest(params -> {
            seen.add(params.getParameter(LootContextParams.TOOL).getEnchantmentLevel(Enchantments.BLOCK_FORTUNE));
            return List.of();
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!seen.isEmpty(), "应已投影一格"))
                .thenExecute(() -> {
                    helper.assertTrue(seen.get(0) == 0, "没有透镜时 TOOL 不该有时运，实际 " + seen.get(0));
                    be.getInventory().setStackInSlot(LeyProjectorBlockEntity.SLOT_LENS, gem.copy());
                    seen.clear();
                })
                .thenWaitUntil(() -> helper.assertTrue(!seen.isEmpty(), "应又投影了一格"))
                .thenExecute(() -> {
                    helper.assertTrue(seen.get(0) == 1, "有透镜时应是时运 I，实际 " + seen.get(0));
                    helper.assertTrue(be.getInventory().getStackInSlot(LeyProjectorBlockEntity.SLOT_LENS).getCount() == 1, "透镜不该被消耗");
                    ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
                    LeyProjectorMenu menu = new LeyProjectorMenu(0, player.getInventory(), be);
                    Slot lens = menu.getSlot(LeyProjectorBlockEntity.SLOT_LENS);
                    helper.assertTrue(lens.getMaxStackSize() == 1 && lens.getMaxStackSize(gem.copyWithCount(5)) == 1, "透镜格上限应是 1");
                    helper.assertTrue(lens.mayPlace(gem) && !lens.mayPlace(new ItemStack(Items.DIAMOND)), "透镜格只收淡青宝石");
                    helper.assertTrue(!menu.getSlot(LeyProjectorBlockEntity.FIRST_OUTPUT).mayPlace(gem), "输出格不能放东西");
                })
                .thenSucceed();
    }

    /** 漏斗、管道：透镜格和输出格插不进去，0 号格只收绑定宝石；透镜格抽不出来，输出格能抽 */
    @GameTest(template = EMPTY)
    public static void leyAutomationRules(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("投影仪应暴露物品能力"));
        ItemStack gem = paleGem(helper);
        helper.assertTrue(handler.insertItem(LeyProjectorBlockEntity.SLOT_LENS, gem.copy(), true).getCount() == 1, "漏斗不能往透镜格塞");
        for (int slot = LeyProjectorBlockEntity.FIRST_OUTPUT; slot < LeyProjectorBlockEntity.SLOTS; slot++) {
            helper.assertTrue(handler.insertItem(slot, new ItemStack(Items.DIAMOND), true).getCount() == 1, "漏斗不能往输出格 " + slot + " 塞");
        }
        helper.assertTrue(handler.insertItem(LeyProjectorBlockEntity.SLOT_GEM, new ItemStack(Items.DIAMOND), true).getCount() == 1,
                "0 号格只收绑定宝石");
        be.getInventory().setStackInSlot(LeyProjectorBlockEntity.SLOT_LENS, gem.copy());
        helper.assertTrue(handler.extractItem(LeyProjectorBlockEntity.SLOT_LENS, 1, false).isEmpty(), "透镜格不能被抽走");
        int out = LeyProjectorBlockEntity.FIRST_OUTPUT + 3;
        be.getInventory().setStackInSlot(out, new ItemStack(Items.RAW_IRON, 3));
        ItemStack pulled = handler.extractItem(out, 3, false);
        helper.assertTrue(pulled.is(Items.RAW_IRON) && pulled.getCount() == 3, "输出格应能抽出来，实际 " + pulled);
        helper.succeed();
    }

    /** 「保留岩石」：按钮 0 切换，别的按钮号被拒；开着时没出矿的格子出岩石表里的东西 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void leyKeepRockToggle(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        be.setRollerForTest(params -> List.of());
        Set<Item> rockItems = tableItems(helper, LeyProjectorBlockEntity.tableId(LeyProjectorBlockEntity.ROCK_DIR, OVERWORLD));
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        LeyProjectorMenu menu = new LeyProjectorMenu(0, player.getInventory(), be);
        helper.assertTrue(!be.keepRock(), "默认不保留岩石");
        helper.assertTrue(menu.clickMenuButton(player, LeyProjectorMenu.BUTTON_KEEP_ROCK) && be.keepRock(), "按钮 0 应打开保留岩石");
        helper.assertTrue(!menu.clickMenuButton(player, 1) && !menu.clickMenuButton(player, -1) && be.keepRock(), "别的按钮号应被拒、不改状态");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.projectedCount() >= 1, "应已投影一格"))
                .thenExecute(() -> {
                    List<ItemStack> out = outputs(be);
                    helper.assertTrue(out.size() == 1 && rockItems.contains(out.get(0).getItem()) && out.get(0).getCount() == 1,
                            "开着保留岩石、没出矿时应出 1 个岩石，实际 " + out);
                    helper.assertTrue(menu.clickMenuButton(player, LeyProjectorMenu.BUTTON_KEEP_ROCK) && !be.keepRock(), "再按一次应关掉");
                })
                .thenSucceed();
    }

    /**
     * 岩石不挡矿（LEY-1）：「保留岩石」开着，6 个输出格先塞满别的东西，只剩 3 格。前 4 格没出矿：圆石、深板岩圆石各占一格（各 64 个），
     * 安山岩（岩石已占 2 格）、再来的圆石（自己那格满了）都丢掉、不进待放；之后出的钻石进最后那个空格，机器一直在投影，不停在「输出格已满」。
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void leyRockNeverBlocksOre(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        Item[] fillers = {Items.DIRT, Items.SAND, Items.OAK_PLANKS, Items.GLASS, Items.BRICKS, Items.CLAY};
        for (int i = 0; i < fillers.length; i++) {
            be.getInventory().setStackInSlot(LeyProjectorBlockEntity.FIRST_OUTPUT + i, new ItemStack(fillers[i], 64));
        }
        int[] oreCalls = {0};
        int[] rockCalls = {0};
        be.setRollerForTest(params -> ++oreCalls[0] <= 4 ? List.of() : List.of(new ItemStack(Items.DIAMOND)));
        ItemStack[] rocks = {new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLED_DEEPSLATE, 64),
                new ItemStack(Items.ANDESITE, 64), new ItemStack(Items.COBBLESTONE, 10)};
        be.setRockRollerForTest(params -> List.of(rocks[Math.min(rockCalls[0]++, rocks.length - 1)].copy()));
        be.toggleKeepRock();
        // 每 tick 都查：待放一直是空的，不会停在「输出格已满」
        helper.onEachTick(() -> {
            helper.assertTrue(be.pendingView().isEmpty(), "岩石不进待放，待放里不该有东西，实际 " + be.pendingView());
            helper.assertTrue(be.status() != Status.OUTPUT_FULL, "岩石占着输出格也不该停在「输出格已满」");
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.projectedCount() >= 6, "应已投影 6 格，实际 " + be.projectedCount()))
                .thenExecute(() -> {
                    helper.assertTrue(rockCalls[0] == 4, "前 4 格没出矿才掷岩石表，实际掷了 " + rockCalls[0] + " 次");
                    helper.assertTrue(be.pendingView().isEmpty(), "岩石不进待放，待放里不该有东西，实际 " + be.pendingView());
                    for (int i = 0; i < fillers.length; i++) {
                        ItemStack filler = be.getInventory().getStackInSlot(LeyProjectorBlockEntity.FIRST_OUTPUT + i);
                        helper.assertTrue(filler.is(fillers[i]) && filler.getCount() == 64, "原来的东西不该动，第 " + i + " 格实际 " + filler);
                    }
                    int cobble = 0;
                    int deepslate = 0;
                    int diamonds = 0;
                    for (int slot = LeyProjectorBlockEntity.FIRST_OUTPUT + fillers.length; slot < LeyProjectorBlockEntity.SLOTS; slot++) {
                        ItemStack out = be.getInventory().getStackInSlot(slot);
                        helper.assertTrue(!out.is(Items.ANDESITE), "岩石已占 2 格时安山岩应丢掉，第 " + slot + " 格实际 " + out);
                        if (out.is(Items.COBBLESTONE)) {
                            cobble++;
                            helper.assertTrue(out.getCount() == 64, "圆石那格应正好 64 个，实际 " + out);
                        } else if (out.is(Items.COBBLED_DEEPSLATE)) {
                            deepslate++;
                        } else if (out.is(Items.DIAMOND)) {
                            diamonds += out.getCount();
                        }
                    }
                    helper.assertTrue(cobble == 1 && deepslate == 1, "圆石、深板岩圆石应各占一格，实际 " + outputs(be));
                    helper.assertTrue(diamonds == be.projectedCount() - 4, "出的钻石应全部进格，应 " + (be.projectedCount() - 4) + "，实际 " + diamonds);
                    helper.assertTrue(be.rockKindsView().containsAll(List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.ANDESITE)),
                            "掷出过的岩石种类应都记下，实际 " + be.rockKindsView());
                })
                .thenExecuteAfter(3, () -> helper.assertTrue(be.status() == Status.WORKING, "岩石占着输出格也应接着投影，实际 " + be.status()))
                .thenSucceed();
    }

    /**
     * 岩石最多占 2 格（LEY-1）：岩石表轮着出圆石、深板岩圆石、安山岩、凝灰岩（各 40 个）。岩石始终不超过 2 格、每种不超过 1 格、
     * 并满 64 的那格不再开新格；取走一格岩石后别的岩石可以补进来，仍是 2 格。认岩石的种类存盘、不发给客户端。
     */
    @GameTest(template = EMPTY, timeoutTicks = 500)
    public static void leyRockSlotsCapped(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        be.setRollerForTest(params -> List.of());
        Item[] cycle = {Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.ANDESITE, Items.TUFF};
        int[] rockCalls = {0};
        be.setRockRollerForTest(params -> List.of(new ItemStack(cycle[rockCalls[0]++ % cycle.length], 40)));
        be.toggleKeepRock();
        Runnable invariant = () -> {
            List<ItemStack> out = outputs(be);
            Set<Item> kinds = new HashSet<>();
            for (ItemStack stack : out) {
                helper.assertTrue(kinds.add(stack.getItem()), "每种岩石最多占一格，实际 " + out);
            }
            helper.assertTrue(out.size() <= LeyProjectorBlockEntity.MAX_ROCK_SLOTS, "岩石最多占 2 格，实际 " + out);
            helper.assertTrue(be.pendingView().isEmpty(), "岩石不进待放，实际 " + be.pendingView());
        };
        helper.onEachTick(invariant);   // 每 tick 都查，违反就立刻失败
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.projectedCount() >= 6, "应已投影 6 格，实际 " + be.projectedCount()))
                .thenExecute(() -> {
                    // 圆石 40+40（多的丢掉）、深板岩圆石 40+40，安山岩、凝灰岩没地方
                    List<ItemStack> out = outputs(be);
                    helper.assertTrue(out.size() == 2 && out.stream().allMatch(stack -> stack.getCount() == 64)
                                    && out.stream().anyMatch(stack -> stack.is(Items.COBBLESTONE))
                                    && out.stream().anyMatch(stack -> stack.is(Items.COBBLED_DEEPSLATE)),
                            "应只有圆石、深板岩圆石各一满组，实际 " + out);
                    CompoundTag saved = be.saveWithoutMetadata();
                    helper.assertTrue(saved.getList(LeyProjectorBlockEntity.TAG_ROCK_KINDS, Tag.TAG_STRING).size() == 4,
                            "掷出过的 4 种岩石应存盘，实际 " + saved.get(LeyProjectorBlockEntity.TAG_ROCK_KINDS));
                    helper.assertTrue(!be.getUpdateTag().contains(LeyProjectorBlockEntity.TAG_ROCK_KINDS), "岩石种类不该发给客户端");
                    // 取走圆石那格
                    for (int slot = LeyProjectorBlockEntity.FIRST_OUTPUT; slot < LeyProjectorBlockEntity.SLOTS; slot++) {
                        if (be.getInventory().getStackInSlot(slot).is(Items.COBBLESTONE)) {
                            be.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
                        }
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(be.projectedCount() >= 10, "应已投影 10 格，实际 " + be.projectedCount()))
                .thenExecute(() -> {
                    List<ItemStack> out = outputs(be);
                    helper.assertTrue(out.size() == 2, "腾出一格后别的岩石应补进来、仍是 2 格，实际 " + out);
                    // 读档后照样认得出：换一台新的方块实体读同一份存档，岩石种类一样
                    LeyProjectorBlockEntity copy = new LeyProjectorBlockEntity(be.getBlockPos(), be.getBlockState());
                    copy.load(be.saveWithoutMetadata());
                    helper.assertTrue(copy.rockKindsView().equals(be.rockKindsView()), "读档后岩石种类应一样，实际 " + copy.rockKindsView());
                })
                .thenSucceed();
    }

    // ---------------- 待放与复制 ----------------

    private static void givePending(LeyProjectorBlockEntity be, ItemStack stack) {
        CompoundTag tag = be.saveWithoutMetadata();
        ListTag list = new ListTag();
        list.add(stack.save(new CompoundTag()));
        tag.put(LeyProjectorBlockEntity.TAG_PENDING, list);
        be.load(tag);
    }

    private static int diamondsAround(GameTestHelper helper, BlockPos rel) {
        AABB box = new AABB(helper.absolutePos(rel)).inflate(2D);
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            count += entity.getItem().is(Items.DIAMOND) ? entity.getItem().getCount() : 1000;
        }
        return count;
    }

    /** 待放的产物：先 clearContent（/clone move、/setblock 覆盖前原版会调）再移除 → 什么都不掉 */
    @GameTest(template = EMPTY)
    public static void leyPendingClearedNoDupe(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        givePending(be, new ItemStack(Items.DIAMOND, 5));
        helper.assertTrue(be.pendingView().size() == 1, "待放应读进来");
        be.clearContent();
        helper.setBlock(CENTER, Blocks.AIR);
        helper.assertTrue(diamondsAround(helper, CENTER) == 0, "清空后移除不该掉任何东西");
        helper.succeed();
    }

    /** 待放的产物：直接挖掉 → 掉一份（正好 5 个钻石），存读档保留 */
    @GameTest(template = EMPTY)
    public static void leyPendingDropsOnce(GameTestHelper helper) {
        LeyProjectorBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        givePending(be, new ItemStack(Items.DIAMOND, 5));
        CompoundTag saved = be.saveWithoutMetadata();
        helper.assertTrue(saved.getList(LeyProjectorBlockEntity.TAG_PENDING, 10).size() == 1, "待放应存盘");
        helper.assertTrue(!be.getUpdateTag().contains(LeyProjectorBlockEntity.TAG_PENDING), "待放不该发给客户端");
        helper.setBlock(CENTER, Blocks.AIR);
        int dropped = diamondsAround(helper, CENTER);
        helper.assertTrue(dropped == 5, "挖掉应正好掉出 5 个钻石（不多不少），实际 " + dropped);
        helper.succeed();
    }

    // ---------------- 世界不动 ----------------

    /**
     * 四周和下面铺石头、铁矿石、放一个装着东西的箱子；模拟领地拒绝所有格子。跑 100 tick：投影照常，
     * 没有任何破坏 / 放置事件（不探测、不代挖），每格方块状态、箱子内容不变，没有掉落物。
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 200)
    public static void leyWorldUntouched(GameTestHelper helper) {
        BlockPos machine = new BlockPos(5, 2, 5);
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 1; x <= 9; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 1; z <= 9; z++) {
                    BlockPos rel = new BlockPos(x, y, z);
                    if (rel.equals(machine)) {
                        continue;
                    }
                    cells.add(rel);
                    if (y == 1 || (y == 2 && Math.abs(x - 5) <= 1 && Math.abs(z - 5) <= 1)) {
                        helper.setBlock(rel, (x + z) % 3 == 0 ? Blocks.IRON_ORE : Blocks.STONE);
                    }
                }
            }
        }
        BlockPos chest = new BlockPos(5, 2, 7);
        helper.setBlock(chest, Blocks.CHEST);
        ((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 7));
        LeyProjectorBlockEntity be = place(helper, machine, UUID.randomUUID(), 0L);
        Map<BlockPos, BlockState> before = new HashMap<>();
        for (BlockPos rel : cells) {
            before.put(rel, helper.getBlockState(rel));
        }
        AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(10, 7, 10))).inflate(1D);
        int itemsBefore = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).size();
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        for (BlockPos rel : cells) {
            claims.deny(rel);
        }
        claims.deny(machine);
        helper.runAfterDelay(100, () -> {
            try {
                helper.assertTrue(be.projectedCount() >= 4, "领地怎么设都该照常投影，实际投影 " + be.projectedCount() + " 格");
                for (BlockPos rel : cells) {
                    helper.assertTrue(helper.getBlockState(rel) == before.get(rel), "方块被改了：" + rel);
                    helper.assertTrue(claims.breaks(rel) == 0 && claims.places(rel) == 0, "不该对 " + rel + " 发破坏 / 放置事件");
                }
                helper.assertTrue(claims.breaks(machine) == 0 && claims.places(machine) == 0, "不该对自己那格发事件");
                ItemStack inChest = ((Container) helper.getBlockEntity(chest)).getItem(0);
                helper.assertTrue(inChest.is(Items.DIAMOND) && inChest.getCount() == 7, "箱子里的东西不该动，实际 " + inChest);
                int itemsAfter = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).size();
                helper.assertTrue(itemsAfter == itemsBefore, "不该生成掉落物，前 " + itemsBefore + " 后 " + itemsAfter);
            } finally {
                claims.close();
            }
            helper.succeed();
        });
    }

    // ---------------- 配方 ----------------

    /** 配方 PFP/SDM/ICI 已加载、产物是投影仪、材料里有魔力挖掘器（按注册名取） */
    @GameTest(template = EMPTY)
    public static void leyRecipeUsesDigger(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Optional<? extends Recipe<?>> recipe = level.getRecipeManager().byKey(EssentialCraft5.id("leyprojector/ley_projector"));
        helper.assertTrue(recipe.isPresent(), "地脉投影仪的配方应已加载");
        ItemStack result = recipe.get().getResultItem(level.registryAccess());
        helper.assertTrue(result.is(ECLeyProjector.LEY_PROJECTOR.get().asItem()), "配方产物应是地脉投影仪，实际 " + result);
        Item digger = itemById("essentialcraft5:magical_digger");
        if (digger != null) {
            boolean usesDigger = false;
            for (Ingredient ingredient : recipe.get().getIngredients()) {
                usesDigger |= ingredient.test(new ItemStack(digger));
            }
            helper.assertTrue(usesDigger, "配方应用魔力挖掘器");
        }
        helper.succeed();
    }
}
