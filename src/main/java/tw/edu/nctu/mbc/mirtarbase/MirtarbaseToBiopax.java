package tw.edu.nctu.mbc.mirtarbase;

import tw.edu.nctu.mbc.mirtarbase.converter.MirtarbaseToBiopaxConverter;
import org.apache.commons.cli.*;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.trove.TProvider;
import org.biopax.paxtools.util.BPCollections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class MirtarbaseToBiopax {
    private static Logger log = LoggerFactory.getLogger(MirtarbaseToBiopax.class);
    private static final String helpText = MirtarbaseToBiopax.class.getSimpleName();

    public static void main( String[] args ) throws JAXBException {
        final CommandLineParser clParser = new GnuParser();
        Options gnuOptions = new Options();
        gnuOptions
            .addOption("i", "input", true, "input: MTI.xsl(x) file from miRTarBase [required]")
            .addOption("o", "output", true, "output: (BioPAX) file name [required]")
            .addOption("m", "mirbase-aliases", true, "miRNA aliases from mirBase (txt) [optional; use the embedded aliases.txt by default]")
            .addOption("s", "mirbase-organisms", true, "miRNA organisms from mirBase (txt) [optional]")
            .addOption("p", "organism-pathway", false, "Generate a large 'pathway' to group target species interactions [optional]");

        try {
            CommandLine commandLine = clParser.parse(gnuOptions, args);

            // input and output files are required!
            if(!commandLine.hasOption("o") || !commandLine.hasOption("i")) {
                HelpFormatter helpFormatter = new HelpFormatter();
                helpFormatter.printHelp(helpText, gnuOptions);
                System.exit(-1);
            }

            // Memory efficiency fix for huge BioPAX models (enable trove collections)
            BPCollections.I.setProvider(new TProvider());

            FileInputStream aliasesStream = null;
            if(commandLine.hasOption("m")) {
                String f = commandLine.getOptionValue("m");
                log.info("Using mirBase aliases file: " + f);
                aliasesStream = new FileInputStream(f);
            }

            FileInputStream organismsStream = null;
            if(commandLine.hasOption("s")) {
                String f = commandLine.getOptionValue("s");
                log.info("Using mirBase organisms file: " + f);
                organismsStream = new FileInputStream(f);
            }

            final String mtiFile = commandLine.getOptionValue("i");
            log.info("MiRTarBase input: " + mtiFile);

            // create, init the converter; run...
            MirtarbaseToBiopaxConverter converter = new MirtarbaseToBiopaxConverter();
            converter.setXmlBase("http://mirtarbase.mbc.nctu.edu.tw/#");
            if(commandLine.hasOption("p"))
                converter.setMakePathwayPerOrganism(true);
            // do convert
            Model model = converter.convert(new FileInputStream(mtiFile), aliasesStream, organismsStream);

            final String outputFile = commandLine.getOptionValue("o");
            log.info("Writing the BioPAX model to: " + outputFile);
            (new SimpleIOHandler()).convertToOWL(model, new FileOutputStream(outputFile));

            log.info("All done.");
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(helpText, gnuOptions);
            System.exit(-1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
