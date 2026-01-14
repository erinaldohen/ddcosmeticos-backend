package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.audit.CustomRevisionEntity;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class AuditoriaService {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // --- CORREÇÃO: Método que recebe Strings do Controller e salva na Entidade ---
    public void registrar(String acao, String detalhes) {
        Auditoria auditoria = new Auditoria();
        auditoria.setDataHora(LocalDateTime.now());

        // Mapeamento correto para a Entidade Auditoria
        // O Front manda "acao", a Entidade espera "tipoEvento"
        auditoria.setTipoEvento(acao != null ? acao : "EVENTO_DESCONHECIDO");

        // O Front manda "detalhes", a Entidade espera "mensagem"
        auditoria.setMensagem(detalhes != null ? detalhes : "Sem detalhes");

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String nomeUsuario = "Sistema/Anônimo";

            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                if (auth.getPrincipal() instanceof Usuario) {
                    nomeUsuario = ((Usuario) auth.getPrincipal()).getNome();
                } else if (auth.getPrincipal() instanceof String) {
                    String principal = (String) auth.getPrincipal();
                    nomeUsuario = usuarioRepository.findByMatricula(principal)
                            .map(Usuario::getNome)
                            .orElse(principal);
                }
            }
            auditoria.setUsuarioResponsavel(nomeUsuario);

        } catch (Exception e) {
            auditoria.setUsuarioResponsavel("Erro Auth");
        }

        auditoriaRepository.save(auditoria);
    }

    // ... (Mantenha os outros métodos: buscarHistoricoDoProduto, listarUltimasAlteracoes, etc.)

    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> buscarHistoricoDoProduto(Long idProduto) {
        return executarQueryAuditoria(idProduto, 0);
    }

    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> listarUltimasAlteracoes(int limite) {
        return executarQueryAuditoria(null, limite);
    }

    private List<HistoricoProdutoDTO> executarQueryAuditoria(Long idProduto, int limite) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        var query = reader.createQuery()
                .forRevisionsOfEntity(Produto.class, false, true)
                .addOrder(AuditEntity.revisionNumber().desc());

        if (idProduto != null) query.add(AuditEntity.id().eq(idProduto));
        if (limite > 0) query.setMaxResults(limite);

        List<Object[]> results = query.getResultList();
        List<HistoricoProdutoDTO> historico = new ArrayList<>();

        for (Object[] row : results) {
            Produto produtoAntigo = (Produto) row[0];
            CustomRevisionEntity revisionEntity = (CustomRevisionEntity) row[1];
            RevisionType revisionType = (RevisionType) row[2];

            String tipo = switch (revisionType) {
                case ADD -> "CRIADO";
                case MOD -> "ALTERADO";
                case DEL -> "EXCLUÍDO";
            };

            historico.add(new HistoricoProdutoDTO(
                    revisionEntity.getId(),
                    new Date(revisionEntity.getTimestamp()),
                    tipo,
                    produtoAntigo.getDescricao(),
                    produtoAntigo.getPrecoVenda(),
                    produtoAntigo.getPrecoCusto(),
                    produtoAntigo.getQuantidadeEmEstoque()
            ));
        }
        return historico;
    }

    public List<Produto> buscarLixeira() {
        return produtoRepository.findAllLixeira();
    }

    @Transactional
    public void restaurarProduto(Long id) {
        produtoRepository.reativarProduto(id);
    }

    public void registrarEvento(Auditoria auditoria) {
        auditoriaRepository.save(auditoria);
    }
}