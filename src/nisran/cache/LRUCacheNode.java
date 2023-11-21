package nisran.cache;


public class LRUCacheNode<V extends Object> {
	
	/* Key Value Store */
	Integer key;
	V value;
	
	/* Bi-directional List 	 */
	LRUCacheNode<V> previous;
	LRUCacheNode<V> next;
	
	public LRUCacheNode(Integer key, V value) {
		this.key = key;
		this.value = value;
	}
}
