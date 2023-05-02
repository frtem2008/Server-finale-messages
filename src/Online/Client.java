package com.livefish.Online;

import java.io.*;

/**
 * Connected client representation type
 */
public class Client implements Closeable {
    /**
     * Unique phone id (from registration or login)
     * For server
     */
    public final int id;
    public final ClientRoot root;
    public final Thread clientThread;
    public ClientDataState dataState;
    /**
     * Connection to communicat through
     */
    private final Connection connection;

    public Client(Connection connection) {
        this.clientThread = null;
        this.connection = connection;
        this.root = ClientRoot.UNAUTHORIZED;
        this.id = -1;
        this.dataState = ClientDataState.STRING_DATA;
    }

    public Client(Connection connection, int id, ClientRoot root, Thread clientThread) {
        this.connection = connection;
        this.id = id;
        this.root = root;
        this.clientThread = clientThread;
        this.dataState = ClientDataState.STRING_DATA;
    }

    @Override
    public String toString() {
        if (root == ClientRoot.ADMIN)
            return "Admin{id=" + id + "}(" + getIp() + ")";
        else if (root == ClientRoot.CLIENT)
            return "Client{id=" + id + "}(" + getIp() + ")";
        else
            return "Unauthorized client(" + getIp() + ")";
    }

    public String getIp() {
        return connection.getIp();
    }

    public boolean isAdmin() {
        return root == ClientRoot.ADMIN;
    }

    public boolean isUnauthorized() {
        return root == ClientRoot.UNAUTHORIZED;
    }

    public boolean isClient() {
        return root == ClientRoot.CLIENT;
    }

    public int getId() {
        return id;
    }

    public String readLine() throws IOException {
        if (dataState == ClientDataState.STRING_DATA)
            return connection.readLine();
        throw new IllegalStateException("Attempted to read line not in string data mode. Current mode: " + dataState.toString());
    }

    public void writeLine(String msg) throws IOException {
        if (dataState == ClientDataState.STRING_DATA)
            connection.writeLine(msg);
        else
            throw new IllegalStateException("Attempted to write line not in string data mode. Current mode: " + dataState.toString());
    }

    public void writeLong(Long l) throws IOException {
        if (dataState == ClientDataState.FILE_DATA)
            connection.writeLong(l);
        else
            throw new IllegalStateException("Attempted to write long not in file data mode. Current mode: " + dataState.toString());
    }

    public void writeBytes(byte[] bytes, int offset, int len) throws IOException {
        if (dataState == ClientDataState.FILE_DATA)
            connection.writeBytes(bytes, offset, len);
        else
            throw new IllegalStateException("Attempted to write bytes not in file data mode. Current mode: " + dataState.toString());
    }

    public void sendFile(File file, String savename) throws IOException {
        int bytes;
        FileInputStream fileInputStream = new FileInputStream(file);

        connection.writeLine(savename);
        dataState = ClientDataState.FILE_DATA;
        connection.writeLong(file.length());

        byte[] buffer = new byte[4 * 1024];
        while ((bytes = fileInputStream.read(buffer)) != -1)
            connection.writeBytes(buffer, 0, bytes);

        fileInputStream.close();
        dataState = ClientDataState.STRING_DATA;
    }

    public Long readLong() throws IOException {
        if (dataState == ClientDataState.FILE_DATA)
            return connection.readLong();
        else
            throw new IllegalStateException("Attempted to read long not in file data mode. Current mode: " + dataState.toString());
    }

    public int readBytes(byte[] buf, int offset, int len) throws IOException {
        if (dataState == ClientDataState.FILE_DATA)
            return connection.readBytes(buf, offset, len);
        else
            throw new IllegalStateException("Attempted to read bytes not in file data mode. Current mode: " + dataState.toString());
    }

    public void readFile(File to, long size) throws IOException {
        int bytes;
        FileOutputStream fileOutputStream = new FileOutputStream(to);

        dataState = ClientDataState.FILE_DATA;

        byte[] buffer = new byte[4 * 1024];

        while (size > 0 && (bytes = connection.readBytes(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
            fileOutputStream.write(buffer, 0, bytes);
            size -= bytes;
        }

        fileOutputStream.close();
        dataState = ClientDataState.STRING_DATA;
    }

    @Override
    public void close() throws IOException {
        if (dataState == ClientDataState.FILE_DATA)
            throw new IllegalStateException("Can not close the socket: client is transferring a file!");
        if (clientThread != null)
            clientThread.interrupt();
        connection.close();
    }
}
