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
import java.util.Base64;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;


/**
 * Klasa zarządzająca oknem klienta.
 *
 * @author Wojtek Kolendo
 */
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
        private int VALUE_P, VALUE_G, value_a;
        private double value_A, value_B, value_s;
        private boolean DIFFIE_READY = false, INIT_MSG = true;

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
         * zezwalającą na wysyłanie zaszyfrowanych wiadomości w Base64.
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
                            System.out.println("received: " + jsonObject.toString());

                            if (jsonObject.get("msg") != null) {
                                System.out.println("received: " + jsonObject.toString());
                                if (jsonObject.get("from") != null) {
                                    byte[] base64decoded = Base64.getDecoder().decode((String) jsonObject.get("msg"));
                                    String decoded = new String(base64decoded, "utf-8");
                                    notifyObservers("<" + jsonObject.get("from") + "> " + decoded);
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
                                    value_s = (Math.pow(value_B, value_a) % VALUE_P);
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
                                value_s = (Math.pow(value_B, value_a) % VALUE_P);
                                System.out.println("s: " + value_s);
                                DIFFIE_READY = true;
                            }

                            if (jsonObject.get("init") != null) {
                                System.out.println("received: " + jsonObject.toString());
                                String init = (String) jsonObject.get("init");
                                if (init.equals("start")) {
                                    System.out.println("rstart auth");
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
        public void sendMessage(String text) {
            try {
                if (DIFFIE_READY) {
                    mJSONObject = new JSONObject();
                    mJSONObject.put("msg", Base64.getEncoder().encodeToString(text.getBytes("utf-8")));
                    mOutputStream.write((mJSONObject.toString() + CRLF).getBytes());
                    mOutputStream.flush();
                } else if (INIT_MSG) {
                    mJSONObject = new JSONObject();
                    mJSONObject.put("msg", text);
                    mOutputStream.write((mJSONObject.toString() + CRLF).getBytes());
                    mOutputStream.flush();
                }
            } catch (IOException e) {
                System.out.println(e);
                notifyObservers(e);
            }
        }

        /**
         * Zamykanie socketa
         */
        public void close() {
            try {
                mSocket.close();
            } catch (IOException ex) {
                notifyObservers(ex);
            }
        }
    }

    /**
     * Klasa implementująca GUI
     */
    static class ChatFrame extends JFrame implements Observer {

        private JTextArea mJTextArea;
        private JTextField mJTextField;
        private JButton mSendButton;
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
            add(box, BorderLayout.SOUTH);
            mJTextField = new JTextField();
            mSendButton = new JButton("Send");
            box.add(mJTextField);
            box.add(mSendButton);

            ActionListener sendListener = e -> {
				String str = mJTextField.getText();
				if (str != null && str.trim().length() > 0) {
                    mChatCommunication.sendMessage(str);
                }
				mJTextField.selectAll();
				mJTextField.requestFocus();
				mJTextField.setText("");
			};
            mJTextField.addActionListener(sendListener);
            mSendButton.addActionListener(sendListener);

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