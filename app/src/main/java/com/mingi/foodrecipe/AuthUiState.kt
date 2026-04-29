package com.mingi.foodrecipe

sealed class AuthUiState {
    object Loading : AuthUiState()
    object SignedOut : AuthUiState()
    data class SignedIn(val email: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    data class ConfigMissing(val message: String) : AuthUiState()
}
