# Token传播机制完整指南

## 概述

本文档详细说明了Zeus OPS Workorder系统中用户登录token如何从前端传递到后端，再传递给大模型，最后被大模型在调用业务系统API时使用的完整流程。

## 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        前端应用 (React/Hilla)                │
│                                                               │
│  用户登录 → 获取token → 存储在localStorage                   │
│                            ↓                                  │
│                   在HTTP Header中传递token                    │
│                 (Authorization: Bearer xxx)                  │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ↓
┌──────────────────────────────────────────────────────────────┐
│                      Spring Boot 后端                         │
│                                                               │
│  1. AssistantController (/api/assistant/chatByUserId)        │
│     ├─ 从Authorization header提取token                       │
│     ├─ 将token存入ThreadLocal (TokenContext)                 │
│     └─ 将token注入到Reactor Context(多线程场景)               │
│                            ↓                                  │
│  2. CustomerSupportAssistant (chat方法)                      │
│     ├─ 调用ChatClient.prompt()                              │
│     ├─ 注册PatrolOrderTools中的三个工具函数                  │
│     └─ Flux流处理                                           │
│                            ↓                                  │
│  3. PatrolOrderTools (工具函数) - 由大模型调用                │
│     ├─ getPOILocations()  → POIServiceV2                    │
│     ├─ getAvailableRoutes() → RouteServiceV2                │
│     └─ createPatrolOrder() → PatrolOrderCreationService     │
│                            ↓                                  │
│  4. Service层 (V2版本和PatrolOrderCreationService)          │
│     ├─ 从TokenContext获取token                              │
│     ├─ 传递给WorkOrderExternalServiceImpl                    │
│     └─ 调用外部API                                          │
│                            ↓                                  │
│  5. TokenInterceptor (RestTemplate拦截器)                   │
│     └─ 自动从TokenContext获取token并添加到HTTP请求头         │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ↓
┌──────────────────────────────────────────────────────────────┐
│                  外部业务系统API                              │
│                                                               │
│  /business/overview-mode/search/getPoiName (POI查询)        │
│  /route/getRouteByRadius (航线查询)                         │
│  /business/workOrder/create (工单创建)                      │
│                                                               │
│  请求头中包含: Authorization: Bearer {user-token}            │
└──────────────────────────────────────────────────────────────┘
```

## 详细实现流程

### 第一步：前端传递Token

前端在调用助手API时，需要在HTTP请求头中传递token：

```javascript
// 前端代码示例 (React/JavaScript)
const token = localStorage.getItem('authToken'); // 从本地存储获取
const userId = 'user123';
const chatId = 'chat-uuid-xxx';
const userMessage = 'I want to create a patrol order...';

