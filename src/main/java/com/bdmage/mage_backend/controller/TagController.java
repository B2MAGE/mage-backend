package com.bdmage.mage_backend.controller;

import java.util.List;

import com.bdmage.mage_backend.dto.CreateTagRequest;
import com.bdmage.mage_backend.dto.TagResponse;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tags")
public class TagController {

	private final TagService tagService;

	public TagController(TagService tagService) {
		this.tagService = tagService;
	}

	@GetMapping
	ResponseEntity<List<TagResponse>> getAllTags() {
		List<Tag> tags = this.tagService.getAllTags();

		return ResponseEntity.ok(tags.stream()
				.map(TagResponse::from)
				.toList());
	}

	@PostMapping
	ResponseEntity<TagResponse> createTag(@Valid @RequestBody CreateTagRequest request) {
		Tag tag = this.tagService.createTag(request.name());

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(TagResponse.from(tag));
	}
}
