package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {}