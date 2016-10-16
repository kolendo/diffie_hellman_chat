package classes;

import javax.swing.*;

/**
 * @author Wojtek Kolendo
 */
public class Main extends JFrame {

	private JButton mRunNewClientButton;
	private JPanel mMainPanel;
	private JButton mRunNewServerButton;

	public static void main(String [] args){
		Main main = new Main();
		main.initForm();
	}

	private void initForm() {
		setContentPane(mMainPanel);
		setTitle("Diffie Hellman");
		setSize(300, 150);
		setVisible(true);

		mRunNewClientButton.addActionListener(e -> startClient());
		mRunNewServerButton.addActionListener(e -> startServer());
	}

	private void startClient() {
		setVisible(false);
		String IPServer = JOptionPane.showInputDialog("Podaj IP serwera:");
		String[] arguments = new String[] {IPServer};
		new Client().main(arguments);
	}

	private void startServer() {
		setVisible(false);
		String[] arguments = new String[] {};
		new Server().main(arguments);
	}

}
