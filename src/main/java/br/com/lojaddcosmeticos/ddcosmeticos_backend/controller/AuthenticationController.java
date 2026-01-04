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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO data) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.getMatricula(), data.getSenha());
        Authentication auth = this.authenticationManager.authenticate(usernamePassword);

        Usuario usuario = (Usuario) auth.getPrincipal();
        String token = jwtService.generateToken(usuario);

        // AGORA É DIRETO: O perfil já é "ADMIN", sem precisar limpar string
        LoginResponseDTO response = new LoginResponseDTO(
                token,
                usuario.getEmail(),
                usuario.getNome(),
                usuario.getPerfilDoUsuario().name()
        );

        return ResponseEntity.ok(response);
    }
}