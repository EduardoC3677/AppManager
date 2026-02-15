// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

/**
 * Stores individual app details item
 */
open class AppDetailsItem<T> @JvmOverloads constructor(
    @JvmField val item: T,
    @JvmField var name: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppDetailsItem<*>) return false
        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
