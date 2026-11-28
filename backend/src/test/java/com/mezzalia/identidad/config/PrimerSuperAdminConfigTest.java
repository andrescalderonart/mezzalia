package com.mezzalia.identidad.config;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El primer SUPER_ADMIN de produccion se crea una sola vez y nunca pisa a nadie. */
class PrimerSuperAdminConfigTest {

    private final UsuarioInternoRepository usuarios = mock(UsuarioInternoRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void sinVariablesNoHaceNada() {
        PrimerSuperAdminConfig.crear(usuarios, encoder, "", "");
        PrimerSuperAdminConfig.crear(usuarios, encoder, null, null);
        verify(usuarios, never()).save(any());
    }

    @Test
    void conUnSuperAdminExistenteNoCreaNiCambiaNada() {
        when(usuarios.existsByRol(RolUsuario.SUPER_ADMIN)).thenReturn(true);
        PrimerSuperAdminConfig.crear(usuarios, encoder, "andres", "arepa con queso y cafe");
        verify(usuarios, never()).save(any());
    }

    @Test
    void creaElPrimeroConLaContrasenaCifrada() {
        when(usuarios.existsByRol(RolUsuario.SUPER_ADMIN)).thenReturn(false);
        PrimerSuperAdminConfig.crear(usuarios, encoder, " andres ", "arepa con queso y cafe");
        ArgumentCaptor<UsuarioInterno> creado = ArgumentCaptor.forClass(UsuarioInterno.class);
        verify(usuarios).save(creado.capture());
        assertThat(creado.getValue().getUsername()).isEqualTo("andres");
        assertThat(creado.getValue().getRol()).isEqualTo(RolUsuario.SUPER_ADMIN);
        assertThat(creado.getValue().getTenantId()).isNull();
        assertThat(encoder.matches("arepa con queso y cafe", creado.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void unaContrasenaDebilImpideArrancar() {
        when(usuarios.existsByRol(RolUsuario.SUPER_ADMIN)).thenReturn(false);
        assertThatThrownBy(() -> PrimerSuperAdminConfig.crear(usuarios, encoder, "andres", "12345678"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no es segura");
        verify(usuarios, never()).save(any());
    }
}
