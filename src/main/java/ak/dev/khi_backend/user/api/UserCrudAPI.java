package ak.dev.khi_backend.user.api;

import ak.dev.khi_backend.user.dto.*;
import ak.dev.khi_backend.user.jwt.Token;
import ak.dev.khi_backend.user.model.User;
import ak.dev.khi_backend.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserCrudAPI {

    private final UserService userService;

    // ========= AUTH =========

    @PostMapping("/auth/register")
    public ResponseEntity<Token> register(@RequestBody RegisterRequestDTO dto, HttpServletRequest request) {
        return userService.register(dto, request);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Token> login(@RequestBody LoginRequestDTO dto, HttpServletRequest request) {
        return userService.login(dto, request);
    }

    @PostMapping("/auth/reset-token")
    public ResponseEntity<String> createResetToken(@RequestParam String email) {
        return userService.createPasswordResetToken(email);
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody PasswordResetRequestDTO req) {
        return userService.resetPassword(req);
    }

    // ========= CRUD =========

    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(@RequestBody User user) {
        UserResponseDTO created = userService.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> getAll() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> update(@PathVariable Long id, @RequestBody User user) {
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponseDTO> patch(@PathVariable Long id, @RequestBody User user) {
        // simple patch style: send only fields you want to change
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully");
    }
}
