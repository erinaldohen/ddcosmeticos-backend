package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
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
    private ProdutoRepository produtoRepository;

    // 1. Time Travel: Busca todas as versões de um produto
    // ... imports ...

    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> buscarHistoricoDoProduto(Long idProduto) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        List<Object[]> results = reader.createQuery()
                .forRevisionsOfEntity(Produto.class, false, true)
                .add(AuditEntity.id().eq(idProduto))
                .getResultList();

        List<HistoricoProdutoDTO> historico = new ArrayList<>();

        for (Object[] row : results) {
            Produto produtoAntigo = (Produto) row[0];
            org.hibernate.envers.DefaultRevisionEntity revisionEntity = (org.hibernate.envers.DefaultRevisionEntity) row[1];
            RevisionType revisionType = (RevisionType) row[2];

            String tipo = switch (revisionType) {
                case ADD -> "CRIADO";
                case MOD -> "ALTERADO";
                case DEL -> "EXCLUÍDO";
            };

            // AQUI ESTAVA O ERRO: Certifique-se de passar os 7 parâmetros na ordem certa
            historico.add(new HistoricoProdutoDTO(
                    revisionEntity.getId(),                     // 1. ID da Revisão (Integer)
                    new Date(revisionEntity.getTimestamp()),    // 2. Data (Date)
                    tipo,                                       // 3. Tipo (String)
                    produtoAntigo.getDescricao(),               // 4. Nome (String)
                    produtoAntigo.getPrecoVenda(),              // 5. Preço Venda (BigDecimal)
                    produtoAntigo.getPrecoCusto(),              // 6. Preço Custo (BigDecimal)
                    produtoAntigo.getQuantidadeEmEstoque()      // 7. Estoque (Integer)
            ));
        }

        historico.sort((a, b) -> b.getDataAlteracao().compareTo(a.getDataAlteracao()));

        return historico;
    }

    // 2. Lixeira: Lista produtos deletados
    public List<Produto> buscarLixeira() {
        return produtoRepository.findAllLixeira();
    }

    // 3. Restaurar: Tira da lixeira
    @Transactional
    public void restaurarProduto(Long id) {
        produtoRepository.reativarProduto(id);
    }
}