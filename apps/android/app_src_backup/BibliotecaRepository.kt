package br.com.brasiltupi.conecta

// ═══════════════════════════════════════════════════════════════════════════
// BibliotecaRepository.kt  · Fase 3.3
//
// Responsabilidades:
//  • Buscar compras do usuário na tabela purchases
//  • Baixar PDF e salvar criptografado no diretório privado do app
//  • Verificar se PDF já existe offline
//  • Abrir PDF criptografado para leitura em memória (sem extrair para disco)
// ═══════════════════════════════════════════════════════════════════════════

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ── Configuração via BuildConfig ───────────────────────────────────────
private val LOCAL_URL = BuildConfig.SUPABASE_URL
private val LOCAL_KEY = BuildConfig.SUPABASE_KEY

// ── Modelos de domínio ────────────────────────────────────────────────────

data class CursoComprado(
    val id:                  String,
    val produtoId:           String,
    val titulo:              String,
    val capaUrl:             String?,
    val totalAulas:          Int,
    val aulasAssistidas:     Int,
    val percentualConcluido: Double,
    val autorNome:           String,
)

data class ProdutoComprado(
    val id:              String,
    val produtoId:       String,
    val titulo:          String,
    val capaUrl:         String?,
    val tipo:            String,   // "pdf", "produto_digital", etc.
    val autorNome:       String,
    val allowScreenshot: Boolean,
    val disponivelOffline: Boolean = false,
)

// ── DTOs Supabase ─────────────────────────────────────────────────────────

@Serializable
private data class PurchaseSupabase(
    val id:         String,
    @SerialName("produto_id")   val produtoId:  String,
    @SerialName("user_id")      val userId:     String,
    val status:     String,
    val products:   ProductNested? = null,
)

@Serializable
private data class ProductNested(
    val id:               String,
    val titulo:           String,
    val tipo:             String,
    @SerialName("capa_url")        val capaUrl:        String? = null,
    @SerialName("allow_screenshot") val allowScreenshot: Boolean = true,
    val perfis:           ProfNested? = null,
)

@Serializable
private data class ProfNested(
    val nome: String? = null,
)

@Serializable
private data class CourseProgressNested(
    @SerialName("aula_id")              val aulaId:             String,
    @SerialName("percentual_concluido") val percentualConcluido: Double = 0.0,
)

class BibliotecaRepository(private val context: Context) {

