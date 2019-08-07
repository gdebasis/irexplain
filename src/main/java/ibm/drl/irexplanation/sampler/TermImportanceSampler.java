/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.sampler;

import ibm.drl.irexplanation.samplegen.BaseSampleGenerator;
import ibm.drl.irexplanation.samplegen.ExplanationUnit;
import ibm.drl.irexplanation.samplegen.PointwiseSampleGenerator;
import ibm.drl.irexplanation.samplegen.TermWeight;
import ibm.drl.irexplanation.samplegen.TermWeights;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.index.IndexReader;

/**
 *
 * @author dganguly
 */
public class TermImportanceSampler extends BaseSampler {
    
    public TermImportanceSampler(Properties prop,
            BaseSampleGenerator gen,
            ExplanationUnit expunit) throws Exception {
        super(prop, gen, expunit);
        
        twts.weightIDFs(expunit.getReader());
    }
    
    @Override
    /**
     * High probability of important words to be retained in a reductive sample.
     */
    public TermWeights nextSample() throws Exception {
        TermWeights vecList = new TermWeights(prop);
        
        List<TermWeight> tweights = twts.getTermWeights();
        int nterms = (int) (tweights.size() * sampleRatio);
        
        for (int i=0; i < nterms; i++) {
            TermWeight tw = sampleTerm(tweights);
            vecList.add(tw);
        }
        return vecList;
    }
    
    TermWeight sampleTerm(List<TermWeight> tweights) {
        TermWeight tw = null;
        float start = 0, wt, end;
        float x = rand.nextFloat();
        int n = tweights.size();
            
        for (int i=0; i < n; i++) {
            tw = tweights.get(i);
            //wt = tw.getWt();
            wt = (float)1.0 / (float)n;
            end = start + wt;
            
            if (start <= x && x < end)  // interval lengths determine probability
                break; // generate this one
                
            start = end;
        }
        return tw;
    }

    @Override
    public void buildTermsInDoc() throws Exception {
        twts = gen.initTermWeights(expunit);
        twts.weightIDFs(expunit.getReader());        
    }
    
}
