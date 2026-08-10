package com.bopomofobruce.decoder.nativ

import android.content.Context
import android.content.res.AssetManager
import java.io.File

/**
 * `libbpmf.so` needs a writable filesystem directory containing the chewing dictionary files
 * (`word.dat`, `tsi.dat`) — it cannot read them straight out of the APK's compressed assets. This
 * extracts them once into [Context.getCacheDir] and returns that directory's path.
 *
 * `bpmf_init()` (see `cmake/include/bpmf.h`) is the only caller in mind; `:decoder` (W2-A) is
 * expected to call [getDataPath] once per process and pass the result straight through.
 */
private const val CHEWING_ASSET_SUBDIR = "chewing"
private const val CHEWING_CACHE_DIR_NAME = "chewing"

/** Convenience wrapper for production callers: extracts into `context.cacheDir/chewing`. */
fun getDataPath(context: Context): String =
    extractChewingData(context.assets, File(context.cacheDir, CHEWING_CACHE_DIR_NAME)).absolutePath

/**
 * Copies every file under the `chewing/` assets directory into [targetDir], creating it if needed.
 * Idempotent: a file already present with non-zero length is assumed up to date and skipped, so
 * repeated app starts don't re-copy multi-megabyte dictionaries every time.
 *
 * Kept independent of [Context] (takes [AssetManager] + [File] directly) so it's testable as a
 * plain JVM unit test with a mocked [AssetManager], without needing Robolectric.
 */
fun extractChewingData(assets: AssetManager, targetDir: File): File {
    if (!targetDir.exists() && !targetDir.mkdirs() && !targetDir.exists()) {
        throw IllegalStateException("Could not create chewing data dir: $targetDir")
    }

    val assetNames = assets.list(CHEWING_ASSET_SUBDIR) ?: emptyArray()
    for (name in assetNames) {
        val target = File(targetDir, name)
        if (target.exists() && target.length() > 0L) {
            continue
        }
        assets.open("$CHEWING_ASSET_SUBDIR/$name").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return targetDir
}
