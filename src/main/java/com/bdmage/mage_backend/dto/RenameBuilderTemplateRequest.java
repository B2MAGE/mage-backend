package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RenameBuilderTemplateRequest(@NotNull @PositiveOrZero Long expectedRevision, String name) { }
