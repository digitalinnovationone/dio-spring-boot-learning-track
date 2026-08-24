package dio.budgeting.domain;

public interface AudioStorageGateway {
    StoredAudio store(byte[] content, String originalFileName);

    byte[] load(String storagePath);
}
