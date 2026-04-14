package com.bdmage.mage_backend.repository;

import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.PresetTag;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private PresetRepository presetRepository;

    @Autowired
    private PresetTagRepository presetTagRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void clearData() {
        this.presetTagRepository.deleteAll();
        this.presetRepository.deleteAll();
        this.tagRepository.deleteAll();
        this.userRepository.deleteAll();
    }

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

    @Test
    void findAllAttachedToPresetsReturnsOnlyTagsWithPresetLinksSortedByName() throws Exception {
        User owner = this.userRepository.saveAndFlush(
                new User("attached-tags-owner-" + System.nanoTime() + "@example.com", "hashed-password-value", "Attached Tags Owner"));
        Preset preset = this.presetRepository.saveAndFlush(new Preset(
                owner.getId(),
                "Aurora Drift",
                this.objectMapper.readTree("""
                        {"visualizer":{"shader":"nebula"}}
                        """)));
        Tag unusedTag = this.tagRepository.saveAndFlush(new Tag("unused"));
        Tag showcaseTag = this.tagRepository.saveAndFlush(new Tag("showcase"));
        Tag ambientTag = this.tagRepository.saveAndFlush(new Tag("ambient"));

        this.presetTagRepository.saveAndFlush(new PresetTag(preset.getId(), showcaseTag.getId()));
        this.presetTagRepository.saveAndFlush(new PresetTag(preset.getId(), ambientTag.getId()));

        this.entityManager.clear();

        assertThat(this.tagRepository.findAllAttachedToPresets())
                .extracting(Tag::getName)
                .containsExactly("ambient", "showcase");
        assertThat(this.tagRepository.findAllAttachedToPresets())
                .extracting(Tag::getId)
                .doesNotContain(unusedTag.getId());
    }
}
