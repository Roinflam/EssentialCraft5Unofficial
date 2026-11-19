package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.item.BuilderActions;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMaterials;
import com.flameforge.essentialcraft5.item.BuilderMix;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderModules;
import com.flameforge.essentialcraft5.item.BuilderSalvage;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 创世神之杖在真游戏里实测用的开发入口（只在开发环境起作用，正式版 jar 里这个类什么都不做）：随身容器取料、排队、被拒退料这些
 * GameTest 里只能用假容器测的情形，换成真装着背包类模组的单人游戏来测。没有指令，测试工具用 {@code setstatic} 往 {@link #request} 写一行，
 * 内置服务端下一刻读走（几条用分号隔开），结果写进 {@link #result} 和日志「[魔杖实测]」。玩家取在线列表里的第一个。
 * 客户端那一半（滚轮、准星下提示的排版）在 {@link BuilderFieldTestClient}。
 * <pre>
 * reset                          生存模式、清空背包、魔杖相关的测试覆盖值全部置回、取消下面的拒放范围和替身
 * wand &lt;points|plane|line&gt; &lt;宽&gt; &lt;长&gt;   第 0 格放一根填充魔杖（MRU 满、选定圆石），选中第 0 格
 * put &lt;格&gt; &lt;物品 id&gt; [数量]          背包第几格（0~35；36~39 盔甲脚→头；40 副手）直接放一叠
 * fill &lt;格&gt; &lt;物品 id&gt; &lt;数量&gt;          往这一格那个容器物品的物品处理器里塞（走它自己的插入规则），回报塞进去多少
 * nest &lt;外层格&gt; &lt;里层格&gt;             把里层那格的东西整个塞进外层容器，回报成没成
 * bag &lt;格&gt;                       这一格容器里的全部东西（里面再有容器的，括号里列它那一层）
 * count                          身上 / 随身容器里的圆石（只读，和魔杖数的同一份）、施工状态、上一单结果
 * blocks x1 y1 z1 x2 y2 z2       这块范围里有几格圆石
 * start [confirm]                对脚下开工（confirm = 照物品右键那样要不要二次确认）
 * undo | stop                    撤销上一次 / 停下
 * jobs &lt;n|off&gt;  rate &lt;n|off&gt;  pause &lt;秒|off&gt;   全服同时施工名额 / 每单每刻格数 / 用完暂停秒数的测试覆盖值
 * deny x1 y1 z1 x2 y2 z2 | deny off   这块范围里的放置事件一律取消（破坏事件放行，等于领地只拒放置）
 * phantom &lt;dx&gt; &lt;dz&gt; &lt;边长&gt; | phantom off   在玩家旁边 (dx, dz) 处放一个不进世界的替身玩家，开一单铺面（占全服名额，排队用）
 * 下面几条是录演示用的布景，只摆好魔杖和身上的东西，开工、调尺寸、开面板仍由测试工具按真实输入去点：
 * kit &lt;paver|builder|ascended&gt; &lt;形状&gt; &lt;宽&gt; &lt;长&gt; &lt;方块 id&gt; [格]   快捷栏那一格放一根这一档的填充魔杖（MRU 满）并选中
 * module &lt;种类,种类…&gt;            给手里的魔杖装模块（range / haste / efficiency / reach / mix / salvage）
 * mix &lt;方块 id=权重,…&gt;            手里魔杖的混铺表
 * discard &lt;物品 id,…&gt;             手里魔杖的丢弃清单
 * mode &lt;fill|clear|clear_matching|clear_others|replace_all&gt;   换模式（同面板换模式那一步）
 * fly [off]                      能飞并飞起来
 * 下面这条看创世神之杖的飞行特效（客户端画面，统计和画法开关见 {@link BuilderFieldTestClient} 的 flights / flightstyle / shaderbody）：
 * 都换成创造模式、手里换一根新魔杖、对准星点中的地方开工（不要二次确认），镜头先摆好再发；之后再发一次换下一段
 * flightdemo plane               铺面填充 9 × 9（石砖）
 * flightdemo wall                竖墙 7 × 5（橡木木板）
 * flightdemo mix                 三阶 + 混铺模块，铺面 9 × 9，石砖 / 苔石砖 / 裂纹石砖 2 : 1 : 1
 * flightdemo undo                撤销上一单（方块从格子飞回杖尖）
 * flightdemo stop [刻]           铺面 9 × 9，开工后过这么多刻（默认 20）停下：还在飞、还没放的格子碎掉
 * </pre>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class BuilderFieldTest {

    public static volatile String request = "";
    public static volatile String result = "";

    @Nullable
    private static BoundingBox denyBox;
    @Nullable
    private static ServerPlayer phantom;
    /** flightdemo stop：还有几刻停下（0 = 没排） */
    private static int stopCountdown;

    private BuilderFieldTest() {
    }

    @SubscribeEvent
    public static void onBuilderFieldTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FMLEnvironment.production) {
            return;
        }
        if (stopCountdown > 0 && --stopCountdown == 0 && !event.getServer().getPlayerList().getPlayers().isEmpty()) {
            ServerPlayer first = event.getServer().getPlayerList().getPlayers().get(0);
            EssentialCraft5.LOGGER.info("[魔杖实测] flightdemo 停下 => {}", BuilderJobs.stop(first));
        }
        String line = request;
        if (line == null || line.isBlank()) {
            return;
        }
        request = "";
        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) {
            result = "error 没有玩家";
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        StringBuilder out = new StringBuilder();
        for (String command : line.split(";")) {
            String c = command.trim();
            if (c.isEmpty()) {
                continue;
            }
            String r;
            try {
                r = run(player, c);
            } catch (RuntimeException e) {
                r = "error " + e;
                EssentialCraft5.LOGGER.warn("[魔杖实测] 指令出错：{}", c, e);
            }
            EssentialCraft5.LOGGER.info("[魔杖实测] {} => {}", c, r);
            out.append(out.length() == 0 ? "" : " | ").append(r);
        }
        result = out.toString();
    }

    /** 拒放范围：只取消放置事件（含多格放置），破坏事件照常，所以探测放行、真放被拒 */
    @SubscribeEvent
    public static void onBuilderFieldPlace(BlockEvent.EntityPlaceEvent event) {
        BoundingBox box = denyBox;
        if (box != null && box.isInside(event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static String run(ServerPlayer player, String command) {
        String[] a = command.split(" +");
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reset" -> {
                if (phantom != null) {
                    BuilderJobs.stop(phantom);
                    phantom = null;
                }
                BuilderJobs.stop(player);
                ECBuilder.Config.Test.reset();
                ECBuilder.Config.setTestOverrides(null, null, null);
                denyBox = null;
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.inventoryMenu.broadcastChanges();
                return "reset";
            }
            case "wand" -> {
                BuilderShape shape = BuilderShape.valueOf(a[1].toUpperCase(Locale.ROOT));
                ItemStack wand = MRUStaffItem.full(ECBuilder.MAGICAL_BUILDER_FILL.get());
                BuilderSelection.setStoredBlock(wand, ForgeRegistries.BLOCKS.getKey(Blocks.COBBLESTONE));
                BuilderSelection.setShape(wand, shape);
                if (shape.resizable()) {
                    BuilderSelection.setSize(wand, shape, Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                }
                player.getInventory().selected = 0;
                player.getInventory().setItem(0, wand);
                player.inventoryMenu.broadcastChanges();
                return "wand " + shape;
            }
            case "put" -> {
                int slot = Integer.parseInt(a[1]);
                Item item = item(a[2]);
                player.getInventory().setItem(slot, new ItemStack(item, a.length > 3 ? Integer.parseInt(a[3]) : 1));
                player.inventoryMenu.broadcastChanges();
                return "put " + slot + " " + player.getInventory().getItem(slot);
            }
            case "fill" -> {
                IItemHandler handler = handler(player, Integer.parseInt(a[1]));
                ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, new ItemStack(item(a[2]), Integer.parseInt(a[3])), false);
                player.inventoryMenu.broadcastChanges();
                return "fill 塞进 " + (Integer.parseInt(a[3]) - rest.getCount()) + " 剩 " + rest.getCount();
            }
            case "nest" -> {
                IItemHandler outer = handler(player, Integer.parseInt(a[1]));
                int innerSlot = Integer.parseInt(a[2]);
                ItemStack inner = player.getInventory().getItem(innerSlot);
                ItemStack rest = ItemHandlerHelper.insertItemStacked(outer, inner.copy(), false);
                if (rest.getCount() != inner.getCount()) {
                    player.getInventory().setItem(innerSlot, rest);
                }
                player.inventoryMenu.broadcastChanges();
                return "nest " + (rest.isEmpty() ? "塞进去了" : "没塞进去（处理器拒收）");
            }
            case "bag" -> {
                return "bag " + describe(handler(player, Integer.parseInt(a[1])), true);
            }
            case "count" -> {
                long[] counted = BuilderMaterials.count(player, Items.COBBLESTONE, player.getMainHandItem());
                int loose = 0;
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.is(Items.COBBLESTONE)) {
                        loose += stack.getCount();
                    }
                }
                UUID id = player.getUUID();
                return "count 背包圆石 " + loose + " 魔杖数 " + Arrays.toString(counted) + " 在做 " + BuilderJobs.isRunning(id)
                        + " 快照 " + BuilderJobs.snapshot(id, player.serverLevel().getGameTime()) + " 上一单 " + BuilderJobs.lastResult(id);
            }
            case "blocks" -> {
                BlockPos from = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                BlockPos to = new BlockPos(Integer.parseInt(a[4]), Integer.parseInt(a[5]), Integer.parseInt(a[6]));
                int n = 0;
                for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                    if (player.level().getBlockState(pos).is(Blocks.COBBLESTONE)) {
                        n++;
                    }
                }
                return "blocks " + n;
            }
            case "start" -> {
                return "start " + BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, a.length > 1 && a[1].equals("confirm"));
            }
            case "undo" -> {
                return "undo " + BuilderJobs.undo(player);
            }
            case "stop" -> {
                return "stop " + BuilderJobs.stop(player);
            }
            case "jobs" -> {
                ECBuilder.Config.Test.maxJobsTotal = number(a[1]);
                return "jobs " + ECBuilder.Config.maxJobsTotal();
            }
            case "rate" -> {
                ECBuilder.Config.setTestOverrides(null, number(a[1]), null);
                return "rate " + ECBuilder.Config.cellsPerTick();
            }
            case "pause" -> {
                ECBuilder.Config.Test.materialPauseSeconds = number(a[1]);
                return "pause " + ECBuilder.Config.materialPauseSeconds();
            }
            case "deny" -> {
                if (a[1].equals("off")) {
                    denyBox = null;
                    return "deny off";
                }
                BlockPos from = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                BlockPos to = new BlockPos(Integer.parseInt(a[4]), Integer.parseInt(a[5]), Integer.parseInt(a[6]));
                denyBox = BoundingBox.fromCorners(from, to);
                return "deny " + denyBox;
            }
            case "phantom" -> {
                if (a[1].equals("off")) {
                    if (phantom != null) {
                        BuilderJobs.stop(phantom);
                        phantom = null;
                    }
                    return "phantom off";
                }
                return "phantom " + startPhantom(player, Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            }
            case "kit" -> {
                return "kit " + kit(player, a);
            }
            case "module" -> {
                ItemStack wand = player.getMainHandItem();
                StringBuilder b = new StringBuilder("module");
                for (String name : a[1].split(",")) {
                    ItemStack next = BuilderModules.install(wand, BuilderModules.Kind.valueOf(name.toUpperCase(Locale.ROOT)));
                    b.append(' ').append(name).append(next.isEmpty() ? " 没装上" : " 装上");
                    if (!next.isEmpty()) {
                        wand = next;
                    }
                }
                player.getInventory().setItem(player.getInventory().selected, wand);
                player.inventoryMenu.broadcastChanges();
                return b.toString();
            }
            case "mix" -> {
                List<BuilderMix.Entry> entries = new ArrayList<>();
                for (String part : a[1].split(",")) {
                    String[] kv = part.split("=");
                    Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(kv[0]));
                    entries.add(new BuilderMix.Entry(block, Integer.parseInt(kv[1])));
                }
                BuilderMix.write(player.getMainHandItem(), entries, 7);
                player.inventoryMenu.broadcastChanges();
                return "mix " + entries.size();
            }
            case "discard" -> {
                List<Item> items = new ArrayList<>();
                for (String id : a[1].split(",")) {
                    items.add(item(id));
                }
                BuilderSalvage.writeDiscard(player.getMainHandItem(), items);
                player.inventoryMenu.broadcastChanges();
                return "discard " + items.size();
            }
            case "mode" -> {
                return "mode " + BuilderActions.setMode(player, InteractionHand.MAIN_HAND,
                        BuilderMode.valueOf(a[1].toUpperCase(Locale.ROOT)).ordinal());
            }
            case "flightdemo" -> {
                return "flightdemo " + flightDemo(player, a);
            }
            case "fly" -> {
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = a.length < 2 || !a[1].equals("off");
                player.onUpdateAbilities();
                return "fly " + player.getAbilities().flying;
            }
            default -> {
                return "error 不认识的指令 " + a[0];
            }
        }
    }

    /**
     * kit &lt;paver|builder|ascended&gt; &lt;形状&gt; &lt;宽&gt; &lt;长&gt; &lt;方块 id&gt; [格]：往快捷栏那一格（默认 0）放一根这一档的填充魔杖并选中，
     * MRU 满、选定那种方块、形状和尺寸照给的设好；三阶照魔力工作台升格那样写上档位
     */
    private static String kit(ServerPlayer player, String[] a) {
        Item item = switch (a[1].toLowerCase(Locale.ROOT)) {
            case "paver" -> ECBuilder.PAVING_WAND.get();
            case "builder", "ascended" -> ECBuilder.MAGICAL_BUILDER_FILL.get();
            default -> throw new IllegalArgumentException("没有这一档 " + a[1]);
        };
        ItemStack wand = new ItemStack(item);
        if (a[1].equalsIgnoreCase("ascended")) {
            wand.getOrCreateTag().putInt(BuilderTier.TAG_TIER, BuilderTier.ASCENDED.level());
        }
        wand.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(handler.getMaxMRU()));
        BuilderShape shape = BuilderShape.valueOf(a[2].toUpperCase(Locale.ROOT));
        BuilderSelection.setStoredBlock(wand, ResourceLocation.tryParse(a[5]));
        BuilderSelection.setShape(wand, shape);
        if (shape.resizable()) {
            BuilderSelection.setSize(wand, shape, Integer.parseInt(a[3]), Integer.parseInt(a[4]));
        }
        int slot = a.length > 6 ? Integer.parseInt(a[6]) : 0;
        player.getInventory().selected = slot;
        player.getInventory().setItem(slot, wand);
        player.inventoryMenu.broadcastChanges();
        return BuilderTier.of(wand) + " " + shape + " 格 " + slot;
    }

    /** flightdemo：几段固定的施工，给看飞行特效用（见类注释） */
    private static String flightDemo(ServerPlayer player, String[] a) {
        String scene = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "plane";
        if (scene.equals("undo")) {
            return "undo " + BuilderJobs.undo(player);
        }
        player.setGameMode(GameType.CREATIVE);
        BuilderJobs.stop(player);
        switch (scene) {
            case "plane", "stop" -> run(player, "kit builder plane 9 9 minecraft:stone_bricks");
            case "wall" -> run(player, "kit builder wall 7 5 minecraft:oak_planks");
            case "mix" -> {
                run(player, "kit ascended plane 9 9 minecraft:stone_bricks");
                run(player, "module mix");
                run(player, "mix minecraft:stone_bricks=2,minecraft:mossy_stone_bricks=1,minecraft:cracked_stone_bricks=1");
            }
            default -> throw new IllegalArgumentException("没有这一段 " + scene);
        }
        String started = run(player, "start");
        if (scene.equals("stop")) {
            stopCountdown = a.length > 2 ? Math.max(1, Integer.parseInt(a[2])) : 20;
        }
        return scene + " " + started;
    }

    /** 不进世界的替身玩家（同 GameTest 的做法：挂一个没有网络通道的连接），拿一根铺面魔杖、带足圆石，对脚下开一单 */
    private static BuilderJobs.StartResult startPhantom(ServerPlayer player, int dx, int dz, int side) {
        ServerLevel level = player.serverLevel();
        ServerPlayer stand = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "ec5-field-phantom"));
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), stand);
        // 替身从不走刻，onGround 一直是 false，脚下锚点取 floor(y) − 1；抬高一格，铺的才是玩家脚下地面上面那一层（不抬就落在地面那层里，全是占着的格子，一下就做完、占不住名额）
        stand.moveTo(player.getX() + dx, player.getY() + 1, player.getZ() + dz, 0F, 90F);
        ItemStack wand = MRUStaffItem.full(ECBuilder.MAGICAL_BUILDER_FILL.get());
        BuilderSelection.setStoredBlock(wand, ForgeRegistries.BLOCKS.getKey(Blocks.COBBLESTONE));
        BuilderSelection.setShape(wand, BuilderShape.PLANE);
        BuilderSelection.setSize(wand, BuilderShape.PLANE, side, side);
        stand.setItemInHand(InteractionHand.MAIN_HAND, wand);
        for (int i = 1; i < 36 && i * 64 <= side * side + 64; i++) {
            stand.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        phantom = stand;
        return BuilderJobs.start(stand, InteractionHand.MAIN_HAND);
    }

    private static IItemHandler handler(ServerPlayer player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        return stack.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve()
                .orElseThrow(() -> new IllegalStateException("第 " + slot + " 格不是容器物品：" + stack));
    }

    private static String describe(IItemHandler handler, boolean deeper) {
        StringBuilder b = new StringBuilder("格数 ").append(handler.getSlots());
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            b.append(' ').append(i).append(':').append(ForgeRegistries.ITEMS.getKey(stack.getItem())).append('x').append(stack.getCount());
            if (stack.hasTag() && !stack.is(Items.COBBLESTONE)) {
                b.append("(带NBT)");
            }
            if (deeper) {
                stack.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(inner -> b.append(" [").append(describe(inner, false)).append(']'));
            }
        }
        return b.toString();
    }

    private static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        if (item == null || item == Items.AIR) {
            throw new IllegalArgumentException("没有这种物品 " + id);
        }
        return item;
    }

    @Nullable
    private static Integer number(String text) {
        return text.equals("off") ? null : Integer.valueOf(text);
    }
}
