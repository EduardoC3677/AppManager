// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import androidx.annotation.WorkerThread
import java.io.IOException
import java.io.OutputStreamWriter

class PathWriter
/**
 * Constructs a ProxyFileWriter object given a File object.
 *
 * @param file a Path object to write to.
 * @throws IOException if the file exists but is a directory rather than
 * a regular file, does not exist but cannot be created,
 * or cannot be opened for any other reason
 */
@WorkerThread
@Throws(IOException::class)
constructor(file: Path) : OutputStreamWriter(file.openOutputStream())
