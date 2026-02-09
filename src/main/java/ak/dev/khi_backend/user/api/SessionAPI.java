package ak.dev.khi_backend.user.api;

import ak.dev.khi_backend.user.dto.SessionDTO;
import ak.dev.khi_backend.user.model.Session;
import ak.dev.khi_backend.user.model.User;
import ak.dev.khi_backend.user.repo.SessionRepository;
import ak.dev.khi_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth/sessions")
@RequiredArgsConstructor
public class SessionAPI {


    private final SessionRepository sessionRepository;

//    private final UserService userService; // Service to fetch current user

    /**
     * Get all active sessions for the authenticated user
     */
    @GetMapping("/getAllSessions")
    public ResponseEntity<List<SessionDTO>> getAllSessions(@AuthenticationPrincipal User user) {
        List<Session> sessions = sessionRepository.findByUserAndIsActive(user, true);
        List<SessionDTO> sessionDTOs = sessions.stream().map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(sessionDTOs);
    }

    /**
     * Revoke a specific session by sessionId
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> revokeSession(@PathVariable String sessionId, @AuthenticationPrincipal User user) {
        Session session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getUser().getUserId().equals(user.getUserId())) {
            return ResponseEntity.status(403).body("You can only revoke your own sessions");
        }

        session.setIsActive(false);
        session.setLogoutTimestamp(new Date());
        sessionRepository.save(session);

        return ResponseEntity.ok("Session revoked successfully");
    }

    /**
     * Revoke all sessions for the authenticated user (optional)
     */
    @DeleteMapping("/revokeAll")
    public ResponseEntity<?> revokeAllSessions(@AuthenticationPrincipal User user) {
        List<Session> sessions = sessionRepository.findByUserAndIsActive(user, true);
        sessions.forEach(session -> {
            session.setIsActive(false);
            session.setLogoutTimestamp(new Date());
        });
        sessionRepository.saveAll(sessions);
        return ResponseEntity.ok("All sessions revoked successfully");
    }

    /**
     * Convert Session entity to SessionDTO
     */
    private SessionDTO convertToDTO(Session session) {
        SessionDTO dto = new SessionDTO();
        dto.setSessionId(session.getSessionId());
        dto.setDeviceInfo(session.getDeviceInfo());
        dto.setIpAddress(session.getIpAddress());
        dto.setLoginTimestamp(session.getLoginTimestamp());
        dto.setExpiresAt(session.getExpiresAt());
        dto.setIsActive(session.getIsActive());
        dto.setLogoutTimestamp(session.getLogoutTimestamp());
        return dto;
    }
}
