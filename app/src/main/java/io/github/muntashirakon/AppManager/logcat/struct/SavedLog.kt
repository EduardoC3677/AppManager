// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct

// Copyright 2012 Nolan Lawson
data class SavedLog(
    val logLines: List<String>,
    val isTruncated: Boolean
)
