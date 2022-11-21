# The DFC file format
Data Flow Circuit macro blocks and programs are saved as `.dfc` files that start with the following magic identifier sequence:

	u8 = 'd'
	u8 = 'f'
	u8 = 'c'
	u8 = 'G'

Data types that appear in this file:
- `u8`: number in range [0..255] represented using 1 byte.
- `i16`: number in range [-32768..+32767] represented using 2 little-endian bytes.
- `varInt`: number in range [0..268435455] using 1..4 bytes represented in binary as
    - `0xxxxxxx` -> x
    - `1xxxxxxx varInt` -> x + varInt * 128
- `int(max)`: number in range [0..max] represented using ceil(log2(max + 1) / 8) little-endian bytes.

## Circuit implementation
The magic identifier sequence from before is followed by the circuit implementation that will be used by the compiler and type checker. This part only describes how the blocks are configured and connected.

	u8 = circuitFormatVersion
	varInt = moduleCount
	moduleCount * {
	  u8 = strLen
	  strLen * u8 = moduleName
	}
	varInt = blockTypeCount
	blockTypeCount * {
	  int(moduleCount) = moduleIndex + 1
	  u8 = outputCount
	  u8 = inputCount
	  u8 = argumentCount
	  u8 = strLen
	  strLen * u8 = blockTypeName
	}
	varInt = stringCount
	stringCount * {
	  varInt = strLen
	  strLen * u8 = string
	}
	varInt = blockCount
	blockCount * {
	  int(blockTypeCount - 1) = blockTypeIndex
	  blockType.inputCount * {
	    int(blockCount) = blockIndex + 1
	    u8 = outputIndex
	  }
	  blockType.argumentCount * {
	    int(stringCount - 1) = stringIndex
	  }
	}

**Variables:**
- `circuitFormatVersion = 0` is the format version the data was saved in.
- `moduleCount` defines an array of external modules from which blocks are used, where
    - `moduleName` is the modules' UTF-8 encoded import name as specified in the current module.
- `blockTypeCount` defines the array of all block types that appear in the circuit, where
    - `moduleIndex` specifies the module the block belongs to as module array index or -1 for the current module.
    - `blockTypeName` is the UTF-8 encoded name of the block as specified in the module above. 
    - `outputCount`, `inputCount`, `argumentCount` specify the number of inputs, outputs and arguments the block is supposed to have. This information is usually redundant but it ensures that the file remains readable in case the block's interface has been modified.
- `stringCount` and `string` define the array of all UTF-8 encoded strings that appear in block arguments.
- `blockCount` defines the array of blocks that make up the circuit, where
    - `blockTypeIndex` is the block's type.
    - `blockIndex` specifies the block connected to each input as block array index or -1 for not connected.
    - `outputIndex` specifies which output of the block given by `blockIndex` was connected (ignored if not connected).
    - `stringIndex` specifies each block argument as string array index.

## Graphical layout
The circuit implementation above is followed by information about how each block is placed in the editor and how the connecting traces are laid out. The compiler and type checker won't read this part of the file, it is only required for editing circuits with the graph editor.

	u8 = graphFormatVersion
	varInt = traceCount
	blockCount * {
	  i16 = blockPosX
	  i16 = blockPosY
	}
	traceCount * {
	  i16 = tracePosX
	  i16 = tracePosY
	  if not output {
	    int(traceCount) = sourceTraceIndex + 1
	  }
	}

**Variables:**
- `graphFormatVersion = 0` is the format version the data was saved in (independent of `circuitFormatVersion`).
- `blockCount` is defined in the [implementation](#circuit-implementation) section and the block entries in this section correspond to the ones in implementation.
- `blockPosX` and `blockPosY` specify the grid position of each block (upper left corner).
- `traceCount` is the total number of trace nodes in the circuit. This trace array is sorted so that it starts with all output pin traces in the order their blocks are defined, then all input pin traces in the order their blocks are defined, and finally all remaining intermediate traces in arbitrary order.
    - `tracePosX` and `tracePosY` specify the grid position of each trace node.
    - `sourceTraceIndex` specifies the source of each trace node as trace array index or -1 if no source connected. This entry is does not exist for `output` pin traces since these can't connect to a source.

All grid positions must be in range `-16384` to `+16383` due to rendering limitations.
