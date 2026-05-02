package br.com.brasiltupi.conecta

fun ProfissionalComPerfil.toProfissionalPMP(): ProfissionalPMP {
    val nomeProf = this.perfis?.nome ?: "Profissional"
    return ProfissionalPMP(
        id                = this.id.hashCode(),
        supabaseId        = this.id,
        iniciais          = nomeProf.split(" ").map { it[0] }.joinToString("").take(2).uppercase(),
        nome              = nomeProf,
        area              = this.area,
        cidade            = buildCidade(this.perfis?.cidade, this.perfis?.estado),
        // Avaliação real vinda do banco (média das notas). Se ainda não houver avaliações, exibe 0.0.
        avaliacao         = this.avaliacao_media ?: 0.0,
        atendimentos      = this.credibilidade / 2,
        disponivelUrgente = this.disponivel_urgente,
        valorNormal       = this.valor_normal,
        valorUrgente      = this.valor_urgente,
        conselho          = listOfNotNull(this.conselho, this.numero_conselho).joinToString(" "),
        descricao         = this.descricao.orEmpty(),
        especialidades    = listOf(this.area),
    )
}

private fun buildCidade(cidade: String?, estado: String?): String = when {
    !cidade.isNullOrEmpty() && !estado.isNullOrEmpty() -> "$cidade, $estado"
    !cidade.isNullOrEmpty()                            -> cidade
    else                                               -> ""
}