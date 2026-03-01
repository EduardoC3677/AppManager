// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.misc

// Keep this in sync with https://cs.android.com/android/platform/superproject/+/master:libcore/libart/src/main/java/dalvik/system/VMRuntime.java
object VMRuntime {
    const val ABI_ARMEABI = "armeabi"
    const val ABI_ARMEABI_V7A = "armeabi-v7a"
    const val ABI_MIPS = "mips"
    const val ABI_MIPS64 = "mips64"
    const val ABI_X86 = "x86"
    const val ABI_X86_64 = "x86_64"
    const val ABI_ARM64_V8A = "arm64-v8a"
    const val ABI_ARM64_V8A_HWASAN = "arm64-v8a-hwasan"

    const val INSTRUCTION_SET_ARM = "arm"
    const val INSTRUCTION_SET_MIPS = "mips"
    const val INSTRUCTION_SET_MIPS64 = "mips64"
    const val INSTRUCTION_SET_X86 = "x86"
    const val INSTRUCTION_SET_X86_64 = "x86_64"
    const val INSTRUCTION_SET_ARM64 = "arm64"

    private val ABI_TO_INSTRUCTION_SET_MAP = mapOf(
        ABI_ARMEABI to INSTRUCTION_SET_ARM,
        ABI_ARMEABI_V7A to INSTRUCTION_SET_ARM,
        ABI_MIPS to INSTRUCTION_SET_MIPS,
        ABI_MIPS64 to INSTRUCTION_SET_MIPS64,
        ABI_X86 to INSTRUCTION_SET_X86,
        ABI_X86_64 to INSTRUCTION_SET_X86_64,
        ABI_ARM64_V8A to INSTRUCTION_SET_ARM64,
        ABI_ARM64_V8A_HWASAN to INSTRUCTION_SET_ARM64
    )

    @JvmStatic
    fun getInstructionSet(abi: String): String {
        return ABI_TO_INSTRUCTION_SET_MAP[abi] ?: throw IllegalArgumentException("Unsupported ABI: $abi")
    }

    @JvmStatic
    fun is64BitInstructionSet(instructionSet: String): Boolean {
        return INSTRUCTION_SET_ARM64 == instructionSet ||
                INSTRUCTION_SET_X86_64 == instructionSet ||
                INSTRUCTION_SET_MIPS64 == instructionSet
    }

    @JvmStatic
    fun is64BitAbi(abi: String): Boolean {
        return is64BitInstructionSet(getInstructionSet(abi))
    }
}
