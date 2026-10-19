package com.abdev.partituraspdf.pdf.android.isolated

import com.abdev.partituraspdf.core.result.AppError

internal object PdfRemoteErrors {
    fun encode(error: AppError): String = when(error) {
        is AppError.Access -> "access:${error.name}"
        is AppError.Validation -> "validation:${error.name}"
        is AppError.Unsupported -> "unsupported:${error.name}"
        is AppError.Resource -> "resource:${error.name}"
        is AppError.Conflict -> "conflict:${error.name}"
    }
    fun decode(value: String): AppError {
        val parts = value.split(':'); require(parts.size == 2)
        return when(parts[0]) {
            "access" -> AppError.Access.valueOf(parts[1])
            "validation" -> AppError.Validation.valueOf(parts[1])
            "unsupported" -> AppError.Unsupported.valueOf(parts[1])
            "resource" -> AppError.Resource.valueOf(parts[1])
            "conflict" -> AppError.Conflict.valueOf(parts[1])
            else -> error("Unknown remote error kind")
        }
    }
}
