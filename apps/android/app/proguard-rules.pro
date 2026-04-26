# ═══════════════════════════════════════════════════════════════════════════
# proguard-rules.pro — Brasil Tupi Conecta
# Stack: Ktor 2.3.7 (Android + OkHttp + WebSockets + ContentNegotiation)
#        kotlinx.serialization 1.6.3
#        Firebase Messaging
#        Coil 2.5.0
#        Jetpack Compose (regras geradas automaticamente via consumer rules)
#
# CRITÉRIO DESTA LISTA: cada regra tem uma justificativa técnica explícita.
# Regras sem justificativa são ruído — não foram adicionadas.
# ═══════════════════════════════════════════════════════════════════════════


# ───────────────────────────────────────────────────────────────────────────
# 1. KOTLINX.SERIALIZATION — CRÍTICO
#
# Por que quebra sem isso:
#   O compilador de serialização gera um companion object `serializer()` e
#   um `SerialDescriptor` em cada classe @Serializable. O R8 remove ou
#   renomeia esses membros porque não vê referências diretas a eles em
#   tempo de compilação — as chamadas passam por reflection interna do
#   runtime. Resultado sem a regra: SerializationException em produção,
#   nunca em debug (debug não minifica).
# ───────────────────────────────────────────────────────────────────────────

# Mantém o runtime de serialização inteiro (KSerializer, SerialDescriptor, etc.)
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

# Mantém o companion object e os membros gerados pelo plugin do compilador
# em QUALQUER classe anotada com @Serializable no projeto
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
    static ** $serializer;
    public static final ** serializer();
    kotlinx.serialization.KSerializer serializer(...);
    *** write$Self(...);
    *** read(...);
}

# Mantém enum classes serializáveis (usadas em sealed classes e models)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ───────────────────────────────────────────────────────────────────────────
# 2. MODELOS DE DADOS DO PROJETO — CRÍTICO
#
# Por que quebra sem isso:
#   Todos os modelos abaixo são serializados/desserializados via
#   kotlinx.serialization usando os nomes exatos dos campos para montar
#   JSON enviado ao Supabase REST ou recebido dele.
#   O R8 renomeia campos (ex: `professional_id` → `a`) quebrando o
#   mapeamento JSON ↔ Kotlin. @SerialName não protege contra isso sozinho
#   — o campo em si ainda pode ser removido se o R8 não ver uso direto.
# ───────────────────────────────────────────────────────────────────────────

-keep @kotlinx.serialization.Serializable class br.com.brasiltupi.conecta.** { *; }

# Proteção explícita para cada modelo público (belt-and-suspenders)
-keep class br.com.brasiltupi.conecta.PerfilUsuario { *; }
-keep class br.com.brasiltupi.conecta.PerfilNested { *; }
-keep class br.com.brasiltupi.conecta.ProfissionalComPerfil { *; }
-keep class br.com.brasiltupi.conecta.AuthRequest { *; }
-keep class br.com.brasiltupi.conecta.AuthResponse { *; }
-keep class br.com.brasiltupi.conecta.AuthUser { *; }
-keep class br.com.brasiltupi.conecta.ConsultaProfissional { *; }
-keep class br.com.brasiltupi.conecta.Mensagem { *; }
-keep class br.com.brasiltupi.conecta.ResultadoAcesso { *; }

# Modelos do UrgenciasRealtime — CRÍTICO para RPC e WebSocket
-keep class br.com.brasiltupi.conecta.Urgencia { *; }
-keep class br.com.brasiltupi.conecta.AcceptUrgenciaRequest { *; }

# Sealed classes de estado — campos podem ser inspecionados via reflection
-keep class br.com.brasiltupi.conecta.EventoUrgencia { *; }
-keep class br.com.brasiltupi.conecta.EventoUrgencia$* { *; }
-keep class br.com.brasiltupi.conecta.ResultadoAceitacao { *; }
-keep class br.com.brasiltupi.conecta.ResultadoAceitacao$* { *; }
-keep class br.com.brasiltupi.conecta.AceitacaoState { *; }
-keep class br.com.brasiltupi.conecta.AceitacaoState$* { *; }


# ───────────────────────────────────────────────────────────────────────────
# 3. KTOR CLIENT — CRÍTICO
#
# Por que quebra sem isso:
#   Ktor usa ServiceLoader para descobrir engines e plugins em runtime.
#   O R8 remove entradas de META-INF/services/ e renomeia as implementações
#   referenciadas, causando "No factory" ou "No engine" na inicialização.
#   O engine OkHttp é especialmente sensível porque carrega a engine via
#   reflection a partir do nome de classe registrado no ServiceLoader.
# ───────────────────────────────────────────────────────────────────────────

# Engine principal (Android / OkHttp)
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.client.engine.android.** { *; }
-keep class io.ktor.client.engine.okhttp.** { *; }

# Plugin de ContentNegotiation e serialização JSON
-keep class io.ktor.client.plugins.contentnegotiation.** { *; }
-keep class io.ktor.serialization.kotlinx.** { *; }

# Plugin e runtime de WebSocket
-keep class io.ktor.client.plugins.websocket.** { *; }
-keep class io.ktor.websocket.** { *; }

# Chamadas HTTP (Request, Response, Headers) — usadas via reflection no pipeline
-keep class io.ktor.client.request.** { *; }
-keep class io.ktor.client.statement.** { *; }
-keep class io.ktor.http.** { *; }

