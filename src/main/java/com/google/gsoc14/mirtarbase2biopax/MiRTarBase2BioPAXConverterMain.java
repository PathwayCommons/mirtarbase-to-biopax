package com.google.gsoc14.mirtarbase2biopax;

import com.google.gsoc14.mirtarbase2biopax.converter.MiRTarBaseConverter;
import com.google.gsoc14.mirtarbase2biopax.converter.MirBaseConverter;
import org.apache.commons.cli.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.controller.Merger;
import org.biopax.paxtools.controller.ModelUtils;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.Rna;
import org.biopax.paxtools.model.level3.RnaReference;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.trove.TProvider;
import org.biopax.paxtools.util.BPCollections;

import javax.xml.bind.JAXBException;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class MiRTarBase2BioPAXConverterMain {
    private static Log log = LogFactory.getLog(MiRTarBase2BioPAXConverterMain.class);
    private static final String helpText = MiRTarBase2BioPAXConverterMain.class.getSimpleName();

    public static void main( String[] args ) throws JAXBException {
        final CommandLineParser clParser = new GnuParser();
        Options gnuOptions = new Options();
        gnuOptions
                .addOption("m", "mirbase-aliases", true, "miRNA aliases from mirBase (txt) [optional]")
                .addOption("t", "mirtarbase-targets", true, "miRTarBase curated targets (XLS) [optional]")
                .addOption("o", "output", true, "Output file (BioPAX) [required]")
                .addOption("r", "remove-tangling", false, "Removed tangling Rna objects [optional]")
        ;

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
            BioPAXFactory defaultFactory = BioPAXLevel.L3.getDefaultFactory();
            Model finalModel = defaultFactory.createModel();
            Merger merger = new Merger(simpleIOHandler.getEditorMap());

            if(commandLine.hasOption("m")) {
                log.debug("Found option 'm'. Will convert mirBase aliases.");
                String aliasFile = commandLine.getOptionValue("m");
                MirBaseConverter mirBaseConverter = new MirBaseConverter();
                log.debug("mirBase file: " + aliasFile);
                FileInputStream fileStream = new FileInputStream(aliasFile);
                Model mirModel = mirBaseConverter.convert(fileStream);
                fileStream.close();
                merger.merge(finalModel, mirModel);
                log.debug("Merged mirBase model into the final one.");
            }

            if(commandLine.hasOption("t")) {
                log.debug("Found option 't'. Will convert mirTarBase.");
                String targetFile = commandLine.getOptionValue("t");
                MiRTarBaseConverter miRTarBaseConverter = new MiRTarBaseConverter();
                log.debug("MiRTarBase file: " + targetFile);
                FileInputStream fileStream = new FileInputStream(targetFile);
                Model targetsModel = miRTarBaseConverter.convert(fileStream);
                fileStream.close();
                merger.merge(finalModel, targetsModel);
                log.debug("Merged miRTarBase model into the final one.");
            }

            if(commandLine.hasOption("r")) {
                log.debug("Removing tangling Rna, RnaReference and UnificationXref classes...");
                int removedObjects = 0;
                removedObjects += ModelUtils.removeObjectsIfDangling(finalModel, Rna.class).size();
                removedObjects += ModelUtils.removeObjectsIfDangling(finalModel, RnaReference.class).size();
                removedObjects += ModelUtils.removeObjectsIfDangling(finalModel, UnificationXref.class).size();
                log.debug("Done removing: " + removedObjects + " objects.");
            }

            String outputFile = commandLine.getOptionValue("o");
            log.debug("Conversions are done. Now writing the final model to the file: " + outputFile);
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            simpleIOHandler.convertToOWL(finalModel, fileOutputStream);
            fileOutputStream.close();

            log.debug("All done.");
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
