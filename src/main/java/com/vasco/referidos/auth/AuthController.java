package com.vasco.referidos.auth;

import com.vasco.referidos.entities.Users;
import com.vasco.referidos.repositories.UsersRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private UsersRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/getUsers")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> getUsers(){
        List<Users> response = userRepository.findAll();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/getUserByUsername")
    public ResponseEntity<?> getUserById(@RequestParam String username){
        Users response = userRepository.findByUsername(username).orElse(null);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/session")
    public ResponseEntity<?> checkSession(HttpServletRequest request) {
        // Buscar el JWT en las cookies
        String token = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No token found");
        }

        try {
            String username = jwtUtil.extractUsername(token);
            Optional<Users> optionalUser = userRepository.findByUsername(username);
            if (optionalUser.isPresent()) {
                Users user = optionalUser.get();
                Map<String, Object> sessionInfo = new HashMap<>();
                sessionInfo.put("id", user.getId());
                sessionInfo.put("name", user.getName());
                sessionInfo.put("role", user.getRol());
                sessionInfo.put("foro", user.getForo());
                return ResponseEntity.ok(sessionInfo);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no encontrado");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token inválido");
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No autenticado");
        }

        Users user = userRepository.findByUsername(userDetails.getUsername()).get();
        return ResponseEntity.ok(Map.of(
                "name", user.getName(),
                "role", user.getRol(),
                "foro", user.getForo()
        ));
    }

    @PutMapping("/editUser")
    public ResponseEntity<?> edit(@RequestBody Users user) {
        user.setId(user.getId());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setName(user.getName());
        user.setForo(user.getForo());
        userRepository.save(user);
        return ResponseEntity.ok("Usuario actualizado");
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<?> register(@RequestBody Users user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("El usuario ya existe");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setName(user.getName());
        user.setForo(user.getForo());
        user.setRol(user.getRol());
        userRepository.save(user);
        return ResponseEntity.ok("Usuario registrado");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(credentials.get("username"), credentials.get("password")));
        } catch (BadCredentialsException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales inválidas");
        }

        // Obtener los detalles del usuario
        Optional<Users> optionalUser = userRepository.findByUsername(credentials.get("username"));
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }

        Users user = optionalUser.get();

        // Generar el token
        String token = jwtUtil.generateToken(credentials.get("username"));


        // Crear cookie HttpOnly para el token
        ResponseCookie jwtCookie = ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(true) // false para entorno de desarrollo y true para producción
                .sameSite("None")
                .path("/")
                .maxAge(60 * 60) // 1 hora
                .build();

        // Construir la respuesta con información no sensible en el body
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", user.getId());
        responseBody.put("name", user.getName());
        responseBody.put("role", user.getRol());
        responseBody.put("foro", user.getForo());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, jwtCookie.toString());

        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        // Crear una cookie con el mismo nombre y maxAge 0 para eliminarla
        ResponseCookie deleteCookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());

        return ResponseEntity.ok("Sesión cerrada correctamente");
    }
}

