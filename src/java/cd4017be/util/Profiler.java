package cd4017be.util;

import java.io.PrintStream;

/**
 * @author CD4017BE */
public final class Profiler {

	private final PrintStream out;
	private long t;

	public Profiler(PrintStream out) {
		this.out = out;
		this.t = System.nanoTime();
	}

	public void log(String msg) {
		out.println(msg);
	}

	public void end(String msg) {
		long t0 = t; t = System.nanoTime();
		out.printf("%s in %d μs\n", msg, (t - t0) / 1000);
	}

}
