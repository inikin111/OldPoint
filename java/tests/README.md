# 行为测试

从仓库根目录运行 `./scripts/test.ps1`。依赖与运行步骤见 [开发命令](../../scripts/README.md)。

| 文件 | 作用 |
| --- | --- |
| `oldpoint/viewpoint/CameraControllerTest.java` | 镜头生命周期、模式切换、记忆返回和输入状态 |
| `oldpoint/viewpoint/CameraCaptureTest.java` | 帧快照、坐标、帧复用和隔离 |
| `oldpoint/viewpoint/CameraDrawingTest.java` | 绘制时读取快照、线程隔离和异常清理 |
| `oldpoint/viewpoint/HookInliningTest.java` | 用真实 ByteBuddy 将生产回调内联到外部包并执行，检查跨包访问 |
| `oldpoint/viewpoint/OcclusionRulesTest.java` | 使用真实规则打包检查墙段端点，覆盖转角、T 形连接、楼层隔离及淡变进度；无需 OpenGL 上下文 |
| `run_*_tests.py` | 生成模拟环境、编译真实产品源码并执行对应 Java 场景 |

Java 测试沿用产品包路径，以便访问内部模块。Python 运行器中的模拟类用于替代游戏、Viewpoint 和 OpenGL；每个行为场景在独立 JVM 中执行。Hook 内联测试使用实际安装的依赖 JAR。

生成的模拟源码和测试类位于 `java/build/`，不应提交。产品构建只扫描 `java/src/`，测试不会打入发布 JAR。

这些测试保护已有行为，无法证明真实游戏中的渲染、输入设备、完整注入链及性能正确。游戏内验证范围见 [架构说明](../../docs/architecture.md)。
