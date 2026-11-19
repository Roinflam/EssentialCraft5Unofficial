package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MatrixRanks;
import com.flameforge.essentialcraft5.client.guide.Figures;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementBonuses;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageLines;
import com.flameforge.essentialcraft5.item.attunement.AttunementTooltipFit;
import com.flameforge.essentialcraft5.item.attunement.BattleScars;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.attunement.GearStat;
import com.flameforge.essentialcraft5.item.attunement.GearStatSheet;
import com.flameforge.essentialcraft5.player.AttunementBudget;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;

/**
 * 同调装备的提示框（batch13.md §3.3.5）：
 * <pre>
 * 同调 ◆◆◇◇◇（2 / 5 阶）
 * 同调度 650 / 1 600
 *  1 阶 ……（各家族的阶效果：解锁了白字、没解锁灰字）
 * 预算还能长 312 点，存满要在线约 2 小时
 * 按住 Shift 查看物品说明和升阶条件
 * </pre>
 * 平时物品自己的说明（appendHoverText 写的，以及本模组另外接的 MRU 抗性、矩阵特性、满电次数）收起来，提示框只剩同调块和原版属性行，
 * 满阶的同调装备在 720p、界面缩放 3 下也放得进屏幕（{@link #onAttunementNotesCollapse}、{@link #holdsBackNotes}）。
 * Shift：物品说明全写出来；同调块加上升到下一阶要的矩阵阶（和你现在几阶）、阶戳不够时提醒穿上 / 拿着一会儿、魔力工作台四角要放什么（从配方表里找）。
 * 这时整份提示框估出来高过屏幕（{@link #fitsScreen}），同调块只留标题一行；连标题也放不下就不写同调块。
 * <p>
 * 各家族的阶效果写成语言键 {@code tooltip.<命名空间>.attunement.<家族>.stage_<n>}（有这个键才画那一行），由实现阶效果的模块写进自己的语言文件；
 * 数能在配置里改的家族，物品实现 {@link AttunementStageLines}，按配置填好参数再给出来。
 * 这里只管排版。盔甲每阶的生命、护甲、韧性、击退抗性和刀剑每阶的攻击都是属性修饰符，并进物品自己的那一条（{@code AttunementBonuses}），
 * 原版的「穿在身上时 / 在主手时」一栏每个属性只列一行合计；枪每阶的增伤由增伤池给，写在「同调词条」一块（{@link #statLines}）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class AttunementTooltips {

    private static final String PREFIX = "tooltip.essentialcraft5.attunement.";

    /** 这一份提示框（按列表对象认）里收起过物品说明；收起的那一步在前，写同调块时看要不要写「按住 Shift 查看物品说明」 */
    private static final ThreadLocal<List<Component>> NOTES_HIDDEN = new ThreadLocal<>();

    /** 展示厅拍「按住 Shift」那一张时代替键盘；null 时看真实按键 */
    @Nullable
    private static Boolean shiftOverride;
    /** 展示厅把提示框画在指定位置时，估折行用的鼠标横坐标（界面坐标）；小于 0 时用真实鼠标 */
    private static int mouseXOverride = -1;

    private AttunementTooltips() {
    }

    /** 展示厅用：拍截图时假定 Shift 按没按住（null 恢复看键盘）、鼠标停在哪一列（小于 0 恢复看真实鼠标） */
    public static void setPreview(@Nullable Boolean shift, int mouseX) {
        shiftOverride = shift;
        mouseXOverride = mouseX;
    }

    /** 按没按住 Shift（展示厅可以代替） */
    public static boolean shiftDown() {
        Boolean override = shiftOverride;
        return override != null ? override : Screen.hasShiftDown();
    }

    /**
     * 同调装备的提示框平时只写同调块和原版属性行，物品自己的说明（护盾、全套效果、MRU 行、介绍）收起来，按住 Shift 才写。
     * 家族被关掉时不收（同调块只剩一行「已停用」，说明照常写）。
     */
    public static boolean hidesItemNotes(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        return family != null && family.active() && !shiftDown();
    }

    /**
     * 本模组另外往提示框里写说明的地方（MRU 抗性、矩阵特性、满电次数）写之前先问这一句：要收起就返回 true，调用方不写；
     * 同时记下「这份提示框收起过东西」，同调块末尾才写「按住 Shift 查看物品说明」。
     */
    public static boolean holdsBackNotes(ItemTooltipEvent event) {
        if (!hidesItemNotes(event.getItemStack())) {
            return false;
        }
        NOTES_HIDDEN.set(event.getToolTip());
        return true;
    }

    /**
     * 收起物品自己的说明：原版先调 appendHoverText 把说明接在名字后面，再发这个事件，这里排最前，把同样的几行再算一遍、
     * 在提示框里找到逐行一样的那一段删掉。找不到（别的模组先动过、说明每次算出来不一样）就不收，照原样显示。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttunementNotesCollapse(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!hidesItemNotes(stack)) {
            return;
        }
        Player player = event.getEntity();
        List<Component> own = new ArrayList<>();
        stack.getItem().appendHoverText(stack, player == null ? null : player.level(), own, event.getFlags());
        if (own.isEmpty()) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        int at = indexOfRun(tooltip, own);
        if (at < 0) {
            return;
        }
        tooltip.subList(at, at + own.size()).clear();
        NOTES_HIDDEN.set(tooltip);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAttunementTooltip(ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();
        boolean notesHidden = NOTES_HIDDEN.get() == tooltip;
        NOTES_HIDDEN.remove();
        ItemStack stack = event.getItemStack();
        AttunementFamily family = AttunementFamily.of(stack);
        if (family == null) {
            return;
        }
        boolean shift = shiftDown();
        // 加上整块同调就高过屏幕时让出几行（AttunementTooltipFit）：按住 Shift 时物品说明全写出来，同调块只留标题一行，
        // 说明本身已经很长（裁决之刃在背包格位置折行以后）连标题都放不下就整块不写，松开 Shift 再看；
        // 不按 Shift 时（附魔多的满阶武器）先去掉逐阶效果那几行，还放不下再只留标题；
        // 按住 Shift 整块不写了还放不下，就从物品说明末尾往前截（truncateNotes）
        List<Component> lines = AttunementTooltipFit.fit(lines(stack, family, event.getEntity(), shift, notesHidden), shift,
                extra -> fitsScreen(stack, tooltip, extra));
        if (shift && lines.isEmpty()) {
            truncateNotes(event, stack, tooltip);
        }
        int at = Math.min(1, tooltip.size());
        tooltip.addAll(at, lines);
    }

    /**
     * 按住 Shift、同调块已经整块让掉了，物品说明本身还高过屏幕：从说明末尾往前截，末尾换一行「……还有 N 行」。
     * 说明的位置照收起时的找法（把 appendHoverText 再算一遍、找逐行一样的那一段，MRU 行这类被别的监听器原地包过一层的也认）；找不到就不截，照原样画。
     * 物品名、附魔行、属性行和别处补在末尾的几行都不在这一段里，不会被截。
     */
    private static void truncateNotes(ItemTooltipEvent event, ItemStack stack, List<Component> tooltip) {
        if (fitsScreen(stack, tooltip, List.of())) {
            return;
        }
        Player player = event.getEntity();
        List<Component> own = new ArrayList<>();
        stack.getItem().appendHoverText(stack, player == null ? null : player.level(), own, event.getFlags());
        int at = own.isEmpty() ? -1 : indexOfRun(tooltip, own);
        if (at < 0) {
            return;
        }
        AttunementTooltipFit.truncateNotes(tooltip, at, at + own.size(),
                n -> Component.translatable(PREFIX + "notes_more", n).withStyle(ChatFormatting.DARK_GRAY),
                candidate -> fitsScreen(stack, candidate, List.of()));
    }

    /** 说明那一段在 tooltip 里从哪开始（{@link AttunementTooltipFit#indexOfRun}：被别的监听器原地包过一层的行也认） */
    private static int indexOfRun(List<Component> tooltip, List<Component> own) {
        return AttunementTooltipFit.indexOfRun(tooltip, own);
    }

    /**
     * tooltip 加上 extra 画出来会不会高过屏幕。照 Forge 的折行（左右两边都放不下才按鼠标那一侧的宽度折行）和原版提示框的高度
     * （每行 10，加上下边框共 8 像素；放不下时原版只往上推，顶边出屏的那几行就看不到了）估算。鼠标位置取当前鼠标，展示厅可以代替。
     */
    static boolean fitsScreen(ItemStack stack, List<Component> tooltip, List<Component> extra) {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        int screenWidth = window.getGuiScaledWidth();
        int screenHeight = window.getGuiScaledHeight();
        Font font = ForgeHooksClient.getTooltipFont(stack, minecraft.font);
        int mouseX = mouseXOverride >= 0 ? mouseXOverride
                : (int) (minecraft.mouseHandler.xpos() * screenWidth / Math.max(1, window.getScreenWidth()));
        List<Component> all = new ArrayList<>(tooltip.size() + extra.size());
        all.addAll(tooltip);
        all.addAll(extra);
        int width = 0;
        for (Component line : all) {
            width = Math.max(width, font.width(line));
        }
        boolean wrap = false;
        if (mouseX + 12 + width + 4 > screenWidth && mouseX - 16 - width < 4) {
            width = mouseX > screenWidth / 2 ? mouseX - 12 - 8 : screenWidth - 16 - mouseX;
            wrap = true;
        }
        int rows = 0;
        for (Component line : all) {
            rows += wrap && !line.getString().isEmpty() ? Math.max(1, font.split(line, Math.max(1, width)).size()) : 1;
        }
        return rows * 10 + 8 <= screenHeight;
    }

    /** 第 n 阶的效果：物品自己按配置填数（{@link AttunementStageLines}），否则照语言键原样写；都没有返回 null */
    @Nullable
    private static Component stageEffect(ItemStack stack, String effectPrefix, int n) {
        if (stack.getItem() instanceof AttunementStageLines own) {
            return own.attunementStageLine(stack, n);
        }
        String key = effectPrefix + n;
        return I18n.exists(key) ? Component.translatable(key) : null;
    }

    /**
     * 提示框里同调这几行（展示厅、测试也能直接调）。第一行总是标题（阶数，满阶时连同战痕）。
     * notesHidden：物品说明收起来了，末尾的 Shift 提示改成「查看物品说明」（没满阶时连同升阶条件）。
     */
    public static List<Component> lines(ItemStack stack, AttunementFamily family, @Nullable Player player, boolean shift, boolean notesHidden) {
        List<Component> lines = new ArrayList<>();
        int stage = Attunement.stage(stack);
        int max = family.maxStage();
        if (stage < max) {
            lines.add(Component.translatable(PREFIX + "stage", diamonds(stage, max), stage, max).withStyle(ChatFormatting.LIGHT_PURPLE));
            int need = Attunement.need(family, stage);
            int points = Attunement.points(stack);
            lines.add(Component.translatable(points >= need ? PREFIX + "points_full" : PREFIX + "points",
                    Figures.number(points), Figures.number(need)).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(maxedLine(stack, diamonds(stage, max)));
        }
        String effectPrefix = "tooltip." + family.id().getNamespace() + ".attunement." + family.id().getPath() + ".stage_";
        for (int n = 1; n <= max; n++) {
            Component effect = stageEffect(stack, effectPrefix, n);
            if (effect != null) {
                lines.add(Component.translatable(AttunementTooltipFit.EFFECT_KEY, n, effect)
                        .withStyle(n <= stage ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
            }
        }
        lines.addAll(statLines(stack, family, shift));
        if (!family.active()) {
            lines.add(Component.translatable(PREFIX + "disabled").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        if (stage >= max) {
            if (notesHidden) {
                lines.add(Component.translatable(PREFIX + "shift_notes").withStyle(ChatFormatting.DARK_GRAY));
            }
            return lines;
        }
        if (player != null) {
            float budget = AttunementBudget.get(player, family.kind().budget());
            Component line = switch (AttunementBudget.line(budget, AttunementConfig.budgetCap(), AttunementConfig.budgetPerHour())) {
                case FULL -> Component.translatable(PREFIX + "budget_full", Figures.number((int) budget));
                case STATIC -> Component.translatable(PREFIX + "budget_static", Figures.number((int) budget));
                case FILLING -> Component.translatable(PREFIX + "budget", Figures.number((int) budget), duration(AttunementBudget.secondsToFull(budget)));
            };
            lines.add(line.copy().withStyle(ChatFormatting.DARK_AQUA));
        }
        if (!shift) {
            lines.add(Component.translatable(PREFIX + (notesHidden ? "shift_notes_next" : "shift")).withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        lines.add(Component.translatable(PREFIX + "next", stage + 1).withStyle(ChatFormatting.GOLD));
        int rank = AttunementConfig.requiredRank(stage + 1);
        if (rank > 0) {
            int own = MatrixRanks.get(player);
            lines.add(Component.translatable(PREFIX + "next_rank", rank, own)
                    .withStyle(own >= rank ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (own >= rank && Attunement.rankStamp(stack) < rank) {
                lines.add(Component.translatable(PREFIX + "stamp_hint").withStyle(ChatFormatting.GRAY));
            }
        }
        AttunementAscensionRecipe recipe = findRecipe(stack, family, stage);
        if (recipe == null) {
            lines.add(Component.translatable(PREFIX + "next_unknown").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.translatable(PREFIX + "next_corners").withStyle(ChatFormatting.GRAY));
        for (int i = 1; i < MagicianTableRecipe.INPUTS; i++) {
            Ingredient ingredient = recipe.getIngredients().get(i);
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack[] options = ingredient.getItems();
            if (options.length == 0) {
                continue;
            }
            MutableComponent name = options[0].getHoverName().copy();
            if (options.length > 1) {
                name = Component.translatable(PREFIX + "any_of", name);
            }
            lines.add(Component.translatable(PREFIX + "corner", name, recipe.countAt(i)).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(PREFIX + "next_mru", Figures.number(recipe.getMRU())).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /**
     * 「同调词条」一块（tools/plan/gear_growth.md 第 5 节）：这一阶的词条、觉醒、下一阶有变化的灰字预告。武器一行一条，盔甲拼成一行。
     * 战痕写在满阶标题行（{@link #maxedLine}）。按住 Shift 再写本模组的合计上限、战绩（首领击杀、累计打实伤害）和这件给增伤池的分项。数都按服务端同步来的配置现算，和结算同一份算法（{@link GearStatSheet}）。
     */
    public static List<Component> statLines(ItemStack stack, AttunementFamily family, boolean shift) {
        List<Component> lines = new ArrayList<>();
        int stage = Attunement.stage(stack);
        int max = family.maxStage();
        EnumMap<GearStat, Double> now = GearStatSheet.at(stack, family, stage);
        EnumMap<GearStat, Double> next = stage < max ? GearStatSheet.at(stack, family, stage + 1) : null;
        List<GearStatSheet.Awakening> awakenings = GearStatSheet.awakenings(family);
        boolean weapon = family.kind() == AttunementFamily.Kind.WEAPON;
        boolean any = !now.isEmpty() || (next != null && !next.isEmpty()) || !awakenings.isEmpty();
        if (!any) {
            return lines;
        }
        if (weapon || now.isEmpty()) {
            lines.add(Component.translatable(PREFIX + "gear_stats").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (weapon) {
            for (Map.Entry<GearStat, Double> entry : now.entrySet()) {
                lines.add(statLine(family, entry.getKey(), entry.getValue()).withStyle(ChatFormatting.WHITE));
            }
        } else if (!now.isEmpty()) {
            // 盔甲的词条少、每条短，拼成一行省地方；武器满阶有五六条，一行一条更清楚
            lines.add(Component.translatable(PREFIX + "gear_stats_line", joinedStats(family, now).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        for (GearStatSheet.Awakening awakening : awakenings) {
            int unlock = awakening.unlockStage();
            if (unlock > max) {
                continue;
            }
            MutableComponent name = Component.translatable(awakening.key());
            if (stage >= unlock) {
                lines.add(Component.translatable(PREFIX + "awakening", name.withStyle(ChatFormatting.LIGHT_PURPLE),
                        Component.translatable(awakening.key() + ".desc", awakening.args().apply(stack))).withStyle(ChatFormatting.WHITE));
            } else {
                lines.add(Component.translatable(PREFIX + "awakening_locked", unlock, name).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (next != null) {
            EnumMap<GearStat, Double> changed = new EnumMap<>(GearStat.class);
            for (Map.Entry<GearStat, Double> entry : next.entrySet()) {
                Double before = now.get(entry.getKey());
                double value = entry.getValue();
                if (before != null && Math.abs(before - value) < 1.0E-9D) {
                    continue;
                }
                changed.put(entry.getKey(), value);
            }
            if (weapon) {
                for (Map.Entry<GearStat, Double> entry : changed.entrySet()) {
                    lines.add(Component.translatable(PREFIX + "stat_new", statLine(family, entry.getKey(), entry.getValue())).withStyle(ChatFormatting.DARK_GRAY));
                }
            } else if (!changed.isEmpty()) {
                lines.add(Component.translatable(PREFIX + "stat_new_line", joinedStats(family, changed)).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (shift) {
            // 合计上限平时用不上，放进按住 Shift 和战绩一起看
            boolean blade = weapon && family.line() == AttunementFamily.Line.BLADE;
            if (weapon) {
                // 刀剑属性栏那份同调攻击也在这个上限里（总闸管乘积），说法不同
                lines.add(Component.translatable(PREFIX + (blade ? "pool_caps.blade" : "pool_caps"), AttunementStageLines.percent(GearGrowthConfig.poolMob()),
                        AttunementStageLines.percent(GearGrowthConfig.poolBoss()), AttunementStageLines.percent(GearGrowthConfig.poolPlayer()))
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.translatable(PREFIX + "reduction_caps", AttunementStageLines.percent(GearGrowthConfig.reductionCap()),
                        AttunementStageLines.percent(GearGrowthConfig.reductionCapPlayer())).withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(Component.translatable(PREFIX + "record", Attunement.bosses(stack),
                    Figures.number((int) Math.min(Integer.MAX_VALUE, Attunement.dealt(stack)))).withStyle(ChatFormatting.GRAY));
            if (weapon) {
                lines.add(Component.translatable(PREFIX + "breakdown").withStyle(ChatFormatting.GRAY));
                if (blade) {
                    lines.add(Component.translatable(PREFIX + "breakdown.attack", AttunementStageLines.percent(AttunementBonuses.bladeAttackFactor(stack) - 1D))
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    lines.add(Component.translatable(PREFIX + "breakdown.stage", AttunementStageLines.percent(now.getOrDefault(GearStat.DAMAGE, 0D)))
                            .withStyle(ChatFormatting.GRAY));
                }
                double scar = GearStatSheet.scarDamage(stack);
                if (scar > 0D) {
                    lines.add(Component.translatable(PREFIX + "breakdown.scar", AttunementStageLines.percent(scar)).withStyle(ChatFormatting.GRAY));
                }
                Double execute = now.get(GearStat.EXECUTE);
                if (execute != null) {
                    lines.add(Component.translatable(PREFIX + "breakdown.execute", AttunementStageLines.percent(execute)).withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable(PREFIX + "breakdown.pvp", AttunementStageLines.number(AttunementConfig.pvpEffectScale()))
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else if (stage >= max && BattleScars.level(stack) > 0) {
                lines.add(Component.translatable(PREFIX + "breakdown.armor_scar",
                        AttunementStageLines.percent(BattleScars.level(stack) * Math.max(0D, GearGrowthConfig.scarArmorPerLevel()))).withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    /**
     * 满阶时的标题行，阶数和战痕并成一行：「同调 ◆◆◆◆ 满阶 · 战痕 6500 / 8000」。
     * 战痕关掉（最高级为 0）只写满阶；战痕满级写「已满」，几级看物品名后面的 ✦。
     */
    private static Component maxedLine(ItemStack stack, String diamonds) {
        int top = GearGrowthConfig.scarMaxLevel();
        if (top <= 0) {
            return Component.translatable(PREFIX + "stage_max", diamonds).withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        int level = BattleScars.level(stack);
        if (level >= top) {
            return Component.translatable(PREFIX + "stage_max.scar_full", diamonds).withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        return Component.translatable(PREFIX + "stage_max.scar", diamonds, Figures.number(Attunement.scar(stack)),
                Figures.number((level + 1) * GearGrowthConfig.scarPointsPerLevel())).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    /** 几条词条拼成一段，用语言里的分隔符连起来（词条键开头留的缩进空格去掉） */
    private static MutableComponent joinedStats(AttunementFamily family, Map<GearStat, Double> stats) {
        String separator = I18n.get(PREFIX + "stat_separator");
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<GearStat, Double> entry : stats.entrySet()) {
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(statLine(family, entry.getKey(), entry.getValue()).getString().strip());
        }
        return Component.literal(builder.toString());
    }

    /** 一条词条那一行（家族有专门的说法时用 {@code <键>.<家族路径>}，比如裁决之刃的冷却写成断罪冷却） */
    private static MutableComponent statLine(AttunementFamily family, GearStat stat, double value) {
        String own = stat.key() + "." + family.id().getPath();
        String key = I18n.exists(own) ? own : stat.key();
        if (stat == GearStat.EXECUTE) {
            return Component.translatable(key, AttunementStageLines.percent(GearGrowthConfig.executeThreshold()), AttunementStageLines.percent(value));
        }
        return Component.translatable(key, AttunementStageLines.percent(value));
    }

    /** ◆◆◇◇◇ */
    static String diamonds(int stage, int max) {
        StringBuilder builder = new StringBuilder(max);
        for (int i = 0; i < max; i++) {
            builder.append(i < stage ? '◆' : '◇');
        }
        return builder.toString();
    }

    /** 「约 2 小时」「约 40 分钟」 */
    static Component duration(long seconds) {
        if (seconds >= 3600L) {
            return Component.translatable(PREFIX + "hours", Figures.number(Math.round(seconds / 360F) / 10F));
        }
        return Component.translatable(PREFIX + "minutes", Math.max(1L, (seconds + 59L) / 60L));
    }

    /** 客户端配方表里这件这一阶的升阶配方（部位对得上的第一条）；没有返回 null */
    @Nullable
    static AttunementAscensionRecipe findRecipe(ItemStack stack, AttunementFamily family, int stage) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        EquipmentSlot slot = AttunementFamily.slotOf(stack, family.kind());
        for (MagicianTableRecipe recipe : minecraft.level.getRecipeManager().getAllRecipesFor(ECRecipes.MAGICIAN_TABLE.get())) {
            if (recipe instanceof AttunementAscensionRecipe ascension && ascension.family().equals(family.id())
                    && ascension.fromStage() == stage && (ascension.parts().isEmpty() || ascension.parts().contains(slot))) {
                return ascension;
            }
        }
        return null;
    }
}
