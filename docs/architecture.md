# 架构

OldPoint 为 Viewpoint 增加俯视相机、瞄准与交互适配，以及建筑和物体遮挡处理。游戏与 Viewpoint 类型由外部依赖提供。

## 模块职责

| 模块 | 职责 |
| --- | --- |
| Bridge | 稳定的 `OldPointBridge` Lua 入口，以及相机、输入、绘制和着色器 Hook |
| CameraController | 相机模式、异步进入请求、返回视角、保存朝向和旋转协调 |
| ViewpointCameraAdapter | Viewpoint 私有方法与字段的反射绑定和调用 |
| OrbitInput / ResetMotion | 拖动输入、光标恢复和旋转过渡 |
| CameraCapture | 捕获相机几何，按 Frame 发布相机快照 |
| CameraDrawing | 绘制线程绑定快照，提供相机位置、方向和居中矩阵 |
| Interior | 编排遮挡捕获，按 SceneData 保存结果，提供 CPU 遮挡查询 |
| NativeRooms / NativeCutaway | 从游戏房间和原版可见性状态选取目标空间 |
| OutdoorTargets / OutdoorCutaway / RoomWallOcclusion | 室内外目标、建筑与墙体候选选择 |
| CutawayProjection / WallTopology / WallContour | 连续投影、实体邻墙捕获和墙体端点轮廓 |
| OpeningFade / ObjectFade / ObjectOpacity | 开口及物体表示捕获、按对象共享的透明度状态 |
| OcclusionSnapshot | CPU 查询和 GPU 打包共同消费的遮挡结果 |
| OcclusionRules | 将快照打包为规则表，比较内容并复用结果，不调用 OpenGL |
| OcclusionRendering / CutawayData | 绘制线程的快照绑定、uniform 和 GPU 缓冲上传与恢复 |
| OcclusionShaders | 内置与 Iris 着色器文本转换 |

墙体规则见[墙体遮挡](continuous-cutaway-projection.md)，物体透明度见[对象消隐](object-fade.md)。测试目录中的 `NativeWallContour` 用于原版参考对照，不进入产品 JAR。

## 调用方向

```text
Lua / Hook → 相机控制与输入
捕获 Hook → CameraCapture → 相机快照
                         → Interior → OcclusionSnapshot
绘制 Hook → CameraDrawing / OcclusionRendering → OcclusionRules → CutawayData
着色器 Hook → OcclusionShaders
```

普通业务代码直接调用内部模块。`@Patch` 方法体会被 ZombieBuddy 内联到游戏或 Viewpoint 的外部包中，必须调用 public 类上的 public 方法；包内可见的控制与绘制逻辑通过公开入口访问。移动 Hook 或修改可见性时，需要执行真实内联测试。

Viewpoint 拥有实际的 `View.enabled`、`View.thirdPerson` 和 Look 状态。发出 `cycleView` 请求不等于启用成功，由切换回调确认；FreeCam 暂时使俯视查询失效，不等同于退出模式。

## 捕获与绘制约定

- 相机快照按 Frame 保存，遮挡快照按 SceneData 保存，不合并两者的生命周期。
- 相机快照在遮挡捕获之前发布。绘制只读取已捕获状态，不推进旋转或物体透明度。
- 普通视角和 `frame.onCamera` 路径覆盖旧的俯视标记，避免帧复用残留。
- 绘制开始绑定线程快照，结束时释放；中途清理失败仍继续执行必要的后续清理，并传播异常。
- `OcclusionSnapshot` 的 origin、player、direction、eye 使用 Viewpoint 渲染轴；矩形、墙体位置和 floor 使用游戏坐标。
- 捕获端拥有快照列表和树网格数组；消费端按只读约定使用，不在每帧做全量深拷贝。
- 规则表最多容纳 32 个上层区域和 128 条墙规则。内容相同时复用打包结果。

## 相机输入

拖动前记录光标位置，旋转使用相对位移；Viewpoint 解除捕获并回中后恢复原位置，坐标限制在窗口内。水平输入采用 8 毫秒时间常数的插值，最大角度滞后为 0.006 弧度。松开后停止追赶目标，不增加松手惯性。绘制阶段只消费捕获结果。

## 验证

构建与分组测试入口见[开发命令](../scripts/README.md)，行为覆盖与限制见[测试说明](../java/tests/README.md)。离线测试不替代实际 GPU、完整注入链和游戏画面验证。
