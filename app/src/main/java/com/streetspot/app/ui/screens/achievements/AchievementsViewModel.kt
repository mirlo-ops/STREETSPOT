package com.streetspot.app.ui.screens.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streetspot.app.data.repository.ProgressRepository
import com.streetspot.app.domain.progression.AchievementProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class AchievementsViewModel(progressRepository: ProgressRepository) : ViewModel() {
    val achievements = progressRepository.observeAchievements().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList<AchievementProgress>(),
    )
}
