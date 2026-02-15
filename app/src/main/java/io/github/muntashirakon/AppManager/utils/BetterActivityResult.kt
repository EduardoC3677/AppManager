// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package io.github.muntashirakon.AppManager.utils

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

class BetterActivityResult<Input, Result> private constructor(
    caller: ActivityResultCaller,
    contract: ActivityResultContract<Input, Result>,
    onActivityResult: OnActivityResult<Result>?
) {
    /**
     * Callback interface
     */
    fun interface OnActivityResult<O> {
        /**
         * Called after receiving a result from the target activity
         */
        fun onActivityResult(result: O)
    }

    private val mLauncher: ActivityResultLauncher<Input>
    private var mOnActivityResult: OnActivityResult<Result>? = null

    init {
        mOnActivityResult = onActivityResult
        mLauncher = caller.registerForActivityResult(contract, ::callOnActivityResult)
    }

    fun setOnActivityResult(onActivityResult: OnActivityResult<Result>?) {
        mOnActivityResult = onActivityResult
    }

    /**
     * Launch activity, same as [ActivityResultLauncher.launch] except that it allows a callback
     * executed after receiving a result from the target activity.
     */
    fun launch(input: Input, onActivityResult: OnActivityResult<Result>? = null) {
        if (onActivityResult != null) {
            mOnActivityResult = onActivityResult
        }
        mLauncher.launch(input)
    }

    private fun callOnActivityResult(result: Result) {
        mOnActivityResult?.onActivityResult(result)
    }

    companion object {
        /**
         * Register activity result using a [ActivityResultContract] and an in-place activity result callback like
         * the default approach. You can still customise callback using [launch].
         */
        @JvmStatic
        @JvmOverloads
        fun <Input, Result> registerForActivityResult(
            caller: ActivityResultCaller,
            contract: ActivityResultContract<Input, Result>,
            onActivityResult: OnActivityResult<Result>? = null
        ): BetterActivityResult<Input, Result> {
            return BetterActivityResult(caller, contract, onActivityResult)
        }

        /**
         * Specialised method for launching new activities.
         */
        @JvmStatic
        fun registerActivityForResult(
            caller: ActivityResultCaller
        ): BetterActivityResult<Intent, ActivityResult> {
            return registerForActivityResult(caller, ActivityResultContracts.StartActivityForResult())
        }
    }
}
