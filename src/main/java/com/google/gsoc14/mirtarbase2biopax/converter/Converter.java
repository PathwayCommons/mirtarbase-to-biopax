package com.google.gsoc14.mirtarbase2biopax.converter;

import com.google.gsoc14.mirtarbase2biopax.util.MiRTarBaseUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;

import java.io.InputStream;

public abstract class Converter {
    private static Log log = LogFactory.getLog(Converter.class);
    public static String sharedXMLBase = "http://mirtarbase.mbc.nctu.edu.tw/#";

    private BioPAXFactory bioPAXFactory = BioPAXLevel.L3.getDefaultFactory();

    public BioPAXFactory getBioPAXFactory() {
        return bioPAXFactory;
    }

    public void setBioPAXFactory(BioPAXFactory bioPAXFactory) {
        this.bioPAXFactory = bioPAXFactory;
    }

    private MiRTarBaseUtils miRTarBaseUtils = new MiRTarBaseUtils();

    public MiRTarBaseUtils getMiRTarBaseUtils() {
        return miRTarBaseUtils;
    }

    public void setMiRTarBaseUtils(MiRTarBaseUtils miRTarBaseUtils) {
        this.miRTarBaseUtils = miRTarBaseUtils;
    }

    protected <T extends BioPAXElement> T create(Class<T> aClass, String id) {
        return getBioPAXFactory().create(aClass, completeId(id));
    }

    protected Model createModel() {
        Model model = getBioPAXFactory().createModel();
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
}
