// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/controller/AuthenticationController.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.LoginRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.LoginResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.JwtService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    @Autowired
    private AuthenticationManager authenticationManager; // Componente do Spring Security

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO data) {

        // 1. Cria o objeto de autenticação com as credenciais
        UsernamePasswordAuthenticationToken usernamePassword =
                new UsernamePasswordAuthenticationToken(data.getMatricula(), data.getSenha());

        // 2. Tenta autenticar (usa o UserDetailsService e o PasswordEncoder configurados)
        Authentication auth = this.authenticationManager.authenticate(usernamePassword);

        // 3. Autenticação bem-sucedida
        Usuario usuario = (Usuario) auth.getPrincipal();

        // 4. Gera o Token JWT
        String token = jwtService.generateToken(usuario);

        // 5. Prepara a resposta
        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setMatricula(usuario.getMatricula());
        response.setNome(usuario.getNome());
        response.setPerfil(usuario.getPerfil());

        return ResponseEntity.ok(response);
    }
}