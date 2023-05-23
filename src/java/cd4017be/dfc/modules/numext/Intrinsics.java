package cd4017be.dfc.modules.numext;

import static java.nio.charset.StandardCharsets.US_ASCII;
import cd4017be.dfc.lang.Module;
import cd4017be.dfc.lang.Type;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Impl;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Init;

/**
 * @author cd4017be */
public class Intrinsics {

	public static Type INT, FLOAT, STRING;

	@Init(phase = Init.POST)
	public static void init(Module m) {
		INT = m.findType("int");
		FLOAT = m.findType("float");
		STRING = m.findType("string");
	}

	//unsigned int operations:

	@Impl(inputs = 1, outType = "INT")
	public static long strToUint(byte[] str) {
		return Long.parseUnsignedLong(new String(str, US_ASCII));
	}

	@Impl(inputs = 1, outType = "STRING")
	public static byte[] uintToStr(long val) {
		return Long.toUnsignedString(val).getBytes();
	}

	@Impl(inputs = 2, outType = "INT")
	public static long udiv(long a, long b) {
		return Long.divideUnsigned(a, b);
	}

	@Impl(inputs = 2, outType = "INT")
	public static long umod(long a, long b) {
		return Long.remainderUnsigned(a, b);
	}

	@Impl(inputs = 2, outType = "INT")
	public static long ushr(long a, long b) {
		return a >>> b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static int ucomp(long a, long b) {
		return Long.compareUnsigned(a, b);
	}

	//float operations:

	@Impl(inputs = 1, outType = "FLOAT")
	public static double strToFloat(byte[] str) {
		return Double.parseDouble(new String(str, US_ASCII));
	}

	@Impl(inputs = 1, outType = "STRING")
	public static byte[] floatToStr(double val) {
		return Double.toString(val).getBytes();
	}

	@Impl(inputs = 1, outType = "FLOAT")
	public static double intToFloat(long val) {
		return (double)val;
	}

	@Impl(inputs = 1, outType = "INT")
	public static long floatToInt(double val) {
		return (long)val;
	}

	@Impl(inputs = 2, outType = "FLOAT")
	public static double fadd(double a, double b) {
		return a + b;
	}

	@Impl(inputs = 2, outType = "FLOAT")
	public static double fsub(double a, double b) {
		return a - b;
	}

	@Impl(inputs = 2, outType = "FLOAT")
	public static double fmul(double a, double b) {
		return a * b;
	}

	@Impl(inputs = 2, outType = "FLOAT")
	public static double fdiv(double a, double b) {
		return a / b;
	}

	@Impl(inputs = 2, outType = "FLOAT")
	public static double fmod(double a, double b) {
		return a % b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long fcomp(double a, double b) {
		return a < b ? -1 : a > b ? 1 : 0;
	}

}
