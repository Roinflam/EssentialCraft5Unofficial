package com.flameforge.essentialcraft5.item.bauble;

import com.flameforge.essentialcraft5.api.IUBMRUGainModifyHandler;
import com.flameforge.essentialcraft5.api.IWindAffinityItem;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 特殊饰品（原作 ItemBaublesSpecial 的一个 meta）。原作 32 个 meta 共用一个类、按 meta 分支，这里每件一个物品，行为用 {@link Settings} 配：
 * <ul>
 *   <li>{@link Settings#gain}：击杀非玩家生物时对 UBMRU 所得的修正（{@link IUBMRUGainModifyHandler}，由一批的 UBMRUEvents 依次调用）；</li>
 *   <li>{@link Settings#windAffinity}：风之好感系数加成（{@link IWindAffinityItem}，由框架 WindAffinity 累加）；</li>
 *   <li>{@link Settings#tick}：戴着时在服务端每 tick 调一次（原作 onWornTick）。</li>
 * </ul>
 * 其余饰品的效果在事件里（BaubleCombatEvents、BaubleHarvestEvents）或战利品修改器里（BaubleLootModifiers），按物品查 {@code CuriosCompat.has}。
 * <p>
 * <b>同种只生效一件</b>（policy §4.2）：{@link #canEquip} 拒第二件；物品自己的钩子（上面三种，不走 CuriosCompat 的「生效」判断）再用
 * {@link #isActive} 确认这一件就是缓存里生效的那一件、没被 {@code general.baubles.disabled} 关掉，所以用指令塞进两件也只算一件。
 * <p>
 * 提示：原作说明文字（翻译键 {@code tooltip.essentialcraft5.bauble.<注册名>}）沿用原作的 {@code |a |c |6 |d |7 |r} 颜色码和 {@code |n} 换行
 * （glossary 第 9 条），这里转成 § 格式码逐行显示；要补充数值 / 限制的饰品另有 {@code .detail}（灰色，同样支持 {@code |n}）。
 * 之后是「同种只有一件生效」，被服务器禁用时再加一行红字。
 */
public class SpecialBaubleItem extends Item implements ICurioItem, IUBMRUGainModifyHandler, IWindAffinityItem {

    /** 击杀 UBMRU 修正（参数同 {@link IUBMRUGainModifyHandler#getModifiedValue}） */
    @FunctionalInterface
    public interface GainModifier {
        float modify(float original, ItemStack stack, RandomSource random, Player player);
    }

    /** 戴着时服务端每 tick 调一次（只对真玩家；已经确认这一件生效） */
    @FunctionalInterface
    public interface WornTick {
        void tick(Player player, ItemStack stack);
    }

    /** 一件饰品的行为 */
    public static final class Settings {
        @Nullable
        GainModifier gain;
        float windAffinity;
        @Nullable
        WornTick tick;
        boolean detail;
        @Nullable
        Supplier<Object[]> args;

        private Settings() {
        }

        public static Settings of() {
            return new Settings();
        }

        public Settings gain(GainModifier gain) {
            this.gain = gain;
            return this;
        }

        public Settings windAffinity(float windAffinity) {
            this.windAffinity = windAffinity;
            return this;
        }

        public Settings tick(WornTick tick) {
            this.tick = tick;
            return this;
        }

        /** 提示里多一段 .detail（数值、限制） */
        public Settings detail() {
            this.detail = true;
            return this;
        }

        /**
         * 说明文字和 .detail 的翻译参数（%s，按配置现算；串里要写百分号时写 %%）。
         * 客户端读的是进服时同步过来的服务端配置（热重载另有 S2CConfigResyncPacket 重发）
         */
        public Settings args(Supplier<Object[]> args) {
            this.args = args;
            return this;
        }
    }

    private static final Object[] NO_ARGS = new Object[0];

    private final String slot;
    private final Settings settings;

    /**
     * @param slot 戴在哪种 Curios 槽（CuriosCompat.RING / NECKLACE / BELT）。真正决定能进哪个槽的是物品标签（注册时 ItemSpec 加的 curios:*），
     *             这里只是记下来给测试、手册用，两者由 ECBaubles 一起给
     */
    public SpecialBaubleItem(Properties properties, String slot, Settings settings) {
        super(properties);
        this.slot = slot;
        this.settings = settings;
    }

    /** 戴在哪种 Curios 槽 */
    public String slot() {
        return slot;
    }

    // ---------------------------------------------------------------- 生效判断

    /**
     * 这一件饰品现在生效：没被配置禁用，而且是佩戴者身上生效的那一件（同种塞了两件时只认缓存里的第一件）。
     * 给不经过 CuriosCompat 查询的钩子（UBMRU 修正、curioTick、风之好感）用。
     */
    public static boolean isActive(LivingEntity wearer, ItemStack stack) {
        return !stack.isEmpty() && !CuriosCompat.isDisabled(stack) && CuriosCompat.find(wearer, stack.getItem()) == stack;
    }

    /** 能享受饰品效果的玩家：不是 FakePlayer（原作 UBMRU 等处也排除 FakePlayer） */
    public static boolean isWearer(@Nullable LivingEntity entity) {
        return entity instanceof Player && !(entity instanceof FakePlayer);
    }

    // ---------------------------------------------------------------- Curios

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CuriosCompat.onlyOneEquipped(slotContext, stack);
    }

    /** 右键直接戴上（放进第一个能放的槽） */
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        WornTick tick = settings.tick;
        LivingEntity entity = slotContext.entity();
        if (tick == null || slotContext.cosmetic() || entity == null || entity.level().isClientSide || !isWearer(entity)) {
            return;
        }
        if (isActive(entity, stack)) {
            tick.tick((Player) entity, stack);
        }
    }

    // ---------------------------------------------------------------- 物品自己的效果钩子

    @Override
    public float getModifiedValue(float original, ItemStack stack, RandomSource random, Player player) {
        GainModifier gain = settings.gain;
        if (gain == null || !isActive(player, stack)) {
            return original;
        }
        return gain.modify(original, stack, random, player);
    }

    @Override
    public float getWindAffinity(ItemStack stack, Player player) {
        return settings.windAffinity != 0F && isActive(player, stack) ? settings.windAffinity : 0F;
    }

    // ---------------------------------------------------------------- 提示

    /** 说明文字的翻译键：tooltip.essentialcraft5.bauble.&lt;注册名&gt; */
    public String helpKey() {
        var id = ForgeRegistries.ITEMS.getKey(this);
        return "tooltip.essentialcraft5.bauble." + (id == null ? "unknown" : id.getPath());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        appendExtraLines(stack, tooltip);
        String key = helpKey();
        Object[] args = settings.args == null ? NO_ARGS : settings.args.get();
        tooltip.addAll(legacyLines(Component.translatable(key, args).getString(), ChatFormatting.GRAY));
        if (settings.detail) {
            tooltip.addAll(legacyLines(Component.translatable(key + ".detail", args).getString(), ChatFormatting.DARK_GRAY));
        }
        tooltip.add(CuriosCompat.onlyOneTooltip());
        if (CuriosCompat.isDisabled(stack)) {
            tooltip.add(CuriosCompat.disabledTooltip());
        }
    }

    /** 子类在说明文字之前加的行（便携矩阵换能器的 MRU） */
    protected void appendExtraLines(ItemStack stack, List<Component> tooltip) {
    }

    /**
     * 把原作说明文字的颜色码转成行：{@code |n} 换行，{@code |r} 重置，{@code |x}（x 是 0~9、a~f、k~o）换成 §x。
     * 每行是一个带基础颜色的字面文本（§ 码由字体渲染时解析，§r 回到基础颜色）。
     */
    public static List<Component> legacyLines(String text, ChatFormatting base) {
        List<Component> lines = new ArrayList<>();
        for (String raw : text.split("\\|n", -1)) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '|' && i + 1 < raw.length() && ChatFormatting.getByCode(raw.charAt(i + 1)) != null) {
                    line.append('\u00a7').append(raw.charAt(i + 1));
                    i++;
                } else {
                    line.append(c);
                }
            }
            if (!line.isEmpty()) {
                lines.add(Component.literal(line.toString()).withStyle(base));
            }
        }
        return lines;
    }
}
