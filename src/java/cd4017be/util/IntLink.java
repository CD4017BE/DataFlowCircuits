package cd4017be.util;

/**Simple linked list node for int values.
 * @author CD4017BE */
public class IntLink {

	public IntLink next;
	public int val;

	public IntLink(IntLink next, int val) {
		this.next = next;
		this.val = val;
	}

}
