package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Ibpt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IbptRepository extends JpaRepository<Ibpt, String> {
    @Query("SELECT i FROM Ibpt i WHERE i.codigo = :ncm")
    Optional<Ibpt> findByNcm(String ncm);
}