// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto

import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.io.Path
import java.io.InputStream
import java.io.OutputStream

class DummyCrypto : Crypto {
    override val modeName: String
        get() = CryptoUtils.MODE_NO_ENCRYPTION

    override fun encrypt(inputFiles: Array<Path>, outputFiles: Array<Path>) {
        // Do nothing since both are the same set of files
    }

    override fun encrypt(unencryptedStream: InputStream, encryptedStream: OutputStream) {
        // Do nothing since both are the same stream
    }

    override fun decrypt(inputFiles: Array<Path>, outputFiles: Array<Path>) {
        // Do nothing since both are the same set of files
    }

    override fun decrypt(encryptedStream: InputStream, unencryptedStream: OutputStream) {
        // Do nothing since both are the same stream
    }

    override fun close() {
        // Nothing to close
    }
}
