#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <assert.h>
#include <string.h>

typedef struct {
  // Vale's control block should also have gen right here.
  uint32_t generationOrRc;
  // Just for now, for naive RC. After that, this is unused.
  uint32_t allocationActualSizeBytes;
  // Only used when free.
  void* nextFree;
} __Heap_Entry;

typedef struct {
  int allocationActualSizeBytes;
  __Heap_Entry** freeListHeadPtrPtr;
} GenHeap;

static __Heap_Entry* __gen_16B_heap_free_head = NULL;
static __Heap_Entry* __gen_24B_heap_free_head = NULL;
static __Heap_Entry* __gen_32B_heap_free_head = NULL;
static __Heap_Entry* __gen_40B_heap_free_head = NULL;
static __Heap_Entry* __gen_48B_heap_free_head = NULL;
static __Heap_Entry* __gen_56B_heap_free_head = NULL;
static __Heap_Entry* __gen_64B_heap_free_head = NULL;
static __Heap_Entry* __gen_80B_heap_free_head = NULL;
static __Heap_Entry* __gen_96B_heap_free_head = NULL;
static __Heap_Entry* __gen_128B_heap_free_head = NULL;

static GenHeap __gen_small_heaps_by_8b_multiple[] = {
    { 16, &__gen_16B_heap_free_head }, // 0
    { 16, &__gen_16B_heap_free_head }, // 8
    { 16, &__gen_16B_heap_free_head }, // 16
    { 24, &__gen_24B_heap_free_head }, // 24
    { 32, &__gen_32B_heap_free_head }, // 32
    { 40, &__gen_40B_heap_free_head }, // 40
    { 48, &__gen_48B_heap_free_head }, // 48
    { 56, &__gen_56B_heap_free_head }, // 56
    { 64, &__gen_64B_heap_free_head }, // 64
    { 80, &__gen_80B_heap_free_head }, // 72
    { 80, &__gen_80B_heap_free_head }, // 80
    { 96, &__gen_96B_heap_free_head }, // 88
    { 96, &__gen_96B_heap_free_head }, // 96
    { 128, &__gen_128B_heap_free_head }, // 104
    { 128, &__gen_128B_heap_free_head }, // 112
    { 128, &__gen_128B_heap_free_head }, // 120
    { 128, &__gen_128B_heap_free_head }, // 128
};

static __Heap_Entry* __gen_2_pow_8B_heap_free_head = NULL;
static GenHeap __gen_2_pow_8B_heap = { 2 << 8, &__gen_2_pow_8B_heap_free_head };
static __Heap_Entry* __gen_2_pow_9B_heap_free_head = NULL;
static GenHeap __gen_2_pow_9B_heap = { 2 << 9, &__gen_2_pow_9B_heap_free_head };
static __Heap_Entry* __gen_2_pow_10B_heap_free_head = NULL;
static GenHeap __gen_2_pow_10B_heap = { 2 << 10, &__gen_2_pow_10B_heap_free_head };
static __Heap_Entry* __gen_2_pow_11B_heap_free_head = NULL;
static GenHeap __gen_2_pow_11B_heap = { 2 << 11, &__gen_2_pow_11B_heap_free_head };
static __Heap_Entry* __gen_2_pow_12B_heap_free_head = NULL;
static GenHeap __gen_2_pow_12B_heap = { 2 << 12, &__gen_2_pow_12B_heap_free_head };
static __Heap_Entry* __gen_2_pow_13B_heap_free_head = NULL;
static GenHeap __gen_2_pow_13B_heap = { 2 << 13, &__gen_2_pow_13B_heap_free_head };
static __Heap_Entry* __gen_2_pow_14B_heap_free_head = NULL;
static GenHeap __gen_2_pow_14B_heap = { 2 << 14, &__gen_2_pow_14B_heap_free_head };
static __Heap_Entry* __gen_2_pow_15B_heap_free_head = NULL;
static GenHeap __gen_2_pow_15B_heap = { 2 << 15, &__gen_2_pow_15B_heap_free_head };
static __Heap_Entry* __gen_2_pow_16B_heap_free_head = NULL;
static GenHeap __gen_2_pow_16B_heap = { 2 << 16, &__gen_2_pow_16B_heap_free_head };
static __Heap_Entry* __gen_2_pow_17B_heap_free_head = NULL;
static GenHeap __gen_2_pow_17B_heap = { 2 << 17, &__gen_2_pow_17B_heap_free_head };
static __Heap_Entry* __gen_2_pow_18B_heap_free_head = NULL;
static GenHeap __gen_2_pow_18B_heap = { 2 << 18, &__gen_2_pow_18B_heap_free_head };
static __Heap_Entry* __gen_2_pow_19B_heap_free_head = NULL;
static GenHeap __gen_2_pow_19B_heap = { 2 << 19, &__gen_2_pow_19B_heap_free_head };
static __Heap_Entry* __gen_2_pow_20B_heap_free_head = NULL;
static GenHeap __gen_2_pow_20B_heap = { 2 << 20, &__gen_2_pow_20B_heap_free_head };
static __Heap_Entry* __gen_2_pow_21B_heap_free_head = NULL;
static GenHeap __gen_2_pow_21B_heap = { 2 << 21, &__gen_2_pow_21B_heap_free_head };
static __Heap_Entry* __gen_2_pow_22B_heap_free_head = NULL;
static GenHeap __gen_2_pow_22B_heap = { 2 << 22, &__gen_2_pow_22B_heap_free_head };
static __Heap_Entry* __gen_2_pow_23B_heap_free_head = NULL;
static GenHeap __gen_2_pow_23B_heap = { 2 << 23, &__gen_2_pow_23B_heap_free_head };
static __Heap_Entry* __gen_2_pow_24B_heap_free_head = NULL;
static GenHeap __gen_2_pow_24B_heap = { 2 << 24, &__gen_2_pow_24B_heap_free_head };


