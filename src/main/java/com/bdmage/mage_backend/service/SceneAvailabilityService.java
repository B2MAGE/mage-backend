package com.bdmage.mage_backend.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.bdmage.mage_backend.config.SceneAvailabilityProperties;
import com.bdmage.mage_backend.dto.CustomRenderingAvailabilityResponse;
import com.bdmage.mage_backend.dto.CustomRenderingControlResponse;
import com.bdmage.mage_backend.dto.SceneAvailabilityResponse;
import com.bdmage.mage_backend.dto.SceneControlResponse;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.CustomRenderingReleaseRequiredException;
import com.bdmage.mage_backend.exception.InvalidSceneAvailabilityRequestException;
import com.bdmage.mage_backend.exception.SceneNotFoundException;
import com.bdmage.mage_backend.exception.SceneOwnershipRequiredException;
import com.bdmage.mage_backend.model.CustomRenderingControl;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneAvailabilityControl;
import com.bdmage.mage_backend.repository.CustomRenderingControlRepository;
import com.bdmage.mage_backend.repository.SceneAvailabilityControlRepository;
import com.bdmage.mage_backend.repository.SceneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SceneAvailabilityService {

	public static final int MAX_STATUS_IDS = 100;
	private static final CustomRenderingControl DISABLED_CUSTOM_RENDERING =
			new CustomRenderingControl(false, null, null, null);

	private final SceneAvailabilityControlRepository sceneControls;
	private final CustomRenderingControlRepository customControls;
	private final SceneRepository scenes;
	private final OperatorAccessService operators;
	private final SceneAvailabilityProperties properties;

	public SceneAvailabilityService(
			SceneAvailabilityControlRepository sceneControls,
			CustomRenderingControlRepository customControls,
			SceneRepository scenes,
			OperatorAccessService operators,
			SceneAvailabilityProperties properties) {
		this.sceneControls = sceneControls;
		this.customControls = customControls;
		this.scenes = scenes;
		this.operators = operators;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public SceneAvailabilityResponse status(Scene scene) {
		validateId(scene.getId());
		return statusesForIds(List.of(scene.getId()), Map.of(scene.getId(), publicationMode(scene.getSceneMode())))
				.get(scene.getId());
	}

	@Transactional(readOnly = true)
	public SceneAvailabilityResponse status(Long sceneId) {
		validateId(sceneId);
		return statusesForIds(List.of(sceneId), Map.of()).get(sceneId);
	}

	@Transactional(readOnly = true)
	public Map<Long, SceneAvailabilityResponse> statuses(List<Scene> scenes) {
		Map<Long, String> loadedModes = new LinkedHashMap<>();
		for (Scene scene : scenes) {
			validateId(scene.getId());
			loadedModes.merge(scene.getId(), publicationMode(scene.getSceneMode()), SceneAvailabilityService::restrictiveMode);
		}
		return statusesForIds(List.copyOf(loadedModes.keySet()), loadedModes);
	}

	@Transactional(readOnly = true)
	public List<SceneAvailabilityResponse> statusesByIds(List<Long> sceneIds) {
		if (sceneIds == null || sceneIds.isEmpty() || sceneIds.size() > MAX_STATUS_IDS) {
			throw new InvalidSceneAvailabilityRequestException("Supply between 1 and 100 scene IDs.");
		}
		sceneIds.forEach(SceneAvailabilityService::validateId);
		return List.copyOf(statusesForIds(List.copyOf(new LinkedHashSet<>(sceneIds)), Map.of()).values());
	}

	@Transactional(readOnly = true)
	public CustomRenderingAvailabilityResponse customRenderingStatus() {
		boolean enabled = customRenderingEnabled(currentCustomControl());
		return new CustomRenderingAvailabilityResponse(enabled,
				enabled ? "AVAILABLE" : "CUSTOM_RENDERING_DISABLED",
				enabled ? null : "Custom rendering is temporarily unavailable.");
	}

	@Transactional(readOnly = true)
	public SceneControlResponse sceneControl(Long sceneId, Long actorId) {
		this.operators.requireOperator(actorId);
		return controlResponse(requireSceneControl(sceneId));
	}

	@Transactional
	public SceneControlResponse setSceneControl(Long sceneId, boolean disabled, String reason, Long actorId) {
		this.operators.requireOperator(actorId);
		String normalizedReason = normalizeReason(reason);
		requireSceneControl(sceneId);
		this.sceneControls.setDisabled(sceneId, disabled, actorId, normalizedReason);
		return controlResponse(requireSceneControl(sceneId));
	}

	@Transactional(readOnly = true)
	public CustomRenderingControlResponse customRenderingControl(Long actorId) {
		this.operators.requireAdministrator(actorId);
		return controlResponse(currentCustomControl());
	}

	@Transactional
	public CustomRenderingControlResponse setCustomRenderingControl(boolean enabled, String reason, Long actorId) {
		this.operators.requireAdministrator(actorId);
		String normalizedReason = normalizeReason(reason);
		if (enabled && !this.properties.customRenderingReleaseApproved()) {
			throw new CustomRenderingReleaseRequiredException(
					"Custom rendering cannot be enabled until the isolation release checks are approved.");
		}
		this.customControls.setEnabled(enabled, actorId, normalizedReason);
		return controlResponse(currentCustomControl());
	}

	// Raw content is only exposed through the explicit owner repair path. Editing
	// the separate scene record never touches its operator availability control.
	@Transactional(readOnly = true)
	public Scene repairScene(Long sceneId, Long requesterId) {
		if (requesterId == null) throw new AuthenticationRequiredException("Authentication is required.");
		validateId(sceneId);
		Scene scene = this.scenes.findById(sceneId)
				.orElseThrow(() -> new SceneNotFoundException("Scene not found."));
		if (!scene.getOwnerUserId().equals(requesterId)) {
			throw new SceneOwnershipRequiredException("Scene ownership is required.");
		}
		return scene;
	}

	private Map<Long, SceneAvailabilityResponse> statusesForIds(List<Long> sceneIds, Map<Long, String> loadedModes) {
		if (sceneIds.isEmpty()) return Map.of();
		Map<Long, SceneAvailabilityControl> controls = this.sceneControls.findForExistingSceneIds(sceneIds).stream()
				.collect(Collectors.toMap(SceneAvailabilityControl::sceneId, Function.identity()));
		boolean customEnabled = customRenderingEnabled(currentCustomControl());
		Map<Long, SceneAvailabilityResponse> statuses = new LinkedHashMap<>();
		for (Long sceneId : sceneIds) {
			SceneAvailabilityControl control = controls.get(sceneId);
			// Publication can use an entity loaded before a concurrent scene replacement.
			// A fresh template mode must never authorize that older custom/legacy payload.
			String mode = control == null ? Scene.LEGACY_CUSTOM : publicationMode(control.sceneMode());
			if (loadedModes.containsKey(sceneId)) mode = restrictiveMode(mode, loadedModes.get(sceneId));
			if (control == null) {
				statuses.put(sceneId, new SceneAvailabilityResponse(sceneId, false, "SCENE_NOT_FOUND", "Scene not found."));
			} else if (control.disabled()) {
				statuses.put(sceneId, new SceneAvailabilityResponse(sceneId, false, "SCENE_DISABLED", "This scene is unavailable."));
			} else if (Scene.LEGACY_CUSTOM.equals(mode)) {
				statuses.put(sceneId, new SceneAvailabilityResponse(sceneId, false,
						"SCENE_UPGRADE_REQUIRED", "This scene needs an update from its creator before it can play."));
			} else if (Scene.BUILDER_V1.equals(mode)) {
				// SB-01 stores bounded builder data. SB-02 supplies its dedicated renderer;
				// neither the custom switch nor a trusted template path can execute it yet.
				statuses.put(sceneId, new SceneAvailabilityResponse(sceneId, false,
						"BUILDER_RENDERING_UNAVAILABLE", "Builder scene playback is not available yet."));
			} else if (Scene.CUSTOM_V1.equals(mode) && !customEnabled) {
				statuses.put(sceneId, new SceneAvailabilityResponse(sceneId, false,
						"CUSTOM_RENDERING_DISABLED", "Custom rendering is temporarily unavailable."));
			} else {
				statuses.put(sceneId, new SceneAvailabilityResponse(sceneId, true, "AVAILABLE", null));
			}
		}
		return statuses;
	}

	private static String publicationMode(String mode) {
		return Scene.CUSTOM_V1.equals(mode) || Scene.TEMPLATE_V1.equals(mode) || Scene.BUILDER_V1.equals(mode)
				? mode : Scene.LEGACY_CUSTOM;
	}

	private static String restrictiveMode(String first, String second) {
		if (Scene.LEGACY_CUSTOM.equals(first) || Scene.LEGACY_CUSTOM.equals(second)) return Scene.LEGACY_CUSTOM;
		if (Scene.BUILDER_V1.equals(first) || Scene.BUILDER_V1.equals(second)) return Scene.BUILDER_V1;
		return Scene.CUSTOM_V1.equals(first) || Scene.CUSTOM_V1.equals(second) ? Scene.CUSTOM_V1 : Scene.TEMPLATE_V1;
	}

	private SceneAvailabilityControl requireSceneControl(Long sceneId) {
		validateId(sceneId);
		return this.sceneControls.findForExistingSceneIds(List.of(sceneId)).stream().findFirst()
				.orElseThrow(() -> new SceneNotFoundException("Scene not found."));
	}

	private CustomRenderingControl currentCustomControl() {
		return this.customControls.findCurrent().orElse(DISABLED_CUSTOM_RENDERING);
	}

	private boolean customRenderingEnabled(CustomRenderingControl control) {
		return this.properties.customRenderingReleaseApproved() && control.enabled();
	}

	private SceneControlResponse controlResponse(SceneAvailabilityControl control) {
		return new SceneControlResponse(control.sceneId(), control.disabled(), control.changedByUserId(),
				control.changedAt(), control.reason());
	}

	private CustomRenderingControlResponse controlResponse(CustomRenderingControl control) {
		return new CustomRenderingControlResponse(control.enabled(), this.properties.customRenderingReleaseApproved(),
				control.changedByUserId(), control.changedAt(), control.reason());
	}

	private static void validateId(Long sceneId) {
		if (sceneId == null || sceneId <= 0) {
			throw new InvalidSceneAvailabilityRequestException("Scene IDs must be positive integers.");
		}
	}

	private static String normalizeReason(String reason) {
		if (reason == null || reason.isBlank() || reason.length() > 1000) {
			throw new InvalidSceneAvailabilityRequestException("A reason between 1 and 1000 characters is required.");
		}
		return reason.trim();
	}
}
