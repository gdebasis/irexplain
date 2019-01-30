/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.sampler;

import ibm.drl.irexplanation.samplegen.BaseSampleGenerator;
import ibm.drl.irexplanation.samplegen.ExplanationUnit;
import ibm.drl.irexplanation.samplegen.TermWeight;
import ibm.drl.irexplanation.samplegen.TermWeights;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;

/**
 * Grays out parts of documents.
 * Parameters to control out graying:
 * 1. Min size of a consecutive visible block.
 * 2. Probability of a block to be visible.
 * 
 * @author dganguly
 */
public class MaskingSampler extends BaseSampler {
    String[] termsInDoc;
    byte[] mask;
    int chunkSize;
    float visProb;
    int numChunks;
    byte[] chunk;
    
    @Override
    public void buildTermsInDoc() throws Exception {
        int docId = expunit.getDocId();
        String contentFieldName = prop.getProperty("field.content");
        String text = expunit.getReader().document(docId).get(contentFieldName);
        
        termsInDoc = this.gen.analyze(new WhitespaceAnalyzer(), text);        
        mask = new byte[termsInDoc.length];
        
        chunkSize = Math.min(Integer.parseInt(prop.getProperty("maskingsampler.chunksize", "5")), termsInDoc.length);
        visProb = Float.parseFloat(prop.getProperty("maskingsampler.visprob"));
        
        numChunks = termsInDoc.length/chunkSize;
        chunk = new byte[chunkSize];
    }
    
    public MaskingSampler(Properties prop, BaseSampleGenerator gen, ExplanationUnit expunit) throws Exception {
        super(prop, gen, expunit);
    }

    void genChunk() {
        float x = rand.nextFloat();
        byte vis = x < visProb? (byte)1 : (byte)0;
        Arrays.fill(chunk, vis);
    }
    
    void genRandomMask() {
        Arrays.fill(mask, (byte)0);
        
        int start = 0;
        
        while (start < termsInDoc.length) {
            genChunk();
            
            for (int j=0; j < chunkSize; j++) {
                if (start + j < termsInDoc.length) {
                    mask[start + j] = chunk[j];
                }
            }
            start += chunkSize;
        }
    }
    
    @Override
    public TermWeights nextSample() throws Exception {
        TermWeights vecList = new TermWeights(prop);
        TermWeight tw;
        
        genRandomMask(); // generate the mask
        
        List<String> tokens = new ArrayList<>();
        // collect raw tokens
        for (int i=0; i < mask.length; i++) {
            if (mask[i] == 1) {
                tokens.add(termsInDoc[i]);
            }
        }
        
        // analyze each... useful to print the raw tokens, while
        // generate scores with analyzed ones
        for (String token: tokens) {
            String[] analyzedTerm = gen.analyze(token);
            if  (analyzedTerm.length > 0) {
                tw = new TermWeight(token, analyzedTerm[0], 1);
                vecList.add(tw);
            }
        }
        
        return vecList;
    }
}
