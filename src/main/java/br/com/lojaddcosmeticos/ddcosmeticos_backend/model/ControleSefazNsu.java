package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@Table(name = "controle_sefaz_nsu")
public class ControleSefazNsu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cnpj_empresa", length = 14, nullable = false, unique = true)
    private String cnpjEmpresa;

    @Column(name = "ultimo_nsu", length = 20, nullable = false)
    private String ultimoNsu = "0";

    // Construtor extra usado lá no SefazDistribuicaoService
    public ControleSefazNsu(String cnpjEmpresa, String ultimoNsu) {
        this.cnpjEmpresa = cnpjEmpresa;
        this.ultimoNsu = ultimoNsu;
    }
}