package br.com.lojaddcosmeticos.ddcosmeticos_backend.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TipoEmissao {

    NORMAL("1", "Emissão Normal"),
    OFFLINE("9", "Contingência Offline NFC-e");

    private final String codigo;
    private final String descricao;
}