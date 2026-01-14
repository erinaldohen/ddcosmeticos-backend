package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.audit.CustomRevisionEntity;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuditoriaService {

    @PersistenceContext private EntityManager entityManager;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    // =========================================================================
    // 1. REGISTRO DE EVENTOS
    // =========================================================================

    // Método Simples (Chamado pelo VendaService, etc.)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String acao, String detalhes) {
        registrar(acao, detalhes, null, null);
    }

    // Método Completo (Com entidade e ID)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String acao, String detalhes, String entidade, Long idEntidade) {
        try {
            Auditoria auditoria = new Auditoria();
            auditoria.setDataHora(LocalDateTime.now());
            auditoria.setTipoEvento(acao);
            auditoria.setMensagem(detalhes);
            auditoria.setEntidadeAfetada(entidade);
            auditoria.setIdEntidadeAfetada(idEntidade);
            auditoria.setUsuarioResponsavel(capturarNomeUsuarioLogado());

            auditoriaRepository.save(auditoria);
        } catch (Exception e) {
            log.error("Falha ao registrar auditoria: {}", e.getMessage());
        }
    }

    // =========================================================================
    // 2. CONSULTAS PARA O DASHBOARD
    // =========================================================================

    @Transactional(readOnly = true)
    public List<AuditoriaRequestDTO> listarUltimasAlteracoes(int limite) {
        // Cria a paginação
        Pageable pageable = PageRequest.of(0, limite, Sort.by("dataHora").descending());

        // O erro estava aqui: O Java estava confuso sobre qual DTO retornar.
        // Forçamos o retorno correto do map().
        return auditoriaRepository.findAll(pageable).stream()
                .map(auditoria -> new AuditoriaRequestDTO(
                        auditoria.getTipoEvento(),
                        auditoria.getMensagem(),
                        auditoria.getUsuarioResponsavel(),
                        auditoria.getDataHora()
                ))
                .collect(Collectors.toList());
    }

    // Converte a Entidade Auditoria para o seu Record AuditoriaRequestDTO
    private AuditoriaRequestDTO converterParaDTO(Auditoria auditoria) {
        return new AuditoriaRequestDTO(
                auditoria.getTipoEvento(),        // Mapeia para 'acao'
                auditoria.getMensagem(),          // Mapeia para 'detalhes'
                auditoria.getUsuarioResponsavel(),// Mapeia para 'usuario'
                auditoria.getDataHora()           // Mapeia para 'dataHora'
        );
    }

    // =========================================================================
    // 3. RELATÓRIOS (PDF)
    // =========================================================================

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioMensalPDF() {
        List<Auditoria> logs = auditoriaRepository.findAllByOrderByDataHoraDesc();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph("DD Cosméticos - Relatório de Segurança").setBold().setFontSize(16));
            document.add(new Paragraph("Gerado em: " + LocalDateTime.now()));

            Table table = new Table(new float[]{3, 3, 5});
            table.addHeaderCell("Data/Hora");
            table.addHeaderCell("Evento");
            table.addHeaderCell("Detalhes");

            logs.stream().limit(200).forEach(l -> {
                table.addCell(l.getDataHora().toString());
                table.addCell(l.getTipoEvento());
                table.addCell(l.getMensagem());
            });

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erro ao gerar PDF de auditoria", e);
            return new byte[0];
        }
    }

    // =========================================================================
    // 4. HIBERNATE ENVERS (Histórico de Alterações de Produtos)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> buscarHistoricoDoProduto(Long idProduto) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        // Busca revisões onde o ID do produto é igual ao solicitado
        List<Object[]> results = reader.createQuery()
                .forRevisionsOfEntity(Produto.class, false, true)
                .add(AuditEntity.id().eq(idProduto))
                .addOrder(AuditEntity.revisionNumber().desc())
                .getResultList();

        List<HistoricoProdutoDTO> historico = new ArrayList<>();

        for (Object[] row : results) {
            Produto p = (Produto) row[0];
            CustomRevisionEntity rev = (CustomRevisionEntity) row[1];
            RevisionType type = (RevisionType) row[2];

            // Certifique-se que o HistoricoProdutoDTO tem este construtor
            historico.add(new HistoricoProdutoDTO(
                    rev.getId(),
                    new Date(rev.getTimestamp()),
                    type.name(), // CRIADO, ALTERADO, DELETADO
                    p.getDescricao(),
                    p.getPrecoVenda(),
                    p.getPrecoCusto(),
                    p.getQuantidadeEmEstoque()
            ));
        }
        return historico;
    }

    // =========================================================================
    // 5. LIXEIRA E RESTAURAÇÃO
    // =========================================================================

    public List<Produto> buscarLixeira() {
        return produtoRepository.findAllLixeira();
    }

    @Transactional
    public void restaurarProduto(Long id) {
        produtoRepository.reativarProduto(id);
        registrar("RESTAURACAO_PRODUTO", "Produto ID " + id + " restaurado da lixeira.");
    }

    // =========================================================================
    // 6. AUXILIARES
    // =========================================================================

    private String capturarNomeUsuarioLogado() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                if (auth.getPrincipal() instanceof String) { // Token JWT geralmente retorna String (matricula/email)
                    return usuarioRepository.findByMatricula((String) auth.getPrincipal())
                            .map(Usuario::getNome)
                            .orElse((String) auth.getPrincipal());
                } else if (auth.getPrincipal() instanceof Usuario) {
                    return ((Usuario) auth.getPrincipal()).getNome();
                }
            }
        } catch (Exception e) {
            log.warn("Não foi possível identificar o usuário para auditoria.");
        }
        return "Sistema";
    }
}