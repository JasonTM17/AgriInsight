package com.agriinsight.backend.identity.infrastructure;

import com.agriinsight.backend.identity.domain.UserProfile;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {

    Optional<UserProfile> findByTenantIdAndId(UUID tenantId, UUID profileId);
}
