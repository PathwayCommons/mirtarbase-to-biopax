package tw.edu.nctu.mbc.mirtarbase;

import tw.edu.nctu.mbc.mirtarbase.converter.Converter;
import tw.edu.nctu.mbc.mirtarbase.converter.MirtarbaseConverter;
import tw.edu.nctu.mbc.mirtarbase.converter.MirbaseConverter;
import org.apache.commons.cli.*;
import org.biopax.paxtools.controller.Merger;
import org.biopax.paxtools.controller.ModelUtils;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.Rna;
import org.biopax.paxtools.model.level3.RnaReference;
import org.biopax.paxtools.model.level3.UnificationXref;
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
            .addOption("m", "mirbase-aliases", true, "miRNA aliases from mirBase (txt) [optional]")
            .addOption("t", "mirtarbase-targets", true, "miRTarBase curated targets (XLS) [optional]")
            .addOption("o", "output", true, "Output file (BioPAX) [required]")
            .addOption("r", "remove-tangling", false, "Removed tangling Rna objects [optional]")
            .addOption("p", "organism-pathway", false, "Generate a large 'pathway' element to group each organism interactions [optional]");

        try {
            CommandLine commandLine = clParser.parse(gnuOptions, args);

            // DrugBank file and output file name are required!
            if(!commandLine.hasOption("o")) {
                HelpFormatter helpFormatter = new HelpFormatter();
                helpFormatter.printHelp(helpText, gnuOptions);
                System.exit(-1);
            }

            // Memory efficiency fix for huge BioPAX models
            BPCollections.I.setProvider(new TProvider());
            SimpleIOHandler simpleIOHandler = new SimpleIOHandler();
            Model finalModel = Converter.bioPAXFactory.createModel();
            Merger merger = new Merger(simpleIOHandler.getEditorMap());

            if(commandLine.hasOption("m")) {
                log.info("Found option 'm'. Will convert mirBase aliases.");
                String aliasFile = commandLine.getOptionValue("m");
                MirbaseConverter mirbaseConverter = new MirbaseConverter();
                log.info("mirBase file: " + aliasFile);
                FileInputStream fileStream = new FileInputStream(aliasFile);
                Model mirModel = mirbaseConverter.convert(fileStream);
                fileStream.close();
                merger.merge(finalModel, mirModel);
                log.info("Merged mirBase model into the final one.");
            }

            if(commandLine.hasOption("t")) {
                log.info("Found option 't'. Will convert mirTarBase.");
                String targetFile = commandLine.getOptionValue("t");
                MirtarbaseConverter mirtarbaseConverter = new MirtarbaseConverter();
                if(commandLine.hasOption("p"))
                    mirtarbaseConverter.setMakePathwayPerOrganism(true);
                log.info("MiRTarBase file: " + targetFile);
                FileInputStream fileStream = new FileInputStream(targetFile);
                Model targetsModel = mirtarbaseConverter.convert(fileStream);
                fileStream.close();
                merger.merge(finalModel, targetsModel);
                log.info("Merged miRTarBase model into the final one.");
            }

            if(commandLine.hasOption("r")) {
                log.info("Removing tangling Rna, RnaReference and UnificationXref classes...");
                int removedObjects = 0;
                removedObjects += ModelUtils.removeObjectsIfDangling(finalModel, Rna.class).size();
                removedObjects += ModelUtils.removeObjectsIfDangling(finalModel, RnaReference.class).size();
                removedObjects += ModelUtils.removeObjectsIfDangling(finalModel, UnificationXref.class).size();
                log.info("Done removing: " + removedObjects + " objects.");
            }

            // set default xml base
            finalModel.setXmlBase(Converter.sharedXMLBase);
            String outputFile = commandLine.getOptionValue("o");
            log.info("Conversions are done. Now writing the final model to the file: " + outputFile);
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            simpleIOHandler.convertToOWL(finalModel, fileOutputStream);
//            fileOutputStream.close(); //not needed

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
