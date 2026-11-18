package com.alumni.linkedinsearcher.repository;

import com.alumni.linkedinsearcher.entity.Alumni;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlumniRepository extends JpaRepository<Alumni, Long> {
    Optional<Alumni> findByLinkedinUrl(String linkedinUrl);
    List<Alumni> findByUniversityContainingIgnoreCaseAndCurrentRoleContainingIgnoreCase(
            String university, String currentRole);
    List<Alumni> findByUniversityContainingIgnoreCaseAndCurrentRoleContainingIgnoreCaseAndPassoutYear(
            String university, String currentRole, Integer passoutYear);
}
