package cd4017be.dfc.lang;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author cd4017be */
public class Interpreter extends Thread {

	private final AtomicReference<Task> scheduled = new AtomicReference<>();
	private volatile long counter;
	private volatile boolean terminate;

	private Closeable[] resources = new Closeable[64];
	private int[] ids = new int[64];
	private long[] free = {-1};
	private int lastId = 0;

	public Interpreter() {
		super("interpreter");
		setPriority(Math.max(MIN_PRIORITY, currentThread().getPriority() - 1));
		setDaemon(true);
		start();
	}

	@Override
	public void run() {
		while(!terminate) {
			synchronized(this) {
				try {this.wait();} catch(InterruptedException e) {}
			}
			Task task = scheduled.get();
			if (task != null) {
				counter = task.limit;
				if (terminate) break;
				long time = System.nanoTime();
				if (task.code != null) try {
					eval(task.code, task.vars, 0);
					task.error = null;
				} catch(SignalError e) {
					e.printStackTrace();
					task.error = e;
				}
				closeAll();
				task.time = System.nanoTime() - time;
				task.ticks = task.limit - counter;
				task.onComplete.accept(task);
				if (scheduled.compareAndExchange(task, null) != task)
					interrupt();
			}
		}
	}

	public boolean active() {
		return scheduled.get() != null;
	}

	public void cancel() {
		scheduled.set(null);
		counter = 0;
	}

	public void schedule(Task task) {
		if (scheduled.getAndSet(task) == null)
			interrupt();
	}

	public void terminate() {
		terminate = true;
		counter = 0;
		interrupt();
	}

	public void eval(Instruction[] code, Value[] vars, int id) throws SignalError {
		if ((counter -= code.length) < 0)
			throw new SignalError(id, "computation took too long");
		for (Instruction ins : code) ins.eval(this, vars);
	}

	public void closeAll() {
		for (int i = 0; i < free.length; i++) {
			long f = free[i];
			if (f == -1) continue;
			for (int j = i * 64, j1 = j + 64; j < j1; j++) {
				Closeable r = resources[j];
				if (r == null) continue;
				resources[j] = null;
				try {
					r.close();
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
			free[i] = -1;
		}
		lastId = 0;
	}

	public long addResource(Closeable r) {
		int j; findFreeIdx: {
			int l = free.length;
			for (int i = 0; i < l; i++) {
				long f = free[i];
				if (f == 0) continue;
				j = i * 64 + Long.numberOfTrailingZeros(f);
				break findFreeIdx;
			}
			free = Arrays.copyOf(free, l * 2);
			Arrays.fill(free, l, l * 2, -1L);
			l *= 64;
			resources = Arrays.copyOf(resources, l * 2);
			ids = Arrays.copyOf(ids, l * 2);
			j = l;
		}
		free[j >> 6] ^= 1L << j;
		resources[j] = r;
		return j | (long)(ids[j] = ++lastId) << 32;
	}

	public Closeable getResource(long id) {
		int idx = (int)id & ids.length - 1;
		return ids[idx] == (int)(id >> 32) ? resources[idx] : null;
	}

	public Closeable removeResource(long id) {
		int idx = (int)id & ids.length - 1;
		if (ids[idx] != (int)(id >> 32))
			return null;
		Closeable r = resources[idx];
		resources[idx] = null;
		free[idx >> 6] &= ~(1 << idx);
		return r;
	}

	public class Task {
		private final Consumer<Task> onComplete;
		public final Value[] vars;
		public final Instruction[] code;
		public final long limit;
		public long time, ticks;
		public SignalError error;

		public Task(Instruction[] code, Value[] vars, long limit, Consumer<Task> onComplete) {
			this.code = code;
			this.vars = vars;
			this.limit = limit;
			this.onComplete = onComplete;
			schedule(this);
		}

		public Task(SignalError error, Consumer<Task> onComplete) {
			this(null, null, 0, onComplete);
			this.error = error;
			onComplete.accept(this);
		}

		public void log() {
			System.out.printf("ran %d operations in %d µs\n", ticks, time / 1000);
		}

		public Interpreter interpreter() {
			return Interpreter.this;
		}
	}

}
