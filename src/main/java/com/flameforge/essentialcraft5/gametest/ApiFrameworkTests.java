package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.command.MRUCUCommands;
import com.flameforge.essentialcraft5.command.PlayerCommands;
import com.flameforge.essentialcraft5.corruption.MRUCorruption;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.item.MRUMoverItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECMRUCU;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 二批框架设施 F6~F8、F11 的 GameTest：元素水晶接口、MRU 抗性、MRU 可见性、/ec5 指令；另有集成时给 MRUCU 移动魔杖加的挪动限制（出生点保护、世界边界）。
 * <p>
 * 这些设施的使用方（ECCrystal、ECGear 等）是二批模块，框架测试不 import 它们：要它们的方块 / 物品时按注册名从 ForgeRegistries 取，
 * 取不到（模块还没接线）就跳过那部分断言，只测框架自己的逻辑。
 * 测试环境造不出能被实体选择器选中的在线玩家，所以 {@code /ec5 player …} 的执行逻辑直接调 {@link PlayerCommands} 的静态方法（参数用模拟玩家），
 * 指令树本身只测解析；{@code /ec5 mrucu …} 不涉及玩家，走真正的指令分发器执行。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ApiFrameworkTests {

    private static final String EMPTY = "empty";
    private static final float EPSILON = 1.0E-5F;

    private ApiFrameworkTests() {
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    /** 按注册名取别的模块的物品，没注册时返回空 */
    private static Optional<Item> optionalItem(String path) {
        ResourceLocation id = EssentialCraft5.id(path);
        return ForgeRegistries.ITEMS.containsKey(id) ? Optional.ofNullable(ForgeRegistries.ITEMS.getValue(id)) : Optional.empty();
    }

    private static CommandSourceStack source(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        return level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atCenterOf(helper.absolutePos(relative)))
                .withPermission(ECCommands.PERMISSION_LEVEL)
                .withSuppressedOutput();
    }

    private static String coords(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    // ---------------------------------------------------------------- F6 元素水晶

    /** 元素编号与原作一致、clampedDelta 夹到 [0,100]；ECCrystal 接线后再查它的方块实体真的实现了接口、mutate 夹到 100 */
    @GameTest(template = EMPTY)
    public static void elementalCrystalContract(GameTestHelper helper) {
        helper.assertTrue(IElementalCrystal.Element.byIndex(0) == IElementalCrystal.Element.FIRE
                        && IElementalCrystal.Element.byIndex(3) == IElementalCrystal.Element.AIR
                        && IElementalCrystal.Element.byIndex(4) == null && IElementalCrystal.Element.byIndex(-1) == null,
                "元素编号应为 0 火、1 水、2 地、3 风，越界为 null");
        helper.assertTrue(IElementalCrystal.Element.WATER.getSerializedName().equals("water"), "元素的序列化名应为小写原作 NBT 键");
        helper.assertTrue(near(IElementalCrystal.clampedDelta(99F, 5F), 1F), "99 + 5 应只加 1");
        helper.assertTrue(near(IElementalCrystal.clampedDelta(1F, -3F), -1F), "1 − 3 应只减 1");
        helper.assertTrue(near(IElementalCrystal.clampedDelta(50F, 2F), 2F), "不越界时应原样加");
        helper.assertTrue(IElementalCrystal.clampedDelta(50F, Float.NaN) == 0F, "NaN 应当 0");

        ResourceLocation id = EssentialCraft5.id("elemental_crystal");
        if (ForgeRegistries.BLOCKS.containsKey(id)) {
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            // 只通知客户端、不做邻居更新：这里只测接口，不管支撑方块
            helper.getLevel().setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockEntity blockEntity = helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(blockEntity instanceof IElementalCrystal, "elemental_crystal 的方块实体应实现 IElementalCrystal，实际 " + blockEntity);
            IElementalCrystal crystal = IElementalCrystal.at(helper.getLevel(), pos);
            helper.assertTrue(crystal == blockEntity, "IElementalCrystal.at 应取到同一个方块实体");
            float before = crystal.element(IElementalCrystal.Element.FIRE);
            float added = crystal.mutate(IElementalCrystal.Element.FIRE, 1000F);
            helper.assertTrue(near(crystal.element(IElementalCrystal.Element.FIRE), 100F) && near(added, 100F - before),
                    "突变应夹到 100、返回实际变化量，实际值 " + crystal.element(IElementalCrystal.Element.FIRE) + "、返回 " + added);
            crystal.mutate(IElementalCrystal.Element.FIRE, -1000F);
            helper.assertTrue(near(crystal.element(IElementalCrystal.Element.FIRE), 0F), "突变应夹到 0");
            helper.assertTrue(crystal.size() >= 0F && crystal.size() <= IElementalCrystal.MAX_SIZE + 0.01F, "大小应在 0~100，实际 " + crystal.size());
            helper.assertTrue(IElementalCrystal.adjacent(helper.getLevel(), pos.east()) == crystal, "adjacent 应找到西边相邻的水晶");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- F7 MRU 抗性

    /** 公式 max(0, 1 − 盔甲和/4 − 饰品和)、提示百分数格式、聚集体矩阵损伤公式 */
    @GameTest(template = EMPTY)
    public static void mruResistanceFormulas(GameTestHelper helper) {
        helper.assertTrue(near(MRUResistance.vulnerability(0F, 0F), 1F), "无防护应为 1");
        helper.assertTrue(near(MRUResistance.vulnerability(1F, 0F), 0.75F), "四件各 0.25 的盔甲应为 0.75");
        helper.assertTrue(near(MRUResistance.vulnerability(1F, 0.5F), 0.25F), "盔甲 1 + 饰品 0.5 应为 0.25");
        helper.assertTrue(MRUResistance.vulnerability(4F, 1F) == 0F, "抗性超过 1 应夹到 0");
        helper.assertTrue(near(MRUResistance.vulnerability(0F, -0.5F), 1.5F), "负抗性应加重");
        helper.assertTrue(MRUResistance.vulnerability(Float.NaN, 0F) == 1F, "NaN 应当无防护");

        helper.assertTrue(MRUResistance.formatPercent(0.25F).equals("+25"), "0.25 应显示 +25，实际 " + MRUResistance.formatPercent(0.25F));
        helper.assertTrue(MRUResistance.formatPercent(0.125F).equals("+12.5"), "0.125 应显示 +12.5，实际 " + MRUResistance.formatPercent(0.125F));
        helper.assertTrue(MRUResistance.formatPercent(-0.1F).equals("-10"), "-0.1 应显示 -10，实际 " + MRUResistance.formatPercent(-0.1F));

        // 4 × ⌊MRU/10000⌋ / (10 − 10 × 易伤)，易伤封顶 0.99（batch2 §3.9）
        int[][] cases = {{1, 100, 40}, {2, 100, 80}, {1, 75, 1}, {2, 75, 3}, {2, 0, 0}, {0, 100, 0}, {1, 995, 40}};
        for (int[] c : cases) {
            float vulnerability = c[1] / (c[1] > 100 ? 1000F : 100F);
            int damage = MRUPresenceEntity.matrixDamage(c[0], vulnerability);
            helper.assertTrue(damage == c[2], String.format(Locale.ROOT, "%d 级、易伤 %s 应得 %d 点矩阵损伤，实际 %d", c[0], vulnerability, c[2], damage));
        }
        helper.assertTrue(MRUPresenceEntity.matrixDamage(2, Float.NaN) == 0, "易伤 NaN 不应加矩阵损伤");
        helper.succeed();
    }

    /** 没穿带抗性的装备时三种易伤都是 1；原版钻石套不算；MRU 腐化效果读的是同一个值 */
    @GameTest(template = EMPTY)
    public static void mruResistanceWithoutGear(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        for (MRUResistance.Type type : MRUResistance.Type.values()) {
            helper.assertTrue(MRUResistance.vulnerability(player, type) == 1F, "空身时 " + type + " 易伤应为 1");
        }
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        helper.assertTrue(MRUResistance.resistance(new ItemStack(Items.DIAMOND_HELMET), MRUResistance.Type.MRU_CORRUPTION) == 0F,
                "没实现 IMRUResistanceItem 的物品抗性应为 0");
        helper.assertTrue(MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION) == 1F, "原版钻石套不应有 MRU 抗性");
        helper.assertTrue(MRUCorruption.vulnerability(player) == 1F, "MRU 腐化效果应读 MRUResistance 的 MRU 腐化易伤");
        helper.succeed();
    }

    /** ECGear 接线后：穿上魔力防护四件，易伤等于按各件自报的抗性套公式算出的值（全套 MRU 腐化应为 0.75，由 ECGear 自己的测试核对具体数值） */
    @GameTest(template = EMPTY)
    public static void mruResistanceReadsArmorSlots(GameTestHelper helper) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        String[] ids = {"mhs_helmet", "mhs_chestplate", "mhs_leggings", "mhs_boots"};
        Player player = helper.makeMockPlayer();
        ItemStack[] stacks = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            Optional<Item> item = optionalItem(ids[i]);
            if (item.isEmpty()) {
                // ECGear 还没接线
                helper.succeed();
                return;
            }
            stacks[i] = new ItemStack(item.get());
            player.setItemSlot(slots[i], stacks[i]);
        }
        for (MRUResistance.Type type : MRUResistance.Type.values()) {
            float sum = 0F;
            for (ItemStack stack : stacks) {
                sum += MRUResistance.resistance(stack, type);
            }
            float expected = MRUResistance.vulnerability(sum, 0F);
            float actual = MRUResistance.vulnerability(player, type);
            helper.assertTrue(near(actual, expected), type + " 易伤应为 " + expected + "，实际 " + actual);
            helper.assertTrue(sum > 0F, "魔力防护套装应对 " + type + " 有抗性");
        }
        // 从盔甲槽拿下来放进背包就不算了
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.getInventory().setItem(9, stacks[0]);
        float withoutHelmet = MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION);
        float fullSet = MRUResistance.vulnerability(MRUResistance.resistance(stacks[0], MRUResistance.Type.MRU_CORRUPTION)
                + MRUResistance.resistance(stacks[1], MRUResistance.Type.MRU_CORRUPTION)
                + MRUResistance.resistance(stacks[2], MRUResistance.Type.MRU_CORRUPTION)
                + MRUResistance.resistance(stacks[3], MRUResistance.Type.MRU_CORRUPTION), 0F);
        helper.assertTrue(withoutHelmet > fullSet, "头盔放进背包后易伤应变大");
        helper.succeed();
    }

    // ---------------------------------------------------------------- F8 MRU 可见性

    /** 空手、一阶移动魔杖看不见；标签里的物品在主手 / 副手 / 头盔槽能看见，放在背包里不行 */
    @GameTest(template = EMPTY)
    public static void mruVisionUsesTag(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        helper.assertFalse(MRUVision.canSee(player), "空手不应看见 MRU");
        ItemStack mover = new ItemStack(ECMRUCU.MRU_MOVER.get());
        helper.assertFalse(mover.is(ECTags.MRU_VISION), "一阶 MRUCU 移动魔杖原作不在可见列表里，不应在 mru_vision 标签里");
        player.setItemSlot(EquipmentSlot.MAINHAND, mover);
        helper.assertFalse(MRUVision.canSee(player), "拿着一阶移动魔杖不应看见 MRU");
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

        List<Item> members = ForgeRegistries.ITEMS.tags().getTag(ECTags.MRU_VISION).stream().toList();
        if (!members.isEmpty()) {
            ItemStack stack = new ItemStack(members.get(0));
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD}) {
                player.setItemSlot(slot, stack.copy());
                helper.assertTrue(MRUVision.canSee(player), members.get(0) + " 在 " + slot + " 时应看见 MRU");
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
            player.getInventory().setItem(9, stack.copy());
            helper.assertFalse(MRUVision.canSee(player), "放在背包里不应看见 MRU");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- F11 /ec5

    /** 每种写法都能完整解析；权限 1 的来源看不到 /ec5 */
    @GameTest(template = EMPTY)
    public static void ec5CommandTreeParses(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack source = source(helper, new BlockPos(2, 1, 2));
        String[] commands = {
                "ec5 mrucu create ~ ~ ~ 5000 1.5",
                "ec5 mrucu set_mru ~ ~ ~ 20000",
                "ec5 mrucu set_balance 0 64 0 0.0",
                "ec5 player @s ubmru get",
                "ec5 player @a ubmru set 100",
                "ec5 player @s ubmru add -50",
                "ec5 player @s matrix_damage get",
                "ec5 player @s matrix_damage set 0",
                "ec5 player @s matrix_type get",
                "ec5 player @s matrix_type set neutral",
                "ec5 player @s matrix_type set shade"};
        for (String command : commands) {
            ParseResults<CommandSourceStack> parse = dispatcher.parse(command, source);
            CommandSyntaxException error = Commands.getParseException(parse);
            helper.assertTrue(error == null && !parse.getReader().canRead() && parse.getContext().getCommand() != null,
                    "应能完整解析并执行：/" + command + (error == null ? "" : "（" + error.getMessage() + "）"));
        }
        String[] invalid = {"ec5 mrucu create ~ ~ ~ 20001 1", "ec5 mrucu create ~ ~ ~ 100 2.5", "ec5 player @s ubmru set -1",
                "ec5 player @s matrix_type set purple"};
        for (String command : invalid) {
            ParseResults<CommandSourceStack> parse = dispatcher.parse(command, source);
            boolean complete = Commands.getParseException(parse) == null && !parse.getReader().canRead() && parse.getContext().getCommand() != null;
            helper.assertFalse(complete, "不应接受：/" + command);
        }
        ParseResults<CommandSourceStack> lowPermission = dispatcher.parse("ec5 player @s ubmru get", source.withPermission(1));
        helper.assertTrue(lowPermission.getReader().canRead() || Commands.getParseException(lowPermission) != null,
                "权限 1 不应能用 /ec5");
        helper.succeed();
    }

    /** mrucu create 放在方块中心；set_mru / set_balance 改最近一团；16 格内没有时报错 */
    @GameTest(template = EMPTY)
    public static void ec5MrucuCommands(GameTestHelper helper) throws CommandSyntaxException {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        BlockPos relative = new BlockPos(2, 2, 2);
        BlockPos pos = helper.absolutePos(relative);
        CommandSourceStack source = source(helper, relative);

        int result = dispatcher.execute("ec5 mrucu create " + coords(pos) + " 5000 1.5", source);
        helper.assertTrue(result == 1, "create 应返回 1，实际 " + result);
        List<MRUPresenceEntity> found = helper.getLevel().getEntitiesOfClass(MRUPresenceEntity.class,
                new AABB(pos).inflate(0.01D), Entity::isAlive);
        helper.assertTrue(found.size() == 1, "应在该方块里新建 1 团聚集体，实际 " + found.size());
        MRUPresenceEntity presence = found.get(0);
        try {
            helper.assertTrue(presence.position().distanceTo(Vec3.atCenterOf(pos)) < 1.0E-6D, "聚集体应在方块中心，实际 " + presence.position());
            helper.assertTrue(presence.getMRU() == 5000 && near(presence.getBalance(), 1.5F),
                    "应为 5000 MRU、平衡值 1.5，实际 " + presence.getMRU() + " / " + presence.getBalance());
            helper.assertTrue(presence.isAlwaysStay() && !presence.getFlag(), "指令放的聚集体应 alwaysStay=true、flag=false（原作默认值）");

            dispatcher.execute("ec5 mrucu set_mru " + coords(pos) + " 12345", source);
            helper.assertTrue(presence.getMRU() == 12345, "set_mru 后应为 12345，实际 " + presence.getMRU());
            dispatcher.execute("ec5 mrucu set_balance " + coords(pos) + " 0.25", source);
            helper.assertTrue(near(presence.getBalance(), 0.25F), "set_balance 后应为 0.25，实际 " + presence.getBalance());

            // 静态方法和指令是同一套逻辑；旁边一格的坐标也找到这一团（别的测试的聚集体离得更远）
            MRUPresenceEntity viaMethod = MRUCUCommands.setMRU(source, pos.offset(1, 0, 0), 0);
            helper.assertTrue(viaMethod == presence && presence.getMRU() == 0, "1 格外应找到同一团并设成 0");
            helper.assertTrue(presence.isAlive(), "alwaysStay 的聚集体 MRU 归零也应保留");

            BlockPos far = pos.above(300);
            boolean threw = false;
            try {
                dispatcher.execute("ec5 mrucu set_mru " + coords(far) + " 10", source);
            } catch (CommandSyntaxException e) {
                threw = true;
            }
            helper.assertTrue(threw, "16 格内没有聚集体时应报错");
        } finally {
            presence.discard();
        }
        helper.succeed();
    }

    /** player 子命令的逻辑：UBMRU set / add 夹到 [0, int 上限]、矩阵损伤、矩阵类型，改完标脏（下个同步周期发给客户端）；多个目标返回人数 */
    @GameTest(template = EMPTY)
    public static void ec5PlayerCommands(GameTestHelper helper) throws CommandSyntaxException {
        CommandSourceStack source = source(helper, new BlockPos(2, 1, 2));
        Player player = helper.makeMockPlayer();
        Player other = helper.makeMockPlayer();
        IPlayerData data = PlayerDataEvents.get(player);
        IPlayerData otherData = PlayerDataEvents.get(other);
        helper.assertTrue(data != null && otherData != null, "模拟玩家应挂着玩家数据");
        List<Player> one = List.of(player);

        data.clearDirty();
        helper.assertTrue(PlayerCommands.setUBMRU(source, one, 1234) == 1 && data.getUBMRU() == 1234, "set 后应为 1234，实际 " + data.getUBMRU());
        helper.assertTrue(data.isDirty(), "改了数据应标脏，等 PlayerDataEvents 同步");
        helper.assertTrue(PlayerCommands.getUBMRU(source, one) == 1234, "只有一个目标时 get 应返回数值");
        PlayerCommands.addUBMRU(source, one, -5000);
        helper.assertTrue(data.getUBMRU() == 0, "减到负数应夹到 0，实际 " + data.getUBMRU());
        PlayerCommands.setUBMRU(source, one, Integer.MAX_VALUE - 10);
        PlayerCommands.addUBMRU(source, one, 100);
        helper.assertTrue(data.getUBMRU() == Integer.MAX_VALUE, "加法应夹到 int 上限，实际 " + data.getUBMRU());

        PlayerCommands.setMatrixDamage(source, one, 77);
        helper.assertTrue(data.getMatrixDamage() == 77 && PlayerCommands.getMatrixDamage(source, one) == 77, "矩阵损伤应为 77");

        int shade = PlayerCommands.MATRIX_TYPES.indexOf("shade");
        helper.assertTrue(shade == 4 && PlayerCommands.MATRIX_TYPES.indexOf("neutral") == 0, "矩阵类型序号应与 IPlayerData 一致（0 中性 … 4 暗影）");
        PlayerCommands.setMatrixType(source, one, shade);
        helper.assertTrue(data.getMatrixType() == 4 && PlayerCommands.getMatrixType(source, one) == 4, "矩阵类型应为暗影（4）");

        int count = PlayerCommands.setUBMRU(source, List.of(player, other), 5);
        helper.assertTrue(count == 2 && data.getUBMRU() == 5 && otherData.getUBMRU() == 5, "两个目标都应设成 5，返回 2");
        helper.assertTrue(PlayerCommands.getUBMRU(source, List.of(player, other)) == 2, "多个目标时 get 应返回人数");
        helper.succeed();
    }

    /**
     * MRUCU 移动魔杖的挪动限制（{@link MRUMoverItem#canMoveTo}，一阶、二阶共用）。
     * GameTest 服没有出生点保护（MinecraftServer.isUnderSpawnProtection 恒为 false），所以这里测世界边界、非玩家不拦，
     * 以及「本来就在保护区里时只放行不靠近出生点的一步」的距离判断（{@link MRUMoverItem#notCloserToSpawn}）。
     */
    @GameTest(template = EMPTY)
    public static void mruMoverMoveRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer();
        Vec3 here = helper.absoluteVec(new Vec3(2.5D, 1.5D, 2.5D));
        helper.assertTrue(MRUMoverItem.canMoveTo(level, player, here, here.add(0.1D, 0D, 0D)), "没有出生点保护时，边界内应能挪");

        double maxX = level.getWorldBorder().getMaxX();
        Vec3 edge = new Vec3(maxX - 0.05D, here.y, here.z);
        Vec3 beyond = new Vec3(maxX + 0.05D, here.y, here.z);
        helper.assertTrue(!MRUMoverItem.canMoveTo(level, player, edge, beyond), "挪出世界边界应被拦");
        helper.assertTrue(MRUMoverItem.canMoveTo(level, player, beyond, edge), "边界外的挪回边界内应放行");
        LivingEntity stand = EntityType.ARMOR_STAND.create(level);
        helper.assertTrue(stand != null && MRUMoverItem.canMoveTo(level, stand, edge, beyond), "不是玩家在用时不拦");

        // 出生点 (0, 64, 0)：距离是 x、z 两轴离方块中心 (0.5, 0.5) 的距离取大的，不看高度
        BlockPos spawn = new BlockPos(0, 64, 0);
        helper.assertTrue(MRUMoverItem.notCloserToSpawn(spawn, new Vec3(5.5D, 70D, 0.5D), new Vec3(5.6D, 70D, 0.5D)), "往外挪应放行");
        helper.assertTrue(!MRUMoverItem.notCloserToSpawn(spawn, new Vec3(5.5D, 70D, 0.5D), new Vec3(5.4D, 70D, 0.5D)), "往出生点挪应被拦");
        helper.assertTrue(!MRUMoverItem.notCloserToSpawn(spawn, new Vec3(5.5D, 70D, 5.5D), new Vec3(5.45D, 70D, 5.45D)), "斜着往出生点挪应被拦");
        helper.assertTrue(MRUMoverItem.notCloserToSpawn(spawn, new Vec3(5.5D, 70D, 2.5D), new Vec3(5.5D, 70D, 2.6D)), "距离不变的横向挪动应放行");
        helper.assertTrue(MRUMoverItem.notCloserToSpawn(spawn, new Vec3(5.5D, 70D, 0.5D), new Vec3(5.5D, 60D, 0.5D)), "上下挪不算靠近");
        helper.assertTrue(MRUMoverItem.notCloserToSpawn(spawn, new Vec3(0.5D, 64D, 0.5D), new Vec3(0.4D, 64D, 0.5D)), "正中心的往哪挪都算往外");
        helper.succeed();
    }
}
