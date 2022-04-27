package cd4017be.dfc.lang;

import java.util.HashMap;

import static cd4017be.dfc.lang.HeaderParser.*;
import static cd4017be.dfc.lang.Signal.CONST;
import static cd4017be.dfc.lang.Signal.IMAGE;
import static cd4017be.dfc.lang.type.Pointer.NO_WRITE;
import static cd4017be.dfc.lang.type.Pointer.SHARED;

import cd4017be.dfc.lang.type.*;

/**
 * @author CD4017BE */
public class ExternalDefinitions {

	public final HashMap<String, CDecl> include = new HashMap<>();
	public final HashMap<String, String> macros = new HashMap<>();

	public Signal signal(String name) {
		CDecl decl = include.get(name);
		if (decl == null) return null;
		if (decl.dfcSignal != null) return decl.dfcSignal;
		int mode = (decl.type.mods & T_STORAGE) == T_TYPEDEF
			? IMAGE : CONST;
		return decl.dfcSignal = new Signal(convertType(decl.type), mode, 0L);
	}

	public void define(String name, Signal val) {
		CDecl decl = new CDecl();
		decl.dfcSignal = val;
		include.put(name, decl);
	}

	public String macro(String name) {
		return macros.get(name);
	}

	public void reset() {
		for (CDecl decl : include.values())
			if (decl.dfcSignal != null)
				resetDecl(decl);
	}

	private static void resetDecl(CDecl decl) {
		while(decl != null) {
			resetType(decl.type);
			decl.dfcSignal = null;
			decl = decl.next;
		}
	}

	private static void resetType(CType type) {
		if (type.dfcType == null) return;
		type.dfcType = null;
		Object cont = type.content;
		if (cont instanceof CDecl decl)
			resetDecl(decl.next);
		else if (cont instanceof CFunction func) {
			resetType(func);
			resetDecl(func.par);
		} else if (cont instanceof CType ctype)
			resetType(ctype);
	}

	public void clear() { 
		include.clear();
		macros.clear();
	}

	private static final Primitive[] intTypes = {
		Primitive.INT, Primitive.LONG, Primitive.LONG, Primitive.SHORT,
		Primitive.INT, Primitive.LONG, Primitive.LONG, Primitive.SHORT,
		Primitive.UINT, Primitive.ULONG, Primitive.ULONG, Primitive.USHORT
	};

	private static Type convertType(CType type) {
		if (type.dfcType != null) return type.dfcType;
		int m = type.mods;
		return type.dfcType = switch(m & T_TYPE) {
		case T_FLOAT -> Primitive.FLOAT;
		case T_DOUBLE -> Primitive.DOUBLE;
		case T_CHAR -> (m & T_SIGNED) != 0 ? Primitive.WORD : Primitive.UWORD;
		default -> intTypes[m >> 4 & 15];
		case T_VOID -> Types.VOID;
		case T_BOOL -> Primitive.BOOL;
		case T_STRUCT -> {
			CDecl elem = (CDecl)type.content;
			int n = 0;
			for (CDecl d = elem.next; d != null; d = d.next) n++;
			if (n == 0 && elem.name != null)
				yield Types.OPAQUE(elem.name);
			String[] names = new String[n];
			Type[] types = new Type[n];
			n = 0;
			for (CDecl d = elem.next; d != null; d = d.next, n++) {
				names[n] = d.name == null ? "" : d.name;
				types[n] = convertType(d.type);
			}
			yield Types.STRUCT(types, names);
		}
		case T_UNION -> Types.OPAQUE("union");//TODO union
		case T_ENUM -> null;//TODO enum
		case T_POINTER -> {
			CType elem = (CType)type.content;
			if (((m = elem.mods) & T_TYPE) == T_FUNCTION)
				yield convertType(elem);
			Pointer p = new Pointer((m & T_CONST) != 0 ? NO_WRITE : SHARED);
			type.dfcType = p;
			yield p.to(convertType(elem));
		}
		case T_ARRAY -> {
			CArray elem = (CArray)type.content;
			yield Types.VECTOR(convertType(elem), elem.size, false);
		}
		case T_FUNCTION -> {
			CFunction elem = (CFunction)type.content;
			int n = 0;
			for (CDecl d = elem.par; d != null; d = d.next) n++;
			String[] names = new String[n];
			Type[] types = new Type[n];
			n = 0;
			for (CDecl d = elem.par; d != null; d = d.next, n++) {
				names[n] = d.name == null ? "" : d.name;
				types[n] = convertType(d.type);
			}
			yield Types.FUNCTION(convertType(elem), types, names, elem.va_arg);
		}
		};
	}

}