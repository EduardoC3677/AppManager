// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

// Copyright 2017 Zheng Li
object ServerActions {
    // This hard coded value won't cause any issue because it's only used internally.
    const val PACKAGE_NAME: String = "io.github.muntashirakon.AppManager"

    const val ACTION_SERVER_STARTED: String = "$PACKAGE_NAME.action.SERVER_STARTED"
    const val ACTION_SERVER_CONNECTED: String = "$PACKAGE_NAME.action.SERVER_CONNECTED"
    const val ACTION_SERVER_DISCONNECTED: String = "$PACKAGE_NAME.action.SERVER_DISCONNECTED"
    const val ACTION_SERVER_STOPPED: String = "$PACKAGE_NAME.action.SERVER_STOPED"
}
