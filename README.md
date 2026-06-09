# hitcard-xhs-sdk

`hitcard-xhs-sdk` 是对 `com.xhs:xhs-sdk:1.0.0` 的一层可直接落地的封装，目标是把原始 SDK 从“只有裸 client 和缺失运行依赖”整理成一个更适合服务端集成、集群部署和统一 OAuth 管理的 Java 包。

## 提供了什么

- 补齐 `xhs-sdk` 运行时缺的依赖：`okhttp`、`okio`、`fastjson`、`jackson`、`commons-collections`、`gson`
- 提供统一的业务 client 聚合入口：`XhsClientBundle`
- 提供集群可见的 OAuth token 管理：`XhsOauthTokenManager`
- 提供 Redis 共享 token 存储：`RedisXhsOauthTokenStore`
- 支持 fat jar 打包，适合直接投到没有统一依赖管理的环境

## 主要类

| 类 | 作用 |
| --- | --- |
| `space.hitcard.xhs.sdk.XhsClientConfig` | 统一配置对象，封装 `url/appId/appSecret/version` |
| `space.hitcard.xhs.sdk.XhsClientBundle` | 聚合全部 XHS 业务域 client |
| `space.hitcard.xhs.sdk.oauth.XhsOauthTokenManager` | OAuth token 交换、读取、刷新协调 |
| `space.hitcard.xhs.sdk.oauth.XhsOauthManagerFactory` | OAuth manager / Redis store 工厂 |
| `space.hitcard.xhs.sdk.oauth.RedisXhsOauthTokenStore` | Redis 共享 token 存储，适合集群 |
| `space.hitcard.xhs.sdk.oauth.MapBackedXhsOauthTokenStore` | 本地内存 token 存储，适合单机测试 |

## 依赖方式

如果你的服务本身走 Maven 依赖管理，直接依赖普通 jar：

```xml
<dependency>
    <groupId>space.hitcard</groupId>
    <artifactId>hitcard-xhs-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

如果你的部署环境不方便补齐依赖，直接使用 fat jar：

```text
target/hitcard-xhs-sdk-1.0.0-all.jar
```

## 快速开始

### 1. 创建 client 聚合入口

```java
XhsClientBundle clients = XhsClientBundle.of(
        "https://your-xhs-gateway",
        "your-app-id",
        "your-app-secret"
);

clients.oauthClient();
clients.orderClient();
clients.packageClient();
clients.productClient();
clients.inventoryClient();
```

Builder 写法：

```java
XhsClientBundle clients = XhsClientBundle.builder()
        .url("https://your-xhs-gateway")
        .appId("your-app-id")
        .appSecret("your-app-secret")
        .version("1.0")
        .build();
```

### 2. 集群 OAuth 管理

多节点部署时，不建议每个节点自己持有本地 access token。推荐所有节点共享 Redis，由 `XhsOauthTokenManager` 统一管理 token 生命周期。

```java
JedisPool jedisPool = new JedisPool("redis-host", 6379);

XhsClientBundle clients = XhsClientBundle.of(
        "https://your-xhs-gateway",
        "your-app-id",
        "your-app-secret"
);

XhsOauthTokenManager tokenManager = XhsOauthManagerFactory.redis(
        clients.oauthClient(),
        jedisPool,
        clients.config().getAppId()
);

XhsOauthTokenRecord token = tokenManager.exchangeCode("shop:123", authCode);
String accessToken = tokenManager.requireAccessToken("shop:123");
```

Redis key 默认形态：

```text
xhs:oauth:token:<appId>:<tokenKey>
```

自定义 key 前缀：

```java
XhsOauthTokenManager tokenManager = XhsOauthManagerFactory.redis(
        clients.oauthClient(),
        jedisPool,
        "biz:xhs:oauth:",
        clients.config().getAppId()
);
```

自定义前缀后 key 形态：

```text
biz:xhs:oauth:<appId>:<tokenKey>
```

### 3. 直接创建 Redis store

```java
RedisXhsOauthTokenStore store =
        new RedisXhsOauthTokenStore(jedisPool, "biz:xhs:oauth:", clients.config().getAppId());
```

## 设计说明

- token 状态存 Redis，所有节点可见
- token 即将过期时自动刷新
- 刷新时通过版本号做 compare-and-set，减少并发覆盖
- 如果当前节点刷新失败，但其他节点已刷新成功，会回退读取最新 token

这个项目解决的是：

- `xhs-sdk` 缺依赖
- 多业务域 client 的统一初始化
- 集群间 OAuth token 可见性
- 多节点刷新竞争

这个项目不负责：

- 小红书扫码/授权回调页面本身
- 分布式长锁
- Spring Boot 自动装配

## 构建

使用项目里的 Maven settings：

```bash
mvn -s /Users/jiaweiguo/Documents/hitcard/settings.xml clean package -DskipTests
```

构建产物：

| 文件 | 用途 |
| --- | --- |
| `target/hitcard-xhs-sdk-1.0.0.jar` | 普通业务封装 jar |
| `target/hitcard-xhs-sdk-1.0.0-all.jar` | fat jar，已打入运行依赖 |
| `target/hitcard-xhs-sdk-1.0.0-sources.jar` | 源码包 |

## 额外说明

更细的 SDK 字节码阅读、接口 method 映射、签名规则和 DTO 说明在：

[`xhs-sdk-1.0.0-readme.md`](./xhs-sdk-1.0.0-readme.md)
