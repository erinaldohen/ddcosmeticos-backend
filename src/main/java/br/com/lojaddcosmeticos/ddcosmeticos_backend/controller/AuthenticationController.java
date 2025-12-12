package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.LoginRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.LoginResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Perfil; // Importe o Enum
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository; // Importe o Repo
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder; // Importe o Encoder
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtService;

    // Injeções novas para o Setup
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO data) {
        // 1. Autentica
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.getMatricula(), data.getSenha());
        Authentication auth = this.authenticationManager.authenticate(usernamePassword);

        // 2. Recupera usuário
        Usuario usuario = (Usuario) auth.getPrincipal();

        // 3. Gera Token
        String token = jwtService.generateToken(usuario);

        // 4. Monta a resposta com TODOS os dados
        // Graças ao @AllArgsConstructor no DTO, a ordem deve ser: Token, Matricula, Nome, Perfil
        LoginResponseDTO response = new LoginResponseDTO(
                token,
                usuario.getMatricula(),
                usuario.getNome(),
                usuario.getPerfil().name()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * ENDPOINT TEMPORÁRIO DE AJUDA
     * Cria o usuário GERENTE02 no banco de produção via código.
     * Chame via navegador ou Postman (GET) para resetar o acesso.
     */
    @GetMapping("/setup")
    public ResponseEntity<String> setupInicial() {
        try {
            // Verifica se existe
            Usuario usuario = usuarioRepository.findByMatricula("GERENTE02")
                    .orElse(null);

            String senhaHash = passwordEncoder.encode("123456");

            if (usuario == null) {
                // Cria novo
                usuario = new Usuario(
                        "Gerente Producao",
                        "GERENTE02",
                        senhaHash,
                        Perfil.ROLE_GERENTE
                );
            } else {
                // ATENÇÃO: Força a atualização da senha e perfil caso já exista
                usuario.setSenha(senhaHash);
                usuario.setPerfil(Perfil.ROLE_GERENTE);
            }

            usuarioRepository.save(usuario);

            return ResponseEntity.ok("SUCESSO! Usuário 'GERENTE02' atualizado/criado. Senha definida para '123456'.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro: " + e.getMessage());
        }
    }
}