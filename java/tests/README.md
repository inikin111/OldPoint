# 行为测试

运行 `scripts/test.ps1`，默认执行 Camera、Occlusion、Hooks；用 `-Suite` 选择分组，`-Suite All` 包含参考对照。依赖与完整命令见[开发命令](../../scripts/README.md)。

| 分组 | 测试 | 保护的行为 |
| --- | --- | --- |
| Camera | CameraControllerTest | 模式切换、记忆返回、异步进入和取消 |
| Camera | OrbitInputTest | 插值、松手停止、光标锁定、焦点与帧率差异 |
| Camera | CameraCaptureTest | 坐标、普通视角覆盖旧标记、帧复用和隔离 |
| Camera | CameraDrawingTest | 快照读取、线程隔离和异常清理 |
| Hooks | HookInliningTest | 实际生产回调通过 ByteBuddy 内联到外部包后可以执行 |
| Occlusion | CutawaySelectionTest | 模拟世界中执行生产室内外选择，检查前后墙、目标与建筑选择 |
| Occlusion | WallTopologyCaptureTest / WallContourTest | 实体邻墙、对象身份、两端轮廓、组合面、语义隔离和容量 |
| Occlusion | ObjectFadeTest | 同对象各表示共享透明度、缓存推进、恢复、快照和上下文隔离 |
| Occlusion | OcclusionRulesTest | 生产规则打包后的端点、楼层和强度 |
| Occlusion | CutawayGeometryTest / CutawayProjectionTest | 旋转、空间范围、投影边界与退化输入 |
| Reference | NativeWallContourTest | 原版列选择与端点映射 |
| Reference | CutawayProjectionTest + verify_native_cutaway_projection.py | 从游戏字节码读取模板，与生产投影及独立屏幕投影查询对照 |
| Reference | verify_wall_contour_model.py | 生产轮廓与独立几何模型、原版组合面及任意相机输入对照 |

Python 运行器生成最小模拟环境，编译真实产品模块并执行 Java 场景；需要状态隔离的场景在独立 JVM 中运行。模拟类代替游戏、Viewpoint 和 OpenGL，不能证明完整游戏运行时行为。Hook 测试使用实际依赖中的 ByteBuddy。原版轮廓参考实现位于 `oldpoint/viewpoint/NativeWallContour.java`，仅随参考测试编译。

`CutawayProjectionTest` 不带参数时运行投影边界场景；带查询文件参数时额外读取原版模板并执行独立对照。模拟源码、类文件、JAR、查询和参考结果均位于 `java/build/`，测试源码不打入发布 JAR。

测试应保护可观察行为或明确的几何、数据约定。一次性排查脚本、重复实现逻辑的断言和运行报告不作为新增回归测试提交。实际 GPU 上传、所有光影包、完整 Hook 注入链、游戏画面和帧时间仍需游戏内验证。
