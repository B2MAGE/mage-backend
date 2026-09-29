package com.bdmage.mage_backend.dto;

import com.bdmage.mage_backend.model.Tag;

public record TagResponse(
		Long tagId,
		String name,
		long sceneCount) {

	public static TagResponse from(Tag tag) {
		return new TagResponse(tag.getId(), tag.getName(), 0);
	}
}
