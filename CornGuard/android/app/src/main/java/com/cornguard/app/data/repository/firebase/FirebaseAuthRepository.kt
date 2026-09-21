package com.cornguard.app.data.repository.firebase

import com.cornguard.app.data.model.AuthResult
import com.cornguard.app.data.model.AuthUser
import com.cornguard.app.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val auth: FirebaseAuth
) : AuthRepository {

    override suspend fun registerWithEmail(email: String, password: String): AuthResult {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.toAuthResult()
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.toAuthResult()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    override fun getCurrentUser(): AuthUser? = auth.currentUser?.toAuthUser()

    override fun observeAuthState(): Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toAuthUser())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private fun com.google.firebase.auth.AuthResult.toAuthResult(): AuthResult {
        val firebaseUser = requireNotNull(user) {
            "Firebase returned a null user on a successful auth call — should be unreachable."
        }
        return AuthResult(
            user = firebaseUser.toAuthUser(),
            isNewUser = additionalUserInfo?.isNewUser ?: false
        )
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        phoneNumber = phoneNumber
    )
}
