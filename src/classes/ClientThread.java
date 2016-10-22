package classes;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Random;

import models.EncryptionType;

/**
 * Klasa implementująca wątek dla każdego klienta. Używane w {@link Server}.
 *
 * @author Wojtek Kolendo
 */
public class ClientThread extends Thread {

	private String mClientName, mMessage, mRequest;
	private DataInputStream mDataInputStream = null;
	private PrintStream mPrintStream = null;
	private Socket mClientSocket = null;
	private final ClientThread[] mClientThreads;
	private int mMaxClientsCount;
	private JSONParser mJSONParser = new JSONParser();
	private int VALUE_P, VALUE_G, value_b;
	private double value_A, value_B, value_s;
	private EncryptionType mEncryptionType = EncryptionType.NONE;

	public ClientThread(Socket clientSocket, ClientThread[] threads, int p, int g) {
		mClientSocket = clientSocket;
		mClientThreads = threads;
		mMaxClientsCount = threads.length;
		VALUE_P = p;
		VALUE_G = g;
		Random generator = new Random();
		value_b = generator.nextInt(10) + 1;
		value_B = Math.pow(VALUE_G, value_b);
	}

	/**
	 * Obsługa komunikacji i wiadomości
	 */
	public void run() {
		int maxClientsCount = mMaxClientsCount;
		ClientThread[] threads = mClientThreads;

		try {
      /* Stworzenie strumieni wej/wyj. */
			mDataInputStream = new DataInputStream(mClientSocket.getInputStream());
			mPrintStream = new PrintStream(mClientSocket.getOutputStream());
			while (true) {
				mPrintStream.println("Podaj swoją nazwę.");
				String msg = mDataInputStream.readLine().trim();
				try{
					Object obj = mJSONParser.parse(msg);
					JSONObject jsonObject = (JSONObject) obj;
					mClientName = (String) jsonObject.get("msg");
					if (mClientName != null) {
						break;
					}
				} catch(ParseException pe) {
					System.out.println(pe);
				}
			}

      /* Przywitanie nowego klienta. */
			mPrintStream.println("Witaj " + mClientName + " w naszym pokoju.");
			synchronized (this) {
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] != null && threads[i] != this) {
						threads[i].mPrintStream.println("*** Nowy użytkownik " + mClientName + " dołączył do pokoju! ***");
					}
				}
			}
      /* Ropoczęcie nowej konwersacji. */
			while (true) {
				String content = mDataInputStream.readLine();
				Object obj = null;
				try {
					obj = mJSONParser.parse(content);
				} catch (ParseException e) {
					e.printStackTrace();
				}
				JSONObject jsonObject = (JSONObject) obj;
				if (jsonObject != null && jsonObject.get("a") != null) {
					value_A = (Double) jsonObject.get("a");
				}

				if (jsonObject != null && jsonObject.get("msg") != null) {
					mMessage = (String) jsonObject.get("msg");
					if (mMessage.startsWith("/quit")) {
						break;
					}
					if (mEncryptionType == EncryptionType.NONE) {
          				/* Wysłanie wiadomości do pozostałych klientów. */
						synchronized (this) {
							for (int i = 0; i < maxClientsCount; i++) {
								if (threads[i] != null && threads[i].mClientName != null) {
									threads[i].mPrintStream.println("<" + mClientName + "> " + mMessage);
								}
							}
						}
					} else if (mEncryptionType == EncryptionType.XOR) {

					} else if (mEncryptionType == EncryptionType.CESAR) {

					}
				}
				if (jsonObject != null && jsonObject.get("encryption") != null) {
					switch ((String) jsonObject.get("encryption")) {
						case "xor": {
							mEncryptionType = EncryptionType.XOR;
							break;
						}
						case "cesar": {
							mEncryptionType = EncryptionType.CESAR;
							break;
						}
						default: {
							mEncryptionType = EncryptionType.NONE;
							break;
						}
					}
				}

			}

			synchronized (this) {
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] != null && threads[i] != this
							&& threads[i].mClientName != null) {
						threads[i].mPrintStream.println("*** Uytkownik " + mClientName + " wyszedł z pokoju! ***");
					}
				}
			}

      /*
       * Czyszczenie miejsca w kolekcji wątków na serwerze.
       */
			synchronized (this) {
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] == this) {
						threads[i] = null;
					}
				}
			}
      /*
       * Zamknięcie strumieni i socketa.
       */
			mDataInputStream.close();
			mPrintStream.close();
			mClientSocket.close();
		} catch (IOException e) {
		}
	}
}