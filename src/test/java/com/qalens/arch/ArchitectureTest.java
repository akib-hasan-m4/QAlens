package com.qalens.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.qalens.repository.AiCallLogRepository;
import com.qalens.repository.CaseVersionEmbeddingRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.RequirementLinkRepository;
import com.qalens.repository.RequirementRepository;
import com.qalens.repository.TestCaseRepository;
import com.qalens.repository.TestCaseVersionRepository;
import com.qalens.repository.TestSuiteRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

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

    private static final Set<String> TENANT_SCOPED_REPOSITORIES = Set.of(
            ProjectRepository.class.getName(), RequirementRepository.class.getName(),
            TestSuiteRepository.class.getName(), TestCaseRepository.class.getName(),
            TestCaseVersionRepository.class.getName(), RequirementLinkRepository.class.getName(),
            AiCallLogRepository.class.getName(), CaseVersionEmbeddingRepository.class.getName());

    private static final Set<String> UNCONSTRAINED_FINDERS =
            Set.of("findById", "getById", "getReferenceById", "findAllById", "findAll", "existsById", "deleteById");

    /** Inherited finders ignore the organisation; tenant-scoped data must be reached through org-aware finders. */
    @ArchTest
    static final ArchRule no_unconstrained_finders_on_tenant_scoped_repositories = noClasses()
            .that().resideOutsideOfPackage("..repository..")
            .should().callMethodWhere(DescribedPredicate.describe(
                    "it is an unconstrained finder on a tenant-scoped repository",
                    (JavaMethodCall call) -> TENANT_SCOPED_REPOSITORIES.contains(call.getTargetOwner().getFullName())
                            && UNCONSTRAINED_FINDERS.contains(call.getTarget().getName())));
}
