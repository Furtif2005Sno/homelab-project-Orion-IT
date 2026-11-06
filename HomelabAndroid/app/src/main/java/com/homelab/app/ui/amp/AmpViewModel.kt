package com.homelab.app.ui.amp

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.AmpAction
import com.homelab.app.data.repository.AmpDashboardData
import com.homelab.app.data.repository.AmpGameInstance
import com.homelab.app.data.repository.AmpRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AmpViewModel @Inject constructor(
    private val repository: AmpRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<UiState<AmpDashboardData>>(UiState.Loading)
    val uiState: StateFlow<UiState<AmpDashboardData>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Id of the game instance an action is running on, to disable its buttons meanwhile. */
    private val _busyInstanceId = MutableStateFlow<String?>(null)
    val busyInstanceId: StateFlow<String?> = _busyInstanceId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.AMP].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        fetchDashboard(forceLoading = true)
    }

    fun fetchDashboard(forceLoading: Boolean = false) {
        viewModelScope.launch {
            if (forceLoading || _uiState.value !is UiState.Success) _uiState.value = UiState.Loading
            _isRefreshing.value = true
            try {
                _uiState.value = UiState.Success(repository.getDashboard(instanceId))
                servicesRepository.markInstanceReachable(instanceId)
            } catch (error: Exception) {
                _uiState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { fetchDashboard(forceLoading = true) }
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun runAction(target: AmpGameInstance, action: AmpAction) {
        if (_busyInstanceId.value != null) return
        viewModelScope.launch {
            _busyInstanceId.value = target.id
            try {
                repository.runAction(instanceId, target, action)
                // AMP applies state changes asynchronously; give it a moment before refreshing.
                delay(1_500)
                _uiState.value = UiState.Success(repository.getDashboard(instanceId))
            } catch (error: Exception) {
                _message.value = ErrorHandler.getMessage(context, error)
            } finally {
                _busyInstanceId.value = null
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch { servicesRepository.setPreferredInstance(ServiceType.AMP, newInstanceId) }
    }
}
