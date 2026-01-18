package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsultaCnpjDTO {

    @JsonAlias("cnpj")
    private String cnpj;

    @JsonAlias("razao_social")
    private String razaoSocial;

    @JsonAlias("nome_fantasia")
    private String nomeFantasia;

    @JsonAlias("descricao_situacao_cadastral")
    private String situacao;

    @JsonAlias("cnae_fiscal_descricao")
    private String atividadePrincipal;

    // Endereço mapeado da API
    @JsonAlias("logradouro")
    private String logradouro;

    @JsonAlias("numero")
    private String numero;

    @JsonAlias("email")
    private String email;

    @JsonAlias("complemento")
    private String complemento;

    @JsonAlias("bairro")
    private String bairro;

    @JsonAlias("municipio")
    private String municipio; // Alterado de 'cidade' para 'municipio' para bater com o Service

    @JsonAlias("uf")
    private String uf;

    @JsonAlias("cep")
    private String cep;

    @JsonAlias("ddd_telefone_1")
    private String telefone;
}