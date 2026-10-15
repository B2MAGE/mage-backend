package com.bdmage.mage_backend.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.bdmage.mage_backend.dto.SceneAvailabilityResponse;
import com.bdmage.mage_backend.dto.SceneDiscoveryResponse;
import com.bdmage.mage_backend.dto.SceneEngagementResponse;
import com.bdmage.mage_backend.dto.SceneSummaryResponse;
import com.bdmage.mage_backend.repository.SceneDiscoveryRepository;
import com.bdmage.mage_backend.repository.SceneDiscoveryRepository.Metadata;
import com.bdmage.mage_backend.repository.SceneTagNameProjection;
import com.bdmage.mage_backend.repository.SceneTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SceneDiscoveryService {
    private final SceneDiscoveryRepository scenes;
    private final SceneTagRepository tags;
    private final SceneEngagementService engagement;
    private final SceneAvailabilityService availability;

    public SceneDiscoveryService(SceneDiscoveryRepository scenes, SceneTagRepository tags,
            SceneEngagementService engagement, SceneAvailabilityService availability) {
        this.scenes = scenes;
        this.tags = tags;
        this.engagement = engagement;
        this.availability = availability;
    }

    @Transactional(readOnly = true)
    public SceneDiscoveryResponse discover(SceneDiscoveryQuery query, Long viewerId) {
        long total = this.scenes.count(query);
        long totalPages = total == 0 ? 0 : (total - 1) / query.size() + 1;
        if (query.offset() >= total) return new SceneDiscoveryResponse(List.of(), query.page(), query.size(), total, totalPages);

        List<Metadata> page = this.scenes.findPage(query);
        if (page.isEmpty()) return new SceneDiscoveryResponse(List.of(), query.page(), query.size(), total, totalPages);
        List<Long> ids = page.stream().map(Metadata::sceneId).toList();
        Map<Long, SceneEngagementResponse> summaries = this.engagement.getSceneEngagements(ids, viewerId);
        Map<Long, SceneAvailabilityResponse> statuses = this.availability.statusesByIds(ids).stream()
                .collect(Collectors.toMap(SceneAvailabilityResponse::sceneId, Function.identity()));
        Map<Long, List<String>> tagsById = this.tags.findTagNamesBySceneIds(ids).stream()
                .collect(Collectors.groupingBy(SceneTagNameProjection::getSceneId,
                        Collectors.mapping(SceneTagNameProjection::getTagName, Collectors.toList())));
        List<SceneSummaryResponse> items = page.stream().map(scene -> new SceneSummaryResponse(
                scene.sceneId(), scene.ownerUserId(), scene.creatorDisplayName(), scene.creatorHandle(),
                scene.creatorAvatarGradientStart(), scene.creatorAvatarGradientEnd(), scene.name(), scene.description(),
                scene.thumbnailRef(), scene.createdAt(), tagsById.getOrDefault(scene.sceneId(), List.of()),
                summaries.getOrDefault(scene.sceneId(), SceneEngagementResponse.empty()), statuses.get(scene.sceneId())))
                .toList();
        return new SceneDiscoveryResponse(items, query.page(), query.size(), total, totalPages);
    }
}
