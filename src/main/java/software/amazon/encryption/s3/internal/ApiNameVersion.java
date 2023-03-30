package software.amazon.encryption.s3.internal;


import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.ApiName;

import java.io.IOException;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Provides the information for the ApiName APIs for the AWS SDK
 */
public class ApiNameVersion {
    private static final ApiName API_NAME = ApiNameVersion.apiNameWithVersion();
    // This is used in overrideConfiguration
    public static final Consumer<AwsRequestOverrideConfiguration.Builder> API_NAME_INTERCEPTOR =
            builder -> builder.addApiName(API_NAME);

    public static final String NAME = "AmazonS3Encrypt";
    public static final String API_VERSION_UNKNOWN = "3-unknown";

    public static ApiName apiNameWithVersion() {
        return ApiName.builder()
                .name(NAME)
                .version(apiVersion())
                .build();
    }

    private static String apiVersion() {
        try {
            final Properties properties = new Properties();
            final ClassLoader loader = ApiNameVersion.class.getClassLoader();
            properties.load(loader.getResourceAsStream("project.properties"));
            return properties.getProperty("version");
        } catch (final IOException ex) {
            return API_VERSION_UNKNOWN;
        }
    }
}
