# 开发命令

在仓库根目录用 PowerShell 运行。所有脚本按自身位置定位仓库，生成文件写入 `java/build/`，Java 模组产物写入 `42/media/java/client/OldPoint.jar`。

## 依赖

准备能够读取当前游戏依赖字节码的 JDK（本机使用 JDK 25，编译输出为 Java 17），以及 README 中列出的游戏、Viewpoint 和 ZombieBuddy JAR。行为测试另需 Python 3。

可将 `scripts/environment.example.psd1` 复制为 `.local/environment.psd1`，填写本机路径。`.local/` 不进入 Git；`test.ps1` 和 `format.ps1` 会读取这个配置。

## 构建

```powershell
.\scripts\build.ps1 -Jdk 'C:\path\to\jdk' -ZombieBuddy 'C:\path\to\ZombieBuddy.jar' -Viewpoint 'C:\path\to\Viewpoint.jar' -Game 'C:\path\to\projectzomboid.jar'
```

只编译 `java/src/` 并打包 Java 模组，不自动运行测试。依赖通过参数传入。

## 测试

配置本机依赖后运行：

```powershell
.\scripts\test.ps1
```

也可显式传入与构建相同的四个依赖参数。测试入口先完整构建，再运行镜头生命周期、帧捕获、绘制和 Hook 内联测试。参见 [测试说明](../java/tests/README.md)。

## 格式检查

```powershell
.\scripts\format.ps1 -Check
.\scripts\format.ps1
```

第二条命令会格式化源码。需要额外准备固定版本的格式化工具，版本、校验值和参数见 [排版说明](../docs/formatting.md)。

以上公开入口都不会将文件复制到已安装的创意工坊模组。
