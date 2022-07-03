[[https://twitter.com/fitzgen/status/968146562831474691?lang=en]{.underline}](https://twitter.com/fitzgen/status/968146562831474691?lang=en)

Our goal is to make code faster on all four platforms.

-   Start on the Vale-Rust Hybrid Model

-   Monomorphize in JS and Java, and be able to call into native code.

-   Improve branch prediction in swift, and be able to call into native
    > code.

our main thrust will be \"make apps and servers lightning fast\"

the vision will have three parts:

1\. be able to transpile vale in such a way that\...

2\. \...we can easily call into native code, which is shared between
platforms (and even server), which\...

3\. \...is extremely fast (and easy to use!)

our demo will show off the first steps towards #1 and #3:

1\. we\'ll have working x-compiling to JS and swift (and hopefully
java), showing off that vale can be x-compiled

3\. we\'ll have a demo of V0 of the Vale-Rust Hybrid Model

the Vale-Rust Hybrid Model (veim, i think you saw \"fast resilient
mode\" which was V1 of this, though now its on \~V3) is vale\'s secret
weapon, and i wasn\'t sure if we could include it in this langjam and
reveal it to the world, but i think i figured out a way to get a
proof-of-concept going

our vision can be summed up by \"making code lightning fast, from the
servers all the way to the apps\"

the \"fresh\" ideas in there are:

1\. we can make single ownership into the lingua franca of the
programming world

2\. how inl and region annotations can be ignored to allow us
optimizability and x-compilability

3\. using generation numbers as the foundation for an entirely new, much
faster and easier memory model

these three fresh ideas combine to bring us hitherto-undreamt speed, all
up and down the stac
