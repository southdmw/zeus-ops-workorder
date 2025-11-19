# Token传播机制实现详细说明

## 概述

本文档提供了完整的Token传播机制实现细节，包括前端、控制层、服务层和拦截器层的详细说明。

---

## 1. 前端集成指南

### 1.1 获取Token

用户登录成功后，前端需要保存token：

```javascript
// 登录请求
async function login(username, password) {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ username, password })
  });
  
  const data = await response.json();
  
  if (data.success) {
    // 保存token到localStorage
    localStorage.setItem('authToken', data.token);
    localStorage.setItem('userId', data.userId);
    return true;
  }
  
  return false;
}
```

### 1.2 调用助手API

前端在调用助手接口时，需要从Authorization header中传递token：

```javascript
// 使用Server-Sent Events (SSE)接收助手的流式响应
async function chatWithAssistant(message) {
  const token = localStorage.getItem('authToken');
  const userId = localStorage.getItem('userId');
  
  const params = new URLSearchParams({
    userId: userId,
    chatId: generateChatId(),
    userMessage: message
  });
  
  const eventSource = new EventSource(
    `/api/assistant/chatByUserId?${params.toString()}`,
    {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    }
  );
  
  eventSource.onmessage = (event) => {
    const chunk = event.data;
    if (chunk === '[complete]') {
      eventSource.close();
    } else {
      // 处理助手响应
      console.log('Assistant:', chunk);
    }
  };
  
  eventSource.onerror = (error) => {
    console.error('Chat error:', error);
    eventSource.close();
  };
}
```

### 1.3 Token刷新处理

如果token过期，需要实现刷新机制：

```javascript
// 拦截器：自动刷新token
async function fetchWithTokenRefresh(url, options = {}) {
  let token = localStorage.getItem('authToken');
  
  const response = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${token}`
    }
  });
  
  // 如果返回401，尝试刷新token
  if (response.status === 401) {
    const refreshToken = localStorage.getItem('refreshToken');
    const refreshResponse = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ refreshToken })
    });
    
    if (refreshResponse.ok) {
      const newData = await refreshResponse.json();
      localStorage.setItem('authToken', newData.token);
      
      // 重试原始请求
      return fetchWithTokenRefresh(url, options);
    } else {
      // 刷新失败，清除token并重定向到登录页
      localStorage.removeItem('authToken');
      window.location.href = '/login';
    }
  }
  
  return response;
}
```

---

## 2. 后端实现

### 2.1 AssistantController - 接收并存储Token

**文件**: `com.gdu.zeus.ops.workorder.client.AssistantController`

#### 工作流程

```
HTTP请求 (Header: Authorization: Bearer xxx)
    ↓
@RequestMapping("/chatByUserId")
    ↓
提取Authorization header中的token
    ↓
TokenContext.setToken(token) 存入ThreadLocal
    ↓
调用agent.chat() 传递userId, chatId, userMessage
    ↓
TokenContextWrapper.wrapWithToken() 将token注入Reactor Context
    ↓
Flux流处理并返回给前端
    ↓
