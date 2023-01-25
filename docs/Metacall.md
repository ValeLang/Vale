
Metacall is a mechanism for calling into (pretty much) any language from any language.

If we could build this into the Vale compiler, and easily call into any other language, it would be a major boon and singlehandedly solve our lack of an ecosystem (and standard library).


# Basic Set Up (outside Vale)

(Moved further below)

# Next steps

## Step 0 (optional)

RocketValeMetacall is a very stripped down, basic example. It would be nice to restore the rest of it from the original RocketVale, and have a working game server using it.

## Step 1: User-space Metacall Bindings

We can start with some standard Vale wrappers for Metacall's API.

To start, we'd have:

 * metacall_initialize
 * metacall_load_from_package
 * metacall_value_create_int
 * metacallv_s
 * metacall_value_to_int
 * metacall_value_destroy

And we'd make a simple RocketValeMetacall program using these Vale functions.


## Step 2: Package Metacall with the Compiler

### Security

There's a very high bar for packaging a third-party library in with a compiler. If we wanted to distribute the metacall binary as part of valec, we would need to do some auditing of Metacall and its dependencies. We don't have the manpower and resources for that yet.


Instead, we'll structure this as a third-party compiler plugin, but make it _ridiculously_ easy for the user to install. If the user tries to use any cross-language features, we'll prompt them to make sure they know they're installing a third-party compiler plugin and that they bear the risks, show them the URL of the installer script we're about to run, and then run it.

Extra benefit: this keeps the Vale installer small, since Metacall is later installed via the network.


The third-party compiler plugin would be maintained by us and live at https://github.com/Verdagon/ValeMetacall, and would have a similar disclaimer about its own dependencies. It would basically just be an installer script that puts the Metacall binaries and libraries in the right place that valec would know to look for them.


Discarded alternative: Running metacall inside a docker container _for security reasons_. It would make little sense to not trust the Metacall binary and then trust the code layers that it generates. Running inside docker might make sense, just not as a security measure.

### Versioning

We might also want to run Metacall inside a docker container, just so we can more precisely control it and all the compiler versions it requires. Presumably we'd copy into the container all the user code that they wish to metacall into.


One downside is that it will be slightly harder to match Metacall's rustc version with the user's own rustc version. One workaround is to copy the user's Rust library project into the docker so that Metacalls' rustc can work on it.


## Step 3: Valec Depending on Other Languages

### Usage

Vale would be able to depend on libraries from other projects. The user would say something like:

`valec build rocketvale=. --rs_dep=~/Rocket`

or if they want to pull it off cargo, then perhaps something like:

`valec build rocketvale=. --cargo_dep=rocket@0.5.0-rc.2`

The actual CLI will likely have to be more flexible and thought out, but those are the basic idea; we want to be able to depend on arbitrary libraries from arbitrary languages.

And of course, the user won't normally see these command line invocations, they'll likely be managed by a Vale package manager (out of scope for now).

There will be some cases where this won't work. Some libraries depend on language-specific mechanisms like C++ templates, Rust macros, Java annotations, etc. The user would likely need to write specific wrappers for those. Perhaps there's a way to make that easier?

### Design

The Coordinator (Vale's small command line interface program) would be mainly responsible for handling all of this. It would first build those dependencies, and somehow ask Metacall for the signatures of the libraries we want to call.


Coordinator would then generate some temporary `.vale` files to feed into the normal compilation process. For example, if there was a Rust file containing this:

```
pub fn rocketvale_test_call(a: i64) -> i64 {
    return a * 2;
}
```

then Metacall might report to us that there's a function `rocketvale_rust_test_call` that takes an `i64` and returns an `i64`. Coordinator would generate a `rocketvale/lib.vale` file containing this:

```
func test_call(a i64) i64 {
  metacall_load_from_package("rs", "./rocketvale.rlib", NULL);
  arg = metacall_value_create_int(3);
  ret = metacallv_s("rocketvale_rust_test_call", #[#](arg));
  result = metacall_value_to_int(ret);
  metacall_value_destroy(arg);
  metacall_value_destroy(ret);
  return ret;
}
```

It would also insert an automatic call to `metacall_initialize` before `main`.

Unknowns:

 * What the final CLI would look like for this.
 * How we might ask Metacall for the signatures. Right now it has an `inspect` option, we can likely just parse its output.
 * How we'll compile these dependencies and feed Metacall the proper files. In the case of Rust, we might need to have a built-in `cargo` so we can download and compile a library. Perhaps we can temporarily only take in an existing rlib via command line.


Long term hopes:

 * Less run-time cost: Later on, we'd hopefully find a way to shortcut past some of the run-time costs here. Metacall currently seems very much like an interpreter, which is definitely fine for the vast majority of use cases. Perhaps someday there would a way to make it more into a _compiler_ itself, where it could generate a static library with a standard C interface which we could call. One can dream!

## Step 4: Other Languages Depending on Valec

### Usage

We'd build Vale as a library that could be called from any specified language. The user would say something like `valec build rocketvale=. --type lib --output jar`.

### Design

Under the hood, we'd do everything normally to generate a static library from the Vale code.

We'd then presumably hand our static library over to Metacall.

We'd write a custom loader for Metacall which can read `.vast`, or just have Metacall's C loader read the `.h` files that the backend already generates.

Metacall would then package up our static library into something callable by another language, such as a `.jar`.


# Basic Set Up (outside Vale)

Install deps:

`sudo apt install git cmake build-essential`

Clone metacall:

```
git clone https://github.com/metacall/core.git
cd core
```

Install metacall deps:

```
tools/metacall-environment.sh rust c
source ~/.bashrc
```

Make a build folder:

```
mkdir build
cd build
```

Build metacall:

```
cmake -DOPTION_BUILD_LOADERS_C=ON -DOPTION_BUILD_LOADERS_RS=ON ..
make -j12
```

Install metacall:

`sudo HOME="$HOME" make install`


Clone RocketValeMetacall:

```
cd
https://github.com/Verdagon/RocketValeMetacall.git
cd RocketValeMetacall
```

Build its Rust side:

```
cd ~/RocketValeMetacall/src/native/rust
cargo build
```

Build its C side:

```
cd ~/RocketValeMetacall/src/native
gcc -I/usr/local/include metacaller.c -L/usr/local/lib -lmetacall
```

Run it:

```
LD_LIBRARY_PATH=/usr/local/lib ./a.out ./rust/target/debug/librocketvale.rlib
```

Should get output:

```
result: 6
```

Done!


# Notes

If you want to run the CLI, `LOADER_SCRIPT_PATH=pwd metacallcli`
