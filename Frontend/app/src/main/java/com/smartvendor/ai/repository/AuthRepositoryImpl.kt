package com.smartvendor.ai.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.smartvendor.ai.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepository {

    override fun getCurrentUser(): Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                val displayName = firebaseUser.displayName.takeUnless { it.isNull_or_blank() }
                    ?: firebaseUser.email?.substringBefore("@")
                    ?: "Shopkeeper"
                trySend(
                    User(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        name = displayName
                    )
                )
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun login(email: String, pass: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, pass).await()
            val firebaseUser = authResult.user ?: throw Exception("Authentication returned null user")
            val displayName = firebaseUser.displayName.takeUnless { it.isNull_or_blank() }
                ?: email.substringBefore("@")
            val user = User(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: email,
                name = displayName
            )
            Result.success(user)
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    override suspend fun register(email: String, pass: String, name: String): Result<User> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
            val firebaseUser = authResult.user ?: throw Exception("Registration returned null user")
            
            val finalName = name.trim().ifBlank { email.substringBefore("@") }
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(finalName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            val user = User(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: email,
                name = finalName
            )
            Result.success(user)
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    override suspend fun logout() {
        auth.signOut()
    }

    override fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
