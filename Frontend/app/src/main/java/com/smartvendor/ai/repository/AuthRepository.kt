package com.smartvendor.ai.repository

import com.smartvendor.ai.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getCurrentUser(): Flow<User?>
    suspend fun login(email: String, pass: String): Result<User>
    suspend fun register(email: String, pass: String, name: String = "Shopkeeper"): Result<User>
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun logout()
    fun isUserLoggedIn(): Boolean
}
