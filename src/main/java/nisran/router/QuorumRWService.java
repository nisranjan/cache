package nisran.router;

import java.util.List;


//This interface is used to define the quorum read/write service
// It can be extended to include methods for quorum reads and writes in a distributed system.   
public interface QuorumRWService {
    /**
     * This method is used to perform a quorum read operation.
     * It should ensure that the read operation is performed on a majority of nodes
     * in the distributed system to ensure consistency.
     *
     * @param key The key to be read.
     * @return The value associated with the key, or null if not found.
     */
    Object quorumRead(String key);

    /**
     * This method is used to perform a quorum write operation.
     * It should ensure that the write operation is performed on a majority of nodes
     * in the distributed system to ensure consistency.
     *
     * @param key The key to be written.
     * @param value The value to be written.
     */
    List<String> quorumWrite(String key, Object value);

}
