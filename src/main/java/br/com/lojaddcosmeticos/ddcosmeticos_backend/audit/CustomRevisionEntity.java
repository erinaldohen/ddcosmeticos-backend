package br.com.lojaddcosmeticos.ddcosmeticos_backend.audit;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario; // Supondo que tenha essa model
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.util.Date;

@Entity
@RevisionEntity(UserRevisionListener.class) // <--- Liga ao Listener abaixo
@Getter @Setter
@Table(name = "revinfo_custom")
public class CustomRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private int id;

    @RevisionTimestamp
    private long timestamp;

    private String usuarioResponsavel; // O nome do operador logado
}