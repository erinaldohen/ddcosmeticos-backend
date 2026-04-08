package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AnaliseCreditoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ClienteDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository repository;

    @Transactional(readOnly = true)
    public Page<ClienteDTO> listar(String termo, Pageable pageable) {
        return repository.findAll(pageable).map(this::converterParaDTO);
    }

    @Transactional(readOnly = true)
    public ClienteDTO buscarPorId(Long id) {
        return repository.findById(id)
                .map(this::converterParaDTO)
                .orElseThrow(() -> new ValidationException("Cliente não encontrado."));
    }

    @Transactional(readOnly = true)
    public ClienteDTO buscarPorDocumento(String doc) {
        String docLimpo = doc.replaceAll("\\D", "");
        return repository.findByDocumento(docLimpo)
                .map(this::converterParaDTO)
                .orElseThrow(() -> new ValidationException("Cliente não encontrado para o documento: " + doc));
    }

    // 🔥 MUDANÇA: O PDV manda um número (CPF, CNPJ ou Telefone). Nós achamos quem é!
    @Transactional(readOnly = true)
    public AnaliseCreditoDTO analisarCredito(String identificador) {
        String limpo = identificador.replaceAll("\\D", "");

        Cliente cliente = repository.findByDocumento(limpo)
                .orElseGet(() -> repository.findByTelefone(limpo)
                        .orElseThrow(() -> new ValidationException("Cliente não encontrado. É necessário cadastrar primeiro.")));

        if (!cliente.isAtivo()) {
            return new AnaliseCreditoDTO(true, "O cadastro deste cliente encontra-se desativado/bloqueado no sistema.", BigDecimal.ZERO);
        }

        BigDecimal debitosVencidos = BigDecimal.ZERO;
        if (debitosVencidos != null && debitosVencidos.compareTo(BigDecimal.ZERO) > 0) {
            return new AnaliseCreditoDTO(true, "O cliente possui faturas do crediário vencidas e não pagas.", debitosVencidos);
        }

        return new AnaliseCreditoDTO(false, "Crédito aprovado. Cliente sem restrições.", BigDecimal.ZERO);
    }

    @Transactional
    public ClienteDTO salvar(ClienteDTO dto) {
        validarRegrasNegocio(dto, null);

        Cliente cliente = new Cliente();
        atualizarEntidade(cliente, dto);

        if (cliente.getId() == null) {
            cliente.setDataCadastro(java.time.LocalDateTime.now());
        }

        return converterParaDTO(repository.save(cliente));
    }

    @Transactional
    public ClienteDTO atualizar(Long id, ClienteDTO dto) {
        Cliente cliente = repository.findById(id)
                .orElseThrow(() -> new ValidationException("Cliente não encontrado."));

        validarRegrasNegocio(dto, cliente);
        atualizarEntidade(cliente, dto);

        return converterParaDTO(repository.save(cliente));
    }

    @Transactional
    public void alternarStatus(Long id) {
        Cliente cliente = repository.findById(id)
                .orElseThrow(() -> new ValidationException("Cliente não encontrado."));
        cliente.setAtivo(!cliente.isAtivo());
        repository.save(cliente);
    }

    // ========================================================================
    // 🔥 MUDANÇA: CENTRAL DE VALIDAÇÃO DE OBRIGATORIEDADE DINÂMICA
    // ========================================================================
    private void validarRegrasNegocio(ClienteDTO dto, Cliente clienteExistente) {
        String docLimpo = dto.documento() != null && !dto.documento().isBlank() ? dto.documento().replaceAll("\\D", "") : null;
        String telLimpo = dto.telefone() != null && !dto.telefone().isBlank() ? dto.telefone().replaceAll("\\D", "") : null;

        boolean isPJ = docLimpo != null && docLimpo.length() > 11;

        if (isPJ) {
            if (docLimpo.length() != 14) throw new ValidationException("CNPJ inválido.");
        } else {
            // É Pessoa Física (CPF preenchido ou vazio)
            if (telLimpo == null || telLimpo.isBlank()) {
                throw new ValidationException("Para consumidores finais e pessoas físicas, o telefone é obrigatório.");
            }
        }

        // Verifica CPF/CNPJ Duplicado
        if (docLimpo != null) {
            repository.findByDocumento(docLimpo).ifPresent(c -> {
                if (clienteExistente == null || !c.getId().equals(clienteExistente.getId())) {
                    throw new ValidationException("Já existe um cliente cadastrado com este CPF/CNPJ.");
                }
            });
        }

        // Verifica Telefone Duplicado
        if (telLimpo != null) {
            repository.findByTelefone(telLimpo).ifPresent(c -> {
                if (clienteExistente == null || !c.getId().equals(clienteExistente.getId())) {
                    throw new ValidationException("Já existe um cliente com este Telefone cadastrado.");
                }
            });
        }
    }

    private void atualizarEntidade(Cliente cliente, ClienteDTO dto) {
        cliente.setNome(dto.nome());
        cliente.setNomeFantasia(dto.nomeFantasia());
        cliente.setDocumento(dto.documento()); // O @PrePersist vai cuidar da limpeza
        cliente.setInscricaoEstadual(dto.inscricaoEstadual());
        cliente.setTelefone(dto.telefone()); // O @PrePersist vai cuidar da limpeza
        cliente.setEndereco(dto.endereco());
        cliente.setLimiteCredito(dto.limiteCredito());
    }

    private ClienteDTO converterParaDTO(Cliente c) {
        return new ClienteDTO(
                c.getId(),
                c.getNome(),
                c.getNomeFantasia(),
                c.getDocumento(),
                c.getInscricaoEstadual(),
                c.getTelefone(),
                c.getEndereco(),
                c.getLimiteCredito(),
                c.getDataCadastro(),
                c.isAtivo()
        );
    }
}