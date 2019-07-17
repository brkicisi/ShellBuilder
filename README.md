<!-- markdownlint-disable MD010 -->
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

### iii_dir, ooc_dir & out_dir

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

*Note*: You cannot use either of these methods within header.

**Important**: None of these three directories must be specified in the xml header.

The *iii_dir* will automatically be created in your pwd as `pwd/.iii` if not specified. The others are simply shortcuts for the user when inputting in the xml file (they will be created if specified but don't exist yet). In fact, if you decided to use the *ooc_dir* for output files and the out_dir for ooc modules, ShellBuilder would have no issue with that. Nor would it complain if you specified an ooc dcp or output file relative to the *iii_dir*.

### XML

ShellBuilder calls on Java librarys (JDOM - [org.w3c.dom](https://docs.oracle.com/javase/8/docs/api/index.html?org/w3c/dom/package-summary.html) & [javax.xml.parsers](https://docs.oracle.com/javase/8/docs/api/index.html?javax/xml/parsers/package-summary.html)) to parse the input XML file.

XML must all be enclosed in a top level root. I have used `<root>` in the example below, but ShellBuilder never checks this tag.

#### Tags

The following are the tags that ShellBuilder checks. If tags are repeated within the same parent the first instance is used and any others are ignored.

The exception to the above rule is `<inst>` which will be repeated many times (once for each instruction instance).

<!-- markdownlint-disable MD033 -->
<table><!--  <table style="width:100%"> -->
	<tr>
		<th>Index</th>
    	<th>Tag</th>
	    <th>Children</th>
	    <th>Attributes</th>
	    <th>Description</th>
	</tr>
	<tr>
		<td>1</td>
		<td>header</td>
		<td>1<strong>.</strong>?</td>
		<td></td>
		<td>Parent to metadata which is common for the whole build.</td>
  	</tr>
  	<tr>
		<td>1.1</td>
    	<td>iii_dir</td>
		<td></td>
		<td></td>
		<td>Working directory to save intermediate designs as well as temporary files.</td>
  	</tr>
	<tr>
		<td>1.2</td>
    	<td>ooc_dir</td>
		<td></td>
		<td></td>
		<td>Directory containing ooc dcps.</td>
  	</tr>
	<tr>
		<td>1.3</td>
    	<td>out_dir</td>
		<td></td>
		<td></td>
		<td>Output directory.</td>
  	</tr>
	<tr>
		<td>1.4</td>
    	<td>refresh</td>
		<td></td>
		<td></td>
		<td>Place and route all modules ignoring and overwriting cached results.</td>
  	</tr>
	<tr>
		<td>1.5</td>
    	<td>module_name</td>
		<td></td>
		<td></td>
		<td>Sets name of module to this if initializing a new design. Cannot contain spaces (will be replaced with underscores). Has no effect if starting with a base design.</td>
  	</tr>
	<tr>
		<td>2</td>
    	<td>inst</td>
		<td>2<strong>.</strong>?</td>
		<td>type</td>
		<td>Instance of an instruction of the given type.</td>
  	</tr>
	<tr>
		<td>2.1</td>
    	<td>dcp</td>
		<td></td>
		<td>loc</td>
		<td>Specify location of an input (merge) or output (write) dcp file.</td>
  	</tr>
	<tr>
		<td>2.2</td>
    	<td>pblock</td>
		<td></td>
		<td></td>
		<td>String representing pblock to merge current dcp into.</td>
  	</tr>
	<tr>
		<td>2.3</td>
    	<td>iname</td>
		<td></td>
		<td></td>
		<td>Name to give this instance of the dcp module.</td>
  	</tr>
	<tr>
		<td>2.4</td>
    	<td>force</td>
		<td></td>
		<td></td>
		<td>Force overwrite of file with this name for this write only.</td>
  	</tr>
	<tr>
		<td>2.5</td>
    	<td>refresh</td>
		<td></td>
		<td></td>
		<td>Place and route this module ignoring and overwriting cached results.</td>
  	</tr>
	<tr>
		<td>2.6</td>
    	<td>hand_placer</td>
		<td></td>
		<td></td>
		<td>Open RapidWright's HandPlacer to allow user to interactively place this module.</td>
  	</tr>
</table>
<table>
	<tr>
		<th>Attribute</th>
		<th>Recognized Values</th>
		<th>Description</th>
	</tr>
	<tr>
    	<td>loc</td>
		<td>iii, ooc, out</td>
		<td>Specify root to resolve filename agianst.</td>
  	</tr>
	<tr>
    	<td>type</td>
		<td>merge, write, init</td>
		<td>Type of operation to perform. Merge adds given dcp to design inside the given pblock. Write saves the current state to a dcp. Init initializes other designs to merge with given base design. Init must only appear in first <em>inst</em> tag.</td>
  	</tr>
</table>
<!-- markdownlint-enable MD033 -->

You can insert comments basically anywhere just as in standard xml.

```xml
<!-- This is an xml comment. -->
```

Here is an example.

```xml
<!-- 
<?xml version = "1.0"?>
I don't know if the Java library checks for xml version.
It worked with it either included or not.
-->
<root>
  <header>
    <name>New-Name</name>
    <iii_dir>/absolute/path/to/iii_dir</iii_dir>
    <ooc_dir>relative/path/from/pwd/to/ooc_dir</ooc_dir>
    <out_dir></out_dir><!-- the output directory is pwd -->
    <!-- Note: unrecognized_token is not checked for -->
    <unrecognized_token>Unrecognized tokens are ignored.</unrecognized_token>
    <iii_dir>Repeated tokens are ignored.</iii_dir>
    <refresh></refresh>
  </header>
  <inst type = "inst">
    <dcp>your_base_design.dcp</dcp>
  </inst>
  <inst type = "merge">
    <dcp loc = "iii">relative/path/from/iii_dir/ooc_checkpoint_1.dcp</dcp>
    <pblock>SLICE_X0Y0:SLICE_X3Y5</pblock>
  </inst>
  <inst type = "write">
    <force></force>
  </inst>
  <inst type = "merge">
    <dcp loc = "ooc">../../other/dir/ooc_checkpoint_2.dcp</dcp>
    <pblock>SLICE_X0Y6:SLICE_X3Y11</pblock>
	<hand_placer></hand_placer>
  </inst>
  <inst type = "merge">
    <dcp>path/from/pwd/to/dcp/ooc_checkpoint_3.dcp</dcp>
    <pblock>SLICE_X0Y20:SLICE_X7Y28</pblock>
  </inst>
  <inst type = "merge">
    <dcp>#out/path/relative/to/out_dir/ooc_checkpoint_3.dcp</dcp>
	<refresh></refresh>
    <pblock>SLICE_X0Y20:SLICE_X7Y28</pblock>
  </inst>
  <inst type = "write">
    <dcp loc = "out">final.dcp</dcp>
  </inst>
</root>
```
