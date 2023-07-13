
```
struct Future<imm'T, r'T, x'> {
  x pthread_thread_t;
}

func launch<imm'T, x' rw, R>(lambda &r'T)
where pure func(&T)'R Futurex'{
  
}
```

imm'T means this coord's region must be immutable




a future should be a builtin. should probably take in an imm&self for its launch, but not store it. itll contain a ptr to the thread stack block which contains the pthread_thread_t and the condition var at the top.

the struct itself will be parameterized on that lambda, a pure call func bound, plus the current default region. the launch function wont be parameterized.

the struct should take an imm region as a generic param. that can ensure it doesnt become mutable again. easy.


