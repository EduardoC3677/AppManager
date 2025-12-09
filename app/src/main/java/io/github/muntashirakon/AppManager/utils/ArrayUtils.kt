// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.utils

// Source: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/com/android/internal/util/ArrayUtils.java

import android.os.Build
import android.util.ArraySet
import androidx.annotation.CheckResult
import androidx.annotation.RequiresApi
import aosp.libcore.util.EmptyArray
import java.io.File
import java.lang.reflect.Array
import java.util.Collections
import java.util.Objects
import java.util.function.IntFunction
import java.util.function.Predicate

/**
 * ArrayUtils contains some methods that you can call to find out
 * the most efficient increments by which to grow arrays.
 */
@Suppress("unused")
object ArrayUtils {
    private const val CACHE_SIZE = 73
    private val sCache = arrayOfNulls<Any>(CACHE_SIZE)

    @JvmField
    val EMPTY_FILE = arrayOf<File>()

    /**
     * Throws [ArrayIndexOutOfBoundsException] if the range is out of bounds.
     *
     * @param len    length of the array. Must be non-negative
     * @param offset start index of the range. Must be non-negative
     * @param count  length of the range. Must be non-negative
     * @throws ArrayIndexOutOfBoundsException if the range from [offset] with length
     *                                        [count] is out of bounds of the array
     */
    @JvmStatic
    fun throwsIfOutOfBounds(len: Int, offset: Int, count: Int) {
        if (len < 0) {
            throw ArrayIndexOutOfBoundsException("Negative length: $len")
        }

        if ((offset or count) < 0 || offset > len - count) {
            throw ArrayIndexOutOfBoundsException(
                "length=$len; regionStart=$offset; regionLength=$count"
            )
        }
    }

    /**
     * Checks if the beginnings of two byte arrays are equal.
     *
     * @param array1 the first byte array
     * @param array2 the second byte array
     * @param length the number of bytes to check
     * @return true if they're equal, false otherwise
     */
    @JvmStatic
    fun equals(array1: ByteArray?, array2: ByteArray?, length: Int): Boolean {
        if (length < 0) {
            throw IllegalArgumentException()
        }

        if (array1 === array2) {
            return true
        }
        if (array1 == null || array2 == null || array1.size < length || array2.size < length) {
            return false
        }
        for (i in 0 until length) {
            if (array1[i] != array2[i]) {
                return false
            }
        }
        return true
    }

