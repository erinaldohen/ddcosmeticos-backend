package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.audit.CustomRevisionEntity; // [IMPORTANTE] Import da entidade customizada
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // --- 1. Time Travel: Busca histórico de UM produto específico ---
    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> buscarHistoricoDoProduto(Long idProduto) {
        return executarQueryAuditoria(idProduto, 0); // 0 = sem limite
    }

    // --- 2. Dashboard: Busca as ÚLTIMAS alterações de QUALQUER produto ---
    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> listarUltimasAlteracoes(int limite) {
        return executarQueryAuditoria(null, limite);
    }

    // --- LÓGICA CENTRAL DE AUDITORIA (Reutilizável) ---
    private List<HistoricoProdutoDTO> executarQueryAuditoria(Long idProduto, int limite) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        var query = reader.createQuery()
                .forRevisionsOfEntity(Produto.class, false, true)
                .addOrder(AuditEntity.revisionNumber().desc()); // Mais recentes primeiro

        // Filtra por ID se fornecido
        if (idProduto != null) {
            query.add(AuditEntity.id().eq(idProduto));
        }

        // Aplica limite se fornecido (para o Dashboard)
        if (limite > 0) {
            query.setMaxResults(limite);
        }

        List<Object[]> results = query.getResultList();
        List<HistoricoProdutoDTO> historico = new ArrayList<>();

        for (Object[] row : results) {
            Produto produtoAntigo = (Produto) row[0];

            // [CORREÇÃO] Aqui estava o erro.
            // O Hibernate retorna a sua CustomRevisionEntity, não a Default.
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

    // --- 3. Lixeira: Lista produtos deletados ---
    public List<Produto> buscarLixeira() {
        return produtoRepository.findAllLixeira();
    }

    // --- 4. Restaurar: Tira da lixeira ---
    @Transactional
    public void restaurarProduto(Long id) {
        produtoRepository.reativarProduto(id);
    }

    public void registrarEvento(Auditoria auditoria) {
        auditoriaRepository.save(auditoria);
    }
}