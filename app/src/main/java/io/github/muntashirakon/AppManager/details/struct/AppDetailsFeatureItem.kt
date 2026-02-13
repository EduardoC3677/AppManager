// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import android.content.pm.FeatureInfo

class AppDetailsFeatureItem(
    featureInfo: FeatureInfo,
    @JvmField val available: Boolean
) : AppDetailsItem<FeatureInfo>(featureInfo) {

    @JvmField val required: Boolean = (featureInfo.flags and FeatureInfo.FLAG_REQUIRED) != 0

    companion object {
        const val OPEN_GL_ES = "OpenGL ES"
    }
}
