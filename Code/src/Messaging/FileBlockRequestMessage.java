package Messaging;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileBlockRequestMessage implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_TARGET_BLOCK_COUNT = 64;
    private static final int MIN_BLOCK_SIZE = 1024;
    private static final int MAX_BLOCK_SIZE = 1024 * 1024;

    private byte[] hash;
    private long offset;
    private int length;
    private String senderAddress;
    private int senderPort = 0; 

    public FileBlockRequestMessage(byte[] hash, long offset, int length) {
        this.hash = hash;
        this.offset = offset;
        this.length = length;
    }
  

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress;
    }

    public void setSenderPort(int senderPort) {
        this.senderPort = senderPort;
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
    
    public String getSenderAddress() {
        return senderAddress;
    }   
    
    public int getSenderPort() {
        return senderPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileBlockRequestMessage that = (FileBlockRequestMessage) o;
        return (
            Arrays.equals(hash, that.hash) &&
            offset == that.offset &&
            length == that.length
        );
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(hash);
        result = 31 * result + Long.hashCode(offset);
        result = 31 * result + Integer.hashCode(length);
        return result;
    }

    public String toString() {
        String hashString = String.valueOf(hash);
        int length = hashString.length();

        String hashDisplay = length > 10
            ? hashString.substring(length - 10)
            : hashString;

        return (
            "FileBlockRequestMessage [hash=" +
            hashDisplay +
            ", offset=" +
            offset +
            ", length=" +
            length +
            "]"
        );
    }
 
    public static List<FileBlockRequestMessage> createBlockList(
        byte[] hash,
        long fileSize
    ) {
        return createBlockList(hash, fileSize, getDynamicBlockSize(fileSize));
    }

    private static int getDynamicBlockSize(long fileSize) {
        if (fileSize <= 0) {
            return MIN_BLOCK_SIZE;
        }

        long proposedBlockSize = (fileSize + DEFAULT_TARGET_BLOCK_COUNT - 1) /
            DEFAULT_TARGET_BLOCK_COUNT;
        long boundedBlockSize = Math.max(
            MIN_BLOCK_SIZE,
            Math.min(MAX_BLOCK_SIZE, proposedBlockSize)
        );

        return (int) boundedBlockSize;
    }

    /*
     * Creates a list of file block request messages
     * 
     * It takes a hash, the size of the file and the block size
     * 
     * It creates a list of file block request messages with the size of the file
     * divided by the block size.
     * 
     * If the file size is not divisible by the block size, it creates a list of
     * file block request messages with the size of the file plus one
     * 
     * If the file size is divisible by the block size, it creates a list of
     * file block request messages with the size of the file     
     */ 
    public static List<FileBlockRequestMessage> createBlockList(
        byte[] hash,
        long fileSize,
        int blockSize
    ) {
        List<FileBlockRequestMessage> blockList = new ArrayList<>();
        long offset = 0;

        while (offset < fileSize) {
            int length = (int) Math.min(blockSize, fileSize - offset);
            blockList.add(new FileBlockRequestMessage(hash, offset, length));
            offset += length;
        }

        return blockList;
    }
}
