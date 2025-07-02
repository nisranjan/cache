package nisran.cache;

@Deprecated
public class LRUCacheNode<V extends Object> {
	
	/* Key Value Store */
	Integer key;
	V value;
	
	/* Bi-directional List 	 */
	LRUCacheNode<V> prev;
	LRUCacheNode<V> next;
	
	public LRUCacheNode(Integer key, V value) {
		this.key = key;
		this.value = value;
	}
}
