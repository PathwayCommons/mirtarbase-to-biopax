package tw.edu.nctu.mbc.mirtarbase.converter;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.biopax.paxtools.controller.*;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.model.level3.Process;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
//import java.util.UUID;

public class MirtarbaseToBiopaxConverter {
    private static Logger log = LoggerFactory.getLogger(MirtarbaseToBiopaxConverter.class);

    private static final String IDENTIFIERS_NS = "http://identifiers.org/";
    private static final String TAXONOMY_NS = IDENTIFIERS_NS + "taxonomy/";
    private static final String MIRT_NS = IDENTIFIERS_NS + "mirtarbase/";

    private Model model;
    private String xmlBase = "";
    private boolean makePathwayPerOrganism = false;

    private final Map<String,String> orgCodeToNameMap = new HashMap<String, String>();
    private final Map<String,String> orgCodeToTaxonMap = new HashMap<String, String>();
    private final Map<String,String> mirNameToIdMap = new HashMap<String, String>();
    private final Map<String,String> orgNameToCodeMap = new HashMap<String, String>();

    /**
     * Whether to generate such "pathways", one per species, that contain all the interactions.
     * @return true/false
     */
    public boolean isMakePathwayPerOrganism() {
        return makePathwayPerOrganism;
    }
    public void setMakePathwayPerOrganism(boolean makePathwayPerOrganism) {
        this.makePathwayPerOrganism = makePathwayPerOrganism;
    }

    public void setXmlBase(String xmlBase) {
        this.xmlBase = xmlBase;
        if(model != null)
            model.setXmlBase(xmlBase);
    }

    private <T extends BioPAXElement> T create(Class<T> aClass, String id) {
        return model.addNew(aClass, absoluteUri(id));
    }

    private <T extends BioPAXElement> T findById(String rdfId) {
        return (T) model.getByID(absoluteUri(rdfId));
    }

    private String absoluteUri(String rdfId) {
        return (rdfId.startsWith("http")) ? rdfId : xmlBase + rdfId;
    }

    private String orgCodeFromMir(String name) {
        return name.substring(0, name.indexOf("-"));
    }

    /**
     * Find or make a BioSource using the abbreviation (see miRBase organisms.txt).
     * @param code abbrev. of the species, e.g., 'ebv' (EB virus) or 'hsa' (human)
     * @return
     */
    private BioSource getOrganism(String code) {
        String taxid = orgCodeToTaxonMap.get(code);
        String name = orgCodeToNameMap.get(code);

        BioSource bioSource = findById(TAXONOMY_NS+taxid);
        if(bioSource == null) {
            bioSource = create(BioSource.class, TAXONOMY_NS+taxid);
            bioSource.setDisplayName(name);
            bioSource.setStandardName(name);
            UnificationXref ux = findById("uxref_taxonomy_" + taxid);
            if(ux == null) {
                ux = create(UnificationXref.class, "uxref_taxonomy_" + taxid);
                ux.setDb("Taxonomy");
                ux.setId(taxid);
            }
            bioSource.addXref(ux);
        }

        return bioSource;
    }


