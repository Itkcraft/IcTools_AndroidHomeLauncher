package com.ictools.ichomelauncher.data

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * アプリの種類（カテゴリ）。
 * 既定はアプリ自身が Android に申告しているカテゴリ（ApplicationInfo.category）から決め、
 * ユーザーが個別に変更したり、自分で種類を作ったりできる。
 */
object AppCategories {
    const val OTHER = "その他"

    /** 自動で付く種類（表示順） */
    val AUTO = listOf("ゲーム", "SNS", "音楽", "動画", "写真", "ニュース", "地図", "仕事効率化", "ユーザー補助")

    /** アプリの申告カテゴリを種類名に変換する（申告が無ければ「その他」） */
    fun fromAndroid(info: ApplicationInfo): String = when (info.category) {
        ApplicationInfo.CATEGORY_GAME -> "ゲーム"
        ApplicationInfo.CATEGORY_SOCIAL -> "SNS"
        ApplicationInfo.CATEGORY_AUDIO -> "音楽"
        ApplicationInfo.CATEGORY_VIDEO -> "動画"
        ApplicationInfo.CATEGORY_IMAGE -> "写真"
        ApplicationInfo.CATEGORY_NEWS -> "ニュース"
        ApplicationInfo.CATEGORY_MAPS -> "地図"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "仕事効率化"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "ユーザー補助"
        else -> OTHER
    }
}

/** 保存する種類の設定 */
@Serializable
data class CategoryData(
    val overrides: Map<String, String> = emptyMap(),  // AppEntry.key → ユーザーが選んだ種類
    val custom: List<String> = emptyList()            // ユーザーが作った種類（作った順）
) {
    /** 種類の一覧（自分で作った種類 → 自動の種類 → その他） */
    val all: List<String> get() = (custom + AppCategories.AUTO + AppCategories.OTHER).distinct()

    /** アプリの種類（変更していれば変更後、していなければ自動） */
    fun of(app: AppEntry): String = overrides[app.key] ?: app.autoCategory
}

/** 種類の設定の保存 */
class CategoryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("app_categories")

    suspend fun load(): CategoryData {
        val raw = context.ihlDataStore.data.first()[key] ?: return CategoryData()
        return runCatching { json.decodeFromString<CategoryData>(raw) }.getOrDefault(CategoryData())
    }

    suspend fun save(data: CategoryData) {
        val raw = json.encodeToString(data)
        context.ihlDataStore.edit { it[key] = raw }
    }
}
