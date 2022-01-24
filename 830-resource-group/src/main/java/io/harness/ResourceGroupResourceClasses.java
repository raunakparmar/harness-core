package io.harness;

import org.reflections.Reflections;

import javax.ws.rs.Path;
import java.util.Collection;

public class ResourceGroupResourceClasses {
    public static final String RESOURCE_PACKAGES = "io.harness.resourcegroup.framework.remote.resource";

    public static Collection<Class<?>> getResourceClasses() {
        final Reflections reflections = new Reflections(RESOURCE_PACKAGES);
        return reflections.getTypesAnnotatedWith(Path.class);
    }

}
