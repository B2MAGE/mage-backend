package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.repository.TagRepository;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TagControllerIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TagRepository tagRepository;

	@BeforeEach
	void clearTags() {
		this.tagRepository.deleteAll();
	}

	@Test
	void createTagPersistsNormalizedTag() throws Exception {
		this.mockMvc.perform(post("/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"  Chillwave  "}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.tagId").isNumber())
				.andExpect(jsonPath("$.name").value("chillwave"));

		Tag savedTag = this.tagRepository.findByName("chillwave").orElseThrow();
		assertThat(savedTag.getId()).isNotNull();
		assertThat(savedTag.getName()).isEqualTo("chillwave");
	}

	@Test
	void createTagReturnsConflictWhenTagAlreadyExists() throws Exception {
		this.tagRepository.saveAndFlush(new Tag("ambient"));

		this.mockMvc.perform(post("/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"  AMBIENT  "}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TAG_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message").value("A tag with this name already exists."));

		assertThat(this.tagRepository.findAll()).hasSize(1);
	}

	@Test
	void createTagRejectsInvalidRequestBody() throws Exception {
		this.mockMvc.perform(post("/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":" "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.name").value("name must not be blank"));
	}
}
