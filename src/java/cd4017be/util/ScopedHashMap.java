package cd4017be.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;


/**A {@link HashMap} that keeps track of modifications within nested scopes.
 * So popping a scope will revert the map to the state of the outer scope.
 * @author CD4017BE */
@SuppressWarnings("serial")
public class ScopedHashMap<K, V> extends HashMap<K, V> {

	private ScopeFrame<K, V> scope;
	private int depth;

	/** Exit the current scope, reverting the map to it's state before the last call to {@link #push()}. */
	public void pop() {
		if (scope == null) throw new IllegalStateException("can't pop root scope");
		for (Modification<K, V> m = scope.mods; m != null; m = m.prev)
			if (m.value == null) super.remove(m.key);
			else super.put(m.key, m.value);
		scope = scope.outer;
		depth--;
	}

	/** Enter a nested scope that can be exited by {@link #pop()}. */
	public void push() {
		scope = new ScopeFrame<>(scope);
		depth++;
	}

	/**@return the current scope nesting depth = {@link #push() #pushes} - {@link #pop() #pops} */
	public int depth() {
		return depth;
	}

	private void addMod(K key, V val) {
		if (scope != null)
			scope.mods = new Modification<>(scope.mods, key, val);
	}

	/**{@inheritDoc HashMap#clear()}
	 * <br> Also pops back to the outermost scope.
	 * {@link #depth()} will be 0 after this call returns. */
	@Override
	public void clear() {
		super.clear();
		scope = null;
		depth = 0;
	}

	@Override
	public V put(K key, V value) {
		V old = super.put(key, value);
		if (old != value) addMod(key, old);
		return old;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V remove(Object key) {
		V old = super.remove(key);
		if (old != null) addMod((K)key, old);
		return old;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) { 
		if (scope == null) super.putAll(m);
		else for (Entry<? extends K, ? extends V> e : m.entrySet())
			put(e.getKey(), e.getValue());
	}

	@Override
	public V putIfAbsent(K key, V value) {
		V old = super.putIfAbsent(key, value);
		if (old == null) addMod(key, old);
		return old;
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) { 
		if (!super.replace(key, oldValue, newValue)) return false;
		addMod(key, oldValue);
		return true;
	}

	@Override
	public V replace(K key, V value) { 
		V old = super.replace(key, value);
		if (old != value) addMod(key, old);
		return old;
	}

	@Override
	public V computeIfAbsent(K key,
		Function<? super K, ? extends V> mappingFunction
	) {
		return super.computeIfAbsent(key, scope == null ? mappingFunction : k -> {
			V value = mappingFunction.apply(k);
			if (value != null) addMod(k, null);
			return value;
		});
	}

	private BiFunction<? super K, ? super V, ? extends V> replacer(
		BiFunction<? super K, ? super V, ? extends V> function
	) {
		return scope == null ? function : (key, old) -> {
			V val = function.apply(key, old);
			if (val != old) addMod(key, old);
			return val;
		};
	}

	@Override
	public V computeIfPresent(K key,
		BiFunction<? super K, ? super V, ? extends V> remappingFunction
	) {
		return super.computeIfPresent(key, replacer(remappingFunction));
	}

	@Override
	public V compute(K key,
		BiFunction<? super K, ? super V, ? extends V> remappingFunction
	) {
		return super.compute(key, replacer(remappingFunction));
	}

	@Override
	public V merge(K key, V value,
		BiFunction<? super V, ? super V, ? extends V> remappingFunction
	) {
		if (scope == null) return super.merge(key, value, remappingFunction);
		V old = get(key);
		if (old != null) value = remappingFunction.apply(old, value);
		if (value != old)
			addMod(key, value == null ? super.remove(key) : super.put(key, value));
		return value;
	}

	@Override
	public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
		super.replaceAll(replacer(function));
	}


	private static class ScopeFrame<K, V> {
		private final ScopeFrame<K, V> outer;
		private Modification<K, V> mods;

		private ScopeFrame(ScopeFrame<K, V> outer) {
			this.outer = outer;
		}
	}


	private static class Modification<K, V> {
		private final Modification<K, V> prev;
		private final K key;
		private final V value;

		private Modification(Modification<K, V> prev, K key, V value) {
			this.prev = prev;
			this.key = key;
			this.value = value;
		}
	}

}
