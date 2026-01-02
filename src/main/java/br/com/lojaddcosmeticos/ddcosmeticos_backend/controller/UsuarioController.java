package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.UsuarioResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.UsuarioUpdateDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuários", description = "Gestão Administrativa de Operadores")
public class UsuarioController {

    @Autowired private UsuarioRepository repository;
    @Autowired private PasswordEncoder passwordEncoder;

    @GetMapping
    @Operation(summary = "Listar todos os usuários")
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        var lista = repository.findAll().stream()
                .map(u -> new UsuarioResponseDTO(u.getId(), u.getEmail(), u.getNome(), u.getPerfilDoUsuario(), u.isEnabled()))
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')") // Só Admin pode editar outros
    @Operation(summary = "Atualizar usuário (Role, Nome ou Senha)")
    public ResponseEntity<Void> atualizar(@PathVariable Long id, @RequestBody UsuarioUpdateDTO dados) {
        Usuario usuario = repository.findById(id).orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (dados.nome() != null) usuario.setNome(dados.nome());
        if (dados.role() != null) usuario.setPerfilDoUsuario(dados.role());

        // Se mandou senha nova, criptografa e salva
        if (dados.novaSenha() != null && !dados.novaSenha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(dados.novaSenha()));
        }

        repository.save(usuario);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Inativar/Ativar Usuário")
    public ResponseEntity<Void> alternarStatus(@PathVariable Long id) {
        Usuario usuario = repository.findById(id).orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        // Se tiver método setEnabled ou setAtivo no seu model
        // usuario.setAtivo(!usuario.isAtivo());
        // repository.save(usuario);
        // Se não tiver, implemente soft delete ou use delete físico:
        repository.delete(usuario);
        return ResponseEntity.ok().build();
    }
}