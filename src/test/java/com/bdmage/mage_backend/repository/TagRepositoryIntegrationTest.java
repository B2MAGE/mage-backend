package com.bdmage.mage_backend.repository;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bdmage.mage_backend.model.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ActiveProfiles("test")
class TagRepositoryIntegrationTest {

    @Autowired
    private TagRepository tagRepository;

    @Test
    void savesTagAndNormalizesName() {
        Tag saved = tagRepository.save(new Tag("  Chill  "));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("chill");
    }

    @Test
    void rejectsDuplicateTagNames() {
        tagRepository.save(new Tag("edm"));

        assertThatThrownBy(() -> tagRepository.save(new Tag("EDM")))
                .isInstanceOf(Exception.class);
    }
}