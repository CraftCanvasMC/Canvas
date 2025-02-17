# Status advancing scheduler

## The problem
A Minecraft world is made up of chunks. Each chunk has a status, which is one of the following:
- NEW: The holder of the chunk is created but not loaded
- EMPTY: The chunk is loaded (or created empty if not exists)
- STRUCTURE_STARTS: A world generation state (skipped if already reached)
- STRUCTURE_REFERENCES
- BIOMES
- NOISE
- SURFACE
- CARVERS
- FEATURES
- INITIALIZE_LIGHT
- LIGHT
- SPAWN
- FULL: Hands the chunk over to the server thread
- BLOCK_TICKING: The chunk is being ticked
- ENTITY_TICKING: The chunk is being ticked

Notes: 
- All statuses are ordered. A chunk can only reach a status if itself have reached the status before.
- Some of the statuses depends on neighbor chunks to reach a certain status. 
  For example, a chunk cannot reach `STRUCTURE_REFERENCES` status until all its 17x17 neighbors reach `STRUCTURE_STARTS` status.
- All tasks require locking the chunk itself.
- The tasks for certain statuses requires locking neighbor chunks.
  For example, a chunk attempting to reach `FEATURES` status requires locking all its 3x3 neighbors.
- Chunks can be downgraded.

## The solution
The scheduler is a thread that advances the status of chunks designed to be simple and efficient.

### The algorithm
Chunks are stored in a hash map (position -> chunk holder).
Every chunk holder holds tickets to maintain its status.

A ticket contains:
- The source of the ticket (the position of the requester)
- The status to be reached
- A callback to be called when the status is reached

The algorithm is simple:
- The scheduler maintains a queue of chunks to be updated.
- The scheduler picks a chunk from the queue and schedules a task to advance or downgrade its status.
  - For advancing: 
    - If the chunk requires neighbor chunks to be loaded to a certain status, it places a ticket to the neighbor chunks.
    - Otherwise, the task for the status is scheduled to run.
  - For downgrading:
    - If the chunk requires neighbor chunks to be loaded to a certain status, it removes the ticket to the neighbor chunks.
    - Then, the task for the status is scheduled to run.

