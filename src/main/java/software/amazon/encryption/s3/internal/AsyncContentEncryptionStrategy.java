package software.amazon.encryption.s3.internal;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.encryption.s3.materials.EncryptionMaterials;

@FunctionalInterface
public interface AsyncContentEncryptionStrategy {
    EncryptedContent encryptContent(EncryptionMaterials materials, AsyncRequestBody content);
}
