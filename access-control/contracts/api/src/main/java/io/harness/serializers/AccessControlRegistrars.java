package io.harness.serializers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializers.kryo.AccessControlKryoRegistrar;

import com.google.common.collect.ImmutableSet;

@OwnedBy(HarnessTeam.PL)
public class AccessControlRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder().add(AccessControlKryoRegistrar.class).build();
}
