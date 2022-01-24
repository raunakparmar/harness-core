package io.harness.resourcegroup;

import com.google.inject.Inject;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.testing.TestExecution;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Map;

import static io.harness.rule.OwnerRule.MEET;
import static org.assertj.core.api.Assertions.assertThatCode;

@Slf4j
public class ResourceGroupComponentTest extends ResourceGroupTestBase {
    @Inject
    private Map<String, TestExecution> tests;

    @Test
    @Owner(developers =MEET)
    @Category(UnitTests.class)
    public void componentNotificationServiceTests() {
        for (Map.Entry<String, TestExecution> test : tests.entrySet()) {
            assertThatCode(() -> test.getValue().run()).as(test.getKey()).doesNotThrowAnyException();
            log.info("{} passed", test.getKey());
        }
    }
}
