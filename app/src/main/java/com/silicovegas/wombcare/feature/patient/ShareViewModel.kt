package com.silicovegas.wombcare.feature.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silicovegas.wombcare.core.data.AuthRepository
import com.silicovegas.wombcare.core.data.CareLinkRepository
import com.silicovegas.wombcare.core.data.PatientSyncRepository
import com.silicovegas.wombcare.core.data.SharingPreference
import com.silicovegas.wombcare.core.data.model.CareLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareUiState(
    val shareCode: String = "",
    val codeLoading: Boolean = true,
    val codeError: Boolean = false,
    val sharingEnabled: Boolean = false,
    val pending: List<CareLink> = emptyList(),
    val activeDoctors: List<CareLink> = emptyList(),
)

/**
 * Backs the patient's Share & care team screen.
 *
 * IMPORTANT: every state mutation goes through [MutableStateFlow.update] (an atomic
 * read-modify-write), NOT `_ui.value = _ui.value.copy(...)`. Three coroutines write this
 * state concurrently — the code generator and the two link listeners — and a plain copy
 * loses updates: a listener firing between the generator's read and write would clobber the
 * freshly generated share code back to blank. That race is exactly why the code sometimes
 * didn't appear.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val careLinks: CareLinkRepository,
    private val sync: PatientSyncRepository,
    private val sharingPref: SharingPreference,
) : ViewModel() {

    private val _ui = MutableStateFlow(ShareUiState(sharingEnabled = sharingPref.isSharingEnabled()))
    val ui: StateFlow<ShareUiState> = _ui.asStateFlow()

    private var uid: String? = null
    private var listenersStarted = false

    init { loadCode() }

    /** Generate (or fetch) the share code, then start the link listeners. Retryable. */
    fun loadCode() {
        _ui.update { it.copy(codeLoading = true, codeError = false) }
        viewModelScope.launch {
            val id = auth.currentUid
            if (id == null) {
                _ui.update { it.copy(codeLoading = false, codeError = true) }
                return@launch
            }
            uid = id

            runCatching { careLinks.ensureShareCode(id) }
                .onSuccess { code ->
                    _ui.update {
                        it.copy(
                            shareCode = code,
                            codeLoading = false,
                            codeError = code.isBlank(),
                        )
                    }
                }
                .onFailure {
                    _ui.update { it.copy(codeLoading = false, codeError = true) }
                }

            startListeners(id)
        }
    }

    private fun startListeners(id: String) {
        if (listenersStarted) return
        listenersStarted = true

        careLinks.pendingRequestsForPatient(id)
            .catch { /* revoked/offline: keep last list */ }
            .onEach { list -> _ui.update { it.copy(pending = list) } }
            .launchIn(viewModelScope)

        careLinks.activeDoctorsForPatient(id)
            .catch { }
            .onEach { list -> _ui.update { it.copy(activeDoctors = list) } }
            .launchIn(viewModelScope)
    }

    fun setSharing(enabled: Boolean) {
        sharingPref.setSharingEnabled(enabled)
        _ui.update { it.copy(sharingEnabled = enabled) }
        val id = uid ?: return
        viewModelScope.launch {
            runCatching {
                sync.setSharingEnabled(id, enabled)
                if (enabled) {
                    val profile = auth.loadProfile(id)
                    sync.publishProfile(id, profile?.fullName ?: "Patient", pregnancyWeeks = null)
                }
            }
        }
    }

    fun approve(doctorUid: String) = act { id -> careLinks.approve(id, doctorUid, now()) }
    fun deny(doctorUid: String) = act { id -> careLinks.revoke(id, doctorUid, now()) }
    fun revoke(doctorUid: String) = act { id -> careLinks.revoke(id, doctorUid, now()) }

    fun rotateCode() {
        val id = uid ?: return
        _ui.update { it.copy(codeLoading = true, codeError = false) }
        viewModelScope.launch {
            runCatching { careLinks.claimShareCodeFor(id) }
                .onSuccess { code -> _ui.update { it.copy(shareCode = code, codeLoading = false) } }
                .onFailure { _ui.update { it.copy(codeLoading = false, codeError = true) } }
        }
    }

    private fun act(block: suspend (String) -> Unit) {
        val id = uid ?: return
        viewModelScope.launch { runCatching { block(id) } }
    }

    private fun now() = System.currentTimeMillis()
}
