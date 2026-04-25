package Services;

import Messaging.FileBlockAnswerMessage;
import Messaging.FileBlockRequestMessage;
import java.util.concurrent.CountDownLatch;

public class DownloadAssistant extends Thread {

    private final DownloadTasksManager taskManager;
    private SubNode peerToRequestBlock;
    private boolean timedOut = false;
    private int timeoutCount;
    private final int MAX_TIMEOUT_COUNT = 5;

    public DownloadAssistant(
        DownloadTasksManager taskManager,
        CountDownLatch latch,
        SubNode peerToRequestBlock,
        int ID
    ) {
        this.timeoutCount = 0;
        this.taskManager = taskManager;
        this.peerToRequestBlock = peerToRequestBlock;
    }


    /*
     * While the taskManager still has requests to be processed (its not finished) and the timedOut flag is false
     * 
     * It takes a block request message from the taskManager and sends it to the peer to request the
     * and waits for the answer to arrive. 
     * 
     */
    @Override
    public void run() {
        while (!taskManager.finished() && !timedOut) {
            FileBlockRequestMessage request;
            try {
                request = taskManager.getDownloadRequest();
                if (request != null) {
                    handleRequest(request);
                }
            } catch (InterruptedException e) {
                System.out.println(taskManager.getNode().getAddressAndPortFormated() + " [taskmanager] [downloadassistant] Error in DownloadAssistant");
                e.printStackTrace();
            }
        }
    }


    /*
     * Handles a block request message
     * 
     * It takes a block request message and sends it to the peer to request the
     * and waits for the answer to arrive. 
     * 
     * In case the answer is not received within the timeout, it returns the request back to the taskManager
     * 
     * And in case the number of timeouts exceeds the maximum, it stops the download assistant
     */
    private void handleRequest(FileBlockRequestMessage request) {
        peerToRequestBlock.sendFileBlockRequest(request);

        if (!waitForAnswer(request, 100000)) {
            timeoutCount++;
            if (timeoutCount > MAX_TIMEOUT_COUNT) {
                taskManager.stopRunning();
                timedOut = true;
            }
            taskManager.addDownloadRequest(request);
        }
    }


    /*
     * Waits for a block answer message
     * 
     * It takes a block request message and waits for the 
     * block answer message corresponding to the request to arrive.
     * 
     * In case the answer is not received within the timeout, it returns false.
     */
    private boolean waitForAnswer(
        FileBlockRequestMessage request,
        long timeoutMs
    ) {
        long endTime = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < endTime) {
            try {
                if (taskManager.isBlockCompleted(request)) {
                    return true; // Answer received
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
                System.out.println( taskManager.getNode().getAddressAndPortFormated() + " [taskmanager] [downloadassistant] Interrupted while waiting for answer");
                return false; // Handle interruption as a failure
            }
        }
        return false; // Timeout occurred
    }
}
