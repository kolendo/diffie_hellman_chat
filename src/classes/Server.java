package classes;

import java.io.PrintStream;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;

/**
 * Klasa implementująca serwer, dla każdego klienta tworzy wątki {@link ClientThread}.
 *
 * @author Wojtek Kolendo
 */
public class Server {

	private static int PORT_ADDRESS_SERVER = 8080;

	/**
	 * Stała maksymalna ilość obsługiwanych klientów przez serwer.
	 */
	private static final int MAX_CLIENTS_COUNT = 10;
	private static final ClientThread[] THREADS = new ClientThread[MAX_CLIENTS_COUNT];
	private static ServerSocket sServerSocket = null;
	private static Socket sClientSocket = null;
	private static final int VALUE_P = 23;
	private static final int VALUE_G = 5;

	@SuppressWarnings("InfiniteLoopStatement")
	public static void main(String args[]) {

		if (args.length < 1) {
			System.out.println("W użyciu port = " + PORT_ADDRESS_SERVER);
		} else {
			PORT_ADDRESS_SERVER = Integer.valueOf(args[0]);
		}

		try {
			sServerSocket = new ServerSocket(PORT_ADDRESS_SERVER);
		} catch (IOException e) {
			System.out.println(e);
		}

		/**
		 * Stworzenie socketu klienta dla każdego połączenia i przekazanie go do wątku {@link ClientThread}.
		 */
		while (true) {
			try {
				sClientSocket = sServerSocket.accept();
				int i;
				for (i = 0; i < MAX_CLIENTS_COUNT; i++) {
					if (THREADS[i] == null) {
						(THREADS[i] = new ClientThread(sClientSocket, THREADS, VALUE_P, VALUE_G)).start();
						break;
					}
				}
				if (i == MAX_CLIENTS_COUNT) {
					PrintStream os = new PrintStream(sClientSocket.getOutputStream());
					os.println("Maksymalna ilość klientów.");
					os.close();
					sClientSocket.close();
				}
			} catch (IOException e) {
				System.out.println(e);
			}
		}
	}
}