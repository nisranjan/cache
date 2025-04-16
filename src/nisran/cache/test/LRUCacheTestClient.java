package nisran.cache.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nisran.cache.LRUCache;

class LRUCacheTestClient {
	
	static int CACHE_CAPACITY = 2;
	
	LRUCache<Integer,Integer> subject;
	

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	@BeforeEach
	void setUp() throws Exception {
		subject = new LRUCache<Integer,Integer>(CACHE_CAPACITY);
	}

	@AfterEach
	void tearDown() throws Exception {
		subject = null;
	}

	@Test
	void testGet() {
		subject.set(1,1);
		assertEquals(1, subject.get(1));
		
	}

	@Test
	void testPut() {
		subject.set(2,2);
		assertEquals(2, subject.get(2));
	}
	
	@Test
	void testLeetCodeCase1()
	{
		//["LRUCache","put","put","get","put","get","put","get","get","get"]
		//[[2],[1,1],[2,2],[1],[3,3],[2],[4,4],[1],[3],[4]]
		
		//"LRUCache" - [2]
		subject = new LRUCache<Integer,Integer>(2);
		
		//"put" - [1,1]
		subject.set(1, 1);
		
		//"put" - [2,2]
		subject.set(2, 2);
		
		//"get" - [1]
		assertEquals(1, subject.get(1));
		
		//"put" - [3,3]
		subject.set(3, 3);
				
		//"get" - [2]
		assertEquals(null, subject.get(2));
		
		//"put" - [4,4]
		subject.set(4, 4);
				
		//"get" - [1]
		assertEquals(null, subject.get(1));
		
		//"get" - [3]
		assertEquals(3, subject.get(3));
		
		//"get" - [4]
		assertEquals(4, subject.get(4));
		
	}
}