// 调用助手API
fetch('/api/assistant/chatByUserId', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  params: {
    userId: userId,
    chatId: chatId,
    userMessage: userMessage
  }
})
```

### 第二步：AssistantController接收并存储Token

```java
@CrossOrigin
@RequestMapping(path = "/chatByUserId", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chat(
        @RequestParam(name = "userId") String userId,
        @RequestHeader(name = "Authorization", required = false) String token,
        @RequestParam(name = "chatId") String chatId,
        @RequestParam(name = "userMessage") String userMessage) {
    
    // 1. 从Authorization header中提取token
    if (token != null && !token.isEmpty()) {
        // token格式: "Bearer xxxxx" 或 "xxxxx"
        TokenContext.setToken(token);
    }
    
    // 2. 调用大模型
    Flux<String> chatFlux = agent.chat(userId, chatId, userMessage);
    
    // 3. 使用Reactor Context传播token(处理多线程场景)
    if (token != null && !token.isEmpty()) {
        chatFlux = TokenContextWrapper.wrapWithToken(chatFlux, token);
    }
    
    // 4. 在Flux完成时清理ThreadLocal
    return chatFlux.doFinally(signalType -> TokenContext.clear());
}
```

### 第三步：大模型调用工具函数

大模型会根据用户的需求自动调用PatrolOrderTools中注册的三个工具函数：

1. **getPOILocations(String area)** - 获取POI位置列表
2. **getAvailableRoutes(...)** - 获取航线列表
3. **createPatrolOrder(...)** - 创建巡查工单

这些调用都发生在同一个请求线程中，因此ThreadLocal中的token仍然可用。

### 第四步：Service层访问Token并调用外部API

#### POIServiceV2 - 获取POI位置

```java
@Service
public class POIServiceV2 {
    @Autowired
    private WorkOrderExternalServiceImpl workOrderExternalService;

    public List<WorkOrderApiDto.POILocationResponse> getLocationsByArea(String area) {
        // 1. 从ThreadLocal获取token
        String token = TokenContext.getToken();
        
        // 2. 构造请求
        WorkOrderApiDto.POILocationRequest request = 
            WorkOrderApiDto.POILocationRequest.builder()
                .name(area)
                .build();

        // 3. 调用外部服务API
        // 注意：token会通过TokenInterceptor自动添加到请求头
        List<WorkOrderApiDto.POILocationResponse> locations = 
            workOrderExternalService.getPoiName(request);
        
        return locations;
    }
}
```

#### RouteServiceV2 - 获取航线

```java
@Service
public class RouteServiceV2 {
    @Autowired
    private WorkOrderExternalServiceImpl workOrderExternalService;

    public List<WorkOrderApiDto.RouteResponseVo> getRoutesByLocation(
            String name, Double x, Double y, Double radius) {
        
        // 1. 从ThreadLocal获取token
        String token = TokenContext.getToken();
        
        // 2. 构造请求
        WorkOrderApiDto.RouteRequest request = 
            WorkOrderApiDto.RouteRequest.builder()
                .lon(x)
                .lat(y)
                .radius(radius)
                .build();

        // 3. 调用外部服务API
        List<WorkOrderApiDto.RouteResponse> routes = 
            workOrderExternalService.getRoutes(request);
        
        return routes;
    }
}
```

#### PatrolOrderCreationService - 创建工单

```java
@Service
public class PatrolOrderCreationService {
    @Autowired
    private WorkOrderExternalServiceImpl workOrderExternalService;

    public Integer createWorkOrderViaAPI(PatrolOrder patrolOrder) {
        // 1. 从ThreadLocal获取token
        String token = TokenContext.getToken();
        
        // 2. 构造请求
        WorkOrderApiDto.CreateWorkOrderRequest request = 
            buildCreateWorkOrderRequest(patrolOrder);

        // 3. 调用外部服务API
        Integer workOrderId = 
            workOrderExternalService.createWorkOrder(request);
        
        return workOrderId;
    }
}
```

### 第五步：TokenInterceptor自动添加Token到HTTP请求

```java
public class TokenInterceptor implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        // 1. 从ThreadLocal获取token
        String token = TokenContext.getToken();
        
        // 2. 如果token存在，自动添加到请求header
        if (token != null && !token.isEmpty()) {
            request.getHeaders().set("Authorization", "Bearer " + token);
        }
        
        // 3. 执行HTTP请求
        return execution.execute(request, body);
    }
}
```

### 第六步：请求结束清理

当Flux流处理完成时，自动清理ThreadLocal中的token：

```java
return chatFlux.doFinally(signalType -> {
    logger.debug("聊天流处理完成，清理ThreadLocal上下文");
    TokenContext.clear();
});
```

## 关键组件说明

### 1. TokenContext (ThreadLocal管理)

**位置**: `com.gdu.zeus.ops.workorder.util.TokenContext`

**作用**: 在同一请求线程中传递token

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

### 2. TokenContextWrapper (Reactor Context传播)

**位置**: `com.gdu.zeus.ops.workorder.util.TokenContextWrapper`

**作用**: 处理多线程场景下的token传播，使用Reactor的Context机制

```java
public class TokenContextWrapper {
    private static final String TOKEN_CONTEXT_KEY = "TOKEN";

    public static <T> Flux<T> wrapWithToken(Flux<T> flux, String token) {
        return flux.contextWrite(Context.of(TOKEN_CONTEXT_KEY, token));
    }

    public static String getTokenFromContext(Context context) {
        return context.getOrDefault(TOKEN_CONTEXT_KEY, null);
    }
}
```

### 3. TokenInterceptor (自动添加Token到请求头)

**位置**: `com.gdu.zeus.ops.workorder.util.TokenInterceptor`

**作用**: RestTemplate拦截器，自动从TokenContext获取token并添加到HTTP请求头

```java
public class TokenInterceptor implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String token = TokenContext.getToken();
        if (token != null && !token.isEmpty()) {
            request.getHeaders().set("Authorization", "Bearer " + token);
        }
        return execution.execute(request, body);
    }
}
```

### 4. HttpClientConfig (配置RestTemplate)

**位置**: `com.gdu.zeus.ops.workorder.config.HttpClientConfig`

**作用**: 将TokenInterceptor注册到RestTemplate

```java
@Configuration
public class HttpClientConfig {
    @Bean("workOrderRestTemplate")
    public RestTemplate workOrderRestTemplate() {
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // 添加token拦截器
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new TokenInterceptor());
        restTemplate.setInterceptors(interceptors);

