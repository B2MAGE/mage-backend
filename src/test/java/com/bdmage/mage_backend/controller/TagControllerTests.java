package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.TagAlreadyExistsException;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.service.TagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TagControllerTests {

	private TagService tagService;
	private MockMvc mockMvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		this.tagService = mock(TagService.class);
		this.validator = new LocalValidatorFactoryBean();
		this.validator.afterPropertiesSet();
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new TagController(this.tagService))
				.setControllerAdvice(new ApiExceptionHandler())
				.setValidator(this.validator)
				.build();
	}

	@AfterEach
	void tearDown() {
		this.validator.close();
	}

	@Test
	void createTagReturnsCreatedTag() throws Exception {
		Tag tag = new Tag("ambient");
		ReflectionTestUtils.setField(tag, "id", 15L);

		when(this.tagService.createTag("Ambient")).thenReturn(tag);

		this.mockMvc.perform(post("/api/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Ambient"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.tagId").value(15L))
				.andExpect(jsonPath("$.name").value("ambient"));
	}

	@Test
	void getAllTagsReturnsTagResponses() throws Exception {
		Tag ambient = new Tag("ambient");
		Tag chillwave = new Tag("chillwave");
		ReflectionTestUtils.setField(ambient, "id", 15L);
		ReflectionTestUtils.setField(chillwave, "id", 16L);

		when(this.tagService.getAllTags()).thenReturn(List.of(ambient, chillwave));

		this.mockMvc.perform(get("/api/tags"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].tagId").value(15L))
				.andExpect(jsonPath("$[0].name").value("ambient"))
				.andExpect(jsonPath("$[1].tagId").value(16L))
				.andExpect(jsonPath("$[1].name").value("chillwave"));
	}

	@Test
	void getAllTagsWithAttachedOnlyReturnsAttachedTagResponses() throws Exception {
		Tag ambient = new Tag("ambient");
		ReflectionTestUtils.setField(ambient, "id", 15L);

		when(this.tagService.getAllTagsAttachedToPresets()).thenReturn(List.of(ambient));

		this.mockMvc.perform(get("/api/tags?attachedOnly=true"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].tagId").value(15L))
				.andExpect(jsonPath("$[0].name").value("ambient"));
	}

	@Test
	void createTagRejectsInvalidRequestBody() throws Exception {
		this.mockMvc.perform(post("/api/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":" "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.name").value("name must not be blank"));
	}

	@Test
	void createTagReturnsConflictWhenTagAlreadyExists() throws Exception {
		when(this.tagService.createTag("Ambient"))
				.thenThrow(new TagAlreadyExistsException("A tag with this name already exists."));

		this.mockMvc.perform(post("/api/tags")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Ambient"}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TAG_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.message").value("A tag with this name already exists."));
	}
}
