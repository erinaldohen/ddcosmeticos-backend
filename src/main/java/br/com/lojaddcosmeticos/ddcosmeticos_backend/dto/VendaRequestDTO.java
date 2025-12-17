package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class VendaRequestDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "A venda deve ter pelo menos um item")
    private List<ItemVendaDTO> itens;

    @NotNull(message = "Informe a forma de pagamento")
    private FormaPagamento formaPagamento;

    private Integer quantidadeParcelas; // Pode ser nulo (assume 1)

    // Opcional: CPF na nota
    private String cpfCliente;
}