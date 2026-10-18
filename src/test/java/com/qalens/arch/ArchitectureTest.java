package com.qalens.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.qalens", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule web_layer_does_not_touch_repositories = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule only_service_layer_calls_ai = noClasses()
            .that().resideOutsideOfPackages("..service..", "..ai..", "..config..")
            .should().dependOnClassesThat().resideInAPackage("..ai..")
            .allowEmptyShould(true);

    // Tenant rule (no unconstrained findById on tenant-scoped repositories) is added with M1 once entities exist.
}
