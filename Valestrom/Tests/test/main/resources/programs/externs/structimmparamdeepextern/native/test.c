#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

#include "vtest/Spigglewigget.h"
#include "vtest/Bogglewoggle.h"
#include "vtest/Flamscrankle.h"
#include "vtest/extFunc_vasp.h"

size_t nextMultipleOf16(size_t x) {
  return ((x - 1) | 15) + 1;
}
size_t floorMultipleOf16(size_t x) {
  return x & ~0xF;
}

ValeInt vtest_extFunc_vasp(vtest_Flamscrankle* flam, ValeInt flamMessageSize) {
  // Make sure the root pointer is at a multiple of 16.
  // If this fails, that means we have a bug, or malloc is breaking our assumptions
  // about alignment.
  assert(((size_t)(void*)flam & 0xF) == 0);

  // Most basic test, try to dereference the thing and make sure it contains something we expect.
  assert(flam->x == 7);

  size_t flamAddr = (size_t)(void*)flam;
  // AP = And Padding; to get the next multiple of 16 from the end of the Flamscrankle.
  size_t flamAPEndAddr = nextMultipleOf16(flamAddr + sizeof(vtest_Flamscrankle));

  // Bogglewoggle is after the Flamscrankle, but at a multiple of 16.
  size_t bogAddr = flamAPEndAddr;
  size_t bogAPEndAddr = nextMultipleOf16(bogAddr + sizeof(vtest_Bogglewoggle));

  // Spigglewigget is after the Bogglewoggle, but at a multiple of 16.
  size_t spigAddr = bogAPEndAddr;
  size_t spigAPEndAddr = nextMultipleOf16(spigAddr + sizeof(vtest_Spigglewigget));

  {
    // The things in this block more just test the test itself, but thats fine.

    // Make sure that they're all at addresses that are multiples of 16
    assert(flamAddr == (flamAddr & ~0xF));
    assert(flamAPEndAddr == (flamAPEndAddr & ~0xF));
    assert(bogAddr == (bogAddr & ~0xF));
    assert(bogAPEndAddr == (bogAPEndAddr & ~0xF));
    assert(spigAddr == (spigAddr & ~0xF));
    assert(spigAPEndAddr == (spigAPEndAddr & ~0xF));
  }

  assert((size_t)(void*)flam->b == bogAddr);
  assert((size_t)(void*)flam->b->s == spigAddr);

  ValeInt result = flam->x + flam->b->s->x + flam->b->s->y + flam->b->s->z + flam->b->x + flam->y;
  assert(result == 42);

  // Tests the _vasp suffix gave us the right message size, see SASP.
  assert(flamMessageSize == (spigAPEndAddr - flamAddr));

  free(flam);
  return result;
}
