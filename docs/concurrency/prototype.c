// clang main.c -c -target thumbv7m-none-eabi -fstack-size-section
// objdump -ds main.o

#include <stdint.h>
//#include <pthread.h>
//#include <stdlib.h>
//#include <stdio.h>

// stack size: 768 (95 locals + another int maybe the param)
uint64_t bar(uint64_t x) {
	// Cause some register spilling
	uint64_t a = x;
	uint64_t b = x;
	uint64_t c = x;
	uint64_t d = x;
	uint64_t e = x;
	uint64_t f = x;
	uint64_t g = x;
	uint64_t h = x;
	uint64_t i = x;
	uint64_t j = x;
	uint64_t k = x;
	uint64_t l = x;
	uint64_t m = x;
	uint64_t n = x;
	uint64_t o = x;
	uint64_t p = x;
	uint64_t q = x;
	uint64_t r = x;
	uint64_t s = x;
	uint64_t a2 = x;
	uint64_t b2 = x;
	uint64_t c2 = x;
	uint64_t d2 = x;
	uint64_t e2 = x;
	uint64_t f2 = x;
	uint64_t g2 = x;
	uint64_t h2 = x;
	uint64_t i2 = x;
	uint64_t j2 = x;
	uint64_t k2 = x;
	uint64_t l2 = x;
	uint64_t m2 = x;
	uint64_t n2 = x;
	uint64_t o2 = x;
	uint64_t p2 = x;
	uint64_t q2 = x;
	uint64_t r2 = x;
	uint64_t s2 = x;
	uint64_t a3 = x;
	uint64_t b3 = x;
	uint64_t c3 = x;
	uint64_t d3 = x;
	uint64_t e3 = x;
	uint64_t f3 = x;
	uint64_t g3 = x;
	uint64_t h3 = x;
	uint64_t i3 = x;
	uint64_t j3 = x;
	uint64_t k3 = x;
	uint64_t l3 = x;
	uint64_t m3 = x;
	uint64_t n3 = x;
	uint64_t o3 = x;
	uint64_t p3 = x;
	uint64_t q3 = x;
	uint64_t r3 = x;
	uint64_t s3 = x;
	uint64_t a4 = x;
	uint64_t b4 = x;
	uint64_t c4 = x;
	uint64_t d4 = x;
	uint64_t e4 = x;
	uint64_t f4 = x;
	uint64_t g4 = x;
	uint64_t h4 = x;
	uint64_t i4 = x;
	uint64_t j4 = x;
	uint64_t k4 = x;
	uint64_t l4 = x;
	uint64_t m4 = x;
	uint64_t n4 = x;
	uint64_t o4 = x;
	uint64_t p4 = x;
	uint64_t q4 = x;
	uint64_t r4 = x;
	uint64_t s4 = x;
	uint64_t a5 = x;
	uint64_t b5 = x;
	uint64_t c5 = x;
	uint64_t d5 = x;
	uint64_t e5 = x;
	uint64_t f5 = x;
	uint64_t g5 = x;
	uint64_t h5 = x;
	uint64_t i5 = x;
	uint64_t j5 = x;
	uint64_t k5 = x;
	uint64_t l5 = x;
	uint64_t m5 = x;
	uint64_t n5 = x;
	uint64_t o5 = x;
	uint64_t p5 = x;
	uint64_t q5 = x;
	uint64_t r5 = x;
	uint64_t s5 = x;
	return a + b + c + d + e + f + g + h + i + j + k + l + m + n + o + p + q + r + s +
		a2 + b2 + c2 + d2 + e2 + f2 + g2 + h2 + i2 + j2 + k2 + l2 + m2 + n2 + o2 + p2 + q2 + r2 + s2 +
		a3 + b3 + c3 + d3 + e3 + f3 + g3 + h3 + i3 + j3 + k3 + l3 + m3 + n3 + o3 + p3 + q3 + r3 + s3 +
		a4 + b4 + c4 + d4 + e4 + f4 + g4 + h4 + i4 + j4 + k4 + l4 + m4 + n4 + o4 + p4 + q4 + r4 + s4 +
		a5 + b5 + c5 + d5 + e5 + f5 + g5 + h5 + i5 + j5 + k5 + l5 + m5 + n5 + o5 + p5 + q5 + r5 + s5;
}
// Stack size 32. 2 params plus 2 more? perhaps its spilling that a into a register?
uint64_t foo(uint64_t a, uint64_t b) {
	return a + bar(b);
}

// Stack size 8?
void* threadmain(void*) {
	return (void*)foo(3, 7);
}

// total: 808


int main() {
	threadmain(0);
   //pthread_attr_t attr;
   //int rc = pthread_attr_init(&attr);
   //if (rc == -1) {
   //   perror("error in pthread_attr_init");
   //   exit(1);
   //}
   //int s1 = 808;
   //rc = pthread_attr_setstacksize(&attr, s1);
   //if (rc == -1) {
   //   perror("error in pthread_attr_setstacksize");
   //   exit(2);
   //}
   //pthread_t thid;
   //rc = pthread_create(&thid, &attr, threadmain, NULL);
   //if (rc == -1) {
   //   perror("error in pthread_create");
   //   exit(3);
   //}
   //void* stat;
   //rc = pthread_join(thid, &stat);

   return 0;
}
