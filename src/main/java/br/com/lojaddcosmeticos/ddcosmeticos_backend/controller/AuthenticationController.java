package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuthenticationDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.LoginResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RegisterDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.security.JwtService;
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
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private JwtService tokenService;

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody @Valid AuthenticationDTO data) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.matricula(), data.senha());

        // 1. Autentica
        var auth = this.authenticationManager.authenticate(usernamePassword);

        // 2. Recupera o objeto Usuario completo do banco
        Usuario usuario = (Usuario) auth.getPrincipal();

        // 3. Gera o token
        var token = tokenService.generateToken(usuario);

        // 4. Retorna o DTO (Record) preenchido com tudo que o Front-end precisa
        return ResponseEntity.ok(new LoginResponseDTO(
                token,
                usuario.getMatricula(),
                usuario.getNome(),
                usuario.getPerfil().name() // Converte o Enum para String
        ));
    }

    @PostMapping("/register")
    public ResponseEntity register(@RequestBody @Valid RegisterDTO data) {
        // CORREÇÃO: Usando getters (getLogin) em vez de acessors de record (login)
        if (this.repository.findByMatricula(data.getMatricula()) != null) {
            return ResponseEntity.badRequest().body("Usuário já existe");
        }

        String encryptedPassword = new BCryptPasswordEncoder().encode(data.getSenha());

        // Adaptação: Usamos o 'login' do DTO como 'matricula' e 'nome'
        // Certifique-se que o construtor do Usuario é: (String nome, String matricula, String senha, PerfilDoUsuario perfil)
        Usuario newUser = new Usuario(
                data.getMatricula(),   // Nome (Temporário igual à matrícula)
                data.getMatricula(),   // Matrícula
                encryptedPassword, // Senha Criptografada
                data.getPerfil()     // Perfil (Enum)
        );

        this.repository.save(newUser);

        return ResponseEntity.ok().build();
    }
}