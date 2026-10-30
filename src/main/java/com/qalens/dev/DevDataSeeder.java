package com.qalens.dev;

import com.qalens.domain.AppUser;
import com.qalens.domain.Organization;
import com.qalens.domain.CaseOrigin;
import com.qalens.domain.CasePriority;
import com.qalens.domain.Project;
import com.qalens.domain.Requirement;
import com.qalens.domain.RequirementLink;
import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.domain.TestCase;
import com.qalens.domain.TestCaseVersion;
import com.qalens.domain.TestStep;
import com.qalens.domain.TestSuite;
import com.qalens.domain.UserProjectRole;
import com.qalens.domain.UserProjectRoleId;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.RequirementLinkRepository;
import com.qalens.repository.RequirementRepository;
import com.qalens.repository.RoleRepository;
import com.qalens.repository.TestCaseRepository;
import com.qalens.repository.TestCaseVersionRepository;
import com.qalens.repository.TestSuiteRepository;
import com.qalens.repository.UserProjectRoleRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent dev-only demo data. Password for every account is "password".
 *
 * <pre>
 * acme   DEMO  admin=ADMIN lead=QA_LEAD tester=TESTER dev=DEVELOPER viewer=VIEWER
 * acme   SEC   admin=ADMIN only (everyone else has no role: 404)
 * globex GLB   admin@globex.test=ADMIN (another organisation: 404 for acme users)
 * </pre>
 * admin@acme.test and admin@globex.test are also organisation admins (the Users page).
 * DEMO also gets a complete sample: requirements, nested suites, cases with version history and links.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private final OrganizationRepository orgs;
    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final RoleRepository roles;
    private final UserProjectRoleRepository memberships;
    private final PasswordEncoder encoder;
    private final RequirementRepository requirements;
    private final TestSuiteRepository suites;
    private final TestCaseRepository cases;
    private final TestCaseVersionRepository versions;
    private final RequirementLinkRepository links;

    public DevDataSeeder(OrganizationRepository orgs, AppUserRepository users, ProjectRepository projects,
                         RoleRepository roles, UserProjectRoleRepository memberships, PasswordEncoder encoder,
                         RequirementRepository requirements, TestSuiteRepository suites, TestCaseRepository cases,
                         TestCaseVersionRepository versions, RequirementLinkRepository links) {
        this.requirements = requirements;
        this.suites = suites;
        this.cases = cases;
        this.versions = versions;
        this.links = links;
        this.orgs = orgs;
        this.users = users;
        this.projects = projects;
        this.roles = roles;
        this.memberships = memberships;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Organization acme = org("acme", "Acme Corp");
        AppUser admin = user(acme, "admin@acme.test", "Ada Admin", true);
        admin.setOrgAdmin(true);
        AppUser lead = user(acme, "lead@acme.test", "Lena Lead", true);
        AppUser tester = user(acme, "tester@acme.test", "Tom Tester", true);
        AppUser dev = user(acme, "dev@acme.test", "Dan Developer", true);
        AppUser viewer = user(acme, "viewer@acme.test", "Vera Viewer", true);
        user(acme, "inactive@acme.test", "Ivy Inactive", false);

        Project demo = project(acme, "DEMO", "Demo Project", "Sample project for the showcase.");
        grant(admin, demo, "ADMIN");
        grant(lead, demo, "QA_LEAD");
        grant(tester, demo, "TESTER");
        grant(dev, demo, "DEVELOPER");
        grant(viewer, demo, "VIEWER");
        seedDemoContent(demo, lead, tester);

        Project sec = project(acme, "SEC", "Security Review", "Only the admin can see this project.");
        grant(admin, sec, "ADMIN");

        Organization globex = org("globex", "Globex Inc");
        AppUser globexAdmin = user(globex, "admin@globex.test", "Gus Globex", true);
        globexAdmin.setOrgAdmin(true);
        Project glb = project(globex, "GLB", "Globex Project", "Belongs to another organisation.");
        grant(globexAdmin, glb, "ADMIN");
    }

    /** A small online-shop checkout project; skipped once the project has any suite, so edits are never overwritten. */
    private void seedDemoContent(Project demo, AppUser lead, AppUser tester) {
        if (!suites.findByProjectIdOrderByPositionAscIdAsc(demo.getId()).isEmpty()) {
            return;
        }
        Long pid = demo.getId();
        Requirement guest = requirement(pid, lead, "SHOP-1", "Guest checkout",
                "A visitor can buy without creating an account by giving an email address and a shipping address.",
                RequirementStatus.APPROVED, RequirementPriority.HIGH);
        Requirement card = requirement(pid, lead, "SHOP-2", "Pay by card",
                "The shopper can pay with a credit or debit card. Declined cards show a clear message and keep the cart.",
                RequirementStatus.APPROVED, RequirementPriority.HIGH);
        Requirement coupon = requirement(pid, lead, "SHOP-3", "Discount codes",
                "A valid discount code reduces the order total; an expired or unknown code is rejected.",
                RequirementStatus.APPROVED, RequirementPriority.MEDIUM);
        Requirement shipping = requirement(pid, lead, "SHOP-4", "Shipping options",
                "The shopper chooses standard or express shipping and sees the cost before paying.",
                RequirementStatus.DRAFT, RequirementPriority.MEDIUM);
        Requirement search = requirement(pid, lead, "SHOP-5", "Product search",
                "Searching by name returns matching products within two seconds; no match shows a helpful message.",
                RequirementStatus.APPROVED, RequirementPriority.LOW);
        requirement(pid, lead, "SHOP-6", "Wishlist sharing",
                "A shopper can share a wishlist by link. Not planned for this release.",
                RequirementStatus.DEPRECATED, RequirementPriority.LOW);

        TestSuite checkout = suite(pid, null, "Checkout", 0);
        TestSuite payment = suite(pid, checkout.getId(), "Payment", 0);
        TestSuite delivery = suite(pid, checkout.getId(), "Delivery", 1);
        TestSuite catalog = suite(pid, null, "Catalog", 1);

        TestCase guestFlow = testCase(checkout, tester, "Guest completes checkout with valid details",
                "The cart contains one in-stock product; the shopper is signed out.", CasePriority.HIGH,
                CaseOrigin.MANUAL, step("Open the cart and choose Checkout", "The checkout page asks for an email"),
                step("Enter a valid email and shipping address", "The delivery step is shown"),
                step("Pick standard shipping and continue", "The payment step shows the order total"),
                step("Pay with a valid test card", "An order confirmation page shows an order number"));
        TestCase cardOk = testCase(payment, tester, "Valid card payment is accepted",
                "Checkout is at the payment step with a total of 49.00.", CasePriority.HIGH, CaseOrigin.MANUAL,
                step("Enter card number 4242 4242 4242 4242, a future expiry and any CVC", "The form shows no errors"),
                step("Click Pay", "The order is confirmed and the card is charged 49.00"));
        // second version, written by the lead: shows the history list and the append-only rule
        addVersion(cardOk, lead, "Valid card payment is accepted and receipt emailed",
                "Checkout is at the payment step with a total of 49.00.", CasePriority.HIGH, CaseOrigin.MANUAL,
                step("Enter card number 4242 4242 4242 4242, a future expiry and any CVC", "The form shows no errors"),
                step("Click Pay", "The order is confirmed and the card is charged 49.00"),
                step("Open the mailbox of the order email", "A receipt with the order number arrives within a minute"));
        TestCase cardDeclined = testCase(payment, tester, "Declined card keeps the cart",
                "Checkout is at the payment step.", CasePriority.HIGH, CaseOrigin.AI_GENERATED,
                step("Enter card number 4000 0000 0000 0002 and pay", "A message says the card was declined"),
                step("Return to the cart", "All items are still in the cart"));
        TestCase cardExpired = testCase(payment, tester, "Expired card is rejected before submitting",
                null, CasePriority.MEDIUM, CaseOrigin.AI_GENERATED,
                step("Enter a valid number with an expiry in the past", "The expiry field shows an error"),
                step("Click Pay", "No payment request is sent"));
        TestCase couponOk = testCase(payment, tester, "Valid discount code lowers the total",
                "Cart total is 100.00; code SAVE10 gives 10 percent off.", CasePriority.MEDIUM, CaseOrigin.MANUAL,
                step("Enter SAVE10 in the discount field and apply", "The total changes to 90.00"),
                step("Complete the payment", "The order shows a 10.00 discount"));
        TestCase couponBad = testCase(payment, tester, "Unknown discount code is rejected",
                null, CasePriority.LOW, CaseOrigin.MANUAL,
                step("Enter NOSUCHCODE and apply", "A message says the code is not valid"),
                step("Check the total", "The total is unchanged"));
        TestCase express = testCase(delivery, tester, "Express shipping shows its cost before paying",
                "Delivery address is inside the supported region.", CasePriority.MEDIUM, CaseOrigin.MANUAL,
                step("Choose express shipping", "The shipping cost line shows 9.90"),
                step("Continue to payment", "The order total includes the shipping cost"));
        TestCase searchHit = testCase(catalog, tester, "Search by name returns matching products",
                null, CasePriority.LOW, CaseOrigin.MANUAL,
                step("Type 'running shoes' in the search box and submit", "Matching products are listed"),
                step("Check the response time", "Results appear in under two seconds"));

        link(guest, guestFlow);
        link(card, guestFlow);
        link(card, cardOk);
        link(card, cardDeclined);
        link(card, cardExpired);
        link(coupon, couponOk);
        link(coupon, couponBad);
        link(shipping, express);
        link(search, searchHit);
    }

    private Requirement requirement(Long projectId, AppUser author, String key, String title, String body,
                                    RequirementStatus status, RequirementPriority priority) {
        return requirements.save(new Requirement(projectId, author.getId(), key, title, body, status, priority));
    }

    private TestSuite suite(Long projectId, Long parentId, String name, int position) {
        return suites.save(new TestSuite(projectId, parentId, name, position));
    }

    private static TestStep step(String action, String expected) {
        return new TestStep(action, expected);
    }

    private TestCase testCase(TestSuite suite, AppUser author, String title, String precondition,
                              CasePriority priority, CaseOrigin origin, TestStep... steps) {
        TestCase tc = cases.save(new TestCase(suite.getId(), author.getId()));
        addVersion(tc, author, title, precondition, priority, origin, steps);
        return tc;
    }

    private void addVersion(TestCase tc, AppUser author, String title, String precondition, CasePriority priority,
                            CaseOrigin origin, TestStep... steps) {
        int next = versions.latestVersionNo(tc.getId()) + 1;
        tc.pointTo(versions.save(new TestCaseVersion(tc.getId(), next, title, precondition, List.of(steps),
                priority, origin, author.getId())));
        cases.save(tc);
    }

    private void link(Requirement requirement, TestCase tc) {
        links.save(new RequirementLink(requirement.getId(), tc.getId()));
    }

    private Organization org(String slug, String name) {
        return orgs.findBySlug(slug).orElseGet(() -> orgs.save(new Organization(slug, name)));
    }

    private AppUser user(Organization org, String email, String name, boolean active) {
        return users.findByEmailIgnoreCase(email)
                .orElseGet(() -> users.save(new AppUser(org, email, name, encoder.encode("password"), active)));
    }

    private Project project(Organization org, String key, String name, String description) {
        return projects.findByOrgIdAndProjKey(org.getId(), key)
                .orElseGet(() -> projects.save(new Project(org, key, name, description)));
    }

    private void grant(AppUser user, Project project, String roleName) {
        if (!memberships.existsById(new UserProjectRoleId(user.getId(), project.getId()))) {
            memberships.save(new UserProjectRole(user.getId(), project.getId(),
                    roles.findByName(roleName).orElseThrow()));
        }
    }
}
