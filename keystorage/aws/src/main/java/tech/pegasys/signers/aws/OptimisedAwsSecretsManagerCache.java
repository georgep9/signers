package tech.pegasys.signers.aws;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OptimisedAwsSecretsManager extends AwsSecretsManager implements Runnable {

    private final ExecutorService executorService;

    public OptimisedAwsSecretsManagerCache(final int nThreads, final long maximumSize) {
        super(maximumSize);
        executorService = Executors.newFixedThreadPool(nThreads);
    }

    @Override
    public void run() {

    }
}
