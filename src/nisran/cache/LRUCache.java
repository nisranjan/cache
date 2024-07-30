package nisran.cache;

import java.util.HashMap;
import java.util.Map;

public class LRUCache<V> {
	
	Integer capacity;
	Integer currSize;
	
	LRUCacheNode<V> head;
	LRUCacheNode<V> tail;
	
	Map<Integer, LRUCacheNode<V>> dict;
	
	
	public LRUCache(Integer size) {
		//Set Capacity as Size
		this.capacity = size;
		//Add assertion here that size ! = 0
		
		//Set Current Size as 0
		this.currSize = 0;
		//Init the Dictionary
		dict = new HashMap<Integer, LRUCacheNode<V>>(this.capacity);
		
		//Create Sentinels for HEAD and TAIL
		head = new LRUCacheNode<V>(Integer.MAX_VALUE,null);
		dict.put(Integer.MAX_VALUE,head);
		
		tail = new LRUCacheNode<V>(Integer.MIN_VALUE,null);
		dict.put(Integer.MIN_VALUE,tail);
		
		head.next = tail;
		tail.prev = head;
		
	}
	
	/**
	 * Given an integer key, 
	 * 				the value associated with key if that exists
	 * 				or 'null' if the key doesn't exist in Cache
	 * 
	 * @param key
	 * @return
	 */
	public V get(Integer key) {
		//Case 1: SEARCH in Cache
		if(dict.containsKey(key)) {
			//Removing and adding the key will perform the
			//extractAndPushToHead() functionality of LRUCache
			LRUCacheNode<V> node = remove(key);
			
			node = add(node.key,node.value);
			//This creates a new Object and high activity 
			//will lead to Garbage Collection
			//One way to solve the problem would be to override the add method 
			//With one which uses a LRUCacheNode
			return node.value;
		}else {
			//Case : Cache Miss : Handle in Calling function
			//TODO: value could be null, which means a cache miss, 
			//TODO: handle in calling class
			return null;
		}
		
		
	}
	
	public void put(Integer key, V value) {
		if(dict.containsKey(key)) { //Case : UPDATE in Cache
			LRUCacheNode<V> node = remove(key);
			//TODO: replace with over-riden function to reduce object creation
			node = add(node.key,node.value);
			node.value = value; 
			//TODO: Handle in the case of Write-Aside Cache
			//TODO: Remove the Row with Key as P_Key from dB
			///Deprecated :extractAndPushToHead(node);
		}else { //Case : INSERT in Cache
			if(currSize == capacity) { //Evict the LRU Node which is tail
				System.out.println("\n ***** Inside LRUCache.put(K,V) ******");
				System.out.println("Tail will be evicted, key is" + tail.prev.key);

				//Remove the penultimate node
				LRUCacheNode<V> penultimate = tail.prev;
				remove(penultimate.key);
				currSize--;
				
			}
			@SuppressWarnings("unused")
			LRUCacheNode<V> newNode = add(key, value);
			//Add newNode 
			currSize++;
		}
	}
	
	
	private LRUCacheNode<V> add(Integer key, V value){
		LRUCacheNode<V> newNode = new LRUCacheNode<V>(key, value);
		
		
		//Add New Node at the Head of the LinkedList
		//Corner Case 1: Adding the first node
		//Main Case 2: Adding the second node
		LRUCacheNode<V> firstNode = head.next;
		//Connecting newNode and HEAD
		head.next = newNode;
		newNode.prev = head;
		
		//Connecting previous first Node with newNode
		//CornerCase : 1st Node, in that case firstNode will be tail
		newNode.next = firstNode;
		firstNode.prev = newNode;
		
		//Add the newNode to HashMap data structure
		dict.put(key, newNode);

		
		return newNode;
	}
	
	private LRUCacheNode<V> remove(Integer key){
		LRUCacheNode<V> res = null;
		//Check if the node exists in the dictionary
		//If exists, get the node
		//Delete the Node from dictionary
		if(dict.containsKey(key)) {
			res = dict.remove(key);
			
			//Remove the LLNode from the LinkedList
			//Main Case: Node is in the middle
			//Corner Case 1: If node is head
			//Corner Case 2: If node is tail
			//Adding a Sentinel for HEAD & TAIL will remove the Corner Case 1 & 2
			LRUCacheNode<V> prev = res.prev;
			LRUCacheNode<V> next = res.next;
			prev.next = next;
			next.prev = prev;
			
			res.prev = null;
			res.next = null;
		}
		
		return res;
	}

}
