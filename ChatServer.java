package com.example.chatting;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ChatServer {
    private static int roomCount = 0;
    public static void main(String[] args) {

        try (ServerSocket serverSocket = new ServerSocket(12345);) {
            System.out.println("서버가 시작되었습니다.");
            Map<String, PrintWriter> chatClients = new HashMap<>();
            Map<Integer, Set<String>> chatRooms = new HashMap<>();
            Map<Integer, List<String>> chatHistory = new HashMap<>();

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("연결된 클라이언트 IP 주소: " + socket.getLocalAddress());

                new ChatThread(socket, chatClients, chatRooms, chatHistory).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized int incrementRoomCount() {
        return ++roomCount;
    }
}