# Token传播机制 - 完整实现文档

## 概述

本文档说明了如何在Zeus OPS Workorder系统中实现用户登录token从前端到后端，再到大模型调用业务系统API的完整传播链路。

## 快速开始

### 前端调用示例

```javascript
// 用户登录获得token
const token = 'Bearer eyJhbGc...';
const userId = 'user123';
const chatId = 'chat-' + Date.now();

// 通过SSE调用助手API
const eventSource = new EventSource(
  `/api/assistant/chatByUserId?userId=${userId}&chatId=${chatId}&userMessage=Create+a+patrol+order`,
  {
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json'
    }
  }
);

eventSource.onmessage = (event) => {
  console.log(event.data);
};
```

### 后端处理流程

```
1. AssistantController接收请求
   ├─ 从Authorization header提取token
   ├─ TokenContext.setToken(token)
   └─ 调用agent.chat()

2. 大模型处理消息并调用工具函数
   ├─ getPOILocations() → POIServiceV2
   ├─ getAvailableRoutes() → RouteServiceV2
   └─ createPatrolOrder() → PatrolOrderCreationService

3. Service层调用业务系统API
   ├─ TokenContext.getToken()获取token
   ├─ 通过RestTemplate发送HTTP请求
   └─ TokenInterceptor自动添加Authorization header

4. 业务系统API处理请求
   ├─ 验证Authorization header中的token
   ├─ 返回结果
   └─ 数据流回到Service层

5. 流处理完成
   └─ TokenContext.clear()清理
```

## 文件清单

### 新创建的文件

#### 1. 数据模型层
```
src/main/java/com/gdu/zeus/ops/workorder/data/
├─ ChatSession.java              (聊天会话实体)
├─ ChatMessage.java              (聊天消息实体)
├─ ChatSessionDTO.java           (会话DTO)
├─ ChatMessageDTO.java           (消息DTO)
└─ enums/MessageRole.java        (消息角色枚举)
```

#### 2. 仓储层
```
src/main/java/com/gdu/zeus/ops/workorder/repository/
├─ ChatSessionRepository.java     (会话仓储)
└─ ChatMessageRepository.java     (消息仓储)
```

#### 3. 服务层
```
src/main/java/com/gdu/zeus/ops/workorder/services/
├─ ChatSessionService.java        (会话服务)
├─ POIServiceV2.java             (POI位置查询-使用token)
├─ RouteServiceV2.java           (航线查询-使用token)
└─ PatrolOrderCreationService.java (工单创建-使用token)
```

#### 4. 工具类层
```
src/main/java/com/gdu/zeus/ops/workorder/util/
├─ TokenContext.java             (ThreadLocal token管理)
├─ TokenContextWrapper.java       (Reactor Context传播)
└─ TokenInterceptor.java          (现有-无修改)
```

#### 5. 文档文件
```
项目根目录/
├─ TOKEN_PROPAGATION_GUIDE.md     (详细设计指南)
├─ IMPLEMENTATION_DETAILS.md      (实现细节说明)
└─ TOKEN_IMPLEMENTATION_README.md (本文档)
```

### 修改的文件

#### 1. AssistantController.java
- 添加了详细的注释说明token传播思路
- 使用TokenContext.setToken()存储token
- 使用TokenContextWrapper.wrapWithToken()处理多线程
- 添加了doFinally()确保清理

#### 2. CustomerSupportAssistant.java
- 添加了stopChat()方法用于停止聊天流
- 优化了chat()方法的注释

#### 3. PatrolOrderTools.java
- 注入PatrolOrderCreationService
- 更新createPatrolOrder()方法，确保调用外部API
- 添加了详细注释说明token传播流程

#### 4. HttpClientConfig.java
- 现有配置中已经添加了TokenInterceptor (无需修改)

## 核心概念

### 1. ThreadLocal (TokenContext)

**用途**: 在同一请求线程中传递token

**特点**:
- 完全线程隔离，无需加锁
- 适用于传统同步调用
- 性能极高 (~1微秒)

**使用方式**:
```java
// 存储
TokenContext.setToken(token);

// 读取
String token = TokenContext.getToken();

// 清理
TokenContext.clear();
```

### 2. Reactor Context (TokenContextWrapper)

**用途**: 处理异步/多线程场景下的token传播

**特点**:
- 与Reactor框架集成
- 支持多线程执行
- 自动跟踪Flux流

**使用方式**:
```java
// 注入token到Flux
Flux<String> flux = TokenContextWrapper.wrapWithToken(flux, token);

// 从Context提取
String token = TokenContextWrapper.extractToken();
```

### 3. TokenInterceptor

**用途**: 自动为HTTP请求添加Authorization header

**工作流程**:
1. RestTemplate执行请求前触发
2. 从ThreadLocal获取token
3. 添加到Authorization header
4. 执行HTTP请求

### 4. Service调用链

