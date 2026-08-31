package com.mezzalia.identidad.controlador;

import com.mezzalia.identidad.dto.LoginRequest;
import com.mezzalia.identidad.dto.RefreshRequest;
import com.mezzalia.identidad.dto.TokensRespuesta;
import com.mezzalia.identidad.servicio.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Login por usuario y contraseña (decisión de producto: sin selector de
 * restaurante previo — el username es único en toda la plataforma y el
 * tenant se resuelve automáticamente a partir de él, ver
 * UsuarioInternoRepository.findByUsername).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<TokensRespuesta> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokensRespuesta> refrescar(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refrescar(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
