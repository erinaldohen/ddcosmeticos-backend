package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PagamentoVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.RegraNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Service
public class ValidacaoRegrasNegocioService {

    @Autowired
    private ConfiguracaoLojaRepository configuracaoRepository;

    public void validarFechamentoDeVenda(Venda venda, List<ItemVenda> itens, List<PagamentoVenda> pagamentos) {
        ConfiguracaoLoja config = configuracaoRepository.findById(1L)
                .orElseThrow(() -> new RegraNegocioException("Configurações do sistema não encontradas."));

        if (config.getLoja() != null && Boolean.TRUE.equals(config.getLoja().getBloqueioForaHorario())) {
            LocalTime agora = LocalTime.now();
            LocalTime abre = config.getLoja().getHorarioAbre();
            LocalTime fecha = config.getLoja().getHorarioFecha();

            if (abre != null && fecha != null) {
                int tolerancia = config.getLoja().getToleranciaMinutos() != null ? config.getLoja().getToleranciaMinutos() : 0;
                LocalTime limiteFechamento = fecha.plusMinutes(tolerancia);

                if (agora.isBefore(abre) || agora.isAfter(limiteFechamento)) {
                    throw new RegraNegocioException("Sistema bloqueado: Fora do horário de expediente comercial.");
                }
            }
        }

        if (config.getVendas() != null && "SEMPRE".equals(config.getVendas().getComportamentoCpf()) && venda.getCliente() == null) {
            throw new RegraNegocioException("Configuração da loja exige identificação do cliente (CPF) para concluir a venda.");
        }

        if (itens != null) {
            for (ItemVenda item : itens) {
                BigDecimal precoPraticado = item.getPrecoUnitario().subtract(item.getDesconto() != null ? item.getDesconto() : BigDecimal.ZERO);

                if (config.getFinanceiro() != null && Boolean.TRUE.equals(config.getFinanceiro().getBloquearAbaixoCusto()) && item.getProduto().getPrecoCusto() != null) {
                    if (precoPraticado.compareTo(item.getProduto().getPrecoCusto()) < 0) {
                        throw new RegraNegocioException("Bloqueio de Preço: O produto " + item.getProduto().getDescricao() + " está a ser vendido abaixo do custo.");
                    }
                }

                if (config.getVendas() != null && Boolean.TRUE.equals(config.getVendas().getBloquearEstoque())) {
                    Integer estoqueAtualInteiro = item.getProduto().getQuantidadeEmEstoque() != null ? item.getProduto().getQuantidadeEmEstoque() : 0;
                    BigDecimal estoqueAtual = new BigDecimal(estoqueAtualInteiro);
                    BigDecimal estoqueProjetado = estoqueAtual.subtract(item.getQuantidade());

                    if (estoqueProjetado.compareTo(BigDecimal.ZERO) < 0) {
                        throw new RegraNegocioException("Estoque Insuficiente para o produto: " + item.getProduto().getDescricao());
                    }
                }
            }
        }

        if (pagamentos != null && config.getFinanceiro() != null) {
            BigDecimal descontoGlobal = venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO;

            for (PagamentoVenda pgto : pagamentos) {
                if (pgto.getFormaPagamento() == null) continue;

                switch (pgto.getFormaPagamento()) {
                    case PIX:
                        if (!Boolean.TRUE.equals(config.getFinanceiro().getAceitaPix())) throw new RegraNegocioException("Pagamento via PIX está desativado na loja.");
                        break;
                    case DINHEIRO:
                        if (!Boolean.TRUE.equals(config.getFinanceiro().getAceitaDinheiro())) throw new RegraNegocioException("Pagamento em Dinheiro está desativado na loja.");
                        break;
                    case CREDIARIO:
                        if (!Boolean.TRUE.equals(config.getFinanceiro().getAceitaCrediario())) throw new RegraNegocioException("Vendas no Fiado/Crediário estão desativadas.");
                        break;
                    case CARTAO_CREDITO:
                        if (!Boolean.TRUE.equals(config.getFinanceiro().getAceitaCredito())) throw new RegraNegocioException("Pagamento no Crédito está desativado.");
                        break;
                    case CARTAO_DEBITO:
                        if (!Boolean.TRUE.equals(config.getFinanceiro().getAceitaDebito())) throw new RegraNegocioException("Pagamento no Débito está desativado.");
                        break;
                    default:
                        break;
                }

                if (descontoGlobal.compareTo(BigDecimal.ZERO) > 0 && pgto.getFormaPagamento() != FormaDePagamento.PIX) {
                    if (Boolean.FALSE.equals(config.getFinanceiro().getDescExtraPix())) {

                    }
                }
            }
        }
    }

    public void validarCancelamento(String cargoUsuarioOperador) {
        ConfiguracaoLoja config = configuracaoRepository.findById(1L)
                .orElseThrow(() -> new RegraNegocioException("Configurações do sistema não encontradas."));

        if (config.getSistema() != null && Boolean.TRUE.equals(config.getSistema().getSenhaGerenteCancelamento())) {
            if (cargoUsuarioOperador == null || (!cargoUsuarioOperador.contains("ROLE_ADMIN") && !cargoUsuarioOperador.contains("ROLE_GERENTE"))) {
                throw new RegraNegocioException("Cancelamento negado. Necessária autorização de um Gerente ou Administrador.");
            }
        }
    }
}