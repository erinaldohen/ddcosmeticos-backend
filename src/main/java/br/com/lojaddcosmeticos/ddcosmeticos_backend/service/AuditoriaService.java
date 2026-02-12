package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoEvento;
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
import org.springframework.data.domain.Page;
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
import java.time.ZoneId;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String acao, String detalhes) {
        registrar(acao, detalhes, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String acao, String detalhes, String entidade, Long idEntidade) {
        TipoEvento tipoEventoEnum;
        try {
            tipoEventoEnum = TipoEvento.valueOf(acao.toUpperCase());
        } catch (Exception e) {
            tipoEventoEnum = TipoEvento.INFO;
        }
        registrar(tipoEventoEnum, detalhes, entidade, idEntidade);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(TipoEvento tipo, String detalhes, String entidade, Long idEntidade) {
        try {
            Auditoria auditoria = new Auditoria();
            auditoria.setDataHora(LocalDateTime.now());
            auditoria.setTipoEvento(tipo);
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
    // 2. CONSULTAS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<AuditoriaRequestDTO> listarUltimosEventos(int limite) {
        Pageable pageable = PageRequest.of(0, limite, Sort.by("dataHora").descending());

        return auditoriaRepository.findAll(pageable).stream()
                .map(auditoria -> new AuditoriaRequestDTO(
                        auditoria.getTipoEvento(),
                        auditoria.getMensagem(),
                        auditoria.getUsuarioResponsavel(),
                        auditoria.getDataHora()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<Auditoria> buscarFiltrado(String search, LocalDateTime inicio, LocalDateTime fim, Pageable pageable) {
        LocalDateTime dataInicio = (inicio != null) ? inicio : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime dataFim = (fim != null) ? fim : LocalDateTime.now().plusDays(1);
        String termoBusca = (search != null) ? search.toLowerCase() : "";

        return auditoriaRepository.buscarPorFiltros(termoBusca, dataInicio, dataFim, pageable);
    }

    // =========================================================================
    // 3. HIBERNATE ENVERS (CORRIGIDO)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> buscarHistoricoDoProduto(Long idProduto) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        List<Object[]> results = reader.createQuery()
                .forRevisionsOfEntity(Produto.class, false, true)
                .add(AuditEntity.id().eq(idProduto))
                .addOrder(AuditEntity.revisionNumber().desc())
                .getResultList();

        List<HistoricoProdutoDTO> historico = new ArrayList<>();

        for (Object[] row : results) {
            Produto p = (Produto) row[0];

            // Cast seguro para a entidade padrão de revisão do Envers
            org.hibernate.envers.DefaultRevisionEntity rev = (org.hibernate.envers.DefaultRevisionEntity) row[1];

            RevisionType type = (RevisionType) row[2];

            historico.add(new HistoricoProdutoDTO(
                    rev.getId(),                 // 1. Integer (ID da Revisão)
                    rev.getRevisionDate(),       // 2. Date (java.util.Date direto, sem converter)
                    type.name(),                 // 3. String (ADD, MOD, DEL)
                    p.getDescricao(),            // 4. String (Descrição)
                    p.getPrecoVenda(),           // 5. BigDecimal (Preço Venda)
                    p.getPrecoCusto(),           // 6. BigDecimal (Preço Custo - FALTAVA ESTE)
                    p.getQuantidadeEmEstoque()   // 7. Integer (Estoque)
            ));
        }
        return historico;
    }

    // =========================================================================
    // 4. RELATÓRIOS (PDF)
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

            logs.stream().limit(200).forEach(auditoria -> {
                table.addCell(auditoria.getDataHora().toString());
                table.addCell(auditoria.getTipoEvento() != null ? auditoria.getTipoEvento().name() : "N/A");
                table.addCell(auditoria.getMensagem() != null ? auditoria.getMensagem() : "");
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
    // 5. AUXILIARES
    // =========================================================================

    private String capturarNomeUsuarioLogado() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                if (auth.getPrincipal() instanceof String) {
                    return (String) auth.getPrincipal();
                } else if (auth.getPrincipal() instanceof Usuario) {
                    return ((Usuario) auth.getPrincipal()).getNome();
                }
            }
        } catch (Exception e) {
            log.warn("Usuario anonimo ou erro na auth");
        }
        return "Sistema";
    }
}