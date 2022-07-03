**the goal**

(moved into the draft)

**the challenges**

\- cross compiling Vale to the right flavor of C to send to the GPU
(dont think we can send assembly or LLVM IR to the GPU)

\- knowing when cuda/metal is available, and good enough to run our code

\- linking with apple-specific libraries for Metal (Zig might have found
a way around this)

\- interaction with Vale\'s perfect replayability feature

\- the necessity of unsafe, so we have precise control. only certain
languages are equipped for this

(todo verdagon: incorporate the above into the below draft)

**open questions (nullspace):**

1.  how futhark does things under the hood. does it use something like
    > SPIR?

    a.  (i sent a message to him on the PL discord, will let you know if
        > he replies. -V)

    b.  will use this in the \"**whirlwind tour of existing GPU
        > parallelism strategies**\" section below

2.  what kind of help LLVM can give us

    a.  will use this in the \"**how we might get there**\" section
        > below

3.  what interesting features are coming out from metal/CUDA which might
    > help in this endeavor

    a.  will use this in the \"**how we might get there**\" section
        > below

4.  ~~can we lazily load memory on-demand to the GPU, like how L1/L2/L3
    > cpu caches work?~~

    a.  (i took this question, sorry =D)

(todo verdagon: incorporate the above into the below draft once we have
answers.

or, nullspace, if you want to merge the answers into the draft, feel
free! i\'ll later make sure that it flows well with the rest)

**open questions (verdagon):**

-   what would we need, to expose memory to the GPU?

    -   mmap, cuMemMap, special language constructs to specify what
        > memory should be available to the GPU

    -   expose entire regions?

    -   expose a certain function\'s indirectly owned memory?

-   supporting recursion?

    -   zig prevents recursion, could be a really good fit

-   supporting abstraction?

-   memory safety concerns: conflicting writes on memory that\'s mutably
    > accessible to multiple kernels/threads.

    -   only really a concern for languages that care, like vale and
        > rust

-   languages that would be good at this?

    -   low level languages, likely. zig, odin, C. rust, c++, and vale
        > have a hidden challenge of the occasional implicit virtual
        > dispatch

    -   ask andrewrk, ginger bill, athas

    -   ask r/programminglanguages

-   from
    > [[https://en.wikipedia.org/wiki/Thread_block\_(CUDA_programming)]{.underline}](https://en.wikipedia.org/wiki/Thread_block_(CUDA_programming)):

    -   \" a process called 'context switching' takes place which
        > transfers control to another warp.\" - could help?

-   whats \"heterogenous compute\"?

-   language: braid

-   language: pacxx

-   language: skepu

-   language: julia

    -   https://cancandan.github.io/julia/graphics/cuda/2022/05/07/triangles.html

-   can we lazily load memory on-demand to the GPU, like how L1/L2/L3
    > cpu caches work?

    -   (i suspect the answer\'s no, need to use e.g. cuMemMap to copy
        > it over)

(todo verdagon: incorporate the above into the below draft once we have
answers)

# Draft

(i\'ll progressively evolve this from a skeleton into an article, feel
free to throw anything in here and i\'ll make sure it ends up in the
right place)

## Prologue

Some of you might remember our [[Seamless
Concurrency]{.underline}](https://verdagon.dev/blog/seamless-fearless-structured-concurrency)
article, about how a region-aware language can safely add
multi-threading with just one simple keyword.

That was just **scratching the surface** of what\'s possible!

There\'s so much incredible potential in this realm:

\* Threading models that have the benefits of both goroutines and
async/await. \[# I\'m referring not only to \[Zig\'s colorless
async/await\](https://kristoff.it/blog/zig-colorblind-async-await/), but
also what lies beyond it!\]

\* Concurrency models that are so reliable that it\'s literally
impossible to crash them them, which enable \[nine
nines\](https://stackoverflow.com/questions/8426897/erlangs-99-9999999-nine-nines-reliability):
uptime of 99.9999999%. \[# Lookin\' at you, Pony actors!\]

\* Ways to go over 100x faster than the most well-optimized CPU-based
program.

Today, we\'re going to talk about that last one!

It\'s sometimes tempting to think that we\'ve reached the end of
programming languages, and that today\'s languages are perfect. I\'m
here to tell you that we ain\'t seen nothin\' yet!

Welcome to the **Next Decade in Languages** series, where we talk about
surprising language aspects that have a lot potential, and could soon
change the way we program. I hope you enjoy it, and if you\'d like us to
continue, please consider \[sponsoring us\]()!

## GPUs\' Hidden Powers

If you have a late 2019 Macbook Pro, you have 6 x 2.6ghz cores, totaling
to 15.6 ghz.

However, there are 1,536 other cores hidden on your machine, each
running at around 1.3 ghz. These are in your GPU, and they\'re usually
used to play your movies and make your games look good.

That totals to 1996.8 ghz! That\'s 128x the power of your CPU. **Imagine
if you could harness that!**

A couple decades ago, folks at Nvidia thought the same thing, and
created
\[CUDA\]([[https://en.wikipedia.org/wiki/CUDA]{.underline}](https://en.wikipedia.org/wiki/CUDA)),
giving us unprecedented control over that raw power.

With CUDA, one can write C++ code that runs on the GPU. I\'m not talking
about shaders, which only do very specialized things, I mean general
purpose, *real* C++ code. One can even use it to write a \[ray
tracing\](https://developer.nvidia.com/discover/ray-tracing) program
that produces this image: \[# I made this in 2012!\]

![](media/image2.png){width="6.5in" height="1.5138888888888888in"}

Since then, while the rest of us weren\'t looking, they\'ve made
incredible strides. While we were struggling to get 10x speedups on our
mere 6 CPU cores, people have been using GPUs to get 100-1000x speedups.

However, it was always very tricky to get it working. One had to have
exactly the right hardware, exactly the right drivers, it only supported
C and C++, and it only seemed to work on odd days of the month.

Alas, kind of a mess.

## A Mess, Perhaps

But maybe there\'s some order emerging out of the chaos.

The Khronos Group created something called SPIR, to serve as a common
protocol between all of the different platforms:

![](media/image1.jpg){width="6.5in" height="3.236111111111111in"}

Common protocols often spark firestorms of innovation.

My favorite example of a common protocol is \[LLVM\](https://llvm.org/),
an easy assembly-like language that comes with many \"backends\" that
can translate to any architecture under the sun, such as x86 or ARM.
Languages only need to compile to the LLVM language, \[# More
specifically, they call the LLVM API to construct an Abstract Syntax
Tree in memory, and then LLVM will translate that.\] and then let the
LLVM backends do the rest. Without LLVM, we might not have languages
like Cone, Zig, Nim, Rust, Lobster, Odin, or our favorite,
\[Vale\]([[https://vale.dev/]{.underline}](https://vale.dev/)). \[#
Though I\'m probably a bit biased. Just a bit.\]

Speaking of LLVM, if you look really closely, there\'s a \"SPIR-V LLVM
IR Translator\" box in the above image. It makes you wonder: could
general purpose languages take advantage of that?

\[ perhaps also mention [[ZLUDA Project Paves the Way for CUDA on Intel
GPUs]{.underline}](https://www.tomshardware.com/news/zluda-project-cuda-intel-gpus)
\]

## Parallelism for the User

The underlying technology is only half of the story. For something to
take off, it needs to be intuitive.

Luckily, parallelism is becoming more and more intuitive.

\* C\'s OpenMP has made parallelism as easy as adding a single line to a
for-loop.

\* Go raised channels to a first-class citizen in their language,
emphasizing message passing in an otherwise imperative language.

\* Languages like Pony and Rust have shown that, with some investment,
we can have code free of data races.

Truly, parallelism is getting more and more within reach.

On top of that, some languages are even combining the best of all
worlds, like described in \[Seamless, Structured, Fearless
Concurrency\](https://verdagon.dev/blog/seamless-fearless-structured-concurrency).

## How they might Meet

I would love to see a language offer one easy keyword, to launch a lot
of code in parallel on a GPU.

(show example)

wouldn\'t it be amazing if we could do:

(insert hypothetical vale snippet here, with \`pure unsafe parallel\`

## Challenges

That all sounds great, but how can we make it happen? There are a *lot*
of challenges between here and there.

### Shipping Data to the GPU

Let\'s pretend we have 10 GPU cores, and we have this data:

80, 82, 84, 86, 88, 1, 3, 5, 7, 9

and we want to halve the even numbers and triple the odd numbers:

45, 46, 47, 48, 49, 3, 9, 15, 21, 27

we might use this code:

results =

gpu-parallel foreach x in data {

is_even = (x % 2 == 0); // A

if is_even {

x / 2 // B

} else {

x \* 3 // C

}

};

If we have 2 CPU cores, and 10 GPU cores, the GPU could run this 5x
faster, right?

Unfortunately, we\'re missing one detail: we need to send that \`data\`
array to the GPU first. And then afterwards, we\'ll need to send that
\`results\` array all the way back to the CPU!

Sending that data could take a long time, so it might be faster to just
use our 2 CPU cores!

Imagine ten thousand Norwegian horseman traveling for two weeks to
Alaska, each with a simple addition problem, like 5 + 7. Ten thousand
Alaskan kindergarteners receive the problems, spend three seconds
solving them in parallel, and the ten thousand horseman spend another
two weeks returning. It would have been faster to have one Norwegian
kindergartener do the ten thousand additions!

If we need more calculations on less data, the GPU becomes much more
viable. A user needs to balance it.

However, there might be some interesting tricks to pull here.

-   We might be able to eagerly ship some data to the GPU before it\'s
    > needed.

-   Futhark allows us to chain different programs together.

    -   Called
        > \[Fusion\]([[https://futhark.readthedocs.io/en/stable/performance.html]{.underline}](https://futhark.readthedocs.io/en/stable/performance.html))

    -   This means we dont have to ship data back to the CPU and then to
        > the GPU again for the next phase, we just leave it there
        > instead.

    -   This is similar to how some shader frameworks allow us to piece
        > subshaders together.

Futhark is helping with this, by giving us building blocks that are
naturally more parallelizable and have less branching, *second-order
array combinators* (SOACs) such as map, reduce, scan, filter,
reduce_by_index.

I suspect languages will also become smarter about eagerly streaming
data to the GPU. We\'re starting to see the early signs of this in
\[CUDA\]([[https://developer.nvidia.com/blog/exploring-the-new-features-of-cuda-11-3/]{.underline}](https://developer.nvidia.com/blog/exploring-the-new-features-of-cuda-11-3/)).
If the GPU isn\'t yet available for more calculations anyway, then it
doesn\'t matter that we\'re sending data while we wait.

### Warps

The first big challenge is that CPU parallelism is *very* different from
GPU parallelism. The user will need to know when their parallelism
isn\'t suited for the GPU.

In CPUs, core A runs code X, while core B runs code Y. You can have
twelve different cores running twelve different functions. They\'re
completely separate

Nvidia GPUs have hundreds or thousands of cores, divided into groups of
32 cores, called \"warps\". \[# In keeping with the 3D graphics
tradition of clear and consistent naming, the SIMD concept is called
SIMD groups on Metal, warps on Nvidia, wavefronts on AMD, and subgroups
on Vulkan.\] All cores in a warp will all be executing the same code,
similar to how SIMD runs the \*s\*ame \*i\*nstructions on \*m\*ultiple
\*d\*ata.

This system has benefits, but also drawbacks, explained below.

### Branching in Warps

Let\'s pretend our GPU has 4 cores per warp, and we have this data:

80, 82, 1, 3

and we want to halve the even numbers and triple the odd numbers:

45, 46, 3, 9

we might use this code:

results =

gpu-parallel foreach x in data {

is_even = (x % 2 == 0); // A

if is_even {

x / 2 // B

} else {

x \* 3 // C

}

};

This is what happens:

-   All cores will run A.

-   Cores 1-2 will run B. Cores 3-4 \*will wait for them to finish.\*

-   Then, cores 1-2 \*will wait\* for cores 3-4 to run C.

As you can see, the warp only runs one instruction at a time, for
whatever cores might need it.

If the user doesn\'t pay attention to their branching, they could get
pretty bad performance.

But, the user can restructure their program so that all cores in a warp
will take the same branch, and there will be no waiting.

This might have two warps doing a lot of waiting:

80, 82, 1, 3, 84, 86, 5, 7

But this input will have two warps doing zero waiting:

80, 82, 84, 86, 1, 3, 5, 7

The user needs to be aware of this, and use techniques like \[bucket
sorting\](link here). Luckily, languages can help with this!

(explain more)

Futhark does this automatically with (mechanism?)

(verdagon continue here)

### More Challenges

(expand into a few more sections)

### Registers and Specificity

number of registers is important.

must be incredibly specific. C and zig are a good level to be at here.

vale\'s unsafe mode would also be pretty good here. rust and c++ would
also be pretty good. however, there are implicit things, like enum tags
in rust and vale.

### Absolute Performance

inconsistencies between platforms.

C, C++, zig embraces UB. vale and rust try to abstract over it.

## Conclusion

these are only some directions this could go. what happens in the next
decade will likely be something none of us expect!

Who knows, in 8 years, we might have ECS on the GPU. We\'ve already done
it with physics, so why not more?

# Scratch

read up more on MPL, integrate a lot more.

ECS OMG

-   manually like CUDA

-   \"parallel\" keyword

-   go-style waitgroups with futures? probably not, too much
    > abstraction.

-   being cross platform enough.

    -   intermediate languages could help a lot:

        -   [[https://devlog.hexops.com/2021/mach-engine-the-future-of-graphics-with-zig]{.underline}](https://devlog.hexops.com/2021/mach-engine-the-future-of-graphics-with-zig)

        -   OpenCL\'s C variant

-   extra factors/concerns:

    -   number of registers used is important

    -   warps, the effect of branching

    -   generation checks are a no-go, so we need \`unsafe\` in vale

openmp is doing some cool things, allowing us to easily specify the
memory to send over:
[[https://stackoverflow.com/questions/28962655/can-openmp-be-used-for-gpus]{.underline}](https://stackoverflow.com/questions/28962655/can-openmp-be-used-for-gpus)

vale could allow us to specify specific allocators?

how does futhark specify what memory goes to the GPU?

falling back to CPU.

it\'s also hidden because a lot of our benchmarks are measuring problems
that are more suited for CPU parallelism. but in things like
\[surprising use case\], GPU\'s absolutely *destroy* CPUs.

there are a lot of promising ways to parallelize code:

-   running a thread (show a C# example)

-   async/await (low overhead, saves memory)

-   goroutines (saves memory)

-   actors (saves memory, actors barely have any state)

-   cone is thinking of blending actors and async/await

-   vale may have found a way to blend async/await and goroutines (or
    > maybe dont mention this yet)

a section on data locality for warps?

This means that gpu-parallel cannot be a magic wand to speed up our
code. The user (or a very smart compiler?) needs to know when to use
gpu-parallel, and when regular CPU parallelism might be better.

For peak efficiency, we\'ll always need to know the low level details,
and break into implementation-defined behavior. This is why languages
like Zig exist; there\'s no such thing as a zero-cost abstraction,
because no abstraction is perfect.

## Futhark

\[overview of futhark\]

## MPL

One language is already leading the charge.

\[describe MPL\]
