package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import com.flameforge.essentialcraft5.item.ComputerSealField;
import com.flameforge.essentialcraft5.network.C2SComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * 饰品封禁场合在一起以后的检查（batch10 §5.4 第 4 条）：手册进度、豁免标签、终端包的未知动作。
 * 进度和标签是静态数据（标签由 tools/tags 生成），要先跑 runData。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CurioSealIntegrationTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "curio_seal_integration";

    private CurioSealIntegrationTests() {
    }

    /**
     * 手册进度 {@code guide/computer/seal_field}：服务器上有；和别的手册进度一样是隐藏进度（没有 display、没有父进度），
     * 只有一个由代码发的条件 {@code awarded_by_code}（第一次在终端里打开封禁场时 {@link ComputerSealField#toggle} 发）。
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealFieldAdvancementExists(GameTestHelper helper) {
        Advancement advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(ComputerSealField.GUIDE_SEAL_FIELD);
        helper.assertTrue(advancement != null, "应有进度 " + ComputerSealField.GUIDE_SEAL_FIELD);
        helper.assertTrue(advancement.getDisplay() == null, "手册进度不显示（没有 display）");
        helper.assertTrue(advancement.getParent() == null, "手册进度没有父进度，实际 "
                + (advancement.getParent() == null ? "-" : advancement.getParent().getId()));
        helper.assertTrue(advancement.getCriteria().keySet().equals(java.util.Set.of("awarded_by_code")),
                "只有 awarded_by_code 一个条件，实际 " + advancement.getCriteria().keySet());
        helper.succeed();
    }

    /**
     * 豁免标签 {@code essentialcraft5:curio_seal_exempt} 存在（本模组只放别的模组的物品，全是可选条目、没装对应模组时是空的；
     * 空标签也要有，数据包才能追加）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealExemptTagExists(GameTestHelper helper) {
        boolean present = BuiltInRegistries.ITEM.getTag(CurioSeal.EXEMPT).isPresent();
        helper.assertTrue(present, "应有物品标签 " + CurioSeal.EXEMPT.location());
        helper.succeed();
    }

    /** 能增加饰品栏位的饰品（腰带之类）默认在豁免标签里，全是可选条目：没装对应模组不报错 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void sealExemptTagListsSlotGivers(GameTestHelper helper) {
        java.util.Set<String> expected = java.util.Set.of(
                "enigmaticlegacy:cursed_ring", "enigmaticlegacy:enchanter_pearl", "enigmaticlegacy:enigmatic_eye",
                "enigmaticlegacy:insignia", "enigmaticlegacy:cosmic_scroll",
                "enigmaticaddons:adventure_charm", "enigmaticaddons:despair_insignia", "enigmaticaddons:forger_gem",
                "l2hostility:infinity_glove", "l2hostility:oddeyes_glasses", "l2hostility:triple_strip_cape",
                "relics:leather_belt", "relics:hunter_belt", "relics:drowned_belt",
                "cataclysm:belt_of_beginner", "cataclysm:belt_of_monstrosity",
                "celestial_artifacts:catastrophe_scroll", "celestial_artifacts:precious_necklace", "celestial_artifacts:precious_bracelet");
        java.util.Set<String> found = new java.util.HashSet<>();
        boolean allOptional = true;
        try (java.io.InputStream stream = CurioSealIntegrationTests.class.getResourceAsStream("/data/essentialcraft5/tags/items/curio_seal_exempt.json")) {
            helper.assertTrue(stream != null, "找不到 curio_seal_exempt.json（要先跑 runData）");
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (com.google.gson.JsonElement element : root.getAsJsonArray("values")) {
                if (!element.isJsonObject()) {
                    allOptional = false;
                    continue;
                }
                com.google.gson.JsonObject entry = element.getAsJsonObject();
                found.add(entry.get("id").getAsString());
                allOptional &= entry.has("required") && !entry.get("required").getAsBoolean();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        helper.assertTrue(found.equals(expected), "豁免标签的默认条目不对，多出 " + diff(found, expected) + "，缺 " + diff(expected, found));
        helper.assertTrue(allOptional, "别的模组的条目都得是 required:false");
        helper.succeed();
    }

    private static java.util.Set<String> diff(java.util.Set<String> a, java.util.Set<String> b) {
        java.util.Set<String> result = new java.util.TreeSet<>(a);
        result.removeAll(b);
        return result;
    }

    /** 终端包的未知动作直接丢弃：不抛异常、不切换封禁场、不回终端数据 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void terminalPacketDropsUnknownAction(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-term-unk");
        List<S2CComputerTerminalPacket> sent = ComputerSealField.captureTerminalForTest(player.getUUID());
        try {
            // 2、3 是 10c 加的模块开关 / 拆下（见 ModulePlatformTests），这里只放没定义的值
            for (byte action : new byte[]{4, 7, 127, -1}) {
                ComputerSealField.handleTerminalPacket(player, new C2SComputerTerminalPacket(action, (byte) 3, (byte) 9));
            }
            helper.assertFalse(ComputerSealField.isOn(player), "未知动作不应打开封禁场");
            helper.assertTrue(sent.isEmpty(), "未知动作不应回终端数据，实际 " + sent.size() + " 个包");
        } finally {
            ComputerSealField.forgetForTest(player);
        }
        helper.succeed();
    }
}
