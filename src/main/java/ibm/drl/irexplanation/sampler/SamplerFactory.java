/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.sampler;

import ibm.drl.irexplanation.samplegen.BaseSampleGenerator;
import ibm.drl.irexplanation.samplegen.ExplanationUnit;
import ibm.drl.irexplanation.samplegen.PointwiseSampleGenerator;
import java.util.HashMap;
import java.util.Properties;

/**
 *
 * @author dganguly
 */
public class SamplerFactory {
    static HashMap<String, BaseSampler> samplerMap;
    static HashMap<String, BaseSampleGenerator> genMap;
    
    static BaseSampleGenerator getGenerator(Properties prop, ExplanationUnit explanationUnit) throws Exception {
        if (genMap == null) {
            genMap = new HashMap<>(2);
            genMap.put("pointwise", new PointwiseSampleGenerator(prop, explanationUnit));
            genMap.put("pairwise", null);
        }
        String explainType = prop.getProperty("explanation.type", "pointwise");
        return genMap.get(explainType);
    }
    
    static void init(Properties prop, ExplanationUnit explanationUnit) throws Exception {
        samplerMap = new HashMap<>(1);
        BaseSampleGenerator gen = getGenerator(prop, explanationUnit);
        samplerMap.put("tfidf", new TermImportanceSampler(prop, gen, explanationUnit));
        
        // TODO: include more
    }
    
    public static BaseSampler createSample(Properties prop, ExplanationUnit explanationUnit) throws Exception {
        if (samplerMap == null)
            init(prop, explanationUnit);
        return samplerMap.get(prop.getProperty("sampler.type"));
    }
    
}
