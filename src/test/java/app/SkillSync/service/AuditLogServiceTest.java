package app.SkillSync.service;

import app.SkillSync.model.AuditLog;
import app.SkillSync.model.Role;
import app.SkillSync.model.User;
import app.SkillSync.repository.AuditLogRepository;
import app.SkillSync.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceTest {

    private AuditLogRepository auditLogRepository;
    private UserRepository userRepository;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        userRepository = mock(UserRepository.class);
        auditLogService = new AuditLogService(auditLogRepository, userRepository);
    }

    @Test
    void recordSanitizesSensitiveMetadata() {
        User actor = user(Role.ORG_ADMIN, "org-1");

        auditLogService.record(
                actor,
                "candidate_invited",
                "candidate",
                "candidate-1",
                Map.of(
                        "candidateEmail", "candidate@example.com",
                        "passwordResetToken", "secret-token"
                )
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertEquals("CANDIDATE_INVITED", savedLog.getAction());
        assertEquals("CANDIDATE", savedLog.getTargetType());
        assertEquals("candidate@example.com", savedLog.getMetadata().get("candidateEmail"));
        assertFalse(savedLog.getMetadata().containsKey("passwordResetToken"));
    }

    @Test
    void listOrganizationLogsRejectsNonAdminRoles() {
        User recruiter = user(Role.RECRUITER, "org-1");

        when(userRepository.findByEmail("recruiter@example.com"))
                .thenReturn(Optional.of(recruiter));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> auditLogService.listOrganizationLogs(
                        new UsernamePasswordAuthenticationToken("recruiter@example.com", null),
                        null
                )
        );

        assertEquals("Only organization admins can view audit logs.", exception.getMessage());
    }

    @Test
    void listOrganizationLogsReturnsOnlyCurrentOrganizationLogs() {
        User orgAdmin = user(Role.ORG_ADMIN, "org-1");
        AuditLog log = new AuditLog();
        log.setId("audit-1");
        log.setOrganizationId("org-1");
        log.setAction("ASSESSMENT_CREATED");

        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(orgAdmin));
        when(auditLogRepository.findTop100ByOrganizationIdOrderByCreatedAtDesc("org-1"))
                .thenReturn(List.of(log));

        assertEquals(
                1,
                auditLogService.listOrganizationLogs(
                        new UsernamePasswordAuthenticationToken("admin@example.com", null),
                        null
                ).size()
        );
    }

    private User user(Role role, String organizationId) {
        User user = new User();
        user.setId("user-1");
        user.setFullName("Demo User");
        user.setEmail(role == Role.RECRUITER ? "recruiter@example.com" : "admin@example.com");
        user.setRole(role);
        user.setOrganizationId(organizationId);
        return user;
    }
}
