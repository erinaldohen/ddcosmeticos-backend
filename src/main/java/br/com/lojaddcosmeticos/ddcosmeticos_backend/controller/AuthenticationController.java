package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.LoginRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.LoginResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RegisterDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private UsuarioRepository repository;
    @Autowired private TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO data) {
        // LoginRequestDTO é um RECORD, então .email() e .senha() funcionam
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.email(), data.senha());
        var auth = authenticationManager.authenticate(usernamePassword);

        var usuario = (Usuario) auth.getPrincipal();
        var token = tokenService.generateToken(usuario);

        // CORREÇÃO LINHA 38: Adicionado usuario.getMatricula() para completar os 4 argumentos
        return ResponseEntity.ok(new LoginResponseDTO(
                token,
                usuario.getMatricula(),
                usuario.getNome(),
                usuario.getPerfilDoUsuario().name()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody @Valid RegisterDTO data) {
        // CORREÇÃO LINHA 43: RegisterDTO é CLASSE (@Data), então usa getters
        if (this.repository.findByEmail(data.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        String encryptedPassword = new BCryptPasswordEncoder().encode(data.getSenha());

        // CORREÇÃO LINHA 49: Usando getters corretos e ordem certa do construtor
        Usuario newUser = new Usuario(
                data.getMatricula(),
                data.getNome(),
                data.getEmail(),
                encryptedPassword,
                data.getPerfil()
        );

        this.repository.save(newUser);

        return ResponseEntity.ok().build();
    }
}