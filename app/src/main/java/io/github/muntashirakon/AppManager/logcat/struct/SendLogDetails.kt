// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct

import io.github.muntashirakon.io.Path

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
data class SendLogDetails(
    var subject: String? = null,
    var attachment: Path? = null,
    var attachmentType: String? = null
)
