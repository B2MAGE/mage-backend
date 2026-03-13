package com.bdmage.mage_backend.model;

import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 64, unique = true)
    private String name;

    protected Tag() {
    }

    public Tag(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        normalize();
    }

    @PrePersist
    @PreUpdate
    private void normalize() {
        this.name = this.name.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }
}