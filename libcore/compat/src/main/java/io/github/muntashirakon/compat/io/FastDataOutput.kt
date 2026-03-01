// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.compat.io

import java.io.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

/**
 * Optimized implementation of {@link DataOutput} which buffers data in memory
 * before flushing to the underlying {@link OutputStream}.
 * <p>
 * Benchmarks have demonstrated this class is 2x more efficient than using a
 * {@link DataOutputStream} with a {@link BufferedOutputStream}.
 */
// NOTE: This class is not actually optimised because we can't use VMRuntime right now
class FastDataOutput(
    private var mOut: OutputStream,
    bufferSize: Int,
    private val mUse4ByteSequence: Boolean
) : DataOutput, Flushable, Closeable {

    // private final VMRuntime mRuntime;

    private val mBuffer: ByteArray
    // private final long mBufferPtr;
    private val mBufferCap: Int

    private var mBufferPos = 0

    /**
     * Values that have been "interned" by {@link #writeInternedUTF(String)}.
     */
    private val mStringRefs = HashMap<String, Short>()

    /**
     * @deprecated callers must specify {@code use4ByteSequence} so they make a
     *             clear choice about working around a long-standing ART bug, as
     *             described by the {@code kUtfUse4ByteSequence} comments in
     *             {@code art/runtime/jni/jni_internal.cc}.
     */
    @Deprecated("callers must specify use4ByteSequence")
    constructor(out: OutputStream, bufferSize: Int) : this(out, bufferSize, true)

    init {
        // mRuntime = VMRuntime.getRuntime();
        if (bufferSize < 8) {
            throw IllegalArgumentException()
        }

        mBuffer = ByteArray(bufferSize) // (byte[]) mRuntime.newNonMovableArray(byte.class, bufferSize);
        // mBufferPtr = mRuntime.addressOf(mBuffer);
        mBufferCap = mBuffer.size
        
        // setOutput logic moved to init block directly or handled by constructor params
        // setOutput(out) // Not needed as mOut is set in constructor
        mBufferPos = 0
        mStringRefs.clear()
    }

    /**
     * Release a {@link FastDataOutput} to potentially be recycled. You must not
     * interact with the object after releasing it.
     */
    fun release() {
        if (mBufferPos > 0) {
            throw IllegalStateException("Lingering data, call flush() before releasing.")
        }

        // mOut = null // Cannot nullify due to non-null type
        mBufferPos = 0
        mStringRefs.clear()

        if (mBufferCap == DEFAULT_BUFFER_SIZE && mUse4ByteSequence) {
            // Try to return to the cache.
            sOutCache.compareAndSet(null, this)
        }
    }

    /**
     * Re-initializes the object for the new output.
     */
    private fun setOutput(out: OutputStream) {
        mOut = out
        mBufferPos = 0
        mStringRefs.clear()
    }

    @Throws(IOException::class)
    private fun drain() {
        if (mBufferPos > 0) {
            mOut.write(mBuffer, 0, mBufferPos)
            mBufferPos = 0
        }
    }

    @Throws(IOException::class)
    override fun flush() {
        drain()
        mOut.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        mOut.close()
        release()
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        writeByte(b)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (mBufferCap < len) {
            drain()
            mOut.write(b, off, len)
        } else {
            if (mBufferCap - mBufferPos < len) drain()
            System.arraycopy(b, off, mBuffer, mBufferPos, len)
            mBufferPos += len
        }
    }

    @Throws(IOException::class)
    override fun writeUTF(s: String) {
        // Attempt to write directly to buffer space if there's enough room,
        // otherwise fall back to chunking into place
        if (mBufferCap - mBufferPos < 2 + s.length) drain()

        // Unfoturnately, we cannot use VMRuntime, so we will take len to be negative as specified below
        // and insert manually
        val tmp = s.toByteArray(StandardCharsets.UTF_8)
        writeShort(tmp.size)
        write(tmp, 0, tmp.size)
    }

    /**
     * Write a {@link String} value with the additional signal that the given
     * value is a candidate for being canonicalized, similar to
     * {@link String#intern()}.
     * <p>
     * Canonicalization is implemented by writing each unique string value once
     * the first time it appears, and then writing a lightweight {@code short}
     * reference when that string is written again in the future.
     *
     * @see FastDataInput#readInternedUTF()
     */
    @Throws(IOException::class)
    fun writeInternedUTF(s: String) {
        val ref = mStringRefs[s]
        if (ref != null) {
            writeShort(ref.toInt())
        } else {
            writeShort(MAX_UNSIGNED_SHORT)
            writeUTF(s)

            // We can only safely intern when we have remaining values; if we're
            // full we at least sent the string value above
            val newRef = mStringRefs.size.toShort()
            if (newRef < MAX_UNSIGNED_SHORT) {
                mStringRefs[s] = newRef
            }
        }
    }

    @Throws(IOException::class)
    override fun writeBoolean(v: Boolean) {
        writeByte(if (v) 1 else 0)
    }

    @Throws(IOException::class)
    override fun writeByte(v: Int) {
        if (mBufferCap - mBufferPos < 1) drain()
        mBuffer[mBufferPos++] = ((v shr 0) and 0xff).toByte()
    }

    @Throws(IOException::class)
    override fun writeShort(v: Int) {
        if (mBufferCap - mBufferPos < 2) drain()
        mBuffer[mBufferPos++] = ((v shr 8) and 0xff).toByte()
        mBuffer[mBufferPos++] = ((v shr 0) and 0xff).toByte()
    }

    @Throws(IOException::class)
    override fun writeChar(v: Int) {
        writeShort(v)
    }

    @Throws(IOException::class)
    override fun writeInt(v: Int) {
        if (mBufferCap - mBufferPos < 4) drain()
        mBuffer[mBufferPos++] = ((v shr 24) and 0xff).toByte()
        mBuffer[mBufferPos++] = ((v shr 16) and 0xff).toByte()
        mBuffer[mBufferPos++] = ((v shr 8) and 0xff).toByte()
        mBuffer[mBufferPos++] = ((v shr 0) and 0xff).toByte()
    }

    @Throws(IOException::class)
    override fun writeLong(v: Long) {
        if (mBufferCap - mBufferPos < 8) drain()
        var i = (v shr 32).toInt()
        mBuffer[mBufferPos++] = ((i shr 24) and 0xff).toByte()
        mBuffer[mBufferPos++] = ((i shr 16) and 0xff).toByte()
        mBuffer[mBufferPos++] = ((i shr 8) and 0xff).toByte()
        mBuffer[mBufferPos++] = ((i shr 0) and 0xff).toByte()
        i = v.toInt()
        mBuffer[mBufferPos++] = ((i shr 24) and 0xff).toByte()
        mBuffer[mBufferPos++] = ((i shr 16) and 0xff).toByte()
        mBuffer[mBufferPos++] = ((i shr 8) and 0xff).toByte()
        mBuffer[mBufferPos++] = ((i shr 0) and 0xff).toByte()
    }

    @Throws(IOException::class)
    override fun writeFloat(v: Float) {
        writeInt(java.lang.Float.floatToIntBits(v))
    }

    @Throws(IOException::class)
    override fun writeDouble(v: Double) {
        writeLong(java.lang.Double.doubleToLongBits(v))
    }

    override fun writeBytes(s: String) {
        // Callers should use writeUTF()
        throw UnsupportedOperationException()
    }

    override fun writeChars(s: String) {
        // Callers should use writeUTF()
        throw UnsupportedOperationException()
    }

    companion object {
        private const val MAX_UNSIGNED_SHORT = 65_535
        private const val DEFAULT_BUFFER_SIZE = 32_768

        private val sOutCache = AtomicReference<FastDataOutput>()

        /**
         * Obtain a {@link FastDataOutput} configured with the given
         * {@link OutputStream} and which encodes large code-points using 3-byte
         * sequences.
         * <p>
         * This <em>is</em> compatible with the {@link DataOutput} API contract,
         * which specifies that large code-points must be encoded with 3-byte
         * sequences.
         */
        @JvmStatic
        fun obtainUsing3ByteSequences(out: OutputStream): FastDataOutput {
            return FastDataOutput(out, DEFAULT_BUFFER_SIZE, false /* use4ByteSequence */)
        }

        /**
         * Obtain a {@link FastDataOutput} configured with the given
         * {@link OutputStream} and which encodes large code-points using 4-byte
         * sequences.
         * <p>
         * This <em>is not</em> compatible with the {@link DataOutput} API contract,
         * which specifies that large code-points must be encoded with 3-byte
         * sequences.
         */
        @JvmStatic
        fun obtainUsing4ByteSequences(out: OutputStream): FastDataOutput {
            val instance = sOutCache.getAndSet(null)
            if (instance != null) {
                instance.setOutput(out)
                return instance
            }
            return FastDataOutput(out, DEFAULT_BUFFER_SIZE, true /* use4ByteSequence */)
        }
    }
}
