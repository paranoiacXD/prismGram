package com.prismgram.account

data class AccountInfo(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val username: String?,
    val phoneNumber: String?,
    val bio: String?,
    val photoPath: String?,
) {
    val displayName: String
        get() = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Unknown" }
}
