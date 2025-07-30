### Cache.Svc Architecture ###

Cache.Svc is a distributed service so there are multiple servers providing higher reliability, high reliability, high availability and some version of consistency : higher reliability is offered through a mix of higher fault tolerance architecture through per node router, data redundancy through replication (& virtual nodes) & resilience through consistent hashing algorithm implemented at router. However being a transient stateful service, Cache.Svc doesn't provide data durability.

### Per Node Router ###

A typical distributed stateful service (e.g. a typical SQL database) has master-slaves in the backend with a router configured 
like so. (e.g. PostGres, MSSQL, etc.)

<img width="451" height="372" alt="Master-Slave-Stateful Service" src="https://github.com/user-attachments/assets/1b312296-8dcd-433b-b832-5f52be479103" />

Cache.Svc uses a per node router architecture which offers higher fault tolerance. In comparison to a Master-Slave architecture where a failure of master can bring the service down (even in realworld databases like MSSQL the service goes down temporarily), the Per Node Router architecure offers uninterrupted service to clients. (Clients can potentially discover all nodes through service discovery)

<img width="437" height="341" alt="Cache Svc Per-Node-Router-Architecture" src="https://github.com/user-attachments/assets/5e7a6e8b-1b8b-4755-98d8-6a76fcca3343" />

Another salient aspect is that router to router communication is HTTP based (as cloud native transport). This is potentially scaleable for the ring topology for this architecture using extended HTTP 3.0 using UDP.

### Role of Service Discovery ###
- every router maintains a list of nodes maintained internally in a consistent hashing algorithm
- on startup, routers discover other services through service discovery
- client can potentially query the service discovery to recover all possible client