# Utils internos do Ktor (logging, pipeline) — não manter tudo, só o necessário
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

# ServiceLoader — impede que o R8 remova entradas de META-INF/services
-keepnames class io.ktor.client.HttpClientEngineContainer


# ───────────────────────────────────────────────────────────────────────────
# 4. OKHTTP (dependência transitiva do Ktor OkHttp engine) — CRÍTICO
#
# Por que quebra sem isso:
#   OkHttp usa reflection para carregar extensões de plataforma (Platform.kt)
#   e para inspecionar certificados SSL. Sem proteção, o R8 remove classes
#   internas que o OkHttp procura por nome em runtime.
# ───────────────────────────────────────────────────────────────────────────

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**


# ───────────────────────────────────────────────────────────────────────────
# 5. FIREBASE MESSAGING — CRÍTICO
#
# Por que quebra sem isso:
#   O FCM precisa que BrasilTupiMessagingService (subclasse de
#   FirebaseMessagingService) seja mantida com seu nome exato, pois o
#   AndroidManifest.xml referencia o nome de classe como string — e o R8
#   não vê essa referência em tempo de análise do bytecode.
# ───────────────────────────────────────────────────────────────────────────

-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Subclasse do projeto — nome registrado no Manifest
-keep class br.com.brasiltupi.conecta.BrasilTupiMessagingService { *; }

# Mantém os métodos de callback sobrescritos (onMessageReceived, onNewToken)
-keepclassmembers class * extends com.google.firebase.messaging.FirebaseMessagingService {
    public void onMessageReceived(com.google.firebase.messaging.RemoteMessage);
    public void onNewToken(java.lang.String);
}


# ───────────────────────────────────────────────────────────────────────────
# 6. COIL — RECOMENDADO
#
# Por que pode quebrar:
#   Coil usa reflection para inspecionar componentes registrados no
#   ImageLoader. O compose-extension usa classes internas que o R8
#   pode remover como "não utilizadas" se a referência vier de um
#   Composable (que é gerado como lambda, não como chamada direta).
# ───────────────────────────────────────────────────────────────────────────

-keep class coil.** { *; }
-keepclassmembers class coil.** { *; }
-dontwarn coil.**


# ───────────────────────────────────────────────────────────────────────────
# 7. COROUTINES / KOTLIN REFLECT — RECOMENDADO
#
# Por que pode quebrar:
#   kotlinx.coroutines usa `DEBUG_PROPERTY_NAME` e inspeção de stack trace
#   para identificar coroutines em modo debug. O R8 pode remover campos
#   que parecem inutilizados mas são lidos via System.getProperty em runtime.
#   Em builds release isso geralmente não causa crash, mas pode causar
#   warnings que valam APKs em lojas mais restritivas.
# ───────────────────────────────────────────────────────────────────────────

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
-dontwarn kotlin.reflect.**


# ───────────────────────────────────────────────────────────────────────────
# 8. JETPACK COMPOSE — NÃO NECESSÁRIO AQUI
#
# As regras de ProGuard do Compose são incluídas automaticamente via
# consumer-rules.pro dentro dos AARs do Compose. Adicionar regras
# manuais aqui causaria duplicação e potencial conflito.
# Mantido como comentário para documentar a decisão.
# ───────────────────────────────────────────────────────────────────────────

# (sem regras manuais para Compose — consumer rules cuidam disso)


# ───────────────────────────────────────────────────────────────────────────
# 9. SUPORTE A DEBUGGING EM PRODUÇÃO — RECOMENDADO
#
# Preserva nomes de classes e métodos no stack trace para que o
# Firebase Crashlytics (Fase 4) consiga desobfuscar automaticamente
# via o arquivo mapping.txt gerado pelo R8.
# ───────────────────────────────────────────────────────────────────────────

# Mantém informação de linha nos stack traces (custo: ~5% no tamanho do APK)
-keepattributes SourceFile,LineNumberTable

# Oculta o nome original do arquivo fonte no stack trace para dificultar
# engenharia reversa, mas mantém o número de linha (Crashlytics desobfusca)
-renamesourcefileattribute SourceFile

# Mantém assinaturas genéricas (necessário para que o Ktor possa
# inspecionar tipos parametrizados como List<Urgencia> em runtime)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions


# ───────────────────────────────────────────────────────────────────────────
# 10. SUPRESSÃO DE WARNINGS CONHECIDOS — MANUTENÇÃO
#
# Warnings de classes ausentes que NÃO fazem parte do APK final mas
# que o R8 tenta resolver ao analisar dependências transitivas.
# Suprimir warnings conhecidos impede falsos positivos no build log.
# ───────────────────────────────────────────────────────────────────────────

-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.Unsafe


# ───────────────────────────────────────────────────────────────────────────
# 11. STREAM VIDEO SDK (Fase 2.1) — CRÍTICO
#
# Por que quebra sem isso:
#   O Stream SDK usa reflection para carregar codecs de vídeo/áudio (WebRTC)
#   e para registrar listeners de estado de chamada. O R8 remove essas
#   classes internas que o SDK procura por nome em runtime.
# ───────────────────────────────────────────────────────────────────────────

-keep class io.getstream.** { *; }
-keep interface io.getstream.** { *; }
-dontwarn io.getstream.**

# WebRTC — engine de vídeo usado pelo Stream internamente
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**