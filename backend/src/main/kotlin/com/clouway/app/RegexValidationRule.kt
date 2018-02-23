package com.clouway.app

import com.clouway.app.core.Error
import com.clouway.app.core.ValidationRule

class RegexValidationRule(val param: String, val expression: String, val errorMessage: String) : ValidationRule {
    override fun validate(params: Map<String, String>): Error? {
        val value = params[param]
        if (value != null && !Regex(expression).matches(value)) {
            return Error(errorMessage)
        }
        return null
    }
}