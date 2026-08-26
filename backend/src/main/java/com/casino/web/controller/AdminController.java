package com.casino.web.controller;

import com.casino.repository.AdminAuditRepository;
import com.casino.security.CurrentUser;
import com.casino.service.AdminService;
import com.casino.web.dto.AdminDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative actions.
 *
 * <p>The whole path is gated to {@code ROLE_ADMIN} by the URL rules, and the service methods are
 * separately annotated with {@code @PreAuthorize}. Two layers, because a future refactor of the
 * URL patterns should not be able to silently open this up.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final int MAX_AUDIT_PAGE = 200;

    private final AdminService adminService;
    private final AdminAuditRepository audit;

    public AdminController(AdminService adminService, AdminAuditRepository audit) {
        this.adminService = adminService;
        this.audit = audit;
    }

    /** Adds balance to the calling admin's own account. */
    @PostMapping("/credit/self")
    public AdminDtos.CreditResponse creditSelf(@Valid @RequestBody AdminDtos.SelfCreditRequest request,
                                               HttpServletRequest http) {
        var result = adminService.creditSelf(CurrentUser.require(), request.amount(), http.getRemoteAddr());
        return AdminDtos.CreditResponse.from(result);
    }

    /**
     * Adds balance to another account or a live guest session, by UID.
     *
     * <p>With no {@code targetUid} this credits the caller, so the console can use one form.
     */
    @PostMapping("/credit")
    public AdminDtos.CreditResponse credit(@Valid @RequestBody AdminDtos.CreditRequest request,
                                           HttpServletRequest http) {
        var actor = CurrentUser.require();
        String target = request.targetUid() == null || request.targetUid().isBlank()
                ? actor.subject()
                : request.targetUid();
        var result = adminService.creditByUid(actor, target, request.amount(), http.getRemoteAddr());
        return AdminDtos.CreditResponse.from(result);
    }

    /** The privileged-action audit trail, most recent first. */
    @GetMapping("/audit")
    public List<AdminDtos.AuditEntryView> auditLog(@RequestParam(defaultValue = "50") int limit) {
        int capped = Math.clamp(limit, 1, MAX_AUDIT_PAGE);
        return audit.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, capped)).stream()
                .map(entry -> new AdminDtos.AuditEntryView(
                        entry.getActorUsername(),
                        entry.getAction(),
                        entry.getTargetRef(),
                        entry.getTargetKind(),
                        entry.getAmount(),
                        entry.getSourceIp(),
                        entry.getCreatedAt()))
                .toList();
    }
}
