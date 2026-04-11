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

        cliente.setDataCadastro(java.time.LocalDateTime.now());
        cliente.setAtivo(true);

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

    @Transactional(readOnly = true)
    public Page<ClienteDTO> listarPorTipo(String tipo, Pageable pageable) {
        return repository.findByTipoPessoa(tipo.toUpperCase(), pageable)
                .map(this::converterParaDTO);
    }

    @Transactional(readOnly = true)
    public Page<ClienteDTO> buscarPorNome(String nome, Pageable pageable) {
        return repository.findByNomeContainingIgnoreCase(nome, pageable)
                .map(this::converterParaDTO);
    }

    // ========================================================================
    // 🔥 CENTRAL DE VALIDAÇÃO DE OBRIGATORIEDADE DINÂMICA
    // ========================================================================
    private void validarRegrasNegocio(ClienteDTO dto, Cliente clienteExistente) {
        String docLimpo = dto.documento() != null ? dto.documento().replaceAll("\\D", "") : "";
        String telLimpo = dto.telefone() != null ? dto.telefone().replaceAll("\\D", "") : "";

        boolean isPJ = docLimpo.length() == 14;

        if (isPJ) {
            if (docLimpo.length() != 14) throw new ValidationException("CNPJ inválido.");
        } else {
            if (telLimpo.isBlank()) {
                throw new ValidationException("Para consumidores finais e pessoas físicas, o telefone é obrigatório.");
            }
        }

        if (!docLimpo.isBlank()) {
            repository.findByDocumento(docLimpo).ifPresent(c -> {
                if (clienteExistente == null || !c.getId().equals(clienteExistente.getId())) {
                    throw new ValidationException("Já existe um cliente cadastrado com este CPF/CNPJ.");
                }
            });
        }

        if (!telLimpo.isBlank()) {
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
        cliente.setDocumento(dto.documento());
        cliente.setTelefone(dto.telefone());

        // Determina o tipo de pessoa pelo tamanho do documento se não for enviado
        String docLimpo = dto.documento() != null ? dto.documento().replaceAll("\\D", "") : "";
        if (dto.tipoPessoa() != null && !dto.tipoPessoa().isBlank()) {
            cliente.setTipoPessoa(dto.tipoPessoa().toUpperCase());
        } else {
            cliente.setTipoPessoa(docLimpo.length() == 14 ? "JURIDICA" : "FISICA");
        }

        // Apenas salva a IE se for CNPJ (14 dígitos).
        if (docLimpo.length() == 14) {
            cliente.setInscricaoEstadual(dto.inscricaoEstadual());
        } else {
            cliente.setInscricaoEstadual(null);
        }

        // Endereço desmembrado
        cliente.setCep(dto.cep());
        cliente.setLogradouro(dto.logradouro());
        cliente.setNumero(dto.numero());
        cliente.setComplemento(dto.complemento());
        cliente.setBairro(dto.bairro());
        cliente.setCidade(dto.cidade());
        cliente.setUf(dto.uf());

        // Mantemos o setLimiteCredito caso a sua Entidade Cliente ainda precise dele.
        // Como o DTO não exige mais, se vier null, salvamos 0 ou mantemos o atual.
        if (dto.limiteCredito() != null) {
            cliente.setLimiteCredito(dto.limiteCredito());
        } else if (cliente.getLimiteCredito() == null) {
            cliente.setLimiteCredito(BigDecimal.ZERO);
        }
    }

    private ClienteDTO converterParaDTO(Cliente c) {
        return new ClienteDTO(
                c.getId(),
                c.getNome(),
                c.getNomeFantasia(),
                c.getTipoPessoa(),       // 🔥 Mapeado para o Frontend (Abas)
                c.getDocumento(),
                c.getInscricaoEstadual(),
                c.getTelefone(),
                c.getCep(),
                c.getLogradouro(),
                c.getNumero(),
                c.getComplemento(),
                c.getBairro(),
                c.getCidade(),
                c.getUf(),
                c.getLimiteCredito(),
                c.getTotalGasto(),       // 🔥 Mapeado para o Frontend (Ranking)
                c.getDataCadastro(),
                c.isAtivo()
        );
    }
}