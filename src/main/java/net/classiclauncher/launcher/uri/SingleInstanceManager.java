package net.classiclauncher.launcher.uri;

import java.io.*;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ensures at most one launcher instance runs per machine, and forwards URI arguments from secondary instances to the
 * primary one via a loopback socket.
 *
 * <h3>How it works</h3>
 * <ol>
 * <li>The primary instance calls {@link #tryClaimInstance(String)}, which binds a {@link ServerSocket} on a
 * deterministic loopback port derived from the launcher name. Success means this is the primary instance.</li>
 * <li>A secondary instance attempting the same bind gets a {@link BindException}. It then connects to the primary,
 * sends the URI arg (if any), and returns {@code false} so the caller can exit immediately.</li>
 * <li>The primary's listener thread reads each message and dispatches it to the registered {@link Consumer} via
 * {@link #setUriListener(Consumer)}.</li>
 * </ol>
 *
 * <p>
 * The chosen port is {@code 49152 + (abs(launcherName.hashCode()) % 16383)}, keeping it in the IANA dynamic/private
 * range and stable across restarts for the same launcher name.
 */
public final class SingleInstanceManager {

	private static final Logger LOG = LogManager.getLogger(SingleInstanceManager.class);

	/**
	 * Timeout in milliseconds for connecting to the primary instance when forwarding a URI.
	 */
	private static final int CONNECT_TIMEOUT_MS = 1500;

	/**
	 * Timeout in milliseconds for reading a line from an incoming client connection.
	 */
	private static final int READ_TIMEOUT_MS = 3000;

	private final int port;
	private volatile ServerSocket serverSocket;
	private volatile Consumer<String> uriListener;

	/**
	 * Creates a manager for the given launcher name. The IPC port is deterministically derived from the name so all
	 * instances agree on it.
	 *
	 * @param launcherName
	 *            logical name of this launcher (e.g. {@code "launcher"})
	 */
	public SingleInstanceManager(String launcherName) {
		this.port = 49152 + (Math.abs(launcherName.hashCode()) % 16383);
	}

	/**
	 * Attempts to claim the primary-instance role by binding the IPC port.
	 *
	 * <ul>
	 * <li>If the bind succeeds: starts the background listener thread and returns {@code true}. The caller is the
	 * primary instance and should continue normal startup.</li>
	 * <li>If a {@link BindException} is thrown (port already bound): connects to the running primary, forwards
	 * {@code uriArg} (if non-null), and returns {@code false}. The caller should call {@link System#exit(int)}
	 * immediately.</li>
	 * </ul>
	 *
	 * @param uriArg
	 *            the {@code classiclauncher://} URI to forward to the primary instance, or {@code null} if this launch
	 *            was not triggered by a URI
	 * @return {@code true} if this instance claimed the primary role; {@code false} if another instance is already
	 *         running (URI has been forwarded)
	 * @throws IOException
	 *             if an unexpected I/O error occurs while binding the server socket (callers should treat this as
	 *             primary and continue)
	 */
	public boolean tryClaimInstance(String uriArg) throws IOException {
		try {
			serverSocket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress());
			LOG.debug("SingleInstanceManager: claimed port {} (primary instance)", port);
			startListenerThread();
			return true;
		} catch (BindException e) {
			LOG.debug("SingleInstanceManager: port {} already bound — forwarding URI to primary", port);
			if (uriArg != null && !uriArg.isEmpty()) {
				sendToRunningInstance(uriArg);
			}
			return false;
		}
	}

	/**
	 * Registers a listener that is called (from the IPC listener thread) whenever the primary instance receives a URI
	 * forwarded from a secondary instance.
	 *
	 * <p>
	 * The listener is invoked from a daemon background thread. Implementations that update Swing components must
	 * dispatch via {@code SwingUtilities.invokeLater}.
	 *
	 * @param listener
	 *            the URI handler; may be set or changed at any time (volatile write)
	 */
	public void setUriListener(Consumer<String> listener) {
		this.uriListener = listener;
	}

	/**
	 * Closes the server socket, terminating the listener thread. Safe to call multiple times. Register as a JVM
	 * shutdown hook.
	 */
	public void close() {
		ServerSocket s = serverSocket;
		if (s != null && !s.isClosed()) {
			try {
				s.close();
			} catch (IOException e) {
				LOG.debug("SingleInstanceManager: error closing server socket", e);
			}
		}
	}

	// ── Private helpers ───────────────────────────────────────────────────────

	private void startListenerThread() {
		Thread t = new Thread(() -> {
			while (!serverSocket.isClosed()) {
				Socket client = null;
				try {
					client = serverSocket.accept();
					client.setSoTimeout(READ_TIMEOUT_MS);
					BufferedReader reader = new BufferedReader(
							new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
					String line = reader.readLine();
					if (line != null && !line.trim().isEmpty()) {
						final String received = line.trim();
						LOG.debug("SingleInstanceManager: received URI from secondary instance: {}", received);
						Consumer<String> listener = uriListener;
						if (listener != null) {
							listener.accept(received);
						}
					}
				} catch (IOException e) {
					if (!serverSocket.isClosed()) {
						LOG.debug("SingleInstanceManager: error handling client connection", e);
					}
				} finally {
					if (client != null) {
						try {
							client.close();
						} catch (IOException ignored) {
						}
					}
				}
			}
		}, "single-instance-ipc");
		t.setDaemon(true);
		t.start();
	}

	private void sendToRunningInstance(String message) {
		try (Socket socket = new Socket()) {
			socket.connect(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS);
			PrintWriter writer = new PrintWriter(
					new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
			writer.println(message);
			writer.flush();
			LOG.debug("SingleInstanceManager: forwarded URI to primary instance: {}", message);
		} catch (IOException e) {
			LOG.warn("SingleInstanceManager: failed to forward URI to running instance (it may have just exited): {}",
					e.getMessage());
		}
	}

}
