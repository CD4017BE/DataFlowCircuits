# The DFC file format
Data Flow Circuit macro blocks and programs are saved as `.dfc` files that start with the following header. All numbers are unsigned and encoded in Little Endian unless otherwise specified.

	//header (44 bytes)
	24 bit = "dfc"
	8 bit = versionNumber
	32 bit = interface
	32 bit = interfaceL
	32 bit = circuit
	32 bit = circuitL
	32 bit = layout
	32 bit = layoutL
	32 bit = icon
	32 bit = iconL
	32 bit = description
	32 bit = descriptionL

**Variables:**
- `versionNumber = 0` is the format version the file was saved in.
- `interface` and `interfaceL` specify the file region for the block's [interface declaration](#interface-declaration). (required)
- `circuit` and `circuitL` specify the file region for the block's [circuit implementation](#circuit-implementation). (optional)
- `layout` and `layoutL` specify the file region for the block's [circuit layout](#circuit-layout). (optional)
- `icon` and `iconL` specify the file region for the block's [icon](icon_format.md). (optional)
- `description` and `descriptionL` specify the file region for the block's [description](#description). (optional)

Each file region is specified by a byte offset relative to the start of the file and its length in bytes.
They can be stored in any order with any amount of padding in between that is ignored.
Some sections may be absent, in which case their offset and length are 0.

## Interface declaration
This sections defines the number of I/O pins and arguments as well as their names and their position on the icon.

	interface:
	8 bit = outCount
	8 bit = inCount
	1 bit = hasArgument
	7 bit = 0
	(outCount + inCount) * {
	  8 bit = pinX
	  8 bit = pinY
	}
	if hasArgument {
	  8 bit = argX
	  8 bit = argY
	  8 bit = argBaseLen
	}
	(outCount + inCount + hasArgument) * {
	  {8 bit = uft8byte while utf8byte != 0} = name
	}

**Variables:**
- `outCount = 1` is the number of output pins. *Reserved in case blocks with multiple outputs are supported in the future.*
- `inCount` is the number of input pins.
- `hasArgument = 1` if the block has a string argument, otherwise `hasArgument = 0`.
- `posX` and `pinY` are signed integers specifying the pin positions relative to the icon's upper left (if >= 0) or bottom right (if < 0) corner in steps of 4 pixels.
- `argX` and `argY` are unsigned integers specifying the argument text-field position relative to the icon's upper left corner in steps of 2 pixels.
- `argBaseLen` specifies the number of characters that can fit into the block without stretching it. Beyond that the icon will stretch in the middle by 4 pixels per character.
- `name` is the name for each I/O pin or argument, encoded as zero terminated UTF-8 string.

## Circuit implementation
This section describes the implementation logic of the block. For intrinsic blocks that are defined by the compiler itself, this section is absent and `circuit = 0`.

	circuit:
	16 bit = blockTypeCount
	blockTypeCount * {
	  8 bit = outCount
	  8 bit = inCount
	  1 bit = hasArgument
	  7 bit = 0
	  {8 bit = uft8byte while utf8byte != 0} = typeId
	}
	16 bit = argumentCount
	argumentCount * {
	  {8 bit = uft8byte while utf8byte != 0} = argString
	}
	16 bit = blockCount
	blockCount * {
	  16 bit = blockTypeIdx
	  inCount * {
	    16 bit = blockIdx + 1
	  }
	  if hasArgument {
	    16 bit = argIdx
	  }
	}

**Variables:**
- `blockTypeCount` is the size of the block type table.
- `outCount`, `inCount` and `hasArgument` specify the number of I/O pins and arguments for each block type. These should match with the [interface declaration](#interface-declaration) in the block type's .dfc file.
- `typeId` is the block types's identifier, encoded as zero terminated UTF-8 string.
- `argumentCount` is the size of the argument string table.
- `argString` is an argument string table entry, encoded as zero terminated UTF-8 string.
- `blockCount` is the total number of blocks in the graph.
- `blockTypeIdx` is an index into the block type table.
- `blockIdx` is the index of another block or -1 if the given input is not connected.
- `argIdx` is an index into the argument string table.

## Circuit layout
If the circuit implementation was created using the graph editor then this section contains the layout information, otherwise it may be absent and `layout = 0`.

	layout:
	blockCount * {
	  16 bit = blockX
	  16 bit = blockY
	  16 bit = outX
	  16 bit = outY
	  inCount * {
	    8 bit = traceCount - 1
	    traceCount * {
	      16 bit = traceX
	      16 bit = traceY
	    }
	  }
	}

**Variables:**
- `blockCount` and `inCount` are defined in the [implementation](#circuit-implementation) section and the block entries in this section correspond to the ones in implementation.
- `blockX` and `blockY` specify the grid position of each block.
- `outX` and `outY` specify the grid position of the block's output pin.
- `traceCount` is the number of trace nodes that form the connection to an input pin. It starts with the input pin itself and goes until the trace either merges into another one or reaches the output pin of the connected block.
- `traceX` and `traceY` specify the grid position of each trace node.

All grid positions are specified by a pair of signed integers in range `-16384` to `+16383`.

## Description
This section is optional and provides a textual description of the block for documentation purposes. If absent `description = 0`.

	description:
	{8 bit = uft8byte while utf8byte != 0} = shortDesc
	{8 bit = uft8byte while utf8byte != 0} = longDesc

**Variables:**
- `shortDesc` is the short description which briefly tells what the block is for.
- `longDesc` is the long description which is meant to explain the block's behavior and usage in detail.

Both strings are encoded as zero terminated UTF-8 strings.