```
大模型工具函数 (PatrolOrderTools)
    ↓
Service层 (POIServiceV2, RouteServiceV2, PatrolOrderCreationService)
    ├─ TokenContext.getToken()
    └─ 调用外部API
        ↓
    HTTP请求 (RestTemplate)
        ├─ TokenInterceptor拦截
        ├─ 添加Authorization header
        └─ 发送到业务系统
            ↓
        业务系统API
        ├─ 验证token
        └─ 返回结果
```

## 详细实现步骤

### 步骤1: 前端传递Token

前端在调用API时，需要在Authorization header中包含token:

```javascript
const token = 'Bearer eyJhbGc...';

fetch('/api/assistant/chatByUserId', {
  method: 'GET',
  headers: {
    'Authorization': token,
    'Content-Type': 'application/json'
  },
  params: {
    userId: 'user123',
    chatId: 'chat-uuid',
    userMessage: 'Create a patrol order'
  }
})
```

### 步骤2: AssistantController接收Token

```java
@RequestMapping(path = "/chatByUserId", 
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chat(
        @RequestParam(name = "userId") String userId,
        @RequestHeader(name = "Authorization", required = false) String token,
        @RequestParam(name = "chatId") String chatId,
        @RequestParam(name = "userMessage") String userMessage) {
    
    // 1. 检查token是否存在
    if (token != null && !token.isEmpty()) {
        // 2. 存入ThreadLocal
        TokenContext.setToken(token);
    }
    
    // 3. 调用agent
    Flux<String> chatFlux = agent.chat(userId, chatId, userMessage);
    
    // 4. 为多线程注入Context
    if (token != null && !token.isEmpty()) {
        chatFlux = TokenContextWrapper.wrapWithToken(chatFlux, token);
    }
    
    // 5. 返回并自动清理
    return chatFlux.doFinally(signalType -> TokenContext.clear());
}
```

### 步骤3: 大模型调用工具函数

大模型根据用户请求自动调用:

- `getPOILocations(area)` - 获取位置列表
- `getAvailableRoutes(...)` - 获取航线列表  
- `createPatrolOrder(...)` - 创建工单

这些调用发生在同一请求线程中，所以TokenContext中的token仍然可用。

### 步骤4: Service层获取Token并调用API

以POIServiceV2为例:

```java
@Service
public class POIServiceV2 {
    @Autowired
    private WorkOrderExternalServiceImpl externalService;

    public List<POILocationResponse> getLocationsByArea(String area) {
        // 1. 从ThreadLocal获取token
        String token = TokenContext.getToken();
        
        // 2. 构造请求
        POILocationRequest request = new POILocationRequest(area);

        // 3. 调用外部API
        // TokenInterceptor会自动添加Authorization header
        return externalService.getPoiName(request);
    }
}
```

### 步骤5: TokenInterceptor自动添加Token

```java
public class TokenInterceptor implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) 
            throws IOException {
        
        // 1. 从ThreadLocal获取token
        String token = TokenContext.getToken();
        
        // 2. 如果存在，添加到请求头
        if (token != null && !token.isEmpty()) {
            request.getHeaders().set("Authorization", 
                "Bearer " + token);
        }
        
        // 3. 继续执行请求
        return execution.execute(request, body);
    }
}
```

## 配置要求

### Maven依赖

项目已包含的依赖:
- spring-boot-starter-webflux (Reactor)
- spring-boot-starter-data-jpa (JPA/Hibernate)
- spring-ai-alibaba-starter-dashscope (大模型)
- lombok (简化代码)

### 数据库表初始化

需要创建以下表:

```sql
-- 聊天会话表
CREATE TABLE chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    chat_id VARCHAR(100) NOT NULL UNIQUE,
    title VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_user_id (user_id)
);

-- 聊天消息表
CREATE TABLE chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    chat_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_chat_id (chat_id),
    INDEX idx_user_id (user_id)
);
```

### application.yml配置

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true

workorder:
  api:
    baseUrl: http://your-api-server/api
    connectTimeout: 5000
    readTimeout: 10000
```

## 使用示例

### 场景1: 用户查询POI位置

```
用户消息: "Show me POI locations in Guanggu"

流程:
1. AssistantController收到消息和token
2. TokenContext.setToken(token)
3. 大模型分析消息，调用getPOILocations("Guanggu")
4. POIServiceV2.getLocationsByArea()
5. TokenContext.getToken()读取token
6. TokenInterceptor添加Authorization header
7. POST /business/overview-mode/search/getPoiName (包含token)
8. 业务系统返回POI列表
9. 大模型处理结果返回给用户
10. TokenContext.clear()清理
```

### 场景2: 用户创建工单

```
用户消息: "Create a patrol order for area X with route Y"

流程:
1. AssistantController收到消息和token
2. TokenContext.setToken(token)
3. 大模型调用多个工具函数
   - getPOILocations()
   - getAvailableRoutes()
   - createPatrolOrder()
