# OldPoint

OldPoint 为《Project Zomboid》Build 42 的 Viewpoint 模组增加可游玩的俯视镜头，以及镜头、瞄准、交互和建筑遮挡的兼容处理。

This repository contains the source code for the OldPoint Project Zomboid mod. It adds a playable overhead camera to Viewpoint, with camera, aiming, interaction, and building occlusion adjustments.

## 版本 / Version

当前源码为 **0.6.153 测试候选版**。它已编译，但此版本的游戏内效果尚待验证。创意工坊已发布版本可能不同。

This is the **0.6.153 candidate source**. It compiles, but this version has not yet been confirmed in game. The published Workshop version may differ.

## 依赖 / Dependencies

- Project Zomboid Build 42.21
- Viewpoint 0.1.6
- ZombieBuddy 2.3.0 or later, for Java patching
- JDK with `javac --release 17` support

These game and mod dependencies are **not included** in this repository. Their names and APIs remain the property of their respective owners.

## 构建 / Build

在 Windows PowerShell 中运行，并将参数换成你本机的实际路径：

```powershell
.\java\build.ps1 `
  -Jdk 'C:\path\to\jdk' `
  -ZombieBuddy 'C:\path\to\ZombieBuddy.jar' `
  -Viewpoint 'C:\path\to\Viewpoint.jar' `
  -Game 'C:\path\to\projectzomboid.jar'
```

脚本将生成 `42/media/java/client/OldPoint.jar`。将 `42` 目录安装为 OldPoint 模组。`42/media/lua` 包含 Lua 源码和翻译。

The script builds `42/media/java/client/OldPoint.jar`. Install the `42` directory as the OldPoint mod. Lua sources and translations are under `42/media/lua`.

## 参与开发 / Contributing

请从 `master` 创建修改分支，并通过 Pull Request 提交，由 `@lxf-sinksky` 审查。步骤见 [CONTRIBUTING.md](CONTRIBUTING.md)。

Base changes on `master` and submit a pull request for review by `@lxf-sinksky`. See [CONTRIBUTING.md](CONTRIBUTING.md).

## 许可证 / License

Copyright (C) 2026 sinksky. 本仓库中的 OldPoint 原创源码、脚本和翻译以 **GNU GPL v3.0 only** 授权，完整条款见 [LICENSE](LICENSE)（SPDX: `GPL-3.0-only`）。第三方游戏、模组及其素材不包含在本授权中；见 [THIRD_PARTY.md](THIRD_PARTY.md)。

Copyright (C) 2026 sinksky. Original OldPoint code, scripts, and translations in this repository are licensed under **GNU GPL v3.0 only** (SPDX: `GPL-3.0-only`). See [LICENSE](LICENSE). Third-party games, mods, and assets are outside this license.
