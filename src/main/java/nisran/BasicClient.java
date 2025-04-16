/**
 * A basic client implementation to test the LRU Cache
 */
package nisran;

import nisran.cache.LRUCache;

/**
 * A basic client implementation to test the LRU Cache
 */
public class BasicClient {
	
	static LRUCache<Integer,String> cache;
	static Integer key;
	static String value;

	static void init(Integer capacity) {
		BasicClient.cache = new LRUCache<Integer,String>(capacity);
	}
	
	static void testSet(Integer key, String value) {
		
		cache.set(key, value);
		BasicClient.key = key;
		BasicClient.value = value;
		//Cache should contain the key-value
		assert (BasicClient.value == cache.get(key));
		System.out.println("\n ******* Inside testSet(K,V) *******");
		System.out.println("Cache contains key " + key );
		//Cache head should be the current key
		//assert (cache.head == cache.dict.get(key));
		//System.out.println("\n ******* Inside testPut(K,V) *******");
		//System.out.println("Key of head is  " + cache.head.key );
	}
	
	static void testGet(Integer key) {
		
		//assert(cache.list.size() != 0);
		
		String value = cache.get(key);
		
		if(value == null) {
			System.out.println("\n ******* Inside testGet(K) *******");
			System.out.println("Cache Miss!!");
		}else {
			//Node should be head after SEARCH
			//Testing is not correct, Assertions are not working
			assert(value == BasicClient.value);
			System.out.println("\n ******* Inside testGet(K) *******");
			System.out.println("Value at head is  " + value );
		}
		
	}
	
	static Boolean testPutAndGetCase0() {
		
		
		
		return true;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		BasicClient.init(1);
		//BasicCacheClient.testGet("Nishant".hashCode());
		BasicClient.testSet("First String".hashCode(), "First String");
		BasicClient.testGet("First String".hashCode());
		BasicClient.testSet("Second String".hashCode(), "Second String");
		BasicClient.testGet("First String".hashCode());
		BasicClient.testGet("Second String".hashCode());
	}

}
