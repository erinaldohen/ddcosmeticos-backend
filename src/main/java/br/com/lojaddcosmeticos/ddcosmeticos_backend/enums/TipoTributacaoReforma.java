package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

public enum TipoTributacaoReforma {
    PADRAO,             // Alíquota Cheia (Perfumes, Maquiagem)
    REDUZIDA_60,        // Desconto de 60% (Higiene Pessoal, Limpeza)
    REDUZIDA_30,        // Desconto de 30% (Profissionais de Saúde, etc - Menos comum no varejo)
    CESTA_BASICA,       // Alíquota Zero (Sabonetes, Absorventes, Itens Essenciais)
    IMPOSTO_SELETIVO,   // "Imposto do Pecado" (Produtos Nocivos/Prejudiciais)
    IMUNE               // Livros, Exportação
}