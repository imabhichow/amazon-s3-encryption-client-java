// Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.amazon.encryption.s3.materials;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.ApiName;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Request;
import software.amazon.encryption.s3.S3EncryptionClient;
import software.amazon.encryption.s3.S3EncryptionClientException;
import software.amazon.encryption.s3.internal.ApiNameVersion;

/**
 * This keyring can wrap keys with the active keywrap algorithm and
 * unwrap with the active and legacy algorithms for KMS keys.
 */
public class KmsKeyring extends S3Keyring {

    private static final ApiName API_NAME = ApiNameVersion.apiNameWithVersion();
    private static final String KEY_ID_CONTEXT_KEY = "kms_cmk_id";

    private final KmsClient _kmsClient;
    private final String _wrappingKeyId;

    private final DecryptDataKeyStrategy _kmsStrategy = new DecryptDataKeyStrategy() {

        private static final String KEY_PROVIDER_INFO = "kms";

        @Override
        public boolean isLegacy() {
            return true;
        }

        @Override
        public String keyProviderInfo() {
            return KEY_PROVIDER_INFO;
        }

        @Override
        public byte[] decryptDataKey(DecryptionMaterials materials, byte[] encryptedDataKey) {
            DecryptRequest request = DecryptRequest.builder()
                    .keyId(_wrappingKeyId)
                    .encryptionContext(materials.encryptionContext())
                    .ciphertextBlob(SdkBytes.fromByteArray(encryptedDataKey))
                    .overrideConfiguration(builder -> builder.addApiName(API_NAME))
                    .build();

            DecryptResponse response = _kmsClient.decrypt(request);
            return response.plaintext().asByteArray();
        }
    };

    private final DataKeyStrategy _kmsContextStrategy = new DataKeyStrategy() {

        private static final String KEY_PROVIDER_INFO = "kms+context";
        private static final String ENCRYPTION_CONTEXT_ALGORITHM_KEY = "aws:x-amz-cek-alg";

        @Override
        public boolean isLegacy() {
            return false;
        }

        @Override
        public String keyProviderInfo() {
            return KEY_PROVIDER_INFO;
        }

        @Override
        public EncryptionMaterials modifyMaterials(EncryptionMaterials materials) {
            S3Request s3Request = materials.s3Request();

            Map<String, String> encryptionContext = new HashMap<>(materials.encryptionContext());
            if (s3Request.overrideConfiguration().isPresent()) {
                AwsRequestOverrideConfiguration overrideConfig = s3Request.overrideConfiguration().get();
                Optional<Map<String, String>> optEncryptionContext = overrideConfig
                        .executionAttributes()
                        .getOptionalAttribute(S3EncryptionClient.ENCRYPTION_CONTEXT);
                optEncryptionContext.ifPresent(encryptionContext::putAll);
            }

            if (encryptionContext.containsKey(ENCRYPTION_CONTEXT_ALGORITHM_KEY)) {
                throw new S3EncryptionClientException(ENCRYPTION_CONTEXT_ALGORITHM_KEY + " is a reserved key for the S3 encryption client");
            }

            encryptionContext.put(ENCRYPTION_CONTEXT_ALGORITHM_KEY, materials.algorithmSuite().cipherName());

            return materials.toBuilder()
                    .encryptionContext(encryptionContext)
                    .build();
        }

        @Override
        public byte[] encryptDataKey(SecureRandom secureRandom, EncryptionMaterials materials) {
            HashMap<String, String> encryptionContext = new HashMap<>(materials.encryptionContext());
            EncryptRequest request = EncryptRequest.builder()
                    .keyId(_wrappingKeyId)
                    .encryptionContext(encryptionContext)
                    .plaintext(SdkBytes.fromByteArray(materials.plaintextDataKey()))
                    .overrideConfiguration(builder -> builder.addApiName(API_NAME))
                    .build();

            EncryptResponse response = _kmsClient.encrypt(request);
            return response.ciphertextBlob().asByteArray();
        }

        @Override
        public byte[] decryptDataKey(DecryptionMaterials materials, byte[] encryptedDataKey){
            Map<String, String> requestEncryptionContext = new HashMap<>();
            GetObjectRequest s3Request = materials.s3Request();
            if (s3Request.overrideConfiguration().isPresent()) {
                AwsRequestOverrideConfiguration overrideConfig = s3Request.overrideConfiguration().get();
                Optional<Map<String, String>> optEncryptionContext = overrideConfig
                        .executionAttributes()
                        .getOptionalAttribute(S3EncryptionClient.ENCRYPTION_CONTEXT);
                if (optEncryptionContext.isPresent()) {
                    requestEncryptionContext = new HashMap<>(optEncryptionContext.get());
                }
            }

            // We are validating the encryption context to match S3EC V2 behavior
            Map<String, String> materialsEncryptionContextCopy = new HashMap<>(materials.encryptionContext());
            materialsEncryptionContextCopy.remove(KEY_ID_CONTEXT_KEY);
            materialsEncryptionContextCopy.remove(ENCRYPTION_CONTEXT_ALGORITHM_KEY);
            if (!materialsEncryptionContextCopy.equals(requestEncryptionContext)) {
                throw new S3EncryptionClientException("Provided encryption context does not match information retrieved from S3");
            }

            DecryptRequest request = DecryptRequest.builder()
                    .keyId(_wrappingKeyId)
                    .encryptionContext(materials.encryptionContext())
                    .ciphertextBlob(SdkBytes.fromByteArray(encryptedDataKey))
                    .overrideConfiguration(builder -> builder.addApiName(API_NAME))
                    .build();

            DecryptResponse response = _kmsClient.decrypt(request);
            return response.plaintext().asByteArray();
        }

    };

    private final Map<String, DecryptDataKeyStrategy> decryptStrategies = new HashMap<>();

    public KmsKeyring(Builder builder) {
        super(builder);

        _kmsClient = builder._kmsClient;
        _wrappingKeyId = builder._wrappingKeyId;

        decryptStrategies.put(_kmsStrategy.keyProviderInfo(), _kmsStrategy);
        decryptStrategies.put(_kmsContextStrategy.keyProviderInfo(), _kmsContextStrategy);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected EncryptDataKeyStrategy encryptStrategy() {
        return _kmsContextStrategy;
    }

    @Override
    protected Map<String, DecryptDataKeyStrategy> decryptStrategies() {
        return decryptStrategies;
    }

    public static class Builder extends S3Keyring.Builder<KmsKeyring, Builder> {
        private KmsClient _kmsClient = KmsClient.builder().build();
        private String _wrappingKeyId;

        private Builder() { super(); }

        @Override
        protected Builder builder() {
            return this;
        }

        /**
         * Note that this does NOT create a defensive clone of KmsClient. Any modifications made to the wrapped
         * client will be reflected in this Builder.
         */
        @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Pass mutability into wrapping client")
        public Builder kmsClient(KmsClient kmsClient) {
            _kmsClient = kmsClient;
            return this;
        }

        public Builder wrappingKeyId(String wrappingKeyId) {
            _wrappingKeyId = wrappingKeyId;
            return this;
        }

        public KmsKeyring build() {
            return new KmsKeyring(this);
        }
    }
}
