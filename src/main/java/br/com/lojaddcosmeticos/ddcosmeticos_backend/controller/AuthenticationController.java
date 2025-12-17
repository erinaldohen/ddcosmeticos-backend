package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.LoginRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.LoginResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.security.JwtService;
import jakarta.validation.Valid;
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
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO data) {
        // 1. Autentica
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.getMatricula(), data.getSenha());
        Authentication auth = this.authenticationManager.authenticate(usernamePassword);

        // 2. Recupera usuário
        Usuario usuario = (Usuario) auth.getPrincipal();

        // 3. Gera Token
        String token = jwtService.generateToken(usuario);

        // 4. Monta a resposta
        LoginResponseDTO response = new LoginResponseDTO(
                token,
                usuario.getMatricula(),
                usuario.getNome(),
                usuario.getPerfil().name()
        );

        return ResponseEntity.ok(response);
    }

    // O MÉTODO SETUP FOI DELETADO. NÃO DEVE HAVER MAIS NADA AQUI.
}