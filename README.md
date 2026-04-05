# TorrentFileSharing

TorrentFileSharing is a local peer-to-peer file sharing app used to test node-to-node search and download behavior without a central server.

## Overview

TorrentFileSharing is a project designed to test the functionality of a distributed file-sharing system locally. It consists of multiple nodes that can request files from connected nodes, implementing a peer-to-peer (P2P) architecture. Each node connects directly to others without the need for a central server, allowing for efficient file sharing and testing of ideas.

## Features

### Best Features

- **Simultaneous Download**: Connect to multiple nodes that have the desired file, allowing for parts of the file to be downloaded concurrently.
- **Multi-threading and Synchronization**: Nodes can download files from multiple neighbors while simultaneously responding to requests from other nodes.
- **Safe Download**: Downloads continue until completion, even if one of the nodes goes down during the process.
- **Anti-download Spam**: Prevents duplicate downloads and ensures that files are downloaded only once.

### Basic Features

- **Architecture**: P2P, where each node communicates directly with known nodes without a central server.
- **Connection**: Users can manually connect nodes through the GUI by entering the corresponding port.
- **Search and Download Files**: Search for files in connected nodes and request their download.

## Layout

- `Code/src/`: Java source code, organized by package.
- `Code/bin/`: Compiled `.class` files created by the run scripts.
- `files/dl{id}`: Source folders for each node's files at the repository root.
- `Code/files/dl{id}`: Runtime folders created and managed by the app for each node.

At runtime, node `id` works inside `Code/files/dl{id}`. The application copies the matching contents from `files/dl{id}` when a node starts.

## Requirements

- Java JDK 11 or newer.
- Bash-compatible shell.

Check your Java installation with:

```bash
java -version
javac -version
```

## Run A Single Node

From the repository root, start one GUI node with:

```bash
./run_project.sh
```

Then enter a single node ID, such as `1`. The app will:

1. Create `Code/files/dl1` if needed.
2. Copy the contents of `files/dl1` when that folder exists.
3. Launch the GUI and listen on port `8080 + id`.

## Run Test Mode

From the repository root, start the test harness with:

```bash
./test_project.sh
```

Then enter one or more node IDs separated by spaces, such as `1 2 3`, and choose a mode:

1. Create a network of nodes and connect them.
2. Create a network, connect nodes, and search in the first node.
3. Create a network, connect nodes, and search in all nodes.
4. Create a network, connect nodes, and download files in the last node.
5. Create a network, connect nodes, and download files in every node.

## Add Node Data

1. Create or update the matching folder under the repository root, for example `files/dl1`.
2. Put the files for that node inside the folder.
3. Re-run `./run_project.sh` or `./test_project.sh`.

## Troubleshooting

- If you see `Permission denied`, make the scripts executable:

```bash
chmod +x run_project.sh test_project.sh
```

- If compilation fails, verify that `javac` is installed and the Java files in `Code/src` compile cleanly.
- If the app says the default file structure is missing, make sure the repository root contains the `files/` directory with `dl{id}` folders.

## License

This project is licensed under the MIT License.
