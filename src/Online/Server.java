package Online;

import IO.Console.Logger;
import IO.Console.OutputColor;
import IO.Files.FileLoader;
import IO.Files.FileLogger;
import IO.Files.LogFileType;
import IO.Files.PropertyReader;
import Online.Messages.Message;
import Online.Messages.MessagePayloadObjects.Admin.PayloadNewRequestData;
import Online.Messages.MessagePayloadObjects.Client.PayloadDoneRequestData;
import Online.Messages.MessagePayloadObjects.Client.PayloadToDoRequestData;
import Online.Messages.MessagePayloadObjects.Common.PayloadLoginData;
import Online.Messages.MessagePayloadObjects.Common.PayloadLoginResult;
import Online.Messages.MessageType;
import Online.Messages.PayloadStringData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    private static Map<Integer, Client> allConnected;
    private static Map<Integer, Client> connectedAdmins;
    private static Map<Integer, Client> connectedClients;
    private static Map<Integer,Request> requestsInProgress;

    private static Set<Integer> allRegisteredIds;

    private static List<Thread> clientThreads;
    private static Scanner input;

    private static Logger logger;
    private static FileLogger fileLogger;

    private static ServerThreadExecutor exec;

    private static PropertyReader propsReader;

    public static void start() {
        initPropertiesReader();

        initLogger();
        initFileLogger();

        createCollections();
        readAllRegisteredIds();
        setRequestIdCount();

        setupThreadExecutor();

        startConsole();
        startServer();
    }

    private static void initLogger() {
        boolean useColorText = Boolean.parseBoolean(
                propsReader.getProperty(propsReader.updateAndGetConfigFile(), "colored_output"));
        logger = Logger.getInstance();
        if (useColorText)
            logger.enableColoredText();
        else
            logger.disableColoredText();
        logger.addPrintColor("Info", OutputColor.RESET);
        logger.addPrintColor("Connection", OutputColor.GREEN);
        logger.addPrintColor("Disconnection", OutputColor.CYAN);
        logger.addPrintColor("Registration", OutputColor.YELLOW);
        logger.addPrintColor("File creation", OutputColor.BLUE);
        logger.addPrintColor("Error", OutputColor.RED);
        logger.addPrintColor("Wrong data", OutputColor.PURPLE);
        logger.addPrintColor("Server state", OutputColor.GREEN);
    }

    private static void initFileLogger() {
        logger.setOutputColor("File creation");
        logger.print("Attempting to create files:\n");
        fileLogger = new FileLogger("logFolder");
        logger.print("Log dir created in: " + fileLogger.getLogDirPath() + "\n");
        fileLogger.addLogFile("Request file", "req.dat", LogFileType.FINISHED_REQUESTS);
        fileLogger.printFileInfo(logger::print, "Request file");
        fileLogger.addLogFile("Command id file", "commandIDs.dat", LogFileType.COMMAND_IDS);
        fileLogger.printFileInfo(logger::print, "Command id file");
        fileLogger.addLogFile("Connections file", "connectedClients.dat", LogFileType.CONNECTIONS);
        fileLogger.printFileInfo(logger::print, "Connections file");
        fileLogger.addLogFile("Turning on-off file", "on-off.dat", LogFileType.ON_OFF);
        fileLogger.printFileInfo(logger::print, "Turning on-off file");
        fileLogger.addLogFile("Id file", "ids.dat", LogFileType.SAVED_IDS);
        fileLogger.printFileInfo(logger::print, "Id file");
        logger.setDefaultOutputColor();
    }

    private static void initPropertiesReader() {
        propsReader = new PropertyReader("config.dat");
    }

    private static void createCollections() {
        connectedClients = new HashMap<>();
        allConnected = new HashMap<>();
        connectedAdmins = new HashMap<>();
        connectedClients = new HashMap<>();
        allRegisteredIds = new HashSet<>();
        requestsInProgress = new HashMap<>();
        clientThreads = new ArrayList<>();
    }

    private static void setupThreadExecutor() {
        int threadCount = Integer.parseInt(
                propsReader.getProperty(propsReader.updateAndGetConfigFile(), "server_max_threads"));

        if (threadCount == 0) {
            threadCount = Runtime.getRuntime().availableProcessors() * Integer.parseInt(
                    propsReader.getProperty(propsReader.updateAndGetConfigFile(), "server_cores_multiplier"));
        }
        exec = new ServerThreadExecutor(threadCount, threadCount, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    private static void setRequestIdCount() {
        String ids = FileLoader.loadFile(fileLogger.getLogFile("Command id file"));

        if (ids.trim().equals(""))
            Request.setRequestCount(1);
        else {
            String[] idSplit = ids.split("\n");
            ArrayList<Integer> commandIds = new ArrayList<>();

            for (String value : idSplit)
                commandIds.add(Integer.parseInt(value.trim()));
            if (commandIds.size() > 0)
                Request.setRequestCount(commandIds.get(commandIds.size() - 1));
            else
                Request.setRequestCount(1);
        }
    }

    private static void readAllRegisteredIds() {
        String ids = FileLoader.loadFile(fileLogger.getLogFile("Id file"));
        String[] idSplit = ids.split("\n");
        if (idSplit[0].equals("")) {
            logger.print("No id input to parse\n", "Info");
            return;
        }
        logger.print("Ids read from file: ", "Info");
        for (int i = 0; i < idSplit.length; i++) {
            allRegisteredIds.add(Integer.parseInt(idSplit[i].trim()));

            if (i != idSplit.length - 1)
                logger.print(Integer.parseInt(idSplit[i].trim()) + ", ");
            else
                logger.print(String.valueOf(Integer.parseInt(idSplit[i].trim())));
        }
        logger.println("", "Info");
    }

    private static void startConsole() {
        logger.println("Console started", "Info");
        input = new Scanner(System.in);
    }

    private static void startServer() {
        final int SERVER_PORT = Integer.parseInt(
                propsReader.getProperty(propsReader.getConfigFile(), "server_port"));
        try (ServerSocket server = new ServerSocket(SERVER_PORT)) {
            logger.println("Server started on port " + server.getLocalPort(), "Server state");
            logger.print("Waiting for clients to connect", "Server state");

            writeOnOff("on");

            while (!exec.isShutdown()) {
                Connection connection = new Connection(server);
                Runnable clientThread;

                clientThread = () -> {
                    logger.println("Client connected: " + connection.getIp(), "Connection");
                    logger.println("Waiting for data...", "Info");
                    communicationLoop(connection);
                    // TODO: 01.05.2023 Process connection data
                };
                exec.execute(clientThread, "Client: " + connection.getIp());
            }
        } catch (RejectedExecutionException e) {
            if (!exec.isShutdown()) {
                logger.print("Failed to start new client thread task!", "Error");
                e.printStackTrace();
            }
        } catch (NullPointerException | IOException e) {
            logger.print("Failed to start a server:\n_________________________", "Error");
            e.printStackTrace();
        } finally {
            stopServer();
        }
    }

    private static void stopServer() {
        logger.print("Shutting down...", "Disconnection");
        writeOnOff("off");

        ArrayList<Client> clients = new ArrayList<>(connectedClients.values());
        for (Client client : clients) {
            // TODO: 01.05.2023 Send shutdown message
            disconnectClient(client);
        }
        connectedClients.clear();
        clients.clear();
        logger.println("Press enter to stop the server", "Info");
        exec.shutdown();
        input.close();
        System.exit(0);
    }

    private static synchronized void disconnectClient(Client client) {
        if (client == null) {
            logger.println("Client to disconnect: wrong data(client == null)!", "Error");
        } else
            try {
                // TODO: 01.05.2023
                connectedClients.remove(client);
                writeConnection(client.id, false);

                client.close();
                client.clientThread.interrupt();

                if (client.isUnauthorized())
                    logger.println("Unauthorized client from " + client.getIp() + " disconnected", "Disconnection");
                else if (client.isAdmin())
                    logger.println("Admin with id " + client.id + " disconnected", "Disconnection");
                else
                    logger.println("Client with id " + client.id + " disconnected", "Disconnection");
            } catch (IOException e) {
                e.printStackTrace();
                logger.println("FAILED TO DISCONNECT PLAYER: " + client, "Error");
            }
    }

    private static void closeConnection(Connection connection) {
        if (connection == null) {
            logger.println("Failed to close null collection!", "Error");
        } else {
            try {
                logger.println("Connection: " + connection + " closed!", "Disconnection");
                connection.close();
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String getServerIp() {
        try {
            URL awsHost = new URL("https://checkip.amazonaws.com");
            BufferedReader in = new BufferedReader(new InputStreamReader(awsHost.openStream()));
            return in.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return "UNABLE TO GET IP!!!";
        }
    }

    private static Client login(Connection unauthorized) throws RuntimeException {
        boolean loginFailed = true;
        Message resMsg = new Message(MessageType.LOGIN_RESULT, new PayloadLoginResult());
        PayloadLoginData loginData = new PayloadLoginData();
        do {
            try {
                Message msg = unauthorized.readMessage();
                if (msg.type != MessageType.LOGIN_DATA) {
                    unauthorized.writeMessage(Message.ErrorMessage("LOGIN NEEDED!"));
                    System.out.println("Client: " + unauthorized.getIp() + " failed to log in: different message type: " + msg.type);
                    return null;
                }

                loginData = (PayloadLoginData) msg.payload;
                if (loginData.id <= 0) {
                    if (allRegisteredIds.contains(-loginData.id)) {
                        logger.print("The user with id " + (-loginData.id) + " already exists", "Wrong data");
                        resMsg.payload = new PayloadLoginResult(PayloadLoginResult.Result.REG_FAILED_EXISTS, 0);
                        unauthorized.writeMessage(resMsg);
                        continue;
                    }

                    resMsg.payload = new PayloadLoginResult(PayloadLoginResult.Result.LOG_SUCCESS, -loginData.id);

                    String register = "Successfully registered new user with root " + loginData.root + " and id: " + (-loginData.id);
                    fileLogger.logToAll("Id file", String.valueOf(-loginData.id));
                    logger.print(register, "Registration");
                    loginFailed = false;
                } else {
                    if (allRegisteredIds.contains(loginData.id)) {
                        if (!allConnected.containsKey(loginData.id)) {
                            loginFailed = false;
                            resMsg.payload = new PayloadLoginResult(PayloadLoginResult.Result.LOG_SUCCESS, loginData.id);
                        } else {
                            logger.print("Failed to login a user with id " + loginData.id + ": user with this id has already logged in", "Wrong data");
                            resMsg.payload = new PayloadLoginResult(PayloadLoginResult.Result.LOG_FAILED_ONLINE, 0);
                        }
                    } else {
                        logger.print("Failed to login a user with id " + loginData.id + ": this id is free", "Wrong data");
                        resMsg.payload = new PayloadLoginResult(PayloadLoginResult.Result.LOG_FAILED_FREE, 0);
                    }
                }
                unauthorized.writeMessage(resMsg);
            } catch (IOException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                return null;
            }
        } while (loginFailed);

        Client client = null;
        if (loginData.id != 0) {
            client = new Client(unauthorized, Math.abs(loginData.id), loginData.root, Thread.currentThread());
        }
        return client;
    }

    private static void communicationLoop(Connection connection) {
        Client client = login(connection);
        if (client == null || client.isUnauthorized() || client.id <= 0) {
            closeConnection(connection);
            return;
        }

        writeConnection(client.id, true);
        clientThreads.add(Thread.currentThread());

        connectedClients.put(client.id, client);
        if (client.isAdmin()) {
            connectedAdmins.put(client.id, client);
            logger.print("Admin connected: ip address is " + connection.getIp() + ", unique id is " + client.id, "Connection");
        } else if (client.isClient()) {
            connectedClients.put(client.id, client);
            logger.print("Client connected: ip address is " + connection.getIp() + ", unique id is " + client.id, "Connection");
        }
        allRegisteredIds.add(client.id);

        try {
            while (!client.clientThread.isInterrupted()) {
                Message message = client.readMessage();
                if (!handleCommonMessage(message, client)) {
                    if (client.isAdmin()) {
                        handleAdminMessage(message, client);
                    } else if (client.isClient()) {
                        handleClientMessage(message, client);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            disconnectClient(client);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            disconnectClient(client);
            throw new RuntimeException(e);
        }
    }

    private static boolean handleCommonMessage(Message msg, Client client) throws IOException {
        switch (msg.type) {
            case INVALID -> {
                client.writeMessage(Message.ErrorMessage("INVALID MESSAGE SENT!"));
                System.out.println("Client: " + client + " sent invalid message with payload: " + msg.payload);
            }
            case ERROR -> {
                disconnectClient(client);
                throw new RuntimeException(((PayloadStringData) msg.payload).str);
            }
            case INFO -> {
                String info = ((PayloadStringData) msg.payload).str;
                // TODO: 02.05.2023 Process info from client
            }
            // invalid messages here
            case LOGIN_DATA -> {
                client.writeMessage(Message.ErrorMessage("ALREADY LOGGED IN!"));
                System.out.println("Client: " + client + " sent login data while being logged in!");
            }
            case LOGIN_RESULT -> {
                client.writeMessage(Message.ErrorMessage("ALREADY LOGGED IN!"));
                System.out.println("Client: " + client + " sent login result data while being logged in!");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static boolean validateSelfSendId(Client admin, int id) throws IOException {
        if (admin.id == id) {
            logger.print("Attempt to send request on itself on id: " + admin.id, "Wrong data");
            admin.writeMessage(new Message(MessageType.SELF_SEND_REQ_ERROR, new PayloadStringData("Error send request on self id")));
            return false;
        }
        return true;
    }

    private static boolean validateAnotherAdminSendId(Client admin, int id) throws IOException {
        if (connectedAdmins.containsKey(id)) {
            logger.print("Attempt to send request to admin with id: " + id, "Wrong data");
            admin.writeMessage(new Message(MessageType.SELF_SEND_REQ_ERROR, new PayloadStringData("Error send request to another admin with id " + id)));
            return false;
        }
        return true;
    }

    private static void sendAdminRequest(Client admin, PayloadNewRequestData reqData) throws IOException {
        if (!connectedClients.containsKey(reqData.targetId)) {
            logger.print("Sending error: system didn't find an online target client with id " + reqData.targetId, "Error");
            admin.writeMessage(new Message(MessageType.OFFLINE_TARGET_SEND_REQ_ERROR, new PayloadStringData("Client with id " + reqData.targetId + " is offline")));
        } else {
            Request thisReq = new Request(admin.id, reqData.targetId, reqData.command, reqData.args);
            requestsInProgress.put(thisReq.id, thisReq);
            connectedClients.get(reqData.targetId)
                    .writeMessage(new Message(MessageType.TO_DO_REQUEST_DATA,
                            new PayloadToDoRequestData(thisReq.id, reqData.command, reqData.args)));
        }
    }

    private static void handleAdminMessage(Message msg, Client admin) throws IOException {
        switch (msg.type) {
            case NEW_REQUEST_DATA -> {
                PayloadNewRequestData reqData = (PayloadNewRequestData) msg.payload;
                if (!validateSelfSendId(admin, reqData.targetId) || !validateAnotherAdminSendId(admin, reqData.targetId)) {
                    return;
                }
                logger.print("Id to send: " + reqData.targetId, "Default");
                logger.print("Id who sent: " + admin.id, "Default");
                logger.print("Command to send: " + reqData.command, "Default");
                logger.print("Args to send: " + reqData.args, "Default");
                sendAdminRequest(admin, reqData);
            }
            default -> throw new IllegalStateException("Admin sent unexpected message with type: " + msg.type);
        }
    }

    private static void logRequestToFile(Request req) {
        String writeReq;
        LocalDateTime now = LocalDateTime.now();

        String dateToWrite = formatDate(now);
        if (req.equals(Request.ZEROREQUEST)) {
            logger.print("A try to write a zero request into file", "Wrong data");
        } else {
            writeReq = dateToWrite + "$" + req.idA + "$" + req.idC + "$" + req.cmd + "$" + req.args + "$" + req.success;
            fileLogger.logToAll("Request file", writeReq);
        }
    }

    private static void logDoneRequest(Client by, Request executed) {
        logger.print("Client id to send: " + by.id, "Default");
        logger.print("Command id: " + executed.id, "Default");
        logger.print("Admin id to send: " + executed.idA, "Default");
        logger.print("Command to send: " + executed.cmd, "Default");
        logger.print("Args to send: " + executed.args, "Default");
        logger.print("Success to send: " + executed.success, "Default");
        logRequestToFile(executed);
    }

    private static void sendDoneRequest(PayloadDoneRequestData reqData) throws IOException {
        Request executed = requestsInProgress.get(reqData.doneRequestId);
        if (executed == null)
            logger.print("Client " + reqData.targetClientId + " sent a result of nonexistent request", "Wrong data");
        else {
            executed = new Request(executed, reqData.commandResult);
            logDoneRequest(connectedClients.get(reqData.targetClientId), executed);
            requestsInProgress.remove(executed.id);

            Client admin = connectedClients.get(executed.idA);
            if (admin != null) {
                admin.writeMessage(new Message(MessageType.DONE_REQUEST_DATA, new PayloadDoneRequestData(executed.idC, executed.id, executed.success)));
            } else {
                logger.print("Sending error: system didn't find an online admin with id " + executed.idA, "Error");
                Client target = connectedClients.get(executed.idC);
                target.writeMessage(new Message(MessageType.OFFLINE_ADMIN_SEND_REQ_ERROR, new PayloadStringData("No online admin with id: " + executed.idA)));
            }
        }
    }

    private static void handleClientMessage(Message msg, Client client) throws IOException {
        // TODO: 02.05.2023 Get-info command,
        switch (msg.type) {
            case DONE_REQUEST_DATA -> {
                PayloadDoneRequestData doneReqData = (PayloadDoneRequestData) msg.payload;
                sendDoneRequest(doneReqData);
            }
            default -> throw new IllegalStateException("Client sent unexpected message with type: " + msg.type);
        }
    }

    private static void updateIdCommandsFile(Request req) {
        fileLogger.clearAll("Command id file");
        fileLogger.logToAll("Command id file", String.valueOf(req.id));
    }

    private static String formatDate(LocalDateTime date) {
        int year = date.getYear();
        String res;

        String month = date.getMonthValue() < 10 ? "0" + date.getMonthValue() : String.valueOf(date.getMonthValue());
        String day = date.getDayOfMonth() < 10 ? "0" + date.getDayOfMonth() : String.valueOf(date.getDayOfMonth());
        String hours = date.getHour() < 10 ? "0" + date.getHour() : String.valueOf(date.getHour());
        String min = date.getMinute() < 10 ? "0" + date.getMinute() : String.valueOf(date.getMinute());
        String sec = date.getSecond() < 10 ? "0" + date.getSecond() : String.valueOf(date.getSecond());

        res = day + "." + month + "." + year + "[" + hours + ":" + min + ":" + sec + "]";
        return res;
    }

    private static void writeOnOff(String onOff) {
        LocalDateTime now = LocalDateTime.now();

        String normalDate = formatDate(now);
        String toAppend = normalDate + "$" + onOff;
        fileLogger.logToAll(LogFileType.ON_OFF, toAppend);
    }

    private static void writeConnection(int clientID, boolean connected) {
        LocalDateTime now = LocalDateTime.now();
        String normalDate = formatDate(now);
        String toAppend = normalDate + "$" + clientID + "$" + (connected ? 'c' : 'd');
        fileLogger.logToAll("Connections file", toAppend);
    }

    static class ServerThreadExecutor extends ThreadPoolExecutor {
        public ServerThreadExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        @Override
        public void execute(Runnable command) {
            new Thread(command).start();
        }

        public void execute(Runnable command, String name) {
            new Thread(command, name).start();
        }
    }

    static class Request {
        private static AtomicInteger requestCount = new AtomicInteger(0);
        private static Request ZEROREQUEST;

        static {
            requestCount = new AtomicInteger(0);
            ZEROREQUEST = new Request(0, 0, "0", "0");
        }

        public final String cmd;
        public final String args;
        public final String success;
        public final int idA;
        public final int idC;
        public final int id;

        public Request(int idA, int idC, String cmd, String args) {
            this.cmd = cmd;
            this.args = args;
            this.success = "NaN";
            this.idC = idC;
            this.idA = idA;
            requestCount.incrementAndGet();
            this.id = requestCount.get();
        }

        public Request(Request what, String success) {
            this.idA = what.idA;
            this.idC = what.idC;
            this.cmd = what.cmd;
            this.args = what.args;
            this.id = what.id;

            this.success = success;
            updateIdCommandsFile(this);
        }

        public static Request getZEROREQUEST() {
            return ZEROREQUEST;
        }

        public static void setRequestCount(int c) {
            Logger.getInstance().print("Request count set to " + c + "\n", "Info");
            requestCount.set(c);
        }
    }
}