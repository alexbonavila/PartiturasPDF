package com.abdev.partituraspdf.storage.api

/** Private transaction identity, not registration, permission or journal existence. */
@JvmInline value class TransactionId(val value: String) {
    init { requireCanonicalUuid(value) }
}
