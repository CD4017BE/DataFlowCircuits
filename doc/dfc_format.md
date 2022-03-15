# The data flow graph format
The data flow graphs that provide the implementation of macro blocks and programs are saved in the following file format:

```
//little endian
2 bit = posBytes - 1
2 bit = 0
4 bit = xCoordBits
varInt(8 bit) = defCount
defCount * {
  8 bit = ioCount
  8 bit = nameLen
  nameLen * {
    8 bit = utf8byte
  }
}
varInt(8 bit) = argCount
argCount * {
  8 bit = argLen
  argLen * {
    8 bit = utf8byte
  }
}
varInt(8 bit) = blockCount
blockCount * {
  ceil_log256(defCount) * 8 bit = def
  ceil_log256(argCount) * 8 bit = arg
  posBytes * 8 bit = blockPos
  ioCount * {
    ceil_log256(blockCount + 1) * 8 bit = source + 1
    varInt(8 bit) = traceCount
    traceCount * {
      posBytes * 8 bit = tracePos
    }
  }
}
```
**Variables:**
- `posBytes` [1..4] specifies the number of bytes used to encode pin and block coordinates.
- `xCoordBits` [0..15] specifies the number of bits used to encode the X coordinate,
and the remaining high bits out of `posBytes` are used for Y.
The editor's grid is limited to -16384 ... +16383 in both X and Y.
- `defCount` is the number of unique block types used:
    - `inCount` is the number of input pins that blocks of this type have (must match with definition).
    - `nameLen` and `utf8byte` encode the unique name (max 255 bytes) of the block type.
- `argCount` is the number of unique strings in the argument table:
    - `argLen` and `utf8byte` encode the argument string (max 255 bytes). An empty string is used for blocks that don't have an argument.
- `blockCount` is the number of blocks that make up the graph:
    - `def` can range from 0 to `defCount - 1` and specifies the index of the block's type.
    - `arg` can range from 0 to `argCount - 1` and specifies the index of the block's argument string.
    - `blockPos` encodes the block's position.
- For each input pin of the block:
    - `source` can range from -1 to `blockCount - 1` and specifies the index of the block whose output this input pin is connected to (-1 if none).
    - `traceCount` and `tracePos` encode the vertices of the signal trace that connects to this pin.
