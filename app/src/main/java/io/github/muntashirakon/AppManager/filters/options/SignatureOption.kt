// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class SignatureOption : FilterOption("signature") {
    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "no_signer" to TYPE_NONE,
        "with_lineage" to TYPE_NONE,
        "with_source_stamp" to TYPE_NONE,
        "without_lineage" to TYPE_NONE,
        "without_source_stamp" to TYPE_NONE,
        "sub_eq" to TYPE_STR_SINGLE,
        "sub_contains" to TYPE_STR_SINGLE,
        "sub_starts_with" to TYPE_STR_SINGLE,
        "sub_ends_with" to TYPE_STR_SINGLE,
        "sub_regex" to TYPE_REGEX,
        "sha256" to TYPE_STR_SINGLE
    )

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val signerInfo = info.fetchSignerInfo()
        if (signerInfo?.currentSignerCerts == null) {
            // No signer
            return result.setMatched(key == "no_signer").setMatchedSubjectLines(emptyList())
        }
        val subjectLines = result.getMatchedSubjectLines() ?: info.signatureSubjectLines.toList()
        return when (key) {
            KEY_ALL -> result.setMatched(true).setMatchedSubjectLines(subjectLines)
            "no_signer" ->                 // Signer exists at this point
                result.setMatched(false).setMatchedSubjectLines(subjectLines)
            "with_source_stamp" -> result.setMatched(signerInfo.sourceStampCert != null).setMatchedSubjectLines(subjectLines)
            "with_lineage" -> result.setMatched(signerInfo.signerCertsInLineage != null).setMatchedSubjectLines(subjectLines)
            "without_source_stamp" -> result.setMatched(signerInfo.sourceStampCert == null).setMatchedSubjectLines(subjectLines)
            "without_lineage" -> result.setMatched(signerInfo.signerCertsInLineage == null).setMatchedSubjectLines(subjectLines)
            "sub_eq" -> {
                val matchedSubjectLines = subjectLines.filter { it == value }
                result.setMatched(matchedSubjectLines.isNotEmpty())
                    .setMatchedSubjectLines(matchedSubjectLines)
            }
            "sub_contains" -> {
                val matchedSubjectLines = subjectLines.filter { it.contains(value!!) }
                result.setMatched(matchedSubjectLines.isNotEmpty())
                    .setMatchedSubjectLines(matchedSubjectLines)
            }
            "sub_starts_with" -> {
                val matchedSubjectLines = subjectLines.filter { it.startsWith(value!!) }
                result.setMatched(matchedSubjectLines.isNotEmpty())
                    .setMatchedSubjectLines(matchedSubjectLines)
            }
            "sub_ends_with" -> {
                val matchedSubjectLines = subjectLines.filter { it.endsWith(value!!) }
                result.setMatched(matchedSubjectLines.isNotEmpty())
                    .setMatchedSubjectLines(matchedSubjectLines)
            }
            "sub_regex" -> {
                val matchedSubjectLines = subjectLines.filter { regexValue!!.matcher(it).matches() }
                result.setMatched(matchedSubjectLines.isNotEmpty())
                    .setMatchedSubjectLines(matchedSubjectLines)
            }
            "sha256" -> {
                val sha256sums = info.signatureSha256Checksums
                for (i in sha256sums.indices) {
                    if (sha256sums[i] == value) {
                        return result.setMatched(true)
                            .setMatchedSubjectLines(listOf(info.signatureSubjectLines[i]))
                    }
                }
                result.setMatched(false).setMatchedSubjectLines(emptyList())
            }
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("Signatures")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "no_signer" -> sb.append(LangUtils.getSeparatorString()).append("none")
            "with_lineage" -> sb.append(" with lineages")
            "without_lineage" -> sb.append(" without lineages")
            "with_source_stamp" -> sb.append(" with source stamps")
            "without_source_stamp" -> sb.append(" without source stamps")
            "sub_eq" -> sb.append("' subject = '").append(value).append("'")
            "sub_contains" -> sb.append("' subject contains '").append(value).append("'")
            "sub_starts_with" -> sb.append("' subject starts with '").append(value).append("'")
            "sub_ends_with" -> sb.append("' subject ends with '").append(value).append("'")
            "sub_regex" -> sb.append("' subject matches '").append(value).append("'")
            "sha256" -> sb.append("' SHA-256 = '").append(value).append("'")
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
