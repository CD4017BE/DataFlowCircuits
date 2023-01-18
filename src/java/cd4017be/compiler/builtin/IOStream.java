package cd4017be.compiler.builtin;

import java.io.*;

import cd4017be.compiler.*;


/**
 * 
 * @author CD4017BE */
public class IOStream extends Value {

	public static final Type IO = Type.builtin("io");

	private final OutputStream out;

	public IOStream(OutputStream out) {
		super(IO);
		this.out = out;
	}

	public static Value con(Arguments args, ScopeData scope) throws SignalError {
		try {
			IOStream io = (IOStream)args.in(0);
			CstBytes data = args.in(1).data();
			io.out.write(data.value, data.ofs, data.len);
			return io;
		} catch(IOException e) {
			return args.error(null, e);
		}
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		return new Value(IO);
	}

}
