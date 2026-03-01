// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader

class PathReader
/**
 * Creates a new [PathReader], given the <tt>Path</tt>
 * to read from.
 *
 * @param file the <tt>Path</tt> to read from
 * @throws FileNotFoundException if the file does not exist,
 * is a directory rather than a regular file,
 * or for some other reason cannot be opened for
 * reading.
 */
@Throws(IOException::class)
constructor(file: Path) : InputStreamReader(file.openInputStream())
