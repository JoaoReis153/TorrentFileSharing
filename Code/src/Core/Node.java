package Core;

import FileSearch.FileSearchResult;
import GUI.GUI;
import Messaging.FileBlockAnswerMessage;
import Messaging.FileBlockRequestMessage;
import Services.DownloadTasksManager;
import Services.SenderAssistant;
import Services.SubNode;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Node {

    public static final String WORK_FOLDER = "Code/files/dl";
    private static final int BASE_PORT = 8080;
    private static final int MAX_PORT = 65535;

    private final int nodeId;
    private final int port;
    private final InetAddress address;
    private final File folder;
    private final Set<SubNode> peers;
    private final HashMap<Integer, DownloadTasksManager> downloadManagers;
    private final GUI gui;
    private ArrayList<FileBlockRequestMessage> blocksToProcess;
    private ExecutorService senders;
    private final int numberOfSenders = 5;
    private HashMap<String, Integer> hashes;

    private final ExecutorService downloadTaskManagersThreadPool =
        Executors.newFixedThreadPool(10);

    public Node(int nodeId, GUI gui) {
        this.hashes = new HashMap<>();
        this.nodeId = nodeId;
        this.port = BASE_PORT + nodeId;
        this.gui = gui;
        this.peers = new HashSet<>();
        this.downloadManagers = new HashMap<>();
        this.blocksToProcess = new ArrayList<>();
        validateId();
        initializeSenders(numberOfSenders);
        this.folder = createWorkingDirectory();
        this.address = initializeAddress();
        loadHashes();
    }

    /*
     * Returns the peer, given a destination address and port, if it exists
     */
    public SubNode getPeerToSend(String address, int port) {
        for (SubNode peer : peers) {
            if (
                peer.getDestinationAddress().equals(address) &&
                peer.getDestinationPort() == port
            ) {
                return peer;
            }
        }
        return null;
    }

    /*
     * Initializes the senders for the node
     * The senders are responsible for answering the requests
     * from other nodes with a reply that contains the requested part of the file
     */ 
    private void initializeSenders(int n) {
        if (n <= 0) return;

        this.senders = Executors.newFixedThreadPool(n);
        for (int i = 0; i < n; i++) {
            this.senders.execute(new SenderAssistant(this));
        }
    }


    /*
     *  Use the already created method to check the ID
     *  And throw an exception if it's not valid
     */
    private void validateId() { 
        if (!Utils.isValidID(this.nodeId)) {
            throw new IllegalArgumentException(
                "Invalid node ID. Input a valid number (1-41070)"
            );
        }
    }

    /*
     * Creates the working directory for the node
     * The working directory is the directory where the node will store the files
     */
    private File createWorkingDirectory() {
        File folder = new File(WORK_FOLDER + nodeId);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new RuntimeException(
                getAddressAndPortFormated() +
                "Failed to create directory: dl" +
                nodeId
            );
        }
        return folder;
    }

    /*
     * Initializes the address of the node
     * The address is the IP address of the node
     */
    private InetAddress initializeAddress() {
        try {
            return Core.Utils.getLocalIPAddress() ;
        } catch (Exception e) {
            throw new RuntimeException(  getAddressAndPortFormated() + " Unable to get the device's address", e);
        }
    }

    /*
     * Starts the server of the node
     * The server is responsible for accepting connections from other nodes
     * and creating a new handler for each connection
     */
    public void startServing() {
        System.out.println(
            getAddressAndPortFormated() + "Awaiting connection..."
        );
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                SubNode clientHandler = new SubNode(this, clientSocket, true);
                clientHandler.start();
                peers.add(clientHandler);
            }
        } catch (IOException e) {
            throw new RuntimeException(
                getAddressAndPortFormated() +
                "Failed to start server: " +
                e.getMessage()
            );
        }
    }

    // Add a new request to the list of requests to process
    public synchronized void addElementToBlocksToProcess(
        FileBlockRequestMessage request
    ) {
        blocksToProcess.add(request);
        notify();
    }

    // Remove a request from the list of requests to process
    public synchronized void removeElementFromBlocksToProcess(
        FileBlockRequestMessage request
    ) throws InterruptedException {
        if (blocksToProcess.isEmpty()) wait();
        blocksToProcess.remove(request);
    }

    /*
     * Connects to a node
     * The connection is made by sending a new connection request to the node
     * and waiting for a response
     */
    public void connectToNode(String targetAddress, int targetPort) {
        try {
            InetAddress targetInetAddress = resolveAddress(targetAddress);

             
            // Check if the connection is valid
            // The valid connection method alreadys throws exceptions accordingly to the type of error
            if (!isValidConnection(targetInetAddress, targetPort)) {
                return;
            }

            establishConnection(targetInetAddress, targetPort);
        } catch (Exception e) {
            System.err.println("Error connecting to node: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /*
     * Resolves the address of a node
     * The address is resolved by using the InetAddress.getByName method
     * and checking if the resolved address is null
     * 
     * This method will be called when trying to connect to a node
     */
    private InetAddress resolveAddress(String address) {
        try {
            InetAddress resolved = InetAddress.getByName(address);
            if (resolved == null) {
                throw new IllegalArgumentException("Invalid address");
            }
            return resolved;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "Unable to resolve address: " + address
            );
        }
    }

    /*
     * Checks if a connection with a node is valid
     * There are three types of errors that can be thrown:
     * 1. Invalid port range
     * 2. Connection already exists
     * 3. Unable to resolve the address
     * 
     * Otherwise, the connection is valid
     */
    private boolean isValidConnection(
        InetAddress targetAddress,
        int targetPort
    ) {
        if (targetPort <= BASE_PORT || targetPort >= MAX_PORT) {
            System.out.println(
                getAddressAndPortFormated() +
                "Failed to connect: Invalid port range"
            );
            return false;
        }

        if (targetAddress.equals(this.address) && targetPort == this.port) {
            System.out.println(
                getAddressAndPortFormated() +
                "Failed to connect: Cannot connect to itself"
            );
            return false;
        }

        if (isAlreadyConnected(targetAddress, targetPort)) {
            System.out.println(
                getAddressAndPortFormated() +
                "Failed to connect: Connection already exists"
            );
            return false;
        }

        return true;
    }

    /*
     * Checks if a connection with a node already exists
     * The connection is checked by comparing the address and port of the connection
     */
    private boolean isAlreadyConnected(
        InetAddress targetAddress,
        int targetPort
    ) {
        for (SubNode peer : peers) {
            if (
                (peer.getSocket().getInetAddress().equals(targetAddress) &&
                    peer.getSocket().getPort() == targetPort) ||
                peer.getOriginalBeforeOSchangePort() == targetPort
            ) {
                return true;
            }
        }
        return false;
    }

    /*
     * Establishes a connection with a node
     * The connection is made by creating a new handler for the connection
     * and adding it to the list of peers
     */
    private void establishConnection(
        InetAddress targetAddress,
        int targetPort
    ) {
        try {
            Socket clientSocket = new Socket(targetAddress, targetPort);
            SubNode handler = new SubNode(this, clientSocket, true);
            handler.start();
            peers.add(handler);

            Thread.sleep(100);
            handler.sendNewConnectionRequest(address, port);
        } catch (IOException e) {
            System.err.println( getAddressAndPortFormated() +
                "Failed to establish connection: " + e.getMessage()
            );
        } catch (InterruptedException e) {
            System.err.println( getAddressAndPortFormated() + 
                "Failed to establish connection: " + e.getMessage()
            );
        }
    }

    /*
     * Broadcasts a word search message request to all peers
     * The message is sent to all peers in the list of peers
     * 
     * That each wil eventually send a FileSearchResult list with the files they have locally that contain the keyword
     */
    public void broadcastWordSearchMessageRequest(String keyword) {
        for (SubNode peer : peers) peer.sendWordSearchMessageRequest(keyword);
    }

    public void downloadFiles(List<List<FileSearchResult>> filesToDownload) {
        for (List<FileSearchResult> file : filesToDownload) {
            //Check if it's already downloading the file
            if (downloadManagers.containsKey(file.get(0).getHash())) continue;

            // Check if already has the file in the directory
            if (hasFileWithHash(file.get(0).getHash())) continue;
            FileSearchResult example = file.get(0);
            System.out.println(
                getAddressAndPortFormated() + "Request file: " + example
            );
            DownloadTasksManager downloadManager = new DownloadTasksManager(
                this,
                file
            );
            downloadTaskManagersThreadPool.execute(downloadManager);
            downloadManagers.put(example.getHash(), downloadManager);
        }
    }

    /*
     * Loads the hashes of the files in the working directory
     * So that there's no need to always be calculating the hashes when a file's hash is needed
     * In this project, the hashes are used to identify the files
     */
    public void loadHashes() {
        if (folder == null || !folder.exists() || !folder.isDirectory()) return;

        File[] files = folder.listFiles();

        for (File file : files) {
            hashes.put(
                file.getAbsolutePath(),
                Utils.calculateFileHash(file.getAbsolutePath())
            );
        }
    }


    /*
     * Checks if there are any hashes that are not in the working directory
     * If there are, it removes them from the hash map
     */
    public synchronized void checkMissingFiles() {
        File[] files = folder.listFiles();
        Set<String> filesPaths = hashes.keySet();

        // Create a set of existing file paths
        Set<String> existingFilePaths = new HashSet<>();
        if (files != null) {
            for (File file : files) {
                existingFilePaths.add(file.getAbsolutePath());
            }
        }

        // Check for missing paths
        for (String path : filesPaths) {
            if (!existingFilePaths.contains(path)) {
                hashes.remove(path);
            }
        }
    }

    /*
     * Returns the hash of a file
     * If the hash is not in the hash map, it calculates the hash and adds it to the map
     */
    public int getHash(String filePath) {
        checkMissingFiles();
        if (hashes.containsKey(filePath)) {
            return hashes.get(filePath);
        } else {
            return Utils.calculateFileHash(filePath);
        }
    }

    /*
     * Checks if a file with a given hash exists in the working directory
     */
    public boolean hasFileWithHash(int hash) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return false;
        }

        File[] files = folder.listFiles();

        for (File file : files) {
            Integer fileHash = hashes.get(file.getAbsolutePath());

            if (fileHash != null && fileHash.equals(hash)) {
                return true;
            }
        }

        return false;
    }

    // Returns the first request in the list of requests to process
    public synchronized FileBlockRequestMessage getBlockRequest()
        throws InterruptedException {
        if (blocksToProcess.isEmpty()) wait();
        try {
            return blocksToProcess.remove(0);
        } catch (Exception e) {}
        return null;
    }

    // Add a new request to the list of requests to process
    public synchronized void addBlockRequest(FileBlockRequestMessage request) {
        blocksToProcess.add(request);
        notify();
    }

    // Remove a request from the list of requests to process
    public void removeDownloadProcess(int hash) {
        downloadManagers.remove(hash);
    }

    // Returns the download process of a file
    public Map<String, Integer> getDownloadProcess(int hash) {
        return downloadManagers.get(hash).getDownloadProcess();
    }

    /*
     *  Add an answer to a download request
     * It uses the address and port of who sent the answer,
     * to know how many answers has each peer sent
     */
    public void addDownloadAnswer(
        int hash,
        InetAddress address,
        int port,
        FileBlockAnswerMessage answer
    ) {
        if (downloadManagers.get(hash) == null) return;
        downloadManagers.get(hash).addDownloadAnswer(answer);
        downloadManagers
            .get(hash)
            .addNumberOfDownloadsForPeer(
                address.getHostAddress() + ":" + port,
                1
            );
    }

    // Remove a peer from the list of peers
    public void removePeer(SubNode peer) {
        peers.remove(peer);
        int port = Utils.isValidPort(peer.getSocket().getPort())
            ? peer.getSocket().getPort()
            : peer.getOriginalBeforeOSchangePort();
        System.out.println(
            getAddressAndPortFormated() +
            "Removed connection with: " +
            peer.getSocket().getInetAddress().getHostAddress() +
            "::" +
            port
        );
    }

    public File getFolder() {
        return folder;
    }

    public GUI getGUI() {
        return gui;
    }

    public int getId() {
        return nodeId;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public Set<SubNode> getPeers() {
        return peers;
    }

    public String getAddressAndPort() {
        return address.getHostAddress() + ":" + port;
    }

    public String getAddressAndPortFormated() {
        return "[" + address.getHostAddress() + ":" + port + "]";
    }

    @Override
    public String toString() {
        return "Node " + address + " " + port;
    }
}
