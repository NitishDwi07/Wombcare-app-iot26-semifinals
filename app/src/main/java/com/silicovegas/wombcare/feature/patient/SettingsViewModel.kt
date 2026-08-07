package com.silicovegas.wombcare.feature.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silicovegas.wombcare.core.data.AccountRepository
import com.silicovegas.wombcare.core.data.AuthRepository
import com.silicovegas.wombcare.core.data.DeviceModePreference
import com.silicovegas.wombcare.core.data.model.UserRole
import com.silicovegas.wombcare.core.device.DeviceSourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the delete-my-data action, so the UI can show progress and errors. */
sealed interface DeleteState {
    data object Idle : DeleteState
    data object Deleting : DeleteState
    data class Error(val message: String) : DeleteState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val deviceMode: DeviceModePreference,
    private val auth: AuthRepository,
    private val account: AccountRepository,
    private val deviceSourceProvider: DeviceSourceProvider,
) : ViewModel() {

    private val _demoMode = MutableStateFlow(deviceMode.isDemoMode())
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    private val _deviceForgotten = MutableStateFlow(false)
    val deviceForgotten: StateFlow<Boolean> = _deviceForgotten.asStateFlow()

    /** True when the OS blocked removeBond (Android 13+) and the user must unpair by hand. */
    private val _needsManualUnpair = MutableStateFlow(false)
    val needsManualUnpair: StateFlow<Boolean> = _needsManualUnpair.asStateFlow()

    /**
     * Remove the Bluetooth bond so the next connect re-prompts for the PIN. On Android 13+
     * the OS may block the programmatic removal — then we flag [needsManualUnpair] so the UI
     * can send the user to system Bluetooth settings to unpair "WombCare" themselves.
     */
    fun forgetDevice() {
        val removed = deviceSourceProvider.current().forget()
        _deviceForgotten.value = true
        _needsManualUnpair.value = !removed
    }

    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState.asStateFlow()

    fun setDemoMode(enabled: Boolean) {
        deviceMode.setDemoMode(enabled)
        _demoMode.value = enabled
    }

    /**
     * Erase everything and delete the account. On success the auth state flips, which the
     * reactive host observes to route back to sign-in — so there's no explicit navigation.
     */
    fun deleteMyData() {
        val uid = auth.currentUid ?: return
        _deleteState.value = DeleteState.Deleting
        viewModelScope.launch {
            val role = auth.loadRole(uid)
            account.deleteMyData(uid, role)
                .onFailure {
                    _deleteState.value = DeleteState.Error(
                        "Couldn't finish deleting. You may need to log in again, then retry.",
                    )
                }
            // On success, do nothing: authState → SignedOut re-routes automatically.
        }
    }
}
