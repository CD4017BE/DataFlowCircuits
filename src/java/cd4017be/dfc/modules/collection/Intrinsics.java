package cd4017be.dfc.modules.collection;

import static cd4017be.dfc.lang.Value.NO_ELEM;
import static modules.core.Intrinsics.dataRead8;
import static modules.core.Intrinsics.elemNew;
import static modules.loader.Intrinsics.NULL;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import cd4017be.dfc.lang.Module;
import cd4017be.dfc.lang.Type;
import cd4017be.dfc.lang.Value;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Impl;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Init;

/**DFC hash map and array list implementation
 * @author cd4017be */
public class Intrinsics {

	public static Type VOID, INT, MAP, LIST;

	@Init(phase = Init.POST)
	public static void init(Module m) {
		VOID = m.findType("void");
		INT = m.findType("int");
		MAP = m.findType("map");
		LIST = m.findType("list");
	}

	@Impl(inputs = 2, outType = "INT")
	public static int typeFind(Value[] arr, Type type) {
		for (int i = 0; i < arr.length; i++) 
			if (arr[i].type == type)
				return i;
		return -1;
	}

	@Impl(inputs = 2, outType = "INT")
	public static int elemFind(Value[] arr, Value val) {
		for (int i = 0; i < arr.length; i++)
			if (arr[i].equals(val))
				return i;
		return -1;
	}

	@Impl(inputs = 4, outType = "INT")
	public static int dataFind(byte[] data, int from, int to, byte[] val) {
		int len = val.length; to -= len;
		find: for (int i = from; i < to; i++) {
			for (int j = 0; j < len; j++)
				if (data[i+j] != val[j])
					continue find;
			return i;
		}
		return -1;
	}

	@Impl(inputs = 0)
	public static Value mapNew() {
		return new Value(MAP, NO_ELEM, new byte[8], 0);
	}

	@Impl(inputs = 0)
	public static Value listNew() {
		return new Value(LIST, NO_ELEM, new byte[8], 0);
	}

	private static void check(Value map) {
		if (dataRead8(map.data, 0) != map.value)
			throw new ConcurrentModificationException();
	}

	@Impl(inputs = 1, outType = "INT")
	public static long len(Value map) {
		check(map);
		return map.value & 0xffffffffL;
	}

	private static int index(Value list, int idx) {
		check(list);
		if (idx >= (int)list.value) throw new IndexOutOfBoundsException(idx);
		return idx;
	}

	@Impl(inputs = 2)
	public static Value listGet(Value list, int idx) {
		return list.elements[index(list, idx)];
	}

	@Impl(inputs = 3)
	public static Value listSet(Value list, int idx, Value val) {
		list.elements[index(list, idx)] = val;
		return updateMap(list, list.elements, list.value);
	}

	@Impl(inputs = 3)
	public static Value listAdd(Value list, int idx, Value val) {
		check(list);
		int l = (int)list.value;
		if (idx > l) throw new IndexOutOfBoundsException(idx);
		Value[] arr = list.elements;
		if (l == arr.length) {
			arr = new Value[l == 0 ? 16 : l << 1];
			Arrays.fill(arr, l + 1, arr.length, NULL);
			System.arraycopy(list.elements, 0, arr, 0, idx);
		}
		arr[idx] = val;
		System.arraycopy(list.elements, idx, arr, idx + 1, l - idx);
		return updateMap(list, arr, list.value + 1);
	}

	@Impl(inputs = 2)
	public static Value listRem(Value list, int idx) {
		check(list);
		int l = (int)list.value;
		if (idx >= l) throw new IndexOutOfBoundsException(idx);
		Value[] arr = list.elements;
		System.arraycopy(arr, idx + 1, arr, idx, l - (idx + 1));
		arr[l - 1] = NULL;
		return updateMap(list, arr, list.value - 1);
	}

	@Impl(inputs = 2)
	public static Value mapGetNode(Value map, byte[] key) {
		check(map);
		Value[] table = map.elements;
		int l = table.length;
		if (l == 0) return NULL;
		int hash = Arrays.hashCode(key);
		hash ^= hash >>> 16;
		for (Value e = table[hash & l - 1]; e.elements.length == 2; e = e.elements[0])
			if ((int)e.value == hash && Arrays.equals(e.data, key))
				return e;
		return NULL;
	}

	private static Value[] resize(Value map, int l) {
		Value[] table = elemNew(l << 1);
		for (int i = 0; i < l; i++) {
			Value e = map.elements[i];
			if (e.elements.length != 2) continue;
			Value next = e.elements[0];
			if (next.elements.length != 2) {
				table[(int)e.value & l * 2 - 1] = e;
				continue;
			}
			Value loTail = null, loHead = null, hiTail = null, hiHead = null;
			for (;; next = next.elements[0]) {
				if (((int)e.value & l) == 0) {
					if (loTail == null) loHead = e;
					else loTail.elements[0] = e;
					loTail = e;
				} else {
					if (hiTail == null) hiHead = e;
					else hiTail.elements[0] = e;
					hiTail = e;
				}
				if ((e = next).elements.length != 2) break;
			}
			if (loTail != null) {
				loTail.elements[0] = NULL;
				table[i] = loHead;
			}
			if (hiTail != null) {
				hiTail.elements[0] = NULL;
				table[i + l] = hiHead;
			}
		}
		return table;
	}

	private static Value updateMap(Value map, Value[] table, long counts) {
		counts += 0x100000000L;
		ByteBuffer.wrap(map.data).putLong(counts);
		return new Value(map.type, table, map.data, counts);
	}

	@Impl(inputs = 2, outType = "VOID")
	public static Value[] mapGetOrCreateNode(Value map, byte[] key) {
		check(map);
		Value[] table = map.elements;
		int l = table.length;
		if (l == 0) table = elemNew(l = 16);
		int hash = Arrays.hashCode(key);
		hash ^= hash >>> 16;
		int idx = hash & l - 1;
		Value last = null;
		for (Value e = table[idx]; e.elements.length == 2; last = e, e = e.elements[0])
			if ((int)e.value == hash && Arrays.equals(e.data, key))
				return new Value[] {updateMap(map, table, map.value), e};
		Value node = new Value(Intrinsics.VOID, new Value[] {NULL, NULL}, key, hash);
		if (last != null) last.elements[0] = node;
		else table[idx] = node;
		long counts = map.value + 1;
		if ((int)counts > (l >> 2 | l >> 1) && l < 0x40000000)
			table = resize(map, l);
		return new Value[] {updateMap(map, table, counts), node};
	}

	@Impl(inputs = 2)
	public static Value mapGet(Value map, byte[] key) {
		Value val = mapGetNode(map, key);
		return val.elements.length == 2 ? val.elements[1] : val;
	}

	@Impl(inputs = 3)
	public static Value mapPut(Value map, byte[] key, Value val) {
		Value[] n = mapGetOrCreateNode(map, key);
		n[1].elements[1] = val;
		return n[0];
	}

}
