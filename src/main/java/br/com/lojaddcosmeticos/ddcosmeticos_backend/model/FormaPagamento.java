package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

public enum FormaPagamento {
    DINHEIRO,
    PIX,
    DEBITO,
    CREDITO, // Parcelado
    BOLETO   // Prazo único ou parcelado
}