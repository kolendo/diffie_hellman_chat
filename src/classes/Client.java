package classes;

import org.json.simple.JSONObject;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Observable;
import java.util.Observer;

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
        private Socket mSocket;
        private OutputStreamWriter mOutputStreamWriter;

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
         * Inicjalizacja socketu i wątku odpowiedzialnego za odbiór od serwera
         * @param server adres IP
         * @param port
         * @throws IOException
         */
        public void InitSocket(String server, int port) throws IOException {
            mSocket = new Socket(server, port);
			mOutputStreamWriter = new OutputStreamWriter(mSocket.getOutputStream(), StandardCharsets.UTF_8);
//			mOutputStreamWriter.write(new JSONObject().put("msg", "keys").toString());

            Thread receivingThread = new Thread() {
                @Override
                public void run() {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null)
                            notifyObservers(line);
                    } catch (IOException ex) {
                        notifyObservers(ex);
                    }
                }
            };
            receivingThread.start();
        }

        private static final String CRLF = "\r\n"; // nowa linia

        /**
         * Wysłanie tekstu na serwer
         * @param text wysłany tekst
         */
        public void sendMessage(String text) {
			try {
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("msg", text);
				mOutputStreamWriter.write(jsonObject.toString());
			} catch (IOException e) {
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