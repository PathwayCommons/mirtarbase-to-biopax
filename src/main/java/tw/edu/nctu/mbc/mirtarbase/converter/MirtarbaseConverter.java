package tw.edu.nctu.mbc.mirtarbase.converter;

import org.apache.poi.ss.usermodel.*;
import org.biopax.paxtools.controller.PropertyEditor;
import org.biopax.paxtools.controller.SimpleEditorMap;
import org.biopax.paxtools.controller.Traverser;
import org.biopax.paxtools.controller.Visitor;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.model.level3.Process;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.UUID;

public class MirtarbaseConverter extends Converter {
    private static Logger log = LoggerFactory.getLogger(MirtarbaseConverter.class);

    private boolean makePathwayPerOrganism = false;

    /**
     * Whether to generate such "pathways", one per species, that contain all the interactions.
     *
     * @return true/false
     */
    public boolean isMakePathwayPerOrganism() {
        return makePathwayPerOrganism;
    }
    public void setMakePathwayPerOrganism(boolean makePathwayPerOrganism) {
        this.makePathwayPerOrganism = makePathwayPerOrganism;
    }

    @Override
    public Model convert(InputStream inputStream) throws Exception {
        Model model = createModel();
        Workbook wb = WorkbookFactory.create(inputStream);
        Sheet sheet = wb.getSheetAt(0);
        log.info("Now parsing the first Excel worksheet: " + sheet.getSheetName());

        int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();
        log.debug("There are " + physicalNumberOfRows + " rows in the miRTarBase file.");

        for(int r=1; r < physicalNumberOfRows; r++) {
            Row row = sheet.getRow(r);

            /*
                0 - miRTarBase ID
                1- miRNA
                2- Species (miRNA)
                3- Target Gene
                4- Target Gene (Entrez Gene ID)
                5- Species (Target Gene)
                6- Experiments
                7- Support Type
                8- References (PMID)
             */

            String id = row.getCell(0).getStringCellValue().trim();
            String name = row.getCell(1).getStringCellValue().trim();
            String organism = row.getCell(2).getStringCellValue().trim(); //miRNA's organism
            String targetGene = row.getCell(3).getStringCellValue().trim();

            Cell cell = row.getCell(4);
            int targetGeneId = 0;
            try {
                targetGeneId = new Double(cell.getNumericCellValue()).intValue();
            } catch (Exception e) {
                log.warn(String.format("failed to parse gene ID at row %d: %s %s, gene: %s (%s)",r,id,name,targetGene, e));
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
                log.warn(String.format("failed to parse PMID at row %d: %s, gene: %s (%s)",r,id,name,e));
            }

            Rna mirna = getMirna(model, id, name);
            TemplateReaction templateReaction = getTranscription(model, targetGene, targetGeneId, targetOrganism);
            TemplateReactionRegulation regulation = create(TemplateReactionRegulation.class, "control_" + r);
            model.add(regulation);
            regulation.setControlType(ControlType.INHIBITION);
            regulation.addControlled(templateReaction);
            regulation.addController(mirna);
            String rname = name + " regulates expression of " + targetGene + " in " + organism;
            regulation.setStandardName(rname);
            regulation.setDisplayName(rname);
            regulation.addName(rname);

            if(pmid > 0) {
                PublicationXref pubxref = create(PublicationXref.class, "pub_" + pmid + "_" + UUID.randomUUID());
                model.add(pubxref);
                pubxref.setDb("PubMed");
                pubxref.setId(pmid + "");
                regulation.addXref(pubxref);
            }

            if(experiments!=null)
                regulation.addComment(experiments);
            if(support!=null)
                regulation.addComment(support);
            regulation.addAvailability(organism);

            if(makePathwayPerOrganism)
                assignReactionToPathway(model, regulation, organism);
        }

        log.debug("Done with the miRTarBase conversion: "
                + model.getObjects(Pathway.class).size() + " pathways; "
                + model.getObjects(TemplateReaction.class).size() + " template reactions; "
                + model.getObjects(TemplateReactionRegulation.class).size() + " controls; "
                + model.getObjects(Protein.class).size() + " products."
        );

        return model;
    }

