package com.qalens.service.project;

public record ProjectView(Long id, String key, String name, String description, boolean archived, String myRole) {
}
