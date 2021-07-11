#include <stdint.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include "tmod/Spaceship.h"
#include "tmod/ImmSpaceshipArray.h"
#include "tmod/cSumFuel.h"

int64_t nextMultipleOf16(int64_t x) {
  return ((x - 1) | 15) + 1;
}
int64_t floorMultipleOf16(int64_t x) {
  return x & ~0xF;
}

extern ValeInt tmod_cSumFuel(tmod_ImmSpaceshipArray* arr) {

  int64_t arrayHeaderAddr = (int64_t)(void*)arr;
  int64_t arrayShallowSize = sizeof(tmod_ImmSpaceshipArray) + sizeof(tmod_Spaceship*) * arr->length;
  // AP = And Padding; to get the next multiple of 16 from the end of the array's header struct.
  int64_t arrayHeaderAPEndAddr = nextMultipleOf16(arrayHeaderAddr + arrayShallowSize);

  // The root object (the header struct here) always has a 16B "metadata block" before it, which
  // contains the start address and the size of the message.
  int64_t rootMetadataAPEndAddr = arrayHeaderAddr;
  int64_t rootMetadataAddr = floorMultipleOf16(rootMetadataAPEndAddr - 16);

  int64_t lastElementAPEndAddr = rootMetadataAddr;
  int64_t lastElementAddr = floorMultipleOf16(lastElementAPEndAddr - sizeof(tmod_Spaceship));

  int64_t elementStride = lastElementAPEndAddr - lastElementAddr;

  int64_t firstElementAPEndAddr = lastElementAPEndAddr - elementStride * 4;
  int64_t firstElementAddr = lastElementAddr - elementStride * 4;

  // Start metadata is at the start of the message, but at a multiple of 16.
  int64_t startMetadataAPEndAddr = firstElementAddr;
  int64_t startMetadataAddr = floorMultipleOf16(startMetadataAPEndAddr - 24);

//  printf("arr %lld %lld elements %lld %lld %lld %lld\n", arrayHeaderAddr, arrayHeaderAPEndAddr, firstElementAddr, firstElementAPEndAddr, lastElementAddr, lastElementAPEndAddr);

  {
    // The things in this block more just test the test itself, but thats fine.

    // Make sure that they're all at addresses that are multiples of 16
    assert(arrayHeaderAddr == (arrayHeaderAddr & ~0xF));
    assert(arrayHeaderAPEndAddr == (arrayHeaderAPEndAddr & ~0xF));
    assert(firstElementAddr == (firstElementAddr & ~0xF));
    assert(firstElementAPEndAddr == (firstElementAPEndAddr & ~0xF));
    assert(lastElementAddr == (lastElementAddr & ~0xF));
    assert(lastElementAPEndAddr == (lastElementAPEndAddr & ~0xF));
  }

  assert((int64_t)(void*)arr->elements[0] == firstElementAddr);
  assert((int64_t)(void*)arr->elements[4] == lastElementAddr);

  int64_t startAddrFromRootMetadata = ((uint64_t*)(void*)rootMetadataAddr)[0];
  assert(startAddrFromRootMetadata == startMetadataAddr);
  int64_t sizeFromRootMetadata = ((uint64_t*)(void*)rootMetadataAddr)[1];
  assert(startMetadataAddr + sizeFromRootMetadata == arrayHeaderAPEndAddr);

  // Make sure the metadata block has the correct size in it, which should be the size of all of them
  // and the metadata block and the padding after each.
  uint64_t sizeFromStartMetadata = ((uint64_t*)(void*)startMetadataAddr)[0];
  assert(sizeFromStartMetadata == sizeFromRootMetadata);
  uint64_t startAddrFromStartMetadata = ((uint64_t*)(void*)startMetadataAddr)[1];
  assert(startAddrFromStartMetadata == startAddrFromRootMetadata);
  uint64_t rootAddrFromStartMetadata = ((uint64_t*)(void*)startMetadataAddr)[2];
  assert(rootAddrFromStartMetadata == arrayHeaderAddr);

  // Now sum up all elements' wings, like a normal piece of code.
  ValeInt total = 0;
  for (int i = 0; i < arr->length; i++) {
    total += arr->elements[i]->fuel;
  }
  ValeReleaseMessage(arr);
  return total;
}
