### Cache.Svc Architecture ###

Cache.Svc is a distributed service so there are multiple servers providing higher reliability, high availability and 
some version of consistency : higher reliability is offered through a mix of n node replication of data, per node router, 
virtual nodes, etc. However being a transient stateful service, Cache.Svc doesn't provide data durability.

### Per Node Router ###

A typical distributed stateful service (e.g. a typical SQL database) has master-slaves in the backend with a router configured 
like so. (e.g. PostGres, MSSQL, etc.)

<img width="451" height="372" alt="Master-Slave-Stateful Service" src="https://github.com/user-attachments/assets/1b312296-8dcd-433b-b832-5f52be479103" />
