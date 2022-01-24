package io.harness.audit;

import org.reflections.Reflections;

import javax.ws.rs.Path;
import java.util.Collection;

public class AuditResourceClasses {
    public static final String RESOURCE_PACKAGES = "io.harness.audit.remote";

    public static Collection<Class<?>> getResourceClasses() {
        final Reflections reflections = new Reflections(RESOURCE_PACKAGES);
        return reflections.getTypesAnnotatedWith(Path.class);
    }
}
