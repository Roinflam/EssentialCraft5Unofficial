package com.flameforge.essentialcraft5.entity.boss.seal;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 天灾封印的混入插件（{@code essentialcraft5.seal.mixins.json} 的 {@code plugin}）：改好字节码以后核对注入点到没到位，记进
 * {@link CalamitySealMixinStatus}。{@code EnchantmentHelper} 要有三个方法调到本模组的处理方法（名字带 {@code ec5$seal}）；
 * {@code ItemStack} 要有补上的两个 Forge 方法、并且调到 {@link CalamitySeal}。没到位（别的模组改了同一处之类）时封印整个停用，免得只封一半。
 * 这个类在混入准备阶段就被加载，只引用 ASM / Mixin 和 {@link CalamitySealMixinStatus}，不碰游戏类。
 */
public final class CalamitySealMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("essentialcraft5/calamity_seal");
    private static final String HANDLER_MARK = "ec5$seal";
    private static final String SEAL_CLASS = "com/flameforge/essentialcraft5/entity/boss/seal/CalamitySeal";

    @Override
    public void onLoad(String mixinPackage) {
        CalamitySealMixinStatus.configLoaded = true;
    }

    @Override
    public String getRefMapperConfig() {
        // 没有 refmap：原版方法两个名字都写在注解里，Forge 的方法名不混淆
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
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
        if (mixinClassName.endsWith(".EnchantmentHelperSealMixin")) {
            int patched = 0;
            for (MethodNode method : targetClass.methods) {
                if (!method.name.contains(HANDLER_MARK) && calls(method, targetClass.name, HANDLER_MARK)) {
                    patched++;
                }
            }
            CalamitySealMixinStatus.helperMethods = patched;
            if (patched < CalamitySealMixinStatus.HELPER_EXPECTED) {
                LOGGER.warn("Calamity Seal: only {} of {} EnchantmentHelper hooks applied; the seal is disabled.",
                        patched, CalamitySealMixinStatus.HELPER_EXPECTED);
            }
        } else if (mixinClassName.endsWith(".ItemStackSealMixin")) {
            boolean level = false;
            boolean all = false;
            for (MethodNode method : targetClass.methods) {
                if ("getEnchantmentLevel".equals(method.name) && "(Lnet/minecraft/world/item/enchantment/Enchantment;)I".equals(method.desc)) {
                    level = calls(method, SEAL_CLASS, "level");
                } else if ("getAllEnchantments".equals(method.name) && "()Ljava/util/Map;".equals(method.desc)) {
                    all = calls(method, SEAL_CLASS, "filterEffects");
                }
            }
            CalamitySealMixinStatus.stackMethods = (level ? 1 : 0) + (all ? 1 : 0);
            if (!level || !all) {
                LOGGER.warn("Calamity Seal: ItemStack hooks missing (level={}, all={}); the seal is disabled.", level, all);
            }
        }
    }

    /** 方法里有没有调 owner 上名字含 mark 的方法 */
    private static boolean calls(MethodNode method, String owner, String mark) {
        if (method.instructions == null) {
            return false;
        }
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.contains(mark)) {
                return true;
            }
        }
        return false;
    }
}
