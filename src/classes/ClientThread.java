package classes;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import models.EncryptionType;

/**
 * Klasa implementująca wątek dla każdego klienta. Używane w {@link Server}.
 *
 * @author Wojtek Kolendo
 */
public class ClientThread extends Thread {

	private String mClientName, mMessage;
	private DataInputStream mDataInputStream = null;
	private PrintStream mPrintStream = null;
	private Socket mClientSocket = null;
	private final ClientThread[] mClientThreads;
	private int mMaxClientsCount;
	private JSONParser mJSONParser = new JSONParser();
	private int VALUE_P, VALUE_G, value_b, value_s;
	private double value_A, value_B;
	private EncryptionType mEncryptionType = EncryptionType.NONE;
	private JSONObject mJSONObject;
	public boolean DIFFIE_READY = false;

	/**
	 * Konstruktor wątku klienta. Losuje wartościom P, G oraz b losową, unikalną dla klienta liczbę.
	 *
	 * @param clientSocket socket klienta
	 * @param threads kolekcja pozostałych wątków (innych klientów) na serwerze
	 */
	public ClientThread(Socket clientSocket, ClientThread[] threads) {
		mClientSocket = clientSocket;
		mClientThreads = threads;
		mMaxClientsCount = threads.length;
		Random generator = new Random();
		VALUE_P = generator.nextInt(100) + 1;
		VALUE_G = generator.nextInt(100) + 1;
		value_b = generator.nextInt(10) + 1;
		value_B = (Math.pow(VALUE_G, value_b) % VALUE_P);
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
				mJSONObject = new JSONObject();
				mJSONObject.put("msg", "Podaj swoją nazwę.");
				mPrintStream.println(mJSONObject.toString());
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
			mJSONObject = new JSONObject();
			mJSONObject.put("msg", "Witaj " + mClientName + " w naszym pokoju.");
			mJSONObject.put("init", "start");
			mPrintStream.println(mJSONObject.toString());
			synchronized (this) {
				mJSONObject = new JSONObject();
				mJSONObject.put("msg", "*** Nowy użytkownik " + mClientName + " dołączył do pokoju! ***");
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] != null && threads[i] != this) {
						threads[i].mPrintStream.println(mJSONObject.toString());
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
				if (jsonObject != null && jsonObject.get("A") != null) {
					System.out.println("received: " + jsonObject.toString());
					value_A = (double) jsonObject.get("A");
					value_s = (int) (Math.pow(value_A, value_b) % VALUE_P);
					DIFFIE_READY = true;
					System.out.println("s: " + value_s);
				}

				if (jsonObject != null && jsonObject.get("request") != null) {
					if (jsonObject.get("request").equals("keys")) {
						System.out.println("received: " + jsonObject.toString());
						mJSONObject = new JSONObject();
						mJSONObject.put("p", VALUE_P);
						mJSONObject.put("g", VALUE_G);
						mJSONObject.put("B", value_B);
						mPrintStream.println(mJSONObject.toString());
					}
				}

				/* Wysłanie wiadomości do pozostałych klientów. */
				if (DIFFIE_READY && jsonObject != null && jsonObject.get("msg") != null) {
					System.out.println("received: " + jsonObject.toString());
					mMessage = (String) jsonObject.get("msg");
					if (mMessage.startsWith("/quit")) {
						break;
					}

					switch (mEncryptionType) {
						case XOR: {
							mMessage = codeXor(mMessage, value_s);
							synchronized (this) {
								for (int i = 0; i < maxClientsCount; i++) {
									if (threads[i] != null && threads[i].mClientName != null) {
										mJSONObject = new JSONObject();
										String tempMessage = codeXor(mMessage, threads[i].value_s);
										mJSONObject.put("msg", tempMessage);
										mJSONObject.put("from", mClientName);
										threads[i].mPrintStream.println(mJSONObject.toString());
									}
								}
							}
							break;
						}
						case CAESAR: {
							mMessage = decodeCaesar(mMessage, value_s);
							synchronized (this) {
								for (int i = 0; i < maxClientsCount; i++) {
									if (threads[i] != null && threads[i].mClientName != null) {
										mJSONObject = new JSONObject();
										String tempMessage = encodeCaesar(mMessage, threads[i].value_s);
										mJSONObject.put("msg", tempMessage);
										mJSONObject.put("from", mClientName);
										threads[i].mPrintStream.println(mJSONObject.toString());
									}
								}
							}
							break;
						}
						default: {
							synchronized (this) {
								for (int i = 0; i < maxClientsCount; i++) {
									if (threads[i] != null && threads[i].mClientName != null) {
										mJSONObject = new JSONObject();
										mJSONObject.put("msg", mMessage);
										mJSONObject.put("from", mClientName);
										threads[i].mPrintStream.println(mJSONObject.toString());
									}
								}
							}
							break;
						}
					}
				}

				if (jsonObject != null && jsonObject.get("encryption") != null) {
					System.out.println("received: " + jsonObject.toString());
					switch ((String) jsonObject.get("encryption")) {
						case "xor": {
							mEncryptionType = EncryptionType.XOR;
							break;
						}
						case "caesar": {
							mEncryptionType = EncryptionType.CAESAR;
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
				mJSONObject = new JSONObject();
				mJSONObject.put("msg", "*** Uytkownik " + mClientName + " wyszedł z pokoju! ***");
				for (int i = 0; i < maxClientsCount; i++) {
					if (threads[i] != null && threads[i] != this && threads[i].mClientName != null) {
						threads[i].mPrintStream.println(mJSONObject.toString());
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
		System.out.println(b);
		StringBuilder encrypted = new StringBuilder();
		for (byte c : string.getBytes(StandardCharsets.UTF_8)){
			encrypted.append((char)(c ^ b));
		}
		return encrypted.toString();
	}
}