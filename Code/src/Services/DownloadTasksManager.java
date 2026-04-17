package Services;

import Core.Node;
import FileSearch.FileSearchResult;
import Messaging.FileBlockAnswerMessage;
import Messaging.FileBlockRequestMessage;
import java.awt.desktop.SystemEventListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadTasksManager extends Thread {

    private Node node;
    private FileSearchResult example;
    private List<FileSearchResult> requests;
    private ExecutorService threadPool;
    private CountDownLatch latch;
    private List<FileBlockRequestMessage> requestList;
    private Set<FileBlockAnswerMessage> answerList;
    private Map<String, Integer> numberOfDownloadsForPeer;
    private ArrayList<SubNode> peersWithFile;
    private int runningAssistants;
    private boolean running = true;

    public DownloadTasksManager(Node node, List<FileSearchResult> requests) {
        this.node = node;
        this.requests = requests;
        this.example = requests.get(0);

        System.out.println(
            node.getAddressAndPortFormated() +
            "[taskmanager]" +
            "Download task manager created for file " +
            example.getHash()
        );
        this.answerList = new HashSet<FileBlockAnswerMessage>();
        this.numberOfDownloadsForPeer = new HashMap<>();
        this.requestList = FileBlockRequestMessage.createBlockList(
            example.getHash(),
            example.getFileSize()
        );
        this.peersWithFile = getNodesWithFile();

        //In case there are no nodes with the file, return
        if(peersWithFile.isEmpty()) {
            System.out.println(node.getAddressAndPort() + "Couldn't find peers with the file");
            return;
        }
        this.threadPool = Executors.newFixedThreadPool(peersWithFile.size());
        System.out.println(
            node.getAddressAndPortFormated() +
            "[taskmanager]" +
            " " +
            requestList.size() +
            " blocks to process"
        );
    }

    @Override
    public void run() {
        try {
            long start = System.currentTimeMillis();
            processDownload();
            long duration = System.currentTimeMillis() - start;
            node.getGUI().showDownloadStats(example.getHash(), duration);
            System.out.println(
                node.getAddressAndPortFormated() +
                "[taskmanager]" +
                "Download finished for file " +
                example.getFileName() + 
                " [" + example.getFileSize() + "] at a rate of " +
                (example.getFileSize() / duration) +
                " bytes/s"
            );
            node.removeDownloadProcess(example.getHash());
            node.getGUI().reloadListModel();
        } catch (Exception e) {
            System.out.println(node.getAddressAndPortFormated() + "Error in DownloadTasksManager");
            e.printStackTrace();
            System.exit(1);
        }

        if (!running && !finished()) {
            System.out.println(
                node.getAddressAndPortFormated() +
                "[taskmanager]" +
                "Download process was interrupted"
            );
        }
    }

    public synchronized void stopRunning() throws RuntimeException {
        runningAssistants--;
        if (runningAssistants == 0) {
            throw new RuntimeException("Download process was interrupted");
        }
    }

    private void processDownload() {
        latch = new CountDownLatch(requestList.size());
        for (int i = 0; i < peersWithFile.size(); i++) {
            DownloadAssistant assistant = new DownloadAssistant(
                this,
                latch,
                peersWithFile.get(i),
                i
            );

            runningAssistants++;
            threadPool.execute(assistant);
            /*
            System.out.println(
                node.getAddressAndPortFormated() +
                "[taskmanager]" +
                "Submitted " +
                (i + 1) +
                "º assistant"
            );
            */
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /*
        System.out.println(
            node.getAddressAndPortFormated() +
            "[taskmanager]" +
            "All assistants finished"
        );
        */

        assembleAndWriteFile(example.getFileName(), answerList);
    }

    public void addNumberOfDownloadsForPeer(String peer, int number) {
        if (numberOfDownloadsForPeer.containsKey(peer)) {
            numberOfDownloadsForPeer.put(
                peer,
                numberOfDownloadsForPeer.get(peer) + number
            );
        } else {
            numberOfDownloadsForPeer.put(peer, number);
        }
    }

    public List<FileBlockRequestMessage> getDownloadRequestList() {
        return requestList;
    }

    public boolean finished() {
        return requestList.isEmpty();
    }

    public synchronized FileBlockRequestMessage getDownloadRequest()
        throws InterruptedException {
        while (requestList.isEmpty()) wait();

        FileBlockRequestMessage request = requestList.remove(0);
        return request;
    }

    public synchronized SubNode getNewPeer(SubNode peer) {
        if (peersWithFile.contains(peer)) peersWithFile.remove(peer);
        if (peersWithFile.isEmpty() && !finished()) {
            throw new RuntimeException(
                "No more nodes available with the file. Download process cannot continue."
            );
        }
        return peersWithFile.get(0);
    }

    public synchronized void addDownloadRequest(
        FileBlockRequestMessage request
    ) {
        requestList.add(request);
        notifyAll();
    }

    public Map<String, Integer> getDownloadProcess() {
        return numberOfDownloadsForPeer;
    }

    public synchronized void addDownloadAnswer(FileBlockAnswerMessage answer) {
        if (!answerList.contains(answer)) {
            answerList.add(answer);
            latch.countDown();
            notify();
        }
    }

    public synchronized FileBlockAnswerMessage getRespectiveAnswerMessage(
        FileBlockRequestMessage request
    ) throws InterruptedException {
        if (answerList.isEmpty()) wait(300);
        for (FileBlockAnswerMessage answer : answerList) {
            if (answer.getRequest().equals(request)) {
                return answer;
            }
        }
        return null;
    }

    private ArrayList<SubNode> getNodesWithFile() {
        ArrayList<SubNode> nodesWithFile = new ArrayList<>();
        for (FileSearchResult request : requests) {
            for (SubNode peer : node.getPeers()) {
                if (
                    peer.hasConnectionWith(
                        request.getAddress(),
                        request.getPort()
                    )
                ) {
                    nodesWithFile.add(peer);
                }
            }
        }
        return nodesWithFile;
    }

    public Set<FileBlockAnswerMessage> getAnswerList() {
        return answerList;
    }

    public Node getNode() {
        return node;
    }

    private void assembleAndWriteFile(
        String fileName,
        Set<FileBlockAnswerMessage> receivedBlockMap
    ) {
        TreeMap<Long, byte[]> fileParts = collectFileParts(receivedBlockMap);
        if (fileParts.isEmpty()) return;
        String filePath = buildFilePath(fileName);
        writeFileToDisc(filePath, fileParts);
        verifyFileCreation(filePath);
        node.loadHashes();
    }

    private TreeMap<Long, byte[]> collectFileParts(
        Set<FileBlockAnswerMessage> receivedBlockMap
    ) {
        TreeMap<Long, byte[]> fileParts = new TreeMap<>();

        for (FileBlockAnswerMessage block : receivedBlockMap) {
            if (block.getData() == null) {
                System.err.println(
                    "Warning: Block data is null for offset: " +
                    block.getOffset()
                );
                return new TreeMap<>();
            }
            fileParts.put(block.getOffset(), block.getData());
        }

        return fileParts;
    }

    private void writeFileToDisc(
        String filePath,
        TreeMap<Long, byte[]> fileParts
    ) {
        byte[] combinedData = combineFileParts(fileParts);

        try {
            Files.write(Paths.get(filePath), combinedData);
        } catch (IOException e) {
            System.out.println("Error writing file: " + filePath);
            e.printStackTrace();
        }
    }

    private byte[] combineFileParts(TreeMap<Long, byte[]> fileParts) {
        int totalSize = 0;
        for (byte[] bytes : fileParts.values()) {
            totalSize += bytes.length;
        }

        byte[] combinedData = new byte[totalSize];
        int position = 0;

        for (byte[] part : fileParts.values()) {
            System.arraycopy(part, 0, combinedData, position, part.length);
            position += part.length;
        }

        return combinedData;
    }

    private void verifyFileCreation(String filePath) {
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) {
            System.err.println("Error: File was not created at: " + filePath);
        }
    }

    private String buildFilePath(String fileName) {
        return (getNode().getFolder().getAbsolutePath() + "/" + fileName);
    }
}
