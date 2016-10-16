package classes;

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
			String name;
			while (true) {
				mPrintStream.println("Podaj swoją nazwę.");
				name = mDataInputStream.readLine().trim();
				if (name.indexOf('@') == -1) {
					break;
				} else {
					mPrintStream.println("Nazwa nie powinna zawierać znaku '@'");
				}
			}

      /* Przywitanie nowego klienta. */
			mPrintStream.println("Witaj " + name
					+ " w naszym pokoju.");
			synchronized (this) {
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] != null && threads[i] == this) {
						mClientName = "@" + name;
						break;
					}
				}
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] != null && threads[i] != this) {
						threads[i].mPrintStream.println("*** Nowy użytkownik " + name
								+ " dołączył do pokoju! ***");
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
								threads[i].mPrintStream.println("<" + name + "> " + line);
							}
						}
					}
			}

			synchronized (this) {
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] != null && threads[i] != this
							&& threads[i].mClientName != null) {
						threads[i].mPrintStream.println("*** Uytkownik " + name
								+ " wyszedł z pokoju! ***");
					}
				}
			}
			mPrintStream.println("*** Bye " + name + " ***");

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