## Cache ##
This is a cloud native distributed cache implementation which supports a per node router.
### Usage ###
#### Put ####
Puts and Key-Value pair in the cache using HTTP POST. 
The key is typically a numeric value (but not essential) and the value can be a complex object.
```
HTTP POST request
http://{IP}:{Port}/api/cache/{key}
```
A sample object
```JSON
{
    "key": "0",
    "value": {
        "person":{
            "name":"Zeroooo",
            "address":"Texas, USA"
        }
    }
}
```

[![GET](https://run.pstmn.io/button.svg)](https://www.postman.com/telecoms-geoscientist-17189098/workspace/cache-workspace/request/43236988-fa9bd59a-47aa-4fe3-b8b2-80dbfaf33ff8)

#### Get ####
Gets a value for an existing Key-Value pair from the cache using HTTP GET Method.
```
HTTP GET request
http://{IP}:{Port}/api/cache/{key}
```
[![GET](https://run.pstmn.io/button.svg)](https://www.postman.com/telecoms-geoscientist-17189098/workspace/cache-workspace/request/43236988-f0927579-9539-464f-86ce-14f1757fed61)

#### Delete ####
Deletes an existing Key-Value pair from the cache using HTTP DELETE Method
```
HTTP DELETE request
http://{IP}:{Port}/api/cache/{key}
```
[![DELETE](https://run.pstmn.io/button.svg)](https://www.postman.com/telecoms-geoscientist-17189098/workspace/cache-workspace/collection/43236988-9ae3fbfc-4963-46fd-8686-5214148cbd81)


### FEATURES ###
  - #### Cloud Native ####
    Cache.svc current runs inside a Container (this service on AWS Elastic Container Service)
    Cache.svc also runs as a Serverless service (Amazon Fargate)
    All communication in Cache.svc is HTTP only (i.e. client to server & also server to server including routing calls)
  - #### Distributed Router ####
      - Every node in the server has a router - this is potentially a more reliable design than a single central router
      - Routing is based on Consistent Hashing algorithm;
      - Routers communicate to each other, if a key maps to a different server, then the request to read-write is forwarded to relevant server, otherwise its handled locally
  - #### LRU Cache ####
    Currently supports only Least Recently Used algorithm

#### Version ####
2.0-SNAPSHOT
