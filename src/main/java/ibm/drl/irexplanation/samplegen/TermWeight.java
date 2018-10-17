/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ibm.drl.irexplanation.samplegen;

/**
 *
 * @author dganguly
 */
public class TermWeight {
    String term;
    int raw_tf;
    float wt;  // normalized weight corresponding to tf
    float idf_wt;  // idf component

    public TermWeight(String term, int raw_tf) {
        this.term = term;
        this.raw_tf = raw_tf;
    }
    
    public float getWt() { return idf_wt; }
    public String getTerm() { return term; }
}
