package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.registry.ECWindGear;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 风之工具的「自动变形」（第三批 ECWindGear，batch3.md §3.4、E3 选 A）。
 * <p>
 * 原作五件风之工具（ItemWindPickaxe / Axe / Shovel / Hoe / Sword）是同一把工具的五个形态：
 * <ul>
 *   <li>挖方块时换成挖这个方块合适的形态（原作 ECEventHandler.onBlockSpeedCheck L594~843，在 BreakSpeed 事件里、只在服务端换）；</li>
 *   <li>镐 / 斧 / 锹打中生物后变成剑（原作各类的 hitEntity）；</li>
 *   <li>任何形态右键方块都是锄地（原作 onItemUse 都转给风之锄）。</li>
 * </ul>
 * 移植后的写法：
 * <ul>
 *   <li><b>什么时候换</b>：开始挖方块（{@code PlayerInteractEvent.LeftClickBlock}，动作 START，客户端和服务端各触发一次，见 {@link WindGearEvents}），
 *       两端用同一套规则各换一次，客户端预测的挖掘进度和服务端一致；原作在挖掘速度事件里只换服务端，客户端要等同步、挖掘进度会跳。
 *       这次左键被别的监听器（领地、保护类模组）取消了也照样换（只动玩家自己手里的物品），两端才一致；
 *       服务端因为够不着、区块没加载完而没换、客户端却可能已经换了的，发一个格子包把客户端改回来（{@link #resync}）。
 *       打中生物后（{@code hurtEnemy}，只在服务端）变成剑，由原版的物品栏同步发给客户端；镐 / 斧 / 锹 / 锄打中生物不扣耐久
 *       （原作这四个的 hitEntity 不调父类；原版挖掘工具打人扣 2），剑形态照原版每下扣 1。
 *       创造模式、旁观模式挖方块不换（原作 BreakSpeed 在创造模式不触发）；创造模式打中生物照样变剑（同原作：原版创造模式也调 hurtEnemy）。</li>
 *   <li><b>换成哪个</b>（{@link #bestFormFor}）：五个形态对这个方块的挖掘速度（{@code Item#getDestroySpeed}：镐 / 斧 / 锹 / 锄看原版 mineable 标签，
 *       剑对蜘蛛网 15、对 sword_efficient 1.5）取最快的，同速看能不能掉落；当前形态不比它慢就不换。原作按 1.12 的 harvestTool 和一张材质表选，
 *       1.20 没有这两样，按挖掘速度选结果相同（石头 → 镐、原木 → 斧、泥土 → 锹、蜘蛛网 → 剑），另外树叶、干草块、海绵这类原版划给锄的方块会换成锄。
 *       硬度 ≤ 0 的方块（花草一碰就碎、基岩挖不动）不换。</li>
 *   <li><b>换的时候保留什么</b>（{@link #transform}）：<b>附魔和铁砧累计惩罚按形态分开</b>（{@link #PER_FORM_KEYS}，收起来的形态存在 {@link #FORMS_KEY} 里），
 *       其余一切（耐久、自定义名字、别的模组的数据、Forge 能力）跟着工具走。原作把整个物品栈（含耐久和名字）按形态收起来，
 *       结果五个形态各有一份耐久（换形态时耐久条跳来跳去、修复只修当前形态，任何一个形态用坏了其余四个一起没了）、改的名字换个形态就没了；
 *       这里改成共用一份耐久（改变玩法，见 ECWindGear 类注释）。</li>
 *   <li><b>右键</b>（{@link #useOn}）：先按风之锄锄地（非锄形态靠 {@link #extraAction} 声明自己也能 HOE_TILL，Forge 的 getToolModifiedState 才放行）；
 *       锄不了的方块再做这个形态自己的原版动作（斧剥树皮 / 刮铜 / 去蜡，锹铲土径 / 灭营火）。原作 1.12 的斧锹没有这些动作，1.20 补回来不和锄地冲突。
 *       逻辑都在 useOn 里、返回 CONSUME，Forge 的放置捕获替我们发放置事件，被领地拒绝时整格还原、耐久也退回。</li>
 * </ul>
 * 只动玩家自己手里的物品，不改世界（policy P1 不触发）；每次开始挖方块只做一次常数量的 NBT 复制，
 * 服务端先查够不够得着、区块是不是已经完整加载，都过了才读方块（LeftClickBlock 在服务端先于原版的距离检查触发、坐标是客户端填的，
 * 不先查的话改过的客户端能让服务端主线程同步等远处的区块加载，见 {@link #onStartMining}）。
 */
public final class WindToolForms {

    /** 收起来的形态：{@code tag.<FORMS_KEY>.<形态>} = 那个形态的 {@link #PER_FORM_KEYS} */
    public static final String FORMS_KEY = "essentialcraft5_wind_forms";
    /** 按形态分开存的物品 NBT：附魔、铁砧累计惩罚 */
    public static final List<String> PER_FORM_KEYS = List.of(ItemStack.TAG_ENCH, "RepairCost");
    /** 够不够得着：同原版服务端开始挖方块时的检查（{@code ServerPlayerGameMode.handleBlockBreakAction}：方块触及距离 + 1.5） */
    public static final double REACH_PADDING = 1.5D;

    private WindToolForms() {
    }

    /** 五个形态。顺序也是挖掘速度相同时的优先顺序 */
    public enum Form {
        PICKAXE("pickaxe", () -> ECWindGear.WIND_ELEMENTAL_PICKAXE.get()),
        AXE("axe", () -> ECWindGear.WIND_ELEMENTAL_AXE.get()),
        SHOVEL("shovel", () -> ECWindGear.WIND_ELEMENTAL_SHOVEL.get()),
        HOE("hoe", () -> ECWindGear.WIND_ELEMENTAL_HOE.get()),
        SWORD("sword", () -> ECWindGear.WIND_ELEMENTAL_SWORD.get());

        private final String key;
        private final Supplier<? extends Item> item;

        Form(String key, Supplier<? extends Item> item) {
            this.key = key;
            this.item = item;
        }

        /** NBT 里的键，也是翻译键的后缀 */
        public String key() {
            return key;
        }

        public Item item() {
            return item.get();
        }

        /** 形态名（「镐」「斧」……）的翻译键 */
        public String nameKey() {
            return "tooltip.essentialcraft5.wind_tool.form." + key;
        }

        /** 物品栈是哪个形态；不是风之工具返回 null */
        @Nullable
        public static Form of(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof FormItem formItem ? formItem.windForm() : null;
        }
    }

    /** 五个风之工具类实现它 */
    public interface FormItem {
        Form windForm();
    }

    // ---------------------------------------------------------------- 选形态

    /**
     * 挖这个方块该换成哪个形态；不用换（当前形态已经最合适、方块硬度 ≤ 0、不是风之工具）返回 null。纯函数，两端结果一致。
     */
    @Nullable
    public static Form bestFormFor(ItemStack stack, BlockState state, BlockGetter level, BlockPos pos) {
        Form current = Form.of(stack);
        if (current == null || state.isAir() || state.getDestroySpeed(level, pos) <= 0F) {
            return null;
        }
        Form best = current;
        float bestSpeed = current.item().getDestroySpeed(stack, state);
        boolean bestCorrect = current.item().isCorrectToolForDrops(stack, state);
        for (Form form : Form.values()) {
            if (form == current) {
                continue;
            }
            Item item = form.item();
            float speed = item.getDestroySpeed(stack, state);
            boolean correct = item.isCorrectToolForDrops(stack, state);
            if (speed > bestSpeed || (speed == bestSpeed && correct && !bestCorrect)) {
                best = form;
                bestSpeed = speed;
                bestCorrect = correct;
            }
        }
        return best == current ? null : best;
    }

    // ---------------------------------------------------------------- 换形态

    /**
     * 返回换成 target 形态的新物品栈（原栈不动）；已经是这个形态、不是风之工具时原样返回。
     * 当前形态的附魔和铁砧惩罚收进 {@link #FORMS_KEY}，target 形态以前收起来的取出来；耐久、名字等其余 NBT 和 Forge 能力照抄。
     */
    public static ItemStack transform(ItemStack stack, Form target) {
        Form from = Form.of(stack);
        if (from == null || from == target) {
            return stack;
        }
        ResourceLocation targetId = ForgeRegistries.ITEMS.getKey(target.item());
        if (targetId == null) {
            return stack;
        }
        CompoundTag root = stack.save(new CompoundTag());   // save 里的 tag 已经是副本
        root.putString("id", targetId.toString());
        CompoundTag tag = root.getCompound("tag");
        CompoundTag forms = tag.getCompound(FORMS_KEY);

        CompoundTag own = new CompoundTag();
        for (String key : PER_FORM_KEYS) {
            Tag value = tag.get(key);
            if (value != null) {
                own.put(key, value);
                tag.remove(key);
            }
        }
        if (own.isEmpty()) {
            forms.remove(from.key());
        } else {
            forms.put(from.key(), own);
        }

        CompoundTag restored = forms.getCompound(target.key());
        forms.remove(target.key());
        for (String key : PER_FORM_KEYS) {
            Tag value = restored.get(key);
            if (value != null) {
                tag.put(key, value);
            }
        }

        if (forms.isEmpty()) {
            tag.remove(FORMS_KEY);
        } else {
            tag.put(FORMS_KEY, forms);
        }
        if (tag.isEmpty()) {
            root.remove("tag");
        } else {
            root.put("tag", tag);
        }
        ItemStack next = ItemStack.of(root);
        return next.isEmpty() ? stack : next;
    }

    /** 把玩家主手（快捷栏选中格）换成 next */
    private static void replaceMainHand(Player player, ItemStack next) {
        player.getInventory().setItem(player.getInventory().selected, next);
    }

    /**
     * 开始挖方块（LeftClickBlock 的 START，两端都调；这次事件被别的监听器取消了也照样调、照样换）。
     * <p>
     * 顺序有讲究：先看手里是不是风之工具，再看够不够得着、区块是不是已经完整加载（{@link ECLevels#isFullyLoaded}），都过了才读方块。
     * 服务端的 LeftClickBlock 先于原版的距离检查触发，坐标是客户端随便填的；{@code level.isLoaded} 只看区块票据，区块还在读盘、生成时就已经是 true，
     * 这时读方块会让服务端主线程同步等它加载完，改过的客户端对视距边缘狂发这种包就能拖垮 TPS。
     * 够不着、没加载完时不读方块，也就不知道客户端有没有按同样的规则预测着换了，服务端一律发一个格子包纠正（{@link #resync}）；
     * 正常客户端只挖自己够得着、已经收到的格子，只有延迟高、两端位置对不上时才会多这一个包。
     * <p>
     * 事件被取消（领地、保护类模组）时照样换：只动玩家自己手里的物品，不改世界。两端都换才一致——只在服务端取消时如果服务端不换、只纠正，
     * 客户端下一 tick 发现手里的物品变了就重开挖掘、又预测着换、又被纠正，工具每个往返在两个形态间闪一次。
     * @return 换了没有
     */
    public static boolean onStartMining(Player player, Level level, BlockPos pos) {
        if (player.isSpectator() || player.isCreative()) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (Form.of(held) == null) {
            return false;
        }
        if (!player.canReach(pos, REACH_PADDING) || !ECLevels.isFullyLoaded(level, pos)) {
            // 不读方块：客户端那边可能已经按同样的规则换了，服务端没换，就把服务端手里的东西发回去
            if (player instanceof ServerPlayer serverPlayer) {
                resync(serverPlayer);
            }
            return false;
        }
        Form target = bestFormFor(held, level.getBlockState(pos), level, pos);
        if (target == null) {
            return false;
        }
        replaceMainHand(player, transform(held, target));
        return true;
    }

    /** 把服务端主手格子的内容发给客户端（容器号 -2 = 直接写玩家物品栏的第 n 格），纠正客户端的预测 */
    static void resync(ServerPlayer player) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        int slot = player.getInventory().selected;
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, player.getInventory().getItem(slot).copy()));
    }

    /**
     * 打中生物之后（镐 / 斧 / 锹 / 锄的 hurtEnemy 调，不扣耐久；原版只在服务端调 hurtEnemy，创造模式也调）：主手的这把变成剑。
     * 空栈、不在主手、攻击者不是玩家、旁观模式时不动。
     */
    public static void afterHit(ItemStack stack, LivingEntity attacker) {
        if (attacker.level().isClientSide || !(attacker instanceof Player player) || player.isSpectator()) {
            return;
        }
        if (stack.isEmpty() || player.getMainHandItem() != stack) {
            return;
        }
        Form form = Form.of(stack);
        if (form == null || form == Form.SWORD) {
            return;
        }
        replaceMainHand(player, transform(stack, Form.SWORD));
    }

    /**
     * 这次主手物品的变化是不是风之工具换形态（同一个快捷栏格子、前后都是风之工具）。
     * 是的话客户端不做原版「放下再举起」的换手动画，挖着挖着工具直接变样（原作用原版动画，挖第一下时手会往下沉一次）。
     */
    public static boolean isMorph(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return !slotChanged && Form.of(oldStack) != null && Form.of(newStack) != null;
    }

    // ---------------------------------------------------------------- 右键

    /** 非锄形态额外声明能做的动作：锄地（原作任何形态右键都是锄地） */
    public static boolean extraAction(ToolAction action) {
        return action == ToolActions.HOE_TILL;
    }

    /**
     * 右键方块：先照风之锄锄地（手里这把的耐久扣 1），锄不了再做这个形态自己的原版动作（{@code own} 传 {@code super::useOn}）。
     */
    public static InteractionResult useOn(UseOnContext context, Function<UseOnContext, InteractionResult> own) {
        InteractionResult tilled = Form.HOE.item().useOn(context);
        return tilled != InteractionResult.PASS ? tilled : own.apply(context);
    }

    // ---------------------------------------------------------------- 读、提示

    /** 收起来的某个形态的附魔（当前形态请直接用 EnchantmentHelper.getEnchantments） */
    public static Map<Enchantment, Integer> storedEnchantments(ItemStack stack, Form form) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(FORMS_KEY, Tag.TAG_COMPOUND)) {
            return Map.of();
        }
        ListTag list = tag.getCompound(FORMS_KEY).getCompound(form.key()).getList(ItemStack.TAG_ENCH, Tag.TAG_COMPOUND);
        return list.isEmpty() ? Map.of() : EnchantmentHelper.deserializeEnchantments(list);
    }

    /** 收起来的某个形态的铁砧累计惩罚 */
    public static int storedRepairCost(ItemStack stack, Form form) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return 0;
        }
        return tag.getCompound(FORMS_KEY).getCompound(form.key()).getInt("RepairCost");
    }

    /** 提示行：当前形态、怎么变形、其它形态身上的附魔 */
    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        Form form = Form.of(stack);
        if (form == null) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_tool.form", Component.translatable(form.nameKey()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_tool.hint").withStyle(ChatFormatting.GRAY));
        Map<Form, Map<Enchantment, Integer>> stored = new LinkedHashMap<>();
        for (Form other : Form.values()) {
            if (other != form) {
                Map<Enchantment, Integer> enchantments = storedEnchantments(stack, other);
                if (!enchantments.isEmpty()) {
                    stored.put(other, enchantments);
                }
            }
        }
        if (stored.isEmpty()) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_tool.stored").withStyle(ChatFormatting.GRAY));
        stored.forEach((other, enchantments) -> {
            MutableComponent names = Component.empty();
            boolean first = true;
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                if (!first) {
                    names.append(Component.translatable("tooltip.essentialcraft5.wind_tool.separator").withStyle(ChatFormatting.DARK_GRAY));
                }
                names.append(entry.getKey().getFullname(entry.getValue()));
                first = false;
            }
            tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_tool.stored_form", Component.translatable(other.nameKey()), names)
                    .withStyle(ChatFormatting.DARK_AQUA));
        });
    }
}
