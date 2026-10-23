package com.qalens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Seeded by migration V2: ADMIN, QA_LEAD, TESTER, DEVELOPER, VIEWER. */
@Entity
@Table(name = "role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    protected Role() {
    }

    public Short getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
