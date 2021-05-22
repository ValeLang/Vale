
fn sumBytes(arr Array<imm, int>) int extern;

typedef struct ArrayImmInt {
  uint64_t length;
  int64_t* elements;
} ArrayImmInt;

int64_t sumBytes(ArrayImmInt* arr) {
  int64_t total = 0;
  for (int i = 0; i < arr->length; i++) {
    total += arr->elements[i];
  }
  return total;
}
