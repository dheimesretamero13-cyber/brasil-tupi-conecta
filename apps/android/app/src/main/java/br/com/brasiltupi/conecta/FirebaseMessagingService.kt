package br.com.brasiltupi.conecta

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
class BrasilTupiMessagingService : FirebaseMessagingService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val titulo = remoteMessage.notification?.title
            ?: remoteMessage.data["titulo"]
            ?: "Brasil Tupi Conecta"

        val corpo = remoteMessage.notification?.body
            ?: remoteMessage.data["corpo"]
            ?: "Você tem uma nova notificação."

        val tipo = remoteMessage.data["tipo"] ?: ""

        mostrarNotificacao(titulo, corpo, tipo)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Salvar token no Supabase para enviar notificações direcionadas
        salvarTokenFirebase(token)
    }

    private fun salvarTokenFirebase(token: String) {
        val uid = currentUserId ?: run {
            android.util.Log.d("FCM", "Token recebido sem usuário logado: $token")
            return
        }
        serviceScope.launch {
            salvarFcmTokenAndroid(uid, token)
        }
    }

    private fun mostrarNotificacao(titulo: String, corpo: String, tipo: String) {
        val channelId = when (tipo) {
            "urgencia" -> "canal_urgencias"
            "avaliacao" -> "canal_avaliacoes"
            else -> "canal_geral"
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("tipo_notificacao", tipo)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Criar canal de notificação (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val (nomeCanal, descricao, importancia) = when (tipo) {
                "urgencia" -> Triple(
                    "Consultas Urgentes",
                    "Notificações de consultas urgentes",
                    NotificationManager.IMPORTANCE_HIGH
                )
                "avaliacao" -> Triple(
                    "Avaliações",
                    "Notificações de novas avaliações",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                else -> Triple(
                    "Geral",
                    "Notificações gerais da plataforma",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            }
            val canal = NotificationChannel(channelId, nomeCanal, importancia).apply {
                description = descricao
            }
            notificationManager.createNotificationChannel(canal)
        }

        val notificacao = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (tipo == "urgencia") NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .build()

        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notificacao)
    }
}