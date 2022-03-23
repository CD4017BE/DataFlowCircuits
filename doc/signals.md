# Signals:
Signals transfer data between operation blocks. Every signal represents the output of one operation block and may be used by one or more inputs of other operation blocks.

The data carried by a signal may consist of multiple elements with different data types being bundled together similar to a C-struct that is passed by value. It is possible for a signal to have zero elements, meaning it carries no data and only declares execution order dependencies between operation blocks. This so called empty signal is often used by blocks that operate through side effects.

## Working with bundled signals:
The individual elements of a bundled signal are addressed with a zero based index.

When two signals with `N` and `M` elements are packed together by the ![pack][pack] operator, the output signal will have `N+M` elements and contain the top input at indices `0 ... N-1` and the bottom input at indices `N ... N+M-1`.

The ![pick][pick] operator isolates one or more elements of its input based the indices specified in its text argument:
- `a` specifies the single index `a` that must be given as positive decimal number (including 0).
- `a-b` specifies a range of indices from `a` to `b` inclusive.
- `-b` specifies a range from 0 to `b` inclusive.
- `a-` specifies a range from `a` up to the last element of the input.
- `-` specifies the full range containing all input elements.
- An index or range may be followed by `[x]` to perform pointer indexing on each element. Here `x` stands for another index or range that addresses elements in the pointer's data structure. The result is a pointer to the specified data sub-region.
- Multiple indices or ranges may be separated by `,` (not allowed inside `[ ]`) to be concatenated as if by ![pack][pack].

# The type system:
The following lists all signal element types that are currently supported in this language:

- `?` (unknown): represents unknown data layout in pointers or an incomplete type checking result.
- `T` (type): always constant, represents a type.
- `V` (void): 0-bit no data, currently unused.
- `Z` (boolean): 1-bit integer, unsigned (false = 0) < (true = 1), signed (true = -1) < (false = 0)
- `B` (byte): 8-bit integer, unsigned 0 ... 255, signed -128 ... +127
- `S` (short): 16-bit integer, unsigned 0 ... 65 535, signed -32 768 ... +32 767
- `I` (int): 32-bit integer, unsigned 0 ... 4 294 967 296, signed -2 147 483 648 ... +2 147 483 648
- `L` (long): 64-bit integer, unsigned 0 ... 1.84 x10^19, signed ±9.22 x10^18
- `F` (float): 32-bit floating point, range ±1.18 x10^-38 ... ±3.4 x10^38, relative precision 1.19 x10^-7
- `D` (double): 64-bit floating point, range ±2.23 x10^-308 ... ±1.80 x10^308, relative precision 2.22 x10^-16
- `[s:a]` (heap pointer): target dependent size. Points to a fixed structure of types `s` followed by an array of types `a` allocated in heap memory and must be manually freed.
- `[!s:a]` (stack pointer): target dependent size. Allocated on the call stack and automatically freed on return.
- `[#s:a]`/`[!#s:a]` (read only pointers)
- `(p:r)` (function pointer): target dependent size. Points to a function that takes parameters `p` and returns `r` (multiple return values possible).

In addition to having a type, each signal element can be either constant or dynamic. Constant signal elements carry data whose state is already known at compile time. Whereas the state of dynamic signal elements is only defined at runtime.

## Types as signals:
Since everything in this language is represented by a flow graph, this also applies to its type system.
This handled via type signals that don't carry any data but instead represent the types of other signals or the memory layout of data structures. These are always constant and only exist at compile time. This means you can not use "type" as element of a pointer's data structure or in parameters and return values of functions.

