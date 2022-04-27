# Signals:
Signals transfer data between operation blocks. Every signal represents the output of one operation block and may be used by one or more inputs of other operation blocks.

Signals have a data type, a value and one of the following behaviors:
- **Constant**: The signal value is evaluated at compile time during type checking and it will be a constant at runtime.
- **Dynamic**: The signal value is only evaluated at runtime and unknown at compile time.
- **Imaginary**: The signal value is never defined and only serves as a placeholder to specify data types.

Data type and behavior are always defined at compile time during type checking, which makes this language statically typed.

## Data Types:
The data types in this language can be categorized into primitive types, aggregate types and reference types.

**Primitive Types**:
- ![b](../src/resources/textures/blocks/#b.png) 1-bit boolean: `false = 0, true = 1`
- ![w](../src/resources/textures/blocks/#w.png) 8-bit signed: `-128 ... +127`
- ![s](../src/resources/textures/blocks/#s.png) 16-bit signed: `-32 768 ... +32 767`
- ![i](../src/resources/textures/blocks/#i.png) 32-bit signed: `-2 147 483 648 ... +2 147 483 647`
- ![l](../src/resources/textures/blocks/#l.png) 64-bit signed: `±9.22 x10^18`
- ![W](../src/resources/textures/blocks/#uw.png) 8-bit unsigned: `0 ... 255`
- ![S](../src/resources/textures/blocks/#us.png) 16-bit unsigned: `0 ... 65 535`
- ![I](../src/resources/textures/blocks/#ui.png) 32-bit unsigned: `0 ... 4 294 967 295`
- ![L](../src/resources/textures/blocks/#ul.png) 64-bit unsigned: `0 ... 1.845 x10^19`
- ![f](../src/resources/textures/blocks/#f.png) 32-bit floating point: range `±1.18 x10^-38 ... ±3.4 x10^38`, relative precision `1.19 x10^-7`
- ![d](../src/resources/textures/blocks/#d.png) 64-bit floating point: range `±2.23 x10^-308 ... ±1.80 x10^308`, relative precision `2.22 x10^-16`

**Aggregate Types**:
- ![size[element] | [element]](../src/resources/textures/blocks/array.png) array: Holds a sequence of same typed values. The size can be statically or dynamically defined, but dynamically sized arrays can only exist in memory (pointers).
- ![size element | ptr_element size*](../src/resources/textures/blocks/vector.png) vector: Holds a sequence of same typed values. Can be supplied to all kinds of arithmetic, logic or comparison operators to perform element-wise SIMD operations. It only allows primitive or pointer types as element and the size must be statically defined. 
- ![{elements...}](../src/resources/textures/blocks/struct.png) structure: Holds multiple values that can have different types. The content types must be statically defined. Elements can be given names to use instead of numbers for indexing.
- ![elements...][pack] bundle: This is not technically a data type, since it is not allowed as element of any other type, can't be stored in memory and signals of this type have no behavior or value by themselves. It just packages multiple signals of possibly different data types and even different behaviors together. Just like in structures, elements can be named. Bundles allow blocks to receive a variable number of input signals (structure initializers or function parameters for example) though a fixed number of input pins. They are also useful to feed multiple signals through a switch or loop or to simply tidy up wiring in large circuits.

**Reference Types**:
- ![element*][ref] (data) pointer: Represents a location in memory and specifies the data type stored there.
- ![return(parameters...)][funt] function pointer: Represents a callable function and specifies its parameter types and return type. Parameters can be named for clarity.

# Control Flow:
When the program is executed the operation blocks and signals in it's data flow graph are evaluated based on the following rules:

- ~All macro blocks are recursively replaced with a copy of their implementation graph at compile time.~ *(macros are not implemented yet)*
- An operation block can not be evaluated before all of it's inputs have been evaluated first.
- An operation block is only evaluated if its output is used by at least one operation block that will be evaluated later.
- ~An operation block that has already been evaluated will not be evaluated again unless any of its inputs is also evaluated again.~ *(not properly implemented yet) [1]*
- An operation block that outputs a constant or imaginary signal will be evaluated at compile time. This will not cause runtime evaluation of its inputs according to the last two rules above, even if some of these inputs are dynamic. Except the operators ![pack bundle][pack], ![get element][get], ![pre-process][pre] and ![post-process][post] will always evaluate their inputs, even if the output is constant.
- A ![pre-process][pre] block will evaluate its `side-effect` input first and its `value` input last.
- A ![post-process][post] block will evaluate its `value` input first and its `side-effect` input last.
- A ![conditional switch][swt] block evaluates its `switch condition` input first and then only evaluates the `branch` input that is chosen by the condition. If the condition is a constant then only the selected branch will be compiled, completely eliminating the unused branch. *[1]*
- A ![loop][loop] block first evaluates its `loop initializer` input and provides it as output. Then it evaluates the `loop condition` input which generally should depend on the loop's output. If the condition evaluates to true then the loop's `next state` input is evaluated and used as new output for evaluating the loop block again.*[1]*
- A ![function definition][def] block evaluates its `function signature` input at compile time to produce a function pointer of that signature. At runtime, whenever a ![call][call] block that uses this function pointer is evaluated, it will evaluate the `return` input of the corresponding ![function definition][def] block for its output. During this evaluation, the parameters passed to the ![call][call] block can be obtained by using a ![get element][get] operator on the function pointer output of ![][def] (functions are defined like a loop body).
- The ![main][main] block works similar to a ![function definition][def] block but defines the program's main function with a fixed signature. It is automatically called when the program starts.
- The `return` inputs of different ![function definition][def] or ![main][main] blocks are not allowed to depend on shared dynamic signals (shared constants are fine). *[2]*
- Except for ![loop][loop], ![function definition][def], ![main][main] and ![reference][ref], blocks are not allowed to depend on their own output.

Even though block inputs may seem to be always evaluated in a particular order, programmers should not rely on that apparent behavior. Because the compiler is generally free to choose any block evaluation order (potentially even asynchronous) that complies with the above rules. If parts of a program require a specific evaluation order to function correctly you have to explicitly enforce it through signal dependencies and ![pre-process][pre] / ![post-process][post] blocks.

*[1] : Commonly used nodes between switch branches or next loop state and loop result are not properly resolved yet. Make sure to force the switch or loop condition to depend on such common nodes via* ![pre-process][pre] *or* ![post-process][post] *to avoid generating invalid or wrong assembly code!*  
*[2] : Enforcement of this rule is not implemented yet, but violating it will produce invalid assembly code!*

[pack]: ../src/resources/textures/blocks/pack.png
[get]: ../src/resources/textures/blocks/get.png
[pre]: ../src/resources/textures/blocks/pre.png
[post]: ../src/resources/textures/blocks/post.png
[swt]: ../src/resources/textures/blocks/swt.png
[loop]: ../src/resources/textures/blocks/loop.png
[def]: ../src/resources/textures/blocks/def.png
[main]: ../src/resources/textures/blocks/main.png
[call]: ../src/resources/textures/blocks/call.png
[ref]: ../src/resources/textures/blocks/ref.png
[funt]: ../src/resources/textures/blocks/funt.png

# External Definitions:
A lot of advanced functionality such as file I/O or graphics rendering is only available through external libraries.

You can access these in your code via the ![include][include] block that takes in an imaginary signal specifying the type of an external function or global variable pointer to declare and outputs a constant pointer for the declaration of a given name.

However, manually declaring all function signatures like this for a large library is tedious and error prone. Instead you can also declare things through a C source code file that must to be located in the same folder as the `.dfc` file of your program and have the same name except for a `.c` suffix instead of `.dfc`.
This file is automatically loaded when opening the circuit but you can also manually reload it later via `CTRL`+`H`.

The file is first passed through the C preprocessor and then parsed for declarations and macro definitions.
*Note that `cpp` is called as external program so you need to have a c compiler (such as [gcc](https://gcc.gnu.org)) installed and properly registered in the `PATH` environment variable so the editor can find it.* If you don't use any directives such as `#inlude` or `#ifdef` and don't need macro expansions in your `.c` file, you can also press `CTRL`+`SHIFT`+`H` to load the file without invoking the preprocessor (faster).

When using c-libraries you can simply include their header files with `#include "header.h"` (or `#include <header.h>` for system headers) in your `.c` file.

After that you can access the declarations through the ![include][include] block from just the name without having to provide type templates. In addition to functions and globals it also provides any C type declaration as an imaginary signal. Also any C macro that is defined to a numeric value can be used inside numeric constant blocks.

[include]: ../src/resources/textures/blocks/#x.png
