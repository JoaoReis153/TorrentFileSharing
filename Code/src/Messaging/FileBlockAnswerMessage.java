package Messaging;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Arrays;

public class FileBlockAnswerMessage implements Serializable {

    private static final long serialVersionUID = 1L;
    private final int nodeId;
    private final byte[] hash;
    private final long offset;
    private final int length;
    private int senderPort;
    private String senderAddress;
    private FileBlockRequestMessage request;
    private byte[] data;
    private File file;

    public FileBlockAnswerMessage( String senderAddress, int senderPort, int nodeId, FileBlockRequestMessage request, File file) { 
        this.senderPort = senderPort;
        this.senderAddress = senderAddress;
        this.request = request;
        this.nodeId = nodeId;
        this.hash = request.getHash();
        this.offset = request.getOffset();
        this.length = request.getLength();
        this.file = file;
        if (length <= 0) {
            throw new IllegalArgumentException(
                "Invalid length: length must be positive"
            );
        }
        loadDataFromFile();
    }

    private void loadDataFromFile() {
        try {
            byte[] fileContents = Files.readAllBytes(file.toPath());

            if (offset < 0 || offset + length > fileContents.length) {
                throw new IllegalArgumentException("Invalid offset or length");
            }

            this.data = Arrays.copyOfRange(
                fileContents,
                (int) offset,
                (int) (offset + length)
            );
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            this.data = new byte[0];
        } catch (IllegalArgumentException e) {
            System.err.println("Warning: " + e.getMessage());
            this.data = new byte[0];
        }
    }

    public byte[] getHash() {
        return hash;
    }

    public long getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public byte[] getData() {
        return data;
    }

    public FileBlockRequestMessage getRequest() {
        return request;
    }

    public int getSenderPort() {
        return senderPort;
    }

    public String getSenderAddress() {
        return senderAddress;
    }   

    @Override
    public String toString() {
        return (
            "FileBlockAnswerMessage{" +
            "nodeId=" +
            nodeId +
            ", hash=" +
            hash +
            ", offset=" +
            offset +
            ", length=" +
            length +
            ", dataSize= " +
            data.length +
            '}'
        );
    }


}
