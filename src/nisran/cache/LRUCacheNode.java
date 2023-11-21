package nisran.cache;

import java.math.BigInteger;

public class LRUCacheNode<V extends Object> {
	
	/* Key Value Store */
	BigInteger key;
	V value;
	
	/* Bi-directional List 	 */
	LRUCacheNode<V> previous;
	LRUCacheNode<V> next;
	
	public LRUCacheNode(BigInteger key, V value) {
		this.key = key;
		this.value = value;
	}
}
