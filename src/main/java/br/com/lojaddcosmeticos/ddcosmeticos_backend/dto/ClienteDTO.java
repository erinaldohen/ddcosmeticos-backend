package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClienteDTO(
        Long id,

        @NotBlank(message = "O nome/razão social é obrigatório")
        String nome,

        String nomeFantasia,

        // 🔥 NOVO: Essencial para o CRM dividir as abas (FISICA ou JURIDICA)
        String tipoPessoa,

        // A obrigatoriedade é decidida pelo Service (PF x PJ).
        String documento,

        String inscricaoEstadual,

        // Telefone agora é vital para a máquina de vendas (WhatsApp)
        String telefone,

        // Endereço desmembrado exigido para a Sefaz (NF-e/NFC-e)
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,

        // 🔥 MUDANÇA: Removidas as anotações @NotNull e @PositiveOrZero.
        // O limite de crédito não trava mais a edição do cadastro no frontend.
        BigDecimal limiteCredito,

        // Totalizador calculado pelo @Formula no banco de dados para o Ranking
        BigDecimal totalGasto,

        LocalDateTime dataCadastro,
        boolean ativo
) {}