package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// BrasilTupiMessagingService.kt  · Fase 2.6 (atualizado)
//
// Mudanças em relação à versão original:
//  1. Deep linking via payload `data` — cada notificação abre a tela certa
//  2. Novo canal `canal_pagamentos` adicionado
//  3. Token registrado na tabela `user_fcm_tokens` (não mais em `perfis`)
//     com device_id (ANDROID_ID) para suporte a múltiplos dispositivos
//  4. AppLogger instrumentando registro de token e erros
//  5. Registro de token também ao abrir o app (não só ao renovar)
// ═══════════════════════════════════════════════════════════════════════════

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── DTO para upsert na tabela user_fcm_tokens ─────────────────────────────
@Serializable
private data class FcmTokenUpsertRequest(
    @SerialName("user_id")   val userId:     String,
    @SerialName("fcm_token") val fcmToken:   String,
    @SerialName("device_id") val deviceId:   String,
    val platform:            String = "android",
)

private const val API_KEY = SUPABASE_KEY
private const val TAG_FCM      = "FCMService"

// ── Canais de notificação ─────────────────────────────────────────────────
// Definidos como constantes para reuso no AndroidManifest e no serviço
object CanalFcm {
    const val URGENCIAS  = "canal_urgencias"
    const val PAGAMENTOS = "canal_pagamentos"
    const val AGENDAMENTOS = "canal_agendamentos"
    const val GERAL      = "canal_geral"
}

class BrasilTupiMessagingService : FirebaseMessagingService() {

    private val serviceJob   = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ── Mensagem recebida ─────────────────────────────────────────────────

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val titulo = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Brasil Tupi Conecta"

        val corpo  = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "Você tem uma nova notificação."

        val tipo       = remoteMessage.data["tipo"]        ?: ""
        val tela       = remoteMessage.data["tela"]        ?: ""
        val urgenciaId = remoteMessage.data["urgencia_id"] ?: ""
        val slotId     = remoteMessage.data["slot_id"]     ?: ""

        AppLogger.info(TAG_FCM, "Notificação recebida: tipo=$tipo tela=$tela")

