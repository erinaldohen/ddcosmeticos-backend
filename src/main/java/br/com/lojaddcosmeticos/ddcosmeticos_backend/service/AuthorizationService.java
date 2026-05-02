package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService implements UserDetailsService {

    @Autowired
    UsuarioRepository repository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ✅ CORREÇÃO: Respeita o retorno Optional do Repository e lança o erro nativo do Spring Security se falhar
        return repository.findByMatriculaOrEmail(username, username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado com e-mail/matrícula: " + username));
    }
}