    // ── 1. BUSCAR COMPRAS ─────────────────────────────────────────────────
    suspend fun buscarCompras(): Pair<List<CursoComprado>, List<ProdutoComprado>> {
        val userId = currentUserId ?: return Pair(emptyList(), emptyList())
        val token  = currentToken  ?: LOCAL_KEY

        return try {
            val response = httpClient.get("$LOCAL_URL/rest/v1/purchases") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
                parameter("user_id", "eq.$userId")
                parameter("status",  "eq.approved")
                parameter("select",
                    "id,produto_id,user_id,status," +
                            "products(id,titulo,tipo,capa_url,allow_screenshot," +
                            "perfis(nome))"
                )
            }.body<List<PurchaseSupabase>>()

            val cursos   = mutableListOf<CursoComprado>()
            val produtos = mutableListOf<ProdutoComprado>()

            response.forEach { purchase ->
                val produto = purchase.products ?: return@forEach
                when (produto.tipo) {
                    "curso", "aula" -> {
                        // Buscar progresso das aulas do curso
                        val progresso = buscarProgressoCurso(produto.id, userId, token)
                        cursos.add(CursoComprado(
                            id                  = purchase.id,
                            produtoId           = produto.id,
                            titulo              = produto.titulo,
                            capaUrl             = produto.capaUrl,
                            totalAulas          = progresso.first,
                            aulasAssistidas     = progresso.second,
                            percentualConcluido = progresso.third,
                            autorNome           = produto.perfis?.nome ?: "",
                        ))
                    }
                    else -> {
                        produtos.add(ProdutoComprado(
                            id               = purchase.id,
                            produtoId        = produto.id,
                            titulo           = produto.titulo,
                            capaUrl          = produto.capaUrl,
                            tipo             = produto.tipo,
                            autorNome        = produto.perfis?.nome ?: "",
                            allowScreenshot  = produto.allowScreenshot,
                            disponivelOffline = pdfExisteOffline(produto.id),
                        ))
                    }
                }
            }

            Pair(cursos, produtos)
        } catch (e: Exception) {
            AppLogger.erroRede("purchases", e, "userId=$userId")
            Pair(emptyList(), emptyList())
        }
    }

    // ── 2. PROGRESSO DO CURSO ─────────────────────────────────────────────
    // Retorna Triple(totalAulas, aulasAssistidas, percentualMedio)
    private suspend fun buscarProgressoCurso(
        cursoId: String,
        userId:  String,
        token:   String,
    ): Triple<Int, Int, Double> {
        return try {
            val response = httpClient.get("$LOCAL_URL/rest/v1/course_progress") {
                header("apikey",        LOCAL_KEY)
                header("Authorization", "Bearer $token")
                header("Accept",        "application/json")
                parameter("curso_id", "eq.$cursoId")
                parameter("user_id",  "eq.$userId")
                parameter("select",   "aula_id,percentual_concluido")
            }.body<List<CourseProgressNested>>()

            val total      = response.size.takeIf { it > 0 } ?: 1
            val assistidas = response.count { it.percentualConcluido >= 90.0 }
            val media      = response.map { it.percentualConcluido }.average()
                .takeIf { !it.isNaN() } ?: 0.0

            Triple(total, assistidas, media)
        } catch (e: Exception) {
            Triple(0, 0, 0.0)
        }
    }

    // ── 3. DOWNLOAD CRIPTOGRAFADO ─────────────────────────────────────────
    // Baixa PDF via URL assinada e salva com EncryptedFile no dir privado.
    // O arquivo NUNCA fica em texto plano no disco.
    // CORREÇÃO: GeneralSecurityException separada — falha de Keystore gera
    // entrada no Crashlytics em vez de ser engolida pelo catch genérico.
    suspend fun downloadPdfOffline(
        produtoId: String,
        signedUrl: String,
        onProgress: (Float) -> Unit,
    ): Result<Unit> {
        return try {
            // Baixar bytes
            onProgress(0.1f)
            val bytes = baixarBytes(signedUrl) { progresso ->
                onProgress(0.1f + progresso * 0.7f)
            }

            // Salvar com EncryptedFile (write-then-swap internamente)
            onProgress(0.8f)
            salvarCriptografado(produtoId, bytes)
            onProgress(1.0f)

            AppLogger.info("BibliotecaRepo", "PDF offline salvo: produto=$produtoId")
            Result.success(Unit)
        } catch (e: java.security.GeneralSecurityException) {
            AppLogger.erro("BibliotecaRepo", "Keystore inválido ao salvar PDF offline=$produtoId", e)
            Result.failure(e)
        } catch (e: Exception) {
            AppLogger.erro("BibliotecaRepo", "Falha ao salvar PDF offline: produto=$produtoId", e)
            Result.failure(e)
        }
    }

    // ── 4. LER PDF CRIPTOGRAFADO EM MEMÓRIA ──────────────────────────────
    // Retorna ByteArray para o PdfViewerViewModel renderizar.
    // Nunca extrai para disco em texto plano.
    // CORREÇÃO: GeneralSecurityException separada de IOException — Keystore
    // inválido (ex: update de OS) gera entrada no Crashlytics e remove o
    // arquivo corrompido. IOException é aviso silencioso (sem entrada).
    fun lerPdfOffline(produtoId: String): ByteArray? {
        return try {
            val arquivo = arquivoOffline(produtoId)
            if (!arquivo.exists()) return null

            val masterKey = masterKey()
            val encryptedFile = EncryptedFile.Builder(
                context,
                arquivo,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
            ).build()

            encryptedFile.openFileInput().use { it.readBytes() }
        } catch (e: java.security.GeneralSecurityException) {
            // Keystore comprometido ou chave inválida após update de OS.
            // Remove o arquivo inutilizável para não ocupar espaço e não
            // repetir o erro em aberturas futuras.
            AppLogger.erro("BibliotecaRepo", "Keystore inválido — removendo PDF corrompido=$produtoId", e)
            arquivoOffline(produtoId).delete()
            null
        } catch (e: java.io.IOException) {
            AppLogger.aviso("BibliotecaRepo", "IO falhou ao ler PDF offline=$produtoId: ${e.message}")
            null
        }
    }

    // ── 5. VERIFICAR SE EXISTE OFFLINE ────────────────────────────────────
    fun pdfExisteOffline(produtoId: String): Boolean =
        arquivoOffline(produtoId).exists()

    // ── 6. REMOVER PDF OFFLINE ────────────────────────────────────────────
    fun removerPdfOffline(produtoId: String): Boolean =
        arquivoOffline(produtoId).delete()

    // ── Helpers privados ──────────────────────────────────────────────────

    private fun arquivoOffline(produtoId: String): File {
        val dir = File(context.filesDir, "pdfs_offline").also { it.mkdirs() }
        return File(dir, "$produtoId.enc")
    }

    private fun masterKey(): MasterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun salvarCriptografado(produtoId: String, bytes: ByteArray) {
        val arquivoFinal = arquivoOffline(produtoId)
        // CORREÇÃO write-then-swap: escreve em arquivo temporário e só substitui
        // o original após write bem-sucedido. Evita deixar arquivo parcialmente
        // corrompido se houver falta de espaço ou exceção no meio do write.
        val arquivoTemp = File(arquivoFinal.parent, "$produtoId.tmp.enc")
        if (arquivoTemp.exists()) arquivoTemp.delete()

        val masterKey = masterKey()
        val encryptedFile = EncryptedFile.Builder(
            context,
            arquivoTemp,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

        encryptedFile.openFileOutput().use { it.write(bytes) }

        // Write concluído com sucesso — substituir o arquivo final
        if (arquivoFinal.exists()) arquivoFinal.delete()
        arquivoTemp.renameTo(arquivoFinal)
    }

    private fun baixarBytes(url: String, onProgress: (Float) -> Unit): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout    = 60_000
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            val tamanho = connection.contentLength.toFloat()
            val buffer  = ByteArray(8192)
            val output  = java.io.ByteArrayOutputStream()
            var lido    = 0

            connection.inputStream.use { input ->
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    lido += bytes
                    if (tamanho > 0) onProgress(lido / tamanho)
                }
            }
            output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}

// ── Factory manual ────────────────────────────────────────────────────────
class BibliotecaRepositoryFactory(private val context: Context) {
    fun create(): BibliotecaRepository = BibliotecaRepository(context)
}