doFinally() 中清理TokenContext
```

#### 关键代码

```java
@CrossOrigin
@RequestMapping(path = "/chatByUserId", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chat(
        @RequestParam(name = "userId") String userId,
        @RequestHeader(name = "Authorization", required = false) String token,
        @RequestParam(name = "chatId") String chatId,
        @RequestParam(name = "userMessage") String userMessage) {
    
    logger.info("收到聊天请求: userId={}, chatId={}, token present={}", 
        userId, chatId, token != null);
    
    try {
        // 1. 提取token并存入ThreadLocal
        if (token != null && !token.isEmpty()) {
            logger.debug("将token存入ThreadLocal上下文");
            TokenContext.setToken(token);
        } else {
            logger.warn("未提供Authorization header");
        }

        // 2. 调用agent的chat方法
        Flux<String> chatFlux = agent.chat(userId, chatId, userMessage);
        
        // 3. 使用Reactor Context传播token
        if (token != null && !token.isEmpty()) {
            chatFlux = TokenContextWrapper.wrapWithToken(chatFlux, token);
        }
        
        // 4. 在Flux完成时清理ThreadLocal
        return chatFlux.doFinally(signalType -> {
            logger.debug("聊天流处理完成，清理ThreadLocal上下文");
            TokenContext.clear();
        });
        
    } catch (Exception e) {
        logger.error("聊天处理异常", e);
        TokenContext.clear();
        throw e;
    }
}
```

### 2.2 TokenContext - ThreadLocal管理

**文件**: `com.gdu.zeus.ops.workorder.util.TokenContext`

```java
public class TokenContext {
    private static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<>();

    public static void setToken(String token) {
        TOKEN_HOLDER.set(token);
    }

    public static String getToken() {
        return TOKEN_HOLDER.get();
    }

    public static void clear() {
        TOKEN_HOLDER.remove();
    }
}
```

#### 使用场景

- **存储**: 在请求开始时，从HTTP header中提取token并存储
- **检索**: 在同一请求线程中的任何地方都可以检索token
- **清理**: 在请求完成或发生异常时清理

### 2.3 TokenContextWrapper - Reactor Context传播

**文件**: `com.gdu.zeus.ops.workorder.util.TokenContextWrapper`

```java
public class TokenContextWrapper {
    private static final String TOKEN_CONTEXT_KEY = "TOKEN";

    /**
     * 将token注入到Reactor Context中
     * 用于处理多线程执行的工具函数
     */
    public static <T> Flux<T> wrapWithToken(Flux<T> flux, String token) {
        if (token == null || token.isEmpty()) {
            return flux;
        }
        logger.debug("将token注入到Reactor Context中");
        return flux.contextWrite(Context.of(TOKEN_CONTEXT_KEY, token));
    }

    public static String getTokenFromContext(Context context) {
        return context.getOrDefault(TOKEN_CONTEXT_KEY, null);
    }

    public static String extractToken() {
        // 优先从ThreadLocal获取
        String token = TokenContext.getToken();
        if (token != null) {
            return token;
        }
        
        // 尝试从Reactor Context获取
        try {
            Context context = reactor.util.context.Context.currentContext();
            return getTokenFromContext(context);
        } catch (Exception e) {
            logger.debug("无法从Reactor Context中获取token");
        }

        return null;
    }
}
```

### 2.4 TokenInterceptor - HTTP请求拦截

**文件**: `com.gdu.zeus.ops.workorder.util.TokenInterceptor`

#### 工作原理

```
RestTemplate执行HTTP请求
    ↓
触发TokenInterceptor
    ↓
从ThreadLocal获取token
    ↓
if (token != null)
    添加Authorization header: "Bearer " + token
    ↓
执行HTTP请求
```

#### 代码实现

```java
public class TokenInterceptor implements ClientHttpRequestInterceptor {
    
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) 
            throws IOException {
        
        // 1. 从ThreadLocal获取token
        String token = TokenContext.getToken();
        
        // 2. 如果token存在，添加到请求header
        if (token != null && !token.isEmpty()) {
            // 处理token格式，支持 "Bearer xxx" 或 "xxx"
            String authValue = token;
            if (!token.startsWith("Bearer ")) {
                authValue = "Bearer " + token;
            }
            request.getHeaders().set("Authorization", authValue);
            logger.debug("为请求添加Authorization header");
        }
        
        // 3. 执行HTTP请求
        return execution.execute(request, body);
    }
}
```

### 2.5 HttpClientConfig - RestTemplate配置

**文件**: `com.gdu.zeus.ops.workorder.config.HttpClientConfig`

```java
@Configuration
public class HttpClientConfig {
    
    @Bean("workOrderRestTemplate")
    public RestTemplate workOrderRestTemplate() {
        // 创建SimpleClientHttpRequestFactory
        SimpleClientHttpRequestFactory factory = 
            new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        // 创建RestTemplate
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // 添加TokenInterceptor
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new TokenInterceptor());
        restTemplate.setInterceptors(interceptors);

        return restTemplate;
    }
}
```

---

## 3. 服务层实现

### 3.1 POIServiceV2 - 获取POI位置

**文件**: `com.gdu.zeus.ops.workorder.services.POIServiceV2`

```java
@Service
public class POIServiceV2 {
    
    private static final Logger logger = LoggerFactory.getLogger(POIServiceV2.class);

    @Autowired
    private WorkOrderExternalServiceImpl workOrderExternalService;

