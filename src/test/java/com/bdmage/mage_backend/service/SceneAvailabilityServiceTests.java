package com.bdmage.mage_backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import com.bdmage.mage_backend.config.SceneAvailabilityProperties;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.CustomRenderingReleaseRequiredException;
import com.bdmage.mage_backend.exception.InvalidSceneAvailabilityRequestException;
import com.bdmage.mage_backend.exception.OperatorAccessRequiredException;
import com.bdmage.mage_backend.exception.SceneNotFoundException;
import com.bdmage.mage_backend.exception.SceneOwnershipRequiredException;
import com.bdmage.mage_backend.model.CustomRenderingControl;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneAvailabilityControl;
import com.bdmage.mage_backend.repository.CustomRenderingControlRepository;
import com.bdmage.mage_backend.repository.SceneAvailabilityControlRepository;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SceneAvailabilityServiceTests {

	private static final Instant CHANGED_AT = Instant.parse("2026-10-03T12:00:00Z");
	private final SceneAvailabilityControlRepository sceneControls = mock(SceneAvailabilityControlRepository.class);
	private final CustomRenderingControlRepository customControls = mock(CustomRenderingControlRepository.class);
	private final SceneRepository scenes = mock(SceneRepository.class);
	private final OperatorAccessService operators = mock(OperatorAccessService.class);

	@Test
	void missingGlobalControlFailsClosedEvenAfterReleaseApproval() {
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(control(false)));
		when(this.customControls.findCurrent()).thenReturn(Optional.empty());
		SceneAvailabilityService service = service(true);

		assertThat(service.customRenderingStatus().enabled()).isFalse();
		assertThat(service.status(23L).code()).isEqualTo("CUSTOM_RENDERING_DISABLED");
		assertThat(service.customRenderingControl(7L).enabled()).isFalse();
		assertThat(service.customRenderingControl(7L).changedAt()).isNull();
	}

	@Test
	void bothReleaseApprovalAndPersistedOptInAreRequired() {
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(control(false)));
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(true)));
		assertThat(service(false).status(23L).available()).isFalse();
		assertThat(service(false).customRenderingStatus().enabled()).isFalse();
		assertThat(service(true).status(23L).available()).isTrue();

		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(false)));
		assertThat(service(true).status(23L).available()).isFalse();
	}

	@Test
	void releaseGateRejectsEnableBeforeAnyPersistedWrite() {
		assertThatThrownBy(() -> service(false).setCustomRenderingControl(true, "Checks passed", 7L))
				.isInstanceOf(CustomRenderingReleaseRequiredException.class);
		verify(this.operators).requireAdministrator(7L);
		verifyNoInteractions(this.customControls, this.sceneControls, this.scenes);
	}

	@Test
	void emergencyDisableRemainsAvailableWithoutReleaseApproval() {
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(false)));
		var result = service(false).setCustomRenderingControl(false, "  Disable for investigation  ", 7L);
		verify(this.customControls).setEnabled(false, 7L, "Disable for investigation");
		assertThat(result.enabled()).isFalse();
		assertThat(result.releaseApproved()).isFalse();
	}

	@Test
	void disabledSceneStaysUnavailableAfterOwnerReplacesAllContent() throws Exception {
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(control(true)));
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(true)));
		Scene scene = scene("{\"visualizer\":{\"shader\":\"original\"}}");
		SceneAvailabilityService service = service(true);
		assertThat(service.status(scene).code()).isEqualTo("SCENE_DISABLED");

		scene.updateDetails("Changed", "Repaired", new ObjectMapper().readTree(
				"{\"visualizer\":{\"shader\":\"replacement\"}}"));
		assertThat(service.status(scene).code()).isEqualTo("SCENE_DISABLED");
		verify(this.sceneControls, never()).setDisabled(any(), anyBoolean(), any(), any());
	}

	@Test
	void submittedTemplateMetadataCannotBypassGlobalSwitch() throws Exception {
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(
				new SceneAvailabilityControl(23L, false, null, null, null, Scene.LEGACY_CUSTOM)));
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(false)));
		Scene claimedTemplate = scene("{\"kind\":\"template\",\"templateId\":\"trusted\",\"visualizer\":{\"shader\":\"arbitrary\"}}");
		assertThat(service(true).status(claimedTemplate).code()).isEqualTo("SCENE_UPGRADE_REQUIRED");
	}

	@Test
	void validatedTemplatesBypassOnlyTheCustomSwitchAndStillHonorSceneDisable() {
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(false)));
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(
				new SceneAvailabilityControl(23L, false, null, null, null, Scene.TEMPLATE_V1)), List.of(
				new SceneAvailabilityControl(23L, true, 7L, CHANGED_AT, "Investigation", Scene.TEMPLATE_V1)));
		assertThat(service(false).status(23L).available()).isTrue();
		assertThat(service(false).status(23L).code()).isEqualTo("SCENE_DISABLED");
		verifyNoInteractions(this.scenes);
	}

	@Test
	void builderDocumentsUseTrustedPlaybackRegardlessOfCustomReleaseAndOperatorSwitch() {
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(
				new SceneAvailabilityControl(23L, false, null, null, null, Scene.BUILDER_V1)));
		for (boolean enabled : List.of(false, true)) {
			when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(enabled)));
			for (boolean approved : List.of(false, true)) {
				assertThat(service(approved).status(23L).available()).isTrue();
				assertThat(service(approved).status(23L).code()).isEqualTo("AVAILABLE");
			}
		}
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(
				new SceneAvailabilityControl(23L, true, 7L, CHANGED_AT, "Investigation", Scene.BUILDER_V1)));
		assertThat(service(true).status(23L).code()).isEqualTo("SCENE_DISABLED");
		verifyNoInteractions(this.scenes);
	}

	@Test
	void builderAndExecutableSnapshotsCannotAuthorizeEachOtherDuringConcurrentReplacement() throws Exception {
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(true)));
		for (String existingMode : List.of(Scene.CUSTOM_V1, Scene.TEMPLATE_V1, Scene.BUILDER_V1)) {
			when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(
					new SceneAvailabilityControl(23L, false, null, null, null, existingMode)));
			assertThat(service(true).status(loadedScene(23L, Scene.BUILDER_V1)).code())
					.isEqualTo(existingMode.equals(Scene.BUILDER_V1) ? "AVAILABLE" : "SCENE_UPGRADE_REQUIRED");
			var snapshots = List.of(loadedScene(23L, existingMode), loadedScene(23L, Scene.BUILDER_V1));
			assertThat(service(true).statuses(snapshots).get(23L).code())
					.isEqualTo(existingMode.equals(Scene.BUILDER_V1) ? "AVAILABLE" : "SCENE_UPGRADE_REQUIRED");
			assertThat(service(true).statuses(snapshots.reversed()).get(23L).code())
					.isEqualTo(existingMode.equals(Scene.BUILDER_V1) ? "AVAILABLE" : "SCENE_UPGRADE_REQUIRED");
		}
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(
				new SceneAvailabilityControl(23L, false, null, null, null, Scene.BUILDER_V1)));
		for (String loadedMode : List.of(Scene.CUSTOM_V1, Scene.TEMPLATE_V1)) {
			assertThat(service(true).status(loadedScene(23L, loadedMode)).code()).isEqualTo("SCENE_UPGRADE_REQUIRED");
		}
		assertThat(service(true).status(loadedScene(23L, Scene.LEGACY_CUSTOM)).code()).isEqualTo("SCENE_UPGRADE_REQUIRED");
	}

	@Test
	void upgradingCurrentRowCannotAuthorizePreviouslyLoadedCustomOrLegacySource() throws Exception {
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(false)));
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(
				new SceneAvailabilityControl(23L, false, null, null, null, Scene.TEMPLATE_V1)));
		SceneAvailabilityService service = service(false);
		assertThat(service.status(loadedScene(23L, Scene.CUSTOM_V1)).code()).isEqualTo("CUSTOM_RENDERING_DISABLED");
		assertThat(service.status(loadedScene(23L, Scene.LEGACY_CUSTOM)).code()).isEqualTo("SCENE_UPGRADE_REQUIRED");
		assertThat(service.status(loadedScene(23L, "unknown-v9")).code()).isEqualTo("SCENE_UPGRADE_REQUIRED");
		assertThat(service.status(loadedScene(23L, null)).code()).isEqualTo("SCENE_UPGRADE_REQUIRED");
		assertThat(service.status(loadedScene(23L, Scene.TEMPLATE_V1)).available()).isTrue();
		// Status-only callers do not publish an older payload and report current DB state.
		assertThat(service.status(23L).available()).isTrue();
		assertThat(service.statusesByIds(List.of(23L)).getFirst().available()).isTrue();
	}

	@Test
	void currentCustomOrLegacyStateStillRestrictsPreviouslyLoadedTemplateData() throws Exception {
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(false)));
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(control(false)));
		assertThat(service(false).status(loadedScene(23L, Scene.TEMPLATE_V1)).code()).isEqualTo("CUSTOM_RENDERING_DISABLED");
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(
				new SceneAvailabilityControl(23L, false, null, null, null, Scene.LEGACY_CUSTOM)));
		assertThat(service(true).status(loadedScene(23L, Scene.TEMPLATE_V1)).code()).isEqualTo("SCENE_UPGRADE_REQUIRED");
	}

	@Test
	void batchPublicationCombinesCurrentAndLoadedModesForEveryPayload() throws Exception {
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(false)));
		when(this.sceneControls.findForExistingSceneIds(List.of(23L, 24L, 25L, 26L))).thenReturn(List.of(
				new SceneAvailabilityControl(23L, false, null, null, null, Scene.TEMPLATE_V1),
				new SceneAvailabilityControl(24L, false, null, null, null, Scene.TEMPLATE_V1),
				new SceneAvailabilityControl(25L, false, null, null, null, Scene.CUSTOM_V1),
				new SceneAvailabilityControl(26L, false, null, null, null, Scene.TEMPLATE_V1)));
		var statuses = service(false).statuses(List.of(
				loadedScene(23L, Scene.CUSTOM_V1), loadedScene(24L, Scene.LEGACY_CUSTOM),
				loadedScene(25L, Scene.TEMPLATE_V1), loadedScene(26L, Scene.TEMPLATE_V1)));
		assertThat(statuses.get(23L).code()).isEqualTo("CUSTOM_RENDERING_DISABLED");
		assertThat(statuses.get(24L).code()).isEqualTo("SCENE_UPGRADE_REQUIRED");
		assertThat(statuses.get(25L).code()).isEqualTo("CUSTOM_RENDERING_DISABLED");
		assertThat(statuses.get(26L).available()).isTrue();
		verifyNoInteractions(this.scenes);
	}

	@Test
	void duplicateSnapshotsCannotWeakenPublicationRequirements() throws Exception {
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(false)));
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(
				new SceneAvailabilityControl(23L, false, null, null, null, Scene.TEMPLATE_V1)));
		var customFirst = List.of(loadedScene(23L, Scene.CUSTOM_V1), loadedScene(23L, Scene.TEMPLATE_V1));
		assertThat(service(false).statuses(customFirst).get(23L).code()).isEqualTo("CUSTOM_RENDERING_DISABLED");
		assertThat(service(false).statuses(customFirst.reversed()).get(23L).code()).isEqualTo("CUSTOM_RENDERING_DISABLED");
	}

	@Test
	void sceneDisableAlwaysWinsOverLoadedModeAndReleaseGate() throws Exception {
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(false)));
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(
				new SceneAvailabilityControl(23L, true, 7L, CHANGED_AT, "Investigation", Scene.TEMPLATE_V1)));
		assertThat(service(false).status(loadedScene(23L, Scene.LEGACY_CUSTOM)).code()).isEqualTo("SCENE_DISABLED");
		assertThat(service(false).status(loadedScene(23L, Scene.CUSTOM_V1)).code()).isEqualTo("SCENE_DISABLED");
	}

	@Test
	void legacyAndUnknownModesRemainUnavailableEvenWhenCustomRenderingIsEnabled() {
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(true)));
		for (String mode : List.of(Scene.LEGACY_CUSTOM, "unknown-v9")) {
			when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(
					new SceneAvailabilityControl(23L, false, null, null, null, mode)));
			assertThat(service(true).status(23L).code()).isEqualTo("SCENE_UPGRADE_REQUIRED");
		}
		verifyNoInteractions(this.scenes);
	}

	@Test
	void repeatedReadsObserveChangedDatabaseStateWithoutCachedDecisions() {
		when(this.sceneControls.findForExistingSceneIds(List.of(23L)))
				.thenReturn(List.of(control(false)), List.of(control(true)), List.of(control(false)));
		when(this.customControls.findCurrent()).thenReturn(
				Optional.of(custom(true)), Optional.of(custom(true)), Optional.of(custom(false)));
		SceneAvailabilityService service = service(true);

		assertThat(service.status(23L).available()).isTrue();
		assertThat(service.status(23L).code()).isEqualTo("SCENE_DISABLED");
		assertThat(service.status(23L).code()).isEqualTo("CUSTOM_RENDERING_DISABLED");
		verify(this.sceneControls, times(3)).findForExistingSceneIds(List.of(23L));
		verify(this.customControls, times(3)).findCurrent();
		verifyNoInteractions(this.scenes);
	}

	@Test
	void batchStatusIncludesUnknownIdsAndNeverLoadsShaderSource() {
		when(this.sceneControls.findForExistingSceneIds(List.of(23L, 99L))).thenReturn(List.of(control(true)));
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(true)));
		var responses = service(true).statusesByIds(List.of(23L, 99L, 23L));
		assertThat(responses).extracting("sceneId").containsExactly(23L, 99L);
		assertThat(responses).extracting("code").containsExactly("SCENE_DISABLED", "SCENE_NOT_FOUND");
		assertThat(responses.getFirst().message()).isEqualTo("This scene is unavailable.");
		verifyNoInteractions(this.scenes);
	}

	@Test
	void publicStatusesDoNotExposePrivateReasonsOrActorDetails() throws Exception {
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of(control(true)));
		when(this.customControls.findCurrent()).thenReturn(Optional.of(custom(false)));
		ObjectMapper mapper = new ObjectMapper();
		String sceneJson = mapper.writeValueAsString(service(true).status(23L));
		String globalJson = mapper.writeValueAsString(service(true).customRenderingStatus());
		assertThat(sceneJson).doesNotContain("Private investigation", "changedAt", "changedByUserId", "reason");
		assertThat(globalJson).doesNotContain("Private investigation", "changedAt", "changedByUserId", "reason");
	}

	@Test
	void statusInputIsBoundedAndPositiveBeforeDatabaseAccess() {
		SceneAvailabilityService service = service(false);
		assertThatThrownBy(() -> service.statusesByIds(null)).isInstanceOf(InvalidSceneAvailabilityRequestException.class);
		assertThatThrownBy(() -> service.statusesByIds(List.of())).isInstanceOf(InvalidSceneAvailabilityRequestException.class);
		assertThatThrownBy(() -> service.statusesByIds(LongStream.rangeClosed(1, 101).boxed().toList()))
				.isInstanceOf(InvalidSceneAvailabilityRequestException.class);
		assertThatThrownBy(() -> service.statusesByIds(List.of(0L))).isInstanceOf(InvalidSceneAvailabilityRequestException.class);
		assertThatThrownBy(() -> service.status((Long) null)).isInstanceOf(InvalidSceneAvailabilityRequestException.class);
		verifyNoInteractions(this.sceneControls, this.customControls, this.scenes);
	}

	@Test
	void exactlyOneHundredIdsAreAcceptedAndEmptyInternalCollectionsNeedNoQueries() {
		SceneAvailabilityService service = service(true);
		assertThat(service.statuses(List.of())).isEmpty();
		verifyNoInteractions(this.sceneControls, this.customControls);
		List<Long> ids = LongStream.rangeClosed(1, 100).boxed().toList();
		when(this.sceneControls.findForExistingSceneIds(ids)).thenReturn(List.of());
		when(this.customControls.findCurrent()).thenReturn(Optional.empty());
		assertThat(service.statusesByIds(ids)).hasSize(100);
	}

	@Test
	void unauthorizedActorCannotReadControlsOrMutateEvenWithInvalidInput() {
		doThrow(new OperatorAccessRequiredException("Operator required")).when(this.operators).requireOperator(42L);
		doThrow(new OperatorAccessRequiredException("Administrator required")).when(this.operators).requireAdministrator(42L);
		SceneAvailabilityService service = service(true);
		assertThatThrownBy(() -> service.sceneControl(23L, 42L)).isInstanceOf(OperatorAccessRequiredException.class);
		assertThatThrownBy(() -> service.setSceneControl(23L, true, "Reason", 42L)).isInstanceOf(OperatorAccessRequiredException.class);
		assertThatThrownBy(() -> service.customRenderingControl(42L)).isInstanceOf(OperatorAccessRequiredException.class);
		assertThatThrownBy(() -> service.setCustomRenderingControl(true, null, 42L)).isInstanceOf(OperatorAccessRequiredException.class);
		verifyNoInteractions(this.sceneControls, this.customControls, this.scenes);
	}

	@Test
	void mutationsRequireBoundedNonBlankReasonIncludingNoOpCalls() {
		SceneAvailabilityService service = service(true);
		for (String reason : new String[] { null, " ", "x".repeat(1001) }) {
			assertThatThrownBy(() -> service.setSceneControl(23L, false, reason, 7L))
					.isInstanceOf(InvalidSceneAvailabilityRequestException.class);
			assertThatThrownBy(() -> service.setCustomRenderingControl(false, reason, 7L))
					.isInstanceOf(InvalidSceneAvailabilityRequestException.class);
		}
		verifyNoInteractions(this.sceneControls, this.customControls, this.scenes);
	}

	@Test
	void sceneMutationReturnsPersistedAuditAndDelegatesAuthoritativeTransition() {
		when(this.sceneControls.findForExistingSceneIds(List.of(23L)))
				.thenReturn(List.of(control(false)), List.of(control(true)));
		var result = service(true).setSceneControl(23L, true, "  Private investigation  ", 7L);
		assertThat(result.disabled()).isTrue();
		assertThat(result.changedAt()).isEqualTo(CHANGED_AT);
		assertThat(result.changedByUserId()).isEqualTo(7L);
		assertThat(result.reason()).isEqualTo("Private investigation");
		verify(this.operators).requireOperator(7L);
		verify(this.sceneControls).setDisabled(23L, true, 7L, "Private investigation");
	}

	@Test
	void missingSceneCannotReceiveControls() {
		when(this.sceneControls.findForExistingSceneIds(List.of(23L))).thenReturn(List.of());
		assertThatThrownBy(() -> service(true).setSceneControl(23L, true, "Reason", 7L))
				.isInstanceOf(SceneNotFoundException.class);
		verify(this.sceneControls, never()).setDisabled(any(), anyBoolean(), any(), any());
	}

	@Test
	void onlyOwnerCanReadRawRepairContentWithoutChangingControls() throws Exception {
		Scene scene = scene("{\"visualizer\":{\"shader\":\"private repair source\"}}");
		when(this.scenes.findById(23L)).thenReturn(Optional.of(scene));
		SceneAvailabilityService service = service(false);
		assertThat(service.repairScene(23L, 42L)).isSameAs(scene);
		assertThatThrownBy(() -> service.repairScene(23L, null)).isInstanceOf(AuthenticationRequiredException.class);
		assertThatThrownBy(() -> service.repairScene(23L, 7L)).isInstanceOf(SceneOwnershipRequiredException.class);
		verifyNoInteractions(this.sceneControls, this.customControls, this.operators);
	}

	private SceneAvailabilityService service(boolean releaseApproved) {
		return new SceneAvailabilityService(this.sceneControls, this.customControls, this.scenes,
				this.operators, new SceneAvailabilityProperties(Set.of(7L), releaseApproved));
	}

	private static SceneAvailabilityControl control(boolean disabled) {
		return new SceneAvailabilityControl(23L, disabled, 7L, CHANGED_AT, "Private investigation", Scene.CUSTOM_V1);
	}

	private static CustomRenderingControl custom(boolean enabled) {
		return new CustomRenderingControl(enabled, 7L, CHANGED_AT, "Private investigation");
	}

	private static Scene scene(String data) throws Exception {
		Scene scene = new Scene(42L, "Test", new ObjectMapper().readTree(data));
		ReflectionTestUtils.setField(scene, "id", 23L);
		return scene;
	}

	private static Scene loadedScene(long id, String mode) throws Exception {
		Scene scene = scene("{\"visualizer\":{\"shader\":\"previously loaded source\"}}");
		ReflectionTestUtils.setField(scene, "id", id);
		ReflectionTestUtils.setField(scene, "sceneMode", mode);
		return scene;
	}
}
