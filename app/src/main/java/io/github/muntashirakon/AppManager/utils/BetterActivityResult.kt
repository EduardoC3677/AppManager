// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package io.github.muntashirakon.AppManager.utils

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

class BetterActivityResult<I, O> @JvmOverloads constructor(
    caller: ActivityResultCaller,
    contract: ActivityResultContract<I, O>,
    onActivityResult: OnActivityResult<O>? = null
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

    private val mLauncher: ActivityResultLauncher<I>
    private var mOnActivityResult: OnActivityResult<O>? = null

    init {
        mOnActivityResult = onActivityResult
        mLauncher = caller.registerForActivityResult(contract, ::callOnActivityResult)
    }

    fun setOnActivityResult(onActivityResult: OnActivityResult<O>?) {
        mOnActivityResult = onActivityResult
    }

    /**
     * Launch activity, same as [ActivityResultLauncher.launch] except that it allows a callback
     * executed after receiving a result from the target activity.
     */
    fun launch(input: I, onActivityResult: OnActivityResult<O>? = null) {
        if (onActivityResult != null) {
            mOnActivityResult = onActivityResult
        }
        mLauncher.launch(input)
    }

    private fun callOnActivityResult(result: O) {
        mOnActivityResult?.onActivityResult(result)
    }

    companion object {
        /**
         * Register activity result using a [ActivityResultContract] and an in-place activity result callback like
         * the default approach. You can still customise callback using [launch].
         */
        @JvmStatic
        @JvmOverloads
        fun <I, O> registerForActivityResult(
            caller: ActivityResultCaller,
            contract: ActivityResultContract<I, O>,
            onActivityResult: OnActivityResult<O>? = null
        ): BetterActivityResult<I, O> {
            return BetterActivityResult(caller, contract, onActivityResult)
        }

        /**
         * Specialised method for launching new activities.
         */
        @JvmStatic
        fun registerActivityForResult(
            caller: ActivityResultCaller
        ): BetterActivityResult<Intent, ActivityResult> {
            return registerForActivityResult(caller, ActivityResultContracts.StartActivityForResult(), null)
        }
    }
}
