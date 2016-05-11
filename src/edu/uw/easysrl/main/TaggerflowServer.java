package edu.uw.easysrl.main;

import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;
import uk.co.flamingpenguin.jewel.cli.CliFactory;
import uk.co.flamingpenguin.jewel.cli.Option;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import edu.uw.Taggerflow;
import edu.uw.TaggerflowProtos.TaggingInput;
import edu.uw.easysrl.util.LibraryUtil;

public class TaggerflowServer {
	protected final int port;
	protected final Taggerflow taggerflow;

	public interface CommandLineArguments {
		@Option(shortName = "p", defaultValue = "4444", description = "Port number") int getPort();

		@Option(shortName = "m", description = "Path to the parser model") String getModel();

		@Option(shortName = "b", defaultValue = "1e-5", description = "Supertagger beam") double getBeam();

		@Option(helpRequest = true, description = "Display this message", shortName = "h") boolean getHelp();
	}

	public TaggerflowServer(String[] args) {
		try {
			final CommandLineArguments cmd = CliFactory.parseArguments(CommandLineArguments.class, args);
			LibraryUtil.setLibraryPath("lib");
			this.taggerflow = new Taggerflow(new File(cmd.getModel(), "taggerflow"), cmd.getBeam());
			this.port = cmd.getPort();
		} catch (ArgumentValidationException e) {
			throw new RuntimeException(e);
		}
	}

	public void execute() {
		try {
			ServerSocket serverSocket = new ServerSocket(port);
			System.err.println("Taggerflow server listening on port " + port);
			while (true) {
				new TaggerThread(taggerflow, serverSocket.accept()).start();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws IOException {
		TaggerflowServer server = new TaggerflowServer(args);
		server.execute();
	}

	public class TaggerThread extends Thread {
		private Socket socket = null;
		private final Taggerflow taggerflow;
		private final String client;

		public TaggerThread(Taggerflow taggerflow, Socket socket) {
			super("TaggerThread");
			this.taggerflow = taggerflow;
			this.socket = socket;
			this.client = socket.getRemoteSocketAddress().toString();
			System.err.println("Established connection with client " + client);
		}

		@Override
		public void run() {
			try {
				OutputStream out = socket.getOutputStream();
				InputStream in = socket.getInputStream();
				while (!socket.isClosed()) {
					TaggingInput input = TaggingInput.parseDelimitedFrom(in);
					if (input != null) {
						taggerflow.predict(input).forEach(sentence -> {
							try {
								sentence.writeDelimitedTo(out);
							} catch (final IOException e) {
								throw new RuntimeException(e);
							}
						});
						out.flush();
					} else {
						break;
					}
				}
				out.close();
				in.close();
				socket.close();
				System.err.println("Closed connection with client " + client);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