static inline void* mallocAndZeroGen(int allocationActualSizeBytes) {
  __Heap_Entry* newAllocation = malloc(allocationActualSizeBytes);
  newAllocation->generationOrRc = 0;
  newAllocation->allocationActualSizeBytes = allocationActualSizeBytes;
  return newAllocation;
}

static inline void incrementGenAndAddToFreeList(__Heap_Entry* entry, __Heap_Entry** head) {
  // This doesnt make much sense for RCs, but thats fine since itll be overwritten to zero when
  // its allocated.
  entry->generationOrRc++;
  entry->allocationActualSizeBytes = 0;
  entry->nextFree = *head;
  *head = entry;
}

static inline __Heap_Entry* popFromFreeList(__Heap_Entry** head) {
  __Heap_Entry* entry = *head;
  *head = entry->nextFree;
  return entry;
}

GenHeap* getGenHeapForDesiredSize(int desiredBytesNotMultipleOf8) {
  // Bump it up to the next multiple of 8
  int desiredBytes = (desiredBytesNotMultipleOf8 + 7) / 8 * 8;

  if (desiredBytes <= 128) {
    return &__gen_small_heaps_by_8b_multiple[desiredBytes / 8];
  } else if (desiredBytes <= (2 << 8)) {
    return &__gen_2_pow_8B_heap;
  } else if (desiredBytes <= (2 << 9)) {
    return &__gen_2_pow_9B_heap;
  } else if (desiredBytes <= (2 << 10)) {
    return &__gen_2_pow_10B_heap;
  } else if (desiredBytes <= (2 << 11)) {
    return &__gen_2_pow_11B_heap;
  } else if (desiredBytes <= (2 << 12)) {
    return &__gen_2_pow_12B_heap;
  } else if (desiredBytes <= (2 << 13)) {
    return &__gen_2_pow_13B_heap;
  } else if (desiredBytes <= (2 << 14)) {
    return &__gen_2_pow_14B_heap;
  } else if (desiredBytes <= (2 << 15)) {
    return &__gen_2_pow_15B_heap;
  } else if (desiredBytes <= (2 << 16)) {
    return &__gen_2_pow_16B_heap;
  } else if (desiredBytes <= (2 << 17)) {
    return &__gen_2_pow_17B_heap;
  } else if (desiredBytes <= (2 << 18)) {
    return &__gen_2_pow_18B_heap;
  } else if (desiredBytes <= (2 << 19)) {
    return &__gen_2_pow_19B_heap;
  } else if (desiredBytes <= (2 << 20)) {
    return &__gen_2_pow_20B_heap;
  } else if (desiredBytes <= (2 << 21)) {
    return &__gen_2_pow_21B_heap;
  } else if (desiredBytes <= (2 << 22)) {
    return &__gen_2_pow_22B_heap;
  } else if (desiredBytes <= (2 << 23)) {
    return &__gen_2_pow_23B_heap;
  } else if (desiredBytes <= (2 << 24)) {
    return &__gen_2_pow_24B_heap;
  } else {
    fprintf(stderr, "Tried to allocate %d bytes!\n", desiredBytesNotMultipleOf8);
    assert(0);
  }
}

// we assume bytes is a multiple of 8
void* __genMalloc(int desiredBytes) {
  GenHeap* genHeapForDesiredSize = getGenHeapForDesiredSize(desiredBytes);
  int allocationActualSizeBytes = genHeapForDesiredSize->allocationActualSizeBytes;
  __Heap_Entry** freeListHeadPtrPtr = genHeapForDesiredSize->freeListHeadPtrPtr;

  __Heap_Entry* result = NULL;
  if (*freeListHeadPtrPtr) {
    result = popFromFreeList(freeListHeadPtrPtr);
  } else {
    result = mallocAndZeroGen(allocationActualSizeBytes);
  }
  // Dont change the generation
  result->allocationActualSizeBytes = allocationActualSizeBytes;
  return result;
}

// we assume bytes is a multiple of 8
void __genFree(void* allocationVoidPtr) {
  __Heap_Entry* allocation = (__Heap_Entry*)allocationVoidPtr;

  __Heap_Entry** freeListHeadPtrPtr = getGenHeapForDesiredSize(allocation->allocationActualSizeBytes)->freeListHeadPtrPtr;
  incrementGenAndAddToFreeList((__Heap_Entry*)allocation, freeListHeadPtrPtr);
}
