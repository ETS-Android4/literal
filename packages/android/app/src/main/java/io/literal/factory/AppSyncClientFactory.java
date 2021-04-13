package io.literal.factory;

import android.content.Context;
import android.util.Log;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient;
import com.amazonaws.mobileconnectors.appsync.S3ObjectManagerImplementation;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import io.literal.lib.Constants;
import io.literal.lib.WebRoutes;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class AppSyncClientFactory {

    private static volatile AWSConfiguration configuration;
    private static volatile AWSAppSyncClient client;
    private static volatile AmazonS3Client s3Client;
    private static volatile S3ObjectManagerImplementation s3ObjectManager;

    public static AWSAppSyncClient getInstance(Context context) {
        // FIXME: Authenticate API access, get guest AwsCredentials
        if (client == null) {
            client = AWSAppSyncClient.builder()
                    .context(context)
                    .okHttpClient(
                        new OkHttpClient.Builder()
                            .addNetworkInterceptor(
                                chain -> {
                                    Request newRequest = chain.request().newBuilder()
                                            .addHeader("origin", WebRoutes.getAPIHost())
                                            .build();
                                    return chain.proceed(newRequest);
                                }
                            )
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build()
                    )
                    .awsConfiguration(getConfiguration(context))
                    .s3ObjectManager(getS3ObjectManager(context))
                    .cognitoUserPoolsAuthProvider(() -> {
                        try {
                            return AWSMobileClient.getInstance().getTokens().getIdToken().getTokenString();
                        } catch (Exception e) {
                            Log.e(Constants.LOG_TAG, e.getLocalizedMessage());
                            return e.getLocalizedMessage();
                        }
                    })
                    .build();
        }
        return client;
    }

    public static AWSConfiguration getConfiguration(Context context) {
        if (configuration == null) {
            configuration = new AWSConfiguration(context);
        }
        return configuration;
    }

    public static final AmazonS3Client getS3Client(Context context) {
        if (s3Client == null) {
            AWSConfiguration configuration = getConfiguration(context);
            s3Client = new AmazonS3Client(getCredentialsProvider(context, configuration));
            JSONObject s3TransferUtility = configuration.optJsonObject("S3TransferUtility");
            String region = s3TransferUtility.optString("Region");
            s3Client.setRegion(Region.getRegion(region));
        }
        return s3Client;
    }

    public static final S3ObjectManagerImplementation getS3ObjectManager(Context context) {
        if (s3ObjectManager == null) {
            s3ObjectManager = new S3ObjectManagerImplementation(getS3Client(context));
        }
        return s3ObjectManager;
    }

    // initialize and fetch cognito credentials provider for S3 Object Manager
    public static final AWSCredentialsProvider getCredentialsProvider(final Context context, final AWSConfiguration configuration) {

        JSONObject credentialsProviderJson = configuration.optJsonObject("CredentialsProvider");
        if (credentialsProviderJson == null) {
            return null;
        }
        JSONObject cognitoIdentityJson = credentialsProviderJson.optJSONObject("CognitoIdentity");
        if (cognitoIdentityJson == null) {
            return null;
        }
        JSONObject defaultJson = cognitoIdentityJson.optJSONObject("Default");
        if (defaultJson == null) {
            return null;
        }

        String poolId = defaultJson.optString("PoolId");
        String region = defaultJson.optString("Region");

        final CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                context,
                poolId,
                Regions.fromName(region)
        );
        return credentialsProvider;
    }
}
