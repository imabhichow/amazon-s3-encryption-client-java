package software.amazon.encryption.s3.internal;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.io.InputStream;

public class AuthenticatedCipherInputStream extends CipherInputStream {

    public AuthenticatedCipherInputStream(InputStream inputStream, Cipher cipher) {
        super(inputStream, cipher);
    }

    /**
     * Authenticated ciphers call doFinal upon the last read,
     * so no need to do so upon close
     * TODO: Should this throw a security exception? Probably?
     * @throws IOException from the wrapped InputStream
     */
    @Override
    public void close() throws IOException {
        if (!eofReached) {
            // If the stream is closed before reaching EOF,
            // the auth tag cannot be written (on encrypt)
            // or validated (on decrypt).
            throw new SecurityException("Stream closed before end of stream reached!");
        }
        in.close();
        currentPosition = maxPosition = 0;
        abortIfNeeded();
    }

    @Override
    protected int endOfFileReached() {
        eofReached = true;
        try {
            outputBuffer = cipher.doFinal();
            if (outputBuffer == null) {
                return -1;
            }
            currentPosition = 0;
            return maxPosition = outputBuffer.length;
        } catch (IllegalBlockSizeException ignore) {
            // Swallow exception
        } catch (BadPaddingException exception) {
            // In an authenticated scheme, this indicates a security
            // exception
            throw new SecurityException(exception);
        }
        return -1;
    }
}
