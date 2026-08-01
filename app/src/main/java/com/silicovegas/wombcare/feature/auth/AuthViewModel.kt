package com.silicovegas.wombcare.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silicovegas.wombcare.core.data.AuthRepository
import com.silicovegas.wombcare.core.data.model.ConsentType
import com.silicovegas.wombcare.core.data.model.DoctorInfo
import com.silicovegas.wombcare.core.data.model.UserRole
import com.silicovegas.wombcare.core.legal.LegalDocs
import com.silicovegas.wombcare.core.util.InputValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthFormState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullName: String = "",
    val registrationNo: String = "",
    val clinic: String = "",
    // consent gate
    val acceptedTerms: Boolean = false,
    val acceptedPrivacy: Boolean = false,
    val acceptedDisclaimer: Boolean = false,
    // per-field errors, shown only after a submit attempt
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    val nameError: String? = null,
    val registrationError: String? = null,
    val consentError: String? = null,
    // async
    val submitting: Boolean = false,
    val formError: String? = null,
    val success: Boolean = false,
) {
    /** Continue on the consent gate is live only when all three boxes are ticked. */
    val allConsentsGiven: Boolean get() = acceptedTerms && acceptedPrivacy && acceptedDisclaimer
}

/**
 * Drives login, signup, and password reset. Validation is client-side and instant; the
 * repository owns the atomic profile+consent write and the rollback described in
 * [AuthRepository.signUp]. `clock` is injected so the accepted-at timestamps are testable.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    // field setters ---------------------------------------------------------------------
    fun onEmail(v: String) = _form.update { it.copy(email = v, emailError = null, formError = null) }
    fun onPassword(v: String) = _form.update { it.copy(password = v, passwordError = null) }
    fun onConfirm(v: String) = _form.update { it.copy(confirmPassword = v, confirmError = null) }
    fun onName(v: String) = _form.update { it.copy(fullName = v, nameError = null) }
    fun onRegistration(v: String) = _form.update { it.copy(registrationNo = v, registrationError = null) }
    fun onClinic(v: String) = _form.update { it.copy(clinic = v) }
    fun onTerms(v: Boolean) = _form.update { it.copy(acceptedTerms = v, consentError = null) }
    fun onPrivacy(v: Boolean) = _form.update { it.copy(acceptedPrivacy = v, consentError = null) }
    fun onDisclaimer(v: Boolean) = _form.update { it.copy(acceptedDisclaimer = v, consentError = null) }

    fun reset() { _form.value = AuthFormState() }

    // login ------------------------------------------------------------------------------
    fun login(nowMillis: Long) {
        val s = _form.value
        val emailErr = InputValidation.emailError(s.email)
        val passErr = if (s.password.isBlank()) "Enter your password" else null
        if (emailErr != null || passErr != null) {
            _form.update { it.copy(emailError = emailErr, passwordError = passErr) }
            return
        }
        _form.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            authRepository.signIn(s.email, s.password)
                .onSuccess { _form.update { f -> f.copy(submitting = false, success = true) } }
                .onFailure { e ->
                    _form.update { f -> f.copy(submitting = false, formError = friendly(e)) }
                }
        }
    }

    // signup -----------------------------------------------------------------------------
    fun signUp(role: UserRole, nowMillis: Long) {
        val s = _form.value
        val emailErr = InputValidation.emailError(s.email)
        val passErr = InputValidation.passwordError(s.password)
        val confErr = InputValidation.confirmPasswordError(s.password, s.confirmPassword)
        val nameErr = InputValidation.nameError(s.fullName)
        val regErr = if (role == UserRole.DOCTOR) InputValidation.registrationError(s.registrationNo) else null
        val consentErr = if (!s.allConsentsGiven) "Please accept all three to continue" else null

        if (listOf(emailErr, passErr, confErr, nameErr, regErr, consentErr).any { it != null }) {
            _form.update {
                it.copy(
                    emailError = emailErr, passwordError = passErr, confirmError = confErr,
                    nameError = nameErr, registrationError = regErr, consentError = consentErr,
                )
            }
            return
        }

        val consents = listOf(
            ConsentType.TERMS to LegalDocs.versionFor(ConsentType.TERMS),
            ConsentType.PRIVACY to LegalDocs.versionFor(ConsentType.PRIVACY),
            ConsentType.NOT_A_MEDICAL_DEVICE to LegalDocs.versionFor(ConsentType.NOT_A_MEDICAL_DEVICE),
        )
        val doctorInfo = if (role == UserRole.DOCTOR) {
            DoctorInfo(registrationNo = s.registrationNo, clinic = s.clinic)
        } else null

        _form.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            authRepository.signUp(
                email = s.email, password = s.password, fullName = s.fullName,
                role = role, acceptedConsents = consents, nowMillis = nowMillis,
                doctorInfo = doctorInfo,
            ).onSuccess { _form.update { f -> f.copy(submitting = false, success = true) } }
                .onFailure { e ->
                    _form.update { f -> f.copy(submitting = false, formError = friendly(e)) }
                }
        }
    }

    // password reset ---------------------------------------------------------------------
    fun sendReset() {
        val s = _form.value
        val emailErr = InputValidation.emailError(s.email)
        if (emailErr != null) { _form.update { it.copy(emailError = emailErr) }; return }
        _form.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            authRepository.sendPasswordReset(s.email)
                .onSuccess { _form.update { f -> f.copy(submitting = false, success = true) } }
                .onFailure { e -> _form.update { f -> f.copy(submitting = false, formError = friendly(e)) } }
        }
    }

    /** Turn a raw Firebase exception into something a mother should read. */
    private fun friendly(e: Throwable): String {
        val msg = e.message ?: return "Something went wrong. Please try again."
        return when {
            msg.contains("password is invalid", true) ||
                msg.contains("no user record", true) ||
                msg.contains("credential is incorrect", true) -> "Email or password is incorrect."
            msg.contains("email address is already in use", true) -> "That email already has an account."
            msg.contains("network error", true) -> "No connection. Check your internet and try again."
            msg.contains("blocked all requests", true) -> "Too many attempts. Please wait and try again."
            else -> "Something went wrong. Please try again."
        }
    }
}