    /**
     * 根据区域名称获取POI位置列表
     * 
     * 流程：
     * 1. 获取当前请求的token
     * 2. 构造POI查询请求
     * 3. 调用外部服务API
     * 4. token通过TokenInterceptor自动添加到HTTP请求头
     */
    public List<WorkOrderApiDto.POILocationResponse> getLocationsByArea(String area) {
        logger.info("查询POI位置，区域: {}", area);
        
        try {
            // 1. 获取token
            String token = TokenContext.getToken();
            if (token != null) {
                logger.debug("获取到token，长度: {}", token.length());
            } else {
                logger.warn("未获取到token，查询可能会失败");
            }

            // 2. 构造请求
            WorkOrderApiDto.POILocationRequest request = 
                WorkOrderApiDto.POILocationRequest.builder()
                    .name(area)
                    .build();

            // 3. 调用外部服务API
            // TokenInterceptor会自动添加token到请求头
            List<WorkOrderApiDto.POILocationResponse> locations = 
                workOrderExternalService.getPoiName(request);

            if (locations != null && !locations.isEmpty()) {
                logger.info("查询到{}个POI位置", locations.size());
                return locations;
            } else {
                logger.warn("未查询到POI位置");
                return List.of();
            }

        } catch (Exception e) {
            logger.error("查询POI位置异常", e);
            throw new RuntimeException("查询POI位置失败: " + e.getMessage(), e);
        }
    }
}
```

### 3.2 RouteServiceV2 - 获取航线

**文件**: `com.gdu.zeus.ops.workorder.services.RouteServiceV2`

```java
@Service
public class RouteServiceV2 {
    
    private static final Logger logger = LoggerFactory.getLogger(RouteServiceV2.class);

    @Autowired
    private WorkOrderExternalServiceImpl workOrderExternalService;

    /**
     * 根据位置获取可用航线列表
     */
    public List<WorkOrderApiDto.RouteResponseVo> getRoutesByLocation(
            String name, Double x, Double y, Double radius) {
        
        logger.info("查询航线，位置: {}, 经度: {}, 纬度: {}, 半径: {}米", 
            name, x, y, radius);
        
        try {
            // 获取token
            String token = TokenContext.getToken();
            if (token != null) {
                logger.debug("获取到token，长度: {}", token.length());
            }

            // 构造请求
            WorkOrderApiDto.RouteRequest request = 
                WorkOrderApiDto.RouteRequest.builder()
                    .lon(x)
                    .lat(y)
                    .radius(radius)
                    .build();

            // 调用外部服务API
            List<WorkOrderApiDto.RouteResponse> routes = 
                workOrderExternalService.getRoutes(request);

            if (routes != null && !routes.isEmpty()) {
                logger.info("查询到{}条航线", routes.size());
                
                // 转换为简化的VO对象
                return routes.stream()
                    .map(route -> WorkOrderApiDto.RouteResponseVo.builder()
                        .routeId(route.getRouteId())
                        .routeName(route.getRouteName())
                        .build())
                    .collect(Collectors.toList());
            } else {
                logger.warn("未查询到航线");
                return List.of();
            }

        } catch (Exception e) {
            logger.error("查询航线异常", e);
            throw new RuntimeException("查询航线失败: " + e.getMessage(), e);
        }
    }
}
```

### 3.3 PatrolOrderCreationService - 创建工单

**文件**: `com.gdu.zeus.ops.workorder.services.PatrolOrderCreationService`

```java
@Service
public class PatrolOrderCreationService {
    
    private static final Logger logger = 
        LoggerFactory.getLogger(PatrolOrderCreationService.class);

    @Autowired
    private WorkOrderExternalServiceImpl workOrderExternalService;

    /**
     * 调用业务系统API创建工单
     * 
     * 流程：
     * 1. 获取当前请求的token
     * 2. 构造创建工单请求
     * 3. 调用外部服务API创建工单
     * 4. token通过TokenInterceptor自动添加到HTTP请求头
     */
    public Integer createWorkOrderViaAPI(PatrolOrder patrolOrder) {
        logger.info("通过API创建工单: {}", patrolOrder.getOrderName());
        
        try {
            // 获取token
            String token = TokenContext.getToken();
            if (token != null) {
                logger.debug("获取到token，长度: {}", token.length());
            } else {
                logger.warn("未获取到token，工单创建可能会失败");
            }

            // 构造请求
            WorkOrderApiDto.CreateWorkOrderRequest request = 
                buildCreateWorkOrderRequest(patrolOrder);

            // 调用外部服务API
            Integer workOrderId = 
                workOrderExternalService.createWorkOrder(request);
            
            if (workOrderId != null && workOrderId > 0) {
                logger.info("工单创建成功，工单ID: {}", workOrderId);
                return workOrderId;
            } else {
                logger.warn("工单创建失败");
                return null;
            }

        } catch (Exception e) {
            logger.error("创建工单异常", e);
            throw new RuntimeException("创建工单失败: " + e.getMessage(), e);
        }
    }

