
# Collections

## ArrayList

## LinkedList

## SegmentedList

A linked array list, where every slab has a certain number of elements.

Good for holding an unknown number of things with stable addresses, with good iterating speed.

## PageList

A linked array list, where every slab has a specified number of bytes.

Bad for random access (O(N)) so perhaps we should definitely have an accompanying array that points to each of the chunks.

## ExponentialSegmentedList

A linked array list, where the first slab has 64 bytes, next slab has 128, and so on.

If theyre immutables or unis, then maybe this can lower to just be a vector.

Bad for random access (O(logN)) so perhaps we should have an accompanying array that points to each of the chunks instead of working forwards or backwards.

## PinningHashMap

I'd like some sort of hash map that doesn't move its values. Perhaps we'd have a separate array

## Sparse Collections

Sparse means that they're conceptually collections of optionals. Particularly good for implementing hash maps, swiss tables, etc.

We could have these automatically created behind the scenes for collections of optionals. Yeah, that sounds reasonable. Some optimizations:

 * Collections of optional yonders can be lowered to nullables (we should do that in general for optionals anyway)
 * Collections of optional mutables can use a bit in the generation word.
 * We can use any padding that exist in any contained imm.

Otherwise, we can have a parallel array of bits.

## PrefetchingIterator

This is an iterator that will prefetch a certain number (L) of elements ahead.

We should have some optimization that finds loops that use these, and reduce them to:

// For SegmentedList / PageList

for (int p = 0; p < numPages; p++) {
  thisPage = ...
  // Non-prefetching loop:
  for (int i = 0; i < numPerPage - L; i++) {
    // run body on thisPage[i]
  }
  nextPage = ...
  // Prefetching loop:
  for (int i = 0; i < L; i++) {
    // prefetch nextPage[i]
    // run body on thisPage[numPerPage - L + i]
  }
}

and something similar for ExponentialSegmentedList.

Perhaps the inner loops can be unrolled a bit too, LLVM might take care of that.

If we dont want to do the optimization yet, we could have a `foreach` method that just looks like that. Heck, we might want one of those anyway because itll be a good step towards HGM's passthrough tethers inside iterator structs.

## Optional List

Perhaps we can have variants of all the above that do this.

The idea is that we use a bit in the generation to represent "is this slot filled".

Then we can build a free-list into it, or something. Itd be a good basis for a generational_arena kind of thing, and also a good foundation for swiss tables.

If there's no generations, such as for immutables, we can have a side array of bits.


# File System

Possible way forward:

1. make an export sealed interface FileError (vale's equivalent of enum)

2. make an export struct and impl it (vale's equivalent of an enum case):

```
export struct NotFound { }
impl FileError for NotFound;
```

3. of course, we want to return a Result not an error... so lets export Result:

```
export Result<i64, FileError> as FileResult;
export Result<i64, FileError> as StringResult;
```

If that works, then we should be able to do some simple file operations, similar to this Rust snippet:

```
let mut file = File::open(&path).expect("Error opening File");
let mut contents = String::new();
file.read_to_string(&mut contents).expect("Unable to read to string");
```

in Vale it might be:

```
file = FileOpen("myfile.txt").Expect("Error opening File");
contents = file.ReadToString().Expect("Unable to read to string");

string_to_write = “asd”;
file.WriteString(string_to_write).Expect("Unable to write to file");
```

we'd need these:

```
extern func FileOpen(path str) FileResult;

extern func ReadToString(file i64) StringResult;

extern func WriteString(file i64, contents str) StringResult;
```

For now we can communicate with Rust in terms of that i64. After that works, as a later step we can add a real vale File struct to wrap it (that part would be just a wrapper around the above functions; we'll still use the above functions under the hood)

This is better than what we roughly had in path.vale because:
It communicates Results across the FFI boundary, which makes things a lot better. This way, we can convey errors pretty seamlessly from Rust into Vale. Right now we just kinda toss an int over the boundary and ignore it lol
It uses the Rust standard library under the hood, which is more reliable than the C code we were writing in the stdlib

```
cargo init FSVale --lib
```

add this to cargo toml:

```
[lib]
crate-type = ["staticlib"]

and then put this into lib.rs:

use std::fs::{self, File};

use std::ffi::CStr;
use std::os::raw::c_char;

#[no_mangle]
pub extern "C" fn createDir(path: *const c_char) -> usize {
  let slice = unsafe { CStr::from_ptr(path) };
  let create_dir_result = fs::create_dir(slice.to_str().unwrap());
  match create_dir_result {
    Ok(result) => 0,
    Err(error) => 1,
  }
}
```

That will generate a library in the target directory.

This C can call into it:

```
extern void createDir(char *p);
int main(void) {
  createDir("Created from Rust stdlib!");
  return 0;
}
```

Use `-lFSVale -L target\debug`

