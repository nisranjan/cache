# DISTRIBUTED CACHE #
This is a basic distributed cache implementation which supports a per node router.

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
