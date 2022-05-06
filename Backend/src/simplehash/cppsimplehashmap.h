#ifndef SIMPLEHASH_CPPSIMPLEHASHMAP_H
#define SIMPLEHASH_CPPSIMPLEHASHMAP_H

#include <cstdint>
#include <cstdio>
#include <cassert>
#include <cstring>

extern const int64_t hashTableSizeList[];

template<typename K, typename V>
struct CppSimpleHashMapNode {
  K key;
  V value;

  CppSimpleHashMapNode() = default;
  CppSimpleHashMapNode(K key, V value) : key(key), value(value) {}
};

template<typename K, typename V, typename H, typename E>
class CppSimpleHashMap {
public:
  CppSimpleHashMap(H hasher, E equator) :
      capacity(0),
      size(0),
      presences(nullptr),
      entries(nullptr),
      hasher(hasher),
      equator(equator) { }

  int64_t contains(K key) {
    int64_t index = findIndexOf(key);
    return index != -1;
  }

  void add(K key, V value) {
    if (size >= capacity) {
      expand();
    }
    if (contains(key)) {
      fprintf(stderr, "Tried to add element to hashset, but was already present!\n");
      assert(0);
    }
    innerAdd(key, value);
    size++;
  }

  void remove(K key) {
    int64_t originalIndex = innerRemove(key);
    size--;

    for (int64_t i = 1; i < capacity; i++) {
      int64_t neighborIndex = (originalIndex + i) % capacity;
      K neighbor = entries[neighborIndex].key;
      if (presences[neighborIndex]) {
        presences[neighborIndex] = false;
        innerAdd(neighbor);
      } else {
        break;
      }
    }
  }

  // Returns -1 if not found.
  int64_t findIndexOf(K key) {
    if (!entries) {
      return -1;
    }
    int64_t startIndex = hasher(key) % capacity;
    for (int64_t i = 0; i < capacity; i++) {
      int64_t indexInTable = (startIndex + i) % capacity;
      if (!presences[indexInTable]) {
        return -1;
      }
      if (equator(entries[indexInTable].key, key)) {
        return indexInTable;
      }
    }
    exit(1); // We shouldnt get here, it would mean the table is full.
  }

  // Returns -1 if already present.
  int64_t findOpenSpaceIndexFor(K key) {
    int64_t startIndex = hasher(key) % capacity;
    for (int64_t i = 0; i < capacity; i++) {
      int64_t indexInTable = (startIndex + i) % capacity;
      if (equator(entries[indexInTable].key, key)) {
        fprintf(stderr, "Found element while looking for open hashset space, it's already present!\n");
        assert(0);
      }
      if (!presences[indexInTable]) {
        return indexInTable;
      }
    }
    fprintf(stderr, "%s:%d, unreachable!\n", __FILE__, __LINE__);
    exit(1); // We shouldnt get here, it would mean the table is full.
  }

  // Doesnt do any fixing of neighbors, or decrementing of size.
  int64_t innerRemove(K key) {
    int64_t originalIndex = findIndexOf(key);
    assert(originalIndex != -1);
    assert(entries[originalIndex].key == key);
    presences[originalIndex] = false;
    return originalIndex;
  }

  // Doesnt expand or increment size.
  void innerAdd(K key, V value) {
    assert(size < capacity);
    int64_t index = findOpenSpaceIndexFor(key);
    assert(index != -1);
    assert(!presences[index]);
    entries[index] = CppSimpleHashMapNode<K, V>(key, value);
    int64_t doublecheckIndex = findIndexOf(key);
    assert(doublecheckIndex == index);
  }

  void expand() {
    int64_t oldNumEntries = capacity;
    CppSimpleHashMapNode<K, V>* oldEntries = entries;
    bool* oldEntryIndexToIsFilled = presences;

    //int64_t oldCapacity = capacity;

    int64_t indexInSizeList = 0;
    while (hashTableSizeList[indexInSizeList] != capacity) {
      indexInSizeList++;
    }
    // Now, indexInSizeList is accurate for our old capacity.
    int64_t newIndexInSizeList = indexInSizeList + 1;
    auto newCapacity = hashTableSizeList[newIndexInSizeList];

    capacity = hashTableSizeList[newCapacity];
    entries = new CppSimpleHashMapNode<K, V>[capacity];
    memset(entries, 0, sizeof(CppSimpleHashMapNode<K, V>) * capacity);
    presences = new bool[capacity];
    memset(presences, 0, sizeof(bool) * capacity);

    if (oldEntries) {
      for (int64_t i = 0; i < oldNumEntries; i++) {
        if (oldEntryIndexToIsFilled[i]) {
          innerAdd(oldEntries[i].key, oldEntries[i].value);
        }
      }
      delete[] oldEntries;
      delete[] oldEntryIndexToIsFilled;
    }
  }

  // All of these are intentionally public.
  // One of the main uses of CppSimpleHashMap is to move a hash map from the compiler to the
  // resulting program, so the program's main() doesn't have to repopulate the entire hash map.

  int64_t capacity;
  int64_t size;
  // optimize: make this an array of bits, not an array of bytes.
  bool* presences;
  CppSimpleHashMapNode<K, V>* entries;
  H hasher;
  E equator;
};

#endif //SIMPLEHASH_CPPSIMPLEHASHMAP_H