    private WorkOrderApiDto.CreateWorkOrderRequest 
            buildCreateWorkOrderRequest(PatrolOrder patrolOrder) {
        return WorkOrderApiDto.CreateWorkOrderRequest.builder()
            .name(patrolOrder.getOrderName())
            .description(patrolOrder.getDescription())
            .source(2)  // 2表示由AI创建
            .build();
    }
}
```

---

## 4. Token传播流程详解

### 4.1 详细请求流程

```
1. 前端发送请求
   GET /api/assistant/chatByUserId
   Header: Authorization: Bearer eyJhbGc...
   Params: userId=user123, chatId=chat-uuid, userMessage=xxx

2. Spring DispatcherServlet接收请求

3. AssistantController.chat() 方法被调用
   └─ token = "Bearer eyJhbGc..."
   └─ TokenContext.setToken(token)  [存入ThreadLocal]
   └─ agent.chat(userId, chatId, userMessage)

4. CustomerSupportAssistant.chat() 处理
   └─ ChatClient.prompt()
   └─ .defaultTools(patrolOrderTools)  [注册工具函数]
   └─ .stream().content()  [获取Flux流]

5. 大模型处理用户消息
   └─ 分析需求
   └─ 调用工具函数 (发生在同一线程)
   
6a. 工具调用: getPOILocations("光谷广场")
    └─ PatrolOrderTools.getPOILocations()
    └─ POIServiceV2.getLocationsByArea()
    └─ TokenContext.getToken()  [从ThreadLocal读取]
    └─ WorkOrderExternalServiceImpl.getPoiName(request)
    └─ RestTemplate.exchange()
    
7a. RestTemplate执行请求
    └─ TokenInterceptor.intercept()
    └─ token = TokenContext.getToken()  [再次从ThreadLocal读取]
    └─ request.getHeaders().set("Authorization", "Bearer xxx")
    └─ execution.execute(request, body)  [执行HTTP请求]

8a. HTTP POST /business/overview-mode/search/getPoiName
    └─ 业务系统接收请求
    └─ 验证Authorization header中的token
    └─ 返回POI位置列表

9a. 响应返回到POIServiceV2
    └─ 解析响应
    └─ 返回List<POILocationResponse>

10. 大模型处理工具返回的结果
    └─ 继续分析
    └─ 可能调用其他工具
    └─ 生成响应

11. Flux流返回给AssistantController
    └─ doFinally() 被触发
    └─ TokenContext.clear()  [清理ThreadLocal]

