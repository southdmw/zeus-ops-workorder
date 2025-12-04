package com.gdu.zeus.ops.workorder.data;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_algorithms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIAlgorithm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario")
    private String scenario; // 适用场景

    @Column(name = "algorithm_name")
    private String algorithmName; // 算法名称

    @Column(name = "algorithm_function", length = 1000)
    private String algorithmFunction; // 算法功能

    @Column(name = "algorithm_usage", length = 2000)
    private String algorithmUsage; // 算法用途
}
