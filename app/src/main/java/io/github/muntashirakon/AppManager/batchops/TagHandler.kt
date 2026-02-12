// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops

import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.entity.App
import io.github.muntashirakon.AppManager.logs.Logger
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.types.UserPackagePair
import kotlinx.coroutines.runBlocking

object TagHandler {
    @JvmStatic
    fun opEditTags(
        info: BatchOpsManager.BatchOpsInfo,
        progressHandler: ProgressHandler?,
        logger: Logger?,
        tags: String?
    ): BatchOpsManager.Result {
        val failedPackages = ArrayList<UserPackagePair>()
        val lastProgress = progressHandler?.lastProgress ?: 0f
        val appDao = AppsDb.getInstance().appDao()

        val max = info.size()
        for (i in 0 until max) {
            progressHandler?.postUpdate(lastProgress + i + 1)
            val pair = info.getPair(i)
            
            try {
                runBlocking {
                    appDao.updateTags(pair.packageName, pair.userId, tags)
                }
            } catch (e: Exception) {
                failedPackages.add(pair)
                logger?.println("====> op=EDIT_TAGS, pkg=$pair failed", e)
            }
        }
        return BatchOpsManager.Result(failedPackages)
    }

    @JvmStatic
    fun getTags(app: App): List<String> {
        return app.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    @JvmStatic
    fun setTags(app: App, tags: List<String>) {
        app.tags = tags.joinToString(",")
    }
}
