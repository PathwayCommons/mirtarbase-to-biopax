package com.google.gsoc14.mirtarbase2biopax.converter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;

import java.io.InputStream;

public abstract class Converter {
    private static Log log = LogFactory.getLog(Converter.class);

    private BioPAXFactory bioPAXFactory = BioPAXLevel.L3.getDefaultFactory();

    public BioPAXFactory getBioPAXFactory() {
        return bioPAXFactory;
    }

    public void setBioPAXFactory(BioPAXFactory bioPAXFactory) {
        this.bioPAXFactory = bioPAXFactory;
    }

    protected <T extends BioPAXElement> T create(Class<T> aClass, String id) {
        return getBioPAXFactory().create(aClass, id);
    }

    protected Model createModel() {
        return getBioPAXFactory().createModel();
    }

    abstract public Model convert(InputStream inputStream) throws Exception;

}