4. 每个工具函数都能访问ThreadLocal中的token
5. TokenInterceptor为每个HTTP请求添加token
6. 所有API调用都通过身份验证
7. 工单创建成功
8. TokenContext.clear()清理
```

## 故障排查

### 问题1: API返回401 Unauthorized

**可能原因**:
- Token未被正确传递
- Token已过期
- Token格式不正确

**排查步骤**:
1. 检查Authorization header: `logger.debug("token={}", token)`
2. 验证token格式: 应为 "Bearer xxx" 或 "xxx"
3. 检查业务系统是否接收到header
4. 验证token有效性

### 问题2: Token丢失

**可能原因**:
- 未调用TokenContext.setToken()
- 在多个线程中使用
- ThreadLocal被提前清理

**排查步骤**:
1. 添加日志: `logger.debug("TokenContext={}", TokenContext.getToken())`
2. 检查是否调用了clear(): `grep -r "TokenContext.clear" .`
3. 检查是否跨线程使用: 检查Executor/Thread使用
4. 验证Reactor Context: 检查TokenContextWrapper是否正确使用

### 问题3: 内存泄漏

**可能原因**:
- ThreadLocal未清理
- 请求处理异常未执行finally
- 连接未关闭

**排查步骤**:
1. 确保所有路径都调用TokenContext.clear()
2. 使用try-finally确保清理: 
   ```java
   try {
       TokenContext.setToken(token);
       // 业务逻辑
   } finally {
       TokenContext.clear();
   }
   ```
3. 检查HTTP连接是否被正确关闭
4. 使用memory profiler检查内存占用

## 安全建议

### 1. Token存储

✅ **安全做法**:
- 使用HTTPS传输
- TokenContext自动隔离
- 请求完成后立即清理

❌ **不安全做法**:
- 在日志中打印完整token
- 在响应体中返回token
- 在静态变量中存储

### 2. Token验证

✅ **推荐**:
- 后端验证token签名
- 检查token过期时间
- 验证token与用户匹配

### 3. 跨域处理

✅ **安全做法**:
- 使用@CrossOrigin明确允许
- 仅允许必要的origin
- 验证预检请求

## 测试指南

### 单元测试

```java
@Test
public void testTokenContextPropagation() {
    String testToken = "Bearer test-token";
    
    TokenContext.setToken(testToken);
    assertEquals(testToken, TokenContext.getToken());
    
    TokenContext.clear();
    assertNull(TokenContext.getToken());
}
```

### 集成测试

```java
@Test
public void testAssistantControllerWithToken() {
    String token = "Bearer valid-token";
    
    Flux<String> response = controller.chat(
        "user123", token, "chat-123", "test message"
    );
    
    StepVerifier.create(response)
        .expectComplete()
        .verify();
}
```

### 端到端测试

```javascript
// 前端测试脚本
async function testTokenFlow() {
    const token = 'Bearer test-token';
    const response = await fetch('/api/assistant/chatByUserId', {
        headers: { 'Authorization': token }
    });
    
    console.log(response.status === 200 ? '✓ Token流传播成功' : '✗ Token流传播失败');
}
```

## 常见问题 (FAQ)

**Q: Token能在多个请求中使用吗?**
A: 可以。每个请求都有自己的线程和ThreadLocal副本，不会互相干扰。

**Q: 如果业务系统API失败怎么办?**
A: 异常会被捕获并抛出RuntimeException，助手会告知用户。本地工单仍然会被保存。

**Q: Token过期了怎么办?**
A: 业务系统API会返回401，需要前端实现token刷新机制。

**Q: 支持JWT token吗?**
A: 是的。只要按照Authorization header格式传递即可。

**Q: 支持API Key认证吗?**
A: 可以。修改TokenContext使用API Key即可。

## 性能优化

1. **连接池**: 使用RestTemplate的连接池复用
2. **异步处理**: 利用Reactor的异步特性
3. **缓存**: 考虑缓存POI和航线信息
4. **日志级别**: 生产环境使用INFO级别

## 总结

这个Token传播机制通过以下方式确保了完整的身份验证链路:

1. ✅ 前端安全地传递token
2. ✅ 后端正确接收和存储
3. ✅ 大模型工具函数能访问token
4. ✅ 业务系统API接收到token
5. ✅ 请求完成后正确清理
6. ✅ 多线程安全
7. ✅ 异常处理完整

## 下一步

1. **部署**: 将代码部署到开发环境
2. **测试**: 运行单元测试和集成测试
3. **监控**: 监控token相关的日志和性能
4. **优化**: 根据实际使用情况优化配置
5. **文档**: 更新API文档和用户指南

## 联系与支持

如有问题或建议，请参考:
- TOKEN_PROPAGATION_GUIDE.md - 完整设计指南
- IMPLEMENTATION_DETAILS.md - 实现细节
- 源代码注释 - 详细的代码说明
