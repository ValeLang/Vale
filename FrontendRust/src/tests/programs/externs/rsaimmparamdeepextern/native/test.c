#include <stdint.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include "vtest/Spaceship.h"
#include "vtest/ImmSpaceshipArray.h"
#include "vtest/cSumFuel_vasp.h"

// We use incrementIntFile to get some side effects to test replayability, see AASETR.
int64_t incrementIntFile(const char* filename);

int64_t nextMultipleOf16(int64_t x) {
  return ((x - 1) | 15) + 1;
}

extern ValeInt vtest_cSumFuel_vasp(vtest_ImmSpaceshipArray* arr, ValeInt arrSize) {
  int runNumber = incrementIntFile("myfile.bin");

  printf("assert(arr->length == 5);\n  arr->length = %lld\n", arr->length);
  assert(arr->length == 5);
  int64_t arrayAddr = (int64_t)(void*)arr;
  printf("arrayAddr = %lld\n", arrayAddr);
  int64_t arrayShallowSize = sizeof(vtest_ImmSpaceshipArray) + sizeof(vtest_Spaceship*) * arr->length;
  // AP = And Padding; to get the next multiple of 16 from the end of the array's header struct.
  int64_t arrayShallowAPEndAddr = nextMultipleOf16(arrayAddr + arrayShallowSize);

  int64_t firstElementAddr = arrayShallowAPEndAddr;
  int64_t firstElementAPEndAddr = nextMultipleOf16(firstElementAddr + sizeof(vtest_Spaceship));

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

  printf("assert((int64_t)(void*)arr->elements[0] == firstElementAddr);\n  arr->elements[0] = %lld\n  firstElementAddr = %lld\n", arr->elements[0], firstElementAddr);
  printf("assert((int64_t)(void*)arr->elements[4] == lastElementAddr);\n  arr->elements[4] = %lld\n  lastElementAddr = %lld\n", arr->elements[4], lastElementAddr);
  assert((int64_t)(void*)arr->elements[0] == firstElementAddr);
  assert((int64_t)(void*)arr->elements[4] == lastElementAddr);

  assert(arrSize == arrayAPEndAddr - arrayAddr);

  // Now sum up all elements' wings, like a normal piece of code.
  ValeInt total = 0;
  for (int i = 0; i < arr->length; i++) {
    total += arr->elements[i]->fuel;
  }
  free(arr);
  return total * runNumber;
}
