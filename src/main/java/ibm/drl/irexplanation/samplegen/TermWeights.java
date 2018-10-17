/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.samplegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

/**
 *
 * @author dganguly
 */
public class TermWeights {
    Properties prop;
    List<TermWeight> termwts;
    static int TERMS_IN_DOC = 200;
    
    public TermWeights(Properties prop) {
        this.prop = prop;
        termwts = new ArrayList<>(TERMS_IN_DOC);
    }
    
    public void add(TermWeight tw) {
        this.termwts.add(tw);
    }

    public void normalizeTfs() {
        float sum_tf = 0;
        for (TermWeight tw: termwts) {
            sum_tf += tw.raw_tf;
        }
        for (TermWeight tw: termwts) {
            tw.wt = tw.raw_tf/sum_tf;
        }
    }
    
    public void normalizeTfIdfs() {
        float z = 0;
        for (TermWeight tw: termwts) {
            z += tw.idf_wt;
        }
        for (TermWeight tw: termwts) {
            tw.idf_wt = tw.idf_wt/z;
        }
    }
    
    
    public void weightIDFs(IndexReader reader) throws Exception {
        normalizeTfs();
        
        long cf;
        long cs;
        float idf;
        
        String contentFieldName = prop.getProperty("field.content");
        
        cs = reader.getSumTotalTermFreq(contentFieldName);
        float lambda = Float.parseFloat(prop.getProperty("term.importance.sampler.lambda", "0.6"));
        float alpha = lambda/(1-lambda);
        
        for (TermWeight tw: termwts) {
            cf = reader.totalTermFreq(new Term(contentFieldName, tw.term));
            idf = cs/(float)cf;
            tw.idf_wt = (float)(Math.log(1 + alpha * tw.wt * idf));
        }    
        
        normalizeTfIdfs();
    }
    
    public List<TermWeight> getTermWeights() { return termwts; }
    
    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (TermWeight tw: this.getTermWeights()) {
            buff.append(tw.term).append(" ");
        }
        if (buff.length() > 0) buff.deleteCharAt(buff.length()-1);
        return buff.toString();
    }
}
