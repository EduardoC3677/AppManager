// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.Context
import android.content.pm.ComponentInfo
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.LangUtils

class ComponentsOption : FilterOption("components") {
    companion object {
        const val COMPONENT_TYPE_ACTIVITY = 1 shl 0
        const val COMPONENT_TYPE_SERVICE = 1 shl 1
        const val COMPONENT_TYPE_RECEIVER = 1 shl 2
        const val COMPONENT_TYPE_PROVIDER = 1 shl 3
    }

    private val mKeysWithType = linkedMapOf(
        KEY_ALL to TYPE_NONE,
        "with_type" to TYPE_INT_FLAGS,
        "without_type" to TYPE_INT_FLAGS,
        "eq" to TYPE_STR_SINGLE,
        "contains" to TYPE_STR_SINGLE,
        "starts_with" to TYPE_STR_SINGLE,
        "ends_with" to TYPE_STR_SINGLE,
        "regex" to TYPE_REGEX,
        "count_eq" to TYPE_INT,
        "count_le" to TYPE_INT,
        "count_ge" to TYPE_INT
    )

    private val mComponentTypeFlags = linkedMapOf<Int, CharSequence>(
        COMPONENT_TYPE_ACTIVITY to "Activities",
        COMPONENT_TYPE_SERVICE to "Services",
        COMPONENT_TYPE_RECEIVER to "Receivers",
        COMPONENT_TYPE_PROVIDER to "Providers"\n)

    override fun getKeysWithType(): Map<String, Int> = mKeysWithType

    override fun getFlags(key: String): Map<Int, CharSequence> {
        return if (key == "with_type" || key == "without_type") {
            mComponentTypeFlags
        } else {
            super.getFlags(key)
        }
    }

    override fun test(info: IFilterableAppInfo, result: TestResult): TestResult {
        val components = result.getMatchedComponents() ?: info.allComponents
        return when (key) {
            KEY_ALL -> result.setMatched(true).setMatchedComponents(components)
            "with_type" -> {
                val filteredComponents = mutableMapOf<ComponentInfo, Int>()
                for ((component, type) in components) {
                    if ((intValue and type) != 0) {
                        filteredComponents[component] = type
                    }
                }
                result.setMatched(filteredComponents.isNotEmpty()).setMatchedComponents(filteredComponents)
            }
            "without_type" -> {
                val filteredComponents = mutableMapOf<ComponentInfo, Int>()
                for ((component, type) in components) {
                    if ((intValue and type) == 0) {
                        filteredComponents[component] = type
                    }
                }
                result.setMatched(filteredComponents.size == components.size).setMatchedComponents(filteredComponents)
            }
            "eq" -> {
                val filteredComponents = mutableMapOf<ComponentInfo, Int>()
                for ((component, type) in components) {
                    if (component.name == value) {
                        filteredComponents[component] = type
                    }
                }
                result.setMatched(filteredComponents.isNotEmpty()).setMatchedComponents(filteredComponents)
            }
            "contains" -> {
                val filteredComponents = mutableMapOf<ComponentInfo, Int>()
                for ((component, type) in components) {
                    if (component.name.contains(value!!)) {
                        filteredComponents[component] = type
                    }
                }
                result.setMatched(filteredComponents.isNotEmpty()).setMatchedComponents(filteredComponents)
            }
            "starts_with" -> {
                val filteredComponents = mutableMapOf<ComponentInfo, Int>()
                for ((component, type) in components) {
                    if (component.name.startsWith(value!!)) {
                        filteredComponents[component] = type
                    }
                }
                result.setMatched(filteredComponents.isNotEmpty()).setMatchedComponents(filteredComponents)
            }
            "ends_with" -> {
                val filteredComponents = mutableMapOf<ComponentInfo, Int>()
                for ((component, type) in components) {
                    if (component.name.endsWith(value!!)) {
                        filteredComponents[component] = type
                    }
                }
                result.setMatched(filteredComponents.isNotEmpty()).setMatchedComponents(filteredComponents)
            }
            "regex" -> {
                val filteredComponents = mutableMapOf<ComponentInfo, Int>()
                for ((component, type) in components) {
                    if (regexValue!!.matcher(component.name).matches()) {
                        filteredComponents[component] = type
                    }
                }
                result.setMatched(filteredComponents.isNotEmpty()).setMatchedComponents(filteredComponents)
            }
            "count_eq" -> result.setMatched(components.size == intValue).setMatchedComponents(components)
            "count_le" -> result.setMatched(components.size <= intValue).setMatchedComponents(components)
            "count_ge" -> result.setMatched(components.size >= intValue).setMatchedComponents(components)
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val sb = SpannableStringBuilder("App components")
        return when (key) {
            KEY_ALL -> sb.append(LangUtils.getSeparatorString()).append("any")
            "with_type" -> sb.append(" with types ").append(flagsToString("with_type", intValue))
            "without_type" -> sb.append(" without types ").append(flagsToString("without_type", intValue))
            "eq" -> sb.append(" = '").append(value).append("'")
            "contains" -> sb.append(" contains '").append(value).append("'")
            "starts_with" -> sb.append(" starts with '").append(value).append("'")
            "ends_with" -> sb.append(" ends with '").append(value).append("'")
            "regex" -> sb.append(" matches '").append(value).append("'")
            "count_eq" -> sb.append(" count = ").append(intValue.toString())
            "count_le" -> sb.append(" count ≤ ").append(intValue.toString())
            "count_ge" -> sb.append(" count ≥ ").append(intValue.toString())
            else -> throw UnsupportedOperationException("Invalid key $key")
        }
    }
}
