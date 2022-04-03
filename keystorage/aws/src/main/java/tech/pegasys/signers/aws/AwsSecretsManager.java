/*
 * Copyright 2022 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.signers.aws;

import java.io.Closeable;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

public class AwsSecretsManager implements Closeable {

  private static final Logger LOG = LogManager.getLogger();

  private final SecretsManagerClient secretsManagerClient;

  private AwsSecretsManager(final SecretsManagerClient secretsManagerClient) {
    this.secretsManagerClient = secretsManagerClient;
  }

  static AwsSecretsManager createAwsSecretsManager(
      final String accessKeyId, final String secretAccessKey, final String region) {
    final AwsBasicCredentials awsBasicCredentials =
        AwsBasicCredentials.create(accessKeyId, secretAccessKey);
    final StaticCredentialsProvider credentialsProvider =
        StaticCredentialsProvider.create(awsBasicCredentials);

    final SecretsManagerClient secretsManagerClient =
        SecretsManagerClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(region))
            .build();

    return new AwsSecretsManager(secretsManagerClient);
  }

  static AwsSecretsManager createAwsSecretsManager() {
    final SecretsManagerClient secretsManagerClient = SecretsManagerClient.builder().build();

    return new AwsSecretsManager(secretsManagerClient);
  }

  public Optional<String> fetchSecret(final String secretName) {
    try {
      final GetSecretValueRequest getSecretValueRequest =
          GetSecretValueRequest.builder().secretId(secretName).build();
      final GetSecretValueResponse valueResponse =
          secretsManagerClient.getSecretValue(getSecretValueRequest);
      return Optional.of(valueResponse.secretString());
    } catch (final ResourceNotFoundException e) {
      return Optional.empty();
    } catch (final SecretsManagerException e) {
      throw new RuntimeException("Failed to fetch secret from AWS Secrets Manager.", e);
    }
  }

  public <R> Collection<R> mapSecretsList(
      final String prefix, final BiFunction<String, String, R> mapper) {
    final Set<R> result = ConcurrentHashMap.newKeySet();
    secretsManagerClient.listSecretsPaginator().stream()
        .parallel()
        .forEach(
            p ->
                p.secretList()
                    .parallelStream()
                    .forEach(
                        secret -> {
                          final String secretName = secret.name();
                          if (secretName.startsWith(prefix)) {
                            try {
                              final R obj = mapper.apply(secretName, fetchSecret(secretName).get());
                              if (obj != null) {
                                result.add(obj);
                              } else {
                                LOG.warn("Mapped '{}' to a null object, and was discarded", secret);
                              }
                            } catch (final Exception e) {
                              LOG.warn(
                                  "Failed to map secret '{}' to requested object type.",
                                  secretName);
                            }
                          }
                        }));
    return result;
  }

  public <R> Collection<R> mapSecretsList(final BiFunction<String, String, R> mapper) {
    return mapSecretsList("", mapper);
  }

  @Override
  public void close() {
    this.secretsManagerClient.close();
  }
}
