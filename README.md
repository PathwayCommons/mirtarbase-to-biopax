# mirtarbase-to-biopax
Originated from https://bitbucket.org/armish/gsoc14 and will continue here (ToDo).

## MiRTarBase to BioPAX Level3 data converter.
This data resource is manually curated and it contains validated miRNA-target interactions. These interactions can easily be converted to BioPAX format.

### Data source
- **Home page**: [http://mirtarbase.mbc.nctu.edu.tw](http://mirtarbase.mbc.nctu.edu.tw)
- **Type**: microRNA-target interactions
- **Format**: XLS/TSV
- **License**: Free for academic use

### Implementation details
The database provides downloadable miRNA-target relationships in Excel format.
For each interaction, we have knowledge about the miRNA, the target gene, the organism and the coresponding publication that describes the interaction.
Each miRNA has a unique MiRTarBase ID, but these IDs are registered in the Miriam database.
This creates a problem for us when creating miRNA references with `UnificationXref`s to them.
To overcome this problem, we also import [miRBase](http://www.mirbase.org/) aliases which provides official unique IDs for given miRNA names.

We encode miRNA-target relationships with `TemplateReaction`s through which the corresponding gene product is produced
and the reaction is inhibitied by the corresponding miRNA.
miRNAs are represented by `Rna` objects, where they have `RelationshipXref`s to MiRTarBase and `UnificationXref`s to miRBase.
If a miRNA name is associated with multiple unique miRNAs,
then we capture this information via adding different-named miRNAs as a `MemberPhysicalEntity` to the original miRBase reference.

We encapsulate each interaction in an organism-specific pathway so that users can only work with pathways that are of interest to them, for example human miRNA-targets. 

The image below shows a partial `Homo sapiens` pathway:

![Example Homo sapiens pathway with miRNA-target interactions](https://bitbucket.org/armish/gsoc14/downloads/goal4_human_mirna_screenshot-20140731.jpg)

### Usage
Check out (git clone) and change to:

	$ cd mirtarbase-to-biopax

build with Maven:

	$ mvn clean install assembly:single

This will create a single executable JAR file under the `target/` directory, with the following file name: `mirtarbase2biopax-{version}-single.jar`.
You can also download this file under the downloads, e.g. [goal4_mirtarbase2biopax-1.0-SNAPSHOT-single.jar](https://bitbucket.org/armish/gsoc14/downloads/goal4_mirtarbase2biopax-1.0-SNAPSHOT-single.jar).
Once you have the single JAR file, you can try to run without any command line options to see the help text:

	$ java -jar goal4_mirtarbase2biopax-1.0-SNAPSHOT-single.jar
	usage: MiRTarBase2BioPAXConverterMain
	-m,--mirbase-aliases <arg>      miRNA aliases from mirBase (txt)
 	                                [optional]
	-o,--output <arg>               Output file (BioPAX) [required]
	-r,--remove-tangling            Removed tangling Rna objects [optional]
	-t,--mirtarbase-targets <arg>   miRTarBase curated targets (XLS)
	                                [optional]

For a full conversion, two input files are required:

1. MiRTarBase Excel file (either [full](https://bitbucket.org/armish/gsoc14/downloads/goal4_mirtarbase-all_MTI-20140731.xlsx.gz) or [partial](https://bitbucket.org/armish/gsoc14/downloads/goal4_mirtarbase-hsa_MTI-20140731.xlsx.gz), e.g. human)
2. miRBase aliases ([download](https://bitbucket.org/armish/gsoc14/downloads/goal4_mirbase_aliases-20140731.txt.gz))

both of which can be downloaded from the repository. 

Once downloaded and gunzipped, these can be coverted into BioPAX via the following command:

	$ java -jar goal4_mirtarbase2biopax-1.0-SNAPSHOT-single.jar -m goal4_mirbase_aliases-20140731.txt -t goal4_mirtarbase-all_MTI-20140731.xlsx -r -o goal4_output_all_mirna-20140731.owl

The `-r` switch is optional, but helps reduce the size of the final model.
When provided, this makes sure the final model does not included `Rna` and `RnaReference` objects that do not participate in a reaction.

You can download the miRNA-target relationships as a BioPAX file either for all organism ([goal4_output_all_mirna-20140731.owl.gz](https://bitbucket.org/armish/gsoc14/downloads/goal4_output_all_mirna-20140731.owl.gz)) or only human ([goal4_output_human_mirna-20140731.owl.gz](https://bitbucket.org/armish/gsoc14/downloads/goal4_output_human_mirna-20140731.owl.gz))

### Validation results
The fully converted model is too big to be validated via the web,
but the validation results for the human interactions are available under the Downloads: [goal4_mirtarbase_validationResults_20140731.zip](https://bitbucket.org/armish/gsoc14/downloads/goal4_mirtarbase_validationResults_20140731.zip).

We don't have any validation errors, but since we are identifying `Protein`s with `Entrez Gene ID`s and there can be multiple proteins associated with a single gene.
This is not the best practice and this is why we have *denied xrefs* in the report.
To fix this, we can bring in new background files, for example Gene -> Uniprot mappings, into the conversion tool,
but since Pathway Commons already has these utilities in place,
I think we can leave this mapping issue to the PC integration pipeline for now.
