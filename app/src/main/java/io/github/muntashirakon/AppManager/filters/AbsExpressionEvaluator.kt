// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters

abstract class AbsExpressionEvaluator {
    var lastError: CharSequence? = null
        protected set

    protected abstract fun evalId(id: String): Boolean

    fun evaluate(expr: String): Boolean {
        var currentExpr = expr
        lastError = null
        // Process parentheses first
        while (currentExpr.contains("(")) {
            val start = currentExpr.lastIndexOf('(')
            val end = currentExpr.indexOf(')', start)
            if (end == -1) {
                lastError = "Expected ')'."
                return false
            }
            // Get expression without parenthesis
            val subExpr = currentExpr.substring(start + 1, end)
            val subResult = evalOrExpr(subExpr)
            currentExpr = currentExpr.substring(0, start) + subResult + currentExpr.substring(end + 1)
        }
        // Evaluate the final expression without parentheses
        return evalOrExpr(currentExpr)
    }

    private fun evalOrExpr(expr: String): Boolean {
        val orParts = expr.split(" \| ".toRegex()).toTypedArray()
        for (part in orParts) {
            if (evalAndExpr(part)) {
                return true
            }
        }
        return false
    }

    private fun evalAndExpr(expr: String): Boolean {
        val andParts = expr.split(" & ".toRegex()).toTypedArray()
        for (andPart in andParts) {
            val trimmedPart = andPart.trim()
            if (trimmedPart == "true") continue
            if (trimmedPart == "false" || !evalId(trimmedPart)) {
                return false
            }
        }
        return true
    }
}
