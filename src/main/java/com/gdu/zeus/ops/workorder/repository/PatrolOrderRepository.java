package com.gdu.zeus.ops.workorder.repository;

import com.gdu.zeus.ops.workorder.data.PatrolOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatrolOrderRepository extends JpaRepository<PatrolOrder, Long> {
    List<PatrolOrder> findByPatrolAreaContaining(String area);
    List<PatrolOrder> findByOrderNameContaining(String orderName);
}
