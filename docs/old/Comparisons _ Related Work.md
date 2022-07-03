**Swift**

Definitely the closest, if they ever choose to cross-compile to JVM.

-   Their cross-compiled-to-JVM code will be slower. Because of unowned
    > and weak, they\'ll need a ref count in their objects, even in
    > production modes. Vale won\'t need it; it will only use it in
    > development, and then turn it off for production builds on the
    > JVM. These ref counts are atomic, so they\'re not only adding
    > refcount overhead, theyre adding **atomic** refcount overhead.

**Xamarin**

Xamarin is a framework, that takes control of your entire application.
Vale is about being used alongside everything. One can gradually migrate
to and from Vale.