        mostrarNotificacao(
            titulo     = titulo,
            corpo      = corpo,
            tipo       = tipo,
            tela       = tela,
            urgenciaId = urgenciaId,
            slotId     = slotId,
        )
    }

    // ── Token renovado pelo Firebase ──────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppLogger.info(TAG_FCM, "Token FCM renovado — registrando no Supabase")
        serviceScope.launch {
            registrarTokenNoSupabase(token)
        }
    }

    // ── Exibir notificação com deep link ──────────────────────────────────

    private fun mostrarNotificacao(
        titulo:     String,
        corpo:      String,
        tipo:       String,
        tela:       String,
        urgenciaId: String,
        slotId:     String,
    ) {
        val channelId = when (tipo) {
            "urgencia"    -> CanalFcm.URGENCIAS
            "pagamento"   -> CanalFcm.PAGAMENTOS
            "agendamento" -> CanalFcm.AGENDAMENTOS
            else          -> CanalFcm.GERAL
        }

        // Intent com deep link — MainActivity lê os extras e navega
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Extras para deep linking
            putExtra("fcm_tela",        tela)
            putExtra("fcm_tipo",        tipo)
            putExtra("fcm_urgencia_id", urgenciaId)
            putExtra("fcm_slot_id",     slotId)
        }

        val requestCode = System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Criar canais (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            criarCanais(notificationManager)
        }

        val prioridade = if (tipo == "urgencia")
            NotificationCompat.PRIORITY_MAX
        else
            NotificationCompat.PRIORITY_DEFAULT

        val notificacao = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(prioridade)
            .also {
                if (tipo == "urgencia") {
                    it.setDefaults(NotificationCompat.DEFAULT_ALL)
                    it.setVibrate(longArrayOf(0, 500, 200, 500))
                }
            }
            .build()

        val notifId = when (tipo) {
            "urgencia"    -> 1001
            "pagamento"   -> 1002
            "agendamento" -> 1003
            else          -> (System.currentTimeMillis() % 10000).toInt()
        }

        notificationManager.notify(notifId, notificacao)
        AppLogger.info(TAG_FCM, "Notificação exibida: id=$notifId canal=$channelId")
    }

    // ── Criar todos os canais de notificação ──────────────────────────────

    private fun criarCanais(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        listOf(
            NotificationChannel(
                CanalFcm.URGENCIAS,
                "Consultas Urgentes",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Notificações de novas consultas urgentes" },

            NotificationChannel(
                CanalFcm.PAGAMENTOS,
                "Pagamentos",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Confirmações e atualizações de pagamento" },

            NotificationChannel(
                CanalFcm.AGENDAMENTOS,
                "Agendamentos",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Confirmações de novos agendamentos" },

            NotificationChannel(
                CanalFcm.GERAL,
                "Geral",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Notificações gerais da plataforma" },
        ).forEach { nm.createNotificationChannel(it) }
    }

    // ── Registrar/atualizar token na tabela user_fcm_tokens ───────────────

    @SuppressLint("HardwareIds")
    private suspend fun registrarTokenNoSupabase(token: String) {
        val uid = currentUserId ?: run {
            AppLogger.aviso(TAG_FCM, "Token recebido mas usuario nao logado — aguardando login")
            return
        }

        // device_id estável por dispositivo — identifica o aparelho
        val deviceId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: "unknown"

        try {
            val response = httpClient.post(
                "$SUPABASE_URL/rest/v1/user_fcm_tokens"
            ) {
                header("apikey",        API_KEY)
                header("Authorization", "Bearer ${currentToken ?: API_KEY}")
                header("Content-Type",  "application/json")
                // ON CONFLICT (user_id, device_id) → atualiza token e last_updated
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                setBody(FcmTokenUpsertRequest(
                    userId   = uid,
                    fcmToken = token,
                    deviceId = deviceId,
                ))
            }

            if (response.status.value in 200..299) {
                AppLogger.info(TAG_FCM, "Token registrado: user=$uid device=$deviceId")
            } else {
                AppLogger.aviso(TAG_FCM,
                    "Falha ao registrar token: HTTP ${response.status.value}"
                )
            }
        } catch (e: Exception) {
            AppLogger.erroRede("POST /user_fcm_tokens", e, "user=$uid")
        }
    }

    companion object {
        /**
         * Registrar token ao fazer login ou iniciar o app.
         * Chamar após login bem-sucedido no signInAndroid().
         * Scope externo necessário pois é companion object.
         */
        fun registrarTokenSeLogado(scope: CoroutineScope, context: Context) {
            val uid = currentUserId ?: return
            scope.launch {
                try {
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        scope.launch {
                            @SuppressLint("HardwareIds")
                            val deviceId = Settings.Secure.getString(
                                context.contentResolver,
                                Settings.Secure.ANDROID_ID,
                            ) ?: "unknown"

                            try {
                                httpClient.post(
                                    "$SUPABASE_URL/rest/v1/user_fcm_tokens"
                                ) {
                                    header("apikey",        API_KEY)
                                    header("Authorization", "Bearer ${currentToken ?: API_KEY}")
                                    header("Content-Type",  "application/json")
                                    header("Prefer", "resolution=merge-duplicates,return=minimal")
                                    setBody(FcmTokenUpsertRequest(
                                        userId   = uid,
                                        fcmToken = token,
                                        deviceId = deviceId,
                                    ))
                                }
                                AppLogger.info(TAG_FCM, "Token registrado no login: user=$uid")
                            } catch (e: Exception) {
                                AppLogger.erroRede("POST /user_fcm_tokens (login)", e, "user=$uid")
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.aviso(TAG_FCM, "Erro ao obter token FCM: ${e.message}")
                }
            }
        }
    }
}