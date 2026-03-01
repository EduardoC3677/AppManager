// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

object DirectoryUtils {
    private fun isDirectoryChanged(
        directory: Path, sinceTimestamp: Long, maxDepth: Int,
        ignoredDirs: Set<String>, currentDepth: Int
    ): Boolean {
        // Check depth limit
        if (currentDepth > maxDepth) {
            return false
        }

        // Ensure it's a valid directory
        if (!directory.exists() || !directory.isDirectory()) {
            return false
        }

        try {
            val files = directory.listFiles()
            for (file in files) {
                // Skip ignored folders
                if (file.isDirectory() && ignoredDirs.contains(file.getName())) {
                    continue
                }

                // Check if file/directory has been modified since timestamp
                if (file.lastModified() > sinceTimestamp ||
                    file.creationTime() > sinceTimestamp
                ) {
                    return true
                }

                // Recursively check subdirectories
                if (file.isDirectory() && currentDepth < maxDepth) {
                    if (isDirectoryChanged(
                            file, sinceTimestamp, maxDepth, ignoredDirs,
                            currentDepth + 1
                        )
                    ) {
                        // Early return
                        return true
                    }
                }
            }
        } catch (e: SecurityException) {
            // Handle permission errors gracefully
            return false
        }
        return false // No changes detected
    }

    @JvmStatic
    fun isDirectoryChanged(
        directoryPath: Path, sinceTimestamp: Long,
        maxDepth: Int, ignoredDirs: Set<String>
    ): Boolean {
        return isDirectoryChanged(directoryPath, sinceTimestamp, maxDepth, ignoredDirs, 0)
    }
}
