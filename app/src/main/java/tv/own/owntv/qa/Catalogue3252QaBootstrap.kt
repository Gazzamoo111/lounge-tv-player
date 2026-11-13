package tv.own.owntv.qa

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import tv.own.owntv.BuildConfig
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncResult

/**
 * One-time, QA-package-only switch for the 3,252 channel staging release.
 * NEVER runs in production; preserves existing Xtream credentials, profiles,
 * movie/series caches and stored original URL for rollback.
 */
internal object Catalogue3252QaBootstrap {
    private const val TAG = "Lounge3252QA"
    private const val URL =
        "https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-core-xtream-qa-3252"

    private fun eligible(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.contains("connect.loungetv.nz") ||
            lower.contains("/functions/v1/lounge-core-xtream")
    }

    fun start(context: Context, scope: CoroutineScope) {
        if (!BuildConfig.DEBUG || !BuildConfig.LOUNGE_TV_2 ||
            !BuildConfig.APPLICATION_ID.endsWith(".v2qa")) return

        scope.launch {
            delay(2_500L)
            try {
                val dao = GlobalContext.get().get<SourceDao>()
                val repository = GlobalContext.get().get<SourceRepository>()
                val flags = context.getSharedPreferences(
                    "loungetv_qa_catalogue_3252", Context.MODE_PRIVATE
                )
                val sources = dao.getAllOnce().filter {
                    it.type == SourceType.XTREAM && eligible(it.url)
                }
                if (sources.isEmpty()) {
                    Log.w(TAG, "No Lounge Xtream playlist found; existing playlists unchanged")
                }
                for (original in sources) {
                    val marker = "synced_source_" + original.id
                    if (original.url == URL && flags.getBoolean(marker, false)) continue

                    // Preserve the login and all user settings. Only swap the
                    // server URL, and synchronise the Live portion of this source.
                    if (original.url != URL) {
                        flags.edit()
                            .putString("original_url_" + original.id, original.url)
                            .apply()
                    }
                    val qaSource = original.copy(url = URL)
                    try {
                        if (original.url != URL) dao.update(qaSource)
                        val result = repository.sync(
                            source = qaSource,
                            contentTypes = SyncContentTypes(
                                live = true, movies = false, series = false
                            ),
                            forcePrune = true,
                            onProgress = {},
                        )
                        if (result is SyncResult.Success) {
                            flags.edit().putBoolean(marker, true).apply()
                            Log.i(TAG, "QA Live catalogue synchronised; source=" + original.id)
                        } else {
                            if (original.url != URL) dao.update(original)
                            Log.e(TAG, "QA sync unsuccessful; previous source URL restored")
                        }
                    } catch (failure: Exception) {
                        if (original.url != URL) dao.update(original)
                        Log.e(TAG, "QA sync failed; previous source URL restored", failure)
                    }
                }
            } catch (failure: Exception) {
                Log.e(TAG, "QA bootstrap did not run; existing source unchanged", failure)
            }
        }
    }
}