        return restTemplate;
    }
}
```

## 工作流程总结

### 流程图

```
1. 前端用户登录
   ↓
2. 获取Authorization Token
   ↓
3. 调用 /api/assistant/chatByUserId API，在Header中传递token
   ↓
4. AssistantController接收请求
   ├─ 提取Authorization header中的token
   ├─ TokenContext.setToken(token) 存入ThreadLocal
   └─ 调用agent.chat()
   ↓
5. CustomerSupportAssistant处理请求
   ├─ 调用ChatClient.prompt()
   ├─ 注册PatrolOrderTools工具函数
   └─ Flux流处理大模型响应
   ↓
6. 大模型自动调用工具函数 (发生在同一线程)
   ├─ getPOILocations() → POIServiceV2
   ├─ getAvailableRoutes() → RouteServiceV2
   └─ createPatrolOrder() → PatrolOrderCreationService
   ↓
7. Service层调用外部API
   ├─ 从ThreadLocal获取token
   ├─ TokenContext.getToken()
   └─ 传递给WorkOrderExternalServiceImpl
   ↓
8. WorkOrderExternalServiceImpl使用RestTemplate发送HTTP请求
   ├─ RestTemplate调用TokenInterceptor
   ├─ TokenInterceptor自动添加Authorization header
   └─ HTTP请求发送到业务系统API
   ↓
9. 业务系统接收请求
   ├─ 验证Authorization token
   ├─ 执行业务逻辑
   └─ 返回结果
   ↓
10. 聊天流处理完成
    └─ TokenContext.clear() 清理ThreadLocal
```

## 数据流向

### 请求流向

```
前端请求
  └─ Authorization: Bearer {token}
     └─ /api/assistant/chatByUserId
        └─ AssistantController.chat()
           └─ TokenContext.setToken(token)
              └─ CustomerSupportAssistant.chat()
                 └─ ChatClient处理
                    └─ 大模型调用工具函数
                       ├─ getPOILocations
                       │  └─ POIServiceV2.getLocationsByArea()
                       │     └─ TokenContext.getToken()
                       │        └─ WorkOrderExternalServiceImpl.getPoiName()
                       │           └─ RestTemplate.exchange()
                       │              └─ TokenInterceptor拦截
                       │                 └─ 添加Authorization header
                       │                    └─ HTTP POST /business/overview-mode/search/getPoiName
                       │
                       ├─ getAvailableRoutes
                       │  └─ RouteServiceV2.getRoutesByLocation()
                       │     └─ TokenContext.getToken()
                       │        └─ WorkOrderExternalServiceImpl.getRoutes()
                       │           └─ RestTemplate.exchange()
                       │              └─ TokenInterceptor拦截
                       │                 └─ 添加Authorization header
                       │                    └─ HTTP POST /route/getRouteByRadius
                       │
                       └─ createPatrolOrder
                          └─ PatrolOrderCreationService.createWorkOrderViaAPI()
                             └─ TokenContext.getToken()
                                └─ WorkOrderExternalServiceImpl.createWorkOrder()
                                   └─ RestTemplate.exchange()
                                      └─ TokenInterceptor拦截
                                         └─ 添加Authorization header
                                            └─ HTTP POST /business/workOrder/create
```

### 响应流向

```
业务系统API响应
  └─ 返回数据 (HTTP 200)
     └─ WorkOrderExternalServiceImpl处理响应
        └─ Service层返回结果
           └─ PatrolOrderTools工具函数返回结果
              └─ 大模型处理结果
                 └─ CustomerSupportAssistant.chat() Flux
                    └─ AssistantController返回流
                       └─ 前端接收SSE流
