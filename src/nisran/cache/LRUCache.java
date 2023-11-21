package nisran.cache;

import java.util.HashMap;
import java.util.Map;

public class LRUCache<V> {
	
	Integer capacity;
	Integer currSize;
	
	LRUCacheNode<V> head;
	LRUCacheNode<V> tail;
	
	Map<Integer, LRUCacheNode<V>> map;
	
	
	public LRUCache(Integer size) {
		//Set Capacity as Size
		this.capacity = size;
		//Set Current Size as 0
		this.currSize = 0;
		//Init the Map
		map = new HashMap<Integer, LRUCacheNode<V>>(this.capacity);	
		
	}
	
	public V get(Integer key) {
		LRUCacheNode<V> node = map.get(key); //Case : SEARCH in Cache
		//TODO: value could be null, which means a cache miss, 
		//TODO: handle in calling class
		
		if(node != null) { //Case: Extract and Push the Node
			extractAndPushToHead(node);
			return node.value;
		}else { //Case : Cache Miss : Handle in Calling function
			return null;
		}
		
	}
	
	public void put(Integer key, V value) {
		System.out.println("\n ***** Inside LRUCache.put(K,V) ******");
		if(map.containsKey(key)) { //Case : UPDATE in Cache
			LRUCacheNode<V> node = map.get(key);
			node.value = value; 
			//TODO: Handle in the case of Write-Aside Cache
			//TODO: Remove the Row with Key as P_Key from dB
			extractAndPushToHead(node);
		}else { //Case : INSERT in Cache
			if(currSize == capacity) { //Evict the LRU Node which is tail
				LRUCacheNode<V> penultimate = tail.previous;
				//Remove the tail node from HashMap
				map.remove(key);
				//Remove the tail from the DoublyLinkedList
				if(penultimate != null) {
					penultimate.next=null;
					System.out.println("Tail is evicted, key was" + tail.key);
					tail = penultimate;
				}
				currSize--;
				
			}
			LRUCacheNode<V> newNode = new LRUCacheNode<V>(key, value);
			//Add newNode to the head of the DoublyLinkedList
			newNode.next = head;
			if(head != null) { //Handle corner case of first element
				head.previous = newNode;
			}else {
				head = tail = newNode;
			}
			head = newNode;
			map.put(key, newNode);
			currSize++;
		}
	}
	
	private void extractAndPushToHead(LRUCacheNode<V> node) {
		
		// Case: When node is HEAD
		if(head == node) { 
			//Nothing to do
			return;
		}
		
		//Removing the node from the DoublyLinkedList
		//By connecting the previous node with next node
		LRUCacheNode<V> prevNode = node.previous;
		LRUCacheNode<V> nextNode = node.next;
		
		prevNode.next = nextNode;
		
		//Case: When node is TAIL
		if(nextNode != null) { 
			nextNode.previous = prevNode;
		}else { //Case: When node is tail
			tail = prevNode;
		}
		
		//Adding the removed node to head
		node.next = head;
		if(head != null) {
			head.previous = node;
		}
		head = node;
	}

}
