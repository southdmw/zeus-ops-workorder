package com.gdu.zeus.ops.workorder.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

/**
 * Token上下文包装器
 * 用于在Reactor Flux中传播TokenContext
 */
public class TokenContextWrapper {

    private static final Logger logger = LoggerFactory.getLogger(TokenContextWrapper.class);
    private static final String TOKEN_CONTEXT_KEY = "TOKEN";

    /**
     * 将token注入到Reactor Context中
     */
    public static <T> Flux<T> wrapWithToken(Flux<T> flux, String token) {
        if (token == null || token.isEmpty()) {
            logger.debug("Token为空，直接返回原Flux");
            return flux;
        }

        logger.debug("将token注入到Reactor Context中");
        
        return flux.contextWrite(Context.of(TOKEN_CONTEXT_KEY, token));
    }

    /**
     * 从Reactor Context中获取token
     */
    public static String getTokenFromContext(Context context) {
        return context.getOrDefault(TOKEN_CONTEXT_KEY, null);
    }

    /**
     * 获取token，优先从ThreadLocal获取，然后尝试从Reactor Context获取
     */
    public static String extractToken() {
        // 优先从ThreadLocal获取(适用于同线程执行)
        String token = TokenContext.getToken();
        if (token != null) {
            return token;
        }
        
        // 如果线程不同，尝试从Reactor Context获取
        try {
            Context context = reactor.util.context.Context.currentContext();
            token = getTokenFromContext(context);
            if (token != null) {
                logger.debug("从Reactor Context中获取到token");
                return token;
            }
        } catch (Exception e) {
            logger.debug("无法从Reactor Context中获取token: {}", e.getMessage());
        }

        return null;
    }
}
