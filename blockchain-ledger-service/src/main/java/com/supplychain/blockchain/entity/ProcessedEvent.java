package com.supplychain.blockchain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events", uniqueConstraints = { @UniqueConstraint(columnNames = { "event_id" }) })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100, unique = true)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
