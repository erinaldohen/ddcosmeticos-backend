package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@Table(name = "nota_pendente_importacao")
public class NotaPendenteImportacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chave_acesso", length = 44, nullable = false, unique = true)
    private String chaveAcesso;

    @Column(nullable = false, length = 20)
    private String nsu;

    @Column(name = "cnpj_fornecedor", length = 14)
    private String cnpjFornecedor;

    @Column(name = "nome_fornecedor")
    private String nomeFornecedor;

    @Column(name = "valor_total", precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "xml_completo", columnDefinition = "TEXT")
    private String xmlCompleto;

    // Guardará os status: "PENDENTE_MANIFESTACAO", "PRONTO_IMPORTACAO" ou "IMPORTADO"
    @Column(length = 50)
    private String status = "PENDENTE_MANIFESTACAO";

    @Column(name = "data_emissao")
    private LocalDateTime dataEmissao;

    @Column(name = "data_captura", updatable = false)
    private LocalDateTime dataCaptura = LocalDateTime.now();
}