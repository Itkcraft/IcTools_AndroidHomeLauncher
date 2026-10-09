package com.ictools.ichomelauncher.ui.settings

/** 使用ライブラリ1件分（名前とライセンス） */
data class LibraryInfo(val name: String, val license: String)

/** 使用ライブラリ一覧。ライブラリを追加したら README と合わせてここにも追記する */
val USED_LIBRARIES = listOf(
    LibraryInfo("AndroidX (Core, Activity, Lifecycle)", "Apache License 2.0"),
    LibraryInfo("Jetpack Compose", "Apache License 2.0"),
    LibraryInfo("Jetpack DataStore", "Apache License 2.0"),
    LibraryInfo("kotlinx.serialization", "Apache License 2.0"),
    LibraryInfo("kotlinx.coroutines", "Apache License 2.0"),
    LibraryInfo("Kotlin Standard Library", "Apache License 2.0")
)

/** 同梱しているライセンス全文のパス（assets 内） */
const val APACHE_LICENSE_ASSET = "licenses/APACHE-2.0.txt"
