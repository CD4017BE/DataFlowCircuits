## Constants:

- ![Number constant](../src/rawimg/textures/blocks/#N.png) outputs a bundle of constant numbers defined by a string.
- ![Type constant](../src/rawimg/textures/blocks/#T.png) outputs the types specified by a type string.
- ![Function declaration](../src/rawimg/textures/blocks/#X.png) outputs a function pointer to the externally declared function with the given name and the signature specified by the input function type.
- ![Zero initializer](../src/rawimg/textures/blocks/zero.png) outputs a bundle of signals with the constant default values for the given input types. These are `0` for integer types, `+0.0` for floating point types, `null` (null-pointer) for pointer types and `?` (unknown type) for type types.

## Arithmetic:
These operators accept integer (**B, S, I, L**) and floating point (**F, D**) inputs, but both inputs must be of the same type.
If the inputs are bundles of more than one signal, the operation is performed element-wise.
The output has the same type as the inputs and will be constant if both inputs are constant.

- ![Negation](../src/rawimg/textures/blocks/neg.png) outputs the arithmetic negation of the input (has only one).
- ![Addition](../src/rawimg/textures/blocks/add.png) outputs the sum of the two inputs.
- ![Subtraction](../src/rawimg/textures/blocks/sub.png) outputs the top input minus the bottom input.
- ![Multiplication](../src/rawimg/textures/blocks/mul.png) outputs the product of the two inputs.
- ![Signed division](../src/rawimg/textures/blocks/div.png) outputs the signed division of the top input by the bottom input. *[1]*
- ![Signed remainder](../src/rawimg/textures/blocks/mod.png) outputs the signed division remainder of the top input by the bottom input. The output has the same sign as the dividend. *[1]*
- ![Unsigned division](../src/rawimg/textures/blocks/udiv.png) outputs the unsigned division of the top input by the bottom input. *[1,2]*
- ![Unsigned remainder](../src/rawimg/textures/blocks/umod.png) outputs the unsigned division remainder of the top input by the bottom input. *[1,2]*
- ![Sum](../src/rawimg/textures/blocks/sum.png) outputs the sum of all elements in the input signal. *[3]*
- ![Product](../src/rawimg/textures/blocks/prod.png) outputs the product of all elements in the input signal. *[3]*

*[1] : Integer division by zero has undefined behavior at runtime and is a compile error if it's constant.*  
*[2] : Not supported for floating point types.*
*[3] : Output has only one element and all input elements must be of same type.*

## Bitwise Logic:
These operators accept integer (**B, S, I, L**) and boolean (**Z**) inputs, but both inputs must be of the same type.
If the inputs are bundles of more than one signal, the operation is performed element-wise.
The output has the same type as the inputs and will be constant if both inputs are constant.

- ![NOT-Gate](../src/rawimg/textures/blocks/not.png) outputs the logical/bitwise negation of the input (has only one).
- ![OR-Gate](../src/rawimg/textures/blocks/or.png) outputs the logical/bitwise OR of the two inputs.
- ![AND-Gate](../src/rawimg/textures/blocks/and.png) outputs the logical/bitwise AND of the two inputs.
- ![XOR-Gate](../src/rawimg/textures/blocks/xor.png) outputs the logical/bitwise exclusive-OR of the two inputs.
- ![NOR-Gate](../src/rawimg/textures/blocks/nor.png) outputs the logical/bitwise not-OR of the two inputs.
- ![NAND-Gate](../src/rawimg/textures/blocks/nand.png) outputs the logical/bitwise not-AND of the two inputs.
- ![Bit-shift left](../src/rawimg/textures/blocks/shl.png) outputs the bits of the left input shifted to the left by the top input. *[3]*
- ![Signed bit-shift right](../src/rawimg/textures/blocks/sshr.png) outputs the bits of the left input shifted to the right by the top input. Bits shifted in copy the sign bit.*[1]*
- ![Unsigned bit-shift right](../src/rawimg/textures/blocks/ushr.png) outputs the bits of the left input shifted to the right by the top input. Bits shifted in are filled with zeroes.*[1]*
- ![Any](../src/rawimg/textures/blocks/any.png) outputs true if any element in the input is true. *[2]*
- ![All](../src/rawimg/textures/blocks/all.png) outputs true only if all elements in the input are true. *[2]*

*[1] : Not supported for booleans.*  
*[2] : Output has only one element and all input elements must be boolean.*

## Comparison:
These operators accept integer (**B, S, I, L**), floating point (**F, D**) and pointer (**[], ()**) inputs, but both inputs must be of the same type (pointers may have different data structure types).
If the inputs are bundles of more than one signal, the operation is performed element-wise.
The output is always of type boolean and will be constant if both inputs are constant and not pointers.

- ![Equal](../src/rawimg/textures/blocks/eq.png) outputs true if both inputs are equal.
- ![Not equal](../src/rawimg/textures/blocks/ne.png) outputs true if both inputs are not equal.
- ![Less than](../src/rawimg/textures/blocks/lt.png) outputs true if top input is less than bottom input. *[1]*
- ![Greater than](../src/rawimg/textures/blocks/gt.png) outputs true if top input is greater than bottom input. *[1]*
- ![Less or equal](../src/rawimg/textures/blocks/le.png) outputs true if top input is less than or equal bottom input. *[1]*
- ![Greater or equal](../src/rawimg/textures/blocks/ge.png) outputs true if top input is greater than or equal bottom input. *[1]*

*[1] : Not supported for function pointers. And ordered comparison of data pointers is only meaningful if they both point to parts of the same data structure.*

## Casts:

- ![Signed cast](../src/rawimg/textures/blocks/cast.png) casts Z <-> BSILFD, B <-> SILFD, S <-> ILFD, I <-> LFD, L <-> FD as signed integers and F <-> D according to floating point standard.
- ![Unsigned cast](../src/rawimg/textures/blocks/ucast.png) casts Z <-> BSILFD, B <-> SILFD, S <-> ILFD, I <-> LFD, L <-> FD as unsigned integers and F <-> D according to floating point standard. Converting a negative floating point number to an unsigned integer has undefined results.
- ![Bit cast](../src/rawimg/textures/blocks/bcast.png) casts by reinterpreting the binary data. The input source data must have the same compact binary size as the target type. Casts between number/boolean and pointer types are not allowed. Pointer -> Pointer casts are only allowed if their structure types could also bit cast to each other or are of type unknown and the cast can not loosen access restrictions. Function -> Function casts can only apply legal target -> source bit casts on pointer parameter types and source -> target bit casts on pointer return types.

## Memory:

- ![Create reference](../src/rawimg/textures/blocks/ref.png) outputs a pointer to a data structure that is initialized with the values from the bottom input. The type of the returned pointer can be specified via the top input but it must be compatible with the types of the provided initial values. If no type is supplied, it defaults to a fixed structure. If all initial values are constants then the pointer is allocated as global variable (or constant if pointer type is read-only), otherwise it is allocated on the stack and automatically freed when the current function returns.
- ![Allocate memory](../src/rawimg/textures/blocks/alloc.png) outputs a pointer to allocated uninitialized memory. The bottom input specifies the pointer type and the top input specifies the number of array elements to allocate (only needed if the data structure has an array section).
- ![Free memory](../src/rawimg/textures/blocks/free.png) Frees the memory allocated for the input pointer (if it's heap memory) and outputs nothing. The given pointer must have been created by the allocation operators above.
- ![Array indexing](../src/rawimg/textures/blocks/idx.png) outputs a pointer to an element in the array section of the input pointer. The array element index is given by the top input which must be of an integer type (**B, S, I, L**). The data layout of the resulting pointer will be the array element as fixed structure.
- ![Pointer load](../src/rawimg/textures/blocks/load.png) dereferences the input pointer and outputs the contents of it's fixed section.
- ![Pointer store](../src/rawimg/textures/blocks/store.png) writes the values from the top input into the fixed section of the pointer from left input and outputs nothing.

## Control flow:

- ![Input node](../src/rawimg/textures/blocks/in.png) provides an external input signal for the current macro block through its output.
- ![Output node](../src/rawimg/textures/blocks/out.png) evaluates the output signal for the current macro block through its input. There must be exactly one such block inside every macro definition and its output should not be connected!
- ![Switch](../src/rawimg/textures/blocks/swt.png) If the boolean top input is true then the left input is evaluated as output, otherwise the bottom input is evaluated as output. The types of both branches must be equal, except when the condition input is a constant then the unused branch is completely discarded by the compiler.
- ![Loop](../src/rawimg/textures/blocks/loop.png) The left output initially returns the top input. While the boolean bottom input evaluates to true, the right input is (re-)evaluated and provides the state of the left output for the next iteration. Any circuitry not part of the loop will see the output in its final state that caused the loop condition to become false (assuming this eventually happens).
- ![Function call](../src/rawimg/textures/blocks/call.png) calls the function pointer from the top input with the argument values from the bottom input and outputs the values returned by the call.
- ![Function definition](../src/rawimg/textures/blocks/def.png) defines a function with the type specified by the top input and outputs (left side) a pointer to it, followed by the function parameter list to be used by its implementation graph. The right input is recursive and serves as return node of the implementation graph and must be compatible with the function's return type. *Note: you should isolate the output function pointer from the parameter list because using the parameters outside the function's implementation graph is not allowed.*
- ![Main function](../src/rawimg/textures/blocks/main.png) defines the main() function of the program. It outputs the command line arguments given to the program as integer argument count, followed by a pointer to an array of null-terminated strings (pointers to byte array). The left input gets evaluated when the program is executed and represents the exit code as integer.

## Signals & Types

- ![Pack signals](../src/rawimg/textures/blocks/pack.png) outputs the concatenation of the three input signal bundles.
- ![Pick signals](../src/rawimg/textures/blocks/pick.png) outputs a picked subset of signals from the input signal bundle. **TODO:** string syntax with `[]`.
- ![Signal count](../src/rawimg/textures/blocks/count.png) outputs the constant number of elements in the input signal bundle as unsigned int.
- ![Type of](../src/rawimg/textures/blocks/type.png) outputs the types of the input values.
- ![Pointer type of](../src/rawimg/textures/blocks/ptrt.png) outputs a data pointer type based on the fixed structure types from the top input and the array structure types from the bottom input. Both inputs support recursion. *[1]*
- ![Function type of](../src/rawimg/textures/blocks/funt.png) outputs a function pointer type based on the parameter types from the top input and the return types from the bottom input. Both inputs support recursion. *[1]*
- ![Parameter types](../src/rawimg/textures/blocks/elt0.png) outputs the fixed structure types of a data pointer type input or the parameter types of a function pointer type input. *[1]*
- ![Return types](../src/rawimg/textures/blocks/elt1.png) outputs the array structure types of a data pointer type input or the return types of a function pointer type input. *[1]*

*[1] : Also accept actual values instead of types as input that will be automatically converted.*

