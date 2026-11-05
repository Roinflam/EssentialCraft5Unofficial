package com.flameforge.essentialcraft5.worldgen.hoanna;

import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaNature;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.util.List;

/**
 * 海纳尔自己的地表规则（第六批 6a；框架 F6a 建空实现，M1「群系与海纳尔植物」填，batch6a.md §5.1）。
 * <p>
 * datagen（{@code datagen/ECWorldgenProvider} → {@link HoannaTerrain#surfaceRule}）把这里返回的规则<b>整个拼在原版主世界规则
 * {@code SurfaceRuleData.overworld()} 之前</b>：{@code sequence(本列表…, 原版主世界规则)}，按顺序第一个命中的生效。改了要重跑 runData
 * （噪声设置 {@code essentialcraft5:hoanna} 是生成物），GameTest {@code HoannaNatureTests.hoannaSurfaceRulesWrapped} 查生成物跟这里一致。
 * <ul>
 *   <li><b>每条规则自己包一层 {@code SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), …)}</b>：原版的序列是
 *       「底部基岩 → above_preliminary_surface 里的地表 → 深板岩」（{@code SurfaceRuleData} L84~95），拼在最前面的规则不包的话，
 *       {@code stone_depth floor} 会把地下洞穴的地面也铺成恐惧泥土 / 沙子；底部基岩在 y −64~−60，不会被地表条件命中。</li>
 *   <li>条件写法照原版（{@code SurfaceRuleData.overworldLike} L60~120）：{@code ON_FLOOR} = 地表最上面一格，{@code UNDER_FLOOR} = 往下到地表深度（约 3~6 格，
 *       原作 1.12 的填充深度约 3~5），{@code DEEP_UNDER_FLOOR} = 再多 0~6 格（secondary_depth_range 6；原版海滩用它，原版沙漠用 30 格的
 *       {@code VERY_DEEP_UNDER_FLOOR}。计划定 6：1.12 原作沙漠沙子下面的砂岩只有 0~3 格 + 高出海平面的格数，海纳尔沙漠用不着 30 格厚的砂岩）；
 *       {@code ON_CEILING} = 这一格下面就是洞（沙子会掉，原版在这里换成砂岩 / 石头）。</li>
 *   <li>三个群系（其余四个腐化之地不写，交给原版规则：地表草方块、下面泥土、水下深处沙砾）：
 *     <ul>
 *       <li>恐惧之地：{@code UNDER_FLOOR} → 恐惧泥土（原作 topBlock = fillerBlock = dreadDirt），水下也是；</li>
 *       <li>海纳尔沙漠：{@code UNDER_FLOOR} → 沙子（下面是洞的那格砂岩），再往下 {@code DEEP_UNDER_FLOOR} → 砂岩（原作 topBlock = fillerBlock = 沙子，1.12 在沙子下面自动垫砂岩）；</li>
 *       <li>海纳尔之海：水下的地表一格 → 沙砾（下面是洞的那格石头，同原版）；露出水面的小岛、浅滩下面照原版（草方块、泥土）。</li>
 *     </ul></li>
 *   <li>前提：噪声设置的 {@code default_block = minecraft:stone}（主世界矿替换 {@code #stone_ore_replaceables}、上古遗迹笼子判定石头都靠它），
 *       {@code default_fluid = minecraft:water}、海平面 63。</li>
 *   <li>必须返回 {@code List}（可以为空）：{@code SurfaceRules.sequence} 零参数会抛「Need at least 1 rule」，拼接由调用方做。</li>
 *   <li>方块状态按 {@code ECHoannaNature.DREAD_DIRT.get()} 取：只在 runData（和测试）时调用，方块早已注册。</li>
 * </ul>
 */
public final class HoannaSurfaceRules {

    private HoannaSurfaceRules() {
    }

    /** 恐惧之地 → 恐惧泥土、海纳尔沙漠 → 沙 / 砂岩、海纳尔之海 → 水下沙砾（§5.1）；每条都包 abovePreliminarySurface */
    public static List<SurfaceRules.RuleSource> rules() {
        SurfaceRules.RuleSource dreadDirt = state(ECHoannaNature.DREAD_DIRT.get().defaultBlockState());
        SurfaceRules.RuleSource sand = state(Blocks.SAND.defaultBlockState());
        SurfaceRules.RuleSource sandstone = state(Blocks.SANDSTONE.defaultBlockState());
        SurfaceRules.RuleSource gravel = state(Blocks.GRAVEL.defaultBlockState());
        SurfaceRules.RuleSource stone = state(Blocks.STONE.defaultBlockState());
        return List.of(
                surface(SurfaceRules.ifTrue(SurfaceRules.isBiome(ECHoanna.DREADLANDS),
                        SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, dreadDirt))),
                surface(SurfaceRules.ifTrue(SurfaceRules.isBiome(ECHoanna.HOANNA_DESERT), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.ON_CEILING, sandstone), sand)),
                        SurfaceRules.ifTrue(SurfaceRules.DEEP_UNDER_FLOOR, sandstone)))),
                surface(SurfaceRules.ifTrue(SurfaceRules.isBiome(ECHoanna.HOANNA_SEA),
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                                // waterBlockCheck(−1, 0) = 「这一格不在水面以下」（原版陆地地表的条件）；取反 = 水下的地表
                                SurfaceRules.ifTrue(SurfaceRules.not(SurfaceRules.waterBlockCheck(-1, 0)),
                                        SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.ON_CEILING, stone), gravel))))));
    }

    private static SurfaceRules.RuleSource surface(SurfaceRules.RuleSource rule) {
        return SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), rule);
    }

    private static SurfaceRules.RuleSource state(BlockState state) {
        return SurfaceRules.state(state);
    }
}
