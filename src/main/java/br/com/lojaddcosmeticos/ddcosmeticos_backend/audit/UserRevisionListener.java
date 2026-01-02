package br.com.lojaddcosmeticos.ddcosmeticos_backend.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        CustomRevisionEntity rev = (CustomRevisionEntity) revisionEntity;

        // Pega o usuário logado do Spring Security
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null) {
            rev.setUsuarioResponsavel(auth.getName()); // Salva o login/email
        } else {
            rev.setUsuarioResponsavel("Sistema/Anônimo");
        }
    }
}