    /**
     * Returns an empty array of the specified type.  The intent is that
     * it will return the same empty array every time to avoid reallocation,
     * although this is not guaranteed.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> emptyArray(kind: Class<T>): Array<T> {
        if (kind == Any::class.java) {
            return EmptyArray.OBJECT as Array<T>
        }

        val bucket = (kind.hashCode() and 0x7FFFFFFF) % CACHE_SIZE
        var cache = sCache[bucket]

        if (cache == null || cache.javaClass.componentType != kind) {
            cache = Array.newInstance(kind, 0)
            sCache[bucket] = cache

            // Log.e("cache", "new empty " + kind.getName() + " at " + bucket);
        }

        return cache as Array<T>
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    @JvmStatic
    fun isEmpty(array: Collection<*>?): Boolean {
        return array == null || array.isEmpty()
    }

    /**
     * Checks if given map is null or has zero elements.
     */
    @JvmStatic
    fun isEmpty(map: Map<*, *>?): Boolean {
        return map == null || map.isEmpty()
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    @JvmStatic
    fun <T> isEmpty(array: Array<T>?): Boolean {
        return array == null || array.isEmpty()
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    @JvmStatic
    fun isEmpty(array: IntArray?): Boolean {
        return array == null || array.isEmpty()
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    @JvmStatic
    fun isEmpty(array: LongArray?): Boolean {
        return array == null || array.isEmpty()
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    @JvmStatic
    fun isEmpty(array: ByteArray?): Boolean {
        return array == null || array.isEmpty()
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    @JvmStatic
    fun isEmpty(array: BooleanArray?): Boolean {
        return array == null || array.isEmpty()
    }

    /**
     * Length of the given array or 0 if it's null.
     */
    @JvmStatic
    fun size(array: Array<*>?): Int {
        return array?.size ?: 0
    }

    /**
     * Length of the given collection or 0 if it's null.
     */
    @JvmStatic
    fun size(collection: Collection<*>?): Int {
        return collection?.size ?: 0
    }

    /**
     * Checks that value is present as at least one of the elements of the array.
     *
     * @param array the array to check in
     * @param value the value to check for
     * @return true if the value is present in the array
     */
    @JvmStatic
    fun <T> contains(array: Array<T>?, value: T): Boolean {
        return indexOf(array, value) != -1
    }

    /**
     * Return first index of [value] in [array], or `-1` if
     * not found.
     */
    @JvmStatic
    fun <T> indexOf(array: Array<T>?, value: T): Int {
        if (array == null) return -1
        for (i in array.indices) {
            if (Objects.equals(array[i], value)) return i
        }
        return -1
    }

    /**
     * Test if all [check] items are contained in [array].
     */
    @JvmStatic
    fun <T> containsAll(array: Array<T>?, check: Array<T>?): Boolean {
        if (check == null) return true
        for (checkItem in check) {
            if (!contains(array, checkItem)) {
                return false
            }
        }
        return true
    }

    /**
     * Test if any [check] items are contained in [array].
     */
    @JvmStatic
    fun <T> containsAny(array: Array<T>?, check: Array<T>?): Boolean {
        if (check == null) return false
        for (checkItem in check) {
            if (contains(array, checkItem)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun contains(array: IntArray?, value: Int): Boolean {
        if (array == null) return false
        for (element in array) {
            if (element == value) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun contains(array: LongArray?, value: Long): Boolean {
        if (array == null) return false
        for (element in array) {
            if (element == value) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun contains(array: CharArray?, value: Char): Boolean {
        if (array == null) return false
        for (element in array) {
            if (element == value) {
                return true
            }
        }
        return false
    }

    /**
     * Test if all [check] items are contained in [array].
     */
    @JvmStatic
    fun containsAll(array: CharArray?, check: CharArray?): Boolean {
        if (check == null) return true
        for (checkItem in check) {
            if (!contains(array, checkItem)) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun total(array: LongArray?): Long {
        var total = 0L
        if (array != null) {
            for (value in array) {
                total += value
            }
        }
        return total
    }

    @JvmStatic
    fun max(array: IntArray): Int {
        var candidate = array[0]
        for (i in 1 until array.size) {
            val next = array[i]
            if (next > candidate) {
                candidate = next
            }
        }
        return candidate
    }

    @JvmStatic
    fun max(array: LongArray): Long {
        var candidate = array[0]
        for (i in 1 until array.size) {
            val next = array[i]
            if (next > candidate) {
                candidate = next
            }
        }
        return candidate
    }

    @JvmStatic
    fun max(array: FloatArray): Float {
        var candidate = array[0]
        for (i in 1 until array.size) {
            val next = array[i]
            if (next > candidate) {
                candidate = next
            }
        }
        return candidate
    }

    @JvmStatic
    fun <T> max(array: Array<T>): T where T : Comparable<T> {
        var candidate = array[0]
        for (i in 1 until array.size) {
            val next = array[i]
            if (next.compareTo(candidate) > 0) {
                candidate = next
            }
        }
        return candidate
    }

    @JvmStatic
    fun convertToIntArray(list: List<Int>): IntArray {
        val array = IntArray(list.size)
        for (i in list.indices) {
            array[i] = list[i]
        }
        return array
    }

    @JvmStatic
    fun convertToIntArray(set: Set<Int>): IntArray {
        return convertToIntArray(ArrayList(set))
    }

    @JvmStatic
    fun convertToLongArray(intArray: IntArray?): LongArray? {
        if (intArray == null) return null
        val array = LongArray(intArray.size)
        for (i in intArray.indices) {
            array[i] = intArray[i].toLong()
        }
        return array
    }

    @JvmStatic
    fun <T> toCharSequence(list: ArrayList<T>): ArrayList<CharSequence> {
        val charSequenceList = ArrayList<CharSequence>(list.size)
        try {
            for (item in list) charSequenceList.add(item as CharSequence)
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(e)
        }
        return charSequenceList
    }

    @Suppress("UNCHECKED_CAST")
    @CheckResult
    @JvmStatic
    fun <T> concatElements(kind: Class<T>, a: Array<T>?, b: Array<T>?): Array<T> {
        val an = a?.size ?: 0
        val bn = b?.size ?: 0
        if (an == 0 && bn == 0) {
            if (kind == String::class.java) {
                return EmptyArray.STRING as Array<T>
            } else if (kind == Any::class.java) {
                return EmptyArray.OBJECT as Array<T>
            }
        }
        val res = Array.newInstance(kind, an + bn) as Array<T>
        if (an > 0) System.arraycopy(a, 0, res, 0, an)
        if (bn > 0) System.arraycopy(b, 0, res, an, bn)
        return res
    }

    /**
     * Adds value to given array if not already present, providing set-like
     * behavior.
     */
    @CheckResult
    @JvmStatic
    fun <T> appendElement(kind: Class<T>, array: Array<T>?, element: T): Array<T> {
        return appendElement(kind, array, element, false)
    }

    /**
     * Adds value to given array.
     */
    @Suppress("UNCHECKED_CAST")
    @CheckResult
    @JvmStatic
    fun <T> appendElement(
        kind: Class<T>,
        array: Array<T>?,
        element: T,
        allowDuplicates: Boolean
    ): Array<T> {
        val result: Array<T>
        val end: Int
        if (array != null) {
            if (!allowDuplicates && contains(array, element)) return array
            end = array.size
            result = Array.newInstance(kind, end + 1) as Array<T>
            System.arraycopy(array, 0, result, 0, end)
        } else {
            end = 0
            result = Array.newInstance(kind, 1) as Array<T>
        }
        result[end] = element
        return result
    }

    /**
     * Removes value from given array if present, providing set-like behavior.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> removeElement(kind: Class<T>, array: Array<T>?, element: T): Array<T>? {
        if (array != null) {
            if (!contains(array, element)) return array
            val length = array.size
            for (i in 0 until length) {
                if (Objects.equals(array[i], element)) {
                    if (length == 1) {
                        return null
                    }
                    val result = Array.newInstance(kind, length - 1) as Array<T>
                    System.arraycopy(array, 0, result, 0, i)
                    System.arraycopy(array, i + 1, result, i, length - i - 1)
                    return result
                }
            }
        }
        return array
    }

    /**
     * Adds value to given array.
     */
    @JvmStatic
    fun appendInt(
        cur: IntArray?,
        `val`: Int,
        allowDuplicates: Boolean
    ): IntArray {
        if (cur == null) {
            return intArrayOf(`val`)
        }
        val N = cur.size
        if (!allowDuplicates) {
            for (value in cur) {
                if (value == `val`) {
                    return cur
                }
            }
        }
        val ret = IntArray(N + 1)
        System.arraycopy(cur, 0, ret, 0, N)
        ret[N] = `val`
        return ret
    }

    /**
     * Adds value to given array if not already present, providing set-like
     * behavior.
     */
    @JvmStatic
    fun appendInt(cur: IntArray?, `val`: Int): IntArray {
        return appendInt(cur, `val`, false)
    }

    /**
     * Removes value from given array if present, providing set-like behavior.
     */
    @JvmStatic
    fun removeInt(cur: IntArray?, `val`: Int): IntArray? {
        if (cur == null) {
            return null
        }
        val N = cur.size
        for (i in 0 until N) {
            if (cur[i] == `val`) {
                val ret = IntArray(N - 1)
                if (i > 0) {
                    System.arraycopy(cur, 0, ret, 0, i)
                }
                if (i < (N - 1)) {
                    System.arraycopy(cur, i + 1, ret, i, N - i - 1)
                }
                return ret
            }
        }
        return cur
    }

    /**
     * Removes value from given array if present, providing set-like behavior.
     */
    @JvmStatic
    fun removeString(cur: Array<String>?, `val`: String?): Array<String>? {
        if (cur == null) {
            return null
        }
        val N = cur.size
        for (i in 0 until N) {
            if (Objects.equals(cur[i], `val`)) {
                val ret = arrayOfNulls<String>(N - 1)
                if (i > 0) {
                    System.arraycopy(cur, 0, ret, 0, i)
                }
                if (i < (N - 1)) {
                    System.arraycopy(cur, i + 1, ret, i, N - i - 1)
                }
                @Suppress("UNCHECKED_CAST")
                return ret as Array<String>
            }
        }
        return cur
    }

    /**
     * Adds value to given array if not already present, providing set-like
     * behavior.
     */
    @JvmStatic
    fun appendLong(
        cur: LongArray?,
        `val`: Long,
        allowDuplicates: Boolean
    ): LongArray {
        if (cur == null) {
            return longArrayOf(`val`)
        }
        val N = cur.size
        if (!allowDuplicates) {
            for (l in cur) {
                if (l == `val`) {
                    return cur
                }
            }
        }
        val ret = LongArray(N + 1)
        System.arraycopy(cur, 0, ret, 0, N)
        ret[N] = `val`
        return ret
    }

    /**
     * Adds value to given array if not already present, providing set-like
     * behavior.
     */
    @JvmStatic
    fun appendLong(cur: LongArray?, `val`: Long): LongArray {
        return appendLong(cur, `val`, false)
    }

    /**
     * Removes value from given array if present, providing set-like behavior.
     */
    @JvmStatic
    fun removeLong(cur: LongArray?, `val`: Long): LongArray? {
        if (cur == null) {
            return null
        }
        val N = cur.size
        for (i in 0 until N) {
            if (cur[i] == `val`) {
                val ret = LongArray(N - 1)
                if (i > 0) {
                    System.arraycopy(cur, 0, ret, 0, i)
                }
                if (i < (N - 1)) {
                    System.arraycopy(cur, i + 1, ret, i, N - i - 1)
                }
                return ret
            }
        }
        return cur
    }

    @JvmStatic
    fun cloneOrNull(array: LongArray?): LongArray? {
        return array?.clone()
    }

    /**
     * Clones an array or returns null if the array is null.
     */
    @JvmStatic
    fun <T> cloneOrNull(array: Array<T>?): Array<T>? {
        return array?.clone()
    }

    @JvmStatic
    fun <T> cloneOrNull(array: HashSet<T>?): HashSet<T>? {
        return if (array != null) HashSet(array) else null
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @JvmStatic
    fun <T> cloneOrNull(array: ArraySet<T>?): ArraySet<T>? {
        return if (array != null) ArraySet(array) else null
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @JvmStatic
    fun <T> add(cur: ArraySet<T>?, `val`: T): ArraySet<T> {
        val result = cur ?: ArraySet()
        result.add(`val`)
        return result
    }

    @JvmStatic
    fun <T> remove(cur: ArraySet<T>?, `val`: T): ArraySet<T>? {
        if (cur == null) {
            return null
        }
        cur.remove(`val`)
        return if (cur.isEmpty()) {
            null
        } else {
            cur
        }
    }

    @JvmStatic
    fun <T> add(cur: ArrayList<T>?, `val`: T): ArrayList<T> {
        val result = cur ?: ArrayList()
        result.add(`val`)
        return result
    }

    @JvmStatic
    fun <T> remove(cur: ArrayList<T>?, `val`: T): ArrayList<T>? {
        if (cur == null) {
            return null
        }
        cur.remove(`val`)
        return if (cur.isEmpty()) {
            null
        } else {
            cur
        }
    }

    @JvmStatic
    fun <T> contains(cur: Collection<T>?, `val`: T): Boolean {
        return cur != null && cur.contains(`val`)
    }

    @JvmStatic
    fun <T> trimToSize(array: Array<T>?, size: Int): Array<T>? {
        if (array == null || size == 0) {
            return null
        } else if (array.size == size) {
            return array
        } else {
            return array.copyOf(size)
        }
    }

    /**
     * Returns true if the two ArrayLists are equal with respect to the objects they contain.
     * The objects must be in the same order and be reference equal (== not .equals()).
     */
    @JvmStatic
    fun <T> referenceEquals(a: ArrayList<T>?, b: ArrayList<T>?): Boolean {
        if (a === b) {
            return true
        }
        if (a == null || b == null) {
            return false
        }
        val sizeA = a.size
        val sizeB = b.size
        if (sizeA != sizeB) {
            return false
        }
        var diff = false
        for (i in 0 until sizeA) {
            if (diff) break
            diff = diff or (a[i] !== b[i])
        }
        return !diff
    }

    /**
     * Removes elements that match the predicate in an efficient way that alters the order of
     * elements in the collection. This should only be used if order is not important.
     *
     * @param collection The ArrayList from which to remove elements.
     * @param predicate  The predicate that each element is tested against.
     * @return the number of elements removed.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @JvmStatic
    fun <T> unstableRemoveIf(
        collection: ArrayList<T>?,
        predicate: Predicate<T>
    ): Int {
        if (collection == null) {
            return 0
        }

        val size = collection.size
        var leftIdx = 0
        var rightIdx = size - 1
        while (leftIdx <= rightIdx) {
            // Find the next element to remove moving left to right.
            while (leftIdx < size && !predicate.test(collection[leftIdx])) {
                leftIdx++
            }

            // Find the next element to keep moving right to left.
            while (rightIdx > leftIdx && predicate.test(collection[rightIdx])) {
                rightIdx--
            }

            if (leftIdx >= rightIdx) {
                // Done.
                break
            }

            Collections.swap(collection, leftIdx, rightIdx)
            leftIdx++
            rightIdx--
        }

        // leftIdx is now at the end.
        if (size > leftIdx) {
            collection.subList(leftIdx, size).clear()
        }
        return size - leftIdx
    }

    @JvmStatic
    fun defeatNullable(`val`: IntArray?): IntArray {
        return `val` ?: EmptyArray.INT
    }

    @JvmStatic
    fun defeatNullable(`val`: Array<String>?): Array<String> {
        return `val` ?: EmptyArray.STRING
    }

    @JvmStatic
    fun defeatNullable(`val`: Array<File>?): Array<File> {
        return `val` ?: EMPTY_FILE
    }

    @JvmStatic
    fun <T> defeatNullable(clazz: Class<T>, `val`: Array<T>?): Array<T> {
        return `val` ?: emptyArray(clazz)
    }

    /**
     * Throws [ArrayIndexOutOfBoundsException] if the index is out of bounds.
     *
     * @param len   length of the array. Must be non-negative
     * @param index the index to check
     * @throws ArrayIndexOutOfBoundsException if the [index] is out of bounds of the array
     */
    @JvmStatic
    fun checkBounds(len: Int, index: Int) {
        if (index < 0 || len <= index) {
            throw ArrayIndexOutOfBoundsException("length=$len; index=$index")
        }
    }

    /**
     * Returns an array with values from [val] minus `null` values
     *
     * @param arrayConstructor typically `T[]::new` e.g. `String[]::new`
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @JvmStatic
    fun <T> filterNotNull(`val`: Array<T>, arrayConstructor: IntFunction<Array<T>>): Array<T> {
        var nullCount = 0
        val size = size(`val`)
        for (i in 0 until size) {
            if (`val`[i] == null) {
                nullCount++
            }
        }
        if (nullCount == 0) {
            return `val`
        }
        val result = arrayConstructor.apply(size - nullCount)
        var outIdx = 0
        for (i in 0 until size) {
            if (`val`[i] != null) {
                result[outIdx++] = `val`[i]
            }
        }
        return result
    }

    @JvmStatic
    fun startsWith(cur: ByteArray?, `val`: ByteArray?): Boolean {
        if (cur == null || `val` == null) return false
        if (cur.size < `val`.size) return false
        for (i in `val`.indices) {
            if (cur[i] != `val`[i]) return false
        }
        return true
    }

    /**
     * Returns the first element from the array for which
     * condition [predicate] is true, or null if there is no such element
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @JvmStatic
    fun <T> find(items: Array<T>?, predicate: Predicate<T>): T? {
        if (isEmpty(items)) return null
        for (item in items!!) {
            if (predicate.test(item)) return item
        }
        return null
    }

    @JvmStatic
    fun deepToString(value: Any?): String {
        if (value != null && value.javaClass.isArray) {
            return when (value.javaClass) {
                BooleanArray::class.java -> (value as BooleanArray).contentToString()
                ByteArray::class.java -> (value as ByteArray).contentToString()
                CharArray::class.java -> (value as CharArray).contentToString()
                DoubleArray::class.java -> (value as DoubleArray).contentToString()
                FloatArray::class.java -> (value as FloatArray).contentToString()
                IntArray::class.java -> (value as IntArray).contentToString()
                LongArray::class.java -> (value as LongArray).contentToString()
                ShortArray::class.java -> (value as ShortArray).contentToString()
                else -> (value as Array<*>).contentDeepToString()
            }
        } else {
            return value.toString()
        }
    }

    @JvmStatic
    fun <T> firstOrNull(items: Array<T>): T? {
        return if (items.isNotEmpty()) items[0] else null
    }
}
