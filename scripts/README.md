# 开发命令

在仓库根目录用 PowerShell 运行。脚本按自身位置定位仓库；构建和验证生成文件集中写入 Git 忽略的 `java/build/`。

## 依赖配置

准备能读取游戏依赖字节码的 JDK（本机使用 JDK 25，输出为 Java 17），以及游戏、Viewpoint 和 ZombieBuddy JAR。涉及模拟环境或参考对照的测试另需 Python 3。

将 `scripts/environment.example.psd1` 复制为 `.local/environment.psd1` 并填写路径。构建、测试共用配置读取与依赖检查，显式参数优先。

```powershell
.\scripts\build.ps1 -Jdk 'C:\path\to\jdk' -ZombieBuddy 'C:\path\to\ZombieBuddy.jar' -Viewpoint 'C:\path\to\Viewpoint.jar' -Game 'C:\path\to\projectzomboid.jar'
```

## 构建

配置本机依赖后：

```powershell
.\scripts\build.ps1
.\scripts\build.ps1 -SkipPackage
```

两种方式均编译 `java/src/`，生成 `java/build/OldPoint.jar`。默认还将 JAR 复制到模组分发目录 `42/media/java/client/OldPoint.jar`；`-SkipPackage` 只生成构建产物。均不自动运行测试。

## 测试

```powershell
.\scripts\test.ps1
.\scripts\test.ps1 -Suite Camera
.\scripts\test.ps1 -Suite Occlusion,Hooks
.\scripts\test.ps1 -Suite Reference
.\scripts\test.ps1 -Suite All
```

| 分组 | 内容 |
| --- | --- |
| Camera | 生命周期、拖动输入、帧捕获、绘制与异常清理 |
| Occlusion | 室内外选择、拓扑与轮廓、对象透明度、规则打包、几何和投影边界 |
| Hooks | 真实 ByteBuddy 内联后的跨包执行 |
| Reference | 原版列选择、游戏字节码模板、独立屏幕投影查询与轮廓模型对照 |
| All | 以上全部 |

默认运行 Camera、Occlusion、Hooks。每次先重新构建，使用 `java/build/OldPoint.jar`，不覆盖分发目录的 JAR。失败即停止。测试接受与构建相同的四个依赖路径参数。

Reference 适合遮挡算法变更或依赖升级时运行。投影查询、参考结果和轮廓模型结果写入 `java/build/`；不把运行报告写回 `docs/`。覆盖和局限见[测试说明](../java/tests/README.md)。

## 诊断与研究工具

`tools/summarize_capture_cost.py` 汇总本机游戏日志，例如：

```powershell
python tools/summarize_capture_cost.py --since 2026-10-28 --output .local/capture-cost.json
```

额外的原版图集对照工具 `tools/verify_native_wall_contours.py` 需要本机反编译源码与贴图，使用 `--help` 查看参数；它不属于默认测试。研究报告、截图和一次性结果保存在 `.local/`，可复现的临时查询和测试输出保存在 `java/build/`。

以上入口都不会部署到已安装的创意工坊模组。
