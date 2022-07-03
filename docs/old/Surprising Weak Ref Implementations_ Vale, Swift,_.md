notes for a part 2

in between the unowned pointer, which is similar to a raw pointer in C &
C plus plus. this pointer is conceptually week with an assertion that it
is still alive when we use it. valgrind does this for us. we might
support this kind of reference in vale, but only to weakable objects, so
people are aware of the cost.

Inside: Python

https://dev.nextthought.com/blog/2018/05/python-weakref-cython-slots.html

\"This example was written in CPython, which uses a reference counting
garbage collection system. In this system, weak references are
(usually!) cleared immediately after the last strong reference goes
away. In other implementations like PyPy or Jython that use a different
garbage collection strategy, the reference may not be cleared until some
time later. Even in CPython, reference cycles may delay the clearing.
Don\'t depend on references being cleared immediately!\"

You can attach Call backs which is kind of cool, but it does cause a lot
of weird effects, see
https://hg.python.org/cpython/file/4e687d53b645/Modules/gc_weakref.txt.

DO NOT USE. deterministic until you fuck up and make a cycle, or switch
to gc.

Dispose

Java is similar so far. Though, in Java, we often mimic weak pointers in
C# with an alive Boolean, and we manually check whether something is
alive in any method on the object.

C# has a much better way of doing that. its dispose method there is a
bit that will keep track of whether the object is still alive and then
CLR checks if we are accessing a disposed object.

https://docs.microsoft.com/en-us/dotnet/api/system.objectdisposedexception?view=netcore-3.1

this is further proof that even in garbage collected languages we could
benefit from single ownership. see the next steps for raii for more on
this.

Similar to an unowned pointer.

In fact you can make a week pointer out of this by trying to call
tostring and seeing if it throws an exception.
