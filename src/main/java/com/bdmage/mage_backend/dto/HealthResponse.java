package com.bdmage.mage_backend.dto;

/**
 * HealthResponse
 *
 * This class represents the JSON response returned by the /health endpoint.
 *
 * In backend APIs we usually do not return raw objects directly. Instead,
 * we define small "response shapes" called DTOs (Data Transfer Objects).
 *
 * A DTO is simply a lightweight class whose job is to define the structure
 * of the data sent to or received from the API.
 *
 * In this case, the response looks like this:
 *
 * {
 * "status": "UP"
 * }
 *
 * This class is defined as a Java *record*. Records are a compact way of
 * defining immutable data objects without writing boilerplate code such as
 * getters or constructors.
 *
 * Spring Boot automatically converts this object into JSON when it is
 * returned from a controller method.
 */
public record HealthResponse(String status) {
}