


How do we get a pointer to the right mutex to use for the region? I suppose every region metadata will have a pointer to the mutex it should use. If it's a function-bound region, itll point at the thread's mutex, and if it's a mutexed region, itll point at that mutex.


