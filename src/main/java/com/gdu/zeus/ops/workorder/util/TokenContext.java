package com.gdu.zeus.ops.workorder.util;

/**
 * Token上下文,使用ThreadLocal在同一请求线程中传递token
 */
public class TokenContext {
    private static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前线程的token
     */
    public static void setToken(String token) {
        TOKEN_HOLDER.set(token);
    }
    /**
     * 获取当前线程的token
     */
    public static String getToken() {
        return TOKEN_HOLDER.get();
    }

    /**
     * 清除当前线程的token
     */
    public static void clear() {
        TOKEN_HOLDER.remove();
    }
}
