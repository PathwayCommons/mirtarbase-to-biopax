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
            UnificationXref ux = findById("taxonomy_" + taxid);
            if(ux == null) {
                ux = create(UnificationXref.class, "taxonomy_" + taxid);
                ux.setDb("Taxonomy");
                ux.setId(taxid);
            }
            bioSource.addXref(ux);
        }

        return bioSource;
    }

    /**
     * Converts miRTarBase to BioPAX L3 model using built-in aliases and organisms maps from MiRBase.
     *
     * @param miRTarBase the MTI XSL(X) MS Excel Workbook input stream
     * @throws Exception when there is an I/O error or invalid input format.
     * @return model
     */
    public Model convert(InputStream miRTarBase) throws IOException {
        return convert(miRTarBase,
                getClass().getResourceAsStream("/aliases.txt"),
                    getClass().getResourceAsStream("/organisms.txt"));
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

        if(mirAliases==null)
            mirAliases = getClass().getResourceAsStream("/aliases.txt");
        if(mirOrganisms==null)
            mirOrganisms = getClass().getResourceAsStream("/organisms.txt");

        //init internal maps from miRBase
        loadMirBase(mirAliases,mirOrganisms);

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

            //find prev. generated TemplateReactionRegulation by MIRT ID or make a new one
            TemplateReactionRegulation regulation = findById(MIRT_NS + id);
            if(regulation == null)
            {
                TemplateReaction templateReaction = getTranscription(targetGene, targetGeneId, targetOrganism);

                regulation = create(TemplateReactionRegulation.class, MIRT_NS + id);
                regulation.setControlType(ControlType.INHIBITION);
                regulation.addControlled(templateReaction);

                //find or create a Rna, RnaRef...
                final String lcName = name.toLowerCase(); //lc is important
                final String rnaRefRdfId = "ref_" + lcName;
                final String rnaRdfId = "rna_"+ lcName; //names are like 'hsa-miR...'
                Rna mirna = findById(rnaRdfId);
                if (mirna == null) {
                    mirna = create(Rna.class, rnaRdfId);
                    mirna.setDisplayName(name);
                    //find/create a RnaReference
                    RnaReference rnaReference = findById(rnaRefRdfId);
                    if(rnaReference == null) {
                        rnaReference = create(RnaReference.class, rnaRefRdfId);
                        rnaReference.setDisplayName(lcName);
                        rnaReference.setStandardName(lcName);
                        rnaReference.setOrganism(getOrganism(orgCodeFromMir(lcName)));
                        String accessions = mirNameToIdMap.get(lcName);
                        if(accessions != null) {
                            for(String ac : accessions.split(";")) {
                                RelationshipXref x = findById("mirbase_" + ac);
                                if(x == null) {
                                    x = create(RelationshipXref.class, "mirbase_" + ac);
                                    x.setDb((ac.startsWith("MIMAT")) ? "miRBase mature sequence" : "miRBase Sequence");
                                    x.setId(ac);
                                }
                                rnaReference.addXref(x);
                            }
                        }
                    }
                    mirna.setEntityReference(rnaReference);
                }

                regulation.addController(mirna);
                regulation.addName(name + " (" + organism + ") regulates expression of " + targetGene + " in " + targetOrganism);
                regulation.setDisplayName(name + " regulates " + targetGene);
                regulation.setStandardName(id);

                RelationshipXref rx = create(RelationshipXref.class, "mirtarbase_" + id);
                rx.setDb("miRTarBase");
                rx.setId(id);
                regulation.addXref(rx);
            }


            cell = row.getCell(6);
            String experiments = (cell != null) ? cell.getStringCellValue().trim() : null;
            cell = row.getCell(7);
            String support = (cell != null) ? cell.getStringCellValue().trim() : null;
            cell = row.getCell(8);

            try {
                int pmid = new Double(cell.getNumericCellValue()).intValue();

                PublicationXref pubxref = findById("pub_" + pmid);
                if (pubxref == null) {
                    pubxref = create(PublicationXref.class, "pub_" + pmid);
                    pubxref.setDb("PubMed");
                    pubxref.setId(pmid + "");
                }
                regulation.addXref(pubxref);

                //TODO: add Evidence using 'pmid','experiment','support' columns...
                //TODO: add Score - exp.methods, e.g.: 'Microarray', and value, e.g.: 'Functional MTI (Weak)'?..
//                Evidence ev = create(Evidence.class, "evidence_" + id + "_" + pmid); //TODO: id for Evidence?..
//                ev.addXref(pubxref);
//                Score score = create(Score.class, "?..");
//                score.addXref(methodRelXref);
//                ev.addConfidence(score);

                if (experiments != null) {
                    //TODO: add either evidence/confidence:Score/scoreSource or evidence/evidenceCode (CV/Xref- MI term)?
                    regulation.addComment(experiments);
                }

                if (support != null) {
                    //TODO: add evidence/confidence:Score/value (e.g., 'Functional MTI')
                    regulation.addComment(support);
                }

            } catch (Exception e) {
                log.error(String.format("failed to parse PMID at row %d: %s, gene: %s (%s)", r, id, name, e));
            }

            if(makePathwayPerOrganism) //per miRNA's species, not target gene's organism
                assignReactionToPathway(regulation, organism);
        }

