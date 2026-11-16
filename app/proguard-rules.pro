# App 自定义 R8/ProGuard 规则。
# Compose、Room、androidx.startup 等库都自带 consumer 规则，一般不用在这里加东西。
# 只有出现「release 包能找到 debug 找不到」的反射/序列化问题时才需要补 keep 规则。

# 保留行号信息，方便看懂 release 包的崩溃堆栈
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
