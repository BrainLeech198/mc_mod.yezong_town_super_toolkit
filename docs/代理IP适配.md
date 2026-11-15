# 代理客户端地址适配

## 默认解析行为

- `ipResolution=auto`（默认）：发布 Forge `ClientIpResolveEvent`；没有服务端适配 mod 提供地址时，使用当前连接地址。
- `ipResolution=remote`：不发布事件，强制使用连接地址。
- `ipResolution=header`：兼容旧配置，行为等同 `auto`。`forwardedHeaderName` 也只是兼容项；Minecraft TCP 连接不是 HTTP 请求，不存在通用的 `X-Forwarded-For` 读取接口。

解析后的地址统一用于账号与闸门限流、审计记录、登录 IP 变化提醒和管理员登录通知。

## 适配器契约

代理适配 mod 在 Forge 全局事件总线上监听 `ClientIpResolveEvent`，从自身支持的代理协议中解析并验证来源，然后调用 `setClientAddress(InetAddress)`。事件在服务器线程同步触发；先设置有效地址的监听器优先。没有地址时不设置，认证 mod 自动回退到连接地址。

```java
@SubscribeEvent(priority = EventPriority.HIGH)
public static void resolveClientAddress(ClientIpResolveEvent event) {
    // 从适配器提供的协议数据中解析，并先验证共享密钥/受信任代理来源。
    InetAddress verifiedAddress = resolveAndVerify(event.getPlayer());
    if (verifiedAddress != null) event.setClientAddress(verifiedAddress);
}
```

`resolveAndVerify` 是适配器自己的协议实现，不属于本 mod API。适配器不可直接信任可由客户端伪造的主机名、聊天内容或普通文本字段。公网部署还应限制后端端口只能被受信任代理访问。

## 为什么使用适配器

Minecraft 代理的转发方式并不统一。以 Velocity 为例，Forge 上的现代转发需要兼容 mod；旧式 BungeeCord 转发也依赖相应服务端支持，且安全性较弱。[Velocity 的玩家信息转发说明](https://docs.papermc.io/velocity/player-information-forwarding/) 和 [服务端兼容说明](https://docs.papermc.io/velocity/server-compatibility/)列出这些差异。因此本 mod 不会猜测未知协议，也不会默认信任转发数据。
