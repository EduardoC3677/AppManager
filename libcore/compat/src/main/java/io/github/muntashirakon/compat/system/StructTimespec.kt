// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.compat.system

import androidx.annotation.Keep

/**
 * Corresponds to C's {@code struct timespec} from {@code <time.h>}.
 */
@Keep
class StructTimespec(
    /**
     * Seconds part of time of last data modification.
     */
    @JvmField val tv_sec: Long /*time_t*/,
    /**
     * Nanoseconds (values are [0, 999999999]).
     */
    @JvmField val tv_nsec: Long
) : Comparable<StructTimespec> {

    init {
        if (tv_nsec != OsCompat.UTIME_OMIT && tv_nsec != OsCompat.UTIME_NOW) {
            if (tv_nsec < 0 || tv_nsec > 999_999_999) {
                throw IllegalArgumentException("tv_nsec value $tv_nsec is not in [0, 999999999]")
            }
        }
    }

    override fun compareTo(other: StructTimespec): Int {
        if (tv_sec > other.tv_sec) {
            return 1
        }
        if (tv_sec < other.tv_sec) {
            return -1
        }
        if (tv_nsec > other.tv_nsec) {
            return 1
        }
        if (tv_nsec < other.tv_nsec) {
            return -1
        }
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as StructTimespec

        if (tv_sec != that.tv_sec) return false
        return tv_nsec == that.tv_nsec
    }

    override fun hashCode(): Int {
        var result = (tv_sec xor (tv_sec ushr 32)).toInt()
        result = 31 * result + (tv_nsec xor (tv_nsec ushr 32)).toInt()
        return result
    }

    override fun toString(): String {
        return "StructTimespec{" +
                "tv_sec=" + tv_sec +
                ", tv_nsec=" + tv_nsec +
                '}'
    }
}
