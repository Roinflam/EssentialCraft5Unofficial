package com.flameforge.essentialcraft5.client.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 流程教程的两条规则：条目 {@code optional} 怎么读（恒可选 / 恒必做 / 按条件可选），以及分类格子上的 x/y 和「下一步」怎么数
 *
 * <p>不带 {@code @OnlyIn}：只用 Gson。界面（{@link GuideView} 的打勾状态表）和 GameTest 服务器（gametest/GuideDataTests
 * 拿手册文件和真实配置快照算 x/y 与「下一步」）走的是同一份规则，测试守的就是界面里的行为。</p>
 *
 * <h3>为什么要「按条件可选」</h3>
 * <p>有的步骤只在某个开关开着时才是主线必经的：风之药水、受眷顾、风之石板在海纳尔之门开着时是去海纳尔的前提，
 * 门关着时第十六章整章藏掉，它们就没有用处了。只写 {@code true / false} 的话，要么门开着时首页不指它们，
 * 要么门关着时首页把玩家指去做一件没用、还可能只靠运气才做得到的事。所以 {@code optional} 也收条件，写法和 {@code if} 一样。</p>
 */
public final class GuideSteps {

    private GuideSteps() {}

    /**
     * 条目的 {@code optional} 字段
     *
     * <ul>
     *   <li>{@code true} / {@code false} / 不写：照旧，恒为可选 / 恒为必做。</li>
     *   <li>字符串或字符串数组：<b>按条件可选</b>，写法和 {@code if} 完全一样（{@link GuideConditions}：数组 = 全部成立，
     *       {@code ||} = 任一成立，可带 {@code !}）。条件成立时这一步是可选的，不成立时是必做的。
     *       例：{@code "optional": "!cfg:general.hoanna.portal_enabled"} = 海纳尔之门关着时可选、开着时必做。
     *       判定不了（键不存在、写错）时和 {@code if} 一样按不成立处理，也就是当必做 —— 内容由校验脚本查键，
     *       客户端拿到的又是服务端下发的配置快照，正常不会走到这一步。</li>
     * </ul>
     * 只对写了 {@code complete} 的条目有意义（validate_guide.py 查）。
     *
     * @param always 恒为可选（{@code true}）
     * @param cond   按条件可选的条件；空表 = 不是按条件可选
     */
    public record OptionalRule(boolean always, List<String> cond) {

        /** {@code false} 或不写 */
        public static final OptionalRule NEVER = new OptionalRule(false, List.of());
        /** {@code true} */
        public static final OptionalRule ALWAYS = new OptionalRule(true, List.of());

        public OptionalRule {
            cond = List.copyOf(cond);
        }

        /**
         * @return 是不是按条件可选（结果随配置变）
         */
        public boolean conditional() {
            return !always && !cond.isEmpty();
        }

        /**
         * 当前配置下这一步是不是可选的
         *
         * @param test 条件求值：界面里是 {@code GuideValues::test}，测试里是 {@link GuideConditions#test(List)}
         * @return 是否可选
         */
        public boolean test(@Nonnull Predicate<List<String>> test) {
            return always || !cond.isEmpty() && test.test(cond);
        }

        /**
         * 解析 JSON 里的 {@code optional}
         *
         * <p>宽容：字符串 {@code "true"} / {@code "false"}（不分大小写）按布尔读 —— 手写资源包里常见，
         * 当成条件的话会被当作写错的条件、变成必做，和作者的本意正好相反。数字、对象、空串、空数组报一次问题、按必做读
         * （旧版的读法 {@code getAsBoolean} 对它们也是 false）。</p>
         *
         * @param el  {@code optional} 字段（可空 = 没写）
         * @param bad 有问题时调用（参数是给日志的说明，不含位置）
         * @return 解析结果
         */
        @Nonnull
        public static OptionalRule parse(@Nullable JsonElement el, @Nonnull Consumer<String> bad) {
            if (el == null || el.isJsonNull()) {
                return NEVER;
            }
            if (el.isJsonPrimitive()) {
                JsonPrimitive p = el.getAsJsonPrimitive();
                if (p.isBoolean()) {
                    return p.getAsBoolean() ? ALWAYS : NEVER;
                }
                if (p.isString()) {
                    String s = p.getAsString().trim();
                    String lower = s.toLowerCase(Locale.ROOT);
                    if (lower.equals("true")) {
                        return ALWAYS;
                    }
                    if (lower.equals("false")) {
                        return NEVER;
                    }
                    if (s.isEmpty()) {
                        bad.accept("optional 是空串，按必做处理");
                        return NEVER;
                    }
                    return new OptionalRule(false, List.of(s));
                }
                bad.accept("optional 要写 true / false 或条件（写法同 if），「" + p + "」按必做处理");
                return NEVER;
            }
            if (el.isJsonArray()) {
                if (el.getAsJsonArray().isEmpty()) {
                    bad.accept("optional 是空数组，按必做处理");
                    return NEVER;
                }
                List<String> cond = GuideConditions.parse(el, () -> { });
                if (cond.contains(GuideConditions.INVALID)) {
                    // 数组里混了对象 / 数组 / null：那一项必然不成立，整组也就不成立 —— 和 if 的处理一样，结果是必做
                    // （数字、布尔这类基本值和 if 一样按字符串读，读出来是不认识的条件，同样不成立）
                    bad.accept("optional 数组里只能写字符串条件，有一项不是，整组按不成立（必做）处理");
                }
                return new OptionalRule(false, cond);
            }
            bad.accept("optional 要写 true / false 或条件（写法同 if），对象按必做处理");
            return NEVER;
        }
    }

    /**
     * 一章的步骤计数：首页格子角标的 x/y
     *
     * <p>有必做步骤时数必做步骤；全是可选步骤的分类（支线）数全部步骤；一步都没有时 {@link #total()} 为 -1（格子写「n 篇」）。
     * 「必做」按当前配置算（{@link OptionalRule#test}），所以同一章在门开、门关时的 y 可以不一样。</p>
     */
    public static final class Tally {
        private int required;
        private int requiredDone;
        private int steps;
        private int stepsDone;

        /** 清零（界面每半秒重算一次，复用同一个对象） */
        public void reset() {
            required = 0;
            requiredDone = 0;
            steps = 0;
            stepsDone = 0;
        }

        /**
         * 按全书顺序记一个可见条目
         *
         * @param step     写了 {@code complete}（流程里的一步）
         * @param required 必做：是步骤、且当前配置下不是可选的
         * @param done     这一步做完了
         * @return 能不能当「下一步」：必做且还没做完。全书第一个返回 true 的条目就是「下一步」
         */
        public boolean add(boolean step, boolean required, boolean done) {
            if (!step) {
                return false;
            }
            steps++;
            if (done) {
                stepsDone++;
            }
            if (!required) {
                return false;
            }
            this.required++;
            if (done) {
                requiredDone++;
            }
            return !done;
        }

        public int required() {
            return required;
        }

        public int requiredDone() {
            return requiredDone;
        }

        public int steps() {
            return steps;
        }

        public int stepsDone() {
            return stepsDone;
        }

        /** @return 格子上的 y：有必做步骤数必做步骤，没有就数全部步骤；一步都没有为 -1 */
        public int total() {
            return required > 0 ? required : steps > 0 ? steps : -1;
        }

        /** @return 格子上的 x，和 {@link #total()} 数的是同一批 */
        public int doneCount() {
            return required > 0 ? requiredDone : stepsDone;
        }
    }
}
