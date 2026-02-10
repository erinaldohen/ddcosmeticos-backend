package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.UsuarioResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.UsuarioUpdateDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioRepository repository;

    @GetMapping
    @Operation(summary = "Lista todos os usuários")
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        List<UsuarioResponseDTO> usuarios = repository.findAll().stream()
                .map(u -> new UsuarioResponseDTO(
                        u.getId(),
                        u.getNome(),
                        u.getEmail(),
                        u.getMatricula(),
                        u.getPerfilDoUsuario(),
                        u.isAtivo()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(usuarios);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> buscarPorId(@PathVariable Long id) {
        return repository.findById(id)
                .map(u -> ResponseEntity.ok(new UsuarioResponseDTO(
                        u.getId(),
                        u.getNome(),
                        u.getEmail(),
                        u.getMatricula(),
                        u.getPerfilDoUsuario(),
                        u.isAtivo()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza dados do usuário")
    public ResponseEntity<UsuarioResponseDTO> atualizar(@PathVariable Long id, @RequestBody @Valid UsuarioUpdateDTO dados) {
        return repository.findById(id)
                .map(usuario -> {
                    // CORREÇÃO: Usando acessores de Record (.campo()) em vez de Getters (.getCampo())

                    if (dados.nome() != null) {
                        usuario.setNome(dados.nome());
                    }

                    if (dados.email() != null) {
                        usuario.setEmail(dados.email());
                    }

                    // CORREÇÃO: Usando 'novaSenha()' conforme definido no DTO
                    if (dados.novaSenha() != null && !dados.novaSenha().isBlank()) {
                        usuario.setSenha(new BCryptPasswordEncoder().encode(dados.novaSenha()));
                    }

                    // CORREÇÃO: Usando 'role()' conforme definido no DTO
                    if (dados.role() != null) {
                        usuario.setPerfilDoUsuario(dados.role());
                    }

                    repository.save(usuario);

                    return ResponseEntity.ok(new UsuarioResponseDTO(
                            usuario.getId(),
                            usuario.getNome(),
                            usuario.getEmail(),
                            usuario.getMatricula(),
                            usuario.getPerfilDoUsuario(),
                            usuario.isAtivo()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativa um usuário (Soft Delete)")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}