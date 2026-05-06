// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.BackupException
import io.github.muntashirakon.AppManager.backup.BackupFlags
import io.github.muntashirakon.AppManager.backup.BackupItems
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.backup.MetadataManager
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.crypto.CryptoException
import io.github.muntashirakon.AppManager.profiles.ProfileManager
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.io.Path
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/**
 * Smart Launcher Backup Converter
 *
 * Converts Smart Launcher backup files to AppManager profiles/tags.
 * Smart Launcher stores app categories in JSON format with the following structure:
 * - Pages containing app icons organized into categories
 * - Each category has a name and list of package names
 * - App vectors for ML-based categorization (optional)
 *
 * This converter extracts the category information and creates AppManager profiles
 * that users can use to filter and organize their apps.
 */
class SLConverter(private val mBackupFile: Path) : Converter() {
    companion object {
        @JvmField
        val TAG: String = SLConverter::class.java.simpleName
        
        private const val CATEGORY_PREFIX = "sl_category_"\n}

    private val mContext: Context = ContextUtils.getContext()
    private val mPm: PackageManager = mContext.packageManager
    private val mConvertedPackages = mutableListOf<String>()
    private val mCreatedProfiles = mutableListOf<String>()
    private var mBackupTime: Long = 0
    private var mCategoryCount: Int = 0

    override val packageName: String = "SmartLauncher_Import"\noverride fun convert() {
        mBackupTime = mBackupFile.lastModified()
        
        try {
            val backupContent = mBackupFile.openInputStream().use { 
                it.bufferedReader().use { reader -> reader.readText() }
            }
            
            val backupJson = try {
                JSONObject(backupContent)
            } catch (e: JSONException) {
                // Try parsing as array (some SL backup formats)
                try {
                    val jsonArray = JSONArray(backupContent)
                    processSmartLauncherJson(jsonArray)
                    return
                } catch (e2: Exception) {
                    throw BackupException("Invalid Smart Launcher backup format", e2)
                }
            }
            
            // Process different Smart Launcher backup structures
            processSmartLauncherBackup(backupJson)
            
            Log.i(TAG, "Successfully imported $mCategoryCount categories with $mConvertedPackages.size apps from Smart Launcher backup")
            
        } catch (e: IOException) {
            throw BackupException("Failed to read Smart Launcher backup file", e)
        } catch (e: JSONException) {
            throw BackupException("Failed to parse Smart Launcher backup JSON", e)
        } catch (e: Exception) {
            throw BackupException("Unknown error during Smart Launcher import", e)
        }
    }

    override fun cleanup() {
        // No cleanup needed - we don't modify the source file
        mConvertedPackages.clear()
        mCreatedProfiles.clear()
    }

    /**
     * Process Smart Launcher backup JSON structure
     * Different SL versions may have different structures
     */
    @SuppressLint("NewApi")
    private fun processSmartLauncherBackup(backupJson: JSONObject) {
        // Structure 1: pages -> icons -> category structure
        if (backupJson.has("pages")) {
            val pages = backupJson.getJSONArray("pages")
            processPages(pages)
            return
        }
        
        // Structure 2: categories directly
        if (backupJson.has("categories")) {
            val categories = backupJson.getJSONArray("categories")
            processCategories(categories)
            return
        }
        
        // Structure 3: app vectors (ML-based categorization)
        if (backupJson.has("app_vectors")) {
            val appVectors = backupJson.getJSONArray("app_vectors")
            processAppVectors(appVectors)
            return
        }
        
        // Structure 4: Try to detect any package-to-category mappings
        val keys = backupJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            try {
                val value = backupJson.get(key)
                if (value is JSONArray) {
                    processCategoryArray(key, value)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process key: $key", e)
            }
        }
    }

    /**
     * Process Smart Launcher pages structure
     */
    private fun processPages(pages: JSONArray) {
        val categoryMap = mutableMapOf<String, MutableSet<String>>()
        
        for (i in 0 until pages.length()) {
            val page = pages.getJSONObject(i)
            val pageName = page.optString("name", "Page ${i + 1}")
            
            // Process icons on this page
            if (page.has("icons")) {
                val icons = page.getJSONArray("icons")
                for (j in 0 until icons.length()) {
                    val icon = icons.getJSONObject(j)
                    val packageName = icon.optString("package")
                    val category = icon.optString("category", pageName)
                    
                    if (packageName.isNotEmpty()) {
                        categoryMap.getOrPut(category) { mutableSetOf() }.add(packageName)
                    }
                }
            }
        }
        
        // Convert category map to profiles
        for ((categoryName, packages) in categoryMap) {
            createProfileForCategory(categoryName, packages.toList())
        }
    }

