// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.entity.FmFavorite
import io.github.muntashirakon.io.Path
import kotlinx.coroutines.runBlocking

object FmFavoritesManager {
    private val sFavoriteAddedLiveData = MutableLiveData<FmFavorite?>()

    @JvmStatic
    fun getFavoriteAddedLiveData(): LiveData<FmFavorite?> {
        return sFavoriteAddedLiveData
    }

    @JvmStatic
    @WorkerThread
    fun addToFavorite(path: Path, options: FmActivity.Options): Long {
        val fmFavorite = FmFavorite().apply {
            name = path.name
            uri = if (options.isVfs) options.uri.toString() else path.uri.toString()
            initUri = if (options.isVfs) path.uri.toString() else null
            this.options = options.options
        }
        val id = runBlocking { AppsDb.getInstance().fmFavoriteDao().insert(fmFavorite) }
        sFavoriteAddedLiveData.postValue(fmFavorite)
        return id
    }

    @JvmStatic
    @WorkerThread
    fun removeFromFavorite(id: Long) {
        runBlocking { AppsDb.getInstance().fmFavoriteDao().delete(id) }
        sFavoriteAddedLiveData.postValue(null)
    }

    @JvmStatic
    fun renameFavorite(id: Long, newName: String) {
        runBlocking { AppsDb.getInstance().fmFavoriteDao().rename(id, newName) }
        sFavoriteAddedLiveData.postValue(null)
    }

    @JvmStatic
    @WorkerThread
    fun getAllFavorites(): List<FmFavorite> {
        return runBlocking { AppsDb.getInstance().fmFavoriteDao().getAll() }
    }
}
