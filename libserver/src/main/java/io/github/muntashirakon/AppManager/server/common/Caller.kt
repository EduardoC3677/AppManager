// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.os.Parcelable

// Copyright 2017 Zheng Li
abstract class Caller : Parcelable {
    protected var mParameterTypes: Array<Class<*>?>? = null
    protected var mParameterTypesAsString: Array<String>? = null
    protected var mParameters: Array<Any?>? = null

    protected fun initParameters(parameterTypes: Array<Class<*>>, parameters: Array<Any?>) {
        setParameterTypes(parameterTypes)
        this.mParameters = parameters
    }

    open fun setParameterTypes(parameterTypes: Array<Class<*>>?) {
        if (parameterTypes != null) {
            mParameterTypesAsString = Array(parameterTypes.size) { i -> parameterTypes[i].name }
        }
    }

    open fun setParameterTypes(parameterTypes: Array<String>?) {
        this.mParameterTypesAsString = parameterTypes
    }

    open fun getParameterTypes(): Array<Class<*>?>? {
        if (mParameterTypesAsString != null) {
            if (mParameterTypes == null) {
                mParameterTypes = ClassUtils.string2Class(*mParameterTypesAsString!!)
            }
            return mParameterTypes
        }
        return null
    }

    open fun getParameters(): Array<Any?>? {
        return mParameters
    }

    open fun wrapParameters(): Caller {
        return ParamsFixer.wrap(this)
    }

    open fun unwrapParameters(): Caller {
        return ParamsFixer.unwrap(this)
    }

    abstract fun getType(): Int

    // Property accessors for Java compatibility (if needed)
    val parameterTypes: Array<Class<*>?>?
        get() = getParameterTypes()

    val parameters: Array<Any?>?
        get() = getParameters()
}
