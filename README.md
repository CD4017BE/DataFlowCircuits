"Data Flow Circuits" is a hobby project, where I'm creating a data flow oriented programming language with a graphical editor.

As part of my Minecraft mod RedstoneControl, I had previously implemented a runtime compiler that would translate a data flow graph into JVM byte code in order to efficiently emulate player build logic circuits.
Now I decided to turn that concept into a complete programming language for writing regular programs that run on computers in the real world.

The language is strongly statically typed and quite low level in its core with pointers and manual memory management.
But most of the time types are automatically derived from input signals through compile-time polymorphic operators. And higher level abstractions will be possible via macro blocks (not implemented yet). 

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
- [x] name tags for structure, function parameter and bundle elements
- [ ] dynamic memory allocation
- [ ] type casting operators
- [ ] macro blocks
- [ ] built in multi-threading

**Editor:**
- [x] primitive data flow graph editor
- [x] load / save source graph files (currently single hard-coded file path)
- [x] type-checking in editor
- [ ] real time type checking in editor
- [x] panning and zooming
- [x] multi-selection for movement and deletion
- [x] menu for multiple source files
- [x] improved block palette
- [ ] improved signal type inspection (as tree)
- [ ] routing assistance

## Editor Controls
The editor is started by running the class `cd4017be/dfc/editor/Main.java`:

Scroll on the board to zoom the view.  
Right-click-drag on the board to move it around.  
Scroll on the block palette to slide it sideways.  
Middle-click to clone a selected circuit block.  

Left-click ...
- in the block palette to spawn a new block of the selected type. Click again on the board to place it.
- a pin or trace node to draw a trace, click again to place a new trace node or connect the trace to a selected node / pin.
- drag a trace node to move it (pushing a node on top of another will connect them).
- a text containing block to edit it (click somewhere else to end text edit mode).
- drag a block to move it (overlapping pins will auto-connect).
- drag from an empty position to select multiple blocks and traces to be moved after release. Then click again to finish movement.

Key-bindings:
- `CTRL`+`O`: open a circuit schematic file.
- `CTRL`+`S`: save circuit schematic to current file.
- `CTRL`+`SHIFT`+`S`: save circuit schematic to a different file.
- `CTRL`+`T`: run the type checker to show traces colored depending on data type. If the type check fails, an error message is displayed at the bottom of the screen and the problematic circuit block is selected.
- `CTRL`+`H`: reload external declarations specified in a `.c` file with the same name as the current circuit schematic.
- `CTRL`+`SHIFT`+`H`: reload external declarations without preprocessing the `.c` file through cpp.
- `CTRL`+`M`: run the compiler, creating a LLVM_IR assembly file in same place as the circuit schematic with `.dfc` replaced by `.ll`.
- `CTRL`+`SHIFT`+`M`: compile with explicit variable assignments and labels included.
- `CTRL`+`D`: remove all orphaned traces.
- `DELETE`: delete the selected circuit block.

In text editing:
- `<-`, `->`: move cursor left / right
- `HOME`, `END`: move cursor to start / end
- `SHIFT` + (`<-`, `->`, `HOME`, `END`): select text
- `CTRL`+`A`: select all
- `CTRL`+`C`: copy selected text to clip-board
- `CTRL`+`V`: insert clip-board at cursor
- `CTRL`+`X`: cut selected text and store it in clip-board
- `BACKSPACE`, `DELETE`: delete selected text or character left/right to cursor.

Note: Editing a block's text may change its size but the I/O pins will stay at their old position unless you move the block afterwards.

## Compiled Programs
The compiled program can be executed from command line using the LLVM interpreter like so `lli test.ll ...`, with optional arguments passed to the program.

This will call the program's main function that outputs the integer number of command-line arguments followed by a pointer to array of pointer to array of byte containing the arguments as null-terminated strings.

For more info about the language see the [documentation](doc/signals.md)
