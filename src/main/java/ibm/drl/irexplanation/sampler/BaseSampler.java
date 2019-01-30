/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.sampler;

import ibm.drl.irexplanation.samplegen.BaseSampleGenerator;
import ibm.drl.irexplanation.samplegen.ExplanationUnit;
import ibm.drl.irexplanation.samplegen.TermWeights;
import java.util.Properties;
import java.util.Random;

/**
 *
 * @author dganguly
 */
public abstract class BaseSampler implements Sampler {
    Properties prop;
    BaseSampleGenerator gen;
    TermWeights twts;
    float sampleRatio;
    Random rand;
    ExplanationUnit expunit;
    
    public BaseSampler(Properties prop, BaseSampleGenerator gen, ExplanationUnit expunit) throws Exception {
        this.prop = prop;
        this.gen = gen;
        this.expunit = expunit;
        sampleRatio = Float.parseFloat(prop.getProperty("sample.size.ratio", "0.2"));
        twts = gen.initTermWeights(expunit);
        
        rand = new Random(Integer.parseInt(prop.getProperty("sampler.seed", "123456")));
    }
    
    public BaseSampleGenerator getGen() { return gen; }
    
    public abstract void buildTermsInDoc() throws Exception;
    
    public void setExpUnit(ExplanationUnit expunit) { this.expunit = expunit; }
    
}
