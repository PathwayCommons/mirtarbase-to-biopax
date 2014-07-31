package com.google.gsoc14.mirtarbase2biopax.converter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.biopax.paxtools.controller.PropertyEditor;
import org.biopax.paxtools.controller.SimpleEditorMap;
import org.biopax.paxtools.controller.Traverser;
import org.biopax.paxtools.controller.Visitor;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.model.level3.Process;

import java.io.InputStream;
import java.util.UUID;

public class MiRTarBaseConverter extends Converter {
    private static Log log = LogFactory.getLog(MiRTarBaseConverter.class);

    @Override
    public Model convert(InputStream inputStream) throws Exception {
        Model model = createModel();
        Workbook wb = WorkbookFactory.create(inputStream);
        Sheet sheet = wb.getSheet("miRTarBase");

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
            String organism = row.getCell(2).getStringCellValue().trim();
            String targetGene = row.getCell(3).getStringCellValue().trim();
            int targetGeneId = new Double(row.getCell(4).getNumericCellValue()).intValue();
            String targetOrganism = row.getCell(5).getStringCellValue().trim();
            String experiments = row.getCell(6).getStringCellValue().trim();
            String support = row.getCell(7).getStringCellValue().trim();
            int pmid = new Double(row.getCell(8).getNumericCellValue()).intValue();

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

            PublicationXref pubxref = create(PublicationXref.class, "pub_" + pmid + "_" + UUID.randomUUID());
            model.add(pubxref);
            pubxref.setDb("PubMed");
            pubxref.setId(pmid + "");
            regulation.addXref(pubxref);

            regulation.addComment(experiments);
            regulation.addComment(support);
            regulation.addAvailability(organism);

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

    private void assignReactionToPathway(Model model, TemplateReactionRegulation regulation, String organism) {
        String pid = "pathway_" + organism.hashCode();
        Pathway pathway = (Pathway) model.getByID(pid);
        if(pathway == null) {
            pathway = create(Pathway.class, pid);
            model.add(pathway);

            pathway.setDisplayName(organism);
            pathway.setStandardName(organism);
            pathway.addName(organism);

            pathway.setOrganism(getOrganism(model, organism));
        }

        addReactionToPathwayByTraversing(model, regulation, pathway);
        pathway.addPathwayComponent(regulation);

    }

    private void addReactionToPathwayByTraversing(Model model, Process process, Pathway pathway) {
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
        traverser.traverse(process, model);
    }

    private TemplateReaction getTranscription(Model model, String targetGene, double targetGeneId, String targetOrganism) {
        String refId = "ref_" + targetGeneId;
        ProteinReference ref = (ProteinReference) model.getByID(refId);
        if(ref == null) {
            ref = create(ProteinReference.class, refId);
            model.add(ref);
            ref.setDisplayName(targetGene);
            ref.setStandardName(targetGene);
            ref.addName(targetGene);
            ref.setOrganism(getOrganism(model, targetOrganism));

            Xref entrezXref = create(UnificationXref.class, "entrezref_" + targetGeneId);
            model.add(entrezXref);
            entrezXref.setDb("NCBI Gene");
            entrezXref.setId(targetGeneId + "");
            ref.addXref(entrezXref);

            RelationshipXref symbolXref = create(RelationshipXref.class, "symbolref_" + targetGeneId);
            model.add(symbolXref);
            symbolXref.setDb("HGNC Symbol");
            symbolXref.setId(targetGene);
            ref.addXref(symbolXref);
        }

        String proteinId = "protein_" + targetGeneId;
        Protein protein = (Protein) model.getByID(proteinId);
        if(protein == null) {
            protein = create(Protein.class, proteinId);
            model.add(protein);
            protein.setStandardName(targetGene);
            protein.setDisplayName(targetGene);
            protein.addName(targetGene);

            protein.setEntityReference(ref);
        }

        String reactionId = "template_" + targetGeneId;
        TemplateReaction templateReaction = (TemplateReaction) model.getByID(reactionId);
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
        BioSource bioSource = (BioSource) model.getByID(orgId);
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
        String mirnaRDF = getMiRTarBaseUtils().getMirnaRDF(name);
        Rna rna = (Rna) model.getByID(mirnaRDF);
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
