# MyBatis-Plus 迁移文档

## 迁移概述

本文档记录了从 Spring Data JPA 迁移到 MyBatis-Plus 的完整过程。

## 迁移日期
2025-11-28

## 主要变更

### 1. 依赖更新 (pom.xml)

**移除的依赖：**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

**添加的依赖：**
```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.5</version>
</dependency>
```

### 2. 实体类注解迁移

所有实体类从 JPA 注解迁移到 MyBatis-Plus 注解：

| JPA 注解 | MyBatis-Plus 注解 | 说明 |
|---------|------------------|------|
| @Entity | @TableName | 指定表名 |
| @Id | @TableId | 指定主键 |
| @GeneratedValue | @TableId(type = IdType.AUTO) | 主键自增策略 |
| @Column | @TableField | 指定字段映射 |
| @ManyToOne | Long xxxId | 改为外键ID字段 |

**实体类变更示例：**

**User.java** - 基础实体类
```java
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    // ...
}
```

**Agent.java** - 关联关系处理
```java
// 原 JPA 方式
@ManyToOne
private LlmModel llmModel;

// 迁移后 MyBatis-Plus 方式
@TableField("llm_model_id")
private Long llmModelId;
```

**ApiKey.java** - 自动填充字段
```java
@TableField(fill = FieldFill.INSERT)
private LocalDateTime createdAt;
```

**Prompt.java** - 创建和更新时间自动填充
```java
@TableField(fill = FieldFill.INSERT)
private LocalDateTime createdAt;

@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updatedAt;
```

### 3. Repository → Mapper 迁移

**删除的 Repository 接口：**
- AgentRepository
- ApiKeyRepository
- ChannelRepository
- LlmModelRepository
- PromptRepository
- UserRepository

**创建的 Mapper 接口：**

所有 Mapper 接口继承 `BaseMapper<T>` 并使用 `@Mapper` 注解：

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

### 4. Service 层创建

遵循最佳实践，为每个实体创建 Service 层，避免在 Controller 中直接使用 Mapper。

**Service 类结构：**
```java
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    
    public User findByUsername(String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        return this.getOne(wrapper);
    }
}
```

**创建的 Service 类：**
- UserService - 包含 `findByUsername()` 方法
- AgentService - 包含 `findBySlug()` 方法
- ApiKeyService - 包含 `findByToken()` 和 `revoke()` 方法
- ChannelService - 基础 CRUD
- LlmModelService - 基础 CRUD
- PromptService - 基础 CRUD

### 5. Controller 层更新

所有 Controller 从直接使用 Repository/Mapper 改为使用 Service：

```java
// 原方式
private final AgentRepository agentRepository;

// 新方式
private final AgentService agentService;
```

**更新的 Controller：**
- AuthController
- AgentController
- ApiKeyController
- ChannelController
- ModelController
- PromptController
- ExternalChatController

### 6. 配置文件更新

**application.yml 变更：**

**移除的 JPA 配置：**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

**添加的 MyBatis-Plus 配置：**
```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  mapper-locations: classpath*:/mapper/**/*.xml
```

### 7. 新增配置类

**MybatisPlusConfig.java** - MyBatis-Plus 核心配置
```java
@Configuration
@MapperScan("com.example.llmmanager.mapper")
public class MybatisPlusConfig {
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        return interceptor;
    }
}
```

**MetaObjectHandlerConfig.java** - 自动填充处理器
```java
@Component
public class MetaObjectHandlerConfig implements MetaObjectHandler {
    
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
    
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

### 8. 其他文件更新

**LlmExecutionService.java** - 业务服务更新
- 从使用 `LlmModelRepository` 改为 `LlmModelService`
- 添加 `ChannelService` 依赖
- 更新所有方法以适配新的实体关系（使用ID而非对象引用）

**ApiKeyAuthFilter.java** - 认证过滤器更新
- 从使用 `ApiKeyRepository` 改为 `ApiKeyService`
- 更新查询方法调用

## 关键技术点

### 1. 关联关系处理
MyBatis-Plus 采用更简单的方式处理关联关系，使用外键 ID 而非对象引用：
- 优点：查询效率高，避免 N+1 问题
- 缺点：需要手动进行关联查询

### 2. 动态查询
使用 `QueryWrapper` 构建动态查询条件：
```java
QueryWrapper<User> wrapper = new QueryWrapper<>();
wrapper.eq("username", username);
User user = userMapper.selectOne(wrapper);
```

### 3. 自动填充
通过 `MetaObjectHandler` 实现字段的自动填充，如创建时间、更新时间。

### 4. 分页支持
通过 `PaginationInnerInterceptor` 拦截器实现分页功能。

## 数据库兼容性

当前配置支持 H2 数据库，如需切换到其他数据库（如 MySQL、PostgreSQL），需要：
1. 修改 `pom.xml` 中的数据库驱动依赖
2. 更新 `MybatisPlusConfig` 中的 `DbType`
3. 调整 `application.yml` 中的数据源配置

## 验证清单

- [x] 所有实体类已迁移到 MyBatis-Plus 注解
- [x] 所有 Repository 接口已替换为 Mapper 接口
- [x] 创建了完整的 Service 层
- [x] 所有 Controller 已更新为使用 Service
- [x] 配置文件已更新
- [x] 创建了 MyBatis-Plus 配置类
- [x] 创建了自动填充处理器
- [x] 清除了所有对 repository 包的引用
- [x] 更新了业务服务类（LlmExecutionService、ApiKeyAuthFilter）

## 注意事项

1. **实体关系变更**：所有 `@ManyToOne` 关系都改为了外键 ID，在查询时需要手动关联
2. **查询方法**：原 JPA 的 `findByXxx()` 方法需要使用 `QueryWrapper` 重写
3. **事务管理**：MyBatis-Plus 同样支持 Spring 的 `@Transactional` 注解
4. **缓存机制**：如需使用二级缓存，需要额外配置

## 后续优化建议

1. 考虑为复杂查询创建自定义 XML 映射文件
2. 实现逻辑删除功能（已在配置中预留）
3. 根据业务需要添加乐观锁支持
4. 考虑使用 MyBatis-Plus 的代码生成器简化开发

## 参考资料

- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [Spring Boot 集成 MyBatis-Plus](https://baomidou.com/pages/226c21/)