    /**
     * Process Smart Launcher categories structure
     */
    private fun processCategories(categories: JSONArray) {
        for (i in 0 until categories.length()) {
            val category = categories.getJSONObject(i)
            val categoryName = category.optString("name", "Category ${i + 1}")
            
            val packages = mutableListOf<String>()
            if (category.has("apps")) {
                val apps = category.getJSONArray("apps")
                for (j in 0 until apps.length()) {
                    val app = apps.getJSONObject(j)
                    val packageName = app.optString("package")
                    if (packageName.isNotEmpty()) {
                        packages.add(packageName)
                    }
                }
            } else if (category.has("packages")) {
                val packagesArray = category.getJSONArray("packages")
                for (j in 0 until packagesArray.length()) {
                    packages.add(packagesArray.getString(j))
                }
            }
            
            if (packages.isNotEmpty()) {
                createProfileForCategory(categoryName, packages)
            }
        }
    }

    /**
     * Process Smart Launcher app vectors (ML-based categorization)
     */
    private fun processAppVectors(appVectors: JSONArray) {
        val categoryMap = mutableMapOf<String, MutableSet<String>>()
        
        for (i in 0 until appVectors.length()) {
            val appVector = appVectors.getJSONObject(i)
            val packageName = appVector.optString("key")
            
            if (packageName.isNotEmpty() && appVector.has("value")) {
                val value = appVector.getJSONObject("value")
                val category = value.optString("category", "Uncategorized")
                categoryMap.getOrPut(category) { mutableSetOf() }.add(packageName)
            }
        }
        
        // Convert to profiles
        for ((categoryName, packages) in categoryMap) {
            createProfileForCategory(categoryName, packages.toList())
        }
    }

    /**
     * Process a generic category array structure
     */
    private fun processCategoryArray(categoryName: String, packages: JSONArray) {
        val packageList = mutableListOf<String>()
        for (i in 0 until packages.length()) {
            val packageName = packages.optString(i)
            if (packageName.isNotEmpty()) {
                packageList.add(packageName)
            }
        }
        
        if (packageList.isNotEmpty()) {
            createProfileForCategory(categoryName, packageList)
        }
    }

    /**
     * Process Smart Launcher JSON array format
     */
    private fun processSmartLauncherJson(jsonArray: JSONArray) {
        val categoryMap = mutableMapOf<String, MutableSet<String>>()
        
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val packageName = item.optString("key")
            
            if (packageName.isNotEmpty() && item.has("value")) {
                val value = item.getJSONObject("value")
                val category = value.optString("category", "Imported")
                categoryMap.getOrPut(category) { mutableSetOf() }.add(packageName)
            }
        }
        
        // Convert to profiles
        for ((categoryName, packages) in categoryMap) {
            createProfileForCategory(categoryName, packages.toList())
        }
    }

    /**
     * Create an AppManager profile for a Smart Launcher category
     */
    @SuppressLint("NewApi")
    private fun createProfileForCategory(categoryName: String, packages: List<String>) {
        val sanitizedCategoryName = sanitizeCategoryName(categoryName)
        val profileName = "$CATEGORY_PREFIX$sanitizedCategoryName"\n// Check if profile already exists
        val existingProfiles = ProfileManager.getProfiles()
        for (profile in existingProfiles) {
            if (profile.name == profileName) {
                Log.d(TAG, "Profile $profileName already exists, skipping")
                return
            }
        }
        
        // Filter to only installed packages
        val installedPackages = packages.filter { isPackageInstalled(it) }
        mConvertedPackages.addAll(installedPackages)
        
        if (installedPackages.isEmpty()) {
            Log.w(TAG, "No installed packages found for category: $categoryName")
            return
        }
        
        // Create profile
        try {
            val profile = AppsProfile()
            profile.name = profileName
            profile.packageNames = installedPackages.toTypedArray()
            profile.isEnabled = true
            
            ProfileManager.addProfile(profile)
            mCreatedProfiles.add(profileName)
            mCategoryCount++
            
            Log.i(TAG, "Created profile '$profileName' with ${installedPackages.size} apps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create profile for category: $categoryName", e)
        }
    }

    /**
     * Check if a package is installed on the device
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            mPm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Sanitize category name for use as profile name
     */
    private fun sanitizeCategoryName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(50) // Limit length
    }

    /**
     * Get import statistics
     */
    fun getImportStats(): ImportStats {
        return ImportStats(
            categoryCount = mCategoryCount,
            totalPackages = mConvertedPackages.size,
            profileNames = mCreatedProfiles.toList()
        )
    }

    /**
     * Statistics about the import operation
     */
    data class ImportStats(
        val categoryCount: Int,
        val totalPackages: Int,
        val profileNames: List<String>
    )
}
