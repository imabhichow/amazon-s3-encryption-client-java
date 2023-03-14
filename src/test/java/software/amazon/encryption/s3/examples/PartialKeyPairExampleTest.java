package software.amazon.encryption.s3.examples;

import org.junit.jupiter.api.Test;
import software.amazon.encryption.s3.utils.S3EncryptionClientTestResources;

public class PartialKeyPairExampleTest {

    @Test
    public void testLoop() {
        for (int i = 0; i < 400; i++) {
            if (i % 10 == 0) {
                System.out.println(i);
            }
            testPartialKeyPairExamples();
        }
    }

    //@Test
    public void testPartialKeyPairExamples() {
        final String bucket = S3EncryptionClientTestResources.BUCKET;

        PartialKeyPairExample.main(new String[]{bucket});
    }
}
