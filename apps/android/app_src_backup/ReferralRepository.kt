package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// ReferralRepository.kt  · Fase 4.2
// ═══════════════════════════════════════════════════════════════════════════

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Configuração via BuildConfig ───────────────────────────────────────
private val LOCAL_URL = BuildConfig.SUPABASE_URL
private val LOCAL_KEY = BuildConfig.SUPABASE_KEY

@Serializable
data class CreditoUsuario(
    val id:         String,
    val amount:     Double,
    val reason:     String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val usado:      Boolean = false,
    @SerialName("criado_em") val criadoEm:  String  = "",
)

@Serializable
data class ReferralInfo(
    val id:          String,
    @SerialName("referee_id")  val refereeId:  String,
    val status:      String,
    @SerialName("criado_em")   val criadoEm:   String = "",
    @SerialName("creditado_em") val creditadoEm: String? = null,
)

@Serializable
private data class RpcGerarCodeRequest(
    @SerialName("p_user_id") val userId: String,
)

@Serializable
private data class RpcAplicarCodeRequest(
    @SerialName("p_referee_id") val refereeId: String,
    @SerialName("p_codigo")     val codigo:    String,
)

class ReferralRepository {

    // ── 1. GERAR OU BUSCAR CÓDIGO DO USUÁRIO ──────────────────────────────
    suspend fun obterOuGerarCodigo(): String? {
        val userId = currentUserId ?: return null
        val token  = currentToken  ?: LOCAL_KEY
        return try {
            httpClient.post("$LOCAL_URL/rest/v1/rpc/gerar_referral_code") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Content-Type",  "application/json")
                setBody(RpcGerarCodeRequest(userId = userId))
            }.body<String>().trim().removeSurrounding("\"")
        } catch (e: Exception) {
            AppLogger.erroRede("rpc/gerar_referral_code", e)
            null
        }
    }

    // ── 2. APLICAR CÓDIGO NO CADASTRO ────────────────────────────────────
    // Retorna: "ok", "codigo_invalido", "codigo_proprio", "ja_indicado"
    suspend fun aplicarCodigo(codigo: String): String {
        val userId = currentUserId ?: return "sessao_invalida"
        val token  = currentToken  ?: LOCAL_KEY
        return try {
            val response = httpClient.post(
                "$LOCAL_URL/rest/v1/rpc/aplicar_referral_code"
            ) {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Content-Type",  "application/json")
                setBody(RpcAplicarCodeRequest(
                    refereeId = userId,
                    codigo    = codigo,
                ))
            }.body<kotlinx.serialization.json.JsonObject>()

            if (response["ok"]?.toString() == "true") "ok"
            else response["erro"]?.toString()?.removeSurrounding("\"") ?: "erro_desconhecido"
        } catch (e: Exception) {
            AppLogger.erroRede("rpc/aplicar_referral_code", e)
            "erro_rede"
        }
    }

    // ── 3. BUSCAR CRÉDITOS DO USUÁRIO ─────────────────────────────────────
    suspend fun buscarCreditos(): List<CreditoUsuario> {
        val userId = currentUserId ?: return emptyList()
        val token  = currentToken  ?: LOCAL_KEY
        return try {
            httpClient.get("$LOCAL_URL/rest/v1/credits") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
                parameter("user_id", "eq.$userId")
                parameter("usado",   "eq.false")
                parameter("select",  "id,amount,reason,expires_at,usado,criado_em")
                parameter("order",   "criado_em.desc")
            }.body<List<CreditoUsuario>>()
        } catch (e: Exception) {
            AppLogger.erroRede("credits", e)
            emptyList()
        }
    }

    // ── 4. BUSCAR INDICAÇÕES FEITAS ───────────────────────────────────────
    suspend fun buscarIndicacoes(): List<ReferralInfo> {
        val userId = currentUserId ?: return emptyList()
        val token  = currentToken  ?: LOCAL_KEY
        return try {
            httpClient.get("$LOCAL_URL/rest/v1/referrals") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
                parameter("referrer_id", "eq.$userId")
                parameter("select",      "id,referee_id,status,criado_em,creditado_em")
                parameter("order",       "criado_em.desc")
            }.body<List<ReferralInfo>>()
        } catch (e: Exception) {
            AppLogger.erroRede("referrals", e)
            emptyList()
        }
    }
}

object ReferralRepositoryFactory {
    fun create(): ReferralRepository = ReferralRepository()
}