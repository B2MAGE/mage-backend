package com.bdmage.mage_backend.repository;

import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@Testcontainers
class TagRepositoryIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private TagRepository tagRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void savesTagAndNormalizesName() {
        Tag saved = tagRepository.saveAndFlush(new Tag("  Chill  "));

        entityManager.clear();

        Tag found = tagRepository.findByName("chill").orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("chill");
        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    void rejectsDuplicateTagNames() {
        tagRepository.saveAndFlush(new Tag("edm"));

        assertThatThrownBy(() -> tagRepository.saveAndFlush(new Tag("EDM")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}