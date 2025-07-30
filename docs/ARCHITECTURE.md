### Cache.Svc Architecture ###

Cache.Svc is a distributed service offering higher reliability, high availability and some version of consistency : higher reliability is offered through a mix of higher fault tolerance architecture through per node router, data redundancy through replication (& virtual nodes) & resilience through consistent hashing algorithm implemented at router. However Cache.Svc doesn't provide data durability.

### Per Node Router ###

A typical distributed stateful service (e.g. a typical SQL database) has Master-Slave architecture with a router associated with master which communicates with slaves. (e.g. PostGres, MSSQL, etc.) Client which want to write / read have to connect to master.

<img width="451" height="372" alt="Master-Slave-Stateful Service" src="https://github.com/user-attachments/assets/1b312296-8dcd-433b-b832-5f52be479103" />

Cache.Svc uses a per node router architecture which offers higher fault tolerance. In comparison to a Master-Slave architecture where a failure of master can bring the service down (even in realworld databases like MSSQL the service goes down temporarily), the Per Node Router architecure offers a more fault-tolerant service to clients. 

<img width="437" height="341" alt="Cache Svc Per-Node-Router-Architecture" src="https://github.com/user-attachments/assets/5e7a6e8b-1b8b-4755-98d8-6a76fcca3343" align="center" />

A salient aspect is that router to router communication is HTTP based (as cloud native transport) versus proprietary protocols. 

### CH Router, Service discovery offer higher resilience  ###

The router, on startup of node, registers itself to service discovery, and discovers others nodes as well through service discovery. It builds an internal map of the nodes using Consistent Hashing algorithm. Cache.Svc keys are mapped to this Consistent Hash ring. The server ring is rebuilt periodically. How often this happens can be tuned through parameter __router.router-refresh-interval-seconds__ application parameter.

<img width="460" height="350" alt="Cache Svc Router-Service-Discovery-CHAlgo" src="https://github.com/user-attachments/assets/d4c59f94-e7e2-48d9-bb7d-f2cd53a520fb" />


When a node receives a client's request, it finds out the node to which this key is mapped (primary), if this is local, it services the request and sends a response. Otherwise, it finds the primary node, forwards the request to the primary node, collects the response from the primary node, and sends the response back to the client.

Service discovery, a router which periodically refreshes server dictionary with mapping using Consistent Hashing algorithm allows individual nodes to fail and new nodes to join seamlessly.

### Redundancy through data replication ###

For write requests (i.e. PUT), the routers writes to W virtual replicas which can be configured through application property __service.quorum.write__. This call is planned to remain blocking to ensure something about consistency.

Virtual replicas is currenly hardcoded to 1, and so this ensures that the application property provides a real replication guarantee.

For reads requests (i.e. GET), the router reads from R  replicas configured through application property __service.quorum.read__. In the implementation, this is currenly hardcoded to 1.

### Role of Service Discovery ###
Service Discovery's role is crucial in this architecture. It maintains a list of healthy server nodes in the cluster. For this it send health status updates periodically. If the number of servers are below a predefined number then service discovery starts a new node.

Individual routers (associated to nodes) use service discovery to (re)build their server dictionary periodically. Potentially clients can also do the same.


### Future Work ###
Cache.Svc is lacking in implemenation from the perspective of redistributing the keys when a new server is added. This plus Apache Cassandra style tunable consistency could be areas of improvement.



