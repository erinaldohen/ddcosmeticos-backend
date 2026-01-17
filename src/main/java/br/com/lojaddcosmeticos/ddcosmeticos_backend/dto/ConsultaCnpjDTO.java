package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ConsultaCnpjDTO(
        @JsonAlias("cnpj") String cnpj,
        @JsonAlias("razao_social") String razaoSocial,
        @JsonAlias("nome_fantasia") String nomeFantasia,
        @JsonAlias("descricao_situacao_cadastral") String situacao,
        @JsonAlias("cnae_fiscal_descricao") String atividadePrincipal,

        // Endereço mapeado da API
        @JsonAlias("logradouro") String logradouro,
        @JsonAlias("numero") String numero,
        @JsonAlias("complemento") String complemento,
        @JsonAlias("bairro") String bairro,
        @JsonAlias("municipio") String cidade,
        @JsonAlias("uf") String uf,
        @JsonAlias("cep") String cep,
        @JsonAlias("ddd_telefone_1") String telefone
) {}