package com.example.chatting;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatThread extends Thread {
    private Socket socket;
    private String nickname;
    private Map<String, PrintWriter> chatClients;
    private Map<Integer, Set<String>> chatRooms;
    private Map<Integer, List<String>> chatHistory;
    private int currentRoom = -1;

    private BufferedReader in;
    PrintWriter out;

    public ChatThread(Socket socket, Map<String, PrintWriter> chatClients, Map<Integer, Set<String>> chatRooms, Map<Integer, List<String>> chatHistory) {
        this.socket = socket;
        this.chatClients = chatClients;
        this.chatRooms = chatRooms;
        this.chatHistory = chatHistory;

        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            synchronized (chatClients) {
                checkNickname(chatClients);
            }
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            handleServerCommand();
        } catch (IOException e) {
            System.out.println(e);
            e.printStackTrace();
        } finally {
            synchronized (chatClients) {
                chatClients.remove(nickname);
            }
            System.out.println(nickname + " 닉네임의 사용자가 연결을 종료했습니다.");
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void checkNickname(Map<String, PrintWriter> chatClients) throws IOException {
        while(true){
            nickname = in.readLine();
            if(!chatClients.containsKey(nickname)) {
                out.println("y");
                chatClients.put(this.nickname, out);
                System.out.println(nickname + " 닉네임의 사용자가 연결했습니다.");
                displayCommand();
                break;
            } else{
                out.println("n");
            }
        }
    }

    private void handleServerCommand() throws IOException {
        String msg;
        while ((msg = in.readLine()) != null) {
            if ("/bye".equalsIgnoreCase(msg)){
                disconnectedUser();
                break;
            }
            else if (msg.equalsIgnoreCase("/create"))       createRoom();
            else if (msg.equalsIgnoreCase("/roomlist"))         listRooms();
            else if (msg.startsWith("/join"))                           joinRoom(msg);
            else if (msg.equalsIgnoreCase("/exit"))         exitRoom();
            else if (msg.equalsIgnoreCase("/users"))        listConnectedUsers();
            else if (msg.equalsIgnoreCase("/roomusers"))    listUsersInRoom();
            else if (msg.startsWith("/whisper"))                        sendWhisperMessage(msg);
            else if (msg.startsWith("/command"))                        displayCommand();
            else if(msg.startsWith("/"))                                out.println("잘못된 명령어입니다.");

            else if(!msg.startsWith("/") && currentRoom != -1)          sendMessageToRoomMembers(msg);
            else                                                        out.println("채팅방에 입장한 후 메시지를 보내주세요");
        }
    }

    private void displayCommand(){
        out.println("====== 명령어 모음 ======");
        out.println("방 생성: /create");
        out.println("방 나가기: /exit");
        out.println("방 입장: /join [방번호]");
        out.println("현재 방 유저 목록: /roomusers");
        out.println("채팅방 목록: /roomlist");
        out.println("전체 유저 목록: /users");
        out.println("특정 유저에게 메시지 보내기: /whisper [대상 닉네임] [보낼 메시지]");
        out.println("명령어 보기: /command");
        out.println("접속종료: /bye");
        out.println();
    }

    private void disconnectedUser(){
        if(currentRoom != -1)   exitRoom();

        out.println(nickname + "님이 연결을 종료했습니다.");
    }

    private void createRoom(){
        if(currentRoom == -1){
            synchronized (chatRooms) {
                int newRoomNumber = ChatServer.incrementRoomCount();
                chatRooms.put(newRoomNumber, new HashSet<>());
                synchronized (chatHistory) {
                    chatHistory.put(newRoomNumber, new ArrayList<>());
                }
                out.println(newRoomNumber + "번 채팅방이 생성되었습니다.");
                joinRoom("/join "+newRoomNumber);
            }
        } else  out.println("해당 채팅방에서 나온 후 다시 실행해주세요");
    }

    private void listRooms(){
        if(chatRooms.size() > 0) {
            synchronized (chatRooms) {
                out.println("======= 채팅방 목록 =======");
                for (int roomNumber : chatRooms.keySet()) {
                    out.println("- " + roomNumber + "번 방");
                }
            }
        } else  out.println("현재 채팅방이 존재하지 않습니다.");
    }

    private void joinRoom(String msg){
        int roomNumber = -1;
        try {
            roomNumber = Integer.parseInt(msg.split(" ")[1]);

            synchronized (chatRooms) {
                if (chatRooms.containsKey(roomNumber)) {
                    if(roomNumber == currentRoom){
                        out.println("이미 해당 채팅방에 있습니다.");
                    } else {
                        if (currentRoom != -1) exitRoom();

                        chatRooms.get(roomNumber).add(nickname);
                        currentRoom = roomNumber;
                        out.println(roomNumber + "번 채팅방에 입장했습니다.");

                        sendNoticeToRoomMembers(nickname + "님이 " + roomNumber + "번 채팅방에 입장했습니다.");
                    }
                } else  out.println(roomNumber + "번 채팅방을 찾을 수 없습니다.");
            }
        } catch (Exception e){
            out.println("/join [방번호]와 같은 형식으로 입력해주세요.");
        }
    }

    private void exitRoom(){
        synchronized (chatRooms) {
            if (currentRoom != -1) {
                if (chatRooms.get(currentRoom).size() > 1)  sendNoticeToRoomMembers(nickname +"님이 "+ currentRoom + "번 방에서 퇴장하셨습니다.");
                chatRooms.get(currentRoom).remove(nickname);
                out.println(currentRoom + "번 방에서 퇴장하셨습니다.");
                if (chatRooms.get(currentRoom).isEmpty()) {
                    chatRooms.remove(currentRoom);
                    out.println(currentRoom + "번 방이 삭제되었습니다.");
                    saveChatHistoryToFile(currentRoom);
                }
                currentRoom = -1;
            } else out.println("현재 참여중인 채팅방이 없습니다.");
        }
    }

    private void listConnectedUsers() {
        if(chatClients.isEmpty()) out.println("현재 접속중인 유저가 없습니다.");
        else {
            synchronized (chatClients) {
                out.println("전체 유저 목록");
                for (String key : chatClients.keySet()) out.println("- 닉네임: " + key);
            }
        }
    }

    private void listUsersInRoom() {
        if(currentRoom != -1){
            out.println(currentRoom + "번 방의 유저 목록");
            for(String user: chatRooms.get(currentRoom)){
                out.println("- 닉네임: " + user);
            }
        } else  out.println("현재 참여중인 채팅방이 없습니다.");
    }

    private void sendWhisperMessage(String command) {
        String target = "";
        String msg = "";
        try {
            target = command.split(" ")[1];
            msg = command.split(" ")[2];
        } catch (Exception e){
            out.println("/whisper [대상 닉네임] [보낼 메시지]와 같은 형식으로 입력해주세요.");
            return;
        }

        if(!target.equals(nickname)) {
            synchronized (chatClients) {
                boolean flag = false;

                for (String key : chatClients.keySet()) {
                    if (key.equals(target)) flag = true;
                }

                if (flag) chatClients.get(target).println(nickname + "에게 \"" + msg + "\"라는 메시지가 왔습니다.");
                else out.println("해당 유저가 존재하지 않습니다.");
            }
        }
        else out.println("자기 자신에게 귓속말을 보낼 수 없습니다.");
    }

    private void sendMessageToRoomMembers(String message) {
        Set<String> roomMembers = chatRooms.get(currentRoom);

        synchronized (chatHistory) {
            chatHistory.get(currentRoom).add(nickname + ": " + message);
        }
        for (String member : roomMembers) {
            PrintWriter memberOut = chatClients.get(member);
            if (memberOut != null)  memberOut.println(currentRoom+ "번 방 " + nickname + "님의 메시지: " + message);
        }
    }

    private void sendNoticeToRoomMembers(String message) {
        Set<String> roomMembers = chatRooms.get(currentRoom);

        for (String member : roomMembers) {
            PrintWriter memberOut = chatClients.get(member);
            if (memberOut != null)  memberOut.println(currentRoom + "번방 공지: " + message);
        }
    }

    private void saveChatHistoryToFile(int roomId) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String timestamp = now.format(formatter);
        String fileName = "log/"+ "room_" + roomId + "_" + timestamp + "_log.txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            synchronized (chatHistory) {
                for (String message : chatHistory.get(roomId)) {
                    writer.write(message);
                    writer.newLine();
                }
            }
            System.out.println(fileName + "라는 파일명으로 " + roomId + "번방의 채팅 내역을 저장했습니다.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
