package com.bopomofobruce.decoder.nativ

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.IOException

/**
 * `libbpmf.so` needs a readable filesystem directory containing the chewing dictionary files
 * (`word.dat`, `tsi.dat`) — it cannot read them straight out of the APK's compressed assets. This
 * extracts them once into [Context.getCacheDir] and returns that directory's path.
 *
 * `bpmf_init()` (see `cmake/include/bpmf.h`) only needs this directory to be *readable* — `bpmf.h`
 * documents `data_path` as syspath-only, and W1-A deliberately passes NULL as libchewing's userpath
 * (no built-in user dictionary), so nothing libchewing-side ever writes into this directory.
 * [Context.getCacheDir] being writable is a requirement of *this function's own* extraction
 * (copying assets in, atomically renaming `.tmp` files into place — see [extractChewingData]'s
 * KDoc), not a requirement `bpmf_init()` imposes.
 *
 * `bpmf_init()` (see `cmake/include/bpmf.h`) is the only caller in mind; `:decoder` (W2-A) is
 * expected to call [getDataPath] once per process and pass the result straight through.
 */
private const val CHEWING_ASSET_SUBDIR = "chewing"
private const val CHEWING_CACHE_DIR_PREFIX = "chewing-"

/**
 * Must be bumped in lockstep with `VERSION` in `decoder-native/scripts/fetch_chewing_data.sh` every
 * time that script's `VERSION` changes (i.e. every time the packaged `word.dat`/`tsi.dat` content
 * changes). There is no automated check tying these two together — they are two different languages
 * (Kotlin vs. bash) built at different times (this one at app-compile time, the script's at
 * CI/dev-machine asset-fetch time) — so this is a manual invariant, not a mechanically-enforced
 * one.
 *
 * Why this exists at all (K5): [extractChewingData] only checks "does the file already exist at its
 * final cache path" — it has no way to tell "cached word.dat" apart from "this app version's
 * word.dat". Folding the data version into the cache directory name means an app upgrade that ships
 * new dictionary data gets a brand-new, never-before-seen directory name, so the "already
 * extracted" fast path can never accidentally match stale content from a previous app version's
 * cacheDir (which Android preserves across app upgrades).
 */
private const val CHEWING_DATA_VERSION = "2026.3.22"

private val CHEWING_CACHE_DIR_NAME = "$CHEWING_CACHE_DIR_PREFIX$CHEWING_DATA_VERSION"

/** Serializes [extractChewingData] calls — see its KDoc for why. */
private val extractionLock = Any()

/**
 * Convenience wrapper for production callers: extracts into `context.cacheDir/chewing-$VERSION`
 * (see [CHEWING_DATA_VERSION]) and deletes any sibling `chewing-*` directories left behind by a
 * previous app version's data — see [deleteStaleChewingCacheDirs].
 */
fun getDataPath(context: Context): String {
    val cacheDir = context.cacheDir
    deleteStaleChewingCacheDirs(cacheDir)
    return extractChewingData(context.assets, File(cacheDir, CHEWING_CACHE_DIR_NAME)).absolutePath
}

/**
 * Deletes every `chewing-*` directory directly under [cacheDir] other than the current
 * [CHEWING_CACHE_DIR_NAME] — leftovers from a previous app version's dictionary data (see K5 note
 * on [CHEWING_DATA_VERSION]). [Context.getCacheDir] is preserved across app upgrades by the
 * platform, so without this, an upgrade that bumps the dictionary version would leave the old
 * version's directory on disk forever (unbounded growth), never read again once
 * [CHEWING_CACHE_DIR_NAME] changes name.
 *
 * Deliberately best-effort: a directory that fails to delete (e.g. still open by a lagging process)
 * is left behind rather than throwing — going stale on disk is a (bounded, single-directory) space
 * leak, not a correctness problem, since [getDataPath] never reads from it again once the current
 * version's directory exists.
 */
private fun deleteStaleChewingCacheDirs(cacheDir: File) {
    val staleDirs =
        cacheDir.listFiles { file ->
            file.isDirectory &&
                file.name.startsWith(CHEWING_CACHE_DIR_PREFIX) &&
                file.name != CHEWING_CACHE_DIR_NAME
        } ?: return
    for (staleDir in staleDirs) {
        staleDir.deleteRecursively()
    }
}

/**
 * Copies every file under the `chewing/` assets directory into [targetDir], creating it if needed.
 *
 * Self-healing / idempotent **only against a killed process retrying against the SAME [targetDir]
 * with the SAME asset content** — never against the asset content itself changing (e.g. an app
 * upgrade shipping a new dictionary version) while [targetDir] stays the same. This function has no
 * way to detect "the file at this final name is stale, not just present"; it treats final-name
 * presence as proof of completeness, full stop (see K5 — [getDataPath] is what actually handles the
 * "content changed" case, by giving each dictionary version its own [targetDir] name so this
 * function's "does the final name already exist" check can never fire against stale content).
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