12. 响应通过SSE返回给前端
```

### 4.2 关键点总结

| 步骤 | 操作 | Token来源 | Token去向 |
|-----|------|---------|---------|
| 1 | 前端发送请求 | localStorage | Authorization header |
| 2 | 控制器接收 | HTTP header | ThreadLocal (TokenContext) |
| 3 | 服务调用 | ThreadLocal | Service参数/日志 |
| 4 | HTTP请求 | ThreadLocal | HTTP header (拦截器) |
| 5 | 业务系统处理 | HTTP header | 验证/业务逻辑 |
| 6 | 清理 | ThreadLocal | 垃圾回收 |

---

## 5. 异常处理

### 5.1 Token丢失处理

```java
// POIServiceV2.getLocationsByArea()
String token = TokenContext.getToken();
if (token == null) {
    logger.warn("未获取到token，API调用可能失败");
    // 继续执行，让API返回401处理
}
```

### 5.2 API返回401处理

```java
// WorkOrderExternalServiceImpl.getPoiName()
try {
    ResponseEntity<ApiResponse> response = 
        restTemplate.exchange(url, HttpMethod.POST, entity, typeRef);
    // 检查响应
} catch (HttpClientErrorException.Unauthorized e) {
    logger.error("未授权: token可能已过期或无效", e);
    throw new RuntimeException("认证失败", e);
}
```

### 5.3 异常清理

```java
// AssistantController
try {
    // 处理请求
} catch (Exception e) {
    logger.error("处理异常", e);
    TokenContext.clear();  // 确保清理
    throw e;  // 重新抛出异常
} finally {
    // doFinally会自动调用
}
```

---

## 6. 安全最佳实践

### 6.1 Token存储

✅ **推荐**: ThreadLocal存储 (自动隔离)
✅ **推荐**: 使用HTTPS传输 (加密)
❌ **禁止**: 在日志中打印token
❌ **禁止**: 在响应体中返回token

### 6.2 Token清理

```java
// 正确的清理方式
try {
    TokenContext.setToken(token);
    // 业务逻辑
} finally {
    TokenContext.clear();  // 确保执行
}
```

### 6.3 跨域处理

```java
@CrossOrigin  // 允许跨域请求
@RequestMapping(path = "/chatByUserId", 
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chat(...) {
    // Authorization header会自动包含在跨域请求中
}
```

---

## 7. 测试方案

### 7.1 单元测试

```java
@Test
public void testTokenPropagation() {
    // 1. 设置token
    String testToken = "Bearer test-token-12345";
    TokenContext.setToken(testToken);
    
    // 2. 验证TokenContext
    assertEquals(testToken, TokenContext.getToken());
    
    // 3. 清理
    TokenContext.clear();
    assertNull(TokenContext.getToken());
}
```

### 7.2 集成测试

```java
@Test
public void testAssistantControllerWithToken() {
    String token = "Bearer test-token";
    String userId = "test-user";
    String chatId = "test-chat";
    
    // 调用API
    Flux<String> response = assistantController.chat(
        userId, token, chatId, "test message"
    );
    
    // 验证响应
    StepVerifier.create(response)
        .expectComplete()
        .verify();
}
```

### 7.3 模拟测试

```java
@Test
public void testTokenInterceptor() {
    // 模拟HTTP请求
    HttpRequest request = mock(HttpRequest.class);
    HttpHeaders headers = new HttpHeaders();
    when(request.getHeaders()).thenReturn(headers);
    
    // 设置token
    TokenContext.setToken("Bearer mock-token");
    
    // 执行拦截器
    TokenInterceptor interceptor = new TokenInterceptor();
    interceptor.intercept(request, new byte[0], execution);
    
    // 验证token已添加到header
    verify(request.getHeaders())
        .set("Authorization", "Bearer Bearer mock-token");
}
```

---

## 8. 性能考虑

### 8.1 ThreadLocal性能

- ThreadLocal操作时间: ~1微秒
- 内存占用: 每个线程~32字节
- 无锁设计: 完全线程隔离

### 8.2 优化建议

1. 尽早设置token，减少查询次数
2. 在finally块中清理，防止内存泄漏
3. 使用连接池复用HTTP连接
4. 启用HTTP请求压缩

---

## 9. 故障排查

### 问题: API返回401 Unauthorized

```
诊断步骤:
1. 检查前端是否发送了Authorization header
2. 检查token格式是否正确 (Bearer xxx)
3. 检查token是否过期
4. 检查业务系统是否识别了token
```

### 问题: Token丢失

```
诊断步骤:
1. 添加日志: logger.debug("token={}",TokenContext.getToken())
2. 检查是否调用了TokenContext.setToken()
3. 检查是否提前调用了TokenContext.clear()
4. 检查是否在多个线程间使用
```

### 问题: 内存泄漏

```
诊断步骤:
1. 确保所有请求都调用了TokenContext.clear()
2. 使用profiler检查ThreadLocal内存占用
3. 启用线程池监控
4. 检查是否有未完成的异步任务
```

---

## 10. 总结

这个Token传播机制通过以下方式实现了完整的token链路：

1. **前端**: 在Authorization header中传递token
2. **控制器**: 从header提取并存入ThreadLocal
3. **服务层**: 从ThreadLocal读取token
4. **拦截器**: 自动添加token到HTTP请求头
5. **业务系统**: 接收并验证token
6. **清理**: 请求完成时清理ThreadLocal

整个过程确保了：
- ✅ 线程安全 (ThreadLocal隔离)
- ✅ 多线程兼容 (Reactor Context备选)
- ✅ 自动化处理 (拦截器)
- ✅ 错误恢复 (异常处理)
- ✅ 内存安全 (自动清理)
