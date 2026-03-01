// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.system

import androidx.annotation.Keep

@Keep
class StructGroup(
    @JvmField val gr_name: String,
    @JvmField val gr_passwd: String,
    @JvmField val gr_id: Int,
    @JvmField val gr_mem: Array<String>
)
