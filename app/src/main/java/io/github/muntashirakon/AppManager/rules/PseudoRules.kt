// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules

import android.os.RemoteException
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.IOException

class PseudoRules(packageName: String, userHandle: Int) : RulesStorageManager(packageName, userHandle) {
    init {
        setReadOnly()
    }

    override fun setMutable() {
        // Do nothing
    }

    @Throws(IOException::class, RemoteException::class)
    fun loadExternalEntries(file: Path) {
        super.loadEntries(file, true)
    }

    /**
     * No rules will be loaded
     *
     * @return /dev/null
     */
    override fun getDesiredFile(create: Boolean): Path {
        return Paths.get("/dev/null")
    }
}
