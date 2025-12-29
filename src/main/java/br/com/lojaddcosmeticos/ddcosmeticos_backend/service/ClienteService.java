package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ClienteDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public ClienteDTO salvar(ClienteDTO dto) {
        String docLimpo = dto.documento().replaceAll("\\D", "");

        if (dto.id() == null && repository.existsByDocumento(docLimpo)) {
            throw new ValidationException("Já existe um cliente cadastrado com este CPF/CNPJ.");
        }

        Cliente cliente = new Cliente();
        atualizarEntidade(cliente, dto);

        // Garante documento limpo na entidade
        cliente.setDocumento(docLimpo);

        if (cliente.getId() == null) {
            cliente.setDataCadastro(java.time.LocalDateTime.now());
        }

        return converterParaDTO(repository.save(cliente));
    }

    @Transactional
    public ClienteDTO atualizar(Long id, ClienteDTO dto) {
        Cliente cliente = repository.findById(id)
                .orElseThrow(() -> new ValidationException("Cliente não encontrado."));

        String docLimpo = dto.documento().replaceAll("\\D", "");

        if (!cliente.getDocumento().equals(docLimpo) && repository.existsByDocumento(docLimpo)) {
            throw new ValidationException("CPF/CNPJ já utilizado por outro cliente.");
        }

        atualizarEntidade(cliente, dto);
        cliente.setDocumento(docLimpo); // Garante atualização do doc

        return converterParaDTO(repository.save(cliente));
    }

    @Transactional
    public void alternarStatus(Long id) {
        Cliente cliente = repository.findById(id)
                .orElseThrow(() -> new ValidationException("Cliente não encontrado."));
        cliente.setAtivo(!cliente.isAtivo());
        repository.save(cliente);
    }

    private void atualizarEntidade(Cliente cliente, ClienteDTO dto) {
        cliente.setNome(dto.nome());
        cliente.setNomeFantasia(dto.nomeFantasia());
        cliente.setInscricaoEstadual(dto.inscricaoEstadual());
        cliente.setTelefone(dto.telefone());
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