package classes;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import models.EncryptionType;


/**
 * Klasa zarządzająca oknem klienta.
 *
 * @author Wojtek Kolendo
 */

@SuppressWarnings({"Duplicates", "unchecked"})
public class Client {

    private static final int PORT_ADDRESS_CLIENT = 8080;

    /**
     * Klasa odpowiedzialna za komunikację z serwerem
     */
    static class ChatCommunication extends Observable {
        private JSONParser mJSONParser = new JSONParser();
        private Socket mSocket;
        private OutputStream mOutputStream;
        JSONObject mJSONObject = new JSONObject();
        private static final String CRLF = "\r\n";
        private int VALUE_P, VALUE_G, value_a, value_s;
        private double value_A, value_B;
        private boolean DIFFIE_READY = false, INIT_MSG = true;
        private EncryptionType mEncryptionType = EncryptionType.NONE;

        /**
         * Powiadomienie o zmianach obserwującego GUI
         * @param arg obiekt do przekazania
         */
        @Override
        public void notifyObservers(Object arg) {
            super.setChanged();
            super.notifyObservers(arg);
        }

        /**
         * Inicjalizacja socketu i wątku odpowiedzialnego za odbiór wiadomości z serwera.
         *
         * Wątek zajmuje się filtrowaniem odebranych danych ze struktury JSON i wykonanie
         * odpowiedniej akcji. Kiedy klient otrzyma dane {"init":"start"} rozpoczyna
         * implementację protokułu Diffiego-Hellmana, po której zakończeniu ustawia flagę
         * zezwalającą na wysyłanie zaszyfrowanych wiadomości w Base64 oraz dodatkowo wcześniej w
         * szyfsze Cezara, Xor lub żadnym.
         *
         * @param server adres IP
         * @param port
         * @throws IOException
         */
        public void InitSocket(String server, int port) throws IOException {
            mSocket = new Socket(server, port);
            mOutputStream = mSocket.getOutputStream();
            Random generator = new Random();
            value_a = generator.nextInt(10) + 1;

            Thread receivingThread = new Thread() {
                @Override
                public void run() {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null){
                            Object obj;
                            try {
                                obj = mJSONParser.parse(line);
                            } catch (ParseException e) {
                                e.printStackTrace();
                                break;
                            }
                            JSONObject jsonObject = (JSONObject) obj;

                            if (jsonObject.get("msg") != null) {
                                System.out.println("received: " + jsonObject.toString());
                                if (jsonObject.get("from") != null) {
                                    switch (mEncryptionType) {
                                        case XOR: {
                                            String decodedMsg = codeXor((String) jsonObject.get("msg"), value_s);
                                            byte[] base64decoded = Base64.getDecoder().decode(decodedMsg);
                                            String decoded = new String(base64decoded, "utf-8");
                                            notifyObservers("<" + jsonObject.get("from") + "> " + decoded);
                                            break;
                                        }
                                        case CAESAR: {
                                            String decodedMsg = decodeCaesar((String) jsonObject.get("msg"), value_s);
                                            byte[] base64decoded = Base64.getDecoder().decode(decodedMsg);
                                            String decoded = new String(base64decoded, "utf-8");
                                            notifyObservers("<" + jsonObject.get("from") + "> " + decoded);
                                            break;
                                        }
                                        default: {
                                            byte[] base64decoded = Base64.getDecoder().decode((String) jsonObject.get("msg"));
                                            String decoded = new String(base64decoded, "utf-8");
                                            notifyObservers("<" + jsonObject.get("from") + "> " + decoded);
                                            break;
                                        }
                                    }

                                } else {
                                    notifyObservers(jsonObject.get("msg"));
                                }
                            }

                            if (jsonObject.get("p") != null && jsonObject.get("g") != null) {
                                System.out.println("received: " + jsonObject.toString());
                                VALUE_P = (int)(long) jsonObject.get("p");
                                VALUE_G = (int)(long) jsonObject.get("g");
                                if (jsonObject.get("B") != null) {
                                    value_B = (double) jsonObject.get("B");
                                    value_s = (int)(Math.pow(value_B, value_a) % VALUE_P);
                                    DIFFIE_READY = true;
                                    System.out.println("s: " + value_s);
                                }
                                value_A = (Math.pow(VALUE_G, value_a) % VALUE_P);
                                mJSONObject = new JSONObject();
                                mJSONObject.put("A", value_A);
                                mOutputStream.write((mJSONObject.toString() + CRLF).getBytes());
                                mOutputStream.flush();
                            }

                            if (jsonObject.get("B") != null) {
                                System.out.println("received: " + jsonObject.toString());
                                value_B = (double) jsonObject.get("B");
                                value_s = (int)(Math.pow(value_B, value_a) % VALUE_P);
                                System.out.println("s: " + value_s);
                                DIFFIE_READY = true;
                            }

                            if (jsonObject.get("init") != null) {
                                System.out.println("received: " + jsonObject.toString());
                                String init = (String) jsonObject.get("init");
                                if (init.equals("start")) {
                                    System.out.println("start auth");
                                    mJSONObject = new JSONObject();
                                    mJSONObject.put("request", "keys");
                                    mOutputStream.write((mJSONObject.toString() + CRLF).getBytes());
                                }
                            }

                        }
                    } catch (IOException ex) {
                        notifyObservers(ex);
                    }
                }
            };
            receivingThread.start();
        }

        /**
         * Wysłanie tekstu na serwer
         * @param text wysyłany tekst
         */
        private void sendMessage(String text) {
            try {
                if (INIT_MSG || text.startsWith("/quit")) {
                    mJSONObject = new JSONObject();
                    mJSONObject.put("msg", text);
                    mOutputStream.write((mJSONObject.toString() + CRLF).getBytes());
                    mOutputStream.flush();
                    if (INIT_MSG) {
                        INIT_MSG = false;
                    }
                } else if (DIFFIE_READY) {
                    switch (mEncryptionType) {
                        case XOR: {
                            text = codeXor(Base64.getEncoder().encodeToString(text.getBytes("utf-8")), value_s);
                            mJSONObject = new JSONObject();
                            mJSONObject.put("msg", text);
                            mOutputStream.write((mJSONObject.toString() + CRLF).getBytes());
                            mOutputStream.flush();
                            break;
                        }
                        case CAESAR: {
                            text = encodeCaesar(Base64.getEncoder().encodeToString(text.getBytes("utf-8")), value_s);
                            mJSONObject = new JSONObject();
                            mJSONObject.put("msg", text);
                            mOutputStream.write((mJSONObject.toString() + CRLF).getBytes());
                            mOutputStream.flush();
                            break;
                        }
                        default: {
                            mJSONObject = new JSONObject();
                            mJSONObject.put("msg", Base64.getEncoder().encodeToString(text.getBytes("utf-8")));
                            mOutputStream.write((mJSONObject.toString() + CRLF).getBytes());
                            mOutputStream.flush();
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println(e);
                notifyObservers(e);
            }
        }

        /**
         * Zamykanie socketa
         */
        private void close() {
            try {
                mSocket.close();
            } catch (IOException ex) {
                notifyObservers(ex);
            }
        }

        private String decodeCaesar(String text, int offset) {
            return encodeCaesar(text, 26-offset);
        }

        private String encodeCaesar(String enc, int offset) {
            offset = offset % 26 + 26;
            StringBuilder encoded = new StringBuilder();
            for (char i : enc.toCharArray()) {
                if (Character.isLetter(i)) {
                    if (Character.isUpperCase(i)) {
                        encoded.append((char) ('A' + (i - 'A' + offset) % 26 ));
                    } else {
                        encoded.append((char) ('a' + (i - 'a' + offset) % 26 ));
                    }
                } else {
                    encoded.append(i);
                }
            }
            return encoded.toString();
        }

        private String codeXor(String string, int secret){
            byte b = (byte) (secret & 0xFF);
            StringBuilder encrypted = new StringBuilder();
            for (byte c : string.getBytes(StandardCharsets.UTF_8)){
                encrypted.append((char)(c ^ b));
            }
            return encrypted.toString();
        }

        public void setEncryptionType(EncryptionType encryptionType) {
            mEncryptionType = encryptionType;
            switch (encryptionType) {
                case XOR: {
                    System.out.println("set xor");
                    mJSONObject = new JSONObject();
                    mJSONObject.put("encryption", "xor");
                    try {
                        mOutputStream.write((mJSONObject.toString() + CRLF).getBytes());
                        mOutputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case CAESAR: {
                    System.out.println("set caesar");
                    mJSONObject = new JSONObject();
                    mJSONObject.put("encryption", "caesar");
                    try {
                        mOutputStream.write((mJSONObject.toString() + CRLF).getBytes());
                        mOutputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                default: {
                    System.out.println("set none");
                    mJSONObject = new JSONObject();
                    mJSONObject.put("encryption", "none");
                    try {
                        mOutputStream.write((mJSONObject.toString() + CRLF).getBytes());
                        mOutputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    }

    /**
     * Klasa implementująca GUI
     */
    static class ChatFrame extends JFrame implements Observer {

        private JTextArea mJTextArea;
        private JTextField mJTextField;
        private JButton mSendButton, mNoneButton, mXorButton, mCaesarButton;
        private ChatCommunication mChatCommunication;

        public ChatFrame(ChatCommunication chatCommunication) {
            this.mChatCommunication = chatCommunication;
            chatCommunication.addObserver(this);
            buildGUI();
        }

        /**
         * Inicjalizacja interfejsu użytkownika
         */
        private void buildGUI() {
            mJTextArea = new JTextArea(20, 50);
            mJTextArea.setEditable(false);
            mJTextArea.setLineWrap(true);
            add(new JScrollPane(mJTextArea), BorderLayout.CENTER);

            Box box = Box.createHorizontalBox();
            Box boxEncrypt= Box.createHorizontalBox();
            add(box, BorderLayout.SOUTH);
            add(boxEncrypt, BorderLayout.NORTH);
            mJTextField = new JTextField();
            mSendButton = new JButton("Send");
            mNoneButton = new JButton("None");
            mXorButton = new JButton("XOR");
            mCaesarButton = new JButton("Caesar");
            box.add(mJTextField);
            box.add(mSendButton);
            boxEncrypt.add(new JLabel("Encryption mode:  "));
            boxEncrypt.add(mNoneButton);
            boxEncrypt.add(mXorButton);
            boxEncrypt.add(mCaesarButton);

            ActionListener sendListener = e -> {
				String str = mJTextField.getText();
				if (str != null && str.trim().length() > 0) {
                    mChatCommunication.sendMessage(str);
                }
				mJTextField.selectAll();
				mJTextField.requestFocus();
				mJTextField.setText("");
			};

            ActionListener noneListener = e -> {
                mChatCommunication.setEncryptionType(EncryptionType.NONE);
            };
            ActionListener xorListener = e -> {
                mChatCommunication.setEncryptionType(EncryptionType.XOR);
            };
            ActionListener caesarListener = e -> {
                mChatCommunication.setEncryptionType(EncryptionType.CAESAR);
            };

            mJTextField.addActionListener(sendListener);
            mSendButton.addActionListener(sendListener);
            mNoneButton.addActionListener(noneListener);
            mXorButton.addActionListener(xorListener);
            mCaesarButton.addActionListener(caesarListener);

            this.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    mChatCommunication.close();
                }
            });
        }

        /**
         * Aktualizuje GUI z parametrem Object
         * @param o Obserwowany obiekt
         * @param arg Obiekt na podstawie którego jest aktualizowane GUI
         */
        public void update(Observable o, Object arg) {
            final Object finalArg = arg;
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    mJTextArea.append(finalArg.toString());
                    mJTextArea.append("\n");
                }
            });
        }
    }

    public static void main(String[] args) {
        String server = args[0];
        ChatCommunication communication = new ChatCommunication();

        JFrame frame = new ChatFrame(communication);
        frame.setTitle("Podłączono do " + server + ":" + PORT_ADDRESS_CLIENT);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);

        try {
            communication.InitSocket(server, PORT_ADDRESS_CLIENT);
        } catch (IOException ex) {
            System.out.println("Nie można się połączyć z " + server + ":" + PORT_ADDRESS_CLIENT);
            ex.printStackTrace();
            System.exit(0);
        }
    }
}