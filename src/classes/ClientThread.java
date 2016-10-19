package classes;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;

/**
 * Klasa implementująca wątek dla każdego klienta. Używane w {@link Server}.
 *
 * @author Wojtek Kolendo
 */
public class ClientThread extends Thread {

	private String mClientName = null;
	private DataInputStream mDataInputStream = null;
	private PrintStream mPrintStream = null;
	private Socket mClientSocket = null;
	private final ClientThread[] mClientThreads;
	private int mMaxClientsCount;
	private JSONParser mJSONParser = new JSONParser();

	public ClientThread(Socket clientSocket, ClientThread[] threads) {
		mClientSocket = clientSocket;
		mClientThreads = threads;
		mMaxClientsCount = threads.length;
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
				System.out.println("while");
				mPrintStream.println("Podaj swoją nazwę.");
				String content = mDataInputStream.readLine();
				System.out.println(content);

				try{
					Object obj = mJSONParser.parse(content);
					JSONObject jsonObject = (JSONObject) obj;
					mClientName = (String) jsonObject.get("msg");
					System.out.println("decoded" + mClientName);
					if (mClientName.indexOf('@') == -1) {
						break;
					} else {
						mPrintStream.println("Nazwa nie powinna zawierać znaku '@'");
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
				String line = mDataInputStream.readLine();

				if (line.startsWith("/quit")) {
					break;
				}
          /* Wysłanie wiadomości do pozostałych klientów. */
					synchronized (this) {
						for (int i = 0; i < maxClientsCount; i++) {
							if (threads[i] != null && threads[i].mClientName != null) {
								threads[i].mPrintStream.println("<" + mClientName + "> " + line);
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