    // Recursively adds control interaction and controlled reactions
    // to a all-in-one organism "pathway" (not really a bio pathway)
    private void assignReactionToPathway(Model model, TemplateReactionRegulation regulation, String organism) {
        String pid = "pathway_" + organism.hashCode();
        Pathway pathway = (Pathway) model.getByID(completeId(pid));
        if(pathway == null) {
            pathway = create(Pathway.class, pid);
            model.add(pathway);
            pathway.setDisplayName(organism);
            pathway.setStandardName(organism);
            pathway.addName(organism);
            pathway.setOrganism(getOrganism(model, organism));
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

    private TemplateReaction getTranscription(Model model, String targetGene, int targetGeneId, String targetOrganism) {
        final String refId = (targetGeneId>0) ? String.valueOf(targetGeneId) : targetGene;

        ProteinReference ref = (ProteinReference) model.getByID(completeId("ref_" + refId));
        if(ref == null) {
            ref = create(ProteinReference.class, "ref_" + refId);
            model.add(ref);
            ref.setDisplayName(targetGene);
            ref.setStandardName(targetGene);
            ref.addName(targetGene);
            ref.setOrganism(getOrganism(model, targetOrganism));

            if(targetGeneId>0) {
                Xref entrezXref = create(RelationshipXref.class, "entrezref_" + refId);
                model.add(entrezXref);
                entrezXref.setDb("NCBI Gene");
                entrezXref.setId(refId);
                ref.addXref(entrezXref);
            }

            RelationshipXref symbolXref = create(RelationshipXref.class, "symbolref_" + refId);
            model.add(symbolXref);
            symbolXref.setDb("HGNC Symbol");
            symbolXref.setId(targetGene);
            ref.addXref(symbolXref);
        }

        String proteinId = "protein_" + refId;
        Protein protein = (Protein) model.getByID(completeId(proteinId));
        if(protein == null) {
            protein = create(Protein.class, proteinId);
            model.add(protein);
            protein.setStandardName(targetGene);
            protein.setDisplayName(targetGene);
            protein.addName(targetGene);

            protein.setEntityReference(ref);
        }

        String reactionId = "template_" + refId;
        TemplateReaction templateReaction = (TemplateReaction) model.getByID(completeId(reactionId));
        if(templateReaction == null) {
            templateReaction = create(TemplateReaction.class, reactionId);
            model.add(templateReaction);
            String tname = targetGene + " production.";
            templateReaction.setDisplayName(tname);
            templateReaction.setStandardName(tname);
            templateReaction.addName(tname);
            templateReaction.addProduct(protein);
            templateReaction.setTemplateDirection(TemplateDirectionType.FORWARD);
        }

        return templateReaction;
    }

    private BioSource getOrganism(Model model, String targetOrganism) {
        String orgId = "org_" + targetOrganism.hashCode();
        BioSource bioSource = (BioSource) model.getByID(completeId(orgId));
        if(bioSource == null) {
            bioSource = create(BioSource.class, orgId);
            model.add(bioSource);
            bioSource.setStandardName(targetOrganism);
            bioSource.setDisplayName(targetOrganism);
            bioSource.addName(targetOrganism);
        }
        return bioSource;
    }

    private Rna getMirna(Model model, String id, String name) {
        String mirnaRDF = getMirnaRdfId(name); //rna name has organism part as well, such as 'hsa-' or 'ebv-' (virus)
        Rna rna = (Rna) model.getByID(completeId(mirnaRDF));
        if(rna == null) {
            rna = create(Rna.class, mirnaRDF);
            model.add(rna);
            rna.setDisplayName(name);
            rna.setStandardName(name);
            rna.addName(name);
            rna.addName(id);

            RelationshipXref relationshipXref = create(RelationshipXref.class, "rxref_" + id);
            model.add(relationshipXref);
            relationshipXref.setDb("miRTarBase");
            relationshipXref.setId(id);
            rna.addXref(relationshipXref);
        }

        return rna;
    }

}
