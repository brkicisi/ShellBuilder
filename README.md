<!-- markdownlint-disable MD010 -->
# Shell Builder

Builds a shell.

Main worker is main.top.ShellBuilder

More documentation in [documentation.md](documentation.md).

## About

Author: Isidor Randall Brkic

Email: igi.brkic@mail.utoronto.ca

Supervisor: Prof. Paul Chow

Institution: University of Toronto

Date: Summer 2019

## Jars and Data

This git repository contains a submodule of RapidWright (make sure to clone the submodule when cloning this repository). However, this submodule does not contain the required jar files.

To fix this,

1. Download RapidWright jars and data from the [Xilinx Github](https://github.com/Xilinx/RapidWright/releases).

   - Skip this step if you already have the correct jars on your computer.
   - Under version v2019.1.1-beta download `rapidwright_jars.zip` and `rapidwright_data.zip`.
     - Note: I am not sure if the data zip is needed internally by RapidWright. I never reference it explicitly.
   - Extract zips.

1. Link jars to project.

   - Open `ShellBuilder/.classpath`.
   - Replace filepath before `jars` directories with path to jars folder on your computer.

There seems to be something similar to this in `RapidWright/.travis.yml`.

## ShellBuilder (main.top)

This class should perform the main workflow of this project (see [Code Workflow](documentation.md#code-workflow)).

### Command Line

The following is what worked for ILADebug, I have been using [VSCode](https://code.visualstudio.com/) so that I don't need to manage the details of compiling and running a set of Java files. You should be able to do it manually **similar** to what follows (I haven't actually run ShellBuilder from command line). Alternatively, VSCode is free and small and you can find instructions online for how to clone a git reposotory with submodules which you can then open with VSCode and use `.vscode/launch.json` to set run configurations and arguments.

ShellBuilder can be run like any other Java file. It requires that the RapidWright directory be in the class path as well as $CLASSPATH (which is set/appended to by running rapidwright.tcl).

From the directory containing [README.md](README.md):

compile: `javac /path/file.java`
run: `java -cp .:<RapidWright_dir>/RapidWright:$CLASSPATH ShellBuilder <args ...>`

The only mandatory argument to the java call is the path to the xml file to be run. Other arguments may be added as specified by 'help' (force, refresh, quiet, verbose, extra verbose).

Note: Print help using `-h` or `--help`.

### XML

ShellBuilder calls on Java librarys (JDOM - [org.w3c.dom](https://docs.oracle.com/javase/8/docs/api/index.html?org/w3c/dom/package-summary.html) & [javax.xml.parsers](https://docs.oracle.com/javase/8/docs/api/index.html?javax/xml/parsers/package-summary.html)) to parse the input XML file.

XML must all be enclosed in a top level root. I have used `<root>` in the example below, but ShellBuilder never checks this tag.

#### Tags

The following are the tags that ShellBuilder checks. If tags are repeated within the same parent the first instance is used and any others are ignored.

The exception to the above rule is `inst` which will be repeated many times (once for each instruction instance - ie. once for each cell instance being built).

| Index | Tag                  | Children     | Attributes   | Description                                                                                                                                                                       |
| :---- | :------------------- | :----------- | :----------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1     | `header`             | 1.?          |              | Parent to metadata which is common for all `inst`s and `template`s that are siblings to this `header`.                                                                            |
| 1.1   | `iii_dir`            |              |              | Working directory to save intermediate designs as well as temporary files.                                                                                                        |
| 1.2   | `ooc_dir`            |              |              | Directory containing ooc dcps.                                                                                                                                                    |
| 1.3   | `out_dir`            |              |              | Output directory.                                                                                                                                                                 |
| 1.4   | `initial`            |              | `loc` (opt)  | Use this dcp as the base design to the `Merger`. Add all descendant modules to this design.                                                                                       |
| 1.5   | `synth`              |              | `loc` (opt)  | Use this dcp as a template from which to copy nets when connecting the modules in this level of hierarchy (and descendant levels unless alternative `synth` specified).           |
| 1.6   | `module_name`        |              |              | Sets name of hierarchial module constructed by the build instruction this `header` is part of (spaces will be replaced with underscores).                                         |
| 1.7   | `refresh`            |              |              | Place and route all descendant modules ignoring and overwriting cached results.                                                                                                   |
| 1.8   | `hand_placer`        |              |              | Open RapidWright's HandPlacer to allow user to interactively place all descendant modules in this build. To finish and accept HandPlacer placement close it using the 'X' button. |
| 1.9   | `buffer_inputs`      |              |              | Indicates to ShellBuilder that this build should be a normal dcp (not an out of context dcp which is default).                                                                    |
| 1.10  | `proj`               |              | `loc` (opt)  | Use this project file to write constraints for each cell.                                                                                                                         |
|       |                      |              |              |                                                                                                                                                                                   |
| 2     | `inst`               | 1, 2, 2.?, 3 | `type` (req) | Instance of an instruction of the given type.                                                                                                                                     |
| 2.1   | `dcp`                |              | `loc` (opt)  | Specify location of an input (`type="merge"`) or output (`type="write"`) dcp file.                                                                                                |
| 2.2   | `pblock`             |              |              | String representing pblock to place and route current design into. Space separated list of pblock ranges.                                                                         |
| 2.3   | `iname`              |              |              | Name to give this instance of the design (`ModuleInst` name).                                                                                                                     |
| 2.4   | `force`              |              |              | Force overwrite of file specified by `dcp` with for this `write` only.                                                                                                            |
| 2.5   | `refresh`            |              |              | Place and route this module ignoring and overwriting cached results.                                                                                                              |
| 2.6   | `hand_placer`        |              |              | Open RapidWright's HandPlacer to allow user to interactively place this module. To finish and accept HandPlacer placement close it using the 'X' button.                          |
| 2.7   | `only_wires`         |              |              | Indicates that this module contains only nets, pins and ports (thus can't be placed & routed ooc). Copy it from design in 1.5.                                                    |
|       |                      |              |              |                                                                                                                                                                                   |
| 3     | `template`           | 1, 3.?       |              | Generate a template that can be filled in to build a project                                                                                                                      |
| 3.1   | `dcp`                |              | `loc` (opt)  | Specify location of the top level wrapper file. You will likely use this file in the generated template as an initial and/or synth file.                                          |
| 3.2   | `out`                |              | `loc` (opt)  | Specify the location to which the output template will be written.                                                                                                                |
| 3.3   | `include_primitives` |              |              | Include primitive cells in the template (default is false).                                                                                                                       |
| 3.4   | `force`              |              |              | Force overwrite of file specified by `out`.                                                                                                                                       |

Note: (req) = required, (opt) = optional. If required, the parser will error if not included.

| Attribute | Recognized Values         | Description                                                                                                                                                                                                                    |
| :-------- | :------------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `loc`     | `iii`, `ooc`, `out`       | Specify root to resolve filename agianst.                                                                                                                                                                                      |
| `type`    | `merge`, `write`, `build` | Type of operation to perform. `merge` adds given dcp to the design placed and routed inside the given pblock. `build` constructs a module from its descendant `merge`s and `build`s. `write` saves the current state to a dcp. |

You can insert comments basically anywhere just as in standard xml.

```xml
<!-- This is an xml comment. -->
<!--
This is a 
block xml comment.
-->
```

#### iii_dir, ooc_dir & out_dir

The `iii_dir` is the working directory to save intermediate designs as well as temporary files (such as tcl scripts).

The `ooc_dir` is the directory where your out of context dcps are located.

The `out_dir` is the output directory.

These are defined in the xml header using their name as the [tag](#Tags). If defined, they may then be used when specifying files in `inst`. Either,

1. Specify `loc` attribute as `iii`, `ooc` or `out`.

	```xml
	<dcp loc = "iii">relative/path/from/iii_dir/checkpoint.dcp</dcp>
	```

1. Use ShellBuilder escape sequences (`#iii/`, `#ooc/`, `#out/`).

	```xml
	<dcp>#iii/relative/path/from/iii_dir/checkpoint.dcp</dcp>
	```

*Note*: You can use either of these methods for any tag that has attribute `loc`.

**Important**: None of these three directories must be specified in the xml header.

The `iii_dir` will automatically be created in your pwd as `pwd/.iii` if not specified. The others are simply shortcuts for the user when inputting in the xml file (they will be created if specified but don't exist yet). In fact, if you decided to use the `ooc_dir` for output files and the out_dir for ooc modules, ShellBuilder would have no issue with that. Nor would it complain if you specified an ooc dcp or output file relative to the `iii_dir`.

#### An example

This example is to demonstrate the syntactical use of some of the tags. It is not intended to be a working example.

```xml
<!-- 
<?xml version = "1.0"?>
I don't know if the Java library checks for xml version.
It worked whether version was either included or not.
-->
<root>
    <header>
        <module_name>wrapper</module_name>
        <iii_dir>/absolute/path/to/iii_dir</iii_dir>
        <ooc_dir>relative/path/from/pwd/to/ooc_dir</ooc_dir>
        <out_dir></out_dir><!-- the output directory is pwd -->
        <!-- Note: unrecognized_tag is not checked for -->
        <unrecognized_tag>Unrecognized tags are ignored.</unrecognized_tag>
        <iii_dir>Repeated tags are ignored.</iii_dir>
        <synth loc = "ooc">synth_1/your_top_level_synth_design.dcp</synth>
        <initial loc = "ooc">synth_1/your_top_level_synth_design.dcp</initial>
        <hand_placer/> <!-- Note: this is a shorthand equivalant to <hand_placer></hand_placer> -->
    </header>
    <inst type = "merge">
        <dcp loc = "iii">relative/path/from/iii_dir/ooc_checkpoint_1.dcp</dcp>
        <pblock>SLICE_X0Y0:SLICE_X3Y5</pblock>
    </inst>
    <inst type = "write">
        <dcp>intermediate.dcp</dcp> <!-- pwd/intermediate.dcp -->
        <force/>
    </inst>
    <inst = "build">
        <iname>sub_module_i</iname>
        <header>
            <module_name>sub_module</module_name>
            <refresh/>
        </header>
        <inst type = "merge">
            <dcp loc = "ooc">../../other/dir/ooc_checkpoint_2.dcp</dcp>
            <pblock>SLICE_X0Y6:SLICE_X3Y11</pblock>
            <hand_placer/>
        </inst>
        <inst type = "merge">
            <dcp>path/from/pwd/to/dcp/ooc_checkpoint_3.dcp</dcp>
            <pblock>SLICE_X0Y20:SLICE_X7Y28</pblock>
        </inst>
    </inst>
    <inst type = "merge">
        <dcp>#out/path/relative/to/out_dir/ooc_checkpoint_3.dcp</dcp>
        <refresh/>
        <pblock>SLICE_X0Y20:SLICE_X7Y28</pblock>
    </inst>
    <inst type = "write">
        <dcp loc = "out">final.dcp</dcp>
    </inst>
</root>
```

### Bugs and fixes

Bugs that have been seen previously with RapidWright.

#### java.lang.UnsupportedOperationException at ILAInserter#244

Source:

```java
244: constraints.add(c);
```

Fix:

```java
232: -  List<String> constraints = original.getXDCConstraints(ConstraintGroup.NORMAL);
232: +  List<String> constraints = new ArrayList<>(original.getXDCConstraints(ConstraintGroup.NORMAL));
```
