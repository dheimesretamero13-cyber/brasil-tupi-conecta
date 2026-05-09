package br.com.brasiltupi.conecta

import android.content.Context
import android.content.SharedPreferences

object ConsentimentoCache {
    private const val PREFS_NAME = "consentimento_prefs"
    private const val KEY_ACEITO = "consentimento_aceito"

    fun isAceito(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ACEITO, false)
    }

    fun marcarAceito(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ACEITO, true).apply()
    }
}