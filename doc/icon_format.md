# The block icon format
The editor expects the icons of blocks to be in a special image format. This format is similar to bitmap but supports an alpha channel and is more optimized to be loaded as OpenGL texture. It also contains information about the block's pin layout.

## Image format
The file first encodes the image data in following way:

```
//little endian
2 bit = log2(bytesPerColor)
4 bit = colorFormat
1 bit = 0
1 bit = usePalette
8 bit = width - 1
8 bit = height - 1
if usePalette {
  8 bit = colorCount - 1
  colorCount * {
    bytesPerColor * 8 bit = colorData
  }
  height * {
    width * {
      ceil(log2(colorCount)) bit = colorIndex
    }
  }
  padToAlign(8 bit) = 0
} else {
  height * {
    width * {
      bytesPerColor * 8 bit = colorData
    }
  }
}
```
**Variables:**
- `width` and `height` can range from 1 pixel up to 256 pixels.
- `bytesPerColor` is a power of 2 ranging from 1 to 8 and represents the color resolution (8, 16, 32 or 64 bit).
- `colorFormat` specifies the format of `colorData`:
    - 0: red only (8, 16 or 32 bit)
    - 1: green only (8, 16 or 32 bit)
    - 2: blue only (8, 16 or 32 bit)
    - 3: alpha only (8, 16 or 32 bit)
    - 4: red, green (8+8 bit, 16+16 bit or 32+32 bit)
    - 5: blue, green, red (2+3+3 bit or 5+6+5 bit)
    - 5: red, green (24+8 bit or 32+32 float bit)
    - 6: blue, green, red, alpha (4+4+4+4 bit, 8+8+8+8 bit or 16+16+16+16 bit)
    - 7: blue, green, red, alpha (5+5+5+1 bit, 10+10+10+2 bit or 16+16+16+16 float bit)
- `usePalette`: 1 means a color palette is used, 0 means that `colorData` is stored in the pixel array directly.
- `colorCount` can range from 1 to 256 and specifies the size of the color palette.
- `colorIndex` can range from 0 to `colorCount - 1` and specifies an index into the color palette.

## Block pin layout
The image data above is followed by the block's pin layout:

```
//little endian
8 bit = pinCount - 1
pinCount * {
  8 bit = pinX
  8 bit = pinY
}
4 bit = textX
4 bit = textY
4 bit = textBaseLen
3 bit = 0
1 bit = hasText
```
**Variables:**
- `pinCount` can range from 1 up to 256. Pin 0 is output, the remaining pins are inputs.
- `pinX` and `pinY` specify the pin's position relative to the icon's upper left corner in steps of 4 pixels. A negative `pinX` specifies the position relative to the right edge instead.
- `hasText` = 1 if the block supports a text argument.
- `textX` and `textY` specify the text position relative to the icon's upper left corner in steps of 2 pixels.
- `textBaseLen` specifies the number of characters that can fit into the block without stretching it. Beyond that the icon will stretch in the middle by 4 pixels per character.
