package veyron.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicProgressBarUI;

/**
 * The Veyron launcher: keeps the player's client up to date and starts it.
 *
 * The client lives in ~/.veyron/client.jar. On every start the launcher asks the update server (update_url in
 * veyron_launcher.properties; the game server's ClientUpdates hands out data/client) for the current client's SHA-256.
 * When it differs from the local copy's, the new client is downloaded (with progress), checked against that hash and put
 * in place; then the client is started with this launcher's own Java and the launcher closes. When the update server
 * can't be reached, the client already there is started; without one, the player is told why it can't start.
 *
 * -Dveyron.update.url=... points it elsewhere (testing), -Dveyron.home=... keeps the client somewhere else.
 */
public class VeyronLauncher {

	private static final Color BACKGROUND = new Color(0x16, 0x10, 0x10), TEXT = new Color(0xE8, 0xD9, 0xC8), CRIMSON = new Color(0xB0, 0x1C, 0x1C),
		TRACK = new Color(0x2E, 0x22, 0x22), DIM = new Color(0x9A, 0x8A, 0x7A);

	private final Properties config = new Properties();
	private final File home, client;
	private JFrame frame;
	private JLabel status;
	private JProgressBar bar;

	public static void main(String[] args) {
		new VeyronLauncher().run();
	}

	private VeyronLauncher() {
		try (InputStream in = VeyronLauncher.class.getResourceAsStream("/veyron_launcher.properties")) {
			config.load(in);
		} catch (Exception e) {
			throw new IllegalStateException("veyron_launcher.properties is missing", e);
		}
		home = new File(System.getProperty("veyron.home", new File(System.getProperty("user.home"), ".veyron").getPath()));
		client = new File(home, "client.jar");
	}

	private String updateUrl() {
		String url = System.getProperty("veyron.update.url", config.getProperty("update_url"));
		return url.endsWith("/") ? url : url + "/";
	}

	private void run() {
		SwingUtilities.invokeLater(this::window);
		try {
			if (!home.isDirectory() && !home.mkdirs())
				throw new IOException("can't make " + home);
			update();
		} catch (Exception e) {
			if (!client.isFile()) {
				fail("The Veyron client couldn't be downloaded.\n\n" + e.getMessage() + "\n\nIs the server online? Try again in a moment.");
				return;
			}
			say("Update server unreachable, starting your client", -1);
			pause(1500);
		}
		launch();
	}

	/** the server's client when it isn't the one here */
	private void update() throws Exception {
		say("Checking for updates...", -1);
		String remote = remoteHash();
		if (remote.equalsIgnoreCase(sha256(client))) {
			say("Your client is up to date", 100);
			pause(400);
			return;
		}
		// a client published between the hash and the download has another hash: then once more
		for (int attempt = 0; ; attempt++) {
			File part = new File(home, "client.jar.part");
			download(updateUrl() + "client.jar", part);
			String got = sha256(part);
			if (got.equalsIgnoreCase(remote)) {
				Files.move(part.toPath(), client.toPath(), StandardCopyOption.REPLACE_EXISTING);
				say("Update installed", 100);
				pause(400);
				return;
			}
			part.delete();
			if (attempt == 1)
				throw new IOException("the downloaded client was damaged (" + got + ")");
			remote = remoteHash();
		}
	}

	private String remoteHash() throws IOException {
		HttpURLConnection c = connect(updateUrl() + "client.sha256");
		try (InputStream in = c.getInputStream()) {
			String text = new String(readAll(in), StandardCharsets.US_ASCII).trim();
			String hash = text.split("\\s+")[0];
			if (hash.length() != 64)
				throw new IOException("the update server sent no client hash");
			return hash;
		} finally {
			c.disconnect();
		}
	}

