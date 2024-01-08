package org.niklasunrau.pqcmessenger.presentation.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.niklasunrau.pqcmessenger.R
import org.niklasunrau.pqcmessenger.domain.crypto.AsymmetricPublicKey
import org.niklasunrau.pqcmessenger.domain.crypto.AsymmetricSecretKey
import org.niklasunrau.pqcmessenger.domain.crypto.aes.AES
import org.niklasunrau.pqcmessenger.domain.model.User
import org.niklasunrau.pqcmessenger.domain.repository.AuthRepository
import org.niklasunrau.pqcmessenger.domain.repository.UserRepository
import org.niklasunrau.pqcmessenger.domain.util.Algorithm
import org.niklasunrau.pqcmessenger.domain.util.Json.json
import org.niklasunrau.pqcmessenger.domain.util.Status
import org.niklasunrau.pqcmessenger.presentation.util.UiText
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUIState())
    val uiState = _uiState.asStateFlow()

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, usernameError = UiText.DynamicString("")) }

    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, emailError = UiText.DynamicString("")) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, passwordError = UiText.DynamicString("")) }
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update {
            it.copy(
                confirmPassword = confirmPassword, confirmPasswordError = UiText.DynamicString("")
            )
        }
    }

    private fun isTextFieldsValid(type: String): Boolean {
        val usernameValid = _uiState.value.username.isNotBlank()
        val emailValid = _uiState.value.email.isNotBlank()
        val passwordValid = _uiState.value.password.isNotBlank()
        val confirmPasswordValid = _uiState.value.confirmPassword.isNotBlank()
        _uiState.update { currentState ->
            currentState.copy(
                usernameError = if (usernameValid) currentState.usernameError else UiText.StringResource(
                    R.string.cannot_be_empty
                ),
                emailError = if (emailValid) currentState.emailError else UiText.StringResource(
                    R.string.cannot_be_empty
                ),
                passwordError = if (passwordValid) currentState.passwordError else UiText.StringResource(
                    R.string.cannot_be_empty
                ),
                confirmPasswordError = if (confirmPasswordValid) currentState.confirmPasswordError else UiText.StringResource(
                    R.string.cannot_be_empty
                )
            )
        }

        return if (type == "login") (usernameValid && passwordValid) else (usernameValid && emailValid && passwordValid && confirmPasswordValid)
    }

    fun login(
        onNavigateToHome: (String) -> Unit
    ) {
        val username = uiState.value.username
        val password = uiState.value.password
        _uiState.update { it.copy(isLoading = true) }

        if (!isTextFieldsValid("login")) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {

            val user = userRepository.getUserByUsername(username)
            if (user == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        usernameError = UiText.StringResource(R.string.invalid_credentials),
                        passwordError = UiText.StringResource(R.string.invalid_credentials)
                    )
                }
                return@launch
            }

            authRepository.login(user.email, password).collectLatest { result ->
                when (result) {
                    is Status.Loading -> {
                    }

                    is Status.Error -> {
                        when (result.error) {
                            is FirebaseAuthInvalidCredentialsException -> _uiState.update { currentState ->
                                currentState.copy(
                                    isLoading = false,
                                    usernameError = UiText.StringResource(R.string.invalid_credentials),
                                    passwordError = UiText.StringResource(R.string.invalid_credentials)
                                )
                            }
                        }
                    }

                    is Status.Success -> {
                        _uiState.update { it.copy(isLoading = false) }
                        onNavigateToHome(password)
                    }
                }
            }
        }
    }


    fun signup(
        onNavigateToHome: (String) -> Unit
    ) {
        val username = _uiState.value.username
        val email = _uiState.value.email
        val password = _uiState.value.password
        val confirmPassword = _uiState.value.confirmPassword

        _uiState.update { it.copy(isLoading = true) }

        if (!isTextFieldsValid("signup")) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        if (password != confirmPassword) {
            _uiState.update { currentState ->
                currentState.copy(
                    confirmPasswordError = UiText.StringResource(R.string.password_not_identical), isLoading = false
                )
            }
            return
        }

        viewModelScope.launch {
            if (userRepository.isUsernameInUse(username)) {
                _uiState.update { currentState ->
                    currentState.copy(
                        usernameError = UiText.StringResource(R.string.username_in_use), isLoading = false
                    )
                }
                this.cancel()
            }

            authRepository.signup(email, password).collectLatest { result ->
                when (result) {
                    is Status.Loading -> {}
                    is Status.Error -> {
                        _uiState.update { it.copy(isLoading = false) }
                        when (result.error) {
                            is FirebaseAuthWeakPasswordException -> {
                                _uiState.update { currentState ->
                                    currentState.copy(passwordError = UiText.StringResource(R.string.password_not_valid))
                                }
                            }

                            is FirebaseAuthInvalidCredentialsException -> {
                                _uiState.update { currentState ->
                                    currentState.copy(emailError = UiText.StringResource(R.string.email_not_valid))
                                }
                            }

                            is FirebaseAuthUserCollisionException -> {
                                _uiState.update { currentState ->
                                    currentState.copy(emailError = UiText.StringResource(R.string.email_in_use))
                                }
                            }
                        }
                    }


                    is Status.Success -> {
                        val mapEncryptedSKs = mutableMapOf<String, String>()
                        val mapPKs = mutableMapOf<String, String>()
                        for ((name, alg) in Algorithm.map) {
                            val (secretKey, publicKey) = viewModelScope.async {
                                alg.generateKeyPair()
                            }.await()
                            val stringSK = json.encodeToString<AsymmetricSecretKey>(secretKey)
                            val stringPK = json.encodeToString<AsymmetricPublicKey>(publicKey)
                            val encryptedSK = AES.encrypt(stringSK, password)
                            mapEncryptedSKs[name.name] = encryptedSK
                            mapPKs[name.name] = stringPK
                        }

                        userRepository.createUser(
                            User(
                                id = authRepository.currentUserId,
                                email = email,
                                username = username,
                                encryptedSecretKeys = mapEncryptedSKs,
                                publicKeys = mapPKs
                            )
                        )
                        _uiState.update { it.copy(isLoading = false) }
                        onNavigateToHome(password)
                    }

                }
            }

        }
    }
}
