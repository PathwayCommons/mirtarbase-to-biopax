# mirtarbase-to-biopax
Originated from https://bitbucket.org/armish/gsoc14 and will continue here (ToDo).

## MiRTarBase to BioPAX Level3 data converter.
This data resource is manually curated and it contains validated 
miRNA-target interactions. These interactions can easily be converted 
to BioPAX Level3 format.

### Data source [now it looks outdated/discontinued; format is now different too]
- **Home page**: http://mirtarbase.mbc.nctu.edu.tw (also, - aliases.txt from http://www.mirbase.org)
- **Type**: microRNA-target interactions
- **Format**: XLS/TSV
- **License**: Free for academic use

### Implementation details
The database provides downloadable miRNA-target relationships in Excel format.
For each interaction, we have knowledge about the miRNA, the target gene, 
the organism and the coresponding publication that describes the interaction.
Each miRNA has a unique MiRTarBase ID, but these IDs are registered in 
the Miriam database. This creates a problem for us when creating miRNA 
references with `UnificationXref`s to them. To overcome this problem, 
we also import [miRBase](http://www.mirbase.org/) aliases which provides 
official unique IDs for given miRNA names.

We encode miRNA-target relationships with `TemplateReaction`s through 
which the corresponding gene product is produced and the reaction is 
inhibitied by the corresponding miRNA. miRNAs are represented by `Rna` 
objects, where they have `RelationshipXref`s to MiRTarBase and 
`UnificationXref`s to miRBase. If a miRNA name is associated with multiple 
unique miRNAs, then we capture this information via adding different-named 
miRNAs as a `MemberPhysicalEntity` to the original miRBase reference.

We encapsulate each interaction in an organism-specific pathway so that 
users can only work with pathways that are of interest to them, 
for example human miRNA-targets. 

The image below shows a partial `Homo sapiens` pathway:

![Example Homo sapiens pathway with miRNA-target interactions](https://bitbucket.org/armish/gsoc14/downloads/goal4_human_mirna_screenshot-20140731.jpg)

### Usage
Check out (git clone) and change to:

	$ cd mirtarbase-to-biopax

build with Maven:

	$ mvn clean package

This will create a single executable JAR file under the `target/` directory, 
with the following file name: `mirtarbase-to-biopax.jar`. Once you have 
the "fat" JAR file, you can try to run it without any command line options 
to see the help text:

	$ java -jar target/mirtarbase-to-biopax.jar
	usage: MirtarbaseToBiopax
	-o,--output <arg>               Output file (BioPAX) [required]
	...

For a conversion, one input data file is required, and two more are optional 
(e.g., if you'd like to import organisms and aliases mapping from a specific miRBase release):

1. MiRTarBase Excel file - either [full](http://mirtarbase.mbc.nctu.edu.tw/cache/download/6.1/miRTarBase_MTI.xlsx) 
or [partial](http://mirtarbase.mbc.nctu.edu.tw/cache/download/6.1/hsa_MTI.xlsx) (human);
2. miRBase aliases: ftp://mirbase.org/pub/mirbase/CURRENT/aliases.txt.gz (optional, - if you'd use the very latest data)
3. miRBase organisms: ftp://mirbase.org/pub/mirbase/CURRENT/organisms.txt.gz (optional)

Once downloaded and expanded, these can be converted to BioPAX using the 
following command:

	$ java -Xmx4g -jar mirtarbase-to-biopax.jar -m aliases.txt -s organisms.txt -i hsa_MTI.xlsx -o out.biopax.owl
