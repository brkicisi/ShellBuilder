<!-- markdownlint-disable MD010 -->
# Shell Builder

Builds a shell.

Main worker is main.top.ShellBuilder

More documentation in [documentation.md](documentation.md).

## Jars and Data

This git repository contains a submodule of RapidWright. However, this submodule does not contain the required jar files.

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

This class should perform the main workflow of this project.

### XML

ShellBuilder calls on Java librarys (JDOM - [org.w3c.dom](https://docs.oracle.com/javase/8/docs/api/index.html?org/w3c/dom/package-summary.html) & [javax.xml.parsers](https://docs.oracle.com/javase/8/docs/api/index.html?javax/xml/parsers/package-summary.html)) to parse the input XML file.

XML must all be enclosed in a top level root. I have used `<root>` in the example below, but ShellBuilder never checks this tag.

#### Tags

The following are the tags that ShellBuilder checks. If tags are repeated within the same parent the first instance is used and any others are ignored.

The exception to the above rule is `<inst>` which will be repeated many times (once for each instruction instance).

| Index | Tag           | Children  | Attributes | Description                                                                                                                                                                       |
| :---- | :------------ | :-------- | :--------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1     | header        | 1.?       |            | Parent to metadata which is common for the whole build.                                                                                                                           |
| 1.1   | iii_dir       |           |            | Working directory to save intermediate designs as well as temporary files.                                                                                                        |
| 1.2   | ooc_dir       |           |            | Directory containing ooc dcps.                                                                                                                                                    |
| 1.3   | out_dir       |           |            | Output directory.                                                                                                                                                                 |
| 1.4   | initial       |           | loc (opt)  | Use this dcp as the base design to the Merger. Add all descendant modules to this design.                                                                                         |
| 1.5   | synth         |           | loc (opt)  | Use this dcp as a template to copy nets from to connect the modules in this level of hierarchy (and descendant levels unless alternative synth specified).                        |
| 1.6   | module_name   |           |            | Sets name of hierarchial module constructed by the build instr this header is part of (spaces will be replaced with underscores).                                                 |
| 1.7   | refresh       |           |            | Place and route all descendant modules ignoring and overwriting cached results.                                                                                                   |
| 1.8   | hand_placer   |           |            | Open RapidWright's HandPlacer to allow user to interactively place all descendant modules in this build. To finish and accept HandPlacer placement close it using the 'X' button. |
| 1.9   | buffer_inputs |           |            | Indicates to ShellBuilder that this build should be a normal dcp (not an out of context dcp which is default).                                                                    |
| 1.10  | proj          |           | loc (opt)  | Use this project file to write constraints for each cell.                                                                                                                         |
|       |               |           |            |                                                                                                                                                                                   |
| 2     | inst          | 1, 2, 2.? | type (req) | Instance of an instruction of the given type.                                                                                                                                     |
| 2.1   | dcp           |           | loc (opt)  | Specify location of an input (merge) or output (write) dcp file.                                                                                                                  |
| 2.2   | pblock        |           |            | String representing pblock to place and route current design into. Space separated list of pblock ranges.                                                                         |
| 2.3   | iname         |           |            | Name to give this instance of the design module.                                                                                                                                  |
| 2.4   | force         |           |            | Force overwrite of file with this name for this write only.                                                                                                                       |
| 2.5   | refresh       |           |            | Place and route this module ignoring and overwriting cached results.                                                                                                              |
| 2.6   | hand_placer   |           |            | Open RapidWright's HandPlacer to allow user to interactively place this module. To finish and accept HandPlacer placement close it using the 'X' button.                          |
| 2.7   | only_wires    |           |            | Indicates that this module contains only nets, pins and ports (thus can't be placed & routed ooc). Copy it from design in 1.5.                                                    |

Note: (req) = required, (opt) = optional. If required, the parser will error if not included.

| Attribute | Recognized Values   | Description                                                                                                                                                                                    |
| :-------- | :------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| loc       | iii, ooc, out       | Specify root to resolve filename agianst.                                                                                                                                                      |
| type      | merge, write, build | Type of operation to perform. Merge adds given dcp to design inside the given pblock. Build constructs a module from its descendant merges and builds. Write saves the current state to a dcp. |

You can insert comments basically anywhere just as in standard xml.

```xml
<!-- This is an xml comment. -->
```

##### iii_dir, ooc_dir & out_dir

The *iii_dir* is the working directory to save intermediate designs as well as temporary files (such as tcl scripts).

The *ooc_dir* is the directory where your out of context dcps are located.

The *out_dir* is the output directory.

These are defined in the xml header using their name as the [tag](#Tags). If defined, they may then be used when specifying files in *inst*. Either,

1. Specify *loc* attribute as `iii`, `ooc` or `out`.

	```xml
	<dcp loc = "iii">relative/path/to/iii_dir/checkpoint.dcp</dcp>
	```

1. Use ShellBuilder escape sequences (`#iii/`, `#ooc/`, `#out/`).

	```xml
	<dcp>#iii/relative/path/to/iii_dir/checkpoint.dcp</dcp>
	```

*Note*: You can use either of these methods only when the tag has attribute *loc*.

**Important**: None of these three directories must be specified in the xml header.

The *iii_dir* will automatically be created in your pwd as `pwd/.iii` if not specified. The others are simply shortcuts for the user when inputting in the xml file (they will be created if specified but don't exist yet). In fact, if you decided to use the *ooc_dir* for output files and the out_dir for ooc modules, ShellBuilder would have no issue with that. Nor would it complain if you specified an ooc dcp or output file relative to the *iii_dir*.

### NOTES

if `only_wires` and `dcp` specified. Then use dcp as synth_1.

##### An example

```xml
<!-- 
<?xml version = "1.0"?>
I don't know if the Java library checks for xml version.
It worked with it either included or not.
-->
<root>
    <header>
        <module_name>wrapper</module_name>
        <iii_dir>/absolute/path/to/iii_dir</iii_dir>
        <ooc_dir>relative/path/from/pwd/to/ooc_dir</ooc_dir>
        <out_dir></out_dir><!-- the output directory is pwd -->
        <!-- Note: unrecognized_token is not checked for -->
        <unrecognized_token>Unrecognized tokens are ignored.</unrecognized_token>
        <iii_dir>Repeated tokens are ignored.</iii_dir>
        <synth loc = "ooc">synth_1/your_top_level_synth_design.dcp</dcp>
        <hand_placer/>
    </header>
    <inst type = "merge">
        <dcp loc = "iii">relative/path/from/iii_dir/ooc_checkpoint_1.dcp</dcp>
        <pblock>SLICE_X0Y0:SLICE_X3Y5</pblock>
    </inst>
    <inst type = "write">
        <dcp>intermediate.dcp</dcp>
        <force/>
    </inst>
    <inst = "build">
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

java.lang.UnsupportedOperationException at ILAInserter#244.

```java
244: constraints.add(c);
```

#### Changes

```java
232: -  List<String> constraints = original.getXDCConstraints(ConstraintGroup.NORMAL);
232: +  List<String> constraints = new ArrayList<>(original.getXDCConstraints(ConstraintGroup.NORMAL));
```
