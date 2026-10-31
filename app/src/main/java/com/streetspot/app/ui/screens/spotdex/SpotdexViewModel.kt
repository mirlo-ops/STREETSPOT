package com.streetspot.app.ui.screens.spotdex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streetspot.app.data.model.CarModelSpotdexEntry
import com.streetspot.app.data.repository.CarCatalogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class SpotdexViewModel(catalogRepository: CarCatalogRepository) : ViewModel() {
    val entries = catalogRepository.observeSpotdex().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList<CarModelSpotdexEntry>(),
    )
}
