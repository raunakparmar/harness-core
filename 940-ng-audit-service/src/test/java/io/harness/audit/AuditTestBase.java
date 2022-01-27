package io.harness.audit;

import io.harness.CategoryTest;
import io.harness.rule.LifecycleRule;
import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AuditTestBase extends CategoryTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
    @Rule public AuditTestRule auditTestRule=new AuditTestRule(lifecycleRule.getClosingFactory());
}