// No clean-up - this version converter does not generate any dangling objects.
//        log.info("Removing dangling Rna, RnaReference and Xref...");
//        int removedObjects = 0;
//        removedObjects += ModelUtils.removeObjectsIfDangling(model, Rna.class).size();
//        removedObjects += ModelUtils.removeObjectsIfDangling(model, RnaReference.class).size();
//        removedObjects += ModelUtils.removeObjectsIfDangling(model, Xref.class).size();
//        log.info("Removed " + removedObjects + " objects.");
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
        String org = orgNameToCodeMap.get(mirOrganism.toLowerCase());
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

        String reactionId = "template_" + refId;
        TemplateReaction templateReaction = findById(reactionId);
        if(templateReaction == null) {
            templateReaction = create(TemplateReaction.class, reactionId);
            String tname = targetGene + " production.";
            templateReaction.setDisplayName(tname);
            templateReaction.setTemplateDirection(TemplateDirectionType.FORWARD);
            // define/find the protein
            String proteinId = "protein_" + refId;
            Protein protein = findById(proteinId);
            if(protein == null) {
                protein = create(Protein.class, proteinId);
                protein.setDisplayName(targetGene);

                //define/find a protein reference
                ProteinReference ref = findById("ref_" + refId);
                if(ref == null) {
                    ref = create(ProteinReference.class, "ref_" + refId);
                    ref.setDisplayName(targetGene);
                    ref.setOrganism(getOrganism(orgNameToCodeMap.get(targetOrganism.toLowerCase())));
                    //add xrefs
                    if(targetGeneId > 0) {
                        RelationshipXref x = findById( "ncbi_gene_" + targetGeneId);
                        if(x == null) {
                            x = create(RelationshipXref.class, "ncbi_gene_" + targetGeneId);
                            x.setDb("NCBI Gene");
                            x.setId(targetGeneId+"");
                        }
                        ref.addXref(x);
                    }
                    RelationshipXref x = findById("hgnc_symbol_" + targetGene);
                    if(x == null) {
                        x = create(RelationshipXref.class, "hgnc_symbol_" + targetGene);
                        x.setDb("HGNC Symbol");
                        x.setId(targetGene);
                    }
                    ref.addXref(x);
                }

                protein.setEntityReference(ref);
            }

            templateReaction.addProduct(protein);
        }

        return templateReaction;
    }

    private void loadMirBase(InputStream aliasesInputStream, InputStream organismsInputStream) throws IOException {
        final String separator = "\t";
        final String intraFieldSeparator = ";";

        mirNameToIdMap.clear();

        //init miRNA name to MI/MIMAT IDs (if many, separate IDs with semicolon) map
        Scanner scanner = new Scanner(aliasesInputStream);
        while(scanner.hasNext()) {
            String line = scanner.nextLine();
            if (line.startsWith("#"))
                continue;

            String cols[] = line.split(separator);
            final String id = cols[0].trim();
            String names = cols[1].trim();
            if (names.endsWith(";"))
                names = names.substring(0, names.length() - 1); // Get rid of the last ; at the end

            for (String tname : names.split(intraFieldSeparator)) {
                final String name = tname.toLowerCase();
                String wasId = mirNameToIdMap.get(name);
                if( wasId != null) {
                    log.debug(String.format("miR name %s maps to: %s;%s", name, wasId,id));
                    if(!wasId.contains(id))
                        mirNameToIdMap.put(name,wasId+";"+id);
                } else {
                    mirNameToIdMap.put(name, id);
                }
            }
        }

        // Init organism code to/form name, taxon maps;
        orgCodeToTaxonMap.clear();
        orgCodeToNameMap.clear();
        orgNameToCodeMap.clear();
        //organisms.txt (miRBase) columns:
        //#organism #division   #name   #tree   #NCBI-taxid
        scanner = new Scanner(organismsInputStream);
        while(scanner.hasNext()) {
            String line = scanner.nextLine();
            if(line.startsWith("#"))
                continue;

            String cols[] = line.split(separator);
            String code = cols[0].trim();
            String name = cols[2].trim();
            String taxid = cols[4].trim();

            orgCodeToTaxonMap.put(code,taxid);
            orgCodeToNameMap.put(code,name);
            orgNameToCodeMap.put(name.toLowerCase(),code);
        }

    }
}
