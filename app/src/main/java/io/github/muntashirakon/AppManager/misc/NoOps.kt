// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import io.github.muntashirakon.AppManager.settings.Ops
import java.lang.annotation.Documented
import java.lang.annotation.ElementType.*
import java.lang.annotation.RetentionPolicy.SOURCE

/**
 * Denotes that the method, constructor or class does not contain any checks from [Ops]. This is useful to prevent
 * cycles when checking for root, ADB, etc.
 *
 * TODO: Build a annotation detector
 */
@Documented
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.CLASS)
annotation class NoOps(
    /**
     * Whether any [Ops] checks have been used.
     */
    val used: Boolean = false
)
