/**
 * 
 */
package nisran.cache;


/**
 * A basic client implementation to test the LRU Cache
 */
public class BasicCacheClient {
	
	static LRUCache<String> cache;

	static void init(Integer capacity) {
		BasicCacheClient.cache = new LRUCache<String>(capacity);
	}
	
	static void testPut(Integer key, String value) {
		cache.put(key, value);
		//Cache should contain the key-value
		assert (cache.map.containsKey(key));
		System.out.println("Cache contains key " + key );
		//Cache head should be the current key
		assert (cache.head == cache.map.get(key));
		System.out.println("Key of head is  " + cache.head.key );
	}
	
	static void testGet(Integer key) {
		System.out.println("\n ******* Inside testGet() *******");
		assert(cache.currSize != 0);
		
		String value = cache.get(key);
		
		if(value == null) {
			System.out.println("Cache Miss!!");
		}else {
			//Node should be head after SEARCH
			assert(value == cache.head.value);
			System.out.println("Value at head is  " + cache.head.value );
		}
		
	}
	
	static Boolean testPutAndGetCase0() {
		
		
		
		return true;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		BasicCacheClient.init(1);
		//BasicCacheClient.testGet("Nishant".hashCode());
		BasicCacheClient.testPut("First String".hashCode(), "First String");
		BasicCacheClient.testGet("First String".hashCode());
		BasicCacheClient.testPut("Second String".hashCode(), "Second String");
		BasicCacheClient.testGet("First String".hashCode());
		BasicCacheClient.testGet("Second String".hashCode());
	}

}