The ![type constant](../src/resources/textures/blocks/#T.png) block creates a type signal bundle from its string argument (multiple types are simply concatenated without any separation characters):  
- Primitive types are represented by the characters `VZBSILFD` according to the [list above](#the-type-system).
- Data pointer types start with `[`, followed by an optional `!` to mark it as stack pointer and an optional `#` to mark it as read only. After that comes a (possibly empty) sequence of types describing the fixed data structure layout, followed by `:`, an optional number and another (possibly empty) sequence of types describing the array length (if known) and array element layout. Finally the pointer type ends with `]`. The `:` can be omitted if the array section of the pointer type is empty.
- Function pointer types start with `(`, followed by a (possibly empty) sequence of parameter types, a `:` and a (possibly empty) sequence of return types. Finally it ends with `)`. The `:` can be omitted if the return types are empty.
- `T` represents the type of type signals but is generally not useful. It is also not valid inside function or data pointer types.
- `?` is only allowed inside the array element layout of a pointer to declare the data layout as (partially) unknown. In this case it must be the only type element in the array layout and the array length must be zero or unspecified.

Pointer types can also be programmatically defined using the ![pointer typedef][ptrt] and ![function typedef][funt] blocks that take the structure & array / parameter & return type sequences as type signal inputs. These blocks can depend on their own output which makes it possible to define recursive types.

# Control Flow:
When the program is executed the operation blocks and signals in it's data flow graph are evaluated based on the following rules:

- ~All macro blocks are recursively replaced with a copy of their implementation graph at compile time.~ *(macros are not implemented yet)*
- An operation block can not be evaluated before all of it's inputs have been evaluated first.
- An operation block is only evaluated if its output is used by at least one operation block that will be evaluated later.
- ~An operation block that has already been evaluated will not be evaluated again unless any of its inputs is also evaluated again.~ *(not properly implemented yet) [1]*
- An operation block that only outputs constant signal elements may be evaluated at compile time. This will not cause runtime evaluation of its inputs according to the last two rules above, even if some of these inputs are dynamic. Currently, only the signal bundling operators ![pack signals][pack], ![extract signals][pick] and ![signal dependency][dep] will runtime evaluate inputs despite the output being constant (this is so that the empty output signal from blocks that operate through side effects can be bundled together with other signals to declare a runtime dependency on that side effect).
- A ![conditional switch][swt] block evaluates its `switch condition input` first and then only evaluates the `branch input` that is chosen by the condition.*[1]*
- A ![loop][loop] block first evaluates its `initial state input` and provides it as output. Then it evaluates the `loop condition input` which generally should depend on the loop's output. If the condition evaluates to true then the loop's `next state input` is evaluated and used as new output for evaluating the loop block again.*[1]*
- A ![function definition][def] block evaluates its `type input` at compile time to produce a function pointer while the remaining entries of its output (`function parameters`) are undefined. At runtime, whenever a ![call][call] block that uses this function pointer is evaluated, it will evaluate the `return input` of the corresponding ![function definition][def] block for its output. During this evaluation, the ![function definition][def] block will output the ![call][call] block's `parameters input` (functions are defined like a loop body).
- The ![main][main] block works similar to a ![function definition][def] block but defines the program's main function with a fixed signature and doesn't output a function pointer (instead it's automatically called when the program starts).
- The `return input` of a ![function definition][def] or ![main][main] block is not allowed to depend on the `function parameters` of a different ![function definition][def] block.
- Except for ![loop][loop], ![function definition][def], ![main][main], ![pointer type definition][ptrt] and ![function type definition][funt], blocks are not allowed to depend on their own output.

*[1] : Commonly used nodes between switch branches or next loop state and loop result are not properly resolved yet. Make sure to force the switch or loop condition to depend on such common nodes via ![signal dependency][dep] to avoid generating invalid or wrong assembly code!*

[pack]: ../src/resources/textures/blocks/pack.png
[pick]: ../src/resources/textures/blocks/pick.png
[dep]: ../src/resources/textures/blocks/void.png
[swt]: ../src/resources/textures/blocks/swt.png
[loop]: ../src/resources/textures/blocks/loop.png
[def]: ../src/resources/textures/blocks/def.png
[main]: ../src/resources/textures/blocks/main.png
[call]: ../src/resources/textures/blocks/call.png
[ptrt]: ../src/resources/textures/blocks/ptrt.png
[funt]: ../src/resources/textures/blocks/funt.png
