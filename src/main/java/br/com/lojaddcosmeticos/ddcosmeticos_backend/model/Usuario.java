// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/model/Usuario.java (REVISÃO DE SEGURANÇA)

package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data
@Entity
@NoArgsConstructor
@Table(name = "usuario")
public class Usuario implements UserDetails { // IMPLEMENTA INTERFACE DE SEGURANÇA

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "matricula", unique = true, nullable = false)
    private String matricula; // Usada como Username para login

    @Column(name = "senha", nullable = false)
    private String senha; // Senha criptografada

    @Enumerated(EnumType.STRING) // Grava o texto "ROLE_CAIXA" no banco
    @Column(name = "perfil", nullable = false)
    private Perfil perfil;

    // Construtor para inicialização (usado no Application.java)
    public Usuario(String nome, String matricula, String senha, Perfil perfil) {
        this.nome = nome;
        this.matricula = matricula;
        this.senha = senha;
        this.perfil = perfil;
    }

    // --- Métodos de UserDetails (Spring Security) ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Pega o nome do Enum (ex: "ROLE_CAIXA")
        return List.of(new SimpleGrantedAuthority(perfil.name()));
    }

    @Override
    public String getPassword() {
        return this.senha;
    }

    @Override
    public String getUsername() {
        return this.matricula; // Matricula será o username
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}