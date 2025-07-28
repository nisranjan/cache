## Cache ##
This is a cloud native distributed cache implementation which supports a per node router.

### Put ###
Puts and object in the cache

[![PUT](https://run.pstmn.io/button.svg)](https://www.getpostman.com/run-collection/YOUR_COLLECTION_ID)

### Get ###
Get (an existing) object from the cache

[![GET](https://run.pstmn.io/button.svg)](https://www.getpostman.com/run-collection/YOUR_COLLECTION_ID)

### Delete ###
Deletes (existing) object from the cache

[![DELETE](https://run.pstmn.io/button.svg)](https://www.getpostman.com/run-collection/YOUR_COLLECTION_ID)


### FEATURES ###
  - #### Cloud Native ####
    This is currenly implemented to work only on Amazon AWS. Plus all communication is HTTP only (even server to server communication).
  - #### Serverless ####
    Also this currently works only on FARGATE (Serverless technology) although can easily be configured for EC2 instances
  - #### Distributed Router ####
      - Every node in the server has a router - this is potentially a more reliable design than a single central router
      - Routing is based on Consistent Hashing algorithm;
      - Routers communicate to each other, if a key maps to a different server, then the request to read-write is forwarded to relevant server, otherwise its handled locally
  - #### LRU Cache ####
    Currently supports only Least Recently Used algorithm

#### Version ####
2.0-SNAPSHOT
