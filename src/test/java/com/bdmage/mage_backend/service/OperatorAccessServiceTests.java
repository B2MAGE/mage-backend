package com.bdmage.mage_backend.service;

import java.util.Set;
import com.bdmage.mage_backend.config.AdministratorProperties;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.OperatorAccessRequiredException;
import com.bdmage.mage_backend.repository.ModeratorPermissionRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperatorAccessServiceTests {
    private final ModeratorPermissionRepository permissions = mock(ModeratorPermissionRepository.class);
    @Test void deniesGuestsAndUnprivilegedAccounts() {
        var service = new OperatorAccessService(new AdministratorProperties(null), permissions);
        assertThat(service.isOperator(null)).isFalse();
        assertThat(service.isOperator(1L)).isFalse();
        assertThatThrownBy(() -> service.requireOperator(null)).isInstanceOf(AuthenticationRequiredException.class);
        assertThatThrownBy(() -> service.requireAdministrator(null)).isInstanceOf(AuthenticationRequiredException.class);
        assertThatThrownBy(() -> service.requireOperator(1L)).isInstanceOf(OperatorAccessRequiredException.class);
        assertThatThrownBy(() -> service.requireAdministrator(1L)).isInstanceOf(OperatorAccessRequiredException.class);
        assertThat(service.capabilities(1L).canManageModerators()).isFalse();
    }
    @Test void checksDatabaseAgainAfterRevocationWithoutGrantingAdministration() {
        var service = new OperatorAccessService(new AdministratorProperties(Set.of()), permissions);
        when(permissions.isModerator(7L)).thenReturn(true, false);
        assertThatNoException().isThrownBy(() -> service.requireOperator(7L));
        assertThatThrownBy(() -> service.requireOperator(7L)).isInstanceOf(OperatorAccessRequiredException.class);
        assertThatThrownBy(() -> service.requireAdministrator(7L)).isInstanceOf(OperatorAccessRequiredException.class);
        verify(permissions, times(2)).isModerator(7L);
    }
    @Test void explicitAdministratorHasAllCapabilitiesWithoutModeratorGrant() {
        var service = new OperatorAccessService(new AdministratorProperties(Set.of(7L)), permissions);
        var capabilities = service.capabilities(7L);
        assertThat(capabilities.canManageModerators()).isTrue();
        assertThat(capabilities.canModerateScenes()).isTrue();
        assertThat(capabilities.canManageCustomRendering()).isTrue();
        verifyNoInteractions(permissions);
    }
    @Test void administratorConfigurationDefaultsEmptyAndRejectsInvalidIds() {
        assertThat(new AdministratorProperties(null).userIds()).isEmpty();
        assertThatThrownBy(() -> new AdministratorProperties(Set.of(0L))).isInstanceOf(IllegalArgumentException.class);
    }
}
