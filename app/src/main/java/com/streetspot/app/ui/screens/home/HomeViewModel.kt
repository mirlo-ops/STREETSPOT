package com.streetspot.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streetspot.app.data.repository.HomeSnapshot
import com.streetspot.app.data.repository.ProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(progressRepository: ProgressRepository) : ViewModel() {
    val snapshot = progressRepository.observeHome().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeSnapshot(),
    )
}
