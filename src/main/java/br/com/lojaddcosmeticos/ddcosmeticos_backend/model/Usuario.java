package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

@Data
@Entity
@Audited
@NoArgsConstructor
@Table(name = "usuario")
@SQLDelete(sql = "UPDATE usuario SET ativo = false WHERE id = ?")
@SQLRestriction("ativo = true")
public class Usuario implements UserDetails, Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String matricula;

    @Column(nullable = false)
    private String nome;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PerfilDoUsuario perfilDoUsuario;

    @Column(nullable = false)
    private boolean ativo = true;

    // CONSTRUTOR AJUSTADO (Ordem: Matricula -> Nome -> Email -> Senha -> Perfil)
    // Isso evita o erro de trocar nome por matrícula no registro
    public Usuario(String matricula, String nome, String email, String senha, PerfilDoUsuario perfilDoUsuario) {
        this.matricula = matricula;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
        this.perfilDoUsuario = perfilDoUsuario;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Adiciona o prefixo ROLE_ para o Spring Security entender as permissões
        // Ex: Se no banco é ADMIN, aqui vira ROLE_ADMIN
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfilDoUsuario.name()));
    }

    @Override
    public String getPassword() {
        return this.senha;
    }

    @Override
    public String getUsername() {
        // IMPORTANTE: Retorna o EMAIL como username para o Spring Security
        return this.email;
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return this.ativo; }
}