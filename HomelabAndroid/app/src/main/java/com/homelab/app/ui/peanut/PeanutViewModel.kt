package com.homelab.app.ui.peanut

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.PeanutDashboardData
import com.homelab.app.data.repository.PeanutRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PeanutViewModel @Inject constructor(
    private val repository: PeanutRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<UiState<PeanutDashboardData>>(UiState.Loading)
    val uiState: StateFlow<UiState<PeanutDashboardData>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.PEANUT].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        fetchDashboard(forceLoading = true)
    }

    fun fetchDashboard(forceLoading: Boolean = false) {
        viewModelScope.launch {
            if (forceLoading || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }
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

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.PEANUT, newInstanceId)
        }
    }
}
