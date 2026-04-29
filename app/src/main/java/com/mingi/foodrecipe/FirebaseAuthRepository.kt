package com.mingi.foodrecipe

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseAuthRepository(private val context: Context) {

    private val apiKey = BuildConfig.FIREBASE_API_KEY.trim()
    private val appId = BuildConfig.FIREBASE_APP_ID.trim()
    private val projectId = BuildConfig.FIREBASE_PROJECT_ID.trim()

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && appId.isNotBlank() && projectId.isNotBlank()

    fun initialize(): AuthUiState {
        if (!isConfigured) {
            return AuthUiState.ConfigMissing(
                "Firebase 설정이 없습니다. local.properties에 FIREBASE_API_KEY, FIREBASE_APP_ID, FIREBASE_PROJECT_ID를 추가하세요."
            )
        }

        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(appId)
                .setProjectId(projectId)
                .build()
            FirebaseApp.initializeApp(context, options)
        }

        return currentAuthState()
    }

    fun currentAuthState(): AuthUiState {
        if (!isConfigured) return initialize()
        val user = FirebaseAuth.getInstance().currentUser
        return if (user == null) {
            AuthUiState.SignedOut
        } else {
            AuthUiState.SignedIn(user.email ?: "회원")
        }
    }

    suspend fun signIn(email: String, password: String): AuthUiState {
        validateInput(email, password)
        if (!isConfigured) return initialize()
        FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email, password)
            .await()
        return currentAuthState()
    }

    suspend fun signUp(email: String, password: String): AuthUiState {
        validateInput(email, password)
        if (!isConfigured) return initialize()
        FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(email, password)
            .await()
        return currentAuthState()
    }

    fun signOut(): AuthUiState {
        if (!isConfigured) return initialize()
        FirebaseAuth.getInstance().signOut()
        return AuthUiState.SignedOut
    }

    private fun validateInput(email: String, password: String) {
        require(email.isNotBlank()) { "이메일을 입력하세요." }
        require(password.length >= 6) { "비밀번호는 6자 이상이어야 합니다." }
    }

    private suspend fun Task<AuthResult>.await(): AuthResult {
        return suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                continuation.resume(result)
            }
            addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
        }
    }
}