    /**
     * Converts miRTarBase to BioPAX L3 model.
     *
     * @param miRTarBase the MTI XSL(X) MS Excel Workbook input stream
     * @param mirAliases aliases.txt
     * @param mirOrganisms organisms.txt
     * @throws Exception when there is an I/O error or invalid input format.
     * @return model
     */
    public Model convert(InputStream miRTarBase, InputStream mirAliases, InputStream mirOrganisms)
            throws IOException
    {
        Workbook wb = null;
        try {
            wb = WorkbookFactory.create(miRTarBase);
        } catch (InvalidFormatException e) {
            throw new IOException(e);
        }

        Sheet sheet = wb.getSheetAt(0);
        log.info("Now parsing the first Excel worksheet: " + sheet.getSheetName());

        int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();
        log.info("There are " + physicalNumberOfRows + " rows in the miRTarBase file.");

        //TODO: initialize internal maps from miRBase organisms.txt and aliases.txt
        if(mirAliases==null)
            mirAliases = getClass().getResourceAsStream("/aliases.txt");
        if(mirOrganisms==null)
            mirOrganisms = getClass().getResourceAsStream("/organisms.txt");

        // create a new empty biopax level3 model; set the xml:base
        model = BioPAXLevel.L3.getDefaultFactory().createModel();
        model.setXmlBase(xmlBase);

        // process rows
        for(int r=1; r < physicalNumberOfRows; r++) {
            Row row = sheet.getRow(r);
            /*  Columns:
              
                0- miRTarBase ID
                1- miRNA
                2- Species (miRNA)
                3- Target Gene
                4- Target Gene (Entrez Gene ID)
                5- Species (Target Gene)
                6- Experiments - one per line
                7- Support Type
                8- References (PMID) - one per line

                The same ID (col:0) and next 5 values can occur again in other lines,
                but one or more of columns: 6,7, or (especially) 8 will differ.
            */

            String id = row.getCell(0).getStringCellValue().trim(); //MIRT\d{6} standard identifier
            String name = row.getCell(1).getStringCellValue().trim();
            String organism = row.getCell(2).getStringCellValue().trim(); //miRNA's species name
            String targetGene = row.getCell(3).getStringCellValue().trim();

            Cell cell = row.getCell(4);
            int targetGeneId = 0;
            try {
                targetGeneId = new Double(cell.getNumericCellValue()).intValue();
            } catch (Exception e) {
                log.warn(String.format("failed to parse gene ID at row %d: %s %s, gene: %s (%s)",
                        r, id, name, targetGene, e));
            }

            String targetOrganism = row.getCell(5).getStringCellValue().trim();

            cell = row.getCell(6);
            String experiments = (cell != null) ? cell.getStringCellValue().trim() : null;

            cell = row.getCell(7);
            String support = (cell != null) ? cell.getStringCellValue().trim() : null;

            cell = row.getCell(8);
            int pmid = 0;
            try {
                pmid = new Double(cell.getNumericCellValue()).intValue();
            } catch (Exception e) {
                log.warn(String.format("failed to parse PMID at row %d: %s, gene: %s (%s)", r, id, name, e));
            }

            //TODO: find prev. generated TemplateReactionRegulation by MIRT ID or make a new one
            TemplateReactionRegulation regulation = findById(MIRT_NS + id);
            if(regulation == null)
            {
                regulation = create(TemplateReactionRegulation.class, MIRT_NS + id);
                TemplateReaction templateReaction = getTranscription(targetGene, targetGeneId, targetOrganism);
                regulation.setControlType(ControlType.INHIBITION);
                regulation.addControlled(templateReaction);

                //find or create Rna
                //we don't use MIRT id here (which belongs to the Control interaction)
                String mirRdfId = name.toLowerCase(); //names are like 'hsa-miR...'
                Rna mirna = findById(mirRdfId);
                if (mirna == null) {
                    mirna = create(Rna.class, mirRdfId);
                    mirna.setDisplayName(name);
                    mirna.setStandardName(name);
                    mirna.addName(name);

                    //TODO: find/create a RnaReference (map name to MI/MIMAT miRBase id)
//            RelationshipXref relationshipXref = create(RelationshipXref.class, "rxref_" + id);
//            relationshipXref.setDb("miRTarBase");
//            relationshipXref.setId(id);
//            rna.addXref(relationshipXref);
                }
                regulation.addController(mirna);

                String rname = name + " (" + organism + ") regulates expression of " + targetGene + " in " + targetOrganism;
                regulation.setStandardName(rname);
                regulation.setDisplayName(rname);
                regulation.addName(rname);
            }

            if(pmid > 0) {
                PublicationXref pubxref = findById("pub_" + pmid);
                if(pubxref == null) {
                    pubxref = create(PublicationXref.class, "pub_" + pmid);
                    pubxref.setDb("PubMed");
                    pubxref.setId(pmid + "");
                }
                regulation.addXref(pubxref);
            }

            if(experiments!=null)
                regulation.addComment(experiments);
            if(support!=null)
                regulation.addComment(support);

            if(makePathwayPerOrganism) //per miRNA's species, not target gene's organism
                assignReactionToPathway(regulation, organism);
        }

        log.info("Removing dangling Rna, RnaReference and Xref...");
        int removedObjects = 0;
        removedObjects += ModelUtils.removeObjectsIfDangling(model, Rna.class).size();
        removedObjects += ModelUtils.removeObjectsIfDangling(model, RnaReference.class).size();
        removedObjects += ModelUtils.removeObjectsIfDangling(model, Xref.class).size();
        log.info("Removed " + removedObjects + " objects.");
        log.info("Converted miRTarBase BioPAX model contains: "
                + model.getObjects(Pathway.class).size() + " pathways; "
                + model.getObjects(TemplateReaction.class).size() + " template reactions; "
                + model.getObjects(TemplateReactionRegulation.class).size() + " controls; "
                + model.getObjects(Protein.class).size() + " products."
        );

        miRTarBase.close();
        mirAliases.close();
        mirOrganisms.close();

        return this.model;
    }

