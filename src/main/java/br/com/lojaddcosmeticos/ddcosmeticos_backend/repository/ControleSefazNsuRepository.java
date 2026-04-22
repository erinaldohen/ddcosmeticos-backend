package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ControleSefazNsu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ControleSefazNsuRepository extends JpaRepository<ControleSefazNsu, Long> {
    Optional<ControleSefazNsu> findByCnpjEmpresa(String cnpjEmpresa);
}