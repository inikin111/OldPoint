# 架构迁移：相机生命周期

本分支从 `471d839` 开始渐进重构。第一阶段只迁移相机控制状态，不修改遮挡算法或 Viewpoint Hook 协议。

## 当前职责

- `CameraController` 拥有 OldPoint 的模式启用标志、俯视锁存、待处理进入请求、进入来源、返回目标、保存的视角及旋转输入。只有它能写这些状态。
- Viewpoint 继续拥有实际的 `View.enabled`、`View.thirdPerson` 和 `Look`。请求 `FP.cycleView` 并不等于启用成功，仍由 `afterToggle` 确认并消费请求。
- `Bridge` 保留 `OldPointBridge` Lua 名称、原有对外方法和全部嵌套补丁类。相机控制方法委托给 `CameraController`；当前帧几何、帧快照与渲染接入仍在 Bridge。
- `CompatibilityErrors` 保留相机和绘制共用的“兼容错误只报告一次”行为，避免模块拆分后各自重复报告。

## 帧与模式

模式状态属于 CameraController；`Bridge.CameraState` 是已经捕获的一帧，不是相机模式的第二个所有者。

捕获端调用 `CameraController.captureYaw()` 推进原有旋转过渡，再计算当前帧几何。绘制端仍优先使用帧快照，不推进旋转动画。无绘制快照时的方向查询只读取 Controller 的朝向。

## 保留的转换规则

- 循环键：第一人称 → 第三人称 → 俯视 → 第一人称。处理后的按键仍跳过原 ThirdPerson.poll。
- 记忆俯视键：记住来源，退出时返回原版、第一或第三人称。
- 原版下请求 Viewpoint 启用必须经过其正常检查；请求保持原有 10 秒超时及回调内检查时机。
- 自动/瞄准请求继续使用原有 0..4 编码、守卫和朝向保持行为。
- FreeCam 临时使 topDownActive 为 false，不等同于退出俯视。
- 关闭 OldPoint 时保留原有第三人称底座行为；Viewpoint 关闭后按原回调清理俯视。
- 固定角度、拖动旋转、分步旋转和重置朝向的条件与顺序保持原样。

这一步没有统一或修正各入口原本不同的守卫，没有把状态重写成新枚举状态机，也没有调整 Hook 执行顺序。

## 验证

完整构建：在源码根目录运行 `.\.local\dev.ps1 -Action Build`。

生命周期测试：

```powershell
$envConfig = Import-PowerShellDataFile .\.local\environment.psd1
python .\java\tests\run_camera_tests.py --jdk $envConfig.Jdk
```

测试执行真实 CameraController、Layout 和 ResetMotion，使用轻量模拟运行环境代替游戏、Viewpoint、输入设备及无关效果。每个场景单独启动 JVM，避免静态状态污染。测试覆盖循环、记忆返回、异步启用、显式退出、守卫、FreeCam、禁用、外部关闭、旋转光标、固定角度、分步旋转、朝向保持及待处理请求取消。

本次迁移还提供编译产物的接口兼容检查：

```powershell
python .\java\tests\check_hook_compatibility.py --baseline .\.local\camera-refactor-baseline\OldPoint.jar --current .\42\media\java\client\OldPoint.jar
```

baseline 是迁移前的构建产物，本机保存在 Git 忽略目录。检查 Lua 暴露注解、Bridge 的 public/package 方法，以及全部 Hook 的类目标、方法名、回调时机和参数绑定；它不比较或执行所有 Hook 的逻辑。

模拟环境不会加载游戏或 OpenGL；它不能证明真实版本的反射、字节码注入、GLFW 鼠标捕获或游戏内画面正确。待处理请求的 10 秒超时仍使用真实时钟，本轮不通过修改私有字段或等待十秒来测试它；迁移方法体比对确认保留原有实现。需要结合完整依赖编译、Hook/Lua 接口兼容核对与游戏内冒烟测试。

游戏内检查：三种视角循环；从原版及两种 Viewpoint 模式记忆进入/退出；驾驶位与瞄准自动切换；旋转/固定角度；暂停/模态窗口；退出 Viewpoint 后重入。尚未完成游戏内验证时，不把分支部署到稳定运行副本。

## 后续阶段

1. 为 Viewpoint 反射操作建立集中 Adapter，继续减少 Controller 对具体引擎操作的了解。
2. 将当前帧几何与快照发布从 Bridge 提取为相机捕获模块。
3. 在保留多种几何表示的前提下整理遮挡结果接口。
4. 集中绘制上下文和规则上传；性能优化另用测量验证。
