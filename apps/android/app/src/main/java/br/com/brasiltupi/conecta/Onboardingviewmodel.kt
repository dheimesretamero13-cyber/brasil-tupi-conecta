package br.com.brasiltupi.conecta

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class VerificacaoEstado {
    PENDENTE, VERIFICADO_SIM, VERIFICADO_NAO
}

sealed class OnboardingNavState {
    object Carregando : OnboardingNavState()
    object MostrarOnboarding : OnboardingNavState()
    object IrParaCliente : OnboardingNavState()
    object IrParaProfissional : OnboardingNavState()
}

class OnboardingViewModel(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _navState = MutableStateFlow<OnboardingNavState>(OnboardingNavState.Carregando)
    val navState: StateFlow<OnboardingNavState> = _navState.asStateFlow()

    private val _consentimentoEstado = MutableStateFlow(VerificacaoEstado.PENDENTE)
    val consentimentoEstado: StateFlow<VerificacaoEstado> = _consentimentoEstado.asStateFlow()

    private val _kycAprovado = MutableStateFlow(VerificacaoEstado.PENDENTE)
    val kycAprovado: StateFlow<VerificacaoEstado> = _kycAprovado.asStateFlow()

    private val CONSENTIMENTO_KEY = booleanPreferencesKey("consentimento_aceito")
    private val ULTIMO_ACESSO_KEY = longPreferencesKey("ultimo_acesso")
    private val USER_TYPE_KEY = stringPreferencesKey("user_type")
    private val PRIMEIRO_PAGAMENTO_KEY = booleanPreferencesKey("primeiro_pagamento_registrado")
    private val PRIMEIRA_CHAMADA_KEY = booleanPreferencesKey("primeira_chamada_registrada")

    init {
        viewModelScope.launch {
            dataStore.data.map { prefs ->
                val tipo = prefs[USER_TYPE_KEY] ?: ""
                when (tipo) {
                    "cliente" -> OnboardingNavState.IrParaCliente
                    "profissional" -> OnboardingNavState.IrParaProfissional
                    else -> OnboardingNavState.MostrarOnboarding
                }
            }.collect { novoEstado ->
                _navState.value = novoEstado
            }
        }
    }

    fun selecionarCliente() {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[USER_TYPE_KEY] = "cliente" }
        }
    }

    fun selecionarProfissional() {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[USER_TYPE_KEY] = "profissional" }
        }
    }

    fun gravarConsentimentoLocal() {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[CONSENTIMENTO_KEY] = true }
        }
    }

    fun salvarConsentimentoLocal(aceito: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[CONSENTIMENTO_KEY] = aceito }
        }
    }

    // PA-01: Convertida para suspend — elimina runBlocking e risco de ANR na Main Thread.
    // Chamadores em LaunchedEffect (MainActivity.kt) não precisam de alteração.
    suspend fun consentimentoExisteLocal(): Boolean {
        return dataStore.data
            .map { prefs -> prefs[CONSENTIMENTO_KEY] ?: false }
            .first()
    }

    suspend fun salvarUltimoAcesso(timestamp: Long) {
        dataStore.edit { prefs -> prefs[ULTIMO_ACESSO_KEY] = timestamp }
    }

    // PA-01: Convertida para suspend — elimina runBlocking e risco de ANR na Main Thread.
    suspend fun ultimoAcesso(): Long {
        return dataStore.data
            .map { prefs -> prefs[ULTIMO_ACESSO_KEY] ?: 0L }
            .first()
    }

    suspend fun registrarPrimeiroPagamento(): Boolean {
        val prefs = dataStore.data.first()
        val ehPrimeiro = !(prefs[PRIMEIRO_PAGAMENTO_KEY] ?: false)
        if (ehPrimeiro) {
            dataStore.edit { prefs -> prefs[PRIMEIRO_PAGAMENTO_KEY] = true }
        }
        return ehPrimeiro
    }

    suspend fun registrarPrimeiraChamada(): Boolean {
        val prefs = dataStore.data.first()
        val ehPrimeira = !(prefs[PRIMEIRA_CHAMADA_KEY] ?: false)
        if (ehPrimeira) {
            dataStore.edit { prefs -> prefs[PRIMEIRA_CHAMADA_KEY] = true }
        }
        return ehPrimeira
    }

    fun verificarConsentimento(uid: String) {
        viewModelScope.launch {
            val existe = try {
                verificarConsentimentoExiste(uid)
            } catch (e: Exception) {
                false
            }
            _consentimentoEstado.value = if (existe) VerificacaoEstado.VERIFICADO_SIM
            else VerificacaoEstado.VERIFICADO_NAO
        }
    }

    fun verificarKyc(uid: String) {
        if (_kycAprovado.value != VerificacaoEstado.PENDENTE) return
        viewModelScope.launch {
            val aprovado = try {
                verificarKycAprovado(uid)
            } catch (e: Exception) {
                false
            }
            _kycAprovado.value = if (aprovado) VerificacaoEstado.VERIFICADO_SIM
            else VerificacaoEstado.VERIFICADO_NAO
        }
    }

    fun resetarVerificacoes() {
        _consentimentoEstado.value = VerificacaoEstado.PENDENTE
        _kycAprovado.value = VerificacaoEstado.PENDENTE
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs.remove(CONSENTIMENTO_KEY)
                prefs.remove(USER_TYPE_KEY)
            }
        }
    }
}

class OnboardingViewModelFactory(private val dataStore: DataStore<Preferences>) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(dataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}