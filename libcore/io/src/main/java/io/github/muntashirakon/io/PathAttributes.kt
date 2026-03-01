// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

open class PathAttributes protected constructor(
    val name: String,
    val mimeType: String?,
    val lastModified: Long,
    val lastAccess: Long,
    val creationTime: Long,
    val isRegularFile: Boolean,
    val isDirectory: Boolean,
    val isSymbolicLink: Boolean,
    val size: Long
) {
    val isOtherFile: Boolean = !isRegularFile && !isDirectory && !isSymbolicLink
}
