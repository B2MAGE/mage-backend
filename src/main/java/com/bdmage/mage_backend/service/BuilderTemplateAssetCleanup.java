package com.bdmage.mage_backend.service;

import java.sql.Timestamp;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A small persistent deletion queue: storage failures never roll back a successful template save. */
@Service
@EnableScheduling
public class BuilderTemplateAssetCleanup {
    private static final Logger LOG = LoggerFactory.getLogger(BuilderTemplateAssetCleanup.class);
    private final JdbcTemplate jdbc;
    private final ThumbnailStorageService storage;

    public BuilderTemplateAssetCleanup(JdbcTemplate jdbc, ThumbnailStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    /** Called before copying: a crashed or rolled-back save still leaves a cleanup record. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackCopy(String publicUrl) {
        enqueue(publicUrl, Instant.now().plusSeconds(86400));
    }

    public void enqueue(String ref, Instant notBefore) {
        if (ref != null) this.jdbc.update("""
                INSERT INTO builder_template_asset_cleanup(thumbnail_ref, not_before) VALUES (?, ?)
                ON CONFLICT (thumbnail_ref) DO UPDATE SET not_before = LEAST(builder_template_asset_cleanup.not_before, EXCLUDED.not_before)
                """, ref, Timestamp.from(notBefore));
    }

    @Scheduled(fixedDelayString = "${mage.builder-template.cleanup-delay-ms:60000}", initialDelay = 60000)
    @Transactional
    public void cleanup() {
        var refs = this.jdbc.queryForList("""
                SELECT thumbnail_ref FROM builder_template_asset_cleanup
                WHERE not_before <= CURRENT_TIMESTAMP ORDER BY not_before LIMIT 100 FOR UPDATE SKIP LOCKED
                """, String.class);
        for (String ref : refs) {
            try {
                // A process can stop after the template commits and before its provisional cleanup is removed.
                Boolean retained = this.jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM builder_templates WHERE thumbnail_ref = ?)", Boolean.class, ref);
                if (!Boolean.TRUE.equals(retained)) this.storage.deleteTemplateAsset(ref);
                this.jdbc.update("DELETE FROM builder_template_asset_cleanup WHERE thumbnail_ref = ?", ref);
                this.jdbc.update("DELETE FROM builder_template_uploads WHERE object_key = ?", ref);
            } catch (RuntimeException ex) {
                LOG.warn("Template thumbnail cleanup will retry for {}", ref, ex);
            }
        }
    }
}
