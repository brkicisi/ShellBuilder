# Shell Builder

Builds a shell.

Main worker is ShellBuilder.java

## Jars and Data

This git repository contains a submodule of RapidWright. However, this submodule does not contain the required jar files.

To fix this,

1. Download RapidWright jars and data from the [Xilinx Github](https://github.com/Xilinx/RapidWright/releases).

   - Skip this step if you already have the correct jars on your computer.
   - Under version v2018.3.3-beta (6d426bd) download `rapidwright_jars.zip`.
   - Extract zip.

1. Link jars to project.

   - Open `ShellBuilder/.classpath`.
   - Replace filepath before `jars` directories with path to jars folder on your computer.

There seems to be something similar to this in `RapidWright/.travis.yml`.

Note: This project doesn't know where data is.

## ShellBuilder

This should perform the main workflow of this project.