    // Recursively adds control interaction and controlled reactions
    // to a all-in-one organism "pathway" (not really a bio pathway)
    private void assignReactionToPathway(TemplateReactionRegulation regulation, String mirOrganism) {
        String org = orgNameToCodeMap.get(mirOrganism);
        String taxon = orgCodeToTaxonMap.get(org);
        String pid = "pathway_" + taxon;
        Pathway pathway = findById(pid);
        if(pathway == null) {
            pathway = create(Pathway.class, pid);
            pathway.setDisplayName(mirOrganism);
            pathway.setOrganism(getOrganism(org));
        }

        // Propagate pathway assignments
        final Pathway finalPathway = pathway;
        Traverser traverser = new Traverser(SimpleEditorMap.get(BioPAXLevel.L3), new Visitor() {
            @Override
            public void visit(BioPAXElement domain, Object range, Model model, PropertyEditor<?, ?> editor) {
                if(range != null && range instanceof Process) {
                    finalPathway.addPathwayComponent((Process) range);
                }
            }
        });
        traverser.traverse(regulation, model);

        pathway.addPathwayComponent(regulation);
    }

    private TemplateReaction getTranscription(String targetGene, int targetGeneId, String targetOrganism) {
        final String refId = (targetGeneId>0) ? String.valueOf(targetGeneId) : targetGene;

        ProteinReference ref = findById("ref_" + refId);
        if(ref == null) {
            ref = create(ProteinReference.class, "ref_" + refId);
            ref.setDisplayName(targetGene);
            ref.setStandardName(targetGene);
            ref.addName(targetGene);
            ref.setOrganism(getOrganism(orgNameToCodeMap.get(targetOrganism)));

            if(targetGeneId>0) {
                Xref entrezXref = create(RelationshipXref.class, "entrezref_" + refId);
                entrezXref.setDb("NCBI Gene");
                entrezXref.setId(refId);
                ref.addXref(entrezXref);
            }

            RelationshipXref symbolXref = create(RelationshipXref.class, "symbolref_" + refId);
            symbolXref.setDb("HGNC Symbol");
            symbolXref.setId(targetGene);
            ref.addXref(symbolXref);
        }

        String proteinId = "protein_" + refId;
        Protein protein = findById(proteinId);
        if(protein == null) {
            protein = create(Protein.class, proteinId);
            protein.setStandardName(targetGene);
            protein.setDisplayName(targetGene);
            protein.addName(targetGene);
            protein.setEntityReference(ref);
        }

        String reactionId = "template_" + refId;
        TemplateReaction templateReaction = findById(reactionId);
        if(templateReaction == null) {
            templateReaction = create(TemplateReaction.class, reactionId);
            String tname = targetGene + " production.";
            templateReaction.setDisplayName(tname);
            templateReaction.setStandardName(tname);
            templateReaction.addName(tname);
            templateReaction.addProduct(protein);
            templateReaction.setTemplateDirection(TemplateDirectionType.FORWARD);
        }

        return templateReaction;
    }

    private void loadMirBase(InputStream aliasesInputStream, InputStream organismsInputStream) throws Exception {
        final String separator = "\t";
        final String intraFieldSeparator = ";";

        //TODO: refactor; init MI/MIMAT ID to/form miRNA names maps (instead of generating biopax)...
        Scanner scanner = new Scanner(aliasesInputStream);
        while(scanner.hasNext()) {
            String line = scanner.nextLine();
            String tokens[] = line.split(separator);
            String id = tokens[0].trim();
            String names = tokens[1].trim();
            if(names.endsWith(";"))
                names = names.substring(0, names.length()-1); // Get rid of the last ; at the end
            String tnames[] = names.split(intraFieldSeparator);

            RnaReference rnaReference = create(RnaReference.class, id);

            UnificationXref unificationXref = create(UnificationXref.class, "uxref_" + id);
            unificationXref.setDb((id.startsWith("MIMAT")) ? "miRBase mature sequence" : "miRBase Sequence");
            unificationXref.setId(id);
            rnaReference.addXref(unificationXref);
            rnaReference.setDisplayName(id);
            rnaReference.setStandardName(id);
            rnaReference.addName(id);

            for (String name : tnames) {
                String rdfId = name.toLowerCase();
                Rna rna = findById(rdfId);
                if(rna == null) { // by default generate new one
                    rna = create(Rna.class, rdfId);
                } else { // or add it as a generic
                    Rna oldRna = rna;
                    rna = create(Rna.class, rdfId + "_" + UUID.randomUUID());
                    oldRna.addMemberPhysicalEntity(rna);
                }

                rna.setEntityReference(rnaReference);
                rna.setDisplayName(name);
                rna.setStandardName(name);
                rna.addName(name);
                rna.addName(id);
                rnaReference.addName(name);

                //set 'organism' using 'hsa', 'ebv',.. from the first name
                if(rnaReference.getOrganism()==null)
                    rnaReference.setOrganism(getOrganism(name.substring(0,3)));
            }
        }

        //TODO: init organism code to/form name, taxon maps...
    }
}
