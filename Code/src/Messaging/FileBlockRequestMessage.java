package Messaging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Request for a specific block of a file.
 *
 * Wire layout:
 *   [byte[]: hash]
 *   [long: offset]
 *   [int: length]
 *   [String: senderAddress]
 *   [int: senderPort]
 */
public class FileBlockRequestMessage {

    public static final byte TYPE_ID = 4;

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

    // ── Serialization ─────────────────────────────────────────────────────────

    public void writeToStream(DataOutputStream dos) throws IOException {
        BinaryProtocol.writeBytes(dos, hash);
        dos.writeLong(offset);
        dos.writeInt(length);
        BinaryProtocol.writeString(dos, senderAddress);
        dos.writeInt(senderPort);
    }

    public static FileBlockRequestMessage readFromStream(DataInputStream dis) throws IOException {
        byte[] hash = BinaryProtocol.readBytes(dis);
        long offset = dis.readLong();
        int length = dis.readInt();
        String senderAddress = BinaryProtocol.readString(dis);
        int senderPort = dis.readInt();

        FileBlockRequestMessage msg = new FileBlockRequestMessage(hash, offset, length);
        msg.setSenderAddress(senderAddress);
        msg.setSenderPort(senderPort);
        return msg;
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        writeToStream(dos);
        dos.flush();
        return baos.toByteArray();
    }

    public static FileBlockRequestMessage fromBytes(byte[] bytes) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
        return readFromStream(dis);
    }

    // ── Getters/Setters ───────────────────────────────────────────────────────

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

    @Override
    public String toString() {
        String hashString = Arrays.toString(hash);
        int hashLen = hashString.length();

        String hashDisplay = hashLen > 10
            ? hashString.substring(hashLen - 10)
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