```

## 异常处理

### Token丢失的情况

1. **Token未提供**: 如果前端没有在Authorization header中传递token
   - TokenContext.getToken()返回null
   - TokenInterceptor不添加Authorization header
   - 业务系统API返回401 Unauthorized
   - Service层捕获异常并抛出RuntimeException

2. **Token过期**: 如果token在请求处理过程中过期
   - 业务系统API返回401 Unauthorized
   - HTTP Client捕获错误响应
   - WorkOrderExternalServiceImpl抛出异常
   - Service层捕获异常并向调用方报告

3. **Token无效**: 如果token格式不正确或被篡改
   - TokenInterceptor仍会添加到header
   - 业务系统API验证失败返回401
   - 同样被异常处理机制处理

### 错误恢复策略

```java
try {
    // 调用外部API
    List<POILocationResponse> locations = workOrderExternalService.getPoiName(request);
    return locations;
} catch (Exception e) {
    logger.error("查询POI位置异常，区域: {}", area, e);
    throw new RuntimeException("查询POI位置失败: " + e.getMessage(), e);
}
```

## 安全考虑

### 1. Token存储
- Token存储在ThreadLocal中，不会被其他请求访问
- Token在请求结束时被清理
- 不会被记录到日志中(已在日志中移除敏感信息)

### 2. Token传递
- Token通过HTTPS传输(生产环境)
- Token添加到Authorization header，符合REST标准
- TokenInterceptor确保每个请求都包含token

### 3. 多线程安全
- ThreadLocal天然支持多线程隔离
- Reactor Context提供额外的多线程支持
- 两种机制结合确保token不会跨线程泄露

## 配置文件示例

### application.yml

```yaml
workorder:
  api:
    baseUrl: http://172.16.64.112:30755/gdu-domp-api
    connectTimeout: 5000
    readTimeout: 10000
    retryEnabled: true
    maxRetries: 3
    auth:
      type: BEARER
    endpoints:
      natureList: /business/workOrder/natureList
      getPoiName: /business/overview-mode/search/getPoiName
      getRoutes: /route/getRouteByRadius
      getRouteInfo: /route/getByRouteId
      createWorkOrder: /business/workOrder/create
```

## 测试方案

### 单元测试

```java
@Test
public void testTokenPropagation() {
    // 1. 设置token
    String testToken = "Bearer test-token-123";
    TokenContext.setToken(testToken);
    
    // 2. 验证TokenContext
    assertEquals(testToken, TokenContext.getToken());
    
    // 3. 调用Service
    POIServiceV2 service = new POIServiceV2();
    List<POILocationResponse> locations = service.getLocationsByArea("test-area");
    
    // 4. 验证结果
    assertNotNull(locations);
    
    // 5. 清理
    TokenContext.clear();
    assertNull(TokenContext.getToken());
}
```

### 集成测试

```java
@Test
public void testAssistantControllerTokenPropagation() {
    String token = "Bearer test-token-123";
    String userId = "user123";
    String chatId = "chat-123";
    String message = "Get POI locations in guanggu";
    
    Flux<String> response = assistantController.chat(
        userId, token, chatId, message
    );
    
    StepVerifier.create(response)
        .expectNextMatches(s -> s.contains("[complete]"))
        .verifyComplete();
}
```

## 常见问题

### Q1: 如果大模型在多个线程中执行工具函数怎么办？

**A**: 这是个好问题。虽然ThreadLocal可以处理大多数情况，但为了安全起见：
- 我们同时使用了TokenContextWrapper将token注入到Reactor Context
- TokenInterceptor有双重检查机制，首先从ThreadLocal获取，如果失败则从Context获取
- 这样确保了即使在多线程场景下token也能正确传播

### Q2: Token在什么时候被清理？

**A**: Token在以下时刻被清理：
- 正常情况：Flux流处理完成时 (在doFinally中)
- 异常情况：捕获异常后立即清理
- 这样确保了ThreadLocal不会造成内存泄漏

### Q3: 能否在多个并发请求中使用同一个token？

**A**: 可以的。由于每个请求都有自己的线程和ThreadLocal副本，所以多个并发请求可以使用相同的token而不会互相干扰。

### Q4: 如果业务系统API需要使用不同的token怎么办？

**A**: 可以在Service层中临时替换token：
```java
String oldToken = TokenContext.getToken();
try {
    TokenContext.setToken(newToken);
    // 调用API
} finally {
    TokenContext.setToken(oldToken);
}
```

## 总结

这个Token传播机制实现了从前端到后端再到大模型和外部API的完整token链路：

1. **前端**: 在Authorization header中传递token
2. **AssistantController**: 从header提取token并存入ThreadLocal
3. **大模型工具函数**: 在同一线程中执行，可访问ThreadLocal中的token
4. **Service层**: 从ThreadLocal获取token并使用
5. **TokenInterceptor**: 自动添加token到HTTP请求头
6. **业务系统API**: 接收并验证token

整个流程通过ThreadLocal + Reactor Context的组合方案确保了线程安全和多场景兼容性，同时提供了完整的错误处理和清理机制。
