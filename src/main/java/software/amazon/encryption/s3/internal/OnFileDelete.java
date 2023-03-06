package software.amazon.encryption.s3.internal;


/**
 * A service provider interface (SPI) used to notify the event of a file
 * deletion.
 */
public interface OnFileDelete {
    /**
     * Called upon a file deletion event.
     * <p>
     * Implementation of this method should never block.
     *
     * @param event file deletion event
     */
    void onFileDelete(FileDeletionEvent event);
}