// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig

class SysConfigInfo(
    @SysConfigType val type: String,
    val name: String,
    val isPackage: Boolean
) {
    var actors: Array<String>? = null
    var classNames: Array<String>? = null
    var whitelist: BooleanArray? = null
    var packages: Array<String>? = null
    var filename: String? = null
    var gids: IntArray? = null
    var permissions: Array<String>? = null
    var dependencies: Array<String>? = null
    var userTypes: Array<String>? = null
    var perUser: Boolean = false
    var targetSdk: Int = 0
    var targetSdks: IntArray? = null
    var version: Int = 0
}
