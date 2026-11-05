package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.client.hoanna.HoannaClientEvents;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;

/**
 * 风之试炼（第十批 10b-M1）在客户端要读的东西。只在客户端经 DistExecutor 调，专用服不会加载这个类。
 */
public final class WindTrialClient {

    private WindTrialClient() {
    }

    /** 本人所在的海纳尔正在黑暗天灾中（同步来的天灾状态）：风之号角在客户端先判一遍「夜里或黑暗天灾」用 */
    public static boolean hoannaDarkness() {
        return HoannaClientEvents.inHoanna() && HoannaClientEvents.current() == HoannaEvent.DARKNESS;
    }
}
