package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// BrasilTupiApp.kt — classe Application do projeto
//
// Responsabilidades:
//  • Inicializar Firebase (FirebaseApp.initializeApp é chamado automaticamente
//    pelo FirebaseInitProvider, mas garantimos aqui para cenários edge-case)
//  • Configurar Crashlytics: habilitar coleta apenas em release
//  • Definir chaves de contexto globais (versão do app, build type)
//
// REGISTRO NO MANIFEST:
//   Adicionar no AndroidManifest.xml dentro de <application>:
//     android:name=".BrasilTupiApp"
//
// ═══════════════════════════════════════════════════════════════════════════

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

class BrasilTupiApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ── Firebase ──────────────────────────────────────────────────────
        // FirebaseInitProvider já inicializa o FirebaseApp automaticamente
        // via ContentProvider antes do onCreate(). Esta chamada é segura
        // (não duplica a inicialização) e garante funcionamento mesmo em
        // cenários onde o provider falha (ex: multi-process ou instant apps).
        FirebaseApp.initializeApp(this)

        // ── Crashlytics ───────────────────────────────────────────────────
        val crashlytics = FirebaseCrashlytics.getInstance()

        // Habilitar coleta APENAS em builds de release.
        // Em debug, o Crashlytics está presente mas não envia dados,
        // evitando poluição do dashboard com crashes de desenvolvimento.
        val isRelease = !BuildConfig.DEBUG
        crashlytics.setCrashlyticsCollectionEnabled(isRelease)

        if (isRelease) {
            // Chaves de contexto globais — aparecem em todo crash report
            crashlytics.setCustomKey("app_version_name", BuildConfig.VERSION_NAME)
            crashlytics.setCustomKey("app_version_code", BuildConfig.VERSION_CODE)
            crashlytics.setCustomKey("build_type",       "release")
            crashlytics.log("App iniciado — v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// INSTRUÇÃO DE REGISTRO NO MANIFEST
//
// Abra o AndroidManifest.xml e adicione o atributo `android:name` na tag
// <application>. O resultado deve ficar assim:
//
//   <application
//       android:name=".BrasilTupiApp"
//       android:allowBackup="true"
//       android:icon="@mipmap/ic_launcher"
//       ... >
//
// Sem essa linha, a classe Application nunca é instanciada e o Crashlytics
// não será configurado corretamente.
// ═══════════════════════════════════════════════════════════════════════════