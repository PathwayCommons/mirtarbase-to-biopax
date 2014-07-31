package com.google.gsoc14.mirtarbase2biopax.converter;

import org.biopax.paxtools.model.Model;

import java.io.InputStream;

public class MiRTarBaseConverter extends Converter {
    @Override
    public Model convert(InputStream inputStream) throws Exception {
        Model model = createModel();
        return model;
    }
}
