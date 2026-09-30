# Memory Gate の R8 設定
# Room / DataStore / Navigation Compose / Compose は各ライブラリが consumer ProGuard ルールを同梱しているため、
# 追加の keep ルールは基本的に不要。マニフェストから参照されるクラス（Activity / Service / Application）は
# AAPT が生成するルールで保持される。

# スタックトレースを読みやすくするため行番号を保持する
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
