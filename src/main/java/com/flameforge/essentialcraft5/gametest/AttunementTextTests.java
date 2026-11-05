package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.GuideTextFit;
import com.flameforge.essentialcraft5.config.GuideConfigSnapshot;
import com.flameforge.essentialcraft5.item.ForgedElementalSwordItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.VerdictBladeItem;
import com.flameforge.essentialcraft5.item.WindArmorItem;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageLines;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData.Element;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第十三批 13b：提示框和手册里写的数跟着这把剑 / 这件盔甲的阶和配置走，不再写死（batch13.md §3.3.5、§4.4）。
 * <ul>
 *   <li>余烬元素剑、裁决之刃的命中、右键、余烬秒数按这把剑的倍数算，和真打出来的效果时长一致；</li>
 *   <li>同调提示框里风之护甲、宅男套装、余烬剑、裁决之刃各阶那一行按配置填数（{@link AttunementStageLines}）；</li>
 *   <li>风之护甲「四件穿齐」那段按这件的阶写，被配置关掉的那样不写；</li>
 *   <li>升格配方图下面的矩阵阶说明（升格单独一种说法）、手册窄处放字（{@link GuideTextFit}）、
 *       手册阶表的 {@code addcfg} 写法在默认配置下算出来就是设计数值、预算行英文。</li>
 * </ul>
 * 测试覆盖值（{@code setTestOverrides} 等）都在同一段同步代码里设、测、在 finally 里置回。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class AttunementTextTests {

    private static final String EMPTY = "empty";
    private static final Pattern ADDCFG = Pattern.compile("\\{cfg:([^}]*)}");

    private AttunementTextTests() {
    }

    // ================================================================ 工具

    private static ForgedSwordData data(Element primary, Element secondary, EmberKind a, EmberKind b) {
        List<ResourceLocation> embers = List.of(a.id(), b.id(), a.id(), b.id());
        List<ResourceLocation> foci = List.of(Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus());
        return new ForgedSwordData(embers, foci, primary, secondary);
    }

    /** 满电、锻好、stage 阶的一把（余烬元素剑或裁决之刃） */
    private static ItemStack sword(ItemStack empty, ForgedSwordData data, int stage) {
        ItemStack stack = MRUStaffItem.full(empty.getItem());
        data.writeTo(stack);
        Attunement.set(stack, stage, 0);
        return stack;
    }

    private static List<Component> tooltip(GameTestHelper helper, ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, helper.getLevel(), lines, TooltipFlag.NORMAL);
        return lines;
    }

    @Nullable
    private static TranslatableContents find(List<Component> lines, String key) {
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)) {
                return contents;
            }
        }
        return null;
    }

    private static TranslatableContents translatable(@Nullable Object arg, String what) {
        if (arg instanceof Component component && component.getContents() instanceof TranslatableContents contents) {
            return contents;
        }
        throw new AssertionError(what + " 不是可翻译文字：" + arg);
    }

    private static String key(@Nullable Component component) {
        return component != null && component.getContents() instanceof TranslatableContents contents ? contents.getKey() : String.valueOf(component);
    }

    private static List<String> args(@Nullable Component component) {
        List<String> out = new ArrayList<>();
        if (component != null && component.getContents() instanceof TranslatableContents contents) {
            for (Object arg : contents.getArgs()) {
                out.add(String.valueOf(arg));
            }
        }
        return out;
    }

    /** 命中那一行里主元素效果的秒数、余烬那一行的秒数、右键那一行的秒数 */
    private static String[] seconds(GameTestHelper helper, ItemStack stack, Element primary, Element secondary) {
        List<Component> lines = tooltip(helper, stack);
        TranslatableContents hit = find(lines, "tooltip.essentialcraft5.forged_elemental_sword.hit_primary");
        TranslatableContents ember = find(lines, "tooltip.essentialcraft5.forged_elemental_sword.hit_target");
        TranslatableContents use = find(lines, "tooltip.essentialcraft5.forged_elemental_sword.use");
        helper.assertTrue(hit != null && ember != null && use != null, "提示框缺命中 / 余烬 / 右键行：" + lines);
        TranslatableContents hitEffect = translatable(hit.getArgs()[1], "命中效果");
        TranslatableContents useEffect = translatable(use.getArgs()[1], "右键效果");
        helper.assertTrue(hitEffect.getKey().endsWith("hit." + primary.id()) && useEffect.getKey().endsWith("use." + secondary.id()),
                "元素键不对：" + hitEffect.getKey() + " / " + useEffect.getKey());
        return new String[]{String.valueOf(hitEffect.getArgs()[0]), String.valueOf(ember.getArgs()[1]), String.valueOf(useEffect.getArgs()[0])};
    }

    private static int duration(LivingEntity entity, MobEffect effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? -1 : instance.getDuration();
    }

    private static int ticks(String seconds) {
        return (int) Math.round(Double.parseDouble(seconds) * 20D);
    }

    private static void expect(List<String> problems, String what, Object expected, Object actual) {
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            problems.add(what + " 应为 " + expected + "，实际 " + actual);
        }
    }

    private static void report(GameTestHelper helper, String what, List<String> problems) {
        if (!problems.isEmpty()) {
            helper.fail(what + "：\n" + String.join("\n", problems));
        }
    }

    // ================================================================ 余烬剑、裁决之刃的提示框秒数

    /**
     * 提示框的秒数按这把剑的倍数算：0 阶余烬剑（地 / 水，普通 + 腐化余烬）2 / 3 / 2.5 秒，2 阶 4 / 4.5 / 5 秒，
     * 裁决之刃 0 阶一律按 2 阶（4 / 4.5 / 5）；火主元素 2 阶 10 秒。2 阶真打一下，怪身上的失明和虚弱正好是提示框写的秒数。
     */
    @GameTest(template = EMPTY)
    public static void forgedSwordTooltipSecondsFollowStage(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "text-sword");
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 1D, 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 1, 2));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        husk.setHealth(200F);
        try {
            ForgedSwordData forged = data(Element.EARTH, Element.WATER, EmberKind.COMMON, EmberKind.CORRUPTED);
            ItemStack emberSword = new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
            ItemStack blade = new ItemStack(ECEmberForge.VERDICT_BLADE.get());
            List<String> problems = new ArrayList<>();
            String[] s0 = seconds(helper, sword(emberSword, forged, 0), Element.EARTH, Element.WATER);
            expect(problems, "0 阶命中", "2", s0[0]);
            expect(problems, "0 阶余烬", "3", s0[1]);
            expect(problems, "0 阶右键", "2.5", s0[2]);
            ItemStack stage2 = sword(emberSword, forged, 2);
            String[] s2 = seconds(helper, stage2, Element.EARTH, Element.WATER);
            expect(problems, "2 阶命中", "4", s2[0]);
            expect(problems, "2 阶余烬", "4.5", s2[1]);
            expect(problems, "2 阶右键", "5", s2[2]);
            String[] b0 = seconds(helper, sword(blade, forged, 0), Element.EARTH, Element.WATER);
            expect(problems, "裁决之刃 0 阶命中", "4", b0[0]);
            expect(problems, "裁决之刃 0 阶余烬", "4.5", b0[1]);
            expect(problems, "裁决之刃 0 阶右键", "5", b0[2]);
            String[] fire = seconds(helper, sword(emberSword, data(Element.FIRE, Element.WATER, EmberKind.COMMON, EmberKind.CORRUPTED), 2),
                    Element.FIRE, Element.WATER);
            expect(problems, "2 阶火主元素", "10", fire[0]);

            // 真打一下：效果时长和提示框写的一致
            ((ForgedElementalSwordItem) stage2.getItem()).hurtEnemy(stage2, husk, player);
            expect(problems, "2 阶打怪的失明刻数", ticks(s2[0]), duration(husk, MobEffects.BLINDNESS));
            expect(problems, "2 阶打怪的虚弱（余烬）刻数", ticks(s2[1]), duration(husk, MobEffects.WEAKNESS));
            helper.assertTrue(ForgedElementalSwordItem.applySecondary(stage2, Element.WATER, player), "2 阶右键没生效");
            expect(problems, "2 阶右键的生命恢复刻数", ticks(s2[2]), duration(player, MobEffects.REGENERATION));
            report(helper, "提示框的秒数", problems);
        } finally {
            husk.discard();
            ForgedElementalSwordItem.Config.setTestStageValues(null);
        }
        helper.succeed();
    }

    // ================================================================ 同调提示框各阶那一行

    /**
     * 余烬剑、裁决之刃各阶那一行：默认配置下就是设计数值；改了倍数跟着变（花费填 0 换成「不花 MRU」），裁决之刃的命中秒数也跟着变。
     * 断罪关掉时裁决之刃只写裁断（二分也不写），裁断上限填 0 时只写断罪，两样都关了不写
     */
    @GameTest(template = EMPTY)
    public static void swordStageLinesFollowConfig(GameTestHelper helper) {
        ItemStack emberSword = new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        ItemStack blade = new ItemStack(ECEmberForge.VERDICT_BLADE.get());
        AttunementStageLines swordLines = (AttunementStageLines) emberSword.getItem();
        AttunementStageLines bladeLines = (AttunementStageLines) blade.getItem();
        List<String> problems = new ArrayList<>();
        expect(problems, "余烬剑 1 阶", List.of("2"), args(swordLines.attunementStageLine(emberSword, 1)));
        expect(problems, "余烬剑 2 阶", List.of("50", "1.5"), args(swordLines.attunementStageLine(emberSword, 2)));
        String[][] verdict = {
                {"3.5", "50"},
                {"13", "4", "60"},
                {"4.5", "70", "12"},
                {"5", "80", "11"},
                {"2", "5.5", "90", "10"},
        };
        for (int stage = 1; stage <= 5; stage++) {
            Component line = bladeLines.attunementStageLine(blade, stage);
            expect(problems, "裁决之刃 " + stage + " 阶的键", "tooltip.essentialcraft5.attunement.verdict_blade.stage_" + stage, key(line));
            expect(problems, "裁决之刃 " + stage + " 阶", List.of(verdict[stage - 1]), args(line));
        }
        try {
            // 断罪关掉（damage_multiplier 0）：只写裁断，二分跟着断罪一起不写
            VerdictBladeItem.Config.Values v = VerdictBladeItem.Config.DEFAULTS;
            VerdictBladeItem.Config.setTestOverrides(withJudgment(v, 0D, v.verdictPercent(), v.verdictCap()));
            String[][] verdictOnly = {{"4", "60"}, {"4.5", "70"}, {"5", "80"}, {"5.5", "90"}};
            for (int stage = 2; stage <= 5; stage++) {
                Component line = bladeLines.attunementStageLine(blade, stage);
                expect(problems, "断罪关掉的 " + stage + " 阶的键", "tooltip.essentialcraft5.attunement.verdict_blade.stage_1", key(line));
                expect(problems, "断罪关掉的 " + stage + " 阶", List.of(verdictOnly[stage - 2]), args(line));
            }
            // 裁断上限填 0：只写断罪；1 阶什么都没有
            VerdictBladeItem.Config.setTestOverrides(new VerdictBladeItem.Config.Values(v.traitMultiplier(), v.verdictPercent(),
                    v.verdictPercentStage(), 0D, 0D, v.verdictMru(), v.verdictPvpPercent(), v.chargeTicks(), v.radius(), v.maxTargets(),
                    v.damageMultiplier(), v.judgmentMru(), v.cooldown(), v.cooldownStage(), v.bisectTicks()));
            helper.assertTrue(bladeLines.attunementStageLine(blade, 1) == null, "裁断关掉，1 阶那一行应该不写");
            expect(problems, "裁断关掉的 2 阶", "tooltip.essentialcraft5.attunement.verdict_blade.stage_2.judgment_only",
                    key(bladeLines.attunementStageLine(blade, 2)));
            expect(problems, "裁断关掉的 4 阶", List.of("11"), args(bladeLines.attunementStageLine(blade, 4)));
            expect(problems, "裁断关掉的 5 阶", List.of("2", "10"), args(bladeLines.attunementStageLine(blade, 5)));
            VerdictBladeItem.Config.setTestOverrides(withJudgment(v, 0D, v.verdictPercent(), 0D));
            helper.assertTrue(bladeLines.attunementStageLine(blade, 3) == null, "裁断、断罪都关了，3 阶那一行应该不写");
        } finally {
            VerdictBladeItem.Config.setTestOverrides(null);
        }
        try {
            ForgedElementalSwordItem.Config.setTestStageValues(new ForgedElementalSwordItem.Config.StageValues(3D, 0D, 2D));
            expect(problems, "改成 ×3 的余烬剑 1 阶", List.of("3"), args(swordLines.attunementStageLine(emberSword, 1)));
            Component free = swordLines.attunementStageLine(emberSword, 2);
            expect(problems, "花费填 0 的余烬剑 2 阶的键", "tooltip.essentialcraft5.attunement.ember_sword.stage_2.free", key(free));
            expect(problems, "花费填 0 的余烬剑 2 阶", List.of("2"), args(free));
            String[] seconds = seconds(helper, sword(blade, data(Element.EARTH, Element.WATER, EmberKind.COMMON, EmberKind.CORRUPTED), 0),
                    Element.EARTH, Element.WATER);
            expect(problems, "×3 时裁决之刃的命中秒数", "6", seconds[0]);
            expect(problems, "×2 时裁决之刃的余烬秒数", "6", seconds[1]);
        } finally {
            ForgedElementalSwordItem.Config.setTestStageValues(null);
        }
        report(helper, "剑的阶效果行", problems);
        helper.succeed();
    }

    /** 裁决之刃配置换掉断罪倍率、裁断比例和上限（上限每阶增量跟着上限一起填 0 时才算关掉） */
    private static VerdictBladeItem.Config.Values withJudgment(VerdictBladeItem.Config.Values v, double damageMultiplier, double percent, double cap) {
        return new VerdictBladeItem.Config.Values(v.traitMultiplier(), percent, v.verdictPercentStage(), cap, cap > 0D ? v.verdictCapStage() : 0D,
                v.verdictMru(), v.verdictPvpPercent(), v.chargeTicks(), v.radius(), v.maxTargets(), damageMultiplier, v.judgmentMru(),
                v.cooldown(), v.cooldownStage(), v.bisectTicks());
    }

    /** 宅男套装各阶那一行：默认 225 MRU、×2、6 秒 1%、200 MRU 50% ×3、25% 1 秒 60 秒；改了过载屏障的配置跟着变 */
    @GameTest(template = EMPTY)
    public static void computerStageLinesFollowConfig(GameTestHelper helper) {
        ItemStack chest = new ItemStack(ECComputer.COMPUTER_CHESTPLATE.get());
        AttunementStageLines lines = (AttunementStageLines) chest.getItem();
        List<String> problems = new ArrayList<>();
        String[][] expected = {
                {"225"},
                {"2"},
                {"6", "1"},
                {"200", "50", "3"},
                {"25", "1", "60"},
        };
        for (int stage = 1; stage <= 5; stage++) {
            Component line = lines.attunementStageLine(chest, stage);
            expect(problems, stage + " 阶的键", "tooltip.essentialcraft5.attunement.computer_armor.stage_" + stage, key(line));
            expect(problems, stage + " 阶", List.of(expected[stage - 1]), args(line));
        }
        helper.assertTrue(lines.attunementStageLine(chest, 6) == null, "没有 6 阶");
        try {
            ECComputer.Config.setAttunedTestOverrides(ECComputer.Config.ATTUNED_DEFAULTS.toBuilder().overload(0.5D, 40, 2400).build());
            expect(problems, "改过的 5 阶", List.of("50", "2", "120"), args(lines.attunementStageLine(chest, 5)));
        } finally {
            ECComputer.Config.setAttunedTestOverrides(null);
        }
        report(helper, "宅男的阶效果行", problems);
        helper.succeed();
    }

    // ================================================================ 风之护甲

    private static ECWindGear.Config.Values wind(boolean deflect, List<Integer> jumps, double sprint, int tailwind) {
        ECWindGear.Config.Values d = ECWindGear.Config.DEFAULTS;
        return new ECWindGear.Config.Values(deflect, d.deflectCooldownTicks(), jumps, d.airJumpVelocity(), d.fallDamageMultiplier(), sprint,
                tailwind, d.tailwindSpeedBonus(), d.tailwindAttackSpeedBonus());
    }

    private static List<String> keys(List<Component> lines) {
        List<String> out = new ArrayList<>();
        for (Component line : lines) {
            out.add(key(line));
        }
        return out;
    }

    /**
     * 「四件穿齐」那段按这件的阶写：3 阶标题带阶数、偏转 2 秒、空中跳 2 次；0 阶标题不带阶数、6 秒、1 次。
     * 各阶那一行：默认 4 秒 / 2 次 10% / 2 秒 1 秒 20% 10%；空中跳填 0 时两段都不写空中跳，偏转关掉时 1 阶只剩潜行、3 阶整行不写；
     * 偏转间隔填 0 时 1、3 阶换成「每一颗都偏开」。
     */
    @GameTest(template = EMPTY)
    public static void windArmorLinesFollowStage(GameTestHelper helper) {
        ItemStack chest = new ItemStack(ECWindGear.WIND_CHESTPLATE.get());
        WindArmorItem item = (WindArmorItem) chest.getItem();
        List<String> problems = new ArrayList<>();
        List<Component> stage3 = new ArrayList<>();
        WindArmorItem.setLines(stage3, 3);
        expect(problems, "3 阶标题", "tooltip.essentialcraft5.wind_armor.set_stage", key(stage3.get(0)));
        expect(problems, "3 阶标题的阶数", List.of("3"), args(stage3.get(0)));
        expect(problems, "3 阶偏转", List.of("2"), args(stage3.get(1)));
        expect(problems, "3 阶空中跳", List.of("2"), args(stage3.get(2)));
        List<Component> stage0 = new ArrayList<>();
        WindArmorItem.setLines(stage0, 0);
        expect(problems, "0 阶标题", "tooltip.essentialcraft5.wind_armor.set", key(stage0.get(0)));
        expect(problems, "0 阶偏转", List.of("6"), args(stage0.get(1)));
        expect(problems, "0 阶空中跳", List.of("1"), args(stage0.get(2)));

        expect(problems, "1 阶", List.of("4"), args(item.attunementStageLine(chest, 1)));
        expect(problems, "2 阶", List.of("2", "10"), args(item.attunementStageLine(chest, 2)));
        expect(problems, "3 阶", List.of("2", "1", "20", "10"), args(item.attunementStageLine(chest, 3)));
        try {
            ECWindGear.Config.setTestOverrides(wind(true, List.of(0), 0.1D, 20));
            List<Component> noJumps = new ArrayList<>();
            WindArmorItem.setLines(noJumps, 2);
            helper.assertFalse(keys(noJumps).contains("tooltip.essentialcraft5.wind_armor.set.air_jump"), "空中跳填 0 还写了空中跳：" + keys(noJumps));
            expect(problems, "空中跳填 0 的 2 阶", "tooltip.essentialcraft5.attunement.wind_armor.stage_2.sprint", key(item.attunementStageLine(chest, 2)));
            ECWindGear.Config.setTestOverrides(wind(true, List.of(0), 0D, 20));
            helper.assertTrue(item.attunementStageLine(chest, 2) == null, "空中跳、疾跑都关了，2 阶那一行应该不写");
            ECWindGear.Config.setTestOverrides(wind(false, ECWindGear.Config.DEFAULT_AIR_JUMPS, 0.1D, 20));
            expect(problems, "偏转关掉的 1 阶", "tooltip.essentialcraft5.attunement.wind_armor.stage_1.no_deflect", key(item.attunementStageLine(chest, 1)));
            helper.assertTrue(item.attunementStageLine(chest, 3) == null, "偏转关了，3 阶（偏转间隔和顺风）那一行应该不写");
            ECWindGear.Config.setTestOverrides(wind(true, ECWindGear.Config.DEFAULT_AIR_JUMPS, 0.1D, 0));
            expect(problems, "顺风关掉的 3 阶", "tooltip.essentialcraft5.attunement.wind_armor.stage_3.no_tailwind", key(item.attunementStageLine(chest, 3)));
            // 偏转间隔填 0 = 每一颗都偏开，「每 0 秒」读不通，换句（同「四件穿齐」那段的 deflect_all）
            ECWindGear.Config.Values d = ECWindGear.Config.DEFAULTS;
            ECWindGear.Config.setTestOverrides(new ECWindGear.Config.Values(true, List.of(0), d.airJumps(), d.airJumpVelocity(),
                    d.fallDamageMultiplier(), d.sprintSpeedBonus(), d.tailwindTicks(), d.tailwindSpeedBonus(), d.tailwindAttackSpeedBonus()));
            expect(problems, "间隔 0 的 1 阶", "tooltip.essentialcraft5.attunement.wind_armor.stage_1.deflect_all", key(item.attunementStageLine(chest, 1)));
            Component allStage3 = item.attunementStageLine(chest, 3);
            expect(problems, "间隔 0 的 3 阶", "tooltip.essentialcraft5.attunement.wind_armor.stage_3.deflect_all", key(allStage3));
            expect(problems, "间隔 0 的 3 阶顺风", List.of("1", "20", "10"), args(allStage3));
            ECWindGear.Config.setTestOverrides(new ECWindGear.Config.Values(true, List.of(0), d.airJumps(), d.airJumpVelocity(),
                    d.fallDamageMultiplier(), d.sprintSpeedBonus(), 0, d.tailwindSpeedBonus(), d.tailwindAttackSpeedBonus()));
            expect(problems, "间隔 0、顺风关掉的 3 阶", "tooltip.essentialcraft5.attunement.wind_armor.stage_3.deflect_all_no_tailwind",
                    key(item.attunementStageLine(chest, 3)));
        } finally {
            ECWindGear.Config.setTestOverrides(null);
        }
        report(helper, "风之护甲的提示", problems);
        helper.succeed();
    }

    // ================================================================ 配方图、手册

    /** 升格配方图下面只写「升格要矩阵 n 阶」（门槛照旧按升到 3 阶算）；普通升阶照旧写「升到 n 阶要矩阵 m 阶」 */
    @GameTest(template = EMPTY)
    public static void ascensionRankNoteForReforging(GameTestHelper helper) {
        Recipe<?> promotion = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id("attunement/verdict_promotion")).orElse(null);
        Recipe<?> stageUp = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id("attunement/ember_sword_1")).orElse(null);
        helper.assertTrue(promotion instanceof AttunementAscensionRecipe && stageUp instanceof AttunementAscensionRecipe,
                "找不到升格 / 升阶配方：" + promotion + " / " + stageUp);
        AttunementAscensionRecipe reforge = (AttunementAscensionRecipe) promotion;
        AttunementAscensionRecipe plain = (AttunementAscensionRecipe) stageUp;
        List<String> problems = new ArrayList<>();
        int reforgeRank = reforge.requiredRank();
        if (reforgeRank > 0) {
            expect(problems, "升格的键", "guide.essentialcraft5.recipe.attunement.rank_promote", key(reforge.rankNote()));
            expect(problems, "升格的参数", List.of(String.valueOf(reforgeRank)), args(reforge.rankNote()));
        } else {
            helper.assertTrue(reforge.rankNote() == null, "矩阵阶关着时升格配方不该写阶要求");
        }
        int plainRank = plain.requiredRank();
        if (plainRank > 0) {
            expect(problems, "普通升阶的键", "guide.essentialcraft5.recipe.attunement.rank", key(plain.rankNote()));
            expect(problems, "普通升阶的参数", List.of(String.valueOf(plain.toStage()), String.valueOf(plainRank)), args(plain.rankNote()));
        }
        report(helper, "配方图的矩阵阶说明", problems);
        helper.succeed();
    }

    /** 配方图徽标：半号字宽 20 以内放在两角之间，再宽挪到下面；表头截断：放得下原样，放不下截尾补省略号、宽不超，连省略号都放不下给空串 */
    @GameTest(template = EMPTY)
    public static void guideTextFitsNarrowPlaces(GameTestHelper helper) {
        helper.assertTrue(GuideTextFit.fitsBetweenCorners(19) && GuideTextFit.fitsBetweenCorners(20), "中文「2 阶 · 满」约 19 像素，应该放得下");
        helper.assertFalse(GuideTextFit.fitsBetweenCorners(21) || GuideTextFit.fitsBetweenCorners(35), "英文 Stage 2 · full 约 35 像素，应该挪下去");
        java.util.function.ToIntFunction<String> width = s -> s.codePointCount(0, s.length()) * 6;
        List<String> problems = new ArrayList<>();
        expect(problems, "放得下", "Contents", GuideTextFit.clip("Contents", 48, width));
        String clipped = GuideTextFit.clip("Contents", 30, width);
        expect(problems, "截断", "Cont" + GuideTextFit.ELLIPSIS, clipped);
        helper.assertTrue(width.applyAsInt(clipped) <= 30, "截完还超宽：" + clipped);
        expect(problems, "截掉尾巴的空格", "目" + GuideTextFit.ELLIPSIS, GuideTextFit.clip("目 录表", 18, width));
        expect(problems, "省略号都放不下", "", GuideTextFit.clip("Contents", 5, width));
        report(helper, "手册窄处放字", problems);
        helper.succeed();
    }

    /**
     * 手册里 {@code addcfg:键} 引用的键都存在、是数字；同调一章裁决之刃那张阶表照引擎的写法算，默认配置下就是设计数值
     * （裁断 3.5% / 50 … 5.5% / 90，断罪冷却 13 / 12 / 11 / 10 秒，对 20 生命的僵尸 0.6）。
     */
    @GameTest(template = EMPTY)
    public static void guideVerdictTableFollowsConfig(GameTestHelper helper) {
        Map<String, String> snapshot = GuideConfigSnapshot.capture();
        List<String> problems = new ArrayList<>();
        for (String language : List.of("zh_cn", "en_us")) {
            String text = guideFile(language, "attunement.json");
            Matcher m = ADDCFG.matcher(text);
            List<String> tokens = new ArrayList<>();
            while (m.find()) {
                String token = m.group(1);
                for (String part : token.split("\\|")) {
                    if (part.startsWith("addcfg:") && !isNumber(snapshot.get(part.substring(7)))) {
                        problems.add(language + "：{cfg:" + token + "} 加的 " + part.substring(7) + " 不是数字配置");
                    }
                }
                if (token.startsWith("general.verdict_blade.")) {
                    tokens.add(token);
                }
            }
            for (String[] row : new String[][]{
                    {"general.verdict_blade.verdict.percent_per_stage|mul:1|addcfg:general.verdict_blade.verdict.percent|pctv", "3.5"},
                    {"general.verdict_blade.verdict.percent_per_stage|mul:5|addcfg:general.verdict_blade.verdict.percent|pctv", "5.5"},
                    {"general.verdict_blade.verdict.cap_per_stage|mul:1|addcfg:general.verdict_blade.verdict.cap", "50"},
                    {"general.verdict_blade.verdict.cap_per_stage|mul:5|addcfg:general.verdict_blade.verdict.cap", "90"},
                    {"general.verdict_blade.judgment.cooldown_per_stage|mul:-2|addcfg:general.verdict_blade.judgment.cooldown|max:0|sec", "260"},
                    {"general.verdict_blade.judgment.cooldown_per_stage|mul:-5|addcfg:general.verdict_blade.judgment.cooldown|max:0|sec", "200"},
                    {"general.verdict_blade.verdict.percent|mul:0.2", "0.6"},
            }) {
                if (!tokens.contains(row[0])) {
                    problems.add(language + "：阶表里找不到 {cfg:" + row[0] + "}");
                    continue;
                }
                double value = evaluate(row[0], snapshot);
                if (Math.abs(value - Double.parseDouble(row[1])) > 1e-6) {
                    problems.add(language + "：{cfg:" + row[0] + "} 默认算出 " + value + "，应为 " + row[1]);
                }
            }
        }
        report(helper, "手册裁决之刃阶表", problems);
        helper.succeed();
    }

    /** 预算行的英文不写死复数名词（预算剩 1 时「1 more points」） */
    @GameTest(template = EMPTY)
    public static void budgetLineEnglishCountsAnyNumber(GameTestHelper helper) {
        String lang = langFile("en_us.json");
        for (String key : List.of("tooltip.essentialcraft5.attunement.budget", "tooltip.essentialcraft5.attunement.budget_static")) {
            JsonElement value = JsonParser.parseString(lang).getAsJsonObject().get(key);
            helper.assertTrue(value != null, "en_us 没有 " + key);
            String text = value.getAsString();
            helper.assertFalse(text.contains("%s more points") || text.contains("%s points"), key + " 写成了「" + text + "」，预算为 1 时单复数不对");
        }
        helper.succeed();
    }

    // ================================================================ 读资源、算格式链

    private static String guideFile(String language, String file) {
        return read(ModList.get().getModFileById(EssentialCraft5.MODID).getFile().findResource("assets", EssentialCraft5.MODID, "guide", language, file));
    }

    private static String langFile(String file) {
        return read(ModList.get().getModFileById(EssentialCraft5.MODID).getFile().findResource("assets", EssentialCraft5.MODID, "lang", file));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("读不了 " + path + "：" + e);
        }
    }

    private static boolean isNumber(@Nullable String value) {
        if (value == null) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 照 {@code client/guide/GuideValues} 的格式链算数（只管这里用到的 mul / add / min / max / addcfg，显示格式不管） */
    private static double evaluate(String token, Map<String, String> snapshot) {
        String[] parts = token.split("\\|");
        double v = Double.parseDouble(snapshot.get(parts[0]));
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            int colon = p.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String op = p.substring(0, colon);
            String arg = p.substring(colon + 1);
            switch (op) {
                case "addcfg" -> v += Double.parseDouble(snapshot.get(arg));
                case "mul" -> v *= Double.parseDouble(arg);
                case "add" -> v += Double.parseDouble(arg);
                case "min" -> v = Math.min(v, Double.parseDouble(arg));
                case "max" -> v = Math.max(v, Double.parseDouble(arg));
                default -> throw new AssertionError("测试没照抄的运算 " + op);
            }
        }
        return v;
    }
}
