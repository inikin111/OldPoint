# 俯视模式深度范围

OldPoint 俯视模式的近裁面从 Viewpoint 默认的 `0.05` 改为 `2.0` 世界单位。
相机距离、15° FOV、宽高比和远裁面保持原值；普通 Viewpoint 视角不启用此调整。
目的是增加远处相邻表面的深度分辨能力，尚不能据此宣布游戏内闪烁已修复。

近裁面固定为 `2.0`，不改变 FOV、远裁面和相机距离。
捕获场景进入俯视模式时标记 cameraCut，交由 Viewpoint 重置时间历史。

`ProjectionDepth` 管理捕获帧到渲染帧的场景标记，使用捕获结果而非实时相机开关。
`CameraCapture` 在拾取快照之前更新投影的两个深度系数；Viewpoint 后续生成的视图投影、
逆矩阵和 TAA 历史矩阵从这一投影派生。

以下入口同步对应近裁面：

- `FrameContext.camera`：内置效果的 `uCamera`。
- `PackStages.uniforms`：Viewpoint 光影阶段的 `uCamera`。
- `IrisUniforms.matrices`：Iris 主场景投影，在计算逆矩阵及保存历史之前更新。
- `IrisUniforms.camera`：Iris `near` uniform。
- `VendorInputs.describe`：超分辨率输入的 near 元数据；保留原有矩阵、FOV 和重置状态。

Iris 主场景远裁面继续采用其原公式，包括自由相机的额外距离。
阴影投影及独立远景投影不改近裁面。
不修改几何、贴花层级或 `pulled()` 的表面偏移公式。

验证包含真实 JOML 投影及逆投影、横纵投影系数保持不变、约四十倍的理想深度间隔、
Iris/超分辨率元数据、复用及独立普通场景隔离，以及五个实际 Viewpoint 方法的 advice 内联。
相机和遮挡的现有测试也应通过。
这些检查不启动 OpenGL，也不代替游戏场景的端到端验证。

游戏内重点检查：远处墙面与井盖是否改善，右键拾取、雾效、TAA 和光影是否正常。
距离相机不足 `2.0` 世界单位的几何会被近裁面裁掉；极近距离或穿墙视角需要留意这一变化。
