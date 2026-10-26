# Java 代码排版

统一使用四空格缩进、120 列行宽、运算符与参数间的空格，方法体和多条语句展开到独立行。复杂计算先提取有意义的局部变量，再排版；避免仅靠嵌套换行形成阶梯。if/else、for、while、do 的语句体补齐花括号；else if 链保持自然连接。不修改字符串内容、导入顺序或业务逻辑。

运行：

```powershell
.\scripts\format.ps1
.\scripts\format.ps1 -Check
```

脚本读取本机 `.local/environment.psd1` 中的 Jdk，也接受 `-Jdk` 和 `-Formatter` 参数。格式检查会在 `java/build/format-tools` 生成工具类，不改源码。

格式化包括 `java/src`、`java/tests` 和 `tools` 中的 Java 文件，不处理 Lua、Python 模拟运行环境字符串或生成文件。

工具：google-java-format 1.37.0，使用 AOSP 四空格风格。默认 JAR 路径为 `.local/tools/google-java-format-1.37.0-all-deps.jar`。

- 官方下载：https://github.com/google/google-java-format/releases/download/v1.37.0/google-java-format-1.37.0-all-deps.jar
- SHA-256：`834b2a0c38cb774953322a84b5ca3f2f40dd3156650b3cd44d3b744345962f7a`

`ReadableJavaFormat` 先通过 JDK 语法树定位没有花括号的控制流语句体，再交给格式化工具排版。它不会用正则表达式匹配条件或改写字符串。普通格式化工具保留的单行 if-return 在这里也会展开。
