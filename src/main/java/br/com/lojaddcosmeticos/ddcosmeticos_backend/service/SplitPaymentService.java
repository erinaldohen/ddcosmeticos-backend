package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SplitPaymentInstructionDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoDestinatarioSplit;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class SplitPaymentService {

    public List<SplitPaymentInstructionDTO> gerarInstrucoesSplit(Venda venda) {
        BigDecimal totalIBS = BigDecimal.ZERO;
        BigDecimal totalCBS = BigDecimal.ZERO;
        BigDecimal totalSeletivo = BigDecimal.ZERO;
        BigDecimal totalVenda = venda.getTotalVenda().subtract(venda.getDescontoTotal());

        for (ItemVenda item : venda.getItens()) {
            BigDecimal valorItem = item.getTotalItem();
            totalIBS = totalIBS.add(valorItem.multiply(item.getAliquotaIbsAplicada()));
            totalCBS = totalCBS.add(valorItem.multiply(item.getAliquotaCbsAplicada()));
            totalSeletivo = totalSeletivo.add(item.getValorImpostoSeletivo());
        }

        List<SplitPaymentInstructionDTO> instrucoes = new ArrayList<>();

        // 1. Instrução para o IBS (Estado/Município) usando o Enum
        instrucoes.add(new SplitPaymentInstructionDTO(
                TipoDestinatarioSplit.ESTADO_MUNICIPIO, totalIBS, "CHAVE_PIX_ESTADUAL"));

        // 2. Instrução para a União usando o Enum
        instrucoes.add(new SplitPaymentInstructionDTO(
                TipoDestinatarioSplit.UNIAO_FEDERAL, totalCBS.add(totalSeletivo), "CHAVE_PIX_FEDERAL"));

        // 3. Instrução para o Lojista usando o Enum
        BigDecimal valorLojista = totalVenda.subtract(totalIBS).subtract(totalCBS).subtract(totalSeletivo);
        instrucoes.add(new SplitPaymentInstructionDTO(
                TipoDestinatarioSplit.LOJISTA, valorLojista, venda.getUsuario().getMatricula()));

        return instrucoes;
    }
}