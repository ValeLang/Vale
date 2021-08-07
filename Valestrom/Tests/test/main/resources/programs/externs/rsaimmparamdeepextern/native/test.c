#include <stdint.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include "tmod/Spaceship.h"
#include "tmod/ImmSpaceshipArray.h"
#include "tmod/cSumFuel_vasp.h"

int64_t nextMultipleOf16(int64_t x) {
  return ((x - 1) | 15) + 1;
}

extern ValeInt tmod_cSumFuel_vasp(tmod_ImmSpaceshipArray* arr, ValeInt arrSize) {
  assert(arr->length == 5);
  int64_t arrayAddr = (int64_t)(void*)arr;
  int64_t arrayShallowSize = sizeof(tmod_ImmSpaceshipArray) + sizeof(tmod_Spaceship*) * arr->length;
  // AP = And Padding; to get the next multiple of 16 from the end of the array's header struct.
  int64_t arrayShallowAPEndAddr = nextMultipleOf16(arrayAddr + arrayShallowSize);

  int64_t firstElementAddr = arrayShallowAPEndAddr;
  int64_t firstElementAPEndAddr = nextMultipleOf16(firstElementAddr + sizeof(tmod_Spaceship));

  int64_t stride = firstElementAPEndAddr - firstElementAddr;

  int64_t lastElementAddr = arrayShallowAPEndAddr + (arr->length - 1) * stride;
  int64_t lastElementAPEndAddr = arrayShallowAPEndAddr + arr->length * stride;

  int64_t arrayAPEndAddr = lastElementAPEndAddr;

//  printf("arr %lld %lld elements %lld %lld %lld %lld\n", arrayAddr, arrayShallowAPEndAddr, firstElementAddr, firstElementAPEndAddr, lastElementAddr, lastElementAPEndAddr);

  {
    // The things in this block more just test the test itself, but thats fine.

    // Make sure that the array is at a multiple of 16
    assert(arrayAddr == (arrayAddr & ~0xF));
    // Make sure the elements are at multiples of 8
    assert(arrayShallowAPEndAddr == (firstElementAddr & ~0x7));
    assert(firstElementAddr == (firstElementAddr & ~0x7));
    assert(firstElementAPEndAddr == (firstElementAPEndAddr & ~0x7));
    assert(lastElementAddr == (lastElementAddr & ~0x7));
    assert(lastElementAPEndAddr == (lastElementAPEndAddr & ~0x7));
  }

  assert((int64_t)(void*)arr->elements[0] == firstElementAddr);
  assert((int64_t)(void*)arr->elements[4] == lastElementAddr);

  assert(arrSize == arrayAPEndAddr - arrayAddr);

  // Now sum up all elements' wings, like a normal piece of code.
  ValeInt total = 0;
  for (int i = 0; i < arr->length; i++) {
    total += arr->elements[i]->fuel;
  }
  free(arr);
  return total;
}
