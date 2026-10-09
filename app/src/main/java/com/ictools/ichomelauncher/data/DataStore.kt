package com.ictools.ichomelauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** アプリ全体で共有する DataStore（Preferences）。同一ファイルに複数インスタンスを作らないよう拡張プロパティで1つに限定する */
val Context.ihlDataStore: DataStore<Preferences> by preferencesDataStore(name = "ihl_prefs")
