package com.flameforge.essentialcraft5.compat.curio;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 饰品压制核心的混入插件（{@code essentialcraft5.mixins.json} 的 {@code plugin}）：
 * <ul>
 *   <li><b>版本门</b>：五个注入目标都是 Curios 的内部类（不在它的 api 里），只对 5.14.x 打补丁。别的版本一律不打，
 *       封禁场显示「不可用」，游戏照常进（配置里 {@code defaultRequire = 0}，注入点对不上也不崩）。</li>
 *   <li><b>核对</b>：{@link #postApply} 在改好的字节码里数每个目标方法调到本模组处理方法（名字带 {@code ec5$}）的次数，
 *       记进 {@link CurioSealMixinStatus}；{@link CurioSeal} 在 {@code FMLLoadCompleteEvent} 里强制加载五个目标类再看结果。</li>
 * </ul>
 * 这个类在混入准备阶段就被加载，只引用 fmlloader / ASM / Mixin 和 {@link CurioSealMixinStatus}，不碰游戏类。
 */
public final class CurioSealMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("essentialcraft5/curio_seal");
    private static final String MIXIN_PACKAGE = "com.flameforge.essentialcraft5.mixin.";
    /** 5.14、5.14.1、5.14.1+1.20.1 都算 */
    private static final Pattern SUPPORTED = Pattern.compile("^5\\.14(\\D.*)?$");
    /** 混入后的处理方法名里带着原名（例：modify$zza000$ec5$maskSealed） */
    private static final String HANDLER_MARK = "ec5$";

    @Override
    public void onLoad(String mixinPackage) {
        String version = curiosVersion();
        CurioSealMixinStatus.curiosVersion = version == null ? "" : version;
        boolean ok = version != null && SUPPORTED.matcher(version).matches();
        CurioSealMixinStatus.gate = ok ? CurioSealMixinStatus.Gate.ACCEPTED : CurioSealMixinStatus.Gate.REJECTED;
        if (!ok) {
            LOGGER.warn("Curios {} is not 5.14.x; the Curio Seal Field is unavailable (no Curios classes are patched).",
                    version == null ? "(not found)" : version);
        }
    }

    /** 读 Curios 的版本串；读不到返回 null */
    private static String curiosVersion() {
        try {
            LoadingModList list = LoadingModList.get();
            if (list == null) {
                return null;
            }
            ModFileInfo file = list.getModFileById("curios");
            if (file == null) {
                return null;
            }
            for (IModInfo mod : file.getMods()) {
                if ("curios".equals(mod.getModId())) {
                    // ArtifactVersion 解析不了「5.14.1+1.20.1」（会把整串当限定符，主版本读成 0），直接比字符串
                    return mod.getVersion().toString();
                }
            }
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Could not read the Curios version", e);
        }
        return null;
    }

    @Override
    public String getRefMapperConfig() {
        // 目标全是 Curios / Forge 自己声明的成员，不经重映射，没有 refmap
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return CurioSealMixinStatus.gate == CurioSealMixinStatus.Gate.ACCEPTED;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        String mixin = mixinClassName.startsWith(MIXIN_PACKAGE) ? mixinClassName.substring(MIXIN_PACKAGE.length()) : mixinClassName;
        List<String> expected = CurioSealMixinStatus.EXPECTED.get(mixin);
        if (expected == null) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String method : expected) {
            if (handlerCalls(targetClass, method) == 0) {
                missing.add(method);
            }
        }
        CurioSealMixinStatus.record(mixin, missing);
        if (!missing.isEmpty()) {
            LOGGER.warn("Curio Seal Field: {} could not patch {}; the field will be unavailable.", mixin, missing);
        }
    }

    /** 目标类里这个方法（名字 + 描述符）调用本模组处理方法的次数；方法不存在返回 0 */
    static int handlerCalls(ClassNode targetClass, String nameAndDesc) {
        int paren = nameAndDesc.indexOf('(');
        String name = nameAndDesc.substring(0, paren);
        String desc = nameAndDesc.substring(paren);
        int calls = 0;
        for (MethodNode method : targetClass.methods) {
            if (!method.name.equals(name) || !method.desc.equals(desc)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(targetClass.name) && call.name.contains(HANDLER_MARK)) {
                    calls++;
                }
            }
        }
        return calls;
    }
}
