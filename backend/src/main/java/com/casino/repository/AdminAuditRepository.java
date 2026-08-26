package com.casino.repository;

import com.casino.domain.AdminAuditEntry;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, Long> {

    List<AdminAuditEntry> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
