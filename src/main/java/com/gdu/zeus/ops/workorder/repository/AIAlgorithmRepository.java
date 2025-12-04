package com.gdu.zeus.ops.workorder.repository;

import com.gdu.zeus.ops.workorder.data.AIAlgorithm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIAlgorithmRepository extends JpaRepository<AIAlgorithm, Long> {
    Optional<AIAlgorithm> findByAlgorithmName(String algorithmName);
    List<AIAlgorithm> findByScenarioContaining(String scenario);
}
