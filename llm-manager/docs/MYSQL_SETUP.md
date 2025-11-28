# MySQL数据库设置指南

## 前置要求

确保已安装MySQL 5.7或更高版本。

## 数据库创建步骤

### 1. 登录MySQL

```bash
mysql -u root -p
```

### 2. 创建数据库

```sql
CREATE DATABASE llm_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 创建用户（可选）

如果不想使用root用户，可以创建专用用户：

```sql
CREATE USER 'llmmanager'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON llm_manager.* TO 'llmmanager'@'localhost';
FLUSH PRIVILEGES;
```

### 4. 配置application.yml

更新`src/main/resources/application.yml`中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/llm_manager?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root  # 或使用你创建的用户名
    password: root  # 修改为你的密码
```

## 表结构说明

应用启动时会自动执行`schema.sql`创建以下表：

### 1. users - 用户表
- id: 主键
- username: 用户名（唯一）
- password: 密码
- email: 邮箱
- created_at: 创建时间
- updated_at: 更新时间

### 2. channel - 渠道表
- id: 主键
- name: 渠道名称
- base_url: API基础URL
- api_key: API密钥
- type: 渠道类型
- created_at: 创建时间
- updated_at: 更新时间

### 3. llm_model - LLM模型表
- id: 主键
- name: 模型名称
- model_identifier: 模型标识符
- channel_id: 关联渠道ID（外键）
- temperature: 温度参数
- max_tokens: 最大token数
- created_at: 创建时间
- updated_at: 更新时间

### 4. agent - Agent表
- id: 主键
- name: Agent名称
- slug: URL友好标识（唯一）
- description: 描述
- system_prompt: 系统提示词
- llm_model_id: 关联模型ID（外键）
- temperature_override: 温度覆盖值
- created_at: 创建时间
- updated_at: 更新时间

### 5. prompt - 提示词模板表
- id: 主键
- name: 模板名称
- content: 模板内容
- description: 描述
- created_at: 创建时间
- updated_at: 更新时间

### 6. api_key - API密钥表
- id: 主键
- name: 密钥名称
- token: 密钥令牌（唯一）
- active: 是否激活
- created_at: 创建时间

## 启动应用

1. 确保MySQL服务正在运行
2. 确保已创建数据库`llm_manager`
3. 运行应用：

```bash
mvn spring-boot:run
```

或使用IDE运行`LlmManagerApplication`主类。

## 常见问题

### 1. 连接被拒绝

**错误信息**: `Communications link failure`

**解决方案**:
- 检查MySQL服务是否运行：`sudo systemctl status mysql`
- 检查端口3306是否开放
- 检查防火墙设置

### 2. 认证失败

**错误信息**: `Access denied for user`

**解决方案**:
- 检查用户名和密码是否正确
- 确保用户有访问数据库的权限

### 3. 时区问题

**错误信息**: `The server time zone value 'XXX' is unrecognized`

**解决方案**:
- URL中已包含`serverTimezone=Asia/Shanghai`参数
- 或在MySQL配置文件中设置默认时区

### 4. 表已存在错误

如果需要重新创建表，可以：

```sql
USE llm_manager;
DROP TABLE IF EXISTS api_key;
DROP TABLE IF EXISTS prompt;
DROP TABLE IF EXISTS agent;
DROP TABLE IF EXISTS llm_model;
DROP TABLE IF EXISTS channel;
DROP TABLE IF EXISTS users;
```

然后重启应用，表会自动重新创建。

## 数据库备份

### 备份数据库

```bash
mysqldump -u root -p llm_manager > llm_manager_backup.sql
```

### 恢复数据库

```bash
mysql -u root -p llm_manager < llm_manager_backup.sql
```

## 性能优化建议

1. 为常用查询字段添加索引
2. 定期分析和优化表
3. 监控慢查询日志
4. 根据实际情况调整连接池配置

## 生产环境注意事项

1. **不要使用root用户**：创建专用数据库用户
2. **使用强密码**：确保数据库密码足够复杂
3. **启用SSL连接**：在URL中设置`useSSL=true`
4. **定期备份**：设置自动备份策略
5. **监控性能**：使用监控工具跟踪数据库性能
6. **限制访问**：只允许应用服务器IP访问数据库