package io.harness.gitops;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.Singleton;
import javax.xml.ws.Provider;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.GITOPS)
public class GitopsResourceClientHttpFactory
    extends AbstractHttpClientFactory implements Provider<GitopsResourceClient> {
  public GitopsResourceClientHttpFactory(ServiceHttpClientConfig templateServiceConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId) {
    super(templateServiceConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId);
    log.info("secretManagerConfig {}", templateServiceConfig);
  }

  @Override
  public GitopsResourceClient get() {
    return getRetrofit().create(GitopsResourceClient.class);
  }
}
