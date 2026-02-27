package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuthenticationDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.LoginResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RegisterDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.UsuarioResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*; // Import do CrossOrigin e outros

@RestController
@RequestMapping("/api/v1/auth")
// IMPORTANTE: Ajuste o "http://localhost:5173" para a porta exata que seu React usa.
// O allowCredentials = "true" é obrigatório para o Front-end conseguir ler o Cookie.
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AuthenticationController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private JwtService tokenService;

    @PostMapping("/login")
    // Boa prática: Definir o tipo exato do retorno (LoginResponseDTO) no ResponseEntity
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid AuthenticationDTO data, HttpServletResponse response) {

        var usernamePassword = new UsernamePasswordAuthenticationToken(data.login(), data.password());
        var auth = authenticationManager.authenticate(usernamePassword);

        Usuario usuario = (Usuario) auth.getPrincipal();
        var token = tokenService.generateToken(usuario);

        // Configuração do Cookie Seguro
        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true); // Impede acesso via JavaScript (XSS protection)
        cookie.setSecure(false);  // True em produção com HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 dias
        response.addCookie(cookie);

        // Retornamos o token também no corpo (JSON) para caso o React precise lê-lo,
        // substituindo o 'null' que estava lá antes.
        return ResponseEntity.ok(new LoginResponseDTO(
                token,
                new UsuarioResponseDTO(
                        usuario.getId(),
                        usuario.getNome(),
                        usuario.getEmail(),
                        usuario.getMatricula(),
                        usuario.getPerfilDoUsuario(),
                        usuario.isAtivo()
                )
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0); // Deleta o cookie instantaneamente
        response.addCookie(cookie);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/register")
    // Alterado para ResponseEntity<Void> para tipagem forte
    public ResponseEntity<Void> register(@RequestBody @Valid RegisterDTO data){
        if(this.repository.findByEmail(data.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        String encryptedPassword = new BCryptPasswordEncoder().encode(data.getSenha());

        Usuario newUser = new Usuario(
                data.getNome(),
                data.getMatricula(),
                data.getEmail(),
                encryptedPassword,
                data.getPerfil()
        );

        this.repository.save(newUser);

        // Retornar CREATED (201) faz mais sentido semanticamente do que OK (200) para criação
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}