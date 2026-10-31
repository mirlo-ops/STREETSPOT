package com.streetspot.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streetspot.app.data.repository.ProgressRepository
import com.streetspot.app.domain.statistics.StatisticsSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class StatisticsViewModel(progressRepository: ProgressRepository) : ViewModel() {
    val snapshot = progressRepository.observeStatistics().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null as StatisticsSnapshot?,
    )
}
