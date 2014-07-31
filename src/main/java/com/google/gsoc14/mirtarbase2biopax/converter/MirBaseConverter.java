package com.google.gsoc14.mirtarbase2biopax.converter;

import com.google.gsoc14.mirtarbase2biopax.util.MiRTarBaseUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.Rna;
import org.biopax.paxtools.model.level3.RnaReference;
import org.biopax.paxtools.model.level3.UnificationXref;

import java.io.InputStream;
import java.util.Scanner;
import java.util.UUID;

public class MirBaseConverter extends Converter {
    private static Log log = LogFactory.getLog(MirBaseConverter.class);

    private final String separator = "\t";
    private final String intraFieldSeparator = ";";

    @Override
    public Model convert(InputStream inputStream) throws Exception {
        Model model = createModel();
        MiRTarBaseUtils utils = getMiRTarBaseUtils();

        Scanner scanner = new Scanner(inputStream);
        while(scanner.hasNext()) {
            String line = scanner.nextLine();
            String tokens[] = line.split(separator);

            String id = tokens[0].trim();
            String names = tokens[1].trim();
            names = names.substring(0, names.length()-1); // Get rid of the last ; at the end
            String tnames[] = names.split(intraFieldSeparator);

            RnaReference rnaReference = create(RnaReference.class, id);
            model.add(rnaReference);

            UnificationXref unificationXref = create(UnificationXref.class, "uxref_" + id);
            model.add(unificationXref);
            unificationXref.setDb("miRBase Sequence");
            unificationXref.setId(id);
            rnaReference.addXref(unificationXref);
            rnaReference.setDisplayName(id);
            rnaReference.setStandardName(id);
            rnaReference.addName(id);

            for (String name : tnames) {
                String rdfId = utils.getMirnaRDF(name);
                Rna rna = (Rna) model.getByID(rdfId);
                if(rna == null) { // by default generate new one
                    rna = create(Rna.class, rdfId);
                } else { // or add it as a generic
                    Rna oldRna = rna;
                    rna = create(Rna.class, rdfId + "_" + UUID.randomUUID());
                    oldRna.addMemberPhysicalEntity(rna);
                }

                model.add(rna);
                rna.setEntityReference(rnaReference);

                rna.setDisplayName(name);
                rna.setStandardName(name);
                rna.addName(name);
                rna.addName(id);
                rnaReference.addName(name);
            }
        }

        log.debug(
                "Converted " + model.getObjects(Rna.class).size() + " miRNAs and "
                + model.getObjects(RnaReference.class).size() + " references to BioPAX."
        );

        return model;
    }
}
