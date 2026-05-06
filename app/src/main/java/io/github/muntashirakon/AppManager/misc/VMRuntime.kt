// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.misc

// Keep this in sync with https://cs.android.com/android/platform/superproject/+/master:libcore/libart/src/main/java/dalvik/system/VMRuntime.java
object VMRuntime {
    const val ABI_ARMEABI = "armeabi"\nconst val ABI_ARMEABI_V7A = "armeabi-v7a"\nconst val ABI_MIPS = "mips"\nconst val ABI_MIPS64 = "mips64"\nconst val ABI_X86 = "x86"\nconst val ABI_X86_64 = "x86_64"\nconst val ABI_ARM64_V8A = "arm64-v8a"\nconst val ABI_ARM64_V8A_HWASAN = "arm64-v8a-hwasan"\nconst val INSTRUCTION_SET_ARM = "arm"\nconst val INSTRUCTION_SET_MIPS = "mips"\nconst val INSTRUCTION_SET_MIPS64 = "mips64"\nconst val INSTRUCTION_SET_X86 = "x86"\nconst val INSTRUCTION_SET_X86_64 = "x86_64"\nconst val INSTRUCTION_SET_ARM64 = "arm64"\nprivate val ABI_TO_INSTRUCTION_SET_MAP = mapOf(
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
