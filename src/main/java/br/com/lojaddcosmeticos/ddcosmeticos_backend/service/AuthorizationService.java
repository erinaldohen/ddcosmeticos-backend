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
    private UsuarioRepository repository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // CORREÇÃO: Como o repositório retorna UserDetails direto (e não Optional),
        // removemos o .orElseThrow() e usamos uma verificação de nulo simples.

        UserDetails user = repository.findByMatricula(username);

        if (user == null) {
            throw new UsernameNotFoundException("Usuário com matrícula " + username + " não encontrado.");
        }

        return user;
    }
}