	private void download(String url, File to) throws IOException {
		HttpURLConnection c = connect(url);
		long total = c.getContentLengthLong(), done = 0;
		byte[] buf = new byte[64 * 1024];
		try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
			int n;
			long shown = -1;
			while ((n = in.read(buf)) != -1) {
				out.write(buf, 0, n);
				done += n;
				long mb = done / (1024 * 1024);
				if (mb != shown) {
					shown = mb;
					int percent = total > 0 ? (int) (done * 100 / total) : -1;
					say("Downloading update... " + mb + " / " + (total > 0 ? total / (1024 * 1024) : "?") + " MB", percent);
				}
			}
		} finally {
			c.disconnect();
		}
		if (total > 0 && done != total)
			throw new IOException("the download stopped at " + done + " of " + total + " bytes");
	}

	private static HttpURLConnection connect(String url) throws IOException {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(5000);
		c.setReadTimeout(20000);
		c.setUseCaches(false);
		c.setRequestProperty("User-Agent", "VeyronLauncher");
		if (c.getResponseCode() != 200)
			throw new IOException(url + " answered " + c.getResponseCode());
		return c;
	}

	/** the client, with this launcher's Java (javaw on Windows, so no console); the launcher then closes */
	private void launch() {
		say("Starting Veyron...", 100);
		String java = System.getProperty("java.home") + File.separator + "bin" + File.separator
			+ (System.getProperty("os.name").toLowerCase().contains("win") ? "javaw.exe" : "java");
		List<String> cmd = new ArrayList<>();
		cmd.add(java);
		cmd.addAll(words(config.getProperty("jvm_args", "")));
		cmd.add("-jar");
		cmd.add(client.getAbsolutePath());
		cmd.addAll(words(config.getProperty("client_args", "")));
		try {
			new ProcessBuilder(cmd).directory(home).start();
		} catch (IOException e) {
			fail("Veyron couldn't be started.\n\n" + e.getMessage());
			return;
		}
		pause(1500);
		System.exit(0);
	}

	private static List<String> words(String s) {
		List<String> l = new ArrayList<>();
		for (String w : s.trim().split("\\s+"))
			if (!w.isEmpty()) l.add(w);
		return l;
	}

	/** the file's SHA-256 in hex, "" when there is no file */
	static String sha256(File f) throws Exception {
		if (!f.isFile())
			return "";
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] buf = new byte[64 * 1024];
		try (InputStream in = new FileInputStream(f)) {
			int n;
			while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
		}
		StringBuilder sb = new StringBuilder();
		for (byte b : md.digest()) sb.append(String.format("%02x", b));
		return sb.toString();
	}

	private static byte[] readAll(InputStream in) throws IOException {
		java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) != -1) o.write(buf, 0, n);
		return o.toByteArray();
	}

	private static void pause(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
		}
	}

	// ---------------------------------------------------------------- the window

	/** a dark window: the Veyron shield, the name, what is happening and a crimson progress bar */
	private void window() {
		try {
			UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		} catch (Exception ignored) {
		}
		frame = new JFrame("Veyron");
		frame.setUndecorated(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		List<Image> icons = new ArrayList<>();
		for (String name : Arrays.asList("/veyron_16.png", "/veyron_32.png", "/veyron_64.png")) {
			Image i = image(name);
			if (i != null) icons.add(i);
		}
		frame.setIconImages(icons);

		JPanel root = new JPanel(new BorderLayout(0, 6));
		root.setBackground(BACKGROUND);
		root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x5A, 0x14, 0x14), 2),
			BorderFactory.createEmptyBorder(14, 22, 16, 22)));

		JLabel logo = new JLabel();
		Image shield = image("/veyron_splash.png");
		if (shield != null) logo.setIcon(new ImageIcon(shield.getScaledInstance(150, 150, Image.SCALE_SMOOTH)));
		logo.setHorizontalAlignment(SwingConstants.CENTER);
		JLabel name = new JLabel("VEYRON", SwingConstants.CENTER);
		name.setForeground(TEXT);
		name.setFont(new Font("Serif", Font.BOLD, 26));
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(logo, BorderLayout.CENTER);
		top.add(name, BorderLayout.SOUTH);

		status = new JLabel("Starting...", SwingConstants.CENTER);
		status.setForeground(DIM);
		status.setFont(new Font("SansSerif", Font.PLAIN, 12));
		bar = new JProgressBar(0, 100);
		bar.setPreferredSize(new Dimension(300, 12));
		bar.setBorderPainted(false);
		bar.setBackground(TRACK);
		bar.setForeground(CRIMSON);
		bar.setUI(new BasicProgressBarUI());
		bar.setIndeterminate(true);
		JPanel bottom = new JPanel(new BorderLayout(0, 6));
		bottom.setOpaque(false);
		bottom.add(status, BorderLayout.NORTH);
		bottom.add(bar, BorderLayout.SOUTH);

		root.add(top, BorderLayout.CENTER);
		root.add(bottom, BorderLayout.SOUTH);
		frame.setContentPane(root);
		frame.setSize(360, 300);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private static Image image(String resource) {
		try (InputStream in = VeyronLauncher.class.getResourceAsStream(resource)) {
			if (in == null) return null;
			BufferedImage i = ImageIO.read(in);
			return i;
		} catch (IOException e) {
			return null;
		}
	}

	/** the status line, and the bar at percent (-1: busy, no amount) */
	private void say(String text, int percent) {
		System.out.println("[launcher] " + text);
		SwingUtilities.invokeLater(() -> {
			if (status == null) return;
			status.setText(text);
			bar.setIndeterminate(percent < 0);
			if (percent >= 0) bar.setValue(percent);
		});
	}

	private void fail(String message) {
		System.out.println("[launcher] failed: " + message.replace('\n', ' '));
		try {
			SwingUtilities.invokeAndWait(() -> {
				if (frame != null) frame.setVisible(false);
				JOptionPane.showMessageDialog(null, message, "Veyron", JOptionPane.ERROR_MESSAGE);
			});
		} catch (Exception ignored) {
		}
		System.exit(1);
	}
}
