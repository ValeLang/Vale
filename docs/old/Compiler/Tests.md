# Extern and Export Tests

(EAET)

We have a *lot* of tests for extern and export functionality. They live
inside Valestrom/Tests, but most of them are only used by Midas, since
they test interaction with C code.

We have some patterns among all the extern/export tests.

## Naming

We prefix export functions with \"vale\" and c functions with \"c\".

For example cSumFuel and vSumFuel.

## Export/Extern

For immutables, there is often an **export** and **extern** flavor of
it.

For example, we have an interfaceimmparamdeep**export** and
interfaceimmparamdeep**extern** test.

-   The **extern** one just sends the data into C, and then the C reads
    > it directly and does a calculation on it.

-   The **export** one sends the data into C, and then C sends it back
    > into Vale to do calculations, and both return the result to
    > vale\'s main.

For mutables, there\'s just an **export** flavor. This is because C
can\'t directly read the data in mutables, it needs to call back into
vale to read from it.

## Deep

Also for immutables, there is often a **deep** and normal flavor of it.

For example, we have an interfaceimmparamexport and
interfaceimmparam**deep**export test.

The **deep** one includes something else inside it, like a struct. This
is to make sure we deeply serialize correctly.

To save testing time, we do it on param tests, but not on return tests.
We can reasonably assume that if deep serializing/unserializing works
for one, it works for the other.
