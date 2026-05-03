package br.com.brasiltupi.conecta

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// Extension property to provide a singleton DataStore instance for onboarding
val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

