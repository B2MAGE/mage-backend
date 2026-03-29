package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.dto.CreateTagRequest;
import com.bdmage.mage_backend.dto.TagResponse;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tags")
public class TagController {

	private final TagService tagService;

	public TagController(TagService tagService) {
		this.tagService = tagService;
	}

	@PostMapping
	ResponseEntity<TagResponse> createTag(@Valid @RequestBody CreateTagRequest request) {
		Tag tag = this.tagService.createTag(request.name());

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(TagResponse.from(tag));
	}
}
