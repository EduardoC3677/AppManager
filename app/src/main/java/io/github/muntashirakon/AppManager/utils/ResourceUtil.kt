// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat

class ResourceUtil {
    class ParsedResource internal constructor(
        private val mPackageName: String,
        private val mRes: Resources,
        @DrawableRes private val mResId: Int
    ) {
        fun getPackageName(): String {
            return mPackageName
        }

        /**
         * @see ResourcesCompat.getDrawable
         */
        fun getDrawable(): Drawable? {
            return getDrawable(null)
        }

        /**
         * @see ResourcesCompat.getDrawable
         */
        fun getDrawable(theme: Resources.Theme?): Drawable? {
            return ResourcesCompat.getDrawable(mRes, mResId, theme)
        }
    }

    var packageName: String? = null
    var className: String? = null
    var resources: Resources? = null

    fun loadResources(pm: PackageManager, packageName: String): Boolean {
        return try {
            this.packageName = packageName
            this.className = null
            this.resources = pm.getResourcesForApplication(packageName)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun loadResources(pm: PackageManager, packageName: String, className: String): Boolean {
        return try {
            this.packageName = packageName
            this.className = className
            this.resources = pm.getResourcesForActivity(ComponentName(packageName, className))
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun loadAndroidResources(): Boolean {
        this.packageName = "android"\nthis.className = null
        this.resources = Resources.getSystem()
        return true
    }

    /**
     * Return the string value associated with a particular resource ID. It will be stripped of any styled text information.
     *
     * @param stringRes The desired resource identifier.
     * @return String The string data associated with the resource, stripped of styled text information.
     * @throws Resources.NotFoundException Throws NotFoundException if the given ID does not exist.
     */
    @SuppressLint("DiscouragedApi")
    fun getString(stringRes: String): String {
        val resources = this.resources
            ?: throw Resources.NotFoundException("No resource could be loaded.")
        val intStringRes = resources.getIdentifier(stringRes, "string", packageName)
        if (intStringRes == 0) {
            throw Resources.NotFoundException("String resource ID $stringRes")
        }
        return resources.getString(intStringRes)
    }

    companion object {
        /**
         * Parse a resource name having the following format:
         * <p>
         * <code>
         * package-name:type/res-name
         * </code>
         */
        @JvmStatic
        @Throws(PackageManager.NameNotFoundException::class, Resources.NotFoundException::class)
        fun getResourceFromName(pm: PackageManager, resName: String): ParsedResource {
            val indexOfColon = resName.indexOf(':')
            val indexOfSlash = resName.indexOf('/')
            if (indexOfColon == -1 || indexOfSlash == -1) {
                throw Resources.NotFoundException("Resource $resName is not found.")
            }
            val packageName = resName.substring(0, indexOfColon)
            val type = resName.substring(indexOfColon + 1, indexOfSlash)
            val name = resName.substring(indexOfSlash + 1)
            val res = pm.getResourcesForApplication(packageName)
            @SuppressLint("DiscouragedApi")
            val resId = res.getIdentifier(name, type, packageName)
            if (resId == 0) {
                throw Resources.NotFoundException("Resource $name of type $type is not found in package $packageName")
            }
            return ParsedResource(packageName, res, resId)
        }

        @JvmStatic
        @SuppressLint("DiscouragedApi")
        fun getRawDataId(context: Context, name: String): Int {
            return context.resources.getIdentifier(name, "raw", context.packageName)
        }
    }
}
