package tw.edu.nctu.mbc.mirtarbase.converter;

import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public abstract class Converter {
    private static Logger log = LoggerFactory.getLogger(Converter.class);
    public static String sharedXMLBase = "http://mirtarbase.mbc.nctu.edu.tw/#";

    public static final BioPAXFactory bioPAXFactory = BioPAXLevel.L3.getDefaultFactory();

       protected <T extends BioPAXElement> T create(Class<T> aClass, String id) {
        return bioPAXFactory.create(aClass, completeId(id));
    }

    protected Model createModel() {
        Model model = bioPAXFactory.createModel();
        model.setXmlBase(getXMLBase());
        return model;
    }

    abstract public Model convert(InputStream inputStream) throws Exception;

    private String XMLBase = Converter.sharedXMLBase;
    public String getXMLBase() {
        return XMLBase;
    }

    public void setXMLBase(String XMLBase) {
        this.XMLBase = XMLBase;
    }

    public String completeId(String partialId) {
        return getXMLBase() + partialId;
    }

    public String getMirnaRdfId(String tname) {
        return tname.toLowerCase();
    }
}
