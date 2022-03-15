"Data Flow Circuits" is a hobby project, where I'm creating a data flow oriented programming language with a graphical editor.

As part of my Minecraft mod RedstoneControl, I had previously implemented a runtime compiler that would translate a data flow graph into JVM byte code in order to efficiently emulate player build logic circuits.
Now I decided to turn that concept into a complete programming Language for writing regular programs that run on computers in the real world.

![](example.png)

The editor is using OpenGL-3.2 and GLFW from [LWJGL-3.2.1](https://www.lwjgl.org/) for rendering and inputs.
The compiler is currently outputting LLVM-IR that can be executed or further processed with [LLVM](https://llvm.org/).

## Features (implemented and planned):
**Compiler:**
- [x] LLVM-IR target (allows optimization and compiling to many other systems)
- [ ] JVM-bytecode target
- [ ] GLSL shader target

**Language:**
- [x] type system based on compile time constants
- [x] arithmetic, logic and comparison with integers and booleans
- [x] floating point arithmetic and comparison
- [x] conditional switches
- [x] loops
- [x] struct data types bundling values together to feed through switches and other structures.
- [x] memory access
- [x] typed pointers
- [x] functions
- [x] declaration of external functions
- [ ] name tags for signal and pointer type elements
- [ ] macro blocks
- [ ] built in multi-threading

**Editor:**
- [x] primitive data flow graph editor
- [x] load / save source graph files (currently single hard-coded file path)
- [x] type-checking in editor
- [x] panning and zooming
- [ ] multi-selection for movement, deletion and copy-paste
- [ ] menu for multiple source files
- [?] improved block palette with hot-keys
- [ ] improved signal type inspection (as tree)
- [ ] routing assistance

## Editor Controls
The editor is started by running the class `cd4017be/dfc/editor/Main.java`:

Right-click on pin or trace node to draw a trace, right click again to place trace node or connect trace to clicked node/pin. Right-click-drag a trace node to move it (pushing a node on top of another will connect them).

Left-click a circuit block to select it. Left-click-drag a circuit block to move it (overlapping pins will auto-connect).

Middle-click a circuit block to clone it.

Typing while a constant is selected will edit the value of that constant.

Key-bindings:
- `CTRL`+`S`: save the circuit schematic to `./test/test.dfc`.
- `CTRL`+`L`: load the circuit schematic from `./test/test.dfc`.
- `CTRL`+`T`: run the type checker to show traces colored depending on data type. If the type check fails, an error message is displayed at the bottom of the screen and the problematic circuit block is selected.
- `CTRL`+`M`: run the compiler, creating a LLVM_IR assembly file at `./test/test.ll`.
- `CTRL`+`D`: remove all orphaned traces.
- `DEL`: delete a selected circuit block.

## Compiled Programs
The compiled program can be executed from command line using the LLVM interpreter like so `lli test.ll ...`, with optional arguments passed to the program.

This will call the program's main function that outputs the integer number of command-line arguments followed by a pointer to array of pointer to array of byte containing the arguments as null-terminated strings.

For more info about the language see the [documentation](doc/signals.md)