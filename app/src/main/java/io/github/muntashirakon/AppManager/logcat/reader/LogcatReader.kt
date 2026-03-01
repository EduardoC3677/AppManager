// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader

import java.io.IOException

/**
 * Copyright 2012 Nolan Lawson
 */
interface LogcatReader {
    /**
     * Read a single log line, ala [java.io.BufferedReader.readLine].
     *
     * @return A single log line
     */
    @Throws(IOException::class)
    fun readLine(): String?

    /**
     * Kill the reader and close all resources without throwing any exceptions.
     */
    fun killQuietly()

    fun readyToRecord(): Boolean

    fun getProcesses(): List<Process>
}
