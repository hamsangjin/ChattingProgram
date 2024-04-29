package com.example.chatting;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) {
        String hostName = "localhost";
        int portNumber = 12345;

        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;
        try{
            socket = new Socket(hostName, portNumber);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner stdIn = new Scanner(System.in);

            while(true){
                if (checkNickname(stdIn, out, in)) break;
            }

            Thread readThread = new Thread(new ServerMessageReader(in));
            readThread.start();

            handleClientCommand(stdIn, out);

            disconnectedFromServer(in);

            in.close();
            out.close();
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void disconnectedFromServer(BufferedReader in) {
        try {
            String serverLine;
            while (!(serverLine = in.readLine()).endsWith("종료했습니다.")) {
                System.out.println(serverLine);
            }
        } catch (Exception e) {}
    }

    private static void handleClientCommand(Scanner stdIn, PrintWriter out) {
        String userInput;

        while (true) {
            userInput = stdIn.nextLine();

            if ("/bye".equals(userInput)) {
                out.println(userInput);
                break;
            } else  out.println(userInput);
        }
    }

    private static boolean checkNickname(Scanner stdIn, PrintWriter out, BufferedReader in) throws IOException {
        System.out.print("닉네임 입력: ");
        String nickname = stdIn.nextLine();
        out.println(nickname);
        String res = in.readLine();

        if(res.equals("y"))     return true;
        else System.out.println("중복된 닉네임입니다.");
        return false;
    }
}