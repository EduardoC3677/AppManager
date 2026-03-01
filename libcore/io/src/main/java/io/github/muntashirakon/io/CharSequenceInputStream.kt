// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.io

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CharsetEncoder
import java.nio.charset.CoderResult
import java.util.*
import kotlin.math.min

/**
 * Implements an [InputStream] to read from String, StringBuffer, StringBuilder or CharBuffer.
 *
 * **Note:** Supports [.mark] and [.reset].
 */
// Copyright 2012 Apache Software Foundation
class CharSequenceInputStream(cs: CharSequence, charset: Charset, bufferSize: Int) : InputStream() {
    private val mByteBuffer: ByteBuffer
    private var mByteBufferMark: Int = 0 // position in mByteBuffer
    private val mCharBuffer: CharBuffer
    private var mCharBufferMark: Int = 0 // position in mCharBuffer
    private val mCharsetEncoder: CharsetEncoder

    /**
     * Constructs a new instance with a buffer size of [IoUtils.DEFAULT_BUFFER_SIZE].
     *
     * @param cs      the input character sequence.
     * @param charset the character set name to use.
     * @throws IllegalArgumentException if the buffer is not large enough to hold a complete character.
     */
    constructor(cs: CharSequence, charset: Charset) : this(cs, charset, IoUtils.DEFAULT_BUFFER_SIZE)

    /**
     * Constructs a new instance.
     *
     * @param cs         the input character sequence.
     * @param charset    the character set name to use, null maps to the default Charset.
     * @param bufferSize the buffer size to use.
     * @throws IllegalArgumentException if the buffer is not large enough to hold a complete character.
     */
    init {
        mCharsetEncoder = charset.newEncoder()
        // Ensure that buffer is long enough to hold a complete character
        mByteBuffer = ByteBuffer.allocate(checkMinBufferSize(mCharsetEncoder, bufferSize))
        mByteBuffer.flip()
        mCharBuffer = CharBuffer.wrap(cs)
        mCharBufferMark = NO_MARK
        mByteBufferMark = NO_MARK
    }

    private fun checkMinBufferSize(encoder: CharsetEncoder, bufferSize: Int): Int {
        val minSize = encoder.maxBytesPerChar().toInt()
        if (bufferSize < minSize) {
            throw IllegalArgumentException("Buffer size $bufferSize is smaller than maxBytesPerChar $minSize")
        }
        return bufferSize
    }

    constructor(cs: CharSequence, bufferSize: Int, charsetEncoder: CharsetEncoder) : this(
        cs,
        charsetEncoder.charset(),
        bufferSize
    )

    /**
     * Constructs a new instance with a buffer size of [IoUtils.DEFAULT_BUFFER_SIZE].
     *
     * @param cs      the input character sequence.
     * @param charset the character set name to use.
     * @throws IllegalArgumentException if the buffer is not large enough to hold a complete character.
     */
    constructor(cs: CharSequence, charset: String) : this(cs, charset, IoUtils.DEFAULT_BUFFER_SIZE)

    /**
     * Constructs a new instance.
     *
     * @param cs         the input character sequence.
     * @param charset    the character set name to use, null maps to the default Charset.
     * @param bufferSize the buffer size to use.
     * @throws IllegalArgumentException if the buffer is not large enough to hold a complete character.
     */
    constructor(cs: CharSequence, charset: String, bufferSize: Int) : this(cs, Charset.forName(charset), bufferSize)

    /**
     * Return an estimate of the number of bytes remaining in the byte stream.
     *
     * @return the count of bytes that can be read without blocking (or returning EOF).
     */
    @Throws(IOException::class)
    override fun available(): Int {
        // The cached entries are in bBuf; since encoding always creates at least one byte
        // per character, we can add the two to get a better estimate (e.g. if bBuf is empty)
        // Note that the implementation in 2.4 could return zero even though there were
        // encoded bytes still available.
        return mByteBuffer.remaining() + mCharBuffer.remaining()
    }

    @Throws(IOException::class)
    override fun close() {
        // noop
    }

    /**
     * Fills the byte output buffer from the input char buffer.
     *
     * @throws CharacterCodingException an error encoding data.
     */
    @Throws(CharacterCodingException::class)
    private fun fillBuffer() {
        mByteBuffer.compact()
        val result = mCharsetEncoder.encode(mCharBuffer, mByteBuffer, true)
        if (result.isError) {
            result.throwException()
        }
        mByteBuffer.flip()
    }

    /**
     * Gets the CharsetEncoder.
     *
     * @return the CharsetEncoder.
     */
    fun getCharsetEncoder(): CharsetEncoder {
        return mCharsetEncoder
    }

    /**
     * {@inheritDoc}
     *
     * @param readlimit max read limit (ignored).
     */
    @Synchronized
    override fun mark(readlimit: Int) {
        mCharBufferMark = mCharBuffer.position()
        mByteBufferMark = mByteBuffer.position()
        mCharBuffer.mark()
        mByteBuffer.mark()
        // It would be nice to be able to use mark & reset on the cBuf and bBuf;
        // however the bBuf is re-used so that won't work
    }

    override fun markSupported(): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun read(): Int {
        while (true) {
            if (mByteBuffer.hasRemaining()) {
                return mByteBuffer.get().toInt() and 0xFF
            }
            fillBuffer()
            if (!mByteBuffer.hasRemaining() && !mCharBuffer.hasRemaining()) {
                return EOF
            }
        }
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    @Throws(IOException::class)
    override fun read(array: ByteArray, off: Int, len: Int): Int {
        Objects.requireNonNull(array, "array")
        if (len < 0 || off + len > array.size) {
            throw IndexOutOfBoundsException("Array Size=" + array.size + ", offset=" + off + ", length=" + len)
        }
        if (len == 0) {
            return 0 // must return 0 for zero length read
        }
        if (!mByteBuffer.hasRemaining() && !mCharBuffer.hasRemaining()) {
            return EOF
        }
        var bytesRead = 0
        var currentOff = off
        var currentLen = len
        while (currentLen > 0) {
            if (mByteBuffer.hasRemaining()) {
                val chunk = min(mByteBuffer.remaining(), currentLen)
                mByteBuffer.get(array, currentOff, chunk)
                currentOff += chunk
                currentLen -= chunk
                bytesRead += chunk
            } else {
                fillBuffer()
                if (!mByteBuffer.hasRemaining() && !mCharBuffer.hasRemaining()) {
                    break
                }
            }
        }
        return if (bytesRead == 0 && !mByteBuffer.hasRemaining() && !mCharBuffer.hasRemaining()) EOF else bytesRead
    }

    @Synchronized
    @Throws(IOException::class)
    override fun reset() {
        if (mByteBufferMark != NO_MARK) {
            // if there is a mark in the byte buffer, start from there
            mByteBuffer.position(mByteBufferMark)
            mCharBuffer.position(mCharBufferMark)
            mCharBuffer.mark()
            mByteBuffer.mark()
            // We need to keep the mark in the char buffer, as that is not reset
            // when the byte buffer is compact()-ed.
        }
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        var skipped = 0L
        while (n > skipped && available() > 0) {
            read()
            skipped++
        }
        return skipped
    }

    companion object {
        private const val NO_MARK = -1
        private const val EOF = -1
    }
}
