package com.bdmage.mage_backend.controller;

import java.util.List;
import com.bdmage.mage_backend.config.AuthenticatedUserRequest;

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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tags")
public class TagController {

	private final TagService tagService;

	public TagController(TagService tagService) {
		this.tagService = tagService;
	}

	@GetMapping
	ResponseEntity<List<TagResponse>> getAllTags(
			@RequestParam(name = "attachedOnly", defaultValue = "false") boolean attachedOnly) {
		return ResponseEntity.ok(this.tagService.getTags(attachedOnly));
	}

	@PostMapping
	ResponseEntity<TagResponse> createTag(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@Valid @RequestBody CreateTagRequest request) {
		Tag tag = this.tagService.createTag(authenticatedUserId, request.name());

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(TagResponse.from(tag));
	}
}
