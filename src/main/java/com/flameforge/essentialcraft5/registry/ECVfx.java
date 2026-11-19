package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.config.VfxClientConfig;

/**
 * 特效底座模块（ECVfx）：自带着色器、渲染类型、发光粒子、屏幕效果都在 client/vfx（只在客户端加载，自带事件订阅，不用接线）。
 * 这里只登记 CLIENT 配置 [vfx]（{@link VfxClientConfig}，common 类）。没有方块、物品、网络包，服务端什么都不加。
 */
public final class ECVfx {

    private ECVfx() {
    }

    public static void init() {
        ECConfig.client(VfxClientConfig::define);
    }
}
