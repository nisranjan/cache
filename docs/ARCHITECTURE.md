### Cache.Svc Architecture ###

Cache.Svc is a distributed service so there are multiple servers providing higher reliability, high reliability, high availability and some version of consistency : higher reliability is offered through a mix of higher fault tolerance architecture through per node router, data redundancy through replication (& virtual nodes) & resilience through consistent hashing algorithm implemented at router. However being a transient stateful service, Cache.Svc doesn't provide data durability.

### Per Node Router ###

A typical distributed stateful service (e.g. a typical SQL database) has master-slaves in the backend with a router configured 
like so. (e.g. PostGres, MSSQL, etc.)

<img width="451" height="372" alt="Master-Slave-Stateful Service" src="https://github.com/user-attachments/assets/1b312296-8dcd-433b-b832-5f52be479103" />

Cache.Svc uses a per node router architecture which offers higher fault tolerance. In comparison to a Master-Slave architecture where a failure of master can bring the service down (even in realworld databases like MSSQL the service goes down temporarily), the Per Node Router architecure offers uninterrupted service to clients. (Clients can potentially discover all nodes through service discovery)

<img width="437" height="341" alt="Cache Svc Per-Node-Router-Architecture" src="https://github.com/user-attachments/assets/5e7a6e8b-1b8b-4755-98d8-6a76fcca3343" />

A salient aspect is that router to router communication is HTTP based (as cloud native transport). This is potentially scaleable for the ring topology for this architecture using extended HTTP 3.0 using UDP.

### Higher resilience  ###

The router, on startup of node, registers itself to service discovery, and discovers others nodes as well through service discovery. It builds an internal map of the nodes using Consistent Hashing algorithm. Cache.Svc keys are mapped to this Consistent Hash ring. The server ring is rebuilt periodically which can be tuned through parameter router.router-refresh-interval-seconds application parameter.

When a node receives a client's request, it finds out the node to which this key is mapped (primary), if this is local, it services the request and sends a response. Otherwise, it finds the primary node, forwards the request to the primary node, collects the response from the primary node, and sends the response back to the client.

Service discovery, a router which periodically refreshes server dictionary with mapping using Consistent Hashing algorithm allows individual nodes to fail and new nodes to join seamlessly.

### Redundancy through data replication ###

For write requests (i.e. PUT), the routers writes to W virtual replicas which can be configured through application property service.quorum.write. This call is planned to remain blocking to ensure something about consistency.

Virtual replicas is currenly hardcoded to 1, and so this ensures that the application property provides a real replication guarantee.

For reads requests (i.e. GET), the router reads from R  replicas configured through application property service.quorum.read. In the implementation, this is currenly hardcoded to 1.

### Role of Service Discovery ###
- every router maintains a list of nodes maintained internally in a consistent hashing algorithm
- on startup, routers discover other services through service discovery
- client can potentially query the service discovery to recover all possible client
- health status and ensuring that a certain number of nodes are up



