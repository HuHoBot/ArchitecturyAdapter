# HuHoBot-ArchitecturyAdapter-Adapter v2.0.7

feat(core): 修复部分版本下Kotlin运行库冲突问题
- 更新所有构建脚本以排除 _COROUTINE 相关文件

feat(config): 更新配置版本并添加服务器名称字段
- 将配置版本从 3 升级到 4
- 在默认配置中添加 serverName 字段，默认值为 "Server"
- 添加配置迁移逻辑，当旧版本小于 4 时自动设置默认服务器名称
- 修复 getHashKey 方法返回类型，确保始终返回字符串

feat(event): 添加加/退群事件监听