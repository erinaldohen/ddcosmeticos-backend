package br.com.lojaddcosmeticos.ddcosmeticos_backend.services;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service // <--- ESSA ANOTAÇÃO É OBRIGATÓRIA PARA O ERRO SUMIR
public class AuthorizationService implements UserDetailsService {

    @Autowired
    private UsuarioRepository repository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // O "username" do Spring Security é a nossa "matricula"
        return repository.findByMatricula(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado com a matrícula: " + username));
    }
}