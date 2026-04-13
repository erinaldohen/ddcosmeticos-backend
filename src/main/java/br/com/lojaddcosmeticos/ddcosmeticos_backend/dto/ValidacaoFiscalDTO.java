package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;

@Data
public class ValidacaoFiscalDTO {
    private String ncm;
    private String cest;
    private String cst;
    private boolean monofasico;
    private boolean impostoSeletivo;
}