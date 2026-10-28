# Viewpoint Surface Fix 天花板兼容

Surface Fix 的 `RoomCeilings.capture` / `captureRoof` 通过 `vpinteriors.Vp.emit` 添加补充天花板。它们是普通 recipe 网格，不一定有独立屋顶对象；部分网格由当前层 recipe 提交到上方，表面向下偏移 0.008。OldPoint 原先没有捕获这条路径的屋顶来源信息，单靠源楼层无法可靠隐藏。

## 兼容方式

`SurfaceCeilings` 提供可选补丁，不引用 Surface Fix 类型，也不修改对方 JAR：

1. 仅在 `RoomCeilings.capture` 和 `captureRoof` 执行期间启用线程局部范围，可嵌套，异常退出也清理。
2. 在 `Vp.emit` 参数中保留已有标志并加入现有 `GeneratedRoofs.TAG`，交给既有屋顶隐藏规则。
3. ROOTED/STANDING 几何保持原样，避免与根位置编码冲突。
4. 既有选中房间/建筑范围、行走地面保护和近/远景着色器继续生效。没有扩大高度阈值，也没有全局关闭天花板。

Viewpoint 的 `WorldMesher.emit` 将标志传入 `Recipe.Op.flags`；`Cook.Builder.emit` 保留相关高位。没有安装 Surface Fix 时，这些可选补丁没有目标，不增加编译依赖。

## 回归检查

运行 `scripts/test-surface-ceilings.ps1`，依赖参数或 `.local/environment.psd1` 配置与现有构建脚本一致。可追加 `-SurfaceFix <ViewpointInteriorSurfaces.jar 路径>` 检查已安装模组。该命令构建仓库内 JAR，不部署游戏；独立入口同时适用于当前 master 与 PR #5 的构建脚本。

`SurfaceCeilingsTest` 回放补充天花板的提交边界，实际内联生产补丁，检查跨包访问、标志保留、根位置位保护、嵌套范围及退出后不污染其他网格。默认不需要安装 Surface Fix；额外传入其 JAR 路径可检查实际方法签名并执行真实 `mappedVertices`。

本机已确认修复前来源标记断言失败、修复后通过，并核对 Surface Fix 1.0 的实际 JAR 与向下偏移 0.008 的天花板几何。完整 Hook 与遮挡测试通过。这不替代游戏内验收，也不代表 Surface Fix 的所有补充墙面、窗框等功能已一并验证。
