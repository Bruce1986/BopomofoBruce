package com.bopomofobruce.decoder.nativ

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.IOException

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

/** Serializes [extractChewingData] calls — see its KDoc for why. */
private val extractionLock = Any()

/** Convenience wrapper for production callers: extracts into `context.cacheDir/chewing`. */
fun getDataPath(context: Context): String =
    extractChewingData(context.assets, File(context.cacheDir, CHEWING_CACHE_DIR_NAME)).absolutePath

/**
 * Copies every file under the `chewing/` assets directory into [targetDir], creating it if needed.
 *
 * Self-healing / idempotent by construction, not by inspection: each asset is copied into a sibling
 * `<name>.tmp` file first, then atomically moved into place with [File.renameTo]. A file is only
 * ever considered "already extracted" by the FINAL name existing — never by inspecting its length —
 * because the final name can only come into existence via a completed rename. A process kill
 * mid-copy (e.g. low-memory kill) can therefore only ever leave behind an orphaned `.tmp` file,
 * never a truncated file at the final name; the next call harmlessly overwrites that `.tmp` and
 * retries. If the rename itself fails, the `.tmp` file is deleted and an [IOException] is thrown —
 * no partial state is left at the final name either way.
 *
 * Synchronized on [extractionLock]: without it, two threads racing this call for the first time
 * could both observe the final file missing, then interleave writes into (or one truncate the
 * other's) the same `.tmp` path before either renames.
 *
 * **The guarantees above hold within a single process only.** [extractionLock] is an ordinary JVM
 * monitor, so two processes each get their own instance and neither blocks the other; they can
 * interleave writes into the same `.tmp` path and then both rename, atomically publishing
 * half-written content under the final name. No module currently declares `android:process`
 * (checked across all seven manifests), so this is not reachable today — but if the IME service is
 * ever split into its own process, this must move to a file lock
 * ([java.nio.channels.FileChannel.lock]) or the extraction must be funnelled through a single
 * designated component.
 *
 * Kept independent of [Context] (takes [AssetManager] + [File] directly) so it's testable as a
 * plain JVM unit test with a mocked [AssetManager], without needing Robolectric.
 */
fun extractChewingData(assets: AssetManager, targetDir: File): File =
    synchronized(extractionLock) {
        if (!targetDir.exists() && !targetDir.mkdirs() && !targetDir.exists()) {
            throw IllegalStateException("Could not create chewing data dir: $targetDir")
        }

        val assetNames = assets.list(CHEWING_ASSET_SUBDIR) ?: emptyArray()
        for (name in assetNames) {
            val target = File(targetDir, name)
            if (target.exists()) {
                continue
            }
            val tmp = File(targetDir, "$name.tmp")
            assets.open("$CHEWING_ASSET_SUBDIR/$name").use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("Could not rename $tmp to $target")
            }
        }
        targetDir
    }
