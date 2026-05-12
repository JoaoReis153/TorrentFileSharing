package Services;

import Core.Node;
import FileSearch.FileSearchResult;
import Messaging.FileBlockAnswerMessage;
import Messaging.FileBlockRequestMessage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
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
    private Set<FileBlockRequestMessage> completedRequests;
    private File destinationFile;
    private Map<String, Integer> numberOfDownloadsForPeer;
    private ArrayList<SubNode> peersWithFile;
    private int totalBlocks;
    private int completedBlocks;
    private int runningAssistants;
    private boolean running = true;
    private boolean downloadSuccessful = false;

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
        this.completedRequests = new HashSet<FileBlockRequestMessage>();
        this.numberOfDownloadsForPeer = new HashMap<>();
        this.requestList = FileBlockRequestMessage.createBlockList(
            example.getHash(),
            example.getFileSize()
        );
        this.totalBlocks = requestList.size();
        this.completedBlocks = 0;
        this.peersWithFile = getNodesWithFile();
        this.destinationFile = new File(buildFilePath(example.getFileName()));

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
            node
                .getListener()
                .startDownloadProgress(
                    example.getHash(),
                    example.getFileName(),
                    totalBlocks
                );

            long start = System.currentTimeMillis();
            processDownload();
            downloadSuccessful = true;
            long duration = System.currentTimeMillis() - start;
            long safeDurationMs = Math.max(1L, duration);
            long bytesPerSecond = (example.getFileSize() * 1000L) / safeDurationMs;

            node
                .getListener()
                .completeDownloadProgress(
                    example.getHash(),
                    duration,
                    new HashMap<>(numberOfDownloadsForPeer)
                );
            System.out.println(
                node.getAddressAndPortFormated() +
                "[taskmanager]" +
                "Download finished for file " +
                example.getFileName() + 
                " [" + example.getFileSize() + "] at a rate of " +
                bytesPerSecond +
                " bytes/s"
            );
            node.removeDownloadProcess(example.getHash());
            node.getListener().reloadListModel();
            node.loadHashes();
        } catch (Exception e) {
            node.getListener().finishDownloadProgress(example.getHash());
            System.out.println(node.getAddressAndPortFormated() + "Error in DownloadTasksManager: " + e.getMessage());
            // e.printStackTrace();
        } finally {
            if (!downloadSuccessful) {
                System.err.println(node.getAddressAndPortFormated() + " [taskmanager] Download failed or interrupted. Deleting partial file: " + destinationFile.getAbsolutePath());
                if (destinationFile.exists()) {
                    destinationFile.delete();
                }
            }
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
        if (completedRequests.contains(answer.getRequest())) {
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(destinationFile, "rw")) {
            raf.seek(answer.getOffset());
            raf.write(answer.getData());
            
            completedRequests.add(answer.getRequest());
            completedBlocks++;
            latch.countDown();
            node
                .getListener()
                .updateDownloadProgress(example.getHash(), completedBlocks);
            notifyAll();
        } catch (IOException e) {
            System.err.println(node.getAddressAndPortFormated() + " Error writing block to disk: " + e.getMessage());
            running = false;
        }
    }

    public synchronized boolean isBlockCompleted(
        FileBlockRequestMessage request
    ) throws InterruptedException {
        if (completedRequests.isEmpty()) wait(300);
        return completedRequests.contains(request);
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

    public Set<FileBlockRequestMessage> getCompletedRequests() {
        return completedRequests;
    }

    public Node getNode() {
        return node;
    }

    private String buildFilePath(String fileName) {
        return (getNode().getFolder().getAbsolutePath() + File.separator + fileName);
    }
}
