package io.harness.serializer.morphia;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.resourcegroup.commons.bootstrap.ConfigurationState;

import java.util.Set;

public class ResourceGroupMorphiaRegistrar implements MorphiaRegistrar {
    @Override
    public void registerClasses(Set<Class> set) {
        set.add(ConfigurationState.class);
    }

    @Override
    public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
        // no classes to register
    }
}