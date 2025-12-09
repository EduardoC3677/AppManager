// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageVolume
import android.os.storage.StorageVolumeHidden
import android.text.TextUtils
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.collection.ArrayMap
import androidx.core.content.ContextCompat
import dev.rikka.tools.refine.Refine
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.PathReader
import io.github.muntashirakon.io.Paths
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.util.Arrays
import java.util.HashSet

object StorageUtils {
    @JvmField
    val TAG = "StorageUtils"

    private const val ENV_SECONDARY_STORAGE = "SECONDARY_STORAGE"

    @JvmStatic
    @WorkerThread
    fun getAllStorageLocations(context: Context): ArrayMap<String, Uri> {
        val storageLocations = ArrayMap<String, Uri>(10)
        val sdCard = Paths.get(Environment.getExternalStorageDirectory())
        addStorage(context.getString(R.string.external_storage), sdCard, storageLocations)
        getStorageEnv(context, storageLocations)
        retrieveStorageManager(context, storageLocations)
        retrieveStorageFilesystem(storageLocations)
        getStorageExternalFilesDir(context, storageLocations)
        // Get SAF persisted directories
        val grantedUrisAndDate = SAFUtils.getUrisWithDate(context)
        for (i in 0 until grantedUrisAndDate.size) {
            val uri = grantedUrisAndDate.keyAt(i)
            val time = grantedUrisAndDate.valueAt(i)
            if (Paths.get(uri).isDirectory()) {
                // Only directories are locations
                val readableName = Paths.getLastPathSegment(uri.path) + " " + DateUtils.formatDate(context, time)
                storageLocations[readableName] = getFixedTreeUri(uri)
            }
        }
        return storageLocations
    }

    /**
     * unified test function to add storage if fitting
     */
    private fun addStorage(label: String, entry: Path?, storageLocations: MutableMap<String, Uri>) {
        if (entry == null) {
            return
        }
        var actualEntry = entry
        if (actualEntry.isSymbolicLink()) {
            // Use the real path
            val finalEntry = actualEntry
            actualEntry = ExUtils.requireNonNullElse({ finalEntry.realPath }, finalEntry)
        }
        val uri = actualEntry.uri
        if (!storageLocations.containsValue(uri)) {
            storageLocations[label] = uri
        } else {
            Log.d(TAG, entry.uri.toString())
        }
    }

    /**
     * Get storage from ENV, as recommended by 99%, doesn't detect external SD card, only internal ?!
     */
    private fun getStorageEnv(context: Context, storageLocations: MutableMap<String, Uri>) {
        val rawSecondaryStorage = System.getenv(ENV_SECONDARY_STORAGE)
        if (!TextUtils.isEmpty(rawSecondaryStorage)) {
            val externalCards = rawSecondaryStorage!!.split(":")
            for (i in externalCards.indices) {
                val path = externalCards[i]
                addStorage(context.getString(R.string.sd_card) + if (i == 0) "" else " $i", Paths.get(path), storageLocations)
            }
        }
    }

    /**
     * Get storage indirect, best solution so far
     */
    private fun getStorageExternalFilesDir(context: Context, storageLocations: MutableMap<String, Uri>) {
        // Get primary & secondary external device storage (internal storage & micro SDCARD slot...)
        val listExternalDirs = ContextCompat.getExternalFilesDirs(context, null)
        for (listExternalDir in listExternalDirs) {
            if (listExternalDir != null) {
                val path = listExternalDir.absolutePath
                val indexMountRoot = path.indexOf("/Android/data/")
                if (indexMountRoot >= 0) {
                    // Get the root path for the external directory
                    val file = Paths.get(path.substring(0, indexMountRoot))
                    addStorage(file.name, file, storageLocations)
                }
            }
        }
    }

    /**
     * Get storages via StorageManager
     */
    private fun retrieveStorageManager(context: Context, storageLocations: MutableMap<String, Uri>) {
        val storageVolumes = HashSet<StorageVolume>()
        val users = Users.getUsersIds()
        for (user in users) {
            try {
                storageVolumes.addAll(Arrays.asList(*StorageManagerCompat.getVolumeList(context, user, 0)))
            } catch (ignore: SecurityException) {
            }
        }
        try {
            for (volume in storageVolumes) {
                val vol = Refine.unsafeCast<StorageVolumeHidden>(volume)
                val dir = vol.pathFile ?: continue
                val label = vol.userLabel
                addStorage(label ?: dir.name, Paths.get(dir), storageLocations)
            }
            Log.d(TAG, "used storagemanager")
        } catch (e: Exception) {
            Log.w(TAG, "error during storage retrieval", e)
        }
    }

    /**
     * Get storage via /proc/mounts, probably never works
     */
    private fun retrieveStorageFilesystem(storageLocations: MutableMap<String, Uri>) {
        val mountFile = Paths.get("/proc/mounts")
        if (!mountFile.isDirectory()) {
            return
        }
        try {
            BufferedReader(PathReader(mountFile)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("/dev/block/vold/")) {
                        val lineElements = line!!.split(" ")
                        val element = Paths.get(lineElements[1])
                        // Don't add the default mount path since it's already in the list.
                        addStorage(element.name, element, storageLocations)
                    }
                }
            }
        } catch (ignore: IOException) {
        }
    }

    private fun getFixedTreeUri(uri: Uri): Uri {
        val paths = uri.pathSegments
        val size = paths.size
        if (size < 2 || "tree" != paths[0]) {
            throw IllegalArgumentException("Not a tree URI.")
        }
        // FORMAT: /tree/<id>/document/<id>%2F<others>
        return when (size) {
            2 -> uri.buildUpon()
                .appendPath("document")
                .appendPath(paths[1])
                .build()
            3 -> {
                if ("document" != paths[2]) {
                    throw IllegalArgumentException("Not a document URI.")
                }
                uri.buildUpon()
                    .appendPath(paths[1])
                    .build()
            }
            4 -> {
                if ("document" != paths[2]) {
                    throw IllegalArgumentException("Not a document URI.")
                }
                uri
            }
            else -> throw IllegalArgumentException("Malformed URI.")
        }
    }
}
