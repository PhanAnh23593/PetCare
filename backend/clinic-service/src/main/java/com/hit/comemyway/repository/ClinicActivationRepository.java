package com.hit.comemyway.repository;

import com.hit.comemyway.entity.ClinicActivation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ClinicActivationRepository extends JpaRepository<ClinicActivation, Long> {
  List<ClinicActivation> findAllByOrderByAttemptsAsc(Pageable page);
}
