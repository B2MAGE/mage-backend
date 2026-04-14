package com.bdmage.mage_backend.dto;

import com.bdmage.mage_backend.model.SceneTag;

public record SceneTagResponse(
		Long sceneId,
		Long tagId) {

	public static SceneTagResponse from(SceneTag sceneTag) {
		return new SceneTagResponse(sceneTag.getSceneId(), sceneTag.getTagId());
	}
}
