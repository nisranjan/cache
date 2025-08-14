## Cache.Svc ##
This is a cloud native distributed cache service with a more reliable per node router.
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

[<img src="https://run.pstmn.io/button.svg" alt="Run In Postman" style="width: 128px; height: 32px;">](https://app.getpostman.com/run-collection/43236988-9ae3fbfc-4963-46fd-8686-5214148cbd81?action=collection%2Ffork&source=rip_markdown&collection-url=entityId%3D43236988-9ae3fbfc-4963-46fd-8686-5214148cbd81%26entityType%3Dcollection%26workspaceId%3D8e152caf-acc5-4aeb-83e2-e082ae66c835#?env%5BPRODUCTION%5D=W3sia2V5Ijoibm9kZTEiLCJ2YWx1ZSI6IjMuNi45My4yMTIiLCJlbmFibGVkIjp0cnVlLCJ0eXBlIjoiZGVmYXVsdCIsInNlc3Npb25WYWx1ZSI6IjMuNi45My4yMTIiLCJjb21wbGV0ZVNlc3Npb25WYWx1ZSI6IjMuNi45My4yMTIiLCJzZXNzaW9uSW5kZXgiOjB9LHsia2V5Ijoibm9kZTIiLCJ2YWx1ZSI6IjEzLjEyNy4yMzkuMjE3IiwiZW5hYmxlZCI6dHJ1ZSwidHlwZSI6ImRlZmF1bHQiLCJzZXNzaW9uVmFsdWUiOiIxMy4xMjcuMjM5LjIxNyIsImNvbXBsZXRlU2Vzc2lvblZhbHVlIjoiMTMuMTI3LjIzOS4yMTciLCJzZXNzaW9uSW5kZXgiOjF9XQ==)

#### Get ####
Gets a value for an existing Key-Value pair from the cache using HTTP GET Method.
```
HTTP GET request
http://{IP}:{Port}/api/cache/{key}
```
[<img src="https://run.pstmn.io/button.svg" alt="Run In Postman" style="width: 128px; height: 32px;">](https://app.getpostman.com/run-collection/43236988-9ae3fbfc-4963-46fd-8686-5214148cbd81?action=collection%2Ffork&source=rip_markdown&collection-url=entityId%3D43236988-9ae3fbfc-4963-46fd-8686-5214148cbd81%26entityType%3Dcollection%26workspaceId%3D8e152caf-acc5-4aeb-83e2-e082ae66c835#?env%5BPRODUCTION%5D=W3sia2V5Ijoibm9kZTEiLCJ2YWx1ZSI6IjMuNi45My4yMTIiLCJlbmFibGVkIjp0cnVlLCJ0eXBlIjoiZGVmYXVsdCIsInNlc3Npb25WYWx1ZSI6IjMuNi45My4yMTIiLCJjb21wbGV0ZVNlc3Npb25WYWx1ZSI6IjMuNi45My4yMTIiLCJzZXNzaW9uSW5kZXgiOjB9LHsia2V5Ijoibm9kZTIiLCJ2YWx1ZSI6IjEzLjEyNy4yMzkuMjE3IiwiZW5hYmxlZCI6dHJ1ZSwidHlwZSI6ImRlZmF1bHQiLCJzZXNzaW9uVmFsdWUiOiIxMy4xMjcuMjM5LjIxNyIsImNvbXBsZXRlU2Vzc2lvblZhbHVlIjoiMTMuMTI3LjIzOS4yMTciLCJzZXNzaW9uSW5kZXgiOjF9XQ==)

#### Delete ####
Deletes an existing Key-Value pair from the cache using HTTP DELETE Method
```
HTTP DELETE request
http://{IP}:{Port}/api/cache/{key}
```
[<img src="https://run.pstmn.io/button.svg" alt="Run In Postman" style="width: 128px; height: 32px;">](https://app.getpostman.com/run-collection/43236988-9ae3fbfc-4963-46fd-8686-5214148cbd81?action=collection%2Ffork&source=rip_markdown&collection-url=entityId%3D43236988-9ae3fbfc-4963-46fd-8686-5214148cbd81%26entityType%3Dcollection%26workspaceId%3D8e152caf-acc5-4aeb-83e2-e082ae66c835#?env%5BPRODUCTION%5D=W3sia2V5Ijoibm9kZTEiLCJ2YWx1ZSI6IjMuNi45My4yMTIiLCJlbmFibGVkIjp0cnVlLCJ0eXBlIjoiZGVmYXVsdCIsInNlc3Npb25WYWx1ZSI6IjMuNi45My4yMTIiLCJjb21wbGV0ZVNlc3Npb25WYWx1ZSI6IjMuNi45My4yMTIiLCJzZXNzaW9uSW5kZXgiOjB9LHsia2V5Ijoibm9kZTIiLCJ2YWx1ZSI6IjEzLjEyNy4yMzkuMjE3IiwiZW5hYmxlZCI6dHJ1ZSwidHlwZSI6ImRlZmF1bHQiLCJzZXNzaW9uVmFsdWUiOiIxMy4xMjcuMjM5LjIxNyIsImNvbXBsZXRlU2Vzc2lvblZhbHVlIjoiMTMuMTI3LjIzOS4yMTciLCJzZXNzaW9uSW5kZXgiOjF9XQ==)


### FEATURES ###
  - #### Cloud Native ####
       - Cache.svc current runs inside a Container (this service on AWS Elastic Container Service)
       - Cache.svc also runs as a Serverless service (Amazon Fargate)
       - All communication in Cache.svc is HTTP only (i.e. client to server & also server to server including routing calls)
  - #### Distributed Router ####
      - Every node in the server has a router - this is potentially a more reliable design than a single central router
      - Routing is based on Consistent Hashing algorithm;
      - Routers communicate to each other, if a key maps to a different server, then the request to read-write is forwarded to relevant server, otherwise its handled locally
  - #### LRU Cache ####
      - Currently supports only Least Recently Used algorithm
   
### Cache.Svc Architecture ###
[Read this for Cache.Svc architecture](/docs/ARCHITECTURE.md)

#### Version ####
2.0-SNAPSHOT
