package com.qalens.service.testcase;

/** {@code depth} is 0 for top-level suites; the list is already in tree order. */
public record SuiteView(Long id, Long parentId, String name, int depth) {
}
