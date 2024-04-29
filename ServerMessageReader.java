package com.example.chatting;

import java.io.*;

public class ServerMessageReader implements Runnable {
    private BufferedReader in;

    public ServerMessageReader(BufferedReader in) {
        this.in = in;
    }

    @Override
    public void run() {
        try {
            String serverLine;
            while ((serverLine = in.readLine()) != null) {
                System.out.println(serverLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

